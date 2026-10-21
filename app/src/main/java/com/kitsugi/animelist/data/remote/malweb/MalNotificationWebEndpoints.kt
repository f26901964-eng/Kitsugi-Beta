package com.kitsugi.animelist.data.remote.malweb

import android.content.Context

/**
 * MyAnimeList bildirimleri için **web uç noktaları** — `myanimelist.net/notification` ailesi.
 *
 * MAL'ın resmi API v2'si kişisel bildirim sunmaz. Tüm kişisel bildirim akışı yalnızca
 * *web oturumu* (WebView çerezi `MALHLOGSESSID` + `MALSESSIONID`) ile imzalanmış
 * `https://myanimelist.net/notification` sayfasındaki `window.MAL.notification`
 * JSON'u üzerinden okunur. Bu dosya o sayfanın ve türevlerinin **uç nokta tanımlarını**
 * tek bir yerde toplar; uygulamanın geri kalanı ([MalNotificationsClient] ve ViewModel)
 * bu uç noktalar üzerinden konuşur.
 *
 * ### Uç nokta haritası (ekran görüntüleri referansı `image-*.png`)
 *
 * ```
 * GET https://myanimelist.net/notification
 *   → window.MAL.notification { items: [], historyItems: [], pagination: {} }
 *   → 45 bildirimin ilk ~20'si (View more ile devamı gelir)
 *   Header: Cookie: MALHLOGSESSID=...; MALSESSIONID=...
 *
 * GET https://myanimelist.net/notification?offset=20
 *   → aynı sayfa, offset'li — MAL'ın "View more" XHR'i bu deseni kullanır
 *   → normalizeNotificationPageUrl() tarafından doğrulanır, yalnızca aynı-kaynak HTTPS'e izin verilir
 *
 * GET https://myanimelist.net/notification?category=related_anime
 *   → kategori filtresi (ör. New Related Anime 39, Now Airing 4)
 *   → Sunucu filtrelemeyi desteklemiyorsa istemci tarafında [MalNotificationCategory] ile filtrelenir
 *
 * WebView devamlılığı (/bildirim sayfasının kendi JS'i):
 *   GET https://myanimelist.net/notification (WebView içinde kaydır + "View more" tıkla)
 *   → MAL'ın kendi JS'i bir sonraki paketi XHR ile çeker ve window.MAL.notification güncellenir
 *   → MalNotificationsWebPager bu yolu otomatikleştirir
 *
 * Engellenen uç nokta (okunmamış işaretlemesini engellemek için):
 *   POST https://myanimelist.net/notification/api/check-items-as-read.json
 *   → shouldInterceptRequest() içinde 200 {result:false} ile nötrlenir; sayfa açmak bildirim tüketmez
 * ```
 *
 * #### Neden ayrı bir "endpoints" dosyası?
 *
 * Önceki sürümde bildirim mantığı ViewModel + Client + Pager arasında dağınık haldeydi ve
 * sayfalama `"View more"` düğmesini tanımadığı için 9 kayıttan sonra kesiliyordu. Bu dosya:
 *  1. Tüm URL oluşturma kurallarını tek bir yerde toplar (güvenli kaynak doğrulaması ile),
 *  2. Kategori filtrelerini [MalNotificationCategory] üzerinden tip güvenli kılar,
 *  3. Offset ve kategori parametrelerini şeffaf bir şekilde yönetir,
 *  4. Her fonksiyonun ne işe yaradığını ve hangi MAL sayfa yapısına dayandığını belgeler.
 */
internal object MalNotificationWebEndpoints {

    // ── Temel URL'ler ─────────────────────────────────────────────────────────

    const val NOTIFICATIONS_URL = "https://myanimelist.net/notification"
    const val NOTIFICATIONS_API_MARK_READ = "https://myanimelist.net/notification/api/check-items-as-read.json"

    // MAL'ın View more XHR'inin kullanabileceği alternatif API yolları (keşif amaçlı saklanır;
    // doğrudan çağrı hâlâ Cookie + Referer gerektirir ve window.MAL.notification parse edilir).
    const val NOTIFICATION_HISTORY_API_CANDIDATE = "https://myanimelist.net/notification/history"
    const val NOTIFICATION_PANEL_API_CANDIDATE = "https://myanimelist.net/includes/notification-time-line.php"

    /** Sayfa başına beklenen kayıt sayısı (MAL tipik olarak 20 döndürür). */
    const val PAGE_SIZE = 20

    // ── URL Oluşturucular ────────────────────────────────────────────────────

    /**
     * Temel bildirim sayfası URL'si.
     *
     * @param offset 0 ise parametre eklenmez; aksi halde `?offset=N` eklenir.
     * @param category null ise filtresiz; aksi halde `?category=...` veya tek parametre olacaksa `?category=...&offset=N`.
     */
    fun buildNotificationUrl(
        offset: Int = 0,
        category: MalNotificationCategory? = null
    ): String {
        if (offset == 0 && category == null) return NOTIFICATIONS_URL
        val params = mutableListOf<String>()
        category?.webParam?.let { params += "category=${it}" }
        if (offset > 0) params += "offset=$offset"
        return if (params.isEmpty()) NOTIFICATIONS_URL else "$NOTIFICATIONS_URL?${params.joinToString("&")}"
    }

