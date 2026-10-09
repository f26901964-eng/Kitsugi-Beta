package com.kitsugi.animelist.data.remote

import com.kitsugi.animelist.utils.PreferenceHelpers

/**
 * Bangumi yanıtlarındaki çok dilli ad alanlarının kaynaktan bağımsız temsilidir.
 *
 * Bangumi'nin v0 ve p1 uçları tek bir dil alanı döndürmez: `name` çoğunlukla özgün
 * (Japonca), `name_cn` / `nameCN` Çince, İngilizce ve romaji ise sıklıkla infobox
 * içindeki `英文名`, `别名` veya `罗马字` alanlarında bulunur. Bu sınıf bu değerleri
 * ekrana yazılacak tek metne erken indirgemek yerine ayrı tutar. Böylece uygulamanın
 * başlık dili değiştiğinde aynı önbellekli veri doğru biçimde yeniden çizilebilir.
 */
internal data class BangumiLocalizedName(
    /** Modelin dil-nötr, güvenli varsayılan adı. Romaji → İngilizce → özgün → Çince. */
    val display: String,
    val english: String?,
    val romaji: String?,
    val native: String?,
    val chinese: String?,
    val alternatives: List<String>
) {
    fun displayFor(titleLanguage: String): String = when (titleLanguage) {
        "ENGLISH" -> english ?: romaji ?: native ?: chinese ?: display
        "NATIVE", "JAPANESE_STAFF" -> native ?: romaji ?: english ?: chinese ?: display
        else -> romaji ?: english ?: native ?: chinese ?: display
    }
}

/**
 * Bangumi'nin dağınık infobox adlarını Kitsugi'nin English / Romaji / Native sözleşmesine
 * dönüştürür. İstemci tarafında Japonca kanji için güvenilir bir okuma üretmek mümkün değildir;
 * kaynakta romaji yoksa İngilizce Latin ad, o da yoksa özgün ad yalnızca güvenli geri dönüş
 * olarak kullanılır. Böylece uydurma/transliterasyonu yanlış bir ad hiç gösterilmez.
 */
internal object BangumiNameLocalizer {
    private val englishKeys = setOf(
        "英文名", "英語名", "英语名", "英文名称", "英文名稱", "英文标题", "英文標題",
        "英文译名", "英文譯名", "外文名", "英语", "英語", "英語タイトル",
        "English", "English Name", "English Title", "English name", "English title", "English names"
    )
    private val romajiKeys = setOf(
        "罗马字", "羅馬字", "罗马音", "羅馬音", "罗马拼写", "羅馬拼寫",
        "罗马化", "羅馬化", "日文罗马字", "ローマ字",
        "Romaji", "Romaji Name", "Romaji Title", "Romanized", "Romanized Name",
        "Romanized Title", "Romanisation", "Romanization"
    )
    private val aliasKeys = setOf(
        "别名", "別名", "又名", "原名", "别称", "別稱", "别名列表", "其他名称", "其它名称",
        "Alias", "Aliases", "Alternative title", "Alternative titles", "Alternative name", "Alternative names"
    )

    /** Tam bir v0 subject ya da p1 entity için infobox'taki tüm bilinen ad alanlarını kullanır. */
    fun subject(
        name: String?,
        nameCn: String?,
        infobox: Map<String, List<String>> = emptyMap()
    ): BangumiLocalizedName = entityFromInfobox(name, nameCn, infobox)

    /** Karakter ve kişi ayrıntı uçları aynı infobox biçimini kullandığından ortak çözücü. */
    fun entityFromInfobox(
        name: String?,
        nameCn: String?,
        infobox: Map<String, List<String>>
    ): BangumiLocalizedName {
        val english = valuesFor(infobox, englishKeys)
        val romaji = valuesFor(infobox, romajiKeys)
        val aliases = valuesFor(infobox, aliasKeys)
        return resolve(name, nameCn, english, romaji, aliases)
    }

    /** p1 kısa subject / character / person nesnesi için. */
    fun entity(
        name: String?,
        nameCn: String? = null,
        aliases: List<String> = emptyList(),
        englishAliases: List<String> = emptyList(),
        romajiAliases: List<String> = emptyList()
    ): BangumiLocalizedName = resolve(name, nameCn, englishAliases, romajiAliases, aliases)

    private fun valuesFor(infobox: Map<String, List<String>>, keys: Set<String>): List<String> =
        infobox.entries
            .filter { (key, _) -> keys.any { it.equals(key.trim(), ignoreCase = true) } }
            .flatMap { it.value }

    private fun resolve(
        nativeRaw: String?,
        chineseRaw: String?,
        englishAliases: List<String>,
        romajiAliases: List<String>,
        aliases: List<String>
    ): BangumiLocalizedName {
        fun clean(value: String?): String? = value?.trim()?.takeIf { it.isNotEmpty() && !it.equals("null", true) }
        val native = clean(nativeRaw)
        val chinese = clean(chineseRaw)
        val allAliases = (englishAliases + romajiAliases + aliases)
            .mapNotNull(::clean)
            .distinctBy { it.lowercase() }

        fun latin(values: Iterable<String?>): String? = values
            .mapNotNull(::clean)
            .firstOrNull { isLatinDisplayName(it) }

        val explicitEnglish = latin(englishAliases)
        val explicitRomaji = latin(romajiAliases)
        val nativeLatin = native?.takeIf(::isLatinDisplayName)
        val aliasLatin = latin(aliases)

        // Bangumi çoğu kayıtta romaji için ayrı bir alan sunmaz. Latin biçimli özgün ad veya
        // alias, romaji için en iyi kaynaktır; İngilizce alan ise yalnızca English tercihini
        // öncelemek için ayrı korunur.
        val romaji = explicitRomaji ?: nativeLatin ?: aliasLatin ?: explicitEnglish
        val english = explicitEnglish ?: nativeLatin ?: romaji
        val display = romaji ?: english ?: native ?: chinese ?: "?"
        // Diğer adlar yalnızca Latin harfli olmalı: Japonca/Çince/Korece varyantlar kullanıcıya
        // gösterilmez (özgün ad yalnızca başlığın kendisi için son çare olarak kalır).
        val alternatives = (listOfNotNull(native, chinese, english, romaji) + allAliases)
            .filter { it.isNotBlank() && !it.equals(display, ignoreCase = true) && isLatinDisplayName(it) }
            .distinctBy { it.lowercase() }

        return BangumiLocalizedName(
            display = display,
            english = english,
            romaji = romaji,
            native = native,
            chinese = chinese,
            alternatives = alternatives
        )
    }

    private fun isLatinDisplayName(value: String): Boolean =
        value.any { it.isLetter() } && !PreferenceHelpers.hasCjkCharacters(value)
}
