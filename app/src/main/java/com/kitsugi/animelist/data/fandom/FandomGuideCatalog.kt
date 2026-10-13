package com.kitsugi.animelist.data.fandom

/**
 * Evren rehberi kategori adayları (saf veri; ağ isteği yok).
 *
 * Fandom wikilerinde kategori adlandırma birliği taşımaz: organizasyonlar için
 * `Organizations` / `Factions`, olay örgüsü için `Story Arcs` / `Sagas`, içerik dizinleri
 * için `Episodes` / `Chapters` gibi alternatifler kullanılır. Her grup için öncelik
 * sırasındaki ilk dolu kategori seçilir.
 *
 * Wiki diline göre (`siteinfo.lang`) yerel dil adayları öne alınır, İngilizce adaylar
 * her zaman son çare olarak eklenir — böylece kategori var/yok kararı `categoryinfo`
 * ile 50 başlıklı gruplar halinde sorgulanabilir.
 */
object FandomGuideCatalog {

    private val EN = mapOf(
        FandomGuideGroup.CHARACTERS to listOf(
            "Characters", "Main Characters", "Character Index", "Character List"
        ),
        FandomGuideGroup.ORGANIZATIONS to listOf(
            "Organizations", "Organisations", "Factions", "Groups", "Clans", "Guilds", "Teams"
        ),
        FandomGuideGroup.STORY_ARCS to listOf(
            "Story Arcs", "Arcs", "Sagas", "Story", "Plot"
        ),
        FandomGuideGroup.EPISODES to listOf(
            "Episodes", "Anime Episodes", "TV Episodes", "Episode List"
        ),
        FandomGuideGroup.CHAPTERS to listOf(
            "Chapters", "Manga Chapters", "Chapter List", "Volumes"
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
        FandomGuideGroup.CHARACTERS to listOf(
            "Karakterler", "Ana Karakterler", "Karakter Dizini"
        ),
        FandomGuideGroup.ORGANIZATIONS to listOf(
            "Organizasyonlar", "Kuruluşlar", "Gruplar", "Klanlar", "Fraksiyonlar", "Takımlar"
        ),
        FandomGuideGroup.STORY_ARCS to listOf(
            "Hikâye Yayları", "Hikaye Yayları", "Hikâye", "Hikaye", "Olay Örgüsü", "Destanlar"
        ),
        FandomGuideGroup.EPISODES to listOf(
            "Bölümler", "Anime Bölümleri", "Dizi Bölümleri"
        ),
        FandomGuideGroup.CHAPTERS to listOf(
            "Manga Bölümleri", "Bölümler (Manga)", "Fasıllar", "Ciltler"
        ),
        FandomGuideGroup.ABILITIES to listOf(
            "Yetenekler", "Güçler", "Teknikler", "Beceriler", "Büyüler", "Lanetler"
        ),
        FandomGuideGroup.PLACES to listOf(
            "Mekanlar", "Mekânlar", "Konumlar", "Yerler", "Şehirler", "Ülkeler", "Bölgeler"
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

    /** Tüm grupların adayları (karakter → organizasyon → hikâye → bölüm → bilgi sırası). */
    fun allCandidates(lang: String?): Map<FandomGuideGroup, List<String>> =
        FandomGuideGroup.values().associateWith { candidates(lang, it) }
}
