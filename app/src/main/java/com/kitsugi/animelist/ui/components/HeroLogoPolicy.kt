package com.kitsugi.animelist.ui.components

import com.kitsugi.animelist.data.remote.JikanSearchResult
import com.kitsugi.animelist.model.MediaType

/** Bangumi and Simkl showcase their artwork logo even when the optional global logo setting is off. */
internal fun shouldShowHeroLogo(item: JikanSearchResult, showAnimeLogos: Boolean): Boolean =
    item.type != MediaType.Manga &&
        (showAnimeLogos ||
            item.source.equals("bangumi", ignoreCase = true) ||
            item.source.equals("bgm", ignoreCase = true) ||
            item.source.equals("simkl", ignoreCase = true))
