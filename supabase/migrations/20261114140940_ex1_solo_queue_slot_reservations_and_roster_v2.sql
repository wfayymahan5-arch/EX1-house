
DO $backfill$
DECLARE v_room record; v_slot integer;
BEGIN
  FOR v_room IN
    SELECT DISTINCT q.room_id, LEAST(GREATEST(COALESCE(r.max_teams,25),1),25) AS max_teams
    FROM private.tournament_room_solo_queue q
    JOIN public.tournament_rooms r ON r.id=q.room_id
    WHERE q.status='waiting' AND q.slot_number IS NULL
    ORDER BY q.room_id
  LOOP
    SELECT s INTO v_slot FROM generate_series(1,v_room.max_teams) s
    WHERE NOT EXISTS (
      SELECT 1 FROM public.tournament_room_registrations rr
      WHERE rr.room_id=v_room.room_id AND rr.slot_number=s AND rr.status IN ('pending','approved')
    )
    AND NOT EXISTS (
      SELECT 1 FROM private.tournament_room_solo_queue q2
      WHERE q2.room_id=v_room.room_id AND q2.status='waiting' AND q2.slot_number=s
    )
    ORDER BY s LIMIT 1;
    IF v_slot IS NOT NULL THEN
      UPDATE private.tournament_room_solo_queue
      SET slot_number=v_slot
      WHERE room_id=v_room.room_id AND status='waiting' AND slot_number IS NULL;
    END IF;
  END LOOP;
END;
$backfill$;

CREATE OR REPLACE FUNCTION private.get_my_room_solo_status(p_room_id uuid)
 RETURNS jsonb
 LANGUAGE plpgsql
 STABLE SECURITY DEFINER
 SET search_path TO ''
AS $function$
DECLARE
  v_uid uuid:=auth.uid();
  v_row private.tournament_room_solo_queue%rowtype;
  v_room public.tournament_rooms%rowtype;
  v_team_size integer;
  v_waiting_count integer;
  v_team_name text;
BEGIN
  IF v_uid IS NULL THEN RAISE EXCEPTION 'ابتدا وارد حساب شو.'; END IF;
  SELECT * INTO v_room FROM public.tournament_rooms r WHERE r.id=p_room_id;
  IF NOT FOUND THEN RAISE EXCEPTION 'روم پیدا نشد.'; END IF;
  SELECT * INTO v_row FROM private.tournament_room_solo_queue q
  WHERE q.room_id=p_room_id AND q.user_id=v_uid
  ORDER BY q.created_at DESC LIMIT 1;
  IF NOT FOUND THEN RETURN jsonb_build_object('registered',false); END IF;

  v_team_size:=GREATEST(2,LEAST(COALESCE(v_room.team_size,4),4));
  IF v_row.status='waiting' AND (v_room.status<>'open' OR (v_room.starts_at IS NOT NULL AND v_room.starts_at<=now())) THEN
    UPDATE private.tournament_room_solo_queue q SET status='cancelled'
    WHERE q.id=v_row.id AND q.status='waiting';
    RETURN jsonb_build_object('registered',false,'status','cancelled');
  END IF;

  IF v_row.status='matched' THEN
    SELECT t.name INTO v_team_name FROM public.teams t WHERE t.id=v_row.team_id;
    RETURN jsonb_build_object('registered',true,'status','matched',
      'slot_number',v_row.slot_number,'team_name',v_team_name,'team_size',v_team_size);
  ELSIF v_row.status='waiting' THEN
    SELECT count(*) INTO v_waiting_count FROM private.tournament_room_solo_queue q
    WHERE q.room_id=p_room_id AND q.slot_number=v_row.slot_number AND q.status='waiting';
    RETURN jsonb_build_object('registered',true,'status','waiting','slot_number',v_row.slot_number,
      'players_waiting',v_waiting_count,'players_needed',GREATEST(0,v_team_size-v_waiting_count),
      'team_size',v_team_size);
  END IF;
  RETURN jsonb_build_object('registered',false,'status','cancelled');
