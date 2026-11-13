-- Team invitations and roster-based room registration.
-- Demo points remain non-monetary and are not charged or redeemable.

create table if not exists public.team_invites (
  id uuid primary key default gen_random_uuid(),
  team_id uuid not null references public.teams(id) on delete cascade,
  invited_user_id uuid not null references public.profiles(id) on delete cascade,
  invited_by uuid not null references public.profiles(id) on delete cascade,
  status text not null default 'pending' check (status in ('pending','accepted','declined','cancelled')),
  created_at timestamptz not null default now(),
  responded_at timestamptz,
  constraint team_invites_not_self check (invited_user_id <> invited_by)
);
create unique index if not exists team_invites_one_pending_per_user
  on public.team_invites(team_id, invited_user_id) where status='pending';
create index if not exists team_invites_invited_user_status
  on public.team_invites(invited_user_id,status,created_at desc);
create unique index if not exists team_members_one_team_per_user
  on public.team_members(user_id);

alter table public.team_invites enable row level security;
drop policy if exists "Users can view their team invitations" on public.team_invites;
create policy "Users can view their team invitations"
on public.team_invites for select to authenticated
using (
  invited_user_id = (select auth.uid())
  or invited_by = (select auth.uid())
  or exists (select 1 from public.teams t where t.id=team_id and t.captain_id=(select auth.uid()))
  or private.has_permission('teams.manage')
);
revoke insert, update, delete on public.team_invites from anon, authenticated;
grant select on public.team_invites to authenticated;

create or replace function public.team_invite_create(p_username text)
returns jsonb
language plpgsql
security definer
set search_path to ''
as $function$
declare
  v_uid uuid := auth.uid();
  v_team_id uuid;
  v_target_id uuid;
  v_invite_id uuid;
  v_member_count integer;
begin
  if v_uid is null then raise exception 'برای ارسال دعوت ابتدا وارد حساب شو.'; end if;
  select t.id into v_team_id
  from public.teams t
  where t.captain_id=v_uid
  order by t.created_at
  limit 1
  for update;
  if v_team_id is null then raise exception 'فقط کاپیتان تیم می‌تواند دعوت بفرستد.'; end if;

  select p.id into v_target_id
  from public.profiles p
  where lower(p.username)=lower(trim(coalesce(p_username,'')))
  limit 1;
  if v_target_id is null then raise exception 'این نام کاربری پیدا نشد.'; end if;
  if v_target_id=v_uid then raise exception 'نمی‌توانی خودت را دعوت کنی.'; end if;
  if exists(select 1 from public.team_members tm where tm.user_id=v_target_id) then
    raise exception 'این بازیکن از قبل عضو یک تیم است.';
  end if;
  select count(*) into v_member_count from public.team_members tm where tm.team_id=v_team_id;
  if v_member_count>=4 then raise exception 'ظرفیت تیم چهار نفر است و تکمیل شده.'; end if;
  if exists(select 1 from public.team_invites i where i.team_id=v_team_id and i.invited_user_id=v_target_id and i.status='pending') then
    raise exception 'برای این بازیکن دعوت در انتظار پاسخ وجود دارد.';
  end if;

  insert into public.team_invites(team_id,invited_user_id,invited_by)
  values(v_team_id,v_target_id,v_uid)
  returning id into v_invite_id;
  return jsonb_build_object('success',true,'invite_id',v_invite_id);
end;
$function$;

create or replace function public.team_invite_respond(p_invite_id uuid,p_accept boolean)
returns jsonb
language plpgsql
security definer
set search_path to ''
as $function$
declare
  v_uid uuid := auth.uid();
  v_invite public.team_invites%rowtype;
  v_member_count integer;
begin
  if v_uid is null then raise exception 'برای پاسخ به دعوت ابتدا وارد حساب شو.'; end if;
  select * into v_invite from public.team_invites where id=p_invite_id for update;
  if not found or v_invite.invited_user_id<>v_uid then raise exception 'دعوت پیدا نشد یا متعلق به این حساب نیست.'; end if;
  if v_invite.status<>'pending' then raise exception 'این دعوت دیگر فعال نیست.'; end if;

  if p_accept then
    if exists(select 1 from public.team_members tm where tm.user_id=v_uid) then
      raise exception 'برای پیوستن باید ابتدا از تیم فعلی خارج شوی یا عضویت آن لغو شود.';
    end if;
    select count(*) into v_member_count from public.team_members tm where tm.team_id=v_invite.team_id;
    if v_member_count>=4 then raise exception 'ظرفیت این تیم تکمیل شده است.'; end if;
    insert into public.team_members(team_id,user_id,role) values(v_invite.team_id,v_uid,'member');
    update public.team_invites set status='accepted',responded_at=now() where id=p_invite_id;
  else
    update public.team_invites set status='declined',responded_at=now() where id=p_invite_id;
  end if;
  return jsonb_build_object('success',true,'status',case when p_accept then 'accepted' else 'declined' end);
