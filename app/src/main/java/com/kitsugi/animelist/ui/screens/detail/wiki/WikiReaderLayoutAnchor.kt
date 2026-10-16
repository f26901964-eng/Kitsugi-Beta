package com.kitsugi.animelist.ui.screens.detail.wiki

import kotlin.math.roundToInt

/**
 * OKUMA KONUMU ÇAPASI (wiki okuyucu)
 * ==================================
 *
 * Kullanıcı sözleşmesi: "wiki açılır sayfaları özel olarak çeviriye gidip gelmeli ve
 * konumları da aynen korunmalı". Okuyucuda `Orijinal / Çeviri` geçişi yapıldığında metin
 * uzunluğu değişir (çeviri genelde farklı satır sayısı üretir) ve eski piksel kaydırma
 * değeri aynen bırakılırsa kullanıcı **başka bir paragrafa** bakar. Bu dosya o sıçramayı
 * önler: geçişten önce görünümün üstündeki **blok** çapa olarak alınır, geçişten sonra aynı
 * bloğun içindeki okuma oranı geri kurulur.
 *
 * Neden blok seviyesi? İçindekiler kartı bütün bölümleri içinde barındırır; kart seviyesinde
 * çapa tutmak tek bir bölümün uzamasını tüm kartın oranına yayar ve konum kayardı. Blok
 * seviyesinde çapa, çeviri yapısı korunarak üretildiği için (blok sırası ve türü birebir
 * aynı) geçişten sonra da aynı blokla eşleşir.
 *
 * Bu sınıflar Compose'a bağlı değildir: ölçümler UI thread'inde toplanır, karar mantığı
 * saf fonksiyonlardan oluşur ve birim testleriyle doğrulanabilir.
 */
internal data class WikiReaderAnchor(
    /** Çapa bloğun kimliği: `"<bölüm anahtarı>#b<blok sırası>"` (çeviride de aynıdır). */
    val id: String,
    /** Bloğun içerik koordinatındaki üst kenarı (piksel). */
    val top: Int,
    /** Bloğun yüksekliği (piksel). */
    val height: Int
) {
    /**
     * Geçiş sonrası kaydırma değerini hesaplar.
     *
     *  - Blok görünümün üstündeyse (bloğun içindeyiz) **oran** korunur: kullanıcı bloğun
     *    yüzde kaçındaysa orada kalır — çeviri satır sayısını değiştirdiğinde okuma noktası
     *    kaymaz.
     *  - Blok görünümün altındaysa (araya kart/boşluk girdiyse) aradaki mesafe korunur;
     *    böylece hiçbir durumda içerik yukarı/aşağı zıplamaz.
     *
     * @param newTop aynı bloğun geçiş sonrası üst kenarı.
     * @param newHeight aynı bloğun geçiş sonrası yüksekliği.
     * @param scrollValue geçiş anındaki kaydırma değeri.
     * @param maxScrollValue geçiş sonrası içeriğin en büyük kaydırma değeri (0 ise sınırsız).
     */
    fun restoreScrollValue(
        newTop: Int,
        newHeight: Int,
        scrollValue: Int,
        maxScrollValue: Int
    ): Int {
        val target = when {
            top >= scrollValue -> newTop - (top - scrollValue)
            height <= 0 || newHeight <= 0 -> newTop - (top - scrollValue)
            else -> {
                val ratio = ((scrollValue - top).toFloat() / height.toFloat()).coerceIn(0f, 1f)
                newTop + (ratio * newHeight).roundToInt()
            }
        }
        val max = if (maxScrollValue > 0) maxScrollValue else Int.MAX_VALUE
        return target.coerceIn(0, max)
    }
}

