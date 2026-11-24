# Room result review pipeline — implementation design

Status: design only. This document does not create database objects, Storage buckets, policies, secrets, schedules, or deployments.

## Scope

The pipeline handles only non-monetary match evidence and the visible fields:
- Call of Duty Mobile UID
- placement
- kill count

It must remain disconnected from wallets, Stars balances, entry requirements, purchases, prizes, transfers, and payouts.

## Storage

Create a dedicated private bucket named `room-result-evidence`; never use the public `tournament-assets` bucket.

Object path convention:
`<room_uuid>/<batch_uuid>/<sha256>.<jpg|png|webp>`

Requirements:
- enforce a 4 MiB per-image limit and JPEG/PNG/WebP content-signature validation;
- keep the bucket private and issue short-lived signed URLs only after authorization;
- SELECT: approved registrants of the exact room and staff with an explicit result-view permission;
- INSERT: staff with `results.manage`, only under the matching room folder;
- no public, anonymous, or broad authenticated access;
- reject path traversal, malformed UUID folders, and mismatched room IDs.

## Private review records

Use tables in the non-exposed `private` schema (RLS enabled as defense in depth; no direct grants to anon/authenticated):

### `private.room_result_batches`
- `id uuid primary key`
- `room_id uuid not null references public.tournament_rooms(id)`
- `uploaded_by uuid not null references auth.users(id)`
- `status text not null` constrained to uploaded / processing / review_required / approved / rejected / failed
- `provider text`, `model text`
- `extraction jsonb not null default '{}'`
- `uncertainties jsonb not null default '[]'`
- `created_at timestamptz not null default now()`
- `reviewed_by uuid`, `reviewed_at timestamptz`, `reviewer_note text`

### `private.room_result_images`
- `id uuid primary key`, `batch_id uuid not null references private.room_result_batches(id)`
- `room_id uuid not null references public.tournament_rooms(id)`
- `bucket_id text not null default 'room-result-evidence'`
- `object_path text not null unique`
- `sha256 text not null`, `mime_type text not null`, `byte_size integer not null`
- `uploaded_by uuid not null references auth.users(id)`, `created_at timestamptz not null default now()`
- unique `(room_id, sha256)` to prevent re-uploading identical evidence for the same room.

### `private.room_result_candidates`
- `id uuid primary key`, `batch_id uuid not null references private.room_result_batches(id)`
- `game_uid text`, `placement integer`, `kills integer`
- `confidence text not null` constrained to high / medium / low
- `evidence_image_indexes jsonb not null default '[]'`
- `matched_user_id uuid`, `review_status text not null default 'pending'` constrained to pending / approved / rejected / needs_manual_match
- `created_at timestamptz not null default now()`
- Do not add a uniqueness constraint on UID at candidate level: conflicting screenshot readings must remain visible for manual review.

## Approval rules

Approval must be a single transaction in a guarded RPC:
1. Require a valid authenticated user and `results.manage`.
2. Lock the candidate and batch rows to prevent concurrent approvals.
3. Require the room to have ended.
4. Reject approval by the original uploader (`auth.uid() <> uploaded_by`).
5. Match by normalized COD UID against `profiles.game_uid`, and require the user to have an approved registration in this exact room.
6. If there are zero or multiple matching participants, require manual resolution; never guess.
7. Prevent duplicate official results with a unique constraint on `(room_id, user_id)` in `tournament_player_results` after checking for existing duplicates.
8. Insert only approved placement and kills. Do not invent a points formula; retain the existing default points value until a documented scoring rule is separately approved.
9. Record reviewer, timestamp, decision, and note. Rejection must never write to official results.
10. Keep the RPC in a non-exposed schema where possible. Any exposed SECURITY DEFINER wrapper must use a fixed search_path, explicitly check `auth.uid()`, validate permissions and room membership, and have EXECUTE revoked from PUBLIC/anon then granted only to authenticated.

## UI flow

- Admin/manager: select a completed room, upload original screenshots, see upload progress, then inspect a review queue.
- Review cards: show original image via authorized signed URL, extracted UID/placement/kills, confidence, uncertainty flags, and a participant match.
- Reviewer can edit uncertain values, manually match a UID only to a registered participant, approve, reject, or leave pending.
- The uploader cannot approve their own batch.
- Participant gallery: visible only to approved registrants of that exact room and authorized staff; show original evidence and approved results.
- The UI must clearly distinguish pending candidates from official approved results.

## Profile statistics

Only rows in `tournament_player_results` that were created by the approval transaction count as completed matches, wins, top-three finishes, and kills. Registration counts must not be labelled as completed matches. Avoid double-counting by using the unique room/user key.

## Required verification before activation

- Review current Google Gemini data terms and player notice/consent requirements.
- Create and verify the private bucket and Storage RLS policies.
- Apply schema changes only after a reviewed migration and a safe test environment are available.
- Test with synthetic screenshots, including malformed files, duplicate images, conflicting UIDs, missing UID, unregistered UID, self-approval, duplicate approval, and concurrent approval.
- Run Deno type/lint checks, database security advisors, and end-to-end tests.
- Deploy only after explicit owner authorization. The provider remains disabled by default.
