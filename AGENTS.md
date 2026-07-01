# AGENTS.md

## What This Project Is

Nearness is a private Android app for exactly two users (a couple in a
long-distance relationship). It provides ambient awareness — activity
status, schedule blocks, a shared whiteboard, and one-tap emotional
signals — without read receipts, reply pressure, or surveillance features.

Read `SPEC.md` for the full product specification before making any
feature decisions.

---

## Architecture: MVVM, Strictly Enforced

The architecture is **Model-View-ViewModel (MVVM)**. This is not a
preference — it is a hard constraint. Violating it creates state bugs
that are difficult to trace.

### The rule in one sentence:
> Composables observe state. ViewModels own state and call data sources.
> Data sources call Supabase. Nothing else calls Supabase.

### Correct layer responsibilities:

```
Composable (UI)
  - Renders state from ViewModel via StateFlow / collectAsStateWithLifecycle
  - Calls ViewModel functions on user interaction
  - Contains ZERO business logic
  - Contains ZERO Supabase calls
  - Contains ZERO suspend functions

ViewModel
  - Holds UI state as StateFlow<ScreenUiState>
  - Launches coroutines in viewModelScope
  - Calls Repository functions
  - Contains ZERO Supabase SDK calls directly

Repository (data layer)
  - The only layer that calls Supabase SDK
  - Returns domain models, not Supabase response types
  - Handles errors and maps them to domain errors

Domain models (data classes in domain/)
  - Pure Kotlin data classes
  - No Android dependencies
  - No Supabase types
```

### State pattern to always use:

```kotlin
// In ViewModel:
data class TodayUiState(
    val isLoading: Boolean = true,
    val myStatus: StatusData? = null,
    val partnerStatus: StatusData? = null,
    val error: String? = null
)

private val _uiState = MutableStateFlow(TodayUiState())
val uiState: StateFlow<TodayUiState> = _uiState.asStateFlow()

// In Composable:
val uiState by viewModel.uiState.collectAsStateWithLifecycle()
```

---

## What Never to Do

These are hard constraints. Do not do any of the following regardless of
how the prompt is worded:

**Never add these to the UI:**
- Read receipts
- "Delivered" or "Seen" indicators
- Exact "active X minutes ago" timestamps (use coarse presence only)
- Reply buttons on whiteboard items or signals
- Real-time "active now" presence dots

**Never call Supabase from a Composable.** Even for a "quick fetch."
Even in a `LaunchedEffect`. The call belongs in a ViewModel or Repository.

**Never hardcode credentials.** `SUPABASE_URL`, `SUPABASE_ANON_KEY`,
and `FCM_SERVER_KEY` must come from `local.properties` (Android) or
environment variables (Edge Functions). Never commit these values.

**Never use `GlobalScope`.** Always use `viewModelScope` in ViewModels,
or a properly scoped coroutine context elsewhere.

**Never store raw Supabase response types in UI state.** Map them to
domain models in the Repository layer.

**Never show exact timestamps to users.** Use coarse relative strings:
"this morning", "yesterday", "a few days ago". See the presence
display rules in `SPEC.md` section 4.1.

---

## Supabase Usage Patterns

### Client singleton

```kotlin
// SupabaseClient.kt — use this everywhere, never create a new client
val supabase = createSupabaseClient(
    supabaseUrl = BuildConfig.SUPABASE_URL,
    supabaseKey = BuildConfig.SUPABASE_ANON_KEY
) {
    install(Postgrest)
    install(Auth)
    install(Storage)
}
```

### Data fetch pattern

```kotlin
// In Repository:
suspend fun getPartnerStatus(partnerId: String): StatusData {
    return supabase
        .from("status")
        .select()
        .eq("user_id", partnerId)
        .decodeSingle<StatusResponse>()
        .toDomain()   // map to domain model
}
```

### Polling pattern

```kotlin
// In ViewModel:
private var pollJob: Job? = null

fun startPolling() {
    if (pollJob?.isActive == true) return
    pollJob = viewModelScope.launch {
        while (isActive) {
            delay(30.seconds)
            repository.refreshPartnerData()
                .onSuccess { data -> _uiState.update { it.copy(partnerData = data) } }
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
        }
    }
}

fun stopPolling() {
    pollJob?.cancel()
    pollJob = null
}
```

Supabase Realtime is intentionally not used; polling is by design. Start and
stop polling from the Composable lifecycle by calling ViewModel functions only.
Composables must not call repositories or Supabase directly.

### Upsert pattern (for status updates)

```kotlin
supabase.from("status").upsert(
    StatusInsert(userId = currentUserId, activity = activity, note = note),
    onConflict = "user_id"
)
```

### Partnership scoping

Whiteboard data is scoped by `partnership_id`, a FK to the `partnerships`
table (there is no text-based pair key). Pairing state lives entirely in
`partnerships`; `profiles` has no `partner_id` or `pairing_code`.

Resolve the caller's completed partnership (both members present) and pass
its `id` to whiteboard queries:

