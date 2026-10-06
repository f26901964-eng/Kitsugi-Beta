package com.kitsugi.animelist.data.remote

import android.util.Log
import com.kitsugi.animelist.model.MediaEntry
import java.util.Calendar

/**
 * MyAnimeList bildirim hattının ortak veri katmanı.
 * Hem uygulama içi Bildirimler > MAL sekmesi ([KitsugiNotificationsViewModel])
 * hem de arka plan sistem bildirimleri ([AiringNotificationWorker]) bu yardımcıyı
 * kullanır; böylece iki yüzey her zaman tutarlı sonuç üretir.
 *
 * Tasarım:
 *  1. Birincil kaynak AniList `airingSchedules` — KAYAN pencere ile
 *     (son N gün + gelecek M gün). Eski takvim-haftası yaklaşımı haftanın ilk
 *     günlerinde (örn. Salı) neredeyse her zaman boş sonuç veriyordu.
 *  2. AniList başarısız olur veya boş dönerse yedek kaynak Simkl anime takvimi
 *     (CDN JSON, auth gerektirmez, MAL ID içerir) denenir.
 *  3. Eşleştirme üç aşamalıdır: MAL ID → AniList offset ID → normalize başlık.
 *     Böylece malId'si eksik/hatalı kayıtlar da başlık üzerinden yakalanır.
 *  4. [MalAiringResult.failed] yalnızca hiçbir kaynaktan takvim alınamadığında
 *     true olur — çağıran bu durumda hata göstermelidir ("yayın yok" değil).
 */
object MalAiringNotifications {

    private const val TAG = "MalAiringNotif"

    data class MalAiringResult(
        /** Pencerede yayınlanmış bölümler (yeniden eskiye sıralı) */
        val aired: List<AiringEntry>,
        /** Pencerede yayınlanacak bölümler (yakından uzağa sıralı) */
        val upcoming: List<AiringEntry>,
        /** true ise hiçbir kaynaktan takvim alınamadı */
        val failed: Boolean
    )

    suspend fun fetchForEntries(
        entries: List<MediaEntry>,
        pastSeconds: Long = 7 * 24 * 3600L,
        upcomingSeconds: Long = 3 * 24 * 3600L,
        accessToken: String? = null
    ): MalAiringResult {
        if (entries.isEmpty()) return MalAiringResult(emptyList(), emptyList(), failed = false)
        val nowSeconds = System.currentTimeMillis() / 1000L

        // ── 1) Birincil kaynak: AniList kayan pencere ────────────────────────
        val calendarClient = KitsugiAiringCalendarClient()
        var schedule: List<AiringEntry> = emptyList()
        var anilistFailed = false
        try {
            val window = calendarClient.fetchAiringWindow(
                airingAtGreater = nowSeconds - pastSeconds,
                airingAtLesser = nowSeconds + upcomingSeconds.coerceAtLeast(1),
                accessToken = accessToken,
                maxPages = 12
            )
            schedule = window.entries
            anilistFailed = window.failed
        } catch (e: Exception) {
            Log.e(TAG, "AniList window fetch failed: ${e.message}")
            anilistFailed = true
        }

        // ── 2) Yedek kaynak: Simkl anime takvimi ─────────────────────────────
        var usedFallback = false
        if ((anilistFailed || schedule.isEmpty()) && entries.any { (it.malId ?: 0) > 0 }) {
            val fallback = fetchSimklFallback()
            if (fallback != null) {
                Log.d(TAG, "Simkl fallback active: ${fallback.size} entries")
                usedFallback = true
                anilistFailed = false
                schedule = fallback
            }
        }

        if (schedule.isEmpty()) {
            return MalAiringResult(
                aired = emptyList(),
                upcoming = emptyList(),
                failed = anilistFailed && !usedFallback
            )
        }

        // ── 3) Eşleştirme ───────────────────────────────────────────────────
        val matched = matchEntries(entries, schedule)
        Log.d(TAG, "Matched ${matched.size} airing entries for ${entries.size} watchlist entries")

        val aired = matched
            .filter { it.airingAt <= nowSeconds }
            .sortedByDescending { it.airingAt }
        val upcoming = if (upcomingSeconds > 0) {
            matched
                .filter { it.airingAt > nowSeconds }
                .sortedBy { it.airingAt }
        } else {
            emptyList()
        }
        return MalAiringResult(aired = aired, upcoming = upcoming, failed = false)
    }

