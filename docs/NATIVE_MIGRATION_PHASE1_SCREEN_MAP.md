# EX1 LEGEND — Phase 1 screen and behavior map

Branch: `feature/native-android-migration`
Inspection date: 2026-11-22
Purpose: turn the three HTML reference clients and the current Kotlin preview into a traceable screen-level migration checklist.
Scope: read-only source inspection and documentation only. No application code, database, main branch, or Production changes.

## Verified Android entry points

- `MainActivity.kt` creates a WebView, enables JavaScript/DOM storage, restricts navigation to the trusted EX1 hosts, loads the player or admin URL according to the product flavor, and supports a bundled HTML fallback.
- `MainActivity` remains the exported launcher in `AndroidManifest.xml`.
- `NativePreviewActivity.kt` remains non-exported and isolated from the launcher.
- `NativePreviewActivity.kt` currently declares preview composables for guest home, login, player, ranking, explore, public profile, profile, wallet preview, support, notifications, team, settings, room detail, room card, admin preview, metric card, permission-denied and generic admin data panel.
- The presence of a preview composable does not prove that it reproduces the corresponding HTML flow, loading/error states, permissions, or API semantics.

## Player client screen map (`app.html`)

Primary bottom navigation:
- `home` — home/player discovery
- `tournaments` — tournaments and room listings
- `ranking` — player/team rankings
- `profile` — profile and account area

Static page and panel roots observed:
- `page-home`, `home-explore`, `exploreUsers`
- `home-messages`, `chatContacts`, `chatTitle`, `chatMessages`, `chatForm`
- `page-profile`, `profileCover`, `profileAvatar`, `profileRoleBadge`, `profileUsername`
- `walletOpen`, `supportPanel`, `supportMessages`, `supportForm`
- `userNotificationsPanel`, `userNotifications`, `profileMatches`, `myMatches`
- `profileTeam`, `myTeam`, `createTeamWrap`, `createTeamForm`, `teamSettingsPanel`, `teamSettingsForm`, `teamInviteForm`
- `teamSentInvites`, `incomingTeamInvites`, `followRequests`
- `page-tournaments`, `roomCategoryFilter`, `roomCards`, `roomNoData`, `roomCategoryEmpty`, `roomDetail`
- `page-ranking`, `ranking`
- Modal/dialog roots: `authModal`, `profileEditorModal`, `publicPlayerProfileModal`, `settingsModal`, `walletModal`, `joinRoomModal`, `ex1ConfirmModal`
- Room detail subpanels: `roomAccessInline`, `roomTeamsPanel`, `roomSlotsMessage`, `roomSlotsList`, `roomWaitingPanel`, `roomWaitingPlayers`, `soloQueueStatus`, `roomResultsPanel`, `roomResultsContent`, `roomRulesPanel`, `roomRegisterBar`

Forms:
- `chatForm`, `supportForm`, `createTeamForm`, `teamSettingsForm`, `teamInviteForm`, `authForm`, `profileForm`.

Player source has 21 distinct `addEventListener` event types, including click, submit, change, input, keyboard, pointer, touch, visibility/page lifecycle, context menu and wheel handling. These must be traced individually; a Compose click handler alone is not equivalent to the full browser interaction model.

## Admin and manager screen map

Both `admin.html` and `manager.html` contain high-level panels with these IDs:
- `authView`, `loginForm`, `appView`, `identity`, `nav`
- `overview`, `overviewText`, `overviewEmpty`
- `tournaments`, `tournamentForm`, `tournamentsTable`
- `rooms`, `roomForm`, `roomImagePreviewWrap`, `roomImagePreview`, `roomAccessAdminPanel`, `roomAccessAdminStatus`, `roomsTable`
- `registrations`, `registrationsTable`, `teams`, `teamsTable`
- `users`, `directNotificationPanel`, `directNotificationForm`, `notificationUsersList`, `usersTable`
- `social`, `socialReportsTable`, `moderation`, `moderationCasesTable`
- `wallet`, `walletTable`, `walletAdjustPanel`, `walletAdjustForm`, `walletHistory`
- `settings`, `themeForm`, `themeMessage`, `toast`

Forms in each source:
- `loginForm`, `tournamentForm`, `roomForm`, `directNotificationForm`, `walletAdjustForm`, `themeForm`.

They are not interchangeable:
- Admin contains `rf_game_category`, `rf_price_category`, `rf_room_effects_enabled`, and `roomEffectsSchemaNotice`; these are absent from manager.
- Manager has a distinct title and permission/access presentation.
- Admin has 132 unique IDs, 53 inputs/selects/textareas and 10 style blocks; manager has 127, 50 and 9.
- Their JavaScript function counts and listener registrations differ.
- Their source order, styling and markup need separate comparison and separate test cases, even where panel IDs and backend operation names overlap.

## API and safety review boundary

The player HTML declares 17 direct RPC names, 7 Edge Function names and 22 `.from()` resource names. Admin and manager each declare 8 direct RPC names, 5 Edge Function names and 8 direct resource names. The existing Kotlin API class also contains methods whose names include wallet, room registration, room access, and admin data.

For this migration, do not newly connect or activate financial, paid-entry, prize, wallet-adjustment, or money-transfer operations. Those areas must remain non-operational in the native UI. Any existing callable API methods are not permission to wire them into a user flow. Safe read-only presentation or clearly disabled explanatory UI can be considered separately. Authentication, authorization, and server-side access boundaries must not be weakened.

## Current native preview coverage (declaration-level only)

Observed composables:
- `NativePreviewRoot`, `GuestNativeHome`, `LoginPanel`, `PlayerPreviewPanel`
- `RankingPreviewPanel`, `ExplorePreviewPanel`, `PublicProfileDialog`, `ProfilePreviewPanel`
- `WalletPreviewPanel`, `SupportPreviewPanel`, `NotificationsPreviewPanel`, `TeamPreviewPanel`
- `SettingsPreviewPanel`, `RoomDetailDialog`, `RoomPreviewCard`
- `AdminPreviewPanel`, `AdminMetricCard`, `AdminNoPermission`, `AdminDataPanel`

This list is not a parity assertion. In particular, generic admin data panels do not establish that every admin and manager form, moderation action, permission state, image workflow, room state, and validation path has been recreated.

## Phase 1 traceability tasks

- [ ] Trace every player page/modal root to the function(s) that open, close, refresh, and populate it.
- [ ] Trace all player event listeners to DOM targets, state transitions, API calls and error handling.
- [ ] Trace admin and manager tab construction, permission gates, form validation, and delegated event handling independently.
- [ ] Classify every `.from()` target as a database table/view or Storage bucket; map query verbs and filters.
- [ ] Trace all Edge Function request payloads and response/error states.
- [ ] Extract exact colors, typography, dimensions, spacing, gradients, shadows, assets, breakpoints and animation timing.
- [ ] Build a source-line-linked parity/test matrix before changing the launcher or describing the app as Native-complete.

## Verification status

- The Android preview workflow run `44940326297` for commit `3635cb76bf91531231e504e1da6dfd31918b4e86` completed successfully on 2026-11-21. This verifies only the configured CI workflow result, not device runtime, UI parity, or production readiness.
- No physical-device or emulator test was performed in this inspection.
- No launcher switch, merge, deployment, live database operation, or financial workflow activation was performed.
