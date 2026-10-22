package com.kitsugi.animelist.core.sync

import android.content.Context
import com.kitsugi.animelist.data.auth.AniListImportManager
import com.kitsugi.animelist.data.auth.BangumiAuthStore
import com.kitsugi.animelist.data.auth.BangumiImportManager
import com.kitsugi.animelist.data.auth.ExternalAuthManager
import com.kitsugi.animelist.data.auth.KitsuImportManager
import com.kitsugi.animelist.data.auth.MalImportManager
import com.kitsugi.animelist.data.auth.ShikimoriImportManager
import com.kitsugi.animelist.data.auth.SimklImportManager
import com.kitsugi.animelist.data.local.KitsugiDatabase
import com.kitsugi.animelist.data.local.MediaEntryRepository
import com.kitsugi.animelist.data.local.SourceRefreshStats
import com.kitsugi.animelist.data.settings.SettingsDataStore
import com.kitsugi.animelist.model.MediaEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Listem sayfasının **otomatik ve artımlı** yenileme motoru.
 *
 * Eski davranış (kaldırıldı):
 * - Yalnızca AniList · MAL · Simkl yenileniyordu; Kitsu, Shikimori ve Bangumi hiç
 *   otomatik güncellenmiyordu. Kullanıcı yalnızca Bangumi bağlıysa hiçbir şey
 *   otomatik gelmiyor, "hangi kaynak olursa olsun sayfalar kendini güncellemiyor"
 *   görülüyordu.
 * - Tek bir global "son yenileme" damgası vardı ve 24 saat geçmeden atlanıyordu.
 *   Bir kaynak hata verse bile damga yazılıyor, diğerleri de 24 saat kilitleniyordu;
 *   yeni bağlanan bir hesap açılışta hiç çekilmiyordu.
 * - Yenileme `replaceSourcePreservingAdultFlags` = `deleteBySource + insertAll`
 *   (kaynağı sıfırdan yazma) idi. Liste her açılışta baştan kurulduğu için sayfalar
 *   "yenilendi" değil "sıfırdan çekildi" gibi davranıyordu: bekleyen çevrimdışı
 *   değişiklikler düşüyor, kaydırma konumu bozuluyor, +18/başlık zenginleştirmeleri
 *   risk altına giriyordu.
 *
 * Şimdiki davranış:
 * - **Altı kaynağın tamamı** (AniList · MAL · Simkl · Kitsu · Shikimori · Bangumi)
 *   taranır; yalnızca **bağlı** olanlara istek atılır, her kaynağın kendi "son
 *   başarılı yenileme" damgası tutulur (biri patlarsa diğerlerini kilitlemez).
 * - Yenileme **artımlıdır**: [MediaEntryRepository.refreshFromSource] yalnızca
 *   eksik kayıtları ekler ve gerçekten değişmiş alanları günceller; hiçbir satır
 *   silinmez, bekleyen senkron işi düşürülmez.
 * - Aynı anda iki yenileme koşmaz (mutex). Uygulama açılışı ve Listem sayfası
 *   aynı anda istese bile iş bir kez yapılır.
 */
object AutoListRefresh {

    /** Desteklenen kaynaklar. `id` değerleri `MediaEntry.source` ile aynıdır. */
    enum class Source(val id: String, val label: String) {
        ANILIST("anilist", "AniList"),
        MAL("mal", "MAL"),
        SIMKL("simkl", "Simkl"),
        KITSU("kitsu", "Kitsu"),
        SHIKIMORI("shikimori", "Shikimori"),
        BANGUMI("bangumi", "Bangumi");

        companion object {
            val ALL: List<Source> = entries.toList()

            /** `MediaEntry.source` değerinden kaynağı bulur ("jikan" → MAL). */
            fun forEntrySource(source: String): Source? = when (source.trim().lowercase()) {
                "anilist" -> ANILIST
                "mal", "jikan", "myanimelist" -> MAL
                "simkl" -> SIMKL
                "kitsu" -> KITSU
                "shikimori" -> SHIKIMORI
                "bangumi" -> BANGUMI
                else -> null
            }
        }
    }

    /**
     * Tek kaynağın yenileme sonucu. `stats == null` ise kaynak atlandı
     * (bağlı değil / kimlik yok) ve `error` bunun nedenini taşır.
     */
    data class Report(
        val source: Source,
        val stats: SourceRefreshStats? = null,
        val error: String? = null,
        val skipped: Boolean = false
    ) {
        val isSuccess: Boolean get() = error == null
    }

    /** Ön planda (Listem açılırken) kabul edilen bayatlık süresi. */
    const val FOREGROUND_MIN_INTERVAL_MS: Long = 15 * 60 * 1000L

    /** Uygulama açılışında kabul edilen bayatlık süresi. */
    const val STARTUP_MIN_INTERVAL_MS: Long = 6 * 60 * 60 * 1000L

