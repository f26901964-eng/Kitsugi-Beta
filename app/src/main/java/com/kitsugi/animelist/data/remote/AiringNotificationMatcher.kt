package com.kitsugi.animelist.data.remote

import android.content.Context
import com.kitsugi.animelist.data.auth.ExternalAuthManager
import com.kitsugi.animelist.data.auth.MalImportManager
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.model.WatchStatus
import com.kitsugi.animelist.utils.PreferenceHelpers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Yayın takvimi (AiringEntry) ile kullanıcının izleme listesini eşleştiren ortak yardımcı.
 * Hem uygulama içi MAL bildirim sekmesi ([KitsugiNotificationsViewModel]) hem de
 * arka plan [AiringNotificationWorker] tarafından kullanılır — tek doğruluk kaynağıdır.
 *
 * Eşleşme önceliği:
 *  1. MAL ID (AniList `idMal` ↔ listedeki MAL id)
 *  2. AniList ID (100M offset normalize edilir)
 *  3. Başlık benzerliği (normalize fuzzy — `idMal` boş olan kayıtlar için son çare)
 */
object AiringNotificationMatcher {

    /** Bildirim takibine alınacak liste öğesinin özeti. */
    data class WatchTarget(
        /** Ham MAL id (100M offset'li AniList id'leri hariç, `null` olabilir) */
        val malId: Int?,
        /** Ham AniList id (offset çözülmüş, `null` olabilir) */
        val aniListId: Int?,
        /** Eşleştirme için başlık varyantları (boş olmayanlar) */
        val titles: List<String>,
        /** Kullanıcının izlediği bölüm sayısı */
        val progress: Int,
        /** Teşhis amaçlı görünen başlık */
        val displayTitle: String
    ) {
        /** Toplu eşleştirme için önceden normalize edilmiş başlıklar (boş olmayanlar). */
        val normalizedTitles: List<String> by lazy {
            titles.map { PreferenceHelpers.normalizeTitleForMatch(it) }.filter { it.isNotEmpty() }
        }
    }

    /** Yayın bildirimi takibine girecek öğe mi? (sadece izlenmekte olan animeler) */
    fun MediaEntry.isTrackableForAiring(): Boolean {
        if (type != MediaType.Anime) return false
        return status == WatchStatus.Watching || status == WatchStatus.Repeating
    }

    fun MediaEntry.toWatchTarget(): WatchTarget {
        val raw = malId
        // AniList içe aktarma: `idMal` varsa onu (<100M, gerçek MAL id), yoksa 100M+mediaId yazar.
        // 100M altı AniList id'leri MAL id'dir — AniList id sanılıp eşleştirilmemeli!
        val aniListId = when {
            source.equals("anilist", ignoreCase = true) && raw != null && raw >= 100_000_000 -> raw - 100_000_000
            else -> null
        }
        val pureMalId = when {
            raw == null || raw <= 0 || raw >= 100_000_000 -> null
            source.equals("tmdb", ignoreCase = true) -> null // TMDB id'leri MAL id değildir
            source.equals("simkl", ignoreCase = true) && simklId != null && raw == simklId -> null
            else -> raw
        }
        val titles = listOfNotNull(
            title.takeIf { it.isNotBlank() },
            titleEnglish?.takeIf { it.isNotBlank() },
            titleJapanese?.takeIf { it.isNotBlank() }
        ).distinct()
        return WatchTarget(
            malId = pureMalId,
            aniListId = aniListId,
            titles = titles,
            progress = progress.coerceAtLeast(0),
            displayTitle = title
        )
    }

    /** Takvim girdisi ile izleme hedefi eşleşiyor mu? */
    fun matches(scheduleEntry: AiringEntry, target: WatchTarget): Boolean {
        // 1. MAL ID eşleşmesi (her iki taraf da null-olmayan olmalı — null==null tuzağına düşme!)
        val scheduleMalId = scheduleEntry.malId
        if (scheduleMalId != null && target.malId != null && scheduleMalId == target.malId) {
            return true
        }
        // 2. AniList ID eşleşmesi
        val targetAniListId = target.aniListId
        if (targetAniListId != null && scheduleEntry.aniListId == targetAniListId) {
            return true
        }
        // 3. Başlık benzerliği (idMal boş kayıtlar için son çare)
        if (target.normalizedTitles.isEmpty()) return false
        val scheduleTitles = listOf(
            PreferenceHelpers.normalizeTitleForMatch(scheduleEntry.title),
            PreferenceHelpers.normalizeTitleForMatch(scheduleEntry.titleEnglish),
            PreferenceHelpers.normalizeTitleForMatch(scheduleEntry.titleNative)
        )
        for (scheduleTitle in scheduleTitles) {
            if (scheduleTitle.isEmpty()) continue
            for (targetTitle in target.normalizedTitles) {
                if (PreferenceHelpers.normalizedTitlesMatch(scheduleTitle, targetTitle)) {
                    return true
                }
            }
        }
        return false
    }

    /**
     * MAL izleme hedeflerini çözer:
     *  1. Önce yerel listedeki MAL/Jikan kaynaklı izlenmekte animeler kullanılır.
     *  2. Yerel liste boşsa (hiç senkronize edilmemişse) ve MAL bağlıysa,
     *     MAL API'den doğrudan çekilir — sekme/işçi boş kalmaz.
     */
    suspend fun resolveMalWatchTargets(
        context: Context,
        localEntries: List<MediaEntry>
    ): List<WatchTarget> = withContext(Dispatchers.IO) {
        val localTargets = localEntries
            .filter {
                (it.source.equals("mal", ignoreCase = true) ||
                    it.source.equals("jikan", ignoreCase = true) ||
                    it.source.equals("myanimelist", ignoreCase = true)) &&
                    it.isTrackableForAiring()
            }
            .map { it.toWatchTarget() }
        if (localTargets.isNotEmpty()) return@withContext localTargets

        // Yerel ayna boş → MAL API'den doğrudan dene (liste hiç eşitlenmemiş olabilir).
        val token = try {
            ExternalAuthManager.getOrRefreshMalToken(context.applicationContext)
        } catch (e: Exception) {
            android.util.Log.w("AiringMatcher", "MAL token alınamadı: ${e.message}")
            null
        } ?: return@withContext emptyList()

        try {
            // nsfw=true: kullanıcı zaten izlediği için yetişkin filtresi uygulanmaz.
            MalImportManager.fetchAnimeList(token, showAdultContent = true)
                .filter { it.isTrackableForAiring() }
                .map { it.toWatchTarget() }
        } catch (e: Exception) {
            android.util.Log.w("AiringMatcher", "MAL API listesi alınamadı: ${e.message}")
            emptyList()
        }
    }

    /**
     * Arka plan işçisi için TÜM kaynaklardaki izlenmekte animeleri hedeflere çevirir
     * (AniList/MAL/Simkl/Kitsu/Shikimori/TMDB aynaları).
     */
    fun allWatchTargets(localEntries: List<MediaEntry>): List<WatchTarget> {
        return localEntries
            .filter { it.isTrackableForAiring() }
            .map { it.toWatchTarget() }
    }
}