END;
$function$


CREATE OR REPLACE FUNCTION private.get_tournament_room_slots(p_room_id uuid)
 RETURNS TABLE(slot_number integer, team_name text, status text, players jsonb)
 LANGUAGE sql
 STABLE SECURITY DEFINER
 SET search_path TO ''
AS $function$
  WITH registered AS (
    SELECT r.slot_number,t.name AS team_name,r.status,
      COALESCE(
        (SELECT jsonb_agg(jsonb_build_object(
          'user_id',p.id,'display_name',p.display_name,'game_name',p.game_name,
          'game_uid',CASE WHEN p.game_uid IS NOT NULL AND strpos(p.game_uid,'@')=0 THEN p.game_uid ELSE NULL END,
          'avatar_url',p.avatar_url
        ) ORDER BY q.created_at,q.id)
         FROM private.tournament_room_solo_queue q
         JOIN public.profiles p ON p.id=q.user_id
         WHERE q.team_id=r.team_id AND q.status='matched'),
        (SELECT jsonb_agg(jsonb_build_object(
          'user_id',p.id,'display_name',p.display_name,'game_name',p.game_name,
          'game_uid',CASE WHEN p.game_uid IS NOT NULL AND strpos(p.game_uid,'@')=0 THEN p.game_uid ELSE NULL END,
          'avatar_url',p.avatar_url
        ) ORDER BY CASE WHEN tm.role='captain' THEN 0 ELSE 1 END,tm.joined_at)
         FROM public.team_members tm
         JOIN public.profiles p ON p.id=tm.user_id
         WHERE tm.team_id=r.team_id),
        '[]'::jsonb
      ) AS players
    FROM public.tournament_room_registrations r
    JOIN public.teams t ON t.id=r.team_id
    JOIN public.tournament_rooms room ON room.id=r.room_id
    JOIN public.tournaments tournament ON tournament.id=room.tournament_id
    WHERE r.room_id=p_room_id
      AND r.status IN ('pending','approved')
      AND tournament.status IN ('published','registration_open','ongoing','completed')
  ),
  waiting AS (
    SELECT q.slot_number,'در حال تشکیل تیم'::text AS team_name,'waiting'::text AS status,
      jsonb_agg(jsonb_build_object(
        'user_id',p.id,'display_name',p.display_name,'game_name',p.game_name,
        'game_uid',CASE WHEN p.game_uid IS NOT NULL AND strpos(p.game_uid,'@')=0 THEN p.game_uid ELSE NULL END,
        'avatar_url',p.avatar_url
      ) ORDER BY q.created_at,q.id) AS players
    FROM private.tournament_room_solo_queue q
    JOIN public.profiles p ON p.id=q.user_id
    JOIN public.tournament_rooms room ON room.id=q.room_id
    JOIN public.tournaments tournament ON tournament.id=room.tournament_id
    WHERE q.room_id=p_room_id AND q.status='waiting' AND q.slot_number IS NOT NULL
      AND tournament.status IN ('published','registration_open','ongoing','completed')
    GROUP BY q.slot_number
  )
  SELECT r.slot_number,r.team_name,r.status,r.players
  FROM registered r
  WHERE auth.uid() IS NOT NULL
  UNION ALL
  SELECT w.slot_number,w.team_name,w.status,w.players
  FROM waiting w
  WHERE auth.uid() IS NOT NULL
    AND NOT EXISTS (SELECT 1 FROM registered r WHERE r.slot_number=w.slot_number)
  ORDER BY slot_number;
$function$


CREATE OR REPLACE FUNCTION private.get_tournament_room_waiting_players(p_room_id uuid)
 RETURNS jsonb
 LANGUAGE plpgsql
 STABLE SECURITY DEFINER
 SET search_path TO ''
AS $function$
DECLARE
  v_uid uuid:=auth.uid();
  v_tournament_status text;
  v_players jsonb;
