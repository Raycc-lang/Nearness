// Nearness — auto-archive Edge Function
//
// Archives (does NOT delete) whiteboard items older than 5 days by setting
// archived_at. Intended to run nightly at 03:00 UTC.
//
// Two ways to schedule:
//   1. Supabase Cron invoking this function over HTTP, or
//   2. The equivalent SQL job in cron.sql (no function needed).
//
// Required secrets:
//   SUPABASE_URL
//   SUPABASE_SERVICE_ROLE_KEY

import { createClient } from "jsr:@supabase/supabase-js@2";

const supabase = createClient(
  Deno.env.get("SUPABASE_URL")!,
  Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
);

Deno.serve(async () => {
  const cutoff = new Date(Date.now() - 5 * 24 * 60 * 60 * 1000).toISOString();

  const { data, error } = await supabase
    .from("whiteboard_items")
    .update({ archived_at: new Date().toISOString() })
    .is("archived_at", null)
    .lt("created_at", cutoff)
    .select("id");

  if (error) {
    console.error(error);
    return new Response(JSON.stringify({ error: error.message }), { status: 500 });
  }

  return new Response(JSON.stringify({ archived: data?.length ?? 0 }), {
    headers: { "Content-Type": "application/json" },
  });
});
