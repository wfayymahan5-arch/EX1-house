create or replace function private.list_room_result_review_batches(p_room_id uuid default null)
returns jsonb language plpgsql security definer set search_path=''
as $function$
declare v_user uuid:=auth.uid(); v_data jsonb;
begin
 if v_user is null or not private.has_permission('results.manage') then raise exception 'not_authorized' using errcode='42501'; end if;
 select coalesce(jsonb_agg(jsonb_build_object(
  'id',b.id,'room_id',b.room_id,'status',b.status,'created_at',b.created_at,'uploaded_by',b.uploaded_by,
  'provider',b.provider,'model',b.model,'uncertainties',b.uncertainties,
  'candidates',(select coalesce(jsonb_agg(jsonb_build_object('id',c.id,'game_uid',c.game_uid,'placement',c.placement,'kills',c.kills,'confidence',c.confidence,'evidence_image_indexes',c.evidence_image_indexes,'review_status',c.review_status,'matched_user_id',c.matched_user_id) order by c.created_at),'[]'::jsonb) from private.room_result_candidates c where c.batch_id=b.id),
  'images',(select coalesce(jsonb_agg(jsonb_build_object('object_path',i.object_path,'mime_type',i.mime_type,'byte_size',i.byte_size,'sha256',i.sha256) order by i.created_at),'[]'::jsonb) from private.room_result_images i where i.batch_id=b.id)
 ) order by b.created_at desc),'[]'::jsonb) into v_data
 from private.room_result_batches b where (p_room_id is null or b.room_id=p_room_id) and b.status in ('review_required','approved','rejected','failed') limit 100;
 return v_data;
end;
$function$;
create or replace function private.get_room_result_evidence(p_room_id uuid)
returns jsonb language plpgsql security definer set search_path=''
as $function$
declare v_user uuid:=auth.uid(); v_data jsonb;
begin
 if v_user is null then raise exception 'authentication_required' using errcode='42501'; end if;
 if not(private.has_permission('results.view') or private.has_permission('results.manage') or exists(
  select 1 from public.tournament_room_registrations rr where rr.room_id=p_room_id and rr.user_id=v_user and rr.status='approved'
 )) then raise exception 'not_authorized' using errcode='42501'; end if;
 select coalesce(jsonb_agg(jsonb_build_object('object_path',i.object_path,'mime_type',i.mime_type) order by i.created_at),'[]'::jsonb)
 into v_data from private.room_result_images i join private.room_result_batches b on b.id=i.batch_id
 where i.room_id=p_room_id and b.status='approved';
 return v_data;
end;
$function$;
revoke all on function private.list_room_result_review_batches(uuid) from public,anon;
revoke all on function private.get_room_result_evidence(uuid) from public,anon;
grant execute on function private.list_room_result_review_batches(uuid) to authenticated;
grant execute on function private.get_room_result_evidence(uuid) to authenticated;
create or replace function public.list_room_result_review_batches(p_room_id uuid default null)
returns jsonb language sql security invoker set search_path=''
as $fn$ select private.list_room_result_review_batches(p_room_id); $fn$;
create or replace function public.get_room_result_evidence(p_room_id uuid)
returns jsonb language sql security invoker set search_path=''
as $fn$ select private.get_room_result_evidence(p_room_id); $fn$;
revoke all on function public.list_room_result_review_batches(uuid) from public,anon;
revoke all on function public.get_room_result_evidence(uuid) from public,anon;
grant execute on function public.list_room_result_review_batches(uuid) to authenticated;
grant execute on function public.get_room_result_evidence(uuid) to authenticated;
