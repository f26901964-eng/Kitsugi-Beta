package com.kitsugi.animelist.data.remote.malweb

import android.content.Context
import com.kitsugi.animelist.core.network.KitsugiHttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * MyAnimeList's website-only notification feed.
 *
 * MAL API v2 does not expose personal notifications. The signed-in website page embeds the
 * initial batch in `window.MAL.notification`, renders `.notification-item` rows plus
 * `.notificaiton-category-nav` counters/links in `#content`, and loads older batches via its
 * own pagination / "View more" mechanism.
 */
internal object MalNotificationsClient {
    const val NOTIFICATIONS_URL = "https://myanimelist.net/notification"
    const val WEB_ID_PREFIX = "mal_web_"
    private const val MAL_HOME_URL = "https://myanimelist.net/"
    const val PAGE_SIZE = 20
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Remembers within the app process whether query-parameter pagination on `/notification`
     * returned genuinely unseen IDs (`true`) or merely repeated page 1 (`false`), avoiding
     * wasted HTTP roundtrips on subsequent scroll pages.
     */
    @Volatile
    private var directUrlPaginationWorks: Boolean? = null

    /** Live continuation endpoint observed from MAL's own XHR/fetch traffic when GET-addressable. */
    @Volatile
    private var discoveredApiUrlTemplate: String? = null

    /** `window.MAL.notification = { ... }` assignment. */
    private val NOTIFICATION_MARKER = Regex("""window\.MAL\.notification\s*=""", RegexOption.IGNORE_CASE)

    private val PROFILE_LINK = Regex(
        """(?:https?://(?:www\.)?myanimelist\.net)?/profile/([A-Za-z0-9_-]{2,32})(?=[/?#"'\s<>)])""",
        RegexOption.IGNORE_CASE
    )

    private val HEADER_PROFILE_TAG = Regex("""<a\b[^>]*header-profile[^>]*>""", RegexOption.IGNORE_CASE)

    private val MANGA_MEDIA_TYPES = listOf(
        "manga", "novel", "manhwa", "manhua", "one-shot", "oneshot", "doujin"
    )

    private val KNOWN_TYPE_IDENTIFIERS = setOf(
        "related_anime_add",
        "on_air",
        "friend_request",
        "friend_request_accept",
        "friend_request_deny",
        "profile_comment",
        "forum_quote",
        "blog_comment",
        "watched_topic_message",
        "club_mass_message_in_forum",
        "club_message",
        "user_mention_in_forum_message",
        "user_mention_in_club_comment",
        "user_mention",
        "payment_stripe",
        "payment",
        "payment_completed",
        "payment_failed"
    )

    private val RESERVED_PROFILE_NAMES = setOf(
        "login", "logout", "register", "signup", "index", "edit", "settings", "notification", "profile"
    )

    private val noRedirectClient by lazy {
        KitsugiHttpClient.jikanClient.newBuilder()
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
    }

    /** Strips any UI prefix (`mal_web_`) so comparisons against `Notification.id` always match. */
    fun rawNotificationId(idOrPrefixed: String): String =
        idOrPrefixed.trim().removePrefix(WEB_ID_PREFIX)

    fun normalizeKnownIds(ids: Collection<String>): Set<String> =
        ids.mapNotNull { rawNotificationId(it).takeIf(String::isNotEmpty) }.toSet()

    internal fun recordDiscoveredApiUrl(rawUrl: String?) {
        val safe = normalizeNotificationPageUrl(rawUrl) ?: return
        if (safe != NOTIFICATIONS_URL) {
            discoveredApiUrlTemplate = safe
        }
    }

    data class Feed(
        val items: List<Notification>,
        val sessionUsername: String? = null,
        val page: Int = 1,
        val hasNextPage: Boolean = false,
        val nextPageUrl: String? = null,
        /** Exact server totals from `.notificaiton-category-nav` (e.g. All 45, Related 39, Airing 4). */
        val categoryCounts: Map<MalNotificationCategory, Int> = emptyMap(),
        /** Safe server URLs from `.notificaiton-category-nav a[href]`. */
        val categoryUrls: Map<MalNotificationCategory, String> = emptyMap()
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

        val mediaTitle: String?
            get() = animeTitle?.trim()?.takeIf { it.isNotEmpty() }
                ?: animeItems.firstOrNull()?.title?.trim()?.takeIf { it.isNotEmpty() }

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

        /** Canonical content key so DOM rows never duplicate items already decoded from JSON. */
        internal fun signatureKey(): String {
            val normType = typeIdentifier.trim().lowercase()
            val normUrl = (targetUrl ?: animeUrl ?: url).orEmpty().trim().trimEnd('/').lowercase()
            val normTitle = (mediaTitle ?: friendName ?: commentUserName ?: topicTitle ?: pageTitle ?: text)
                .orEmpty().trim().lowercase()
            val normTime = (createdAtForDisplay ?: date).orEmpty().trim().lowercase()
            return "$normType|$normUrl|$normTitle|$normTime"
        }
    }

