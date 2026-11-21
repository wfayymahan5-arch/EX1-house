# Native Android API parity inventory

This inventory is derived from the current player/admin web clients. It is a discovery aid, not permission to change backend behavior.

## Client architecture observed
- Player and admin pages initialize the Supabase JavaScript SDK in the browser.
- Player auth explicitly persists sessions in `window.localStorage`, enables token refresh and URL callback detection.
- Admin auth uses `getUser`, `signInWithPassword`, `signOut`, and auth-state callbacks.
- The current Android Activity still hosts these pages in WebView. The primary URL is remote; a bundled HTML fallback exists.
- The current Activity sets WebView cache mode to `LOAD_NO_CACHE`, so the web page is not using normal WebView caching.

## Player client resources

### Tables queried
`profiles`, `site_theme_settings`, `social_conversation_members`, `social_conversations`, `social_messages`, `social_reports`, `support_conversations`, `support_messages`, `team_invites`, `team_members`, `teams`, `tournament_player_results`, `tournament_results`, `tournament_room_registrations`, `tournament_rooms`, `tournaments`, `user_blocks`, `user_follows`, `user_notifications`, `user_settings`.

### RPC calls
`ex1_my_role_summary`, `ex1_public_role_badges`, `get_my_room_solo_status`, `get_my_tournament_room_access`, `get_tournament_room_slots`, `get_tournament_room_waiting_players`, `is_current_user_banned`, `register_for_tournament_room`, `register_for_tournament_room_solo`, `team_delete`, `team_invite_cancel`, `team_invite_create`, `team_invite_respond`, `team_leave`, `team_member_remove`, `wallet_my_history`, `wallet_my_summary`.

### Storage buckets
`ex1-avatar-review`, `ex1-avatars`.

### Realtime channels
`ex1-site-theme-settings`.

## Admin client resources

### Tables queried
`ai_moderation_cases`, `profile_moderation`, `profiles`, `site_theme_settings`, `social_reports`, `tournament_rooms`, `tournaments`.

### RPC calls (including calls routed through the local rpc(name, args) helper)
`admin_dashboard_registrations`, `admin_dashboard_teams`, `admin_dashboard_tournaments`, `admin_dashboard_users`, `admin_get_tournament_room_access_status`, `admin_list_user_bans`, `admin_my_permissions`, `admin_publish_tournament_room_access`, `admin_social_reported_content`, `admin_wallet_demo_adjust`, `admin_wallet_demo_history`, `admin_wallet_demo_overview_v2`, `is_current_user_owner`, `set_site_theme`.

### Storage buckets
`tournament-assets`.

### Realtime channels
`ex1-admin-site-theme`.

## Required implementation constraints
1. Reuse the existing Supabase Auth identity and RLS/permission model. Never place a service-role key in the app.
2. Preserve the current RPCs and their arguments/semantics; do not reimplement authorization or transactional logic on-device.
3. Wallet-related RPCs and tables are parity-only. In particular, do not change balances, ledger entries, point deductions, rewards, or any wallet business logic.
4. Admin wallet controls, where present in the current product, must call only the existing owner/permission-protected RPCs and must not be altered as part of the migration.
5. Registration, room capacity, access publication, team membership and moderation writes must remain server-authoritative.
6. Cache only read models with explicit expiry. Always revalidate critical state before registration, team changes, moderation decisions, or admin writes.
7. Map every direct table write and every dynamically invoked RPC before implementing native write flows. This document lists discovered calls but is not a complete per-screen behavioral specification.

## Performance findings to address
- The Android Activity currently loads remote HTML as its main UI.
- The Android Activity disables WebView cache via `LOAD_NO_CACHE`.
- A local bundled HTML fallback exists but is still HTML/JavaScript and therefore is not a native interface.
- The new native client should render its shell and navigation immediately, then hydrate data asynchronously from the backend.
- Use bounded image sizes, lazy lists, pagination, and request deduplication; avoid caching authoritative write outcomes.
