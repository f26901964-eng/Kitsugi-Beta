package com.kitsugi.animelist.data.remote

import com.kitsugi.animelist.core.memory.BoundedCache

import java.util.concurrent.ConcurrentHashMap

object DetailCache {

    // ─── Episode Ratings (tmdbId → CacheEntry) ───────────────────────────────
    data class RatingCacheEntry(
        val ratings: Map<Pair<Int, Int>, Double>,
        val expiresAtMs: Long
    )
    val episodeRatingsCache = BoundedCache<Int, RatingCacheEntry>("detail.episodeRatings", 150)

    // ─── MAL ID → TMDB ID mapping ────────────────────────────────────────────
    // Negative keys = AniList IDs (stored as -aniListId)
    val malToTmdbCache: MutableMap<Int, Int?> = BoundedCache<Int, Int?>("detail.malToTmdb", 3000)

    // ─── TMDB ID → TVDB ID mapping ───────────────────────────────────────────
    val tmdbToTvdbCache: MutableMap<Int, Int?> = BoundedCache<Int, Int?>("detail.tmdbToTvdb", 3000)

    // ─── TMDB ID → media kind ("movie" | "tv" | null = kontrol edildi, film değil) ──
    // ARM ("media") / animeapi ("themoviedb_type") çapraz eşlemelerinden doldurulur.
    // Film kayıtlarının TVDB/fanart-TV zincirine girmesini engelleyen doğruluk kaynağı:
    // TVDB filmleri dizinin 0. sezonu olarak tutar; film için TVDB kullanılırsa dizi
    // logosu/afişi sızar (Chainsaw Man Reze-hen vakası).
    val tmdbMediaCache: MutableMap<Int, String?> = BoundedCache<Int, String?>("detail.tmdbMedia", 3000)

    // ─── TMDB ID → Logo URL mapping ──────────────────────────────────────────
    val logoCache: MutableMap<Int, String?> = BoundedCache<Int, String?>("detail.logo", 1500)

    // ─── TMDB (tmdbId, season) → episode DTOs ────────────────────────────────
    data class TmdbEpisodeDtoCached(
        val episodeNumber: Int,
        val name: String?,
        val overview: String?,
        val stillPath: String?,
        val airDate: String?
    )
    val tmdbEpisodesCache = BoundedCache<Pair<Int, Int>, List<TmdbEpisodeDtoCached>>("detail.tmdbEpisodes", 80)
    private val mediaDetails = BoundedCache<String, KitsugiMediaDetail>("detail.media", 160)
    private val mediaCharacters = BoundedCache<String, List<KitsugiCharacter>>("detail.characters", 80)
    private val mediaStaff = BoundedCache<String, List<KitsugiStaff>>("detail.staff", 80)
    private val mediaRelations = BoundedCache<String, List<KitsugiRelation>>("detail.relations", 80)
    private val mediaStats = BoundedCache<String, KitsugiStats>("detail.stats", 80)
    private val mediaReviews = BoundedCache<String, List<KitsugiReview>>("detail.reviews", 40)
    private val mediaEpisodes = BoundedCache<String, List<KitsugiStreamingEpisode>>("detail.episodes", 60)
    private val mediaRecommendations = BoundedCache<String, List<KitsugiRelation>>("detail.recommendations", 60)

    private val characterDetails = BoundedCache<String, KitsugiCharacterDetail>("detail.characterDetail", 60)
    private val staffDetails = BoundedCache<String, KitsugiStaffDetail>("detail.staffDetail", 60)
    private val studioDetails = BoundedCache<String, KitsugiStudioDetail>("detail.studioDetail", 30)

    private val translations = BoundedCache<String, String>("detail.translations", 300)

    fun makeKey(source: String, id: Int, mediaType: String? = null): String {
        return if (!mediaType.isNullOrBlank()) {
            "${source.lowercase()}_${mediaType.lowercase()}_$id"
        } else {
            "${source.lowercase()}_$id"
        }
    }

    // Media Details
    fun getMediaDetail(source: String, id: Int, mediaType: String? = null): KitsugiMediaDetail? {
        val composite = mediaDetails[makeKey(source, id, mediaType)]
        return composite ?: mediaDetails[makeKey(source, id)]
    }

    fun putMediaDetail(source: String, id: Int, detail: KitsugiMediaDetail, mediaType: String? = null) {
        mediaDetails[makeKey(source, id, mediaType)] = detail
        mediaDetails[makeKey(source, id)] = detail
    }

    fun removeMediaDetail(source: String, id: Int, mediaType: String? = null) {
        mediaDetails.remove(makeKey(source, id, mediaType))
        mediaDetails.remove(makeKey(source, id))
    }

    // Characters Tab
    fun getMediaCharacters(source: String, id: Int, mediaType: String? = null): List<KitsugiCharacter>? {
        return mediaCharacters[makeKey(source, id, mediaType)] ?: mediaCharacters[makeKey(source, id)]
    }

