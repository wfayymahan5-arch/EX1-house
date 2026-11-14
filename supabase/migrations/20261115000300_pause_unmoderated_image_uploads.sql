-- Pause direct user image uploads until the private image-review pipeline is available.
-- Existing public assets remain readable; new client-side uploads are blocked.
drop policy if exists "EX1 avatar upload own folder" on storage.objects;
drop policy if exists "EX1 avatar update own folder" on storage.objects;
drop policy if exists "EX1 users upload own social media" on storage.objects;
drop policy if exists "EX1 users update own social media" on storage.objects;
