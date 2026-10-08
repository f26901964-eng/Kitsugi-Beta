package com.kitsugi.animelist.utils

/**
 * TMDB ve Simkl medya başlıkları için tek merkezi başlık çözümleyicisi.
 *
 * Kural (kullanıcı sözleşmesi — Türkçe seçiliyken):
 *   1) Yerelleştirilmiş başlık (Türkçe) varsa onu kullan.
 *   2) Yoksa İngilizce başlığı kullan.
 *   3) O da yoksa Romaji / Latin harfli orijinal başlığı kullan.
 *   4) Hiçbiri yoksa elde kalan başlık gösterilir — ekran asla boş kalmaz.
 *
 * Japonca/Çince/Korece (CJK) başlıklar 1-3 adımlarında ASLA kabul edilmez.
 *
 * Neden gerekli: TMDB `language=tr-TR` ile çağrıldığında Türkçe çevirisi olmayan
 * içerikler için orijinal (Japonca) başlığı döndürür. İstemci katmanı bu durumda
 * otomatik olarak en-US başlığa, o da yoksa Latin orijinal başlığa düşmelidir.
 */
object MediaTitleResolver {

    /**
     * Başlık çözümleme sürümü.
     *
     * Başlık seçim mantığı değiştiğinde bu değer artırılır; bellek içi ve disk
     * önbellek anahtarları sürümle birlikte tutulduğu için eski (hatalı) başlıklar
     * önbellekten servis edilmez.
     */
    const val VERSION = 2

    fun hasCjk(text: String?): Boolean = PreferenceHelpers.hasCjkCharacters(text)

    /**
     * TMDB / Simkl kaynaklı medya, kullanıcı hangi başlık dilini seçerse seçsin
     * Latin alfabesinde gösterilir (Türkçe → İngilizce → Romaji). Bu kaynaklarda
     * "orijinal başlık" çoğunlukla Japonca/Çince olduğu için ekranlarda CJK
     * başlık görünmesi istenmez.
     */
    fun isLatinPreferredSource(source: String?): Boolean {
        val s = source?.lowercase().orEmpty()
        return s == "tmdb" || s == "simkl"
    }

    /**
     * Gösterilebilir Latin başlık: boş değil ve CJK karakter içermiyor.
     * Uygun değilse null döner.
     */
    fun latin(text: String?): String? =
        text?.trim()?.takeIf { it.isNotEmpty() && !hasCjk(it) }

    /** Boş olmayan ilk başlık — CJK olsa bile (son çare). */
    fun nonBlank(vararg titles: String?): String? =
        titles.firstOrNull { !it.isNullOrBlank() }?.trim()

    /**
     * Adaylar içinden ilk gösterilebilir (Latin, CJK içermeyen) başlığı döner.
     *
     * Ör. Simkl yanıtlarında `title` Japonca olabilir; `title_en`/`title_romaji`
     * gibi alanlar sırayla denenerek ekrana CJK düşmesi engellenir.
     */
    fun firstLatin(vararg candidates: String?): String? {
        for (candidate in candidates) {
            val value = latin(candidate)
            if (value != null) return value
        }
        return null
    }

    /**
     * Türkçe → İngilizce → Romaji zinciri.
     *
     * @param localized Yerelleştirilmiş başlık (TMDB aktif dil / Simkl yerel başlık)
     * @param english   İngilizce başlık (en-US)
     * @param romaji    Latin harfli orijinal/alternatif başlık (romaji)
     * @param original  Orijinal başlık — CJK olabilir, yalnızca son çare
     * @return Asla boş olmayan gösterim başlığı
     */
    fun resolve(
        localized: String?,
        english: String? = null,
        romaji: String? = null,
        original: String? = null
    ): String =
        latin(localized)
            ?: latin(english)
            ?: latin(romaji)
            ?: latin(original)
            ?: nonBlank(localized, english, romaji, original)
            ?: ""

    /** İngilizce başlık alanı için: daima Latin harfli bir değer (varsa). */
    fun resolveEnglish(
        localized: String?,
        english: String?,
        romaji: String? = null,
        original: String? = null
    ): String? =
        latin(english)
            ?: latin(localized)
            ?: latin(romaji)
            ?: latin(original)

    /** Yerel (Japonca/Çince/Korece) başlık alanı için: yalnızca gerçekten CJK olan adaylar. */
    fun resolveNative(vararg candidates: String?): String? =
        candidates.firstOrNull { !it.isNullOrBlank() && hasCjk(it) }?.trim()
            ?: candidates.firstOrNull { !it.isNullOrBlank() }?.trim()

    /**
     * TMDB yerelleştirilmiş başlığının "gerçek" olup olmadığını belirler.
     *
     * TMDB, istenen dilde başlık bulamazsa orijinal başlığı döndürür. Bu durumda
     * istek dili ile orijinal dil farklıysa yerelleştirilmiş başlık sahte kabul
     * edilir ve zincir İngilizce'ye düşer. (Örn. tr isteği + orijinal dil ja.)
     */
    fun isLocalizedFallback(
        localized: String?,
        original: String?,
        requestedLanguage: String?,
        originalLanguage: String?
    ): Boolean {
        if (localized.isNullOrBlank()) return true
        val requestIso = requestedLanguage?.substringBefore('-')?.lowercase().orEmpty()
        val originalIso = originalLanguage?.lowercase().orEmpty()
        if (originalIso.isNotBlank() && requestIso.isNotBlank() && originalIso == requestIso) return false
        return localized.trim().equals(original?.trim(), ignoreCase = true)
    }

    /**
     * Alternatif başlık listesinden (TMDB alternative_titles) uygun Latin başlığı seçer.
     *
     * @param entries "iso_3166_1"/ülke kodu → başlık
     * @param preferredCountries öncelikli ülke kodları (örn. TR, US)
     */
    fun pickAlternative(
        entries: List<Pair<String, String>>,
        preferredCountries: List<String>
    ): String? {
        val latinEntries = entries.filter { latin(it.second) != null }
        if (latinEntries.isEmpty()) return null
        for (country in preferredCountries) {
            latinEntries.firstOrNull { it.first.equals(country, ignoreCase = true) }?.let { return it.second.trim() }
        }
        return latinEntries.first().second.trim()
    }
}
