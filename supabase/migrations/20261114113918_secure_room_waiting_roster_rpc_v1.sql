CREATE OR REPLACE FUNCTION private.get_tournament_room_waiting_players(p_room_id uuid)
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

CREATE OR REPLACE FUNCTION public.get_tournament_room_waiting_players(p_room_id uuid)
RETURNS jsonb
LANGUAGE sql
STABLE
SET search_path TO ''
AS $function$
  SELECT private.get_tournament_room_waiting_players($1);
$function$;

REVOKE ALL ON FUNCTION public.get_tournament_room_waiting_players(uuid) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.get_tournament_room_waiting_players(uuid) TO authenticated;

REVOKE ALL ON FUNCTION private.get_tournament_room_waiting_players(uuid) FROM PUBLIC, anon;
GRANT USAGE ON SCHEMA private TO authenticated;
GRANT EXECUTE ON FUNCTION private.get_tournament_room_waiting_players(uuid) TO authenticated;
