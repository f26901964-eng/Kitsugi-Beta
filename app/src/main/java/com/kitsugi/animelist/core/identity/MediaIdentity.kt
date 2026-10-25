package com.kitsugi.animelist.core.identity

import com.kitsugi.animelist.data.cloudstream.CsTitleMatcher
import com.kitsugi.animelist.data.remote.ResolvedIds
import com.kitsugi.animelist.model.MediaType
import java.util.Locale

/**
 * Kaynak-BAĞIMSIZ içerik kimliği (Ortak Kaynak/Altyazı Omurgası – Faz 1).
 *
 * SORUN (2026-10-25 kullanıcı raporu):
 *  Aynı bölüm, farklı giriş noktalarından (AniList detayı, bir CloudStream eklentisinin
 *  kendi kataloğu, izleme geçmişi, arama sonucu, otomatik oynatma) açıldığında farklı
 *  kimlik setleriyle sorgulanıyordu:
 *    • AniList/MAL detayından giriş  → malId + aniListId + tmdbId + tüm başlık varyantları
 *    • CS eklentisi kataloğundan giriş → yalnızca cs3Url + tek başlık (ID YOK)
 *    • Geçmişten devam              → cs3Url + tek başlık, ID'ler eksik olabiliyor
 *  Sonuç: aynı bölüm için "bazı kaynaklardan veri geliyor, bazılarından gelmiyor" ve
 *  altyazı eklentilerinin bir kaynakta çalışıp diğerinde hiç sonuç vermemesi.
 *
 * ÇÖZÜM: Tüm giriş noktaları aynı [MediaIdentity] modelini üretir. Kimlik çözümü bir kez
 *  yapılır, [MediaIdentityRegistry] üzerinden paylaşılır; altyazı önbelleği bu kimliğe
 *  göre anahtarlanır. Böylece hangi kaynaktan girilirse girilsin aynı kimlik, aynı
 *  başlık varyantları ve aynı altyazı havuzu kullanılır.
 */
