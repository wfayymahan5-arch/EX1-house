-- EX1 HOUSE database schema for Supabase (PostgreSQL)
-- Run this in Supabase SQL Editor after creating a project.
-- Admin access is granted only through trusted Supabase Auth app_metadata.role = 'admin'.
-- Never put the service_role key in browser code.

create extension if not exists pgcrypto;

create table if not exists public.profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  username text unique,
  display_name text,
  created_at timestamptz not null default now()
);

create table if not exists public.tournaments (
  id uuid primary key default gen_random_uuid(),
  title text not null,
  game_mode text not null default 'Battle Royale',
  map_name text,
  description text,
  status text not null default 'draft'
    check (status in ('draft','open','full','live','completed','cancelled')),
  max_players integer not null default 100 check (max_players > 0),
  starts_at timestamptz,
  xp_reward integer not null default 0 check (xp_reward >= 0),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.registrations (
  id uuid primary key default gen_random_uuid(),
  tournament_id uuid not null references public.tournaments(id) on delete cascade,
  user_id uuid not null references auth.users(id) on delete cascade,
  status text not null default 'registered'
    check (status in ('registered','confirmed','cancelled','checked_in')),
  created_at timestamptz not null default now(),
  unique (tournament_id, user_id)
);

create table if not exists public.xp_transactions (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  amount integer not null check (amount <> 0),
  reason text not null,
  tournament_id uuid references public.tournaments(id) on delete set null,
  created_by uuid references auth.users(id) on delete set null,
  created_at timestamptz not null default now()
);

create table if not exists public.app_settings (
  key text primary key,
  value jsonb not null default '{}'::jsonb,
  updated_at timestamptz not null default now()
);

create index if not exists registrations_tournament_idx on public.registrations(tournament_id);
create index if not exists registrations_user_idx on public.registrations(user_id);
create index if not exists xp_transactions_user_idx on public.xp_transactions(user_id, created_at desc);
create index if not exists tournaments_status_starts_idx on public.tournaments(status, starts_at);

create or replace function public.is_ex1_admin()
returns boolean
language sql
stable
security invoker
set search_path = ''
as $$
  select coalesce((auth.jwt() -> 'app_metadata' ->> 'role') = 'admin', false);
$$;

alter table public.profiles enable row level security;
alter table public.tournaments enable row level security;
alter table public.registrations enable row level security;
alter table public.xp_transactions enable row level security;
alter table public.app_settings enable row level security;

drop policy if exists "Profiles readable by owner or admin" on public.profiles;
create policy "Profiles readable by owner or admin"
on public.profiles for select to authenticated
using (id = (select auth.uid()) or (select public.is_ex1_admin()));

drop policy if exists "Users update own basic profile" on public.profiles;
create policy "Users update own basic profile"
on public.profiles for update to authenticated
using (id = (select auth.uid()))
with check (id = (select auth.uid()));

drop policy if exists "Admins manage profiles" on public.profiles;
create policy "Admins manage profiles"
on public.profiles for all to authenticated
using ((select public.is_ex1_admin()))
with check ((select public.is_ex1_admin()));

drop policy if exists "Public can read open tournaments" on public.tournaments;
create policy "Public can read open tournaments"
on public.tournaments for select to anon, authenticated
using (status in ('open','full','live','completed'));

drop policy if exists "Admins manage tournaments" on public.tournaments;
create policy "Admins manage tournaments"
on public.tournaments for all to authenticated
using ((select public.is_ex1_admin()))
with check ((select public.is_ex1_admin()));

drop policy if exists "Users read own registrations or admin reads all" on public.registrations;
create policy "Users read own registrations or admin reads all"
on public.registrations for select to authenticated
using (user_id = (select auth.uid()) or (select public.is_ex1_admin()));

drop policy if exists "Users register themselves" on public.registrations;
create policy "Users register themselves"
on public.registrations for insert to authenticated
with check (
  user_id = (select auth.uid())
  and exists (
    select 1 from public.tournaments t
    where t.id = tournament_id and t.status = 'open'
  )
);

drop policy if exists "Users cancel own registrations" on public.registrations;
create policy "Users cancel own registrations"
on public.registrations for update to authenticated
using (user_id = (select auth.uid()))
with check (user_id = (select auth.uid()) and status = 'cancelled');

drop policy if exists "Admins manage registrations" on public.registrations;
create policy "Admins manage registrations"
on public.registrations for all to authenticated
using ((select public.is_ex1_admin()))
with check ((select public.is_ex1_admin()));

drop policy if exists "Users read own XP or admin reads all" on public.xp_transactions;
create policy "Users read own XP or admin reads all"
on public.xp_transactions for select to authenticated
using (user_id = (select auth.uid()) or (select public.is_ex1_admin()));

drop policy if exists "Admins create XP transactions" on public.xp_transactions;
create policy "Admins create XP transactions"
on public.xp_transactions for insert to authenticated
with check ((select public.is_ex1_admin()) and created_by = (select auth.uid()));

drop policy if exists "Admins read and manage settings" on public.app_settings;
create policy "Admins read and manage settings"
on public.app_settings for all to authenticated
using ((select public.is_ex1_admin()))
with check ((select public.is_ex1_admin()));

-- Create a profile automatically whenever a new Auth user signs up.
create or replace function public.handle_new_ex1_user()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  insert into public.profiles (id, username, display_name)
  values (
    new.id,
    nullif(new.raw_user_meta_data ->> 'username', ''),
    coalesce(nullif(new.raw_user_meta_data ->> 'display_name', ''), new.email)
  )
  on conflict (id) do nothing;
  return new;
end;
$$;

drop trigger if exists on_auth_user_created_ex1 on auth.users;
create trigger on_auth_user_created_ex1
after insert on auth.users
for each row execute procedure public.handle_new_ex1_user();
