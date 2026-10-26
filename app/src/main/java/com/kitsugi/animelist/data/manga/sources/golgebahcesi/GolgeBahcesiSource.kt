package com.kitsugi.animelist.data.manga.sources.golgebahcesi

import android.content.Context
import android.util.Log
import com.kitsugi.animelist.core.network.GolgeBahcesiImageHeaders
import com.kitsugi.animelist.data.manga.ExtensionEngine
import com.kitsugi.animelist.data.manga.MangaChapter
import com.kitsugi.animelist.data.manga.MangaDetails
import com.kitsugi.animelist.data.manga.MangaPage
import com.kitsugi.animelist.data.manga.MangaSource
import com.kitsugi.animelist.data.manga.MangaSourceResult
import com.kitsugi.animelist.data.manga.SourceConfigStore
import com.kitsugi.animelist.data.manga.sources.golgebahcesi.GolgeBahcesiParser.toMangaChapter
import com.kitsugi.animelist.data.manga.sources.golgebahcesi.GolgeBahcesiParser.toMangaDetails
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import uy.kohesive.injekt.Injekt
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit

/**
 * Kitsugi birinci sınıf (native) Gölge Bahçesi (`golgebahcesi.com`) manga kaynağı.
 *
 * - Popüler/güncel listeleme, arama (`alternativeTitles` dahil), seri detay ve bölüm listesini
 *   `https://api.golgebahcesi.com/api` REST uçlarından doğrudan çeker.
 * - `legacy` bölümleri (`c2.skycdn.online` / `golgebahcesi.com`) uygun `Referer` ve `Accept`
 *   başlıklarıyla doğrudan OkHttp üzerinden indirir.
 * - `secure` (şifreli `.png.enc` / `.jpg.enc`) bölümlerde sayfa listesini API'den anında
 *   (~200 ms) döndürür ve sayfaları [GolgeBahcesiSecureSessionManager] üzerinden akışlı
 *   olarak çözüp disk önbelleğine yazar.
 * - Kilitli (VIP/jetonlu erken erişim) bölümleri listeden ayıklar; doğrudan açılırsa
 *   anlaşılır Türkçe hata mesajı üretir.
 */
