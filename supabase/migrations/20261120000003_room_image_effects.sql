-- Optional lightweight visual overlay setting for each room.
-- Existing rooms remain unchanged and effects are disabled by default.
alter table public.tournament_rooms
  add column if not exists room_effects_enabled boolean not null default false;
