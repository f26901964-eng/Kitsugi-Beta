package com.kitsugi.animelist.data.manga

/**
 * TR Manga eklentilerinin kullandığı scraping motorunu tanımlar.
 *
 * APK içindeki classes.dex'ten string analizi ile tespit edilir.
 *
 * MADARA      → WordPress + WP-Manga Plugin (31 site)
 * THEMESIA    → WP Manga Themesia (16 site)
 * SVELTE      → SvelteKit __data.json API (6 site: uzaymanga, afroditscans, vb.)
 * INERTIA     → Laravel + Inertia.js (1 site: mangadenizi)
 * CUSTOM_HTML → Kendi HTML/JSON yapısı (15 site: hattorimanga, siyahmelek, vb.)
 * UNKNOWN     → Tespit edilemeyen (MEX veya yeni formatlar)
 */
enum class ExtensionEngine {
    MADARA,
    THEMESIA,
    SVELTE,
    INERTIA,
    KEI_SOURCE,
    CUSTOM_HTML,
    UNKNOWN
}

/**
 * Tüm manga kaynak eklentilerinin uymak zorunda olduğu temel kontrat.
 *
 * Her eklenti bu interface'i implemente ederek kendi sitesindeki
 * manga listesini, bölümleri ve sayfa URL'lerini Kitsugi'ya sunar.
 *
 * Mihon/Aniyomi'nin HttpSource yapısından esinlenerek, Kitsugi'nun
 * mevcut hafif eklenti mimarisine uygun şekilde tasarlanmıştır.
 */
interface MangaSource {
    /** Kaynak eklentinin görünen adı. Örn: "Manga-TR", "SadScans" */
    val name: String

    /** Eklentinin kapsadığı site adresi. Örn: "https://manga-tr.com" */
    val baseUrl: String

    /** Eklentinin orijinal (sabit kodlanmış) base adresi. */
    val originalBaseUrl: String get() = baseUrl

    /** Kaynak dilin ISO 639-1 kodu. Örn: "tr", "en", "ko" */
    val lang: String
    
    /** Eklentinin paket adı veya id'si. */
    val pkgName: String get() = ""

    /** Eklentinin scraping motor tipi (APK DEX analizinden tespit edilir). */
    val engineType: ExtensionEngine get() = ExtensionEngine.UNKNOWN

    /**
     * Bu kaynağın yerel arşiv dosyası (ZIP/CBZ/RAR) üzerinden manga sunuyor olup olmadığını
     * işaret eder. true ise MangaPageLoaderV2/WebReader archive yolunu kullanır;
     * false (varsayılan) ise normal HTTP/HTTPS scraping akışı devam eder.
     */
    val isArchive: Boolean get() = false

    /**
     * Sitedeki en güncel / popüler mangaların listesini döndürür.
     * Sayfalama için [page] parametresi kullanılır (1'den başlar).
     */
    suspend fun fetchPopularManga(page: Int): MangaSourceResult

    /**
     * Source-defined browse feeds. Unlike Cloudstream mainPage shelves, manga extensions expose
     * capabilities such as popular/latest and (for Kotatsu) supported sort orders. The UI must
     * only render the feeds declared here instead of showing a broken Latest tab everywhere.
     */
    val exploreSections: List<MangaExploreSection>
        get() = listOf(MangaExploreSection(MangaExploreSection.POPULAR))

    /** Loads one declared [exploreSections] feed with independent pagination. */
    suspend fun fetchExploreManga(sectionId: String, page: Int): MangaSourceResult {
        require(sectionId == MangaExploreSection.POPULAR) {
            "Bu manga kaynağı '$sectionId' keşfet listesini desteklemiyor"
        }
        return fetchPopularManga(page)
    }

    /**
     * Arama terimi veya filtreye göre manga listesi döndürür.
     */
    suspend fun fetchSearchManga(page: Int, query: String): MangaSourceResult

    /**
     * Verilen [mangaUrl] için detaylı manga bilgilerini çeker.
     * Açıklama, yazar, durum ve kapak resmi bu çağrıyla doldurulur.
     */
    suspend fun fetchMangaDetails(mangaUrl: String): MangaDetails

    /**
     * Verilen manga URL'si için tüm bölüm listesini döndürür.
     * Sonuç en yeniden en eskiye sıralanmış olmalıdır.
     */
    suspend fun fetchChapterList(mangaUrl: String): List<MangaChapter>

    /**
     * Verilen [chapter] için bu bölümdeki tüm sayfa bilgilerini döndürür.
     * Döndürülen listenin her elemanı 0'dan indexlenir.
     */
    suspend fun fetchPageList(chapter: MangaChapter): List<MangaPage>

    /**
     * Bir sayfanın resim URL'si henüz bilinmiyorsa (boş ise),
     * ek bir HTTP isteği yaparak gerçek resim URL'sini çözer.
     * Çoğu kaynak için override gerekmeyebilir.
     */
    suspend fun fetchImageUrl(page: MangaPage): String = page.imageUrl ?: page.url

    /**
     * Bir sayfanın resmini indirir ve giriş akışını (InputStream) döndürür.
     * Bu sayede eklenti kendi özel client/headers yapılandırmasını kullanarak istek yapabilir.
     */
    suspend fun getImage(page: MangaPage): java.io.InputStream {
        val imageUrl = page.imageUrl ?: throw IllegalArgumentException("Image URL is null")
        val networkHelper = uy.kohesive.injekt.Injekt.get(eu.kanade.tachiyomi.network.NetworkHelper::class.java)
        val request = okhttp3.Request.Builder()
            .url(imageUrl)
            .header("Referer", baseUrl)
            .header("User-Agent", networkHelper.defaultUserAgentProvider())
            .build()
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val response = networkHelper.client.newCall(request).execute()
            if (!response.isSuccessful) {
                response.close()
                throw java.io.IOException("HTTP error ${response.code} for $imageUrl")
            }
            val body = response.body ?: throw java.io.IOException("Response body is null")
            object : java.io.FilterInputStream(body.byteStream()) {
                override fun close() {
                    super.close()
                    response.close()
                }
            }
        }
    }
}

/** Stable ID of a source-owned browse feed; titles are localized by the UI. */
data class MangaExploreSection(val id: String) {
    companion object {
        const val POPULAR = "popular"
        const val LATEST = "latest"
        const val NEWEST = "newest"
        const val RATING = "rating"
        const val ALPHABETICAL = "alphabetical"
        const val ADDED = "added"
        const val TRENDING_HOUR = "trending_hour"
        const val TRENDING_TODAY = "trending_today"
        const val TRENDING_WEEK = "trending_week"
        const val TRENDING_MONTH = "trending_month"
        const val TRENDING_YEAR = "trending_year"
    }
}

/**
 * Bir sorgu veya listeleme işleminin sonucunu taşır.
 */
data class MangaSourceResult(
    val mangas: List<MangaDetails>,
    val hasNextPage: Boolean
)

/**
 * F9: Captcha veya WebView doğrulaması gerektiren kaynaklarda fırlatılır.
 * MangaReaderViewModel / MangaPageLoader bu exception'ı yakalayıp
 * kullanıcıya anlamlı bir hata mesajı gösterebilir.
 */
class CaptchaRequiredException(
    val sourceName: String,
    override val message: String = "Captcha gerekli"
) : Exception(message)

/**
 * Dil bagimsiz cok dilli / global katalog kaynaklari (MangaDex, Comick, NamiComi vb.)
 */
val MangaSource.isGlobalCatalog: Boolean
    get() = lang.equals("all", ignoreCase = true) || lang.equals("multi", ignoreCase = true)

