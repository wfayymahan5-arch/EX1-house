create index if not exists profiles_id_text_prefix_idx
on public.profiles ((id::text) text_pattern_ops);

create or replace function public.admin_manage_list_users()
returns table(user_id uuid,username text,display_name text,created_at timestamptz,is_admin boolean,role_codes text[],effective_permissions text[])
language plpgsql security definer set search_path=''
as $function$
begin
 if auth.uid() is null or not private.has_permission('admins.manage') or not private.has_permission('permissions.manage') then
  raise exception 'Permission denied' using errcode='42501';
 end if;
 return query
 select p.id,p.username,p.display_name,p.created_at,true,
        coalesce(roles.role_codes,array[]::text[]),
        coalesce(perms.permission_codes,array[]::text[])
 from public.profiles p
 join lateral (
  select array_agg(distinct aur.role_code order by aur.role_code) role_codes
  from private.admin_user_roles aur where aur.user_id=p.id and aur.is_active is true
 ) roles on coalesce(array_length(roles.role_codes,1),0)>0
 left join lateral (
  select array_agg(distinct e.permission_code order by e.permission_code) permission_codes
  from (
   select o.permission_code from private.admin_user_permission_overrides o where o.user_id=p.id and o.effect='allow'
   union
   select rp.permission_code from private.admin_user_roles aur
   join private.role_permissions rp on rp.role_code=aur.role_code
   where aur.user_id=p.id and aur.is_active is true
    and not exists(select 1 from private.admin_user_permission_overrides o where o.user_id=p.id and o.permission_code=rp.permission_code)
  ) e
 ) perms on true
 where not exists(select 1 from private.system_owner so where so.singleton is true and so.user_id=p.id)
 order by p.created_at desc;
end;
$function$;

create or replace function public.admin_manage_search_users(p_user_id_prefix text)
returns table(user_id uuid,username text,display_name text,created_at timestamptz,is_admin boolean,role_codes text[],effective_permissions text[])
language plpgsql security definer set search_path=''
as $function$
declare v_query text:=lower(btrim(coalesce(p_user_id_prefix,'')));
begin
 if auth.uid() is null or not private.has_permission('admins.manage') or not private.has_permission('permissions.manage') then
  raise exception 'Permission denied' using errcode='42501';
 end if;
 if length(v_query)<4 or length(v_query)>36 or v_query !~ '^[0-9a-f-]+$' then
  raise exception 'حداقل ۴ نویسه از ابتدای شناسه کاربر را وارد کن.' using errcode='22023';
 end if;
 return query
 select p.id,p.username,p.display_name,p.created_at,
        coalesce(array_length(roles.role_codes,1),0)>0,
        coalesce(roles.role_codes,array[]::text[]),
        coalesce(perms.permission_codes,array[]::text[])
 from public.profiles p
 left join lateral (
  select array_agg(distinct aur.role_code order by aur.role_code) role_codes
  from private.admin_user_roles aur where aur.user_id=p.id and aur.is_active is true
 ) roles on true
 left join lateral (
  select array_agg(distinct e.permission_code order by e.permission_code) permission_codes
  from (
   select o.permission_code from private.admin_user_permission_overrides o where o.user_id=p.id and o.effect='allow'
   union
   select rp.permission_code from private.admin_user_roles aur
   join private.role_permissions rp on rp.role_code=aur.role_code
   where aur.user_id=p.id and aur.is_active is true
    and not exists(select 1 from private.admin_user_permission_overrides o where o.user_id=p.id and o.permission_code=rp.permission_code)
  ) e
 ) perms on true
 where p.id::text like v_query || '%'
   and not exists(select 1 from private.system_owner so where so.singleton is true and so.user_id=p.id)
 order by p.created_at desc
 limit 20;
end;
$function$;

revoke all on function public.admin_manage_search_users(text) from public,anon;
grant execute on function public.admin_manage_search_users(text) to authenticated;