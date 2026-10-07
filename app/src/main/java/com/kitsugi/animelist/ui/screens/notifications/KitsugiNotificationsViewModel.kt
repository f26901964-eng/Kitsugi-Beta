package com.kitsugi.animelist.ui.screens.notifications

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kitsugi.animelist.R
import com.kitsugi.animelist.data.auth.ExternalAuthManager
import com.kitsugi.animelist.data.auth.KitsuApiClient
import com.kitsugi.animelist.data.auth.ShikimoriApiClient
import com.kitsugi.animelist.data.notifications.NotificationDiagnostics
import com.kitsugi.animelist.data.remote.KitsugiAiringCalendarClient
import com.kitsugi.animelist.data.remote.KitsugiAniListNotificationClient
import com.kitsugi.animelist.data.remote.SimklCalendarClient
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.WatchStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.OffsetDateTime
import java.util.Date
import java.util.Locale

enum class AniListFilter(
    val labelResId: Int,
    val group: KitsugiAniListNotificationClient.NotificationGroup
) {
    ALL(R.string.notif_filter_all, KitsugiAniListNotificationClient.NotificationGroup.ALL),
    AIRING(R.string.notif_filter_airing, KitsugiAniListNotificationClient.NotificationGroup.AIRING),
    ACTIVITY(R.string.notif_filter_activity, KitsugiAniListNotificationClient.NotificationGroup.ACTIVITY),
    FORUM(R.string.notif_filter_forum, KitsugiAniListNotificationClient.NotificationGroup.FORUM),
    FOLLOWS(R.string.notif_filter_follows, KitsugiAniListNotificationClient.NotificationGroup.FOLLOWS),
    MEDIA(R.string.notif_filter_media, KitsugiAniListNotificationClient.NotificationGroup.MEDIA)
}

// ─── Veri Modeli ─────────────────────────────────────────────────────────────

data class NotifItem(
    val id: String,
    val imageUrl: String?,
    val title: String,
    val body: String,
    val dateText: String?,
    val isUnread: Boolean = false,
    val mediaId: Int? = null,
    val activityId: Int? = null,
    val mediaType: String? = null,
    val userId: Int? = null,
    val userName: String? = null,
    val userAvatarUrl: String? = null,
    val source: String = "AniList"
)

// ─── UI State ─────────────────────────────────────────────────────────────────

data class NotifUiState(
    val items: List<NotifItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val hasMore: Boolean = true,
    val page: Int = 1,
    /**
     * Kullanıcıya gösterilecek bilgi notu. Örn.
     * "MAL API'si kişisel bildirim sunmaz; bu liste yayın takvimi eşleşmesidir."
     * Boş liste ile API hatasını birbirinden ayırır.
     */
    val notice: String? = null
)

/** Bildirim Teşhisi ekran durumu. */
data class NotifDiagnosticsUiState(
    val isRunning: Boolean = false,
    val rows: List<NotificationDiagnostics.Row> = emptyList(),
    val error: String? = null
)

// ─── ViewModel ───────────────────────────────────────────────────────────────

class KitsugiNotificationsViewModel(application: Application) : AndroidViewModel(application) {

    private val ctx: Context get() = getApplication<Application>().applicationContext

    // ── AniList ──
    private val _aniListStates = AniListFilter.entries.associateWith { MutableStateFlow(NotifUiState()) }
    val aniListStates: Map<AniListFilter, StateFlow<NotifUiState>> = _aniListStates.mapValues { it.value.asStateFlow() }

    fun getAniListState(filter: AniListFilter): StateFlow<NotifUiState> = _aniListStates[filter]!!.asStateFlow()

    /** AniList okunmamış bildirim sayısı (sekme rozeti için). */
    private val _aniListUnread = MutableStateFlow<Int?>(null)
    val aniListUnread: StateFlow<Int?> = _aniListUnread.asStateFlow()

    // ── MAL ──
    private val _mal = MutableStateFlow(NotifUiState())
    val mal: StateFlow<NotifUiState> = _mal.asStateFlow()

    // ── TMDB+Simkl ──
    private val _tmdbSimkl = MutableStateFlow(NotifUiState())
    val tmdbSimkl: StateFlow<NotifUiState> = _tmdbSimkl.asStateFlow()

