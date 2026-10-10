package com.kitsugi.animelist.utils

import com.kitsugi.animelist.ui.components.KitsugiMarkdownUriHandler
import java.net.URLEncoder

/**
 * AniList/MAL markdown ve BBCode temizleme + dönüştürme yardımcısı.
 *
 * Pipeline:
 *  1. Kullanıcı bio'sunu temizle (JSON payload, boş parantezler)
 *  2. BBCode → Markdown dönüştür
 *  3. AniList özel syntax'ları → standart/scheme Markdown
 *     - img(url)           → ![](url)  tıklanabilir galeri linki
 *     - ~!spoiler!~        → [Spoiler](kitsugi-spoiler://encoded)
 *     - ~~~center~~~       → düz metin
 *     - __bold__           → **bold**
 *     - <br>               → \n\n
 *     - ~~strikethrough~~  → ~~strikethrough~~ (zaten standart)
 */
object KitsugiMarkdownUtils {

    // ── Regex tanımları ──────────────────────────────────────────────────────
    private val spoilerRegex    = Regex("~!(.*?)!~", RegexOption.DOT_MATCHES_ALL)
    private val centerRegex     = Regex("~~~(.*?)~~~", RegexOption.DOT_MATCHES_ALL)
    private val boldUnderscoreRegex = Regex("__(.*?)__")
    private val htmlBrRegex     = Regex("<br\\s*/?>", RegexOption.IGNORE_CASE)

    /** AniList img syntax: img(url), img720(url), img720{url}, etc. */
    private val aniListImgRegex = Regex("""img\d*%*[({\[](.*?)[)}\]]""")

    /** Standard markdown image syntax: ![alt](url) */
    private val markdownImgRegex = Regex("""!\[.*?\]\((.*?)\)""")

    /** Standalone raw image/gif URL regex */
    private val standaloneImgRegex = Regex("""(?<![\[\(="'])(https?://[^\s<>"'\)]+\.(?:jpe?g|png|gif|webp)(?:\?[^\s<>"'\)]*)?)(?![\]\)"'])""", RegexOption.IGNORE_CASE)

    /** Eski spoiler formatı: > ⚠️ *Spoiler:* ... */
    private val oldSpoilerRegex = Regex(""">\s*⚠️\s*\*?Spoiler:\*?\s*(.*)""", RegexOption.IGNORE_CASE)

    // Kullanıcı bio temizleme regex'leri
    private val jsonPayloadRegex    = Regex("""(?i)\bjson[A-Za-z0-9+/=_-]{8,}""")
    private val jsonBlockRegex      = Regex("""(?i)\bjson\s*\{.*?\}""", RegexOption.DOT_MATCHES_ALL)
    private val emptyParensRegex    = Regex("""\(\s*\)""")
    private val emptyBracketsRegex  = Regex("""\[\s*\]""")

    // Çeviri veya kaynak kaynaklı bozulmuş link regex'leri
    private val brokenLabelRegex    = Regex("""\[([^\]\n]+)\s*\n+\s*([^\]\n]+)\]\s*\((https?://[^\s)]+)\)""")
    private val separatedLinkRegex  = Regex("""\[([^\]]+)\]\s+\((https?://[^\s)]+)\)""")

