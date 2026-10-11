package com.kitsugi.animelist.data.settings

import com.kitsugi.animelist.utils.KitsugiStrings
import com.kitsugi.animelist.R

/**
 * V2-F02 – AniListUserOptions
 *
 * AniList kullanıcısının hesap genelindeki tercihlerini tutar.
 * AniHyou UserOptions referans alındı.
 *
 * Alanlar:
 *  - adult:              Yetişkin içerikleri göster/gizle
 *  - airingNotifications: Takip edilen animelerde yayın bildirimi
 *  - notificationOptions: Hangi bildirim tiplerinin aktif olduğu
 *  - timezone:           IANA timezone string (örn. "Europe/Istanbul")
 *  - activityMergeTime:  Aynı medya aktivitelerini kaç dakika içinde birleştir
 *  - staffNameLanguage:  Seiyuu/yönetmen isim dili (ROMAJI / NATIVE)
 *  - restrictMessagesToFollowing: Sadece takip edilenlerden mesaj al
 */
data class AniListUserOptions(
    val adult: Boolean = false,
    val airingNotifications: Boolean = true,
    val notificationOptions: List<AniListNotificationOption> = AniListNotificationOption.defaults(),
    val timezone: String = "Europe/Istanbul",
    val activityMergeTime: Int = 30,           // dakika; 0 = hiç birleştirme
    val staffNameLanguage: StaffNameLanguage = StaffNameLanguage.ROMAJI,
    val restrictMessagesToFollowing: Boolean = false
)

/**
 * Hangi AniList bildirim kategorisinin aktif olduğunu belirtir.
 */
data class AniListNotificationOption(
    val type: AniListNotificationCategory,
    val enabled: Boolean
) {
    companion object {
        fun defaults(): List<AniListNotificationOption> = AniListNotificationCategory.entries.map {
            AniListNotificationOption(type = it, enabled = it.defaultEnabled)
        }
    }
}

/**
 * AniList bildirim kategorileri.
 *
 * API'deki `notificationTypeSettings` alanına karşılık gelir.
 */
enum class AniListNotificationCategory(
    val apiValue: String,
    val label: String,
    val defaultEnabled: Boolean
) {
    AIRING(
        apiValue = "AIRING",
        label = KitsugiStrings.get(R.string.settings_airing_notifications),
        defaultEnabled = true
    ),
    ACTIVITY(
        apiValue = "ACTIVITY_MESSAGE",
        label = KitsugiStrings.get(R.string.aktivite_mesajlari),
        defaultEnabled = true
    ),
    ACTIVITY_REPLY(
        apiValue = "ACTIVITY_REPLY",
        label = KitsugiStrings.get(R.string.aktivite_yanitlari),
        defaultEnabled = true
    ),
    ACTIVITY_MENTION(
        apiValue = "ACTIVITY_MENTION",
        label = "Etiketlemeler",
        defaultEnabled = true
    ),
    FOLLOWING(
        apiValue = "FOLLOWING",
        label = KitsugiStrings.get(R.string.yeni_takipciler),
        defaultEnabled = true
    ),
    FORUM_COMMENT(
        apiValue = "THREAD_COMMENT_MENTION",
        label = KitsugiStrings.get(R.string.forum_yorumlari),
        defaultEnabled = false
    ),
    FORUM_SUBSCRIBED(
        apiValue = "THREAD_SUBSCRIBED",
        label = KitsugiStrings.get(R.string.abone_forum_guncellemeleri),
        defaultEnabled = false
    ),
    MEDIA_CHANGE(
        apiValue = "MEDIA_DATA_CHANGE",
        label = KitsugiStrings.get(R.string.medya_verisi_degisiklikleri),
        defaultEnabled = false
    );

    companion object {
        fun fromApiValue(value: String): AniListNotificationCategory? =
            entries.find { it.apiValue == value }
    }
}

/**
 * Seiyuu ve yönetmen isimlerinin hangi dilde gösterileceğini belirler.
 */
enum class StaffNameLanguage(val apiValue: String, val label: String) {
    ROMAJI("ROMAJI", "Romaji"),
    NATIVE("NATIVE", "Yerel (Japonca vb.)"),
    ROMAJI_WESTERN("ROMAJI_WESTERN", "Romaji (Batı sırası)");

    companion object {
        fun fromApiValue(value: String) =
            entries.find { it.apiValue == value } ?: ROMAJI
    }
}
