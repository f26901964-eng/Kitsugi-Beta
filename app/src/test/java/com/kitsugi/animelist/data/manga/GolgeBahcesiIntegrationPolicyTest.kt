package com.kitsugi.animelist.data.manga

import com.kitsugi.animelist.data.manga.sources.golgebahcesi.GolgeBahcesiSource
import com.kitsugi.animelist.data.remote.MangaRepoClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GolgeBahcesiIntegrationPolicyTest {

    private val dummyGolgeSource = object : MangaSource {
        override val name: String = GolgeBahcesiSource.SOURCE_NAME
        override val baseUrl: String = GolgeBahcesiSource.DEFAULT_BASE_URL
        override val lang: String = "tr"
        override val pkgName: String = GolgeBahcesiSource.PKG_NAME
        override val isBuiltIn: Boolean = true
        override suspend fun fetchPopularManga(page: Int) = MangaSourceResult(emptyList(), false)
        override suspend fun fetchSearchManga(page: Int, query: String) = MangaSourceResult(emptyList(), false)
        override suspend fun fetchMangaDetails(mangaUrl: String) = MangaDetails(mangaUrl, "")
        override suspend fun fetchChapterList(mangaUrl: String) = emptyList<MangaChapter>()
        override suspend fun fetchPageList(chapter: MangaChapter) = emptyList<MangaPage>()
    }

    @Test
    fun `matches english anilist queries against turkish golge bahcesi alternativeTitles`() {
        val shadowSlave = MangaDetails(
            url = "/manga/golge-kole",
            title = "Gölge Köle",
            source = GolgeBahcesiSource.SOURCE_NAME,
            alternativeTitles = listOf("Shadow Slave"),
        )
        val infiniteMage = MangaDetails(
            url = "/manga/mutlak-buyucu",
            title = "Mutlak Büyücü",
            source = GolgeBahcesiSource.SOURCE_NAME,
            alternativeTitles = listOf("Infinite Mage", "무한의 마법사"),
        )

        val simShadow = MangaTitleMatcher.getBestSimilarityScore("Shadow Slave", shadowSlave)
        assertTrue("Expected exact match on alternativeTitles, got $simShadow", simShadow >= 1.0)

        val queryShadow = CanonicalMangaResolver.resolve("Shadow Slave")
        val scoreShadow = SearchResultScorer.score(dummyGolgeSource, queryShadow, shadowSlave)
        val minAcceptedShadow = SearchResultScorer.minAcceptedScore(dummyGolgeSource, queryShadow)
        assertTrue(
            "Expected score ($scoreShadow) >= minAccepted ($minAcceptedShadow)",
            scoreShadow >= minAcceptedShadow
        )

        val queryMage = CanonicalMangaResolver.resolve("Infinite Mage")
        val scoreMage = SearchResultScorer.score(dummyGolgeSource, queryMage, infiniteMage)
        val minAcceptedMage = SearchResultScorer.minAcceptedScore(dummyGolgeSource, queryMage)
        assertTrue(
            "Expected score ($scoreMage) >= minAccepted ($minAcceptedMage)",
            scoreMage >= minAcceptedMage
        )
    }

    @Test
    fun `kotatsu adapter disables legacy golgebahcesi and defaults to zero active languages`() {
        KotatsuExtensionAdapter.setActiveLangs(emptySet())
        assertTrue(KotatsuExtensionAdapter.getActiveLangs().isEmpty())
        assertTrue(KotatsuExtensionAdapter.getActiveSources().isEmpty())
        assertTrue(KotatsuExtensionAdapter.isParserDisabled("GOLGEBAHCESI"))
        assertTrue(KotatsuExtensionAdapter.isParserDisabled("golgebahcesi"))
        assertFalse(KotatsuExtensionAdapter.isParserDisabled("MANGADEX"))

        // Son aktif dil de kapatılabilmeli (eski size > 1 kilidi kaldırıldı)
        KotatsuExtensionAdapter.toggleLang("tr")
        assertEquals(setOf("tr"), KotatsuExtensionAdapter.getActiveLangs())
        KotatsuExtensionAdapter.toggleLang("tr")
        assertTrue(KotatsuExtensionAdapter.getActiveLangs().isEmpty())
    }

    @Test
    fun `kitsugi manga plugins repo url normalization and default catalog`() {
        assertTrue(MangaRepoClient.isKitsugiMangaRepoUrl(MangaRepoClient.KITSUGI_MANGA_REPO_URL))
        assertTrue(MangaRepoClient.isKitsugiMangaRepoUrl("https://github.com/f26901964-eng/Kitsugi-Manga-Plugins"))
        assertFalse(MangaRepoClient.isKitsugiMangaRepoUrl(MangaRepoClient.KEIYOUSHI_INDEX_URL))

        assertEquals(
            MangaRepoClient.KITSUGI_MANGA_REPO_URL,
            MangaRepoClient.normalizeMangaRepoUrl("https://github.com/f26901964-eng/Kitsugi-Manga-Plugins")
        )
        assertEquals(
            "Kitsugi Manga Plugins",
            MangaRepoClient.displayNameForRepo(MangaRepoClient.KITSUGI_MANGA_REPO_URL)
        )

        val defaultCatalog = MangaRepoClient.defaultKitsugiMangaCatalog()
        assertEquals(1, defaultCatalog.size)
        val golge = defaultCatalog.single()
        assertEquals(GolgeBahcesiSource.PKG_NAME, golge.pkg)
        assertEquals(GolgeBahcesiSource.SOURCE_NAME, golge.name)
        assertEquals("tr", golge.lang)
        assertEquals(GolgeBahcesiSource.VERSION_CODE.toInt(), golge.versionCode)
    }

    @Test
    fun `turkish source registry gives golge bahcesi top priority`() {
        val policy = TurkishSourceRegistry.policyFor(dummyGolgeSource)
        assertTrue(policy.isTurkishPreferred)
        assertTrue(policy.isTrusted)
        assertTrue("Expected priority >= 180, got ${policy.priority}", policy.priority >= 180)
    }
}
