import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2.119.0";
const cors={"Access-Control-Allow-Origin":"*","Access-Control-Allow-Headers":"authorization, x-client-info, apikey, content-type","Access-Control-Allow-Methods":"POST, OPTIONS"};
const reply=(status:number,data:Record<string,unknown>)=>new Response(JSON.stringify(data),{status,headers:{...cors,"Content-Type":"application/json"}});
const uuid=/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

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
 let body:{action?:string;name?:string;tag?:string|null;description?:string|null;team_id?:string};
 try{body=await req.json();}catch{return reply(400,{error:"درخواست معتبر نیست."});}
 const action=String(body.action??"");
 const name=String(body.name??"").trim();
 const tag=String(body.tag??"").trim().toUpperCase();
 const description=String(body.description??"").trim();
 const teamId=String(body.team_id??"");
 if(!["create","update"].includes(action)||name.length<2||name.length>40||tag.length>8||description.length>240||(action==="update"&&!uuid.test(teamId))){
  return reply(400,{error:"اطلاعات تیم معتبر نیست."});
 }
 if(action==="update"){
  const {data:team,error}=await admin.from("teams").select("id,captain_id").eq("id",teamId).maybeSingle();
  if(error||!team)return reply(404,{error:"تیم پیدا نشد."});
  if(team.captain_id!==auth.user.id)return reply(403,{error:"فقط کاپیتان تیم می‌تواند تنظیمات آن را تغییر دهد."});
 }
 const content=[name,tag,description].filter(Boolean).join("\n");
 const moderationResponse=await fetch("https://api.openai.com/v1/moderations",{
  method:"POST",headers:{Authorization:`Bearer ${key}`,"Content-Type":"application/json"},
  body:JSON.stringify({model:"omni-moderation-latest",input:content})
 });
 if(!moderationResponse.ok){console.error("Team text moderation:",moderationResponse.status);return reply(502,{error:"بررسی ایمنی متن تیم انجام نشد؛ تغییری ذخیره نشد."});}
 const moderationJson=await moderationResponse.json(),result=moderationJson?.results?.[0];
 if(!result)return reply(502,{error:"پاسخ بررسی متن معتبر نبود."});
 if(result.flagged===true){
  const scores=result.category_scores??{},vals=Object.values(scores).filter((v):v is number=>typeof v==="number");
  const confidence=vals.length?Math.max(...vals):null;
  const severe=Math.max(Number(scores["sexual/minors"]??0),Number(scores["violence/graphic"]??0))>=0.5;
  const {data:caseRow,error:caseErr}=await admin.from("ai_moderation_cases").insert({
   user_id:auth.user.id,content_type:"team_description",source_id:action==="update"?teamId:null,
   content_text:content,moderation_categories:result.categories??{},moderation_scores:scores,
   severity:severe?"critical":(confidence??0)>=0.85?"high":"medium",confidence,
   model_name:"omni-moderation-latest",status:"pending_review"
  }).select("id").single();
  if(caseErr||!caseRow){console.error("Team moderation case:",caseErr);return reply(500,{error:"ثبت پرونده بررسی انجام نشد؛ متن ذخیره نشد."});}
  return reply(200,{success:false,pending_review:true,case_id:caseRow.id,message:"متن تیم برای بررسی مالک ارسال شد و ذخیره نشد."});
 }
 if(action==="create"){
  const {data:teamIdCreated,error}=await admin.rpc("team_create_for_user",{p_user_id:auth.user.id,p_name:name,p_tag:tag||null,p_description:description||null});
  if(error){console.error("Create team:",error);return reply(400,{error:error.message||"ساخت تیم انجام نشد."});}
  return reply(200,{success:true,team_id:teamIdCreated});
 }
 const {error:updateErr}=await admin.from("teams").update({name,tag:tag||null,description:description||null}).eq("id",teamId).eq("captain_id",auth.user.id);
 if(updateErr){console.error("Update team:",updateErr);return reply(400,{error:updateErr.message||"به‌روزرسانی تیم انجام نشد."});}
 return reply(200,{success:true,team_id:teamId});
});
