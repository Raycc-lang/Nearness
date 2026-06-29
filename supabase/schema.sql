-- Nearness — database schema
-- Run this in the Supabase SQL editor (or via `supabase db push`).
-- RLS policies live in rls.sql; run that afterwards.

-- ---------------------------------------------------------------------------
-- Extensions
-- ---------------------------------------------------------------------------
create extension if not exists "pgcrypto"; -- gen_random_uuid()

-- ---------------------------------------------------------------------------
-- Enums
-- ---------------------------------------------------------------------------
do $$ begin
    create type activity_type as enum ('resting', 'working', 'free', 'out');
exception when duplicate_object then null; end $$;

do $$ begin
    create type whiteboard_item_type as enum ('text', 'photo', 'voice');
exception when duplicate_object then null; end $$;

do $$ begin
    create type signal_type as enum ('thinking_of_you', 'good_morning', 'good_night', 'miss_you', 'custom');
exception when duplicate_object then null; end $$;

-- ---------------------------------------------------------------------------
-- profiles — extends auth.users
-- ---------------------------------------------------------------------------
create table if not exists public.profiles (
    id            uuid primary key references auth.users (id) on delete cascade,
    display_name  text,
    partner_id    uuid references public.profiles (id) on delete set null,
    fcm_token     text,
    pairing_code  text unique,
    pairing_code_expires_at timestamptz,
    created_at    timestamptz not null default now()
);

-- Auto-create a profile row when a new auth user signs up.
create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer set search_path = public
as $$
begin
    insert into public.profiles (id, display_name)
    values (new.id, coalesce(new.raw_user_meta_data->>'display_name', split_part(new.email, '@', 1)));
    return new;
end;
$$;

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created
    after insert on auth.users
    for each row execute function public.handle_new_user();

-- ---------------------------------------------------------------------------
-- status — one row per user, upserted in place
-- ---------------------------------------------------------------------------
create table if not exists public.status (
    user_id     uuid primary key references public.profiles (id) on delete cascade,
    activity    activity_type not null default 'free',
    note        text check (char_length(note) <= 80),
    updated_at  timestamptz not null default now()
);

-- ---------------------------------------------------------------------------
-- schedule_blocks — manual time blocks per user
-- ---------------------------------------------------------------------------
create table if not exists public.schedule_blocks (
    id          uuid primary key default gen_random_uuid(),
    user_id     uuid not null references public.profiles (id) on delete cascade,
    label       text not null,
    starts_at   timestamptz not null,
    ends_at     timestamptz not null,
    created_at  timestamptz not null default now(),
    constraint ends_after_start check (ends_at > starts_at)
);
create index if not exists schedule_blocks_user_starts_idx
    on public.schedule_blocks (user_id, starts_at);

-- ---------------------------------------------------------------------------
-- whiteboard_items — shared feed, scoped by partner_pair_key
-- ---------------------------------------------------------------------------
create table if not exists public.whiteboard_items (
    id                uuid primary key default gen_random_uuid(),
    partner_pair_key  text not null,
    author_id         uuid not null references public.profiles (id) on delete cascade,
    type              whiteboard_item_type not null,
    content           text,           -- text body, or storage path for photo/voice
    caption           text check (char_length(caption) <= 100),
    archived_at       timestamptz,    -- null = active, non-null = archived
    created_at        timestamptz not null default now()
);
create index if not exists whiteboard_pair_created_idx
    on public.whiteboard_items (partner_pair_key, created_at desc);
create index if not exists whiteboard_pair_archived_idx
    on public.whiteboard_items (partner_pair_key, archived_at);

-- ---------------------------------------------------------------------------
-- signals — one-tap, ephemeral
-- ---------------------------------------------------------------------------
create table if not exists public.signals (
    id           uuid primary key default gen_random_uuid(),
    sender_id    uuid not null references public.profiles (id) on delete cascade,
    receiver_id  uuid not null references public.profiles (id) on delete cascade,
    type         signal_type not null,
    custom_text  text check (char_length(custom_text) <= 30),
    sent_at      timestamptz not null default now()
);
create index if not exists signals_receiver_sent_idx
    on public.signals (receiver_id, sent_at desc);

-- ---------------------------------------------------------------------------
-- Pairing helper — atomically links two accounts by code
-- ---------------------------------------------------------------------------
create or replace function public.redeem_pairing_code(code text)
returns void
language plpgsql
security definer set search_path = public
as $$
declare
    owner_id uuid;
begin
    select id into owner_id
    from public.profiles
    where pairing_code = upper(code)
      and pairing_code_expires_at > now()
      and partner_id is null
    for update;

    if owner_id is null then
        raise exception 'Invalid or expired pairing code';
    end if;

    if owner_id = auth.uid() then
        raise exception 'Cannot pair with yourself';
    end if;

    update public.profiles
        set partner_id = auth.uid(), pairing_code = null, pairing_code_expires_at = null
        where id = owner_id;

    update public.profiles
        set partner_id = owner_id, pairing_code = null, pairing_code_expires_at = null
        where id = auth.uid();
end;
$$;
