-- Custom per-admin permissions controlled by the primary owner.
insert into private.admin_roles(role_code,display_name,description,is_system)
values ('custom_admin','مدیر سفارشی','دسترسی‌هایی که مالک به‌صورت مستقل برای این مدیر انتخاب می‌کند',false)
on conflict (role_code) do update set display_name=excluded.display_name,description=excluded.description;

create or replace function public.admin_manage_list_users()
returns table(user_id uuid,username text,display_name text,created_at timestamptz,is_admin boolean,role_codes text[],effective_permissions text[])
language plpgsql security definer set search_path=''
as $function$
begin
 if auth.uid() is null or not private.has_permission('admins.manage') or not private.has_permission('permissions.manage') then
  raise exception 'Permission denied' using errcode='42501';
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
 where not exists(select 1 from private.system_owner so where so.singleton is true and so.user_id=p.id)
 order by p.created_at desc limit 500;
end;
$function$;

create or replace function public.admin_manage_save_custom_admin(p_user_id uuid,p_permissions jsonb)
returns void language plpgsql security definer set search_path=''
as $function$
declare v_uid uuid:=auth.uid(); v_permission_count integer; v_requested_count integer; v_enabled text[];
begin
 if v_uid is null or not private.has_permission('admins.manage') or not private.has_permission('permissions.manage') then
  raise exception 'Permission denied' using errcode='42501';
 end if;
 if p_user_id is null or p_permissions is null or jsonb_typeof(p_permissions)<>'object' then
  raise exception 'کاربر و فهرست دسترسی‌ها معتبر نیست.' using errcode='22023';
 end if;
 if p_user_id=v_uid or exists(select 1 from private.system_owner so where so.singleton is true and so.user_id=p_user_id) then
  raise exception 'حساب مالک اصلی قابل تغییر نیست.' using errcode='42501';
 end if;
 if not exists(select 1 from public.profiles p where p.id=p_user_id) then
  raise exception 'کاربر EX1 پیدا نشد.' using errcode='22023';
 end if;
 select count(*) into v_permission_count from private.permissions;
 select count(*) into v_requested_count from jsonb_each(p_permissions);
 if v_requested_count<>v_permission_count or exists(
  select 1 from jsonb_each(p_permissions) e
  where jsonb_typeof(e.value)<>'boolean' or not exists(select 1 from private.permissions p where p.permission_code=e.key)
 ) then raise exception 'فهرست دسترسی‌ها ناقص یا نامعتبر است.' using errcode='22023'; end if;
 update private.admin_user_roles set is_active=false where user_id=p_user_id and is_active is true;
 insert into private.admin_user_roles(user_id,role_code,assigned_by,is_active)
 values(p_user_id,'custom_admin',v_uid,true)
 on conflict(user_id,role_code) do update set assigned_by=excluded.assigned_by,is_active=true,assigned_at=now();
 delete from private.admin_user_permission_overrides where user_id=p_user_id;
 insert into private.admin_user_permission_overrides(user_id,permission_code,effect,changed_by)
 select p_user_id,e.key,case when e.value='true'::jsonb then 'allow' else 'deny' end,v_uid
 from jsonb_each(p_permissions) e;
 select array_agg(e.key order by e.key) into v_enabled from jsonb_each(p_permissions) e where e.value='true'::jsonb;
 insert into private.admin_audit_logs(actor_id,target_user_id,action,entity_type,entity_id,details)
 values(v_uid,p_user_id,'custom_admin_permissions_saved','admin',p_user_id::text,
  jsonb_build_object('enabled_permissions',coalesce(v_enabled,array[]::text[]),'permission_count',v_permission_count));
end;
$function$;

create or replace function public.admin_manage_remove_custom_admin(p_user_id uuid)
returns void language plpgsql security definer set search_path=''
as $function$
declare v_uid uuid:=auth.uid();
begin
 if v_uid is null or not private.has_permission('admins.manage') or not private.has_permission('permissions.manage') then
  raise exception 'Permission denied' using errcode='42501';
 end if;
 if p_user_id is null or p_user_id=v_uid or exists(select 1 from private.system_owner so where so.singleton is true and so.user_id=p_user_id) then
  raise exception 'حساب مالک اصلی یا حساب فعلی قابل حذف از مدیران نیست.' using errcode='42501';
 end if;
 update private.admin_user_roles set is_active=false where user_id=p_user_id and is_active is true;
 delete from private.admin_user_permission_overrides where user_id=p_user_id;
 insert into private.admin_audit_logs(actor_id,target_user_id,action,entity_type,entity_id,details)
 values(v_uid,p_user_id,'custom_admin_removed','admin',p_user_id::text,'{}'::jsonb);
end;
$function$;

revoke all on function public.admin_manage_list_users() from public,anon;
revoke all on function public.admin_manage_save_custom_admin(uuid,jsonb) from public,anon;
revoke all on function public.admin_manage_remove_custom_admin(uuid) from public,anon;
grant execute on function public.admin_manage_list_users() to authenticated;
grant execute on function public.admin_manage_save_custom_admin(uuid,jsonb) to authenticated;
grant execute on function public.admin_manage_remove_custom_admin(uuid) to authenticated;