    private const val PREFS_NAME = "kitsugi_auto_sync"
    private const val KEY_LAST_SYNC_LEGACY = "last_list_sync_ms"
    private const val KEY_LAST_SUCCESS_PREFIX = "last_refresh_ms_"
    // Deneme damgası: başarısız bir kaynak her ekran açılışında yeniden denenip
    // kullanıcıya tekrar tekrar hata snackbar'ı üretmesin diye ayrı tutulur.
    private const val KEY_LAST_ATTEMPT_PREFIX = "last_attempt_ms_"

    private val runMutex = Mutex()

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun lastSuccessKey(source: Source) = "$KEY_LAST_SUCCESS_PREFIX${source.id}"
    private fun lastAttemptKey(source: Source) = "$KEY_LAST_ATTEMPT_PREFIX${source.id}"

    /** Bu kaynağın son **başarılı** yenilemesinden bu yana geçen süre (ms). */
    fun millisSinceLastRefresh(context: Context, source: Source): Long {
        val stored = prefs(context).getLong(lastSuccessKey(source), 0L)
        if (stored <= 0L) return Long.MAX_VALUE
        return (System.currentTimeMillis() - stored).coerceAtLeast(0L)
    }

    /**
     * Bu kaynağın son **denemesinden** (başarılı ya da başarısız) bu yana geçen süre.
     * Bayatlık kararı bunu kullanır: sürekli hata veren bir kaynak her ekran
     * açılışında yeniden denenip kullanıcıyı hata mesajıyla dövmez.
     */
    private fun millisSinceLastAttempt(context: Context, source: Source): Long {
        val stored = prefs(context).getLong(lastAttemptKey(source), 0L)
        if (stored <= 0L) return Long.MAX_VALUE
        return (System.currentTimeMillis() - stored).coerceAtLeast(0L)
    }

    fun isStale(
        context: Context,
        source: Source,
        minIntervalMs: Long = FOREGROUND_MIN_INTERVAL_MS
    ): Boolean = millisSinceLastAttempt(context, source) >= minIntervalMs

    /**
     * Bağlı hesaplardan hangisi var? (hiçbiri yoksa hiç ağ isteği atılmaz)
     */
    fun connectedSources(context: Context): List<Source> {
        val result = mutableListOf<Source>()
        if (!ExternalAuthManager.getAniListToken(context).isNullOrBlank()) result += Source.ANILIST
        if (!ExternalAuthManager.getMalToken(context).isNullOrBlank()) result += Source.MAL
        if (!ExternalAuthManager.getSimklToken(context).isNullOrBlank()) result += Source.SIMKL
        if (!ExternalAuthManager.getKitsuToken(context).isNullOrBlank() &&
            !ExternalAuthManager.getKitsuUserId(context).isNullOrBlank()
        ) {
            result += Source.KITSU
        }
        if (!ExternalAuthManager.getShikimoriToken(context).isNullOrBlank()) result += Source.SHIKIMORI
        if (BangumiAuthStore.isConnected(context)) result += Source.BANGUMI
        return result
    }

    fun hasAnyConnectedSource(context: Context): Boolean = connectedSources(context).isNotEmpty()

    /**
     * Bayat olan **bağlı** kaynakları artımlı olarak yeniler.
     *
     * @param minIntervalMs bu süreden daha kısa süre önce başarıyla yenilenmiş kaynak atlanır.
     * @param sources yalnızca bu kaynaklar değerlendirilir (varsayılan: altısı).
     */
    suspend fun refreshStale(
        context: Context,
        minIntervalMs: Long = FOREGROUND_MIN_INTERVAL_MS,
        sources: List<Source> = Source.ALL
    ): List<Report> = withContext(Dispatchers.IO) {
        runMutex.withLock {
            val connected = connectedSources(context).toSet()
            val stale = sources.filter { source ->
                source in connected && isStale(context, source, minIntervalMs)
            }
            runRefresh(context.applicationContext, stale)
        }
    }

    /**
     * Verilen kaynakları (bağlı olanları) bayatlık kontrolü **yapmadan** yeniler.
     * Kullanıcı "Yenile"ye bastığında kullanılır.
     */
    suspend fun refreshNow(
        context: Context,
        sources: List<Source> = Source.ALL
    ): List<Report> = withContext(Dispatchers.IO) {
        runMutex.withLock {
            val connected = connectedSources(context).toSet()
            runRefresh(context.applicationContext, sources.filter { it in connected })
        }
    }

