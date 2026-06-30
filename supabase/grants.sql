-- Nearness — table privileges (v2)
-- rls.sql controls row-level access; these GRANTs give the authenticated/anon
-- roles the base table + function privileges PostgREST needs to attempt a query.
-- Without them every request fails with "permission denied for table <name>".
-- Run after rls.sql (functions are defined there). Idempotent.

grant select, insert, update, delete
    on public.profiles,
         public.status,
         public.schedule_blocks,
         public.partnerships,
         public.whiteboard_items,
         public.signals
    to authenticated, anon;

grant execute on function
    public.create_pending_partnership(),
    public.redeem_pairing_code(text),
    public.my_partner_id(),
    public.is_partnership_member(uuid),
    public.is_self_or_partner(uuid)
    to authenticated, anon;

-- archive_old_whiteboard_items() is SECURITY DEFINER and must NOT be callable by
-- app clients (it would let any user archive the pair's feed). Only the
-- auto-archive Edge Function (service_role) and the cron owner run it.
revoke execute on function public.archive_old_whiteboard_items() from public;
grant execute on function public.archive_old_whiteboard_items() to service_role;
