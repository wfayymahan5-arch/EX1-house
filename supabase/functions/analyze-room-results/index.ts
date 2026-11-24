// EX1 LEGEND — guarded Gemini vision adapter for room-result screenshots.
// Extracts candidate data only. It never writes official results, wallets, or profile stats.
// Keep disabled until the project owner reviews Google's current data terms.

import { createClient } from "npm:@supabase/supabase-js@2";

const MAX_IMAGES = 8;
const MAX_IMAGE_BYTES = 4 * 1024 * 1024;
const ALLOWED_MIME = new Set(["image/jpeg", "image/png", "image/webp"]);
const DEFAULT_ORIGINS = ["https://ex1-house.vercel.app"];

type InputImage = { mime_type?: string; data_base64?: string };
type Candidate = {
  game_uid: string | null;
  placement: number | null;
  kills: number | null;
  confidence: "high" | "medium" | "low";
  evidence_image_indexes: number[];
};

function allowedOrigins(): Set<string> {
  const configured = (Deno.env.get("EX1_RESULTS_ALLOWED_ORIGINS") ?? "")
    .split(",").map((value) => value.trim()).filter(Boolean);
  return new Set([...DEFAULT_ORIGINS, ...configured]);
}

function corsHeaders(req: Request): HeadersInit {
  const origin = req.headers.get("origin");
  return {
    ...(origin && allowedOrigins().has(origin) ? { "Access-Control-Allow-Origin": origin, "Vary": "Origin" } : {}),
    "Access-Control-Allow-Headers": "authorization, apikey, x-client-info, content-type",
    "Access-Control-Allow-Methods": "POST, OPTIONS",
    "Content-Type": "application/json; charset=utf-8",
  };
}

function json(req: Request, status: number, body: Record<string, unknown>) {
  return new Response(JSON.stringify(body), { status, headers: corsHeaders(req) });
}

function decodeImage(encodedInput: string, mimeType: string): Uint8Array | null {
  try {
    const encoded = encodedInput.replace(/^data:[^,]+,/, "");
    const binary = atob(encoded);
    if (binary.length < 16 || binary.length > MAX_IMAGE_BYTES) return null;
    const bytes = Uint8Array.from(binary, (char) => char.charCodeAt(0));
    const isJpeg = mimeType === "image/jpeg" &&
      bytes[0] === 0xff && bytes[1] === 0xd8 && bytes[2] === 0xff;
    const isPng = mimeType === "image/png" &&
      bytes[0] === 0x89 && bytes[1] === 0x50 && bytes[2] === 0x4e &&
      bytes[3] === 0x47 && bytes[4] === 0x0d && bytes[5] === 0x0a &&
      bytes[6] === 0x1a && bytes[7] === 0x0a;
    const isWebp = mimeType === "image/webp" &&
      binary.slice(0, 4) === "RIFF" && binary.slice(8, 12) === "WEBP";
    return isJpeg || isPng || isWebp ? bytes : null;
  } catch {
    return null;
  }
}

async function sha256(bytes: Uint8Array): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", bytes);
  return Array.from(new Uint8Array(digest), (b) => b.toString(16).padStart(2, "0")).join("");
}

function cleanText(value: unknown, max: number): string | null {
  if (typeof value !== "string") return null;
  const clean = value.replace(/[\u0000-\u001f\u007f]/g, "").trim().slice(0, max);
  return clean || null;
}

function cleanMetric(value: unknown, max: number): number | null {
  if (value === null || value === undefined || value === "") return null;
  if (typeof value !== "number" && !(typeof value === "string" && /^\d+$/.test(value.trim()))) return null;
  const number = Number(value);
  return Number.isSafeInteger(number) && number >= 0 && number <= max ? number : null;
}

function normalizeCandidate(value: any, imageCount: number): Candidate | null {
  if (!value || typeof value !== "object" || Array.isArray(value)) return null;
  const confidence = ["high", "medium", "low"].includes(value.confidence) ? value.confidence : "low";
  const indexes = Array.isArray(value.evidence_image_indexes)
    ? [...new Set(value.evidence_image_indexes.filter((n: unknown) => typeof n === "number" && Number.isInteger(n) && n >= 0 && n < imageCount))]
    : [];
  return {
    game_uid: cleanText(value.game_uid, 32),
    placement: cleanMetric(value.placement, 1000),
    kills: cleanMetric(value.kills, 1000),
    confidence,
    evidence_image_indexes: indexes,
  };
}

