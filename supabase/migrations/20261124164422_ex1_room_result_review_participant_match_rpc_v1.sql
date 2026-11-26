create or replace function private.list_room_result_participants(p_room_id uuid)
returns jsonb language plpgsql security definer set search_path=''
as $function$
declare v_user uuid:=auth.uid(); v_data jsonb;
begin
 if v_user is null or not private.has_permission('results.manage') then raise exception 'not_authorized' using errcode='42501'; end if;
 select coalesce(jsonb_agg(jsonb_build_object('user_id',p.id,'game_uid',p.game_uid,'game_name',p.game_name,'display_name',p.display_name)
 order by coalesce(p.game_name,p.display_name)),'[]'::jsonb) into v_data
 from public.tournament_room_registrations rr join public.profiles p on p.id=rr.user_id
 where rr.room_id=p_room_id and rr.status='approved';
 return v_data;
end;
$function$;
revoke all on function private.list_room_result_participants(uuid) from public,anon;
grant execute on function private.list_room_result_participants(uuid) to authenticated;
create or replace function public.list_room_result_participants(p_room_id uuid)
returns jsonb language sql security invoker set search_path=''
as $fn$ select private.list_room_result_participants(p_room_id); $fn$;
revoke all on function public.list_room_result_participants(uuid) from public,anon;
grant execute on function public.list_room_result_participants(uuid) to authenticated;
