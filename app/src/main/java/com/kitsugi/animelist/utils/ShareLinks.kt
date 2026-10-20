package com.kitsugi.animelist.utils

import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaType
import java.net.URLEncoder
import java.util.Locale

/**
 * Paylaşım bağlantılarının **tek gerçek kaynağı** (2026-10-20).
 *
 * ## Neden bu dosya var?
 *
 * Paylaş butonları her ekranda ayrı ayrı URL üretiyordu ve her biri farklı varsayımlar
 * yapıyordu. Sonuç: Shikimori/Bangumi/Simkl/Kitsu satırlarında `myanimelist.net/anime/500123456`
 * gibi **hiç var olmayan** sayfalar, AniList offset'inin (`+100M`) çözülmemesi ve hatta
 * `myanimelist.net/anime/0` (kimlik yerine `0`) gibi artıklar.
 *
 * Bu nesne, `MediaEntry.malId` / `JikanSearchResult.malId` alanındaki değeri **satırın
 * kaynağına göre** yorumlar ([decode]) ve yalnızca **kanıtlanmış** sayfa şemalarına bağlantı
 * üretir. Bir kaynağın o tür için herkese açık sayfası yoksa uydurma yol yazılmaz: sıradaki
 * doğrulanmış kaynak denenir, o da yoksa (yalnızca karakter/ekip/stüdyo için) sitenin
 * **arama sayfası** paylaşılır. Hiçbiri yoksa `null` döner ve paylaşım bağlantısız yapılır —
 * asla bozuk bir adres üretilmez.
 *
 * ## Doğrulanmış şemalar (2026-10-20)
 *
 * | Kaynak | Yapım | Karakter | Ekip | Stüdyo | Profil |
 * |---|---|---|---|---|---|
 * | AniList | `/anime/{id}`, `/manga/{id}` | `/character/{id}` | `/staff/{id}` | `/studio/{id}` | `/user/{name}` |
 * | MAL | `/anime/{id}`, `/manga/{id}` | `/character/{id}` | `/people/{id}` | `/anime/producer/{id}` | `/profile/{name}` |
 * | Bangumi | `/subject/{id}` | `/character/{id}` | `/person/{id}` | `/person/{id}` | `/user/{name}` |
 * | Shikimori | `/animes/{id}`, `/mangas/{id}` | `/characters/{id}` | `/people/{id}` | `/animes?studio={id}` | `/users/{name}` |
 * | Kitsu | `/anime/{id}`, `/manga/{id}` | — (404) | — (404) | — (404) | `/users/{name}` |
 * | TMDB | `/movie/{id}`, `/tv/{id}` | `/person/{id}` | `/person/{id}` | `/company/{id}` | `/u/{name}` |
 * | Simkl | `/anime/{id}`, `/movies/{id}`, `/tv/{id}` | — | — | — | `/{name}` |
 *
 * Kitsu'nun karakter/kişi/stüdyo sayfası **yoktur** (`kitsu.app/characters/1` → 404) ve Simkl
 * manga bölümü yoktur. Bu durumlarda MAL arama sayfası paylaşılır: alıcı her zaman açılan bir
 * sayfaya düşer.
 */
object ShareLinks {

    // ─── Sabitler ────────────────────────────────────────────────────────────

    private const val REAL_MAL_MIN = 1
    private const val REAL_MAL_MAX = 99_999_999

    private const val ANILIST_OFFSET = 100_000_000
    private const val ANILIST_MIN = 100_000_001
    private const val ANILIST_MAX = 149_999_999

    private const val SIMKL_OFFSET = 150_000_000
    private const val SIMKL_MIN = 150_000_001
    private const val SIMKL_MAX = 249_999_999

    private const val KITSU_OFFSET = 300_000_000
    private const val SHIKIMORI_OFFSET = 400_000_000
    private const val BANGUMI_OFFSET = 500_000_000
    private const val WINDOW = 100_000_000

    /**
     * Shikimori'nin uygulamanın da kullandığı aktif alan adı.
     * (`.one` bazı bölgelerde bloklu; API istemcisi de `.io` kullanıyor.)
     */
    private const val SHIKIMORI = "https://shikimori.io"

    /** Kimlik yokken denenecek doğrulanmış kaynak sırası. */
    private val FALLBACK_ORDER = listOf("mal", "anilist", "tmdb", "bangumi", "shikimori", "kitsu", "simkl")

    // ─── Kimlik çözümleme ────────────────────────────────────────────────────

