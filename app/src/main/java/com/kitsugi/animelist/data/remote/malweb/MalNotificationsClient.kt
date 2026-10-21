package com.kitsugi.animelist.data.remote.malweb

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

import android.content.Context
import com.kitsugi.animelist.core.network.KitsugiHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import okhttp3.Request
import org.jsoup.Jsoup

/**
 * MyAnimeList's website-only notification feed.
 *
 * MAL API v2 does not expose personal notifications. The signed-in website page embeds the
 * account feed in `window.MAL.notification`; this client reads that JSON with the user's MAL
 * WebView session cookie and returns data for the native notification UI. The OAuth API token
 * is deliberately not sent here: it is not a MAL website session.
 */
internal object MalNotificationsClient {
    const val NOTIFICATIONS_URL = "https://myanimelist.net/notification"
    private const val MAL_HOME_URL = "https://myanimelist.net/"
    /** MAL tipik olarak sayfa başına 20 bildirim döndürür — 45 toplam (All) buna uyar. */
    const val PAGE_SIZE = 20
    private val json = Json { ignoreUnknownKeys = true }

    /** `window.MAL.notification = { ... }` ataması. */
    private val NOTIFICATION_MARKER = Regex("""window\.MAL\.notification\s*=""", RegexOption.IGNORE_CASE)

    /**
     * Profil bağlantısı. Başındaki MAL kök adresi isteğe bağlıdır; sayfa içi
     * `/profile/ad` ve tam `https://myanimelist.net/profile/ad` biçimlerinin ikisi de eşleşir.
     * Kullanıcı adından sonra bir ayırıcı zorunludur ki `/profile/ad/friends` alt yolları
     * adın içine kaçmasın.
     */
    private val PROFILE_LINK = Regex(
        """(?:https?://(?:www\.)?myanimelist\.net)?/profile/([A-Za-z0-9_-]{2,32})(?=[/?#"'\s<>)])""",
        RegexOption.IGNORE_CASE
    )

    /** Oturum sahibinin avatar bağlantısı (sayfa başlığı). */
    private val HEADER_PROFILE_TAG = Regex("""<a\b[^>]*header-profile[^>]*>""", RegexOption.IGNORE_CASE)

    /**
     * MAL'ın manga evrenindeki medya türleri. Anime türlerinin (TV/OVA/ONA/Movie/
     * Special/PV) hiçbiri bu kelimeleri içermez.
     */
    private val MANGA_MEDIA_TYPES = listOf(
        "manga", "novel", "manhwa", "manhua", "one-shot", "oneshot", "doujin"
    )

    /** Profil sayfası olmayan, ayrılmış yollar. */
    private val RESERVED_PROFILE_NAMES = setOf(
        "login", "logout", "register", "signup", "index", "edit", "settings", "notification", "profile"
    )
    private val noRedirectClient by lazy {
        KitsugiHttpClient.jikanClient.newBuilder()
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
    }

    /**
     * @param items Bildirim kartları.
     * @param sessionUsername Sayfayı döndüren web oturumunun sahibi olan MAL kullanıcı adı.
     *   Bağlı OAuth hesabıyla karşılaştırma için kullanılır; sayfa yapısı nedeniyle
     *   okunamazsa `null` döner (bu durumda eşleşme "doğrulanamadı" sayılır).
     */
    data class Feed(
        val items: List<Notification>,
        val sessionUsername: String? = null,
        /** Okunan akış sayfası (1 tabanlı). */
        val page: Int = 1,
        /**
         * Daha eski bildirimler için devam denemesi yapılabilir mi? MAL bu akış için
         * yayımlanmış bir sözleşme sunmuyor; açık bir next URL veya web sayfasının kendi
         * istemcisiyle aşağı kaydırma sonucu yeni kayıt bulunması beklenir.
         */
        val hasNextPage: Boolean = false,
        /** MAL HTML/JSON açık bir next URL verdiyse güvenli aynı-kaynak bağlantısı. */
        val nextPageUrl: String? = null
    )

