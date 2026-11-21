CREATE OR REPLACE FUNCTION public.ex1_normalize_room_price_category()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = pg_catalog, public
AS $$
BEGIN
  -- Keep older admin builds compatible: they do not send price_category.
  -- A zero entry is always free; a positive entry with a legacy/default
  -- free category becomes economy unless the new admin explicitly chose
  -- economy or expensive.
  IF NEW.entry_demo_points = 0 THEN
    NEW.price_category := 'free';
  ELSIF NEW.price_category IS NULL OR NEW.price_category = 'free' THEN
    NEW.price_category := 'economy';
  END IF;
  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_ex1_normalize_room_price_category
  ON public.tournament_rooms;

CREATE TRIGGER trg_ex1_normalize_room_price_category
BEFORE INSERT OR UPDATE OF entry_demo_points, price_category
ON public.tournament_rooms
FOR EACH ROW
EXECUTE FUNCTION public.ex1_normalize_room_price_category();