    /** Bir yapımın kaynak bağımsız, **offset'siz** gerçek kimlikleri. */
    data class MediaIds(
        val malId: Int? = null,
        val aniListId: Int? = null,
        val kitsuId: Int? = null,
        val shikimoriId: Int? = null,
        val bangumiId: Int? = null,
        val simklId: Int? = null,
        val tmdbId: Int? = null
    ) {
        val isEmpty: Boolean
            get() = malId == null && aniListId == null && kitsuId == null && shikimoriId == null &&
                bangumiId == null && simklId == null && tmdbId == null
    }

    /** Kaynak adlarını tek yazıma indirger (`jikan` → `mal`, `bgm` → `bangumi`, `shiki` → `shikimori`). */
    fun canonicalSource(source: String?): String {
        // Arama sonuçları `character_anilist` / `staff_anilist` gibi sözde kaynak taşır;
        // gerçek sağlayıcı adı sona eklenmiştir.
        val raw = source?.trim()?.lowercase(Locale.ROOT).orEmpty()
        val value = raw
            .removePrefix("character_")
            .removePrefix("staff_")
            .removePrefix("studio_")
        return when (value) {
            "al" -> "anilist"
            "jikan", "myanimelist", "jikan (mal)", "mal (jikan)", "mal" -> "mal"
            "shiki" -> "shikimori"
            "bgm", "bgm.tv" -> "bangumi"
            "themoviedb" -> "tmdb"
            "tmdb-network", "tmdbnetwork" -> "tmdb_network"
            else -> value
        }
    }

    /** `MediaEntry.malId` / `JikanSearchResult.malId` alanındaki değeri kaynağına göre yorumlar. */
    fun decode(
        source: String?,
        storedId: Int?,
        tmdbId: Int? = null,
        simklId: Int? = null,
        realMalId: Int? = null
    ): MediaIds {
        val key = canonicalSource(source)
        var mal: Int? = null
        var aniList: Int? = null
        var kitsu: Int? = null
        var shikimori: Int? = null
        var bangumi: Int? = null
        var simkl: Int? = null
        var tmdb = tmdbId?.takeIf { it > 0 }

        val id = storedId?.takeIf { it > 0 }
        if (id != null) {
            when {
                // Offset pencereleri çakışmaz: 500M yalnızca Bangumi, 400M yalnızca Shikimori…
                id in (BANGUMI_OFFSET + 1)..(BANGUMI_OFFSET + WINDOW - 1) -> bangumi = id - BANGUMI_OFFSET
                id in (SHIKIMORI_OFFSET + 1)..(SHIKIMORI_OFFSET + WINDOW - 1) -> shikimori = id - SHIKIMORI_OFFSET
                id in (KITSU_OFFSET + 1)..(KITSU_OFFSET + WINDOW - 1) -> kitsu = id - KITSU_OFFSET
                // AniList (100M..299M) ile Simkl (150M..249M) pencereleri örtüşür; kaynak ayırır.
                id in SIMKL_MIN..SIMKL_MAX -> if (key == "simkl") simkl = id - SIMKL_OFFSET else aniList = id - ANILIST_OFFSET
                id in ANILIST_MIN..ANILIST_MAX -> aniList = id - ANILIST_OFFSET
                id in REAL_MAL_MIN..REAL_MAL_MAX -> when (key) {
                    // Ham (<100M) değerler satırın KAYNAĞINA göre yorumlanır — MediaIdentity.keys sözleşmesi.
                    "shikimori" -> shikimori = id
                    "bangumi" -> bangumi = id
                    "kitsu" -> kitsu = id
                    "simkl" -> simkl = id
                    // AniList satırları `idMal` bilindiğinde gerçek MAL kimliği saklar.
                    "anilist" -> mal = id
                    "tmdb" -> tmdb = tmdb ?: id
                    else -> mal = id
                }
            }
        }
        simklId?.takeIf { it > 0 }?.let { simkl = it }
        // AniList sonuçlarında `realMalId` aynı yapının MAL karşılığıdır (boşsa doldur).
        realMalId?.takeIf { it in REAL_MAL_MIN..REAL_MAL_MAX }?.let { mal = mal ?: it }

        return MediaIds(
            malId = mal,
            aniListId = aniList,
            kitsuId = kitsu,
            shikimoriId = shikimori,
            bangumiId = bangumi,
            simklId = simkl,
            tmdbId = tmdb
        )
    }

    /** Satırın kendi kimliğini (varsa `simklId`/`tmdbId` alanlarıyla birlikte) çözer. */
    fun decode(entry: MediaEntry): MediaIds = decode(
        source = entry.source,
        storedId = entry.malId,
        tmdbId = entry.tmdbId,
        simklId = entry.simklId
    )

    // ─── Yapım (anime / manga / film / dizi) ─────────────────────────────────

