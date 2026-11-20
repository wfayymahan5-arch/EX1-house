-- Only the system owner may manage administrator accounts or permissions.
-- All other manager permissions remain governed by the existing role/override model.
create or replace function private.has_permission(p_permission text)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select coalesce(
    auth.uid() is not null
    and (
      exists (
        select 1 from private.system_owner so
        where so.singleton is true and so.user_id = (select auth.uid())
      )
      or (
        p_permission not in ('admins.manage', 'permissions.manage')
        and (
          exists (
            select 1 from private.admin_user_permission_overrides o
            where o.user_id = (select auth.uid())
              and o.permission_code = p_permission
              and o.effect = 'allow'
          )
          or (
            not exists (
              select 1 from private.admin_user_permission_overrides o
              where o.user_id = (select auth.uid())
                and o.permission_code = p_permission
            )
            and exists (
              select 1
              from private.admin_user_roles aur
              join private.role_permissions rp on rp.role_code = aur.role_code
              where aur.user_id = (select auth.uid())
                and aur.is_active is true
                and rp.permission_code = p_permission
            )
          )
        )
      )
    ),
    false
  );
$$;
