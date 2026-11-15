-- EX1 LEGEND: non-cash room reward metadata.
-- Stars are internal points only. Room entry never debits or pools user balances.
-- Rewards are fixed admin-configured values, independent of participant entry balances.

alter table public.tournament_rooms
  add column if not exists prize_demo_points bigint not null default 0,
  add column if not exists prize_second_demo_points bigint not null default 0,
  add column if not exists prize_third_demo_points bigint not null default 0;

do $$
begin
  if not exists (
    select 1 from pg_constraint
    where conrelid='public.tournament_rooms'::regclass
      and conname='tournament_rooms_prize_demo_points_nonnegative'
  ) then
    alter table public.tournament_rooms
      add constraint tournament_rooms_prize_demo_points_nonnegative
      check (prize_demo_points >= 0 and prize_demo_points <= 1000000000);
  end if;
  if not exists (
    select 1 from pg_constraint
    where conrelid='public.tournament_rooms'::regclass
      and conname='tournament_rooms_prize_second_nonnegative'
  ) then
    alter table public.tournament_rooms
      add constraint tournament_rooms_prize_second_nonnegative
      check (prize_second_demo_points >= 0 and prize_second_demo_points <= 1000000000);
  end if;
  if not exists (
    select 1 from pg_constraint
    where conrelid='public.tournament_rooms'::regclass
      and conname='tournament_rooms_prize_third_nonnegative'
  ) then
    alter table public.tournament_rooms
      add constraint tournament_rooms_prize_third_nonnegative
      check (prize_third_demo_points >= 0 and prize_third_demo_points <= 1000000000);
  end if;
end
$$;

comment on column public.tournament_rooms.entry_demo_points is
  'Informational internal EX1 Stars value only; no automatic debit, stake, or pool.';
comment on column public.tournament_rooms.prize_demo_points is
  'Fixed non-cash EX1 Stars reward configured by admins; independent of entries.';
comment on column public.tournament_rooms.prize_second_demo_points is
  'Fixed non-cash second-place EX1 Stars reward; independent of entries.';
comment on column public.tournament_rooms.prize_third_demo_points is
  'Fixed non-cash third-place EX1 Stars reward; independent of entries.';
