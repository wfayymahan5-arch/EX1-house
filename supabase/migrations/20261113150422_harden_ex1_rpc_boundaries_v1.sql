-- Keep privileged EX1 operations in the non-exposed private schema.
alter function public.team_create(text,text,text) set schema private;
alter function public.team_invite_create(text) set schema private;
alter function public.team_invite_respond(uuid,boolean) set schema private;
alter function public.team_invite_cancel(uuid) set schema private;
alter function public.register_for_tournament_room(uuid) set schema private;
alter function public.get_tournament_room_slots(uuid) set schema private;

create or replace function private.team_invite_respond(p_invite_id uuid,p_accept boolean)
returns jsonb language plpgsql security definer set search_path to ''
as $function$
declare v_uid uuid:=auth.uid(); v_invite public.team_invites%rowtype; v_member_count integer;
begin
 if v_uid is null then raise exception 'برای پاسخ به دعوت ابتدا وارد حساب شو.'; end if;
 select * into v_invite from public.team_invites where id=p_invite_id for update;
 if not found or v_invite.invited_user_id<>v_uid then raise exception 'دعوت پیدا نشد یا متعلق به این حساب نیست.'; end if;
 if v_invite.status<>'pending' then raise exception 'این دعوت دیگر فعال نیست.'; end if;
 perform 1 from public.teams t where t.id=v_invite.team_id for update;
 if not found then raise exception 'تیم دیگر وجود ندارد.'; end if;
 if p_accept then
  if exists(select 1 from public.team_members tm where tm.user_id=v_uid) then raise exception 'برای پیوستن باید ابتدا از تیم فعلی خارج شوی یا عضویت آن لغو شود.'; end if;
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

create or replace function private.register_for_tournament_room(p_room_id uuid)
returns jsonb language plpgsql security definer set search_path to ''
as $function$
declare v_uid uuid:=auth.uid(); v_room public.tournament_rooms%rowtype; v_team public.teams%rowtype; v_team_id uuid; v_slot integer; v_member_count integer;
begin
 if v_uid is null then raise exception 'برای ثبت‌نام ابتدا وارد حساب شو.'; end if;
 select * into v_room from public.tournament_rooms where id=p_room_id for update;
 if not found then raise exception 'روم پیدا نشد.'; end if;
 if v_room.status<>'open' then raise exception 'ثبت‌نام این روم باز نیست یا ظرفیت آن تکمیل شده است.'; end if;
 if not exists(select 1 from public.tournaments t where t.id=v_room.tournament_id and t.status='registration_open') then raise exception 'ثبت‌نام مسابقه مادر باز نیست.'; end if;
 select t.* into v_team from public.teams t join public.team_members tm on tm.team_id=t.id
 where tm.user_id=v_uid and t.captain_id=v_uid order by t.created_at limit 1 for update of t;
 if v_team.id is null then raise exception 'برای ثبت‌نام باید کاپیتان یک تیم باشی؛ ابتدا تیم بساز یا از کاپیتان بخواه ثبت‌نام کند.'; end if;
 v_team_id:=v_team.id;
 select count(*) into v_member_count from public.team_members tm where tm.team_id=v_team_id;
 if v_member_count<>v_room.team_size then raise exception 'این روم به تیم % نفره نیاز دارد؛ تیم تو اکنون % عضو دارد.',v_room.team_size,v_member_count; end if;
 if exists(select 1 from public.tournament_room_registrations r where r.room_id=p_room_id and r.team_id=v_team_id and r.status in ('pending','approved')) then raise exception 'تیمت قبلاً در این روم ثبت‌نام کرده است.'; end if;
 if exists(select 1 from public.tournament_registrations tr where tr.tournament_id=v_room.tournament_id and tr.team_id=v_team_id and tr.status in ('pending','approved')) then raise exception 'تیمت برای این مسابقه قبلاً ثبت‌نام فعال دارد.'; end if;
 select s into v_slot from generate_series(1,least(greatest(v_room.max_teams,1),25)) s
 where not exists(select 1 from public.tournament_room_registrations r where r.room_id=p_room_id and r.slot_number=s and r.status in ('pending','approved'))
 order by s limit 1;
 if v_slot is null then raise exception 'ظرفیت این روم تکمیل شده است.'; end if;
 insert into public.tournament_registrations(tournament_id,team_id,status) values(v_room.tournament_id,v_team_id,'pending');
 insert into public.tournament_room_registrations(room_id,tournament_id,team_id,user_id,slot_number,status) values(p_room_id,v_room.tournament_id,v_team_id,v_uid,v_slot,'pending');
 if v_slot>=least(greatest(v_room.max_teams,1),25) then update public.tournament_rooms set status='full',updated_at=now() where id=p_room_id; end if;
 return jsonb_build_object('success',true,'slot_number',v_slot,'team_name',v_team.name,'status','pending');
