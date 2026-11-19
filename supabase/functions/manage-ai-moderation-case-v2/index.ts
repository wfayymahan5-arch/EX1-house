import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2.119.0";
const cors={"Access-Control-Allow-Origin":"*","Access-Control-Allow-Headers":"authorization, x-client-info, apikey, content-type","Access-Control-Allow-Methods":"POST, OPTIONS"};
const reply=(status:number,data:Record<string,unknown>)=>new Response(JSON.stringify(data),{status,headers:{...cors,"Content-Type":"application/json"}});
Deno.serve(async(req:Request)=>{
 if(req.method==="OPTIONS")return new Response("ok",{headers:cors});
 if(req.method!=="POST")return reply(405,{error:"روش درخواست مجاز نیست."});
 const url=Deno.env.get("SUPABASE_URL")??"",anon=Deno.env.get("SUPABASE_ANON_KEY")??"",service=Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")??"";
 const token=(req.headers.get("Authorization")??"").replace(/^Bearer\s+/i,"");
 if(!url||!anon||!service||!token)return reply(401,{error:"نشست معتبر نیست."});
 const caller=createClient(url,anon,{global:{headers:{Authorization:`Bearer ${token}`}},auth:{persistSession:false,autoRefreshToken:false}});
 const {data:auth,error:authErr}=await caller.auth.getUser(token);
 if(authErr||!auth.user)return reply(401,{error:"ورود معتبر نیست."});
 const admin=createClient(url,service,{auth:{persistSession:false,autoRefreshToken:false}});
 const {data:owner,error:ownerErr}=await admin.rpc("is_system_owner",{p_user_id:auth.user.id});
 if(ownerErr||owner!==true)return reply(403,{error:"فقط مالک اصلی می‌تواند پرونده هوش مصنوعی را تعیین تکلیف کند."});
 let body:{case_id?:string;decision?:string;note?:string};try{body=await req.json();}catch{return reply(400,{error:"درخواست معتبر نیست."});}
 const id=String(body.case_id??""),decision=String(body.decision??""),note=String(body.note??"").trim().slice(0,500);
 const allowed=["warn_only","warn_and_ban","dismiss","remove_avatar","remove_cover"];
 if(!/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(id)||!allowed.includes(decision))return reply(400,{error:"پرونده یا تصمیم معتبر نیست."});
 const {data:record,error:caseErr}=await admin.from("ai_moderation_cases").select("id,user_id,status,content_type,severity,original_storage_bucket,original_storage_path").eq("id",id).maybeSingle();
 if(caseErr||!record)return reply(404,{error:"پرونده پیدا نشد."});
 if(record.status!=="pending_review")return reply(409,{error:"این پرونده قبلاً بررسی شده است."});
 const isAvatar=record.content_type==="profile_avatar";
 const isCover=record.content_type==="profile_cover";
 const validCoverPath=path.startsWith(record.user_id+"/cover-")&&/^cover-[0-9a-f-]{36}\\.(jpg|png|webp)$/i.test(path.slice((record.user_id+"/").length));
 const bucket=String(record.original_storage_bucket??""),path=String(record.original_storage_path??"");
 const validAvatarPath=path.startsWith(record.user_id+"/avatar-");
 const publishPrivateAvatar=async()=>{
  if(!isAvatar||bucket!=="ex1-avatar-review")return true;
  if(!validAvatarPath)return false;
  const {data:file,error:downloadErr}=await admin.storage.from("ex1-avatar-review").download(path);
  if(downloadErr||!file)return false;
  const {error:uploadErr}=await admin.storage.from("ex1-avatars").upload(path,file,{upsert:true,contentType:file.type,cacheControl:"3600"});
  if(uploadErr)return false;
  const {error:profileErr}=await admin.from("profiles").update({avatar_url:path}).eq("id",record.user_id);
  if(profileErr){await admin.storage.from("ex1-avatars").remove([path]);return false;}
  const {error:caseUpdateErr}=await admin.from("ai_moderation_cases").update({original_storage_bucket:"ex1-avatars",original_storage_path:path,evidence_bucket:"ex1-avatars",evidence_path:path}).eq("id",id);
  if(caseUpdateErr){await admin.from("profiles").update({avatar_url:null}).eq("id",record.user_id);await admin.storage.from("ex1-avatars").remove([path]);return false;}
  await admin.storage.from("ex1-avatar-review").remove([path]);
  return true;
 };
 const publishPrivateCover=async()=>{
  if(!isCover||bucket!=="ex1-avatar-review")return true;
  if(!validCoverPath)return false;
  const {data:file,error:downloadErr}=await admin.storage.from("ex1-avatar-review").download(path);
  if(downloadErr||!file)return false;
  if(file.size<1||file.size>5*1024*1024||!new Set(["image/jpeg","image/png","image/webp"]).has(file.type))return false;
  const {data:oldProfile}=await admin.from("profiles").select("cover_url").eq("id",record.user_id).maybeSingle();
  const {error:uploadErr}=await admin.storage.from("ex1-avatars").upload(path,file,{upsert:true,contentType:file.type,cacheControl:"3600"});
  if(uploadErr)return false;
  const {error:profileErr}=await admin.from("profiles").update({cover_url:path}).eq("id",record.user_id);
  if(profileErr){await admin.storage.from("ex1-avatars").remove([path]);return false;}
  const {error:caseUpdateErr}=await admin.from("ai_moderation_cases").update({original_storage_bucket:"ex1-avatars",original_storage_path:path,evidence_bucket:"ex1-avatars",evidence_path:path}).eq("id",id);
  if(caseUpdateErr){await admin.from("profiles").update({cover_url:oldProfile?.cover_url??null}).eq("id",record.user_id);await admin.storage.from("ex1-avatars").remove([path]);return false;}
  const oldPath=String(oldProfile?.cover_url??"");
  if(oldPath&&oldPath!==path&&oldPath.startsWith(record.user_id+"/cover-"))await admin.storage.from("ex1-avatars").remove([oldPath]);
  await admin.storage.from("ex1-avatar-review").remove([path]);
  return true;
 };
 const removeCover=async()=>{
  if(!isCover)return true;
  if(!validCoverPath)return false;
  if(bucket==="ex1-avatar-review"){
   const {error}=await admin.storage.from(bucket).remove([path]);if(error)return false;
  }else if(bucket==="ex1-avatars"){
   const {data:profile}=await admin.from("profiles").select("cover_url").eq("id",record.user_id).maybeSingle();
   if(profile?.cover_url===path){const {error:profileErr}=await admin.from("profiles").update({cover_url:null}).eq("id",record.user_id);if(error)return false;}
   const {error}=await admin.storage.from(bucket).remove([path]);if(error)return false;
  }else if(bucket||path)return false;
  return true;
 };
 const removeAvatar=async()=>{
  if(!isAvatar)return true;
  if(!validAvatarPath)return false;
  if(bucket==="ex1-avatar-review"){
   const {error}=await admin.storage.from(bucket).remove([path]);if(error)return false;
  }else if(bucket==="ex1-avatars"){
   const {data:profile}=await admin.from("profiles").select("avatar_url").eq("id",record.user_id).maybeSingle();
   if(profile?.avatar_url===path){
    const {error:profileErr}=await admin.from("profiles").update({avatar_url:null}).eq("id",record.user_id);
    if(profileErr)return false;
   }
   const {error}=await admin.storage.from(bucket).remove([path]);if(error)return false;
  }else if(bucket||path)return false;
  return true;
 };
 if(decision==="remove_avatar"&&!isAvatar)return reply(400,{error:"این اقدام فقط برای عکس پروفایل مجاز است."});
 if(decision==="remove_cover"&&!isCover)return reply(400,{error:"این اقدام فقط برای تصویر پس‌زمینه پروفایل مجاز است."});
 if(decision==="remove_cover"){
  if(!(await removeCover()))return reply(500,{error:"حذف تصویر پس‌زمینه انجام نشد؛ پرونده باز ماند."});
  const {error}=await admin.from("ai_moderation_cases").update({status:"cover_removed",owner_decision_by:auth.user.id,owner_decision_at:new Date().toISOString(),owner_note:note}).eq("id",id).eq("status","pending_review");
  if(error)return reply(500,{error:"تصویر حذف شد اما ثبت تصمیم کامل نشد."});
  return reply(200,{success:true,decision:"cover_removed"});
 }
 if(decision==="remove_avatar"){
  if(!(await removeAvatar()))return reply(500,{error:"حذف عکس پروفایل انجام نشد؛ پرونده باز ماند."});
  const {error}=await admin.from("ai_moderation_cases").update({status:"avatar_removed",owner_decision_by:auth.user.id,owner_decision_at:new Date().toISOString(),owner_note:note}).eq("id",id).eq("status","pending_review");
  if(error)return reply(500,{error:"عکس حذف شد اما ثبت تصمیم کامل نشد."});
  return reply(200,{success:true,decision:"avatar_removed"});
 }
 if(decision==="dismiss"){
  if(!(await publishPrivateAvatar())||!(await publishPrivateCover()))return reply(500,{error:"انتشار تصویر پس از بررسی مالک انجام نشد؛ پرونده باز ماند."});
  const {error}=await admin.from("ai_moderation_cases").update({status:"rejected",owner_decision_by:auth.user.id,owner_decision_at:new Date().toISOString(),owner_note:note}).eq("id",id).eq("status","pending_review");
  if(error)return reply(500,{error:"ثبت رد پرونده انجام نشد."});
  return reply(200,{success:true,decision:"dismissed"});
 }
 if(decision==="warn_only"){
  if(!(isAvatar&&record.severity==="critical")&&(!(await publishPrivateAvatar())||!(await publishPrivateCover())))return reply(500,{error:"تصویر برای نمایش پس از اخطار آماده نشد؛ اخطار ارسال نشد."});
  const {error:noticeErr}=await admin.from("user_notifications").insert({user_id:record.user_id,notification_type:"moderation_warning",title:"اخطار رعایت قوانین EX1",body:"محتوای انتخاب‌شده برای پروفایل با معیارهای بررسی هوشمند علامت‌گذاری شد. این اخطار برای آگاه‌سازی است؛ اگر بررسی اشتباه بوده، از پشتیبانی درخواست بازبینی کنید.",related_case_id:id});
  if(noticeErr)return reply(500,{error:"ارسال اخطار به کاربر انجام نشد."});
  const {error:updateErr}=await admin.from("ai_moderation_cases").update({status:"warning_approved",owner_decision_by:auth.user.id,owner_decision_at:new Date().toISOString(),owner_note:note,warning_sent_at:new Date().toISOString()}).eq("id",id).eq("status","pending_review");
  if(updateErr)return reply(500,{error:"اخطار ارسال شد اما ثبت نهایی پرونده انجام نشد."});
  return reply(200,{success:true,decision:"warning_sent"});
 }
 if(record.user_id===auth.user.id)return reply(400,{error:"مالک نمی‌تواند خودش را مسدود کند."});
 const {data:isTargetOwner,error:targetOwnerErr}=await admin.rpc("is_system_owner",{p_user_id:record.user_id});
 if(targetOwnerErr)return reply(500,{error:"بررسی حساب هدف انجام نشد."});
 if(isTargetOwner===true)return reply(403,{error:"حساب مالک اصلی قابل مسدودسازی نیست."});
 const {data:target,error:targetErr}=await admin.auth.admin.getUserById(record.user_id);
 if(targetErr||!target.user)return reply(404,{error:"حساب کاربر پیدا نشد."});
 if((isAvatar&&!(await removeAvatar()))||(isCover&&!(await removeCover())))return reply(500,{error:"حذف محتوای پروفایل انجام نشد؛ مسدودسازی انجام نشد."});
 const {error:noticeErr}=await admin.from("user_notifications").insert({user_id:record.user_id,notification_type:"moderation_warning",title:"اخطار رعایت قوانین EX1",body:"محتوای ارسالی شما با قوانین ایمنی EX1 سازگار تشخیص داده شد. با تأیید مالک، دسترسی حساب شما مسدود می‌شود. در صورت اشتباه بودن بررسی، از پشتیبانی درخواست بازبینی کنید.",related_case_id:id});
 if(noticeErr)return reply(500,{error:"ارسال اخطار به کاربر انجام نشد؛ مسدودسازی انجام نشد."});
 const {error:banAuthErr}=await admin.auth.admin.updateUserById(record.user_id,{ban_duration:"876000h"});
 if(banAuthErr)return reply(500,{error:"اخطار ارسال شد اما مسدودسازی انجام نشد. پرونده برای پیگیری باقی ماند."});
 const {error:banRowErr}=await admin.from("user_bans").upsert({user_id:record.user_id,reason:"تأیید تخلف توسط مالک EX1 پس از بررسی هوش مصنوعی",banned_by:auth.user.id});
 if(banRowErr){await admin.auth.admin.updateUserById(record.user_id,{ban_duration:"none"});return reply(500,{error:"اخطار ارسال شد اما ثبت مسدودسازی کامل نشد."});}
 const {error:updateErr}=await admin.from("ai_moderation_cases").update({status:"ban_approved",owner_decision_by:auth.user.id,owner_decision_at:new Date().toISOString(),owner_note:note,warning_sent_at:new Date().toISOString(),ban_applied_at:new Date().toISOString()}).eq("id",id).eq("status","pending_review");
 if(updateErr)return reply(500,{error:"کاربر مسدود شد اما ثبت نهایی پرونده نیاز به بررسی دارد."});
 return reply(200,{success:true,decision:"warned_and_banned"});
});