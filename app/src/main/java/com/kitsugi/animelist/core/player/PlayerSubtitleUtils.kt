package com.kitsugi.animelist.core.player

/**
 * S04 – Dil eşleşme yardımcısı.
 *
 * KitsugiTV-dev PlayerSubtitleUtils.matchesLanguageCode() portu.
 * Stremio addon'larından gelen ham dil kodlarını normalize ederek
 * kullanıcının tercih ettiği dil listesiyle karşılaştırır.
 *
 * Örnekler:
 *  - "tr", "tur", "Türkçe", "Turkish", "Tur" → "tr" ile eşleşir
 *  - "en", "eng", "English", "İngilizce"     → "en" ile eşleşir
 *  - "jp", "jpn", "Japanese", "ja"            → "ja" ile eşleşir
 */
object PlayerSubtitleUtils {

    /** BCP-47 2-harf kodu → eşleşen ham string setleri (küçük harf) */
    private val LANG_ALIASES: Map<String, Set<String>> = mapOf(
        "tr" to setOf("tr", "tur", "türkçe", "turkce", "turkish", "turk", "turkish language"),
        "en" to setOf("en", "eng", "english", "ingilizce", "english language"),
        "ja" to setOf("ja", "jp", "jpn", "japanese", "japonca", "日本語"),
        "ar" to setOf("ar", "ara", "arabic", "arapça"),
        "de" to setOf("de", "deu", "ger", "german", "almanca"),
        "fr" to setOf("fr", "fra", "fre", "french", "fransızca"),
        "es" to setOf("es", "spa", "spanish", "ispanyolca"),
        "it" to setOf("it", "ita", "italian", "italyanca"),
        "pt" to setOf("pt", "por", "portuguese", "portekizce"),
        "ru" to setOf("ru", "rus", "russian", "rusça"),
        "ko" to setOf("ko", "kor", "korean", "korece"),
        "zh" to setOf("zh", "chi", "zho", "chinese", "çince", "中文"),
        "nl" to setOf("nl", "nld", "dutch", "flemish"),
        "pl" to setOf("pl", "pol", "polish"),
        "sv" to setOf("sv", "swe", "swedish"),
        "no" to setOf("no", "nor", "norwegian"),
        "da" to setOf("da", "dan", "danish"),
        "fi" to setOf("fi", "fin", "finnish"),
        "cs" to setOf("cs", "cze", "ces", "czech"),
        "hu" to setOf("hu", "hun", "hungarian"),
        "ro" to setOf("ro", "ron", "rum", "romanian"),
        "el" to setOf("el", "gre", "ell", "greek"),
        "he" to setOf("he", "heb", "hebrew"),
        "fa" to setOf("fa", "per", "fas", "persian"),
        "id" to setOf("id", "ind", "indonesian"),
        "ms" to setOf("ms", "msa", "malay"),
        "th" to setOf("th", "tha", "thai"),
        "vi" to setOf("vi", "vie", "vietnamese")
    )

    /**
     * Verilen ham dil string'inin [targetLangCode] ile eşleşip eşleşmediğini döner.
     *
     * @param rawLang    Addon'dan gelen ham dil string'i (örn: "Türkçe", "tr", "tur")
     * @param targetCode Karşılaştırılacak BCP-47 kodu (örn: "tr", "en")
     */
    fun matchesLanguageCode(rawLang: String, targetCode: String): Boolean {
        val normalizedRaw = rawLang.trim().lowercase()
        val normalizedTarget = targetCode.trim().lowercase()

        // Doğrudan eşleşme
        if (normalizedRaw == normalizedTarget) return true

        // Alias tablosundan karşılaştırma
        val targetAliases = LANG_ALIASES[normalizedTarget] ?: setOf(normalizedTarget)
        if (normalizedRaw in targetAliases) return true

        // Hedef kodun hangi alias grubunda olduğunu da kontrol et
        // (örn: rawLang = "tr", targetCode = "tur" → tur grubuna bak)
        val rawCode = LANG_ALIASES.entries.firstOrNull { (_, aliases) ->
            normalizedRaw in aliases
        }?.key

        if (rawCode != null) {
            val targetNormalized = LANG_ALIASES.entries.firstOrNull { (_, aliases) ->
                normalizedTarget in aliases
            }?.key ?: normalizedTarget
            return rawCode == targetNormalized
        }

        return false
    }

