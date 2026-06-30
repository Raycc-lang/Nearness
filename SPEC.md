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

**Nearness solves a different problem:** ambient awareness without
surveillance, and shared presence without reply obligation.

The emotional goals are:

1. **Reduce anxiety** — "is she okay?" answered at a glance
2. **Enable better timing** — "is now a good time to call?" answered without asking
3. **Create shared rhythm** — feeling like two lives run in parallel, not apart
4. **Build something together** — a shared space that accumulates meaning over time

---

## 2. Design Philosophy

These principles are non-negotiable and must be reflected in every feature
and every UI decision:

- **No read receipts.** Anywhere. Ever. Not on whiteboard posts, not on
  replies, not on signals.
- **No delivered/seen indicators.** The sender never knows if the receiver
  has opened something.
- **No reply pressure.** Every feature is designed so the receiver can ignore
  it without social cost.
- **Coarse presence only.** Never show "active 2 minutes ago." Show
  "updated this morning." The difference matters emotionally.
- **Pull, not push.** The app is something you check when you want to feel
  close, not something that interrupts you to demand attention.
- **Low friction to update.** If updating takes more than 10 seconds, it
  won't happen. Status is one tap. Signals are one tap.
- **Connection, not monitoring.** Every feature should feel like leaving a
  light on, not filing a report.
- **Gestures, not broadcasts.** Signals are acts directed *at* the other
  person, not status updates *about* yourself.

---

## 3. Target Users

Exactly **two people** in a long-distance relationship. This is not a
general-purpose app. There is no public registration, no user discovery,
no social graph. The app is private by design.

Both users are on **Android**.

---

## 4. Features

### 4.1 Today — Status, Schedule, and Note

The core feature. One unified picture of "how I am today," composed of
three fields that are edited together and displayed together.

**This is one feature, not three.** Status, schedule blocks, and the
optional note collectively answer the question: *what is your day like
right now?* They are displayed as a single card per person on the Today
screen and edited in a single bottom sheet.

#### Activity Status

Each user sets their current state with one tap.

| Emoji | Label | Meaning |
|---|---|---|
| 🌙 | Resting | Sleeping or lying down |
| 💼 | Working | Focused, busy, not available |
| 🌿 | Free | Available, relaxed |
| 🚶 | Out | Away from home |

#### Note

An optional free-text annotation on the current activity (max 80
characters). Examples: "Working from home, free after 6." or "Tired today,
might sleep early."

The note is for context about the current moment — it is not a message.
It disappears when status is next updated.

#### Schedule Blocks

Manual only. No calendar integration.

Each user can add named time blocks to their own day:
- Label (free text, e.g. "Team standup", "Lunch with family", "Busy")
- Start time + end time

Partner sees today's blocks as a simple timeline inside the partner's card.
Purpose: helps the other person know when *not* to call without having to ask.

#### Display Rules

