package com.kitsugi.animelist.data.auth

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Bangumi arama düzeltmeleri:
 *  - `filter.nsfw` JSON **boolean** olmalı (eskiden `"include"` metni gidiyor, sunucu HTTP 400 verip
 *    aramayı tamamen boş gösteriyordu),
 *  - sunucu sayfa başına en fazla 20 kayıt verir (istemci 24 isteyip offset'i yanlış hesaplıyordu),
 *  - legacy `GET /search/subject/{q}` yedeği doğru ayrıştırılmalı (http görselleri https'e yükseltilir).
 */
class BangumiApiClientSearchTest {

    private fun payload(nsfw: Boolean?, types: List<Int> = listOf(2)) = BangumiApiClient.buildSearchSubjectsPayload(
        keyword = "date a live",
        sort = BangumiApiClient.SearchSort.MATCH,
        types = types,
        tags = emptyList(),
        airDate = emptyList(),
        rating = emptyList(),
        rank = emptyList(),
        nsfw = nsfw
    )

    @Test
    fun searchPayload_sendsNsfwAsJsonBoolean() {
        val filter = payload(nsfw = false).getJSONObject("filter")
        val value = filter.get("nsfw")
        assertTrue("nsfw JSON boolean olmalı, metin değil: $value", value is Boolean)
        assertEquals(false, value)
        assertEquals(true, payload(nsfw = true).getJSONObject("filter").get("nsfw"))
    }

    @Test
    fun searchPayload_omitsNsfwWhenUnspecified() {
        val filter = payload(nsfw = null).getJSONObject("filter")
        assertFalse(filter.has("nsfw"))
        assertEquals(1, filter.getJSONArray("type").length())
    }

    @Test
    fun searchPayload_hasNoFilterObjectWhenNothingToFilter() {
        val body = payload(nsfw = null, types = emptyList())
        assertFalse(body.has("filter"))
        assertEquals("date a live", body.getString("keyword"))
        assertEquals("match", body.getString("sort"))
    }

    @Test
    fun searchPageSize_matchesServerSoftLimit() {
        assertEquals(20, BangumiApiClient.SEARCH_PAGE_SIZE)
    }

    @Test
    fun parseLegacySearchResponse_mapsItemsAndUpgradesImageScheme() {
        val body = """
            {"results":2,"list":[
              {"id":51,"url":"http://bgm.tv/subject/51","type":2,"name":"デート・ア・ライブ","name_cn":"约会大作战",
               "summary":"精灵","air_date":"2013-04-05","air_weekday":5,"eps":12,"rank":1500,
               "images":{"large":"http://lain.bgm.tv/pic/cover/l/aa/bb/51_x.jpg","common":"http://lain.bgm.tv/pic/cover/c/aa/bb/51_x.jpg","medium":"http://lain.bgm.tv/pic/cover/m/aa/bb/51_x.jpg","small":"http://lain.bgm.tv/pic/cover/s/aa/bb/51_x.jpg","grid":"http://lain.bgm.tv/pic/cover/g/aa/bb/51_x.jpg"},
               "rating":{"total":4000,"score":7.4},"collection":{"wish":10,"collect":20,"doing":3,"on_hold":1,"dropped":2}},
              {"id":777,"url":"http://bgm.tv/subject/777","type":2,"name":"Bilinmeyen","name_cn":"","summary":"","air_date":"0000-00-00","images":null}
            ]}
        """.trimIndent()
        val page = BangumiApiClient.parseLegacySearchResponse(body, limit = 20, offset = 0)
        assertEquals(2, page.total)
        assertEquals(2, page.data.size)

        val first = page.data[0]
        assertEquals(51, first.id)
        assertEquals("约会大作战", first.displayTitle)
        assertEquals("デート・ア・ライブ", first.name)
        assertEquals("2013-04-05", first.date)
        assertEquals(12, first.eps)
        assertEquals(7.4, first.rating!!.score, 0.0001)
        assertEquals(36, first.collection!!.total)
        assertEquals(
            "https://lain.bgm.tv/pic/cover/c/aa/bb/51_x.jpg",
            BangumiApiClient.absoluteImageUrl(first.images?.poster)
        )

        // "0000-00-00" = bilinmeyen tarih → boş tarih (yıl 0 olarak görünmemeli)
        val second = page.data[1]
        assertEquals(777, second.id)
        assertTrue(second.date.isNullOrBlank())
        assertNull(second.images)
    }

    @Test
    fun parseLegacySearchResponse_toleratesEmptyAndBrokenBodies() {
        assertEquals(0, BangumiApiClient.parseLegacySearchResponse("""{"results":0,"list":null}""", 20, 0).data.size)
        assertEquals(0, BangumiApiClient.parseLegacySearchResponse("""{"request":"/search/subject/x","code":404,"error":"Not Found"}""", 20, 0).data.size)
        assertEquals(0, BangumiApiClient.parseLegacySearchResponse("<html>502</html>", 20, 0).data.size)
        assertEquals(0, BangumiApiClient.parseLegacySearchResponse("", 20, 40).data.size)
    }

    @Test
    fun absoluteImageUrl_normalizesSchemes() {
        assertEquals("https://lain.bgm.tv/a.jpg", BangumiApiClient.absoluteImageUrl("http://lain.bgm.tv/a.jpg"))
        assertEquals("https://lain.bgm.tv/a.jpg", BangumiApiClient.absoluteImageUrl("//lain.bgm.tv/a.jpg"))
        assertEquals("https://bgm.tv/img/x.png", BangumiApiClient.absoluteImageUrl("/img/x.png"))
        assertEquals("https://lain.bgm.tv/a.jpg", BangumiApiClient.absoluteImageUrl("https://lain.bgm.tv/a.jpg"))
        assertNull(BangumiApiClient.absoluteImageUrl(""))
        assertNull(BangumiApiClient.absoluteImageUrl(null))
    }
}