    // ── Eşleştirme ───────────────────────────────────────────────────────────

    private fun matchEntries(
        entries: List<MediaEntry>,
        schedule: List<AiringEntry>
    ): List<AiringEntry> {
        val byMalId = schedule.filter { it.malId != null }.groupBy { it.malId }
        val byAniListId = schedule.groupBy { it.aniListId }
        // Başlık indeksi (malId'siz kayıtlar için yedek eşleştirme)
        val byTitle = mutableMapOf<String, MutableList<AiringEntry>>()
        schedule.forEach { entry ->
            listOf(entry.title, entry.titleEnglish).forEach { candidate ->
                val key = normalizeTitle(candidate)
                if (key.length >= 4) {
                    byTitle.getOrPut(key) { mutableListOf() }.add(entry)
                }
            }
        }

        val matched = linkedSetOf<AiringEntry>()
        entries.forEach { media ->
            var hits: List<AiringEntry>? = null
            val mid = media.malId

            // a) MAL ID eşleşmesi (birincil)
            if (mid != null && mid > 0 && mid < 100_000_000) {
                hits = byMalId[mid]
            }
            // b) AniList offset ID eşleşmesi (AniList kaynaklı kayıtlar için)
            if (hits.isNullOrEmpty() &&
                media.source.equals("anilist", ignoreCase = true) &&
                mid != null && mid >= 100_000_000
            ) {
                hits = byAniListId[mid - 100_000_000]
            }
            // c) Normalize başlık eşleşmesi (malId eksik/hatalı kayıtlar için)
            if (hits.isNullOrEmpty()) {
                val keys = listOf(media.title, media.titleEnglish)
                    .map { normalizeTitle(it) }
                    .filter { it.length >= 4 }
                    .distinct()
                for (key in keys) {
                    val byTitleHits = byTitle[key]
                    if (!byTitleHits.isNullOrEmpty()) {
                        hits = byTitleHits
                        break
                    }
                }
            }
            if (!hits.isNullOrEmpty()) matched.addAll(hits)
        }
        return matched.toList()
    }

    /** Başlıkları karşılaştırılabilir forma indirger (küçük harf + alfanümerik). */
    fun normalizeTitle(title: String?): String {
        if (title.isNullOrBlank()) return ""
        return title.lowercase().filter { it in 'a'..'z' || it in '0'..'9' }
    }

    // ── Simkl yedek kaynağı ──────────────────────────────────────────────────

    private suspend fun fetchSimklFallback(): List<AiringEntry>? {
        return try {
            val items = SimklApiClient().getCalendar("anime")
            if (items.isEmpty()) return null
            val nowSeconds = System.currentTimeMillis() / 1000L
            items.mapNotNull { item ->
                val malId = item.realMalId ?: return@mapNotNull null
                val (episode, airingAt) = item.nextAiringEpisode
                    ?.split("|")
                    ?.let { parts ->
                        val ep = parts.getOrNull(0)?.toIntOrNull() ?: 0
                        val at = parts.getOrNull(1)?.toLongOrNull()
                        ep to at
                    } ?: (0 to null)
                // Tarihi ayrıştırılamayan kayıtları "yakında" kabul et
                val resolvedAiringAt = airingAt ?: (nowSeconds + 1)
                AiringEntry(
                    aniListId = item.malId,
                    malId = malId,
                    title = item.title,
                    titleEnglish = item.titleEnglish,
                    titleNative = null,
                    coverUrl = item.imageUrl,
                    episode = episode,
                    airingAt = resolvedAiringAt,
                    dayOfWeek = Calendar.getInstance().apply {
                        timeInMillis = resolvedAiringAt * 1000L
                    }.get(Calendar.DAY_OF_WEEK),
                    averageScore = item.score?.let { (it * 10).coerceIn(0, 100) }
                )
            }.takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            Log.e(TAG, "Simkl fallback failed: ${e.message}")
            null
        }
    }
}