    // ── Kitsu ──
    private val _kitsu = MutableStateFlow(NotifUiState())
    val kitsu: StateFlow<NotifUiState> = _kitsu.asStateFlow()

    // ── Shikimori ──
    private val _shikimori = MutableStateFlow(NotifUiState())
    val shikimori: StateFlow<NotifUiState> = _shikimori.asStateFlow()

    // ── Teşhis ──
    private val _diagnostics = MutableStateFlow(NotifDiagnosticsUiState())
    val diagnostics: StateFlow<NotifDiagnosticsUiState> = _diagnostics.asStateFlow()

    /** 5 kaynağın bildirim altyapısını canlı isteklerle test eder. */
    fun runDiagnostics(mediaEntries: List<MediaEntry>) {
        if (_diagnostics.value.isRunning) return
        viewModelScope.launch {
            _diagnostics.value = NotifDiagnosticsUiState(isRunning = true)
            try {
                val rows = NotificationDiagnostics.runAll(ctx, mediaEntries)
                _diagnostics.value = NotifDiagnosticsUiState(isRunning = false, rows = rows)
            } catch (e: Exception) {
                _diagnostics.value = NotifDiagnosticsUiState(
                    isRunning = false,
                    error = e.message ?: e.javaClass.simpleName
                )
            }
        }
    }

    // ─── AniList Yükleyici ────────────────────────────────────────────────────

    fun loadAniList(
        filter: AniListFilter,
        resetPage: Boolean = false,
        mediaEntries: List<MediaEntry> = emptyList()
    ) {
        val stateFlow = _aniListStates[filter] ?: return
        val current = stateFlow.value
        if (!resetPage && !current.hasMore) return
        if (current.isLoading) return

        val page = if (resetPage) 1 else current.page

        viewModelScope.launch {
            stateFlow.value = if (resetPage) {
                NotifUiState(isLoading = true)
            } else {
                current.copy(isLoading = true, error = null)
            }

            try {
                val token = ExternalAuthManager.getAniListToken(ctx)
                    ?: run {
                        stateFlow.value = stateFlow.value.copy(
                            isLoading = false,
                            error = ctx.getString(R.string.notif_login_required_anilist)
                        )
                        return@launch
                    }

                val client = KitsugiAniListNotificationClient()

                // Okunmamış sayaç yalnızca gerçekten sıfırlanabilsin: sadece "Tümü"
                // sekmesinin ilk sayfasında reset istenir (AniHyou davranışı).
                val shouldResetCount = resetPage && filter == AniListFilter.ALL
                val result = client.fetchNotifications(
                    accessToken = token,
                    page = page,
                    perPage = 25,
                    group = filter.group,
                    resetCount = shouldResetCount
                )

                if (shouldResetCount || _aniListUnread.value == null) {
                    // Sayaç okunamazsa akışı bozmuyoruz; yalnızca rozet güncellenmez.
                    _aniListUnread.value = client.fetchUnreadCount(token) ?: _aniListUnread.value
                }

                val mapped = result.notifications.map { n -> n.toNotifItem() }

                val existingItems = if (resetPage) emptyList() else stateFlow.value.items
                stateFlow.value = NotifUiState(
                    items = existingItems + mapped,
                    isLoading = false,
                    error = null,
                    hasMore = result.hasNextPage,
                    page = if (result.hasNextPage) page + 1 else page,
                    notice = null
                )
            } catch (e: Exception) {
                stateFlow.value = stateFlow.value.copy(
                    isLoading = false,
                    error = ctx.getString(R.string.notif_error_load, e.message ?: e.javaClass.simpleName)
                )
            }
        }
    }

