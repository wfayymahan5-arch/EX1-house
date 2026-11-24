# Room results pipeline — live project status

Last verified: 2026-11-24. Scope is limited to non-cash match evidence and official score records. This feature must not connect to wallets, Stars balances, paid entry, prizes, or payouts.

## Applied to Supabase project `wkyzhqygmppdppvfqtgf`

The following migrations are recorded in the project's migration history:

- `20261124163703_ex1_private_room_result_review_pipeline_v1`
- `20261124163822_ex1_room_result_batch_intake_and_storage_read_v1`
- `20261124163855_ex1_room_result_reject_manual_match_fix_v1`

Changes:
- Private Storage bucket `room-result-evidence`, public=false, max 4 MiB, JPEG/PNG/WebP only.
- Storage upload restricted to staff with `results.manage`, under a completed room's UUID folder.
- Storage read restricted to staff with result-view/manage permission or approved participants of that exact room.
- Storage delete restricted to staff with `results.manage`.
- Private tables for result batches, images, extracted candidates, and immutable review audit events. RLS is enabled and direct table privileges are revoked from anon/authenticated.
- Unique official result key on `(room_id,user_id)`.
- Authenticated, permission-checked RPCs for batch intake, candidate approval, and rejection.
- Approval requires a different reviewer from the uploader, a completed room, an exact and globally unique COD UID match, and an approved registration in that room. It writes only placement/kills with points=0 and creates an audit event.
- Rejected and uncertain candidates remain out of official profile stats.

## Verification completed

- Bucket exists and is private with the intended size and MIME restrictions.
- Storage policies exist for SELECT, INSERT, and DELETE.
- The four private result tables exist with RLS enabled.
- The official results table remains empty; no test or real player result was inserted.
- The analysis Edge Function is still not deployed and its Gemini integration remains disabled by default.
- No Storage objects were uploaded.

## Still not complete

- Admin/manager upload and review UI.
- Room participant results gallery and authorized signed-URL display.
- Connecting the vision function response to the batch-intake RPC.
- Deno type/lint and end-to-end tests using synthetic screenshots.
- Google Gemini data-term/notice review by the adult project owner before any real player screenshot is sent.
- A repository migration-source file matching the applied database migration history must be checked in before merging this feature.

No Production deployment or GitHub merge was performed.
