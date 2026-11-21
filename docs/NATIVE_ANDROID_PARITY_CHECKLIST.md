# EX1 LEGEND — Native parity and regression acceptance checklist

This checklist consolidates previously approved product decisions for the Native Android migration. It is a preservation checklist, not authorization to change the live backend.

## Release and data safety gates
- [ ] Work only on `feature/native-android-migration` until the user separately approves a merge.
- [ ] Never modify `main`, Production deployments, Production data, or live Supabase schema as part of the native UI migration.
- [ ] Keep the current web client and current APK workflow available as rollback until native parity and device acceptance are complete.
- [ ] Do not claim a feature is complete until code review, automated tests, build, and the appropriate real-device/server test have passed.
- [ ] Do not commit secrets, service-role keys, private keys, signing files, passwords, or financial PINs.

## Non-negotiable virtual-points boundary
- [ ] EX1 uses virtual points only. They have no conversion to real money, withdrawal, external transfer, sale, or cash value.
- [ ] Do not change wallet schema, balance calculations, ledger, point deductions, rewards, entry costs, prize allocation, wallet RPCs, or their transactional/authorization behavior.
- [ ] Do not add payment gateways, cash-out, real-money wagering, betting, or external value transfer.
- [ ] Native wallet views must call the existing authorized read APIs and display their returned values; any existing owner-authorized virtual-point administration must preserve its exact server-side permission checks and behavior.
- [ ] Revalidate authoritative server state before any room registration or other write; never trust cached wallet or capacity data for writes.

## Player identity, profile, and account
- [ ] Sign-in, sign-up, session refresh, persisted sessions, and sign-out.
- [ ] Store native session material encrypted with Android Keystore (AES-GCM); never store raw access/refresh tokens in plaintext preferences or logs.
- [ ] Profile read/update, username, display name, game UID/name, avatar and cover image flows, with existing moderation and storage rules preserved.
- [ ] Public player profiles from Explore: open a player profile, display public name/username/game name and approved biography; never expose game UID or private account data.
- [ ] Biography is optional, limited to 500 characters, editable only by its owner, and saved through the authenticated `update-profile` Edge Function moderation flow. Flagged text remains unpublished pending owner review.
- [ ] Account deletion must require explicit confirmation and the existing server-side reauthentication/authorization safeguards.
- [ ] Sign-out must show the agreed confirmation prompt before clearing the session.
- [ ] Do not expose one user's private profile, avatar-review media, or account details to another user.

## Player rooms and tournaments
- [ ] Preserve the independent game categories: Battle Royale, Fight, Multiplayer, and Uncategorised for legacy records.
- [ ] Within each game category, preserve separate Free, Economy, and Expensive entry tiers; horizontal room cards within each tier.
- [ ] Admin room creation/editing must keep game category and entry tier as independent fields; legacy room fallback remains understandable.
- [ ] Preserve start/end scheduling, room status, team size, capacity, max teams, results, placement, kills, and room retention/visibility rules.
- [ ] Preserve solo and team registration, slot selection, waiting queue, cancellation/re-entry, duplicate-registration protection, and server-side capacity enforcement.
- [ ] Preserve team creation, invites, accept/decline, cancel invite, leave team, remove member, and team deletion flows.
- [ ] Preserve room details, slot roster, solo waiting status, and result visibility.
- [ ] Private game room code/password must be visible only to approved registered participants according to the existing server authorization rules; provide copy controls without weakening access checks.
- [ ] Keep room sorting and card-state behavior correct: upcoming/open rooms first according to existing product rules, completed rooms visually inactive/grayscale and not spuriously highlighted on tap.
- [ ] Preserve image/effect behavior and the agreed room-card appearance without introducing heavy animations or excessive GPU effects.

## Notifications and communication
- [ ] Preserve user notifications, read/unread state, and deletion of only the current user's permitted notifications.
- [ ] Preserve room-start/reminder notifications and admin notifications according to existing settings and permissions.
- [ ] Preserve social conversations, members, messages, follows, blocks, and reports.
- [ ] Preserve support conversations/messages and manager access restrictions.

## Administration and authorization
- [ ] Preserve owner, general admin, tournament manager, moderator, and support roles.
- [ ] Preserve fine-grained RBAC: permissions are configurable per individual manager, including two managers with the same role title but different grants.
- [ ] The owner has full control. Manager access and assignment controls remain owner-only where currently specified.
- [ ] The owner and the owner's father share the single owner account; do not create a second owner account. Financial-area access uses its separate owner-provided security PIN and must never be embedded in app code or logs.
- [ ] Every admin screen and action must be permission-gated on the server as well as in the UI; hiding a button is not authorization.
- [ ] Preserve room creation/editing, scheduling, access publication, registrations, team/slot views, results, user management, moderation, support, theme settings, audit views, and permitted virtual-points admin views.
- [ ] Before publishing room credentials, preserve the existing requirement to enter the game room code/password in the admin panel and publish only to the approved participants.

## Moderation and safety
- [ ] Suspicious user-generated content is hidden from public display pending review.
- [ ] A moderation case records reason and evidence for owner review.
- [ ] Bans require owner approval; send a warning before applying a ban, and re-report repeated violations.
- [ ] Preserve current moderation workflows for text, profile/avatar/cover images, social messages, and support content.
- [ ] Do not weaken RLS, RPC grants, edge-function authorization, storage policies, or audit protections during migration.

## Visual and performance requirements
- [ ] Native Persian RTL layout, consistent typography, legible controls, responsive sizing, and accessible touch targets.
- [ ] Native navigation, dialogs, forms, lazy lists, and state handling; no WebView-hosted website pages as the Android UI at final acceptance.
- [ ] App shell and navigation should render immediately; fetch backend data asynchronously.
- [ ] Deduplicate network requests, paginate long lists, optimize image decoding/caching, and use explicit cache freshness rules.
- [ ] Distinguish cached/stale information from live server state. Refresh critical room capacity, registration, permissions, and access before writes.
- [ ] Avoid repeated loading spinners, layout jumps, blocking main-thread work, excessive animations, and unnecessary image effects.
- [ ] Profile, home, room list/detail, team, notification, social, support, and admin screens must be tested on a lower-memory Android device, including the user's Galaxy A06 4 GB target class.

## Migration test gates
- [ ] Unit tests for API serialization, error handling, auth/session refresh, category mapping, and cache expiry.
- [ ] Contract tests for every existing RPC used by the native clients; preserve argument names and response interpretation.
- [ ] UI tests for login/logout, profile, room category filters, registration states, team workflows, notifications, and admin permission visibility.
- [ ] Security tests for unauthenticated access, cross-user data isolation, private room credentials, owner-only controls, and moderation approval.
- [ ] Regression tests for duplicate charges/registrations and server-authoritative wallet/capacity state, without changing wallet business logic.
- [ ] Build both Player and Admin debug APKs and verify package IDs, icons, version metadata, and artifact hashes.
- [ ] Real-device test both APKs on Android 14 / Galaxy A06-class hardware before declaring acceptance.
- [ ] Only after all gates pass, request explicit approval for any merge or Production release.
