DO $migration$
DECLARE
  v_definition text;
  v_old_branch text := $old$
    ELSE
      RAISE EXCEPTION 'ثبت‌نام قبلی این حساب لغو شده است؛ با پشتیبانی روم تماس بگیر.';
    END IF;
$old$;
  v_new_branch text := $new$
    ELSIF v_existing.status='cancelled' THEN
      -- Reuse the cancelled row after the normal room and capacity validations below.
      NULL;
    ELSE
      RAISE EXCEPTION 'وضعیت ثبت‌نام قبلی معتبر نیست؛ با پشتیبانی روم تماس بگیر.';
    END IF;
$new$;
  v_old_insert text := $oldinsert$
  INSERT INTO private.tournament_room_solo_queue(room_id,user_id,status,slot_number)
  VALUES(p_room_id,v_uid,'waiting',p_slot_number)
  ON CONFLICT(room_id,user_id) DO NOTHING;

  IF NOT FOUND THEN RAISE EXCEPTION 'این حساب قبلاً برای همین روم ثبت‌نام کرده است.'; END IF;
$oldinsert$;
  v_new_insert text := $newinsert$
  IF v_existing.id IS NOT NULL AND v_existing.status='cancelled' THEN
    UPDATE private.tournament_room_solo_queue
    SET status='waiting', slot_number=p_slot_number, team_id=NULL,
        matched_at=NULL, created_at=now()
    WHERE id=v_existing.id AND status='cancelled';
    IF NOT FOUND THEN RAISE EXCEPTION 'وضعیت ثبت‌نام تغییر کرده است؛ دوباره تلاش کن.'; END IF;
  ELSE
    INSERT INTO private.tournament_room_solo_queue(room_id,user_id,status,slot_number)
    VALUES(p_room_id,v_uid,'waiting',p_slot_number)
    ON CONFLICT(room_id,user_id) DO NOTHING;
    IF NOT FOUND THEN RAISE EXCEPTION 'این حساب قبلاً برای همین روم ثبت‌نام کرده است.'; END IF;
  END IF;
$newinsert$;
BEGIN
  SELECT pg_get_functiondef(p.oid) INTO v_definition
  FROM pg_proc p
  JOIN pg_namespace n ON n.oid=p.pronamespace
  WHERE n.nspname='private'
    AND p.proname='register_for_tournament_room_solo'
    AND pg_get_function_identity_arguments(p.oid)='p_room_id uuid, p_slot_number integer';

  IF v_definition IS NULL THEN
    RAISE EXCEPTION 'Target function was not found.';
  END IF;
  IF position(v_old_branch in v_definition)=0 OR position(v_old_insert in v_definition)=0 THEN
    RAISE EXCEPTION 'Expected source fragments were not found; migration aborted without changing the function.';
  END IF;

  v_definition := replace(v_definition, v_old_branch, v_new_branch);
  v_definition := replace(v_definition, v_old_insert, v_new_insert);
  EXECUTE v_definition;
END
$migration$;
