package com.kitsugi.animelist.data.cloudstream

import android.content.Context
import android.util.Log

/**
 * Otomatik plugin güncellemesi için başarısız (plugin, sürüm) çiftlerini kalıcı olarak hatırlar.
 *
 * Sorun (çökme raporlarında görüldü): Uzak depo bir sürümü (ör. JetFilmizle v97) listelese de
 * dosyası hiçbir yansıda bulunmuyorsa, her eşitlemede (uygulama açılışı dahil) 6–8 aday URL için
 * HTTP 404 isteği atılıyordu ve bu, her kurulu plugin için her seferinde tekrarlanıyordu.
 *
 * Bu sınıf, başarısız olan sürüm için [RETRY_AFTER_MS] süresince yeniden denemeyi atlar.
 * Depo farklı (daha yeni) bir sürüm yayınlarsa anahtar değiştiği için deneme hemen yapılır.
 * Başarılı güncellemeden sonra o plugin'in tüm bekleme kayıtları temizlenir.
 */
object CsAutoUpdateBackoff {

    private const val TAG = "CsAutoUpdateBackoff"
    private const val PREFS_NAME = "cs_auto_update_backoff"

    /** Başarısız bir sürüm bu süre dolana kadar yeniden denenmez. */
    private const val RETRY_AFTER_MS = 12L * 60L * 60L * 1000L

    /** Bu süreden eski kayıtlar budanır (tercih dosyası sınırsız büyümesin). */
    private const val PRUNE_AFTER_MS = 7L * 24L * 60L * 60L * 1000L

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Bekleme kaydının anahtarı.
     *
     * `fileHash` de anahtara girer: depo aynı sürüm numarasıyla YENİ bir dosya yayınlarsa
     * (yeniden derleme) anahtar değişir ve bekleme süresi beklenmeden hemen denenir. Aksi
     * halde içerik tabanlı güncelleme tespiti 12 saat boyunca boşa çalışırdı.
     */
    private fun key(internalName: String, version: Int, fileHash: String? = null): String {
        val normalized = fileHash?.trim()?.takeIf { it.isNotBlank() }
        return if (normalized == null) {
            "$internalName@$version"
        } else {
            "$internalName@$version@${normalized.takeLast(16)}"
        }
    }

    /** Bu (plugin, sürüm, içerik) için yakın zamanda başarısız bir deneme yapıldıysa true döner. */
    fun shouldSkip(
        context: Context,
        internalName: String,
        version: Int,
        fileHash: String? = null,
        nowMs: Long = System.currentTimeMillis()
    ): Boolean {
        return try {
            val failedAt = prefs(context).getLong(key(internalName, version, fileHash), 0L)
            failedAt > 0L && nowMs - failedAt < RETRY_AFTER_MS
        } catch (_: Throwable) {
            false
        }
    }

    /** Güncelleme denemesi (tüm adaylar) başarısız olduğunda çağrılır. */
    fun markFailed(
        context: Context,
        internalName: String,
        version: Int,
        fileHash: String? = null,
        nowMs: Long = System.currentTimeMillis()
    ) {
        try {
            val p = prefs(context)
            val editor = p.edit()
            editor.putLong(key(internalName, version, fileHash), nowMs)
            // Eski kayıtları buda.
            p.all.forEach { (k, v) ->
                if (v is Long && nowMs - v > PRUNE_AFTER_MS) editor.remove(k)
            }
            editor.apply()
            Log.d(TAG, "Auto-update failure recorded for $internalName v$version; retry after ${RETRY_AFTER_MS / 3_600_000L}h")
        } catch (_: Throwable) {
        }
    }

    /** Başarılı güncellemeden sonra o plugin'in tüm bekleme kayıtlarını temizler. */
    fun clear(context: Context, internalName: String) {
        try {
            val p = prefs(context)
            val prefix = "$internalName@"
            val editor = p.edit()
            p.all.keys.filter { it.startsWith(prefix) }.forEach { editor.remove(it) }
            editor.apply()
        } catch (_: Throwable) {
        }
    }

    /**
     * Tüm kayıtları siler. Kurulu eklentiler topluca kaldırıldığında çağrılır; aksi halde
     * artık var olmayan eklentiler için bekleme kayıtları tercih dosyasında kalırdı.
     */
    fun clearAll(context: Context) {
        try {
            prefs(context).edit().clear().apply()
        } catch (_: Throwable) {
        }
    }
}
