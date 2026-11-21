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
