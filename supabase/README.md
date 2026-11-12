# EX1 HOUSE — Supabase setup

This folder contains the first database migration for EX1.

## 1. Create the Supabase project
1. Create a project in the Supabase dashboard.
2. Open **SQL Editor** → **New query**.
3. Copy all of `schema.sql`, paste it into the editor, and run it.
4. In **Project Settings → API**, copy the Project URL and the publishable/anon key.

## 2. Important security rules
- The browser may use only the publishable/anon key.
- Never put the `service_role` or secret key in `index.html`, `admin.html`, GitHub, or any public file.
- Admin authorization is based on the trusted Auth JWT claim `app_metadata.role = admin`; a user-editable profile field is not an admin permission.
- Do not make XP editable from browser-only code. XP awards must be authorized by the database policy or a trusted server function.
- Keep tournaments non-cash and XP as non-monetary experience points.

## 3. What the schema creates
- `profiles`: public profile details linked to Supabase Auth.
- `tournaments`: tournament listings and status.
- `registrations`: user registrations, with one registration per user per tournament.
- `xp_transactions`: an auditable XP ledger.
- `app_settings`: admin-only settings.
- Row Level Security policies for public tournament reading, user-owned records, and admin-only operations.

## 4. Before connecting the website
The website still needs its Supabase URL and publishable key, sign-in/sign-up screens, and server-trusted admin-role provisioning. Do not treat the current `admin.html` preview as a secure admin panel until those steps are complete.