    data class Notification(
        val id: String,
        val typeIdentifier: String,
        val categoryName: String?,
        val createdAt: Long?,
        val createdAtForDisplay: String?,
        val isRead: Boolean,
        val isDeleted: Boolean,
        val isApproved: Boolean,
        val url: String?,
        val animeTitle: String?,
        val animeMediaType: String?,
        val animeUrl: String?,
        val animeImageUrl: String? = null,
        val senderName: String?,
        val senderProfileUrl: String?,
        val pageUrl: String?,
        val pageTitle: String?,
        val commentUserName: String?,
        val commentUserProfileUrl: String?,
        val commentUserImageUrl: String?,
        val text: String?,
        val date: String?,
        val friendName: String?,
        val friendProfileUrl: String?,
        val friendImageUrl: String?,
        val quoteUserName: String?,
        val quoteUserProfileUrl: String?,
        val postedUserName: String?,
        val postedUserProfileUrl: String?,
        val topicUrl: String?,
        val topicTitle: String?,
        val message: String?,
        val animeItems: List<RelatedAnime>
    ) {
        val targetUrl: String?
            get() = sequenceOf(
                url, pageUrl, topicUrl, friendProfileUrl, commentUserProfileUrl, senderProfileUrl,
                quoteUserProfileUrl, postedUserProfileUrl, animeUrl
            )
                .filterNotNull()
                .mapNotNull { MalNotificationsClient.normalizeMalUrl(it) }
                .firstOrNull()

        val imageUrl: String?
            get() = sequenceOf(friendImageUrl, commentUserImageUrl, animeImageUrl)
                .filterNotNull()
                .mapNotNull { MalNotificationsClient.normalizeMalUrl(it) }
                .firstOrNull()

        val mediaId: Int?
            get() = sequenceOf(animeUrl, url, animeItems.firstOrNull()?.url)
                .filterNotNull()
                .mapNotNull { MalNotificationsClient.normalizeMalUrl(it) }
                .mapNotNull { MalNotificationsClient.extractMediaId(it) }
                .firstOrNull()

        /**
         * Bildirimin işaret ettiği medyanın GERÇEK adı (kategori adı değil).
         *
         * `related_anime_add` / `on_air` gibi bildirimlerde başlık MAL'ın kategori adıdır
         * ("New Related Anime"); detay sayfası bu metinle açıldığında ekranda anime adı
         * yerine kategori adı kalıyordu. Detay yönlendirmesi bu alanı kullanır.
         */
        val mediaTitle: String?
            get() = animeTitle?.trim()?.takeIf { it.isNotEmpty() }
                ?: animeItems.firstOrNull()?.title?.trim()?.takeIf { it.isNotEmpty() }

        /**
         * Bildirimin medya türü: `"anime"` / `"manga"` / `null`.
         *
         * MAL medya türleri iki evrene ayrılır: `TV, OVA, ONA, Movie, Special, PV, Music`
         * (anime) ve `Manga, Novel, Light Novel, Manhwa, Manhua, One-shot, Doujin` (manga).
         * Eskiden yalnızca `"manga"` metni arandığı için **Novel/Manhwa** kayıtları anime
         * sayılıyor, manga kimliği anime aramasına veriliyor ve detay sayfası aynı sayıya
         * sahip bambaşka bir animeyi açabiliyordu.
         */
        val mediaType: String?
            get() {
                val declared = animeMediaType?.trim()?.lowercase().orEmpty()
                val urlLooksManga = sequenceOf(animeUrl, url, animeItems.firstOrNull()?.url)
                    .filterNotNull()
                    .any { it.contains("/manga/") }
                return when {
                    declared.isEmpty() -> when {
                        urlLooksManga -> "manga"
                        mediaId != null -> "anime"
                        else -> null
                    }
                    MANGA_MEDIA_TYPES.any { declared.contains(it) } -> "manga"
                    else -> "anime"
                }
            }

        fun displayTitle(): String = categoryName?.takeIf(String::isNotBlank)
            ?: typeIdentifier.replace('_', ' ').replaceFirstChar { it.uppercase() }

        fun displayBody(): String {
            val explicit = sequenceOf(message, text)
                .filterNotNull()
                .map { it.trim() }
                .firstOrNull { it.isNotEmpty() }
            if (explicit != null) return explicit

            val person = sequenceOf(friendName, commentUserName, quoteUserName, postedUserName, senderName)
                .filterNotNull()
                .firstOrNull(String::isNotBlank)
            val anime = animeTitle?.takeIf(String::isNotBlank)
                ?: animeItems.firstOrNull()?.title?.takeIf(String::isNotBlank)
            val topic = topicTitle?.takeIf(String::isNotBlank) ?: pageTitle?.takeIf(String::isNotBlank)
            val pieces = when (typeIdentifier) {
                "friend_request" -> listOfNotNull(person)
                "friend_request_accept", "friend_request_deny" -> listOfNotNull(person)
                "related_anime_add" -> listOfNotNull(anime, animeMediaType)
                "on_air" -> listOfNotNull(date, animeItems.joinToString { it.title }.takeIf(String::isNotBlank))
                "profile_comment" -> listOfNotNull(person, topic)
                "forum_quote", "watched_topic_message" -> listOfNotNull(person, topic)
                "user_mention_in_club_comment", "user_mention_in_forum_message" -> listOfNotNull(person, topic)
                "club_mass_message_in_forum", "blog_comment" -> listOfNotNull(person, topic)
                else -> listOfNotNull(person, topic, anime, date)
            }
            return pieces.joinToString(" • ").ifBlank { typeIdentifier.replace('_', ' ') }
        }

        fun normalizedTargetUrl(): String? = targetUrl
    }

