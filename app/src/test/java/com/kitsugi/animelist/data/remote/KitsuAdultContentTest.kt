package com.kitsugi.animelist.data.remote

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KitsuAdultContentTest {
    @Test
    fun marksExplicitKitsuRatingsAndHentaiGuidesAsAdult() {
        assertTrue(isKitsuAdultContent("R18"))
        assertTrue(isKitsuAdultContent("r18+"))
        assertTrue(isKitsuAdultContent(null, "Hentai"))
    }

    @Test
    fun doesNotTreatGeneralMatureRatingAsAdult() {
        assertFalse(isKitsuAdultContent("R"))
        assertFalse(isKitsuAdultContent("PG"))
        assertFalse(isKitsuAdultContent(null, "Violence and mature themes"))
    }
}
