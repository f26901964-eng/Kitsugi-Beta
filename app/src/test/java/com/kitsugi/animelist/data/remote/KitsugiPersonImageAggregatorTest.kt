package com.kitsugi.animelist.data.remote

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KitsugiPersonImageAggregatorTest {
    @Test
    fun nameMatchingSupportsNativeJapaneseAndChineseNames() {
        assertTrue(KitsugiPersonImageAggregator.nameMatches("キョン", "キョン"))
        assertTrue(KitsugiPersonImageAggregator.nameMatches("凉宫春日", "凉宫春日"))
    }

    @Test
    fun nameMatchingNormalizesDiacriticsAndNameOrderWithoutAcceptingPrefixes() {
        assertTrue(KitsugiPersonImageAggregator.nameMatches("Shōta Aoi", "Aoi Shota"))
        assertFalse(KitsugiPersonImageAggregator.nameMatches("Yuki", "Yuki Takeya"))
        assertFalse(KitsugiPersonImageAggregator.nameMatches("Kyon", "Kyonko"))
    }
}
