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
 let body:{case_id?:string;decision?:string;note?:string};
 try{body=await req.json();}catch{return reply(400,{error:"درخواست معتبر نیست."});}
 const id=String(body.case_id??""),decision=String(body.decision??""),note=String(body.note??"").trim().slice(0,500);
 if(!/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(id)||!["warn_only","warn_and_ban","dismiss"].includes(decision))return reply(400,{error:"پرونده یا تصمیم معتبر نیست."});
 const {data:record,error:caseErr}=await admin.from("ai_moderation_cases").select("id,user_id,status,content_type").eq("id",id).maybeSingle();
 if(caseErr||!record)return reply(404,{error:"پرونده پیدا نشد."});
 if(record.status!=="pending_review")return reply(409,{error:"این پرونده قبلاً بررسی شده است."});
 if(decision==="dismiss"){
  const {error}=await admin.from("ai_moderation_cases").update({status:"rejected",owner_decision_by:auth.user.id,owner_decision_at:new Date().toISOString(),owner_note:note}).eq("id",id).eq("status","pending_review");
  if(error)return reply(500,{error:"ثبت رد پرونده انجام نشد."});
  return reply(200,{success:true,decision:"dismissed"});
 }
 if(decision==="warn_only"){
  const {error:noticeErr}=await admin.from("user_notifications").insert({
   user_id:record.user_id,notification_type:"moderation_warning",title:"اخطار رعایت قوانین EX1",
   body:"محتوای ارسالی شما با قوانین ایمنی EX1 سازگار تشخیص داده شد. این اخطار برای آگاه‌سازی است؛ اگر بررسی اشتباه بوده، از پشتیبانی درخواست بازبینی کنید.",
   related_case_id:id
  });
  if(noticeErr)return reply(500,{error:"ارسال اخطار به کاربر انجام نشد."});
  const {error:updateErr}=await admin.from("ai_moderation_cases").update({
   status:"warning_approved",owner_decision_by:auth.user.id,owner_decision_at:new Date().toISOString(),
   owner_note:note,warning_sent_at:new Date().toISOString()
  }).eq("id",id).eq("status","pending_review");
  if(updateErr)return reply(500,{error:"اخطار ارسال شد اما ثبت نهایی پرونده انجام نشد."});
  return reply(200,{success:true,decision:"warning_sent"});
 }
 if(record.user_id===auth.user.id)return reply(400,{error:"مالک نمی‌تواند خودش را مسدود کند."});
 const {data:isTargetOwner,error:targetOwnerErr}=await admin.rpc("is_system_owner",{p_user_id:record.user_id});
 if(targetOwnerErr)return reply(500,{error:"بررسی حساب هدف انجام نشد."});
 if(isTargetOwner===true)return reply(403,{error:"حساب مالک اصلی قابل مسدودسازی نیست."});
 const {data:target,error:targetErr}=await admin.auth.admin.getUserById(record.user_id);
 if(targetErr||!target.user)return reply(404,{error:"حساب کاربر پیدا نشد."});

 // Persist and deliver the warning first. Only then attempt the ban.
 const {error:noticeErr}=await admin.from("user_notifications").insert({
  user_id:record.user_id,notification_type:"moderation_warning",title:"اخطار رعایت قوانین EX1",
  body:"محتوای ارسالی شما با قوانین ایمنی EX1 سازگار تشخیص داده شد. با تأیید مالک، دسترسی حساب شما مسدود می‌شود. در صورت اشتباه بودن بررسی، از پشتیبانی درخواست بازبینی کنید.",
  related_case_id:id
 });
 if(noticeErr)return reply(500,{error:"ارسال اخطار به کاربر انجام نشد؛ مسدودسازی انجام نشد."});
 const {error:banAuthErr}=await admin.auth.admin.updateUserById(record.user_id,{ban_duration:"876000h"});
 if(banAuthErr)return reply(500,{error:"اخطار ارسال شد اما مسدودسازی انجام نشد. پرونده برای پیگیری باقی ماند."});
 const {error:banRowErr}=await admin.from("user_bans").upsert({user_id:record.user_id,reason:"تأیید تخلف توسط مالک EX1 پس از بررسی هوش مصنوعی",banned_by:auth.user.id});
 if(banRowErr){
  await admin.auth.admin.updateUserById(record.user_id,{ban_duration:"none"});
  return reply(500,{error:"اخطار ارسال شد اما ثبت مسدودسازی کامل نشد."});
 }
 const {error:updateErr}=await admin.from("ai_moderation_cases").update({
  status:"ban_approved",owner_decision_by:auth.user.id,owner_decision_at:new Date().toISOString(),
  owner_note:note,warning_sent_at:new Date().toISOString(),ban_applied_at:new Date().toISOString()
 }).eq("id",id).eq("status","pending_review");
 if(updateErr)return reply(500,{error:"کاربر مسدود شد اما ثبت نهایی پرونده نیاز به بررسی دارد."});
 return reply(200,{success:true,decision:"warned_and_banned"});
});
