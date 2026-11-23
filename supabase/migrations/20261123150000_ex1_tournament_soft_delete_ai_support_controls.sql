-- EX1 LEGEND: reversible tournament removal and owner-managed support rules.
-- No room, registration, result, or symbolic-points rows are deleted or modified.

alter table public.tournaments
  add column if not exists deleted_at timestamptz,
  add column if not exists deleted_by uuid references public.profiles(id) on delete set null,
  add column if not exists deleted_previous_status text;

create index if not exists tournaments_deleted_at_idx
  on public.tournaments (deleted_at, created_at desc)
  where deleted_at is not null;

create table if not exists private.tournament_audit_logs (
  id uuid primary key default gen_random_uuid(),
  tournament_id uuid not null,
  actor_id uuid not null,
  action text not null check (action in ('soft_delete','restore')),
  reason text,
  details jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now()
);
alter table private.tournament_audit_logs enable row level security;
revoke all on table private.tournament_audit_logs from public, anon, authenticated;

drop policy if exists "Public and authorized admins can view tournaments" on public.tournaments;
create policy "Public and authorized admins can view tournaments"
on public.tournaments
for select
using (
  deleted_at is null
  and (
    status = any (array['published','registration_open','ongoing','completed']::text[])
    or (
      auth.uid() is not null
      and (
        private.has_permission('tournaments.view')
        or private.has_permission('tournaments.create')
        or private.has_permission('tournaments.update')
        or private.has_permission('tournaments.delete')
        or private.has_permission('rooms.view')
        or private.has_permission('rooms.manage')
      )
    )
  )
);

drop policy if exists "Authorized staff can update tournaments" on public.tournaments;
create policy "Authorized staff can update tournaments"
on public.tournaments
for update
using (deleted_at is null and private.has_permission('tournaments.update'))
with check (deleted_at is null and private.has_permission('tournaments.update'));

drop policy if exists "Authorized staff can delete tournaments" on public.tournaments;
revoke delete on table public.tournaments from anon, authenticated;

create or replace function private.admin_dashboard_tournaments_impl()
returns table(
  id uuid,
  title text,
  description text,
  game text,
  mode text,
  map text,
  status text,
  max_teams integer,
  starts_at timestamptz,
  created_at timestamptz
)
language plpgsql
security definer
set search_path = ''
as $function$
begin
  if auth.uid() is null or not private.has_permission('tournaments.view') then
    raise exception 'Permission denied' using errcode = '42501';
  end if;

  return query
    select t.id,t.title,t.description,t.game,t.mode,t.map,t.status,
           t.max_teams,t.starts_at,t.created_at
    from public.tournaments t
    where t.deleted_at is null
    order by t.created_at desc
    limit 200;
end;
$function$;

