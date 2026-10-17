package com.kitsugi.animelist.ui.components

import android.content.Context
import coil3.SingletonImageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.size.Precision
import coil3.size.Scale
import com.kitsugi.animelist.data.remote.JikanSearchResult
import com.kitsugi.animelist.data.remote.KitsugiHeroArtworkResolver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap

/**
 * Admission gate, not a poster fallback. Native banners are tried first; MAL/Bangumi/etc.
 * without one can obtain a real backdrop from the shared TMDB/Fanart resolver. A small
 * decode verifies dimensions and warms the disk cache without keeping dozens of full-size
 * ALL-mode images in memory. The selected URL is then frozen for the lifetime of the page.
 */
internal class HomeHeroArtworkLoader(context: Context) {
    private val context = context.applicationContext
    private val imageLoader by lazy { SingletonImageLoader.get(this.context) }
    private val decodedBackdrops = ConcurrentHashMap<String, Boolean>()
    private val resolvedBackdrops = ConcurrentHashMap<String, String>() // empty = no usable artwork

    fun retryFailures() {
        resolvedBackdrops.entries.removeAll { it.value.isBlank() }
        decodedBackdrops.entries.removeAll { !it.value }
    }

    suspend fun prepare(item: JikanSearchResult): JikanSearchResult? {
        if (!isHomeHeroMedia(item)) return null
        val key = "${KitsugiHeroArtworkResolver.cacheKey(item)}:${item.imageUrl}:${item.backdropUrl}"
        resolvedBackdrops[key]?.let { cached ->
            return cached.takeIf { it.isNotBlank() }?.let { item.copy(backdropUrl = it) }
        }
        val tried = mutableSetOf<String>()
        suspend fun firstUsable(urls: List<String?>): String? {
            for (candidate in urls) {
                val url = candidate?.let { homeHeroBackdropUrl(item.copy(backdropUrl = it)) } ?: continue
                if (!tried.add(url)) continue
                if (verifyBackdrop(url)) return url
            }
            return null
        }

        val url = try {
            withTimeoutOrNull(18_000L) {
                val cached = KitsugiHeroArtworkResolver.getCached(item)
                firstUsable(listOf(item.backdropUrl, cached?.bestBackdropUrl) + cached?.backdropCandidates.orEmpty())
                    ?: KitsugiHeroArtworkResolver.resolveArtwork(item).let { artwork ->
                        firstUsable(listOf(artwork.bestBackdropUrl) + artwork.backdropCandidates)
                    }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
        resolvedBackdrops[key] = url.orEmpty()
        // Do not replace provider IDs or the library poster: only the verified backdrop changes.
        return url?.let { item.copy(backdropUrl = it) }
    }

    private suspend fun verifyBackdrop(url: String): Boolean {
        decodedBackdrops[url]?.let { return it }
        val valid = withTimeoutOrNull(5_000L) {
            val result = imageLoader.execute(
                ImageRequest.Builder(context)
                    .data(url)
                    .size(480, 270)
                    .scale(Scale.FIT)
                    .precision(Precision.INEXACT)
                    .memoryCachePolicy(CachePolicy.DISABLED)
                    .build()
            )
            result is SuccessResult && isHorizontalHomeHeroArtwork(result.image.width, result.image.height)
        } ?: false
        decodedBackdrops[url] = valid
        return valid
    }
}