    /** AniList bildirimini UI modeline çevirir. */
    private fun KitsugiAniListNotificationClient.KitsugiNotification.toNotifItem(): NotifItem {
        val body = when (type) {
            "AIRING" -> buildString {
                airingContexts?.getOrNull(0)?.let { append(it) }
                append(" ")
                airingContexts?.getOrNull(1)?.let { append(it) }
                append(" ${episode}")
                airingContexts?.getOrNull(2)?.let { append(it) }
            }.trim().ifBlank { ctx.getString(R.string.notif_body_airing, episode) }
            "FOLLOWING"                 -> ctx.getString(R.string.notif_body_following, userName ?: "")
            "ACTIVITY_MESSAGE"          -> ctx.getString(R.string.notif_body_activity_message, userName ?: "")
            "ACTIVITY_REPLY"            -> ctx.getString(R.string.notif_body_activity_reply, userName ?: "", context ?: ctx.getString(R.string.notif_body_activity_reply_fallback))
            "ACTIVITY_REPLY_SUBSCRIBED" -> ctx.getString(R.string.notif_body_activity_reply_subscribed, userName ?: "", context ?: ctx.getString(R.string.notif_body_activity_reply_subscribed_fallback))
            "ACTIVITY_MENTION"          -> ctx.getString(R.string.notif_body_activity_mention, userName ?: "", context ?: ctx.getString(R.string.notif_body_activity_mention_fallback))
            "ACTIVITY_LIKE"             -> ctx.getString(R.string.notif_body_activity_like, userName ?: "", context ?: ctx.getString(R.string.notif_body_activity_like_fallback))
            "ACTIVITY_REPLY_LIKE"       -> ctx.getString(R.string.notif_body_activity_reply_like, userName ?: "", context ?: ctx.getString(R.string.notif_body_activity_reply_like_fallback))
            "THREAD_COMMENT_MENTION"    -> ctx.getString(R.string.notif_body_thread_comment_mention, userName ?: "")
            "THREAD_COMMENT_REPLY"      -> ctx.getString(R.string.notif_body_thread_comment_reply, userName ?: "")
            "THREAD_COMMENT_SUBSCRIBED" -> ctx.getString(R.string.notif_body_thread_comment_subscribed, userName ?: "")
            "THREAD_COMMENT_LIKE"       -> ctx.getString(R.string.notif_body_thread_comment_like, userName ?: "")
            "THREAD_LIKE"               -> ctx.getString(R.string.notif_body_thread_like, userName ?: "", threadTitle ?: ctx.getString(R.string.notif_body_thread_like_fallback))
            "RELATED_MEDIA_ADDITION"    -> ctx.getString(R.string.notif_body_related_media_addition, mediaTitle ?: "", context ?: ctx.getString(R.string.notif_body_related_media_addition_fallback))
            "MEDIA_DATA_CHANGE"         -> ctx.getString(R.string.notif_body_media_data_change, mediaTitle ?: "", context ?: ctx.getString(R.string.notif_body_media_data_change_fallback))
            "MEDIA_MERGE"               -> ctx.getString(R.string.notif_body_media_merge, mediaTitle ?: "", context ?: ctx.getString(R.string.notif_body_media_merge_fallback))
            "MEDIA_DELETION"            -> ctx.getString(R.string.notif_body_media_deletion, deletedMediaTitle ?: ctx.getString(R.string.notif_body_media_deletion_fallback))
            else                        -> context ?: ctx.getString(R.string.notif_body_new)
        }
        return NotifItem(
            id = "al_${id}",
            imageUrl = mediaCoverUrl ?: userAvatarUrl,
            title = mediaTitle ?: userName ?: threadTitle ?: "AniList",
            body = body,
            dateText = dateText,
            mediaId = mediaId,
            activityId = activityId,
            mediaType = mediaType?.lowercase(),
            userId = userId,
            userName = userName,
            userAvatarUrl = userAvatarUrl,
            source = "AniList"
        )
    }

    // ─── MAL Yükleyici ───────────────────────────────────────────────────────