end;
$function$;

create or replace function public.team_invite_cancel(p_invite_id uuid)
returns jsonb
language plpgsql
security definer
set search_path to ''
as $function$
declare
  v_uid uuid := auth.uid();
  v_invite public.team_invites%rowtype;
begin
  if v_uid is null then raise exception 'ابتدا وارد حساب شو.'; end if;
  select i.* into v_invite
  from public.team_invites i
  join public.teams t on t.id=i.team_id
  where i.id=p_invite_id and t.captain_id=v_uid
  for update of i;
  if not found then raise exception 'دعوت پیدا نشد یا اجازهٔ لغو آن را نداری.'; end if;
  if v_invite.status<>'pending' then raise exception 'این دعوت دیگر فعال نیست.'; end if;
  update public.team_invites set status='cancelled',responded_at=now() where id=p_invite_id;
  return jsonb_build_object('success',true);
end;
$function$;

create or replace function public.register_for_tournament_room(p_room_id uuid)
returns jsonb
language plpgsql
security definer
set search_path to ''
as $function$
declare
  v_uid uuid := auth.uid();
  v_room public.tournament_rooms%rowtype;
  v_team public.teams%rowtype;
  v_team_id uuid;
  v_slot integer;
  v_member_count integer;
begin
  if v_uid is null then raise exception 'برای ثبت‌نام ابتدا وارد حساب شو.'; end if;

  select * into v_room from public.tournament_rooms where id=p_room_id for update;
  if not found then raise exception 'روم پیدا نشد.'; end if;
  if v_room.status<>'open' then raise exception 'ثبت‌نام این روم باز نیست یا ظرفیت آن تکمیل شده است.'; end if;
  if not exists(select 1 from public.tournaments t where t.id=v_room.tournament_id and t.status='registration_open') then
    raise exception 'ثبت‌نام مسابقه مادر باز نیست.';
  end if;

  select t.* into v_team
  from public.teams t
  join public.team_members tm on tm.team_id=t.id
  where tm.user_id=v_uid and t.captain_id=v_uid
  order by t.created_at
  limit 1;
  if v_team.id is null then raise exception 'برای ثبت‌نام باید کاپیتان یک تیم باشی؛ ابتدا تیم بساز یا از کاپیتان بخواه ثبت‌نام کند.'; end if;
  v_team_id:=v_team.id;

  select count(*) into v_member_count from public.team_members tm where tm.team_id=v_team_id;
  if v_member_count<>v_room.team_size then
    raise exception 'این روم به تیم % نفره نیاز دارد؛ تیم تو اکنون % عضو دارد.',v_room.team_size,v_member_count;
  end if;

  if exists(select 1 from public.tournament_room_registrations r where r.room_id=p_room_id and r.team_id=v_team_id and r.status in ('pending','approved')) then
    raise exception 'تیمت قبلاً در این روم ثبت‌نام کرده است.';
  end if;
  if exists(select 1 from public.tournament_registrations tr where tr.tournament_id=v_room.tournament_id and tr.team_id=v_team_id and tr.status in ('pending','approved')) then
    raise exception 'تیمت برای این مسابقه قبلاً ثبت‌نام فعال دارد.';
  end if;

  select s into v_slot
  from generate_series(1,least(greatest(v_room.max_teams,1),25)) s
  where not exists(
    select 1 from public.tournament_room_registrations r
    where r.room_id=p_room_id and r.slot_number=s and r.status in ('pending','approved')
  )
  order by s limit 1;
  if v_slot is null then raise exception 'ظرفیت این روم تکمیل شده است.'; end if;

  insert into public.tournament_registrations(tournament_id,team_id,status)
  values(v_room.tournament_id,v_team_id,'pending');
  insert into public.tournament_room_registrations(room_id,tournament_id,team_id,user_id,slot_number,status)
  values(p_room_id,v_room.tournament_id,v_team_id,v_uid,v_slot,'pending');

  if v_slot>=least(greatest(v_room.max_teams,1),25) then
    update public.tournament_rooms set status='full',updated_at=now() where id=p_room_id;
  end if;
  return jsonb_build_object('success',true,'slot_number',v_slot,'team_name',v_team.name,'status','pending');
end;
$function$;

revoke all on function public.team_invite_create(text) from public, anon;
revoke all on function public.team_invite_respond(uuid,boolean) from public, anon;
revoke all on function public.team_invite_cancel(uuid) from public, anon;
revoke all on function public.register_for_tournament_room(uuid) from public, anon;
revoke all on function public.get_tournament_room_slots(uuid) from public, anon;
grant execute on function public.team_invite_create(text) to authenticated;
grant execute on function public.team_invite_respond(uuid,boolean) to authenticated;
grant execute on function public.team_invite_cancel(uuid) to authenticated;
grant execute on function public.register_for_tournament_room(uuid) to authenticated;
grant execute on function public.get_tournament_room_slots(uuid) to authenticated;
