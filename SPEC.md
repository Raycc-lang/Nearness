# Nearness — Product Specification

> The authoritative product specification. Read this before making any
> feature decisions. `AGENTS.md` defines *how* to build; this defines *what*
> and *why*.

---

## 1. The Problem

Long-distance relationships create a specific kind of low-grade anxiety:
not knowing if the other person is okay, not knowing if now is a good time
to call, not feeling the ordinary rhythm of a shared life.

Existing tools solve this poorly:

- **Messaging apps** (WhatsApp, Discord) create reply obligation and urgency
  signals — seen receipts, typing indicators, delivery timestamps — that turn
  communication into performance.
- **Location sharing** (Find My, Life360) answers "where" but not "how" —
  a GPS dot tells you nothing about whether she's sleeping, stressed, or free.
- **Social media** is public and noisy, not intimate and quiet.

**Nearness solves a different problem:** ambient awareness without surveillance,
and asynchronous intimacy without reply obligation.

The emotional goals are:

1. **Reduce anxiety** — "is she okay?" answered at a glance
2. **Enable better timing** — "is now a good time to call?" answered without asking
3. **Create shared rhythm** — feeling like two lives run in parallel, not apart

---

## 2. Design Philosophy

These principles are non-negotiable and must be reflected in every feature
and every UI decision:

- **No read receipts.** Anywhere. Ever. Not on messages, not on whiteboard
  items, not on signals.
- **No delivered/seen indicators.** The sender never knows if the receiver
  has opened something.
- **No reply pressure.** Every feature is designed so the receiver can ignore
  it without social cost.
- **Coarse presence only.** Never show "active 2 minutes ago." Show
  "active this morning." The difference matters emotionally.
- **Pull, not push.** The app is something you check when you want to feel
  close, not something that interrupts you to demand attention.
- **Low friction to update.** If updating takes more than 10 seconds,
  it won't happen. Every input should be one or two taps.
- **Connection, not monitoring.** Every feature should feel like leaving
  a light on, not filing a report.

---

## 3. Target Users

Exactly **two people** in a long-distance relationship. This is not a
general-purpose app. There is no public registration, no user discovery,
no social graph. The app is private by design.

Both users are on **Android**.

---

## 4. Features

### 4.1 Activity Status

The core feature. Each user sets their current state with one tap.

**Four states:**
| Emoji | Label | Meaning |
|---|---|---|
| 🌙 | Resting | Sleeping or lying down |
| 💼 | Working | Focused, busy, not available |
| 🌿 | Free | Available, relaxed |
| 🚶 | Out | Away from home |

**Optional annotation:** A single free-text line (max 80 characters) for
context. Example: "Working from home, free after 6."

**Update behavior:** Upsert — one row per user, updated in place. The
`updated_at` timestamp drives the presence display.

