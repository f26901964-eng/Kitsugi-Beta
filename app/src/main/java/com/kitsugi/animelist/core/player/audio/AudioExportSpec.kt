package com.kitsugi.animelist.core.player.audio

import java.util.Locale

/** Identifies a player row without assuming its UI index (or mpv aid) is a demuxer stream index. */
internal data class AudioTrackIdentity(
    val language: String?,
    val name: String?,
    val externalUrl: String? = null
)

/** A physical stream is present, but it does not uniquely represent the chosen player row. */
internal class AudioTrackSelectionException(message: String) : IllegalArgumentException(message)

internal object AudioExportSpec {
    fun language(raw: String?): String {
        val code = raw?.trim()?.lowercase(Locale.ROOT)?.replace('_', '-')?.substringBefore('-').orEmpty()
        return when (code) {
            "und", "unknown", "", "mul", "zxx" -> ""
            "tur" -> "tr"; "eng" -> "en"; "jpn" -> "ja"; "kor" -> "ko"
            "deu", "ger" -> "de"; "fra", "fre" -> "fr"; "spa" -> "es"
            "ita" -> "it"; "rus" -> "ru"; "zho", "chi" -> "zh"
            "ara" -> "ar"; "por" -> "pt"; "hin" -> "hi"
            else -> code.takeIf { it.matches(Regex("[a-z]{2,3}")) }.orEmpty()
        }
    }

    /** Prefer an explicit player filename; otherwise a sole track may use the stream's separate audio URL. */
    fun source(videoUrl: String?, separateAudioUrl: String?, selected: AudioTrackIdentity,
               playerTracks: List<AudioTrackIdentity>): String {
        val external = selected.externalUrl?.takeIf { it.isNotBlank() }
        if (external != null) return external
        if (!separateAudioUrl.isNullOrBlank()) {
            require(playerTracks.size == 1) {
                "Ayrı ses ile gömülü sesler arasında seçilen parça ayırt edilemiyor"
            }
            return separateAudioUrl
        }
        return videoUrl?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("Ses kaynağı bulunamadı")
    }

    /** A name can distinguish equal-language HLS/DASH renditions, but not progressive demux tracks. */
    fun needsManifestName(selected: AudioTrackIdentity, playerTracks: List<AudioTrackIdentity>): Boolean {
        // A distinct external URL identifies its own file. Two player rows sharing the
        // SAME external playlist do not: their language/name still has to be matched.
        val sameFile = playerTracks.filter { it.externalUrl == selected.externalUrl }
        val lang = language(selected.language)
        return sameFile.size > 1 &&
            (lang.isEmpty() || sameFile.count { language(it.language) == lang } > 1)
    }

    data class Candidate(val index: Int, val language: String)

    /** No guessing from mpv aid / UI index when multiple physical streams can match. */
    fun select(candidates: List<Candidate>, wantedLanguage: String?, requireKnownLanguage: Boolean = false): Int {
        require(candidates.isNotEmpty()) { "Kaynakta ses parçası bulunamadı" }
        val wanted = language(wantedLanguage)
        if (candidates.size == 1) {
            val actual = language(candidates.single().language)
            if (requireKnownLanguage && actual.isEmpty()) {
                throw AudioTrackSelectionException("Birden çok oynatıcı sesi var, kaynak dilini doğrulayamıyor")
            }
            if (wanted.isNotEmpty() && actual.isNotEmpty() && wanted != actual) {
                throw AudioTrackSelectionException("Seçilen ses dili kaynakta bulunamadı")
            }
            return candidates.single().index
        }
        if (wanted.isEmpty()) throw AudioTrackSelectionException(
            "Ses dili belirtilmemiş: birden çok parça arasından seçim yapılamıyor"
        )
        val matches = candidates.filter { language(it.language) == wanted }
        if (matches.isEmpty()) throw AudioTrackSelectionException("Seçilen ses dili kaynakta bulunamadı")
        if (matches.size > 1) throw AudioTrackSelectionException("Aynı dilde birden çok ses var; yanlış parça indirilmedi")
        return matches.single().index
    }

    fun filenamePart(value: String, fallback: String): String = value
        .replace(Regex("[\\p{Cntrl}/\\\\:*?\"<>|]"), "_")
        .trim(' ', '.')
        .take(90)
        .ifBlank { fallback }
}
