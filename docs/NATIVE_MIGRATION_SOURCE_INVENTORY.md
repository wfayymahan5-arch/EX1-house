# EX1 LEGEND — Source inventory findings (Phase 1)

Inspection ref: `feature/native-android-migration` after execution-plan commit `e09dd60d4578f383880e809a66af22c58fece1d6`.
Method: fetched and parsed the full source text of all three reference HTML files from this branch. Counts below are source counts, not estimates of runtime routes.

## Source size and structural counts

| Source | Characters | Unique HTML IDs | Forms | Buttons | Inputs/selects/textareas | CSS keyframes | Script tags | Style tags |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| `app.html` | 183,611 | 161 | 7 | 86 | 25 | 23 | 6 | 23 |
| `admin.html` | 94,888 | 132 | 6 | 41 | 53 | 12 | 3 | 10 |
| `manager.html` | 87,760 | 127 | 6 | 41 | 50 | 12 | 3 | 9 |

Unique ID count is the number of distinct `id` attributes in the source. These counts do not include controls or elements created dynamically by JavaScript, so they are a lower bound on the actual interaction inventory.

## Player form inventory

- `chatForm`
- `supportForm`
- `createTeamForm`
- `teamSettingsForm`
- `teamInviteForm`
- `authForm`
- `profileForm`

Inline event-handler types observed: `click`, `error`, `keydown`, `backdrop`, `key`.

All 23 keyframes observed in player source:
`ex1AmbientDrift`, `ex1Rise`, `ex1LightSweep`, `ex1NeonSweep`, `ex1SoftEnter`, `ex1MatrixDrift`, `ex1AmbientSpin`, `ex1BorderPulse`, `ex1Shimmer`, `ex1PageSoftEnter`, `ex1DialogSoftEnter`, `ex1NavAmbient`, `ex1NavSweep`, `ex1ProfileOrbit`, `ex1ProfileScan`, `ex1AvatarPulse`, `ex1LevelFloat`, `ex1NavSelected`, `ex1CinematicPageEnter`, `ex1NavIconPop`, `ex1RoomGlow`, `ex1RoomScan`, `ex1RoomParticle`.

## Admin form inventory

- `loginForm`
- `tournamentForm`
- `roomForm`
- `directNotificationForm`
- `walletAdjustForm`
- `themeForm`

Inline event-handler types observed: `load`, `error`, `click`.

All 12 keyframes observed in admin source:
`ex1GridDrift`, `ex1HeroSweep`, `ex1RiseIn`, `ex1ToastIn`, `ex1AdminAmbient`, `ex1AdminSweep`, `ex1AdminNeonSweep`, `ex1AdminTabEnter`, `ex1MatrixDrift`, `ex1AmbientSpin`, `ex1BorderPulse`, `ex1Shimmer`.

## Manager form inventory

The same six form IDs exist in `manager.html`, but source markup and controls differ from `admin.html`. Inline event-handler types observed: `click`, `load`. It has 127 unique IDs, 50 inputs/selects/textareas and 9 style blocks, compared with 132, 53 and 10 respectively in `admin.html`.

## Confirmed admin/manager differences requiring separate implementations

- `admin.html` contains `rf_game_category` and `rf_price_category` room form controls; these IDs are absent from `manager.html`.
- `admin.html` contains room-effects controls `rf_room_effects_enabled` and `roomEffectsSchemaNotice`; these are absent from `manager.html`.
- Both have room image selection/preview/focus controls and room access code/password publication controls, but their full DOM and script behavior must be compared independently.
- The files have different lengths, unique-ID counts, input counts and style-block counts. They must not be treated as interchangeable HTML shells.

## Important interpretation

- Static ID/form/button counts are not sufficient for feature parity. JavaScript-generated cards, table rows, dialogs, navigation states, validation, and asynchronous states must be enumerated from script logic.
- A matching label or Compose component name is not proof that the original event behavior exists.
- The current native preview composables do not cover the full source inventory.
- The app remains WebView-launched until a complete Native implementation is verified. Do not switch the launcher as a shortcut.

## Remaining Phase 1 tasks

- [ ] Enumerate every static and dynamic page/panel/modal and its visibility transitions.
- [ ] Enumerate every click/submit/key handler and trace it to a state change or API operation.
- [ ] Extract all CSS custom properties, typography declarations, layout breakpoints, image URLs and icon definitions.
- [ ] Map all Supabase table reads/writes, RPCs, Edge Functions, Storage calls and Realtime channels to the exact UI action.
- [ ] Produce source-linked parity rows and test cases for player, admin and manager independently.