    /**
     * Verilen altyazı listesini tercih listesine göre sıralar.
     * Önce tercih edilen diller (sırasıyla), sonra diğerleri.
     *
     * @param subtitles      Sıralanacak altyazı listesi (Subtitle domain modeli)
     * @param preferredLangs Tercih sırası (BCP-47): ["tr", "en"] gibi
     */
    fun <T> sortByPreference(
        subtitles: List<T>,
        preferredLangs: List<String>,
        getLang: (T) -> String
    ): List<T> {
        if (preferredLangs.isEmpty()) return subtitles
        return subtitles.sortedWith(Comparator { a, b ->
            val aIdx = preferredLangs.indexOfFirst { matchesLanguageCode(getLang(a), it) }
                .let { if (it == -1) Int.MAX_VALUE else it }
            val bIdx = preferredLangs.indexOfFirst { matchesLanguageCode(getLang(b), it) }
                .let { if (it == -1) Int.MAX_VALUE else it }
            aIdx.compareTo(bIdx)
        })
    }

    /**
     * KitsugiPlayerViewModel'ın aradığı özel tipteki (SubtitleInput) sıralama metodu.
     */
    fun sortSubtitlesByPreference(
        subtitles: List<SubtitleInput>,
        preferredLangs: List<String>
    ): List<SubtitleInput> {
        return sortByPreference(subtitles, preferredLangs) { it.lang ?: "" }
    }

    /**
     * Listeden ilk eşleşen tercih edilen altyazıyı seç.
     * Önce "tr", bulamazsa "en", hiçbiri yoksa ilk altyazı.
     */
    fun <T> autoSelect(
        subtitles: List<T>,
        preferredLangs: List<String>,
        getLang: (T) -> String
    ): T? {
        if (subtitles.isEmpty()) return null
        for (lang in preferredLangs) {
            val match = subtitles.firstOrNull { matchesLanguageCode(getLang(it), lang) }
            if (match != null) return match
        }
        return subtitles.firstOrNull()
    }

    /**
     * Dil kodunu veya ham dil adını kullanıcı dostu Türkçe dil adına çevirir.
     */
    fun getFriendlyLanguageName(lang: String): String {
        val normalized = lang.trim().lowercase()
        return when {
            matchesLanguageCode(normalized, "tr") -> "Türkçe"
            matchesLanguageCode(normalized, "en") -> "İngilizce"
            matchesLanguageCode(normalized, "ja") -> "Japonca"
            matchesLanguageCode(normalized, "ar") -> "Arapça"
            matchesLanguageCode(normalized, "de") -> "Almanca"
            matchesLanguageCode(normalized, "fr") -> "Fransızca"
            matchesLanguageCode(normalized, "es") -> "İspanyolca"
            matchesLanguageCode(normalized, "it") -> "İtalyanca"
            matchesLanguageCode(normalized, "pt") -> "Portekizce"
            matchesLanguageCode(normalized, "ru") -> "Rusça"
            matchesLanguageCode(normalized, "ko") -> "Korece"
            matchesLanguageCode(normalized, "zh") -> "Çince"
            else -> lang.uppercase()
        }
    }