BEGIN
  IF v_uid IS NULL THEN RAISE EXCEPTION 'برای مشاهده فهرست بازیکنان ابتدا وارد حساب شو.'; END IF;
  SELECT t.status INTO v_tournament_status
  FROM public.tournament_rooms r
  JOIN public.tournaments t ON t.id=r.tournament_id
  WHERE r.id=p_room_id;
  IF NOT FOUND THEN RAISE EXCEPTION 'روم پیدا نشد.'; END IF;
  IF v_tournament_status NOT IN ('published','registration_open','ongoing','completed') THEN
    RETURN '[]'::jsonb;
  END IF;

  SELECT COALESCE(jsonb_agg(jsonb_build_object(
    'user_id',p.id,'display_name',p.display_name,'game_name',p.game_name,
    'game_uid',CASE WHEN p.game_uid IS NOT NULL AND strpos(p.game_uid,'@')=0 THEN p.game_uid ELSE NULL END,
    'avatar_url',p.avatar_url,'slot_number',q.slot_number,'registered_at',q.created_at
  ) ORDER BY q.slot_number,q.created_at,q.id),'[]'::jsonb)
  INTO v_players
  FROM private.tournament_room_solo_queue q
  JOIN public.profiles p ON p.id=q.user_id
  WHERE q.room_id=p_room_id AND q.status='waiting';
  RETURN v_players;
END;
$function$


CREATE OR REPLACE FUNCTION private.register_for_tournament_room(p_room_id uuid)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
DECLARE
  v_room public.tournament_rooms%rowtype;
  v_slot integer;
  v_max_teams integer;
BEGIN
  SELECT * INTO v_room FROM public.tournament_rooms WHERE id=p_room_id FOR UPDATE;
  IF NOT FOUND THEN RAISE EXCEPTION 'روم پیدا نشد.'; END IF;
  v_max_teams:=LEAST(GREATEST(COALESCE(v_room.max_teams,1),1),25);
  SELECT s INTO v_slot
  FROM generate_series(1,v_max_teams) s
  WHERE NOT EXISTS (
    SELECT 1 FROM public.tournament_room_registrations r
    WHERE r.room_id=p_room_id AND r.slot_number=s AND r.status IN ('pending','approved')
  )
  AND NOT EXISTS (
    SELECT 1 FROM private.tournament_room_solo_queue q
    WHERE q.room_id=p_room_id AND q.slot_number=s AND q.status='waiting'
  )
  ORDER BY s LIMIT 1;
  IF v_slot IS NULL THEN RAISE EXCEPTION 'ظرفیت این روم تکمیل شده است.'; END IF;
  RETURN private.register_for_tournament_room(p_room_id,v_slot);
END;
$function$


CREATE OR REPLACE FUNCTION private.register_for_tournament_room(p_room_id uuid, p_slot_number integer)
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
  v_member_count integer;
  v_max_teams integer;