    /**
     * Eski `?page=N` varyantı — bazı MAL sayfaları offset yerine page kullanır.
     * Güvenli URL normalizasyonu her iki varyantı da kabul eder.
     */
    fun buildNotificationUrlWithPage(
        page: Int = 1,
        category: MalNotificationCategory? = null
    ): String {
        if (page <= 1 && category == null) return NOTIFICATIONS_URL
        val offset = (page - 1) * PAGE_SIZE
        return buildNotificationUrl(offset = offset, category = category)
    }

    /** Kategori filtresiz, offset'li URL — ViewModel'in sayfalama döngüsü için. */
    fun pageUrl(page: Int): String = buildNotificationUrlWithPage(page = page)

    // ── Yüksek Seviye Yardımcılar ───────────────────────────────────────────

    /**
     * Belirli bir kategorideki bildirimler için filtre uygular.
     *
     * Sunucu kategoriyi görmezden gelirse (aynı tüm listeyi döndürürse) istemci tarafında
     * [MalNotificationCategory.typeIdentifiers] ile filtrelenir — böylece
     * "New Related Anime (39)" gibi sol menü sayıları doğru şekilde yansıtılır.
     */
    fun filterByCategory(
        items: List<MalNotificationsClient.Notification>,
        category: MalNotificationCategory
    ): List<MalNotificationsClient.Notification> {
        if (category == MalNotificationCategory.ALL) return items
        return items.filter { notif ->
            category.typeIdentifiers.any { it.equals(notif.typeIdentifier, ignoreCase = true) }
        }
    }

    /**
     * Tüm kategoriler için sayım üretir — Bildirimler ekranındaki sol menü
     * (`All 45, Related 39, Airing 4 …`) referans alınarak rozet/filtre sayıları gösterilir.
     */
    fun countsByCategory(
        items: List<MalNotificationsClient.Notification>
    ): Map<MalNotificationCategory, Int> {
        val map = mutableMapOf<MalNotificationCategory, Int>()
        MalNotificationCategory.entries.forEach { cat -> map[cat] = 0 }
        map[MalNotificationCategory.ALL] = items.size
        for (notif in items) {
            val cat = MalNotificationCategory.fromTypeIdentifier(notif.typeIdentifier)
            if (cat != MalNotificationCategory.ALL) {
                map[cat] = (map[cat] ?: 0) + 1
            }
        }
        return map
    }

    /** View more düğmesinin metni — MalNotificationsWebPager içinde aranır. */
    val VIEW_MORE_PATTERNS = listOf(
        "view more",
        "load more",
        "show more",
        "older",
        "daha fazla",
        "daha eski",
        "devamını gör"
    )

    // ── Teşhis ────────────────────────────────────────────────────────────────

    fun describeEndpoints(): List<String> = listOf(
        "GET $NOTIFICATIONS_URL (window.MAL.notification — items + historyItems)",
        "GET $NOTIFICATIONS_URL?offset={N} (sayfalama — View more)",
        "GET $NOTIFICATIONS_URL?category={type} (kategori filtresi — ${MalNotificationCategory.entries.mapNotNull { it.webParam }.joinToString()})",
        "WebView: $NOTIFICATIONS_URL (kaydır + View more tıkla → window.MAL.notification güncellenir)",
        "BLOCK $NOTIFICATIONS_API_MARK_READ (okundu işaretlemesi engellenir)"
    )

    // ── Doğrudan Fetch (istemciler için kolay yol) ─────────────────────────

    /**
     * Tüm bildirimleri tek seferde çeker (ilk sayfa).
     * Doğrulama ve URL güvenliği [MalNotificationsClient] içindedir.
     */
    suspend fun fetchAll(cookieHeader: String): MalNotificationsClient.Feed =
        MalNotificationsClient.fetch(cookieHeader)

    /**
     * Belirli bir kategorideki ilk sayfayı çeker.
     * Sunucu tarafı filtreleme desteklenmiyorsa istemci tarafında filtrelenir.
     */
    suspend fun fetchCategory(
        cookieHeader: String,
        category: MalNotificationCategory
    ): MalNotificationsClient.Feed {
        if (category == MalNotificationCategory.ALL) return MalNotificationsClient.fetch(cookieHeader)
        // Önce kategorili URL'yi dene; başarısız olursa tümünü çek ve filtrele.
        val categoryUrl = buildNotificationUrl(category = category)
        return try {
            MalNotificationsClient.fetchWithUrl(cookieHeader, categoryUrl, page = 1)
        } catch (_: Exception) {
            val all = MalNotificationsClient.fetch(cookieHeader)
            val filtered = filterByCategory(all.items, category)
            all.copy(
                items = filtered,
                hasNextPage = filtered.isNotEmpty() && all.hasNextPage
            )
        }
    }

    /**
     * Eski bildirimler için bir sonraki sayfayı çeker (offset tabanlı).
     * Önce `nextPageUrl` denenir, sonra offset URL, son olarak WebView.
     */
    suspend fun fetchMore(
        context: Context,
        cookieHeader: String,
        page: Int,
        nextPageUrl: String?,
        knownIds: Set<String>,
        category: MalNotificationCategory? = null,
        batchSize: Int? = null
    ): MalNotificationsClient.Feed? =
        MalNotificationsClient.fetchMore(
            context = context,
            cookieHeader = cookieHeader,
            page = page,
            nextPageUrl = nextPageUrl,
            knownIds = knownIds,
            category = category,
            batchSize = batchSize
        )
}