    fun mediaUrl(
        source: String?,
        storedId: Int?,
        type: MediaType?,
        tmdbId: Int? = null,
        simklId: Int? = null,
        realMalId: Int? = null
    ): String? = mediaUrl(
        ids = decode(source, storedId, tmdbId, simklId, realMalId),
        preferredSource = canonicalSource(source),
        type = type
    )

    fun mediaUrl(entry: MediaEntry): String? = mediaUrl(
        ids = decode(entry),
        preferredSource = canonicalSource(entry.source),
        type = entry.type
    )

    fun mediaUrl(ids: MediaIds, preferredSource: String, type: MediaType?): String? {
        if (ids.isEmpty) return null
        val order = buildList {
            if (preferredSource.isNotBlank()) add(preferredSource)
            addAll(FALLBACK_ORDER)
        }.distinct()
        for (provider in order) {
            providerMediaUrl(provider, ids, type)?.let { return it }
        }
        return null
    }

    private fun providerMediaUrl(provider: String, ids: MediaIds, type: MediaType?): String? {
        val isManga = type == MediaType.Manga
        return when (provider) {
            "mal" -> ids.malId?.let { "https://myanimelist.net/${if (isManga) "manga" else "anime"}/$it" }
            "anilist" -> ids.aniListId?.let { "https://anilist.co/${if (isManga) "manga" else "anime"}/$it" }
            "bangumi" -> ids.bangumiId?.let { "https://bgm.tv/subject/$it" }
            "shikimori" -> ids.shikimoriId?.let { "$SHIKIMORI/${if (isManga) "mangas" else "animes"}/$it" }
            "kitsu" -> ids.kitsuId?.let { "https://kitsu.app/${if (isManga) "manga" else "anime"}/$it" }
            "simkl" -> {
                // Simkl'de manga bölümü yok — uydurma `/manga/{id}` adresi üretilmez.
                if (isManga) return null
                ids.simklId?.let { simkl ->
                    val section = when (type) {
                        MediaType.Movie -> "movies"
                        MediaType.TvShow -> "tv"
                        else -> "anime"
                    }
                    "https://simkl.com/$section/$simkl"
                }
            }
            "tmdb" -> ids.tmdbId?.let { tmdb ->
                val section = when (type) {
                    MediaType.Movie -> "movie"
                    MediaType.TvShow -> "tv"
                    // TMDB'de "anime" ayrı bir tür değildir; çizgi/anime kayıtları dizi (tv)
                    // olarak tutulur ve filmler zaten Movie tipiyle gelir.
                    MediaType.Anime -> "tv"
                    else -> return null
                }
                "https://www.themoviedb.org/$section/$tmdb"
            }
            else -> null
        }
    }

    // ─── Karakter ────────────────────────────────────────────────────────────

    fun characterUrl(source: String?, characterId: Int?, name: String? = null): String? {
        val resolved = resolveEntityProvider(source, characterId)
        val rawId = resolved.second
        val url = if (rawId != null) {
            when (resolved.first) {
                "mal" -> "https://myanimelist.net/character/$rawId"
                "anilist" -> "https://anilist.co/character/$rawId"
                "bangumi" -> "https://bgm.tv/character/$rawId"
                "shikimori" -> "$SHIKIMORI/characters/$rawId"
                "tmdb" -> "https://www.themoviedb.org/person/$rawId"
                // Kitsu ve Simkl'in herkese açık karakter sayfası yok → arama sayfasına düşülür.
                else -> null
            }
        } else null
        return url ?: malSearchUrl(name, "character")
    }

    // ─── Ekip / seslendirmen ─────────────────────────────────────────────────

    fun staffUrl(source: String?, staffId: Int?, name: String? = null): String? {
        val resolved = resolveEntityProvider(source, staffId)
        val rawId = resolved.second
        val url = if (rawId != null) {
            when (resolved.first) {
                "mal" -> "https://myanimelist.net/people/$rawId"
                "anilist" -> "https://anilist.co/staff/$rawId"
                "bangumi" -> "https://bgm.tv/person/$rawId"
                "shikimori" -> "$SHIKIMORI/people/$rawId"
                "tmdb" -> "https://www.themoviedb.org/person/$rawId"
                else -> null
            }
        } else null
        return url ?: malSearchUrl(name, "people")
    }

    // ─── Stüdyo / şirket ─────────────────────────────────────────────────────

