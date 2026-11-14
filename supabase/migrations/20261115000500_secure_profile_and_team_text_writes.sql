-- Prevent direct client writes to user-controlled profile/team text.
drop policy if exists "Users or authorized staff can update profiles" on public.profiles;
drop policy if exists "Captains or authorized staff can create teams" on public.teams;
drop policy if exists "Captains or authorized staff can update teams" on public.teams;

create or replace function public.team_create_for_user(
  p_user_id uuid,
  p_name text,
  p_tag text default null,
  p_description text default null
) returns uuid
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_team_id uuid;
  v_name text := trim(coalesce(p_name,''));
  v_tag text := nullif(upper(trim(coalesce(p_tag,''))),'');
  v_description text := nullif(trim(coalesce(p_description,'')),'');
begin
  if auth.role() <> 'service_role' then
    raise exception 'این عملیات فقط از مسیر امن سرور مجاز است.';
  end if;
  if p_user_id is null then raise exception 'شناسه کاربر معتبر نیست.'; end if;
  if char_length(v_name)<2 or char_length(v_name)>40 then raise exception 'نام تیم باید بین ۲ تا ۴۰ نویسه باشد.'; end if;
  if v_tag is not null and char_length(v_tag)>8 then raise exception 'تگ تیم حداکثر ۸ نویسه است.'; end if;
  if v_description is not null and char_length(v_description)>240 then raise exception 'توضیحات تیم حداکثر ۲۴۰ نویسه است.'; end if;
  if exists(select 1 from public.team_members tm where tm.user_id=p_user_id) then
    raise exception 'هر بازیکن در حال حاضر فقط می‌تواند عضو یک تیم باشد.';
  end if;
  insert into public.teams(name,tag,description,captain_id)
  values(v_name,v_tag,v_description,p_user_id)
  returning id into v_team_id;
  insert into public.team_members(team_id,user_id,role)
  values(v_team_id,p_user_id,'captain');
  return v_team_id;
end;
$$;
revoke all on function public.team_create_for_user(uuid,text,text,text) from public, anon, authenticated;
grant execute on function public.team_create_for_user(uuid,text,text,text) to service_role;
