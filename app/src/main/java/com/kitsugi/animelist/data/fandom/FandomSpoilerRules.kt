package com.kitsugi.animelist.data.fandom

/**
 * Spoiler ve gizli bölüm kuralları (saf fonksiyonlar).
 *
 * Spoiler koruması açıkken bu alanlar/bölümler kullanıcı dokunana kadar gösterilmez.
 * Kurallar bilinçli olarak temkinlidir: şüpheli bir alanı gizlemek, yanlışlıkla
 * hikâyenin sonunu göstermekten iyidir.
 */
object FandomSpoilerRules {

    private val SPOILER_FIELD = Regex(
        "(?i)\\b(status|state|fate|death|died|deceased|killed|cause of death|date of death|" +
            "durum|ölüm|estado|statut|stato|zustand)\\b"
    )

    private val SPOILER_SECTION = Regex(
        "(?i)(history|background|backstory|synopsis|plot|story|battles?|events|fate|death|" +
            "relationships?|geçmiş|hikaye|hikâye|olaylar|ilişkiler|historia|trama|histoire|biograf)"
    )

    private val HIDDEN_SECTION = Regex(
        "(?i)^\\s*(references?|notes? (and|&) references|notes|navigation|site navigation|nav|" +
            "gallery|image gallery|images|videos?|external links?|links|see also|sources?|" +
            "kaynaklar|kaynakça|notlar|galeri|resimler|dış bağlantılar|ayrıca bakınız|" +
            "referencias|galería|navegación|enlaces externos|véase también|" +
            "références|galerie|liens externes|voir aussi)\\s*$"
    )

    fun isSpoilerField(label: String, source: String?): Boolean {
        if (SPOILER_FIELD.containsMatchIn(label)) return true
        val param = source?.replace('_', ' ')?.trim().orEmpty()
        return param.isNotEmpty() && SPOILER_FIELD.containsMatchIn(param)
    }

    fun isSpoilerSection(title: String): Boolean = SPOILER_SECTION.containsMatchIn(title)

    fun isHiddenSection(title: String): Boolean = HIDDEN_SECTION.matches(title)
}
