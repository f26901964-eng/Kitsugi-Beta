package com.kitsugi.animelist.data.remote.malweb

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

/**
 * MyAnimeList's website-only notification feed.
 *
 * MAL API v2 does not expose personal notifications. The signed-in website page embeds the
 * account feed in `window.MAL.notification`; this client reads that JSON with the user's MAL
 * WebView session cookie and returns data for the native notification UI. The OAuth API token
 * is deliberately not sent here: it is not a MAL website session.
 */
internal object MalNotificationsClient {
    private const val NOTIFICATIONS_URL = "https://myanimelist.net/notification"
    private const val MAL_HOME_URL = "https://myanimelist.net/"
    private val json = Json { ignoreUnknownKeys = true }
    private val noRedirectClient by lazy {
        KitsugiHttpClient.jikanClient.newBuilder()
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
    }

    data class Feed(val items: List<Notification>)

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
            get() = sequenceOf(friendImageUrl, commentUserImageUrl)
                .filterNotNull()
                .mapNotNull { MalNotificationsClient.normalizeMalUrl(it) }
                .firstOrNull()

        val mediaId: Int?
            get() = sequenceOf(animeUrl, url, animeItems.firstOrNull()?.url)
                .filterNotNull()
                .mapNotNull { MalNotificationsClient.normalizeMalUrl(it) }
                .mapNotNull { MalNotificationsClient.extractMediaId(it) }
                .firstOrNull()

        val mediaType: String?
            get() = when {
                animeMediaType?.contains("manga", ignoreCase = true) == true -> "manga"
                animeMediaType?.isNotBlank() == true -> "anime"
                targetUrl?.contains("/manga/") == true -> "manga"
                mediaId != null -> "anime"
                else -> null
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

    data class RelatedAnime(val title: String, val url: String?)

    suspend fun fetch(cookieHeader: String): Feed = withContext(Dispatchers.IO) {
        // Çerez adına göre karar verilmez: MAL'ın hangi çerezle "giriş yapılmış" saydığını
        // yalnızca MAL bilir. İstek yapılır, giriş sayfasına düşülürse oturum istenir.
        if (cookieHeader.isBlank()) {
            throw MalWebSessionRequiredException("MAL web oturumu gerekli")
        }
        val safeCookie = cookieHeader.replace("\r", "").replace("\n", "").trim()
        val requestCookie = if (safeCookie.split(';').none {
                it.trim().substringBefore('=').equals("anime_update_advanced", ignoreCase = true)
            }
        ) {
            "$safeCookie; anime_update_advanced=0"
        } else safeCookie
        var currentUrl = NOTIFICATIONS_URL

        // Never let an automatically followed redirect carry a website session cookie to an
        // unrelated host. Follow only HTTPS redirects back to MAL's own canonical host(s).
        for (redirectCount in 0..4) {
            val request = Request.Builder()
                .url(currentUrl)
                .header("Cookie", requestCookie)
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "tr-TR,tr;q=0.9,en-US;q=0.8,en;q=0.7")
                .header("Referer", MAL_HOME_URL)
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
                    parseHtml(body)?.let { return@withContext it }
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

    /** Parses MAL's JSON assignment without evaluating page JavaScript. */
    internal fun parseHtml(html: String): Feed? {
        val marker = Regex("""window\.MAL\.notification\s*=""", RegexOption.IGNORE_CASE).find(html) ?: return null
        val objectStart = html.indexOf('{', marker.range.last + 1)
        if (objectStart < 0) return null
        val objectEnd = findJsonObjectEnd(html, objectStart) ?: return null
        val root = runCatching { json.parseToJsonElement(html.substring(objectStart, objectEnd + 1)).jsonObject }
            .getOrNull() ?: return null
        val rawItems = root["items"] as? JsonArray ?: return null
        return Feed(rawItems.mapNotNull { it.asObjectOrNull()?.toNotification() })
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
            RelatedAnime(title, item.string("url"))
        }
        if (type.equals("on_air", ignoreCase = true) && animeItems.isEmpty()) return null
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
