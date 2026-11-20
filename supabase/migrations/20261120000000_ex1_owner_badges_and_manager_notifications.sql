-- EX1 owner/manager identity, public role badges, and assignment notifications.
-- Existing role/permission records remain the source of truth.

create or replace function private.ex1_my_role_summary()
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_uid uuid := (select auth.uid());
  v_is_owner boolean := false;
  v_roles text[] := array[]::text[];
  v_permissions text[] := array[]::text[];
begin
  if v_uid is null then
    raise exception 'Authentication required' using errcode = '42501';
  end if;

  select exists (
    select 1 from private.system_owner so
    where so.singleton is true and so.user_id = v_uid
  ) into v_is_owner;

  select coalesce(array_agg(distinct aur.role_code order by aur.role_code), array[]::text[])
    into v_roles
  from private.admin_user_roles aur
  where aur.user_id = v_uid and aur.is_active is true;

  select coalesce(array_agg(p.permission_code order by p.permission_code), array[]::text[])
    into v_permissions
  from private.permissions p
  where private.has_permission(p.permission_code);

  return jsonb_build_object(
    'is_owner', v_is_owner,
    'is_manager', coalesce(array_length(v_roles, 1), 0) > 0,
    'roles', to_jsonb(v_roles),
    'permissions', to_jsonb(v_permissions)
  );
end;
$$;

create or replace function public.ex1_my_role_summary()
returns jsonb
language sql
stable
set search_path = ''
as $$
  select private.ex1_my_role_summary();
$$;

revoke all on function public.ex1_my_role_summary() from public, anon;
grant execute on function public.ex1_my_role_summary() to authenticated;

create or replace function public.ex1_public_role_badges(p_user_ids uuid[])
returns table(user_id uuid, badge_type text)
language plpgsql
stable
security definer
set search_path = ''
as $$
begin
  if coalesce(array_length(p_user_ids, 1), 0) > 100 then
    raise exception 'At most 100 user ids may be requested' using errcode = '22023';
  end if;

  return query
  select distinct x.user_id, x.badge_type
  from (
    select so.user_id, 'owner'::text as badge_type
    from private.system_owner so
    where so.singleton is true and so.user_id = any(coalesce(p_user_ids, array[]::uuid[]))
    union all
    select aur.user_id, 'manager'::text as badge_type
    from private.admin_user_roles aur
    where aur.is_active is true and aur.user_id = any(coalesce(p_user_ids, array[]::uuid[]))
  ) x;
end;
$$;

revoke all on function public.ex1_public_role_badges(uuid[]) from public;
grant execute on function public.ex1_public_role_badges(uuid[]) to anon, authenticated;

create or replace function private.notify_ex1_manager_role_assignment()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_new_assignment boolean := (tg_op = 'INSERT');
begin
  if tg_op = 'UPDATE' then
    v_new_assignment := old.is_active is distinct from true;
  end if;

  if new.is_active is true and v_new_assignment then
    insert into public.user_notifications
      (user_id, notification_type, title, body)
    values
      (new.user_id, 'manager_role', 'نشان مدیر برای حساب شما فعال شد',
       'مالک EX1 برای حساب شما نقش مدیریتی فعال کرده است. وظایف مجاز شما در بخش ابزارهای مدیر نمایش داده می‌شود.');
  end if;

  return new;
end;
$$;

drop trigger if exists ex1_manager_role_assignment_notification on private.admin_user_roles;
create trigger ex1_manager_role_assignment_notification
after insert or update of is_active on private.admin_user_roles
for each row execute function private.notify_ex1_manager_role_assignment();

revoke all on function private.notify_ex1_manager_role_assignment() from public, anon, authenticated;