data class MediaIdentity(
    /** Birincil (yerelleştirilmiş) başlık. */
    val title: String,
    /** Alternatif başlıklar: İngilizce / romaji / native / eş anlamlılar. */
    val alternativeTitles: List<String> = emptyList(),
    val mediaType: MediaType? = null,
    val isMovie: Boolean = false,
    val startYear: Int? = null,
    val malId: Int? = null,
    val aniListId: Int? = null,
    val tmdbId: Int? = null,
    val bangumiId: Int? = null,
    val imdbId: String? = null,
    val kitsuId: Int? = null,
    val season: Int = 1,
    val episode: Int = 1
) {

    /** Ağ sorgularında kullanılacak normalize edilmiş tüm başlıklar (tekrarsız). */
    val allTitles: List<String>
        get() = (listOf(title) + alternativeTitles)
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { CsTitleMatcher.normalizeTitleForMatch(it) }
            .toList()

    /** Altyazı eklentilerine "isimle arama" için kullanılacak başlıklar (en fazla 4). */
    val queryTitles: List<String>
        get() = allTitles.take(4)

    val normalizedTitle: String
        get() = CsTitleMatcher.normalizeTitleForMatch(title)

    /**
     * İçerik (bölümden bağımsız) anahtarı.
     *
     * ID varsa ID'ler kullanılır — böylece farklı başlık dilleriyle gelen aynı içerik
     * aynı anahtara düşer. ID yoksa normalize başlık + yıl kullanılır ve
     * [MediaIdentityRegistry] bu anahtarı ID'lere bağlar.
     */
    val contentKey: String
        get() {
            val ids = buildList {
                imdbId?.takeIf { it.isNotBlank() }?.let { add("imdb=${it.lowercase(Locale.ROOT)}") }
                malId?.takeIf { it > 0 }?.let { add("mal=$it") }
                aniListId?.takeIf { it > 0 }?.let { add("anilist=$it") }
                kitsuId?.takeIf { it > 0 }?.let { add("kitsu=$it") }
                tmdbId?.takeIf { it > 0 }?.let { add("tmdb=$it") }
                bangumiId?.takeIf { it > 0 }?.let { add("bangumi=$it") }
            }
            return if (ids.isNotEmpty()) ids.joinToString("|")
            else "title=${titleKey(title, startYear)}"
        }

    /** Altyazı/akış anahtarlarında kullanılan sezon (film → 1). */
    val effectiveSeason: Int get() = if (isMovie) 1 else season

    /** Altyazı/akış anahtarlarında kullanılan bölüm (film → 1). */
    val effectiveEpisode: Int get() = if (isMovie) 1 else episode

    /** Bölüm bazlı altyazı anahtarı. */
    val subtitleKey: String
        get() = "$contentKey|s=$effectiveSeason|e=$effectiveEpisode"

    /** Aynı içeriğin başka bir bölümü. */
    fun withEpisode(season: Int, episode: Int): MediaIdentity = copy(season = season, episode = episode)

    /** Çözülen kimlikleri mevcut kimliğe işler (yalnızca boş alanları doldurur). */
    fun withResolved(ids: ResolvedIds?): MediaIdentity {
        if (ids == null) return this
        return copy(
            imdbId = imdbId ?: ids.imdbId?.takeIf { it.isNotBlank() },
            kitsuId = kitsuId ?: ids.kitsuId?.takeIf { it > 0 },
            malId = malId ?: ids.malId?.takeIf { it > 0 },
            aniListId = aniListId ?: ids.aniListId?.takeIf { it > 0 },
            tmdbId = tmdbId ?: ids.tmdbId?.takeIf { it > 0 }
        )
    }

    /**
     * Altyazı önbelleği için kullanılacak anahtar adayları.
     *
     * Aynı bölüm hem ID hem başlık anahtarıyla saklanmış olabilir (eski kayıtlar, ID'siz
     * girişler). İki anahtar birlikte okunur ve birleştirilir → kaynak kayırmama.
     */
    val subtitleKeyCandidates: List<String>
        get() {
            val s = effectiveSeason
            val e = effectiveEpisode
            val candidates = mutableListOf(subtitleKey)
            // Yıl bilgisi giriş noktasına göre değişebiliyor (detay sayfası yılı bilir,
            // katalog/geçmiş bilmez). Bu yüzden yıllı VE yılsız başlık anahtarı birlikte
            // aranır; aksi halde aynı bölüm "bulunamadı" görünür.
            if (startYear != null && startYear > 0) {
                candidates.add("title=${titleKey(title, startYear)}|s=$s|e=$e")
            }
            candidates.add("title=${titleKey(title, null)}|s=$s|e=$e")
            // Alternatif başlıklarla saklanmış kayıtlar (farklı dilde giriş) da bulunsun.
            alternativeTitles.asSequence()
                .map { it.trim() }
                .filter { it.isNotBlank() && it != title }
                .take(3)
                .forEach { alt -> candidates.add("title=${titleKey(alt, null)}|s=$s|e=$e") }
            return candidates.distinct()
        }

    companion object {
        /** Başlık + yıl tabanlı, dilden bağımsız anahtar parçası. */
        fun titleKey(title: String, year: Int?): String {
            val norm = CsTitleMatcher.normalizeTitleForMatch(title)
            return if (year != null && year > 0) "$norm:$year" else norm
        }

        fun fromResolved(
            title: String,
            alternativeTitles: List<String> = emptyList(),
            mediaType: MediaType? = null,
            isMovie: Boolean = false,
            startYear: Int? = null,
            malId: Int? = null,
            aniListId: Int? = null,
            tmdbId: Int? = null,
            bangumiId: Int? = null,
            season: Int = 1,
            episode: Int = 1,
            ids: ResolvedIds? = null
        ): MediaIdentity = MediaIdentity(
            title = title,
            alternativeTitles = alternativeTitles,
            mediaType = mediaType,
            isMovie = isMovie,
            startYear = startYear,
            malId = malId,
            aniListId = aniListId,
            tmdbId = tmdbId,
            bangumiId = bangumiId,
            imdbId = ids?.imdbId,
            kitsuId = ids?.kitsuId,
            season = season,
            episode = episode
        ).withResolved(ids)
    }
}
