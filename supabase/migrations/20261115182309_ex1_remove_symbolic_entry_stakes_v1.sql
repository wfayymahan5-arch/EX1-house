-- Remove the temporary Stars-entry staking RPCs; room registration stays free.
drop function if exists public.register_for_tournament_room_with_stars(uuid,integer);
drop function if exists public.register_for_tournament_room_solo_with_stars(uuid,integer);
drop function if exists private.register_for_tournament_room_with_stars(uuid,integer);
drop function if exists private.register_for_tournament_room_solo_with_stars(uuid,integer);
drop function if exists private.charge_room_entry(uuid,uuid,bigint,text);
