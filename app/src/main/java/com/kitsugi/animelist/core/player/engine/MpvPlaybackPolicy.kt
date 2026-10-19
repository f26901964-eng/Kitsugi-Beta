package com.kitsugi.animelist.core.player.engine

/** Android/JNI-independent MPV option policies, shared by the engine and its tests. */
internal object MpvPlaybackPolicy {
    // Observe the leaves, not the parent map: MPV does not emit a string for a map.
    val scriptProperties = listOf(
        "show_text", "toggle_ui", "show_panel", "software_keyboard",
        "set_button_title", "reset_button_title", "toggle_button", "switch_episode",
        "pause", "seek_by", "seek_to", "seek_by_with_text", "seek_to_with_text",
        "launch_int_picker"
    ).map { "user-data/aniyomi/$it" }

    fun volume(volume: Float, boostCap: Int): Float =
        if (volume.isFinite()) volume.coerceIn(0f, boostCap.coerceIn(100, 200) / 100f) else 1f

    // A single chain preserves both filters; setting vf twice would drop gradfun.
    fun videoFilters(debandMode: String, forceYuv420p: Boolean): String =
        buildList {
            if (debandMode == "cpu") add("gradfun=radius=12")
            if (forceYuv420p) add("format=yuv420p")
        }.joinToString(",")

    fun isReady(fileLoaded: Boolean, pausedForCache: Boolean, coreIdle: Boolean,
                playing: Boolean, seeking: Boolean): Boolean =
        fileLoaded && !pausedForCache && !(coreIdle && playing) && !seeking
    /** mpv 0.38 added an index argument before per-file options. */
    fun loadfile(url: String, startOption: String?, version: String): Array<String> {
        if (startOption == null) return arrayOf("loadfile", url, "replace")
        val match = Regex("""(?:mpv\s+)?(\d+)\.(\d+)""").find(version)
        val major = match?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val minor = match?.groupValues?.get(2)?.toIntOrNull() ?: 0
        return if (major > 0 || minor >= 38) arrayOf("loadfile", url, "replace", "-1", startOption)
            else arrayOf("loadfile", url, "replace", startOption)
    }

}
