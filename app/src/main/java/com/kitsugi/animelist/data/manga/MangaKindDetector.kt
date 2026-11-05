package com.kitsugi.animelist.data.manga

/**
 * Serinin TÜRÜNÜ (manga / manhwa / manhua / webtoon / light novel) tahmin eder
 * ve buna uygun **varsayılan okuma modunu** önerir.
 *
 * Neden? Kullanıcı manhwa açtığında uygulama hâlâ "sağdan sola sayfalama" ile
 * açılıyordu; her seferinde elle mod değiştirmek gerekiyordu. Kotatsu-Redo bunu
 * "auto-detect the best mode for each title" olarak sunuyor.
 *
 * Sinyaller (en güçlüden zayıfa):
 *  1. Kullanıcının bu seri için seçtiği mod (kalıcı, her şeyi ezer)
 *  2. Kaynak adı (ör. "AsuraScans", "Webtoon", "Novel")
 *  3. Tür etiketleri (Manhwa / Manhua / Webtoon / Light Novel / Novel)
 *  4. Başlıkta geçen "manhwa / manhua / novel" kelimeleri
 *  5. Bölüm içeriği: tek HTML sayfası → novel, çok uzun tek sayfa → webtoon
 */
object MangaKindDetector {

    enum class Kind(val label: String) {
        Manga("Manga"),
        Manhwa("Manhwa"),
        Manhua("Manhua"),
        Webtoon("Webtoon"),
        LightNovel("Light Novel"),
        Unknown("Bilinmiyor")
    }

    /** Kaynak adında geçen ipuçları. */
    private val manhwaSourceHints = listOf("manhwa", "asura", "void", "reaper", "toonily", "webtoon", "flame", "manga18")
    private val manhuaSourceHints = listOf("manhua", "wuxia", "baozimh", "kuaikan", "dongman")
    private val novelSourceHints = listOf("novel", "wuxiaworld", "royalroad", "webnovel", "lnreader", "ranobes", "araznovel")

    /** Etiket eşleşmeleri (küçük harf). */
    private val manhwaTags = listOf("manhwa", "kore", "korean")
    private val manhuaTags = listOf("manhua", "çin", "cin", "chinese")
    private val webtoonTags = listOf("webtoon", "long strip", "uzun şerit")
    private val novelTags = listOf("novel", "light novel", "roman", "web novel", "ln")

    /**
     * @param sourceName Eklenti/kaynak adı.
     * @param title Seri başlığı.
     * @param genres Türler.
     * @param chapterPageCount Bölümün sayfa sayısı (biliniyorsa; novel tek "sayfa").
     * @param singleHtmlChapter Bölüm tek bir HTML metin sayfası mı.
     */
    fun detect(
        sourceName: String,
        title: String,
        genres: List<String> = emptyList(),
        chapterPageCount: Int = -1,
        singleHtmlChapter: Boolean = false
    ): Kind {
        val src = sourceName.lowercase()
        val ttl = title.lowercase()
        val tags = genres.map { it.lowercase() }

        // 1) Roman: kaynak adı / tür / tek HTML sayfa
        if (TurkishNovelSources.isNovelSource(sourceName)) return Kind.LightNovel
        if (novelSourceHints.any { it in src }) return Kind.LightNovel
        if (tags.any { tag -> novelTags.any { tag == it || tag.contains(it) } }) return Kind.LightNovel
        if (ttl.contains("novel") || ttl.contains("light novel")) return Kind.LightNovel
        if (singleHtmlChapter && chapterPageCount <= 1) return Kind.LightNovel

        // 2) Manhwa (Kore) — dikey webtoon akışı
        if (manhwaTags.any { tag -> tags.any { it == tag || it.contains(tag) } }) return Kind.Manhwa
        if (manhwaSourceHints.any { it in src }) return Kind.Manhwa
        if ("manhwa" in ttl) return Kind.Manhwa

        // 3) Manhua (Çin) — genelde renkli, LTR
        if (manhuaTags.any { tag -> tags.any { it == tag || it.contains(tag) } }) return Kind.Manhua
        if (manhuaSourceHints.any { it in src }) return Kind.Manhua
        if ("manhua" in ttl) return Kind.Manhua

        // 4) Açıkça webtoon etiketi
        if (webtoonTags.any { tag -> tags.any { it.contains(tag) } }) return Kind.Webtoon

        // 5) İçerik ipucu: çok uzun tek sayfa → webtoon
        if (chapterPageCount in 1..3) return Kind.Webtoon

        // 6) Manhwa/Manhua kaynaklı olmayan, tür etiketi "Manga" olan → manga
        if (tags.any { it == "manga" }) return Kind.Manga

        return Kind.Unknown
    }

    /**
     * Türe göre önerilen okuma modu. [Unknown] → manga varsayılanı (RTL).
     * Not: Kullanıcının kayıtlı tercihi her zaman bu öneriyi ezer.
     */
    fun recommendedMode(kind: Kind): ReadingMode = when (kind) {
        Kind.Manhwa, Kind.Webtoon -> ReadingMode.Webtoon
        Kind.Manhua -> ReadingMode.LeftToRight
        Kind.Manga -> ReadingMode.RightToLeft
        // Roman türü sayfa okuyucu kullanmaz; yine de güvenli varsayılan.
        Kind.LightNovel, Kind.Unknown -> ReadingMode.RightToLeft
    }

    /** Rozet rengi/etiketi için kısa açıklama (UI'da çip olarak gösterilir). */
    fun describe(kind: Kind): String = when (kind) {
        Kind.Manga -> "Manga · sağdan sola"
        Kind.Manhwa -> "Manhwa · dikey kaydırma"
        Kind.Manhua -> "Manhua · soldan sağa"
        Kind.Webtoon -> "Webtoon · kesintisiz"
        Kind.LightNovel -> "Light Novel · metin"
        Kind.Unknown -> "Tür algılanamadı"
    }
}