BEGIN
  IF v_uid IS NULL THEN RAISE EXCEPTION 'برای ثبت‌نام ابتدا وارد حساب شو.'; END IF;

  SELECT * INTO v_room FROM public.tournament_rooms WHERE id=p_room_id FOR UPDATE;
  IF NOT FOUND THEN RAISE EXCEPTION 'روم پیدا نشد.'; END IF;
  IF v_room.status<>'open' THEN RAISE EXCEPTION 'ثبت‌نام این روم باز نیست یا ظرفیت آن تکمیل شده است.'; END IF;
  IF v_room.starts_at IS NOT NULL AND v_room.starts_at<=now() THEN
    RAISE EXCEPTION 'مهلت ثبت‌نام این روم به پایان رسیده است.';
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM public.tournaments t
    WHERE t.id=v_room.tournament_id AND t.status IN ('published','registration_open')
  ) THEN RAISE EXCEPTION 'این مسابقه منتشر نشده یا امکان ثبت‌نام آن بسته شده است.'; END IF;

  v_max_teams:=LEAST(GREATEST(COALESCE(v_room.max_teams,1),1),25);
  IF p_slot_number IS NULL OR p_slot_number<1 OR p_slot_number>v_max_teams THEN
    RAISE EXCEPTION 'شماره جایگاه انتخاب‌شده معتبر نیست؛ دوباره یک جایگاه آزاد انتخاب کن.';
  END IF;

  IF EXISTS (
    SELECT 1 FROM private.tournament_room_solo_queue q
    WHERE q.room_id=p_room_id AND q.user_id=v_uid AND q.status IN ('waiting','matched')
  ) THEN RAISE EXCEPTION 'این حساب قبلاً به‌صورت انفرادی در همین روم ثبت‌نام کرده است.'; END IF;

  SELECT t.* INTO v_team
  FROM public.teams t
  JOIN public.team_members tm ON tm.team_id=t.id
  WHERE tm.user_id=v_uid AND t.captain_id=v_uid
  ORDER BY t.created_at
  LIMIT 1
  FOR UPDATE OF t;

  IF v_team.id IS NULL THEN
    RAISE EXCEPTION 'برای ثبت‌نام با تیم کامل باید کاپیتان یک تیم باشی.';
  END IF;
  v_team_id:=v_team.id;

  SELECT count(*) INTO v_member_count
  FROM public.team_members tm WHERE tm.team_id=v_team_id;
  IF v_member_count<>v_room.team_size THEN
    RAISE EXCEPTION 'این روم به تیم % نفره نیاز دارد؛ تیم تو اکنون % عضو دارد.',v_room.team_size,v_member_count;
  END IF;

  IF EXISTS (
    SELECT 1 FROM public.tournament_room_registrations r
    WHERE r.room_id=p_room_id AND r.team_id=v_team_id AND r.status IN ('pending','approved')
  ) THEN RAISE EXCEPTION 'تیمت قبلاً در این روم ثبت‌نام کرده است.'; END IF;

  IF EXISTS (
    SELECT 1 FROM public.tournament_room_registrations r
    WHERE r.room_id=p_room_id AND r.slot_number=p_slot_number AND r.status IN ('pending','approved')
  ) OR EXISTS (
    SELECT 1 FROM private.tournament_room_solo_queue q
    WHERE q.room_id=p_room_id AND q.slot_number=p_slot_number AND q.status='waiting'
  ) THEN
    RAISE EXCEPTION 'این جایگاه قبلاً رزرو شده؛ یک جایگاه آزاد دیگر انتخاب کن.';
  END IF;

  INSERT INTO public.tournament_registrations(tournament_id,team_id,status)
  VALUES(v_room.tournament_id,v_team_id,'pending')
  ON CONFLICT(tournament_id,team_id) DO NOTHING;

  INSERT INTO public.tournament_room_registrations(room_id,tournament_id,team_id,user_id,slot_number,status)
  VALUES(p_room_id,v_room.tournament_id,v_team_id,v_uid,p_slot_number,'pending');

  IF (
    SELECT count(*) FROM public.tournament_room_registrations r
    WHERE r.room_id=p_room_id AND r.status IN ('pending','approved')
  ) >= v_max_teams THEN
    UPDATE public.tournament_rooms SET status='full',updated_at=now() WHERE id=p_room_id;
  END IF;

  RETURN jsonb_build_object('success',true,'slot_number',p_slot_number,'team_name',v_team.name,'status','pending');
END;
$function$


CREATE OR REPLACE FUNCTION private.register_for_tournament_room_solo(p_room_id uuid)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
DECLARE
  v_uid uuid:=auth.uid();
  v_room public.tournament_rooms%rowtype;
  v_slot integer;
  v_max_teams integer;