class GolgeBahcesiSource private constructor(
    context: Context,
) : MangaSource {

    companion object {
        private const val TAG = "GolgeBahcesiSource"

        const val SOURCE_ID = 7442439989890509521L
        const val SOURCE_NAME = "Gölge Bahçesi"
        const val PKG_NAME = "eu.kanade.tachiyomi.extension.tr.golgebahcesi"
        const val DEFAULT_BASE_URL = "https://golgebahcesi.com"
        const val API_BASE_URL = "https://api.golgebahcesi.com/api"
        const val VERSION_NAME = "1.6.1"
        const val VERSION_CODE = 106001L

        private const val PAGE_LIMIT = 20
        private const val DEFAULT_CHROME_UA =
            "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

        @Volatile
        private var instance: GolgeBahcesiSource? = null

        fun getInstance(context: Context): GolgeBahcesiSource {
            return instance ?: synchronized(this) {
                instance ?: GolgeBahcesiSource(context.applicationContext).also { instance = it }
            }
        }
    }

    private val appContext: Context = context.applicationContext
    private val configStore = SourceConfigStore(appContext)

    private val networkHelper: NetworkHelper by lazy {
        runCatching { Injekt.get(NetworkHelper::class.java) }
            .getOrElse { NetworkHelper(appContext) }
    }

    private val httpClient: OkHttpClient by lazy {
        networkHelper.client.newBuilder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    override val name: String = SOURCE_NAME
    override val lang: String = "tr"
    override val pkgName: String = PKG_NAME
    override val originalBaseUrl: String = DEFAULT_BASE_URL
    override val baseUrl: String
        get() = configStore.getPreferredBaseUrl(this, DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL
    override val engineType: ExtensionEngine = ExtensionEngine.KEI_SOURCE
    override val isBuiltIn: Boolean = true
    override val imageFetchTimeoutMs: Long = 90_000L

    private fun effectiveUserAgent(): String {
        return configStore.getUserAgentOverride(this)
            ?.takeIf { it.isNotBlank() }
            ?: runCatching { networkHelper.defaultUserAgentProvider() }.getOrNull()
            ?: DEFAULT_CHROME_UA
    }

    private suspend fun applySlowdownIfEnabled() {
        if (configStore.getSlowdownEnabled(this)) {
            delay(450L)
        }
    }

    private suspend fun executeApiGet(url: String): String = withContext(Dispatchers.IO) {
        applySlowdownIfEnabled()
        val cleanBase = baseUrl.trimEnd('/')
        var lastError: IOException? = null

        for (attempt in 1..2) {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", effectiveUserAgent())
                .header("Referer", "$cleanBase/")
                .header("Origin", cleanBase)
                .header("Accept", "application/json, text/plain, */*")
                .header("Accept-Language", "tr-TR,tr;q=0.9,en-US;q=0.8,en;q=0.7")
                .build()
            try {
                httpClient.newCall(req).execute().use { response ->
                    if (!response.isSuccessful) {
                        val code = response.code
                        if (code in 500..599 && attempt == 1) {
                            lastError = IOException("Gölge Bahçesi API sunucu hatası (HTTP $code)")
                        } else {
                            throw IOException("Gölge Bahçesi API hatası (HTTP $code)")
                        }
                    } else {
                        val body = response.body?.string()
                        if (!body.isNullOrBlank()) {
                            return@withContext body
                        }
                        throw IOException("Gölge Bahçesi API boş yanıt döndürdü")
                    }
                }
            } catch (e: IOException) {
                lastError = e
                if (attempt == 1) {
                    delay(350L)
                }
            }
        }
        throw lastError ?: IOException("Gölge Bahçesi API isteği başarısız")
    }

    override suspend fun fetchPopularManga(page: Int): MangaSourceResult = withContext(Dispatchers.IO) {
        val safePage = page.coerceAtLeast(1)
        val url = "$API_BASE_URL/series".toHttpUrl().newBuilder()
            .addQueryParameter("page", safePage.toString())
            .addQueryParameter("limit", PAGE_LIMIT.toString())
            .addQueryParameter("sort", "popular")
            .build()
            .toString()

        val raw = executeApiGet(url)
        val parsed = GolgeBahcesiParser.json.decodeFromString(SeriesListResponseDto.serializer(), raw)
        val cleanBase = baseUrl
        MangaSourceResult(
            mangas = parsed.data.map { it.toMangaDetails(cleanBase, name) },
            hasNextPage = parsed.pagination.currentPage < parsed.pagination.totalPages,
        )
    }

    suspend fun fetchLatestUpdates(page: Int): MangaSourceResult = withContext(Dispatchers.IO) {
        val safePage = page.coerceAtLeast(1)
        val url = "$API_BASE_URL/series".toHttpUrl().newBuilder()
            .addQueryParameter("page", safePage.toString())
            .addQueryParameter("limit", PAGE_LIMIT.toString())
            .addQueryParameter("sort", "updatedAt")
            .build()
            .toString()

        val raw = executeApiGet(url)
        val parsed = GolgeBahcesiParser.json.decodeFromString(SeriesListResponseDto.serializer(), raw)
        val cleanBase = baseUrl
        MangaSourceResult(
            mangas = parsed.data.map { it.toMangaDetails(cleanBase, name) },
            hasNextPage = parsed.pagination.currentPage < parsed.pagination.totalPages,
        )
    }

    override suspend fun fetchSearchManga(page: Int, query: String): MangaSourceResult = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            return@withContext fetchPopularManga(page)
        }

        // Doğrudan slug veya manga URL'si ile arama desteği (slug:golge-kole veya https://golgebahcesi.com/manga/golge-kole)
        if (trimmed.startsWith("slug:", ignoreCase = true) || trimmed.contains("/manga/")) {
            val slug = GolgeBahcesiParser.extractSeriesSlug(trimmed)
            if (slug.isNotEmpty()) {
                return@withContext try {
                    val detail = fetchMangaDetails("/manga/$slug")
                    MangaSourceResult(mangas = listOf(detail), hasNextPage = false)
                } catch (_: Exception) {
                    MangaSourceResult(mangas = emptyList(), hasNextPage = false)
                }
            }
        }

        val safePage = page.coerceAtLeast(1)
        val url = "$API_BASE_URL/series".toHttpUrl().newBuilder()
            .addQueryParameter("page", safePage.toString())
            .addQueryParameter("limit", PAGE_LIMIT.toString())
            .addQueryParameter("search", trimmed)
            .build()
            .toString()

        val raw = executeApiGet(url)
        val parsed = GolgeBahcesiParser.json.decodeFromString(SeriesListResponseDto.serializer(), raw)
        val cleanBase = baseUrl
        MangaSourceResult(
            mangas = parsed.data.map { it.toMangaDetails(cleanBase, name) },
            hasNextPage = parsed.pagination.currentPage < parsed.pagination.totalPages,
        )
    }

    override suspend fun fetchMangaDetails(mangaUrl: String): MangaDetails = withContext(Dispatchers.IO) {
        val slug = GolgeBahcesiParser.extractSeriesSlug(mangaUrl)
        if (slug.isEmpty()) {
            throw IOException("Geçersiz manga adresi: $mangaUrl")
        }
        val url = "$API_BASE_URL/series".toHttpUrl().newBuilder()
            .addPathSegment(slug)
            .build()
            .toString()

        val raw = executeApiGet(url)
        val dto = GolgeBahcesiParser.json.decodeFromString(SeriesDto.serializer(), raw)
        dto.toMangaDetails(baseUrl, name)
    }

    override suspend fun fetchChapterList(mangaUrl: String): List<MangaChapter> = withContext(Dispatchers.IO) {
        val slug = GolgeBahcesiParser.extractSeriesSlug(mangaUrl)
        if (slug.isEmpty()) {
            throw IOException("Geçersiz manga adresi: $mangaUrl")
        }
        val url = "$API_BASE_URL/series".toHttpUrl().newBuilder()
            .addPathSegment(slug)
            .addPathSegment("chapters")
            .build()
            .toString()

        val raw = executeApiGet(url)
        val chapters = GolgeBahcesiParser.json.decodeFromString(
            ListSerializer(ChapterDto.serializer()),
            raw,
        )

        chapters
            .filterNot { it.isLocked }
            .sortedWith(
                compareByDescending<ChapterDto> { it.number }
                    .thenByDescending { GolgeBahcesiParser.parseIsoTimestamp(it.publishedAt ?: it.createdAt) }
            )
            .map { it.toMangaChapter(seriesSlugFallback = slug) }
    }

    override suspend fun fetchPageList(chapter: MangaChapter): List<MangaPage> = withContext(Dispatchers.IO) {
        val ref = GolgeBahcesiParser.parseChapterUrl(chapter.url)
            ?: throw IOException("Geçersiz bölüm adresi: ${chapter.url}")

        val chapterId = ref.chapterId?.takeIf { it.isNotBlank() }
            ?: resolveChapterIdBySlug(ref.seriesSlug, ref.chapterSlug)
            ?: throw IOException("Bölüm kimliği çözümlenemedi: ${chapter.name}")

        val detailUrl = "$API_BASE_URL/chapters".toHttpUrl().newBuilder()
            .addPathSegment(chapterId)
            .build()
            .toString()

        val raw = executeApiGet(detailUrl)
        val dto = GolgeBahcesiParser.json.decodeFromString(ChapterDto.serializer(), raw)

        if (dto.isLocked) {
            throw IOException("Bu bölüm şu anda kilitli (erken erişim). Ücretsiz açıldığında okunabilir.")
        }

        val orderedPages = dto.pages.sortedBy { it.index }
        if (orderedPages.isEmpty()) {
            throw IOException("Bu bölümde henüz sayfa bulunmuyor.")
        }

        val effectiveSeriesSlug = dto.seriesSlug.trim().ifEmpty { ref.seriesSlug }
        val effectiveChapterSlug = dto.slug.trim().ifEmpty { ref.chapterSlug }
        val isSecure = GolgeBahcesiParser.isSecureChapter(dto)

        if (isSecure) {
            if (orderedPages.all { it.url.isBlank() }) {
                throw IOException("Bu bölümün sayfaları kilitli veya erişime kapalı.")
            }
            orderedPages.mapIndexed { idx, _ ->
                val secureUrl = GolgeBahcesiParser.buildSecurePageUrl(
                    chapterId = chapterId,
                    seriesSlug = effectiveSeriesSlug,
                    chapterSlug = effectiveChapterSlug,
                    pageIndex = idx,
                )
                MangaPage(
                    index = idx,
                    url = "",
                    imageUrl = secureUrl,
                )
            }
        } else {
            val cleanBase = baseUrl
            orderedPages
                .mapNotNull { p ->
                    GolgeBahcesiParser.resolveSiteUrl(cleanBase, p.url).takeIf { it.isNotEmpty() }
                }
                .mapIndexed { idx, resolved ->
                    MangaPage(
                        index = idx,
                        url = "",
                        imageUrl = resolved,
                    )
                }
        }
    }

    private suspend fun resolveChapterIdBySlug(seriesSlug: String, chapterSlug: String): String? {
        return try {
            val url = "$API_BASE_URL/series".toHttpUrl().newBuilder()
                .addPathSegment(seriesSlug)
                .addPathSegment("chapters")
                .build()
                .toString()
            val raw = executeApiGet(url)
            val chapters = GolgeBahcesiParser.json.decodeFromString(
                ListSerializer(ChapterDto.serializer()),
                raw,
            )
            chapters.firstOrNull { it.slug.equals(chapterSlug, ignoreCase = true) }?.id
                ?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            Log.w(TAG, "chapterId yedek sorgusu başarısız ($seriesSlug/$chapterSlug): ${e.message}")
            null
        }
    }

    override suspend fun fetchImageUrl(page: MangaPage): String {
        return page.imageUrl?.takeIf { it.isNotBlank() } ?: page.url
    }

    override suspend fun getImage(page: MangaPage): InputStream = withContext(Dispatchers.IO) {
        val imageUrl = page.imageUrl?.takeIf { it.isNotBlank() }
            ?: page.url.takeIf { it.isNotBlank() }
            ?: throw IOException("Görsel adresi boş (sayfa ${page.index + 1})")

        // 1) Şifreli (secure) bölüm sayfası: WebView akış oturumundan al
        val secureRef = GolgeBahcesiParser.parseSecurePageUrl(imageUrl)
        if (secureRef != null) {
            return@withContext GolgeBahcesiSecureSessionManager.awaitPageStream(
                context = appContext,
                ref = secureRef,
                baseUrl = baseUrl,
                customUserAgent = configStore.getUserAgentOverride(this@GolgeBahcesiSource),
                timeoutMs = 85_000L,
            )
        }

        // 2) Doğrudan (legacy CDN) görsel: Referer ve tarayıcı uyumlu Accept başlıklarıyla indir
        applySlowdownIfEnabled()
        val cleanBase = baseUrl.trimEnd('/')
        val request = Request.Builder()
            .url(imageUrl)
            .header("User-Agent", effectiveUserAgent())
            .header("Referer", "$cleanBase/")
            .header("Accept", GolgeBahcesiImageHeaders.ACCEPT)
            .header("Accept-Language", "tr-TR,tr;q=0.9,en;q=0.8")
            .build()

        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            val code = response.code
            response.close()
            throw IOException("Görsel indirilemedi (HTTP $code): $imageUrl")
        }
        val body = response.body ?: run {
            response.close()
            throw IOException("Görsel yanıt gövdesi boş: $imageUrl")
        }
        object : FilterInputStream(body.byteStream()) {
            override fun close() {
                try {
                    super.close()
                } finally {
                    response.close()
                }
            }
        }
    }
}
