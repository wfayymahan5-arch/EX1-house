# EX1 LEGEND room-result image analysis

This Supabase Edge Function is a guarded Gemini vision adapter. It extracts candidate fields from a batch of Call of Duty Mobile result screenshots and returns them with `status: review_required`.

It does **not** write official results, update profile statistics, change Stars/points balances, or publish results. Every extraction must be reviewed separately.

## Default safety state

The provider remains disabled unless all three server-side settings are present:

- `EX1_RESULTS_AI_ENABLED=true`
- `EX1_RESULTS_AI_DATA_TERMS_APPROVED=true`
- `GEMINI_API_KEY` (secret; never put this in client code or Git)

The function also needs the normal Supabase Edge Function environment values `SUPABASE_URL` and `SUPABASE_ANON_KEY` (or `SUPABASE_PUBLISHABLE_KEY`).

Optional settings:

- `GEMINI_RESULTS_MODEL=gemini-3.1-flash-lite`
- `EX1_RESULTS_ALLOWED_ORIGINS=https://your-preview-host.example` (comma-separated; production EX1 origin is already allowed)

The project owner must review Google's current Gemini API Free Tier terms and the privacy implications before approving screenshot transfer. The Free Tier pricing page indicates submitted content may be used to improve Google products. Do not enable processing of real player screenshots until the owner has reviewed the terms and arranged any required player notice/consent.

## Authorization and validation

- The Edge Function must keep platform JWT verification enabled.
- The caller must have a valid Supabase Auth session.
- The caller must have `results.manage` and either `rooms.view` or `rooms.manage`.
- The requested room must be visible to the caller and completed (or have an elapsed `ends_at`).
- Only JPEG, PNG, and WebP are accepted; maximum 8 images per request and 4 MiB per image.
- Duplicate images in one request are rejected by SHA-256 hash.
- The function never returns the Gemini provider's raw error body.
- The response is an untrusted review candidate and explicitly reports `official_write_performed: false`.

## Request

Send a signed-in user's JWT through the Authorization header and call the function with:

```json
{
  "room_id": "00000000-0000-0000-0000-000000000000",
  "images": [
    { "mime_type": "image/jpeg", "data_base64": "..." }
  ]
}
```

## Still required before end-to-end completion

1. Add a dedicated **private** Storage bucket for room result originals. Do not use the existing public `tournament-assets` bucket for these screenshots.
2. Add and verify Storage RLS rules that limit reads to participants of the exact room and authorized staff; upload must require `results.manage`.
3. Add a result-image manifest and review queue with room ID, object path, image hash, extraction payload, confidence, status, timestamps, and reviewer audit fields.
4. Add idempotent approval/rejection RPCs. Approval must verify room/user membership, match game UID before game name, prevent duplicate `(room_id,user_id)` results, and reject self-approval by the uploader.
5. Only approved rows may be written to `tournament_player_results` and included in profile performance statistics.
6. Add the admin/manager upload and review interface, and the participant-only room results gallery.
7. Add a bounded retry mechanism for transient provider failures. Do not add a paid scheduler or paid database branch.
8. Run Deno type/lint checks, migration validation, Supabase Security Advisor, and end-to-end tests with synthetic screenshots before any production deployment.

## Current implementation boundary

The current code only authenticates and authorizes the analysis request, checks room completion, validates image input, calls Gemini, hashes images, deduplicates identical UID rows, and returns a review-required candidate payload. It does not persist originals or extraction records. No database migration, Storage bucket, scheduler, secret, or deployment is created by this GitHub change.
