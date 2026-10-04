package com.kitsugi.animelist.ui.screens.search

import com.kitsugi.animelist.model.MediaType

/**
 * Ayrıntı Sayfaları (Detail Pages) <-> Arama Sayfası (Search Page) Çift Yönlü Birleşik Mimari
 * Detay sayfasında tıklanan her metadata öğesi (Tür, Tema, Demografi, Etiket/Keyword,
 * Sezon+Yıl, Format, Durum, Kaynak, Ülke, Stüdyo, Dergi, Kanal, Sıralama) bu model üzerinden
 * SearchScreen / SearchViewModel'a aktarılır.
 */
data class DetailSearchFilterRequest(
    val source: String,                      // "anilist", "mal"/"jikan", "tmdb", "shikimori", "kitsu", "simkl"
    val mediaType: MediaType,                // Anime, Manga, Movie, TvShow
    val format: String? = null,              // TV, MOVIE, OVA, ONA, SPECIAL, MANGA, MANHWA, MANHUA, NOVEL, ONE_SHOT
    val status: String? = null,              // RELEASING, FINISHED, NOT_YET_RELEASED, CANCELLED, HIATUS
    val genre: String? = null,               // Tür (Action, Romance, Sci-Fi vb.)
    val theme: String? = null,               // Tema (Isekai, Mecha, Psychological, Time Travel, School vb.)
    val demographic: String? = null,         // Demografi (Shounen, Seinen, Shoujo, Josei, Kids)
    val tag: String? = null,                 // AniList Tag / Kitsu Category / TMDB Keyword adı
    val keywordId: Int? = null,              // TMDB Keyword ID (varsa doğrudan with_keywords={id})
    val season: String? = null,              // WINTER, SPRING, SUMMER, FALL
    val year: Int? = null,                  // Sezon yılı veya başlangıç yılı (örn. 2024)
    val sourceMaterial: String? = null,      // ORIGINAL, MANGA, LIGHT_NOVEL, WEB_NOVEL, VISUAL_NOVEL, VIDEO_GAME
    val countryCode: String? = null,         // JP, KR, CN, TW, US, TR, GB vb.
    val languageCode: String? = null,        // ja, ko, zh, en, tr vb.
    val studioId: Int? = null,               // Stüdyo / Yapımcı / Şirket ID
    val studioName: String? = null,          // Stüdyo / Yapımcı adı
    val producerId: Int? = null,             // MAL/Jikan Producer veya Shikimori Studio/Publisher ID
    val magazineId: Int? = null,             // MAL/Jikan Manga Dergisi (Serialization) veya Shikimori Publisher ID
    val magazineName: String? = null,        // Dergi adı (örn. Weekly Shounen Jump)
    val networkId: Int? = null,              // TMDB TV Network ID (örn. Netflix, HBO, tvN, Tokyo MX)
    val networkName: String? = null,         // Kanal/Platform adı
    val streamerName: String? = null,        // Yayıncı platform (Crunchyroll, Netflix, Disney+, Bilibili vb.)
    val ageRating: String? = null,           // G, PG, PG-13, R, R+, Rx, TV-MA vb.
    val sortBy: String? = null               // Puan Sırası (#Rank) veya Popülerlik Sırası (#Popularity) tıklandığında
)

/**
 * 6 Kaynak ve Tümü için Kanonik Filtre Köprüsü (CanonicalFilterBridge)
 * Kaynaklar arası filtre çevirisi ve Akıllı Kapsam Koruması (Auto-Scope Guard)
 */
object CanonicalFilterBridge {

    fun sourceToEngine(source: String): SearchSourceEngine = when (source.lowercase().trim()) {
        "anilist" -> SearchSourceEngine.ANILIST
        "mal", "jikan" -> SearchSourceEngine.MAL
        "tmdb" -> SearchSourceEngine.TMDB
        "shikimori" -> SearchSourceEngine.SHIKIMORI
        "kitsu" -> SearchSourceEngine.KITSU
        "simkl" -> SearchSourceEngine.SIMKL
        else -> SearchSourceEngine.ALL
    }

