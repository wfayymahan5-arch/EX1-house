ALTER TABLE public.profiles
  ADD CONSTRAINT profiles_game_uid_no_email_chk
  CHECK (game_uid IS NULL OR strpos(game_uid, '@') = 0) NOT VALID;
ALTER TABLE public.profiles
  VALIDATE CONSTRAINT profiles_game_uid_no_email_chk;

CREATE OR REPLACE FUNCTION private.get_tournament_room_slots(p_room_id uuid)
RETURNS TABLE(slot_number integer, team_name text, status text, players jsonb)
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path TO ''
AS $function$
  select r.slot_number, t.name, r.status,
    coalesce(
      (
        select jsonb_agg(jsonb_build_object(
          'user_id', p.id,
          'display_name', p.display_name,
          'game_name', p.game_name,
          'game_uid', case when p.game_uid is not null and strpos(p.game_uid, '@') = 0 then p.game_uid else null end,
          'avatar_url', p.avatar_url
        ) order by q.created_at, q.id)
        from private.tournament_room_solo_queue q
        join public.profiles p on p.id = q.user_id
        where q.team_id = r.team_id and q.status = 'matched'
      ),
      (
        select jsonb_agg(jsonb_build_object(
          'user_id', p.id,
          'display_name', p.display_name,
          'game_name', p.game_name,
          'game_uid', case when p.game_uid is not null and strpos(p.game_uid, '@') = 0 then p.game_uid else null end,
          'avatar_url', p.avatar_url
        ) order by case when tm.role = 'captain' then 0 else 1 end, tm.joined_at)
        from public.team_members tm
        join public.profiles p on p.id = tm.user_id
        where tm.team_id = r.team_id
      ),
      '[]'::jsonb
    ) as players
  from public.tournament_room_registrations r
  join public.teams t on t.id = r.team_id
  join public.tournament_rooms room on room.id = r.room_id
  join public.tournaments tournament on tournament.id = room.tournament_id
  where r.room_id = p_room_id
    and r.status in ('pending', 'approved')
  order by r.slot_number;
$function$;

CREATE OR REPLACE FUNCTION public.get_tournament_room_waiting_players(p_room_id uuid)
RETURNS jsonb
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path TO ''
AS $function$
DECLARE
  v_uid uuid := auth.uid();
  v_tournament_status text;
  v_players jsonb;
BEGIN
  IF v_uid IS NULL THEN
    RAISE EXCEPTION 'برای مشاهده فهرست بازیکنان ابتدا وارد حساب شو.';
  END IF;

  SELECT t.status
  INTO v_tournament_status
  FROM public.tournament_rooms r
  JOIN public.tournaments t ON t.id = r.tournament_id
  WHERE r.id = p_room_id;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'روم پیدا نشد.';
  END IF;

  IF v_tournament_status NOT IN ('published', 'registration_open', 'ongoing', 'completed') THEN
    RETURN '[]'::jsonb;
  END IF;

  SELECT COALESCE(
    jsonb_agg(
      jsonb_build_object(
        'user_id', p.id,
        'display_name', p.display_name,
        'game_name', p.game_name,
        'game_uid', CASE
          WHEN p.game_uid IS NOT NULL AND strpos(p.game_uid, '@') = 0 THEN p.game_uid
          ELSE NULL
        END,
        'avatar_url', p.avatar_url,
        'registered_at', q.created_at
      )
      ORDER BY q.created_at, q.id
    ),
    '[]'::jsonb
  )
  INTO v_players
  FROM private.tournament_room_solo_queue q
  JOIN public.profiles p ON p.id = q.user_id
  WHERE q.room_id = p_room_id
    AND q.status = 'waiting';

  RETURN v_players;
END;
$function$;

REVOKE ALL ON FUNCTION public.get_tournament_room_waiting_players(uuid) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.get_tournament_room_waiting_players(uuid) TO authenticated;
