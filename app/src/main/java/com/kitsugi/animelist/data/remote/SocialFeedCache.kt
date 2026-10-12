package com.kitsugi.animelist.data.remote

import com.kitsugi.animelist.core.memory.BoundedCache
import com.kitsugi.animelist.model.MediaType

/**
 * Yorumlar sekmesinin sosyal akış verileri (tartışma konuları / aktiviteler / incelemeler)
 * için gezinme önbelleği.
 *
 * NEDEN VAR?
 * Detay sayfasında yalnızca yığın tepesindeki ekran compose edildiği için kullanıcı bir
 * profil ekranına tıklayıp geri döndüğünde [com.kitsugi.animelist.ui.screens.detail.ReviewsTabContent]
 * ve "Tüm ..." alt sayfaları yeniden kuruluyordu; `remember` ile tutulan tüm listeler sıfırlanıp
 * ağdan yeniden çekiliyordu ("yorumlar sayfası bozuluyor, veriler gitti" hatası).
 * Bu önbellek, aynı medyanın akışını süreç boyunca tutar; geri dönüşte liste anında
 * geri gelir ve sayfa yeniden yüklenmez.
 *
 * Not: Beğeni (like) durumu gibi yerel değişiklikler de yazılır; kaynak API beğeni
 * tutarlılığı için [com.kitsugi.animelist.data.remote.DetailCache] mantığına benzer
 * "son yazan kazanır" ilerletilir.
 */
object SocialFeedCache {

    /** Sayfalanmış akış durumu: [items] birikimli listedir ([page] sayfaya kadar). */
    data class FeedState<T>(
        val items: List<T>,
        val page: Int = 1,
        val hasMore: Boolean = true
    )

    private val previewTopics = BoundedCache<String, FeedState<KitsugiForumTopic>>("social.preview_topics", 24)
    private val allTopics = BoundedCache<String, FeedState<KitsugiForumTopic>>("social.all_topics", 24)
    private val previewActivities = BoundedCache<String, FeedState<KitsugiActivity>>("social.preview_activities", 24)
    private val allActivities = BoundedCache<String, FeedState<KitsugiActivity>>("social.all_activities", 24)
    private val allReviews = BoundedCache<String, FeedState<KitsugiReview>>("social.all_reviews", 24)

    /** Detay sekmesi parametrelerinden kararlı bir akış anahtarı üretir. */
    fun feedKey(
        source: String,
        externalId: Int,
        mediaType: MediaType,
        tmdbId: Int? = null,
        realMalId: Int? = null
    ): String = "${source.lowercase()}|$externalId|${mediaType.name}|${tmdbId ?: -1}|${realMalId ?: -1}"

    // ── Tartışma konuları ────────────────────────────────────────────────────

    fun previewTopics(key: String): FeedState<KitsugiForumTopic>? = previewTopics[key]

    fun putPreviewTopics(key: String, items: List<KitsugiForumTopic>) {
        if (items.isEmpty()) return
        previewTopics[key] = FeedState(items = items, page = 1, hasMore = true)
    }

    fun allTopics(key: String): FeedState<KitsugiForumTopic>? = allTopics[key]

    fun putAllTopics(key: String, state: FeedState<KitsugiForumTopic>) {
        if (state.items.isEmpty() && state.page <= 1) return
        allTopics[key] = state
    }

    // ── Aktiviteler ──────────────────────────────────────────────────────────

    fun previewActivities(key: String): FeedState<KitsugiActivity>? = previewActivities[key]

    fun putPreviewActivities(key: String, items: List<KitsugiActivity>) {
        if (items.isEmpty()) return
        previewActivities[key] = FeedState(items = items, page = 1, hasMore = true)
    }

    fun allActivities(key: String): FeedState<KitsugiActivity>? = allActivities[key]

    fun putAllActivities(key: String, state: FeedState<KitsugiActivity>) {
        if (state.items.isEmpty() && state.page <= 1) return
        allActivities[key] = state
    }

    // ── İncelemeler (Tüm İncelemeler sayfası) ────────────────────────────────

    fun allReviews(key: String): FeedState<KitsugiReview>? = allReviews[key]

    fun putAllReviews(key: String, state: FeedState<KitsugiReview>) {
        if (state.items.isEmpty() && state.page <= 1) return
        allReviews[key] = state
    }

    // ── Yerel beğeni güncellemesi ────────────────────────────────────────────

    /**
     * Bir tartışması konusunun beğeni durumunu tüm önbellek kopyalarında günceller
     * (önizleme + "Tüm Konular" listeleri birlikte kalsın diye).
     */
    fun updateTopicLike(key: String, topicId: Int, source: String, liked: Boolean, likeCount: Int) {
        fun List<KitsugiForumTopic>.patch(): List<KitsugiForumTopic> = map { t ->
            if (t.id == topicId && t.source.equals(source, ignoreCase = true)) {
                t.copy(isLiked = liked, likeCount = likeCount)
            } else t
        }
        previewTopics[key]?.let { previewTopics[key] = it.copy(items = it.items.patch()) }
        allTopics[key]?.let { allTopics[key] = it.copy(items = it.items.patch()) }
    }

    /** Bir aktivitenin beğeni durumunu tüm önbellek kopyalarında günceller. */
    fun updateActivityLike(key: String, activityId: Int, liked: Boolean, likeCount: Int) {
        fun List<KitsugiActivity>.patch(): List<KitsugiActivity> = map { a ->
            if (a.id == activityId) a.copy(isLiked = liked, likeCount = likeCount) else a
        }
        previewActivities[key]?.let { previewActivities[key] = it.copy(items = it.items.patch()) }
        allActivities[key]?.let { allActivities[key] = it.copy(items = it.items.patch()) }
    }
}
