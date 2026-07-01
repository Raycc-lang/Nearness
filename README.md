# Nearness

> Ambient presence for two people who are far apart.

Nearness is a private Android app for couples in long-distance relationships.
It replaces the low-grade anxiety of not knowing what your partner is doing
with a quiet, ambient picture of their day — without turning into surveillance,
and without the reply pressure of a messaging app.

---

## The Problem It Solves

Messaging apps are built around urgency. Read receipts, typing indicators,
and delivery timestamps turn every message into a small performance. Location
sharing answers "where" but not "how." Social media is public and noisy.

Nearness is none of those things. It is closer to:

- Glancing at a shared calendar on the fridge
- Finding a post-it note left on the kitchen table
- Knowing the light is on in her room

The goal is connection that asks nothing back.

---

## Features

### Activity Status
Set your current state with one tap: 🌙 Resting · 💼 Working · 🌿 Free · 🚶 Out.
Add an optional one-line note ("Free after 6"). Your partner sees it on their
Today screen. No exact timestamps — only coarse presence ("updated this morning").

### Schedule Blocks
Add time blocks to your day (e.g. "Team meeting 2–4pm"). Your partner sees
today's blocks as a simple timeline. Helps them know when not to call
without either of you having to say it.

### Shared Whiteboard
A quiet, shared space of text notes, photos, and voice memos that both of you
build together over time. Posts support one level of replies so you can
collaborate — but there are no read receipts, no delivered indicators, and no
urgency. Posts older than 5 days are auto-archived (still findable, just out of
the main view). The feeling: a shared corkboard, not a chat.

### One-Tap Signals
Send a 🫂 hug, a 💋 kiss, a ☀️ good morning, a 🌙 good night, a 💭, or a custom
word — a gesture directed at your partner, sent from a button on their card.
They feel a gentle notification and see a soft banner on their Today screen.
No reply button. Ephemeral — disappears after 24 hours.

### No Surveillance Features
- No real-time location
- No read receipts (anywhere, ever)
- No "active now" indicators
- No app usage monitoring

---

## Design Principles

Every feature in Nearness is evaluated against three questions:

1. Does this reduce anxiety, or does it create new anxiety?
2. Does this feel like connection, or does it feel like monitoring?
3. Does this require effort from the receiver, or does it ask nothing back?

If the answer to any of these is wrong, the feature doesn't ship.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose |
| Architecture | MVVM (ViewModel + StateFlow) |
| Database | Supabase Postgres |
| Auth | Supabase Auth (magic link) |
| File storage | Supabase Storage |
| Backend logic | Supabase Edge Functions (Deno) |
| Push notifications | Firebase Cloud Messaging (FCM) |
| Android network path | Cloudflare Worker proxy + polling + FCM |

The Android app talks to Supabase REST/Auth/Storage through a Cloudflare
Worker proxy. Supabase Edge Functions still run server-side and send FCM
notifications from database webhooks.

---

## Self-Hosting

Nearness is designed to be self-hosted. There is no shared backend —
every couple runs their own private instance.

### Prerequisites

- A [Supabase](https://supabase.com) account (free tier is sufficient)
- A [Firebase](https://console.firebase.google.com) project with FCM enabled
- A [Cloudflare](https://dash.cloudflare.com) account for the Worker proxy
- Android Studio (to build and install the APK)

### Setup

**1. Supabase**

Create a new Supabase project. In the SQL editor, run these files in order:

1. `supabase/schema.sql` — tables, enums, triggers, pairing + archive RPCs
2. `supabase/rls.sql` — RLS helpers + Row Level Security policies
3. `supabase/grants.sql` — table + function privileges (run after rls.sql)
4. `supabase/storage.sql` — private buckets (whiteboard-media, avatars) + policies
5. `supabase/cron.sql` — nightly auto-archive job (needs the `pg_cron` extension)

Then deploy the Edge Functions:

```bash
supabase functions deploy send-notification
supabase functions deploy auto-archive
```

Set the required secrets in Supabase Dashboard → Edge Functions → Secrets
(notifications use the FCM HTTP v1 API, which needs a service account):

```
SUPABASE_URL=https://yourproject.supabase.co
SUPABASE_SERVICE_ROLE_KEY=your_service_role_key
FCM_PROJECT_ID=your_firebase_project_id
FCM_SERVICE_ACCOUNT={...}   # the full service-account JSON, as one line
```

Set up the database webhooks (Dashboard → Database → Webhooks), all pointing
at the `send-notification` function:
- `signals` INSERT → `send-notification`
- `whiteboard_items` INSERT → `send-notification`
- `status` UPDATE → `send-notification`

The `cron.sql` job archives items nightly even without the Edge Function;
the `auto-archive` function is provided for HTTP-triggered scheduling.

**2. Cloudflare Worker**

Deploy the HTTP proxy in `cloudflare/worker`. It forwards only Supabase
HTTP API paths used by the Android app: `/auth/v1`, `/rest/v1`, and
`/storage/v1`.

```bash
cd cloudflare/worker
cp wrangler.toml.example wrangler.toml
# Edit SUPABASE_URL to your direct Supabase project URL.
npx wrangler deploy
```

Do not put service-role keys or Firebase credentials in the Worker. The
Android app still uses the public Supabase anon key from `local.properties`.

**3. Firebase**

Create an Android app in your Firebase project with your chosen package name.
Download `google-services.json` and place it in the `app/` directory.

**4. Android App**

Copy `local.properties.example` to `local.properties` and fill in:

```
SUPABASE_URL=https://nearness-supabase-proxy.your-subdomain.workers.dev
SUPABASE_ANON_KEY=your_anon_key
```

`SUPABASE_URL` must be the Cloudflare Worker URL for the Android app. The
direct Supabase URL is only used in Supabase Edge Function secrets and in the
Worker's `SUPABASE_URL` variable.

Build and install:

```bash
./gradlew installDebug
```

Or open the project in Android Studio and run it.

**5. Pairing**

Both users install the app and sign in with their email (magic link).
One person shares their 6-character pairing code; the other enters it.
That's it — the accounts are linked permanently.

---

## Project Structure

```
nearness/
├── app/
│   └── src/main/
│       ├── java/com/yourname/nearness/
│       │   ├── data/           Repository layer, Supabase calls
│       │   ├── domain/         Data models, presence logic
│       │   ├── ui/
│       │   │   ├── auth/       Magic-link login
│       │   │   ├── pairing/    Share / enter pairing code
│       │   │   ├── today/      Today tab (status + schedule + signal banner)
│       │   │   ├── whiteboard/ Whiteboard tab + compose bar
│       │   │   ├── archive/    Archive browser (pushed screen)
│       │   │   └── signals/    Send-signal bottom sheet
│       │   ├── service/        FCM service, notification channels
│       │   └── SupabaseClient.kt
│       └── res/
├── supabase/
│   ├── schema.sql              Tables, enums, triggers, pairing + archive RPCs
│   ├── rls.sql                 RLS helpers + Row Level Security policies
│   ├── grants.sql              Table + function privileges
│   ├── storage.sql             Private buckets (whiteboard-media, avatars) + policies
│   ├── cron.sql                Scheduled auto-archive job
│   └── functions/
│       ├── send-notification/  FCM dispatch Edge Function
│       └── auto-archive/       Nightly archive Edge Function
├── cloudflare/
│   └── worker/                 Android Supabase HTTP proxy
└── AGENTS.md                   Guidance for AI coding agents
```


---

## License

MIT
