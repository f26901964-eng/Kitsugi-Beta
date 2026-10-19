package com.kitsugi.animelist.data.cloudstream.extractors

import android.util.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.newSubtitleFile
import com.lagradost.cloudstream3.utils.extractorApis
import com.lagradost.cloudstream3.utils.newExtractorLink
import java.net.URI
import java.util.Locale

/**
 * T-CR-1 — Close / Rapid (JWPlayer "dc_" şifreli) CDN ailesi için UYGULAMA YERLEŞİK extractor.
 *
 * NEDEN GEREKLİ (2026-10-18 kök neden analizi):
 *  1. HDFilmCehennemi ve FilmMakinesi videoları aynı iki gömülü kaynaktan geliyor:
 *     **Close** (`closeload.*`) ve **Rapid** (`rapidrame.*` / `rplayer.*`).
 *  2. Eklentiler (.cs3) cihazlarda bayat kalabiliyor: depo manifestindeki `version`
 *     yükseltilmeden yeniden derleme yapıldığında güncelleme denetimi "güncel" sanıp
 *     ATLADIĞI için kullanıcı eski çözücüyle kalıyor ("halen oynatılmıyor" döngüsü).
 *  3. FilmMakinesi eklentisi (v62 dahil) `rapidrame` iframe'ini `loadExtractor`'a bırakıyor
 *     ama hiçbir eklenti rapidrame için extractor KAYDETMİYOR → "DUAL Rapid" düğmesi
 *     uygulama tarafında sıfır link üretiyordu.
 *
 * ÇÖZÜM: CS çalışma zamanının global `extractorApis` listesine uygulama yerleşikleri
 * eklenir. `loadExtractor` önek eşleşmesini TERSTEN iterasyonda yaptığı için eklentiler
 * sonradan kayıt olduğunda kendi host'larında öncelik alır; eklentinin hiç bilmediği
 * host'larda (rapidrame.*, closeload.top, closeload.com ...) ise bu yerleşikler devreye
 * girer. Böylece oynatma zinciri eklenti sürümünden bağımsız hale gelir.
 *
 * Algoritma, eklenti deposunun son kararlı çözücüsüyle (CloseLoad v62) birebir aynıdır:
 *  - JWPlayer sayfasındaki `dc_*(...)` JS bloğunu native deşifre et
 *    (dizi birleştir → işlem sırası: atob / reverse / rot(replace) → modulo unmix),
 *  - olmazsa JSON-LD `contentUrl`,
 *  - olmazsa sayfada `master.txt` / `.m3u8` ara,
 *  - master listesini açıp EN YÜKSEK çözünürlüklü varyantı seç ve
 *    Origin/Referer'ı GERÇEK yayın host'una sabitle (yoksa CDN 5004 ile düşürüyor).
 */
