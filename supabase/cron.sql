-- Nearness — scheduled jobs (pg_cron)
-- Run after schema.sql. Requires the pg_cron extension (Supabase: enable
-- under Database → Extensions).

create extension if not exists pg_cron;

-- Nightly auto-archive at 03:00 UTC.
-- This mirrors the auto-archive Edge Function so archiving works even if the
-- function is not deployed. Items older than 5 days are archived, not deleted.
select cron.schedule(
    'nearness-auto-archive',
    '0 3 * * *',
    $$
        update public.whiteboard_items
        set archived_at = now()
        where archived_at is null
          and created_at < now() - interval '5 days';
    $$
);

-- To remove the job:
--   select cron.unschedule('nearness-auto-archive');
