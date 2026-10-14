package com.kitsugi.animelist.core.storage

import android.content.Context
import android.util.Log
import android.webkit.WebStorage
import android.webkit.WebView
import androidx.annotation.StringRes
import com.kitsugi.animelist.R
import com.kitsugi.animelist.core.p2p.P2pSettingsRepository
import com.kitsugi.animelist.core.p2p.P2pStreamingEngine
import com.kitsugi.animelist.core.p2p.isStreaming
import com.kitsugi.animelist.data.fandom.FandomRepository
import com.kitsugi.animelist.data.local.KitsugiDatabase
import com.kitsugi.animelist.data.manga.loader.MangaCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Kitsugi genel depolama bekçisi.
 *
 * ## Neden gerekli
 * Uygulamada her önbellek kendi başına sınırlı görünüyordu ama **hiçbir ortak temizleyici yoktu**:
 *  - P2P disk önbelleği (varsayılan 2 GB) + Coil görselleri (512 MB) + manga sayfaları (150 MB)
 *    + video önizlemeleri (150 MB) = ~2,8 GB taban,
 *  - üstüne altyazı zip'leri, Room blob tabloları (`persistent_details_cache`, `video_cache`,
 *    `translation_cache`) ve teşhis dosyaları eklendiğinde kurulum ~4 GB'a çıkıyordu,
 *  - bazı budama kodları yazılmış ama **yanlış koşullara bağlı** olduğu için hiç çalışmıyordu
 *    (bkz. `MappingSyncWorker` — yalnızca Wi-Fi + batarya iyi şartı),
 *  - bu birikim zamanla tüm disk G/Ç işlerini ağırlaştırıp kaydırma/gezinme kasmasına dönüşüyordu.
 *
 * Bu nesne tek noktadan:
 *  1. [scan] ile her kalemin gerçek disk kullanımını ölçer (Ayarlar > Depolama ekranı),
 *  2. [clean] / [cleanAll] ile kullanıcı isteğine göre temizler,
 *  3. [runMaintenance] ile arka planda **yaş + bütçe** kurallarına göre kendiliğinden budar.
 *
 * ## Kalem kapsamı
 *  - Dosya tabanlı kalemler (görsel, P2P, manga, önizleme, altyazı, teşhis, WebView, eklenti)
 *    fiziksel dizin boyutuyla ölçülür.
 *  - **Wiki önbelleği** (`fandom:v1:` Room kayıtları) mantıksal veri boyutuyla ölçülür;
 *    temizliği kullanıcının eşleşme kararlarını korur ([FandomRepository.clearCache]).
 *  - **Veritabanı önbelleği** SQLite dosyalarının disk boyutunu gösterir.
 *
 * ## Güvenlik kuralları
 *  - Hiçbir işlem ana iş parçacığında çalışmaz (tamamı `Dispatchers.IO`).
 *  - Aktif oynatma sırasında P2P önbelleği temizlenmez ([P2pStreamingEngine.clearCache] zaten korur).
 *  - Silme işlemleri `runCatching` ile sarılır; tek bir dosya hatası tüm temizliği düşürmez.
 *  - Kullanıcı verisi (mylist, hesap, indirilen videolar) **asla** silinmez; burada yalnızca
 *    yeniden üretilebilir önbellekler ve teşhis kayıtları vardır.
 */
object StorageJanitor {

    private const val TAG = "StorageJanitor"

    private const val PREFS_NAME = "kitsugi_storage_janitor"
    private const val KEY_LAST_MAINTENANCE = "last_maintenance_ms"

    /** Otomatik bakım en fazla bu sıklıkta çalışır (6 saat). */
    private const val MAINTENANCE_INTERVAL_MS = 6L * 60L * 60L * 1000L

    // ─── Bütçe / yaş kuralları ─────────────────────────────────────────────────

    /** Video önizleme kareleri: 150 MB ve 30 gün. */
    private const val PREVIEWS_MAX_BYTES = 150L * 1024 * 1024
    private const val PREVIEWS_MAX_AGE_MS = 30L * 24L * 60L * 60L * 1000L

    /** Manga sayfa önbelleği: 150 MB (MangaCache ile aynı bütçe). */
    private const val MANGA_MAX_BYTES = 150L * 1024 * 1024

