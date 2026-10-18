package com.kitsugi.animelist.data.cloudstream

import org.junit.Assert.assertEquals
import org.junit.Test

class CsLanguageDetectorTest {

    @Test
    fun turkishVariants_mapToTr() {
        assertEquals("tr", CsLanguageDetector.detectLanguageCode("Türkçe"))
        assertEquals("tr", CsLanguageDetector.detectLanguageCode("Turkish"))
        assertEquals("tr", CsLanguageDetector.detectLanguageCode("tr"))
        assertEquals("tr", CsLanguageDetector.detectLanguageCode("Türkçe Altyazı"))
    }

    @Test
    fun englishVariants_mapToEn() {
        assertEquals("en", CsLanguageDetector.detectLanguageCode("English"))
        assertEquals("en", CsLanguageDetector.detectLanguageCode("eng"))
    }

    @Test
    fun unknownLanguages_are_notTurkish() {
        assertEquals("und", CsLanguageDetector.detectLanguageCode("Deutsch"))
        assertEquals("und", CsLanguageDetector.detectLanguageCode("Arabic"))
        assertEquals("und", CsLanguageDetector.detectLanguageCode(""))
    }
}
