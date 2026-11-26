import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2.119.0";

const cors = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};
const reply = (status: number, data: Record<string, unknown>) =>
  new Response(JSON.stringify(data), {
    status,
    headers: { ...cors, "Content-Type": "application/json" },
  });

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: cors });
  if (req.method !== "POST") return reply(405, { error: "روش درخواست مجاز نیست." });

  const url = Deno.env.get("SUPABASE_URL") ?? "";
  const anon = Deno.env.get("SUPABASE_ANON_KEY") ?? "";
  const service = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
  const token = (req.headers.get("Authorization") ?? "").replace(/^Bearer\s+/i, "");
  if (!url || !anon || !service || !token) return reply(401, { error: "ابتدا وارد حساب شو." });

  const caller = createClient(url, anon, {
    global: { headers: { Authorization: `Bearer ${token}` } },
    auth: { persistSession: false, autoRefreshToken: false },
  });
  const { data: auth, error: authErr } = await caller.auth.getUser(token);
  if (authErr || !auth.user) return reply(401, { error: "نشست ورود معتبر نیست." });

  const admin = createClient(url, service, {
    auth: { persistSession: false, autoRefreshToken: false },
  });
  const { data: ban } = await admin.from("user_bans").select("user_id").eq("user_id", auth.user.id).maybeSingle();
  if (ban) return reply(403, { error: "حساب کاربری در حال حاضر مسدود است." });

  let body: { display_name?: string; game_uid?: string; game_name?: string };
  try { body = await req.json(); } catch { return reply(400, { error: "درخواست معتبر نیست." }); }

  const displayName = String(body.display_name ?? "").trim();
  const gameUid = String(body.game_uid ?? "").trim();
  const gameName = String(body.game_name ?? "").trim();
  if (displayName.length < 2 || displayName.length > 30 ||
      !/^[0-9]{19,20}$/.test(gameUid) ||
      gameName.length < 2 || gameName.length > 40) {
    return reply(400, { error: "پروفایل باید شامل نام نمایشی معتبر، UID عددی ۱۹ یا ۲۰ رقمی و نام داخل بازی باشد." });
  }

  const { error: updateErr } = await admin.from("profiles").upsert({
    id: auth.user.id,
    display_name: displayName,
    game_uid: gameUid,
    game_name: gameName,
  }, { onConflict: "id" });
  if (updateErr) {
    console.error("Profile update:", updateErr);
    return reply(400, { error: updateErr.message || "ذخیره پروفایل انجام نشد." });
  }
  return reply(200, { success: true });
});
