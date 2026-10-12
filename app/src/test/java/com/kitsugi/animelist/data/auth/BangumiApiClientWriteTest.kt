package com.kitsugi.animelist.data.auth

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.Buffer
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Bangumi koleksiyon **yazma** istekleri — `Content-Type` sözleşmesi (2026-10-12, v2.4.261).
 *
 * Belirti: her `Listeye Ekle` / ilerleme yazması
 * `HTTP 415 Unsupported Media Type — request with body must set "content-type" header to "application/json"`.
 *
 * Kök neden: sunucu (`bangumi/server` → `web/req/content_type.go`) başlığı **tam dize eşitliğiyle**
 * denetler (`application/json` **veya** `application/json; charset=UTF-8`). OkHttp'nin
 * `String.toRequestBody(mediaType)` yolu tipe `; charset=utf-8` (küçük harf) ekler ve
 * `BridgeInterceptor` elle konan başlığı gövdenin tipiyle ezer → sunucu
 * `application/json; charset=utf-8` görür → 415. Arama ucu bu ara katmanı kullanmadığı için
 * arama çalışırken yazma hep düşüyordu.
 *
 * Bu testler [BangumiApiClient.buildJsonRequestBody] çıktısının telde birebir
 * `application/json` üreteceğini garanti eder (Mihon/Aniyomi ile aynı sözleşme).
 */
class BangumiApiClientWriteTest {

    /** `web/req/content_type.go` → `req.JSON` ara katmanının kabul ettiği iki değer, başka yok. */
    private val serverAcceptedContentTypes = setOf(
        "application/json",
        "application/json; charset=UTF-8"
    )

    @Test
    fun jsonRequestBody_contentTypeIsExactlyApplicationJson() {
        val body = BangumiApiClient.buildJsonRequestBody(
            JSONObject().put("type", BangumiApiClient.CollectionType.DOING).put("private", false)
        )
        val contentType = body.contentType()

        assertEquals("application/json", contentType.toString())
        assertNull("charset parametresi eklenmemeli (OkHttp tuzağı)", contentType?.charset())
        assertTrue(
            "sunucunun tam-eşitlik denetimini geçmeli: ${contentType}",
            contentType.toString() in serverAcceptedContentTypes
        )
    }

    @Test
    fun jsonRequestBody_emptyProbeBodyKeepsExactJsonContentType() {
        // probeCollectionWrite(): PATCH ... gövde `{}`; req.JSON yine de başlığı denetler.
        val body = BangumiApiClient.buildJsonRequestBody(JSONObject())

        assertEquals("application/json", body.contentType().toString())
        assertEquals("{}", body.readUtf8())
    }

    @Test
    fun jsonRequestBody_nullBodyIsEmptyButStillJsonTyped() {
        val body = BangumiApiClient.buildJsonRequestBody(null)

        assertEquals("application/json", body.contentType().toString())
        assertEquals(0L, body.contentLength())
        assertEquals("", body.readUtf8())
    }

    @Test
    fun jsonRequestBody_writesUtf8BytesAndDeclaresExactLength() {
        val payload = JSONObject()
            .put("comment", "Türkçe not — 日本語 — 😀")
            .put("tags", org.json.JSONArray(listOf("恋爱", "key社")))
        val body = BangumiApiClient.buildJsonRequestBody(payload)

        val expected = payload.toString()
        assertEquals(expected, body.readUtf8())
        assertEquals(expected.toByteArray(Charsets.UTF_8).size.toLong(), body.contentLength())
    }

    /**
     * Regresyon belgesi: eski `send()` tam olarak bunu yapıyordu. Bu test, OkHttp'nin
     * davranışı değişmediği sürece eski yolun **neden** 415 aldığını kanıtlar; biri gün gelip
     * `String.toRequestBody(JSON_MEDIA_TYPE)`'a geri dönerse yukarıdaki testler kırılır.
     */
    @Test
    fun legacyStringToRequestBody_appendsLowercaseCharsetWhichServerRejects() {
        val legacy = "{}".toRequestBody("application/json".toMediaType())
        val onTheWire = legacy.contentType().toString()

        assertEquals("application/json; charset=utf-8", onTheWire)
        assertFalse(
            "sunucu bu değeri kabul etmez (büyük/küçük harf duyarlı tam eşitlik)",
            onTheWire in serverAcceptedContentTypes
        )
    }

    @Test
    fun jsonContentTypeConstant_isServerAcceptedValue() {
        assertEquals("application/json", BangumiApiClient.JSON_CONTENT_TYPE)
        assertTrue(BangumiApiClient.JSON_CONTENT_TYPE in serverAcceptedContentTypes)
    }

    private fun RequestBody.readUtf8(): String = Buffer().also { writeTo(it) }.readUtf8()
}
