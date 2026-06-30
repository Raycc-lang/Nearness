-- Nearness — database schema (v2)
-- partnerships replaces partner_id on profiles; whiteboard_items splits content
-- into text_body/storage_path with a type check and supports one level of
-- replies via parent_id; profiles gains avatar_path.
-- This is a RESET: drops the app tables/types and recreates them. auth.users is
-- untouched; existing auth users get a profiles row backfilled below.
-- Run order: schema.sql → rls.sql → grants.sql → storage.sql.

create extension if not exists "pgcrypto";

-- Drop app tables (dependents first), then enum types. auth.users is not touched.
drop table if exists public.signals cascade;
drop table if exists public.whiteboard_items cascade;
drop table if exists public.schedule_blocks cascade;
drop table if exists public.status cascade;
drop table if exists public.partnerships cascade;
drop table if exists public.profiles cascade;
drop type if exists signal_type;
drop type if exists whiteboard_item_type;
drop type if exists activity_type;

-- Enums (recreated from scratch after the drops above).
create type activity_type as enum ('resting','working','free','out');
create type whiteboard_item_type as enum ('text','photo','voice');
create type signal_type as enum ('hug','kiss','good_morning','good_night','thinking_of_you','custom');

-- profiles — extends auth.users. Pairing state lives in partnerships now.
create table public.profiles (
    id           uuid primary key references auth.users(id) on delete cascade,
    display_name text,
    avatar_path  text,
    fcm_token    text,
    created_at   timestamptz not null default now()
);

-- Auto-create a profile row on signup.
create or replace function public.handle_new_user()
returns trigger language plpgsql security definer set search_path = public as $$
begin
    insert into public.profiles (id, display_name)
    values (new.id, coalesce(new.raw_user_meta_data->>'display_name', split_part(new.email, '@', 1)));
    return new;
end; $$;
drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created after insert on auth.users
    for each row execute function public.handle_new_user();

-- Backfill profiles for auth.users that pre-date this schema (e.g. after reset).
insert into public.profiles (id, display_name)
    select id, split_part(email, '@', 1) from auth.users au
    where not exists (select 1 from public.profiles p where p.id = au.id);

-- status — one row per user, upserted in place.
create table public.status (
    user_id    uuid primary key references public.profiles(id) on delete cascade,
    activity   activity_type not null default 'free',
    note       text check (char_length(note) <= 80),
    updated_at timestamptz not null default now()
);

-- schedule_blocks — manual time blocks per user.
create table public.schedule_blocks (
    id         uuid primary key default gen_random_uuid(),
    user_id    uuid not null references public.profiles(id) on delete cascade,
    label      text not null,
    starts_at  timestamptz not null,
    ends_at    timestamptz not null,
    created_at timestamptz not null default now(),
    constraint ends_after_start check (ends_at > starts_at)
);
create index if not exists schedule_blocks_user_starts_idx on public.schedule_blocks(user_id, starts_at);

-- partnerships — one row per pair. pending = user_b null + code set; complete = user_b set + code null.
create table public.partnerships (
    id                      uuid primary key default gen_random_uuid(),
    user_a_id               uuid not null references public.profiles(id) on delete cascade,
    user_b_id               uuid references public.profiles(id) on delete cascade,
    pairing_code            text unique,
    pairing_code_expires_at timestamptz,
    created_at              timestamptz not null default now(),
    constraint no_self_pair check (user_a_id is distinct from user_b_id),
    constraint pending_or_complete check (
        (user_b_id is null  and pairing_code is not null and pairing_code_expires_at is not null)
        or (user_b_id is not null and pairing_code is null and pairing_code_expires_at is null)
    )
);
create index if not exists partnerships_user_a_idx on public.partnerships(user_a_id);
create index if not exists partnerships_user_b_idx on public.partnerships(user_b_id);
create index if not exists partnerships_code_idx on public.partnerships(pairing_code);

-- whiteboard_items — scoped by partnership; content split by type.
-- parent_id null = top-level post; non-null = reply to a top-level post.
-- Only one level of replies is allowed (enforced by enforce_reply_depth below).
create table public.whiteboard_items (
    id             uuid primary key default gen_random_uuid(),
    partnership_id uuid not null references public.partnerships(id) on delete cascade,
    author_id      uuid not null references public.profiles(id) on delete cascade,
    parent_id      uuid references public.whiteboard_items(id) on delete cascade,
    type           whiteboard_item_type not null,
    text_body      text check (char_length(text_body) <= 500),
    storage_path   text,
    caption        text check (char_length(caption) <= 100),
    archived_at    timestamptz,
    created_at     timestamptz not null default now(),
    constraint content_matches_type check (
        (type = 'text'  and text_body is not null and storage_path is null) or
        (type = 'photo' and storage_path is not null and text_body is null) or
        (type = 'voice' and storage_path is not null and text_body is null)
    )
);
create index if not exists whiteboard_partnership_created_idx on public.whiteboard_items(partnership_id, created_at desc);
create index if not exists whiteboard_partnership_archived_idx on public.whiteboard_items(partnership_id, archived_at);
create index if not exists whiteboard_parent_idx on public.whiteboard_items(parent_id);

