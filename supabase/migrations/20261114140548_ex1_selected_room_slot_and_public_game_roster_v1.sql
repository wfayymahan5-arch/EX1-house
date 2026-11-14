
CREATE OR REPLACE FUNCTION private.register_for_tournament_room(
  p_room_id uuid,
  p_slot_number integer
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO ''
AS $function$
DECLARE
  v_uid uuid := auth.uid();
  v_room public.tournament_rooms%rowtype;
  v_team public.teams%rowtype;
  v_team_id uuid;
  v_slot integer;
  v_member_count integer;
  v_max_teams integer;
BEGIN
  IF v_uid IS NULL THEN
    RAISE EXCEPTION 'برای ثبت‌نام ابتدا وارد حساب شو.';
  END IF;
  SELECT * INTO v_room FROM public.tournament_rooms WHERE id = p_room_id FOR UPDATE;
  IF NOT FOUND THEN RAISE EXCEPTION 'روم پیدا نشد.'; END IF;
  IF v_room.status <> 'open' THEN
    RAISE EXCEPTION 'ثبت‌نام این روم باز نیست یا ظرفیت آن تکمیل شده است.';
  END IF;
  IF v_room.starts_at IS NOT NULL AND v_room.starts_at <= now() THEN
    RAISE EXCEPTION 'مهلت ثبت‌نام این روم به پایان رسیده است.';
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM public.tournaments t
    WHERE t.id = v_room.tournament_id AND t.status IN ('published', 'registration_open')
  ) THEN
    RAISE EXCEPTION 'این مسابقه منتشر نشده یا امکان ثبت‌نام آن بسته شده است.';
  END IF;
  v_max_teams := LEAST(GREATEST(COALESCE(v_room.max_teams, 1), 1), 25);
  IF p_slot_number IS NULL OR p_slot_number < 1 OR p_slot_number > v_max_teams THEN
    RAISE EXCEPTION 'شماره جایگاه انتخاب‌شده معتبر نیست؛ دوباره یک جایگاه آزاد انتخاب کن.';
  END IF;
  SELECT t.* INTO v_team
  FROM public.teams t
  JOIN public.team_members tm ON tm.team_id = t.id
  WHERE tm.user_id = v_uid AND t.captain_id = v_uid
  ORDER BY t.created_at LIMIT 1 FOR UPDATE OF t;
  IF v_team.id IS NULL THEN
    RAISE EXCEPTION 'برای ثبت‌نام با تیم کامل باید کاپیتان یک تیم باشی.';
  END IF;
  v_team_id := v_team.id;
  SELECT count(*) INTO v_member_count FROM public.team_members tm WHERE tm.team_id = v_team_id;
  IF v_member_count <> v_room.team_size THEN
    RAISE EXCEPTION 'این روم به تیم % نفره نیاز دارد؛ تیم تو اکنون % عضو دارد.', v_room.team_size, v_member_count;
  END IF;
  IF EXISTS (
    SELECT 1 FROM public.tournament_room_registrations r
    WHERE r.room_id = p_room_id AND r.team_id = v_team_id AND r.status IN ('pending','approved')
  ) THEN
    RAISE EXCEPTION 'تیمت قبلاً در این روم ثبت‌نام کرده است.';
  END IF;
  IF EXISTS (
    SELECT 1 FROM public.tournament_room_registrations r
    WHERE r.room_id = p_room_id AND r.slot_number = p_slot_number AND r.status IN ('pending','approved')
  ) THEN
    RAISE EXCEPTION 'این جایگاه همین الان توسط شخص دیگری رزرو شد؛ یک جایگاه آزاد دیگر انتخاب کن.';
  END IF;
  v_slot := p_slot_number;
  INSERT INTO public.tournament_registrations(tournament_id, team_id, status)
  VALUES (v_room.tournament_id, v_team_id, 'pending')
  ON CONFLICT (tournament_id, team_id) DO NOTHING;
  INSERT INTO public.tournament_room_registrations(room_id, tournament_id, team_id, user_id, slot_number, status)
  VALUES (p_room_id, v_room.tournament_id, v_team_id, v_uid, v_slot, 'pending');
  IF v_slot >= v_max_teams THEN
    UPDATE public.tournament_rooms SET status = 'full', updated_at = now() WHERE id = p_room_id;
  END IF;
  RETURN jsonb_build_object('success', true, 'slot_number', v_slot, 'team_name', v_team.name, 'status', 'pending');
END;
$function$;

CREATE OR REPLACE FUNCTION public.register_for_tournament_room(p_room_id uuid, p_slot_number integer)
RETURNS jsonb
LANGUAGE sql
SET search_path TO ''
AS $function$
  SELECT private.register_for_tournament_room($1, $2);
$function$;

REVOKE ALL ON FUNCTION private.register_for_tournament_room(uuid, integer) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION private.register_for_tournament_room(uuid, integer) TO authenticated;
REVOKE ALL ON FUNCTION public.register_for_tournament_room(uuid, integer) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.register_for_tournament_room(uuid, integer) TO authenticated;

CREATE OR REPLACE FUNCTION private.get_tournament_room_slots(p_room_id uuid)
RETURNS TABLE(slot_number integer, team_name text, status text, players jsonb)
LANGUAGE sql
STABLE SECURITY DEFINER
SET search_path TO ''
AS $function$
  SELECT r.slot_number, t.name, r.status,
    COALESCE(
      (SELECT jsonb_agg(jsonb_build_object(
        'user_id', p.id, 'display_name', p.display_name, 'game_name', p.game_name,
        'game_uid', CASE WHEN p.game_uid IS NOT NULL AND strpos(p.game_uid, '@') = 0 THEN p.game_uid ELSE NULL END,
        'avatar_url', p.avatar_url
      ) ORDER BY q.created_at, q.id)
       FROM private.tournament_room_solo_queue q
       JOIN public.profiles p ON p.id = q.user_id
       WHERE q.team_id = r.team_id AND q.status = 'matched'),
      (SELECT jsonb_agg(jsonb_build_object(
        'user_id', p.id, 'display_name', p.display_name, 'game_name', p.game_name,
        'game_uid', CASE WHEN p.game_uid IS NOT NULL AND strpos(p.game_uid, '@') = 0 THEN p.game_uid ELSE NULL END,
        'avatar_url', p.avatar_url
      ) ORDER BY CASE WHEN tm.role = 'captain' THEN 0 ELSE 1 END, tm.joined_at)
       FROM public.team_members tm
       JOIN public.profiles p ON p.id = tm.user_id
       WHERE tm.team_id = r.team_id),
      '[]'::jsonb
    ) AS players
  FROM public.tournament_room_registrations r
  JOIN public.teams t ON t.id = r.team_id
  JOIN public.tournament_rooms room ON room.id = r.room_id
  JOIN public.tournaments tournament ON tournament.id = room.tournament_id
  WHERE r.room_id = p_room_id
    AND auth.uid() IS NOT NULL
    AND tournament.status IN ('published','registration_open','ongoing','completed')
    AND r.status IN ('pending','approved')
  ORDER BY r.slot_number;
$function$;

REVOKE ALL ON FUNCTION private.get_tournament_room_slots(uuid) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION private.get_tournament_room_slots(uuid) TO authenticated;
REVOKE ALL ON FUNCTION public.get_tournament_room_slots(uuid) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.get_tournament_room_slots(uuid) TO authenticated;
