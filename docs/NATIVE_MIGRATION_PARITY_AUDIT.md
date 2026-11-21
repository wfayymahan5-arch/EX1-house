# EX1 LEGEND — Native Migration Parity Audit

Status: baseline inventory only; no UI, runtime, backend, database, wallet, points, or production changes are made by this document.
Reference branch: `feature/native-android-migration`
Reference files: `app.html`, `admin.html`, `manager.html`
Native implementation inspected: `android/app/src/main/java/com/ex1legend/app/NativePreviewActivity.kt`

## Non-negotiable migration contract

The Native application must reproduce the reference WebView application's observable behavior and presentation, not reinterpret or redesign it. Preserve all page flows, text, content, typography, spacing, colors, imagery, iconography, component states, responsive behavior, motion, validation, permissions, backend semantics, and user journeys. Do not remove, simplify, reorder, rename, or invent existing functionality.

Only explicitly approved product changes:
1. Move user notifications out of the profile area into a bell control beside Settings; show a small red unread indicator and open the existing notifications experience when tapped.
2. Make the sign-in/entry preview more game-like and exciting, without changing authentication options, validation, or the underlying flow.

Native implementation means Kotlin/Jetpack Compose UI and native navigation/components; HTML/WebView must not be used as a hidden substitute for the final Native UI. Existing WebView files remain the immutable visual/behavioral reference until parity is verified.

## Source baseline measurements

| Source | Size | IDs / forms / buttons / keyframes |
|---|---:|---|
| `app.html` | 183,611 chars | 4 explicit page IDs; 7 forms; 86 button elements; 23 CSS keyframes |
| `admin.html` | 94,888 chars | 6 forms; 41 button elements; 12 CSS keyframes |
| `manager.html` | 87,760 chars | manager/admin variant; same principal management workflow and 6 forms |

Note: page IDs alone do not describe the full app. Dialogs, panels, nested workflows, dynamic content, and controls are also part of the reference and must be inventoried.

## Player app — reference areas and workflows

### Main navigation / shell
- Connection status and settings entry
- Header identity and authentication controls
- Home / player field
- Explore players
- Conversations / chat
- Tournaments / room categories
- Ranking / champions
- Profile
- Bottom navigation and its animated states

### Explore and social
- Player search and results
- Public player profile
- Follow and follower counts/lists
- Follow requests
- Profile bio and public profile details
- Chat contacts, conversation, message list, composer, send action
- Support panel and support conversation

### Profile and account
- Profile cover and avatar selection
- Cover preview, save, cancel, remove
- Profile editor and fields
- Public username/display name/role/bio
- Team summary and team management
- My matches
- Wallet entry point and displayed balance
- Account settings, appearance settings, font/theme preferences
- Blocked accounts
- Change account, logout, account deletion with confirmation
- Authentication modal: sign in, sign up, forgot password, switch modes

### Rooms and competitions
- Room categories: For You, Battle Royale, Fight, Multiplayer, Uncategorized
- Room search and category filters
- Room cards, details, registration states, waiting states, and results
- Solo registration / automatic team formation
- Full-team registration
- Team slots, player lists, waiting players, queue status
- Room rules, results, room access details, retry states
- Room image and visual effects
- My tournaments / my rooms
- Tournament rewards and top-player rankings

### Teams
- Create team
- Edit team settings
- Team name, tag, description
- Team logo flow
- Invite player
- Sent invitations, incoming invitations, follow requests
- Team member management and team detail

### Wallet, points, stars and notifications
- Wallet view and transaction/history ledger
- Stars/reward ledger and displayed point/reward values
- Existing wallet and points semantics must be preserved exactly; do not introduce new balance behavior.
- Account notifications list, read state, delete/read actions
- Approved bell placement change described above

