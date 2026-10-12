package com.kitsugi.animelist.data.fandom

import com.kitsugi.animelist.data.remote.KitsugiCharacterDetail

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
}
