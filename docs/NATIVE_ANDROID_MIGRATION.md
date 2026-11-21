# EX1 LEGEND — Native Android Migration

## Goal
Replace the current WebView-hosted player and admin interfaces with native Android screens. The website remains an independent web client; Android screens must not load app.html or admin.html as their UI.

## Guardrails
- Work only on `feature/native-android-migration`; do not merge to `main`, deploy Production, or change the live database without a separate explicit approval.
- Preserve all existing player/admin capabilities and authorization checks.
- Do not change the virtual-points wallet, balances, ledger, room-entry deductions, rewards, or related RPC/database behavior.
- Never embed Supabase service-role keys or other privileged secrets in the Android client.
- Keep the existing WebView APK branch available as a rollback until native parity is verified.

## Current findings
- The current Android `MainActivity` creates a WebView and loads remote `app.html` / `admin.html` URLs.
- WebView cache is configured as `LOAD_NO_CACHE`.
- The HTML files are bundled as a fallback asset, but they remain web pages, not native UI.
- The web clients use the Supabase browser SDK, persisted browser session state, direct table queries, and RPC calls.
- The Gradle project now includes Jetpack Compose, player/admin flavors, an OkHttp-based Supabase API layer, encrypted session storage, and a `NativePreviewActivity`.
- `NativePreviewActivity` is still isolated from the launcher. `MainActivity` remains the launcher and still hosts WebView pages; this is intentional until native parity and device testing pass.

## Target architecture
- Kotlin + Jetpack Compose for screens, navigation, dialogs, lists, forms, loading and error states.
- A repository/data layer for Supabase Auth, PostgREST, Storage and Realtime using only the public/publishable client key and user-scoped sessions.
- Encrypted/local secure session persistence where supported; no credentials in source control.
- Room list cache with explicit freshness rules. Cached data may improve first paint, but registration/capacity and other authoritative state must be refreshed from the server before writes.
- Separate player and admin navigation graphs/flavors, sharing design components and API models.
- Offline/poor-network states that clearly distinguish cached data from live server state.
- Accessibility, Persian RTL layout, responsive sizing, and low-memory performance budgets.

## Public player biography addition (staged)
- Player Explore and ranking rows can open a public player profile.
- Public profile detail shows display name, username, game name, approved biography, and role badge where available; it deliberately does not expose game UID.
- Profile owner can edit an optional biography up to 500 characters.
- Biography is sent through the existing authenticated `update-profile` Edge Function. The staged function copy includes biography in the existing moderation input; flagged text is not published and creates an owner-review case.
- `supabase/migrations/20261121133500_profiles_public_bio.sql` adds the nullable `profiles.bio` column and a 500-character constraint.
- The migration and Edge Function source are committed only to this branch. Neither has been applied/deployed to the connected Supabase Production project.

## Migration sequence
1. Inventory every screen, user action, table, RPC, storage bucket, authorization rule and realtime subscription in both web clients.
2. Define typed API contracts and parity tests without changing backend semantics.
3. Add Compose design system, app navigation, auth/session layer and diagnostics.
4. Implement player screens and validate against existing read/write behavior.
5. Implement admin screens and permission-gated workflows.
6. Add cache/realtime/offline behavior and performance profiling.
7. Build preview APKs, run automated checks, and require real-device acceptance before any release/merge.

## First parity inventory
- Player: onboarding/auth, home and room categories, room details, registration/team workflows, profile, wallet read views, settings, social/moderation surfaces, notifications and support.
- Admin: authentication, role/permission resolution, overview, tournaments, rooms, registrations, teams, users, social/moderation, wallet read/admin views, settings and audit-sensitive actions.
- Backend behavior must be mapped from the existing code before implementing writes. The native client must call the existing approved RPCs/policies rather than recreate sensitive business logic locally.