**Coarse presence display** (shown on partner's card only):
- `updated_at` < 1 hour ago → "Updated just now"
- Same day, > 1 hour ago → "Updated this morning / this afternoon / this evening"
- Yesterday → "Updated yesterday"
- Older → "Last seen a few days ago"

Never show exact timestamps. Never show "active now."

---

### 4.2 Schedule Blocks

Manual only. No calendar integration.

Each user can add time blocks to their own day:
- Label (free text, e.g. "Team standup", "Busy", "Lunch with family")
- Start time + end time

Partner sees today's blocks displayed as a simple timeline below the
status cards on the Today screen.

Purpose: helps the other person know when *not* to call without having
to ask.

---

### 4.3 Shared Whiteboard

A shared feed of asynchronous notes — text, photos, and voice memos —
that sits between both users. Both can post to it.

**The key feeling:** finding a post-it note left for you, not opening a chat.

**Rules:**
- No read receipts
- No delivered indicators
- No reply button (replies are just new posts to the same feed)
- No urgency signals of any kind
- Items are visible to both users in the pair

**Item types:**
- **Text** — up to 500 characters
- **Photo** — any photo from the device gallery, with optional caption
  (max 100 characters)
- **Voice memo** — recorded inside the app, with optional caption
  (max 100 characters)

**Auto-archiving:** Items older than 5 days are automatically archived
(not deleted). A scheduled Edge Function runs nightly.

**Archive browser:** All archived items are accessible in a separate
screen, grouped by month, with a local search bar. Items can be
unarchived (moved back to the active feed).

**Freshness:** New items from partner are surfaced by FCM while the app is
backgrounded. When the Whiteboard tab is open, the app polls the active feed
through the Cloudflare Worker proxy. Supabase Realtime is intentionally not
used; polling is by design.

---

### 4.4 One-Tap Signals

Lightweight, one-directional emotional signals. No reply button.
The receiver feels a gentle notification and sees a soft banner.
That's it.

**Preset signals:**
| Emoji | Label |
|---|---|
| 🤍 | Thinking of you |
| ☀️ | Good morning |
| 🌙 | Good night |
| 👀 | Miss you |

**Custom signal:** Free text, max 30 characters. Displayed exactly
as typed. Emoji-friendly.

**Display on Today screen:** A soft banner showing the most recent
signal received in the last 24 hours. After 24 hours it disappears.
No history screen — signals are intentionally ephemeral.

---

### 4.5 Push Notifications

Delivered via FCM (Firebase Cloud Messaging). Android only.

**Notification channels:**
- `signals` — HIGH importance (signals and one-tap messages)
- `whiteboard` — DEFAULT importance (new whiteboard items)
- `status` — LOW importance (partner status updates)

**Notification content:**
- Signal: sender name + signal text (or custom text)
- Whiteboard item: "[Name] left something for you" + type hint
- Status update: "[Name] updated their status" + activity label

**No notification** is ever sent for:
- Reading a whiteboard item
- Viewing the Today screen
- Anything the receiver does

---

### 4.6 Pairing Flow

One-time setup. Each new user generates a 6-character alphanumeric
pairing code (expires after 7 days). The other user enters this code
to link the two accounts permanently.

After pairing:
- `partner_id` is set on both profile rows
- Pairing code is cleared from both rows
- Codes cannot be reused

Once paired, the relationship is fixed. No re-pairing in v1.

---

## 5. Screens

```
App
├── AuthScreen          Magic link email login
├── PairingScreen       Share code / Enter code (tabs)
└── MainScreen          Bottom navigation
    ├── TodayTab        Status cards + schedule + signals banner
    ├── WhiteboardTab   Feed + compose bar
    │   └── ArchiveScreen (pushed)
    └── SignalsTab      Send signal (bottom sheet trigger)
```

---

## 6. Technology Stack

### Android App
| Concern | Choice | Reason |
|---|---|---|
| Language | Kotlin | Native Android, best tooling, best AI agent support |
| UI | Jetpack Compose | Modern declarative Android UI |
| Architecture | MVVM | ViewModels + StateFlow; no logic in Composables |
| Navigation | Navigation Compose | Single-activity, type-safe routes |
| HTTP / async | Ktor (via Supabase SDK) | Kotlin-native, coroutine-first |
| Image loading | Coil | Compose-native image loading |
| Permissions | Accompanist | Runtime permission handling in Compose |
| Local cache | DataStore Preferences | Persist last-known state for fast launch |
| Push | Firebase Messaging (FCM) | Android-native, required for background notifications |
| ISP-safe proxy | Cloudflare Worker | Proxies Android REST/Auth/Storage HTTP traffic to Supabase |

### Backend
| Concern | Choice |
|---|---|
| Database | Supabase Postgres |
| Auth | Supabase Auth (magic link / JWT) |
| File storage | Supabase Storage (private bucket) |
| Custom logic | Supabase Edge Functions (Deno / TypeScript) |
| Scheduled jobs | Supabase Cron (pg_cron) |
| Notification delivery | FCM HTTP v1 API (called from Edge Function) |

### Key SDK
- `io.github.jan-tennert.supabase:supabase-kt` (BOM 2.6.1)
  Plugins used: `postgrest-kt`, `auth-kt`, `storage-kt`

---

## 7. Database Schema (Summary)

```
profiles          — extends auth.users; holds partner_id, fcm_token, pairing_code
status            — one row per user, upserted; holds activity + note + updated_at
schedule_blocks   — time blocks per user per day (label, starts_at, ends_at)
whiteboard_items  — feed items (type, content, caption, archived_at, partner_pair_key)
signals           — one-tap signals (sender, receiver, type, custom_text, sent_at)
```

`partner_pair_key` is a stable string derived by sorting both user UUIDs
alphabetically and joining with `_`. Used to scope whiteboard queries without
requiring a join.

Row Level Security is enabled on all tables. Users can read their own rows
and their partner's rows. Users cannot read or write other users' data.

---

## 8. Edge Functions

| Function | Trigger | Purpose |
|---|---|---|
| `send-notification` | DB Webhook on INSERT to `signals`, `whiteboard_items`; UPDATE to `status` | Looks up receiver FCM token, calls FCM HTTP v1 API |
| `auto-archive` | Supabase Cron, daily 03:00 UTC | Sets `archived_at` on whiteboard items older than 5 days |

The Android app does not connect to Supabase directly. It uses a Cloudflare
Worker as an HTTP proxy for `/auth/v1`, `/rest/v1`, and `/storage/v1`.
Server-side Edge Functions still use the direct Supabase project URL.

---

## 9. What This App Deliberately Does Not Do

- No text messaging (use WhatsApp / Discord / any existing platform)
- No real-time location sharing
- No read receipts or seen indicators (anywhere, ever)
- No app usage monitoring (no UsageStats API)
- No public profiles or user discovery
- No group support (exactly two users)
- No web app (Android only, v1)
- No iOS support (v1)

---

## 10. Open Source Notes

This project is open source. The intent is that any couple could fork it,
connect their own Supabase project and Firebase project, and run their own
private instance.

Setup requires:
1. A Supabase project (free tier sufficient)
2. A Firebase project with FCM enabled
3. A Cloudflare Worker route for the Android app's Supabase proxy
4. Building and sideloading the APK (or publishing to Play Store privately)

There is no shared backend. Every deployment is a private instance.
