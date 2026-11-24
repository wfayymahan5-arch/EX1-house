create table if not exists private.room_result_batches (
  id uuid primary key default gen_random_uuid(),
  room_id uuid not null references public.tournament_rooms(id) on delete cascade,
  uploaded_by uuid not null references auth.users(id) on delete restrict,
  status text not null default 'uploaded' check (status in ('uploaded','processing','review_required','approved','rejected','failed')),
  provider text, model text,
  extraction jsonb not null default '{}'::jsonb,
  uncertainties jsonb not null default '[]'::jsonb,
  created_at timestamptz not null default now(),
  reviewed_by uuid references auth.users(id) on delete set null,
  reviewed_at timestamptz, reviewer_note text
);
create table if not exists private.room_result_images (
  id uuid primary key default gen_random_uuid(),
  batch_id uuid not null references private.room_result_batches(id) on delete cascade,
  room_id uuid not null references public.tournament_rooms(id) on delete cascade,
  bucket_id text not null default 'room-result-evidence' check (bucket_id='room-result-evidence'),
  object_path text not null unique,
  sha256 text not null check (sha256 ~ '^[0-9a-f]{64}$'),
  mime_type text not null check (mime_type in ('image/jpeg','image/png','image/webp')),
  byte_size integer not null check (byte_size between 16 and 4194304),
  uploaded_by uuid not null references auth.users(id) on delete restrict,
  created_at timestamptz not null default now(),
  unique(room_id,sha256)
);
create table if not exists private.room_result_candidates (
  id uuid primary key default gen_random_uuid(),
  batch_id uuid not null references private.room_result_batches(id) on delete cascade,
  game_uid text, placement integer check(placement is null or placement between 1 and 1000),
  kills integer check(kills is null or kills between 0 and 1000),
  confidence text not null default 'low' check(confidence in ('high','medium','low')),
  evidence_image_indexes jsonb not null default '[]'::jsonb check(jsonb_typeof(evidence_image_indexes)='array'),
  matched_user_id uuid references auth.users(id) on delete set null,
  review_status text not null default 'pending' check(review_status in ('pending','approved','rejected','needs_manual_match')),
  official_result_id uuid references public.tournament_player_results(id) on delete set null,
  created_at timestamptz not null default now()
);
create table if not exists private.room_result_review_audit (
  id uuid primary key default gen_random_uuid(),
  batch_id uuid not null references private.room_result_batches(id) on delete cascade,
  candidate_id uuid references private.room_result_candidates(id) on delete set null,
  reviewer_id uuid not null references auth.users(id) on delete restrict,
  decision text not null check(decision in ('approved','rejected')),
  before_data jsonb not null default '{}'::jsonb,
  after_data jsonb not null default '{}'::jsonb,
  note text, created_at timestamptz not null default now()
);
create index if not exists room_result_batches_room_created_idx on private.room_result_batches(room_id,created_at desc);
create index if not exists room_result_candidates_batch_status_idx on private.room_result_candidates(batch_id,review_status);
create index if not exists room_result_images_batch_idx on private.room_result_images(batch_id);
alter table private.room_result_batches enable row level security;
alter table private.room_result_images enable row level security;
alter table private.room_result_candidates enable row level security;
alter table private.room_result_review_audit enable row level security;
revoke all on private.room_result_batches,private.room_result_images,private.room_result_candidates,private.room_result_review_audit from public,anon,authenticated;
grant all on private.room_result_batches,private.room_result_images,private.room_result_candidates,private.room_result_review_audit to service_role;

insert into storage.buckets(id,name,public,file_size_limit,allowed_mime_types)
values('room-result-evidence','room-result-evidence',false,4194304,array['image/jpeg','image/png','image/webp'])
on conflict(id) do update set public=false,file_size_limit=4194304,allowed_mime_types=array['image/jpeg','image/png','image/webp'];

drop policy if exists "EX1 staff upload private room result evidence" on storage.objects;
create policy "EX1 staff upload private room result evidence" on storage.objects for insert to authenticated
with check(bucket_id='room-result-evidence' and private.has_permission('results.manage')
 and (storage.foldername(name))[1] ~* '^[0-9a-f-]{36}$'
 and exists(select 1 from public.tournament_rooms r where r.id::text=(storage.foldername(name))[1]
 and (r.status='completed' or r.ends_at<=now())));

drop policy if exists "EX1 authorized room participants read result evidence" on storage.objects;
create policy "EX1 authorized room participants read result evidence" on storage.objects for select to authenticated
using(bucket_id='room-result-evidence' and (storage.foldername(name))[1] ~* '^[0-9a-f-]{36}$'
 and (private.has_permission('results.view') or private.has_permission('results.manage')
 or exists(select 1 from public.tournament_room_registrations rr where rr.room_id::text=(storage.foldername(name))[1]
 and rr.user_id=(select auth.uid()) and rr.status='approved')));

drop policy if exists "EX1 staff delete private room result evidence" on storage.objects;
create policy "EX1 staff delete private room result evidence" on storage.objects for delete to authenticated
using(bucket_id='room-result-evidence' and private.has_permission('results.manage'));

create unique index if not exists tournament_player_results_room_user_uidx
on public.tournament_player_results(room_id,user_id);

create or replace function public.approve_room_result_candidate(
 p_candidate_id uuid,p_matched_user_id uuid,p_placement integer,p_kills integer,p_reviewer_note text default null
) returns uuid language plpgsql security definer set search_path=''
as $function$
declare
 v_reviewer uuid:=auth.uid(); v_candidate private.room_result_candidates%rowtype;
 v_batch private.room_result_batches%rowtype; v_room public.tournament_rooms%rowtype;
 v_game_uid text; v_uid_matches integer; v_result_id uuid;
