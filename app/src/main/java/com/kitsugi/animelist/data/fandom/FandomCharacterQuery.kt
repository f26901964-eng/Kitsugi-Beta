package com.kitsugi.animelist.data.fandom

import com.kitsugi.animelist.data.remote.KitsugiCharacterDetail
import com.kitsugi.animelist.data.remote.KitsugiStaffDetail

/** Karakterin geçtiği bir yapım (wiki'yi bulmak için). */
data class FandomAppearanceRef(
    val ids: FandomSourceIds,
    val isManga: Boolean,
    val isMovie: Boolean,
    val isMainRole: Boolean,
    val titles: List<String>
)

/** Karakter → Fandom sayfası çözümlemesi için girdi. */
data class FandomCharacterQuery(
    val source: String,
    val characterId: Int,
    /** AniList karakter kimliği (Wikidata P11736). */
    val aniListCharacterId: Int?,
    /** MAL karakter kimliği (Wikidata P4085). */
    val malCharacterId: Int?,
    /** Latin ad yazımları: İngilizce, Romaji, görünen ad (sıra = öncelik). */
    val names: List<String>,
    /** Kaynaktaki yerel ad (Kanji/Hangul) — en güçlü doğrulama kanıtı. */
    val nativeName: String?,
    val alternatives: List<String>,
    val appearances: List<FandomAppearanceRef>
) {
    fun cacheKey(): String = when {
        aniListCharacterId != null -> "al:$aniListCharacterId"
        malCharacterId != null -> "mal:$malCharacterId"
        else -> "${source.lowercase()}:$characterId"
    }
}

/** [KitsugiCharacterDetail] → [FandomCharacterQuery] (saf fonksiyon). */
object FandomCharacterQueries {

    private const val MAX_APPEARANCES = 6

    /**
     * @return TMDB (gerçek kişi rolleri) için ve doğrulanabilir hiçbir kimlik/ad yoksa null.
     */
    fun from(detail: KitsugiCharacterDetail, source: String, characterId: Int): FandomCharacterQuery? {
        val canonical = source.trim().lowercase()
        if (canonical == "tmdb") return null
        val isMal = FandomMediaIds.isMalSource(canonical)

        // CharacterDetailViewModel ile aynı kurallar: Shikimori karakter kimliği MAL'i aynalar;
        // Kitsu dalı Jikan detayına düştüyse detayın kimliği gerçek MAL kimliğidir.
        val malCharacterId = when {
            isMal -> characterId.takeIf { it > 0 }
            canonical == "shikimori" -> detail.id.takeIf { it > 0 }
            canonical == "kitsu" -> detail
                .takeIf { FandomMediaIds.isMalSource(it.source) && it.id != characterId }
                ?.id?.takeIf { it > 0 }
            else -> null
        }
        val aniListCharacterId = detail.aniListId?.takeIf { it > 0 }
            ?: if (canonical == "anilist") characterId.takeIf { it > 0 } else null

        val names = listOfNotNull(detail.englishName, detail.romanizedName, detail.name)
            .map { it.replace(Regex("\\s+"), " ").trim() }
            .filter { it.isNotEmpty() && FandomNameVariants.isMostlyLatin(it) }
            .distinct()

        val appearances = detail.mediaAppearances.mapNotNull { appearance ->
            val ids = FandomMediaIds.fromStableId(appearance.source, appearance.mediaId)
            if (ids.isEmpty) return@mapNotNull null
            FandomAppearanceRef(
                ids = ids,
                isManga = FandomMediaIds.isMangaType(appearance.mediaType),
                isMovie = FandomMediaIds.isMovieType(appearance.mediaType),
                isMainRole = FandomMediaIds.isMainRole(appearance.characterRole),
                titles = listOfNotNull(appearance.titleEnglish, appearance.titleRomaji, appearance.title)
                    .map { it.trim() }
                    .filter {
                        it.isNotEmpty() && FandomNameVariants.isMostlyLatin(it) &&
                            !it.equals("Bilinmeyen", ignoreCase = true) && !it.equals("Unknown", ignoreCase = true)
                    }
                    .distinct()
            )
        }.take(MAX_APPEARANCES)

        val hasCharacterId = aniListCharacterId != null || malCharacterId != null
        if (!hasCharacterId && (names.isEmpty() || appearances.isEmpty())) return null

        return FandomCharacterQuery(
            source = canonical,
            characterId = characterId,
            aniListCharacterId = aniListCharacterId,
            malCharacterId = malCharacterId,
            names = names,
            nativeName = detail.nativeName?.trim()?.takeIf { it.isNotEmpty() },
            alternatives = detail.alternativeNames
                .map { it.trim() }
                .filter { it.isNotEmpty() && FandomNameVariants.isMostlyLatin(it) }
                .distinct()
                .take(3),
            appearances = appearances
        )
    }

