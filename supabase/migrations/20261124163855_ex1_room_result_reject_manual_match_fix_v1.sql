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
 if v_candidate.review_status not in ('pending','needs_manual_match') then raise exception 'candidate_not_pending' using errcode='22023'; end if;
 update private.room_result_candidates set review_status='rejected' where id=p_candidate_id;
 update private.room_result_batches set status=case when not exists(
  select 1 from private.room_result_candidates c where c.batch_id=v_batch.id and c.review_status in ('pending','needs_manual_match')
 ) then 'rejected' else status end,reviewed_by=v_reviewer,reviewed_at=now(),reviewer_note=p_reviewer_note where id=v_batch.id;
 insert into private.room_result_review_audit(batch_id,candidate_id,reviewer_id,decision,before_data,after_data,note)
 values(v_batch.id,p_candidate_id,v_reviewer,'rejected',
 jsonb_build_object('game_uid',v_candidate.game_uid,'placement',v_candidate.placement,'kills',v_candidate.kills),
 jsonb_build_object('review_status','rejected'),p_reviewer_note);
 return true;
end;
$function$;
