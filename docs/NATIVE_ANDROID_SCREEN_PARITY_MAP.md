# EX1 LEGEND Native migration — screen and API parity map

This map was extracted from the current `app.html` and `admin.html` clients on the migration branch. It is the source checklist for feature-by-feature native replacement. It does not authorize backend changes.

## Player screens and dialogs
- Home/explore: `page-home`
- Profile: `page-profile`
- Tournament/room discovery: `page-tournaments`
- Room details: `roomDetail`
- Ranking: `page-ranking`
- Team profile and matches: `profileTeam`, `profileMatches`
- Support: `supportPanel`
- Notifications: `userNotificationsPanel`
- Waiting queue: `roomWaitingPanel`
- Dialogs: `authModal`, `profileEditorModal`, `settingsModal`, `walletModal`, `joinRoomModal`, `ex1ConfirmModal`

## Admin screens and forms
- Owner/admin entry: `authView`
- Overview: `overview`
- Tournaments: `tournaments`
- Rooms: `rooms`
- Registrations: `registrations`
- Teams: `teams`
- Users: `users`
- Social moderation: `social`
- Content moderation: `moderation`
- Virtual wallet oversight: `wallet`
- Site settings/theme: `settings`
- Forms: `loginForm`, `tournamentForm`, `roomForm`, `directNotificationForm`, `walletAdjustForm`, `themeForm`

## Player API surface

### RPCs
`ex1_my_role_summary`, `ex1_public_role_badges`, `get_my_room_solo_status`, `get_my_tournament_room_access`, `get_tournament_room_slots`, `get_tournament_room_waiting_players`, `is_current_user_banned`, `register_for_tournament_room`, `register_for_tournament_room_solo`, `team_delete`, `team_invite_cancel`, `team_invite_create`, `team_invite_respond`, `team_leave`, `team_member_remove`, `wallet_my_history`, `wallet_my_summary`.

### Edge Functions
`ai-support`, `delete-account`, `manage-team`, `moderate-avatar-immediate`, `moderate-profile-cover`, `send-social-message`, `update-profile`.

### Direct table writes observed
- `user_settings`: upsert
- `user_follows`: insert, update, delete
- `user_blocks`: insert, delete
- `social_reports`: insert
- `social_conversations`: insert
- `social_conversation_members`: insert
- `user_notifications`: update, delete
- `teams`: update

### Storage and Realtime
- Buckets: `ex1-avatar-review`, `ex1-avatars`
- Channel: `ex1-site-theme-settings`

## Admin API surface

### RPCs (including wrapper calls through `rpc(name,args)`)
`admin_dashboard_registrations`, `admin_dashboard_teams`, `admin_dashboard_tournaments`, `admin_dashboard_users`, `admin_get_tournament_room_access_status`, `admin_list_user_bans`, `admin_my_permissions`, `admin_publish_tournament_room_access`, `admin_social_reported_content`, `admin_wallet_demo_adjust`, `admin_wallet_demo_history`, `admin_wallet_demo_overview_v2`, `is_current_user_owner`, `set_site_theme`.

### Edge Functions
`admin-list-users-for-notifications`, `admin-send-user-notification`, `get-ai-moderation-evidence`, `manage-ai-moderation-case-v2`, `manage-user-ban`.

### Direct table writes observed
- `profile_moderation`: insert, delete
- `social_reports`: update
- `tournament_rooms`: insert, update, delete
- `tournaments`: insert

### Storage and Realtime
- Bucket: `tournament-assets`
- Channel: `ex1-admin-site-theme`

## Migration implementation order
1. Auth and session lifecycle; account recovery and account deletion with the same confirmation/reauthentication requirements.
2. Native app shell, Persian RTL design system, home navigation, player profile and settings.
3. Tournament/room discovery and details, categories and price tiers, read-only roster/results, then server-authoritative registration and waiting queue.
4. Teams/invites, notifications, wallet read-only views, support, social messaging/follow/block/report.
5. Owner and manager admin entry, server permission gates, tournaments/rooms/registrations/teams/users, notifications, moderation, wallet oversight, theme settings.
6. Storage image workflows, realtime refresh, offline/stale state, pagination, performance, security and device regression tests.

## Safety and compatibility notes
- Player and manager access must continue to be authorized by the existing server-side RLS/RPC/Edge Function checks.
- The owner-only admin HTML flow first verifies `is_current_user_owner`; manager workspaces must remain separately permission-scoped. Do not infer owner status from the fact that `admin_my_permissions` returned a list.
- Direct table writes are not to be ported until their existing RLS policy and server-side invariants are understood. Prefer existing RPCs or Edge Functions where the current client already uses them.
- Virtual points are internal-only. Do not change any wallet ledger, balance, deduction, reward, or adjustment semantics; do not add cash value, deposits, withdrawals, external transfers, or payment integrations.
- Native preview code is isolated from the launcher. Keep the existing WebView Activity as the production/rollback entry point until all player and admin parity gates and real-device acceptance pass.
