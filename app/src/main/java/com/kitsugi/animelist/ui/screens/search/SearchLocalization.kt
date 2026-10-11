package com.kitsugi.animelist.ui.screens.search

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R

/** Locale-aware labels for search enums. Keep enum values stable for search/API logic. */
@Composable
fun SearchSourceEngine.localizedLabel(): String = stringResource(
    when (this) {
        SearchSourceEngine.ALL -> R.string.search_source_all_sources
        SearchSourceEngine.ANILIST -> R.string.search_source_anilist
        SearchSourceEngine.MAL -> R.string.search_source_mal
        SearchSourceEngine.TMDB -> R.string.search_source_tmdb
        SearchSourceEngine.SHIKIMORI -> R.string.search_source_shikimori
        SearchSourceEngine.KITSU -> R.string.search_source_kitsu
        SearchSourceEngine.SIMKL -> R.string.search_source_simkl
        SearchSourceEngine.BANGUMI -> R.string.search_source_bangumi
    }
)

@Composable
fun SearchSourceEngine.localizedDisplayLabel(): String = "$emoji ${localizedLabel()}"

@Composable
fun SearchSourceEngine.localizedShortLabel(): String = when (this) {
    SearchSourceEngine.ALL -> "🌐 ${stringResource(R.string.search_source_all_sources)}"
    SearchSourceEngine.ANILIST -> "🅰️ AL"
    SearchSourceEngine.MAL -> "Ⓜ️ MAL"
    SearchSourceEngine.TMDB -> "🎬 TMDB"
    SearchSourceEngine.SHIKIMORI -> "🌸 SHI"
    SearchSourceEngine.KITSU -> "🦊 KTS"
    SearchSourceEngine.SIMKL -> "📺 SMK"
    SearchSourceEngine.BANGUMI -> "🎌 BGM"
}

@Composable
fun SearchScope.localizedLabel(): String = stringResource(
    when (this) {
        SearchScope.ALL_MIXED -> R.string.search_scope_all
        SearchScope.ANIME -> R.string.search_scope_anime
        SearchScope.MANGA -> R.string.search_scope_manga
        SearchScope.MANHWA -> R.string.search_scope_manhwa
        SearchScope.MANHUA -> R.string.search_scope_manhua
        SearchScope.LIGHT_NOVEL -> R.string.search_scope_light_novel
        SearchScope.TV -> R.string.search_scope_tv
        SearchScope.MOVIE -> R.string.search_scope_movies
        SearchScope.K_DRAMA -> R.string.search_scope_k_drama
        SearchScope.CHARACTER -> R.string.search_scope_characters
        SearchScope.STAFF -> R.string.search_scope_staff
        SearchScope.STUDIO -> R.string.search_scope_studios
    }
)

@Composable
fun SearchScope.localizedDisplayLabel(): String =
    if (emoji.isNotBlank()) "$emoji ${localizedLabel()}" else localizedLabel()

@Composable
fun SearchPlatform.localizedLabel(): String = stringResource(
    when (this) {
        SearchPlatform.All -> R.string.search_source_all_sources
        SearchPlatform.AniList -> R.string.search_source_anilist
        SearchPlatform.MAL -> R.string.search_source_mal
        SearchPlatform.TMDB -> R.string.search_source_tmdb
        SearchPlatform.Kitsu -> R.string.search_source_kitsu
        SearchPlatform.Shikimori -> R.string.search_source_shikimori
        SearchPlatform.Simkl -> R.string.search_source_simkl
        SearchPlatform.Bangumi -> R.string.search_source_bangumi
        SearchPlatform.CS3 -> R.string.search_source_extensions
    }
)
