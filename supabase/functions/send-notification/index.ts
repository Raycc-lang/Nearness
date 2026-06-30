// Nearness — send-notification Edge Function
//
// Triggered by Database Webhooks:
//   - INSERT on `signals`           → HIGH importance "signals" channel
//   - INSERT on `whiteboard_items`  → DEFAULT importance "whiteboard" channel
//                                     (top-level posts AND replies)
//   - UPDATE on `status`            → LOW importance "status" channel
//
// It resolves the recipient (the partner, via the partnerships table) and
// their FCM token, then sends via the FCM HTTP v1 API.
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

// Body text for preset signals, phrased as a gesture from the sender
// (e.g. "[Name]" as title + "sent you a hug 🫂" as body).
const SIGNAL_LABELS: Record<string, string> = {
  hug: "sent you a hug 🫂",
  kiss: "sent you a kiss 💋",
  good_morning: "says good morning ☀️",
  good_night: "says good night 🌙",
  thinking_of_you: "is thinking of you 💭",
};

const ACTIVITY_LABELS: Record<string, string> = {
  resting: "🌙 Resting",
  working: "💼 Working",
  free: "🌿 Free",
  out: "🚶 Out",
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

async function tokenOf(userId: string): Promise<string | null> {
  const { data } = await supabase
    .from("profiles")
    .select("fcm_token")
    .eq("id", userId)
    .single();
  return data?.fcm_token ?? null;
}

// The completed partnership a user belongs to (user_b_id non-null).
async function partnerOf(userId: string): Promise<string | null> {
  const { data } = await supabase
    .from("partnerships")
    .select("user_a_id, user_b_id")
    .not("user_b_id", "is", null)
    .or(`user_a_id.eq.${userId},user_b_id.eq.${userId}`)
    .maybeSingle();
  if (!data) return null;
  return data.user_a_id === userId ? (data.user_b_id as string) : (data.user_a_id as string);
}

// The member of a partnership who is not the given user.
async function otherMember(partnershipId: string, userId: string): Promise<string | null> {
  const { data } = await supabase
    .from("partnerships")
    .select("user_a_id, user_b_id")
    .eq("id", partnershipId)
    .single();
  if (!data || !data.user_b_id) return null;
  return data.user_a_id === userId ? (data.user_b_id as string) : (data.user_a_id as string);
}

Deno.serve(async (req) => {
  try {
    const payload = (await req.json()) as WebhookPayload;
    const { table, type, record } = payload;

    if (table === "signals" && type === "INSERT") {
      const receiver = record.receiver_id as string;
      const token = await tokenOf(receiver);
      if (!token) return new Response("no token", { status: 200 });
      const name = await nameOf(record.sender_id as string);
      const text = record.type === "custom"
        ? (record.custom_text as string)
        : (SIGNAL_LABELS[record.type as string] ?? "sent you a signal");
      await sendFcm(token, name, text, "signals");
    } else if (table === "whiteboard_items" && type === "INSERT") {
      // Notify the *other* member of the partnership, not the author.
      const author = record.author_id as string;
      const recipient = await otherMember(record.partnership_id as string, author);
      if (!recipient) return new Response("no partner", { status: 200 });
      const token = await tokenOf(recipient);
      if (!token) return new Response("no token", { status: 200 });
      const name = await nameOf(author);
      const body = record.parent_id
        ? "replied to a post"
        : "added something to the whiteboard";
      await sendFcm(token, name, body, "whiteboard");
    } else if (table === "status" && type === "UPDATE") {
      const owner = record.user_id as string;
      const partner = await partnerOf(owner);
      if (!partner) return new Response("no partner", { status: 200 });
      const token = await tokenOf(partner);
      if (!token) return new Response("no token", { status: 200 });
      const name = await nameOf(owner);
      const label = ACTIVITY_LABELS[record.activity as string] ?? "their status";
      await sendFcm(token, name, `is now ${label}`, "status");
    }

    return new Response("ok", { status: 200 });
  } catch (err) {
    console.error(err);
    return new Response("error", { status: 500 });
  }
});
