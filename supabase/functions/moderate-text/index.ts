import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2.119.0";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

function reply(status: number, body: Record<string, unknown>) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...corsHeaders, "Content-Type": "application/json" },
  });
}

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "POST") return reply(405, { error: "روش درخواست مجاز نیست." });

  const supabaseUrl = Deno.env.get("SUPABASE_URL") ?? "";
  const anonKey = Deno.env.get("SUPABASE_ANON_KEY") ?? "";
  const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
  const openAiKey = Deno.env.get("OPENAI_API_KEY") ?? "";
  const token = (req.headers.get("Authorization") ?? "").replace(/^Bearer\s+/i, "");
  if (!token || !supabaseUrl || !anonKey || !serviceRoleKey) {
    return reply(401, { error: "نشست معتبر نیست." });
  }
  if (!openAiKey) return reply(503, { error: "سرویس بررسی محتوا پیکربندی نشده است." });

  const caller = createClient(supabaseUrl, anonKey, {
    global: { headers: { Authorization: `Bearer ${token}` } },
    auth: { persistSession: false, autoRefreshToken: false },
  });
  const { data: authData, error: authError } = await caller.auth.getUser(token);
  if (authError || !authData.user) return reply(401, { error: "ورود معتبر نیست." });

  let body: { content_text?: string; content_type?: string; source_id?: string };
  try { body = await req.json(); } catch { return reply(400, { error: "درخواست معتبر نیست." }); }
  const contentText = String(body.content_text ?? "").trim();
  const allowedTypes = new Set(["chat_message", "profile_text", "team_description", "social_post_text"]);
  const contentType = String(body.content_type ?? "");
  const sourceId = body.source_id == null ? null : String(body.source_id);
  if (sourceId && !/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(sourceId)) {
    return reply(400, { error: "شناسه محتوا معتبر نیست." });
  }
  if (!contentText || contentText.length > 6000 || !allowedTypes.has(contentType)) {
    return reply(400, { error: "نوع یا متن محتوا معتبر نیست." });
  }

  const response = await fetch("https://api.openai.com/v1/moderations", {
    method: "POST",
    headers: { Authorization: `Bearer ${openAiKey}`, "Content-Type": "application/json" },
    body: JSON.stringify({ model: "omni-moderation-latest", input: contentText }),
  });
  if (!response.ok) {
    console.error("OpenAI moderation request failed:", response.status);
    return reply(502, { error: "بررسی محتوا موقتاً انجام نشد؛ لطفاً دوباره تلاش کن." });
  }

  const result = await response.json();
  const moderation = result?.results?.[0];
  if (!moderation) return reply(502, { error: "پاسخ سرویس بررسی محتوا معتبر نبود." });
  if (moderation.flagged !== true) return reply(200, { allowed: true, flagged: false });

  const categories = moderation.categories ?? {};
  const scores = moderation.category_scores ?? {};
  const numericScores = Object.values(scores).filter((value): value is number => typeof value === "number");
  const confidence = numericScores.length ? Math.max(...numericScores) : null;
  const severe = Math.max(
    Number(scores["sexual/minors"] ?? 0),
    Number(scores["violence/graphic"] ?? 0),
    Number(scores["self-harm/instructions"] ?? 0),
  );
  const severity = severe >= 0.5 ? "critical" : (confidence ?? 0) >= 0.85 ? "high" : "medium";

  const admin = createClient(supabaseUrl, serviceRoleKey, {
    auth: { persistSession: false, autoRefreshToken: false },
  });
  const { data: caseRow, error: caseError } = await admin
    .from("ai_moderation_cases")
    .insert({
      user_id: authData.user.id,
      content_type: contentType,
      source_id: sourceId,
      content_text: contentText,
      moderation_categories: categories,
      moderation_scores: scores,
      severity,
      confidence,
      model_name: "omni-moderation-latest",
      status: "pending_review",
    })
    .select("id")
    .single();

  if (caseError || !caseRow) {
    console.error("Could not create moderation case:", caseError);
    return reply(500, { error: "ثبت گزارش بررسی محتوا انجام نشد؛ محتوا منتشر نشد." });
  }

  return reply(200, {
    allowed: false,
    flagged: true,
    pending_review: true,
    case_id: caseRow.id,
    message: "این محتوا تا بررسی مالک منتشر نمی‌شود.",
  });
});
