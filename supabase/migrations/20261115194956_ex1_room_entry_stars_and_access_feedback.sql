-- EX1 LEGEND: per-player symbolic Stars entry fees and clearer timed room access.
-- Stars remain non-cash internal points. Fees are charged atomically at signup.

-- Retire the former minimum-balance-only checks; entry Stars are now actually charged.
drop trigger if exists trg_check_room_team_minimum_demo_stars on public.tournament_room_registrations;
drop trigger if exists trg_check_room_solo_minimum_demo_stars on private.tournament_room_solo_queue;
drop function if exists private.check_room_team_minimum_demo_stars();
drop function if exists private.check_room_solo_minimum_demo_stars();
drop function if exists private.assert_room_minimum_demo_stars(uuid, uuid);

create or replace function private.charge_room_entry_stars(
  p_room_id uuid,
  p_user_id uuid
)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_room public.tournament_rooms%rowtype;
  v_account_id uuid;
  v_balance bigint;
  v_existing private.wallet_ledger%rowtype;
  v_key text;
begin
  if p_room_id is null or p_user_id is null then
    raise exception 'اطلاعات هزینه ورودی معتبر نیست.';
  end if;

  select * into v_room
  from public.tournament_rooms
  where id = p_room_id;

  if not found then
    raise exception 'روم پیدا نشد.';
  end if;

  if coalesce(v_room.entry_demo_points, 0) = 0 then
    return;
  end if;

  if v_room.entry_demo_points < 0 or v_room.entry_demo_points > 1000000 then
    raise exception 'هزینه ورودی روم معتبر نیست.';
  end if;

  v_key := 'room-entry:' || p_room_id::text || ':' || p_user_id::text;

  insert into private.wallet_accounts(user_id)
  values (p_user_id)
  on conflict (user_id) do nothing;

  select id into v_account_id
  from private.wallet_accounts
  where user_id = p_user_id
  for update;

  if v_account_id is null then
    raise exception 'حساب استارز این بازیکن پیدا نشد.';
  end if;

  select * into v_existing
  from private.wallet_ledger
  where idempotency_key = v_key;

  if found then
    if v_existing.account_id = v_account_id
       and v_existing.entry_type = 'debit'
       and v_existing.reference_type = 'tournament_room'
       and v_existing.reference_id = p_room_id::text
       and v_existing.amount_units = v_room.entry_demo_points then
      return;
    end if;
    raise exception 'هزینه این روم قبلاً با اطلاعات متفاوت ثبت شده است؛ با پشتیبانی تماس بگیر.';
  end if;

  select coalesce(sum(
    case when entry_type = 'credit' then amount_units else -amount_units end
  ), 0)::bigint
  into v_balance
  from private.wallet_ledger
  where account_id = v_account_id;

  if v_balance < v_room.entry_demo_points then
    raise exception 'برای ورود به این روم % استارز لازم داری؛ موجودی فعلی تو % استارز است.',
      v_room.entry_demo_points, v_balance;
  end if;

  insert into private.wallet_ledger(
    account_id, entry_type, amount_units, balance_after_units,
    reason, idempotency_key, reference_type, reference_id, created_by
  ) values (
    v_account_id, 'debit', v_room.entry_demo_points,
    v_balance - v_room.entry_demo_points,
    'هزینه ورودی روم: ' || v_room.title,
    v_key, 'tournament_room', p_room_id::text, auth.uid()
  );
end;
$$;

revoke all on function private.charge_room_entry_stars(uuid, uuid) from public, anon, authenticated;

create or replace function private.charge_room_team_entry_stars()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_member record;
  v_count integer := 0;
begin
  -- Players joining through the solo queue were charged individually on queue insertion.
  if new.team_id is not null and exists (
    select 1
    from private.tournament_room_solo_queue q
    where q.room_id = new.room_id
      and q.team_id = new.team_id
      and q.status = 'matched'
  ) then
    return new;
  end if;

  if new.team_id is not null then
    for v_member in
      select tm.user_id
      from public.team_members tm
      where tm.team_id = new.team_id
      order by tm.user_id
    loop
      perform private.charge_room_entry_stars(new.room_id, v_member.user_id);
      v_count := v_count + 1;
    end loop;
  end if;

  if v_count = 0 and new.user_id is not null then
    perform private.charge_room_entry_stars(new.room_id, new.user_id);
  end if;

  return new;
