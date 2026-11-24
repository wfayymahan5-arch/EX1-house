-- EX1 LEGEND referral codes and private attribution records.
-- Codes begin at 4 digits and expand when the current length reaches 90% capacity.
create schema if not exists private;

create table if not exists private.referral_codes (
  user_id uuid primary key references auth.users(id) on delete cascade,
  code text not null unique,
  created_at timestamptz not null default now(),
  constraint referral_codes_length_check check (code ~ '^[0-9]{4,8}$')
);

create table if not exists private.referral_attributions (
  referred_user_id uuid primary key references auth.users(id) on delete cascade,
  referrer_user_id uuid not null references auth.users(id) on delete cascade,
  referral_code text not null,
  created_at timestamptz not null default now(),
  constraint referral_attributions_not_self check (referred_user_id <> referrer_user_id)
);

create index if not exists referral_attributions_referrer_created_idx
  on private.referral_attributions (referrer_user_id, created_at desc);

alter table private.referral_codes enable row level security;
alter table private.referral_attributions enable row level security;
revoke all on table private.referral_codes from public, anon, authenticated;
revoke all on table private.referral_attributions from public, anon, authenticated;

create or replace function private.assign_referral_code(p_user_id uuid)
returns text
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_existing text;
  v_total bigint;
  v_length integer;
  v_code text;
  v_attempt integer;
begin
  if p_user_id is null then
    raise exception 'user id is required';
  end if;

  perform pg_catalog.pg_advisory_xact_lock(
    pg_catalog.hashtextextended('ex1_referral_code_allocation', 0)
  );

  select rc.code into v_existing
  from private.referral_codes rc
  where rc.user_id = p_user_id;

  if v_existing is not null then
    return v_existing;
  end if;

  select count(*) into v_total from private.referral_codes;
  v_length := case
    when v_total < 9000 then 4
    when v_total < 90000 then 5
    when v_total < 900000 then 6
    when v_total < 9000000 then 7
    when v_total < 90000000 then 8
    else 8
  end;

  if v_total >= 90000000 then
    raise exception 'referral code capacity reached';
  end if;

  for v_attempt in 1..200 loop
    v_code := pg_catalog.lpad(
      pg_catalog.floor(pg_catalog.random() * pg_catalog.power(10::numeric, v_length))::bigint::text,
      v_length,
      '0'
    );

    if not exists (select 1 from private.referral_codes rc where rc.code = v_code) then
      insert into private.referral_codes (user_id, code)
      values (p_user_id, v_code);
      return v_code;
    end if;
  end loop;

  raise exception 'could not allocate a unique referral code';
end;
$$;

revoke all on function private.assign_referral_code(uuid) from public, anon, authenticated;

create or replace function private.handle_profile_referral()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_code text;
  v_referrer uuid;
begin
  perform private.assign_referral_code(new.id);

  select nullif(pg_catalog.btrim(u.raw_user_meta_data ->> 'referral_code'), '')
    into v_code
  from auth.users u
  where u.id = new.id;

  if v_code is not null and v_code ~ '^[0-9]{4,8}$' then
    select rc.user_id into v_referrer
    from private.referral_codes rc
    where rc.code = v_code;

    if v_referrer is not null and v_referrer <> new.id then
      insert into private.referral_attributions (
        referred_user_id, referrer_user_id, referral_code
      )
      values (new.id, v_referrer, v_code)
      on conflict (referred_user_id) do nothing;
    end if;
  end if;

  return new;
end;
$$;

revoke all on function private.handle_profile_referral() from public, anon, authenticated;

drop trigger if exists profiles_assign_referral_code on public.profiles;
create trigger profiles_assign_referral_code
after insert on public.profiles
for each row execute function private.handle_profile_referral();

-- Backfill codes for existing accounts without changing their profile data.
do $$
declare
  v_user_id uuid;
begin
  for v_user_id in select p.id from public.profiles p order by p.created_at nulls first, p.id loop
    perform private.assign_referral_code(v_user_id);
  end loop;
end;
$$;

create or replace function public.my_referral_data()
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_user_id uuid := auth.uid();
  v_result jsonb;
begin
  if v_user_id is null then
    raise exception 'authentication required';
  end if;

  select pg_catalog.jsonb_build_object(
    'code', (
      select rc.code
      from private.referral_codes rc
      where rc.user_id = v_user_id
    ),
    'count', (
      select count(*)
      from private.referral_attributions ra
      where ra.referrer_user_id = v_user_id
    ),
    'members', coalesce((
      select pg_catalog.jsonb_agg(
        pg_catalog.jsonb_build_object(
          'display_name', coalesce(nullif(p.display_name, ''), nullif(p.username, ''), 'بازیکن EX1'),
          'username', p.username,
          'created_at', ra.created_at
        )
        order by ra.created_at desc
      )
      from private.referral_attributions ra
      join public.profiles p on p.id = ra.referred_user_id
      where ra.referrer_user_id = v_user_id
    ), '[]'::jsonb)
  ) into v_result;

  return v_result;
end;
$$;

revoke all on function public.my_referral_data() from public, anon;
grant execute on function public.my_referral_data() to authenticated;
