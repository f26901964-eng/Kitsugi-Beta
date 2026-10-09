package com.kitsugi.animelist.utils

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * "nextAiringEpisode" alanının TEK biçim/sözlük katmanı.
 *
 * Makine biçimi: `"episode|epochSeconds"` (AniList, TMDB, takvim, tüm kaynaklar)
 *  - `episode > 0`  → bölüm numarası biliniyor
 *  - `episode == 0` → Film (vizyon tarihi)
 *  - `episode <  0` → Dizi, bölüm numarası bilinmiyor (TMDB "upcoming" geleneği)
 *
 * Eski TMDB düz metni ("Bölüm 2, 2026-10-15 tarihinde yayında") önbelleklerden
 * gelebilir; [parse] bunu tarih/bölüm numarasına indirger, indirgenemezse
 * düz metin olarak korur.
 *
 * Görüntüleme kuralı (kaynak fark etmeksizin her yerde aynı):
 *  - Detay: `"Bölüm 2, 2026-10-15 tarihinde yayında (6 gün sonra)"`
 *  - Kart/çip: `"Bölüm 2 · 2026-10-15 · 6 gün sonra yayında"`
 */
object NextAiringFormat {

    /** Film (vizyon) işareti — [com.kitsugi.animelist.data.remote.AiringEntry] ile aynı konvansiyon. */
    const val MOVIE_EPISODE = 0

    /** Bölüm numarası bilinmeyen dizi işareti (TMDB upcoming). */
    const val UNKNOWN_EPISODE = -1

    data class Parsed(
        val episode: Int?,
        val epoch: Long?,
        /** Biçim indirgenemiyorsa gösterilecek ham metin. */
        val legacyText: String?
    ) {
        val isMachineFormat: Boolean get() = episode != null && epoch != null
    }

    /** `"episode|epoch"` ve eski TMDB düz metnini ortak yapıya çözer. */
    fun parse(raw: String?): Parsed {
        if (raw.isNullOrBlank()) return Parsed(null, null, null)
        if (raw.contains("|")) {
            val parts = raw.split("|")
            val ep = parts.getOrNull(0)?.trim()?.toIntOrNull()
            val epoch = parts.getOrNull(1)?.trim()?.toLongOrNull()
            if (ep != null && epoch != null) return Parsed(ep, epoch, null)
        }
        // Eski TMDB düz metni: "Bölüm 25, 2024-02-01 tarihinde yayında"
        val date = ISO_DATE_REGEX.find(raw)?.groupValues?.getOrNull(1)
        val ep = LEGACY_EPISODE_REGEX.find(raw)?.groupValues?.getOrNull(1)?.toIntOrNull()
        val epoch = date?.let { isoDateToEpoch(it) }
        return if (epoch != null) {
            Parsed(ep ?: UNKNOWN_EPISODE, epoch, null)
        } else {
            Parsed(null, null, raw)
        }
    }

    /** `"yyyy-MM-dd"` tarihini yerel gece yarısı epoch saniyeye çevirir.
     *  Yerel gece yarısı seçilir ki [formatDate] geri dönüşümü her zaman aynı
     *  takvim tarihini versin (TMDB `air_date` bir takvim tarihidir). */
    fun isoDateToEpoch(dateStr: String): Long? = runCatching {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        sdf.timeZone = TimeZone.getDefault()
        sdf.isLenient = false
        sdf.parse(dateStr)?.time?.div(1000L)
    }.getOrNull()

