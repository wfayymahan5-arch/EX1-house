-- Public player biography, written only through the moderated update-profile Edge Function.
-- This migration is staged on the Native migration branch only; it is not applied to Production.
alter table public.profiles
  add column if not exists bio text;

do $$
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
end
$;

comment on column public.profiles.bio is
  'Optional public player biography; content is moderated by the update-profile Edge Function before it is stored.';
