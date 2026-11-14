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
  const token = (req.headers.get("Authorization") ?? "").replace(/^Bearer\s+/i, "");

  if (!token || !supabaseUrl || !anonKey || !serviceRoleKey) {
    return reply(401, { error: "برای استفاده از پشتیبانی ابتدا وارد حساب شو." });
  }

  const caller = createClient(supabaseUrl, anonKey, {
    global: { headers: { Authorization: `Bearer ${token}` } },
    auth: { persistSession: false, autoRefreshToken: false },
  });
  const { data: authData, error: authError } = await caller.auth.getUser(token);
  if (authError || !authData.user) return reply(401, { error: "نشست ورود معتبر نیست." });

  let body: { message?: string; conversation_id?: string };
  try { body = await req.json(); } catch { return reply(400, { error: "درخواست معتبر نیست." }); }
  const message = String(body.message ?? "").trim();
  if (!message || message.length > 3000) {
    return reply(400, { error: "پیام باید بین ۱ تا ۳۰۰۰ نویسه باشد." });
  }

  const admin = createClient(supabaseUrl, serviceRoleKey, {
    auth: { persistSession: false, autoRefreshToken: false },
  });
  const since = new Date(Date.now() - 60_000).toISOString();
  const { data: ownedConversations, error: conversationsError } = await admin
    .from("support_conversations").select("id").eq("user_id", authData.user.id);
  if (conversationsError) return reply(503, { error: "محدودیت استفاده بررسی نشد؛ کمی بعد دوباره تلاش کن." });
  const ownedIds = (ownedConversations ?? []).map((x) => x.id);
  let recentCount = 0;
  if (ownedIds.length) {
    const { count, error: rateError } = await admin
      .from("support_messages")
      .select("id", { count: "exact", head: true })
      .eq("sender_role", "user")
      .gte("created_at", since)
      .in("conversation_id", ownedIds);
    if (rateError) return reply(503, { error: "محدودیت استفاده بررسی نشد؛ کمی بعد دوباره تلاش کن." });
    recentCount = count ?? 0;
  }
  if (recentCount >= 8) return reply(429, { error: "برای جلوگیری از استفادهٔ بیش از حد، کمی صبر کن و دوباره پیام بده." });

  let conversationId = String(body.conversation_id ?? "");
  let conversationWasWaitingForHuman = false;
  if (conversationId) {
    const { data: conversation, error } = await admin
      .from("support_conversations")
      .select("id,user_id,status")
      .eq("id", conversationId)
      .eq("user_id", authData.user.id)
      .maybeSingle();
    if (error || !conversation) return reply(404, { error: "گفت‌وگوی پشتیبانی پیدا نشد." });
    conversationWasWaitingForHuman = conversation.status === "waiting_for_human";
    if (conversation.status === "closed" || conversation.status === "resolved") {
      return reply(409, { error: "این گفت‌وگو بسته شده است. یک گفت‌وگوی تازه ایجاد کن." });
    }
  } else {
    const { data: conversation, error } = await admin
      .from("support_conversations")
      .insert({ user_id: authData.user.id })
      .select("id")
      .single();
    if (error || !conversation) return reply(500, { error: "ساخت گفت‌وگوی پشتیبانی انجام نشد." });
    conversationId = conversation.id;
  }

  const { error: userMessageError } = await admin.from("support_messages").insert({
    conversation_id: conversationId, sender_role: "user", body: message,
  });
  if (userMessageError) return reply(500, { error: "ذخیره پیام انجام نشد." });

  let answer = "";
  let handoff = false;
  const normalized = message.replace(/[\u200c\s]+/g, " ").trim().toLowerCase();
  const explicitHumanRequest = /(?:وصل|ارتباط|ارجاع|صحبت).{0,24}(?:پشتیبان|انسان|اپراتور|مدیر)|(?:پشتیبان|اپراتور|مدیر).{0,24}(?:می.?خوام|می.?خواهم|وصل|صحبت)/i.test(message);

  // Once handed to a human, keep all follow-up messages in the same human-owned thread.
  if (conversationWasWaitingForHuman) {
    answer = "پیامت به همان گفت‌وگوی پشتیبانی اضافه شد. درخواستت همچنان در صف پشتیبان انسانی است و پاسخ بعدی در همین چت نمایش داده می‌شود.";
    handoff = true;
  } else if (explicitHumanRequest) {
    answer = "درخواستت برای پیگیری به پشتیبان انسانی ارجاع شد. پاسخ پشتیبان در همین گفت‌وگو نمایش داده می‌شود.";
    handoff = true;
  } else if (/^(سلام|درود|hi|hello|hey|سلام خوبی|سلام خوبی؟|خوبی؟?|وقت بخیر|صبح بخیر|شب بخیر)[!.؟\s]*$/i.test(normalized)) {
    answer = "سلام! 👋 خوش اومدی به پشتیبانی EX1 LEGEND. درباره ورود، ثبت‌نام، روم‌ها، قوانین یا پروفایلت سؤالی داری؟";
  } else if (/(ثبت.?نام|شرکت در مسابقه|ورود به مسابقه)/i.test(message)) {
    answer = "برای ثبت‌نام، وارد بخش مسابقات یا روم‌ها شو، مسابقهٔ موردنظرت رو باز کن و شرایط و قوانین همون مسابقه رو بخون. اگر گزینهٔ ثبت‌نام فعال بود، مراحل داخل اپ رو انجام بده. اگر ثبت‌نام انجام نشد، نام مسابقه و متن خطا رو برای پشتیبان انسانی بفرست.";
  } else if (/(قانون|قوانین|شرایط مسابقه)/i.test(message)) {
    answer = "قوانین هر مسابقه ممکنه متفاوت باشه؛ قبل از ثبت‌نام، بخش توضیحات و قوانین همان روم رو بررسی کن. اگر بندی نامشخصه، متنش رو بفرست تا راهنمایی‌ات کنم.";
  } else if (/(پروفایل|عکس.? پروفایل|نام کاربری)/i.test(message)) {
    answer = "برای اطلاعات پروفایل، از بخش پروفایل داخل اپ استفاده کن. اگر تغییرات ذخیره نمی‌شن یا خطا می‌بینی، متن خطا رو بفرست؛ برای بررسی حساب مشخص، باید پشتیبان انسانی پیگیری کنه.";
  } else if (/(امتیاز|امتیازات|استار|رتبه.?بندی|قهرمانان)/i.test(message)) {
    answer = "در EX1 دو نوع امتیاز می‌بینی: ۱) امتیاز داخلی یا استار کیف پول که موجودی حسابته و فقط داخل EX1 کاربرد آزمایشی داره؛ قابل خرید، فروش، انتقال یا برداشت نیست و ارزش نقدی نداره. ۲) امتیاز نتیجهٔ مسابقه که بعد از ثبت نتیجهٔ تأییدشده برای تیم یا بازیکن نمایش داده می‌شه و در رتبه‌بندی قهرمانان اثر داره. جایگاه و تعداد کیل هم کنار نتیجه ثبت می‌شن. مقدار امتیاز نتیجه را مدیر در پنل ثبت می‌کنه؛ فرمول ثابت و خودکار امتیازدهی در حال حاضر برای پاسخ‌گو تعریف نشده، پس درباره امتیاز دقیق هر مسابقه باید قوانین همان مسابقه یا پشتیبان را بررسی کنی. موجودی استار و تاریخچه‌اش را هم از بخش کیف پول در پروفایل می‌تونی ببینی.";
  } else if (/(روم|اتاق|کد روم|رمز روم)/i.test(message)) {
    answer = "اطلاعات زندهٔ روم و کد یا رمز آن در دسترس پاسخ‌گوی خودکار نیست. این مورد رو به پشتیبان انسانی ارجاع می‌دم تا وضعیتش بررسی بشه.";
    handoff = true;
  } else if (/(کیف پول|موجودی|پرداخت|برداشت|واریز|جایزه|پول)/i.test(message)) {
    answer = "برای امنیت حساب، اطلاعات کیف پول و پرداخت فقط باید توسط پشتیبان مجاز بررسی بشه. درخواستت رو به پشتیبان انسانی ارجاع می‌دم؛ لطفاً رمز یا اطلاعات بانکی نفرست.";
    handoff = true;
  } else if (/(ورود|لاگین|رمز عبور|کد ورود|ایمیل)/i.test(message)) {
    answer = "اگر برای ورود مشکل داری، ایمیل حساب و اتصال اینترنت رو بررسی کن و از گزینهٔ بازیابی یا ارسال دوبارهٔ کد در خود اپ استفاده کن. کد ورود یا رمزت رو برای هیچ‌کس نفرست. اگر مشکل ادامه داشت، پشتیبان انسانی باید بررسی کنه.";
  } else {
    answer = "پیامت ثبت شد، اما برای این سؤال پاسخ مطمئن و تأییدشده‌ای در راهنمای رایگان من ندارم. درخواستت به صف پشتیبانی انسانی فرستاده شد و پاسخ پشتیبان در همین گفت‌وگو نمایش داده می‌شه.";
    handoff = true;
  }

  const { error: saveError } = await admin.from("support_messages").insert({
    conversation_id: conversationId, sender_role: "assistant", body: answer,
  });
  if (saveError) return reply(500, { error: "پاسخ تولید شد اما ذخیره آن انجام نشد." });

  await admin.from("support_conversations").update({
    status: conversationWasWaitingForHuman || handoff ? "waiting_for_human" : "open",
    updated_at: new Date().toISOString(),
  }).eq("id", conversationId);

  return reply(200, { success: true, conversation_id: conversationId, answer, handoff });
});