    /**
     * MyAnimeList'in resmî API'sinde **bildirim ucu yoktur** (MAL API v2 / resmî
     * uygulama API'si yalnızca anime, manga, liste ve kullanıcı uçları sunar).
     * Bu yüzden MAL sekmesi "izleme listenizin son 7 gündeki yayınları" akışıdır ve
     * bunu kullanıcıya açıkça söyler.
     */
    fun loadMal(mediaEntries: List<MediaEntry>) {
        if (_mal.value.isLoading) return
        viewModelScope.launch {
            _mal.value = NotifUiState(isLoading = true)
            try {
                val nowSec = System.currentTimeMillis() / 1000L
                val fromSec = nowSec - 7L * 24 * 3600L
                val airings = KitsugiAiringCalendarClient().fetchAiringWindow(fromSec, nowSec + 2L * 3600L)

                val malEntries = mediaEntries.filter {
                    (it.source.equals("jikan", ignoreCase = true) || it.source.equals("mal", ignoreCase = true)) &&
                        (it.status == WatchStatus.Watching || it.status == WatchStatus.Repeating)
                }
                val malIds = malEntries.mapNotNull { it.malId }.toSet()
                val matched = airings
                    .filter { it.malId != null && malIds.contains(it.malId) }
                    .sortedByDescending { it.airingAt }

                val items = matched.map { entry ->
                    val aired = entry.airingAt <= nowSec
                    NotifItem(
                        id = "mal_${entry.malId}_${entry.episode}",
                        imageUrl = entry.coverUrl,
                        title = entry.title,
                        body = if (aired)
                            ctx.getString(R.string.notif_mal_episode_released, entry.episode)
                        else
                            ctx.getString(R.string.notif_mal_episode_upcoming, entry.episode),
                        dateText = formatEpochSec(entry.airingAt),
                        mediaId = entry.malId,
                        mediaType = "anime",
                        source = "MyAnimeList"
                    )
                }

                val notice = when {
                    malEntries.isEmpty() -> ctx.getString(R.string.notif_notice_mal_no_list)
                    items.isEmpty() -> ctx.getString(R.string.notif_notice_mal_no_airing, malIds.size)
                    else -> ctx.getString(R.string.notif_notice_mal_feed, items.size, malIds.size)
                }

                _mal.value = NotifUiState(
                    items = items,
                    isLoading = false,
                    hasMore = false,
                    notice = notice
                )
            } catch (e: Exception) {
                android.util.Log.e("KitsugiNotif", "MAL load failed: ${e.message}", e)
                _mal.value = NotifUiState(
                    isLoading = false,
                    error = ctx.getString(R.string.notif_error_load, e.message ?: e.javaClass.simpleName)
                )
            }
        }
    }

    // ─── TMDB+Simkl Yükleyici ────────────────────────────────────────────────

    /**
     * Simkl'in de bildirim ucu yoktur. Doğru yöntem (Simkl resmî dokümanı):
     * kullanıcının "watching" listesi + v2 CDN takvimi (`data.simkl.in/calendar/v2/...`)
     * birleştirilir. Token yoksa yerel kayıtlarla eşleştirme yapılır.
     */
    fun loadTmdbSimkl(mediaEntries: List<MediaEntry>) {
        if (_tmdbSimkl.value.isLoading) return
        viewModelScope.launch {
            _tmdbSimkl.value = NotifUiState(isLoading = true)
            try {
                val calendarClient = SimklCalendarClient()
                val calendar = calendarClient.fetchAll()
                val token = ExternalAuthManager.getSimklToken(ctx)

                val now = System.currentTimeMillis()
                val windowStart = now - 7L * 24 * 3600_000
                val windowEnd = now + 7L * 24 * 3600_000
                val window = calendar.filter { it.airingAtMs in windowStart..windowEnd }

                var matched: List<SimklCalendarClient.SimklCalendarEntry> = emptyList()
                var notice: String

                val watchIds = if (!token.isNullOrBlank()) {
                    runCatching { calendarClient.fetchWatchingIds(token) }.getOrNull()
                } else null

                if (watchIds != null && !watchIds.isEmpty) {
                    matched = window.filter { watchIds.matches(it) }
                    notice = if (matched.isEmpty())
                        ctx.getString(R.string.notif_notice_simkl_no_airing)
                    else
                        ctx.getString(R.string.notif_notice_simkl_feed_api, matched.size)
                } else {
                    // Token yok/okunamadı → yerel kayıtlarla eşleştir (eski davranışın düzeltilmiş hâli)
                    val relevant = mediaEntries.filter { me ->
                        (me.source.equals("simkl", ignoreCase = true) || me.source.equals("tmdb", ignoreCase = true)) &&
                            (me.status == WatchStatus.Watching || me.status == WatchStatus.Repeating)
                    }
                    val simklIds = relevant.mapNotNull { it.simklId }.toSet()
                    val tmdbIds = relevant.mapNotNull { it.tmdbId }.toSet()
                    val malIds = relevant.mapNotNull { it.malId }.toSet()
                    matched = window.filter { entry ->
                        simklIds.contains(entry.simklId) ||
                            (entry.tmdbId != null && tmdbIds.contains(entry.tmdbId)) ||
                            (entry.malId != null && malIds.contains(entry.malId))
                    }
                    notice = when {
                        token.isNullOrBlank() -> ctx.getString(R.string.notif_notice_simkl_not_connected)
                        matched.isEmpty() -> ctx.getString(R.string.notif_notice_simkl_no_airing)
                        else -> ctx.getString(R.string.notif_notice_simkl_feed_local, matched.size)
                    }
                }

                val items = matched
                    .sortedByDescending { it.airingAtMs }
                    .map { entry ->
                        val aired = entry.airingAtMs <= now
                        NotifItem(
                            id = "simkl_${entry.simklId}_${entry.episode ?: 0}_${entry.airingAtMs}",
                            imageUrl = entry.posterUrl,
                            title = entry.title,
                            body = buildString {
                                val label = entry.episodeLabel
                                if (label != null) append(label) else append(ctx.getString(R.string.notif_simkl_movie_release))
                                append(" • ")
                                append(
                                    if (aired) ctx.getString(R.string.notif_state_aired)
                                    else ctx.getString(R.string.notif_state_upcoming)
                                )
                                if (entry.isFinale) append(" • ").append(ctx.getString(R.string.notif_badge_finale))
                            },
                            dateText = formatEpochMs(entry.airingAtMs),
                            mediaId = entry.simklId,
                            mediaType = if (entry.isMovie) "movie" else "tv",
                            source = "Simkl"
                        )
                    }

                _tmdbSimkl.value = NotifUiState(
                    items = items,
                    isLoading = false,
                    hasMore = false,
                    notice = notice
                )
            } catch (e: Exception) {
                android.util.Log.e("KitsugiNotif", "Simkl load failed: ${e.message}", e)
                _tmdbSimkl.value = NotifUiState(
                    isLoading = false,
                    error = ctx.getString(R.string.notif_error_load, e.message ?: e.javaClass.simpleName)
                )
            }
        }
    }

