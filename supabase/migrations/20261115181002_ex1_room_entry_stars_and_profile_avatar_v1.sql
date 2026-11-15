-- EX1 LEGEND: symbolic Stars room entry debit and user-owned profile avatar storage.

CREATE OR REPLACE FUNCTION private.charge_room_entry(p_user_id uuid, p_room_id uuid, p_amount bigint, p_room_title text)
 RETURNS bigint
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
declare
  v_account_id uuid;
  v_balance bigint;
  v_new_balance bigint;
  v_existing private.wallet_ledger%rowtype;
  v_key text := 'room-entry:' || p_room_id::text || ':' || p_user_id::text;
  v_name text;
begin
  if p_amount is null or p_amount < 0 or p_amount > 1000000000 then
    raise exception 'مقدار ورودی روم معتبر نیست.';
  end if;
  if p_amount = 0 then return 0; end if;

  select a.id into v_account_id
  from private.wallet_accounts a
  where a.user_id = p_user_id
  for update;

  if v_account_id is null then
    raise exception 'کیف پول بازیکن پیدا نشد؛ با پشتیبانی تماس بگیر.';
  end if;

  select * into v_existing
  from private.wallet_ledger l
  where l.idempotency_key = v_key;

  if found then
    if v_existing.account_id = v_account_id
       and v_existing.entry_type = 'debit'
       and v_existing.amount_units = p_amount then
      return v_existing.balance_after_units;
    end if;
    raise exception 'تراکنش ورودی این روم قبلاً با اطلاعات متفاوت ثبت شده است.';
  end if;

  select coalesce(sum(case when l.entry_type = 'credit'
    then l.amount_units else -l.amount_units end), 0)::bigint
  into v_balance
  from private.wallet_ledger l
  where l.account_id = v_account_id;

  if v_balance < p_amount then
    select coalesce(p.display_name, p.username, 'بازیکن') into v_name
    from public.profiles p where p.id = p_user_id;
    raise exception 'موجودی استارز % کافی نیست؛ ورودی این روم % است و موجودی فعلی % است.',
      coalesce(v_name, 'بازیکن'), p_amount, v_balance;
  end if;

  v_new_balance := v_balance - p_amount;

  insert into private.wallet_ledger(
    account_id, entry_type, amount_units, balance_after_units, reason,
    idempotency_key, reference_type, reference_id, created_by
  ) values (
    v_account_id, 'debit', p_amount, v_new_balance,
    left('ورودی روم: ' || coalesce(p_room_title, p_room_id::text), 300),
    v_key, 'tournament_room', p_room_id::text, auth.uid()
  );

  return v_new_balance;
end;
$function$

CREATE OR REPLACE FUNCTION private.register_for_tournament_room_solo_with_stars(p_room_id uuid, p_slot_number integer)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
declare
  v_uid uuid := auth.uid();
  v_room public.tournament_rooms%rowtype;
  v_existing private.tournament_room_solo_queue%rowtype;
  v_team_size integer;
  v_max_teams integer;
  v_waiting_count integer;
  v_group_ids uuid[];
  v_team_id uuid;
  v_team_name text;
  v_result jsonb;
