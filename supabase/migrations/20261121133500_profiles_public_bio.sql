-- Public player biography, written only through the moderated update-profile Edge Function.
-- This migration is staged on the Native migration branch only; it is not applied to Production.
alter table public.profiles
  add column if not exists bio text;

do $ex1_bio$
begin
  if not exists (
    select 1
    from pg_constraint
    where conrelid = 'public.profiles'::regclass
      and conname = 'profiles_bio_length_check'
  ) then
    alter table public.profiles
      add constraint profiles_bio_length_check
      check (bio is null or char_length(bio) <= 500);
  end if;
end;
$ex1_bio$;

comment on column public.profiles.bio is
  'Optional public player biography; content is moderated by the update-profile Edge Function before it is stored.';


-- Defense in depth: existing profile UPDATE policies may permit users to update
-- their own row. Prevent direct client writes to bio so every public biography
-- must pass through the moderated update-profile Edge Function (service role).
create or replace function public.ex1_guard_profile_bio_write()
returns trigger
language plpgsql
set search_path = public, auth
as $ex1_bio_guard$
begin
  if auth.role() is distinct from 'service_role' then
    if tg_op = 'INSERT' and new.bio is not null then
      raise exception 'Profile biography must be submitted through the moderated profile endpoint.'
        using errcode = '42501';
    end if;

    if tg_op = 'UPDATE' and new.bio is distinct from old.bio then
      raise exception 'Profile biography must be submitted through the moderated profile endpoint.'
        using errcode = '42501';
    end if;
  end if;

  return new;
end;
$ex1_bio_guard$;

drop trigger if exists ex1_guard_profile_bio_write on public.profiles;
create trigger ex1_guard_profile_bio_write
before insert or update of bio on public.profiles
for each row execute function public.ex1_guard_profile_bio_write();
