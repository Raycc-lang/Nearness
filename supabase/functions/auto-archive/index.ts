// Nearness — auto-archive Edge Function
//
// Archives (does NOT delete) whiteboard posts older than 5 days. Top-level
// posts older than 5 days are archived and the archive cascades to their
// replies (a reply never outlives its parent). Intended to run nightly at
// 03:00 UTC.
//
// The archiving logic lives in the SQL function
// public.archive_old_whiteboard_items() (see schema.sql) so it stays in sync
// with the equivalent cron.sql job.
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
  const { data, error } = await supabase.rpc("archive_old_whiteboard_items");

  if (error) {
    console.error(error);
    return new Response(JSON.stringify({ error: error.message }), { status: 500 });
  }

  return new Response(JSON.stringify({ archived: data ?? 0 }), {
    headers: { "Content-Type": "application/json" },
  });
});
