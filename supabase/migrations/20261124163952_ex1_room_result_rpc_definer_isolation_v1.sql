alter function public.approve_room_result_candidate(uuid,uuid,integer,integer,text) set schema private;
alter function public.reject_room_result_candidate(uuid,text) set schema private;
alter function public.create_room_result_batch(uuid,uuid,text,text,jsonb,jsonb,jsonb,jsonb) set schema private;

create or replace function public.approve_room_result_candidate(p_candidate_id uuid,p_matched_user_id uuid,p_placement integer,p_kills integer,p_reviewer_note text default null)
returns uuid language sql security invoker set search_path=''
as $fn$ select private.approve_room_result_candidate(p_candidate_id,p_matched_user_id,p_placement,p_kills,p_reviewer_note); $fn$;
create or replace function public.reject_room_result_candidate(p_candidate_id uuid,p_reviewer_note text default null)
returns boolean language sql security invoker set search_path=''
as $fn$ select private.reject_room_result_candidate(p_candidate_id,p_reviewer_note); $fn$;
create or replace function public.create_room_result_batch(p_batch_id uuid,p_room_id uuid,p_provider text,p_model text,p_extraction jsonb,p_uncertainties jsonb,p_images jsonb,p_candidates jsonb)
returns uuid language sql security invoker set search_path=''
as $fn$ select private.create_room_result_batch(p_batch_id,p_room_id,p_provider,p_model,p_extraction,p_uncertainties,p_images,p_candidates); $fn$;

revoke all on function public.approve_room_result_candidate(uuid,uuid,integer,integer,text) from public,anon;
revoke all on function public.reject_room_result_candidate(uuid,text) from public,anon;
revoke all on function public.create_room_result_batch(uuid,uuid,text,text,jsonb,jsonb,jsonb,jsonb) from public,anon;
grant execute on function public.approve_room_result_candidate(uuid,uuid,integer,integer,text) to authenticated;
grant execute on function public.reject_room_result_candidate(uuid,text) to authenticated;
grant execute on function public.create_room_result_batch(uuid,uuid,text,text,jsonb,jsonb,jsonb,jsonb) to authenticated;
