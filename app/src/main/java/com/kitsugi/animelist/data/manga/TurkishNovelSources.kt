package com.kitsugi.animelist.data.manga

/**
 * Türkçe LIGHT NOVEL / WEB NOVEL kaynaklarının tanınması.
 *
 * Neden ayrı bir liste?
 *  - `MangaSource` arayüzü hem manga hem novel eklentilerini taşıyor
 *    (aynı Mihon/Kotatsu eklenti API'si). Novel kaynakları sayfa yerine HTML
 *    metin döndürdüğü için okuyucu tarafında AYRI ele alınmalı.
 *  - Bu liste, kaynak adından novel olup olmadığını hızlı ve ağsız söyler;
 *    böylece bölüm açılırken yanlış okuyucu (boş sayfa) açılmaz.
 */
object TurkishNovelSources {

    /** Adında bu kökler geçen kaynaklar novel kabul edilir. */
    private val novelTokens = listOf(
        "novel", "noveltr", "lightnovel", "webnovel", "ranobe", "ranobes",
        "araznovel", "novelarsivi", "noveldegil", "lnmanga", "wattpad"
    )

    /** Novel olduğu kesin Türkçe kaynaklar (küçük harf, normalize edilmiş). */
    private val knownNovelSources = setOf(
        "araznovel", "noveltr", "lightnoveltr", "webnoveltr", "ranobes"
    )

    /** Kaynak adı novel kaynağı mı? */
    fun isNovelSource(sourceName: String): Boolean {
        val name = normalize(sourceName)
        if (name.isEmpty()) return false
        if (knownNovelSources.any { name.contains(it) }) return true
        return novelTokens.any { name.contains(it) }
    }

    /** Kaynak adından okunabilir etiket ("Light Novel" / "Web Novel"). */
    fun labelFor(sourceName: String): String =
        if (isNovelSource(sourceName)) "Light Novel" else "Manga"

    /**
     * Novel kaynağında bölüm listesi genelde cilt/başlık karışık gelir;
     * "Önsöz / Son söz / SSS" gibi meta girişleri ayıklamak için ipucu listesi.
     */
    fun isMetaChapter(chapterName: String): Boolean {
        val n = normalize(chapterName)
        val meta = listOf(
            "ön söz", "onsoz", "prolog", "prologue", "son söz", "sons oz", "epilog", "epilogue",
            "sss", "faq", "yazar notu", "yazarın notu", "duyuru", "teşekkür", "tesekkur",
            "kapak", "cover", "içindekiler", "icindekiler", "table of contents"
        )
        return meta.any { n.contains(it) }
    }

    private fun normalize(value: String): String =
        value.lowercase(java.util.Locale.ROOT)
            .replace('ı', 'i').replace('ş', 's').replace('ğ', 'g')
            .replace('ü', 'u').replace('ö', 'o').replace('ç', 'c')
            .replace(" ", "")
}
