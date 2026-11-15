-- Allow a bounded per-player symbolic Stars entry fee on tournament rooms.
alter table public.tournament_rooms
  drop constraint if exists tournament_rooms_free_only_entry_check;

alter table public.tournament_rooms
  drop constraint if exists tournament_rooms_entry_demo_points_range_check;

alter table public.tournament_rooms
  add constraint tournament_rooms_entry_demo_points_range_check
  check (entry_demo_points >= 0 and entry_demo_points <= 1000000);

comment on column public.tournament_rooms.entry_demo_points is
  'Per-player room entry fee in symbolic non-cash EX1 Stars; charged once atomically at signup.';