BEGIN
  SELECT * INTO v_room FROM public.tournament_rooms WHERE id=p_room_id FOR UPDATE;
  IF NOT FOUND THEN RAISE EXCEPTION 'روم پیدا نشد.'; END IF;

  SELECT q.slot_number INTO v_slot
  FROM private.tournament_room_solo_queue q
  WHERE q.room_id=p_room_id AND q.user_id=v_uid AND q.status IN ('waiting','matched')
  ORDER BY q.created_at DESC LIMIT 1;

  IF v_slot IS NULL THEN
    v_max_teams:=LEAST(GREATEST(COALESCE(v_room.max_teams,1),1),25);
    SELECT s INTO v_slot
    FROM generate_series(1,v_max_teams) s
    WHERE NOT EXISTS (
      SELECT 1 FROM public.tournament_room_registrations r
      WHERE r.room_id=p_room_id AND r.slot_number=s AND r.status IN ('pending','approved')
    )
    AND NOT EXISTS (
      SELECT 1 FROM private.tournament_room_solo_queue q
      WHERE q.room_id=p_room_id AND q.slot_number=s AND q.status='waiting'
    )
    ORDER BY s LIMIT 1;
  END IF;

  IF v_slot IS NULL THEN RAISE EXCEPTION 'ظرفیت این روم تکمیل شده است؛ یک جایگاه در حال تشکیل تیم را انتخاب کن.'; END IF;
  RETURN private.register_for_tournament_room_solo(p_room_id,v_slot);
END;
$function$


CREATE OR REPLACE FUNCTION private.register_for_tournament_room_solo(p_room_id uuid, p_slot_number integer)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
DECLARE
  v_uid uuid:=auth.uid();
  v_room public.tournament_rooms%rowtype;
  v_existing private.tournament_room_solo_queue%rowtype;
  v_team_size integer;
  v_max_teams integer;
  v_waiting_count integer;
  v_group_ids uuid[];
  v_team_id uuid;
  v_team_name text;
  v_result jsonb;
