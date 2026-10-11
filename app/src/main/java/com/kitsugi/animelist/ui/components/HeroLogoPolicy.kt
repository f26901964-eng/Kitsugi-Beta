package com.kitsugi.animelist.ui.components

import com.kitsugi.animelist.data.remote.JikanSearchResult
import com.kitsugi.animelist.model.MediaType

/**
 * Bangumi, Simkl and Shikimori always try their source-resolved artwork logo in the showcase.
 * Other anime sources follow the optional global logo setting; manga never requests anime logos.
 */
internal fun shouldShowHeroLogo(item: JikanSearchResult, showAnimeLogos: Boolean): Boolean =
    item.type != MediaType.Manga &&
        (showAnimeLogos ||
            item.source.equals("bangumi", ignoreCase = true) ||
            item.source.equals("bgm", ignoreCase = true) ||
            item.source.equals("simkl", ignoreCase = true) ||
            item.source.equals("shikimori", ignoreCase = true) ||
            item.source.equals("shiki", ignoreCase = true))