**Coarse presence** (shown on partner's card only):
- `updated_at` < 1 hour ago → "Updated just now"
- Same day, > 1 hour → "Updated this morning / afternoon / evening"
- Yesterday → "Updated yesterday"
- Older → "Last seen a few days ago"

Never show exact timestamps. Never show "active now."

**Update behavior:** Status and note are upserted — one row per user,
updated in place. Schedule blocks are inserted/deleted individually per block.

---

### 4.2 Whiteboard — Shared Project Space

A shared, threaded feed where both users post text, photos, and voice memos.
The emotional register is collaborative: a space you build together over time,
not a one-directional stream of notes left for the other person.

**Key distinction from messaging:** There is no conversation flow, no
typing indicator, no delivery receipt, no urgency. Posts sit until you
look at them. Replies exist because collaboration requires response — not
because messaging requires it.

#### Post Types

- **Text** — up to 500 characters
- **Photo** — from device gallery, with optional caption (max 100 characters)
- **Voice memo** — recorded inside the app, with optional caption (max 100
  characters). Duration displayed; no auto-play.

#### Threading

Posts support one level of replies. A reply is the same structure as a
top-level post (text, photo, or voice). Replies are displayed inline,
collapsed by default if more than three exist (tap to expand).

No read receipts on replies. No reply-to-reply nesting (two levels maximum).

#### Lifecycle

- **Active feed:** Posts less than 5 days old.
- **Auto-archiving:** A scheduled job runs nightly at 03:00 UTC and sets
  `archived_at` on top-level posts (and their replies) older than 5 days.
  Replies inherit the archiving of their parent — a reply cannot outlive
  its parent post in the active feed.
- **Replying to archived posts:** Replies may be posted to an archived
  parent. Such a reply is itself archived on the next nightly run (it
  inherits the parent's archived state and never persists in the active
  feed beyond its parent).
- **Archive browser:** All archived posts are accessible from a separate
  screen, grouped by month, with a local search bar (searches text content
  and captions). Posts can be unarchived individually.
- **No hard delete in v1.** Archive is the only removal mechanism.

#### Freshness

New posts and replies from partner are surfaced by FCM while the app is
backgrounded. When the Whiteboard tab is opened or brought to the foreground,
the app refreshes the active feed once through the Cloudflare Worker proxy.
A background poll runs every 10 minutes while the tab remains open as a
fallback. Pull-to-refresh is always available. Supabase Realtime is
intentionally not used; polling is by design (ISP WebSocket constraint), and
sub-minute freshness is inconsistent with the product's async, low-pressure
philosophy.

---

### 4.3 Signals — Intimate Gestures

Lightweight, one-directional gestures sent *to* the other person. A signal
is an act directed at someone, not a status update about yourself. The
receiver feels a gentle notification and sees a soft banner. No reply button.
No history.

**Preset signals:**

| Emoji | Label |
|---|---|
| 🫂 | Hug |
| 💋 | Kiss |
| ☀️ | Good morning |
| 🌙 | Good night |
| 💭 | Just thinking of you |

**Custom signal:** Free text, max 30 characters. Displayed exactly as
typed. Emoji-friendly.

**Sending:** Triggered from a floating action button (FAB) anchored to the
PartnerCard on the Home screen. Tapping opens a bottom sheet with the signal
picker. Signals are never a browsable destination — they are always an action.

**Display:** A soft ambient banner on the Home screen showing the most recent
signal received in the last 2 hours, phrased as "[Name] sent you a [signal]"
(e.g. "Ray sent you a warm hug"). After 2 hours it disappears automatically.
No relative time is shown. No signal history screen — signals are
intentionally ephemeral.

**What signals are not:** "Miss you" and "Thinking of you" as passive states
belong in the status note field. A signal is something you actively send
someone right now, not something that describes how you currently feel.

---

### 4.4 Push Notifications

Delivered via FCM. Android only.

**Notification channels:**

| Channel | Importance | Used for |
|---|---|---|
| `signals` | HIGH | All signals (preset and custom) |
| `whiteboard` | DEFAULT | New top-level posts and replies |
| `status` | LOW | Partner status updates |

**Notification content:**

- Signal: `[Name] sent you a hug 🫂` (or custom text)
- New whiteboard post: `[Name] added something to the whiteboard`
- Reply: `[Name] replied to a post`
- Status update: `[Name] is now 🌿 Free` (or current activity label)

**No notification** is ever sent for:
- Reading a whiteboard post or reply
- Viewing the Today screen
- Anything the receiver does passively

---

### 4.5 Pairing Flow

One-time setup. Generating a code creates a *pending* `partnerships` row
(`user_a_id` = the generator, `user_b_id` null, a 6-character alphanumeric
code that expires after 7 days). The other user enters this code to link the
two accounts permanently.

After pairing:
- The pending `partnerships` row is completed: `user_b_id` is set to the
  redeeming user
- The pairing code (and its expiry) is cleared from the row
- Codes cannot be reused, and an already-paired user cannot generate or
  redeem a code

Once paired, the relationship is fixed. No re-pairing in v1.

---

## 5. Screens

```
App
├── AuthScreen              Magic link email entry
├ ─ PairingScreen
│   ├── MyCodeTab           Display own code (large, copyable)
│   └── EnterCodeTab        Enter partner's code
└── HomeScreen              Single screen, no tab bar
    ├── SignalBanner        Last received signal (last 24 hrs, ambient)
    ├── PartnerCard         Their activity + note + schedule timeline
    │   └── SignalFAB       Send Signal button, anchored to PartnerCard
    │       └── SignalSheet Bottom sheet: preset signals + custom field
    ├── YourCard            Your activity + note + schedule (tap to edit)
    │   └── EditSheet       Bottom sheet: status picker + note + schedule blocks
    └── WhiteboardEntry     Entry point to the whiteboard (e.g. preview card
                            showing latest post, or a simple "Our Whiteboard →"
                            button); tap navigates to WhiteboardScreen
        └── WhiteboardScreen  (pushed) Full feed + compose bar
            ├── ActiveFeed  Threaded posts, newest first
            │   └── ThreadView  Inline replies, collapsed >3
            ├── ComposeBar  Sticky footer: "+ Add" → PostSheet
            │   └── PostSheet  Bottom sheet: compose (text/photo/voice)
            └── ArchiveLink → ArchiveScreen (pushed) Monthly archive + search
```

**Navigation principles:**
- No tab bar. The home screen is the only persistent screen.
- The Signal button is spatially anchored to the PartnerCard — a gesture
  directed at her, not a generic app action.
- The whiteboard is a pushed screen, entered from the home screen.
- Bottom sheets for all compose and edit flows — no full-screen forms.
- No modal stack deeper than two levels.

---

## 6. Technology Stack

### Android App

| Concern | Choice | Reason |
|---|---|---|
| Language | Kotlin | Native Android, best tooling |
| UI | Jetpack Compose | Modern declarative Android UI |
| Architecture | MVVM | ViewModels + StateFlow; no logic in Composables |
| Navigation | Navigation Compose | Single-activity, type-safe routes |
| HTTP / async | Ktor (via Supabase SDK) | Kotlin-native, coroutine-first |
| Image loading | Coil | Compose-native |
| Permissions | Accompanist | Runtime permission handling |
| Local cache | DataStore Preferences | Persist last-known state for fast launch |
| Push | Firebase Messaging (FCM) | Android-native background notifications |
| ISP-safe proxy | Cloudflare Worker | Proxies all HTTP traffic to Supabase |

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
`io.github.jan-tennert.supabase:supabase-kt` (BOM 2.6.1)
Plugins: `postgrest-kt`, `auth-kt`, `storage-kt`

---

## 7. Database Schema (Summary)

```
profiles          — extends auth.users; display_name, avatar_path, fcm_token
partnerships      — canonical partnership row (user_a_id, user_b_id,
                    pairing_code); single source of truth for the pair
                    relationship and all pairing state
status            — one row per user, upserted; activity + note + updated_at
schedule_blocks   — time blocks per user (label, starts_at, ends_at)
whiteboard_items  — posts and replies (type, content, parent_id,
                    partnership_id FK, archived_at)
signals           — ephemeral one-tap gestures (sender, receiver, type,
                    custom_text, sent_at)
```

`whiteboard_items.partnership_id` is a foreign key to `partnerships(id)`.
No text-based pair key is used.

Row Level Security is enabled on all tables. Users can read their own rows
and their partner's rows only.

---

## 8. Edge Functions

| Function | Trigger | Purpose |
|---|---|---|
| `send-notification` | DB Webhook: INSERT on `signals`, `whiteboard_items`; UPDATE on `status` | Looks up receiver FCM token; calls FCM HTTP v1 API |
| `auto-archive` | Supabase Cron, daily 03:00 UTC | Sets `archived_at` on posts (and their replies) older than 5 days |

The Android app does not connect to Supabase directly. All `/auth/v1`,
`/rest/v1`, and `/storage/v1` traffic is proxied through a Cloudflare
Worker. Server-side Edge Functions use the direct Supabase project URL.

---

## 9. What This App Deliberately Does Not Do

- No text messaging (use WhatsApp / Discord)
- No real-time location sharing
- No read receipts or seen indicators (anywhere, ever)
- No app usage monitoring
- No public profiles or user discovery
- No group support (exactly two users)
- No web app (Android only, v1)
- No iOS support (v1)
- No in-app notification history for signals (ephemeral by design)

---

## 10. Open Source Notes

This project is open source. Any couple can fork it, connect their own
Supabase and Firebase projects, and run a private instance.

Setup requires:
1. A Supabase project (free tier sufficient)
2. A Firebase project with FCM enabled
3. A Cloudflare Worker for the Android app's Supabase proxy
4. Building and sideloading the APK, or publishing privately to Play Store

There is no shared backend. Every deployment is a private instance.