    data class RelatedAnime(val title: String, val url: String?, val imageUrl: String? = null)

    suspend fun fetch(cookieHeader: String): Feed = withContext(Dispatchers.IO) {
        // Çerez adına göre karar verilmez: MAL'ın hangi çerezle "giriş yapılmış" saydığını
        // yalnızca MAL bilir. İstek yapılır, giriş sayfasına düşülürse oturum istenir.
        if (cookieHeader.isBlank()) {
            throw MalWebSessionRequiredException("MAL web oturumu gerekli")
        }
        fetchPage(cookieHeader, NOTIFICATIONS_URL, page = 1)
    }

    /**
     * Belirli bir URL ile sayfa çeker — kategori filtresi veya offset'li sayfalama için.
     * URL mutlaka [normalizeNotificationPageUrl] tarafından doğrulanmalıdır.
     */
    suspend fun fetchWithUrl(cookieHeader: String, url: String, page: Int = 1): Feed = withContext(Dispatchers.IO) {
        if (cookieHeader.isBlank()) throw MalWebSessionRequiredException("MAL web oturumu gerekli")
        val safeUrl = normalizeNotificationPageUrl(url)
            ?: throw MalNotificationsFetchException("MAL bildirim URL'si geçersiz: $url")
        fetchPage(cookieHeader, safeUrl, page)
    }

    /**
     * Loads the next MAL website batch without inventing an API-v2 route or page query.
     *
     * The official MAL v2 API has no personal-notification resource. If the page exposes an
     * explicit same-origin next URL, it is tried first and trusted only when it returns unseen
     * IDs. Otherwise the same signed-in `/notification` page is opened in a short-lived WebView;
     * MAL's own JavaScript is allowed to request the next batch as its notification pane scrolls.
     * No OAuth bearer token is sent to the website; the shared WebView cookie jar supplies the
     * website session.
     */
    /**
     * Uç nokta geçmişi: kategori parametresi veya offset'li URL için aşırı yük.
     * Geriye dönük uyumlu — mevcut ViewModel çağrıları çalışmaya devam eder.
     */
    suspend fun fetchMore(
        context: Context,
        cookieHeader: String,
        page: Int,
        nextPageUrl: String? = null,
        knownIds: Set<String>
    ): Feed? = fetchMore(context, cookieHeader, page, nextPageUrl, knownIds, category = null)