/**
 * Okuyucudaki blok/card ölçümlerini toplar. Değerler **içerik koordinatındadır**: kök
 * koordinatındaki konumdan kaydırma sütununun üst kenarı çıkarılır ve o anki kaydırma
 * değeri eklenir. Böylece kaydırma sırasında bloklar hareket etse de ölçüm sabit kalır.
 *
 * Not: bilinçli olarak Compose state'i kullanmaz; ölçüm `onGloballyPositioned` içinde
 * yerleşim (layout) sırasında yazıldığı için snapshot state yazımı yeniden ölçüm
 * döngülerine yol açabilirdi. Harita yalnızca UI thread'inden okunup yazılır.
 */
internal class WikiReaderLayoutProbe(
    /**
     * O anki kaydırma değeri. Okuyucu, kaydırma durumunu buraya bağlar; ölçümler
     * **içerik koordinatına** çevrilebilsin diye her ölçümde okunur.
     */
    private val scrollValueProvider: () -> Int = { 0 }
) {
    private val tops = HashMap<String, Float>()
    private val heights = HashMap<String, Int>()
    private var containerTop = 0f
    private var containerReady = false

    /** Kaydırma sütununun kök koordinatındaki üst kenarı (görünüm sabittir). */
    fun onContainerPosition(topInRoot: Float) {
        containerTop = topInRoot
        containerReady = true
    }

    /**
     * Bir bloğun ölçümünü kaydeder. Kaydırma sütunu henüz konumlanmadıysa (ilk kare)
     * yazılmaz; sütun ölçülür ölçülmez bloklar kendiliğinden yeniden ölçülür.
     */
    fun record(id: String, topInRoot: Float, height: Int) {
        if (!containerReady) return
        val contentTop = topInRoot - containerTop + scrollValueProvider()
        tops[id] = contentTop
        heights[id] = height
    }

    /** Çeviri geçişinden sonra bloğun yeni üst kenarı (ölçülmediyse null). */
    fun topOf(id: String): Int? = tops[id]?.roundToInt()

    /** Çeviri geçişinden sonra bloğun yeni yüksekliği (ölçülmediyse null). */
    fun heightOf(id: String): Int? = heights[id]

    /**
     * Okuma çapasını seçer.
     *
     *  1. Tercih: görünümün üstünde başlamış ve hâlâ ekranda olan **en alttaki** blok —
     *     yani kullanıcının gerçekten okuduğu paragraf. Ölçüm → oran hesabı bu blokla
     *     en doğru sonucu verir.
     *  2. Yoksa: görünümün altında başlayan ilk blok (araya kart/boşluk girmişse); bu
     *     durumda geri kurma yalnızca mesafeyi korur, içerik zıplamaz.
     */
    fun anchorFor(scrollValue: Int): WikiReaderAnchor? {
        var inside: WikiReaderAnchor? = null
        var below: WikiReaderAnchor? = null
        for ((id, topInContent) in tops) {
            val height = heights[id] ?: continue
            val top = topInContent.roundToInt()
            if (top + height <= scrollValue) continue
            val candidate = WikiReaderAnchor(id, top, height)
            if (top <= scrollValue) {
                val current = inside
                if (current == null || top > current.top) inside = candidate
            } else {
                val current = below
                if (current == null || top < current.top) below = candidate
            }
        }
        return inside ?: below
    }

    /**
     * Bir blok bileşimden çıktığında (bölüm kapandı, yüzey değişti) ölçümü bırakılır:
     * böylece çapa seçimi her zaman EKRANDA OLAN bloklar arasından yapılır.
     */
    fun forget(id: String) {
        tops.remove(id)
        heights.remove(id)
    }

    /** Sayfa değişiminde eski sayfanın ölçümleri taşınmaz. */
    fun clear() {
        tops.clear()
        heights.clear()
    }

    /** Ölçüm kimliği: bölüm anahtarı + blok sırası (çeviride de aynı kalır). */
    companion object {
        fun blockId(sectionKey: String, index: Int): String = "$sectionKey#b$index"

        /** Kart içindeki tek paragraf yüzeyleri (ör. başlık açıklaması) için kimlik. */
        fun surfaceId(key: String, surface: String): String = "$key#$surface"

        const val SURFACE_DESCRIPTION = "desc"
    }
}