    // ── Shikimori / MAL Özel BBCode Regex Tanımları ──────────────────────────
    private val entityTagWithTextRegex = Regex(
        """\[\s*(?:character|karakter|персонаж|person|kisi|kişi|персона|anime|manga|ranobe|entry|club|kulup|kulüp|user|kullanici|kullanıcı|comment|yorum|topic|konu|message|mesaj|profile|profil)\b[^\]]*\](.*?)\[\s*/\s*(?:character|karakter|персонаж|person|kisi|kişi|персона|anime|manga|ranobe|entry|club|kulup|kulüp|user|kullanici|kullanıcı|comment|yorum|topic|konu|message|mesaj|profile|profil)\s*\]""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    private val entityCardBlockRegex = Regex(
        """\[\s*(?:characters|karakterler|персонажи|people|kisiler|kişiler|animes|animeler|mangas|mangalar)\b[^\]]*\]""",
        RegexOption.IGNORE_CASE
    )

    private val entityTagStandaloneRegex = Regex(
        """\[\s*(?:character|karakter|персонаж|person|kisi|kişi|персона|anime|manga|ranobe|entry|club|kulup|kulüp|user|kullanici|kullanıcı|comment|yorum|topic|konu|message|mesaj|profile|profil)\s*=[^\]]*\]""",
        RegexOption.IGNORE_CASE
    )

    private val orphanEntityClosingTagRegex = Regex(
        """\[\s*/\s*(?:character|karakter|персонаж|person|kisi|kişi|персона|anime|manga|ranobe|entry|club|kulup|kulüp|user|kullanici|kullanıcı|comment|yorum|topic|konu|message|mesaj|profile|profil)\s*\]""",
        RegexOption.IGNORE_CASE
    )

    private val alignTagRegex = Regex(
        """\[\s*(?:right|left|justify)\s*\](.*?)\[\s*/\s*(?:right|left|justify)\s*\]""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    private val mediaStripRegex = Regex(
        """\[\s*(?:video|audio)\b[^\]]*\](?:.*?\[\s*/\s*(?:video|audio)\s*\])?""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    // Standart BBCode regex'leri
    private val boldRegex          = Regex("\\[b\\](.*?)\\[/b\\]",          setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val italicRegex        = Regex("\\[i\\](.*?)\\[/i\\]",          setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val underlineRegex     = Regex("\\[u\\](.*?)\\[/u\\]",          setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val strikeRegex        = Regex("\\[s\\](.*?)\\[/s\\]",          setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val centerRegexBB      = Regex("\\[center\\](.*?)\\[/center\\]", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val sizeRegex          = Regex("\\[size=[^\\]]*\\](.*?)\\[/size\\]", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val colorRegex         = Regex("\\[color=[^\\]]*\\](.*?)\\[/color\\]", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val spoilerSimpleRegex = Regex("\\[spoiler\\]((?:(?!\\[spoiler).)*?)\\[/spoiler\\]", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val spoilerParamRegex  = Regex("\\[spoiler=[^\\]]*\\]((?:(?!\\[spoiler).)*?)\\[/spoiler\\]", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val quoteRegex         = Regex("\\[quote\\]((?:(?!\\[quote).)*?)\\[/quote\\]", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val quoteParamRegex    = Regex("\\[quote=([^\\]]*)\\]((?:(?!\\[quote).)*?)\\[/quote\\]", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val urlParamRegex      = Regex("\\[url=([^\\]]+)\\](.*?)\\[/url\\]", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val urlSimpleRegex     = Regex("\\[url\\](.*?)\\[/url\\]",      setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val imgBBRegex         = Regex("\\[img[^\\]]*\\](.*?)\\[/img\\]", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val ytBBRegex          = Regex("\\[yt\\](.*?)\\[/yt\\]", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val codeBBRegex        = Regex("\\[code\\](.*?)\\[/code\\]", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Shikimori ve MAL kaynaklı özel BBCode etiketlerini ([character=...], [anime=...], [person=...], poster kartları vb.)
     * temizleyerek etiket içindeki düz metni çıkarır.
     * Spoiler etiketlerini ([spoiler]) korur.
     */
    fun String.cleanShikimoriBbCode(): String {
        if (isBlank()) return ""
        var current = this
        var previous: String
        do {
            previous = current
            current = current
                .replace(entityTagWithTextRegex) { it.groupValues[1] }
                .replace(entityCardBlockRegex, "")
                .replace(entityTagStandaloneRegex, "")
                .replace(orphanEntityClosingTagRegex, "")
                .replace(alignTagRegex) { it.groupValues[1] }
                .replace(mediaStripRegex, "")
        } while (current != previous)
        return current
    }

    /**
     * Kullanıcı profil/yorum metnini temizler:
     * extension JSON payload'larını ve boş parantezleri siler.
     */
    fun String.cleanUserAboutText(): String {
        if (isBlank()) return ""
        return this
            .replace(jsonPayloadRegex, "")
            .replace(jsonBlockRegex, "")
            .replace(emptyParensRegex, "")
            .replace(emptyBracketsRegex, "")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
    }

    /**
     * AniList/MAL metnini tam pipeline'dan geçirir ve
     * [KitsugiMarkdownText] için hazır standart Markdown üretir.
     *
     * Spoilerlar → `kitsugi-spoiler://` scheme linklere
     * Resimler   → `![](url)` + `kitsugi-image://` galeri linki
     */
    fun String.formatAniListMarkdown(): String {
        val cleaned = this.cleanUserAboutText()
        val bbConverted = cleaned.convertBBCodeToMarkdown()
        var current = bbConverted
            .replace(htmlBrRegex, "\n\n")
            .replace(brokenLabelRegex) { "[${it.groupValues[1].trim()} ${it.groupValues[2].trim()}](${it.groupValues[3]})" }
            .replace(separatedLinkRegex) { "[${it.groupValues[1].trim()}](${it.groupValues[2]})" }
            .replace(markdownImgRegex) { "img(${it.groupValues[1]})" }
            .replace(boldUnderscoreRegex) { "**${it.groupValues[1]}**" }
            .replace(standaloneImgRegex) { "img(${it.groupValues[1]})" }
            .formatAniListImageTags()

        var previous: String
        do {
            previous = current
            current = current
                .replace(spoilerRegex) { match ->
                    val raw     = match.groupValues[1]
                    val cleaned2 = cleanSpoilerContent(raw)
                    val encoded  = safeUrlEncode(cleaned2)
                    "\n[⚠️ Spoiler - Görmek için tıkla](${KitsugiMarkdownUriHandler.SPOILER_SCHEME}$encoded)\n"
                }
                .replace(oldSpoilerRegex) { match ->
                    val raw     = match.groupValues[1]
                    val cleaned2 = cleanSpoilerContent(raw)
                    val encoded  = safeUrlEncode(cleaned2)
                    "\n[⚠️ Spoiler - Görmek için tıkla](${KitsugiMarkdownUriHandler.SPOILER_SCHEME}$encoded)\n"
                }
        } while (current != previous)

        return current
    }

    /**
     * Spoiler içeriğini temizler (URL decode gerekiyorsa yapar).
     */
    fun cleanSpoilerContent(raw: String): String {
        val trimmed = raw.trim()
        return runCatching {
            var s = trimmed
            if (s.contains("%") || s.contains("+")) {
                s = java.net.URLDecoder.decode(s.replace("+", " "), "UTF-8")
            }
            s
        }.getOrElse { trimmed }
    }

    // ── Private yardımcılar ───────────────────────────────────────────────────

    /**
     * AniList `img(url)` tag'larını standart Markdown resim + galeri linki çiftine çevirir.
     *
     * Çıktı örneği:
     * ```
     * [![](url)](kitsugi-image://0%7Curl)
     * ```
     * Bu sayede `Coil3ImageTransformerImpl` resmi gösterir,
     * `KitsugiMarkdownUriHandler` ise tıklamada galeriyi açar.
     */
    private fun String.formatAniListImageTags(): String {
        // Tüm resim URL'lerini topla — galeri için sıralı liste
        val allUrls = aniListImgRegex.findAll(this).map { it.groupValues[1].trim() }.toList()

        return replace(aniListImgRegex) { match ->
            val url = match.groupValues[1].trim()
            val idx = allUrls.indexOf(url).coerceAtLeast(0)
            // Pipe ile ayrılmış galeri payload: INDEX|URL1|URL2|...
            val galleryPayload = buildString {
                append(idx)
                allUrls.forEach { u -> append("|"); append(u) }
            }
            val encodedPayload = safeUrlEncode(galleryPayload)
            // Tıklanabilir resim: [![alt](url)](kitsugi-image://encoded)
            "\n[![](${url})](${KitsugiMarkdownUriHandler.IMAGE_SCHEME}${encodedPayload})\n"
        }
    }

    private fun safeUrlEncode(value: String): String = runCatching {
        URLEncoder.encode(value, "UTF-8")
    }.getOrElse { value }

    /**
     * MAL/forum BBCode'u standart Markdown'a çevirir.
     * Nested tag'ler için do-while döngüsü kullanır.
     */
    private fun String.convertBBCodeToMarkdown(): String {
        var result = this.cleanShikimoriBbCode()

        // Basit liste ve ayırıcı dönüşümleri
        result = result
            .replace(Regex("\\[/?list(?:=[^\\]]*)?\\]", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\[\\*\\]"), "\n- ")
            .replace(Regex("\\[hr\\]", RegexOption.IGNORE_CASE), "\n---\n")

        var previous: String
        var iterations = 0
        do {
            previous = result
            result = result
                .cleanShikimoriBbCode()
                .replace(boldRegex)          { "**${it.groupValues[1]}**" }
                .replace(italicRegex)        { "*${it.groupValues[1]}*" }
                .replace(underlineRegex)     { "__${it.groupValues[1]}__" }
                .replace(strikeRegex)        { "~~${it.groupValues[1]}~~" }
                .replace(centerRegexBB)      { "~~~${it.groupValues[1]}~~~" }
                .replace(sizeRegex)          { it.groupValues[1] }
                .replace(colorRegex)         { it.groupValues[1] }
                .replace(spoilerSimpleRegex) { "~!${it.groupValues[1]}!~" }
                .replace(spoilerParamRegex)  { "~!${it.groupValues[1]}!~" }
                .replace(quoteParamRegex) { match ->
                    val rawMeta = match.groupValues[1]
                    val author = rawMeta
                        .replace(Regex("message=\\d+", RegexOption.IGNORE_CASE), "")
                        .replace("\"", "")
                        .replace("'", "")
                        .trim()
                    val quotedBody = match.groupValues[2].trim().replace("\n", "\n> ")
                    if (author.isNotBlank()) {
                        "\n> **@$author:**\n> $quotedBody\n"
                    } else {
                        "\n> $quotedBody\n"
                    }
                }
                .replace(quoteRegex)         { "\n> ${it.groupValues[1].trim().replace("\n", "\n> ")}\n" }
                .replace(urlParamRegex)      { "[${it.groupValues[2]}](${it.groupValues[1].trim().trim('\"', '\'')})" }
                .replace(urlSimpleRegex)     { "[${it.groupValues[1].trim()}](${it.groupValues[1].trim()})" }
                .replace(imgBBRegex)         { "img(${it.groupValues[1].trim()})" }
                .replace(ytBBRegex) { match ->
                    val rawYt = match.groupValues[1].trim()
                    val ytUrl = if (rawYt.startsWith("http", ignoreCase = true)) rawYt else "https://www.youtube.com/watch?v=$rawYt"
                    "\n[▶ YouTube Videosu]($ytUrl)\n"
                }
                .replace(codeBBRegex)        { "\n```\n${it.groupValues[1].trim()}\n```\n" }
            iterations++
        } while (result != previous && iterations < 12)

        return result
    }
}

/**
 * Global extension for cleanShikimoriBbCode.
 */
fun String.cleanShikimoriBbCode(): String = KitsugiMarkdownUtils.run { this@cleanShikimoriBbCode.cleanShikimoriBbCode() }
