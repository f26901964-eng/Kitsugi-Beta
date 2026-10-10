package com.kitsugi.animelist.data.account

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest

/** Supabase istemcisi (tek örnek). Yapılandırma: [KitsugiBackendConfig]. */
object KitsugiAccountClient {
    const val AUTH_CALLBACK_SCHEME = "kitsugi"
    const val AUTH_CALLBACK_HOST = "account-confirm"
    const val AUTH_CALLBACK_URL = "$AUTH_CALLBACK_SCHEME://$AUTH_CALLBACK_HOST"

    val client: SupabaseClient by lazy {
        createSupabaseClient(
            supabaseUrl = KitsugiBackendConfig.SUPABASE_URL,
            supabaseKey = KitsugiBackendConfig.SUPABASE_PUBLISHABLE_KEY
        ) {
            install(Auth) {
                // PKCE prevents confirmation tokens from being exposed as bearer tokens in a URL.
                flowType = io.github.jan.supabase.auth.FlowType.PKCE
                // Email confirmation links must return to the Android app rather than Supabase's
                // project Site URL (which may still be a local development URL).
                scheme = AUTH_CALLBACK_SCHEME
                host = AUTH_CALLBACK_HOST
            }
            install(Postgrest)
        }
    }
}