    // ─── Kitsu Yükleyici ──────────────────────────────────────────────────────

    /**
     * Kitsu'nun genel API'sinde bildirim kaynağı **yoktur** (JSON:API kaynakları:
     * anime, manga, library-entries, comments, posts...). Bu sekme takip listesini ve
     * Kitsu'nun `nextRelease` alanından gelen "sonraki bölüm" tarihlerini gösterir.
     */
    fun loadKitsu(mediaEntries: List<MediaEntry> = emptyList()) {
        if (_kitsu.value.isLoading) return
        viewModelScope.launch {
            _kitsu.value = NotifUiState(isLoading = true)
            try {
                val token = ExternalAuthManager.getKitsuToken(ctx)
                val userId = ExternalAuthManager.getKitsuUserId(ctx)
                val items = mutableListOf<NotifItem>()

                if (!token.isNullOrBlank() && !userId.isNullOrBlank()) {
                    val entries = KitsuApiClient.fetchAllLibraryEntries(token, userId)
                    val activeEntries = entries.filter {
                        it.status.equals("current", true) || it.status.equals("watching", true)
                    }
                    val upcoming = activeEntries
                        .mapNotNull { entry ->
                            val release = parseIsoToEpochMs(entry.nextRelease) ?: return@mapNotNull null
                            entry to release
                        }
                        .sortedBy { it.second }

                    upcoming.forEach { (entry, releaseMs) ->
                        val aired = releaseMs <= System.currentTimeMillis()
                        items.add(
                            NotifItem(
                                id = "kitsu_next_${entry.id}",
                                imageUrl = entry.imageUrl,
                                title = entry.title,
                                body = ctx.getString(
                                    if (aired) R.string.notif_kitsu_episode_released
                                    else R.string.notif_kitsu_episode_upcoming,
                                    entry.progress + 1
                                ),
                                dateText = formatEpochMs(releaseMs),
                                mediaId = entry.animeId ?: entry.mangaId,
                                mediaType = if (entry.animeId != null) "anime" else "manga",
                                source = "Kitsu"
                            )
                        )
                    }

                    // Sonraki bölüm tarihi olmayan aktif kayıtlar liste olarak gösterilir.
                    activeEntries
                        .filter { it.nextRelease.isNullOrBlank() }
                        .take(30)
                        .forEach { entry ->
                            items.add(
                                NotifItem(
                                    id = "kitsu_${entry.id}",
                                    imageUrl = entry.imageUrl,
                                    title = entry.title,
                                    body = if (entry.progress > 0)
                                        ctx.getString(R.string.notif_kitsu_progress, entry.progress)
                                    else
                                        ctx.getString(R.string.notif_kitsu_in_list),
                                    dateText = null,
                                    mediaId = entry.animeId ?: entry.mangaId,
                                    mediaType = if (entry.animeId != null) "anime" else "manga",
                                    source = "Kitsu"
                                )
                            )
                        }
                }

                if (items.isEmpty()) {
                    val kitsuEntries = mediaEntries.filter {
                        it.source.equals("kitsu", ignoreCase = true) &&
                            (it.status == WatchStatus.Watching || it.status == WatchStatus.Repeating)
                    }
                    kitsuEntries.forEach { me ->
                        items.add(
                            NotifItem(
                                id = "kitsu_local_${me.id}",
                                imageUrl = me.imageUrl,
                                title = me.title,
                                body = ctx.getString(R.string.notif_kitsu_in_list),
                                dateText = null,
                                mediaId = me.malId ?: me.id,
                                mediaType = "anime",
                                source = "Kitsu"
                            )
                        )
                    }
                }

                val notice = if (token.isNullOrBlank())
                    ctx.getString(R.string.notif_notice_kitsu_not_connected)
                else
                    ctx.getString(R.string.notif_notice_kitsu_api)

                _kitsu.value = NotifUiState(
                    items = items,
                    isLoading = false,
                    hasMore = false,
                    notice = notice
                )
            } catch (e: Exception) {
                android.util.Log.e("KitsugiNotif", "Kitsu load failed: ${e.message}", e)
                _kitsu.value = NotifUiState(
                    isLoading = false,
                    error = ctx.getString(R.string.notif_error_load, e.message ?: e.javaClass.simpleName)
                )
            }
        }
    }

