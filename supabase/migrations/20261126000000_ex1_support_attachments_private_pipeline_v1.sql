-- EX1 private support attachment metadata and Storage access.
-- The files live in the existing private social-media bucket under <user-id>/support/<conversation-id>/.
create table public.support_attachments (
  id uuid primary key default gen_random_uuid(),
  conversation_id uuid not null references public.support_conversations(id) on delete cascade,
  message_id uuid references public.support_messages(id) on delete set null,
  user_id uuid not null references public.profiles(id) on delete cascade,
  storage_path text not null unique,
  mime_type text not null check (mime_type in ('image/jpeg','image/png','image/webp')),
  file_size bigint not null check (file_size > 0 and file_size <= 5242880),
  created_at timestamptz not null default now()
);
create index support_attachments_conversation_created_idx
  on public.support_attachments (conversation_id, created_at);
alter table public.support_attachments enable row level security;
grant select on public.support_attachments to authenticated;
create policy "Users and support managers can view support attachments"
  on public.support_attachments
  for select to authenticated
  using (user_id = (select auth.uid()) or (select private.has_permission('support.manage')));
create policy "EX1 users upload own support attachments"
  on storage.objects
  for insert to authenticated
  with check (
    bucket_id = 'social-media'
    and (storage.foldername(name))[1] = (select auth.uid())::text
    and (storage.foldername(name))[2] = 'support'
    and (storage.foldername(name))[3] ~* '^[0-9a-f-]{36}$'
    and exists (
      select 1 from public.support_conversations c
      where c.id::text = (storage.foldername(name))[3]
        and c.user_id = (select auth.uid())
    )
  );
create policy "EX1 support staff can read support attachments"
  on storage.objects
  for select to authenticated
  using (
    bucket_id = 'social-media'
    and (storage.foldername(name))[2] = 'support'
    and (select private.has_permission('support.manage'))
  );
