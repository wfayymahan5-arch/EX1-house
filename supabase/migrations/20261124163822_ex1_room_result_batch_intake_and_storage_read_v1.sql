drop policy if exists "EX1 authorized room participants read result evidence" on storage.objects;
create policy "EX1 authorized room participants read result evidence" on storage.objects for select to authenticated
using(bucket_id='room-result-evidence' and (storage.foldername(name))[1] ~* '^[0-9a-f-]{36}$'
 and (private.has_permission('results.view') or private.has_permission('results.manage')
 or exists(select 1 from public.tournament_room_registrations rr where rr.room_id::text=(storage.foldername(name))[1]
 and rr.user_id=(select auth.uid()) and rr.status='approved')));

create or replace function public.create_room_result_batch(
 p_batch_id uuid,p_room_id uuid,p_provider text,p_model text,p_extraction jsonb,p_uncertainties jsonb,p_images jsonb,p_candidates jsonb
) returns uuid language plpgsql security definer set search_path=''
as $function$
declare
 v_user uuid:=auth.uid(); v_room public.tournament_rooms%rowtype;
 v_img jsonb; v_candidate jsonb; v_image_count integer; v_candidate_count integer;
 v_ext text; v_path text; v_uid text; v_placement integer; v_kills integer; v_confidence text; v_indexes jsonb;
begin
 if v_user is null or not private.has_permission('results.manage') then raise exception 'not_authorized' using errcode='42501'; end if;
 if p_batch_id is null or p_room_id is null or jsonb_typeof(p_images)<>'array' or jsonb_typeof(p_candidates)<>'array'
 or jsonb_typeof(coalesce(p_uncertainties,'[]'::jsonb))<>'array' or jsonb_typeof(coalesce(p_extraction,'{}'::jsonb))<>'object' then
 raise exception 'invalid_batch_payload' using errcode='22023'; end if;
 v_image_count:=jsonb_array_length(p_images); v_candidate_count:=jsonb_array_length(p_candidates);
 if v_image_count<1 or v_image_count>8 or v_candidate_count>100 then raise exception 'batch_size_out_of_range' using errcode='22023'; end if;
 select * into v_room from public.tournament_rooms where id=p_room_id;
 if not found or (v_room.status<>'completed' and (v_room.ends_at is null or v_room.ends_at>now())) then raise exception 'room_not_completed' using errcode='22023'; end if;
 if exists(select 1 from private.room_result_batches b where b.id=p_batch_id) then raise exception 'batch_already_exists' using errcode='23505'; end if;
 insert into private.room_result_batches(id,room_id,uploaded_by,status,provider,model,extraction,uncertainties)
 values(p_batch_id,p_room_id,v_user,'processing',left(coalesce(p_provider,'unknown'),80),left(coalesce(p_model,'unknown'),120),coalesce(p_extraction,'{}'::jsonb),coalesce(p_uncertainties,'[]'::jsonb));
 for v_img in select value from jsonb_array_elements(p_images) loop
  v_ext:=lower(coalesce(v_img->>'extension',''));
  if v_ext not in ('jpg','jpeg','png','webp') then raise exception 'invalid_image_extension'; end if;
  if (v_img->>'mime_type') not in ('image/jpeg','image/png','image/webp') then raise exception 'invalid_image_mime'; end if;
  if (v_img->>'byte_size') !~ '^[0-9]+$' or (v_img->>'byte_size')::integer not between 16 and 4194304 then raise exception 'invalid_image_size'; end if;
  if (v_img->>'sha256') !~ '^[0-9a-f]{64}$' then raise exception 'invalid_image_hash'; end if;
  v_path:=v_img->>'object_path';
  if v_path is null or v_path<>p_room_id::text||'/'||p_batch_id::text||'/'||(v_img->>'sha256')||'.'||v_ext then raise exception 'invalid_image_path'; end if;
  if not exists(select 1 from storage.objects o where o.bucket_id='room-result-evidence' and o.name=v_path) then raise exception 'uploaded_image_not_found'; end if;
  insert into private.room_result_images(batch_id,room_id,object_path,sha256,mime_type,byte_size,uploaded_by)
  values(p_batch_id,p_room_id,v_path,v_img->>'sha256',v_img->>'mime_type',(v_img->>'byte_size')::integer,v_user);
 end loop;
 for v_candidate in select value from jsonb_array_elements(p_candidates) loop
  v_uid:=nullif(left(btrim(coalesce(v_candidate->>'game_uid','')),80),'');
  v_placement:=case when coalesce(v_candidate->>'placement','') ~ '^[0-9]+$' then (v_candidate->>'placement')::integer else null end;
  v_kills:=case when coalesce(v_candidate->>'kills','') ~ '^[0-9]+$' then (v_candidate->>'kills')::integer else null end;
  v_confidence:=case when v_candidate->>'confidence' in ('high','medium','low') then v_candidate->>'confidence' else 'low' end;
  v_indexes:=coalesce(v_candidate->'evidence_image_indexes','[]'::jsonb);
  if jsonb_typeof(v_indexes)<>'array' or exists(select 1 from jsonb_array_elements(v_indexes) e
    where jsonb_typeof(e)<>'number' or (e#>>'{}') !~ '^[0-9]+$' or (e#>>'{}')::integer>=v_image_count) then raise exception 'invalid_evidence_image_indexes'; end if;
  if v_placement is not null and v_placement not between 1 and 1000 then v_placement:=null; end if;
  if v_kills is not null and v_kills not between 0 and 1000 then v_kills:=null; end if;
  insert into private.room_result_candidates(batch_id,game_uid,placement,kills,confidence,evidence_image_indexes,review_status)
  values(p_batch_id,v_uid,v_placement,v_kills,v_confidence,v_indexes,
   case when v_uid is null or v_placement is null or v_kills is null then 'needs_manual_match' else 'pending' end);
 end loop;
 update private.room_result_batches set status='review_required' where id=p_batch_id;
 return p_batch_id;
end;
$function$;
revoke all on function public.create_room_result_batch(uuid,uuid,text,text,jsonb,jsonb,jsonb,jsonb) from public,anon;
grant execute on function public.create_room_result_batch(uuid,uuid,text,text,jsonb,jsonb,jsonb,jsonb) to authenticated;
