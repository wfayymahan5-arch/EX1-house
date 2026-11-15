-- Prevent charging a second time when a solo queue group becomes a room registration.
-- Solo players were charged when entering the queue; the subsequent team registration
-- represents the same people and must not charge the captain/first player again.
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
  if new.user_id is not null and exists (
    select 1
    from private.tournament_room_solo_queue q
    where q.room_id = new.room_id
      and q.user_id = new.user_id
      and q.status in ('waiting','matched')
  ) then
    return new;
  end if;

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

revoke all on function private.charge_room_team_entry_stars() from public, anon, authenticated;