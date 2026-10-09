package com.kitsugi.animelist.data.account

/**
 * Kitsugi hesap altyapısı (Supabase) yapılandırması.
 *
 * ÖNEMLİ: Burada YALNIZCA publishable (anon) anahtar bulunur. Bu anahtar istemcide
 * kullanılmak üzere tasarlandı; güvenlik Row Level Security (supabase/schema.sql) ile sağlanır.
 * service_role / secret anahtarı ASLA buraya veya repoya konmaz.
 */
object KitsugiBackendConfig {
    const val SUPABASE_URL = "https://kjcxyaqwuzjgbeiowqte.supabase.co"
    const val SUPABASE_PUBLISHABLE_KEY = "sb_publishable_QmoQGc_uHqI8_nV3EUG_Qg_OBgB-uM0"
}
