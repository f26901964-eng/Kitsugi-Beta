package com.kitsugi.animelist.core.player

/**
 * Tüm oynatıcı motorları (Media3 ve MPV) için TEK altyazı seçim politikası.
 *
 * Öncelik sırası (kesin):
 *  1. Videonun İÇİNDEKİ (dahili / site kaynaklı) Türkçe altyazı
 *  2. Harici (eklenti / OpenSubtitles vb.) Türkçe altyazı
 *  3. Kullanıcının diğer tercih dilleri (sırayla): önce dahili, sonra harici
 *  4. Eşleşme yoksa null → altyazı kapatılır (yabancı dil ASLA rastgele seçilmez)
 *
 * Türkçe tanıma [PlayerSubtitleUtils.isTurkish] üzerinden yapılır: "tr", "tr-TR", "tur",
 * "Türkçe", "Turkish", "[TR]" gibi varyantları kapsar. Tercih listesinde "tr" yoksa başa eklenir.
 *
 * Bu sınıf saf Kotlin'dir (Android bağımlılığı yok) ve birim testle doğrulanabilir.
 */
object SubtitleSelectionPolicy {

    /**
     * @param item       Motorun kendi track nesnesi (TrackOption, MpvTrack vb.)
     * @param lang       Track dil kodu (varsa)
     * @param label      Track etiketi / adı (varsa)
     * @param isExternal Harici (eklenti) altyazı mı? Videonun kendi track'i ise false.
     */
    data class Candidate<T>(
        val item: T,
        val lang: String?,
        val label: String?,
        val isExternal: Boolean,
    )

    /** Tercih listesini Türkçe'yi içerecek ve Türkçe başta olacak şekilde normalize eder. */
    fun effectiveLanguages(preferredLangs: List<String>): List<String> =
        if (preferredLangs.any { PlayerSubtitleUtils.matchesLanguageCode(it, "tr") }) {
            preferredLangs
        } else {
            listOf("tr") + preferredLangs
        }

    /**
     * Adaylar arasından politikaya göre en iyi altyazıyı seçer. Eşleşme yoksa null döner.
     */
    fun <T> pick(candidates: List<Candidate<T>>, preferredLangs: List<String>): T? {
        if (candidates.isEmpty()) return null

        fun isTr(c: Candidate<T>) = PlayerSubtitleUtils.isTurkish(c.lang, c.label)

        // 1. Videonun içindeki Türkçe
        candidates.firstOrNull { !it.isExternal && isTr(it) }?.let { return it.item }
        // 2. Harici Türkçe
        candidates.firstOrNull { it.isExternal && isTr(it) }?.let { return it.item }

        // 3. Diğer tercih dilleri (tr zaten yukarıda ele alındı)
        for (lang in effectiveLanguages(preferredLangs)) {
            if (PlayerSubtitleUtils.matchesLanguageCode(lang, "tr")) continue
            candidates.firstOrNull {
                !it.isExternal && PlayerSubtitleUtils.matchesTrackLanguage(it.lang, it.label, lang)
            }?.let { return it.item }
            candidates.firstOrNull {
                it.isExternal && PlayerSubtitleUtils.matchesTrackLanguage(it.lang, it.label, lang)
            }?.let { return it.item }
        }

        // 4. Eşleşme yok
        return null
    }
}