## JavaScript behavior and backend references extracted from source

Static JavaScript function declarations and directly named backend calls were also enumerated. These are useful coverage anchors, but indirect calls, helper wrappers, dynamically built queries and event delegation still require call-graph review.

| Source | Declared functions | Distinct addEventListener event types | Explicit RPC names | Direct .from() resource names | Edge Function invokes | CSS custom properties |
|---|---:|---:|---:|---:|---:|---:|
| `app.html` | 71 | 21 | 17 | 22 | 7 | 20 |
| `admin.html` | 37 | 12 | 8 | 8 | 5 | 16 |
| `manager.html` | 34 | 12 | 8 | 8 | 5 | 16 |

### Player explicit RPC calls

`wallet_my_summary`, `wallet_my_history`, `register_for_tournament_room_solo`, `register_for_tournament_room`, `get_my_room_solo_status`, `get_tournament_room_waiting_players`, `get_my_tournament_room_access`, `get_tournament_room_slots`, `is_current_user_banned`, `ex1_public_role_badges`, `ex1_my_role_summary`, `team_invite_create`, `team_invite_respond`, `team_invite_cancel`, `team_member_remove`, `team_leave`, `team_delete`.

### Player Edge Functions

`delete-account`, `moderate-avatar-immediate`, `moderate-profile-cover`, `update-profile`, `send-social-message`, `ai-support`, `manage-team`.

### Admin and manager explicit RPC calls

`admin_social_reported_content`, `admin_dashboard_users`, `is_current_user_owner`, `admin_list_user_bans`, `admin_wallet_demo_history`, `admin_wallet_demo_adjust`, `admin_get_tournament_room_access_status`, `admin_publish_tournament_room_access`.

### Admin and manager Edge Functions

`get-ai-moderation-evidence`, `manage-user-ban`, `admin-list-users-for-notifications`, `admin-send-user-notification`, `manage-ai-moderation-case-v2`.

### Direct .from() resource names

Player: `site_theme_settings`, `user_settings`, `profiles`, `tournament_player_results`, `ex1-avatars`, `ex1-avatar-review`, `user_follows`, `user_blocks`, `social_reports`, `social_conversation_members`, `social_conversations`, `social_messages`, `user_notifications`, `support_conversations`, `support_messages`, `tournament_rooms`, `tournament_room_registrations`, `tournament_results`, `team_invites`, `team_members`, `teams`, `tournaments`.

Admin and manager: `tournament_rooms`, `tournament-assets`, `tournaments`, `social_reports`, `profiles`, `ai_moderation_cases`, `profile_moderation`, `site_theme_settings`.

### Observed player CSS tokens

`bg`, `surface`, `surface2`, `stroke`, `text`, `muted`, `violet`, `violet2`, `cyan`, `gold`, `accent`, `accent2`, `ex1-ui-accent`, `ex1-ui-button`, `ex1-neon`, `ex1-cyan`, `ex1-glow`, `ex1-violet`, `ex1-pink`, `ex1-blue`.

### Observed admin/manager CSS tokens

`bg`, `panel`, `line`, `text`, `muted`, `purple`, `cyan`, `green`, `ex1-glow-purple`, `ex1-glow-cyan`, `ex1-neon`, `ex1-cyan`, `ex1-violet`, `ex1-pink`, `ex1-blue`, `ex1-glow`.

### Observed font stacks

- Player: `Tahoma, Arial, sans-serif`; `system-ui, sans-serif`; `Arial, sans-serif`; `monospace`.
- Admin/manager: `system-ui, Tahoma, Arial, sans-serif`.

### Interpretation and next inspection pass

- Player source includes 21 distinct listener event types, including visibility/page lifecycle, pointer, touch, keyboard, form and context-menu interactions. A native port must account for lifecycle and gesture semantics, not only button clicks.
- Admin and manager have equal named RPC/Edge Function counts but differ in function count, markup and CSS blocks; identical API names do not imply identical UI permissions or behavior.
- The direct resource inventory includes Storage bucket names used through `.from()`; table and bucket references must be classified separately before implementing the repository layer.
- Next pass must trace function bodies and listener registrations to the exact DOM elements and state transitions, then map each to a native screen test.
