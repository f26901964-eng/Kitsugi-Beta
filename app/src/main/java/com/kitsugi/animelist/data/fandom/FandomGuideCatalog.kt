package com.kitsugi.animelist.data.fandom

/**
 * Evren rehberi kategori adayları (saf veri; ağ isteği yok).
 *
 * Fandom wikilerinde kategoriler adlandırma birliği taşımaz: bir wikide `Organizations`,
 * diğerinde `Factions` veya Türkçe wikide `Organizasyonlar` olabilir. Her grup için
 * öncelik sırasıyla adaylar denenir; ilk dolu kategori grubu temsil eder.
 *
 * Wiki diline göre (`siteinfo.lang`) yerel dil adayları öne alınır, İngilizce adaylar
 * her zaman son çare olarak eklenir — böylece tek `categoryinfo` isteğiyle var/yok
 * kararı verilir, grup başına ayrıca istek atılmaz.
 */
object FandomGuideCatalog {

    private val EN = mapOf(
        FandomGuideGroup.ORGANIZATIONS to listOf(
            "Organizations", "Organisations", "Factions", "Groups", "Clans", "Guilds", "Teams"
        ),
        FandomGuideGroup.ABILITIES to listOf(
            "Abilities", "Powers", "Techniques", "Skills", "Magic", "Spells", "Curses", "Quirks"
        ),
        FandomGuideGroup.PLACES to listOf(
            "Locations", "Places", "Cities", "Countries", "Regions"
        ),
        FandomGuideGroup.TERMS to listOf(
            "Terms", "Concepts", "Glossary", "Items", "Objects", "Weapons", "Species", "Races", "Events"
        )
    )

    private val TR = mapOf(
        FandomGuideGroup.ORGANIZATIONS to listOf(
            "Organizasyonlar", "Kuruluşlar", "Gruplar", "Klanlar", "Fraksiyonlar", "Takımlar"
        ),
        FandomGuideGroup.ABILITIES to listOf(
            "Yetenekler", "Güçler", "Teknikler", "Beceriler", "Büyüler", "Lanetler"
        ),
        FandomGuideGroup.PLACES to listOf(
            "Mekanlar", "Konumlar", "Yerler", "Şehirler", "Ülkeler", "Bölgeler"
        ),
        FandomGuideGroup.TERMS to listOf(
            "Terimler", "Kavramlar", "Sözlük", "Eşyalar", "Nesneler", "Silahlar", "Türler", "Olaylar"
        )
    )

    /** Grup için aday kategori adları (önce wiki'nin dili, sonra İngilizce). */
    fun candidates(lang: String?, group: FandomGuideGroup): List<String> {
        val english = EN[group].orEmpty()
        return if (lang?.lowercase() == "tr") (TR[group].orEmpty() + english).distinct() else english
    }

    /** Tüm grupların adayları (grup sırası sabittir: organizasyon → yetenek → mekân → terim). */
    fun allCandidates(lang: String?): Map<FandomGuideGroup, List<String>> =
        FandomGuideGroup.values().associateWith { candidates(lang, it) }
}
