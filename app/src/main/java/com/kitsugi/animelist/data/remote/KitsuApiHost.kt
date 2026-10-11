package com.kitsugi.animelist.data.remote

/**
 * Kitsu JSON:API uç adresi.
 *
 * NEDEN SEKİYOR?
 * Kitsu 2024'te `kitsu.io` alan adını `kitsu.app`'a taşıdı. `kitsu.io/api/edge` hâlâ
 * yanıt veriyor, fakat yalnızca uyumluluk katmanı olarak: bu uçta `sort` ve
 * `page[offset]` yok sayılıyor, her sayfa isteği aynı ilk 20 kaydı döndürüyor.
 * Kitugi'de bunun iki belirtisi görüldü:
 *  - Keşfet → Kitsu "Tümünü Gör" sayfalarında kaydırma 20 içerikte kilitli kalıyordu
 *    (2. sayfa 1. sayfanın aynısı → tekilleştirme sonrası listeye hiçbir şey eklenmiyordu).
 *  - Kitsu karakter listesinde kopya kayıtlar oluşuyordu (bkz. v2.4.205 sayfalama düzeltmesi).
 *
 * Bu yüzden birincil adres [PRIMARY]; ağ/HTTP hatası halinde istekler [LEGACY] üzerinden
 * sürdürülür ve süreç boyunca yedek adres tercih edilir.
 *
 * Yalnızca herkese açık okuma uçları için kullanılır. OAuth ve kullanıcı listesi
 * akışları ([com.kitsugi.animelist.data.auth.KitsuApiClient]) kimlik sözleşmesi
 * değişmesin diye bilinen adresinde bırakılmıştır.
 */
internal object KitsuApiHost {

    /** Kitsu'nun güncel kanonik API adresi. */
    const val PRIMARY = "https://kitsu.app/api/edge"

    /** Eski adres; yönlendirici/uyumluluk katmanı (sayfalama ve sıralama parametreleri yok sayılır). */
    const val LEGACY = "https://kitsu.io/api/edge"

    /** Her zaman kanonik adres tercih edilir; LEGACY yalnızca istek bazında yedektir. */
    val base: String get() = PRIMARY

    /**
     * Denenecek adres sırası: her zaman önce kanonik [PRIMARY], ağ hatası halinde yedek [LEGACY].
     * `LEGACY` (`kitsu.io`) sıralama ve sayfalama parametrelerini yok saydığı için hiçbir zaman
     * kalıcı birincil adrese yükseltilmez.
     */
    fun candidates(): List<String> = listOf(PRIMARY, LEGACY)

    /** Geriye dönük uyumluluk için korunur (no-op). */
    fun degrade() = Unit

    /** Tam adres üretir: `pathAndQuery` "/" ile başlar (ör. `/anime?page[limit]=20`). */
    fun url(pathAndQuery: String, host: String = base): String = host + pathAndQuery
}
