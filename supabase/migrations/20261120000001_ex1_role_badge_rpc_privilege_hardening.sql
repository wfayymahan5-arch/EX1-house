-- Keep the public badge RPC as an invoker wrapper; privileged lookup stays in private schema.
create or replace function private.ex1_public_role_badges_impl(p_user_ids uuid[])
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

revoke all on function private.ex1_public_role_badges_impl(uuid[]) from public, anon;
grant usage on schema private to authenticated;
grant execute on function private.ex1_public_role_badges_impl(uuid[]) to authenticated;

create or replace function public.ex1_public_role_badges(p_user_ids uuid[])
returns table(user_id uuid, badge_type text)
language sql
stable
set search_path = ''
as $$
  select * from private.ex1_public_role_badges_impl(p_user_ids);
$$;

revoke all on function public.ex1_public_role_badges(uuid[]) from public, anon;
grant execute on function public.ex1_public_role_badges(uuid[]) to authenticated;

revoke all on function private.ex1_my_role_summary() from public, anon;
grant execute on function private.ex1_my_role_summary() to authenticated;