### Motion and visual effects in player app
All 23 reference keyframes must be individually mapped to equivalent native motion/visual behavior:
- ex1AmbientDrift
- ex1Rise
- ex1LightSweep
- ex1NeonSweep
- ex1SoftEnter
- ex1MatrixDrift
- ex1AmbientSpin
- ex1BorderPulse
- ex1Shimmer
- ex1PageSoftEnter
- ex1DialogSoftEnter
- ex1NavAmbient
- ex1NavSweep
- ex1ProfileOrbit
- ex1ProfileScan
- ex1AvatarPulse
- ex1LevelFloat
- ex1NavSelected
- ex1CinematicPageEnter
- ex1NavIconPop
- ex1RoomGlow
- ex1RoomScan
- ex1RoomParticle

Additional style blocks/IDs in source must also be inspected; keyframes are not the full motion inventory.

### Player form IDs
- chatForm
- supportForm
- createTeamForm
- teamSettingsForm
- teamInviteForm
- authForm
- profileForm

### Important player source IDs (representative inventory)
- Shell: connectionStatus, openSettings, headerUser, headerAuth
- Home/social: page-home, home-explore, userSearch, searchUsers, exploreUsers, home-messages, chatContacts, chatTitle, chatMessages, chatText, chatSend
- Profile: page-profile, profileCover, profileAvatar, profileDisplayName, profileRoleBadge, profileUsername, profileFollowersCount, profileFollowingCount, profileBioText, openProfileEditor
- Team: profileTeam, myTeam, createTeamWrap, createTeamForm, teamSettingsPanel, teamSettingsForm, teamInviteForm, teamSentInvites, incomingTeamInvites, followRequests
- Rooms: page-tournaments, roomCategoryFilter, roomSearch, roomCards, roomDetail, roomTeamsPanel, roomWaitingPanel, roomResultsPanel, roomRulesPanel, roomRegisterBar
- Ranking: page-ranking, rankingSearch, ranking
- Auth/account: authModal, authForm, forgotPasswordButton, authSwitch, profileEditorModal, publicPlayerProfileModal, settingsModal, logoutButton, deleteAccountButton
- Wallet: walletModal, walletModalBalance, walletHistory
- Confirmations/feedback: ex1ConfirmModal, toast

## Admin / manager panel — reference areas and workflows

Both `admin.html` and `manager.html` contain the management workspace. They must be compared separately before treating them as identical.

- Secure manager sign-in and session refresh
- Live overview and dashboard statistics
- Tournament management: add/edit, game, mode, map, capacity, start time, status, cover, description, rules
- Room management: category, price category, mode, map, team size, capacity, entry/reward display values, schedule, status, image, image focus, effects, rules
- Room access publication: room code/password, publication status and participant access
- Registrations
- Teams
- Users and public profiles
- Direct notifications: search/select users, select all/visible, clear selection, title/body, send
- Social reports
- AI moderation cases and review actions
- Gift-star ledger and adjustment/history tools
- Site appearance/theme settings
- Role/permission-limited manager access and owner-only access states
- Tables, loading/empty/error states, toast feedback, confirmation flows

### Admin forms
- loginForm
- tournamentForm
- roomForm
- directNotificationForm
- walletAdjustForm
- themeForm

### Admin motion keyframes
All 12 source keyframes must be mapped:
- ex1GridDrift
- ex1HeroSweep
- ex1RiseIn
- ex1ToastIn
- ex1AdminAmbient
- ex1AdminSweep
- ex1AdminNeonSweep
- ex1AdminTabEnter
- ex1MatrixDrift
- ex1AmbientSpin
- ex1BorderPulse
- ex1Shimmer

