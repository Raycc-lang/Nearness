-- Nearness — Row Level Security policies
-- Run after schema.sql. Each user can read their own rows and their
-- partner's rows, and write only their own.

-- Helper: is the given user_id either me or my partner?
create or replace function public.is_self_or_partner(target uuid)
returns boolean
language sql stable security definer set search_path = public
as $$
    select target = auth.uid()
        or target = (select partner_id from public.profiles where id = auth.uid());
$$;

-- ---------------------------------------------------------------------------
-- profiles
-- ---------------------------------------------------------------------------
alter table public.profiles enable row level security;

drop policy if exists profiles_select on public.profiles;
create policy profiles_select on public.profiles
    for select using (public.is_self_or_partner(id) or pairing_code is not null);

drop policy if exists profiles_update_self on public.profiles;
create policy profiles_update_self on public.profiles
    for update using (id = auth.uid()) with check (id = auth.uid());

-- ---------------------------------------------------------------------------
-- status
-- ---------------------------------------------------------------------------
alter table public.status enable row level security;

drop policy if exists status_select on public.status;
create policy status_select on public.status
    for select using (public.is_self_or_partner(user_id));

drop policy if exists status_upsert_self on public.status;
create policy status_upsert_self on public.status
    for insert with check (user_id = auth.uid());

drop policy if exists status_update_self on public.status;
create policy status_update_self on public.status
    for update using (user_id = auth.uid()) with check (user_id = auth.uid());

-- ---------------------------------------------------------------------------
-- schedule_blocks
-- ---------------------------------------------------------------------------
alter table public.schedule_blocks enable row level security;

drop policy if exists schedule_select on public.schedule_blocks;
create policy schedule_select on public.schedule_blocks
    for select using (public.is_self_or_partner(user_id));

drop policy if exists schedule_write_self on public.schedule_blocks;
create policy schedule_write_self on public.schedule_blocks
    for all using (user_id = auth.uid()) with check (user_id = auth.uid());

-- ---------------------------------------------------------------------------
-- whiteboard_items — scoped by the author being self-or-partner
-- ---------------------------------------------------------------------------
alter table public.whiteboard_items enable row level security;

drop policy if exists whiteboard_select on public.whiteboard_items;
create policy whiteboard_select on public.whiteboard_items
    for select using (public.is_self_or_partner(author_id));

drop policy if exists whiteboard_insert_self on public.whiteboard_items;
create policy whiteboard_insert_self on public.whiteboard_items
    for insert with check (author_id = auth.uid());

-- Either partner may archive/unarchive items in their shared feed.
drop policy if exists whiteboard_update_pair on public.whiteboard_items;
create policy whiteboard_update_pair on public.whiteboard_items
    for update using (public.is_self_or_partner(author_id))
    with check (public.is_self_or_partner(author_id));

drop policy if exists whiteboard_delete_self on public.whiteboard_items;
create policy whiteboard_delete_self on public.whiteboard_items
    for delete using (author_id = auth.uid());

-- ---------------------------------------------------------------------------
-- signals — sender writes, receiver and sender can read
-- ---------------------------------------------------------------------------
alter table public.signals enable row level security;

drop policy if exists signals_select on public.signals;
create policy signals_select on public.signals
    for select using (sender_id = auth.uid() or receiver_id = auth.uid());

drop policy if exists signals_insert_self on public.signals;
create policy signals_insert_self on public.signals
    for insert with check (sender_id = auth.uid());
