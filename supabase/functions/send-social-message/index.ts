import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2.119.0";

const cors = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};
const out = (status:number, data:Record<string,unknown>) => new Response(JSON.stringify(data), {
  status, headers:{...cors,"Content-Type":"application/json"}
});

Deno.serve(async (req:Request) => {
  if(req.method==="OPTIONS") return new Response("ok",{headers:cors});
  if(req.method!=="POST") return out(405,{error:"روش درخواست مجاز نیست."});
  const url=Deno.env.get("SUPABASE_URL")??"";
  const anon=Deno.env.get("SUPABASE_ANON_KEY")??"";
  const service=Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")??"";
  const key=Deno.env.get("OPENAI_API_KEY")??"";
  const token=(req.headers.get("Authorization")??"").replace(/^Bearer\s+/i,"");
  if(!url||!anon||!service||!token) return out(401,{error:"ابتدا وارد حساب شو."});
  if(!key) return out(503,{error:"سرویس بررسی پیام آماده نیست."});

  const userClient=createClient(url,anon,{global:{headers:{Authorization:`Bearer ${token}`}},auth:{persistSession:false,autoRefreshToken:false}});
  const {data:auth,error:authErr}=await userClient.auth.getUser(token);
  if(authErr||!auth.user) return out(401,{error:"نشست ورود معتبر نیست."});

  let input:{conversation_id?:string;body?:string};
  try{input=await req.json();}catch{return out(400,{error:"درخواست معتبر نیست."});}
  const conversationId=String(input.conversation_id??"");
  const message=String(input.body??"").trim();
  const uuid=/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
  if(!uuid.test(conversationId)||!message||message.length>2000) return out(400,{error:"شناسه گفت‌وگو یا متن پیام معتبر نیست."});

  const admin=createClient(url,service,{auth:{persistSession:false,autoRefreshToken:false}});
  const {data:ban}=await admin.from("user_bans").select("user_id").eq("user_id",auth.user.id).maybeSingle();
  if(ban) return out(403,{error:"حساب کاربری در حال حاضر مسدود است."});

  const {data:members,error:memberErr}=await admin.from("social_conversation_members").select("user_id").eq("conversation_id",conversationId);
  if(memberErr||!members?.some((m)=>m.user_id===auth.user.id)||members.length<2) return out(403,{error:"به این گفت‌وگو دسترسی نداری."});
  const peers=members.filter((m)=>m.user_id!==auth.user.id).map((m)=>m.user_id);
  for(const peer of peers){
    const {data:blocks,error:blockErr}=await admin.from("user_blocks").select("blocker_id").or(
      `and(blocker_id.eq.${auth.user.id},blocked_id.eq.${peer}),and(blocker_id.eq.${peer},blocked_id.eq.${auth.user.id})`
    ).limit(1);
    if(blockErr) return out(500,{error:"بررسی دسترسی گفت‌وگو انجام نشد."});
    if(blocks?.length) return out(403,{error:"به‌دلیل تنظیمات مسدودسازی، ارسال پیام ممکن نیست."});
  }

  const moderationResponse=await fetch("https://api.openai.com/v1/moderations",{
    method:"POST",headers:{Authorization:`Bearer ${key}`,"Content-Type":"application/json"},
    body:JSON.stringify({model:"omni-moderation-latest",input:message})
  });
  if(!moderationResponse.ok){console.error("Moderation API:",moderationResponse.status);return out(502,{error:"بررسی ایمنی پیام انجام نشد؛ پیام ارسال نشد."});}
  const moderationJson=await moderationResponse.json();
  const result=moderationJson?.results?.[0];
  if(!result) return out(502,{error:"پاسخ بررسی پیام معتبر نبود."});

  if(result.flagged===true){
    const scores=result.category_scores??{};
    const vals=Object.values(scores).filter((v):v is number=>typeof v==="number");
    const confidence=vals.length?Math.max(...vals):null;
    const critical=Math.max(Number(scores["sexual/minors"]??0),Number(scores["violence/graphic"]??0))>=0.5;
    const {data:caseRow,error:caseErr}=await admin.from("ai_moderation_cases").insert({
      user_id:auth.user.id,content_type:"chat_message",content_text:message,
      moderation_categories:result.categories??{},moderation_scores:scores,
      severity:critical?"critical":(confidence??0)>=0.85?"high":"medium",
      confidence,model_name:"omni-moderation-latest",status:"pending_review"
    }).select("id").single();
    if(caseErr||!caseRow){console.error("Moderation case:",caseErr);return out(500,{error:"ثبت گزارش بررسی انجام نشد؛ پیام ارسال نشد."});}
    return out(200,{success:false,pending_review:true,message:"این پیام برای بررسی به مالک گزارش شد و ارسال نشد."});
  }

  const {data:inserted,error:insertErr}=await admin.from("social_messages").insert({
    conversation_id:conversationId,sender_id:auth.user.id,body:message
  }).select("id,created_at").single();
  if(insertErr){console.error("Message insert:",insertErr);return out(500,{error:"ارسال پیام انجام نشد."});}
  return out(200,{success:true,message_id:inserted.id,created_at:inserted.created_at});
});
