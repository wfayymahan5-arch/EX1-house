import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2.119.0";
const cors={"Access-Control-Allow-Origin":"*","Access-Control-Allow-Headers":"authorization, x-client-info, apikey, content-type","Access-Control-Allow-Methods":"POST, OPTIONS"};
const reply=(status:number,data:Record<string,unknown>)=>new Response(JSON.stringify(data),{status,headers:{...cors,"Content-Type":"application/json"}});
Deno.serve(async(req:Request)=>{
 if(req.method==="OPTIONS")return new Response("ok",{headers:cors});
 if(req.method!=="POST")return reply(405,{error:"روش درخواست مجاز نیست."});
 const url=Deno.env.get("SUPABASE_URL")??"",anon=Deno.env.get("SUPABASE_ANON_KEY")??"",service=Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")??"",token=(req.headers.get("Authorization")??"").replace(/^Bearer\s+/i,"");
 if(!url||!anon||!service||!token)return reply(401,{error:"نشست معتبر نیست."});
 const caller=createClient(url,anon,{global:{headers:{Authorization:`Bearer ${token}`}},auth:{persistSession:false,autoRefreshToken:false}});
 const {data:auth,error:authErr}=await caller.auth.getUser(token);if(authErr||!auth.user)return reply(401,{error:"ورود معتبر نیست."});
 const admin=createClient(url,service,{auth:{persistSession:false,autoRefreshToken:false}});
 const {data:owner,error:ownerErr}=await admin.rpc("is_system_owner",{p_user_id:auth.user.id});if(ownerErr||owner!==true)return reply(403,{error:"فقط مالک اصلی به شواهد دسترسی دارد."});
 let body:{case_id?:string};try{body=await req.json();}catch{return reply(400,{error:"درخواست معتبر نیست."});}
 const id=String(body.case_id??"");if(!/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(id))return reply(400,{error:"شناسه پرونده معتبر نیست."});
 const {data:record,error}=await admin.from("ai_moderation_cases").select("content_type,evidence_bucket,evidence_path,original_storage_bucket,original_storage_path").eq("id",id).maybeSingle();
 if(error||!record||!["profile_avatar","profile_cover"].includes(record.content_type))return reply(404,{error:"شواهد تصویر پروفایل پیدا نشد."});
 const bucket=String(record.evidence_bucket||record.original_storage_bucket||""),path=String(record.evidence_path||record.original_storage_path||"");
 if(!["ex1-avatars","ex1-avatar-review"].includes(bucket)||!path)return reply(404,{error:"مسیر شواهد معتبر نیست."});
 const prefix=record.content_type==="profile_cover"?"cover-":"avatar-";
 if(!path.startsWith(record.user_id+"/"+prefix))return reply(404,{error:"مسیر شواهد با مالک پرونده مطابقت ندارد."});
 if(bucket==="ex1-avatars"){const {data}=admin.storage.from(bucket).getPublicUrl(path);return reply(200,{success:true,signed_url:data.publicUrl});}
 const {data:signed,error:signedErr}=await admin.storage.from(bucket).createSignedUrl(path,1800);
 if(signedErr||!signed?.signedUrl)return reply(500,{error:"ساخت لینک امن شواهد انجام نشد."});
 return reply(200,{success:true,signed_url:signed.signedUrl});
});