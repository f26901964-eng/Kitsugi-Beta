package com.kitsugi.animelist.core.player

/** Subtitle file to pass to a built-in or external player. */
data class SubtitleInput(
    val url: String,
    val name: String,
    val lang: String,
    /**
     * Whether this subtitle was provided by an external addon/plugin (true) or
     * embedded directly in the media stream / source (false, default).
     * Used by player engines to prioritize internal subtitles over addon-supplied ones.
     */
    val isExternal: Boolean = false
) : java.io.Serializable