    /** Epoch'u `"yyyy-MM-dd"` (yerel saat dilimi) olarak gösterir. */
    fun formatDate(epoch: Long): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(epoch * 1000L))

    /**
     * Geri sayım metni: `"6 gün sonra"`, `"3 saat 20 dakika sonra"`,
     * `"12 dakika sonra"`, `"az sonra"`.
     */
    fun countdownText(remainingSec: Long): String {
        if (remainingSec <= 0) return "yayında"
        if (remainingSec < 60L) return "az sonra"
        if (remainingSec < 3600L) {
            val mins = (remainingSec / 60).toInt()
            return "$mins dakika sonra"
        }
        if (remainingSec < 86400L) {
            val hours = (remainingSec / 3600).toInt()
            val mins = ((remainingSec % 3600) / 60).toInt()
            return if (mins > 0) "$hours saat $mins dakika sonra" else "$hours saat sonra"
        }
        val days = remainingSec / 86400
        return if (days > 30) {
            val months = days / 30
            if (months > 12) "${days / 365} yıl sonra" else "$months ay sonra"
        } else if (days > 7) {
            val weeks = days / 7
            "$weeks hafta sonra"
        } else {
            "$days gün sonra"
        }
    }

    /** `"Bölüm 2"` / `"Film"` / `"Dizi"`. */
    fun episodeLabel(episode: Int?): String = when {
        episode == null -> "Dizi"
        episode == MOVIE_EPISODE -> "Film"
        episode < 0 -> "Dizi"
        else -> "Bölüm $episode"
    }

    private fun airingVerb(episode: Int?): String =
        if (episode == MOVIE_EPISODE) "vizyonda" else "yayında"

    private fun airedText(episode: Int?): String = when {
        episode == MOVIE_EPISODE -> "Film vizyona girdi!"
        episode == null || episode < 0 -> "Dizi yayında!"
        else -> "Bölüm $episode yayınlandı!"
    }

    private fun airedShortText(episode: Int?): String = when {
        episode == MOVIE_EPISODE -> "Film vizyonda"
        episode == null || episode < 0 -> "Dizi yayında"
        else -> "Bölüm $episode yayınlandı"
    }

    /**
     * Detay sayfası satırı — kullanıcının işaretlediği biçim:
     * `"Bölüm 2, 2026-10-15 tarihinde yayında (6 gün sonra)"`.
     * [nowEpoch] testler için verilebilir.
     */
    fun detailText(parsed: Parsed, nowEpoch: Long = System.currentTimeMillis() / 1000L): String {
        if (parsed.isMachineFormat) {
            val ep = parsed.episode!!
            val epoch = parsed.epoch!!
            val date = formatDate(epoch)
            val remaining = epoch - nowEpoch
            if (remaining <= 0) return airedText(ep)
            val label = episodeLabel(ep)
            val verb = airingVerb(ep)
            return "$label, $date tarihinde $verb (${countdownText(remaining)})"
        }
        return parsed.legacyText.orEmpty()
    }

    /**
     * Kart/çip satırı: `"Bölüm 2 · 2026-10-15 · 6 gün sonra yayında"`.
     * [nowEpoch] testler için verilebilir.
     */
    fun chipText(parsed: Parsed, nowEpoch: Long = System.currentTimeMillis() / 1000L): String {
        if (parsed.isMachineFormat) {
            val ep = parsed.episode!!
            val epoch = parsed.epoch!!
            val remaining = epoch - nowEpoch
            if (remaining <= 0) return airedShortText(ep)
            val label = episodeLabel(ep)
            val verb = if (ep == MOVIE_EPISODE) "vizyonda" else "yayında"
            return "$label · ${formatDate(epoch)} · ${countdownText(remaining)} $verb"
        }
        return parsed.legacyText.orEmpty()
    }

    /** String alan için kısayollar. */
    fun detailText(raw: String?, nowEpoch: Long = System.currentTimeMillis() / 1000L): String =
        detailText(parse(raw), nowEpoch)

    fun chipText(raw: String?, nowEpoch: Long = System.currentTimeMillis() / 1000L): String =
        chipText(parse(raw), nowEpoch)

    private val ISO_DATE_REGEX = Regex("(\\d{4}-\\d{2}-\\d{2})")
    private val LEGACY_EPISODE_REGEX = Regex("Bölüm\\s+(\\d+)")
}
