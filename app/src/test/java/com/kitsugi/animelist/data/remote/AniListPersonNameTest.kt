package com.kitsugi.animelist.data.remote

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class AniListPersonNameTest {
    @Test
    fun usesLatinNameInsteadOfAccountPreferredJapanese() {
        val name = JSONObject("""{
            "userPreferred":"エレン・イェーガー", "full":"エレン・イェーガー",
            "first":"Eren", "last":"Yeager", "native":"エレン・イェーガー"
        }""").aniListPersonName()

        assertEquals("Eren Yeager", name.display("ROMAJI"))
        assertEquals("Eren Yeager", name.display("ENGLISH"))
        assertEquals("エレン・イェーガー", name.display("NATIVE"))
        assertEquals("エレン・イェーガー", name.display("JAPANESE_STAFF"))
    }

    @Test
    fun usesLatinAliasWhenComponentsAreNotLatin() {
        val name = JSONObject("""{
            "userPreferred":"梶裕貴", "full":"梶裕貴", "native":"梶裕貴",
            "alternative":["Yuki Kaji", "かじ ゆうき"]
        }""").aniListPersonName()

        assertEquals("Yuki Kaji", name.display("ROMAJI"))
        assertEquals("梶裕貴", name.display("NATIVE"))
    }

    @Test
    fun keepsAvailableNameWhenNoTranslationExists() {
        val name = JSONObject("""{"userPreferred":"未知", "native":"未知"}""").aniListPersonName()
        assertEquals("未知", name.display("ENGLISH"))
        assertEquals("Eren", displayPersonName("エレン", "Eren", "エレン", "ROMAJI"))
        assertEquals("エレン", displayPersonName("エレン", "Eren", "エレン", "NATIVE"))
    }
}