function sameMetrics(a: Candidate, b: Candidate): boolean {
  return a.placement === b.placement && a.kills === b.kills;
}

Deno.serve(async (req: Request) => {
  const origin = req.headers.get("origin");
  if (origin && !allowedOrigins().has(origin)) return json(req, 403, { error: "origin_not_allowed" });
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders(req) });
  if (req.method !== "POST") return json(req, 405, { error: "method_not_allowed" });

  const enabled = Deno.env.get("EX1_RESULTS_AI_ENABLED") === "true";
  const termsApproved = Deno.env.get("EX1_RESULTS_AI_DATA_TERMS_APPROVED") === "true";
  const apiKey = Deno.env.get("GEMINI_API_KEY") ?? "";
  if (!enabled || !termsApproved || !apiKey) {
    return json(req, 503, { error: "provider_not_enabled" });
  }

  const supabaseUrl = Deno.env.get("SUPABASE_URL") ?? "";
  const supabaseKey = Deno.env.get("SUPABASE_ANON_KEY") ?? Deno.env.get("SUPABASE_PUBLISHABLE_KEY") ?? "";
  const authorization = req.headers.get("authorization") ?? "";
  if (!supabaseUrl || !supabaseKey || !/^Bearer\s+\S+/i.test(authorization)) {
    return json(req, 401, { error: "authentication_required" });
  }

  const supabase = createClient(supabaseUrl, supabaseKey, {
    global: { headers: { Authorization: authorization } },
    auth: { persistSession: false, autoRefreshToken: false },
  });
  const accessToken = authorization.replace(/^Bearer\s+/i, "");
  const { data: authData, error: authError } = await supabase.auth.getUser(accessToken);
  if (authError || !authData.user) return json(req, 401, { error: "invalid_session" });

  const { data: permissions, error: permissionError } = await supabase.rpc("admin_my_permissions");
  if (permissionError || !Array.isArray(permissions)) return json(req, 403, { error: "permission_check_failed" });
  const hasPermission = (code: string) => permissions.some((p: any) => p.permission_code === code && p.is_allowed === true);
  if (!hasPermission("results.manage") || !(hasPermission("rooms.view") || hasPermission("rooms.manage"))) {
    return json(req, 403, { error: "results_permission_required" });
  }

  let body: { room_id?: string; images?: InputImage[] };
  try {
    body = await req.json();
  } catch {
    return json(req, 400, { error: "invalid_json" });
  }
  if (!body.room_id || !/^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(body.room_id)) {
    return json(req, 400, { error: "invalid_room_id" });
  }

  const { data: room, error: roomError } = await supabase
    .from("tournament_rooms").select("id,status,ends_at").eq("id", body.room_id).maybeSingle();
  if (roomError || !room) return json(req, 404, { error: "room_not_found_or_not_visible" });
  const endedAt = room.ends_at ? new Date(room.ends_at).getTime() : NaN;
  if (room.status !== "completed" && !(Number.isFinite(endedAt) && endedAt <= Date.now())) {
    return json(req, 409, { error: "room_not_completed" });
  }

  if (!Array.isArray(body.images) || body.images.length < 1 || body.images.length > MAX_IMAGES) {
    return json(req, 400, { error: "invalid_images_count", max_images: MAX_IMAGES });
  }

  const parts: Array<Record<string, unknown>> = [{
    text: [
      "Analyze these Call of Duty Mobile end-of-match result screenshots for EX1 LEGEND.",
      "Extract only information visibly supported by the screenshots.",
      "Extract only the visible player UID, placement, and kill count.",
      "Do not guess unreadable UIDs, placements, or kills.",
      "If a value is unclear, use null and add a concise item to uncertainties.",
      "Do not infer an EX1 account identity. Return game-visible identifiers only.",
      "Multiple screenshots may overlap. Do not duplicate the same player when the visible UID is identical.",
      "Return JSON only with this exact shape:",
      '{"players":[{"game_uid":"string|null","placement":"number|null","kills":"number|null","confidence":"high|medium|low","evidence_image_indexes":[0]}],"uncertainties":["string"],"screenshots_consistent":"boolean"}',
    ].join("\n"),
  }];

  const hashes: string[] = [];
  const seenHashes = new Set<string>();
  for (const [index, image] of body.images.entries()) {
    const mime = image?.mime_type ?? "";
    const encoded = image?.data_base64 ?? "";
    if (!ALLOWED_MIME.has(mime) || !encoded || encoded.length > Math.ceil(MAX_IMAGE_BYTES * 4 / 3) + 64) {
      return json(req, 400, { error: "invalid_image", image_index: index });
    }
    const bytes = decodeImage(encoded, mime);
    if (!bytes) return json(req, 413, { error: "invalid_or_oversized_image", image_index: index });
    const hash = await sha256(bytes);
    if (seenHashes.has(hash)) return json(req, 409, { error: "duplicate_image_in_batch", image_index: index });
    seenHashes.add(hash);
    hashes.push(hash);
    parts.push({ text: "Screenshot index " + index });
    parts.push({ inline_data: { mime_type: mime, data: encoded.replace(/^data:[^,]+,/, "") } });
  }

  const model = Deno.env.get("GEMINI_RESULTS_MODEL") || "gemini-3.1-flash-lite";
  let upstream: Response;
  try {
    upstream = await fetch(
      "https://generativelanguage.googleapis.com/v1beta/models/" + encodeURIComponent(model) + ":generateContent",
      {
        method: "POST",
        headers: { "Content-Type": "application/json", "x-goog-api-key": apiKey },
        body: JSON.stringify({
          contents: [{ role: "user", parts }],
          generationConfig: { temperature: 0, responseMimeType: "application/json", maxOutputTokens: 4096 },
        }),
        signal: AbortSignal.timeout(45_000),
      },
    );
  } catch {
    return json(req, 502, { error: "provider_unreachable" });
  }
  if (!upstream.ok) {
    return json(req, upstream.status === 429 ? 429 : 502, {
      error: upstream.status === 429 ? "provider_rate_limited" : "provider_request_failed",
    });
  }

  let providerResult: any;
  try {
    providerResult = await upstream.json();
  } catch {
    return json(req, 502, { error: "invalid_provider_response" });
  }
  const responseText = providerResult?.candidates?.[0]?.content?.parts?.map((p: any) => p.text || "").join("") ?? "";
  try {
    const extracted = JSON.parse(responseText);
    if (!extracted || !Array.isArray(extracted.players) || !Array.isArray(extracted.uncertainties)) {
      throw new Error("invalid_shape");
    }

    const candidates = extracted.players.slice(0, 100).map((candidate: unknown) => normalizeCandidate(candidate, body.images!.length)).filter(Boolean) as Candidate[];
    const byUid = new Map<string, Candidate>();
    const reviewCandidates: Candidate[] = [];
    const uncertainties = extracted.uncertainties
      .filter((item: unknown) => typeof item === "string")
      .map((item: string) => item.slice(0, 240)).slice(0, 40);
    if (extracted.players.length > 100) uncertainties.push("بیش از ۱۰۰ ردیف از پاسخ هوش مصنوعی دریافت شد؛ فقط ۱۰۰ ردیف اول برای بازبینی نگه داشته شد.");

    for (const candidate of candidates) {
      if (!candidate.game_uid) {
        reviewCandidates.push(candidate);
        uncertainties.push("بازیکنی بدون UID قابل‌تطبیق باقی ماند و باید دستی بررسی شود.");
        continue;
      }
      const key = candidate.game_uid.toLowerCase();
      const existing = byUid.get(key);
      if (!existing) {
        byUid.set(key, candidate);
      } else if (sameMetrics(existing, candidate)) {
        existing.evidence_image_indexes = [...new Set([...existing.evidence_image_indexes, ...candidate.evidence_image_indexes])];
      } else {
        existing.confidence = "low";
        candidate.confidence = "low";
        reviewCandidates.push(candidate);
        uncertainties.push("برای UID " + candidate.game_uid + " اطلاعات متناقض در تصاویر دیده شد؛ هر دو نسخه نیازمند بازبینی هستند.");
      }
    }

    reviewCandidates.unshift(...byUid.values());
    return json(req, 200, {
      status: "review_required",
      provider: "google-gemini",
      model,
      room_id: room.id,
      extracted: {
        players: reviewCandidates,
        uncertainties: [...new Set(uncertainties)].slice(0, 50),
        screenshots_consistent: extracted.screenshots_consistent === true,
      },
      evidence_image_hashes: hashes,
      official_write_performed: false,
    });
  } catch {
    return json(req, 502, { error: "unparseable_extraction" });
  }
});