    /** Altyazı zip açılımları: 14 gün. */
    private const val SUBTITLE_MAX_AGE_MS = 14L * 24L * 60L * 60L * 1000L

    /** Detay/video çözümleme önbelleği: 30 gün. */
    private const val DETAIL_CACHE_MAX_AGE_MS = 30L * 24L * 60L * 60L * 1000L
    private const val VIDEO_CACHE_MAX_AGE_MS = 30L * 24L * 60L * 60L * 1000L

    /** Çeviri önbelleği: en yeni N satır tutulur. */
    private const val TRANSLATION_MAX_ROWS = 5000

    /** Arama geçmişi: UI'da son 20 gösterilir; tabloda en yeni N satır tutulur. */
    private const val SEARCH_HISTORY_MAX_ROWS = 500

    /** Teşhis kayıtları: 7 günden eskiler silinir. */
    private const val DIAGNOSTICS_MAX_AGE_MS = 7L * 24L * 60L * 60L * 1000L

    /** Aynı anda tek bakım/tarama çalışsın. */
    private val inFlight = AtomicBoolean(false)

    // ─── Kalemler ──────────────────────────────────────────────────────────────

    /**
     * Depolamada yer kaplayan kalemler. `clearable=false` olanlar yalnızca bilgi amaçlıdır
     * (ör. eklenti dosyaları kullanıcı tarafından Ayarlar > Eklentiler'den yönetilir).
     */
    enum class Bucket(
        val key: String,
        @StringRes val labelRes: Int,
        @StringRes val descriptionRes: Int,
        val clearable: Boolean,
        /** true → temizlemeden önce kullanıcıdan ayrıca onay istenir (oturum/oturum verisi kaybı). */
        val dangerous: Boolean = false
    ) {
        IMAGE_CACHE(
            "image_cache", R.string.storage_bucket_images, R.string.storage_bucket_images_desc,
            clearable = true
        ),
        P2P_CACHE(
            "p2p_cache", R.string.storage_bucket_p2p, R.string.storage_bucket_p2p_desc,
            clearable = true
        ),
        MANGA_CACHE(
            "manga_cache", R.string.storage_bucket_manga, R.string.storage_bucket_manga_desc,
            clearable = true
        ),
        PREVIEWS(
            "previews", R.string.storage_bucket_previews, R.string.storage_bucket_previews_desc,
            clearable = true
        ),
        SUBTITLE_CACHE(
            "subtitle_cache", R.string.storage_bucket_subtitles, R.string.storage_bucket_subtitles_desc,
            clearable = true
        ),
        DATABASE(
            "database", R.string.storage_bucket_database, R.string.storage_bucket_database_desc,
            clearable = true
        ),
        WIKI_CACHE(
            "wiki_cache", R.string.storage_bucket_wiki, R.string.storage_bucket_wiki_desc,
            clearable = true
        ),
        DIAGNOSTICS(
            "diagnostics", R.string.storage_bucket_diagnostics, R.string.storage_bucket_diagnostics_desc,
            clearable = true
        ),
        WEBVIEW_DATA(
            "webview", R.string.storage_bucket_webview, R.string.storage_bucket_webview_desc,
            clearable = true, dangerous = true
        ),
        EXTENSIONS(
            "extensions", R.string.storage_bucket_extensions, R.string.storage_bucket_extensions_desc,
            clearable = false
        ),
    }

    data class BucketUsage(
        val bucket: Bucket,
        val bytes: Long,
        val fileCount: Int
    )

    data class CleanResult(
        val freedBytes: Long,
        val failures: Int,
        val message: String? = null
    )

    // ─── Ölçüm ─────────────────────────────────────────────────────────────────

    /** Tüm kalemleri ölçer. G/Ç dispatcher'ında çalışır; ana iş parçacığını bloklamaz. */
    suspend fun scan(context: Context): List<BucketUsage> = withContext(Dispatchers.IO) {
        Bucket.entries.map { bucket ->
            val stats = statsOf(context, bucket)
            BucketUsage(bucket, stats.first, stats.second)
        }
    }