-- Enforce: a reply's parent must exist, be top-level (no reply-to-reply, max two
-- levels), and belong to the same partnership as the reply.
create or replace function public.enforce_reply_depth()
returns trigger language plpgsql set search_path = public as $$
declare
    parent_parent_id   uuid;
    parent_partnership uuid;
begin
    if new.parent_id is null then
        return new;
    end if;
    select parent_id, partnership_id into parent_parent_id, parent_partnership
        from public.whiteboard_items where id = new.parent_id;
    if not found then
        raise exception 'Parent post does not exist';
    end if;
    if parent_parent_id is not null then
        raise exception 'Replies cannot be nested more than one level deep';
    end if;
    if parent_partnership is distinct from new.partnership_id then
        raise exception 'Reply must belong to the same partnership as its parent';
    end if;
    return new;
end; $$;
drop trigger if exists enforce_reply_depth_trg on public.whiteboard_items;
create trigger enforce_reply_depth_trg
    before insert or update of parent_id, partnership_id on public.whiteboard_items
    for each row execute function public.enforce_reply_depth();

-- Archive helper — archives top-level posts older than 5 days, then cascades
-- the archive to their replies (a reply never outlives its parent in the active
-- feed). Returns the number of rows archived. Used by cron.sql and the
-- auto-archive Edge Function so the logic lives in one place.
create or replace function public.archive_old_whiteboard_items()
returns integer language plpgsql security definer set search_path = public as $$
declare
    parents integer;
    replies integer;
begin
    update public.whiteboard_items
        set archived_at = now()
        where archived_at is null
          and parent_id is null
          and created_at < now() - interval '5 days';
    get diagnostics parents = row_count;

    update public.whiteboard_items r
        set archived_at = now()
        where r.archived_at is null
          and r.parent_id is not null
          and exists (
              select 1 from public.whiteboard_items p
              where p.id = r.parent_id and p.archived_at is not null
          );
    get diagnostics replies = row_count;

    return parents + replies;
end; $$;

-- signals — one-tap, ephemeral.
create table public.signals (
    id          uuid primary key default gen_random_uuid(),
    sender_id   uuid not null references public.profiles(id) on delete cascade,
    receiver_id uuid not null references public.profiles(id) on delete cascade,
    type        signal_type not null,
    custom_text text check (char_length(custom_text) <= 30),
    sent_at     timestamptz not null default now()
);
create index if not exists signals_receiver_sent_idx on public.signals(receiver_id, sent_at desc);

-- Pairing RPCs (security definer; bypass RLS).
create or replace function public.create_pending_partnership()
returns text language plpgsql security definer set search_path = public as $$
declare
    caller uuid := auth.uid();
    code   text;
    chars  text := 'ABCDEFGHJKMNPQRSTUVWXYZ23456789';
begin
    if caller is null then raise exception 'Not authenticated'; end if;
    if exists (select 1 from public.partnerships
               where (user_a_id = caller and user_b_id is not null)
                  or user_b_id = caller) then
        raise exception 'You are already paired';
    end if;
    delete from public.partnerships where user_a_id = caller and user_b_id is null;
    code := '';
    for i in 1..6 loop
        code := code || substr(chars, 1 + floor(random() * char_length(chars))::int, 1);
    end loop;
    insert into public.partnerships (user_a_id, pairing_code, pairing_code_expires_at)
        values (caller, code, now() + interval '7 days');
    return code;
end; $$;

create or replace function public.redeem_pairing_code(code text)
returns void language plpgsql security definer set search_path = public as $$
declare
    p  public.partnerships%rowtype;
    me uuid := auth.uid();
begin
    if me is null then raise exception 'Not authenticated'; end if;
    select * into p from public.partnerships
        where pairing_code = upper(code)
          and pairing_code_expires_at > now()
          and user_b_id is null
        for update;
    if not found then
        raise exception 'Invalid or expired pairing code';
    end if;
    if p.user_a_id = me then
        raise exception 'Cannot pair with yourself';
    end if;
    if exists (select 1 from public.partnerships where user_a_id = me and user_b_id is not null)
       or exists (select 1 from public.partnerships where user_b_id = me) then
        raise exception 'You are already paired';
    end if;
    -- Drop any pending row the redeemer generated earlier so it can't be
    -- redeemed by someone else and leave them in two partnerships.
    delete from public.partnerships where user_a_id = me and user_b_id is null;
    update public.partnerships
        set user_b_id = me, pairing_code = null, pairing_code_expires_at = null
        where id = p.id;
end; $$;
