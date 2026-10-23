package com.kitsugi.animelist.data.auth

import com.kitsugi.animelist.data.local.MyListIdentityCrosswalk
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.model.WatchStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * End-to-end deterministic verification of cross-platform clustering, identity guard,
 * crosswalk ID extraction, and false-merge protection (`E96`–`E101`).
 */
class CrossSyncClusterEngineTest {

    private fun kaijuSixPlatformEntries(): List<MediaEntry> = listOf(
        // AniList (primary card in user screenshot: AL, Completed, 12/12, 9/10)
        MediaEntry(
            id = 0,
            title = "Kaijuu 8-gou",
            titleEnglish = "Kaiju No. 8",
            titleJapanese = "怪獣８号",
            type = MediaType.Anime,
            status = WatchStatus.Completed,
            score = 9,
            progress = 12,
            total = 12,
            source = "anilist",
            malId = 52588,
            year = 2024
        ),
        // MAL
        MediaEntry(
            id = 0,
            title = "Kaijuu 8-gou",
            titleEnglish = "Kaiju No. 8",
            titleJapanese = "怪獣８号",
            type = MediaType.Anime,
            status = WatchStatus.Completed,
            score = 9,
            progress = 12,
            total = 12,
            source = "mal",
            malId = 52588,
            year = 2024
        ),
        // Simkl (often missing year on list items)
        MediaEntry(
            id = 0,
            title = "Kaiju No. 8",
            type = MediaType.Anime,
            status = WatchStatus.Completed,
            score = 9,
            progress = 12,
            total = 12,
            source = "simkl",
            malId = 151_972_969,
            year = null
        ),
        // Kitsu (300M + 46298)
        MediaEntry(
            id = 0,
            title = "Kaiju No. 8",
            titleJapanese = "怪獣8号",
            type = MediaType.Anime,
            status = WatchStatus.Completed,
            score = 9,
            progress = 12,
            total = 12,
            source = "kitsu",
            malId = 300_046_298,
            year = 2024
        ),
        // Shikimori (targetId == MAL ID == 52588, romaji title without English)
        MediaEntry(
            id = 0,
            title = "Kaijuu 8-gou",
            type = MediaType.Anime,
            status = WatchStatus.Completed,
            score = 9,
            progress = 12,
            total = 12,
            source = "shikimori",
            malId = 52588,
            year = 2024
        ),
        // Bangumi (500M + 400602, Simplified Chinese display title, null year)
        MediaEntry(
            id = 0,
            title = "怪兽8号",
            type = MediaType.Anime,
            status = WatchStatus.Completed,
            score = 9,
            progress = 12,
            total = 12,
            source = "bangumi",
            malId = 500_400_602,
            year = null
        )
    )

    private fun <T> permutations(items: List<T>): List<List<T>> {
        if (items.size <= 1) return listOf(items)
        val result = ArrayList<List<T>>()
        for (i in items.indices) {
            val head = items[i]
            val rest = items.subList(0, i) + items.subList(i + 1, items.size)
            for (tail in permutations(rest)) {
                result.add(listOf(head) + tail)
            }
        }
        return result
    }

    @Test
    fun kaijuNo8All720PlatformPermutationsMergeIntoSingleSixPlatformCluster() {
        val rawEntries = kaijuSixPlatformEntries()
        val subjectToMal = MyListIdentityCrosswalk.reverseBangumiMapping(
            mapOf("bgm_a_52588" to 400_602)
        )
        val perms = permutations(rawEntries)
        assertEquals(720, perms.size)

        for (perm in perms) {
            val assigned = CrossSyncClusterEngine.assignClusterRowIds(perm)
            // Verify every row receives a distinct synthetic positive ID even when input id == 0
            assertEquals(6, assigned.map { it.id }.toSet().size)

            val extras = assigned.associate { entry ->
                entry.id to MyListIdentityCrosswalk.extraKeysFor(entry, null, subjectToMal)
            }.filterValues { it.isNotEmpty() }

            val clusters = CrossSyncClusterEngine.clusterEntries(assigned, extras)
            assertEquals(
                "Permutation ${perm.map { it.source }} split Kaiju No. 8 into ${clusters.size} clusters!",
                1,
                clusters.size
            )
            val cluster = clusters.single()
            assertEquals(6, cluster.size)
            assertEquals(52588, CrossSyncClusterEngine.extractCrosswalkOrCandidateMalId(cluster, extras))

            val verdict = CrossSyncIdentityGuard.evaluate(
                winner = cluster.first(),
                candidates = cluster,
                crossResolvedMalId = 52588,
                crossResolvedTmdbId = null
            )
            assertTrue(
                "Verdict $verdict blocked bulk cross-sync addition for permutation ${perm.map { it.source }}",
                CrossSyncIdentityGuard.allowsBulkAddition(verdict)
            )
        }
    }