    suspend fun totalBytes(context: Context): Long = withContext(Dispatchers.IO) {
        Bucket.entries.sumOf { statsOf(context, it).first }
    }

    // ─── Temizleme (kullanıcı isteği) ──────────────────────────────────────────

    /**
     * Tek bir kalemi temizler ve **kazanılan bayt** miktarını döner.
     * Silinemeyen dosya sayısı [CleanResult.failures] içinde raporlanır.
     */
    suspend fun clean(context: Context, bucket: Bucket): CleanResult = withContext(Dispatchers.IO) {
        val before = statsOf(context, bucket).first
        var failures = 0
        var message: String? = null

        try {
            when (bucket) {
                Bucket.IMAGE_CACHE -> clearCoilDiskCache(context)
                Bucket.P2P_CACHE -> message = clearP2pCache()
                Bucket.MANGA_CACHE -> MangaCache(context).clearAll()
                Bucket.PREVIEWS -> failures += deleteChildren(previewsDir(context))
                Bucket.SUBTITLE_CACHE -> failures += deleteChildren(subtitleCacheDir(context))
                Bucket.DATABASE -> failures += pruneDatabase(context, ignoreAge = true)
                // Wiki kayıtları Room tablosunda tutulur; kalemin temizliği kullanıcının
                // "doğru/yanlış" eşleşme kararlarına dokunmaz (bkz. FandomRepository.clearCache).
                Bucket.WIKI_CACHE -> FandomRepository.clearCache(context)
                Bucket.DIAGNOSTICS -> failures += deleteChildren(diagnosticsDir(context))
                Bucket.WEBVIEW_DATA -> clearWebViewData(context)
                Bucket.EXTENSIONS -> Unit // Bilgi amaçlı; silme yok.
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Temizleme başarısız (${bucket.key}): ${t.message}")
            message = t.message
        }

        val after = statsOf(context, bucket).first
        val freed = (before - after).coerceAtLeast(0L)
        Log.i(TAG, "Temizlendi: ${bucket.key} → ${freed / 1024} KB (hatalı: $failures)")
        CleanResult(freed, failures, message)
    }

    /**
     * Tüm güvenli önbellekleri temizler. [includeDangerous] verilmedikçe WebView verisi
     * (Cloudflare oturumları, eklenti localStorage'ı) **silinmez**.
     */
    suspend fun cleanAll(context: Context, includeDangerous: Boolean = false): Long {
        var freed = 0L
        Bucket.entries.filter { it.clearable && (includeDangerous || !it.dangerous) }.forEach { bucket ->
            freed += clean(context, bucket).freedBytes
        }
        return freed
    }

    // ─── Otomatik bakım ────────────────────────────────────────────────────────

    /**
     * Yaş + bütçe kurallarına göre sessizce budar. Uygulama açılışında ve günlük
     * [StorageMaintenanceWorker] içinde çağrılır.
     *
     * @param force true → 6 saatlik bekleme süresi yok sayılır (WorkManager çağrısı).
     */
    suspend fun runMaintenance(context: Context, force: Boolean = false) {
        if (!force && !isMaintenanceDue(context)) return
        if (!inFlight.compareAndSet(false, true)) return
        try {
            withContext(Dispatchers.IO) {
                val startedAt = System.currentTimeMillis()
                var freed = 0L

                // 1) Altyazı zip açılımları — 14 günden eski klasörler (AddonStreamClient yazar).
                freed += pruneOldChildren(subtitleCacheDir(context), SUBTITLE_MAX_AGE_MS)

                // 2) Video önizleme kareleri — 30 günden eski klasörler + 150 MB bütçe.
                val previews = previewsDir(context)
                freed += pruneOldChildren(previews, PREVIEWS_MAX_AGE_MS)
                freed += trimDirToBudget(previews, PREVIEWS_MAX_BYTES)

                // 3) Manga sayfa önbelleği — MangaCache artımlı sayaç + bütçe kırpması.
                runCatching { MangaCache(context).trimToBudget(MANGA_MAX_BYTES) }

                // 4) Teşhis dosyaları — 7 günden eski kayıtlar (kilitli dosyalar atlanır).
                freed += pruneOldFiles(diagnosticsDir(context), DIAGNOSTICS_MAX_AGE_MS)

                // 5) Room blob tabloları — eski detay/video kayıtları + çeviri satır sınırı.
                freed += pruneDatabase(context, ignoreAge = false)

                // 6) Misafir P2P artıkları — P2P kapalıysa ve bütçe aşıldıysa bırakılan segmentler.
                runCatching { reclaimStaleP2pCache(context) }

                markMaintenanceDone(context)
                Log.i(
                    TAG,
                    "Bakım tamamlandı: ${freed / 1024} KB geri kazanıldı " +
                        "(${System.currentTimeMillis() - startedAt} ms)"
                )
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Bakım hatası: ${t.message}")
        } finally {
            inFlight.set(false)
        }
    }

    private fun isMaintenanceDue(context: Context): Boolean {
        val last = runCatching {
            JanitorPrefs.of(context).getLong(KEY_LAST_MAINTENANCE, 0L)
        }.getOrDefault(0L)
        return System.currentTimeMillis() - last >= MAINTENANCE_INTERVAL_MS
    }

    private fun markMaintenanceDone(context: Context) {
        runCatching {
            JanitorPrefs.of(context).edit().putLong(KEY_LAST_MAINTENANCE, System.currentTimeMillis()).apply()
        }
    }

    // ─── Kalem → dizin eşlemesi ────────────────────────────────────────────────

    private fun imageCacheDir(context: Context) = File(context.cacheDir, "nuvio_images")

    private fun p2pCacheDir(context: Context) = File(context.cacheDir, "p2p-engine")

    private fun mangaCacheDir(context: Context) = File(context.filesDir, "manga_cache")

    private fun previewsDir(context: Context) = File(context.filesDir, "previews")

    private fun subtitleCacheDir(context: Context) = File(context.cacheDir, "anisub_subs")

    private fun diagnosticsDir(context: Context) = File(context.filesDir, "diagnostics")

    private fun extensionsDir(context: Context) = File(context.filesDir, "cs_extensions")

    private fun webViewDir(context: Context) = File(context.dataDir, "app_webview")

    private fun databaseFiles(context: Context): List<File> {
        val dir = context.getDatabasePath("Kitsugi_database").parentFile ?: return emptyList()
        return listOf(
            File(dir, "Kitsugi_database"),
            File(dir, "Kitsugi_database-wal"),
            File(dir, "Kitsugi_database-shm"),
        )
    }

    // ─── Ölçüm yardımcıları ────────────────────────────────────────────────────

    /** [bucket] için (toplam bayt, dosya sayısı). */
    private suspend fun statsOf(context: Context, bucket: Bucket): Pair<Long, Int> = when (bucket) {
        Bucket.IMAGE_CACHE -> dirStats(imageCacheDir(context))
        Bucket.P2P_CACHE -> dirStats(p2pCacheDir(context))
        Bucket.MANGA_CACHE -> dirStats(mangaCacheDir(context))
        Bucket.PREVIEWS -> dirStats(previewsDir(context))
        Bucket.SUBTITLE_CACHE -> dirStats(subtitleCacheDir(context))
        Bucket.DIAGNOSTICS -> dirStats(diagnosticsDir(context))
        Bucket.EXTENSIONS -> dirStats(extensionsDir(context))
        Bucket.WEBVIEW_DATA -> dirStats(webViewDir(context))
        // Wiki önbelleği dosya değil Room kaydı olarak tutulur: diske değil **mantıksal veri
        // boyutuna** bakılır (SQLite dosyası silmeden sonra küçülmez, bkz. DAO.sizeByPrefix).
        Bucket.WIKI_CACHE -> runCatching { FandomRepository.cacheStats(context) }.getOrDefault(0L to 0)
        Bucket.DATABASE -> {
            val files = databaseFiles(context)
            files.sumOf { it.length() } to files.count { it.exists() }
        }
    }

    /** Dizin ağacını yinelemesiz gezer; (bayt, dosya sayısı) döner. Hataları yutar. */
    fun dirStats(dir: File): Pair<Long, Int> {
        if (!dir.exists()) return 0L to 0
        var bytes = 0L
        var count = 0
        val stack = ArrayDeque<File>()
        stack.addLast(dir)
        while (stack.isNotEmpty()) {
            val current = stack.removeLast()
            val children = runCatching { current.listFiles() }.getOrNull() ?: continue
            for (child in children) {
                if (child.isDirectory) {
                    stack.addLast(child)
                } else {
                    bytes += child.length()
                    count++
                }
            }
        }
        return bytes to count
    }

    // ─── Silme yardımcıları ────────────────────────────────────────────────────

    /** Dizinin tüm içeriğini siler (dizin kalır); silinemeyen dosya sayısını döner. */
    private fun deleteChildren(dir: File): Int {
        val children = runCatching { dir.listFiles() }.getOrNull() ?: return 0
        var failures = 0
        for (child in children) {
            val ok = runCatching { child.deleteRecursively() }.getOrDefault(false)
            if (!ok) failures++
        }
        return failures
    }

    /** [maxAgeMs]'den eski **dosyaları** siler; kilitli olanları atlar. */
    private fun pruneOldFiles(dir: File, maxAgeMs: Long): Long {
        if (!dir.exists()) return 0L
        val cutoff = System.currentTimeMillis() - maxAgeMs
        var freed = 0L
        val stack = ArrayDeque<File>()
        stack.addLast(dir)
        while (stack.isNotEmpty()) {
            val current = stack.removeLast()
            val children = runCatching { current.listFiles() }.getOrNull() ?: continue
            for (child in children) {
                if (child.isDirectory) {
                    stack.addLast(child)
                } else if (child.lastModified() < cutoff) {
                    val size = child.length()
                    if (runCatching { child.delete() }.getOrDefault(false)) freed += size
                }
            }
        }
        return freed
    }

    /** [maxAgeMs]'den eski **alt dizinleri** (klasör + içeriği) siler. */
    private fun pruneOldChildren(dir: File, maxAgeMs: Long): Long {
        val children = runCatching { dir.listFiles() }.getOrNull() ?: return 0L
        val cutoff = System.currentTimeMillis() - maxAgeMs
        var freed = 0L
        for (child in children) {
            // Klasörün "son kullanım" izi: en yeni dosyasının zaman damgası (yoksa klasörün kendisi).
            val stamp = runCatching {
                val newest = child.listFiles()?.maxOfOrNull { it.lastModified() }
                newest ?: child.lastModified()
            }.getOrDefault(child.lastModified())
            if (stamp >= cutoff) continue
            val size = dirStats(child).first
            if (runCatching { child.deleteRecursively() }.getOrDefault(false)) freed += size
        }
        return freed
    }

    /** Dizin toplamı [budget]'i aşarsa en eski dosyalardan başlayarak bütçenin %85'ine iner. */
    private fun trimDirToBudget(dir: File, budget: Long): Long {
        if (!dir.exists()) return 0L
        val allFiles = mutableListOf<File>()
        val stack = ArrayDeque<File>()
        stack.addLast(dir)
        var total = 0L
        while (stack.isNotEmpty()) {
            val current = stack.removeLast()
            val children = runCatching { current.listFiles() }.getOrNull() ?: continue
            for (child in children) {
                if (child.isDirectory) stack.addLast(child) else {
                    allFiles += child
                    total += child.length()
                }
            }
        }
        if (total <= budget) return 0L

        val target = (budget * 0.85).toLong()
        var freed = 0L
        allFiles.sortBy { it.lastModified() }
        for (file in allFiles) {
            if (total - freed <= target) break
            val size = file.length()
            if (runCatching { file.delete() }.getOrDefault(false)) freed += size
        }
        return freed
    }

    // ─── Kalem bazlı uygulamalar ───────────────────────────────────────────────

    private suspend fun clearCoilDiskCache(context: Context) {
        // Coil kendi disk günlüğünü yönettiği için dosyaları arkadan silmek yerine API kullanılır.
        val loader = runCatching {
            coil3.SingletonImageLoader.get(context.applicationContext)
        }.getOrNull()
        runCatching { loader?.diskCache?.clear() }
            .onFailure { Log.w(TAG, "Coil disk önbelleği temizlenemedi: ${it.message}") }
        runCatching { loader?.memoryCache?.clear() }
    }

    /** P2P önbelleğini motor API'siyle boşaltır; aktif oynatma varsa dokunmaz. */
    private suspend fun clearP2pCache(): String? {
        return try {
            P2pStreamingEngine.clearCache()
            null
        } catch (t: Throwable) {
            // En sık sebep: aktif oynatma sırasında temizleme denemesi. Sessizce raporlanır.
            Log.i(TAG, "P2P önbelleği temizlenemedi: ${t.message}")
            t.message
        }
    }

    /**
     * P2P kapalıyken diskte kalan segmentleri bütçeye göre geri kazanır.
     * Motor çalışmadığı için dosyalar kilitli değildir; aktif oynatma yoksa güvenlidir.
     */
    private fun reclaimStaleP2pCache(context: Context) {
        val p2pEnabled = runCatching { P2pSettingsRepository.isP2pEnabled() }.getOrDefault(false)
        if (p2pEnabled) return
        // Aktif torrent akışı varsa (nadiren P2P kapalı olsa bile) dosyalara dokunma.
        val streaming = runCatching {
            com.kitsugi.animelist.core.p2p.P2pStreamingEngine.state.value.isStreaming
        }.getOrDefault(false)
        if (streaming) return
        val dir = File(context.cacheDir, "p2p-engine/payload")
        if (!dir.exists()) return
        // Yalnızca 7 günden eski, yarım kalmış segmentleri temizle.
        val freed = pruneOldFiles(dir, 7L * 24L * 60L * 60L * 1000L)
        if (freed > 0) Log.i(TAG, "P2P artıkları temizlendi: ${freed / 1024} KB")
    }

    /**
     * Room içindeki **önbellek** tablolarını budar. Kullanıcı verisi (mylist, geçmiş, ilerleme)
     * bu kapsam dışındadır.
     *
     * @param ignoreAge true → yaş koşulu atlanır, tablolar tamamen boşaltılır (kullanıcı temizliği).
     */
    private suspend fun pruneDatabase(context: Context, ignoreAge: Boolean): Int {
        val db = runCatching { KitsugiDatabase.getDatabase(context.applicationContext) }.getOrNull()
            ?: return 1
        var failures = 0
        val now = System.currentTimeMillis()

        // 1) Detay önbelleği — MappingSyncWorker yalnızca Wi-Fi'de çalışabildiği için burada da budanır.
        runCatching {
            db.persistentDetailCacheDao().deleteOlderThan(
                if (ignoreAge) Long.MAX_VALUE else now - DETAIL_CACHE_MAX_AGE_MS
            )
        }.onFailure { failures++ }

        // 2) Video çözümleme önbelleği — DAO'daki sorgu daha önce hiçbir yerden çağrılmıyordu.
        runCatching {
            db.videoDao().clearExpiredVideos(
                if (ignoreAge) Long.MAX_VALUE else now - VIDEO_CACHE_MAX_AGE_MS
            )
        }.onFailure { failures++ }

        // 3) Çeviri önbelleği — tabloda zaman damgası yok; en eski satırlar rowid ile atılır.
        runCatching { db.translationCacheDao().pruneToLimit(TRANSLATION_MAX_ROWS) }
            .onFailure { failures++ }

        // 4) Arama geçmişi — UI yalnızca son 20 kaydı gösterir; tablo sınırsız büyümesin.
        runCatching { db.searchHistoryDao().pruneToLimit(SEARCH_HISTORY_MAX_ROWS) }
            .onFailure { failures++ }

        return failures
    }

    @Suppress("DEPRECATION")
    private suspend fun clearWebViewData(context: Context) {
        withContext(Dispatchers.Main) {
            runCatching { WebView(context).clearCache(true) }
            runCatching { WebStorage.getInstance().deleteAllData() }
        }
        // Kalan çerez/dosya artıklarını da bırakma (WebView kapalıyken güvenlidir).
        runCatching {
            val children = File(context.dataDir, "app_webview").listFiles() ?: return@runCatching
            children.filter { it.name.startsWith("Cache") || it.name == "WebStorage" }
                .forEach { it.deleteRecursively() }
        }
    }

    /** Kısa ömürlü SharedPreferences erişimi (uygulama bağlamı sızıntısı olmadan). */
    private object JanitorPrefs {
        fun of(context: Context) =
            context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
}
