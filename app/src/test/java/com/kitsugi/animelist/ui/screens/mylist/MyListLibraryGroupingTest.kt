package com.kitsugi.animelist.ui.screens.mylist

import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.model.WatchStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MyListLibraryGroupingTest {

    private fun entry(
        id: Int,
        title: String,
        source: String,
        malId: Int? = null,
        type: MediaType = MediaType.Anime,
        year: Int? = 2024
    ) = MediaEntry(
        id = id,
        title = title,
        type = type,
        status = WatchStatus.Planned,
        score = null,
        progress = 0,
        total = null,
        source = source,
        malId = malId,
        year = year
    )

    @Test
    fun groupsSameTitleAcrossProvidersAndCollectsTheirBadges() {
        val mal = entry(id = 12, title = "Frieren: Beyond Journey's End", source = "mal", malId = 52991)
        val anilist = entry(id = 10, title = "Sousou no Frieren", source = "anilist", malId = 100_000_870)
            .copy(titleEnglish = "Frieren: Beyond Journey's End")
        val kitsu = entry(id = 8, title = "Frieren: Beyond Journey's End", source = "kitsu", malId = 300_123_456)

        val grouped = groupMyListEntries(listOf(mal, anilist, kitsu))

        assertEquals(1, grouped.size)
        // Yeni kural: Temsilci önceliği AniList > MAL > Kitsu > Shikimori > Simkl > TMDB
        assertEquals(anilist.id, grouped.single().entry.id)
        assertEquals(listOf("mal", "anilist", "kitsu"), grouped.single().sourceIds)
    }


    @Test
    fun combinedRepresentativeKeepsAdultFlagFromAnyProvider() {
        val shikimori = entry(id = 12, title = "Same title", source = "shikimori", malId = 52991)
            .copy(isAdult = true)
        val anilist = entry(id = 10, title = "Same title", source = "anilist", malId = 52991)

        val grouped = groupMyListEntries(listOf(shikimori, anilist))

        assertEquals(1, grouped.size)
        assertEquals(anilist.id, grouped.single().entry.id)
        assertTrue("Combined card should stay blurred if any provider marks it adult", grouped.single().entry.isAdult)
        assertEquals(listOf("shikimori", "anilist"), grouped.single().sourceIds)
    }

    @Test
    fun doesNotGroupDifferentMediaTypesOrConflictingProviderIds() {
        val first = entry(id = 4, title = "Same Title", source = "mal", malId = 1234)
        val otherMalRecord = entry(id = 3, title = "Same Title", source = "mal", malId = 5678)
        val manga = entry(id = 2, title = "Same Title", source = "kitsu", malId = 300_000_004, type = MediaType.Manga)

        val grouped = groupMyListEntries(listOf(first, otherMalRecord, manga))

        assertEquals(3, grouped.size)
        assertFalse(grouped.any { it.sourceIds.size > 1 })
    }

    @Test
    fun bangumiCopyMergesIntoCombinedLibraryViaCrosswalkKeys() {
        val anilist = entry(id = 10, title = "Sousou no Frieren", source = "anilist", malId = 52991)
            .copy(titleEnglish = "Frieren: Beyond Journey's End")
        // Bangumi içe aktarması yalnızca bgm.tv'nin kendi kimliğini ve Çince katalog başlığını taşır.
        val bangumi = entry(id = 20, title = "葬送のフリーレン", source = "bangumi", malId = 500_057_162)

        // Without the crosswalk the same production is counted twice (the reported inflation).
        assertEquals(2, groupMyListEntries(listOf(anilist, bangumi)).size)

        val grouped = groupMyListEntries(
            entries = listOf(anilist, bangumi),
            identityExtras = mapOf(bangumi.id to setOf("Anime:mal:52991"))
        )

        assertEquals(1, grouped.size)
        assertEquals("AniList stays the combined tile's representative", anilist.id, grouped.single().entry.id)
        assertEquals(listOf("anilist", "bangumi"), grouped.single().sourceIds)
    }

    @Test
    fun crosswalkInheritsAdultFlagAndDoesNotInventCrossTypeMerges() {
        val bangumi = entry(id = 21, title = "葬送のフリーレン", source = "bangumi", malId = 500_057_162)
            .copy(isAdult = true)
        val manga = entry(id = 22, title = "Sousou no Frieren", source = "mal", malId = 52991, type = MediaType.Manga)

        // The crosswalk key is only ever offered for the row's own media type.
        val grouped = groupMyListEntries(listOf(manga, bangumi), mapOf(bangumi.id to setOf("Anime:mal:52991")))
        assertEquals(2, grouped.size)
    }

    @Test
    fun bangumiTabOnlyShowsStableBangumiCollectionEntries() {
        val importedBangumi = entry(
            id = 3,
            title = "Bangumi collection item",
            source = "bgm.tv",
            malId = 500_000_876
        )
        val manualLocal = entry(
            id = 2,
            title = "Local-only item",
            source = "bangumi",
            malId = null
        )
        val simklCopy = entry(
            id = 1,
            title = "Same title from another provider",
            source = "simkl",
            malId = 500_000_876
        )

        assertTrue(myListSourceMatchesTab(MY_LIST_BANGUMI_TAB_INDEX, importedBangumi.source, importedBangumi.malId))
        assertFalse(myListSourceMatchesTab(MY_LIST_BANGUMI_TAB_INDEX, manualLocal.source, manualLocal.malId))
        assertFalse(myListSourceMatchesTab(MY_LIST_BANGUMI_TAB_INDEX, simklCopy.source, simklCopy.malId))
        assertEquals(1, listOf(importedBangumi, manualLocal, simklCopy).count { isBangumiListMember(it.source, it.malId) })
    }

    @Test
    fun simklMovieAndAnimeCopiesCollapseIntoOneTileInTheCombinedLibrary() {
        // Simkl aynı anime filmini `movies` ve `anime` uçlarında döndürür → Movie + Anime satırı.
        val asMovie = entry(id = 30, title = "Demon Slayer: Infinity Castle", source = "simkl")
            .copy(type = MediaType.Movie, simklId = 2098300, total = 1, progress = 1)
        val asAnime = entry(id = 31, title = "Demon Slayer: Infinity Castle", source = "simkl")
            .copy(type = MediaType.Anime, simklId = 2098300, total = 1, progress = 1)

        val grouped = groupMyListEntries(listOf(asMovie, asAnime))

        assertEquals("aynı film tek kart olmalı", 1, grouped.size)
        assertEquals("temsilci Anime etiketini korur", MediaType.Anime, grouped.single().entry.type)
        assertEquals(listOf("simkl"), grouped.single().sourceIds)
    }

    @Test
    fun perSourceCollapseRemovesDuplicatesInsideATabButNeverAcrossSources() {
        val simklMovie = entry(id = 40, title = "Attack on Titan", source = "simkl")
            .copy(type = MediaType.Movie, simklId = 1234)
        val simklAnime = entry(id = 41, title = "Attack on Titan", source = "simkl")
            .copy(type = MediaType.Anime, simklId = 1234)
        val anilistCopy = entry(id = 42, title = "Attack on Titan", source = "anilist", malId = 100_000_164)

        val sourceScoped = groupMyListEntries(
            entries = listOf(simklMovie, simklAnime, anilistCopy),
            collapseAcrossSources = false
        )

        assertEquals("aynı kaynağın kopyası erir, farklı kaynak korunur", 2, sourceScoped.size)
        assertEquals(2, sourceScoped.count { it.sourceIds.size == 1 })
    }

    @Test
    fun staleCrosswalkHintCannotSplitTwoIdenticalTitles() {
        // Ekran görüntüsündeki Kaiju No. 8 / Attack on Titan çiftleri: bir satırın çapraz kimlik
        // önbelleğinde kalan BAYAT bir mal:/kitsu: değeri, aynı başlığı taşıyan iki grubun
        // birleşmesini "kimlik çakışması" sayıp engelliyordu.
        val anilist = entry(id = 50, title = "Kaiju No. 8 Season 2", source = "anilist", malId = 100_000_999)
        val mal = entry(id = 51, title = "Kaiju No. 8 Season 2", source = "mal", malId = 52991)
        val simkl = entry(id = 52, title = "Kaiju No. 8 Season 2", source = "simkl")
            .copy(simklId = 4188123, malId = null)

        val grouped = groupMyListEntries(
            entries = listOf(anilist, mal, simkl),
            identityExtras = mapOf(simkl.id to setOf("Anime:mal:999999", "Anime:kitsu:777"))
        )

        assertEquals("ipucu çakışması birleşmeyi engellemez", 1, grouped.size)
        assertEquals(anilist.id, grouped.single().entry.id)
        assertEquals(listOf("anilist", "mal", "simkl"), grouped.single().sourceIds)
    }

    @Test
    fun perSourceCollapseHonoursBangumiSubjectProof() {
        val animeSubject = entry(id = 60, title = "葬送のフリーレン", source = "bangumi", malId = 500_057_162)
        val duplicateSubject = entry(id = 61, title = "葬送のフリーレン", source = "bangumi", malId = 500_057_162)

        val grouped = groupMyListEntries(listOf(animeSubject, duplicateSubject), collapseAcrossSources = false)

        assertEquals(1, grouped.size)
        assertEquals(1, grouped.single().sourceIds.size)
    }

    @Test
    fun normalizesProviderAliasesForBadges() {
        val grouped = groupMyListEntries(
            listOf(
                entry(id = 2, title = "Alias title", source = "jikan", malId = 999),
                entry(id = 1, title = "Alias title", source = "myanimelist", malId = 1000)
            )
        )

        assertEquals(2, grouped.size) // Conflicting MAL IDs must remain separate.
        assertTrue(grouped.all { it.sourceIds == listOf("mal") })
        assertEquals("mal", sourceBadgeId("Jikan"))
        assertEquals("shikimori", sourceBadgeId("Shiki"))
        assertEquals(null, sourceBadgeId("manual"))
    }
}