    // ─── Shikimori Yükleyici ──────────────────────────────────────────────────

    /**
     * Shikimori'de **gerçek** bildirim ucu vardır:
     *   GET /api/users/:id/messages?type=notifications  (ayrıca: inbox, private, sent, news)
     *   GET /api/users/:id/unread_messages              → { messages, news, notifications }
     * İkisi de `messages` OAuth izni ister. İzin yoksa izleme geçmişine (history) düşülür
     * ve kullanıcıya izni nasıl vereceği açıkça söylenir.
     */
    fun loadShikimori(mediaEntries: List<MediaEntry> = emptyList()) {
        if (_shikimori.value.isLoading) return
        viewModelScope.launch {
            _shikimori.value = NotifUiState(isLoading = true)
            try {
                val token = runCatching { ExternalAuthManager.getOrRefreshShikimoriToken(ctx) }
                    .getOrNull() ?: ExternalAuthManager.getShikimoriToken(ctx)
                var userId = ExternalAuthManager.getShikimoriUserId(ctx)
                if (!token.isNullOrBlank() && userId == null && runCatching { ExternalAuthManager.ensureShikimoriUserResolved(ctx) }.getOrDefault(false)) {
                    userId = ExternalAuthManager.getShikimoriUserId(ctx)
                }

                if (token.isNullOrBlank() || userId == null) {
                    _shikimori.value = NotifUiState(
                        isLoading = false,
                        hasMore = false,
                        error = ctx.getString(R.string.notif_login_required_shikimori)
                    )
                    return@launch
                }

                val unread = runCatching { ShikimoriApiClient.fetchUnreadCounts(token, userId) }.getOrNull()

                var scopeMissing = false
                val realNotifications = mutableListOf<ShikimoriApiClient.ShikimoriMessage>()
                try {
                    realNotifications += ShikimoriApiClient.fetchMessages(token, userId, "notifications", limit = 30)
                    realNotifications += ShikimoriApiClient.fetchMessages(token, userId, "news", limit = 15)
                } catch (_: ShikimoriApiClient.ShikimoriScopeException) {
                    scopeMissing = true
                }

                if (realNotifications.isNotEmpty()) {
                    val items = realNotifications
                        .sortedByDescending { parseIsoToEpochMs(it.createdAt) ?: 0L }
                        .map { msg -> msg.toNotifItem() }

                    val unreadText = unread?.let {
                        ctx.getString(R.string.notif_notice_shikimori_unread, it.notifications, it.news)
                    } ?: ""

                    _shikimori.value = NotifUiState(
                        items = items,
                        isLoading = false,
                        hasMore = false,
                        notice = ctx.getString(R.string.notif_notice_shikimori_real, items.size) + unreadText
                    )
                    return@launch
                }

                // Gerçek bildirim yok / izin yok → izleme geçmişi (history) akışına düş.
                val history = ShikimoriApiClient.fetchUserHistory(token, userId, limit = 40)
                val historyItems = history.map { h ->
                    NotifItem(
                        id = "shikimori_${h.id}",
                        imageUrl = h.targetImageUrl,
                        title = h.targetTitle,
                        body = h.description.ifBlank { ctx.getString(R.string.notif_shikimori_activity_fallback) },
                        dateText = h.createdAt.take(10),
                        mediaId = h.targetId?.toInt(),
                        mediaType = h.targetType ?: "anime",
                        source = "Shikimori"
                    )
                }

                val notice = if (scopeMissing)
                    ctx.getString(R.string.notif_notice_shikimori_scope_missing)
                else
                    ctx.getString(R.string.notif_notice_shikimori_history_only)

                _shikimori.value = NotifUiState(
                    items = historyItems,
                    isLoading = false,
                    hasMore = false,
                    notice = notice
                )
            } catch (e: Exception) {
                android.util.Log.e("KitsugiNotif", "Shikimori load failed: ${e.message}", e)
                _shikimori.value = NotifUiState(
                    isLoading = false,
                    error = ctx.getString(R.string.notif_error_load, e.message ?: e.javaClass.simpleName)
                )
            }
        }
    }