    fun studioUrl(source: String?, studioId: Int?, name: String? = null): String? {
        val resolved = resolveEntityProvider(source, studioId)
        val rawId = resolved.second
        val url = if (rawId != null) {
            when (resolved.first) {
                "mal" -> "https://myanimelist.net/anime/producer/$rawId"
                "anilist" -> "https://anilist.co/studio/$rawId"
                // Bangumi'de şirketler `person` kimlik alanında yaşar.
                "bangumi" -> "https://bgm.tv/person/$rawId"
                // Shikimori'nin stüdyo için ayrı sayfası yok; aynı işlevi gören filtre sayfası var.
                "shikimori" -> "$SHIKIMORI/animes?studio=$rawId"
                "tmdb" -> "https://www.themoviedb.org/company/$rawId"
                "tmdb_network" -> "https://www.themoviedb.org/network/$rawId"
                else -> null
            }
        } else null
        return url ?: malSearchUrl(name, "company")
    }

    // ─── Profil ──────────────────────────────────────────────────────────────

    fun profileUrl(source: String?, username: String?): String? {
        val name = username?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val encoded = encodePathSegment(name)
        return when (canonicalSource(source)) {
            "anilist" -> "https://anilist.co/user/$encoded"
            "kitsu" -> "https://kitsu.app/users/$encoded"
            "shikimori" -> "$SHIKIMORI/users/$encoded"
            "bangumi" -> "https://bgm.tv/user/$encoded"
            // Simkl profilleri kök altındadır (`simkl.com/{kullanıcı}`), `/user/{ad}` 404 döner.
            "simkl" -> "https://simkl.com/$encoded"
            "tmdb" -> "https://www.themoviedb.org/u/$encoded"
            else -> "https://myanimelist.net/profile/$encoded"
        }
    }

    // ─── Yardımcılar ─────────────────────────────────────────────────────────

    /**
     * Karakter/kişi/stüdyo kimliğini (kaynak, ham kimlik) çiftine çevirir.
     *
     * Bu varlıkların kimlikleri normalde hamdır; ancak eski kayıtlar `malId` alanındaki
     * offset'li stableId'yi sızdırabiliyor. Offset penceresi içindeki bir değer asla ham
     * kimlik sayılmaz (aksi hâlde `bgm.tv/character/500123456` gibi 404 üretilirdi).
     */
    private fun resolveEntityProvider(source: String?, rawId: Int?): Pair<String, Int?> {
        val id = rawId?.takeIf { it > 0 }
        if (id != null) {
            val unwrapped = when {
                id in (BANGUMI_OFFSET + 1)..(BANGUMI_OFFSET + WINDOW - 1) -> "bangumi" to (id - BANGUMI_OFFSET)
                id in (SHIKIMORI_OFFSET + 1)..(SHIKIMORI_OFFSET + WINDOW - 1) -> "shikimori" to (id - SHIKIMORI_OFFSET)
                id in (KITSU_OFFSET + 1)..(KITSU_OFFSET + WINDOW - 1) -> "kitsu" to (id - KITSU_OFFSET)
                id in ANILIST_MIN..ANILIST_MAX -> "anilist" to (id - ANILIST_OFFSET)
                id in SIMKL_MIN..SIMKL_MAX -> "simkl" to (id - SIMKL_OFFSET)
                else -> null
            }
            if (unwrapped != null) return unwrapped
        }
        return canonicalSource(source) to id
    }

    /**
     * Sitenin arama sayfası — yalnızca ilgili kaynağın o varlık için sayfası **yoksa** kullanılır.
     *
     * `character.php`, `people.php` ve `company` uçları MAL'da arama yapar; eşleşme varsa
     * doğrudan o kaydın sayfasına yönlendirir. Böylece Kitsu/Simkl karakterinde alıcı yine
     * anlamlı bir sayfaya düşer.
     */
    private fun malSearchUrl(name: String?, kind: String): String? {
        val query = name?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val encoded = URLEncoder.encode(query, "UTF-8")
        return when (kind) {
            "character" -> "https://myanimelist.net/character.php?q=$encoded"
            "people" -> "https://myanimelist.net/people.php?q=$encoded"
            else -> "https://myanimelist.net/company?q=$encoded"
        }
    }

    private fun encodePathSegment(value: String): String = URLEncoder.encode(value, "UTF-8")

    /** Son savunma hattı: `0` kimliği veya offset'li stableId sızmış adresler paylaşılmaz. */
    fun isTrustworthy(url: String?): Boolean {
        val value = url?.trim().orEmpty()
        if (!value.startsWith("https://")) return false
        // `/0`, `/0/` veya `?id=0` gibi kimliksiz yollar.
        if (Regex("(^|/)0(/|$|\\?)").containsMatchIn(value)) return false
        // 100M+ kimlik bu sitelerde her zaman offset sızıntısıdır.
        if (Regex("/[1-9][0-9]{8,}(/|$|\\?)").containsMatchIn(value)) return false
        return true
    }
}
