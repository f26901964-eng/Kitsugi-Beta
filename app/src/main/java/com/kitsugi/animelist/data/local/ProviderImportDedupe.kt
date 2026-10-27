package com.kitsugi.animelist.data.local

import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaIdentity
import com.kitsugi.animelist.model.MediaType

/**
 * İÇE AKTARIM TEKİLLEŞTİRME — aynı yapımın bir kaynakta iki satır olmasını engeller.
 *
 * ## Kök neden
 * Simkl `GET /sync/all-items/{movies|shows|anime}` üç ayrı kategori döndürür ve **aynı öğe
 * birden fazla kategoride** yer alabilir (anime filmleri `movies` + `anime`; anime dizileri
 * `shows` + `anime`). [com.kitsugi.animelist.data.auth.SimklImportManager] üç listeyi
 * birleştirdiği için aynı `simklId` iki kez geliyordu. Yerel eşleştirme `MediaIdentity.sameMedia`
 * **tür eşitliği** istediğinden `Movie` satırı ile `Anime` satırı hiç eşleşmiyor ve
 * `smartImport` ikinci satırı EKLEME olarak yazıyordu. Sonuç: Listem → Simkl sekmesinde ve
 * "Tümü"nde aynı film/dizi iki kart (bkz. ekran görüntülerindeki Demon Slayer / Kaiju /
 * Attack on Titan çiftleri).
 *
 * ## Çözüm
 * İçe aktarım listesi yazılmadan önce aynı **kaynak + kanıtlanmış kimlik** (Simkl `simklId`,
 * Bangumi `subject_id`) taşıyan kayıtlar tek satıra indirilir. Tür etiketi farklıysa `Anime`
 * etiketi korunur (bölüm ilerlemesi ve çapraz eşitleme anime ekseninde çalışır).
 *
 * Başlık benzerliği burada **kanıt sayılmaz** (`allowTitle = false`): yalnızca gerçek kimlik
 * eşleşmesi birleştirir, böylece "aynı adı taşıyan iki ayrı yapım" asla tek satıra düşmez.
 */
internal object ProviderImportDedupe {

    /**
     * Aynı yapımı iki kez taşıyan içe aktarım listesini tekilleştirir (sıra korunur).
     *
     * PERF: kimlik anahtarları satır başına BİR kez hesaplanır — 800 satırlık bir Simkl
     * içe aktarımında çift başına `MediaIdentity.keys` çağırmak içe aktarımı saniyelerce
     * uzatıyordu.
     */
    fun collapse(entries: List<MediaEntry>): List<MediaEntry> {
        if (entries.size < 2) return entries
        val result = ArrayList<MediaEntry>(entries.size)
        val resultKeys = ArrayList<Set<String>>(entries.size)
        val resultSources = ArrayList<String>(entries.size)
        entries.forEach { entry ->
            val entryKeys = MediaIdentity.keys(entry)
            val entrySource = MediaIdentity.canonicalSource(entry.source)
            var existingIndex = -1
            for (index in result.indices) {
                if (resultSources[index] != entrySource) continue
                if (MediaIdentity.sameProduction(result[index], entry, resultKeys[index], entryKeys, allowTitle = false)) {
                    existingIndex = index
                    break
                }
            }
            if (existingIndex < 0) {
                result.add(entry)
                resultKeys.add(entryKeys)
                resultSources.add(entrySource)
            } else {
                result[existingIndex] = prefer(result[existingIndex], entry)
                resultKeys[existingIndex] = resultKeys[existingIndex] + entryKeys
            }
        }
        return result
    }

    /** Aynı kaynağın, kanıtlanmış kimlikle aynı yapımı gösteren iki satırı mı? */
    internal fun isSameProviderRecord(a: MediaEntry, b: MediaEntry): Boolean {
        if (MediaIdentity.canonicalSource(a.source) != MediaIdentity.canonicalSource(b.source)) return false
        return MediaIdentity.sameProduction(a, b, allowTitle = false)
    }

    /**
     * İki kopyadan hangisinin **alanları** tutulur: `Anime` etiketi > `TvShow` > `Movie` >
     * `Manga`; eşit türde görsel/özet gibi alanları dolu olan kopya.
     *
     * Seçilen kayıt diğerinin EKSİK ALANLARINI devralır: kopyalardan biri yalnızca kimlik
     * (ör. `simklId`), diğeri afiş/özet taşıyabilir; seçimi "hepsini ya da hiçbirini" gibi
     * uygulamak kullanıcının gördüğü kartı boşaltırdı.
     */
    internal fun prefer(current: MediaEntry, candidate: MediaEntry): MediaEntry {
        val currentRank = typeRank(current.type)
        val candidateRank = typeRank(candidate.type)
        val winner: MediaEntry
        val loser: MediaEntry
        if (currentRank != candidateRank) {
            winner = if (candidateRank < currentRank) candidate else current
            loser = if (winner === candidate) current else candidate
        } else {
            val currentRichness = richness(current)
            val candidateRichness = richness(candidate)
            val candidateWins = when {
                candidateRichness != currentRichness -> candidateRichness > currentRichness
                else -> candidate.progress > current.progress
            }
            winner = if (candidateWins) candidate else current
            loser = if (candidateWins) current else candidate
        }
        return winner.copy(
            // Hiçbir kullanıcı verisi gerilemez.
            progress = maxOf(winner.progress, loser.progress),
            volumeProgress = maxOf(winner.volumeProgress, loser.volumeProgress),
            isFavorite = winner.isFavorite || loser.isFavorite,
            isAdult = winner.isAdult || loser.isAdult,
            score = winner.score ?: loser.score,
            notes = winner.notes?.takeIf { it.isNotBlank() } ?: loser.notes,
            startDate = winner.startDate ?: loser.startDate,
            endDate = winner.endDate ?: loser.endDate,
            // Görsel/meta alanları tamamlanır.
            imageUrl = winner.imageUrl?.takeIf { it.isNotBlank() } ?: loser.imageUrl,
            synopsis = winner.synopsis?.takeIf { it.isNotBlank() } ?: loser.synopsis,
            subtitle = winner.subtitle.ifBlank { loser.subtitle },
            titleEnglish = winner.titleEnglish?.takeIf { it.isNotBlank() } ?: loser.titleEnglish,
            titleJapanese = winner.titleJapanese?.takeIf { it.isNotBlank() } ?: loser.titleJapanese,
            total = winner.total ?: loser.total,
            year = winner.year ?: loser.year,
            aniListEntryId = winner.aniListEntryId ?: loser.aniListEntryId,
            tmdbId = winner.tmdbId ?: loser.tmdbId,
            simklId = winner.simklId ?: loser.simklId,
            updatedAt = maxOf(winner.updatedAt, loser.updatedAt)
        )
    }

    private fun typeRank(type: MediaType): Int = when (type) {
        MediaType.Anime -> 0
        MediaType.TvShow -> 1
        MediaType.Movie -> 2
        MediaType.Manga -> 3
    }

    private fun richness(entry: MediaEntry): Int {
        var score = 0
        if (!entry.imageUrl.isNullOrBlank()) score += 2
        if (!entry.synopsis.isNullOrBlank()) score += 2
        if (!entry.titleEnglish.isNullOrBlank()) score += 1
        if (!entry.titleJapanese.isNullOrBlank()) score += 1
        if (entry.total != null) score += 1
        if (entry.year != null) score += 1
        if (entry.aniListEntryId != null) score += 1
        return score
    }
}