    private fun ShikimoriApiClient.ShikimoriMessage.toNotifItem(): NotifItem {
        val kindLabel = when (kind.lowercase()) {
            "anons" -> ctx.getString(R.string.notif_shikimori_kind_anons)
            "news" -> ctx.getString(R.string.notif_shikimori_kind_news)
            else -> ctx.getString(R.string.notif_shikimori_kind_generic)
        }
        val text = stripBbcode(body).ifBlank { stripHtml(htmlBody) }
        return NotifItem(
            id = "shikimori_msg_${type}_${id}",
            imageUrl = targetImageUrl ?: fromAvatarUrl,
            title = targetTitle ?: kindLabel,
            body = buildString {
                append(kindLabel)
                if (text.isNotBlank()) append(" • ").append(text.take(160))
            },
            dateText = parseIsoToEpochMs(createdAt)?.let { formatEpochMs(it) } ?: createdAt.take(10),
            isUnread = !read,
            mediaId = linkedId?.toInt(),
            mediaType = linkedType?.lowercase(),
            userId = fromUserId,
            userName = fromNickname,
            userAvatarUrl = fromAvatarUrl,
            source = "Shikimori"
        )
    }

    // ─── Yardımcılar ─────────────────────────────────────────────────────────

    private fun formatEpochMs(ms: Long): String =
        SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault()).format(Date(ms))

    private fun formatEpochSec(sec: Long): String = formatEpochMs(sec * 1000L)

    private fun parseIsoToEpochMs(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        val text = raw.trim()
        return try {
            when {
                text.endsWith("Z", true) -> Instant.parse(text).toEpochMilli()
                text.length >= 19 -> OffsetDateTime.parse(text).toInstant().toEpochMilli()
                else -> null
            }
        } catch (_: Exception) {
            try {
                OffsetDateTime.parse(text).toInstant().toEpochMilli()
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun stripBbcode(raw: String): String =
        raw.replace(Regex("\\[/?[a-zA-Z0-9=*]+[^\\]]*\\]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun stripHtml(raw: String): String =
        raw.replace(Regex("<[^>]*>"), " ")
            .replace("&nbsp;", " ")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&amp;", "&")
            .replace(Regex("\\s+"), " ")
            .trim()
}