    data class RelatedAnime(val title: String, val url: String?, val imageUrl: String? = null)

    suspend fun fetch(cookieHeader: String): Feed = withContext(Dispatchers.IO) {
        if (cookieHeader.isBlank()) {
            throw MalWebSessionRequiredException("MAL web oturumu gerekli")
        }
        fetchPage(cookieHeader, NOTIFICATIONS_URL, page = 1)
    }

    suspend fun fetchWithUrl(cookieHeader: String, url: String, page: Int = 1): Feed = withContext(Dispatchers.IO) {
        if (cookieHeader.isBlank()) throw MalWebSessionRequiredException("MAL web oturumu gerekli")
        val safeUrl = normalizeNotificationPageUrl(url)
            ?: throw MalNotificationsFetchException("MAL bildirim URL'si geçersiz: $url")
        fetchPage(cookieHeader, safeUrl, page)
    }

    suspend fun fetchMore(
        context: Context,
        cookieHeader: String,
        page: Int,
        nextPageUrl: String? = null,
        knownIds: Set<String>,
        batchSize: Int? = null
    ): Feed? = fetchMore(
        context = context,
        cookieHeader = cookieHeader,
        page = page,
        nextPageUrl = nextPageUrl,
        knownIds = knownIds,
        category = null,
        batchSize = batchSize,
        categoryUrls = emptyMap()
    )

