// Nearness — send-notification Edge Function
//
// Triggered by Database Webhooks:
//   - INSERT on `signals`           → HIGH importance "signals" channel
//   - INSERT on `whiteboard_items`  → DEFAULT importance "whiteboard" channel
//   - UPDATE on `status`            → LOW importance "status" channel
//
// It looks up the receiver's FCM token and sends via the FCM HTTP v1 API.
//
// Required secrets (Supabase Dashboard → Edge Functions → Secrets):
//   SUPABASE_URL
//   SUPABASE_SERVICE_ROLE_KEY   (service role — bypasses RLS for token lookup)
//   FCM_PROJECT_ID
//   FCM_SERVICE_ACCOUNT         (JSON service account key, as a string)

import { createClient } from "jsr:@supabase/supabase-js@2";

interface WebhookPayload {
  type: "INSERT" | "UPDATE" | "DELETE";
  table: string;
  record: Record<string, unknown>;
  old_record: Record<string, unknown> | null;
}

const supabase = createClient(
  Deno.env.get("SUPABASE_URL")!,
  Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
);

const SIGNAL_LABELS: Record<string, string> = {
  thinking_of_you: "🤍 Thinking of you",
  good_morning: "☀️ Good morning",
  good_night: "🌙 Good night",
  miss_you: "👀 Miss you",
};

const ACTIVITY_LABELS: Record<string, string> = {
  resting: "Resting",
  working: "Working",
  free: "Free",
  out: "Out",
};

async function getAccessToken(): Promise<string> {
  const sa = JSON.parse(Deno.env.get("FCM_SERVICE_ACCOUNT")!);
  const now = Math.floor(Date.now() / 1000);
  const header = { alg: "RS256", typ: "JWT" };
  const claim = {
    iss: sa.client_email,
    scope: "https://www.googleapis.com/auth/firebase.messaging",
    aud: "https://oauth2.googleapis.com/token",
    iat: now,
    exp: now + 3600,
  };

  const enc = (obj: unknown) =>
    btoa(JSON.stringify(obj)).replace(/=/g, "").replace(/\+/g, "-").replace(/\//g, "_");
  const unsigned = `${enc(header)}.${enc(claim)}`;

  const keyData = sa.private_key
    .replace(/-----BEGIN PRIVATE KEY-----/, "")
    .replace(/-----END PRIVATE KEY-----/, "")
    .replace(/\s/g, "");
  const keyBytes = Uint8Array.from(atob(keyData), (c) => c.charCodeAt(0));
  const key = await crypto.subtle.importKey(
    "pkcs8",
    keyBytes,
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const sig = await crypto.subtle.sign(
    "RSASSA-PKCS1-v1_5",
    key,
    new TextEncoder().encode(unsigned),
  );
  const sigB64 = btoa(String.fromCharCode(...new Uint8Array(sig)))
    .replace(/=/g, "").replace(/\+/g, "-").replace(/\//g, "_");
  const jwt = `${unsigned}.${sigB64}`;

  const res = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion: jwt,
    }),
  });
  const json = await res.json();
  return json.access_token;
}

async function sendFcm(token: string, title: string, body: string, channelId: string) {
  const projectId = Deno.env.get("FCM_PROJECT_ID")!;
  const accessToken = await getAccessToken();

  await fetch(`https://fcm.googleapis.com/v1/projects/${projectId}/messages:send`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${accessToken}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      message: {
        token,
        notification: { title, body },
        android: { notification: { channel_id: channelId } },
      },
    }),
  });
}

async function nameOf(userId: string): Promise<string> {
  const { data } = await supabase
    .from("profiles")
    .select("display_name")
    .eq("id", userId)
    .single();
  return data?.display_name ?? "Your partner";
}

async function tokenAndPartner(userId: string): Promise<{ token: string | null; partner: string | null }> {
  const { data } = await supabase
    .from("profiles")
    .select("partner_id, fcm_token")
    .eq("id", userId)
    .single();
  return { token: data?.fcm_token ?? null, partner: data?.partner_id ?? null };
}

Deno.serve(async (req) => {
  try {
    const payload = (await req.json()) as WebhookPayload;
    const { table, type, record } = payload;

    if (table === "signals" && type === "INSERT") {
      const receiver = record.receiver_id as string;
      const { token } = await tokenAndPartner(receiver);
      if (!token) return new Response("no token", { status: 200 });
      const name = await nameOf(record.sender_id as string);
      const text = record.type === "custom"
        ? (record.custom_text as string)
        : (SIGNAL_LABELS[record.type as string] ?? "sent you a signal");
      await sendFcm(token, name, text, "signals");
    } else if (table === "whiteboard_items" && type === "INSERT") {
      // Notify the *other* member of the pair, not the author.
      const author = record.author_id as string;
      const { partner } = await tokenAndPartner(author);
      if (!partner) return new Response("no partner", { status: 200 });
      const { token } = await tokenAndPartner(partner);
      if (!token) return new Response("no token", { status: 200 });
      const name = await nameOf(author);
      const hint = record.type === "photo" ? "a photo"
        : record.type === "voice" ? "a voice memo" : "a note";
      await sendFcm(token, `${name} left something for you`, hint, "whiteboard");
    } else if (table === "status" && type === "UPDATE") {
      const owner = record.user_id as string;
      const { partner } = await tokenAndPartner(owner);
      if (!partner) return new Response("no partner", { status: 200 });
      const { token } = await tokenAndPartner(partner);
      if (!token) return new Response("no token", { status: 200 });
      const name = await nameOf(owner);
      const label = ACTIVITY_LABELS[record.activity as string] ?? "their status";
      await sendFcm(token, `${name} updated their status`, label, "status");
    }

    return new Response("ok", { status: 200 });
  } catch (err) {
    console.error(err);
    return new Response("error", { status: 500 });
  }
});
