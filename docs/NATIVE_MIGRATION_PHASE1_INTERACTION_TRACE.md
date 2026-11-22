# EX1 LEGEND — Phase 1 interaction and permission trace

Branch: `feature/native-android-migration`
Inspection date: 2026-11-22
Status: source-level audit; not a Native parity claim.

## Player (`app.html`) — verified interaction families

### Navigation and guest/auth behavior
- Bottom navigation uses `.bottom-nav [data-page]` and `switchPage(page)`.
- Home sub-navigation uses `[data-home-tab]` and `switchHome(tab)`.
- Header auth action routes an authenticated user to profile and opens auth for a guest.
- The source has an auth modal/form with sign-in, account creation and password-reset controls.
- The requested Native behavior is guest-first entry; preserve the option to authenticate later. Do not force a login screen at initial launch.

### Profile and settings
- Profile editing is a separate modal/form; profile submit validates the Call of Duty UID and profile fields before invoking the update flow.
- Avatar and cover selection are separate file-input flows with preview/validation and cancellation paths.
- Settings include appearance and blocked-account areas, plus sign-out and account deletion confirmation paths.
- Native parity must preserve validation, loading/disabled button states, errors, and confirmation dialogs, not only static form fields.

### Explore, social and chat
- Explore supports search, opening public profiles, follow/block/report actions, follow requests and blocked-user unblocking.
- Chat contacts open a conversation; `chatForm` submits messages through the messaging function.
- Support has a new-conversation action and a separate submit flow with loading and failure messaging.
- These are distinct flows and should not be collapsed into one generic list screen.

### Notifications
- Notification cards support a long-press/pointer gesture to reveal a delete action.
- Pointer-up, pointer-cancel and pointer-leave clear the long-press timer; context-menu and keyboard context actions are also handled.
- Card click handling supports read/delete behavior and confirmation.
- Native design request: place notifications behind the bell beside settings, show a red indicator for new notifications. This changes placement, not notification semantics.

### Rooms and rankings
- Room list supports category filters, text search, open-room details, room tabs, retry states and periodic/visibility refresh.
- Ranking supports search, profile opening and retry behavior.
- Room details expose rules, results, teams/slots/waiting status and room-access states.
- Source contains room registration and wallet-related operations. For this migration, do not activate paid-entry, wallet debit/credit, prize payout or money-transfer operations in the Native client. Such controls must remain disabled/non-transactional; do not wire the related RPCs.

### Teams
- Team creation and settings are separate forms.
- Team invitation creation, accept/reject, sent-invite cancellation, member removal, leaving and deleting a team are separate actions.
- Preserve role/owner conditions, confirmation steps, busy states and error feedback.

## Admin and manager — verified interaction families

Both files share a broad panel layout and several forms, but they are separate source products.

### Room image editor
- Both sources use pointer down/move/up/cancel and lost-pointer-capture handling for image focal-point adjustment.
- Both use X/Y range controls, input updates and file validation for JPG/PNG/WebP with a size limit.
- This gesture includes Android-specific compatibility-click suppression to prevent an image drag from accidentally focusing a nearby text input and opening the keyboard.
- Native implementation should use a Compose drag gesture with explicit cancellation and range controls, and test on a real Android device. Do not assume a normal click handler is enough.

### Access control and management
- Admin and manager both have login/session refresh, permission application, logout and protected panel access.
- Room creation/editing, tournament creation, user management, reports, moderation cases, notification selection, theme settings and data refresh have independent handlers.
- User ban/unban checks the owner capability in the inspected source.
- Social report status actions check the social moderation permission.
- Moderation case decisions check owner access and an allow-list of decision values.
- Tournament and room writes check their respective permissions.
- Direct notification submission is owner-gated in the inspected source.
- Native must never replace server-side authorization with UI-only hiding; retain server-enforced permission checks and reflect denied/loading/error states.

### Explicit admin-vs-manager differences
- Admin-only DOM IDs observed: `ex1-room-fx-admin-toggle`, `rf_game_category`, `rf_price_category`, `rf_room_effects_enabled`, `roomEffectsSchemaNotice`.
- Manager-only IDs were not found in the current static ID scan.
- Admin-only named functions observed: `bootEx1Admin`, `detectRoomEffectsColumn`, `readRoomImageBytes`.
- The manager source omits the admin's initial `EX1_NATIVE_APP` / `EX1_UPDATE_CHANNEL` bootstrap script.
- Admin and manager titles, root CSS values and source lengths differ.
- Therefore shared-looking panel IDs do not prove identical permissions or behavior. Keep separate screen specifications and test cases.

## Native preview comparison

The existing `NativePreviewActivity.kt` declares composables for several player and admin preview screens. This is a useful UI sandbox but does not yet establish:
- complete screen/route parity,
- all source form fields and validation,
- full gesture behavior,
- all API loading/error/empty states,
- admin and manager permission parity,
- exact typography, spacing, iconography, image treatment and animation parity.

Do not change the launcher until each route passes the parity and test gates.

## Implementation order proposed after audit

1. Create shared Compose design tokens and reusable primitives only after extracting exact values from the HTML/CSS.
2. Build guest-first player shell and navigation; compare home, tournaments, ranking and profile routes independently.
3. Build player profile/settings and social screens, then teams and notifications.
4. Build room details as read-only/non-transactional Native views; leave financial/paid-entry/prize actions disabled.
5. Build admin and manager as separate route graphs, sharing components only where their source behavior is proven equivalent.
6. Add UI tests for route transitions, guest/auth states, form validation, empty/loading/error states, pointer/drag interactions and permission-denied states.
7. Run CI and device/emulator checks before considering any launcher migration.

## Audit limits and next work

This document records source-level paths and handler families found during inspection. It is not yet a line-by-line mapping of every DOM listener, every payload schema, every CSS token, or every server-side permission. Those remain explicit audit tasks. No application Kotlin/HTML code, database, `main`, or Production was changed by this documentation step.