### Critical admin controls (source IDs)
- Authentication: authView, loginForm, email, password, loginButton, authMessage, appView, identity, logoutButton
- Overview: overview, overviewText, overviewEmpty, statTournaments, statRegistrations, statTeams, statUsers
- Navigation: nav
- Tournaments: tournaments, addTournamentButton, tournamentForm, tf_title, tf_game, tf_mode, tf_map, tf_max_teams, tf_starts_at, tf_status, tf_cover_image_url, tf_description, tf_rules, saveTournamentButton, cancelTournamentButton, tournamentsTable
- Rooms: rooms, addRoomButton, roomForm, rf_game_category (admin.html variant), rf_price_category (admin.html variant), rf_game_mode, rf_map, rf_max_teams, rf_team_size, rf_capacity, rf_entry_demo_points, rf_prize_demo_points, rf_prize_second_demo_points, rf_prize_third_demo_points, rf_starts_at, rf_ends_at, rf_status, rf_room_image_url, rf_image_focus_x, rf_image_focus_y, rf_room_image_file, rf_room_effects_enabled, rf_rules, saveRoomButton, cancelRoomButton, roomAccessAdminPanel, rf_room_code, rf_room_password, publishRoomAccessButton, roomsTable
- Registrations/teams/users: registrations, registrationsTable, teams, teamsTable, users, usersTable
- Notifications: directNotificationPanel, directNotificationForm, directNotificationSearch, selectVisibleNotificationUsers, selectAllNotificationUsers, clearNotificationUsers, notificationSelectionCount, notificationUsersList, directNotificationTitle, directNotificationBody, sendDirectNotification
- Social/moderation: social, socialReportsTable, moderation, moderationCasesTable
- Stars/wallet: wallet, walletTable, walletAdjustPanel, walletAdjustForm, wallet_user_id, wallet_entry_type, wallet_amount, wallet_reason, walletAdjustSubmit, walletAdjustCancel, walletHistory
- Settings: settings, themeForm, background_color, surface_color, primary_text_color, secondary_text_color, accent_color, saveTheme, themeMessage

## Current Native preview — verified scope snapshot

Inspected composables in `NativePreviewActivity.kt`:
- NativePreviewRoot
- LoginPanel
- PlayerPreviewPanel
- RankingPreviewPanel
- ExplorePreviewPanel
- PublicProfileDialog
- ProfilePreviewPanel
- WalletPreviewPanel
- SupportPreviewPanel
- NotificationsPreviewPanel
- TeamPreviewPanel
- SettingsPreviewPanel
- RoomDetailDialog
- RoomPreviewCard
- AdminPreviewPanel
- AdminMetricCard
- AdminNoPermission
- AdminDataPanel

This confirms the current file is a preview-oriented implementation. The composable list is not evidence of full feature parity. Each source workflow above requires an explicit Native implementation and test.

## MainActivity / app entry observation

The inspected `MainActivity.kt` is still a WebView-based entry point. The preview activity is not yet a verified replacement for the full app. Do not switch the launcher to an incomplete Native preview.

## Parity acceptance criteria

A feature is complete only when all applicable checks pass:
1. Same entry flow and auth optionality.
2. Same visible content, ordering, labels, assets, colors, typography, sizes, spacing, and responsive behavior.
3. Same actions, validation, disabled/loading/error/empty/success states.
4. Same navigation and back behavior.
5. Same animations, timing, triggers, and state transitions to the extent Android permits.
6. Same backend data and authorization semantics; no client-side privileged secrets.
7. Same permission boundaries for owner/managers.
8. Same accessibility/touch targets and keyboard behavior.
9. Automated tests for state/logic and manual device screenshot/interaction comparison.
10. No unapproved changes to production, database, users, wallet, points, or stars.

## Work plan

- Phase 1 (this audit): establish immutable source baseline and feature inventory.
- Phase 2: expand inventory to every event handler, data request/RPC, dynamic render template, CSS selector, asset, and state transition.
- Phase 3: create a screen-by-screen Native parity matrix with source references and test cases.
- Phase 4: implement Native design system and screens in small, reviewable slices.
- Phase 5: verify visual/behavioral parity on the target Android device and CI.
- Phase 6: only after explicit acceptance, prepare a release candidate. Production remains untouched until separate explicit authorization.

This audit is a living checklist. Do not mark an item complete based only on compilation or the presence of a Compose screen.
