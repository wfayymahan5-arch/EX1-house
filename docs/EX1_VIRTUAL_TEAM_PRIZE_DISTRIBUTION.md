# EX1 LEGEND — Automatic Virtual Team Prize Distribution

Status: approved product requirement; implementation and live-database deployment are not yet verified.
Target branch: feature/native-parity-rebuild.

## Product rules

- EX1 Stars are virtual, in-app points only. They are not money, cannot be bought with real money, cannot be withdrawn, redeemed, transferred as cash, or converted into real-world value.
- The planned Stars shop is a separate future feature and is not part of this delivery.
- Preserve the existing virtual entry-fee behavior: the configured room entry cost is charged in Stars per player at registration, with insufficient balances rejected. Do not make registration free or remove the debit.
- Rooms may define virtual prize amounts for first, second, and third place.
- After the room result is finalized/confirmed by an authorized manager, distribute the configured prize for each awarded rank automatically to the individual virtual wallets of the members of the winning team.
- Target: all eligible member credits should be completed within 10 minutes of confirmed results. Prefer near-immediate processing; 10 minutes is the maximum target, not a delay.
- No real-money payment, cash prize, withdrawal, purchase, redemption, or external transfer is introduced.

## Allocation rule

For a rank prize total P and the frozen winning-team roster of N eligible members:
- Each member receives floor(P / N) Stars.
- The remaining P mod N Stars are assigned one each to members in a deterministic order (stable team-slot/member order captured at result confirmation).
- The sum of member credits must equal exactly P.
- Example: a 100-Star first-place prize for a four-player team credits 25 Stars to each member.
- Example: a 101-Star prize for four members credits 26, 25, 25, and 25 Stars in the stable roster order.
- If the team has one eligible member, that member receives the full prize.
- Do not infer membership from a later-edited team roster. Snapshot the actual room-team membership at the moment results are confirmed.

## Required server-side workflow

1. An authorized result-confirmation action records the final rank and the immutable roster snapshot for each awarded team.
2. A trusted server-side database transaction or protected server function creates one payout batch per room/rank/team and one ledger credit per eligible member.
3. The wallet balance update and corresponding immutable Stars-ledger entry must commit atomically. Never update a displayed balance without a ledger record.
4. Enforce a unique idempotency key scoped to room + rank + winning team + member (or an equivalent unique payout constraint). Repeated clicks, retries, worker restarts, and duplicate events must never credit a member twice.
5. Record payout status and timestamps, including pending, processing, completed, and failed/retryable states. Surface actionable errors to authorized managers.
6. Reconcile the sum of all member credits against the configured team prize before marking the batch completed.
7. A retry may process only missing/uncredited members; it must not repeat successful credits.
8. Use server-side authorization and row-level security. Never allow a player client or Native app to choose an arbitrary recipient or credit amount.
9. Notify each credited member and refresh their wallet balance/history. The ledger reason should identify the room, placement, and team prize.
10. If result confirmation is corrected or revoked, do not silently mutate historical ledger entries. Require an explicit audited reversal/correction workflow with compensating ledger entries and owner/authorized review.

## Acceptance tests

- Four-player team, 100 Stars: four credits of 25; total exactly 100.
- Four-player team, 101 Stars: deterministic 26/25/25/25; total exactly 101.
- Duo team and solo team: exact total allocation.
- First-, second-, and third-place prize amounts are independent and use the configured room values.
- No credit occurs before authorized result confirmation.
- Duplicate confirmation and concurrent retries do not double-credit.
- A partial failure can safely resume and complete within the 10-minute target under normal service availability.
- Wallet summary and history reflect each credit consistently.
- Unregistered users, non-members, and later-added team members receive nothing.
- Unauthorized managers cannot confirm results or trigger payouts.
- The same rules and visible status are implemented in WebView and Native; Native must call the trusted backend and must not calculate or mint credits locally.

## Existing implementation evidence reviewed

- Player wallet UI reads wallet_my_summary and wallet_my_history.
- Solo registration calls register_for_tournament_room_solo.
- Full-team registration calls register_for_tournament_room.
- The registration dialog displays the per-player virtual entry cost and checks the current virtual Stars balance.
- Room management exposes entry_demo_points, prize_demo_points, prize_second_demo_points, and prize_third_demo_points.
- Admin wallet adjustment uses the protected admin_wallet_demo_adjust RPC with a reason and idempotency key.
- This review did not establish that automatic team-prize payouts already exist. This document records the required behavior; it does not claim that the database functions, background processing, or UI have been implemented or deployed.
- supabase/schema.sql is explicitly marked archived/legacy and must not be used as the live schema source. Before implementation, inspect the actual current database migrations/functions and their authorization/RLS contracts. Do not modify the live database as part of this documentation change.