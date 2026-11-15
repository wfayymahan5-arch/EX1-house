-- EX1 LEGEND: configurable second- and third-place Stars prizes.
-- Existing prize_demo_points is the single winner prize for <=5 slots,
-- and the first-place prize for rooms with more than 5 slots.
alter table public.tournament_rooms
  add column if not exists prize_second_demo_points bigint not null default 0,
  add column if not exists prize_third_demo_points bigint not null default 0;

do $$
begin
  if not exists (select 1 from pg_constraint where conrelid='public.tournament_rooms'::regclass and conname='tournament_rooms_prize_second_nonnegative') then
    alter table public.tournament_rooms add constraint tournament_rooms_prize_second_nonnegative check (prize_second_demo_points >= 0 and prize_second_demo_points <= 1000000000);
  end if;
  if not exists (select 1 from pg_constraint where conrelid='public.tournament_rooms'::regclass and conname='tournament_rooms_prize_third_nonnegative') then
    alter table public.tournament_rooms add constraint tournament_rooms_prize_third_nonnegative check (prize_third_demo_points >= 0 and prize_third_demo_points <= 1000000000);
  end if;
end
$$;

comment on column public.tournament_rooms.prize_demo_points is
  'Single winner prize for rooms with <=5 team slots; first-place prize for rooms with >5 team slots.';
comment on column public.tournament_rooms.prize_second_demo_points is
  'Second-place prize in EX1 Stars; used only when room max_teams > 5.';
comment on column public.tournament_rooms.prize_third_demo_points is
  'Third-place prize in EX1 Stars; used only when room max_teams > 5.';