    fun putMediaCharacters(source: String, id: Int, list: List<KitsugiCharacter>, mediaType: String? = null) {
        mediaCharacters[makeKey(source, id, mediaType)] = list
        mediaCharacters[makeKey(source, id)] = list
    }

    // Staff Tab
    fun getMediaStaff(source: String, id: Int, mediaType: String? = null): List<KitsugiStaff>? {
        return mediaStaff[makeKey(source, id, mediaType)] ?: mediaStaff[makeKey(source, id)]
    }

    fun putMediaStaff(source: String, id: Int, list: List<KitsugiStaff>, mediaType: String? = null) {
        mediaStaff[makeKey(source, id, mediaType)] = list
        mediaStaff[makeKey(source, id)] = list
    }

    // Relations Tab
    fun getMediaRelations(source: String, id: Int, mediaType: String? = null): List<KitsugiRelation>? {
        return mediaRelations[makeKey(source, id, mediaType)] ?: mediaRelations[makeKey(source, id)]
    }

    fun putMediaRelations(source: String, id: Int, list: List<KitsugiRelation>, mediaType: String? = null) {
        mediaRelations[makeKey(source, id, mediaType)] = list
        mediaRelations[makeKey(source, id)] = list
    }

    // Stats Tab
    fun getMediaStats(source: String, id: Int, mediaType: String? = null): KitsugiStats? {
        return mediaStats[makeKey(source, id, mediaType)] ?: mediaStats[makeKey(source, id)]
    }

    fun putMediaStats(source: String, id: Int, stats: KitsugiStats, mediaType: String? = null) {
        mediaStats[makeKey(source, id, mediaType)] = stats
        mediaStats[makeKey(source, id)] = stats
    }

    fun hasMediaStats(source: String, id: Int, mediaType: String? = null): Boolean {
        return mediaStats.containsKey(makeKey(source, id, mediaType)) || mediaStats.containsKey(makeKey(source, id))
    }

    // Reviews Tab
    fun getMediaReviews(source: String, id: Int, mediaType: String? = null): List<KitsugiReview>? {
        // Review results are source + format specific. Unlike general metadata, an
        // untyped fallback can leak a show's/anime's reviews onto a different detail page.
        val key = if (mediaType.isNullOrBlank()) makeKey(source, id) else makeKey(source, id, mediaType)
        return mediaReviews[key]
    }

    fun putMediaReviews(source: String, id: Int, list: List<KitsugiReview>, mediaType: String? = null) {
        mediaReviews[makeKey(source, id, mediaType)] = list
        mediaReviews[makeKey(source, id)] = list
    }

    fun removeMediaReviews(source: String, id: Int, mediaType: String? = null) {
        mediaReviews.remove(makeKey(source, id, mediaType))
        mediaReviews.remove(makeKey(source, id))
    }

    // Episodes Tab
    fun getMediaEpisodes(source: String, id: Int, mediaType: String? = null): List<KitsugiStreamingEpisode>? {
        return mediaEpisodes[makeKey(source, id, mediaType)] ?: mediaEpisodes[makeKey(source, id)]
    }

    fun putMediaEpisodes(source: String, id: Int, list: List<KitsugiStreamingEpisode>, mediaType: String? = null) {
        mediaEpisodes[makeKey(source, id, mediaType)] = list
        mediaEpisodes[makeKey(source, id)] = list
    }

    fun removeMediaEpisodes(source: String, id: Int, mediaType: String? = null) {
        mediaEpisodes.remove(makeKey(source, id, mediaType))
        mediaEpisodes.remove(makeKey(source, id))
    }

    // Recommendations Tab
    fun getMediaRecommendations(source: String, id: Int, mediaType: String? = null): List<KitsugiRelation>? {
        return mediaRecommendations[makeKey(source, id, mediaType)] ?: mediaRecommendations[makeKey(source, id)]
    }

    fun putMediaRecommendations(source: String, id: Int, list: List<KitsugiRelation>, mediaType: String? = null) {
        mediaRecommendations[makeKey(source, id, mediaType)] = list
        mediaRecommendations[makeKey(source, id)] = list
    }

    fun removeMediaRecommendations(source: String, id: Int, mediaType: String? = null) {
        mediaRecommendations.remove(makeKey(source, id, mediaType))
        mediaRecommendations.remove(makeKey(source, id))
    }

    fun removeMediaCharacters(source: String, id: Int, mediaType: String? = null) {
        mediaCharacters.remove(makeKey(source, id, mediaType))
        mediaCharacters.remove(makeKey(source, id))
    }

    fun removeMediaStaff(source: String, id: Int, mediaType: String? = null) {
        mediaStaff.remove(makeKey(source, id, mediaType))
        mediaStaff.remove(makeKey(source, id))
    }

    fun removeMediaRelations(source: String, id: Int, mediaType: String? = null) {
        mediaRelations.remove(makeKey(source, id, mediaType))
        mediaRelations.remove(makeKey(source, id))
    }


