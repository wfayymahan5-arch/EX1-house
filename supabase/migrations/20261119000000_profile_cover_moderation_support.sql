-- Allow profile-cover cases and explicit image-removal outcomes in owner moderation.
-- Safe to re-run; preserves every previously accepted value.
alter table public.ai_moderation_cases
  drop constraint if exists ai_moderation_cases_content_type_check;
alter table public.ai_moderation_cases
  add constraint ai_moderation_cases_content_type_check
  check (content_type = any (array[
    'profile_avatar','profile_cover','team_logo','profile_text','team_description',
    'social_post_text','social_post_image','social_story','chat_message'
  ]::text[]));

alter table public.ai_moderation_cases
  drop constraint if exists ai_moderation_cases_status_check;
alter table public.ai_moderation_cases
  add constraint ai_moderation_cases_status_check
  check (status = any (array[
    'pending_review','warning_approved','ban_approved','rejected','appealed','closed',
    'avatar_removed','cover_removed'
  ]::text[]));
