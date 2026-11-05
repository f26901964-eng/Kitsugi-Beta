package com.kitsugi.animelist.data.manga

/**
 * ÇİFT SAYFA (2-up / spread) eşleştirme mantığı.
 *
 * Neden gerekli?
 *  - Mihon/Tachiyomi'de "Dual page view" tabletlerde ve yatay ekranlarda
 *    iki sayfayı yan yana gösterir. Kitsugi'de bu yoktu.
 *  - Dikine (portre) poster oranlı sayfalar tek başına ekranda çok yer kaplar;
 *    yatay ekran/tablet kullanıcısı için yan yana iki sayfa doğal okuma sağlar.
 *
 * Bu nesne tamamen saf (ağ/UI bağımsız) — birim testleriyle doğrulanır.
 */
enum class DualPageMode {
    /** Tek sayfa (varsayılan). */
    Off,

    /** Her zaman iki sayfa yan yana. */
    Always,

    /**
     * Yalnızca ekran yeterince genişken (tablet veya telefon yatay modda)
     * iki sayfa göster; dar/portre ekranda tek sayfaya dön.
     */
    WideOnly;

    companion object {
        fun fromId(id: String?): DualPageMode = when (id?.uppercase()) {
            "ON", "ALWAYS" -> Always
            "AUTO", "WIDEONLY", "WIDE" -> WideOnly
            else -> Off
        }

        fun idOf(mode: DualPageMode): String = when (mode) {
            Off -> "OFF"
            Always -> "ON"
            WideOnly -> "AUTO"
        }
    }
}

object MangaPagePairing {

    /** Geniş ekran eşiği (dp). Telefon yatay (~800dp) ve tabletler bu değeri geçer. */
    const val WIDE_SCREEN_DP = 600

    /**
     * Sayfa indekslerini çiftlere böler: [0,1], [2,3], …
     * Tek sayı kaldıysa son çift tek elemanlı olur (ör. kapak).
     */
    fun spreads(count: Int): List<List<Int>> {
        if (count <= 0) return emptyList()
        return (0 until count).chunked(2)
    }

    /** Bir sayfanın hangi çiftin içinde olduğu. */
    fun spreadIndexOf(page: Int): Int = if (page <= 0) 0 else page / 2

    /** Çiftin ilk (en küçük) sayfa indeksi — ilerleme kaydı için. */
    fun firstPageOf(spread: List<Int>): Int = spread.minOrNull() ?: 0

    /**
     * Bir çiftin EKRANDA soldan sağa gösterim sırası.
     * RTL manga'da ilk sayfa sağda olmalıdır → liste ters çevrilir.
     */
    fun displayOrder(spread: List<Int>, rightToLeft: Boolean): List<Int> =
        if (rightToLeft) spread.reversed() else spread

    /**
     * Sayfa dikine (portre) mi? Çift sayfa yalnızca dikine sayfalarda anlamlıdır.
     * 1.0 (kare) ve üstü geniş sayılır → tek başına tam genişlik gösterilir.
     */
    fun isPortrait(aspectRatio: Float, threshold: Float = 0.95f): Boolean =
        aspectRatio in 0.01f..threshold

    /**
     * Sayfaları "akıllı" çiftler: yalnızca DİKİNE ve ardışık iki sayfa eşleşir.
     * Araya giren geniş (spread) sayfa tek başına kalır ve ondan sonraki
     * eşleşme sırası bozulmadan devam eder — böylece sayfa sırası ASLA değişmez.
     *
     * @param aspects pageIndex → genişlik/yükseklik. Bilinmeyen sayfa dikine sayılır.
     */
    fun smartSpreads(count: Int, aspects: Map<Int, Float>): List<List<Int>> {
        if (count <= 0) return emptyList()
        val result = ArrayList<List<Int>>(count / 2 + 1)
        var i = 0
        while (i < count) {
            val ratio = aspects[i] ?: 1f / 1.4f
            val nextExists = i + 1 < count
            val nextRatio = if (nextExists) aspects[i + 1] ?: 1f / 1.4f else 0f
            if (isPortrait(ratio) && nextExists && isPortrait(nextRatio)) {
                result.add(listOf(i, i + 1))
                i += 2
            } else {
                result.add(listOf(i))
                i += 1
            }
        }
        return result
    }

    /**
     * Verilen sayfanın çift indeksini bulur (oynatıcı/pager konumu için).
     * Sayfa geniş olduğu için tek başınaysa da kendi çifti döner.
     */
    fun spreadIndexForPage(spreads: List<List<Int>>, page: Int): Int {
        val idx = spreads.indexOfFirst { it.contains(page) }
        return if (idx >= 0) idx else 0
    }

    /**
     * Elde iki sayfa yan yana gösterilmeli mi?
     *
     * @param mode Kullanıcı tercihi.
     * @param availableWidthDp Ekran/genişlik dp cinsinden.
     */
    fun shouldShowTwoPages(mode: DualPageMode, availableWidthDp: Int): Boolean = when (mode) {
        DualPageMode.Off -> false
        DualPageMode.Always -> true
        DualPageMode.WideOnly -> availableWidthDp >= WIDE_SCREEN_DP
    }
}