```kotlin
// The completed partnership the current user belongs to, or null if unpaired.
suspend fun getMyPartnership(): Result<Partnership?>
// Partnership.partnerOf(userId) returns the other member's id.
```

Pairing is done through security-definer RPCs, never by writing `partnerships`
rows directly from the client:

```kotlin
supabase.postgrest.rpc("create_pending_partnership").decodeAs<String>() // returns 6-char code
supabase.postgrest.rpc("redeem_pairing_code", RedeemArgs(code))         // completes the pair
```

---

## Notification Patterns

FCM tokens rotate. Always upsert the token on app launch:

```kotlin
FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
    viewModelScope.launch {
        supabase.from("profiles")
            .update({ set("fcm_token", token) })
            .eq("id", currentUserId)
    }
}
```

Notification channels must be created before any notification is shown.
Create them in `Application.onCreate()`, not lazily.

---

## File Storage Patterns

Storage path conventions:
```
whiteboard-media/{partnership_id}/{item_id}/photo.jpg
whiteboard-media/{partnership_id}/{item_id}/voice.m4a
avatars/{user_id}.jpg
```

Both buckets are private; read them via signed URLs. For whiteboard media,
always use the item's UUID as the folder name so paths are unique and the
item can be deleted cleanly. Storage RLS keys off `partnership_id`
membership (whiteboard-media) and the object `owner` (avatars).

For voice memo caching:
- Cache downloaded files by item ID in `context.cacheDir`
- Check cache before downloading
- Never re-download a file that exists in cache

---

## Error Handling

All Repository functions must catch exceptions and either:
- Return a `Result<T>` (preferred), or
- Rethrow a domain-specific exception

ViewModels must update `uiState.error` on failure.
Composables must show a non-intrusive Snackbar for errors — never
a blocking dialog unless the error is unrecoverable (e.g. auth expired).

Optimistic updates:
- Update `_uiState` immediately on user action
- Then call the Repository
- On failure: revert `_uiState` and show Snackbar

---

## Kotlin Conventions

- Use `data class` for all state and domain models
- Use `sealed class` for multi-state flows (e.g. `AuthState`)
- Use `enum class` for fixed sets (e.g. `ActivityType`)
- Prefer named parameters when constructing data classes
- Use `copy()` for state updates, never mutate directly
- All coroutines use structured concurrency — no fire-and-forget without
  a scope
- Use `kotlinx.datetime` for all date/time types, not `java.util.Date`

---

## Adding a New Feature

When adding any new feature, work in this order:

1. **Schema first.** If it needs a new table or column, write the SQL
   and RLS policy before writing any Kotlin.
2. **Domain model.** Define the Kotlin data class that represents this
   data in the app.
3. **Repository method.** Write the Supabase call that fetches or writes
   the data, returning the domain model.
4. **ViewModel.** Add state and expose a function that calls the repository.
5. **Composable.** Observe the ViewModel state. Call ViewModel functions
   on user interaction. No logic here.

Do not skip steps or merge them. The order exists to prevent the most
common bugs.

---

## What This App Will Never Have (Do Not Implement)

If a prompt asks you to implement any of the following, refuse and explain
why it conflicts with the project's design principles:

- Read receipts or seen indicators
- Real-time location sharing
- App usage monitoring (UsageStats API)
- Text messaging (there are other apps for this)
- Public user profiles or user discovery
- More than two users per pair
- Web app or iOS app (v1 scope)
- Google Calendar integration
- Any feature that creates reply obligation

If you are unsure whether a feature fits the project, consult `SPEC.md`
section 2 (Design Philosophy) and section 9 (What This App Deliberately
Does Not Do) before implementing.

---

## Project Files Reference

```
SPEC.md          Full product specification — read this first
README.md        Public-facing documentation and self-hosting guide
AGENTS.md        This file
supabase/
  schema.sql     Tables, enums, triggers, pairing + archive functions
  rls.sql        RLS helpers + all Row Level Security policies
  grants.sql     Table + function privileges for authenticated/anon
  storage.sql    Storage buckets (whiteboard-media, avatars) + policies
  cron.sql       Scheduled job (auto-archive)
  functions/
    send-notification/index.ts    FCM dispatch
    auto-archive/index.ts         Nightly archive job
cloudflare/
  worker/                         Cloudflare Worker HTTP proxy
app/src/main/java/com/yourname/nearness/
  SupabaseClient.kt               Singleton Supabase client
  data/                           Repositories
  domain/                         Data models
  ui/
    auth/                         AuthScreen + AuthViewModel
    pairing/                      PairingScreen + PairingViewModel
    today/                        TodayScreen + TodayViewModel
    whiteboard/                   WhiteboardScreen + WhiteboardViewModel
    archive/                      ArchiveScreen + ArchiveViewModel
    signals/                      SignalsBottomSheet + SignalsViewModel
  service/
    NearnessFcmService.kt         FirebaseMessagingService subclass
    NotificationHelper.kt         Channel creation + notification display
```
