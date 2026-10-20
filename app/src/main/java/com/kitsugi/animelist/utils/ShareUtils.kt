package com.kitsugi.animelist.utils

import android.content.Context
import android.content.Intent
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaType

/**
 * Paylaşım için ince Android katmanı.
 *
 * Bağlantı üretiminin tamamı [ShareLinks] içindedir (kaynak → doğrulanmış şema).
 * Bu nesne yalnızca `Intent.ACTION_SEND` kurar. Bozuk/eski çağrılar için geriye dönük
 * uyumlu sarmalayıcılar burada tutulur.
 */
object ShareUtils {

    /** Başlık + (varsa) doğrulanmış bağlantı. Bağlantı yoksa yalnızca başlık paylaşılır. */
    fun shareText(context: Context, title: String, url: String?) {
        val safeUrl = url?.takeIf { ShareLinks.isTrustworthy(it) }
        val body = if (safeUrl.isNullOrBlank()) title else "$title\n$safeUrl"

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, body)
        }
        val chooser = Intent.createChooser(intent, "Paylaş: $title")
        context.startActivity(chooser)
    }

    /** `MediaEntry.malId` + `tmdbId`/`simklId` alanlarını birlikte değerlendirir. */
    fun buildMediaUrl(entry: MediaEntry): String? = ShareLinks.mediaUrl(entry)

    fun buildMediaUrl(source: String, mediaId: Int, mediaType: MediaType?): String? =
        ShareLinks.mediaUrl(source = source, storedId = mediaId, type = mediaType)

    fun buildExternalMediaUrl(source: String, id: Int, tmdbId: Int? = null, type: MediaType): String? =
        ShareLinks.mediaUrl(source = source, storedId = id, type = type, tmdbId = tmdbId)

    /** `JikanSearchResult` tabanlı detay sayfaları için kısa yol. */
    fun buildExternalMediaUrl(
        source: String,
        storedId: Int,
        tmdbId: Int?,
        realMalId: Int?,
        type: MediaType
    ): String? = ShareLinks.mediaUrl(
        source = source,
        storedId = storedId,
        type = type,
        tmdbId = tmdbId,
        realMalId = realMalId
    )

    fun buildProfileUrl(source: String, username: String): String =
        ShareLinks.profileUrl(source, username)
            ?: "https://myanimelist.net/profile/${username.trim()}"

    fun buildCharacterUrl(source: String, characterId: Int, name: String? = null): String? =
        ShareLinks.characterUrl(source, characterId, name)

    fun buildStaffUrl(source: String, staffId: Int, name: String? = null): String? =
        ShareLinks.staffUrl(source, staffId, name)

    fun buildStudioUrl(source: String, studioId: Int, name: String? = null): String? =
        ShareLinks.studioUrl(source, studioId, name)
}
