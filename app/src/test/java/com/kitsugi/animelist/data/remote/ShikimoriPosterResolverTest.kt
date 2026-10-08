package com.kitsugi.animelist.data.remote

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.ServerSocket
import java.nio.charset.StandardCharsets
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors

/**
 * Shikimori kapak (poster) çözümleme davranışını doğrular.
 *
 * Arka plan: REST `image` alanı eski Paperclip ekine bakar ve yeni yapımlarda
 * `/assets/globals/missing_original.jpg` ("404 not found" görseli) döner. Gerçek
 * kapaklar GraphQL `poster` alanındadır.
 */
class ShikimoriPosterResolverTest {

    @Before
    fun setUp() {
        ShikimoriPosterResolver.clearCache()
        ShikimoriPosterResolver.hostOverride = null
    }

    @After
    fun tearDown() {
        ShikimoriPosterResolver.clearCache()
        ShikimoriPosterResolver.hostOverride = null
    }

    // ─── Yer tutucu tespiti ───────────────────────────────────────────────

    @Test
    fun missingPlaceholdersAreRejected() {
        // Paperclip default_url'ünün tüm boyut türevleri
        assertTrue(ShikimoriPosterResolver.isMissingImage("/assets/globals/missing_original.jpg"))
        assertTrue(ShikimoriPosterResolver.isMissingImage("/assets/globals/missing_preview.jpg"))
        assertTrue(ShikimoriPosterResolver.isMissingImage("/assets/globals/missing_x96.jpg"))
        assertTrue(ShikimoriPosterResolver.isMissingImage("/assets/globals/missing_avatar/x160.png"))
        assertTrue(
            ShikimoriPosterResolver.isMissingImage("https://shikimori.one/assets/globals/missing_original.jpg")
        )
        assertTrue(ShikimoriPosterResolver.isMissingImage("/assets/globals/missing_original.jpg?1727000000"))

        assertTrue(ShikimoriPosterResolver.isMissingImage(null))
        assertTrue(ShikimoriPosterResolver.isMissingImage(""))
        assertTrue(ShikimoriPosterResolver.isMissingImage("   "))
        assertTrue(ShikimoriPosterResolver.isMissingImage("null"))

        assertFalse(ShikimoriPosterResolver.isMissingImage("/system/animes/original/58567.jpg"))
    }

    @Test
    fun placeholderUrlsBecomeNullSoUiCanFallBack() {
        // null → kart baş harf gösterir; birleşik "Tümü" görünümünde AniList/MAL
        // kaydı temsilci seçilebilir. Boş string dönseydi 404 görseli indirilirdi.
        assertNull(ShikimoriPosterResolver.absoluteUrl("/assets/globals/missing_original.jpg"))
        assertNull(ShikimoriPosterResolver.absoluteUrl(null))
        assertNull(ShikimoriPosterResolver.absoluteUrl(""))
    }

    // ─── Adres normalleştirme ─────────────────────────────────────────────

    @Test
    fun relativePathsResolveAgainstCanonicalHost() {
        assertEquals(
            "https://shikimori.io/system/animes/original/58567.jpg?1727000000",
            ShikimoriPosterResolver.absoluteUrl("/system/animes/original/58567.jpg?1727000000")
        )
        assertEquals("https://shikimori.io", ShikimoriPosterResolver.HOST)
    }

    @Test
    fun absoluteAndProtocolRelativeUrlsArePreserved() {
        // GraphQL zaten mutlak CDN adresi döndürür; dokunulmamalı.
        assertEquals(
            "https://nyaa.shikimori.one/system/posters/original/58567.jpg",
            ShikimoriPosterResolver.absoluteUrl("https://nyaa.shikimori.one/system/posters/original/58567.jpg")
        )
        assertEquals(
            "https://dere.shikimori.one/x.jpg",
            ShikimoriPosterResolver.absoluteUrl("//dere.shikimori.one/x.jpg")
        )
    }

    // ─── GraphQL sorgusu ──────────────────────────────────────────────────

    @Test
    fun animeAndMangaQueriesUseCommaSeparatedStringIds() {
        assertEquals(
            "{ animes(ids: \"1,2,3\", limit: 3, censored: false) { id poster { originalUrl mainUrl } } }",
            ShikimoriPosterResolver.buildQuery(ShikimoriPosterResolver.Kind.ANIME, listOf(1, 2, 3))
        )
        assertEquals(
            "{ mangas(ids: \"7\", limit: 1, censored: false) { id poster { originalUrl mainUrl } } }",
            ShikimoriPosterResolver.buildQuery(ShikimoriPosterResolver.Kind.MANGA, listOf(7))
        )
    }

    @Test
    fun characterAndPersonQueriesUseIdListArgument() {
        // Shikimori şemasında characters/people `ids: [ID]` alır, animes/mangas String.
        assertEquals(
            "{ characters(ids: [11,12], limit: 2) { id poster { originalUrl mainUrl } } }",
            ShikimoriPosterResolver.buildQuery(ShikimoriPosterResolver.Kind.CHARACTER, listOf(11, 12))
        )
        assertEquals(
            "{ people(ids: [99], limit: 1) { id poster { originalUrl mainUrl } } }",
            ShikimoriPosterResolver.buildQuery(ShikimoriPosterResolver.Kind.PERSON, listOf(99))
        )
    }

    // ─── Yanıt ayrıştırma ─────────────────────────────────────────────────