end;
$$;

create or replace function private.charge_room_solo_entry_stars()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  perform private.charge_room_entry_stars(new.room_id, new.user_id);
  return new;
end;
$$;

drop trigger if exists trg_charge_room_team_entry_stars on public.tournament_room_registrations;
create trigger trg_charge_room_team_entry_stars
after insert on public.tournament_room_registrations
for each row execute function private.charge_room_team_entry_stars();

drop trigger if exists trg_charge_room_solo_entry_stars on private.tournament_room_solo_queue;
create trigger trg_charge_room_solo_entry_stars
before insert on private.tournament_room_solo_queue
for each row execute function private.charge_room_solo_entry_stars();

revoke all on function private.charge_room_team_entry_stars() from public, anon, authenticated;
revoke all on function private.charge_room_solo_entry_stars() from public, anon, authenticated;

comment on column public.tournament_rooms.entry_demo_points is
  'Per-player room entry fee in symbolic non-cash EX1 Stars; charged once atomically at signup.';

-- Include direct registration ownership and solo players still waiting for a complete team.
create or replace function private.get_my_tournament_room_access(p_room_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $function$
declare
  v_uid uuid := auth.uid();
  v_reg record;
  v_access record;
  v_players jsonb := '[]'::jsonb;
begin
  if v_uid is null then
    raise exception 'برای مشاهده اطلاعات روم ابتدا وارد حساب شو.';
  end if;

  select x.slot_number, x.team_id, x.registered_at,
         coalesce(t.name, x.team_name) as team_name,
         r.id as room_id, r.title as room_title, r.starts_at, r.rules
  into v_reg
  from (
    select reg.slot_number, reg.team_id, reg.registered_at, null::text as team_name
    from public.tournament_room_registrations reg
    where reg.room_id = p_room_id
      and reg.status in ('pending','approved')
      and (
        reg.user_id = v_uid
        or exists (
          select 1 from public.team_members mine
          where mine.team_id = reg.team_id and mine.user_id = v_uid
        )
        or exists (
          select 1 from private.tournament_room_solo_queue mine
          where mine.team_id = reg.team_id and mine.user_id = v_uid and mine.status = 'matched'
        )
      )
    union all
    select q.slot_number, null::uuid as team_id, q.created_at as registered_at,
           'در انتظار تشکیل تیم'::text as team_name
    from private.tournament_room_solo_queue q
    where q.room_id = p_room_id
      and q.user_id = v_uid
      and q.status = 'waiting'
  ) x
  join public.tournament_rooms r on r.id = p_room_id
  left join public.teams t on t.id = x.team_id
  order by x.registered_at desc
  limit 1;

  if not found then
    raise exception 'ثبت‌نام فعالی برای این روم در حساب تو پیدا نشد.';
  end if;

  if v_reg.starts_at is null or v_reg.starts_at > now() then
    return jsonb_build_object(
      'access_ready', false,
      'waiting_for_start', true,
      'starts_at', v_reg.starts_at,
      'room_title', v_reg.room_title,
      'slot_number', v_reg.slot_number,
      'team_id', v_reg.team_id,
      'team_name', v_reg.team_name,
      'solo_queue_waiting', v_reg.team_id is null
    );
  end if;

  select a.room_code, a.room_password, a.published_at
  into v_access
  from private.tournament_room_access a
  where a.room_id = p_room_id;

  if not found then
    return jsonb_build_object(
      'access_ready', false,
      'waiting_for_admin', true,
      'starts_at', v_reg.starts_at,
      'room_title', v_reg.room_title,
      'slot_number', v_reg.slot_number,
      'team_id', v_reg.team_id,
      'team_name', v_reg.team_name,
      'solo_queue_waiting', v_reg.team_id is null
    );
  end if;

  if v_reg.team_id is null then
    select coalesce(jsonb_agg(jsonb_build_object(
      'user_id', p.id, 'display_name', p.display_name,
      'game_name', p.game_name, 'game_uid', p.game_uid, 'avatar_url', p.avatar_url
    ) order by q.created_at, p.display_name), '[]'::jsonb)
    into v_players
    from private.tournament_room_solo_queue q
    join public.profiles p on p.id = q.user_id
    where q.room_id = p_room_id
      and q.slot_number = v_reg.slot_number
      and q.status = 'waiting';
  else
    select coalesce(jsonb_agg(jsonb_build_object(
      'user_id', roster.user_id, 'display_name', roster.display_name,
      'game_name', roster.game_name, 'game_uid', roster.game_uid, 'avatar_url', roster.avatar_url
    ) order by roster.sort_order, roster.display_name), '[]'::jsonb)
    into v_players
    from (
      select p.id as user_id, p.display_name, p.game_name, p.game_uid, p.avatar_url,
             case when tm.role = 'captain' then 0 else 1 end as sort_order
      from public.team_members tm
      join public.profiles p on p.id = tm.user_id
      where tm.team_id = v_reg.team_id
      union all
      select p.id, p.display_name, p.game_name, p.game_uid, p.avatar_url, 2
      from private.tournament_room_solo_queue q
      join public.profiles p on p.id = q.user_id
      where q.team_id = v_reg.team_id and q.status = 'matched'
    ) roster;
  end if;

  return jsonb_build_object(
    'access_ready', true,
    'room_id', p_room_id,
    'room_title', v_reg.room_title,
    'room_code', v_access.room_code,
    'room_password', v_access.room_password,
    'published_at', v_access.published_at,
    'starts_at', v_reg.starts_at,
    'slot_number', v_reg.slot_number,
    'team_id', v_reg.team_id,
    'team_name', v_reg.team_name,
    'rules', v_reg.rules,
    'solo_queue_waiting', v_reg.team_id is null,
    'players', v_players
  );
end;
$function$;

create or replace function private.admin_get_tournament_room_access_status(p_room_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $function$
declare
  v_uid uuid := auth.uid();
  v_starts_at timestamptz;
  v_saved_at timestamptz;
  v_active_registrations integer := 0;
  v_waiting_solo_players integer := 0;
begin
  if v_uid is null or not private.has_permission('rooms.manage') then
    raise exception 'مجوز مشاهده وضعیت انتشار روم را نداری.' using errcode = '42501';
  end if;

  select r.starts_at, a.published_at
  into v_starts_at, v_saved_at
  from public.tournament_rooms r
  left join private.tournament_room_access a on a.room_id = r.id
  where r.id = p_room_id;

  if not found then
    raise exception 'روم پیدا نشد.';
  end if;

  select count(*)::integer into v_active_registrations
  from public.tournament_room_registrations reg
  where reg.room_id = p_room_id
    and reg.status in ('pending','approved');

  select count(*)::integer into v_waiting_solo_players
  from private.tournament_room_solo_queue q
  where q.room_id = p_room_id and q.status = 'waiting';

  return jsonb_build_object(
    'has_credentials', v_saved_at is not null,
    'starts_at', v_starts_at,
    'saved_at', v_saved_at,
    'release_due', v_starts_at is not null and v_starts_at <= now(),
    'active_registration_count', v_active_registrations,
    'waiting_solo_player_count', v_waiting_solo_players,
    'state', case
      when v_saved_at is null then 'missing'
      when v_starts_at is null then 'missing_start'
      when v_starts_at > now() then 'waiting'
      when v_active_registrations = 0 and v_waiting_solo_players = 0 then 'no_participants'
      else 'released'
    end
  );
end;
$function$;

revoke all on function private.get_my_tournament_room_access(uuid) from public, anon;
grant execute on function private.get_my_tournament_room_access(uuid) to authenticated;
revoke all on function private.admin_get_tournament_room_access_status(uuid) from public, anon;
grant execute on function private.admin_get_tournament_room_access_status(uuid) to authenticated;
