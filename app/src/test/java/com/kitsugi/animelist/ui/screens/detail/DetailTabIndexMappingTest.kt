package com.kitsugi.animelist.ui.screens.detail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DetailTabIndexMappingTest {
    @Test
    fun videoPageDoesNotTriggerRemoteTabLoading() {
        assertNull(detailDataTabIndexForPage(DETAIL_VIDEO_PAGE_INDEX))
    }

    @Test
    fun pagesAroundVideoTabKeepTheirLegacyDataIndexes() {
        assertEquals(0, detailDataTabIndexForPage(0)) // Info
        assertEquals(1, detailDataTabIndexForPage(1)) // Images
        assertEquals(2, detailDataTabIndexForPage(3)) // Characters
        assertEquals(3, detailDataTabIndexForPage(4)) // Staff
        assertEquals(7, detailDataTabIndexForPage(8)) // Reviews
        assertEquals(8, detailDataTabIndexForPage(9)) // Episodes
    }
}
