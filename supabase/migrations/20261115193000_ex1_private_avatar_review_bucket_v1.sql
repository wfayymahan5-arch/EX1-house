-- Private quarantine bucket for profile avatars awaiting moderation.
insert into storage.buckets (id,name,public,file_size_limit,allowed_mime_types)
values ('ex1-avatar-review','ex1-avatar-review',false,5242880,array['image/jpeg','image/png','image/webp'])
on conflict (id) do update
set public=false,file_size_limit=5242880,allowed_mime_types=excluded.allowed_mime_types;

create policy "EX1 users upload own avatar review files"
on storage.objects for insert to authenticated
with check (
  bucket_id='ex1-avatar-review'
  and (storage.foldername(name))[1]=(select auth.uid())::text
  and lower(storage.extension(name)) in ('jpg','png','webp')
);

create policy "EX1 users delete own avatar review files"
on storage.objects for delete to authenticated
using (
  bucket_id='ex1-avatar-review'
  and (storage.foldername(name))[1]=(select auth.uid())::text
);
