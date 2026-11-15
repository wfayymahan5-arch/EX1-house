-- Harden the admin audit view so caller RLS policies are respected.
-- PostgreSQL 15+ supports security_invoker views; Supabase currently runs PG17.
alter view public.admin_audit_logs_view set (security_invoker = true);

-- The view's caller must be able to read the underlying private table.
-- Its existing RLS policy limits rows to users with audit.view.
grant usage on schema private to authenticated;
grant select on table private.admin_audit_logs to authenticated;

-- Keep the view available only to signed-in users; the underlying table RLS
-- remains the final row-level authorization boundary.
revoke all on table public.admin_audit_logs_view from anon;
grant select on table public.admin_audit_logs_view to authenticated;
