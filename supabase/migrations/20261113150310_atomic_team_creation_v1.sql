-- Create a team and its captain membership atomically.
create or replace function public.team_create(p_name text,p_tag text default null,p_description text default null)
returns uuid
language plpgsql
security definer
set search_path to ''
as $function$
declare
  v_uid uuid := auth.uid();
  v_team_id uuid;
  v_name text := trim(coalesce(p_name,''));
  v_tag text := nullif(upper(trim(coalesce(p_tag,''))),'');
  v_description text := nullif(trim(coalesce(p_description,'')),'');
begin
  if v_uid is null then raise exception 'برای ساخت تیم ابتدا وارد حساب شو.'; end if;
  if char_length(v_name)<2 or char_length(v_name)>40 then raise exception 'نام تیم باید بین ۲ تا ۴۰ نویسه باشد.'; end if;
  if v_tag is not null and char_length(v_tag)>8 then raise exception 'تگ تیم حداکثر ۸ نویسه است.'; end if;
  if v_description is not null and char_length(v_description)>240 then raise exception 'توضیحات تیم حداکثر ۲۴۰ نویسه است.'; end if;
  if exists(select 1 from public.team_members tm where tm.user_id=v_uid) then
    raise exception 'هر بازیکن در حال حاضر فقط می‌تواند عضو یک تیم باشد.';
  end if;
  insert into public.teams(name,tag,description,captain_id)
  values(v_name,v_tag,v_description,v_uid)
  returning id into v_team_id;
  insert into public.team_members(team_id,user_id,role)
  values(v_team_id,v_uid,'captain');
  return v_team_id;
end;
$function$;
revoke all on function public.team_create(text,text,text) from public, anon;
grant execute on function public.team_create(text,text,text) to authenticated;