    // Character Detail
    fun getCharacterDetail(source: String, id: Int): KitsugiCharacterDetail? {
        return characterDetails[makeKey(source, id)]
    }

    fun putCharacterDetail(source: String, id: Int, detail: KitsugiCharacterDetail) {
        characterDetails[makeKey(source, id)] = detail
    }

    fun removeCharacterDetail(source: String, id: Int) {
        characterDetails.remove(makeKey(source, id))
    }

    // Staff Detail
    fun getStaffDetail(source: String, id: Int): KitsugiStaffDetail? {
        return staffDetails[makeKey(source, id)]
    }

    fun putStaffDetail(source: String, id: Int, detail: KitsugiStaffDetail) {
        staffDetails[makeKey(source, id)] = detail
    }

    fun removeStaffDetail(source: String, id: Int) {
        staffDetails.remove(makeKey(source, id))
    }

    // Studio Detail
    fun getStudioDetail(source: String, id: Int): KitsugiStudioDetail? {
        return studioDetails[makeKey(source, id)]
    }

    fun putStudioDetail(source: String, id: Int, detail: KitsugiStudioDetail) {
        studioDetails[makeKey(source, id)] = detail
    }

    private val fanartGalleryCache = BoundedCache<String, List<GalleryItem>>("detail.fanartGallery", 20)
    private val tmdbGalleryCache = BoundedCache<String, List<GalleryItem>>("detail.tmdbGallery", 20)

    // Translations (Synopsis / Biography)
    fun getTranslation(type: String, source: String, id: Int): String? {
        val key = "${type.lowercase()}_${makeKey(source, id)}"
        return translations[key]
    }

    fun putTranslation(type: String, source: String, id: Int, translation: String) {
        val key = "${type.lowercase()}_${makeKey(source, id)}"
        translations[key] = translation
    }

    fun removeTranslation(type: String, source: String, id: Int) {
        val key = "${type.lowercase()}_${makeKey(source, id)}"
        translations.remove(key)
    }

    // Fanart.tv Gallery Cache
    fun getFanartGallery(isMovie: Boolean, id: Int): List<GalleryItem>? {
        val key = "${if (isMovie) "movie" else "tv"}_$id"
        return fanartGalleryCache[key]
    }

    fun putFanartGallery(isMovie: Boolean, id: Int, list: List<GalleryItem>) {
        val key = "${if (isMovie) "movie" else "tv"}_$id"
        fanartGalleryCache[key] = list
    }

    fun removeFanartGallery(isMovie: Boolean, id: Int) {
        val key = "${if (isMovie) "movie" else "tv"}_$id"
        fanartGalleryCache.remove(key)
    }

    fun clearFanartCache() {
        fanartGalleryCache.clear()
    }

    // TMDB Gallery Cache
    fun getTmdbGallery(isMovie: Boolean, id: Int): List<GalleryItem>? {
        val key = "${if (isMovie) "movie" else "tv"}_$id"
        return tmdbGalleryCache[key]
    }

    fun putTmdbGallery(isMovie: Boolean, id: Int, list: List<GalleryItem>) {
        val key = "${if (isMovie) "movie" else "tv"}_$id"
        tmdbGalleryCache[key] = list
    }

    fun removeTmdbGallery(isMovie: Boolean, id: Int) {
        val key = "${if (isMovie) "movie" else "tv"}_$id"
        tmdbGalleryCache.remove(key)
    }

    fun clearTmdbGalleryCache() {
        tmdbGalleryCache.clear()
    }

    // Shikimori Gallery Cache
    private val shikimoriGalleryCache = BoundedCache<Int, List<GalleryItem>>("detail.shikimoriGallery", 20)

    fun getShikimoriGallery(id: Int): List<GalleryItem>? = shikimoriGalleryCache[id]
    fun putShikimoriGallery(id: Int, list: List<GalleryItem>) { shikimoriGalleryCache[id] = list }
    fun removeShikimoriGallery(id: Int) { shikimoriGalleryCache.remove(id) }
    fun clearShikimoriGalleryCache() { shikimoriGalleryCache.clear() }

    // Clear all (called on app-level reset / force refresh from settings)
    fun clear() {
        mediaDetails.clear()
        mediaCharacters.clear()
        mediaStaff.clear()
        mediaRelations.clear()
        mediaStats.clear()
        mediaReviews.clear()
        mediaEpisodes.clear()
        mediaRecommendations.clear()
        characterDetails.clear()
        staffDetails.clear()
        studioDetails.clear()
        translations.clear()
        fanartGalleryCache.clear()
        tmdbGalleryCache.clear()
        shikimoriGalleryCache.clear()
        episodeRatingsCache.clear()
        malToTmdbCache.clear()
        tmdbToTvdbCache.clear()
        tmdbMediaCache.clear()
        logoCache.clear()
        tmdbEpisodesCache.clear()
    }
}
