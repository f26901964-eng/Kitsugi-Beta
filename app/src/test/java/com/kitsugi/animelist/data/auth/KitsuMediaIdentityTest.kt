package com.kitsugi.animelist.data.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KitsuMediaIdentityTest {
    @Test
    fun parserKeepsOnlyMalMappingsLinkedToTheExactKitsuMedia() {
        val body = """{
            "data": {
                "id": "42",
                "type": "anime",
                "attributes": {
                    "canonicalTitle": "Example Anime",
                    "titles": {"en": "Example Anime"},
                    "startDate": "2021-04-01"
                },
                "relationships": {
                    "mappings": {"data": [{"type": "mappings", "id": "900"}, {"type": "mappings", "id": "902"}]}
                }
            },
            "included": [
                {
                    "id": "900",
                    "type": "mappings",
                    "attributes": {"externalSite": "myanimelist/anime", "externalId": "12345"},
                    "relationships": {"item": {"data": {"id": "42", "type": "anime"}}}
                },
                {
                    "id": "902",
                    "type": "mappings",
                    "attributes": {"externalSite": "myanimelist/anime", "externalId": "77777"},
                    "relationships": {"item": {"data": {"id": "99", "type": "anime"}}}
                },
                {
                    "id": "901",
                    "type": "mappings",
                    "attributes": {"externalSite": "myanimelist/anime", "externalId": "54321"},
                    "relationships": {"item": {"data": {"id": "99", "type": "anime"}}}
                }
            ]
        }"""

        val identity = KitsuApiClient.parseKitsuMediaIdentity(body, 42, isAnime = true)

        assertNotNull(identity)
        assertEquals(setOf(12345), identity?.malIds)
        assertEquals(2021, identity?.year)
    }

    @Test
    fun parserDoesNotPromoteWrongTypeOrUnlinkedMappingsToMalIdentity() {
        val body = """{
            "data": {
                "id": "42",
                "type": "anime",
                "attributes": {"canonicalTitle": "Example Anime", "startDate": "2021-04-01"},
                "relationships": {"mappings": {"data": []}}
            },
            "included": [
                {
                    "id": "900",
                    "type": "mappings",
                    "attributes": {"externalSite": "myanimelist/manga", "externalId": "12345"},
                    "relationships": {"item": {"data": {"id": "42", "type": "anime"}}}
                },
                {
                    "id": "901",
                    "type": "mappings",
                    "attributes": {"externalSite": "myanimelist/anime", "externalId": "54321"},
                    "relationships": {"item": {"data": {"id": "99", "type": "anime"}}}
                }
            ]
        }"""

        val identity = KitsuApiClient.parseKitsuMediaIdentity(body, 42, isAnime = true)

        assertNotNull(identity)
        assertTrue(identity?.malIds.isNullOrEmpty())
        assertNull(KitsuApiClient.parseKitsuMediaIdentity(body, 99, isAnime = true))
        assertNull(KitsuApiClient.parseKitsuMediaIdentity(body, 42, isAnime = false))
    }
}
