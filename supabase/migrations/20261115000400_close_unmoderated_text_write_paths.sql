-- Close client-side write paths that could bypass AI moderation.
drop policy if exists "Senders can edit or remove own messages" on public.social_messages;

drop policy if exists "Users create own support conversations" on public.support_conversations;
drop policy if exists "Users send messages in own support conversations" on public.support_messages;

drop policy if exists "Users can publish their own posts" on public.social_posts;
drop policy if exists "Authors or moderators can edit posts" on public.social_posts;

drop policy if exists "Users can publish their own stories" on public.social_stories;
drop policy if exists "Authors or moderators can edit stories" on public.social_stories;

drop policy if exists "Users can comment on visible posts" on public.social_post_comments;
drop policy if exists "Authors or moderators can edit comments" on public.social_post_comments;
-- Trusted Edge Functions using the service role are now the only writers for moderated text content.
