import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2.119.0";

const cors = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};
const reply = (status: number, data: Record<string, unknown>) =>
  new Response(JSON.stringify(data), { status, headers: { ...cors, "Content-Type": "application/json" } });

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: cors });
  if (req.method !== "POST") return reply(405, { error: "روش درخواست مجاز نیست." });

  const url = Deno.env.get("SUPABASE_URL") ?? "";
  const anon = Deno.env.get("SUPABASE_ANON_KEY") ?? "";
  const service = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
  const token = (req.headers.get("Authorization") ?? "").replace(/^Bearer\\s+/i, "");
  if (!url || !anon || !service || !token) return reply(401, { error: "ابتدا وارد حساب شو." });

  const caller = createClient(url, anon, {
    global: { headers: { Authorization: `Bearer ${token}` } },
    auth: { persistSession: false, autoRefreshToken: false },
  });
  const { data: auth, error: authError } = await caller.auth.getUser(token);
  if (authError || !auth.user) return reply(401, { error: "نشست ورود معتبر نیست." });

  const admin = createClient(url, service, { auth: { persistSession: false, autoRefreshToken: false } });
  const { data: ban, error: banError } = await admin.from("user_bans").select("user_id").eq("user_id", auth.user.id).maybeSingle();
  if (banError) return reply(500, { error: "بررسی وضعیت حساب انجام نشد." });
  if (ban) return reply(403, { error: "حساب کاربری در حال حاضر مسدود است." });

  let body: { p_cover_path?: string; p_action?: string };
  try { body = await req.json(); } catch { return reply(400, { error: "درخواست معتبر نیست." }); }

  const { data: oldProfile, error: profileReadError } = await admin.from("profiles").select("cover_url").eq("id", auth.user.id).maybeSingle();
  if (profileReadError || !oldProfile) return reply(404, { error: "پروفایل پیدا نشد." });
  const oldPath = String(oldProfile.cover_url ?? "");

  if (body.p_action === "delete") {
    const { error } = await admin.from("profiles").update({ cover_url: null }).eq("id", auth.user.id);
    if (error) return reply(500, { error: "حذف پس‌زمینه از پروفایل انجام نشد." });
    if (oldPath.startsWith(auth.user.id + "/cover-")) {
      const { error: removeError } = await admin.storage.from("ex1-avatars").remove([oldPath]);
      if (removeError) console.error("Old profile cover cleanup:", removeError);
    }
    return reply(200, { success: true, deleted: true });
  }

  const path = String(body.p_cover_path ?? "");
  const match = path.match(new RegExp("^" + auth.user.id + "/cover-([0-9a-f-]{36})\\\\.(jpg|png|webp)$", "i"));
  if (!match) return reply(400, { error: "مسیر تصویر معتبر نیست." });

  const { data: file, error: downloadError } = await admin.storage.from("ex1-avatar-review").download(path);
  if (downloadError || !file) return reply(404, { error: "تصویر موقت پیدا نشد؛ دوباره انتخابش کن." });
  if (file.size < 1 || file.size > 5 * 1024 * 1024) return reply(400, { error: "حجم تصویر باید حداکثر ۵ مگابایت باشد." });
  if (!new Set(["image/jpeg", "image/png", "image/webp"]).has(file.type)) return reply(400, { error: "فقط تصویر JPG، PNG یا WebP مجاز است." });

  const { data: caseRow, error: caseError } = await admin.from("ai_moderation_cases").insert({
    user_id: auth.user.id,
    content_type: "profile_cover",
    source_id: auth.user.id,
    original_storage_bucket: "ex1-avatar-review",
    original_storage_path: path,
    evidence_bucket: "ex1-avatar-review",
    evidence_path: path,
    moderation_categories: { manual_owner_review: true },
    moderation_scores: {},
    severity: "medium",
    confidence: null,
    model_name: "manual-owner-review",
    status: "pending_review",
  }).select("id").single();
  if (caseError || !caseRow) return reply(500, { error: "ثبت درخواست بررسی مالک انجام نشد؛ تصویر عمومی نشد." });

  return reply(200, {
    success: false,
    pending_review: true,
    visible: false,
    case_id: caseRow.id,
    message: "تصویر برای بررسی مالک ارسال شد و تا تأیید او عمومی نمی‌شود.",
  });
});