BEGIN
  IF v_uid IS NULL THEN RAISE EXCEPTION 'برای ثبت‌نام ابتدا وارد حساب EX1 شو.'; END IF;

  SELECT * INTO v_room FROM public.tournament_rooms WHERE id=p_room_id FOR UPDATE;
  IF NOT FOUND THEN RAISE EXCEPTION 'روم پیدا نشد.'; END IF;

  v_team_size:=GREATEST(2,LEAST(COALESCE(v_room.team_size,4),4));
  v_max_teams:=LEAST(GREATEST(COALESCE(v_room.max_teams,1),1),25);

  SELECT * INTO v_existing
  FROM private.tournament_room_solo_queue q
  WHERE q.room_id=p_room_id AND q.user_id=v_uid
  ORDER BY q.created_at DESC
  LIMIT 1;

  IF FOUND THEN
    IF v_existing.status='matched' THEN
      SELECT t.name INTO v_team_name FROM public.teams t WHERE t.id=v_existing.team_id;
      RETURN jsonb_build_object('status','matched','slot_number',v_existing.slot_number,
        'team_name',v_team_name,'team_size',v_team_size,'players_waiting',0,'players_needed',0);
    ELSIF v_existing.status='waiting' THEN
      IF v_room.status<>'open' OR (v_room.starts_at IS NOT NULL AND v_room.starts_at<=now()) THEN
        UPDATE private.tournament_room_solo_queue q SET status='cancelled' WHERE q.id=v_existing.id;
        RETURN jsonb_build_object('status','cancelled','registered',false);
      END IF;
      IF v_existing.slot_number IS DISTINCT FROM p_slot_number THEN
        RAISE EXCEPTION 'قبلاً در جایگاه % ثبت‌نام کرده‌ای؛ برای ادامه همان جایگاه را انتخاب کن.',
          COALESCE(v_existing.slot_number,1);
      END IF;
      SELECT count(*) INTO v_waiting_count
      FROM private.tournament_room_solo_queue q
      WHERE q.room_id=p_room_id AND q.slot_number=v_existing.slot_number AND q.status='waiting';
      RETURN jsonb_build_object('status','waiting','slot_number',v_existing.slot_number,
        'team_size',v_team_size,'players_waiting',v_waiting_count,
        'players_needed',GREATEST(0,v_team_size-v_waiting_count));
    ELSE
      RAISE EXCEPTION 'ثبت‌نام قبلی این حساب لغو شده است؛ با پشتیبانی روم تماس بگیر.';
    END IF;
  END IF;

  IF v_room.status<>'open' THEN RAISE EXCEPTION 'ثبت‌نام این روم باز نیست یا ظرفیت آن تکمیل شده است.'; END IF;
  IF v_room.starts_at IS NOT NULL AND v_room.starts_at<=now() THEN
    RAISE EXCEPTION 'مهلت ثبت‌نام این روم به پایان رسیده است.';
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM public.tournaments t
    WHERE t.id=v_room.tournament_id AND t.status IN ('published','registration_open')
  ) THEN RAISE EXCEPTION 'این مسابقه منتشر نشده یا ثبت‌نام آن بسته شده است.'; END IF;

  IF p_slot_number IS NULL OR p_slot_number<1 OR p_slot_number>v_max_teams THEN
    RAISE EXCEPTION 'شماره جایگاه انتخاب‌شده معتبر نیست.';
  END IF;

  IF EXISTS (
    SELECT 1 FROM public.team_members tm
    JOIN public.tournament_room_registrations r ON r.team_id=tm.team_id
    WHERE tm.user_id=v_uid AND r.room_id=p_room_id AND r.status IN ('pending','approved')
  ) OR EXISTS (
    SELECT 1 FROM public.tournament_room_registrations r
    WHERE r.room_id=p_room_id AND r.user_id=v_uid AND r.status IN ('pending','approved')
  ) THEN
    RAISE EXCEPTION 'این حساب قبلاً با یک تیم در همین روم ثبت‌نام شده است.';
  END IF;

  IF EXISTS (
    SELECT 1 FROM public.tournament_room_registrations r
    WHERE r.room_id=p_room_id AND r.slot_number=p_slot_number AND r.status IN ('pending','approved')
  ) THEN RAISE EXCEPTION 'این جایگاه قبلاً تکمیل شده است؛ جایگاه دیگری انتخاب کن.'; END IF;

  SELECT count(*) INTO v_waiting_count
  FROM private.tournament_room_solo_queue q
  WHERE q.room_id=p_room_id AND q.slot_number=p_slot_number AND q.status='waiting';
  IF v_waiting_count>=v_team_size THEN RAISE EXCEPTION 'این جایگاه در حال تکمیل نهایی است؛ دوباره تلاش کن.'; END IF;

  INSERT INTO private.tournament_room_solo_queue(room_id,user_id,status,slot_number)
  VALUES(p_room_id,v_uid,'waiting',p_slot_number)
  ON CONFLICT(room_id,user_id) DO NOTHING;

  IF NOT FOUND THEN RAISE EXCEPTION 'این حساب قبلاً برای همین روم ثبت‌نام کرده است.'; END IF;

  SELECT array_agg(x.user_id ORDER BY x.created_at,x.id) INTO v_group_ids
  FROM (
    SELECT q.user_id,q.created_at,q.id
    FROM private.tournament_room_solo_queue q
    WHERE q.room_id=p_room_id AND q.slot_number=p_slot_number AND q.status='waiting'
    ORDER BY q.created_at,q.id
    LIMIT v_team_size
    FOR UPDATE
  ) x;

  IF COALESCE(array_length(v_group_ids,1),0)=v_team_size THEN
    v_team_name:='EX1 Solo '||upper(substr(replace(gen_random_uuid()::text,'-',''),1,6));
    INSERT INTO public.teams(name,tag,captain_id,description)
    VALUES(v_team_name,NULL,NULL,'تیم خودکار تشکیل‌شده از ثبت‌نام انفرادی EX1')
    RETURNING id INTO v_team_id;

    INSERT INTO public.tournament_registrations(tournament_id,team_id,status)
    VALUES(v_room.tournament_id,v_team_id,'pending');

    INSERT INTO public.tournament_room_registrations(room_id,tournament_id,team_id,user_id,slot_number,status)
    VALUES(p_room_id,v_room.tournament_id,v_team_id,v_group_ids[1],p_slot_number,'pending');

    UPDATE private.tournament_room_solo_queue
    SET status='matched',team_id=v_team_id,slot_number=p_slot_number,matched_at=now()
    WHERE room_id=p_room_id AND user_id=ANY(v_group_ids) AND status='waiting';

    IF (
      SELECT count(*) FROM public.tournament_room_registrations r
      WHERE r.room_id=p_room_id AND r.status IN ('pending','approved')
    )>=v_max_teams THEN
      UPDATE public.tournament_rooms SET status='full',updated_at=now() WHERE id=p_room_id;
    END IF;

    SELECT jsonb_build_object('status','matched','slot_number',p_slot_number,
      'team_name',v_team_name,'team_size',v_team_size,'players_waiting',0,'players_needed',0)
    INTO v_result;
    RETURN v_result;
  END IF;

  SELECT count(*) INTO v_waiting_count
  FROM private.tournament_room_solo_queue q
  WHERE q.room_id=p_room_id AND q.slot_number=p_slot_number AND q.status='waiting';

  RETURN jsonb_build_object('status','waiting','slot_number',p_slot_number,
    'team_size',v_team_size,'players_waiting',v_waiting_count,
    'players_needed',GREATEST(0,v_team_size-v_waiting_count));
