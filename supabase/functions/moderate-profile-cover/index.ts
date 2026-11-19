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
  const openaiKey = Deno.env.get("OPENAI_API_KEY") ?? "";
  const token = (req.headers.get("Authorization") ?? "").replace(/^Bearer\s+/i, "");
  if (!url || !anon || !service || !token) return reply(401, { error: "ابتدا وارد حساب شو." });
  if (!openaiKey) return reply(503, { error: "سرویس بررسی تصویر پیکربندی نشده است؛ تصویر ذخیره نشد." });

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
  const match = path.match(new RegExp("^" + auth.user.id + "/cover-([0-9a-f-]{36})\\.(jpg|png|webp)$", "i"));
  if (!match) return reply(400, { error: "مسیر تصویر معتبر نیست." });

  const { data: file, error: downloadError } = await admin.storage.from("ex1-avatar-review").download(path);
  if (downloadError || !file) return reply(404, { error: "تصویر موقت پیدا نشد؛ دوباره انتخابش کن." });
  if (file.size < 1 || file.size > 5 * 1024 * 1024) return reply(400, { error: "حجم تصویر باید حداکثر ۵ مگابایت باشد." });
  if (!new Set(["image/jpeg", "image/png", "image/webp"]).has(file.type)) return reply(400, { error: "فقط تصویر JPG، PNG یا WebP مجاز است." });

  const { data: signed, error: signedError } = await admin.storage.from("ex1-avatar-review").createSignedUrl(path, 180);
  if (signedError || !signed?.signedUrl) return reply(500, { error: "لینک امن بررسی تصویر ساخته نشد." });

  let moderationResponse: Response;
  try {
    moderationResponse = await fetch("https://api.openai.com/v1/moderations", {
      method: "POST",
      headers: { Authorization: `Bearer ${openaiKey}`, "Content-Type": "application/json" },
      body: JSON.stringify({ model: "omni-moderation-latest", input: [{ type: "image_url", image_url: { url: signed.signedUrl } }] }),
    });
  } catch {
    return reply(502, { error: "ارتباط با سرویس بررسی تصویر برقرار نشد؛ تصویر ذخیره نشد." });
  }
  if (!moderationResponse.ok) return reply(502, { error: "بررسی هوش مصنوعی انجام نشد؛ تصویر ذخیره نشد." });
  const result = (await moderationResponse.json())?.results?.[0];
  if (!result) return reply(502, { error: "پاسخ بررسی تصویر معتبر نبود." });

  const categories = result.categories ?? {};
  const scores = result.category_scores ?? {};
  const applied = result.category_applied_input_types ?? {};
  const flaggedCategories = Object.keys(categories).filter((category) =>
    categories[category] === true && (!Array.isArray(applied[category]) || applied[category].includes("image"))
  );
  const flagged = result.flagged === true || flaggedCategories.length > 0;
  const criticalSet = new Set(["sexual", "sexual/minors", "self-harm", "self-harm/intent", "self-harm/instructions", "violence/graphic"]);
  const critical = flaggedCategories.some((category) => criticalSet.has(category));

  if (flagged && critical) {
    const { data: caseRow, error: caseError } = await admin.from("ai_moderation_cases").insert({
      user_id: auth.user.id, content_type: "profile_cover", source_id: auth.user.id,
      original_storage_bucket: "ex1-avatar-review", original_storage_path: path,
      evidence_bucket: "ex1-avatar-review", evidence_path: path,
      moderation_categories: categories, moderation_scores: scores, severity: "critical",
      confidence: Math.max(...flaggedCategories.map((category) => Number(scores[category] ?? 0))),
      model_name: "omni-moderation-latest", status: "pending_review",
    }).select("id").single();
    if (caseError || !caseRow) return reply(500, { error: "ثبت گزارش بررسی انجام نشد؛ تصویر عمومی نشد." });
    return reply(200, { success: false, pending_review: true, visible: false, case_id: caseRow.id, message: "این تصویر تا بررسی مالک در پروفایل عمومی نمایش داده نمی‌شود." });
  }

  const publicPath = `${auth.user.id}/cover-${match[1]}.${match[2].toLowerCase()}`;
  const { error: uploadError } = await admin.storage.from("ex1-avatars").upload(publicPath, file, {
    upsert: true, contentType: file.type, cacheControl: "3600",
  });
  if (uploadError) return reply(500, { error: "ذخیره عمومی تصویر انجام نشد." });

  const { error: updateError } = await admin.from("profiles").update({ cover_url: publicPath }).eq("id", auth.user.id);
  if (updateError) {
    await admin.storage.from("ex1-avatars").remove([publicPath]);
    return reply(500, { error: "ثبت تصویر در پروفایل انجام نشد." });
  }

  let caseId: string | null = null;
  if (flagged) {
    const { data: caseRow, error: caseError } = await admin.from("ai_moderation_cases").insert({
      user_id: auth.user.id, content_type: "profile_cover", source_id: auth.user.id,
      original_storage_bucket: "ex1-avatars", original_storage_path: publicPath,
      evidence_bucket: "ex1-avatars", evidence_path: publicPath,
      moderation_categories: categories, moderation_scores: scores,
      severity: Math.max(...flaggedCategories.map((category) => Number(scores[category] ?? 0))) >= 0.85 ? "high" : "medium",
      confidence: Math.max(...flaggedCategories.map((category) => Number(scores[category] ?? 0))),
      model_name: "omni-moderation-latest", status: "pending_review",
    }).select("id").single();
    if (caseError || !caseRow) {
      await admin.from("profiles").update({ cover_url: oldProfile.cover_url }).eq("id", auth.user.id);
      await admin.storage.from("ex1-avatars").remove([publicPath]);
      return reply(500, { error: "ثبت گزارش هوش مصنوعی انجام نشد؛ پس‌زمینه قبلی حفظ شد." });
    }
    caseId = caseRow.id;
  }

  if (oldPath && oldPath !== publicPath && oldPath.startsWith(auth.user.id + "/cover-")) {
    const { error: cleanupError } = await admin.storage.from("ex1-avatars").remove([oldPath]);
    if (cleanupError) console.error("Old profile cover cleanup:", cleanupError);
  }
  await admin.storage.from("ex1-avatar-review").remove([path]);
  return reply(200, { success: true, visible: true, reported: flagged, pending_review: flagged, case_id: caseId, cover_path: publicPath });
});