    /**
     * Verilen dil kodu veya parça etiketinin (label/title) Türkçe olup olmadığını kontrol eder.
     * Hem ISO dil kodlarını ("tr", "tur") hem de etiket içindeki anahtar kelimeleri
     * ("türkçe", "turkish", "turk", "[TR]", "(TR)" vb.) kapsamlı olarak doğrular.
     */
    fun isTurkish(lang: String?, label: String? = null): Boolean {
        if (!lang.isNullOrBlank() && matchesLanguageCode(lang.trim(), "tr")) {
            return true
        }
        if (!label.isNullOrBlank()) {
            val lower = label.trim().lowercase(java.util.Locale.ROOT)
            if (lower.contains("türkçe") || lower.contains("turkce") || lower.contains("turkish") || lower.contains("turk")) {
                return true
            }
            val trRegex = Regex("""(^|[\s\[\(\-_\./])(tr|tur)([\s\]\)\-_\./]|$)""", RegexOption.IGNORE_CASE)
            if (trRegex.containsMatchIn(lower)) {
                return true
            }
        }
        return false
    }

    /**
     * Verilen parça dil kodu veya etiketinin hedeflenen dil kodu ile eşleşip eşleşmediğini kontrol eder.
     */
    fun matchesTrackLanguage(lang: String?, label: String?, targetLangCode: String): Boolean {
        val target = targetLangCode.trim().lowercase(java.util.Locale.ROOT)
        if (target == "tr" || target == "tur") {
            return isTurkish(lang, label)
        }
        if (!lang.isNullOrBlank() && matchesLanguageCode(lang.trim(), target)) {
            return true
        }
        if (!label.isNullOrBlank()) {
            val lower = label.trim().lowercase(java.util.Locale.ROOT)
            val aliases = LANG_ALIASES[target] ?: setOf(target)
            if (aliases.any { lower.contains(it) }) {
                return true
            }
        }
        return false
    }

    /** Verilen dil string'inin Türkçe olup olmadığını döner. */
    fun isTurkishLang(lang: String?): Boolean {
        return isTurkish(lang, null)
    }

    /**
     * SubtitleInput listesinden en iyi altyazıyı seçer.
     *
     * Kesin Öncelik Hiyerarşisi:
     *  1. Dahili / site kaynağının (isExternal=false) Türkçe altyazısı
     *  2. Harici servislerden (OpenSubtitles vb., isExternal=true) gelen Türkçe altyazı
     *  3. Kullanıcı tercih listesindeki diğer diller (örn: "en") - önce dahili, sonra harici
     *  4. Eşleşme yoksa null (ASLA rastgele İtalyanca veya yabancı dil seçilmez!)
     *
     * @param subtitles      Aranacak SubtitleInput listesi
     * @param preferredLangs Kullanıcı tercih dil listesi (BCP-47, örn: ["tr", "en"])
     */
    fun findBestSubtitleInput(
        subtitles: List<SubtitleInput>,
        preferredLangs: List<String>
    ): SubtitleInput? {
        if (subtitles.isEmpty()) return null

        val effectiveLangs = if (preferredLangs.any { matchesLanguageCode(it, "tr") }) {
            preferredLangs
        } else {
            listOf("tr") + preferredLangs
        }

        // 1. ÖNCELİK: Video kaynağı / site kaynaklı Türkçe altyazı (dahili veya eklenti stream'i ile gelen)
        val sourceTurkish = subtitles.firstOrNull { !it.isExternal && isTurkish(it.lang, it.name) }
        if (sourceTurkish != null) return sourceTurkish

        // 2. ÖNCELİK: OpenSubtitles / harici eklenti kaynaklı Türkçe altyazı
        val addonTurkish = subtitles.firstOrNull { it.isExternal && isTurkish(it.lang, it.name) }
        if (addonTurkish != null) return addonTurkish

        // 3. ÖNCELİK: Kullanıcının tercih listesindeki diğer diller
        for (lang in effectiveLangs) {
            if (matchesLanguageCode(lang, "tr")) continue
            val sourceMatch = subtitles.firstOrNull { !it.isExternal && matchesTrackLanguage(it.lang, it.name, lang) }
            if (sourceMatch != null) return sourceMatch

            val addonMatch = subtitles.firstOrNull { it.isExternal && matchesTrackLanguage(it.lang, it.name, lang) }
            if (addonMatch != null) return addonMatch
        }

        // Eşleşme yoksa null dön (Asla yabancı altyazı fallback yapılmaz!)
        return null
    }

