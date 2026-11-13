# EX1 LEGEND — Supabase integration

The live Supabase project is configured in `supabase-config.js` using its **public publishable key**. This key is intended for browser use; database access is still restricted by grants and Row Level Security (RLS).

## Important
- Do not run the old `schema.sql` against the live project. It describes an obsolete first-generation schema and does not match the current database.
- Never put a Supabase secret key or `service_role` key in this repository or browser code.
- The public site now uses Supabase Auth for email/password sign-up and sign-in, reads published tournament rows, and reads the public theme settings.
- New account profile creation is handled by the database trigger in the live project.
- Tournament registration is intentionally not enabled in this first connection step: the current database requires a team captain and a team, so the old one-click individual demo registration was removed.
- Wallet display and financial actions remain demo-only / not connected to real payments.
- The admin page is still a preview. Admin sign-in, role provisioning, finance controls, and owner setup must be completed and tested separately.

## Current live schema
The authoritative schema is the existing Supabase project, not the legacy SQL snapshot in this folder. Tables include profiles, tournaments, teams, team_members, tournament_registrations, tournament_results, admin_users, and site_theme_settings, plus private RBAC and demo-wallet tables.

## Browser security
RLS is the authorization boundary. Hiding buttons in the frontend is not security. The publishable key may be public; privileged keys and secrets must remain server-side.
