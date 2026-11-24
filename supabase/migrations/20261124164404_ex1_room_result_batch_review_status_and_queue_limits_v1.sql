-- Final batch status is approved only if at least one candidate is approved and no unresolved candidates remain.
-- Rejection keeps the batch approved if another candidate in the same batch was approved.
create or replace function private.approve_room_result_candidate(p_candidate_id uuid,p_matched_user_id uuid,p_placement integer,p_kills integer,p_reviewer_note text default null)
returns uuid language plpgsql security definer set search_path=''
as $function$
declare v_reviewer uuid:=auth.uid(); v_candidate private.room_result_candidates%rowtype; v_batch private.room_result_batches%rowtype;
 v_room public.tournament_rooms%rowtype; v_game_uid text; v_uid_matches integer; v_result_id uuid;
begin
 if v_reviewer is null or not private.has_permission('results.manage') then raise exception 'not_authorized' using errcode='42501'; end if;
 if p_placement is null or p_placement<1 or p_placement>1000 or p_kills is null or p_kills<0 or p_kills>1000 then raise exception 'invalid_result_values' using errcode='22023'; end if;
 select * into v_candidate from private.room_result_candidates where id=p_candidate_id for update;
 if not found then raise exception 'candidate_not_found' using errcode='P0002'; end if;
 select * into v_batch from private.room_result_batches where id=v_candidate.batch_id for update;
 if not found then raise exception 'batch_not_found' using errcode='P0002'; end if;
 if v_batch.uploaded_by=v_reviewer then raise exception 'uploader_cannot_approve_own_batch' using errcode='42501'; end if;
 if v_candidate.review_status='approved' and v_candidate.official_result_id is not null then return v_candidate.official_result_id; end if;
 if v_candidate.review_status not in ('pending','needs_manual_match') then raise exception 'candidate_not_pending' using errcode='22023'; end if;
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
 update private.room_result_batches b set status=case
 when exists(select 1 from private.room_result_candidates c where c.batch_id=b.id and c.review_status in ('pending','needs_manual_match')) then 'review_required'
 when exists(select 1 from private.room_result_candidates c where c.batch_id=b.id and c.review_status='approved') then 'approved' else 'rejected' end,
 reviewed_by=v_reviewer,reviewed_at=now(),reviewer_note=p_reviewer_note where b.id=v_batch.id;
 insert into private.room_result_review_audit(batch_id,candidate_id,reviewer_id,decision,before_data,after_data,note)
 values(v_batch.id,p_candidate_id,v_reviewer,'approved',jsonb_build_object('game_uid',v_candidate.game_uid,'placement',v_candidate.placement,'kills',v_candidate.kills),
 jsonb_build_object('matched_user_id',p_matched_user_id,'placement',p_placement,'kills',p_kills,'official_result_id',v_result_id),p_reviewer_note);
 return v_result_id;
end;
$function$;
create or replace function private.reject_room_result_candidate(p_candidate_id uuid,p_reviewer_note text default null)
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
 if v_candidate.review_status not in ('pending','needs_manual_match') then raise exception 'candidate_not_pending' using errcode='22023'; end if;
 update private.room_result_candidates set review_status='rejected' where id=p_candidate_id;
 update private.room_result_batches b set status=case
 when exists(select 1 from private.room_result_candidates c where c.batch_id=b.id and c.review_status in ('pending','needs_manual_match')) then 'review_required'
 when exists(select 1 from private.room_result_candidates c where c.batch_id=b.id and c.review_status='approved') then 'approved' else 'rejected' end,
 reviewed_by=v_reviewer,reviewed_at=now(),reviewer_note=p_reviewer_note where b.id=v_batch.id;
 insert into private.room_result_review_audit(batch_id,candidate_id,reviewer_id,decision,before_data,after_data,note)
 values(v_batch.id,p_candidate_id,v_reviewer,'rejected',jsonb_build_object('game_uid',v_candidate.game_uid,'placement',v_candidate.placement,'kills',v_candidate.kills),
 jsonb_build_object('review_status','rejected'),p_reviewer_note);
 return true;
end;
$function$;
create or replace function private.list_room_result_review_batches(p_room_id uuid default null)
returns jsonb language plpgsql security definer set search_path=''
as $function$
declare v_user uuid:=auth.uid(); v_data jsonb;
begin
 if v_user is null or not private.has_permission('results.manage') then raise exception 'not_authorized' using errcode='42501'; end if;
 select coalesce(jsonb_agg(x.payload order by x.created_at desc),'[]'::jsonb) into v_data
 from (select b.created_at,jsonb_build_object('id',b.id,'room_id',b.room_id,'status',b.status,'created_at',b.created_at,'uploaded_by',b.uploaded_by,
 'provider',b.provider,'model',b.model,'uncertainties',b.uncertainties,
 'candidates',(select coalesce(jsonb_agg(jsonb_build_object('id',c.id,'game_uid',c.game_uid,'placement',c.placement,'kills',c.kills,'confidence',c.confidence,'evidence_image_indexes',c.evidence_image_indexes,'review_status',c.review_status,'matched_user_id',c.matched_user_id) order by c.created_at),'[]'::jsonb) from private.room_result_candidates c where c.batch_id=b.id),
 'images',(select coalesce(jsonb_agg(jsonb_build_object('object_path',i.object_path,'mime_type',i.mime_type,'byte_size',i.byte_size,'sha256',i.sha256) order by i.created_at),'[]'::jsonb) from private.room_result_images i where i.batch_id=b.id)) payload
 from private.room_result_batches b where (p_room_id is null or b.room_id=p_room_id) and b.status in ('review_required','approved','rejected','failed')
 order by b.created_at desc limit 100) x;
 return v_data;
end;
$function$;
