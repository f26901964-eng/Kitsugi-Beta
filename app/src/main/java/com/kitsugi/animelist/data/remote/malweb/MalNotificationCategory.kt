package com.kitsugi.animelist.data.remote.malweb

import com.kitsugi.animelist.R

/**
 * MAL web bildirim kategorileri — `myanimelist.net/notification` sayfasının sol menüsü
 * ve üstteki `View All (45)` açılırı ile birebir eşleşir.
 *
 * Ekran görüntüleri (`image-*.png`) referansı:
 *  - All Notifications    (45) → tamamı
 *  - Friend Requests      (1)  → friend_request*
 *  - Profile Comments     (1)  → profile_comment
 *  - Blog Comments        (0)  → blog_comment
 *  - Forum Quotes         (0)  → forum_quote
 *  - User Mentions        (0)  → user_mention_*
 *  - Watched Topic Posts  (0)  → watched_topic_message
 *  - Club Messages        (0)  → club_mass_message_in_forum
 *  - New Related Anime    (39) → related_anime_add     ← en yoğun
 *  - Now Airing           (4)  → on_air
 *  - Payment              (0)  → payment
 *
 * Her kategori bir veya birden fazla `typeIdentifier` değerini kapsar.
 * `typeIdentifier` MAL'ın `window.MAL.notification` JSON'undaki anahtar alanıdır.
 */
enum class MalNotificationCategory(
    val labelResId: Int,
    val fallbackLabel: String,
    val typeIdentifiers: Set<String>,
    /** MAL sitesindeki kategori parametresi (varsa). Web filtrelemesi için kullanılır; boşsa istemci tarafı filtrelenir. */
    val webParam: String?,
    val sidebarCountKey: String
) {
    ALL(
        labelResId = R.string.mal_notif_filter_all,
        fallbackLabel = "Tümü",
        typeIdentifiers = emptySet(),
        webParam = null,
        sidebarCountKey = "AllNotifications"
    ),
    NEW_RELATED_ANIME(
        labelResId = R.string.mal_notif_filter_related,
        fallbackLabel = "Yeni Bağlantılı Anime",
        typeIdentifiers = setOf("related_anime_add"),
        webParam = "related_anime",
        sidebarCountKey = "New Related Anime"
    ),
    NOW_AIRING(
        labelResId = R.string.mal_notif_filter_airing,
        fallbackLabel = "Yayında",
        typeIdentifiers = setOf("on_air"),
        webParam = "on_air",
        sidebarCountKey = "Now Airing"
    ),
    FRIEND_REQUESTS(
        labelResId = R.string.mal_notif_filter_friend,
        fallbackLabel = "Arkadaşlık İstekleri",
        typeIdentifiers = setOf("friend_request", "friend_request_accept", "friend_request_deny"),
        webParam = "friend_request",
        sidebarCountKey = "Friend Requests"
    ),
    PROFILE_COMMENTS(
        labelResId = R.string.mal_notif_filter_profile,
        fallbackLabel = "Profil Yorumları",
        typeIdentifiers = setOf("profile_comment"),
        webParam = "profile_comment",
        sidebarCountKey = "Profile Comments"
    ),
    BLOG_COMMENTS(
        labelResId = R.string.mal_notif_filter_blog,
        fallbackLabel = "Blog Yorumları",
        typeIdentifiers = setOf("blog_comment"),
        webParam = "blog_comment",
        sidebarCountKey = "Blog Comments"
    ),
    FORUM_QUOTES(
        labelResId = R.string.mal_notif_filter_forum_quote,
        fallbackLabel = "Forum Alıntıları",
        typeIdentifiers = setOf("forum_quote"),
        webParam = "forum_quote",
        sidebarCountKey = "Forum Quotes"
    ),
    USER_MENTIONS(
        labelResId = R.string.mal_notif_filter_mention,
        fallbackLabel = "Bahsetmeler",
        typeIdentifiers = setOf(
            "user_mention_in_club_comment",
            "user_mention_in_forum_message",
            "user_mention"
        ),
        webParam = "user_mention",
        sidebarCountKey = "User Mentions"
    ),
    WATCHED_TOPIC_POSTS(
        labelResId = R.string.mal_notif_filter_watched,
        fallbackLabel = "Takip Edilen Konular",
        typeIdentifiers = setOf("watched_topic_message"),
        webParam = "watched_topic",
        sidebarCountKey = "Watched Topic Posts"
    ),
    CLUB_MESSAGES(
        labelResId = R.string.mal_notif_filter_club,
        fallbackLabel = "Kulüp Mesajları",
        typeIdentifiers = setOf("club_mass_message_in_forum", "club_message"),
        webParam = "club_message",
        sidebarCountKey = "Club Messages"
    ),
    PAYMENT(
        labelResId = R.string.mal_notif_filter_payment,
        fallbackLabel = "Ödeme",
        typeIdentifiers = setOf("payment", "payment_completed", "payment_failed"),
        webParam = "payment",
        sidebarCountKey = "Payment"
    );

    companion object {
        /** typeIdentifier → kategori eşlemesi (bilinmeyenler ALL altında gösterilir). */
        fun fromTypeIdentifier(type: String?): MalNotificationCategory {
            if (type.isNullOrBlank()) return ALL
            val norm = type.trim().lowercase()
            return entries.firstOrNull { cat ->
                cat.typeIdentifiers.any { it.equals(norm, ignoreCase = true) }
            } ?: ALL
        }

        /** URL webParam → kategori (ör. ?category=related_anime). */
        fun fromWebParam(param: String?): MalNotificationCategory? {
            if (param.isNullOrBlank()) return null
            val norm = param.trim().lowercase()
            return entries.firstOrNull { it.webParam.equals(norm, ignoreCase = true) }
        }
    }
}
