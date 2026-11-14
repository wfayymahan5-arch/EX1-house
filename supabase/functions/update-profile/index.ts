import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2.119.0";
const cors={"Access-Control-Allow-Origin":"*","Access-Control-Allow-Headers":"authorization, x-client-info, apikey, content-type","Access-Control-Allow-Methods":"POST, OPTIONS"};
const reply=(status:number,data:Record<string,unknown>)=>new Response(JSON.stringify(data),{status,headers:{...cors,"Content-Type":"application/json"}});
Deno.serve(async(req:Request)=>{
 if(req.method==="OPTIONS")return new Response("ok",{headers:cors});
 if(req.method!=="POST")return reply(405,{error:"روش درخواست مجاز نیست."});
 const url=Deno.env.get("SUPABASE_URL")??"",anon=Deno.env.get("SUPABASE_ANON_KEY")??"",service=Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")??"",key=Deno.env.get("OPENAI_API_KEY")??"";
 const token=(req.headers.get("Authorization")??"").replace(/^Bearer\s+/i,"");
 if(!url||!anon||!service||!token)return reply(401,{error:"ابتدا وارد حساب شو."});
 if(!key)return reply(503,{error:"سرویس بررسی متن آماده نیست."});
 const caller=createClient(url,anon,{global:{headers:{Authorization:`Bearer ${token}`}},auth:{persistSession:false,autoRefreshToken:false}});
 const {data:auth,error:authErr}=await caller.auth.getUser(token);
 if(authErr||!auth.user)return reply(401,{error:"نشست ورود معتبر نیست."});
 const admin=createClient(url,service,{auth:{persistSession:false,autoRefreshToken:false}});
 const {data:ban}=await admin.from("user_bans").select("user_id").eq("user_id",auth.user.id).maybeSingle();
 if(ban)return reply(403,{error:"حساب کاربری در حال حاضر مسدود است."});
 let body:{display_name?:string;username?:string|null;game_uid?:string|null;game_name?:string|null};
 try{body=await req.json();}catch{return reply(400,{error:"درخواست معتبر نیست."});}
 const displayName=String(body.display_name??"").trim(),username=String(body.username??"").trim(),gameUid=String(body.game_uid??"").trim(),gameName=String(body.game_name??"").trim();
 if(displayName.length<2||displayName.length>30||username.length>24||gameUid.length>64||gameName.length>64||gameUid.includes("@")){
  return reply(400,{error:"اطلاعات پروفایل معتبر نیست. UID کالاف نباید ایمیل باشد."});
 }
 const content=[displayName,username,gameName].filter(Boolean).join("\n");
 const response=await fetch("https://api.openai.com/v1/moderations",{
  method:"POST",headers:{Authorization:`Bearer ${key}`,"Content-Type":"application/json"},
  body:JSON.stringify({model:"omni-moderation-latest",input:content})
 });
 if(!response.ok){console.error("Profile moderation:",response.status);return reply(502,{error:"بررسی ایمنی پروفایل انجام نشد؛ تغییری ذخیره نشد."});}
 const moderationJson=await response.json(),result=moderationJson?.results?.[0];
 if(!result)return reply(502,{error:"پاسخ بررسی پروفایل معتبر نبود."});
 if(result.flagged===true){
  const scores=result.category_scores??{},vals=Object.values(scores).filter((v):v is number=>typeof v==="number");
  const confidence=vals.length?Math.max(...vals):null;
  const severe=Math.max(Number(scores["sexual/minors"]??0),Number(scores["violence/graphic"]??0))>=0.5;
  const {data:caseRow,error:caseErr}=await admin.from("ai_moderation_cases").insert({
   user_id:auth.user.id,content_type:"profile_text",source_id:auth.user.id,content_text:content,
   moderation_categories:result.categories??{},moderation_scores:scores,
   severity:severe?"critical":(confidence??0)>=0.85?"high":"medium",confidence,
   model_name:"omni-moderation-latest",status:"pending_review"
  }).select("id").single();
  if(caseErr||!caseRow){console.error("Profile moderation case:",caseErr);return reply(500,{error:"ثبت پرونده بررسی انجام نشد؛ پروفایل ذخیره نشد."});}
  return reply(200,{success:false,pending_review:true,case_id:caseRow.id,message:"اطلاعات پروفایل برای بررسی مالک ارسال شد و ذخیره نشد."});
 }
 const {error:updateErr}=await admin.from("profiles").update({
  display_name:displayName,username:username||null,game_uid:gameUid||null,game_name:gameName||null
 }).eq("id",auth.user.id);
 if(updateErr){console.error("Profile update:",updateErr);return reply(400,{error:updateErr.message||"ذخیره پروفایل انجام نشد."});}
 return reply(200,{success:true});
});