class KitsugiCdnJwExtractor(
    override val name: String,
    override val mainUrl: String
) : ExtractorApi() {

    override val requiresReferer: Boolean = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (com.lagradost.cloudstream3.utils.ExtractorLink) -> Unit
    ) {
        // Referer, çağıranın verdiği host değil embed adresinin KENDİ host'udur:
        // closeload.filmmakinesi.to ölünce başka bir closeload host'u da çalışsın.
        val origin = originOf(url)
            ?: referer?.let { originOf(it) }?.takeIf { it.startsWith("http") }
            ?: mainUrl
        val pageHeaders = headersFor(origin)

        try {
            val html = app.get(url, headers = pageHeaders).text
            if (html.isBlank()) {
                val msg = "[$name] boş HTML — embed sayfası boş döndü (403/CF?) url=${url.take(90)} origin=$origin"
                Log.w(TAG, msg)
                com.kitsugi.animelist.data.cloudstream.diag.CsTrace.warn(name, "extract", msg)
                mirrorToAliasTags("extract", msg, null)
            }

            // 1) JS deşifre algoritması
            var realUrl = decryptNative(html)

            // 2) JSON-LD contentUrl (şifresiz yayınlayan varyantlar)
            if (realUrl.isNullOrBlank()) {
                realUrl = CONTENT_URL_REGEX.find(html)?.groupValues?.get(1)?.replace("\\/", "/")
            }

            // 3) master.txt / .m3u8 doğrudan akış linki
            if (realUrl.isNullOrBlank()) {
                realUrl = MASTER_TXT_REGEX.find(html)?.value ?: M3U8_REGEX.find(html)?.value
            }

            if (!realUrl.isNullOrBlank() && realUrl.startsWith("http")) {
                val (streamUrl, streamHeaders) = resolveMasterPlaylist(realUrl, pageHeaders)
                    ?: (realUrl to pageHeaders)

                val type = if (looksLikeHls(streamUrl)) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                callback.invoke(
                    newExtractorLink(source = name, name = name, url = streamUrl, type = type) {
                        quality = Qualities.P1080.value
                        headers = streamHeaders
                    }
                )
                Log.d(TAG, "[$name] çözüldü → ${streamUrl.take(110)}")
                com.kitsugi.animelist.data.cloudstream.diag.CsTrace.info(name, "extract", "çözüldü → ${streamUrl.take(110)} (kaynak=${url.take(70)})")
            } else {
                val msg = "[$name] gerçek URL bulunamadı/deşifre edilemedi: url=${url.take(90)} origin=$origin htmlLen=${html.length}"
                Log.w(TAG, msg)
                com.kitsugi.animelist.data.cloudstream.diag.CsTrace.warn(name, "extract", msg)
                mirrorToAliasTags("extract", msg, null)
            }

            // Altyazılar (JWPlayer tracks bloğu). for-döngüsü: newSubtitleFile suspend.
            for ((label, file) in parseSubtitleTracks(html)) {
                subtitleCallback(newSubtitleFile(label, file))
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            reportExtractorError(stage = "extract", url = url, referer = referer, origin = origin, error = e)
        }
    }

    /** master.txt / master.m3u8 listesini açıp içindeki gerçek varyantı seçer. */
    private suspend fun resolveMasterPlaylist(
        playlistUrl: String,
        headers: Map<String, String>
    ): Pair<String, Map<String, String>>? {
        if (!playlistUrl.contains("master.txt") && !playlistUrl.contains(".m3u8")) return null
        return try {
            val listText = app.get(playlistUrl, headers = headers).text
            if (listText.isBlank()) {
                val msg = "[$name] master listesi boş döndü (403?) url=${playlistUrl.take(110)}"
                Log.w(TAG, msg)
                com.kitsugi.animelist.data.cloudstream.diag.CsTrace.warn(name, "master", msg)
                mirrorToAliasTags("master", msg, null)
                return null
            }
            val chosen = pickVariant(listText, playlistUrl)
            if (chosen == null) {
                val msg = "[$name] master varyant seçilemedi — liste parse edilemedi url=${playlistUrl.take(110)} ilkSatır=${listText.lineSequence().firstOrNull()?.take(80)}"
                Log.w(TAG, msg)
                com.kitsugi.animelist.data.cloudstream.diag.CsTrace.warn(name, "master", msg)
                mirrorToAliasTags("master", msg, null)
                return null
            }
            Log.d(TAG, "[$name] master listesi çözüldü: $chosen")
            com.kitsugi.animelist.data.cloudstream.diag.CsTrace.info(name, "master", "çözüldü: $chosen")
            // Oynatıcının Origin/Referer'ı liste host'u değil GERÇEK yayın host'u olmalı.
            val streamOrigin = originOf(chosen)
            val streamHeaders = if (streamOrigin != null && streamOrigin != originOf(playlistUrl)) {
                headersFor(streamOrigin)
            } else {
                headers
            }
            chosen to streamHeaders
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            reportExtractorError(stage = "master", url = playlistUrl, referer = headers["Referer"], origin = headers["Origin"], error = e)
            null
        }
    }

    // ── Hata yakalayıcı — tüm extractor hataları buradan tek formatta loglanır ──────
    private fun reportExtractorError(stage: String, url: String, referer: String?, origin: String?, error: Throwable) {
        val shortUrl = url.take(100)
        val head = "[$name] HATA stage=$stage url=$shortUrl ref=${referer?.take(60)} origin=$origin | ${error.javaClass.simpleName}: ${error.message}"
        Log.e(TAG, head, error)
        com.kitsugi.animelist.data.cloudstream.diag.CsTrace.error(name, stage, head, error)
        mirrorToAliasTags(stage, head, error)
        // Ayrıca player loguna yaz — kullanıcı Ayarlar > Log'dan görebilsin
        try {
            val causeChain = generateSequence(error as Throwable) { it.cause }.joinToString(" → ") { "${it.javaClass.simpleName}: ${it.message}" }.take(300)
            Log.e("PlayerDiagnostics", "Extractor hata zinciri: $causeChain", error)
        } catch (_: Exception) {}
    }

    private fun mirrorToAliasTags(stage: String, message: String, error: Throwable?) {
        // Kullanıcı log'u HDCH / Kekik_CloseLoad / Rapidrame ile filtreliyor — o tag'lerde de görünsün
        val aliases = when {
            name.contains("Close", ignoreCase = true) -> listOf("Kekik_CloseLoad", "HDCH")
            name.contains("Rapid", ignoreCase = true) || name.contains("Rplayer", ignoreCase = true) -> listOf("Rapidrame", "HDCH")
            else -> emptyList()
        }
        for (alias in aliases) {
            if (error != null) Log.e(alias, message, error) else Log.w(alias, message)
            if (error != null) com.kitsugi.animelist.data.cloudstream.diag.CsTrace.error(alias, stage, message, error)
            else com.kitsugi.animelist.data.cloudstream.diag.CsTrace.warn(alias, stage, message)
        }
    }

    companion object {
        private const val TAG = "KitsugiCdnJwExtractor"

        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36"

        private val CONTENT_URL_REGEX = Regex(""""contentUrl"\s*:\s*"([^"]+)"""")
        private val MASTER_TXT_REGEX = Regex("""https?://[^\s"']+/master\.txt[^\s"']*""")
        private val M3U8_REGEX = Regex("""https?://[^\s"']+\.m3u8[^\s"']*""")

        /** Uygulama yerleşikleri: (ad, mainUrl öneki). Önek eşleşmesi host ailesini kapsar. */
        val REGISTRATIONS: List<Pair<String, String>> = listOf(
            "KitsugiCloseLoad" to "https://closeload.",
            "KitsugiRapidrame" to "https://rapidrame.",
            "KitsugiRplayer" to "https://rplayer."
        )

        /**
         * Yerleşik extractor'ları CS çalışma zamanına bir kez kaydeder.
         * Eklentiler sonradan kayıt olup kendi host'larında öncelik alır; burada yalnızca
         * eklentilerin hiç bilmediği / bayat kaldığı host aileleri garanti altına alınır.
         */
        fun registerAll() {
            for ((name, mainUrl) in REGISTRATIONS) {
                if (extractorApis.any { it.name == name }) continue
                runCatching { extractorApis.add(KitsugiCdnJwExtractor(name, mainUrl)) }
                    .onFailure { Log.w(TAG, "yerleşik extractor kaydedilemedi: $name — ${it.message}") }
            }
        }

        fun originOf(url: String): String? = try {
            val uri = URI(url)
            val host = uri.host ?: return null
            val scheme = uri.scheme ?: "https"
            val port = if (uri.port > 0 && uri.port != 80 && uri.port != 443) ":${uri.port}" else ""
            "$scheme://$host$port"
        } catch (e: Exception) {
            null
        }

        fun headersFor(origin: String): Map<String, String> = mapOf(
            "User-Agent" to USER_AGENT,
            "Referer" to "${origin.trimEnd('/')}/",
            "Origin" to origin
        )

        fun looksLikeHls(url: String): Boolean {
            val lower = url.lowercase(Locale.ROOT)
            return lower.contains(".m3u8") || lower.contains("master.txt") ||
                lower.contains("playlist.txt") || lower.contains("/hls/")
        }

        /**
         * HLS master listesinden en yüksek çözünürlüklü varyantı seçer.
         * playmix/closeload bazen `#EXT-X-STREAM-INF` bloğu yerine çıplak `.m3u8` satırı
         * döndürür; o yüzden hem etiketler hem düz satırlar taranır.
         * Çözülemezse null (çağıran ham adresle devam eder).
         */
        fun pickVariant(listText: String, baseUrl: String): String? {
            val lines = listText.lines().map { it.trim() }.filter { it.isNotEmpty() }
            var bestUrl: String? = null
            var bestScore = -1
            var pendingScore = -1

            for (line in lines) {
                if (line.startsWith("#EXT-X-STREAM-INF")) {
                    pendingScore = variantScore(line)
                    continue
                }
                if (line.startsWith("#")) continue

                val candidate = absolutize(line, baseUrl) ?: continue
                if (!candidate.contains(".m3u8") && !candidate.contains("master.txt")) continue

                // resolution= etiketi yoksa dosya adındaki 1080p/720p ipucu kullanılır.
                val score = if (pendingScore >= 0) pendingScore else nameScore(candidate)
                if (score > bestScore) {
                    bestScore = score
                    bestUrl = candidate
                }
                pendingScore = -1
            }
            return bestUrl
        }

        fun variantScore(streamInfLine: String): Int {
            val resolution = Regex("""RESOLUTION=(\d+)x(\d+)""").find(streamInfLine)
            if (resolution != null) {
                val height = resolution.groupValues[2].toIntOrNull() ?: 0
                return height * 1000
            }
            val bandwidth = Regex("""BANDWIDTH=(\d+)""").find(streamInfLine)
            return (bandwidth?.groupValues?.get(1)?.toLongOrNull() ?: 0L).let { (it / 1000).toInt() }
        }

        fun nameScore(url: String): Int {
            val match = Regex("""(\d{3,4})p""").find(url.lowercase(Locale.ROOT)) ?: return 0
            return (match.groupValues[1].toIntOrNull() ?: 0) * 1000
        }

        /** Göreli liste satırını (index.m3u8, ../x/y.m3u8) tam adrese çevirir. */
        fun absolutize(line: String, baseUrl: String): String? {
            val value = line.replace("\\/", "/").trim().trim('"', '\'')
            if (value.isBlank()) return null
            return when {
                value.startsWith("http://") || value.startsWith("https://") -> value
                value.startsWith("//") -> "https:$value"
                else -> try {
                    URI(baseUrl).resolve(value).toString()
                } catch (e: Exception) {
                    null
                }
            }
        }

        /**
         * JWPlayer `tracks: [...]` bloğundaki altyazıları çıkarır: (etiket → dosya).
         * Yalnızca http ile başlayan ve altyazı türündeki (captions/subtitles veya türsüz)
         * kayıtlar döner.
         */
        fun parseSubtitleTracks(html: String): List<Pair<String, String>> {
            val out = mutableListOf<Pair<String, String>>()
            try {
                val tracksJson = html.substringAfter("tracks: [", "").substringBefore("]")
                if (tracksJson.isBlank()) return out
                val blockRegex = Regex("""\{[^}]*\}""")
                val fileRegex = Regex(""""file"\s*:\s*"([^"]+)"""")
                val labelRegex = Regex(""""label"\s*:\s*"([^"]+)"""")
                val kindRegex = Regex(""""kind"\s*:\s*"([^"]+)"""")
                blockRegex.findAll(tracksJson).forEach { match ->
                    val block = match.value
                    val file = fileRegex.find(block)?.groupValues?.get(1)?.replace("\\/", "/")
                    if (file.isNullOrBlank() || !file.startsWith("http")) return@forEach
                    val kind = kindRegex.find(block)?.groupValues?.get(1)?.lowercase(Locale.ROOT)
                    if (kind != null && kind !in setOf("captions", "subtitles")) return@forEach
                    val label = labelRegex.find(block)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
                        ?: "Türkçe"
                    out.add(label to file)
                }
            } catch (e: Exception) {
                Log.w(TAG, "altyazı ayrıştırma hatası: ${e.message}")
            }
            return out
        }

        // ── JWPlayer "dc_" native deşifre ────────────────────────────────────────────────

        /**
         * Embed sayfasındaki JS bloğunu deşifre eder:
         *  1. şifreli dizi parçalarını birleştir,
         *  2. fonksiyon gövgesindeki SIRAYA göre atob / reverse / rot(replace) uygula,
         *  3. modulo unmix ile aç.
         * Başarısızsa null.
         */
        fun decryptNative(html: String): String? {
            try {
                val scriptBlockMatch =
                    Regex("""<script[^>]*>(.*?dc_[a-zA-Z0-9_]+\(.*?</script>)""", RegexOption.DOT_MATCHES_ALL)
                        .find(html)
                val scriptContent = scriptBlockMatch?.groupValues?.get(1) ?: return null

                // 1. Şifreli dizi
                val arrayMatch = Regex("""\(\[((?:"[^"]+",?\s*)+)\]\)""").find(scriptContent)
                val parts = arrayMatch?.groupValues?.get(1)?.split(",")?.map {
                    it.trim().trim('"').replace("\\/", "/")
                } ?: return null

                // 2. Dinamik modulo çarpanları
                val moduloMatch = Regex("""(\d+)\s*%\s*\(i\s*\+\s*(\d+)\)""").find(scriptContent)
                val magicNum = moduloMatch?.groupValues?.get(1)?.toLongOrNull() ?: 399756995L
                val magicOffset = moduloMatch?.groupValues?.get(2)?.toIntOrNull() ?: 5

                // 3. Fonksiyon gövdesi
                val funcStartIdx = scriptContent.indexOf("function dc_")
                if (funcStartIdx == -1) return null
                val funcEndIdx = scriptContent.indexOf("function d1x()", funcStartIdx)
                    .takeIf { it != -1 } ?: scriptContent.length
                val functionBody = scriptContent.substring(funcStartIdx, funcEndIdx)

                // 4. Dinamik ROT kaydırma değeri
                var rotShift = 13
                val rotShiftMatch = Regex("""charCodeAt\(0\)\s*\+\s*(\d+)""").find(functionBody)
                if (rotShiftMatch != null) {
                    rotShift = rotShiftMatch.groupValues[1].toInt()
                } else {
                    val alt = Regex("""o\s*-\s*base\s*([+-])\s*(\d+)""").find(functionBody)
                    if (alt != null) {
                        val sign = alt.groupValues[1]
                        val num = alt.groupValues[2].toInt()
                        rotShift = if (sign == "-") (26 - num) % 26 else num
                    }
                }

                // İşlem sırasını siteden oku
                val operations = mutableListOf<Pair<Int, String>>()
                var index = functionBody.indexOf("atob(")
                while (index >= 0) {
                    operations.add(index to "atob")
                    index = functionBody.indexOf("atob(", index + 1)
                }
                index = functionBody.indexOf("reverse")
                while (index >= 0) {
                    operations.add(index to "reverse")
                    index = functionBody.indexOf("reverse", index + 1)
                }
                index = functionBody.indexOf("replace")
                while (index >= 0) {
                    operations.add(index to "rot")
                    index = functionBody.indexOf("replace", index + 1)
                }
                operations.sortBy { it.first }

                var result = parts.joinToString("")

                for ((_, op) in operations) {
                    when (op) {
                        "reverse" -> result = result.reversed()
                        "atob" -> {
                            var padded = result
                            while (padded.length % 4 != 0) padded += "="
                            result = String(
                                java.util.Base64.getDecoder().decode(padded),
                                Charsets.ISO_8859_1
                            )
                        }
                        "rot" -> {
                            val rot = StringBuilder()
                            for (c in result) {
                                val shifted = when (c) {
                                    in 'a'..'z' -> c.code + rotShift
                                    in 'A'..'Z' -> c.code + rotShift
                                    else -> -1
                                }
                                if (shifted == -1) {
                                    rot.append(c)
                                } else {
                                    var ch = shifted
                                    val base = if (c in 'a'..'z') 'z'.code else 'Z'.code
                                    if (ch > base) ch -= 26
                                    rot.append(ch.toChar())
                                }
                            }
                            result = rot.toString()
                        }
                    }
                }

                // Son adım: modulo unmix
                val unmix = StringBuilder()
                for (i in result.indices) {
                    val charCode = result[i].code.toLong()
                    val decryptedCode = (charCode - (magicNum % (i + magicOffset)) + 256) % 256
                    unmix.append(decryptedCode.toInt().toChar())
                }
                return unmix.toString()
            } catch (e: Exception) {
                Log.e(TAG, "native çözümleme hatası: ${e.message}")
                return null
            }
        }
    }
}
