# EX1 LEGEND room-result image analysis

This Edge Function is a guarded Gemini vision adapter. It extracts candidate fields from a batch of result screenshots and returns them with `status: review_required`. It does **not** write to `tournament_player_results`, link game identities to EX1 accounts, or publish official statistics.

## Default safety state

The provider is disabled unless all of these server-side secrets are set:

- `EX1_RESULTS_AI_ENABLED=true`
- `EX1_RESULTS_AI_DATA_TERMS_APPROVED=true`
- `GEMINI_API_KEY` (secret; never place it in client code)
- `EX1_RESULTS_WORKER_SECRET` (secret shared only with the trusted backend worker)

Optional:

- `GEMINI_RESULTS_MODEL=gemini-3.5-flash-lite`

The project owner must review Google's current Gemini API Free Tier terms and approve sending result screenshots to Google before enabling the data-terms flag. Google's Free Tier documentation says submitted content may be used to improve Google products. Do not enable this for real player screenshots until the owner has reviewed the privacy implications and obtained any required notice/consent.

## Request

The caller must use a valid Supabase JWT (the platform's default Edge Function JWT verification) and the private `x-ex1-results-worker` header. The body is:

```json
{
  "room_id": "00000000-0000-0000-0000-000000000000",
  "images": [
    { "mime_type": "image/jpeg", "data_base64": "..." }
  ]
}
```

Limits: 8 images per request, 4 MiB per image; JPEG, PNG, and WebP only.

## Response and downstream requirements

Successful responses are marked `review_required` and explicitly include `official_write_performed: false`. Treat all extracted fields as untrusted candidates. Before official results can be saved, a separate workflow still needs to:

1. verify that the room is completed and the caller is authorized for that room;
2. deduplicate overlapping screenshots and repeated player rows;
3. match game UID first, then game name only as a secondary signal;
4. route low-confidence, unknown, and conflicting matches to human review;
5. require owner approval before official result writes and profile-stat updates;
6. store originals in a private room-scoped bucket and enforce participant-only read access.

No database migration, bucket, scheduler, or deployment is created by this adapter alone.
