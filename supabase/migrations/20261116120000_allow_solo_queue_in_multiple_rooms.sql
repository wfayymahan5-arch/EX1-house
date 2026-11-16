-- Allow a player to wait in multiple rooms at the same time,
-- while still preventing duplicate waiting entries in the same room.
drop index if exists private.uq_solo_queue_one_waiting_per_user;

create unique index uq_solo_queue_one_waiting_per_user
  on private.tournament_room_solo_queue (room_id, user_id)
  where status = 'waiting';
