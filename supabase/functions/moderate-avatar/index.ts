import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2.119.0";

const cors = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};
const reply = (status: number, body: Record<string, unknown>) =>
  new Response(JSON.stringify(body), { status, headers: { ...cors, "Content-Type": "application/json" } });

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: cors });
  if (req.method !== "POST") return reply(405, { error: "روش درخواست مجاز نیست." });

  const url = Deno.env.get("SUPABASE_URL") ?? "";
  const anon = Deno.env.get("SUPABASE_ANON_KEY") ?? "";
  const service = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
  const openAiKey = Deno.env.get("OPENAI_API_KEY") ?? "";
  const token = (req.headers.get("Authorization") ?? "").replace(/^Bearer\s+/i, "");
  if (!url || !anon || !service || !token) return reply(401, { error: "ابتدا وارد حساب شو." });
  if (!openAiKey) return reply(503, { error: "سرویس بررسی ایمنی تصویر هنوز پیکربندی نشده است؛ تصویر عمومی نمی‌شود." });

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

  let body: { p_avatar_path?: string };
  try { body = await req.json(); } catch { return reply(400, { error: "درخواست معتبر نیست." }); }
  const path = String(body.p_avatar_path ?? "");
  const match = path.match(new RegExp("^" + auth.user.id + "/avatar-([0-9a-f-]{36})\\.(jpg|png|webp)$", "i"));
  if (!match) return reply(400, { error: "مسیر تصویر معتبر نیست." });
  const ext = match[2].toLowerCase();

  const { data: file, error: downloadError } = await admin.storage.from("ex1-avatar-review").download(path);
  if (downloadError || !file) return reply(404, { error: "تصویر موقت پیدا نشد؛ دوباره انتخابش کن." });
  if (file.size < 1 || file.size > 5 * 1024 * 1024) return reply(400, { error: "حجم تصویر باید بین ۱ بایت تا ۵ مگابایت باشد." });
  const allowed = new Set(["image/jpeg", "image/png", "image/webp"]);
  if (!allowed.has(file.type)) return reply(400, { error: "فقط تصویر JPG، PNG یا WebP مجاز است." });

  const { data: signed, error: signedError } = await admin.storage.from("ex1-avatar-review").createSignedUrl(path, 180);
  if (signedError || !signed?.signedUrl) return reply(500, { error: "لینک امن بررسی تصویر ساخته نشد." });

  let moderationResponse: Response;
  try {
    moderationResponse = await fetch("https://api.openai.com/v1/moderations", {
      method: "POST",
      headers: { Authorization: `Bearer ${openAiKey}`, "Content-Type": "application/json" },
      body: JSON.stringify({
        model: "omni-moderation-latest",
        input: [{ type: "image_url", image_url: { url: signed.signedUrl } }],
      }),
    });
  } catch {
    return reply(502, { error: "ارتباط با سرویس بررسی تصویر برقرار نشد؛ تصویر خصوصی باقی ماند." });
  }
  if (!moderationResponse.ok) {
    console.error("Avatar moderation API failed:", moderationResponse.status);
    return reply(502, { error: "بررسی ایمنی تصویر انجام نشد؛ تصویر عمومی نشد. کمی بعد دوباره تلاش کن." });
  }
  const result = (await moderationResponse.json())?.results?.[0];
  if (!result) return reply(502, { error: "پاسخ بررسی تصویر معتبر نبود؛ تصویر خصوصی باقی ماند." });

  const categories = result.categories ?? {};
  const scores = result.category_scores ?? {};
  const applied = result.category_applied_input_types ?? {};
  const imageCategories = ["sexual", "self-harm", "self-harm/intent", "self-harm/instructions", "violence", "violence/graphic"];
  const flaggedCategories = imageCategories.filter((category) =>
    categories[category] === true && (!Array.isArray(applied[category]) || applied[category].includes("image"))
  );

  if (flaggedCategories.length) {
    const relevantScores = flaggedCategories.map((category) => Number(scores[category] ?? 0));
    const confidence = relevantScores.length ? Math.max(...relevantScores) : null;
    const critical = flaggedCategories.includes("violence/graphic") || flaggedCategories.includes("self-harm/instructions");
    const { data: caseRow, error: caseError } = await admin.from("ai_moderation_cases").insert({
      user_id: auth.user.id,
      content_type: "profile_avatar",
      source_id: auth.user.id,
      original_storage_bucket: "ex1-avatar-review",
      original_storage_path: path,
      evidence_bucket: "ex1-avatar-review",
      evidence_path: path,
      moderation_categories: categories,
      moderation_scores: scores,
      severity: critical ? "critical" : "high",
      confidence,
      model_name: "omni-moderation-latest",
      status: "pending_review",
    }).select("id").single();
    if (caseError || !caseRow) {
      console.error("Avatar moderation case insert failed:", caseError);
      return reply(500, { error: "ثبت پرونده بررسی انجام نشد؛ تصویر خصوصی باقی ماند." });
    }
    return reply(200, {
      success: false,
      pending_review: true,
      case_id: caseRow.id,
      message: "تصویر تا بررسی مالک در پروفایل عمومی نمایش داده نمی‌شود.",
    });
  }

  const publicPath = `${auth.user.id}/avatar-${match[1]}.${ext}`;
  const { error: publicUploadError } = await admin.storage.from("ex1-avatars").upload(publicPath, file, {
    upsert: true,
    contentType: file.type,
    cacheControl: "3600",
  });
  if (publicUploadError) {
    console.error("Avatar public storage upload failed:", publicUploadError);
    return reply(500, { error: "ذخیره تصویر پس از بررسی انجام نشد؛ تصویر خصوصی باقی ماند." });
  }

  const { data: oldProfile } = await admin.from("profiles").select("avatar_url").eq("id", auth.user.id).maybeSingle();
  const { error: profileError } = await admin.from("profiles").upsert(
    { id: auth.user.id, avatar_url: publicPath },
    { onConflict: "id" },
  );
  if (profileError) {
    await admin.storage.from("ex1-avatars").remove([publicPath]);
    console.error("Avatar profile update failed:", profileError);
    return reply(500, { error: "ثبت تصویر در پروفایل انجام نشد." });
  }
  const oldPath = String(oldProfile?.avatar_url ?? "");
  if (oldPath && oldPath !== publicPath && oldPath.startsWith(auth.user.id + "/avatar-")) {
    await admin.storage.from("ex1-avatars").remove([oldPath]);
  }

  await admin.storage.from("ex1-avatar-review").remove([path]);
  return reply(200, { success: true, avatar_path: publicPath, moderated: true });
});