create or replace function public.admin_soft_delete_tournament(
  p_tournament_id uuid,
  p_reason text default null
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $function$
declare
  v_t public.tournaments%rowtype;
  v_actor uuid := auth.uid();
begin
  if v_actor is null or not private.has_permission('tournaments.delete') then
    raise exception 'Permission denied' using errcode = '42501';
  end if;
  if p_reason is not null and char_length(btrim(p_reason)) > 300 then
    raise exception 'Reason is too long' using errcode = '22023';
  end if;

  update public.tournaments
     set deleted_at = now(),
         deleted_by = v_actor,
         deleted_previous_status = status
   where id = p_tournament_id
     and deleted_at is null
  returning * into v_t;

  if not found then
    raise exception 'Tournament not found or already deleted' using errcode = 'P0002';
  end if;

  insert into private.tournament_audit_logs(tournament_id,actor_id,action,reason,details)
  values (v_t.id,v_actor,'soft_delete',nullif(btrim(p_reason),''),jsonb_build_object('previous_status',v_t.status));

  return jsonb_build_object('success',true,'tournament_id',v_t.id,'deleted_at',v_t.deleted_at);
end;
$function$;

create or replace function public.admin_restore_tournament(p_tournament_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $function$
declare
  v_t public.tournaments%rowtype;
  v_actor uuid := auth.uid();
begin
  if v_actor is null or not private.has_permission('tournaments.delete') then
    raise exception 'Permission denied' using errcode = '42501';
  end if;

  update public.tournaments
     set status = coalesce(deleted_previous_status,status),
         deleted_at = null,
         deleted_by = null,
         deleted_previous_status = null
   where id = p_tournament_id
     and deleted_at is not null
  returning * into v_t;

  if not found then
    raise exception 'Deleted tournament not found' using errcode = 'P0002';
  end if;

  insert into private.tournament_audit_logs(tournament_id,actor_id,action,details)
  values (v_t.id,v_actor,'restore',jsonb_build_object('restored_status',v_t.status));

  return jsonb_build_object('success',true,'tournament_id',v_t.id,'status',v_t.status);
end;
$function$;

create or replace function public.admin_dashboard_deleted_tournaments()
returns table(
  id uuid,
  title text,
  game text,
  status text,
  deleted_at timestamptz,
  deleted_by uuid,
  deleted_previous_status text
)
language plpgsql
security definer
set search_path = ''
as $function$
begin
  if auth.uid() is null or not private.has_permission('tournaments.delete') then
    raise exception 'Permission denied' using errcode = '42501';
  end if;

  return query
    select t.id,t.title,t.game,t.status,t.deleted_at,t.deleted_by,t.deleted_previous_status
    from public.tournaments t
    where t.deleted_at is not null
    order by t.deleted_at desc
    limit 200;
end;
$function$;

revoke all on function public.admin_soft_delete_tournament(uuid,text) from public, anon;
revoke all on function public.admin_restore_tournament(uuid) from public, anon;
revoke all on function public.admin_dashboard_deleted_tournaments() from public, anon;
grant execute on function public.admin_soft_delete_tournament(uuid,text) to authenticated;
grant execute on function public.admin_restore_tournament(uuid) to authenticated;
grant execute on function public.admin_dashboard_deleted_tournaments() to authenticated;

create table if not exists private.ai_support_settings (
  singleton boolean primary key default true check (singleton is true),
  enabled boolean not null default true,
  allow_faq_answers boolean not null default true,
  allow_handoff boolean not null default true,
  welcome_message text not null default 'سلام! 👋 خوش اومدی به پشتیبانی EX1 LEGEND. درباره ورود، ثبت‌نام، روم‌ها، قوانین یا پروفایلت سؤالی داری؟',
  fallback_message text not null default 'پیامت ثبت شد، اما برای این سؤال پاسخ مطمئن و تأییدشده‌ای در راهنمای من ندارم. اگر امکان ارجاع فعال باشد، درخواستت برای پشتیبان انسانی فرستاده می‌شود.',
  updated_by uuid references public.profiles(id) on delete set null,
  updated_at timestamptz not null default now()
);
insert into private.ai_support_settings(singleton)
values (true)
on conflict (singleton) do nothing;

create table if not exists private.ai_support_faq_rules (
  id uuid primary key default gen_random_uuid(),
  trigger_terms text[] not null,
  answer text not null,
  priority integer not null default 100,
  is_active boolean not null default true,
  created_by uuid references public.profiles(id) on delete set null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint ai_support_faq_terms_count check (cardinality(trigger_terms) between 1 and 12),
  constraint ai_support_faq_answer_length check (char_length(btrim(answer)) between 1 and 3000)
);
create index if not exists ai_support_faq_active_priority_idx
  on private.ai_support_faq_rules(is_active,priority desc,created_at desc);

create table if not exists private.ai_support_event_logs (
  id uuid primary key default gen_random_uuid(),
  conversation_id uuid,
  user_id uuid,
  event_type text not null check (event_type in ('auto_answer','faq_match','human_handoff','ai_disabled','settings_updated','faq_rule_saved','faq_rule_disabled')),
  details jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now()
);
create index if not exists ai_support_event_logs_created_at_idx
  on private.ai_support_event_logs(created_at desc);
alter table private.ai_support_settings enable row level security;
alter table private.ai_support_faq_rules enable row level security;
alter table private.ai_support_event_logs enable row level security;
revoke all on table private.ai_support_settings, private.ai_support_faq_rules, private.ai_support_event_logs
  from public, anon, authenticated;

create or replace function public.ai_support_runtime_config()
returns jsonb
language sql
stable
security definer
set search_path = ''
as $function$
  select jsonb_build_object(
    'settings',
    coalesce((
      select jsonb_build_object(
        'enabled',s.enabled,
        'allow_faq_answers',s.allow_faq_answers,
        'allow_handoff',s.allow_handoff,
        'welcome_message',s.welcome_message,
        'fallback_message',s.fallback_message
      )
      from private.ai_support_settings s
      where s.singleton is true
    ), '{}'::jsonb),
    'faq_rules',
    coalesce((
      select jsonb_agg(
        jsonb_build_object(
          'id',f.id,
          'trigger_terms',f.trigger_terms,
          'answer',f.answer,
          'priority',f.priority,
          'is_active',f.is_active
        )
        order by f.priority desc,f.created_at desc
      )
      from private.ai_support_faq_rules f
      where f.is_active is true
    ), '[]'::jsonb)
  );
$function$;

create or replace function public.ai_support_log_event(
  p_conversation_id uuid,
  p_user_id uuid,
  p_event_type text,
  p_details jsonb default '{}'::jsonb
)
returns void
language plpgsql
security definer
set search_path = ''
as $function$
begin
  if p_event_type not in ('auto_answer','faq_match','human_handoff','ai_disabled') then
    raise exception 'Invalid AI event type' using errcode = '22023';
  end if;
  insert into private.ai_support_event_logs(conversation_id,user_id,event_type,details)
  values (p_conversation_id,p_user_id,p_event_type,coalesce(p_details,'{}'::jsonb));
end;
$function$;

create or replace function public.admin_ai_get_settings()
returns jsonb
language plpgsql
security definer
set search_path = ''
as $function$
declare v_settings jsonb;
begin
  if auth.uid() is null or not private.is_current_user_owner() then
    raise exception 'Owner access required' using errcode = '42501';
  end if;
  select to_jsonb(s) - 'singleton'
    into v_settings
    from private.ai_support_settings s
   where s.singleton is true;
  return coalesce(v_settings,'{}'::jsonb);
end;
$function$;

create or replace function public.admin_ai_save_settings(
  p_enabled boolean,
  p_allow_faq_answers boolean,
  p_allow_handoff boolean,
  p_welcome_message text,
  p_fallback_message text
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $function$
declare v_settings jsonb;
begin
  if auth.uid() is null or not private.is_current_user_owner() then
    raise exception 'Owner access required' using errcode = '42501';
  end if;
  if char_length(btrim(coalesce(p_welcome_message,''))) not between 1 and 1200
     or char_length(btrim(coalesce(p_fallback_message,''))) not between 1 and 1200 then
    raise exception 'Messages must be between 1 and 1200 characters' using errcode = '22023';
  end if;

  insert into private.ai_support_settings(
    singleton,enabled,allow_faq_answers,allow_handoff,welcome_message,fallback_message,updated_by,updated_at
  )
  values (
    true,p_enabled,p_allow_faq_answers,p_allow_handoff,
    btrim(p_welcome_message),btrim(p_fallback_message),auth.uid(),now()
  )
  on conflict (singleton) do update set
    enabled=excluded.enabled,
    allow_faq_answers=excluded.allow_faq_answers,
    allow_handoff=excluded.allow_handoff,
    welcome_message=excluded.welcome_message,
    fallback_message=excluded.fallback_message,
    updated_by=excluded.updated_by,
    updated_at=now()
  returning jsonb_build_object('enabled',enabled,'allow_faq_answers',allow_faq_answers,'allow_handoff',allow_handoff,'welcome_message',welcome_message,'fallback_message',fallback_message,'updated_by',updated_by,'updated_at',updated_at) into v_settings;

  insert into private.ai_support_event_logs(event_type,user_id,details)
  values ('settings_updated',auth.uid(),jsonb_build_object(
    'enabled',p_enabled,
    'allow_faq_answers',p_allow_faq_answers,
    'allow_handoff',p_allow_handoff
  ));

  return coalesce(v_settings,'{}'::jsonb) - 'singleton';
end;
$function$;

create or replace function public.admin_ai_list_faq_rules()
returns jsonb
language plpgsql
security definer
set search_path = ''
as $function$
begin
  if auth.uid() is null or not private.is_current_user_owner() then
    raise exception 'Owner access required' using errcode = '42501';
  end if;
  return coalesce((
    select jsonb_agg(to_jsonb(f) order by f.priority desc,f.created_at desc)
    from private.ai_support_faq_rules f
  ),'[]'::jsonb);
end;
$function$;

create or replace function public.admin_ai_upsert_faq_rule(
  p_id uuid,
  p_trigger_terms text[],
  p_answer text,
  p_priority integer,
  p_is_active boolean
)
returns uuid
language plpgsql
security definer
set search_path = ''
as $function$
declare
  v_id uuid;
  v_terms text[];
begin
  if auth.uid() is null or not private.is_current_user_owner() then
    raise exception 'Owner access required' using errcode = '42501';
  end if;
  select array_agg(distinct lower(btrim(term)))
    into v_terms
    from unnest(coalesce(p_trigger_terms,'{}'::text[])) as term
   where char_length(btrim(term)) between 2 and 80;
  if coalesce(cardinality(v_terms),0) not between 1 and 12 then
    raise exception 'Provide between 1 and 12 valid trigger terms' using errcode = '22023';
  end if;
  if char_length(btrim(coalesce(p_answer,''))) not between 1 and 3000 then
    raise exception 'Answer must be between 1 and 3000 characters' using errcode = '22023';
  end if;

  if p_id is null then
    insert into private.ai_support_faq_rules(trigger_terms,answer,priority,is_active,created_by)
    values (v_terms,btrim(p_answer),greatest(0,least(coalesce(p_priority,100),1000)),coalesce(p_is_active,true),auth.uid())
    returning id into v_id;
  else
    update private.ai_support_faq_rules
       set trigger_terms=v_terms,
           answer=btrim(p_answer),
           priority=greatest(0,least(coalesce(p_priority,100),1000)),
           is_active=coalesce(p_is_active,true),
           updated_at=now()
     where id=p_id
    returning id into v_id;
    if v_id is null then
      raise exception 'FAQ rule not found' using errcode = 'P0002';
    end if;
  end if;

  insert into private.ai_support_event_logs(event_type,user_id,details)
  values ('faq_rule_saved',auth.uid(),jsonb_build_object('rule_id',v_id,'active',coalesce(p_is_active,true)));
  return v_id;
end;
$function$;

create or replace function public.admin_ai_delete_faq_rule(p_id uuid)
returns boolean
language plpgsql
security definer
set search_path = ''
as $function$
begin
  if auth.uid() is null or not private.is_current_user_owner() then
    raise exception 'Owner access required' using errcode = '42501';
  end if;
  update private.ai_support_faq_rules
     set is_active=false,updated_at=now()
   where id=p_id;
  if not found then return false; end if;
  insert into private.ai_support_event_logs(event_type,user_id,details)
  values ('faq_rule_disabled',auth.uid(),jsonb_build_object('rule_id',p_id));
  return true;
end;
$function$;

create or replace function public.admin_ai_list_logs(p_limit integer default 100)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $function$
begin
  if auth.uid() is null or not private.is_current_user_owner() then
    raise exception 'Owner access required' using errcode = '42501';
  end if;
  return coalesce((
    select jsonb_agg(to_jsonb(x) order by x.created_at desc)
    from (
      select id,conversation_id,user_id,event_type,details,created_at
      from private.ai_support_event_logs
      order by created_at desc
      limit greatest(1,least(coalesce(p_limit,100),200))
    ) x
  ),'[]'::jsonb);
end;
$function$;

revoke all on function public.ai_support_runtime_config() from public, anon, authenticated;
revoke all on function public.ai_support_log_event(uuid,uuid,text,jsonb) from public, anon, authenticated;
grant execute on function public.ai_support_runtime_config() to service_role;
grant execute on function public.ai_support_log_event(uuid,uuid,text,jsonb) to service_role;

revoke all on function public.admin_ai_get_settings() from public, anon, authenticated, service_role;
revoke all on function public.admin_ai_save_settings(boolean,boolean,boolean,text,text) from public, anon, authenticated, service_role;
revoke all on function public.admin_ai_list_faq_rules() from public, anon, authenticated, service_role;
revoke all on function public.admin_ai_upsert_faq_rule(uuid,text[],text,integer,boolean) from public, anon, authenticated, service_role;
revoke all on function public.admin_ai_delete_faq_rule(uuid) from public, anon, authenticated, service_role;
revoke all on function public.admin_ai_list_logs(integer) from public, anon, authenticated, service_role;
grant execute on function public.admin_ai_get_settings() to authenticated;
grant execute on function public.admin_ai_save_settings(boolean,boolean,boolean,text,text) to authenticated;
grant execute on function public.admin_ai_list_faq_rules() to authenticated;
grant execute on function public.admin_ai_upsert_faq_rule(uuid,text[],text,integer,boolean) to authenticated;
grant execute on function public.admin_ai_delete_faq_rule(uuid) to authenticated;
grant execute on function public.admin_ai_list_logs(integer) to authenticated;