    /**
     * Ekip / seslendirme sanatçısı → sorgu. Kişi sayfaları Wikidata üzerinden Fandom'a bağlı
     * olmadığı için kimlik yolu yoktur; kişi, çalıştığı yapımların wiki'sinde adıyla aranır ve
     * bilgi kutusundaki Japonca/Korece adla doğrulanır (karakterlerle aynı kural).
     *
     * @return TMDB (gerçek kişi, Fandom'da nadiren sayfa) için ve ad/yapım yoksa null.
     */
    fun fromStaff(detail: KitsugiStaffDetail, source: String, staffId: Int): FandomCharacterQuery? {
        val canonical = source.trim().lowercase()
        if (canonical == "tmdb") return null

        val names = listOfNotNull(detail.englishName, detail.romanizedName, detail.name)
            .map { it.replace(Regex("\\s+"), " ").trim() }
            .filter { it.isNotEmpty() && FandomNameVariants.isMostlyLatin(it) }
            .distinct()
        if (names.isEmpty()) return null

        // Seslendirdiği ana karakterlerin yapımları önce (wiki'de kişi sayfası olma olasılığı en yüksek),
        // sonra diğer roller ve ekip çalışmaları. Aynı yapım bir kez alınır.
        val seen = HashSet<String>()
        val appearances = buildList {
            val roleMedia = detail.characterRoles
                .sortedByDescending { FandomMediaIds.isMainRole(it.characterRole) }
            for (role in roleMedia) {
                val ids = FandomMediaIds.fromStableId(role.mediaSource, role.mediaId)
                if (ids.isEmpty) continue
                val key = ids.stableKey(FandomMediaIds.isMangaType(role.mediaType), FandomMediaIds.isMovieType(role.mediaType)) ?: continue
                if (!seen.add(key)) continue
                add(
                    FandomAppearanceRef(
                        ids = ids,
                        isManga = FandomMediaIds.isMangaType(role.mediaType),
                        isMovie = FandomMediaIds.isMovieType(role.mediaType),
                        isMainRole = FandomMediaIds.isMainRole(role.characterRole),
                        titles = staffTitles(role.mediaTitleEnglish, role.mediaTitleRomaji, role.mediaTitle)
                    )
                )
            }
            for (work in detail.mediaWorks) {
                val ids = FandomMediaIds.fromStableId(work.source, work.mediaId)
                if (ids.isEmpty) continue
                val key = ids.stableKey(FandomMediaIds.isMangaType(work.mediaType), FandomMediaIds.isMovieType(work.mediaType)) ?: continue
                if (!seen.add(key)) continue
                add(
                    FandomAppearanceRef(
                        ids = ids,
                        isManga = FandomMediaIds.isMangaType(work.mediaType),
                        isMovie = FandomMediaIds.isMovieType(work.mediaType),
                        isMainRole = false,
                        titles = staffTitles(work.titleEnglish, work.titleRomaji, work.mediaTitle)
                    )
                )
            }
        }.take(MAX_APPEARANCES)
        if (appearances.isEmpty()) return null

        return FandomCharacterQuery(
            source = "staff-$canonical",
            characterId = staffId,
            aniListCharacterId = null,
            malCharacterId = null,
            names = names,
            nativeName = detail.nativeName?.trim()?.takeIf { it.isNotEmpty() },
            alternatives = detail.alternativeNames
                .map { it.trim() }
                .filter { it.isNotEmpty() && FandomNameVariants.isMostlyLatin(it) }
                .distinct()
                .take(3),
            appearances = appearances
        )
    }

    private fun staffTitles(vararg titles: String?): List<String> =
        titles.filterNotNull()
            .map { it.trim() }
            .filter {
                it.isNotEmpty() && FandomNameVariants.isMostlyLatin(it) &&
                    !it.equals("Bilinmeyen", ignoreCase = true) && !it.equals("Unknown", ignoreCase = true)
            }
            .distinct()
}