    @Test
    fun parseResponseReadsPostersAndTreatsNullPosterAsEmpty() {
        val body = """
            {"data":{"animes":[
              {"id":"58567","poster":{"originalUrl":"https://nyaa.shikimori.one/system/posters/original/58567.jpg",
                                      "mainUrl":"https://nyaa.shikimori.one/system/posters/main/58567.webp"}},
              {"id":"42","poster":null}
            ]}}
        """.trimIndent()

        val parsed = ShikimoriPosterResolver.parseResponse(body, ShikimoriPosterResolver.Kind.ANIME)
        assertNotNull(parsed)
        assertEquals(2, parsed!!.size)

        val withPoster = parsed[58567]!!
        assertEquals(
            "https://nyaa.shikimori.one/system/posters/main/58567.webp",
            withPoster.cardUrl
        )
        assertEquals(
            "https://nyaa.shikimori.one/system/posters/original/58567.jpg",
            withPoster.fullUrl
        )
        assertFalse(withPoster.isEmpty)

        assertTrue(parsed[42]!!.isEmpty)
        assertNull(parsed[42]!!.cardUrl)
    }

    @Test
    fun unusableResponsesReturnNullInsteadOfEmptyMap() {
        // null = "bilinmiyor, tekrar sorulabilir"; emptyMap = "kapak yok" demek olurdu.
        assertNull(ShikimoriPosterResolver.parseResponse(null, ShikimoriPosterResolver.Kind.ANIME))
        assertNull(ShikimoriPosterResolver.parseResponse("", ShikimoriPosterResolver.Kind.ANIME))
        assertNull(ShikimoriPosterResolver.parseResponse("<html>502</html>", ShikimoriPosterResolver.Kind.ANIME))
        assertNull(
            ShikimoriPosterResolver.parseResponse(
                """{"errors":[{"message":"Field 'poster' doesn't exist"}]}""",
                ShikimoriPosterResolver.Kind.ANIME
            )
        )
    }

    // ─── Uçtan uca: toplu çözüm + önbellek ────────────────────────────────

    @Test
    fun resolveBatchesIdsInOneRequestAndCachesResult() {
        val serverSocket = ServerSocket(0)
        val port = serverSocket.localPort
        val requestBodies = CopyOnWriteArrayList<String>()
        val executor = Executors.newSingleThreadExecutor()

        val responseJson = "{\"data\":{\"animes\":[" +
            "{\"id\":\"58567\",\"poster\":{\"originalUrl\":\"https://nyaa.shikimori.io/system/posters/original/58567.jpg\"," +
            "\"mainUrl\":\"https://nyaa.shikimori.io/system/posters/main/58567.webp\"}}," +
            "{\"id\":\"42\",\"poster\":null}]}}"

        executor.submit {
            try {
                while (true) {
                    val socket = serverSocket.accept()
                    val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
                    requestBodies.add(readHttpBody(reader))
                    val writer = PrintWriter(OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true)
                    val length = responseJson.toByteArray(StandardCharsets.UTF_8).size
                    writer.print(
                        "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n" +
                            "Content-Length: $length\r\nConnection: close\r\n\r\n$responseJson"
                    )
                    writer.flush()
                    socket.close()
                }
            } catch (_: Exception) {
                // serverSocket kapandığında döngüden çıkılır
            }
        }

        try {
            ShikimoriPosterResolver.hostOverride = "http://127.0.0.1:$port"

            val first = runBlocking {
                ShikimoriPosterResolver.resolve(ShikimoriPosterResolver.Kind.ANIME, listOf(58567, 42))
            }

            assertEquals(1, requestBodies.size)
            assertTrue(
                "Tüm kimlikler tek sorguda gitmeli: ${requestBodies[0]}",
                requestBodies[0].contains("animes(ids: \\\"58567,42\\\"")
            )
            assertEquals(
                "https://nyaa.shikimori.io/system/posters/main/58567.webp",
                first[58567]?.cardUrl
            )
            // Shikimori'de gerçekten kapağı olmayan kayıt null kalır (404 görseli değil).
            assertNull(first[42]?.cardUrl)

            // Aynı kimlikler yeniden istendiğinde ağa çıkılmaz.
            val second = runBlocking {
                ShikimoriPosterResolver.resolve(ShikimoriPosterResolver.Kind.ANIME, listOf(58567, 42))
            }
            assertEquals(1, requestBodies.size)
            assertEquals(first[58567]?.cardUrl, second[58567]?.cardUrl)
        } finally {
            serverSocket.close()
            executor.shutdownNow()
        }
    }

    @Test
    fun networkFailureIsNotCachedAsMissingPoster() {
        // Bağlantı kurulamayan port: sonuç boş olmalı ama önbelleğe "kapak yok" yazılmamalı.
        val freePort = ServerSocket(0).use { it.localPort }
        ShikimoriPosterResolver.hostOverride = "http://127.0.0.1:$freePort"

        val result = runBlocking {
            ShikimoriPosterResolver.resolve(ShikimoriPosterResolver.Kind.ANIME, listOf(58567))
        }
        assertTrue(result.isEmpty())
    }

    /** HTTP isteğinin başlıklarını atlayıp gövdesini okur. */
    private fun readHttpBody(reader: BufferedReader): String {
        var contentLength = 0
        while (true) {
            val line = reader.readLine() ?: break
            if (line.isEmpty()) break
            if (line.startsWith("Content-Length:", ignoreCase = true)) {
                contentLength = line.substringAfter(":").trim().toIntOrNull() ?: 0
            }
        }
        if (contentLength <= 0) return ""
        val buffer = CharArray(contentLength)
        var read = 0
        while (read < contentLength) {
            val count = reader.read(buffer, read, contentLength - read)
            if (count < 0) break
            read += count
        }
        return String(buffer, 0, read)
    }
}