    suspend fun fetchMore(
        context: Context,
        cookieHeader: String,
        page: Int,
        nextPageUrl: String? = null,
        knownIds: Set<String>,
        category: MalNotificationCategory? = null
    ): Feed? = withContext(Dispatchers.IO) {
        if (cookieHeader.isBlank()) return@withContext null
        var sessionError: MalWebSessionRequiredException? = null
        val explicitNextUrl = normalizeNotificationPageUrl(nextPageUrl)
        if (explicitNextUrl != null) {
            val feed = try {
                fetchPage(cookieHeader, explicitNextUrl, page)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: MalWebSessionRequiredException) {
                sessionError = e
                null
            } catch (_: Exception) {
                null
            }
            if (feed?.items?.any { it.id !in knownIds } == true) return@withContext feed
        }

        // Tahmin edilebilir offset denemesi — MAL'ın "View more" XHR'i offset parametresi kullanır.
        // Yalnızca güvenli aynı-kaynak URL üretilir; başarısız olursa sessizce WebView'e düşer.
        val offsetGuessUrl = runCatching {
            val offset = (page - 1) * PAGE_SIZE
            val catParam = category?.webParam?.let { "category=$it&" } ?: ""
            normalizeNotificationPageUrl("$NOTIFICATIONS_URL?${catParam}offset=$offset")
        }.getOrNull()
        if (offsetGuessUrl != null) {
            val offsetFeed = try {
                fetchPage(cookieHeader, offsetGuessUrl, page)
            } catch (_: Exception) { null }
            if (offsetFeed?.items?.any { it.id !in knownIds } == true) return@withContext offsetFeed
        }

        // No guessed query parameters beyond offset: let the first-party frontend run its actual continuation.
        val browserFeed = MalNotificationsWebPager.fetch(context, page)
        if (browserFeed.items.any { it.id !in knownIds }) return@withContext browserFeed
        if (browserFeed.hasNextPage) {
            throw MalNotificationsFetchException(
                "MAL bildirim sayfası devam olabileceğini bildirdi ancak yeni kayıt yüklemedi"
            )
        }
        sessionError?.let { throw it }
        null
    }

