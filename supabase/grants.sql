-- Nearness — table privileges for the Supabase authenticated/anon roles.
-- rls.sql controls row-level access; these GRANTs give the roles the base
-- table privileges PostgREST needs to attempt a query at all. Without them
-- every request fails with "permission denied for table <name>".
-- Run after schema.sql. Idempotent (GRANT is safe to re-run).

grant select, insert, update, delete
    on public.profiles,
         public.status,
         public.schedule_blocks,
         public.whiteboard_items,
         public.signals
    to authenticated, anon;

-- redeem_pairing_code is security definer, but the caller still needs EXECUTE.
grant execute on function public.redeem_pairing_code(text) to authenticated, anon;
