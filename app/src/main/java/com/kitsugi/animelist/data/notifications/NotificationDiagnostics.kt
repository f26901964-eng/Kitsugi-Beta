package com.kitsugi.animelist.data.notifications

import android.content.Context
import android.util.Log
import com.kitsugi.animelist.core.network.KitsugiHttpClient
import com.kitsugi.animelist.data.auth.ExternalAuthManager
import com.kitsugi.animelist.data.auth.KitsuApiClient
import com.kitsugi.animelist.data.auth.ShikimoriApiClient
import com.kitsugi.animelist.data.remote.KitsugiAiringCalendarClient
import com.kitsugi.animelist.data.remote.SimklCalendarClient
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.WatchStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * Bildirim Teşhisi — "çalışıyor mu, çalışmıyorsa neden?" sorusunun kanıtlı cevabı.
 *
 * Her kaynak için **gerçek bir ağ isteği** atar ve şunları raporlar:
 *   • Hesap bağlı mı (token var mı)?
 *   • Kullanılan uç nokta (endpoint)
 *   • HTTP durum kodu (varsa)
 *   • Dönen kayıt sayısı
 *   • Süre (ms) ve oluşan hata mesajı
 *
 * Notlar:
 *   • MyAnimeList'in resmî API'sinde (v2 / resmî uygulama API'si) **bildirim ucu yoktur**.
 *     Bu yüzden MAL için "yayın takvimi eşleşmesi" ölçülür.
 *   • Simkl'in de bildirim ucu yoktur; takvim (v2 CDN) + izleme listesi eşleşmesi ölçülür.
 *   • Kitsu'nun genel API'sinde bildirim kaynağı yoktur.
 *   • Shikimori'de gerçek bildirim ucu vardır: GET /api/users/:id/messages?type=notifications
 *     ve `messages` OAuth izni gerektirir → burada izin durumu da ölçülür.
 */
object NotificationDiagnostics {

    private const val TAG = "NotifDiagnostics"
    private const val UA = "KitsugiApp/2.4 (Android)"

    data class Row(
        val source: String,
        val connected: Boolean,
        val endpoint: String,
        val ok: Boolean,
        val httpStatus: Int? = null,
        val itemCount: Int? = null,
        val durationMs: Long = 0L,
        val detail: String,
        val error: String? = null,
        val hint: String? = null
    )

    suspend fun runAll(context: Context, mediaEntries: List<MediaEntry>): List<Row> {
        val rows = mutableListOf<Row>()
        rows += checkAniList(context)
        rows += checkMal(context, mediaEntries)
        rows += checkSimkl(context, mediaEntries)
        rows += checkKitsu(context)
        rows += checkShikimori(context)
        rows.forEach { row ->
            Log.i(TAG, "${row.source}: ok=${row.ok} status=${row.httpStatus} count=${row.itemCount} " +
                "ms=${row.durationMs} detail=${row.detail} error=${row.error}")
        }
        return rows
    }

    // ─────────────────────────────────────────────────────────────────────────
    // AniList — gerçek bildirim API'si
    // ─────────────────────────────────────────────────────────────────────────

    private suspend fun checkAniList(context: Context): Row {
        val started = System.currentTimeMillis()
        val token = ExternalAuthManager.getAniListToken(context)
        if (token.isNullOrBlank()) {
            return Row(
                source = "AniList",
                connected = false,
                endpoint = "graphql.anilist.co (Page.notifications)",
                ok = false,
                durationMs = System.currentTimeMillis() - started,
                detail = "Hesap bağlı değil",
                hint = "Ayarlar > Platformlar üzerinden AniList'e giriş yapın."
            )
        }

        val query = """
            query KitsugiNotifDiagnostics {
              Viewer { unreadNotificationCount }
              Page(page: 1, perPage: 5) {
                pageInfo { hasNextPage }
                notifications(resetNotificationCount: false) { id type createdAt }
              }
            }
        """.trimIndent()

        return try {
            val payload = JSONObject().put("query", query).toString()
            val http = httpPostJson("https://graphql.anilist.co", payload, token)
            val status = http.first
            val body = http.second
            val duration = System.currentTimeMillis() - started

            val json = runCatching { JSONObject(body) }.getOrNull()
            val errors = json?.optJSONArray("errors")
            if (errors != null && errors.length() > 0) {
                val message = errors.optJSONObject(0)?.optString("message").orEmpty()
                return Row(
                    source = "AniList",
                    connected = true,
                    endpoint = "graphql.anilist.co (Page.notifications)",
                    ok = false,
                    httpStatus = status,
                    durationMs = duration,
                    detail = "API hata döndürdü",
                    error = message.ifBlank { "Bilinmeyen GraphQL hatası" },
                    hint = "Token süresi dolmuş olabilir; Ayarlar > Platformlar'dan yeniden giriş yapın."
                )
            }

            val data = json?.optJSONObject("data")
            val unread = data?.optJSONObject("Viewer")?.optInt("unreadNotificationCount", -1) ?: -1
            val notifications = data?.optJSONObject("Page")?.optJSONArray("notifications")
            val count = notifications?.length() ?: 0

            Row(
                source = "AniList",
                connected = true,
                endpoint = "graphql.anilist.co (Page.notifications)",
                ok = status in 200..299,
                httpStatus = status,
                itemCount = count,
                durationMs = duration,
                detail = buildString {
                    append("Son 5 bildirimden $count kayıt döndü")
                    if (unread >= 0) append(" • okunmamış: $unread")
                },
                error = if (status in 200..299) null else "HTTP $status"
            )
        } catch (e: Exception) {
            Row(
                source = "AniList",
                connected = true,
                endpoint = "graphql.anilist.co (Page.notifications)",
                ok = false,
                durationMs = System.currentTimeMillis() - started,
                detail = "İstek başarısız",
                error = e.message ?: e.javaClass.simpleName
            )
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // MAL — API'de bildirim ucu yok; yayın takvimi eşleşmesi ölçülür
    // ─────────────────────────────────────────────────────────────────────────

    private suspend fun checkMal(context: Context, mediaEntries: List<MediaEntry>): Row {
        val started = System.currentTimeMillis()
        val token = ExternalAuthManager.getMalToken(context)
        val watching = mediaEntries.filter {
            (it.source.equals("jikan", true) || it.source.equals("mal", true)) &&
                (it.status == WatchStatus.Watching || it.status == WatchStatus.Repeating)
        }
        if (token.isNullOrBlank()) {
            return Row(
                source = "MyAnimeList",
                connected = false,
                endpoint = "MAL API v2 (bildirim ucu yok) + AniList yayın takvimi",
                ok = false,
                durationMs = System.currentTimeMillis() - started,
                detail = "Hesap bağlı değil",
                hint = "Ayarlar > Platformlar üzerinden MyAnimeList'e giriş yapın."
            )
        }

        return try {
            val nowSec = System.currentTimeMillis() / 1000L
            val window = KitsugiAiringCalendarClient()
                .fetchAiringWindow(nowSec - 7 * 24 * 3600L, nowSec + 2 * 3600L)
            val malWatchingMalIds = watching.mapNotNull { it.malId }.toSet()
            val matched = window.filter { it.malId != null && malWatchingMalIds.contains(it.malId) }

            Row(
                source = "MyAnimeList",
                connected = true,
                endpoint = "MAL API v2 (bildirim ucu yok) + AniList yayın takvimi",
                ok = true,
                itemCount = matched.size,
                durationMs = System.currentTimeMillis() - started,
                detail = "Listenizde $watchingMalIds.size MAL kaydı • son 7 günde $matched yayın eşleşmesi",
                hint = if (malWatchingMalIds.isEmpty())
                    "İzleme listenizde 'İzleniyor' durumunda MAL kaynaklı kayıt yok; eşleşme bu yüzden 0."
                else null
            )
        } catch (e: Exception) {
            Row(
                source = "MyAnimeList",
                connected = true,
                endpoint = "MAL API v2 (bildirim ucu yok) + AniList yayın takvimi",
                ok = false,
                durationMs = System.currentTimeMillis() - started,
                detail = "Yayın takvimi okunamadı",
                error = e.message ?: e.javaClass.simpleName
            )
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Simkl — bildirim ucu yok; v2 CDN takvim + izleme listesi eşleşmesi
    // ─────────────────────────────────────────────────────────────────────────

    private suspend fun checkSimkl(context: Context, mediaEntries: List<MediaEntry>): Row {
        val started = System.currentTimeMillis()
        val endpoint = "data.simkl.in/calendar/v2 + /sync/all-items/*/watching"
        val token = ExternalAuthManager.getSimklToken(context)
        val calendarClient = SimklCalendarClient()

        val calendar = runCatching { calendarClient.fetchAll() }.getOrDefault(emptyList())
        if (calendar.isEmpty()) {
            return Row(
                source = "Simkl",
                connected = !token.isNullOrBlank(),
                endpoint = endpoint,
                ok = false,
                durationMs = System.currentTimeMillis() - started,
                detail = "Takvim dosyaları okunamadı",
                error = "data.simkl.in boş yanıt verdi"
            )
        }

        if (token.isNullOrBlank()) {
            return Row(
                source = "Simkl",
                connected = false,
                endpoint = endpoint,
                ok = true,
                itemCount = calendar.size,
                durationMs = System.currentTimeMillis() - started,
                detail = "Takvimde ${calendar.size} yayın var • hesap bağlı değil",
                hint = "Kişisel eşleşme için Ayarlar > Platformlar'dan Simkl'e giriş yapın."
            )
        }

        val ids = runCatching { calendarClient.fetchWatchingIds(token) }.getOrNull()
        if (ids == null || ids.isEmpty) {
            return Row(
                source = "Simkl",
                connected = true,
                endpoint = endpoint,
                ok = false,
                itemCount = calendar.size,
                durationMs = System.currentTimeMillis() - started,
                detail = "Takvimde ${calendar.size} yayın var • izleme listesi okunamadı",
                error = "Simkl izleme listesi (watching) alınamadı",
                hint = "Simkl oturumu yenilenmeli olabilir (Ayarlar > Platformlar)."
            )
        }

        val now = System.currentTimeMillis()
        val window = calendar.filter { it.airingAtMs in (now - 7L * 24 * 3600_000)..(now + 7L * 24 * 3600_000) }
        val matched = window.filter { ids.matches(it) }

        return Row(
            source = "Simkl",
            connected = true,
            endpoint = endpoint,
            ok = true,
            itemCount = matched.size,
            durationMs = System.currentTimeMillis() - started,
            detail = "İzleme listesinde ${ids.simkl.size} yapım • ±7 günde ${matched.size} eşleşme (takvim: ${calendar.size})",
            hint = if (matched.isEmpty())
                "Simkl 'İzleniyor' listenizde şu an önümüzdeki/geçmiş 7 güne denk düşen bölüm yok."
            else null
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Kitsu — genel API'de bildirim kaynağı yok; liste + nextRelease
    // ─────────────────────────────────────────────────────────────────────────

    private suspend fun checkKitsu(context: Context): Row {
        val started = System.currentTimeMillis()
        val token = ExternalAuthManager.getKitsuToken(context)
        val userId = ExternalAuthManager.getKitsuUserId(context)
        val endpoint = "kitsu.io/api/edge/library-entries (bildirim kaynağı yok)"

        if (token.isNullOrBlank() || userId.isNullOrBlank()) {
            return Row(
                source = "Kitsu",
                connected = false,
                endpoint = endpoint,
                ok = false,
                durationMs = System.currentTimeMillis() - started,
                detail = "Hesap bağlı değil",
                hint = "Ayarlar > Platformlar üzerinden Kitsu'ya giriş yapın."
            )
        }

        val http = runCatching {
            httpGet(
                "https://kitsu.app/api/edge/library-entries?filter[userId]=$userId&page[limit]=1",
                token,
                "application/vnd.api+json"
            )
        }.getOrNull()
        val status = http?.first
        val duration = System.currentTimeMillis() - started

        if (status == null || status !in 200..299) {
            return Row(
                source = "Kitsu",
                connected = true,
                endpoint = endpoint,
                ok = false,
                httpStatus = status,
                durationMs = duration,
                detail = "Kitsu kitaplığı okunamadı",
                error = if (status == 401 || status == 403) "Yetki reddedildi (HTTP $status)" else "HTTP ${status ?: "-"}"
            )
        }

        val entries = runCatching { KitsuApiClient.fetchAllLibraryEntries(token, userId) }.getOrDefault(emptyList())
        val active = entries.filter { it.status.equals("current", true) || it.status.equals("watching", true) }
        val withNext = active.count { !it.nextRelease.isNullOrBlank() }

        return Row(
            source = "Kitsu",
            connected = true,
            endpoint = endpoint,
            ok = true,
            httpStatus = status,
            itemCount = active.size,
            durationMs = System.currentTimeMillis() - started,
            detail = "Kitaplıkta ${entries.size} kayıt • 'current' $active tanesi • sonraki bölüm tarihi olan $withNext",
            hint = "Kitsu'nun herkese açık bildirim API'si yok; bu liste yayın tarihi akışıdır."
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Shikimori — gerçek bildirim ucu (messages scope gerekir)
    // ─────────────────────────────────────────────────────────────────────────

    private suspend fun checkShikimori(context: Context): Row {
        val started = System.currentTimeMillis()
        val endpoint = "shikimori.one/api/users/:id/(unread_messages|messages)"
        val token = runCatching {
            ExternalAuthManager.getOrRefreshShikimoriToken(context)
        }.getOrNull() ?: ExternalAuthManager.getShikimoriToken(context)
        val userId = ExternalAuthManager.getShikimoriUserId(context)

        if (token.isNullOrBlank() || userId == null) {
            return Row(
                source = "Shikimori",
                connected = false,
                endpoint = endpoint,
                ok = false,
                durationMs = System.currentTimeMillis() - started,
                detail = "Hesap bağlı değil",
                hint = "Ayarlar > Platformlar üzerinden Shikimori'ye giriş yapın."
            )
        }

        val unreadHttp = runCatching { httpGet("https://shikimori.one/api/users/$userId/unread_messages", token) }.getOrNull()
        val unreadStatus = unreadHttp?.first
        val duration = System.currentTimeMillis() - started

        if (unreadStatus != null && (unreadStatus == 401 || unreadStatus == 403)) {
            return Row(
                source = "Shikimori",
                connected = true,
                endpoint = endpoint,
                ok = false,
                httpStatus = unreadStatus,
                durationMs = duration,
                detail = "Token geçerli ama 'messages' izni yok",
                error = "HTTP $unreadStatus (yetkisiz)",
                hint = "Kendi Shikimori uygulamanızı 'user_rates messages' izinleriyle oluşturup " +
                    "Ayarlar > Platformlar'da bu client bilgileriyle yeniden giriş yapın."
            )
        }

        var unreadText = ""
        if (unreadStatus in 200..299) {
            val json = runCatching { JSONObject(unreadHttp?.second.orEmpty()) }.getOrNull()
            if (json != null) {
                unreadText = " • okunmamış: mesaj ${json.optInt("messages", 0)}, " +
                    "haber ${json.optInt("news", 0)}, bildirim ${json.optInt("notifications", 0)}"
            }
        }

        val msgHttp = runCatching {
            httpGet("https://shikimori.one/api/users/$userId/messages?limit=30&type=notifications", token)
        }.getOrNull()
        val msgStatus = msgHttp?.first
        val items = runCatching { JSONArray(msgHttp?.second.orEmpty()).length() }.getOrDefault(0)

        return Row(
            source = "Shikimori",
            connected = true,
            endpoint = endpoint,
            ok = msgStatus in 200..299,
            httpStatus = msgStatus,
            itemCount = items,
            durationMs = System.currentTimeMillis() - started,
            detail = "type=notifications → $items kayıt$unreadText",
            error = if (msgStatus in 200..299) null else "Bildirim ucu HTTP ${msgStatus ?: "-"}",
            hint = if (msgStatus == 401 || msgStatus == 403)
                "Kendi Shikimori uygulamanızı 'user_rates messages' izinleriyle oluşturup yeniden giriş yapın."
            else null
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HTTP yardımcıları
    // ─────────────────────────────────────────────────────────────────────────

    private suspend fun httpGet(url: String, token: String, accept: String = "application/json"): Pair<Int, String> =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(url)
                .header("Accept", accept)
                .header("User-Agent", UA)
                .header("Authorization", "Bearer $token")
                .get()
                .build()
            KitsugiHttpClient.client.newCall(request).execute().use { response ->
                Pair(response.code, response.body?.string().orEmpty())
            }
        }

    private suspend fun httpPostJson(url: String, json: String, token: String): Pair<Int, String> =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .header("User-Agent", UA)
                .header("Authorization", "Bearer $token")
                .post(json.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()
            KitsugiHttpClient.client.newCall(request).execute().use { response ->
                Pair(response.code, response.body?.string().orEmpty())
            }
        }
}
