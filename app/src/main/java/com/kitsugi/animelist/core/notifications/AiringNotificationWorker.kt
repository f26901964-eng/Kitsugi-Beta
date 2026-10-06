package com.kitsugi.animelist.core.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kitsugi.animelist.MainActivity
import com.kitsugi.animelist.data.auth.ExternalAuthManager
import com.kitsugi.animelist.data.auth.SimklImportManager
import com.kitsugi.animelist.data.local.KitsugiDatabase
import com.kitsugi.animelist.data.local.MediaEntryRepository
import com.kitsugi.animelist.data.local.toDomain
import com.kitsugi.animelist.data.remote.KitsugiAniListNotificationClient
import com.kitsugi.animelist.data.remote.KitsugiAiringCalendarClient
import com.kitsugi.animelist.data.settings.SettingsDataStore
import com.kitsugi.animelist.model.WatchStatus
import kotlinx.coroutines.flow.first

class AiringNotificationWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "AiringWorker"
        private const val CHANNEL_ID = "Kitsugi_airing_channel"
        private const val PREFS_NAME = "Kitsugi_notified_prefs"
        private const val KEY_NOTIFIED_ANILIST = "notified_anilist_ids"
        private const val KEY_NOTIFIED_MAL = "notified_mal_keys"
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "AiringNotificationWorker background check started")

        // Load settings
        val settings = SettingsDataStore(context).settingsFlow.first()

        val isAiringEnabled = settings.airingNotificationsEnabled
        val isAniListEnabled = settings.aniListNotificationsEnabled
        val isMalEnabled = settings.malNotificationsEnabled
        val isSimklEnabled = settings.simklNotificationsEnabled

        // If no notifications are enabled, stop immediately
        if (!isAiringEnabled && !isAniListEnabled && !isMalEnabled && !isSimklEnabled) {
            Log.d(TAG, "No notification channels are enabled. Stopping worker.")
            return Result.success()
        }

        // Sistem bildirimi izni yoksa API çağrılarıyla vakit kaybetme.
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            Log.w(TAG, "POST_NOTIFICATIONS izni kapalı — bildirim kontrolü atlandı. Ayarlar > Bildirimler üzerinden izin verilmeli.")
            return Result.success()
        }

        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val titleLanguage = settings.titleLanguage

        // 1. Fetch watching entries from DB
        val db = KitsugiDatabase.getDatabase(context)
        val watchingEntries = try {
            val allEntities = db.mediaEntryDao().getAll()
            allEntities.filter { entity ->
                entity.status == WatchStatus.Watching.name ||
                entity.status == WatchStatus.Repeating.name
            }.map { it.toDomain() }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load database watching entries: ${e.message}")
            emptyList()
        }

        // 2. AniList Polling
        if (isAniListEnabled) {
            val aniListToken = ExternalAuthManager.getAniListToken(context)
            if (!aniListToken.isNullOrBlank()) {
                try {
                    val client = KitsugiAniListNotificationClient()
                    val page = client.fetchNotifications(aniListToken, page = 1, perPage = 25)
                    val notifiedIds = loadTimestampedKeys(sharedPrefs, KEY_NOTIFIED_ANILIST)

                    for (notif in page.notifications) {
                        val notifIdStr = notif.id.toString()
                        if (!notifiedIds.containsKey(notifIdStr)) {
                            val title = notif.mediaTitle ?: notif.userName ?: notif.threadTitle ?: "AniList"
                            val body = formatAniListBody(notif)
                            val isMedia = notif.type in listOf("AIRING", "RELATED_MEDIA_ADDITION", "MEDIA_DATA_CHANGE", "MEDIA_MERGE")

                            showNotification(
                                id = notif.id,
                                title = title,
                                bodyText = body,
                                source = "AniList",
                                imageUrl = notif.mediaCoverUrl,
                                avatarUrl = notif.userAvatarUrl,
                                isMedia = isMedia,
                                mediaId = notif.mediaId
                            )
                            notifiedIds[notifIdStr] = System.currentTimeMillis()
                        }
                    }
                    saveTimestampedKeys(sharedPrefs, KEY_NOTIFIED_ANILIST, notifiedIds)
                } catch (e: Exception) {
                    Log.e(TAG, "AniList notifications fetch failed: ${e.message}", e)
                }
            }
        }

        // 3. MyAnimeList (Airing Calendar) Polling
        if (isMalEnabled || isAiringEnabled) {
            try {
                // Hafta sınırına takılmayan pencere: son 2 gün (varsayılan 180 dk aralığın
                // kat kat üstünde güvenli pay + Pazartesi günü Pazar bölümlerini yakalar).
                val calendarClient = KitsugiAiringCalendarClient()
                val window = calendarClient.fetchAiringWindow(daysBack = 2, daysForward = 0)
                val now = System.currentTimeMillis()

                // Tüm kaynaklardaki izlenmekte animeler + yerel MAL aynası boşsa MAL API yedeği.
                val targets = com.kitsugi.animelist.data.remote.AiringNotificationMatcher
                    .allWatchTargets(watchingEntries).toMutableList()
                val hasLocalMalAnime = watchingEntries.any { me ->
                    (me.source.equals("mal", ignoreCase = true) ||
                        me.source.equals("jikan", ignoreCase = true) ||
                        me.source.equals("myanimelist", ignoreCase = true)) &&
                    com.kitsugi.animelist.data.remote.AiringNotificationMatcher.run { me.isTrackableForAiring() }
                }
                if (!hasLocalMalAnime) {
                    targets += com.kitsugi.animelist.data.remote.AiringNotificationMatcher
                        .resolveMalWatchTargets(context, watchingEntries)
                }
                if (targets.isEmpty()) {
                    Log.d(TAG, "MAL/yayın bildirimi için izlenmekte anime yok — atlandı.")
                }

                val notifiedMalKeys = loadTimestampedKeys(sharedPrefs, KEY_NOTIFIED_MAL)

                for (entry in window) {
                    val triggerMs = entry.airingAt * 1000L
                    // Son 24 saat içinde yayınlanan bölümler
                    if (triggerMs > now || triggerMs <= now - 24 * 60 * 60 * 1000L) continue
                    val target = targets.firstOrNull { t ->
                        com.kitsugi.animelist.data.remote.AiringNotificationMatcher.matches(entry, t)
                    } ?: continue
                    // Kullanıcı bu bölümü zaten izlemişse push atma.
                    if (target.progress > 0 && entry.episode > 0 && target.progress >= entry.episode) continue

                    val malKey = "mal_${entry.malId ?: "al${entry.aniListId}"}_${entry.episode}"
                    if (notifiedMalKeys.containsKey(malKey)) continue

                    // Çakışmasız bildirim id'si (eski `malId * 1000 + episode` formülü
                    // malId'siz kayıtlarda çakışıyordu).
                    val notifId = malKey.hashCode() and 0x7fffffff
                    showNotification(
                        id = notifId,
                        title = entry.getDisplayTitle(titleLanguage),
                        bodyText = "Bölüm ${entry.episode} artık yayında! 🎬",
                        source = "MyAnimeList",
                        imageUrl = entry.coverUrl,
                        isMedia = true,
                        mediaId = entry.malId ?: entry.aniListId
                    )
                    notifiedMalKeys[malKey] = System.currentTimeMillis()
                }
                saveTimestampedKeys(sharedPrefs, KEY_NOTIFIED_MAL, notifiedMalKeys)
            } catch (e: Exception) {
                Log.e(TAG, "MAL Airing Calendar fetch failed: ${e.message}", e)
            }
        }

        // 4. Simkl Polling (Watchlist Sync + Release Notifications)
        if (isSimklEnabled) {
            val simklToken = ExternalAuthManager.getSimklToken(context)
            if (!simklToken.isNullOrBlank()) {
                try {
                    val importedEntries = SimklImportManager.fetchAllLists(simklToken)
                    val repository = MediaEntryRepository(db.mediaEntryDao())
                    repository.deleteBySource("simkl")
                    repository.insertAll(importedEntries)
                    Log.d(TAG, "Simkl background sync successful: ${importedEntries.size} entries")

                    // Check calendar for airing items in user's watchlist
                    val KEY_NOTIFIED_SIMKL = "notified_simkl_keys"
                    val notifiedSimklKeys = sharedPrefs.getStringSet(KEY_NOTIFIED_SIMKL, emptySet())?.toMutableSet() ?: mutableSetOf()
                    val simklClient = com.kitsugi.animelist.data.remote.SimklApiClient()
                    val tvCal = simklClient.getCalendar("tv")
                    val animeCal = simklClient.getCalendar("anime")
                    val movieCal = simklClient.getCalendar("movies")
                    val allCal = (tvCal + animeCal + movieCal).distinctBy { it.malId }

                    val relevantSimkl = watchingEntries.filter { me ->
                        me.source.equals("simkl", ignoreCase = true) || me.source.equals("tmdb", ignoreCase = true)
                    }

                    for (calItem in allCal) {
                        val isMatched = relevantSimkl.any { me ->
                            (me.simklId != null && me.simklId == calItem.malId) ||
                            (me.tmdbId != null && calItem.tmdbId != null && me.tmdbId == calItem.tmdbId)
                        }
                        if (isMatched) {
                            val simklKey = "simkl_${calItem.malId}"
                            if (!notifiedSimklKeys.contains(simklKey)) {
                                val notifId = (calItem.malId.hashCode() and 0x7fffffff)
                                showNotification(
                                    id = notifId,
                                    title = calItem.title,
                                    bodyText = "Yeni bölüm / içerik yayında! 🎬",
                                    source = "Simkl",
                                    imageUrl = calItem.imageUrl,
                                    isMedia = true,
                                    mediaId = calItem.malId
                                )
                                notifiedSimklKeys.add(simklKey)
                            }
                        }
                    }
                    val keptSimklKeys = notifiedSimklKeys.toList().takeLast(200).toSet()
                    sharedPrefs.edit().putStringSet(KEY_NOTIFIED_SIMKL, keptSimklKeys).apply()
                } catch (e: Exception) {
                    Log.e(TAG, "Simkl background sync failed: ${e.message}", e)
                }
            }
        }

        return Result.success()
    }

    /**
     * Bildirimi atılmış anahtarları zaman damgasıyla yükler.
     * Eski sürümün `takeLast(200)` budaması sırasız kümede rastgele eleme yapıp
     * aynı bildirimin tekrar atılmasına yol açıyordu; bu yapı en yeni kayıtları korur.
     *
     * @return anahtar → ilk bildirim zamanı (epoch ms) haritası
     */
    private fun loadTimestampedKeys(
        prefs: SharedPreferences,
        key: String,
        maxAgeDays: Int = 60
    ): MutableMap<String, Long> {
        val raw = prefs.getStringSet(key, emptySet()) ?: emptySet()
        val cutoff = System.currentTimeMillis() - maxAgeDays * 24 * 60 * 60 * 1000L
        val out = mutableMapOf<String, Long>()
        for (item in raw) {
            val sep = item.lastIndexOf('|')
            if (sep == -1) {
                // Eski formatsız anahtar (geriye uyumluluk): bugünün damgasıyla koru.
                out[item] = System.currentTimeMillis()
                continue
            }
            val ts = item.substring(sep + 1).toLongOrNull() ?: continue
            if (ts >= cutoff) {
                out[item.substring(0, sep)] = ts
            }
        }
        return out
    }

    /** Zaman damgalı anahtarları kaydeder: en yeni [maxKeep] kayıt tutulur. */
    private fun saveTimestampedKeys(
        prefs: SharedPreferences,
        key: String,
        keys: Map<String, Long>,
        maxKeep: Int = 400
    ) {
        val stamped = keys.entries
            .sortedByDescending { it.value }
            .take(maxKeep)
            .map { "${it.key}|${it.value}" }
            .toSet()
        prefs.edit().putStringSet(key, stamped).apply()
    }

    private fun formatAniListBody(notif: KitsugiAniListNotificationClient.KitsugiNotification): String {
        return when (notif.type) {
            "AIRING" -> "Bölüm ${notif.episode ?: 1} artık yayında! 🎬"
            "FOLLOWING" -> "${notif.userName ?: "Bir kullanıcı"} sizi takip etti."
            "ACTIVITY_MESSAGE" -> "${notif.userName ?: "Bir kullanıcı"} size mesaj gönderdi."
            "ACTIVITY_REPLY" -> "${notif.userName ?: "Bir kullanıcı"} aktivitenize yanıt verdi."
            "ACTIVITY_REPLY_SUBSCRIBED" -> "${notif.userName ?: "Bir kullanıcı"} abone olduğunuz aktiviteye yanıt verdi."
            "ACTIVITY_MENTION" -> "${notif.userName ?: "Bir kullanıcı"} sizden bahsetti."
            "ACTIVITY_LIKE" -> "${notif.userName ?: "Bir kullanıcı"} aktivitenizi beğendi."
            "ACTIVITY_REPLY_LIKE" -> "${notif.userName ?: "Bir kullanıcı"} yanıtınızı beğendi."
            "THREAD_COMMENT_MENTION" -> "${notif.userName ?: "Bir kullanıcı"} forumda sizi etiketledi."
            "THREAD_COMMENT_REPLY" -> "${notif.userName ?: "Bir kullanıcı"} forum yorumunuza yanıt verdi."
            "THREAD_COMMENT_SUBSCRIBED" -> "${notif.userName ?: "Bir kullanıcı"} takip ettiğiniz konuya yorum yaptı."
            "THREAD_COMMENT_LIKE" -> "${notif.userName ?: "Bir kullanıcı"} forum yorumunuzu beğendi."
            "THREAD_LIKE" -> "${notif.userName ?: "Bir kullanıcı"} forum konunuzu beğendi."
            "RELATED_MEDIA_ADDITION" -> "${notif.mediaTitle ?: "İçerik"} için yeni bağlantılı içerik eklendi."
            "MEDIA_DATA_CHANGE" -> "${notif.mediaTitle ?: "İçerik"} verileri güncellendi (${notif.reason ?: "Site veri güncellemesi"})."
            "MEDIA_MERGE" -> "${notif.mediaTitle ?: "İçerik"} başka bir içerikle birleştirildi."
            "MEDIA_DELETION" -> "${notif.deletedMediaTitle ?: "Bir içerik"} siteden silindi."
            else -> notif.context ?: "Yeni bir bildiriminiz var."
        }
    }

    private fun showNotification(
        id: Int,
        title: String,
        bodyText: String,
        source: String,
        imageUrl: String? = null,
        avatarUrl: String? = null,
        isMedia: Boolean = true,
        mediaId: Int? = null
    ) {
        createNotificationChannel()

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("NAV_TARGET", "NOTIFICATIONS")
            if (mediaId != null) putExtra("NAV_MEDIA_ID", mediaId)
            putExtra("NAV_SOURCE", source)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val targetUrl = avatarUrl ?: imageUrl
        val bitmap = if (!targetUrl.isNullOrBlank()) {
            downloadBitmap(targetUrl)
        } else null

        val displayTitle = "[$source] $title"

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(com.kitsugi.animelist.R.mipmap.ic_launcher)
            .setContentTitle(displayTitle)
            .setContentText(bodyText)
            .setSubText(source)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        if (bitmap != null) {
            builder.setLargeIcon(bitmap)
            if (isMedia) {
                builder.setStyle(
                    NotificationCompat.BigPictureStyle()
                        .bigPicture(bitmap)
                        .setBigContentTitle(displayTitle)
                        .setSummaryText(bodyText)
                )
            } else {
                builder.setStyle(
                    NotificationCompat.BigTextStyle()
                        .setBigContentTitle(displayTitle)
                        .bigText(bodyText)
                )
            }
        } else {
            builder.setStyle(
                NotificationCompat.BigTextStyle()
                    .setBigContentTitle(displayTitle)
                    .bigText(bodyText)
            )
        }

        try {
            NotificationManagerCompat.from(context).notify(id, builder.build())
        } catch (e: SecurityException) {
            Log.w(TAG, "Cannot show notification: permission missing: ${e.message}")
        }
    }

    private fun downloadBitmap(urlString: String): android.graphics.Bitmap? {
        return try {
            val url = java.net.URL(urlString)
            val connection = url.openConnection()
            connection.connectTimeout = 7_000
            connection.readTimeout = 7_000
            connection.inputStream.use { stream ->
                android.graphics.BitmapFactory.decodeStream(stream)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Notification image download failed for $urlString: ${e.message}")
            null
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Kitsugi Bildirimleri",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Takip ettiğiniz anime, manga, dizi ve hesap bildirimleri."
            }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }
}
