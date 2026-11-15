-- The avatar moderation Edge Function now updates profiles with its service role client.
-- Remove direct execution of this SECURITY DEFINER RPC from public client roles.
revoke all on function public.set_my_profile_avatar(text) from public, anon, authenticated;
