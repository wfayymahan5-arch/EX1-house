// EX1 LEGEND — Gemini vision adapter for room-result screenshots.
// Deliberately disabled until the project owner reviews Google's Free Tier data terms
// and explicitly enables this provider. This function only extracts candidates;
// it never writes official results or updates player statistics.

const corsHeaders = {
  "Access-Control-Allow-Origin": "null",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type, x-ex1-results-worker",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

const json = (status: number, body: Record<string, unknown>) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { ...corsHeaders, "Content-Type": "application/json; charset=utf-8" },
  });

function safeEqual(a: string, b: string): boolean {
  if (a.length !== b.length) return false;
  let diff = 0;
  for (let i = 0; i < a.length; i++) diff |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return diff === 0;
}

const MAX_IMAGES = 8;
const MAX_IMAGE_BYTES = 4 * 1024 * 1024;
const ALLOWED_MIME = new Set(["image/jpeg", "image/png", "image/webp"]);

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "POST") return json(405, { error: "method_not_allowed" });

  const workerSecret = Deno.env.get("EX1_RESULTS_WORKER_SECRET") ?? "";
  const suppliedSecret = req.headers.get("x-ex1-results-worker") ?? "";
  if (!workerSecret || !safeEqual(workerSecret, suppliedSecret)) {
    return json(401, { error: "unauthorized" });
  }

  // Fail closed: no external image transfer unless all three server-side gates are set.
  const enabled = Deno.env.get("EX1_RESULTS_AI_ENABLED") === "true";
  const dataConsent = Deno.env.get("EX1_RESULTS_AI_DATA_TERMS_APPROVED") === "true";
  const apiKey = Deno.env.get("GEMINI_API_KEY") ?? "";
  if (!enabled || !dataConsent || !apiKey) {
    return json(503, {
      error: "provider_not_enabled",
      message: "The project owner must review and approve the provider's data terms before image processing is enabled.",
    });
  }

  let body: { room_id?: string; images?: Array<{ mime_type?: string; data_base64?: string }> };
  try {
    body = await req.json();
  } catch {
    return json(400, { error: "invalid_json" });
  }

  if (!body.room_id || !/^[0-9a-f-]{36}$/i.test(body.room_id)) {
    return json(400, { error: "invalid_room_id" });
  }
  if (!Array.isArray(body.images) || body.images.length < 1 || body.images.length > MAX_IMAGES) {
    return json(400, { error: "invalid_images_count", max_images: MAX_IMAGES });
  }

  const parts: Array<Record<string, unknown>> = [{
    text: [
      "Analyze these Call of Duty Mobile end-of-match result screenshots for EX1 LEGEND.",
      "Extract only information visibly supported by the screenshots.",
      "Do not guess unreadable names, UIDs, placements, kills, team associations, or scores.",
      "If a value is unclear, use null and add a concise item to uncertainties.",
      "Do not infer an EX1 account identity. Return game-visible identifiers only.",
      "Multiple screenshots may show overlapping parts of the same result; do not duplicate players.",
      "Return JSON only with this exact shape:",
      '{"room_id":"string","players":[{"game_name":"string|null","game_uid":"string|null","team_label":"string|null","placement":"number|null","kills":"number|null","points":"number|null","confidence":"high|medium|low","evidence_image_indexes":[0]}],"uncertainties":["string"],"screenshots_consistent":"boolean"}',
      "room_id: " + body.room_id,
    ].join("\n"),
  }];

  for (const [index, image] of body.images.entries()) {
    const mime = image?.mime_type ?? "";
    const encoded = image?.data_base64 ?? "";
    if (!ALLOWED_MIME.has(mime) || !encoded || encoded.length > Math.ceil(MAX_IMAGE_BYTES * 4 / 3) + 16) {
      return json(400, { error: "invalid_image", image_index: index });
    }
    let bytes: Uint8Array;
    try {
      const binary = atob(encoded.replace(/^data:[^,]+,/, ""));
      if (binary.length > MAX_IMAGE_BYTES) return json(413, { error: "image_too_large", image_index: index });
      bytes = Uint8Array.from(binary, (char) => char.charCodeAt(0));
    } catch {
      return json(400, { error: "invalid_image_data", image_index: index });
    }
    // Preserve the validated base64 payload; avoid large spread operations on mobile-sized images.
    const base64 = encoded.replace(/^data:[^,]+,/, "");
    parts.push({ text: "Screenshot index " + index });
    parts.push({ inline_data: { mime_type: mime, data: base64 } });
  }

  const model = Deno.env.get("GEMINI_RESULTS_MODEL") || "gemini-3.5-flash-lite";
  let upstream: Response;
  try {
    upstream = await fetch(
      "https://generativelanguage.googleapis.com/v1beta/models/" + encodeURIComponent(model) + ":generateContent",
      {
        method: "POST",
        headers: { "Content-Type": "application/json", "x-goog-api-key": apiKey },
        body: JSON.stringify({
          contents: [{ role: "user", parts }],
          generationConfig: { temperature: 0, responseMimeType: "application/json" },
        }),
        signal: AbortSignal.timeout(45_000),
      },
    );
  } catch {
    return json(502, { error: "provider_unreachable" });
  }

  if (!upstream.ok) {
    // Never return provider response bodies; they may contain request details.
    return json(upstream.status === 429 ? 429 : 502, {
      error: upstream.status === 429 ? "provider_rate_limited" : "provider_request_failed",
    });
  }

  let result: any;
  try {
    result = await upstream.json();
  } catch {
    return json(502, { error: "invalid_provider_response" });
  }

  const text = result?.candidates?.[0]?.content?.parts?.map((p: any) => p.text || "").join("") ?? "";
  try {
    const extracted = JSON.parse(text);
    if (!extracted || !Array.isArray(extracted.players) || !Array.isArray(extracted.uncertainties)) {
      throw new Error("invalid_shape");
    }
    // Output remains a review candidate; caller must not publish it as official results.
    return json(200, {
      status: "review_required",
      provider: "google-gemini",
      model,
      room_id: body.room_id,
      extracted,
      official_write_performed: false,
    });
  } catch {
    return json(502, { error: "unparseable_extraction" });
  }
});