end;
$function$;

create or replace function private.get_tournament_room_slots(p_room_id uuid)
returns table(slot_number integer,team_name text,status text)
language sql stable security definer set search_path to ''
as $function$
 select r.slot_number,tm.name,r.status
 from public.tournament_room_registrations r
 join public.teams tm on tm.id=r.team_id
 join public.tournament_rooms room on room.id=r.room_id
 join public.tournaments tournament on tournament.id=room.tournament_id
 where r.room_id=p_room_id and r.status in ('pending','approved')
 and room.status in ('open','full','completed')
 and tournament.status in ('published','registration_open','ongoing','completed')
 order by r.slot_number;
$function$;

grant usage on schema private to anon,authenticated;
revoke all on function private.team_create(text,text,text) from public,anon;
revoke all on function private.team_invite_create(text) from public,anon;
revoke all on function private.team_invite_respond(uuid,boolean) from public,anon;
revoke all on function private.team_invite_cancel(uuid) from public,anon;
revoke all on function private.register_for_tournament_room(uuid) from public,anon;
revoke all on function private.get_tournament_room_slots(uuid) from public;
grant execute on function private.team_create(text,text,text) to authenticated;
grant execute on function private.team_invite_create(text) to authenticated;
grant execute on function private.team_invite_respond(uuid,boolean) to authenticated;
grant execute on function private.team_invite_cancel(uuid) to authenticated;
grant execute on function private.register_for_tournament_room(uuid) to authenticated;
grant execute on function private.get_tournament_room_slots(uuid) to anon,authenticated;

create or replace function public.team_create(p_name text,p_tag text default null,p_description text default null)
returns uuid language sql security invoker set search_path to '' as $function$ select private.team_create($1,$2,$3); $function$;
create or replace function public.team_invite_create(p_username text)
returns jsonb language sql security invoker set search_path to '' as $function$ select private.team_invite_create($1); $function$;
create or replace function public.team_invite_respond(p_invite_id uuid,p_accept boolean)
returns jsonb language sql security invoker set search_path to '' as $function$ select private.team_invite_respond($1,$2); $function$;
create or replace function public.team_invite_cancel(p_invite_id uuid)
returns jsonb language sql security invoker set search_path to '' as $function$ select private.team_invite_cancel($1); $function$;
create or replace function public.register_for_tournament_room(p_room_id uuid)
returns jsonb language sql security invoker set search_path to '' as $function$ select private.register_for_tournament_room($1); $function$;
create or replace function public.get_tournament_room_slots(p_room_id uuid)
returns table(slot_number integer,team_name text,status text)
language sql stable security invoker set search_path to '' as $function$ select * from private.get_tournament_room_slots($1); $function$;

revoke all on function public.team_create(text,text,text) from public,anon;
revoke all on function public.team_invite_create(text) from public,anon;
revoke all on function public.team_invite_respond(uuid,boolean) from public,anon;
revoke all on function public.team_invite_cancel(uuid) from public,anon;
revoke all on function public.register_for_tournament_room(uuid) from public,anon;
revoke all on function public.get_tournament_room_slots(uuid) from public;
grant execute on function public.team_create(text,text,text) to authenticated;
grant execute on function public.team_invite_create(text) to authenticated;
grant execute on function public.team_invite_respond(uuid,boolean) to authenticated;
grant execute on function public.team_invite_cancel(uuid) to authenticated;
grant execute on function public.register_for_tournament_room(uuid) to authenticated;
grant execute on function public.get_tournament_room_slots(uuid) to anon,authenticated;
