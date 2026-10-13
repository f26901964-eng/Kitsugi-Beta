package com.kitsugi.animelist.data.remote

import com.kitsugi.animelist.data.local.MediaEntryEntity
import com.kitsugi.animelist.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `BangumiLibraryTitleHydrator`'ın **hangi satırlar için ağ isteği yapılacağını** seçen saf
 * mantığını doğrular.
 *
 * Neden önemli: bu seçim yanlış olursa (a) zaten Latin adı olan kayıtlar için boşuna istek
 * gider ya da (b) CJK adlı kayıtlar hiç çözülmez ve "Listem → Bangumi" listesi yine
 * kullanıcının seçtiği başlık dilini yok sayar.
 */
class BangumiLibraryTitleHydratorTest {

    private fun entity(
        id: Int = 1,
        title: String,
        titleEnglish: String? = null,
        titleJapanese: String? = null,
        malId: Int? = BangumiIdNamespace.stableIdFromRaw(1234),
        source: String = BangumiIdNamespace.SOURCE,
        type: String = "Anime"
    ) = MediaEntryEntity(
        id = id,
        title = title,
        subtitle = "bangumi",
        type = type,
        status = "Planned",
        score = null,
        progress = 0,
        total = null,
        isFavorite = false,
        isAdult = false,
        source = source,
        malId = malId,
        imageUrl = null,
        year = null,
        synopsis = null,
        startDate = null,
        endDate = null,
        notes = null,
        tags = null,
        priority = null,
        isRepeating = false,
        repeatCount = 0,
        repeatValue = 0,
        volumeProgress = 0,
        isPrivate = false,
        isHiddenFromStatusLists = false,
        titleEnglish = titleEnglish,
        titleJapanese = titleJapanese
    )

    @Test
    fun pendingRequests_onlyTargetRowsWithoutAnyLatinTitle() {
        val requests = BangumiLibraryTitleHydrator.pendingRequests(
            listOf(
                // 1) Yalnızca özgün (Japonca) ad → çözülmeli.
                entity(id = 1, title = "進撃の巨人", titleJapanese = "進撃の巨人"),
                // 2) Ad zaten Latin → ağ isteği yapılmamalı.
                entity(id = 2, title = "Shingeki no Kyojin"),
                // 3) İngilizce ad zaten var → ağ isteği yapılmamalı.
                entity(id = 3, title = "進撃の巨人", titleEnglish = "Attack on Titan"),
                // 4) Bangumi kaydı değil → dokunulmamalı.
                entity(id = 4, title = "進撃の巨人", source = "jikan"),
                // 5) Bangumi kimliği yok → çözümleme yapılamaz.
                entity(id = 5, title = "進撃の巨人", malId = null)
            )
        )

        assertEquals(listOf(1234), requests.map { it.rawId })
        assertEquals("進撃の巨人", requests.first().nativeTitle)
        assertEquals(MediaType.Anime, requests.first().mediaType)
    }

    @Test
    fun pendingRequests_deduplicatesSameSubjectAndKeepsCjkNativeTitle() {
        val stableId = BangumiIdNamespace.stableIdFromRaw(1234)
        val requests = BangumiLibraryTitleHydrator.pendingRequests(
            listOf(
                entity(id = 1, title = "進撃の巨人", malId = stableId),
                // Aynı subject iki ayrı satırda (ör. anime + manga kaydı) → tek istek.
                entity(id = 2, title = "進撃の巨人", malId = stableId, type = "Manga"),
                // Legacy ham subject_id de kabul edilir.
                entity(id = 3, title = "進撃の巨人", malId = 1234, titleJapanese = null)
            )
        )

        assertEquals(1, requests.size)
        assertEquals(1234, requests.first().rawId)
        assertTrue(requests.first().nativeTitle.isNotBlank())
    }

    @Test
    fun pendingRequests_mapsMediaTypeForAniListMatching() {
        val requests = BangumiLibraryTitleHydrator.pendingRequests(
            listOf(
                entity(id = 1, title = "進撃の巨人", type = "Manga", malId = BangumiIdNamespace.stableIdFromRaw(111)),
                entity(id = 2, title = "進撃の巨人", type = "Movie", malId = BangumiIdNamespace.stableIdFromRaw(222)),
                entity(id = 3, title = "進撃の巨人", type = "TvShow", malId = BangumiIdNamespace.stableIdFromRaw(333))
            )
        )

        assertEquals(MediaType.Manga, requests.first { it.rawId == 111 }.mediaType)
        assertEquals(MediaType.Movie, requests.first { it.rawId == 222 }.mediaType)
        assertEquals(MediaType.TvShow, requests.first { it.rawId == 333 }.mediaType)
    }

    @Test
    fun pendingRequests_skipsRowsThatCarryOnlyChineseTitleWithoutCjkNative() {
        // Özgün ad alanı boş ama ana başlık CJK ise yine de çözülür (AniList eşleşmesi için
        // elde CJK bir ad gerekir).
        val requests = BangumiLibraryTitleHydrator.pendingRequests(
            listOf(entity(id = 1, title = "這いよれ! ニャル子さん", titleJapanese = null))
        )
        assertEquals(listOf(1234), requests.map { it.rawId })
        assertEquals("這いよれ! ニャル子さん", requests.first().nativeTitle)
    }
}
