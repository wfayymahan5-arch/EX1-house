# EX1 LEGEND — Supabase integration

The live Supabase project is configured in `supabase-config.js` using its **public publishable key**. This key is intended for browser use; database access is still restricted by grants and Row Level Security (RLS).

## Important
- Do not run the old `schema.sql` against the live project. It describes an obsolete first-generation schema and does not match the current database.
- Never put a Supabase secret key or `service_role` key in this repository or browser code.
- The public site now uses Supabase Auth for email/password sign-up and sign-in, reads published tournament rows, and reads the public theme settings.
- New account profile creation is handled by the database trigger in the live project.
- Tournament registration is intentionally not enabled in this first connection step: the current database requires a team captain and a team, so the old one-click individual demo registration was removed.
- Wallet display and financial actions remain demo-only / not connected to real payments. Demo points have no cash value and cannot be bought, withdrawn, or exchanged for prizes.
- The admin page is still a preview. The `feature/tournament-room-admin` branch adds permission-gated tournament and room create/edit/delete forms; it is not live until reviewed, merged, and deployed. Image fields currently accept URLs; direct image uploads are not yet connected.

## Current live schema
The authoritative schema is the existing Supabase project, not the legacy SQL snapshot in this folder. Tables include profiles, tournaments, tournament_rooms, teams, team_members, tournament_registrations, tournament_results, admin_users, and site_theme_settings, plus private RBAC and demo-wallet tables. Tournaments now include cover image URL, rules, and non-redeemable demo-point entry/reward fields. Tournament rooms have their own status, capacity, schedule, image URL, and room-specific rules.

## Browser security
RLS is the authorization boundary. Hiding buttons in the frontend is not security. The publishable key may be public; privileged keys and secrets must remain server-side.