begin
  if v_uid is null then raise exception 'برای ثبت‌نام ابتدا وارد حساب EX1 شو.'; end if;
  if exists(select 1 from public.user_bans b where b.user_id=v_uid) then
    raise exception 'دسترسی این حساب به EX1 مسدود شده است.';
  end if;

  select * into v_room from public.tournament_rooms where id=p_room_id for update;
  if not found then raise exception 'روم پیدا نشد.'; end if;

  v_team_size := greatest(2,least(coalesce(v_room.team_size,4),4));
  v_max_teams := least(greatest(coalesce(v_room.max_teams,1),1),25);

  select * into v_existing
  from private.tournament_room_solo_queue q
  where q.room_id=p_room_id and q.user_id=v_uid
  order by q.created_at desc limit 1;

  if found then
    if v_existing.status='matched' then
      select t.name into v_team_name from public.teams t where t.id=v_existing.team_id;
      return jsonb_build_object('status','matched','slot_number',v_existing.slot_number,
        'team_name',v_team_name,'team_size',v_team_size,'players_waiting',0,'players_needed',0,
        'entry_demo_points',coalesce(v_room.entry_demo_points,0),'charged_stars',0);
    elsif v_existing.status='waiting' then
      if v_room.status<>'open' or (v_room.starts_at is not null and v_room.starts_at<=now()) then
        update private.tournament_room_solo_queue q set status='cancelled' where q.id=v_existing.id;
        return jsonb_build_object('status','cancelled','registered',false);
      end if;
      if v_existing.slot_number is distinct from p_slot_number then
        raise exception 'قبلاً در جایگاه % ثبت‌نام کرده‌ای؛ برای ادامه همان جایگاه را انتخاب کن.',
          coalesce(v_existing.slot_number,1);
      end if;
      select count(*) into v_waiting_count
      from private.tournament_room_solo_queue q
      where q.room_id=p_room_id and q.slot_number=v_existing.slot_number and q.status='waiting';
      return jsonb_build_object('status','waiting','slot_number',v_existing.slot_number,
        'team_size',v_team_size,'players_waiting',v_waiting_count,
        'players_needed',greatest(0,v_team_size-v_waiting_count),
        'entry_demo_points',coalesce(v_room.entry_demo_points,0),'charged_stars',0);
    else
      raise exception 'ثبت‌نام قبلی این حساب لغو شده است؛ با پشتیبانی روم تماس بگیر.';
    end if;
  end if;

  if v_room.status<>'open' then raise exception 'ثبت‌نام این روم باز نیست یا ظرفیت آن تکمیل شده است.'; end if;
  if v_room.starts_at is not null and v_room.starts_at<=now() then
    raise exception 'مهلت ثبت‌نام این روم به پایان رسیده است.';
  end if;
  if not exists(
    select 1 from public.tournaments t
    where t.id=v_room.tournament_id and t.status in ('published','registration_open')
  ) then raise exception 'این مسابقه منتشر نشده یا ثبت‌نام آن بسته شده است.'; end if;

  if p_slot_number is null or p_slot_number<1 or p_slot_number>v_max_teams then
    raise exception 'شماره جایگاه انتخاب‌شده معتبر نیست.';
  end if;

  if exists(
    select 1 from public.team_members tm
    join public.tournament_room_registrations r on r.team_id=tm.team_id
    where tm.user_id=v_uid and r.room_id=p_room_id and r.status in ('pending','approved')
  ) or exists(
    select 1 from public.tournament_room_registrations r
    where r.room_id=p_room_id and r.user_id=v_uid and r.status in ('pending','approved')
  ) then raise exception 'این حساب قبلاً با یک تیم در همین روم ثبت‌نام شده است.'; end if;

  if exists(
    select 1 from public.tournament_room_registrations r
    where r.room_id=p_room_id and r.slot_number=p_slot_number and r.status in ('pending','approved')
  ) then raise exception 'این جایگاه قبلاً تکمیل شده است؛ جایگاه دیگری انتخاب کن.'; end if;

  select count(*) into v_waiting_count
  from private.tournament_room_solo_queue q
  where q.room_id=p_room_id and q.slot_number=p_slot_number and q.status='waiting';
  if v_waiting_count>=v_team_size then raise exception 'این جایگاه در حال تکمیل نهایی است؛ دوباره تلاش کن.'; end if;

  -- Charge this player before queue insertion. The whole transaction rolls back if registration fails.
  perform private.charge_room_entry(v_uid,p_room_id,v_room.entry_demo_points,v_room.title);

  insert into private.tournament_room_solo_queue(room_id,user_id,status,slot_number)
  values(p_room_id,v_uid,'waiting',p_slot_number)
  on conflict(room_id,user_id) do nothing;

  if not found then raise exception 'این حساب قبلاً برای همین روم ثبت‌نام کرده است.'; end if;

  select array_agg(x.user_id order by x.created_at,x.id) into v_group_ids
  from (
    select q.user_id,q.created_at,q.id
    from private.tournament_room_solo_queue q
    where q.room_id=p_room_id and q.slot_number=p_slot_number and q.status='waiting'
    order by q.created_at,q.id
    limit v_team_size
    for update
  ) x;

  if coalesce(array_length(v_group_ids,1),0)=v_team_size then
    v_team_name := 'EX1 Solo '||upper(substr(replace(gen_random_uuid()::text,'-',''),1,6));
    insert into public.teams(name,tag,captain_id,description)
    values(v_team_name,null,null,'تیم خودکار تشکیل‌شده از ثبت‌نام انفرادی EX1')
    returning id into v_team_id;

    insert into public.tournament_registrations(tournament_id,team_id,status)
    values(v_room.tournament_id,v_team_id,'pending');

    insert into public.tournament_room_registrations(
      room_id,tournament_id,team_id,user_id,slot_number,status
    ) values(p_room_id,v_room.tournament_id,v_team_id,v_group_ids[1],p_slot_number,'pending');

    update private.tournament_room_solo_queue
    set status='matched',team_id=v_team_id,slot_number=p_slot_number,matched_at=now()
    where room_id=p_room_id and user_id=any(v_group_ids) and status='waiting';

    if (
      select count(*) from public.tournament_room_registrations r
      where r.room_id=p_room_id and r.status in ('pending','approved')
    )>=v_max_teams then
      update public.tournament_rooms set status='full',updated_at=now() where id=p_room_id;
    end if;

    select jsonb_build_object('status','matched','slot_number',p_slot_number,
      'team_name',v_team_name,'team_size',v_team_size,'players_waiting',0,'players_needed',0,
      'entry_demo_points',coalesce(v_room.entry_demo_points,0),'charged_stars',coalesce(v_room.entry_demo_points,0))
    into v_result;
    return v_result;
  end if;

  select count(*) into v_waiting_count
  from private.tournament_room_solo_queue q
  where q.room_id=p_room_id and q.slot_number=p_slot_number and q.status='waiting';

  return jsonb_build_object('status','waiting','slot_number',p_slot_number,
    'team_size',v_team_size,'players_waiting',v_waiting_count,
    'players_needed',greatest(0,v_team_size-v_waiting_count),
    'entry_demo_points',coalesce(v_room.entry_demo_points,0),'charged_stars',coalesce(v_room.entry_demo_points,0));
