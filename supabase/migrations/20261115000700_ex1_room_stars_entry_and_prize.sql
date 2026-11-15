-- EX1 LEGEND: room entry Stars and room prize Stars.
-- Entry Stars are charged once per participant, atomically with the registration/queue insert.
-- Prize Stars are stored and displayed as the room's advertised prize; payout policy is separate.

alter table public.tournament_rooms
  add column if not exists prize_demo_points bigint not null default 0;

do $$
begin
  if not exists (
    select 1 from pg_constraint
    where conrelid='public.tournament_rooms'::regclass
      and conname='tournament_rooms_entry_demo_points_nonnegative'
  ) then
    alter table public.tournament_rooms
      add constraint tournament_rooms_entry_demo_points_nonnegative
      check (entry_demo_points >= 0 and entry_demo_points <= 1000000);
  end if;
  if not exists (
    select 1 from pg_constraint
    where conrelid='public.tournament_rooms'::regclass
      and conname='tournament_rooms_prize_demo_points_nonnegative'
  ) then
    alter table public.tournament_rooms
      add constraint tournament_rooms_prize_demo_points_nonnegative
      check (prize_demo_points >= 0 and prize_demo_points <= 1000000000);
  end if;
end
$$;

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
    raise exception 'اطلاعات دریافت استارز ورودی معتبر نیست.';
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
    raise exception 'مقدار استارز ورودی روم معتبر نیست.';
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
    raise exception 'کیف استارز این حساب پیدا نشد.';
  end if;

  select * into v_existing
  from private.wallet_ledger
  where idempotency_key = v_key;

  if found then
    if v_existing.account_id = v_account_id
       and v_existing.entry_type = 'debit'
       and v_existing.amount_units = v_room.entry_demo_points
       and v_existing.reference_type = 'tournament_room'
       and v_existing.reference_id = p_room_id::text then
      return;
    end if;
    raise exception 'هزینه ورودی این روم قبلاً با مقدار دیگری ثبت شده است؛ با پشتیبانی تماس بگیر.';
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
begin
  -- Solo registrations have already been charged at queue insertion.
  if exists (
    select 1
    from private.tournament_room_solo_queue q
    where q.room_id = new.room_id
      and q.team_id = new.team_id
      and q.status = 'matched'
  ) then
    return new;
  end if;

  for v_member in
    select tm.user_id
    from public.team_members tm
    where tm.team_id = new.team_id
    order by tm.user_id
  loop
    perform private.charge_room_entry_stars(new.room_id, v_member.user_id);
  end loop;

  if not found and new.user_id is not null then
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
  'Per-player room entry fee in internal non-cash EX1 Stars.';
comment on column public.tournament_rooms.prize_demo_points is
  'Advertised room prize amount in internal non-cash EX1 Stars; payout is handled separately.';