    /**
     * Loads older MAL notifications using a multi-stage verified pipeline:
     *  1. Explicit server next-page URL (`nextPageUrl`) if present on the previous page,
     *  2. Selected category's server URL from `.notificaiton-category-nav` when filtering,
     *  3. Compact query-parameter candidates (`?p=`, `?offset=`, `?page=`) unless already
     *     proven ineffective on this MAL deployment,
     *  4. First-party WebView continuation (`MalNotificationsWebPager`) with XHR/fetch
     *     interception, live Vue component inspection, leaf "View more" / pagination clicking,
     *     and rendered DOM `.notification-item` extraction,
     *  5. Parallel server category URL sweep fallback for devices with restricted WebView.
     *
     * Every stage is strictly verified against [normalizeKnownIds]: a response counts ONLY
     * when it yields genuinely unseen notification IDs.
     */
    suspend fun fetchMore(
        context: Context,
        cookieHeader: String,
        page: Int,
        nextPageUrl: String? = null,
        knownIds: Set<String>,
        category: MalNotificationCategory? = null,
        batchSize: Int? = null,
        categoryUrls: Map<MalNotificationCategory, String> = emptyMap()
    ): Feed? = withContext(Dispatchers.IO) {
        if (cookieHeader.isBlank()) return@withContext null
        val rawKnownIds = normalizeKnownIds(knownIds)
        var sessionError: MalWebSessionRequiredException? = null
        var parsedAnyPage = false
        var browserFailure: Exception? = null

        val explicitNextUrl = normalizeNotificationPageUrl(nextPageUrl)
        if (explicitNextUrl != null) {
            val feed = tryFetchCandidate(cookieHeader, explicitNextUrl, page) { sessionError = sessionError ?: it }
            if (feed != null) {
                parsedAnyPage = true
                if (feed.items.any { it.id !in rawKnownIds }) return@withContext feed
            }
        }

        val specificCategoryUrl = category
            ?.takeIf { it != MalNotificationCategory.ALL }
            ?.let { categoryUrls[it] }
            ?.let(::normalizeNotificationPageUrl)
        if (specificCategoryUrl != null) {
            val feed = tryFetchCandidate(cookieHeader, specificCategoryUrl, page) { sessionError = sessionError ?: it }
            if (feed != null) {
                parsedAnyPage = true
                if (feed.items.any { it.id !in rawKnownIds }) return@withContext feed
            }
        }

        discoveredApiUrlTemplate?.let { discovered ->
            val feed = tryFetchCandidate(cookieHeader, discovered, page) { sessionError = sessionError ?: it }
            if (feed != null) {
                parsedAnyPage = true
                if (feed.items.any { it.id !in rawKnownIds }) return@withContext feed
            }
        }

        if (directUrlPaginationWorks != false) {
            var anyCandidateParsed = false
            for (candidate in moreUrlCandidates(page = page, category = category, batchSize = batchSize)) {
                val feed = tryFetchCandidate(cookieHeader, candidate, page) { sessionError = sessionError ?: it }
                if (feed == null) continue
                parsedAnyPage = true
                anyCandidateParsed = true
                if (feed.items.any { it.id !in rawKnownIds }) {
                    directUrlPaginationWorks = true
                    return@withContext feed
                }
            }
            if (anyCandidateParsed && category == null) {
                directUrlPaginationWorks = false
            }
        }

        val browserFeed = try {
            MalNotificationsWebPager.fetch(
                context = context,
                page = page,
                knownIds = rawKnownIds,
                startUrl = specificCategoryUrl ?: NOTIFICATIONS_URL
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: MalWebSessionRequiredException) {
            if (sessionError == null) sessionError = e
            null
        } catch (e: MalNotificationsFetchException) {
            browserFailure = e
            null
        } catch (e: MalNotificationsParseException) {
            browserFailure = e
            null
        } catch (e: Exception) {
            browserFailure = e
            null
        }

        if (browserFeed != null) {
            parsedAnyPage = true
            if (browserFeed.items.any { it.id !in rawKnownIds }) return@withContext browserFeed
            if (browserFeed.hasNextPage) {
                browserFailure = MalNotificationsFetchException(
                    "MAL bildirim sayfası devam olabileceğini bildirdi ancak yeni kayıt yüklemedi"
                )
            }
        }

        // Pure-HTTP fallback: sweep non-empty category pages from `.notificaiton-category-nav`
        // if WebView failed or did not expose unseen entries yet.
        if (categoryUrls.isNotEmpty()) {
            val swept = sweepCategoryPages(cookieHeader, categoryUrls, page)
            if (swept != null && swept.items.any { it.id !in rawKnownIds }) {
                return@withContext swept
            }
        }

        browserFailure?.let { failure ->
            if (failure is MalNotificationsFetchException || failure is MalNotificationsParseException) {
                throw failure
            }
            throw MalNotificationsFetchException(
                "MAL bildirim devamı açılamadı: ${failure.message ?: failure.javaClass.simpleName}"
            )
        }

        if (!parsedAnyPage) sessionError?.let { throw it }
        null
    }

    private suspend fun tryFetchCandidate(
        cookieHeader: String,
        url: String,
        page: Int,
        onSessionError: (MalWebSessionRequiredException) -> Unit
    ): Feed? = try {
        fetchPage(cookieHeader, url, page)
    } catch (e: CancellationException) {
        throw e
    } catch (e: MalWebSessionRequiredException) {
        onSessionError(e)
        null
    } catch (_: Exception) {
        null
    }

    private suspend fun sweepCategoryPages(
        cookieHeader: String,
        categoryUrls: Map<MalNotificationCategory, String>,
        page: Int
    ): Feed? = coroutineScope {
        val urls = categoryUrls
            .filterKeys { it != MalNotificationCategory.ALL }
            .values
            .mapNotNull(::normalizeNotificationPageUrl)
            .distinct()
        if (urls.isEmpty()) return@coroutineScope null
        val feeds = urls.map { url ->
            async {
                runCatching { fetchPage(cookieHeader, url, page = 1) }.getOrNull()
            }
        }.awaitAll().filterNotNull()
        if (feeds.isEmpty()) return@coroutineScope null
        val mergedItems = feeds.flatMap { it.items }.distinctBy { it.id }
        val owner = feeds.firstNotNullOfOrNull { it.sessionUsername }
        val counts = feeds.firstOrNull { it.categoryCounts.isNotEmpty() }?.categoryCounts.orEmpty()
        Feed(
            items = mergedItems,
            sessionUsername = owner,
            page = page,
            hasNextPage = false,
            categoryCounts = counts,
            categoryUrls = categoryUrls
        )
    }

    /**
     * Safe same-origin continuation URL candidates covering MAL's historical conventions.
     */
    internal fun moreUrlCandidates(
        page: Int,
        category: MalNotificationCategory? = null,
        batchSize: Int? = null
    ): List<String> {
        if (page <= 1) return emptyList()
        val categoryPrefix = category?.webParam?.let { "category=$it&" } ?: ""
        val offsets = linkedSetOf<Int>().apply {
            add((page - 1) * PAGE_SIZE)
            batchSize?.takeIf { it > 0 }?.let { add((page - 1) * it) }
        }
        val urls = linkedSetOf<String>()
        urls += "$NOTIFICATIONS_URL?${categoryPrefix}p=$page"
        offsets.forEach { offset ->
            urls += "$NOTIFICATIONS_URL?${categoryPrefix}offset=$offset"
        }
        urls += "$NOTIFICATIONS_URL?${categoryPrefix}page=$page"
        urls += "$NOTIFICATIONS_URL/history?${categoryPrefix}page=$page"
        return urls.toList()
    }

    /** Downloads and parses a single first-party MAL notification URL. */
    private suspend fun fetchPage(cookieHeader: String, startUrl: String, page: Int): Feed {
        val safeCookie = cookieHeader.replace("\r", "").replace("\n", "").trim()
        val requestCookie = if (safeCookie.split(';').none {
                it.trim().substringBefore('=').equals("anime_update_advanced", ignoreCase = true)
            }
        ) {
            "$safeCookie; anime_update_advanced=0"
        } else safeCookie
        var currentUrl = startUrl

        for (redirectCount in 0..4) {
            val request = Request.Builder()
                .url(currentUrl)
                .header("Cookie", requestCookie)
                .header("Accept", "text/html,application/xhtml+xml,application/json,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9,tr-TR;q=0.8,tr;q=0.7")
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
                    parseDirectJsonOrHtml(body, page)?.let { return it }
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

    private fun parseDirectJsonOrHtml(body: String, page: Int): Feed? {
        val trimmed = body.trimStart()
        if (trimmed.startsWith("{")) {
            parseNotificationPayload(payload = trimmed, page = page, sessionUsername = null)?.let { return it }
        }
        return parseHtml(body, page)
    }

    /**
     * Parses both MAL's embedded `window.MAL.notification` assignment and server-rendered
     * `#content` elements (`.notificaiton-category-nav`, `.notification-item`, `.notification-pagination`).
     */
    internal fun parseHtml(html: String, page: Int = 1): Feed? {
        if (html.isBlank()) return null
        val root = extractNotificationJsonObject(html)
        val document = runCatching { Jsoup.parse(html) }.getOrNull()
        val sidebar = document?.let(::parseSidebarMetadata) ?: SidebarMetadata()
        val domItems = document?.let(::parseDomNotifications).orEmpty()
        val htmlNextUrl = document?.let { nextPageUrlFromDocument(it, page) }

        if (root == null && domItems.isEmpty()) return null

        return parseRoot(
            root = root,
            domItems = domItems,
            page = page,
            sessionUsername = parseSessionUsername(html),
            nextPageUrlHint = root?.let(::notificationNextPageUrl) ?: htmlNextUrl,
            categoryCounts = sidebar.counts,
            categoryUrls = sidebar.urls
        )
    }

    /** WebView bridge path: parse the collected notification payload after MAL's JS has run. */
    internal fun parseNotificationPayload(
        payload: String,
        page: Int,
        sessionUsername: String?,
        hasNextPageOverride: Boolean? = null,
        categoryCounts: Map<MalNotificationCategory, Int> = emptyMap(),
        categoryUrls: Map<MalNotificationCategory, String> = emptyMap()
    ): Feed? {
        val root = runCatching { json.parseToJsonElement(payload).jsonObject }.getOrNull() ?: return null
        return parseRoot(
            root = root,
            domItems = emptyList(),
            page = page,
            sessionUsername = sessionUsername,
            browserHasNextOverride = hasNextPageOverride,
            nextPageUrlHint = notificationNextPageUrl(root),
            categoryCounts = categoryCounts,
            categoryUrls = categoryUrls
        )
    }

    private fun extractNotificationJsonObject(html: String): JsonObject? {
        val marker = NOTIFICATION_MARKER.find(html) ?: return null
        val objectStart = html.indexOf('{', marker.range.last + 1)
        if (objectStart < 0) return null
        val objectEnd = findJsonObjectEnd(html, objectStart) ?: return null
        return runCatching { json.parseToJsonElement(html.substring(objectStart, objectEnd + 1)).jsonObject }
            .getOrNull()
    }

    private fun parseRoot(
        root: JsonObject?,
        domItems: List<Notification>,
        page: Int,
        sessionUsername: String?,
        browserHasNextOverride: Boolean? = null,
        nextPageUrlHint: String? = null,
        categoryCounts: Map<MalNotificationCategory, Int> = emptyMap(),
        categoryUrls: Map<MalNotificationCategory, String> = emptyMap()
    ): Feed? {
        val rawItems = root?.get("items") as? JsonArray
        val rawHistory = root?.get("historyItems") as? JsonArray
        val rawData = root?.get("data") as? JsonArray
        val rawNotifications = root?.get("notifications") as? JsonArray
        if (rawItems == null && rawHistory == null && rawData == null && rawNotifications == null && domItems.isEmpty()) {
            return null
        }

        val jsonParsed = (
            rawItems.orEmpty() +
                rawHistory.orEmpty() +
                rawData.orEmpty() +
                rawNotifications.orEmpty()
            ).mapNotNull { it.asObjectOrNull()?.toNotification() }
            .distinctBy { it.id }

        val items = mergeJsonAndDomNotifications(jsonParsed, domItems)
        val pagination = root?.get("pagination") as? JsonObject
        val explicitHasNext = root?.boolean("hasNextPage")
            ?: root?.boolean("has_next_page")
            ?: root?.boolean("hasMore")
            ?: root?.boolean("has_more")
            ?: pagination?.boolean("hasNextPage")
            ?: pagination?.boolean("has_next_page")
        val embeddedNextUrl = nextPageUrlHint ?: root?.let(::notificationNextPageUrl)
        val sidebarTotal = categoryCounts[MalNotificationCategory.ALL]
        val computedHasNext = when {
            explicitHasNext != null -> explicitHasNext
            browserHasNextOverride != null -> browserHasNextOverride
            embeddedNextUrl != null -> true
            sidebarTotal != null -> items.size < sidebarTotal
            else -> items.isNotEmpty()
        }

        return Feed(
            items = items,
            sessionUsername = sessionUsername,
            page = page,
            hasNextPage = computedHasNext,
            nextPageUrl = embeddedNextUrl,
            categoryCounts = categoryCounts,
            categoryUrls = categoryUrls
        )
    }

    private fun mergeJsonAndDomNotifications(
        jsonItems: List<Notification>,
        domItems: List<Notification>
    ): List<Notification> {
        if (domItems.isEmpty()) return jsonItems
        if (jsonItems.isEmpty()) return domItems.distinctBy { it.id }
        val seenIds = jsonItems.map { it.id }.toHashSet()
        val seenSignatures = jsonItems.map { it.signatureKey() }.toHashSet()
        val extraDom = domItems.filter { dom ->
            seenIds.add(dom.id) && seenSignatures.add(dom.signatureKey())
        }
        return jsonItems + extraDom
    }

    private data class SidebarMetadata(
        val counts: Map<MalNotificationCategory, Int> = emptyMap(),
        val urls: Map<MalNotificationCategory, String> = emptyMap()
    )

    /**
     * Reads MAL's left category navigation (`.notificaiton-category-nav` — MAL's literal spelling,
     * plus `.notification-category-nav` in case MAL fixes the typo).
     */
    internal fun parseSidebarMetadata(document: Document): SidebarMetadata {
        val counts = LinkedHashMap<MalNotificationCategory, Int>()
        val urls = LinkedHashMap<MalNotificationCategory, String>()
        val anchors = document.select(
            ".notificaiton-category-nav a, .notification-category-nav a"
        )
        for (anchor in anchors) {
            val counterEl = anchor.selectFirst(".counter")
            val counterText = counterEl?.text().orEmpty()
            val labelText = anchor.text()
                .removeSuffix(counterText)
                .replace(Regex("""\s+"""), " ")
                .trim()
            val category = matchSidebarCategory(labelText, anchor.attr("href")) ?: continue
            val count = counterText
                .replace(",", "")
                .replace("+", "")
                .trim()
                .toIntOrNull()
            if (count != null && count >= 0) {
                counts[category] = count
            }
            normalizeNotificationPageUrl(anchor.attr("href"))?.let { safeUrl ->
                urls[category] = safeUrl
            }
        }
        return SidebarMetadata(counts = counts, urls = urls)
    }

    private fun matchSidebarCategory(label: String, href: String?): MalNotificationCategory? {
        val norm = label.lowercase().trim()
        MalNotificationCategory.entries.firstOrNull {
            it.sidebarCountKey.equals(norm, ignoreCase = true) ||
                norm == it.sidebarCountKey.lowercase().replace(" ", "")
        }?.let { return it }

        when {
            norm.startsWith("all") -> return MalNotificationCategory.ALL
            norm.contains("related anime") -> return MalNotificationCategory.NEW_RELATED_ANIME
            norm.contains("now airing") -> return MalNotificationCategory.NOW_AIRING
            norm.contains("friend") -> return MalNotificationCategory.FRIEND_REQUESTS
            norm.contains("profile comment") -> return MalNotificationCategory.PROFILE_COMMENTS
            norm.contains("blog") -> return MalNotificationCategory.BLOG_COMMENTS
            norm.contains("quote") -> return MalNotificationCategory.FORUM_QUOTES
            norm.contains("mention") -> return MalNotificationCategory.USER_MENTIONS
            norm.contains("watched topic") -> return MalNotificationCategory.WATCHED_TOPIC_POSTS
            norm.contains("club") -> return MalNotificationCategory.CLUB_MESSAGES
            norm.contains("payment") -> return MalNotificationCategory.PAYMENT
        }
        val queryParam = href?.toHttpUrlOrNull()?.let { url ->
            url.queryParameter("type") ?: url.queryParameter("category")
        }
        return MalNotificationCategory.fromWebParam(queryParam)
            ?: queryParam?.let { MalNotificationCategory.fromTypeIdentifier(it).takeIf { c -> c != MalNotificationCategory.ALL } }
    }

    /**
     * Parses server-rendered `<li class="notification-item ...">` rows inside `#content` /
     * `.notification-section` (never matching the header dropdown spinner placeholder).
     */
    internal fun parseDomNotifications(document: Document): List<Notification> {
        val rows = document.select(
            "body.notification li.notification-item:not(.empty), .notification-section li.notification-item:not(.empty)"
        )
        return rows.mapNotNull(::parseDomNotificationElement)
    }

    private fun parseDomNotificationElement(li: Element): Notification? {
        val classNames = (li.classNames() +
            li.selectFirst(".notification-item-content, .header-notification-item-content")
                ?.classNames().orEmpty())
            .map { it.trim() }
        val type = classNames.firstOrNull { it.lowercase() in KNOWN_TYPE_IDENTIFIERS } ?: "unknown"
        val categoryText = li.selectFirst(".category")?.text()?.trim()?.takeIf(String::isNotEmpty)
        val timeText = li.selectFirst(".time, .date")?.text()?.trim()?.takeIf(String::isNotEmpty)
        val isDeleted = li.selectFirst(".deleted") != null
        val isRead = li.selectFirst(".inner.is-read, .is-read.checked") != null ||
            (!li.hasClass("new") && li.selectFirst(".is-read .fa-circle-check, .is-read .fa-check-circle") != null)
        val isApproved = li.selectFirst(".action-button.accept, button.accept") == null

        val contentRoot = li.selectFirst(".notification-item-content, .header-notification-item-content, .notification-item-layout")
            ?: li
        val links = contentRoot.select("a[href]")
        val backgroundUrl = li.selectFirst("a.background[href]")?.attr("href")?.takeIf(String::isNotBlank)
        val mediaLinks = links.filter {
            val h = it.attr("href")
            (h.contains("/anime/") || h.contains("/manga/")) && it.text().isNotBlank()
        }
        val profileLinks = links.filter {
            it.attr("href").contains("/profile/") && it.text().isNotBlank()
        }
        val imgSrc = contentRoot.selectFirst("img[src]")?.attr("src")?.takeIf(String::isNotBlank)
        val quoteText = contentRoot.selectFirst("q.message, .message")?.text()?.trim()?.takeIf(String::isNotEmpty)
        val fullText = contentRoot.text().trim()
        if (fullText.isEmpty() && mediaLinks.isEmpty() && profileLinks.isEmpty()) return null

        val firstMedia = mediaLinks.firstOrNull()
        val animeMediaType = if (type.equals("related_anime_add", ignoreCase = true) && firstMedia != null) {
            val afterTitle = fullText.substringAfter(firstMedia.text(), "")
            Regex("""^\s*\(([^()]{1,24})\)""").find(afterTitle)?.groupValues?.getOrNull(1)?.trim()
        } else null
        val onAirDate = if (type.equals("on_air", ignoreCase = true)) {
            Regex("""airing\s+on\s+([^:]+):""", RegexOption.IGNORE_CASE)
                .find(fullText)?.groupValues?.getOrNull(1)?.trim()
        } else null
        val relatedAnimes = if (type.equals("on_air", ignoreCase = true)) {
            mediaLinks.map { RelatedAnime(title = it.text().trim(), url = it.attr("href")) }
        } else emptyList()
        if (type.equals("on_air", ignoreCase = true) && relatedAnimes.isEmpty()) return null

        val nonProfileLink = links.firstOrNull { !it.attr("href").contains("/profile/") && it.text().isNotBlank() }
        val primaryPerson = profileLinks.firstOrNull()
        val primaryUrl = sequenceOf(
            backgroundUrl,
            firstMedia?.attr("href"),
            nonProfileLink?.attr("href"),
            primaryPerson?.attr("href")
        ).filterNotNull().firstOrNull(String::isNotBlank)

        val explicitId = sequenceOf(
            li.attr("data-id"),
            li.attr("data-notification-id"),
            li.attr("data-item-id"),
            li.id()
        ).map { it.trim() }.firstOrNull(String::isNotEmpty)
        val stableId = explicitId ?: buildDomFallbackId(
            type = type,
            url = primaryUrl,
            title = firstMedia?.text() ?: primaryPerson?.text() ?: fullText,
            time = timeText
        )

        return Notification(
            id = stableId,
            typeIdentifier = type,
            categoryName = categoryText,
            createdAt = null,
            createdAtForDisplay = timeText,
            isRead = isRead,
            isDeleted = isDeleted,
            isApproved = isApproved,
            url = primaryUrl,
            animeTitle = firstMedia?.text()?.trim(),
            animeMediaType = animeMediaType,
            animeUrl = firstMedia?.attr("href"),
            animeImageUrl = if (firstMedia != null && primaryPerson == null) imgSrc else null,
            senderName = if (type.startsWith("user_mention", ignoreCase = true)) primaryPerson?.text()?.trim() else null,
            senderProfileUrl = if (type.startsWith("user_mention", ignoreCase = true)) primaryPerson?.attr("href") else null,
            pageUrl = if (type.startsWith("user_mention", ignoreCase = true)) nonProfileLink?.attr("href") else null,
            pageTitle = if (type.startsWith("user_mention", ignoreCase = true)) nonProfileLink?.text()?.trim() else null,
            commentUserName = if (type.endsWith("_comment", ignoreCase = true)) primaryPerson?.text()?.trim() else null,
            commentUserProfileUrl = if (type.endsWith("_comment", ignoreCase = true)) primaryPerson?.attr("href") else null,
            commentUserImageUrl = if (type.endsWith("_comment", ignoreCase = true)) imgSrc else null,
            text = if (type.endsWith("_comment", ignoreCase = true)) quoteText else null,
            date = onAirDate,
            friendName = if (type.startsWith("friend_request", ignoreCase = true)) primaryPerson?.text()?.trim() else null,
            friendProfileUrl = if (type.startsWith("friend_request", ignoreCase = true)) primaryPerson?.attr("href") else null,
            friendImageUrl = if (type.startsWith("friend_request", ignoreCase = true)) imgSrc else null,
            quoteUserName = if (type.equals("forum_quote", ignoreCase = true)) primaryPerson?.text()?.trim() else null,
            quoteUserProfileUrl = if (type.equals("forum_quote", ignoreCase = true)) primaryPerson?.attr("href") else null,
            postedUserName = if (type.equals("watched_topic_message", ignoreCase = true) || type.startsWith("club_", ignoreCase = true)) {
                primaryPerson?.text()?.trim()
            } else null,
            postedUserProfileUrl = if (type.equals("watched_topic_message", ignoreCase = true) || type.startsWith("club_", ignoreCase = true)) {
                primaryPerson?.attr("href")
            } else null,
            topicUrl = if (type in setOf("forum_quote", "watched_topic_message", "club_mass_message_in_forum")) {
                nonProfileLink?.attr("href")
            } else null,
            topicTitle = if (type in setOf("forum_quote", "watched_topic_message", "club_mass_message_in_forum")) {
                nonProfileLink?.text()?.trim()
            } else null,
            message = if (type.startsWith("friend_request", ignoreCase = true)) quoteText else quoteText ?: fullText.takeIf {
                firstMedia == null && primaryPerson == null
            },
            animeItems = relatedAnimes
        )
    }

    private fun buildDomFallbackId(type: String, url: String?, title: String, time: String?): String {
        val seed = "${type.lowercase()}|${url.orEmpty().trim()}|${title.trim()}|${time.orEmpty().trim()}"
        var hash = -0x340d631b7bdddcdbL
        for (ch in seed) {
            hash = (hash xor ch.code.toLong()) * 0x100000001b3L
        }
        return "dom_${java.lang.Long.toUnsignedString(hash, 16)}"
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
            .firstOrNull { it != NOTIFICATIONS_URL }
    }

    private fun nextPageUrlFromDocument(document: Document, currentPage: Int): String? = runCatching {
        val relNext = document.select("a[rel], link[rel]")
            .firstOrNull { element -> element.attr("rel").split(' ').any { it.equals("next", true) } }
            ?.attr("href")
            ?.let(::normalizeNotificationPageUrl)
            ?.takeIf { it != NOTIFICATIONS_URL }
        if (relNext != null) return@runCatching relNext

        val dataNext = document.selectFirst("[data-next-url]")
            ?.attr("data-next-url")
            ?.let(::normalizeNotificationPageUrl)
            ?.takeIf { it != NOTIFICATIONS_URL }
        if (dataNext != null) return@runCatching dataNext

        val pagers = document.select(".notification-pagination, .pagination")
        for (pager in pagers) {
            // 1. Sibling anchor immediately following the current page indicator (`<span>1</span> <a href="?p=2">2</a>`).
            val activeMarker = pager.children().firstOrNull { child ->
                val txt = child.text().trim()
                txt == currentPage.toString() && (
                    child.tagName().equals("span", ignoreCase = true) ||
                        child.hasClass("active") ||
                        child.hasClass("current") ||
                        child.hasClass("on")
                    )
            }
            activeMarker?.nextElementSibling()
                ?.takeIf { it.tagName().equals("a", ignoreCase = true) }
                ?.attr("href")
                ?.let(::normalizeNotificationPageUrl)
                ?.takeIf { it != NOTIFICATIONS_URL }
                ?.let { return@runCatching it }

            // 2. Anchor explicitly numbered `currentPage + 1`.
            val nextNumberAnchor = pager.select("a[href]").firstOrNull {
                it.text().trim() == (currentPage + 1).toString()
            }
            nextNumberAnchor?.attr("href")
                ?.let(::normalizeNotificationPageUrl)
                ?.takeIf { it != NOTIFICATIONS_URL }
                ?.let { return@runCatching it }

            // 3. Explicit Next / View More / More anchor inside `.notification-pagination`.
            val explicitMoreAnchor = pager.select("a[href]").firstOrNull { anchor ->
                val label = (anchor.text() + " " + anchor.className()).lowercase().trim()
                Regex("""\b(next|view\s*more|load\s*more|show\s*more|more|older)\b|[›»>]""").containsMatchIn(label)
            }
            explicitMoreAnchor?.attr("href")
                ?.let(::normalizeNotificationPageUrl)
                ?.takeIf { it != NOTIFICATIONS_URL }
                ?.let { return@runCatching it }
        }
        null
    }.getOrNull()

    internal fun normalizeNotificationPageUrl(raw: String?): String? = runCatching {
        if (raw.isNullOrBlank()) return@runCatching null
        val base = NOTIFICATIONS_URL.toHttpUrlOrNull() ?: return@runCatching null
        val trimmed = raw.trim()
        if (trimmed.startsWith("#") || trimmed.startsWith("javascript:", ignoreCase = true)) {
            return@runCatching null
        }
        if (trimmed.contains('@') && trimmed.substringBefore('?').substringBefore('#').contains("://")) {
            val rawCheck = java.net.URI(trimmed)
            if (rawCheck.userInfo != null) return@runCatching null
        }
        val target = base.resolve(trimmed)?.newBuilder()?.fragment(null)?.build() ?: return@runCatching null
        val host = target.host.lowercase()
        val path = target.encodedPath
        val isNotificationPath = path.equals("/notification", ignoreCase = true) ||
            path.startsWith("/notification/", ignoreCase = true)
        if (!target.scheme.equals("https", ignoreCase = true) ||
            target.username.isNotEmpty() || target.password.isNotEmpty() ||
            target.port != 443 ||
            (host != "myanimelist.net" && host != "www.myanimelist.net") ||
            !isNotificationPath ||
            path.contains("check-items-as-read", ignoreCase = true) ||
            path.contains("/setting", ignoreCase = true)
        ) return@runCatching null
        target.toString()
    }.getOrNull()

    internal fun parseSessionUsername(html: String): String? {
        val scannable = withoutNotificationJson(html)
        return headerProfileUsername(scannable) ?: mostFrequentProfileUsername(scannable)
    }

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
