package com.kitsugi.animelist.ui.screens.search.composables

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R

// ─── MediaFormat ─────────────────────────────────────────────────────────────

enum class KitsugiMediaFormat(val apiValue: String) : KitsugiLocalizable {
    TV("TV"),
    TV_SHORT("TV_SHORT"),
    MOVIE("MOVIE"),
    SPECIAL("SPECIAL"),
    OVA("OVA"),
    ONA("ONA"),
    MUSIC("MUSIC"),
    MANGA("MANGA"),
    NOVEL("NOVEL"),
    ONE_SHOT("ONE_SHOT");

    @Composable
    override fun localized(): String = when (this) {
        TV -> "TV"
        TV_SHORT -> stringResource(R.string.search_format_tv_short)
        MOVIE -> stringResource(R.string.search_format_movie)
        SPECIAL -> stringResource(R.string.search_format_special)
        OVA -> "OVA"
        ONA -> "ONA"
        MUSIC -> stringResource(R.string.search_format_music)
        MANGA -> "Manga"
        NOVEL -> stringResource(R.string.search_format_novel)
        ONE_SHOT -> stringResource(R.string.search_format_one_shot)
    }

    companion object {
        val animeEntries = listOf(TV, TV_SHORT, MOVIE, SPECIAL, OVA, ONA, MUSIC)
        val mangaEntries = listOf(MANGA, NOVEL, ONE_SHOT)
    }
}

// ─── MediaStatus ─────────────────────────────────────────────────────────────

enum class KitsugiMediaStatus(val apiValue: String) : KitsugiLocalizable {
    FINISHED("FINISHED"),
    RELEASING("RELEASING"),
    NOT_YET_RELEASED("NOT_YET_RELEASED"),
    CANCELLED("CANCELLED"),
    HIATUS("HIATUS");

    @Composable
    override fun localized(): String = when (this) {
        FINISHED -> stringResource(R.string.search_status_finished)
        RELEASING -> stringResource(R.string.search_status_releasing)
        NOT_YET_RELEASED -> stringResource(R.string.search_status_not_released)
        CANCELLED -> stringResource(R.string.search_status_cancelled)
        HIATUS -> stringResource(R.string.search_status_hiatus)
    }
}

// ─── CountryOfOrigin ─────────────────────────────────────────────────────────

enum class KitsugiCountryOfOrigin(val code: String) : KitsugiLocalizable {
    JAPAN("JP"),
    SOUTH_KOREA("KR"),
    CHINA("CN"),
    TAIWAN("TW");

    @Composable
    override fun localized(): String = when (this) {
        JAPAN -> stringResource(R.string.search_country_japan)
        SOUTH_KOREA -> stringResource(R.string.search_country_south_korea)
        CHINA -> stringResource(R.string.search_country_china)
        TAIWAN -> stringResource(R.string.search_country_taiwan)
    }
}

// ─── MediaSource ─────────────────────────────────────────────────────────────

enum class KitsugiMediaSource(val apiValue: String) : KitsugiLocalizable {
    ORIGINAL("ORIGINAL"),
    MANGA("MANGA"),
    LIGHT_NOVEL("LIGHT_NOVEL"),
    VISUAL_NOVEL("VISUAL_NOVEL"),
    VIDEO_GAME("VIDEO_GAME"),
    OTHER("OTHER"),
    NOVEL("NOVEL"),
    DOUJINSHI("DOUJINSHI"),
    ANIME("ANIME"),
    WEB_NOVEL("WEB_NOVEL"),
    LIVE_ACTION("LIVE_ACTION"),
    GAME("GAME"),
    COMIC("COMIC"),
    MULTIMEDIA_PROJECT("MULTIMEDIA_PROJECT"),
    PICTURE_BOOK("PICTURE_BOOK");

    @Composable
    override fun localized(): String = when (this) {
        ORIGINAL -> stringResource(R.string.search_source_original)
        MANGA -> "Manga"
        LIGHT_NOVEL -> stringResource(R.string.search_source_light_novel)
        VISUAL_NOVEL -> stringResource(R.string.search_source_visual_novel)
        VIDEO_GAME -> stringResource(R.string.search_source_video_game)
        OTHER -> stringResource(R.string.search_source_other)
        NOVEL -> stringResource(R.string.search_source_novel)
        DOUJINSHI -> "Doujinshi"
        ANIME -> "Anime"
        WEB_NOVEL -> stringResource(R.string.search_source_web_novel)
        LIVE_ACTION -> stringResource(R.string.search_source_live_action)
        GAME -> stringResource(R.string.search_source_game)
        COMIC -> stringResource(R.string.search_source_comic)
        MULTIMEDIA_PROJECT -> stringResource(R.string.search_source_multimedia)
        PICTURE_BOOK -> stringResource(R.string.search_source_picture_book)
    }
}

// ─── MediaSort ───────────────────────────────────────────────────────────────

enum class KitsugiMediaSortSearch(val ascApiValue: String, val descApiValue: String) : KitsugiLocalizable {
    SEARCH_MATCH("SEARCH_MATCH", "SEARCH_MATCH"),
    POPULARITY("POPULARITY", "POPULARITY_DESC"),
    SCORE("SCORE", "SCORE_DESC"),
    TRENDING("TRENDING", "TRENDING_DESC"),
    FAVOURITES("FAVOURITES", "FAVOURITES_DESC"),
    START_DATE("START_DATE", "START_DATE_DESC"),
    END_DATE("END_DATE", "END_DATE_DESC");

    @Composable
    override fun localized(): String = when (this) {
        SEARCH_MATCH -> stringResource(R.string.search_sort_default)
        POPULARITY -> stringResource(R.string.search_sort_popularity)
        SCORE -> "Puan"
        TRENDING -> stringResource(R.string.search_sort_trending)
        FAVOURITES -> stringResource(R.string.search_sort_favourites)
        START_DATE -> stringResource(R.string.search_sort_start_date)
        END_DATE -> stringResource(R.string.search_sort_end_date)
    }

    companion object {
        fun fromApiValue(apiValue: String): KitsugiMediaSortSearch? =
            entries.find { it.ascApiValue == apiValue || it.descApiValue == apiValue }
    }
}

// ─── MediaSeason ─────────────────────────────────────────────────────────────

enum class KitsugiMediaSeason(val apiValue: String) : KitsugiLocalizable {
    WINTER("WINTER"),
    SPRING("SPRING"),
    SUMMER("SUMMER"),
    FALL("FALL");

    @Composable
    override fun localized(): String = when (this) {
        WINTER -> stringResource(R.string.search_season_winter)
        SPRING -> stringResource(R.string.search_season_spring)
        SUMMER -> stringResource(R.string.search_season_summer)
        FALL -> stringResource(R.string.search_season_fall)
    }
}
