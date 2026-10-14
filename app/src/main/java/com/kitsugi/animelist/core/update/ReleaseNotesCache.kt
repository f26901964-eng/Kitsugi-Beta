package com.kitsugi.animelist.core.update

import android.content.Context
import android.util.Log

/**
 * "Uygulama Hakkında → Sürüm Notları" kartının diskteki önbelleği.
 *
 * ## Neden gerekli
 * Sürüm notları GitHub Releases API'sinden okunur ve bu API kimliksiz isteklerde saatte
 * 60 istekle sınırlıdır. Kullanıcı Hakkında ekranını her açtığında yeni istek atılırsa
 * sınır kolayca dolar; o andan sonra **güncelleme denetimi de** çalışmaz hâle gelir
 * (aynı uç noktayı kullanır). Bu yüzden son başarılı yanıt [TTL_MS] boyunca saklanır:
 * ekran açılışı anında önbellekten beslenir, "Yenile" düğmesi ise zorla tazeler.
 *
 * ## Neden SharedPreferences
 * Tek satırlık, küçük bir kayıt: Room şeması büyümez, yeni bağımlılık gerekmez ve
 * okuma ana iş parçacığını bloklamayacak kadar hızlıdır. Kayıt bozuksa (uygulama
 * verisi temizliği vb.) sessizce yok sayılır ve ağdan yeniden çekilir.
 */
object ReleaseNotesCache {

    private const val TAG = "ReleaseNotesCache"
    private const val PREFS_NAME = "kitsugi_release_notes_cache"

    private const val KEY_VERSION_NAME = "version_name"
    private const val KEY_VERSION_CODE = "version_code"
    private const val KEY_TITLE = "release_title"
    private const val KEY_NOTES = "release_notes"
    private const val KEY_DOWNLOAD_URL = "download_url"
    private const val KEY_APK_SIZE = "apk_size"
    private const val KEY_PUBLISHED_AT = "published_at"
    private const val KEY_FETCHED_AT = "fetched_at_ms"

    /** Önbellek ömrü: 6 saat. Bu süre içinde ağa istek atılmaz. */
    const val TTL_MS = 6L * 60L * 60L * 1000L

    /**
     * Önbellekteki sürüm notları. [nowMs]'den [TTL_MS]'den daha eski kayıtlar ve bozuk
     * kayıtlar için null döner (çağıran taraf ağı dener).
     */
    fun read(context: Context, nowMs: Long = System.currentTimeMillis()): AppRelease? {
        val prefs = prefs(context) ?: return null
        val fetchedAt = prefs.getLong(KEY_FETCHED_AT, 0L)
        if (fetchedAt <= 0L || nowMs - fetchedAt > TTL_MS) return null
        val versionName = prefs.getString(KEY_VERSION_NAME, null)?.takeIf { it.isNotBlank() } ?: return null
        val notes = prefs.getString(KEY_NOTES, null) ?: return null
        return AppRelease(
            versionCode = prefs.getInt(KEY_VERSION_CODE, 0),
            versionName = versionName,
            releaseTitle = prefs.getString(KEY_TITLE, null).orEmpty().ifBlank { versionName },
            releaseNotes = notes,
            downloadUrl = prefs.getString(KEY_DOWNLOAD_URL, null).orEmpty(),
            apkSizeBytes = prefs.getLong(KEY_APK_SIZE, 0L),
            publishedAt = prefs.getString(KEY_PUBLISHED_AT, null).orEmpty()
        )
    }

    /** Başarılı bir okumanın ardından kaydı tazeler (son kullanma zamanı da burada yazılır). */
    fun write(context: Context, release: AppRelease, nowMs: Long = System.currentTimeMillis()) {
        val prefs = prefs(context) ?: return
        prefs.edit()
            .putString(KEY_VERSION_NAME, release.versionName)
            .putInt(KEY_VERSION_CODE, release.versionCode)
            .putString(KEY_TITLE, release.releaseTitle)
            .putString(KEY_NOTES, release.releaseNotes)
            .putString(KEY_DOWNLOAD_URL, release.downloadUrl)
            .putLong(KEY_APK_SIZE, release.apkSizeBytes)
            .putString(KEY_PUBLISHED_AT, release.publishedAt)
            .putLong(KEY_FETCHED_AT, nowMs)
            .apply()
    }

    /** Kayıt silinir: bir sonraki okuma ağdan tazeler. */
    fun clear(context: Context) {
        runCatching { prefs(context)?.edit()?.clear()?.apply() }
    }

    private fun prefs(context: Context) = runCatching {
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }.onFailure { Log.w(TAG, "Önbellek açılamadı: ${it.message}") }.getOrNull()
}
