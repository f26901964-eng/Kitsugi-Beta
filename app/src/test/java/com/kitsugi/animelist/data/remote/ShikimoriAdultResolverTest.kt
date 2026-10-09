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
 * Shikimori +18 (rx/hentai) çözümleme davranışını doğrular.
 *
 * Arka plan: REST liste yanıtları (`/api/animes`, `/api/mangas` ve `user_rates` gömülü
 * nesneleri) `rating`/`genres` alanlarını taşımaz; bu yüzden +18 tespiti GraphQL
 * üzerinden (`rating` + `genres { name }`, `censored: false`) yapılır.
 */
class ShikimoriAdultResolverTest {

    @Before
    fun setUp() {
        ShikimoriAdultResolver.clearCache()
        ShikimoriAdultResolver.hostOverride = null
    }

    @After
    fun tearDown() {
        ShikimoriAdultResolver.clearCache()
        ShikimoriAdultResolver.hostOverride = null
    }

    // ─── GraphQL sorgusu ──────────────────────────────────────────────────

    @Test
    fun animeQueryRequestsRatingAndGenresWithCensoredFalse() {
        assertEquals(
            "{ animes(ids: \"1,2,3\", limit: 3, censored: false) { id rating genres { name } } }",
            ShikimoriAdultResolver.buildQuery(ShikimoriAdultResolver.Kind.ANIME, listOf(1, 2, 3))
        )
    }

    @Test
    fun mangaQueryOmitsRatingBecauseMangaTypeHasNoRatingField() {
        // GraphQL MangaType `rating` alanı taşımaz; manga +18 tespiti tür üzerinden yapılır.
        assertEquals(
            "{ mangas(ids: \"7\", limit: 1, censored: false) { id genres { name } } }",
            ShikimoriAdultResolver.buildQuery(ShikimoriAdultResolver.Kind.MANGA, listOf(7))
        )
    }

    @Test
    fun queryLimitIsClampedToShikimoriBatchMaximum() {
        val many = (1..80).toList()
        val query = ShikimoriAdultResolver.buildQuery(ShikimoriAdultResolver.Kind.ANIME, many)
        assertTrue(query.contains("limit: 50"))
    }

    // ─── Yanıt ayrıştırma ─────────────────────────────────────────────────

    @Test
    fun rxRatingIsMarkedAdult() {
        val body = """
            {"data":{"animes":[{"id":"58567","rating":"rx","genres":[{"name":"Comedy"}]}]}}
        """.trimIndent()

        val parsed = ShikimoriAdultResolver.parseResponse(body, ShikimoriAdultResolver.Kind.ANIME)
        assertNotNull(parsed)
        assertEquals(true, parsed!![58567])
    }

    @Test
    fun hentaiGenreIsMarkedAdultEvenWithoutRxRating() {
        val body = """
            {"data":{"animes":[{"id":"42","rating":"r_plus","genres":[{"name":"Romance"},{"name":"Hentai"}]}]}}
        """.trimIndent()

        val parsed = ShikimoriAdultResolver.parseResponse(body, ShikimoriAdultResolver.Kind.ANIME)
        assertNotNull(parsed)
        assertEquals(true, parsed!![42])
    }

    @Test
    fun rAndRPlusRatingsAreNotAdult() {
        // Uygulama politikası: yalnızca rx/hentai +18; r (17+ şiddet) ve
        // r_plus (hafif çıplaklık) blur kapsamı DIŞINDA.
        val body = """
            {"data":{"animes":[
              {"id":"1","rating":"r","genres":[{"name":"Action"}]},
              {"id":"2","rating":"r_plus","genres":[{"name":"Ecchi"}]},
              {"id":"3","rating":"pg_13","genres":[]},
              {"id":"4","rating":null,"genres":null}
            ]}}
        """.trimIndent()

        val parsed = ShikimoriAdultResolver.parseResponse(body, ShikimoriAdultResolver.Kind.ANIME)
        assertNotNull(parsed)
        assertEquals(4, parsed!!.size)
        assertEquals(false, parsed[1])
        assertEquals(false, parsed[2])
        assertEquals(false, parsed[3])
        assertEquals(false, parsed[4])
    }

    @Test
    fun mangaResponseIsParsedByGenreNames() {
        val body = """
            {"data":{"mangas":[
              {"id":"602","genres":[{"name":"Hentai"}]},
              {"id":"99","genres":[{"name":"Drama"}]}
            ]}}
        """.trimIndent()

        val parsed = ShikimoriAdultResolver.parseResponse(body, ShikimoriAdultResolver.Kind.MANGA)
        assertNotNull(parsed)
        assertEquals(true, parsed!![602])
        assertEquals(false, parsed[99])
    }