END;
$function$


CREATE OR REPLACE FUNCTION public.register_for_tournament_room(p_room_id uuid, p_slot_number integer)
 RETURNS jsonb
 LANGUAGE sql
 SET search_path TO ''
AS $function$
  SELECT private.register_for_tournament_room($1, $2);
$function$


CREATE OR REPLACE FUNCTION public.register_for_tournament_room_solo(p_room_id uuid, p_slot_number integer)
 RETURNS jsonb
 LANGUAGE sql
 SET search_path TO ''
AS $function$
  SELECT private.register_for_tournament_room_solo($1,$2);
$function$


REVOKE ALL ON FUNCTION private.register_for_tournament_room(uuid,integer) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION private.register_for_tournament_room(uuid,integer) TO authenticated;
REVOKE ALL ON FUNCTION private.register_for_tournament_room(uuid) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION private.register_for_tournament_room(uuid) TO authenticated;
REVOKE ALL ON FUNCTION public.register_for_tournament_room(uuid,integer) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.register_for_tournament_room(uuid,integer) TO authenticated;
REVOKE ALL ON FUNCTION private.register_for_tournament_room_solo(uuid,integer) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION private.register_for_tournament_room_solo(uuid,integer) TO authenticated;
REVOKE ALL ON FUNCTION private.register_for_tournament_room_solo(uuid) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION private.register_for_tournament_room_solo(uuid) TO authenticated;
REVOKE ALL ON FUNCTION public.register_for_tournament_room_solo(uuid,integer) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.register_for_tournament_room_solo(uuid,integer) TO authenticated;
REVOKE ALL ON FUNCTION private.get_my_room_solo_status(uuid) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION private.get_my_room_solo_status(uuid) TO authenticated;
REVOKE ALL ON FUNCTION private.get_tournament_room_slots(uuid) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION private.get_tournament_room_slots(uuid) TO authenticated;
REVOKE ALL ON FUNCTION public.get_tournament_room_slots(uuid) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.get_tournament_room_slots(uuid) TO authenticated;
REVOKE ALL ON FUNCTION private.get_tournament_room_waiting_players(uuid) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION private.get_tournament_room_waiting_players(uuid) TO authenticated;
REVOKE ALL ON FUNCTION public.get_tournament_room_waiting_players(uuid) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.get_tournament_room_waiting_players(uuid) TO authenticated;
