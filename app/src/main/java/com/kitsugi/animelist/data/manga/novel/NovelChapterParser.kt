package com.kitsugi.animelist.data.manga.novel

import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/**
 * Light novel / web novel bölümlerini HTML'den temiz metne çevirir.
 *
 * Tasarım notları
 * ───────────────
 * • **OkHttp + Jsoup** ile çalışır (ikisi de projede mevcut; yeni bağımlılık yok).
 * • Reklam/çerez/menü blokları temizlenir; paragraflar korunur.
 * • İçerik kabı sezgisel bulunur: `article > chapter-content > id/class eşleşmesi
 *   > en çok paragraf içeren div`. LNReader'ın "chapter HTML sanitize" mantığının
 *   sade karşılığı.
 * • Çıktı [NovelChapterContent] → okuyucuda tipografi ayarlarına göre çizilir.
 */
class NovelChapterParser(
    private val client: OkHttpClient = OkHttpClient()
) {

    /** Manga kaynaklarındaki adresler HTML sayfası mı? (uzantı veya kaynak ipucu) */
    fun looksLikeHtmlPage(url: String): Boolean {
        val lower = url.substringBefore('?').lowercase()
        if (lower.endsWith(".html") || lower.endsWith(".htm") || lower.endsWith(".xhtml")) return true
        // Uzantılı görsel/arşiv adresleri kesinlikle sayfa değildir.
        val imageOrArchive = Regex(""".*\.(jpg|jpeg|png|gif|webp|avif|bmp|zip|cbz|cbr|rar|pdf|epub)$""")
        if (imageOrArchive.matches(lower)) return false
        // Uzantısız adresler novel sitelerinde çok yaygın: "bolum-12", "/chapter/5".
        return lower.contains("bolum") || lower.contains("chapter") || lower.contains("episode") ||
            lower.contains("novel")
    }

    /** Sirasiyla temizlenen gürültü seçicileri. */
    private val noiseSelectors = listOf(
        "script, style, noscript, iframe, form, nav, footer, header, aside",
        ".ads, .ad, .advert, .advertisement, .reklam, .reklam-alani",
        ".comments, #comments, .comment-box, .yorumlar",
        ".share, .sharing, .social, .paylas",
        ".related, .related-posts, .oneriler, .navigation, .nav-links, .pagination",
        ".cookie, .cookies, .gdpr, .cerez",
        ".breadcrumb, .breadcrumbs",
        "[style*=display:none], [hidden]"
    )

    /** İçerik kabı adayları (sırayla denenir). */
    private val contentSelectors = listOf(
        "#chapter-content", "#chaptercontent", "#content-chapter", "#chapterContent",
        ".chapter-content", ".chaptercontent", ".chapter__content", ".entry-content",
        ".post-content", ".article-content", ".novel-content", ".read-content",
        ".reading-content", ".text-content", "article", "main", "#content", ".content"
    )

    /**
     * Bölüm sayfasını indirip [NovelChapterContent] üretir.
     * Ağ/ayrıştırma hataları fırlatılmaz; `error` alanı doldurulur.
     */
    suspend fun load(
        url: String,
        referer: String? = null,
        userAgent: String = DEFAULT_UA
    ): NovelChapterContent = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .header("Accept-Language", "tr,en;q=0.8")
                .apply { if (!referer.isNullOrBlank()) header("Referer", referer) }
                .build()

            val body = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext NovelChapterContent(
                        sourceUrl = url,
                        error = "Sunucu ${response.code} döndürdü"
                    )
                }
                response.body?.string().orEmpty()
            }
            if (body.isBlank()) {
                return@withContext NovelChapterContent(sourceUrl = url, error = "Sayfa boş döndü")
            }
            parse(body, url)
        } catch (e: Exception) {
            NovelChapterContent(sourceUrl = url, error = e.message ?: "Bölüm yüklenemedi")
        }
    }

    /** HTML'i bloklara ayırır. Testlerde ağ olmadan da çağrılabilir. */
    fun parse(html: String, sourceUrl: String = ""): NovelChapterContent {
        val doc = Jsoup.parse(html, sourceUrl)
        noiseSelectors.forEach { doc.select(it).remove() }

        val container = pickContainer(doc)
        val blocks = mutableListOf<NovelBlock>()

        // 1) Bölüm başlığı
        val heading = container.selectFirst("h1, h2, .chapter-title, .entry-title")
            ?: doc.selectFirst("h1")
        heading?.text()?.trim()?.takeIf { it.isNotEmpty() }?.let {
            blocks += NovelBlock(it, NovelBlockStyle.Title)
            if (heading.parent() == container) heading.remove()
        }

        // 2) Gövde: paragraf / başlık / alıntı / görsel sırasıyla
        val bodyNodes = container.select("p, h2, h3, h4, blockquote, li, img, br")
        if (bodyNodes.isEmpty()) {
            // Bazı kaynaklar metni <div> içine <br> ile yazar.
            splitByBr(container.text()).forEach { line ->
                blocks += NovelBlock(line, NovelBlockStyle.Paragraph)
            }
        } else {
            val seen = HashSet<String>()
            for (node in bodyNodes) {
                when (node.tagName()) {
                    "img" -> {
                        val src = node.absUrl("src").ifBlank { node.attr("src") }
                        if (src.isNotBlank() && seen.add("img:$src")) {
                            blocks += NovelBlock(src, NovelBlockStyle.Image)
                        }
                    }
                    "h2", "h3", "h4" -> node.text().trim().takeIf { it.isNotEmpty() }?.let { text ->
                        if (seen.add("h:$text")) blocks += NovelBlock(text, NovelBlockStyle.Heading)
                    }
                    "blockquote" -> node.text().trim().takeIf { it.isNotEmpty() }?.let { text ->
                        if (seen.add("q:$text")) blocks += NovelBlock(text, NovelBlockStyle.Quote)
                    }
                    else -> {
                        val text = node.text().trim()
                        // Menü/kısa etiketleri ve tekrar eden satırları at.
                        if (text.length < 2) continue
                        if (isBoilerplate(text)) continue
                        if (seen.add("p:$text")) blocks += NovelBlock(text, NovelBlockStyle.Paragraph)
                    }
                }
            }
        }

        val cleaned = mergeShortBlocks(blocks)
        return if (cleaned.isEmpty()) {
            NovelChapterContent(
                sourceUrl = sourceUrl,
                error = "Bu bölümde okunabilir metin bulunamadı (kaynak görsel tabanlı olabilir)"
            )
        } else {
            NovelChapterContent(
                title = blocks.firstOrNull { it.style == NovelBlockStyle.Title }?.text,
                blocks = cleaned,
                sourceUrl = sourceUrl
            )
        }
    }

    /**
     * İçerik kabını seç: bilinen seçiciler → en çok `<p>` içeren öğe.
     * Reklam/kenar çubuğunun kazanmasını engellemek için minimum uzunluk aranır.
     */
    private fun pickContainer(doc: org.jsoup.nodes.Document): Element {
        contentSelectors.forEach { selector ->
            doc.selectFirst(selector)?.let { candidate ->
                if (candidate.text().length >= 120) return candidate
            }
        }
        return doc.body() ?: doc
    }

    private fun splitByBr(raw: String): List<String> =
        raw.split(Regex("""\s*\n\s*|\s{3,}"""))
            .map { it.trim() }
            .filter { it.length >= 2 && !isBoilerplate(it) }

    /** Yaygın site gürültüsü: "Yorum yap", "Sonraki bölüm", telif metni … */
    private fun isBoilerplate(text: String): Boolean {
        val t = text.lowercase()
        val noise = listOf(
            "yorum yap", "yorumlar", "sonraki bölüm", "önceki bölüm", "bölüm listesi",
            "ana sayfa", "iletişim", "gizlilik", "çerez", "reklam", "paylaş",
            "leave a comment", "previous chapter", "next chapter", "table of contents",
            "all rights reserved", "copyright", "read more", "devamını oku"
        )
        if (t.length <= 24 && noise.any { t.contains(it) }) return true
        return false
    }

    /**
     * HTML'de her kelime ayrı `<p>` olarak gelmişse (kötü kaynaklar) tek satıra indirir.
     */
    private fun mergeShortBlocks(blocks: List<NovelBlock>): List<NovelBlock> {
        val paragraphs = blocks.filter { it.style == NovelBlockStyle.Paragraph }
        if (paragraphs.isEmpty()) return blocks
        val avg = paragraphs.map { it.text.length }.average()
        if (avg >= 40.0) return blocks

        val merged = mutableListOf<NovelBlock>()
        val buffer = StringBuilder()
        fun flush() {
            if (buffer.isNotBlank()) {
                merged += NovelBlock(buffer.toString().trim(), NovelBlockStyle.Paragraph)
                buffer.clear()
            }
        }
        for (block in blocks) {
            when (block.style) {
                NovelBlockStyle.Paragraph -> {
                    if (buffer.isNotEmpty()) buffer.append(' ')
                    buffer.append(block.text)
                    if (buffer.length > 220) flush()
                }
                else -> { flush(); merged += block }
            }
        }
        flush()
        return merged
    }

    companion object {
        const val DEFAULT_UA =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/124.0 Mobile Safari/537.36 Kitsugi/1.0"

        /** Bölüm içeriğinden okuma süresi tahmini (dakika). */
        fun estimateReadingMinutes(wordCount: Int): Int =
            (wordCount / 220.0).let { if (it < 1.0) 1 else Math.ceil(it).toInt() }

        fun wordCountOf(blocks: List<NovelBlock>): Int =
            blocks.sumOf { block ->
                if (block.style == NovelBlockStyle.Image) 0
                else block.text.split(Regex("""\s+""")).count { it.isNotBlank() }
            }
    }
}
