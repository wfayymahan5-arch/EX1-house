-- EX1 LEGEND AI foundation: private moderation queue and separate support assistant.
-- This migration is staged in GitHub only; it is NOT applied to the live Supabase project.

create table if not exists public.ai_moderation_cases (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.profiles(id) on delete cascade,
  content_type text not null check (content_type in ('profile_avatar','team_logo','profile_text','team_description','social_post_text','social_post_image','social_story','chat_message')),
  source_table text,
  source_id uuid,
  original_storage_bucket text,
  original_storage_path text,
  evidence_bucket text,
  evidence_path text,
  content_text text,
  moderation_categories jsonb not null default '{}'::jsonb,
  moderation_scores jsonb not null default '{}'::jsonb,
  severity text not null default 'medium' check (severity in ('low','medium','high','critical')),
  confidence numeric(5,4) check (confidence between 0 and 1),
  model_name text,
  status text not null default 'pending_review' check (status in ('pending_review','warning_approved','ban_approved','rejected','appealed','closed')),
  owner_decision_by uuid references public.profiles(id),
  owner_decision_at timestamptz,
  owner_note text,
  warning_sent_at timestamptz,
  ban_applied_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create index if not exists ai_moderation_cases_status_created_idx
  on public.ai_moderation_cases(status, created_at desc);
create index if not exists ai_moderation_cases_user_created_idx
  on public.ai_moderation_cases(user_id, created_at desc);

alter table public.ai_moderation_cases enable row level security;
drop policy if exists "Only owner can read AI moderation cases" on public.ai_moderation_cases;
create policy "Only owner can read AI moderation cases"
  on public.ai_moderation_cases for select to authenticated
  using (public.is_current_user_owner());
drop policy if exists "Only owner can decide AI moderation cases" on public.ai_moderation_cases;
create policy "Only owner can decide AI moderation cases"
  on public.ai_moderation_cases for update to authenticated
  using (public.is_current_user_owner())
  with check (public.is_current_user_owner());
-- No client INSERT/DELETE policies. Edge Functions using service role create and update cases.

create table if not exists public.support_conversations (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.profiles(id) on delete cascade,
  subject text not null default 'پشتیبانی EX1',
  status text not null default 'open' check (status in ('open','waiting_for_human','resolved','closed')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
create index if not exists support_conversations_user_updated_idx
  on public.support_conversations(user_id, updated_at desc);

create table if not exists public.support_messages (
  id uuid primary key default gen_random_uuid(),
  conversation_id uuid not null references public.support_conversations(id) on delete cascade,
  sender_role text not null check (sender_role in ('user','assistant','human_support','system')),
  body text not null check (char_length(body) between 1 and 6000),
  created_at timestamptz not null default now()
);
create index if not exists support_messages_conversation_created_idx
  on public.support_messages(conversation_id, created_at);

alter table public.support_conversations enable row level security;
alter table public.support_messages enable row level security;

drop policy if exists "Users read own support conversations" on public.support_conversations;
create policy "Users read own support conversations"
  on public.support_conversations for select to authenticated
  using (user_id = auth.uid() or public.is_current_user_owner());
drop policy if exists "Users create own support conversations" on public.support_conversations;
create policy "Users create own support conversations"
  on public.support_conversations for insert to authenticated
  with check (user_id = auth.uid());

drop policy if exists "Users read messages in own support conversations" on public.support_messages;
create policy "Users read messages in own support conversations"
  on public.support_messages for select to authenticated
  using (
    exists (
      select 1 from public.support_conversations c
      where c.id = conversation_id
        and (c.user_id = auth.uid() or public.is_current_user_owner())
    )
  );
drop policy if exists "Users send messages in own support conversations" on public.support_messages;
create policy "Users send messages in own support conversations"
  on public.support_messages for insert to authenticated
  with check (
    sender_role = 'user'
    and exists (
      select 1 from public.support_conversations c
      where c.id = conversation_id and c.user_id = auth.uid()
    )
  );
-- Assistant and human-support messages are written only by trusted server-side code.

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('moderation-evidence','moderation-evidence',false,5242880,
        array['image/jpeg','image/png','image/webp'])
on conflict (id) do update set public = false, file_size_limit = 5242880,
  allowed_mime_types = excluded.allowed_mime_types;

drop policy if exists "Only owner can read moderation evidence" on storage.objects;
create policy "Only owner can read moderation evidence"
  on storage.objects for select to authenticated
  using (bucket_id = 'moderation-evidence' and public.is_current_user_owner());

-- No authenticated upload/update/delete policy is created for moderation evidence.
-- Service-role Edge Functions own evidence storage operations.
