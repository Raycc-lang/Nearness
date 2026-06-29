package com.yourname.nearness

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage

/**
 * The single Supabase client for the whole app. Use this everywhere —
 * never construct a new client. Per AGENTS.md, only the data/ (Repository)
 * layer may touch this.
 *
 * IMPORTANT: [BuildConfig.SUPABASE_URL] points at the **Cloudflare Worker
 * proxy**, not at Supabase directly. The proxy mirrors Supabase's URL layout
 * (`/auth/v1`, `/rest/v1`, `/storage/v1`) so the SDK needs no special config.
 *
 * Realtime is intentionally NOT installed; the app uses polling + FCM for
 * updates by design. See cloudflare/worker and the ViewModels' polling loops.
 */
val supabase = createSupabaseClient(
    supabaseUrl = BuildConfig.SUPABASE_URL,
    supabaseKey = BuildConfig.SUPABASE_ANON_KEY,
) {
    install(Postgrest)
    install(Auth) {
        scheme = "nearness"
        host = "auth-callback"
    }
    install(Storage)
}
