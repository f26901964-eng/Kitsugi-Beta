package com.kitsugi.animelist.data.account

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.postgrest.Postgrest

/** Supabase istemcisi (tek örnek). Yapılandırma: [KitsugiBackendConfig]. */
object KitsugiAccountClient {
    val client: SupabaseClient by lazy {
        createSupabaseClient(
            supabaseUrl = KitsugiBackendConfig.SUPABASE_URL,
            supabaseKey = KitsugiBackendConfig.SUPABASE_PUBLISHABLE_KEY
        ) {
            install(Auth)
            install(Postgrest)
        }
    }
}
