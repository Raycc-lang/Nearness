-- Nearness — scheduled jobs (pg_cron)
-- Run after schema.sql. Requires the pg_cron extension (Supabase: enable
-- under Database → Extensions).

create extension if not exists pg_cron;

-- Nightly auto-archive at 03:00 UTC.
-- This mirrors the auto-archive Edge Function so archiving works even if the
-- function is not deployed. Items older than 5 days are archived, not deleted.
-- Top-level posts older than 5 days are archived, and the archive cascades to
-- their replies (a reply never outlives its parent). Logic lives in
-- public.archive_old_whiteboard_items() (defined in schema.sql).
select cron.schedule(
    'nearness-auto-archive',
    '0 3 * * *',
    $$ select public.archive_old_whiteboard_items(); $$
);

-- To remove the job:
--   select cron.unschedule('nearness-auto-archive');
