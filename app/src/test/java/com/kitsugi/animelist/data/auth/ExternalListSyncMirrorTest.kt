package com.kitsugi.animelist.data.auth

import com.kitsugi.animelist.data.remote.BangumiIdNamespace
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaIdentity
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.model.WatchStatus
import com.kitsugi.animelist.ui.components.badgeSources
import com.kitsugi.animelist.ui.screens.mylist.groupMyListEntries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Verifies `E98`: after `ExternalListSyncManager.syncEntry(...)` succeeds (`OUTCOME_SYNCED`),
 * each synced platform gets a canonical local `media_entries` mirror row whose `source` and `malId`
 * match the exact provider ID namespace contract and merge into ONE unified `Listem` card with all
 * 6 synced provider badges (`AL`, `MAL`, `Simkl`, `Kitsu`, `Shikimori`, `Bangumi`).
 */
class ExternalListSyncMirrorTest {

    @Test
    fun allSixSyncedProvidersProduceCanonicalLocalMirrorsAndSingleUnifiedCardWithAllSixBadges() {
        val baseAniListCard = MediaEntry(
            id = 10,
            title = "Kaijuu 8-gou",
            titleEnglish = "Kaiju No. 8",
            titleJapanese = "怪獣８号",
            imageUrl = "https://s4.anilist.co/file/anilistcdn/media/anime/cover/medium/bx153288.jpg",
            type = MediaType.Anime,
            status = WatchStatus.Completed,
            score = 9,
            progress = 12,
            total = 12,
            source = "anilist",
            malId = 52588,
            aniListId = 153288,
            year = 2024
        )
        val crossIds = CrossProviderIds(
            malId = 52588,
            aniListId = 153288,
            kitsuId = 46298,
            shikimoriId = 52588,
            bangumiSubjectId = 400_602,
            simklId = 1_972_969
        )

        val providers = listOf("anilist", "mal", "simkl", "kitsu", "shikimori", "bangumi")
        val mirrors = providers.mapIndexed { idx, provider ->
            val mirror = ExternalListSyncManager.buildLocalProviderMirrorEntry(
                provider = provider,
                sourceEntry = baseAniListCard,
                crossIds = crossIds,
                existingId = idx + 1
            )
            assertNotNull("Expected non-null local mirror for $provider", mirror)
            mirror!!
        }

        val byProvider = mirrors.associateBy { it.source }
        assertEquals(52588, byProvider.getValue("mal").malId)
        assertEquals(52588, byProvider.getValue("shikimori").malId)
        assertEquals(300_046_298, byProvider.getValue("kitsu").malId)
        assertEquals(46298, MediaIdentity.kitsuId(byProvider.getValue("kitsu")))
        assertEquals(BangumiIdNamespace.toStableId(400_602), byProvider.getValue("bangumi").malId)
        assertEquals(400_602, MediaIdentity.bangumiId(byProvider.getValue("bangumi")))

        // Group inside `Listem > Tümü` (same pipeline rendered by MyListScreen)
        val grouped = groupMyListEntries(mirrors)
        assertEquals(
            "All 6 provider mirrors must collapse into ONE Kaiju No. 8 card in Listem",
            1,
            grouped.size
        )

        val unifiedCard = grouped.single()
        val badges = badgeSources(unifiedCard).toSet()
        assertEquals(
            setOf("anilist", "mal", "simkl", "kitsu", "shikimori", "bangumi"),
            badges
        )
    }
}
