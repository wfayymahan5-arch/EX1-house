-- EX1 LEGEND: allow authenticated users to upload and update only their own profile avatars.

CREATE OR REPLACE FUNCTION public.set_my_profile_avatar(p_avatar_path text)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
declare
  v_uid uuid := auth.uid();
begin
  if v_uid is null then raise exception 'برای ذخیره عکس پروفایل ابتدا وارد حساب شو.'; end if;
  if p_avatar_path is null
     or p_avatar_path !~ ('^'||v_uid::text||'/avatar-[0-9a-f-]{36}\.(jpg|png|webp)$') then
    raise exception 'مسیر عکس پروفایل معتبر نیست.';
  end if;

  update public.profiles
  set avatar_url=p_avatar_path
  where id=v_uid;

  if not found then raise exception 'پروفایل حساب پیدا نشد؛ یک‌بار خارج شو و دوباره وارد شو.'; end if;
  return jsonb_build_object('success',true,'avatar_path',p_avatar_path);
end;
$function$


revoke all on function public.set_my_profile_avatar(text) from public, anon;
grant execute on function public.set_my_profile_avatar(text) to authenticated;

create policy "EX1 users upload own avatars" on storage.objects
for insert to authenticated
with check (bucket_id='ex1-avatars' and (storage.foldername(name))[1]=(select auth.uid())::text);

create policy "EX1 users update own avatars" on storage.objects
for update to authenticated
using (bucket_id='ex1-avatars' and (storage.foldername(name))[1]=(select auth.uid())::text)
with check (bucket_id='ex1-avatars' and (storage.foldername(name))[1]=(select auth.uid())::text);

create policy "EX1 users delete own avatars" on storage.objects
for delete to authenticated
using (bucket_id='ex1-avatars' and (storage.foldername(name))[1]=(select auth.uid())::text);