    /**
     * Akıllı Kapsam Koruması (Auto-Scope Guard):
     * Seçilen kaynak ve medya türüne göre en uygun SearchScope'u belirler.
     */
    fun autoScopeGuard(engine: SearchSourceEngine, mediaType: MediaType): SearchScope {
        return when (engine) {
            SearchSourceEngine.ALL -> when (mediaType) {
                MediaType.Anime -> SearchScope.ANIME
                MediaType.Manga -> SearchScope.MANGA
                MediaType.Movie -> SearchScope.MOVIE
                MediaType.TvShow -> SearchScope.TV
            }
            SearchSourceEngine.ANILIST -> when (mediaType) {
                MediaType.Anime -> SearchScope.ANIME
                MediaType.Manga -> SearchScope.MANGA
                MediaType.Movie, MediaType.TvShow -> SearchScope.ANIME
            }
            SearchSourceEngine.MAL -> when (mediaType) {
                MediaType.Anime -> SearchScope.ANIME
                MediaType.Manga -> SearchScope.MANGA
                MediaType.Movie, MediaType.TvShow -> SearchScope.ANIME
            }
            SearchSourceEngine.TMDB -> when (mediaType) {
                MediaType.Movie -> SearchScope.MOVIE
                MediaType.TvShow -> SearchScope.TV
                MediaType.Anime -> SearchScope.ANIME
                MediaType.Manga -> SearchScope.MOVIE
            }
            SearchSourceEngine.SHIKIMORI -> when (mediaType) {
                MediaType.Anime -> SearchScope.ANIME
                MediaType.Manga -> SearchScope.MANGA
                MediaType.Movie, MediaType.TvShow -> SearchScope.ANIME
            }
            SearchSourceEngine.KITSU -> when (mediaType) {
                MediaType.Anime -> SearchScope.ANIME
                MediaType.Manga -> SearchScope.MANGA
                MediaType.Movie, MediaType.TvShow -> SearchScope.ANIME
            }
            SearchSourceEngine.SIMKL -> when (mediaType) {
                MediaType.Anime -> SearchScope.ANIME
                MediaType.TvShow -> SearchScope.TV
                MediaType.Movie -> SearchScope.MOVIE
                MediaType.Manga -> SearchScope.ANIME
            }
        }
    }

    /**
     * MAL Anime vs Manga Genre ID Haritası
     */
    fun mapMalGenreId(genreOrTheme: String, isManga: Boolean): String? {
        val lower = genreOrTheme.lowercase().trim()
        return when (lower) {
            // Ortak Ana Türler
            "action", "aksiyon" -> "1"
            "adventure", "macera" -> "2"
            "avant garde" -> "5"
            "award winning", "ödüllü" -> "46"
            "boys love", "yaoi", "shounen ai" -> "28"
            "comedy", "komedi" -> "4"
            "drama", "dram" -> "8"
            "fantasy", "fantastik" -> "10"
            "girls love", "yuri", "shoujo ai" -> "26"
            "gourmet", "yemek" -> "47"
            "horror", "korku" -> "14"
            "mystery", "gizem" -> "7"
            "romance", "romantik" -> "22"
            "sci-fi", "bilim kurgu" -> "24"
            "slice of life", "günlük yaşam" -> "36"
            "sports", "spor" -> "30"
            "supernatural", "doğaüstü" -> "37"
            "suspense", "gerilim" -> if (isManga) "45" else "41"
            "ecchi" -> "9"
            "erotica" -> "49"
            "hentai" -> "12"

            // Demografiler
            "shounen" -> "27"
            "shoujo" -> "25"
            "seinen" -> if (isManga) "41" else "42"
            "josei" -> if (isManga) "42" else "43"
            "kids", "çocuk" -> "15"

            // Popüler Temalar
            "isekai" -> "62"
            "reincarnation", "reenkarnasyon" -> if (isManga) "73" else "72"
            "time travel", "zaman yolculuğu" -> if (isManga) "79" else "78"
            "vampire", "vampir" -> "32"
            "gore" -> "58"
            "harem" -> "35"
            "reverse harem" -> if (isManga) "74" else "73"
            "psychological", "psikolojik" -> "40"
            "detective", "dedektif" -> "39"
            "military", "askeri" -> "38"
            "mecha" -> "18"
            "music", "müzik" -> "19"
            "school", "okul" -> "23"
            "historical", "tarihi" -> "13"
            "samurai", "samuray" -> "21"
            "martial arts", "dövüş sanatları" -> "17"
            "super power", "süper güç" -> "31"
            "survival", "hayatta kalma" -> if (isManga) "77" else "76"
            "crossdressing" -> if (isManga) "44" else "81"
            "urban fantasy" -> "82"
            "video game" -> if (isManga) "80" else "79"
            "villainess" -> if (isManga) "81" else "83"
            "workplace", "iş yeri" -> "48"
            else -> null
        }
    }

    /**
     * TMDB Movie vs TV Genre ID Haritası
     */
    fun mapTmdbGenreId(genre: String, isTv: Boolean): Int? {
        val lower = genre.lowercase().trim()
        return when (lower) {
            "action", "aksiyon" -> if (isTv) 10759 else 28
            "adventure", "macera" -> if (isTv) 10759 else 12
            "animation", "animasyon", "anime" -> 16
            "comedy", "komedi" -> 35
            "crime", "suç" -> 80
            "documentary", "belgesel" -> 99
            "drama", "dram" -> 18
            "family", "aile" -> 10751
            "fantasy", "fantastik" -> if (isTv) 10765 else 14
            "history", "tarih" -> 36
            "horror", "korku" -> 27
            "music", "müzik" -> 10402
            "mystery", "gizem" -> 9648
            "romance", "romantik" -> 10749
            "sci-fi", "science fiction", "bilim kurgu" -> if (isTv) 10765 else 878
            "tv movie", "tv filmi" -> 10770
            "thriller", "gerilim" -> 53
            "war", "savaş" -> if (isTv) 10768 else 10752
            "western", "kovboy" -> 37
            else -> null
        }
    }
}
