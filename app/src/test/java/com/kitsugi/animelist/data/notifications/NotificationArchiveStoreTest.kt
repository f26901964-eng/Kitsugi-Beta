package com.kitsugi.animelist.data.notifications

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class NotificationArchiveStoreTest {
    @Test fun missingCloudRowIsDifferentFromAnInvalidCloudResponse() {
        assertTrue(NotificationArchiveStore.fromCloudJsonElement(null).isEmpty())
        assertTrue(NotificationArchiveStore.fromCloudJsonElement(Json.parseToJsonElement("{}")).isEmpty())
        for (invalid in listOf("[]", "{\"sources\":\"broken\"}", "{\"unexpected\":true}")) {
            assertTrue(invalid, runCatching {
                NotificationArchiveStore.fromCloudJsonElement(Json.parseToJsonElement(invalid))
            }.isFailure)
        }
    }

    @Test fun cloudArchiveKeepsBackwardCompatibleOptionalFields() {
        val archive = NotificationArchiveStore.fromCloudJsonElement(Json.parseToJsonElement(
            """{"sources":{"Bangumi":[{"id":"test-notification","title":"Example"}]}}"""
        ))
        assertEquals("Example", archive.getValue("Bangumi").single().title)
        assertNull(archive.getValue("Bangumi").single().mediaTitle)
    }

    @Test fun mergingDeduplicatesAndBoundsEachSource() {
        val old = (0..350).map { NotificationArchiveStore.ArchivedNotif(id = "$it", title = "old", tsMs = it.toLong()) }
        val fresh = listOf(NotificationArchiveStore.ArchivedNotif(id = "350", title = "new", tsMs = 350L))
        val merged = NotificationArchiveStore.mergeLists(old, fresh)
        assertEquals(300, merged.size)
        assertEquals("new", merged.first().title)
        assertEquals(merged.size, merged.map { it.id }.toSet().size)
    }
}