    private suspend fun runRefresh(context: Context, sources: List<Source>): List<Report> {
        if (sources.isEmpty()) return emptyList()
        val repository = MediaEntryRepository(
            dao = KitsugiDatabase.getDatabase(context).mediaEntryDao(),
            pendingSyncDao = KitsugiDatabase.getDatabase(context).pendingSyncDao(),
            context = context
        )

        val reports = mutableListOf<Report>()
        var anySuccess = false
        for (source in sources) {
            val report = try {
                val fetched = fetchSource(context, source)
                val stats = repository.refreshFromSource(source.id, fetched)
                anySuccess = true
                Report(source = source, stats = stats)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: NotConnectedException) {
                Report(source = source, error = error.message, skipped = true)
            } catch (error: Exception) {
                android.util.Log.w(
                    "KitsugiAutoRefresh",
                    "${source.label} artımlı yenileme hatası: ${error.message}"
                )
                Report(source = source, error = error.message ?: "bilinmeyen hata")
            }
            markAttempted(context, source)
            if (report.isSuccess) markRefreshed(context, source)
            reports += report
        }
        if (anySuccess) {
            // Eski sürümlerle uyum: global damga da tazelenir.
            prefs(context).edit()
                .putLong(KEY_LAST_SYNC_LEGACY, System.currentTimeMillis())
                .apply()
        }
        return reports
    }

    private fun markAttempted(context: Context, source: Source) {
        prefs(context).edit()
            .putLong(lastAttemptKey(source), System.currentTimeMillis())
            .apply()
    }

    private fun markRefreshed(context: Context, source: Source) {
        val now = System.currentTimeMillis()
        prefs(context).edit()
            .putLong(lastSuccessKey(source), now)
            .putLong(lastAttemptKey(source), now)
            .apply()
    }

    private class NotConnectedException(message: String) : Exception(message)

    /** Kaynağın uzak listesini çeker. Bağlı değilse [NotConnectedException] atar. */
    private suspend fun fetchSource(context: Context, source: Source): List<MediaEntry> = when (source) {
        Source.ANILIST -> {
            val token = ExternalAuthManager.getAniListToken(context)
            if (token.isNullOrBlank()) throw NotConnectedException("AniList bağlı değil")
            AniListImportManager.fetchAllLists(token)
        }

        Source.MAL -> {
            val token = ExternalAuthManager.getOrRefreshMalToken(context)
            if (token.isNullOrBlank()) throw NotConnectedException("MAL oturumu yok")
            val showAdult = runCatching {
                SettingsDataStore(context).settingsFlow.first().showAdultContent
            }.getOrDefault(false)
            MalImportManager.fetchAllLists(token, showAdult)
        }

        Source.SIMKL -> {
            val token = ExternalAuthManager.getSimklToken(context)
            if (token.isNullOrBlank()) throw NotConnectedException("Simkl bağlı değil")
            SimklImportManager.fetchAllLists(token)
        }

        Source.KITSU -> {
            val token = ExternalAuthManager.getKitsuToken(context)
            val userId = ExternalAuthManager.getKitsuUserId(context)
            if (token.isNullOrBlank() || userId.isNullOrBlank()) {
                throw NotConnectedException("Kitsu bağlı değil")
            }
            KitsuImportManager.fetchAllLists(context, token, userId)
        }

        Source.SHIKIMORI -> {
            val token = ExternalAuthManager.getOrRefreshShikimoriToken(context)
            var userId = ExternalAuthManager.getShikimoriUserId(context)
            if (!token.isNullOrBlank() && userId == null) {
                runCatching { ExternalAuthManager.ensureShikimoriUserResolved(context) }
                userId = ExternalAuthManager.getShikimoriUserId(context)
            }
            if (token.isNullOrBlank() || userId == null) {
                throw NotConnectedException("Shikimori bağlı değil")
            }
            ShikimoriImportManager.fetchAllLists(context, token, userId)
        }

        Source.BANGUMI -> {
            val token = BangumiAuthStore.getValidToken(context)
            if (token.isNullOrBlank()) throw NotConnectedException("Bangumi bağlı değil")
            BangumiImportManager.fetchAllLists(context, token)
        }
    }

    /**
     * Yenileme sonucunu tek satırlık kullanıcı mesajına çevirir.
     *
     * Değişiklik yoksa (ve `includeEmpty = false` ise) `null` döner; böylece otomatik
     * yenileme kullanıcıyı gereksiz snackbar ile dövmez.
     */
    fun summaryMessage(
        reports: List<Report>,
        includeEmpty: Boolean = false,
        includeFailures: Boolean = false
    ): String? {
        if (reports.isEmpty()) return null
        val changed = reports.filter { it.stats?.hasChanges == true }
        val failed = if (includeFailures) {
            reports.filter { !it.isSuccess && !it.skipped }
        } else {
            emptyList()
        }

        val parts = mutableListOf<String>()
        changed.forEach { report ->
            val stats = report.stats ?: return@forEach
            val details = buildList {
                if (stats.added > 0) add("+${stats.added} yeni")
                if (stats.updated > 0) add("${stats.updated} güncel")
            }.joinToString(", ")
            parts += "${report.source.label}: $details"
        }
        if (failed.isNotEmpty()) {
            parts += failed.joinToString(", ") { "${it.source.label} yenilenemedi" }
        }

        if (parts.isEmpty()) {
            return if (includeEmpty && failed.isEmpty()) "Listem güncel — eksik kayıt yok" else null
        }
        return "Listem güncellendi · ${parts.joinToString(" · ")}"
    }
}
