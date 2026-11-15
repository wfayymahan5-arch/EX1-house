-- EX1 LEGEND: require a minimum internal Stars balance for room registration.
-- This is an eligibility threshold only: no Stars are deducted or pooled.
-- Room prizes remain separately configured, fixed demo-point awards.

drop trigger if exists trg_charge_room_team_entry_stars on public.tournament_room_registrations;
drop trigger if exists trg_charge_room_solo_entry_stars on private.tournament_room_solo_queue;

drop function if exists private.charge_room_team_entry_stars();
drop function if exists private.charge_room_solo_entry_stars();
drop function if exists private.charge_room_entry_stars(uuid, uuid);

create or replace function private.assert_room_minimum_demo_stars(
  p_room_id uuid,
  p_user_id uuid
)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_required bigint;
  v_account_id uuid;
  v_balance bigint;
begin
  if p_room_id is null or p_user_id is null then
    raise exception 'اطلاعات بررسی استارز معتبر نیست.';
  end if;

  select coalesce(entry_demo_points, 0)::bigint
    into v_required
  from public.tournament_rooms
  where id = p_room_id;

  if not found then
    raise exception 'روم پیدا نشد.';
  end if;

  if v_required <= 0 then
    return;
  end if;

  select id
    into v_account_id
  from private.wallet_accounts
  where user_id = p_user_id
  for update;

  if v_account_id is null then
    v_balance := 0;
  else
    select coalesce(sum(
      case when entry_type = 'credit' then amount_units else -amount_units end
    ), 0)::bigint
      into v_balance
    from private.wallet_ledger
    where account_id = v_account_id;
  end if;

  if v_balance < v_required then
    raise exception 'برای ثبت‌نام در این روم حداقل % استارز لازم داری؛ موجودی فعلی: % استارز.',
      v_required, v_balance;
  end if;
end;
$$;

create or replace function private.check_room_team_minimum_demo_stars()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_member record;
  v_checked integer := 0;
begin
  if new.team_id is not null then
    for v_member in
      select tm.user_id
      from public.team_members tm
      where tm.team_id = new.team_id
      order by tm.user_id
    loop
      perform private.assert_room_minimum_demo_stars(new.room_id, v_member.user_id);
      v_checked := v_checked + 1;
    end loop;
  end if;

  if v_checked = 0 and new.user_id is not null then
    perform private.assert_room_minimum_demo_stars(new.room_id, new.user_id);
  end if;

  return new;
end;
$$;

create or replace function private.check_room_solo_minimum_demo_stars()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  perform private.assert_room_minimum_demo_stars(new.room_id, new.user_id);
  return new;
end;
$$;

drop trigger if exists trg_check_room_team_minimum_demo_stars on public.tournament_room_registrations;
create trigger trg_check_room_team_minimum_demo_stars
before insert on public.tournament_room_registrations
for each row
execute function private.check_room_team_minimum_demo_stars();

drop trigger if exists trg_check_room_solo_minimum_demo_stars on private.tournament_room_solo_queue;
create trigger trg_check_room_solo_minimum_demo_stars
before insert on private.tournament_room_solo_queue
for each row
execute function private.check_room_solo_minimum_demo_stars();

revoke all on function private.assert_room_minimum_demo_stars(uuid, uuid) from public, anon, authenticated;
revoke all on function private.check_room_team_minimum_demo_stars() from public, anon, authenticated;
revoke all on function private.check_room_solo_minimum_demo_stars() from public, anon, authenticated;

comment on column public.tournament_rooms.entry_demo_points is
  'Minimum internal non-cash EX1 Stars balance required per player to register; never deducted.';
