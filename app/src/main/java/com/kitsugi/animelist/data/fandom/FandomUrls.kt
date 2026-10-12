package com.kitsugi.animelist.data.fandom

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.URLEncoder

/**
 * Fandom wiki kimliği ↔ URL dönüşümleri.
 *
 * Wiki kimliği Wikidata P4073 biçimindedir:
 *  - `jujutsu-kaisen` → `https://jujutsu-kaisen.fandom.com`
 *  - `tr.onepiece`    → `https://onepiece.fandom.com/tr`
 *
 * Kimlikler URL'ye girmeden önce sıkı bir desenle doğrulanır; geçersiz bir değer
 * (boşluk, nokta, yol parçası…) asla istek adresine dönüşmez.
 */
object FandomUrls {

    private val WIKI_ID = Regex("^(?:([a-z]{2,3}(?:-[a-z0-9]{2,8})?)\\.)?([a-z0-9][a-z0-9-]{0,99})$")
    private val LANG_SEGMENT = Regex("^[a-z]{2,3}(?:-[a-z0-9]{2,8})?$")
    private val RESERVED_SUBDOMAINS = setOf(
        "www", "community", "services", "auth", "static", "vignette", "images", "api", "fandom", "about"
    )

    data class WikiIdParts(val lang: String?, val name: String)

    /** `tr.onepiece` → (tr, onepiece); `en.` öneki kök wiki sayılır. Geçersizse null. */
    fun parse(wikiId: String?): WikiIdParts? {
        val raw = wikiId?.trim()?.lowercase() ?: return null
        val match = WIKI_ID.matchEntire(raw) ?: return null
        val lang = match.groupValues[1].takeIf { it.isNotEmpty() && it != "en" }
        val name = match.groupValues[2]
        if (name in RESERVED_SUBDOMAINS) return null
        return WikiIdParts(lang, name)
    }

    /** Kimliği kanonik biçime getirir (`EN.Naruto` → `naruto`). Geçersizse null. */
    fun normalize(wikiId: String?): String? = parse(wikiId)?.let { compose(it) }

    fun isRoot(wikiId: String?): Boolean {
        val parts = parse(wikiId) ?: return false
        return parts.lang == null
    }

    fun languageOf(wikiId: String?): String? = parse(wikiId)?.lang

    private fun compose(parts: WikiIdParts): String =
        if (parts.lang == null) parts.name else "${parts.lang}.${parts.name}"

    fun baseUrl(wikiId: String): String? {
        val parts = parse(wikiId) ?: return null
        return if (parts.lang == null) {
            "https://${parts.name}.fandom.com"
        } else {
            "https://${parts.name}.fandom.com/${parts.lang}"
        }
    }

    fun apiUrl(wikiId: String): String? = baseUrl(wikiId)?.let { "$it/api.php" }

    /** MediaWiki API adresi; parametreler güvenli biçimde kodlanır. */
    fun apiQuery(wikiId: String, params: List<Pair<String, String>>): String? {
        val api = apiUrl(wikiId)?.toHttpUrlOrNull() ?: return null
        val builder = api.newBuilder()
        params.forEach { (key, value) -> builder.addQueryParameter(key, value) }
        return builder.build().toString()
    }

    fun pageUrl(wikiId: String, title: String): String {
        val base = baseUrl(wikiId) ?: "https://${wikiId.substringAfter('.')}.fandom.com"
        return "$base/wiki/${encodeTitle(title)}"
    }

    /** Sayfa başlığını `/wiki/` yolu için kodlar (boşluk → `_`). */
    fun encodeTitle(title: String): String =
        URLEncoder.encode(title.trim().replace(' ', '_'), "UTF-8")
            .replace("+", "%20")
            .replace("%2F", "/")
            .replace("%3A", ":")
            .replace("%28", "(")
            .replace("%29", ")")

    /**
     * Fandom URL'sinden wiki kimliği çıkarır.
     *  - `https://obluda.fandom.com/`      → `obluda`
     *  - `https://onepiece.fandom.com/tr/` → `tr.onepiece`
     */
    fun wikiIdFromUrl(url: String?): String? {
        val httpUrl = url?.trim()?.toHttpUrlOrNull() ?: return null
        val host = httpUrl.host.lowercase()
        if (!host.endsWith(".fandom.com")) return null
        val sub = host.removeSuffix(".fandom.com")
        if (sub.isEmpty() || sub.contains('.')) return null
        val first = httpUrl.pathSegments.firstOrNull()?.lowercase().orEmpty()
        val lang = first.takeIf { it.isNotEmpty() && LANG_SEGMENT.matches(it) }
        return normalize(if (lang != null) "$lang.$sub" else sub)
    }

    /**
     * Wikidata P6262 değeri (`jujutsu-kaisen:Satoru_Gojo`) → (wiki kimliği, sayfa başlığı).
     */
    fun parseArticleId(articleId: String?): Pair<String, String>? {
        val raw = articleId?.trim() ?: return null
        val idx = raw.indexOf(':')
        if (idx <= 0 || idx == raw.lastIndex) return null
        val wiki = normalize(raw.substring(0, idx)) ?: return null
        val title = raw.substring(idx + 1).replace('_', ' ').trim()
        if (title.isEmpty()) return null
        return wiki to title
    }

    /**
     * Fandom CDN görselini istenen genişliğe küçültür (`/revision/latest/scale-to-width-down/N`).
     * Başka bir dönüşüm içeren veya Fandom dışı adresler olduğu gibi döner.
     */
    fun scaledImage(url: String, width: Int): String {
        if (!url.contains("static.wikia.nocookie.net") || url.contains("/scale-to-width")) return url
        val marker = "/revision/latest"
        val idx = url.indexOf(marker)
        if (idx < 0) return url
        val insertAt = idx + marker.length
        if (insertAt < url.length && url[insertAt] != '?') return url
        return url.substring(0, insertAt) + "/scale-to-width-down/$width" + url.substring(insertAt)
    }

    /** `//static…` ve `http://` görsel adreslerini `https://` biçimine getirir. */
    fun normalizeImageUrl(url: String?): String? {
        val raw = url?.trim().orEmpty()
        return when {
            raw.startsWith("https://") -> raw
            raw.startsWith("//") -> "https:$raw"
            raw.startsWith("http://") -> "https://" + raw.removePrefix("http://")
            else -> null
        }
    }
}
