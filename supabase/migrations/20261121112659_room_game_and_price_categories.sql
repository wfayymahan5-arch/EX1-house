-- Independent room placement dimensions for EX1 LEGEND.
-- Existing room records are conservatively mapped from known game-mode text;
-- unrecognizable legacy values remain visible in the uncategorized section.
ALTER TABLE public.tournament_rooms
  ADD COLUMN IF NOT EXISTS game_category text,
  ADD COLUMN IF NOT EXISTS price_category text;

UPDATE public.tournament_rooms
SET game_category = CASE
  WHEN lower(coalesce(game_mode,'')) ~ '(fight|1v1|solo|فایت|تک)' THEN 'fight'
  WHEN lower(coalesce(game_mode,'')) ~ '(multi|mp|مولتی)' THEN 'multiplayer'
  WHEN lower(coalesce(game_mode,'')) ~ '(battle|br|بتل|رویال)' THEN 'battle_royale'
  ELSE 'uncategorized'
END
WHERE game_category IS NULL;

UPDATE public.tournament_rooms
SET price_category = CASE
  WHEN coalesce(entry_demo_points,0)=0 THEN 'free'
  ELSE 'economy'
END
WHERE price_category IS NULL;

ALTER TABLE public.tournament_rooms
  ALTER COLUMN game_category SET DEFAULT 'battle_royale',
  ALTER COLUMN game_category SET NOT NULL,
  ALTER COLUMN price_category SET DEFAULT 'free',
  ALTER COLUMN price_category SET NOT NULL;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint
    WHERE conrelid='public.tournament_rooms'::regclass
      AND conname='tournament_rooms_game_category_check'
  ) THEN
    ALTER TABLE public.tournament_rooms
      ADD CONSTRAINT tournament_rooms_game_category_check
      CHECK (game_category IN ('battle_royale','fight','multiplayer','uncategorized'));
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint
    WHERE conrelid='public.tournament_rooms'::regclass
      AND conname='tournament_rooms_price_category_check'
  ) THEN
    ALTER TABLE public.tournament_rooms
      ADD CONSTRAINT tournament_rooms_price_category_check
      CHECK (
        (price_category='free' AND entry_demo_points=0)
        OR
        (price_category IN ('economy','expensive') AND entry_demo_points>0)
      );
  END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_tournament_rooms_categories_created
  ON public.tournament_rooms (game_category, price_category, created_at DESC);
