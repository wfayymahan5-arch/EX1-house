alter table public.tournament_rooms add column if not exists ends_at timestamptz;
create extension if not exists pg_cron with schema pg_catalog;
grant usage on schema cron to postgres;
grant all privileges on all tables in schema cron to postgres;
create or replace function private.cleanup_expired_tournament_rooms()
returns bigint language plpgsql security definer set search_path = '' as $$
declare v_deleted bigint := 0;
begin
 delete from public.tournament_rooms where ends_at is not null and ends_at <= now() - interval '24 hours';
 get diagnostics v_deleted = row_count;
 return v_deleted;
end; $$;
revoke all on function private.cleanup_expired_tournament_rooms() from public, anon, authenticated;
select cron.schedule('ex1-room-retention-cleanup','*/5 * * * *','select private.cleanup_expired_tournament_rooms();');
