-- EX1 LEGEND: grant the dedicated support role access only to human-support queues.
-- Users continue to read only their own conversations. Support managers can read
-- waiting_for_human conversations and their messages; replies are sent by the
-- JWT-verified reply-support Edge Function after checking support.manage.

drop policy if exists "Support managers can view waiting conversations" on public.support_conversations;
create policy "Support managers can view waiting conversations"
on public.support_conversations
for select
to authenticated
using (
  status = 'waiting_for_human'
  and private.has_permission('support.manage')
);

drop policy if exists "Support managers can view waiting conversation messages" on public.support_messages;
create policy "Support managers can view waiting conversation messages"
on public.support_messages
for select
to authenticated
using (
  exists (
    select 1
    from public.support_conversations c
    where c.id = support_messages.conversation_id
      and c.status = 'waiting_for_human'
      and private.has_permission('support.manage')
  )
);

create index if not exists support_conversations_waiting_updated_idx
on public.support_conversations(updated_at desc)
where status = 'waiting_for_human';

create index if not exists support_messages_conversation_created_idx
on public.support_messages(conversation_id, created_at desc);
