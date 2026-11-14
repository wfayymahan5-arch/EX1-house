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
 if(ownerErr||owner!==true)return reply(403,{error:"فقط مالک اصلی می‌تواند به پشتیبانی انسانی پاسخ دهد."});
 let body:{conversation_id?:string;message?:string;resolve?:boolean};
 try{body=await req.json();}catch{return reply(400,{error:"درخواست معتبر نیست."});}
 const id=String(body.conversation_id??""),message=String(body.message??"").trim();
 if(!/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(id)||!message||message.length>4000)return reply(400,{error:"شناسه گفت‌وگو یا پاسخ معتبر نیست."});
 const {data:conversation,error:convErr}=await admin.from("support_conversations").select("id,status").eq("id",id).maybeSingle();
 if(convErr||!conversation)return reply(404,{error:"گفت‌وگوی پشتیبانی پیدا نشد."});
 const {error:insertErr}=await admin.from("support_messages").insert({conversation_id:id,sender_role:"human_support",body:message});
 if(insertErr)return reply(500,{error:"ذخیره پاسخ پشتیبان انجام نشد."});
 const {error:updateErr}=await admin.from("support_conversations").update({
  status:body.resolve===true?"resolved":"open",updated_at:new Date().toISOString()
 }).eq("id",id);
 if(updateErr)return reply(500,{error:"پاسخ ذخیره شد اما وضعیت گفت‌وگو به‌روزرسانی نشد."});
 return reply(200,{success:true,resolved:body.resolve===true});
});
