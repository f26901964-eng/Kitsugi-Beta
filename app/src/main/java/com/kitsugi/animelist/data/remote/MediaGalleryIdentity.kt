package com.kitsugi.animelist.data.remote

/**
 * Cross-provider identifiers that are safe to use when assembling a media image gallery.
 *
 * `JikanSearchResult.malId` is intentionally source-dependent. In particular, Bangumi uses a
 * 500M offset, Simkl uses a Simkl ID, AniList and Kitsu use their own offsets, and TMDB stores
 * its ID in the same field. A gallery must never send those IDs to MAL/ARM as if they were
 * interchangeable.
 */
internal data class MediaGalleryIdentity(
    val tmdbId: Int? = null,
    val malId: Int? = null,
    val aniListId: Int? = null,
    val kitsuId: Int? = null
)

/** Resolve only identities explicitly guaranteed by the item source or a cross-reference. */
internal fun resolveMediaGalleryIdentity(
    result: JikanSearchResult,
    detail: KitsugiMediaDetail?,
    bangumiCross: KitsugiBangumiDetailClient.CrossIds? = null
): MediaGalleryIdentity {
    val source = MalJikanMediaSupport.canonicalSource(result.source).let { canonical ->
        if (canonical == "bgm") "bangumi" else canonical
    }
    fun positive(value: Int?): Int? = value?.takeIf { it > 0 }
    fun realMal(value: Int?): Int? = value?.takeIf { it in 1..99_999_999 }

    val malId = when (source) {
        "jikan" -> realMal(result.malId) ?: realMal(result.realMalId)
        "bangumi" -> realMal(result.realMalId)
            ?: realMal(detail?.realMalId)
            ?: realMal(bangumiCross?.malId)
        "anilist", "kitsu", "simkl", "shikimori" -> realMal(result.realMalId)
            ?: realMal(detail?.realMalId)
        else -> realMal(result.realMalId) ?: realMal(detail?.realMalId)
    }

    val aniListId = when (source) {
        "anilist" -> result.malId
            .takeIf { it in 100_000_001..199_999_999 }
            ?.minus(100_000_000)
        "bangumi" -> positive(bangumiCross?.aniListId)
        else -> null
    }

    val kitsuId = when (source) {
        "kitsu" -> when (result.malId) {
            in 300_000_001..399_999_999 -> result.malId - 300_000_000
            else -> positive(result.malId)
        }
        "bangumi" -> positive(bangumiCross?.kitsuId)
        else -> null
    }

    val tmdbId = positive(result.tmdbId)
        ?: positive(detail?.tmdbId)
        ?: when (source) {
            // TMDB discovery items store the TMDB ID in `malId` by design.
            "tmdb" -> positive(result.malId)
            "bangumi" -> positive(bangumiCross?.tmdbId)
            else -> null
        }

    return MediaGalleryIdentity(
        tmdbId = tmdbId,
        malId = malId,
        aniListId = aniListId,
        kitsuId = kitsuId
    )
}

/**
 * Çapraz sağlayıcıdan (ARM/MAL/AniList/Kitsu/Shikimori) çözülen TMDB kimliğinin gerçekten
 * aynı yapıma ait olup olmadığını başlıktan doğrular.
 *
 * NEDEN GEREKLİ? TMDB'de `movie` ve `tv` kimlik alanları AYRIDIR: 8390 bir film iken aynı
 * sayı bir diziye ait olabilir. Eşleme ya da tür bilgisi yanlışsa galeri, hiç ilgisi olmayan
 * bir yapımın afiş/logolarıyla dolar (ör. Komşum Totoro sayfasında Popeye görselleri).
 * Bu yüzden görseller listeye eklenmeden önce TMDB kaydının başlığıyla arama başlığı
 * karşılaştırılır; eşleşme yoksa görseller hiç kullanılmaz.
 */
internal object TmdbArtworkIdentity {

    fun normalize(value: String?): String = value.orEmpty()
        .lowercase()
        .replace(Regex("[^\\p{L}\\p{N}]+"), "")
        .removePrefix("the")
        .trim()

    /** Eşleşme yoksa galeriye hiçbir şey karışmaz; şüphede kalınırsa (veri eksik) izin verilir. */
    fun matches(expectedTitles: List<String>, tmdbTitle: String?, tmdbOriginalTitle: String?): Boolean {
        val candidates = listOfNotNull(tmdbTitle?.takeIf { it.isNotBlank() }, tmdbOriginalTitle?.takeIf { it.isNotBlank() })
            .map { normalize(it) }
            .filter { it.isNotEmpty() }
        // TMDB başlık alanı boşsa (nadir) doğrulama yapılamaz → fail-open.
        if (candidates.isEmpty()) return true
        val expected = expectedTitles.map { normalize(it) }.filter { it.length >= 3 }
        if (expected.isEmpty()) return true
        return expected.any { exp ->
            candidates.any { tmdb ->
                tmdb == exp ||
                    (tmdb.length >= 5 && (tmdb.contains(exp) || exp.contains(tmdb)))
            }
        }
    }
}