    @Test
    fun unusableResponsesReturnNullInsteadOfEmptyMap() {
        // null = "bilinmiyor, tekrar sorulabilir"; emptyMap = "+18 yok" demek olurdu.
        assertNull(ShikimoriAdultResolver.parseResponse(null, ShikimoriAdultResolver.Kind.ANIME))
        assertNull(ShikimoriAdultResolver.parseResponse("", ShikimoriAdultResolver.Kind.ANIME))
        assertNull(ShikimoriAdultResolver.parseResponse("<html>502</html>", ShikimoriAdultResolver.Kind.ANIME))
        assertNull(
            ShikimoriAdultResolver.parseResponse(
                """{"errors":[{"message":"Field 'rating' doesn't exist"}]}""",
                ShikimoriAdultResolver.Kind.ANIME
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
            "{\"id\":\"58567\",\"rating\":\"rx\",\"genres\":[{\"name\":\"Hentai\"}]}," +
            "{\"id\":\"42\",\"rating\":\"pg_13\",\"genres\":[{\"name\":\"Comedy\"}]}]}}"

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
            ShikimoriAdultResolver.hostOverride = "http://127.0.0.1:$port"

            val first = runBlocking {
                ShikimoriAdultResolver.resolveAdultIds(
                    ShikimoriAdultResolver.Kind.ANIME,
                    listOf(58567, 42)
                )
            }

            assertEquals(1, requestBodies.size)
            assertTrue(
                "Tüm kimlikler tek sorguda gitmeli: ${requestBodies[0]}",
                requestBodies[0].contains("animes(ids: \\\"58567,42\\\"")
            )
            assertTrue(requestBodies[0].contains("censored: false"))
            assertEquals(setOf(58567), first)

            // Aynı kimlikler yeniden istendiğinde ağa çıkılmaz (önbellek).
            val second = runBlocking {
                ShikimoriAdultResolver.resolveAdultIds(
                    ShikimoriAdultResolver.Kind.ANIME,
                    listOf(58567, 42)
                )
            }
            assertEquals(1, requestBodies.size)
            assertEquals(first, second)
        } finally {
            serverSocket.close()
            executor.shutdownNow()
        }
    }

    @Test
    fun networkFailureIsNotCachedAsNotAdult() {
        // Bağlantı kurulamayan port: sonuç boş olmalı ama önbelleğe "+18 değil" yazılmamalı;
        // ağ düzeldiğinde kimlikler yeniden sorulabilmeli.
        val freePort = ServerSocket(0).use { it.localPort }
        ShikimoriAdultResolver.hostOverride = "http://127.0.0.1:$freePort"

        val flags = runBlocking {
            ShikimoriAdultResolver.resolveAdultFlags(ShikimoriAdultResolver.Kind.ANIME, listOf(58567))
        }
        assertTrue(flags.isEmpty())

        val ids = runBlocking {
            ShikimoriAdultResolver.resolveAdultIds(ShikimoriAdultResolver.Kind.ANIME, listOf(58567))
        }
        assertTrue(ids.isEmpty())
    }

    @Test
    fun idsMissingFromSuccessfulResponseAreTreatedAsNotAdultAndCached() {
        // `ids` ile sorgulandığında Shikimori'de hentai filtrelemesi kapalıdır; yanıtta
        // dönmeyen kimlik gerçekten +18 değildir (veya kayıt yoktur) → false olarak
        // önbelleğe alınır, tekrar sorgulanmaz.
        val serverSocket = ServerSocket(0)
        val port = serverSocket.localPort
        val requestCount = CopyOnWriteArrayList<Int>()
        val executor = Executors.newSingleThreadExecutor()

        val responseJson = "{\"data\":{\"mangas\":[{\"id\":\"7\",\"genres\":[{\"name\":\"Drama\"}]}]}}"

        executor.submit {
            try {
                while (true) {
                    val socket = serverSocket.accept()
                    val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
                    readHttpBody(reader)
                    requestCount.add(1)
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
            ShikimoriAdultResolver.hostOverride = "http://127.0.0.1:$port"

            val first = runBlocking {
                ShikimoriAdultResolver.resolveAdultFlags(
                    ShikimoriAdultResolver.Kind.MANGA,
                    listOf(7, 404)
                )
            }
            // 404 yanıtta yok → +18 değil kabul edilir (nihai cevap).
            assertEquals(mapOf(7 to false, 404 to false), first)

            val second = runBlocking {
                ShikimoriAdultResolver.resolveAdultFlags(
                    ShikimoriAdultResolver.Kind.MANGA,
                    listOf(7, 404)
                )
            }
            assertEquals(1, requestCount.size)
            assertEquals(first, second)
        } finally {
            serverSocket.close()
            executor.shutdownNow()
        }
    }

    @Test
    fun emptyAndInvalidIdsAreIgnored() {
        val flags = runBlocking {
            ShikimoriAdultResolver.resolveAdultFlags(
                ShikimoriAdultResolver.Kind.ANIME,
                listOf(0, -5)
            )
        }
        assertTrue(flags.isEmpty())
        assertFalse(flags.containsKey(0))
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
