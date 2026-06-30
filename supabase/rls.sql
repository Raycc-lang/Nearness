-- Nearness — Row Level Security (v2, partnerships model)
-- Run after schema.sql. Helpers are security definer to avoid RLS recursion.

-- The caller's partner uuid (null if unpaired).
create or replace function public.my_partner_id()
returns uuid language sql security definer set search_path = public as $$
    select coalesce(
        (select user_b_id from public.partnerships where user_a_id = auth.uid() and user_b_id is not null),
        (select user_a_id from public.partnerships where user_b_id = auth.uid())
    );
$$;

-- Is the caller a member of the given partnership?
create or replace function public.is_partnership_member(p_id uuid)
returns boolean language sql security definer set search_path = public as $$
    select exists (
        select 1 from public.partnerships
        where id = p_id and (user_a_id = auth.uid() or user_b_id = auth.uid())
    );
$$;

-- Is the target user the caller or the caller's partner?
create or replace function public.is_self_or_partner(target uuid)
returns boolean language sql security definer set search_path = public as $$
    select target = auth.uid() or target = public.my_partner_id();
$$;

alter table public.profiles enable row level security;
alter table public.status enable row level security;
alter table public.schedule_blocks enable row level security;
alter table public.partnerships enable row level security;
alter table public.whiteboard_items enable row level security;
alter table public.signals enable row level security;

-- profiles: read self + partner; update self.
drop policy if exists profiles_select on public.profiles;
create policy profiles_select on public.profiles for select to authenticated
    using (public.is_self_or_partner(id));
drop policy if exists profiles_update_self on public.profiles;
create policy profiles_update_self on public.profiles for update to authenticated
    using (id = auth.uid()) with check (id = auth.uid());

-- status: read self + partner; upsert self.
drop policy if exists status_select on public.status;
create policy status_select on public.status for select to authenticated
    using (public.is_self_or_partner(user_id));
drop policy if exists status_upsert_self on public.status;
create policy status_upsert_self on public.status for insert to authenticated
    with check (user_id = auth.uid());
drop policy if exists status_update_self on public.status;
create policy status_update_self on public.status for update to authenticated
    using (user_id = auth.uid()) with check (user_id = auth.uid());

-- schedule_blocks: read self + partner; write/delete self.
drop policy if exists schedule_select on public.schedule_blocks;
create policy schedule_select on public.schedule_blocks for select to authenticated
    using (public.is_self_or_partner(user_id));
drop policy if exists schedule_write_self on public.schedule_blocks;
create policy schedule_write_self on public.schedule_blocks for all to authenticated
    using (user_id = auth.uid()) with check (user_id = auth.uid());

-- partnerships: read own (as a or b); insert pending as user_a; no direct update/delete (RPCs handle).
drop policy if exists partnerships_select on public.partnerships;
create policy partnerships_select on public.partnerships for select to authenticated
    using (user_a_id = auth.uid() or user_b_id = auth.uid());
drop policy if exists partnerships_insert_self on public.partnerships;
create policy partnerships_insert_self on public.partnerships for insert to authenticated
    with check (user_a_id = auth.uid() and user_b_id is null);

-- whiteboard_items: read/write if caller is a member of the item's partnership.
-- Replies (parent_id not null) are additionally scoped: the parent must live in
-- the same partnership and be a top-level post. This mirrors the
-- enforce_reply_depth trigger (the trigger is the primary guarantee; this is
-- defense in depth at the RLS boundary).
drop policy if exists whiteboard_select on public.whiteboard_items;
create policy whiteboard_select on public.whiteboard_items for select to authenticated
    using (public.is_partnership_member(partnership_id));
drop policy if exists whiteboard_insert_self on public.whiteboard_items;
create policy whiteboard_insert_self on public.whiteboard_items for insert to authenticated
    with check (
        public.is_partnership_member(partnership_id)
        and author_id = auth.uid()
        and (
            parent_id is null
            or exists (
                select 1 from public.whiteboard_items parent
                where parent.id = whiteboard_items.parent_id
                  and parent.partnership_id = whiteboard_items.partnership_id
                  and parent.parent_id is null
            )
        )
    );
drop policy if exists whiteboard_update_pair on public.whiteboard_items;
create policy whiteboard_update_pair on public.whiteboard_items for update to authenticated
    using (public.is_partnership_member(partnership_id))
    with check (public.is_partnership_member(partnership_id));

-- signals: receiver reads; sender inserts (receiver must be partner).
drop policy if exists signals_select on public.signals;
create policy signals_select on public.signals for select to authenticated
    using (receiver_id = auth.uid());
drop policy if exists signals_insert_self on public.signals;
create policy signals_insert_self on public.signals for insert to authenticated
    with check (sender_id = auth.uid() and receiver_id = public.my_partner_id());
