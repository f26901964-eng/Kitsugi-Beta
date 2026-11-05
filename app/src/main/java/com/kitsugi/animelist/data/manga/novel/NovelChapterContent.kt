package com.kitsugi.animelist.data.manga.novel

/**
 * Light novel / web novel bölüm içeriği.
 *
 * Neden gerekli?
 *  - Türkçe ve yabancı kaynaklarda (ör. araznovel, novelupdates aynaları,
 *    web-novel siteleri) "bölüm" bir HTML sayfasıdır; sayfa listesi tek bir
 *    `text/html` adresi döner. Manga sayfa okuyucu bunu görsel sanıp
 *    yüklemeye çalışınca boş ekran veriyordu.
 *  - LNReader (açık kaynak light novel okuyucu) modeli örnek alındı: bölüm
 *    içeriği bloklara ayrılır, tipografi kullanıcı ayarına göre çizilir,
 *    ilerleme metin içinde yüzde olarak tutulur.
 */
data class NovelChapterContent(
    /** Bölüm başlığı (sayfadan çıkarılabilirse). */
    val title: String? = null,
    /** Paragraf/başlık blokları — sırayla çizilir. */
    val blocks: List<NovelBlock> = emptyList(),
    /** Kaynak sayfa adresi (paylaş / tarayıcıda aç için). */
    val sourceUrl: String = "",
    /** Çıkarım başarısızsa kullanıcıya gösterilecek neden. */
    val error: String? = null
) {
    val isEmpty: Boolean get() = blocks.isEmpty()
}

/** Basit, stilize edilebilir metin bloğu. */
data class NovelBlock(
    val text: String,
    val style: NovelBlockStyle = NovelBlockStyle.Paragraph
)

enum class NovelBlockStyle {
    Title,
    Heading,
    Paragraph,
    Quote,
    /** Görsel (illüstrasyon) — URL taşır. */
    Image
}

/** Novel okuyucu tipografi/tema ayarları (LNReader paritesi, sadeleştirilmiş). */
data class NovelReaderStyle(
    val fontSizeSp: Int = 17,
    val lineHeightMultiplier: Float = 1.7f,
    /** Sütun genişliği sınırı (dp) — geniş ekranda satır uzunluğunu dengeler. */
    val maxContentWidthDp: Int = 720,
    val theme: NovelTheme = NovelTheme.Dark,
    /** Metni iki yana yasla. */
    val justified: Boolean = true,
    /** Arapça/İbranice/Farsça metinler için sağdan sola akış. */
    val rtl: Boolean = false,
    /** Paragraflar arası ek boşluk. */
    val paragraphSpacing: Boolean = true
)

enum class NovelTheme {
    /** Saf siyah zemin, düşük kontrastlı beyaz metin (gece). */
    Dark,

    /** Klasik okuma: krem zemin, koyu metin. */
    Sepia,

    /** Beyaz zemin (gündüz). */
    Light,

    /** Uygulama temasını takip et. */
    System;

    companion object {
        fun fromId(id: String?): NovelTheme =
            entries.firstOrNull { it.name == id } ?: Dark
    }
}
