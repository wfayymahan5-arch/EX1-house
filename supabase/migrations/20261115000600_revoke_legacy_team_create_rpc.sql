-- Disable the legacy authenticated RPC that could bypass the moderated team Edge Function.
revoke execute on function public.team_create(text,text,text) from public, anon, authenticated;