    @Test
    fun bridgeEntryCoalescesPreviouslyDisjointProviderClusters() {
        // Bangumi (pure Japanese) and Kitsu (English only, no crosswalk yet) start in separate clusters;
        // AniList arrives third carrying both English ("Kaiju No. 8") and Japanese ("怪獣８号") aliases.
        val bangumi = MediaEntry(
            id = 1,
            title = "怪獣8号",
            type = MediaType.Anime,
            status = WatchStatus.Completed,
            progress = 12,
            total = 12,
            source = "bangumi",
            malId = 500_400_602,
            year = null
        )
        val kitsu = MediaEntry(
            id = 2,
            title = "Kaiju No. 8",
            type = MediaType.Anime,
            status = WatchStatus.Completed,
            progress = 12,
            total = 12,
            source = "kitsu",
            malId = 300_046_298,
            year = 2024
        )
        val anilistBridge = MediaEntry(
            id = 3,
            title = "Kaijuu 8-gou",
            titleEnglish = "Kaiju No. 8",
            titleJapanese = "怪獣８号",
            type = MediaType.Anime,
            status = WatchStatus.Completed,
            progress = 12,
            total = 12,
            source = "anilist",
            malId = 52588,
            year = 2024
        )

        val clusters = CrossSyncClusterEngine.clusterEntries(listOf(bangumi, kitsu, anilistBridge))
        assertEquals(1, clusters.size)
        assertEquals(setOf("bangumi", "kitsu", "anilist"), clusters.single().map { it.source }.toSet())
    }

    @Test
    fun remakeWithDifferentYearNeverMergesEvenAcrossBridge() {
        val hxh1999 = MediaEntry(
            id = 1,
            title = "Hunter x Hunter",
            type = MediaType.Anime,
            status = WatchStatus.Completed,
            progress = 62,
            total = 62,
            source = "mal",
            malId = 136,
            year = 1999
        )
        val hxh2011 = MediaEntry(
            id = 2,
            title = "Hunter x Hunter",
            type = MediaType.Anime,
            status = WatchStatus.Completed,
            progress = 148,
            total = 148,
            source = "kitsu",
            malId = 300_006_448,
            year = 2011
        )

        val clusters = CrossSyncClusterEngine.clusterEntries(listOf(hxh1999, hxh2011))
        assertEquals(2, clusters.size)
    }

    @Test
    fun suspiciousSharedIdCandidateWithUnrelatedTitleIsDetectedAndBlocked() {
        val kaijuAl = MediaEntry(
            id = 1,
            title = "Kaijuu 8-gou",
            titleEnglish = "Kaiju No. 8",
            type = MediaType.Anime,
            status = WatchStatus.Completed,
            progress = 12,
            total = 12,
            source = "anilist",
            malId = 52588,
            year = 2024
        )
        val poisonedEntry = MediaEntry(
            id = 2,
            title = "Boku no Pico Academia Z",
            type = MediaType.Anime,
            status = WatchStatus.Watching,
            progress = 3,
            total = 50,
            source = "shikimori",
            malId = 52588,
            year = 2019
        )

        val suspicious = CrossSyncIdentityGuard.findSuspiciousSharedIdCandidates(
            listOf(kaijuAl, poisonedEntry)
        )
        assertTrue(suspicious.isNotEmpty())

        val verdict = CrossSyncIdentityGuard.evaluate(
            winner = kaijuAl,
            candidates = listOf(kaijuAl, poisonedEntry),
            crossResolvedMalId = 52588,
            crossResolvedTmdbId = null
        )
        assertFalse(CrossSyncIdentityGuard.allowsBulkAddition(verdict))
    }

    @Test
    fun structuralTokenGouPreventsNumberedSequelCollision() {
        assertFalse(CrossSyncIdentityGuard.titlesVariantRelated("Kaijuu 8-gou", "Kaijuu 9-gou"))
        assertFalse(CrossSyncIdentityGuard.titlesVariantRelated("Kaijuu 8-gou", "Kaijuu 8-gou 2nd Season"))
        assertTrue(CrossSyncIdentityGuard.titlesVariantRelated("Kaijuu 8-gou", "Kaijuu 8-gou"))
    }
}
