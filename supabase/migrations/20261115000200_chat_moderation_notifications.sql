-- Enforce server-mediated chat moderation and add user-facing warning notifications.
drop policy if exists "Conversation members can send messages unless blocked" on public.social_messages;

create table if not exists public.user_notifications (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.profiles(id) on delete cascade,
  notification_type text not null default 'general',
  title text not null,
  body text not null,
  related_case_id uuid references public.ai_moderation_cases(id) on delete set null,
  read_at timestamptz,
  created_at timestamptz not null default now()
);
create index if not exists user_notifications_user_created_idx
  on public.user_notifications(user_id, created_at desc);

alter table public.user_notifications enable row level security;
drop policy if exists "Users read own notifications" on public.user_notifications;
create policy "Users read own notifications"
  on public.user_notifications for select to authenticated
  using (user_id = auth.uid());
drop policy if exists "Users mark own notifications read" on public.user_notifications;
create policy "Users mark own notifications read"
  on public.user_notifications for update to authenticated
  using (user_id = auth.uid())
  with check (user_id = auth.uid());
-- Notification creation is service-role only. No client INSERT policy.
