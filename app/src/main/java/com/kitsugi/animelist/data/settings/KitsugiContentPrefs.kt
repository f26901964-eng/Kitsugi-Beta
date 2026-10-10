package com.kitsugi.animelist.data.settings

import org.json.JSONObject

/**
 * V2-F04 – KitsugiContentPrefs
 *
 * AniHyou ayar ekranından uyarlanan içerik/liste tercihlerinin, Compose dışında
 * çalışan istemciler (arama, manga skorlayıcı, senkron yöneticileri) tarafından
 * senkron okunabilmesi için [SettingsDataStore.settingsFlow] içinde güncellenen
 * uçucu önbellek. [com.kitsugi.animelist.utils.KitsugiTranslatePrefs] ile aynı desen.
 *
 * Kapsam: staffNameLanguage, scoreStep, separatedListStyle, showLowPriority,
 * priorityColorsJson, fuzzySearchEnabled, separateNovelsManga.
 */
object KitsugiContentPrefs {
    @Volatile
    var staffNameLanguage: String = "ROMAJI"
        private set

    @Volatile
    var scoreStep: Float = 1f
        private set

    @Volatile
    var separatedListStyle: Boolean = true
        private set

    @Volatile
    var showLowPriority: Boolean = false
        private set

    @Volatile
    var priorityColorsJson: String = ""
        private set

    @Volatile
    var fuzzySearchEnabled: Boolean = true
        private set

    @Volatile
    var separateNovelsManga: Boolean = false
        private set

    fun update(settings: AppSettings) {
        staffNameLanguage = settings.staffNameLanguage
        scoreStep = settings.scoreStep.toFloatOrNull()?.takeIf { it > 0f } ?: 1f
        separatedListStyle = settings.separatedListStyle
        showLowPriority = settings.showLowPriority
        priorityColorsJson = settings.priorityColorsJson
        fuzzySearchEnabled = settings.fuzzySearchEnabled
        separateNovelsManga = settings.separateNovelsManga
    }

    /**
     * Öncelik seviyesi (0 = düşük, 1 = orta, 2 = yüksek) için kullanıcı renk tohumu.
     * JSON bozuksa veya seviye tanımlı değilse null döner → UI varsayılan paleti kullanır.
     */
    fun priorityColorSeed(level: Int): Int? {
        if (priorityColorsJson.isBlank()) return null
        return runCatching {
            val obj = JSONObject(priorityColorsJson)
            if (obj.has(level.toString())) obj.optInt(level.toString()) else null
        }.getOrNull()
    }

    /**
     * Fuzzy eşleşme: önce normalleştirilmiş "içerir" kontrolü; tutmazsa ve fuzzy
     * açıksa kısaltma ("aot" → "attack on titan" harf başları), harf alt dizisi ve
     * Levenshtein toleransı dener. Fuzzy kapalıysa sadece "içerir" sonucu geçerlidir.
     */
    fun fuzzyMatches(haystack: String, query: String): Boolean {
        val hay = normalize(haystack)
        val needle = normalize(query)
        if (needle.isEmpty()) return true
        if (hay.contains(needle)) return true
        if (!fuzzySearchEnabled) return false
        // Kısaltma eşleşmesi: sorgu harfleri, kelimelerin baş harfleriyle örtüşsün
        val initials = hay.split(' ').filter { it.isNotEmpty() }.joinToString("") { it.first().toString() }
        if (initials.contains(needle)) return true
        // Sıralama bozukluğu: sorgu harfleri haystack içinde sırayla görünsün
        if (isSubsequence(hay, needle)) return true
        // Yazım hatası toleransı: uzunluğa göre 1-2 karakterlik Levenshtein mesafesi
        val tolerance = when {
            needle.length <= 4 -> 1
            needle.length <= 8 -> 2
            else -> 3
        }
        return hay.split(' ').any { word ->
            word.length >= needle.length - tolerance && levenshtein(word, needle) <= tolerance
        }
    }

    private fun normalize(text: String): String =
        text.lowercase().replace(Regex("[^a-z0-9ğüşıöçâîûêáéíóúàèìòùäëïöü]+"), " ").trim()

    private fun isSubsequence(hay: String, needle: String): Boolean {
        var i = 0
        for (ch in hay) {
            if (i < needle.length && ch == needle[i]) i++
        }
        return i == needle.length
    }

    private fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var prev = IntArray(b.length + 1) { it }
        var curr = IntArray(b.length + 1)
        for (i in 1..a.length) {
            curr[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                curr[j] = minOf(curr[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
            }
            val tmp = prev; prev = curr; curr = tmp
        }
        return prev[b.length]
    }
}