    /**
     * MPV track snapshot altyazılarından en iyi parçayı seçer.
     *
     * Kesin Öncelik Hiyerarşisi:
     *  1. Video/site kaynağının kendi Türkçe altyazısı (dahili akış veya site tarafından sağlanan, isAddonSubtitle=false)
     *  2. Harici servislerden (OpenSubtitles vb., isAddonSubtitle=true) gelen Türkçe altyazı
     *  3. Kullanıcının diğer tercih dilleri (örn: İngilizce) - önce dahili, sonra harici
     *  4. Eşleşme yoksa null (ASLA rastgele İtalyanca veya istenmeyen dilde altyazı seçilmez!)
     *
     * @param subtitleTracks MPV track snapshot altyazı listesi (MpvTrack)
     * @param preferredLangs Kullanıcı tercih dil listesi (örn: ["tr"] veya ["tr", "en"])
     * @param isAddonSubtitle İlgili parçanın OpenSubtitles gibi harici eklentilerden gelip gelmediğini belirten fonksiyon
     */
    fun findBestMpvSubtitleTrack(
        subtitleTracks: List<com.kitsugi.animelist.core.player.engine.MpvTrack>,
        preferredLangs: List<String>,
        isAddonSubtitle: (com.kitsugi.animelist.core.player.engine.MpvTrack) -> Boolean = { it.isExternal }
    ): com.kitsugi.animelist.core.player.engine.MpvTrack? {
        if (subtitleTracks.isEmpty()) return null

        val effectiveLangs = if (preferredLangs.any { matchesLanguageCode(it, "tr") }) {
            preferredLangs
        } else {
            listOf("tr") + preferredLangs
        }

        // 1. ÖNCELİK: Video kaynağı / site kaynaklı Türkçe altyazı
        val sourceTurkish = subtitleTracks.firstOrNull { track ->
            !isAddonSubtitle(track) && isTurkish(track.language, track.name)
        }
        if (sourceTurkish != null) return sourceTurkish

        // 2. ÖNCELİK: OpenSubtitles / harici eklenti kaynaklı Türkçe altyazı
        val addonTurkish = subtitleTracks.firstOrNull { track ->
            isAddonSubtitle(track) && isTurkish(track.language, track.name)
        }
        if (addonTurkish != null) return addonTurkish

        // 3. ÖNCELİK: Kullanıcının tercih listesindeki diğer diller
        for (lang in effectiveLangs) {
            if (matchesLanguageCode(lang, "tr")) continue
            val sourceMatch = subtitleTracks.firstOrNull { track ->
                !isAddonSubtitle(track) && matchesTrackLanguage(track.language, track.name, lang)
            }
            if (sourceMatch != null) return sourceMatch

            val addonMatch = subtitleTracks.firstOrNull { track ->
                isAddonSubtitle(track) && matchesTrackLanguage(track.language, track.name, lang)
            }
            if (addonMatch != null) return addonMatch
        }

        // Hiçbir tercih edilen dil bulunamadıysa null dön
        return null
    }

    /**
     * MPV track snapshot ses parçalarından en iyi parçanın ID'sini seçer.
     *
     * Öncelik sırası:
     *  1. Türkçe ses parçası
     *  2. Kullanıcı tercih listesi
     *  3. İlk mevcut ses parçası (fallback)
     */
    fun findBestMpvAudioTrack(
        audioTracks: List<com.kitsugi.animelist.core.player.engine.MpvTrack>,
        preferredLangs: List<String>
    ): com.kitsugi.animelist.core.player.engine.MpvTrack? {
        if (audioTracks.isEmpty()) return null

        // 1. Türkçe ses
        audioTracks.firstOrNull { isTurkish(it.language, it.name) }?.let { return it }

        // 2. Kullanıcı tercih listesi
        for (lang in preferredLangs) {
            audioTracks.firstOrNull { matchesTrackLanguage(it.language, it.name, lang) }?.let { return it }
        }

        // 3. Fallback
        return audioTracks.firstOrNull()
    }
}
