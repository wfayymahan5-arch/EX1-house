# EX1 LEGEND — Native migration execution plan

Status: Phase 0 baseline verified; Phase 1 source inventory in progress.
Working branch: `feature/native-android-migration`
Baseline commit inspected: `09c86605e38bd86810df970a989a91a979cba045`
Production/main: out of scope; no merge, deployment, or live database changes.

## Objective

Replace the Android WebView user interface with genuine Kotlin + Jetpack Compose screens. The HTML clients remain immutable reference implementations during migration. A Compose preview APK is not considered a migrated app and must never be labelled as such.

## Verified repository facts at baseline

- Reference clients: `app.html`, `admin.html`, and `manager.html`.
- Android launcher: `android/app/src/main/java/com/ex1legend/app/MainActivity.kt`; it still creates and loads a WebView.
- Current Compose work: `NativePreviewActivity.kt`; it is an isolated preview activity and is not the launcher.
- API layer: `NativeEx1Api.kt`; this is not proof that every web-client API operation has a native equivalent.
- Manifest: `MainActivity` is the exported launcher; `NativePreviewActivity` is not exported.
- Build flavors: player/admin. The `nativePreview` build type inherits debug and adds a separate application ID suffix.
- CI workflow builds four outputs: Player WebView, Admin WebView, Player NativePreview, Admin NativePreview. A successful CI run proves compilation/tests configured in that workflow only; it does not prove UI parity or runtime correctness.
- Latest inspected baseline CI run: `44939247105`, conclusion `success`, branch `feature/native-android-migration`.

## Required phases and exit gates

### Phase 0 — Protect and establish baseline (verified)
- [x] Confirm working branch and commit.
- [x] Confirm launcher and preview activity roles.
- [x] Confirm CI outputs and test tasks.
- [x] Keep main/Production/database untouched.

### Phase 1 — Exhaustive reference inventory (in progress)
- [ ] Inventory every screen, modal, drawer, tab, form, button, input, validation, loading/empty/error/success state and navigation transition in all three HTML clients.
- [ ] Inventory CSS tokens, fonts, images, icons, responsive breakpoints, animation keyframes and timing.
- [ ] Inventory every event handler, Supabase query, RPC, Edge Function, Storage operation and Realtime subscription.
- [ ] Compare admin.html and manager.html independently; do not assume parity.
- [ ] Build a traceable source-to-native matrix with a test case for every interaction.

Exit gate: inventory can be reviewed against source code and has no unexplained feature gaps.

### Phase 2 — Native foundation
- [ ] Compose design tokens and reusable components measured from the reference UI.
- [ ] RTL, Persian typography, system bars, window insets, keyboard, accessibility and responsive behavior.
- [ ] Navigation/state architecture, lifecycle-safe loading, error handling and session lifecycle.
- [ ] Typed API contracts and repository tests; no privileged credentials in the client.

Exit gate: foundation tests pass and reference screenshots for shared components are compared.

### Phase 3 — Player UI and behavior
- [ ] Implement every player screen and state from the parity matrix.
- [ ] Preserve existing auth options and validation; guest-first entry is only applied as an approved product change.
- [ ] Reproduce existing read-only views and social/profile/team/support/notification behavior.
- [ ] Any sensitive financial, paid-entry, prize, or transfer workflow remains non-operational and is not newly enabled as part of this migration.

Exit gate: every player matrix row has implementation evidence, automated test evidence where practical, and screenshot/interaction comparison.

### Phase 4 — Admin and manager UI
- [ ] Inventory and implement the owner/admin and manager experiences separately.
- [ ] Preserve role and per-manager permission boundaries in UI and server authorization.
- [ ] Cover dashboard, tournament/room configuration views, registrations, teams, users, notifications, moderation, support, theme and audit states.
- [ ] Sensitive financial/paid-entry/prize controls are not made operational by this client migration.

Exit gate: permission tests and screen-by-screen comparisons pass; no privilege escalation.

### Phase 5 — Verification
- [ ] Run all player/admin unit tests and static checks.
- [ ] Build all intended variants from the experimental branch.
- [ ] Add UI/instrumentation tests for critical navigation and state transitions.
- [ ] Compare screenshots against reference HTML at the same viewport and density.
- [ ] Test on a real Android device, including low-memory behavior and RTL/keyboard flows.
- [ ] Review logs for crashes, leaked secrets, incorrect permissions and unintended network writes.

Exit gate: build, automated tests, screenshot comparisons and real-device acceptance all pass. CI green alone is insufficient.

### Phase 6 — Release candidate (not authorized yet)
- [ ] Produce clearly labelled candidate APKs only after the prior gates pass.
- [ ] Keep WebView rollback artifacts available.
- [ ] No merge to main, production deployment, or database migration without separate explicit approval.

## Visual parity rules

- Do not replace detailed reference screens with generic cards or placeholder panels.
- Match content hierarchy, dimensions, spacing, font family/weight/size, colors, gradients, borders, shadows, imagery, iconography, motion, scrolling, focus and touch behavior.
- Preserve loading, empty, disabled, validation, error, confirmation and success states.
- Use source HTML/CSS as the reference; do not infer missing details from the existing preview.
- A page is not complete merely because it compiles or renders.

## Reporting format

For each phase, report: completed evidence, remaining gaps, blocked items, tests actually run, branch/commit, and next concrete step. Never claim emulator/device inspection unless it was actually performed.