    /** Tek bir adresi indirip ayrıştırır (yalnızca MAL'ın kendi adreslerine yönlendirme izni var). */
    private suspend fun fetchPage(cookieHeader: String, startUrl: String, page: Int): Feed {
        val safeCookie = cookieHeader.replace("\r", "").replace("\n", "").trim()
        val requestCookie = if (safeCookie.split(';').none {
                it.trim().substringBefore('=').equals("anime_update_advanced", ignoreCase = true)
            }
        ) {
            "$safeCookie; anime_update_advanced=0"
        } else safeCookie
        var currentUrl = startUrl

        // Never let an automatically followed redirect carry a website session cookie to an
        // unrelated host. Follow only HTTPS redirects back to MAL's own canonical host(s).
        for (redirectCount in 0..4) {
            val request = Request.Builder()
                .url(currentUrl)
                .header("Cookie", requestCookie)
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "tr-TR,tr;q=0.9,en-US;q=0.8,en;q=0.7")
                .header("Referer", if (page <= 1) MAL_HOME_URL else NOTIFICATIONS_URL)
                .header("Cache-Control", "no-cache")
                .build()

            val redirectUrl = noRedirectClient.newCall(request).execute().use { response ->
                if (response.code == 401 || response.code == 403) {
                    throw MalWebSessionRequiredException("MAL web oturumu geçersiz")
                }
                if (response.code in 300..399) {
                    val location = response.header("Location")
                        ?: throw MalNotificationsFetchException("MAL bildirim yönlendirmesi bulunamadı")
                    val target = response.request.url.resolve(location)
                        ?: throw MalNotificationsFetchException("MAL bildirim yönlendirmesi geçersiz")
                    val isFirstParty = target.host == "myanimelist.net" || target.host == "www.myanimelist.net"
                    if (target.scheme != "https" || !isFirstParty || target.username.isNotEmpty() || target.password.isNotEmpty()) {
                        throw MalNotificationsFetchException("MAL bildirim isteği güvenilmeyen adrese yönlendirildi")
                    }
                    if (target.encodedPath.contains("login", ignoreCase = true)) {
                        throw MalWebSessionRequiredException("MAL web oturumu sona ermiş")
                    }
                    target.toString()
                } else {
                    if (!response.isSuccessful) {
                        throw MalNotificationsFetchException("MAL bildirim isteği HTTP ${response.code}")
                    }
                    val body = response.body?.string().orEmpty()
                    parseHtml(body, page)?.let { return it }
                    // Bazı durumlarda MAL giriş formunu 200 ile döner (yönlendirme yok).
                    if (looksSignedOutPage(body)) {
                        throw MalWebSessionRequiredException("MAL web oturumu sona ermiş")
                    }
                    throw MalNotificationsParseException(
                        "MAL bildirim verisi sayfada bulunamadı (doğrulama veya sayfa yapısı değişmiş olabilir)"
                    )
                }
            }
            currentUrl = redirectUrl
        }
        throw MalNotificationsFetchException("MAL bildirim isteği çok fazla yönlendirildi")
    }

    /** Parses MAL's embedded notification assignment without evaluating page JavaScript. */
    internal fun parseHtml(html: String, page: Int = 1): Feed? {
        val marker = NOTIFICATION_MARKER.find(html) ?: return null
        val objectStart = html.indexOf('{', marker.range.last + 1)
        if (objectStart < 0) return null
        val objectEnd = findJsonObjectEnd(html, objectStart) ?: return null
        val root = runCatching { json.parseToJsonElement(html.substring(objectStart, objectEnd + 1)).jsonObject }
            .getOrNull() ?: return null
        return parseRoot(
            root = root,
            page = page,
            sessionUsername = parseSessionUsername(html),
            nextPageUrlHint = notificationNextPageUrl(root) ?: nextPageUrlFromHtml(html)
        )
    }

    /** WebView bridge path: parse the object after MAL's own page JavaScript has run. */
    internal fun parseNotificationPayload(
        payload: String,
        page: Int,
        sessionUsername: String?,
        hasNextPageOverride: Boolean? = null
    ): Feed? {
        val root = runCatching { json.parseToJsonElement(payload).jsonObject }.getOrNull() ?: return null
        return parseRoot(
            root = root,
            page = page,
            sessionUsername = sessionUsername,
            browserHasNextOverride = hasNextPageOverride,
            nextPageUrlHint = notificationNextPageUrl(root)
        )
    }

    private fun parseRoot(
        root: JsonObject,
        page: Int,
        sessionUsername: String?,
        browserHasNextOverride: Boolean? = null,
        nextPageUrlHint: String? = null
    ): Feed? {
        val rawItems = root["items"] as? JsonArray
        val rawHistory = root["historyItems"] as? JsonArray
        // MAL may send only historyItems for an all-read notification history.
        if (rawItems == null && rawHistory == null) return null

        val parsed = rawItems.orEmpty().mapNotNull { it.asObjectOrNull()?.toNotification() } +
            rawHistory.orEmpty().mapNotNull { it.asObjectOrNull()?.toNotification() }
        val items = parsed.distinctBy { it.id }
        val pagination = root["pagination"] as? JsonObject
        val explicitHasNext = root.boolean("hasNextPage")
            ?: root.boolean("has_next_page")
            ?: root.boolean("hasMore")
            ?: root.boolean("has_more")
            ?: pagination?.boolean("hasNextPage")
            ?: pagination?.boolean("has_next_page")
        val embeddedNextUrl = nextPageUrlHint ?: notificationNextPageUrl(root)

        return Feed(
            items = items,
            sessionUsername = sessionUsername,
            page = page,
            // Without a server contract, an item-bearing feed is a continuation hint only;
            // the caller still requires unseen IDs before appending or advancing the page.
            hasNextPage = explicitHasNext ?: (browserHasNextOverride ?: (embeddedNextUrl != null || items.isNotEmpty())),
            nextPageUrl = embeddedNextUrl
        )
    }

    private fun notificationNextPageUrl(root: JsonObject): String? {
        val pagination = root["pagination"] as? JsonObject
        return sequenceOf(
            root.string("nextPageUrl"),
            root.string("next_page_url"),
            root.string("nextUrl"),
            root.string("next_url"),
            pagination?.string("nextPageUrl"),
            pagination?.string("next_page_url"),
            pagination?.string("nextUrl"),
            pagination?.string("next_url"),
            pagination?.string("next")
        )
            .filterNotNull()
            .filter(String::isNotBlank)
            .mapNotNull(::normalizeNotificationPageUrl)
            .firstOrNull()
    }

    private fun nextPageUrlFromHtml(html: String): String? = runCatching {
        val document = Jsoup.parse(html)
        val relNext = document.select("a[rel], link[rel]")
            .firstOrNull { element -> element.attr("rel").split(' ').any { it.equals("next", true) } }
            ?.attr("href")
        val dataNext = document.selectFirst("[data-next-url]")?.attr("data-next-url")
        normalizeNotificationPageUrl(relNext ?: dataNext)
    }.getOrNull()

    internal fun normalizeNotificationPageUrl(raw: String?): String? = runCatching {
        if (raw.isNullOrBlank()) return@runCatching null
        val base = NOTIFICATIONS_URL.toHttpUrlOrNull() ?: return@runCatching null
        val trimmed = raw.trim()
        // Reject explicit username/password authority or non-default port before resolution.
        if (trimmed.contains('@') && trimmed.substringBefore('?').substringBefore('#').contains("://")) {
            val rawCheck = java.net.URI(trimmed)
            if (rawCheck.userInfo != null) return@runCatching null
        }
        val target = base.resolve(trimmed) ?: return@runCatching null
        val host = target.host.lowercase()
        val path = target.encodedPath
        val isNotificationPath = path.equals("/notification", ignoreCase = true) ||
            path.startsWith("/notification/", ignoreCase = true)
        if (!target.scheme.equals("https", ignoreCase = true) ||
            target.username.isNotEmpty() || target.password.isNotEmpty() ||
            target.port != 443 ||
            (host != "myanimelist.net" && host != "www.myanimelist.net") ||
            !isNotificationPath
        ) return@runCatching null
        target.toString()
    }.getOrNull()

    /**
     * Reads the signed-in account's username from the page chrome (header profile link).
     *
     * Two passes are used because MAL's markup is not stable:
     *  1. The header profile anchor (`class="...header-profile..."`) — the account that owns
     *     the session.
     *  2. Otherwise the most frequently linked profile on the page, which is the session
     *     owner for the notification page (the header repeats it; other people appear once).
     *
     * The `window.MAL.notification` JSON is excluded before scanning so that senders, friends
     * and commenters inside the feed can never be mistaken for the session owner.
     */
    internal fun parseSessionUsername(html: String): String? {
        val scannable = withoutNotificationJson(html)
        return headerProfileUsername(scannable) ?: mostFrequentProfileUsername(scannable)
    }

    /** Parse the first-party owner profile URL returned by the WebView bridge. */
    internal fun parseSessionUsernameFromProfileUrl(raw: String?): String? {
        val safeUrl = raw?.let(::normalizeMalUrl) ?: return null
        val uri = runCatching { java.net.URI(safeUrl) }.getOrNull() ?: return null
        val host = uri.host?.lowercase()
        if (host != "myanimelist.net" && host != "www.myanimelist.net") return null
        val username = Regex("""^/profile/([A-Za-z0-9_-]{2,32})(?:/|$)""", RegexOption.IGNORE_CASE)
            .find(uri.path.orEmpty())?.groupValues?.getOrNull(1)
            ?: return null
        return username.takeIf(::isPlausibleUsername)
    }

    private fun withoutNotificationJson(html: String): String {
        val marker = NOTIFICATION_MARKER.find(html) ?: return html
        val start = html.indexOf('{', marker.range.last + 1)
        if (start < 0) return html
        val end = findJsonObjectEnd(html, start) ?: return html
        return html.removeRange(start, end + 1)
    }

    private fun headerProfileUsername(html: String): String? {
        for (tag in HEADER_PROFILE_TAG.findAll(html)) {
            val name = PROFILE_LINK.find(tag.value)?.groupValues?.getOrNull(1)
            if (name != null && isPlausibleUsername(name)) return name
        }
        return null
    }

    private fun mostFrequentProfileUsername(html: String): String? {
        val counts = LinkedHashMap<String, Int>()
        val display = HashMap<String, String>()
        for (match in PROFILE_LINK.findAll(html)) {
            val raw = match.groupValues[1]
            if (!isPlausibleUsername(raw)) continue
            val key = raw.lowercase()
            counts[key] = (counts[key] ?: 0) + 1
            if (!display.containsKey(key)) display[key] = raw
        }
        // LinkedHashMap sırası korur: eşit sayıda ilk görülen ad kazanır.
        var bestKey: String? = null
        var bestCount = 0
        for ((key, count) in counts) {
            if (count > bestCount) {
                bestKey = key
                bestCount = count
            }
        }
        return bestKey?.let { display[it] }
    }

    private fun isPlausibleUsername(value: String): Boolean {
        if (value.length < 2 || value.length > 32) return false
        if (value.all { it.isDigit() }) return false
        return value.lowercase() !in RESERVED_PROFILE_NAMES
    }

    /**
     * Bildirim işaretçisi bulunamayan sayfa aslında MAL giriş formu mu?
     * Bu durumda kullanıcıdan bir kez web oturumu istenir; sayfa yapısı hatası demek değildir.
     */
    internal fun looksSignedOutPage(html: String): Boolean {
        if (html.isBlank()) return false
        return html.contains("action=\"/login.php", ignoreCase = true) ||
            html.contains("name=\"loginForm\"", ignoreCase = true) ||
            html.contains("id=\"loginForm\"", ignoreCase = true) ||
            html.contains("name=\"csrf_token\"", ignoreCase = true) &&
            html.contains("login.php", ignoreCase = true)
    }

    private fun findJsonObjectEnd(text: String, start: Int): Int? {
        var depth = 0
        var inString = false
        var escaped = false
        for (index in start until text.length) {
            val char = text[index]
            if (inString) {
                when {
                    escaped -> escaped = false
                    char == '\\' -> escaped = true
                    char == '"' -> inString = false
                }
                continue
            }
            when (char) {
                '"' -> inString = true
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return index
                    if (depth < 0) return null
                }
            }
        }
        return null
    }

    private fun JsonElement.asObjectOrNull(): JsonObject? = this as? JsonObject

    private fun JsonObject.toNotification(): Notification? {
        val id = string("id")?.takeIf(String::isNotBlank) ?: return null
        val type = string("typeIdentifier")?.takeIf(String::isNotBlank) ?: "unknown"
        val anime = this["anime"]?.asObjectOrNull()
        val animeItems = (this["animes"] as? JsonArray).orEmpty().mapNotNull { entry ->
            val item = entry.asObjectOrNull() ?: return@mapNotNull null
            val title = item.string("title")?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            RelatedAnime(title, item.string("url"), item.string("imageUrl") ?: item.string("image_url") ?: item.string("picture"))
        }
        if (type.equals("on_air", ignoreCase = true) && animeItems.isEmpty()) return null
        // MAL bildirim JSON'unda anime görseli farklı anahtarlarda gelebilir: imageUrl / image_url / images.jpg.image_url
        val animeImage = anime?.let {
            it.string("imageUrl")
                ?: it.string("image_url")
                ?: it.string("picture")
                ?: (it["images"] as? JsonObject)?.let { imgs ->
                    (imgs["jpg"] as? JsonObject)?.string("image_url")
                        ?: (imgs["webp"] as? JsonObject)?.string("image_url")
                }
                ?: animeItems.firstOrNull()?.imageUrl
        }
        return Notification(
            id = id,
            typeIdentifier = type,
            categoryName = string("categoryName"),
            createdAt = long("createdAt"),
            createdAtForDisplay = string("createdAtForDisplay"),
            isRead = boolean("isRead") ?: false,
            isDeleted = boolean("isDeleted") ?: false,
            isApproved = boolean("isApproved") ?: true,
            url = string("url"),
            animeTitle = anime?.string("title"),
            animeMediaType = anime?.string("mediaType"),
            animeUrl = anime?.string("url"),
            animeImageUrl = animeImage,
            senderName = string("senderName"),
            senderProfileUrl = string("senderProfileUrl"),
            pageUrl = string("pageUrl"),
            pageTitle = string("pageTitle"),
            commentUserName = string("commentUserName"),
            commentUserProfileUrl = string("commentUserProfileUrl"),
            commentUserImageUrl = string("commentUserImageUrl"),
            text = string("text"),
            date = string("date"),
            friendName = string("friendName"),
            friendProfileUrl = string("friendProfileUrl"),
            friendImageUrl = string("friendImageUrl"),
            quoteUserName = string("quoteUserName"),
            quoteUserProfileUrl = string("quoteUserProfileUrl"),
            postedUserName = string("postedUserName"),
            postedUserProfileUrl = string("postedUserProfileUrl"),
            topicUrl = string("topicUrl"),
            topicTitle = string("topicTitle"),
            message = string("message"),
            animeItems = animeItems
        )
    }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.takeUnless { it == "null" }

    private fun JsonObject.long(key: String): Long? = string(key)?.toLongOrNull()

    private fun JsonObject.boolean(key: String): Boolean? = when (string(key)?.lowercase()) {
        "true", "1" -> true
        "false", "0" -> false
        else -> null
    }

    internal fun extractMediaId(value: String): Int? = Regex("""/(?:anime|manga)/(\d+)""")
        .find(value)?.groupValues?.getOrNull(1)?.toIntOrNull()

    internal fun normalizeMalUrl(raw: String): String? = runCatching {
        val uri = java.net.URI(raw.trim())
        val absolute = when {
            uri.isAbsolute -> uri
            raw.startsWith("//") -> java.net.URI("https:$raw")
            raw.startsWith("/") -> java.net.URI("https://myanimelist.net$raw")
            else -> return@runCatching null
        }
        val host = absolute.host?.lowercase() ?: return@runCatching null
        if (!absolute.scheme.equals("https", ignoreCase = true) ||
            absolute.userInfo != null || (absolute.port != -1 && absolute.port != 443) ||
            (host != "myanimelist.net" && !host.endsWith(".myanimelist.net"))
        ) {
            return@runCatching null
        }
        val path = absolute.path.orEmpty()
        if (path.equals("/notification", ignoreCase = true) ||
            path.equals("/notification.php", ignoreCase = true)
        ) {
            return@runCatching null
        }
        absolute.toString()
    }.getOrNull()
}

internal class MalWebSessionRequiredException(message: String) : Exception(message)
internal class MalNotificationsFetchException(message: String) : Exception(message)
internal class MalNotificationsParseException(message: String) : Exception(message)