begin
 if v_reviewer is null or not private.has_permission('results.manage') then raise exception 'not_authorized' using errcode='42501'; end if;
 if p_placement is null or p_placement<1 or p_placement>1000 or p_kills is null or p_kills<0 or p_kills>1000 then raise exception 'invalid_result_values' using errcode='22023'; end if;
 select * into v_candidate from private.room_result_candidates where id=p_candidate_id for update;
 if not found then raise exception 'candidate_not_found' using errcode='P0002'; end if;
 select * into v_batch from private.room_result_batches where id=v_candidate.batch_id for update;
 if not found then raise exception 'batch_not_found' using errcode='P0002'; end if;
 if v_batch.uploaded_by=v_reviewer then raise exception 'uploader_cannot_approve_own_batch' using errcode='42501'; end if;
 if v_candidate.review_status='approved' and v_candidate.official_result_id is not null then return v_candidate.official_result_id; end if;
 if v_candidate.review_status not in ('pending','needs_manual_match') or v_batch.status not in ('review_required','approved') then raise exception 'candidate_not_pending' using errcode='22023'; end if;
 select * into v_room from public.tournament_rooms where id=v_batch.room_id;
 if not found or (v_room.status<>'completed' and (v_room.ends_at is null or v_room.ends_at>now())) then raise exception 'room_not_completed' using errcode='22023'; end if;
 select p.game_uid into v_game_uid from public.profiles p where p.id=p_matched_user_id;
 if v_candidate.game_uid is null or btrim(v_candidate.game_uid)='' or v_game_uid is null or btrim(v_game_uid)<>btrim(v_candidate.game_uid) then raise exception 'game_uid_mismatch' using errcode='22023'; end if;
 select count(*) into v_uid_matches from public.profiles p where btrim(p.game_uid)=btrim(v_candidate.game_uid);
 if v_uid_matches<>1 then raise exception 'game_uid_not_unique' using errcode='22023'; end if;
 if not exists(select 1 from public.tournament_room_registrations rr where rr.room_id=v_batch.room_id and rr.user_id=p_matched_user_id and rr.status='approved') then raise exception 'user_not_approved_for_room' using errcode='42501'; end if;
 insert into public.tournament_player_results(tournament_id,room_id,user_id,placement,kills,points)
 values(v_room.tournament_id,v_batch.room_id,p_matched_user_id,p_placement,p_kills,0) returning id into v_result_id;
 update private.room_result_candidates set matched_user_id=p_matched_user_id,placement=p_placement,kills=p_kills,review_status='approved',official_result_id=v_result_id where id=p_candidate_id;
 update private.room_result_batches set status='approved',reviewed_by=v_reviewer,reviewed_at=now(),reviewer_note=p_reviewer_note where id=v_batch.id;
 insert into private.room_result_review_audit(batch_id,candidate_id,reviewer_id,decision,before_data,after_data,note)
 values(v_batch.id,p_candidate_id,v_reviewer,'approved',jsonb_build_object('game_uid',v_candidate.game_uid,'placement',v_candidate.placement,'kills',v_candidate.kills),
 jsonb_build_object('matched_user_id',p_matched_user_id,'placement',p_placement,'kills',p_kills,'official_result_id',v_result_id),p_reviewer_note);
 return v_result_id;
end;
$function$;

create or replace function public.reject_room_result_candidate(p_candidate_id uuid,p_reviewer_note text default null)
returns boolean language plpgsql security definer set search_path=''
as $function$
declare v_reviewer uuid:=auth.uid(); v_candidate private.room_result_candidates%rowtype; v_batch private.room_result_batches%rowtype;
begin
 if v_reviewer is null or not private.has_permission('results.manage') then raise exception 'not_authorized' using errcode='42501'; end if;
 select * into v_candidate from private.room_result_candidates where id=p_candidate_id for update;
 if not found then raise exception 'candidate_not_found' using errcode='P0002'; end if;
 select * into v_batch from private.room_result_batches where id=v_candidate.batch_id for update;
 if not found then raise exception 'batch_not_found' using errcode='P0002'; end if;
 if v_batch.uploaded_by=v_reviewer then raise exception 'uploader_cannot_review_own_batch' using errcode='42501'; end if;
 if v_candidate.review_status<>'pending' then raise exception 'candidate_not_pending' using errcode='22023'; end if;
 update private.room_result_candidates set review_status='rejected' where id=p_candidate_id;
 update private.room_result_batches set status=case when not exists(select 1 from private.room_result_candidates c where c.batch_id=v_batch.id and c.review_status='pending') then 'rejected' else status end,
 reviewed_by=v_reviewer,reviewed_at=now(),reviewer_note=p_reviewer_note where id=v_batch.id;
 insert into private.room_result_review_audit(batch_id,candidate_id,reviewer_id,decision,before_data,after_data,note)
 values(v_batch.id,p_candidate_id,v_reviewer,'rejected',jsonb_build_object('game_uid',v_candidate.game_uid,'placement',v_candidate.placement,'kills',v_candidate.kills),jsonb_build_object('review_status','rejected'),p_reviewer_note);
 return true;
end;
$function$;
revoke all on function public.approve_room_result_candidate(uuid,uuid,integer,integer,text) from public,anon;
grant execute on function public.approve_room_result_candidate(uuid,uuid,integer,integer,text) to authenticated;
revoke all on function public.reject_room_result_candidate(uuid,text) from public,anon;
grant execute on function public.reject_room_result_candidate(uuid,text) to authenticated;