end;
$function$

CREATE OR REPLACE FUNCTION private.register_for_tournament_room_with_stars(p_room_id uuid, p_slot_number integer)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
declare
  v_uid uuid := auth.uid();
  v_room public.tournament_rooms%rowtype;
  v_team public.teams%rowtype;
  v_team_id uuid;
  v_member_count integer;
  v_max_teams integer;
  v_member record;
  v_charged integer := 0;
begin
  if v_uid is null then raise exception 'برای ثبت‌نام ابتدا وارد حساب شو.'; end if;
  if exists(select 1 from public.user_bans b where b.user_id=v_uid) then
    raise exception 'دسترسی این حساب به EX1 مسدود شده است.';
  end if;

  select * into v_room from public.tournament_rooms where id=p_room_id for update;
  if not found then raise exception 'روم پیدا نشد.'; end if;
  if v_room.status <> 'open' then raise exception 'ثبت‌نام این روم باز نیست یا ظرفیت آن تکمیل شده است.'; end if;
  if v_room.starts_at is not null and v_room.starts_at <= now() then
    raise exception 'مهلت ثبت‌نام این روم به پایان رسیده است.';
  end if;
  if not exists(
    select 1 from public.tournaments t
    where t.id=v_room.tournament_id and t.status in ('published','registration_open')
  ) then raise exception 'این مسابقه منتشر نشده یا امکان ثبت‌نام آن بسته شده است.'; end if;

  v_max_teams := least(greatest(coalesce(v_room.max_teams,1),1),25);
  if p_slot_number is null or p_slot_number < 1 or p_slot_number > v_max_teams then
    raise exception 'شماره جایگاه انتخاب‌شده معتبر نیست؛ دوباره یک جایگاه آزاد انتخاب کن.';
  end if;

  if exists(
    select 1 from private.tournament_room_solo_queue q
    where q.room_id=p_room_id and q.user_id=v_uid and q.status in ('waiting','matched')
  ) then raise exception 'این حساب قبلاً به‌صورت انفرادی در همین روم ثبت‌نام کرده است.'; end if;

  select t.* into v_team
  from public.teams t
  join public.team_members tm on tm.team_id=t.id
  where tm.user_id=v_uid and t.captain_id=v_uid
  order by t.created_at
  limit 1
  for update of t;

  if v_team.id is null then raise exception 'برای ثبت‌نام با تیم کامل باید کاپیتان یک تیم باشی.'; end if;
  v_team_id := v_team.id;

  select count(*) into v_member_count from public.team_members tm where tm.team_id=v_team_id;
  if v_member_count <> v_room.team_size then
    raise exception 'این روم به تیم % نفره نیاز دارد؛ تیم تو اکنون % عضو دارد.',v_room.team_size,v_member_count;
  end if;

  if exists(
    select 1 from public.tournament_room_registrations r
    where r.room_id=p_room_id and r.team_id=v_team_id and r.status in ('pending','approved')
  ) then raise exception 'تیمت قبلاً در این روم ثبت‌نام کرده است.'; end if;

  if exists(
    select 1
    from public.team_members tm
    join private.tournament_room_solo_queue q on q.user_id=tm.user_id
    where tm.team_id=v_team_id and q.room_id=p_room_id and q.status in ('waiting','matched')
  ) then raise exception 'یکی از اعضای تیم قبلاً به‌صورت انفرادی در این روم ثبت‌نام کرده است.'; end if;

  if exists(
    select 1 from public.tournament_room_registrations r
    where r.room_id=p_room_id and r.slot_number=p_slot_number and r.status in ('pending','approved')
  ) or exists(
    select 1 from private.tournament_room_solo_queue q
    where q.room_id=p_room_id and q.slot_number=p_slot_number and q.status='waiting'
  ) then raise exception 'این جایگاه قبلاً رزرو شده؛ یک جایگاه آزاد دیگر انتخاب کن.'; end if;

  -- Charge each team member, in stable order. Any failure rolls back every debit and registration.
  for v_member in
    select tm.user_id from public.team_members tm
    where tm.team_id=v_team_id order by tm.user_id
  loop
    perform private.charge_room_entry(v_member.user_id,p_room_id,v_room.entry_demo_points,v_room.title);
    v_charged := v_charged + case when coalesce(v_room.entry_demo_points,0)>0 then 1 else 0 end;
  end loop;

  insert into public.tournament_registrations(tournament_id,team_id,status)
  values(v_room.tournament_id,v_team_id,'pending')
  on conflict(tournament_id,team_id) do nothing;

  insert into public.tournament_room_registrations(
    room_id,tournament_id,team_id,user_id,slot_number,status
  ) values (
    p_room_id,v_room.tournament_id,v_team_id,v_uid,p_slot_number,'pending'
  );

  if (
    select count(*) from public.tournament_room_registrations r
    where r.room_id=p_room_id and r.status in ('pending','approved')
  ) >= v_max_teams then
    update public.tournament_rooms set status='full',updated_at=now() where id=p_room_id;
  end if;

  return jsonb_build_object(
    'success',true,'slot_number',p_slot_number,'team_name',v_team.name,
    'status','pending','entry_demo_points',coalesce(v_room.entry_demo_points,0),
    'charged_players',v_charged
  );
end;
$function$

CREATE OR REPLACE FUNCTION public.register_for_tournament_room_solo_with_stars(p_room_id uuid, p_slot_number integer)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
begin
  return private.register_for_tournament_room_solo_with_stars(p_room_id,p_slot_number);
end;
$function$

CREATE OR REPLACE FUNCTION public.register_for_tournament_room_with_stars(p_room_id uuid, p_slot_number integer)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
begin
  return private.register_for_tournament_room_with_stars(p_room_id,p_slot_number);
end;
$function$

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


revoke all on function private.charge_room_entry(uuid,uuid,bigint,text) from public, anon, authenticated;
revoke all on function private.register_for_tournament_room_with_stars(uuid,integer) from public, anon, authenticated;
revoke all on function private.register_for_tournament_room_solo_with_stars(uuid,integer) from public, anon, authenticated;
revoke all on function public.register_for_tournament_room_with_stars(uuid,integer) from public, anon;
revoke all on function public.register_for_tournament_room_solo_with_stars(uuid,integer) from public, anon;
grant execute on function public.register_for_tournament_room_with_stars(uuid,integer) to authenticated;
grant execute on function public.register_for_tournament_room_solo_with_stars(uuid,integer) to authenticated;
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
