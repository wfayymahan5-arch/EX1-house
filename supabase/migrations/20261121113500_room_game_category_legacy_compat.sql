ALTER TABLE public.tournament_rooms
  ALTER COLUMN game_category DROP DEFAULT;

CREATE OR REPLACE FUNCTION public.ex1_normalize_room_game_category()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = pg_catalog, public
AS $$
BEGIN
  -- Older admin builds do not submit game_category. Infer only when the
  -- new field is absent or the legacy row is still explicitly uncategorized.
  IF NEW.game_category IS NULL OR NEW.game_category = 'uncategorized' THEN
    IF lower(coalesce(NEW.game_mode,'')) ~ '(fight|1v1|solo|فایت|تک)' THEN
      NEW.game_category := 'fight';
    ELSIF lower(coalesce(NEW.game_mode,'')) ~ '(multi|mp|مولتی)' THEN
      NEW.game_category := 'multiplayer';
    ELSIF lower(coalesce(NEW.game_mode,'')) ~ '(battle|br|بتل|رویال)' THEN
      NEW.game_category := 'battle_royale';
    ELSE
      NEW.game_category := 'uncategorized';
    END IF;
  END IF;
  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_ex1_normalize_room_game_category
  ON public.tournament_rooms;

CREATE TRIGGER trg_ex1_normalize_room_game_category
BEFORE INSERT OR UPDATE OF game_mode, game_category
ON public.tournament_rooms
FOR EACH ROW
EXECUTE FUNCTION public.ex1_normalize_room_game_category();
