package com.kitsugi.animelist.data.cloudstream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MAL başlık diğer adları, eklenti aramalarının KAYNAKTAN BAĞIMSIZ aynı başlık evrenini
 * kullanmasını sağlar (AniList'ten gelen İngilizce başlık + MAL Romaji/Türkçe başlık).
 */
class MalTitleAliasProviderTest {

    @Test
    fun parsesAllTitleVariantsFromJikanPayload() {
        val body = """
            {
              "data": {
                "mal_id": 16498,
                "title": "Shingeki no Kyojin",
                "title_english": "Attack on Titan",
                "title_japanese": "進撃の巨人",
                "titles": [
                  { "type": "Default", "title": "Shingeki no Kyojin" },
                  { "type": "Synonym", "title": "AoT" },
                  { "type": "Japanese", "title": "進撃の巨人" },
                  { "type": "English", "title": "Attack on Titan" },
                  { "type": "Turkish", "title": "Titanların Saldırısı" }
                ]
              }
            }
        """.trimIndent()

        val titles = MalTitleAliasProvider.parseTitles(body)

        assertTrue(titles.contains("Shingeki no Kyojin"))
        assertTrue(titles.contains("Attack on Titan"))
        assertTrue(titles.contains("Titanların Saldırısı"))
        // Aynı başlık iki kez dönmez (titles + eski alanlar birleşirken tekilleştirilir).
        assertEquals(titles.size, titles.distinct().size)
    }

    @Test
    fun emptyOrBrokenPayloadYieldsNoAliases() {
        assertEquals(emptyList<String>(), MalTitleAliasProvider.parseTitles("not json"))
        assertEquals(emptyList<String>(), MalTitleAliasProvider.parseTitles("{}"))
        assertEquals(
            emptyList<String>(),
            MalTitleAliasProvider.parseTitles("""{"data":{"titles":[]}}""")
        )
    }

    @Test
    fun aliasListIsBoundedSoSearchQueriesStayCheap() {
        val titles = (1..40).joinToString(",") { """{"type":"Synonym","title":"Alias $it"}""" }
        val body = """{"data":{"title":"Main","titles":[$titles]}}"""

        val parsed = MalTitleAliasProvider.parseTitles(body)

        assertTrue("en fazla 8 diğer ad", parsed.size <= 8)
    }
}
