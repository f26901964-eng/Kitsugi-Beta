package com.kitsugi.animelist.data.trailer

/**
 * Bir videonun oynatılabilir akış adresi.
 *
 * [videoUrl] birincil akıştır. YouTube imzalı adresleri kısa ömürlüdür ve HLS manifesti
 * imzalandıktan sonra bile 403 dönebiliyor; bu yüzden aynı çıkarımdan elde edilmiş
 * yedekler [alternateVideoUrls] içinde sırayla saklanır. Oynatıcı birincil akışı
 * açamazsa motoru değiştirmeden önce bu listedekileri dener — "video oynamıyor"
 * şikâyetinin en sık nedeni buydu.
 */
data class TrailerPlaybackSource(
    val videoUrl: String,
    val audioUrl: String? = null,
    /** Birincil akış açılamazsa denenecek yedek video adresleri (kalite sırasıyla). */
    val alternateVideoUrls: List<String> = emptyList(),
    /** [alternateVideoUrls] ile aynı indeksteki ses adresleri; `null` = akış sesli. */
    val alternateAudioUrls: List<String?> = emptyList()
) {
    /** Belirtilen indeks için (video, ses) çifti; 0 birincil akıştır. */
    fun sourceAt(index: Int): Pair<String, String?>? {
        if (index == 0) return videoUrl to audioUrl
        val video = alternateVideoUrls.getOrNull(index - 1) ?: return null
        val audio = alternateAudioUrls.getOrNull(index - 1)
        return video to audio
    }

    /** Denenebilecek toplam akış sayısı (birincil + yedekler). */
    val totalSources: Int
        get() = 1 + alternateVideoUrls.size
}
