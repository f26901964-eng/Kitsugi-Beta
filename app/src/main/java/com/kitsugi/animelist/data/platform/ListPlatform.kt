package com.kitsugi.animelist.data.platform

import androidx.annotation.StringRes
import com.kitsugi.animelist.R
import java.util.Locale

/**
 * Kitsugi'nin desteklediği **liste kaynakları** (platform entegrasyonları).
 *
 * Bu enum, eşitleme günlüklerinde ve hata raporlarında geçen görünen adları
 * (`"AniList"`, `"MyAnimeList"`, `"Eşitleme"` …) sabit bir kaynağa bağlar; böylece
 * bir hata mesajının hangi platformdan geldiği tahmine bırakılmaz.
 *
 * Eşleştirme **kelime bazlıdır**: `"Analiz"` gibi bir etiket, içinde "al" geçtiği
 * için yanlışlıkla AniList'e bağlanmaz.
 *
 * @property labelRes   Kullanıcıya gösterilen ad (dil dosyasından).
 * @property codePrefix [PlatformErrorCode] içindeki kod ön eki (`KTS-MAL-3001` → `MAL`).
 * @property aliases    Günlüklerde karşılaşılabilecek ad yazımları (küçük harf).
 */
enum class ListPlatform(
    @StringRes val labelRes: Int,
    val codePrefix: String,
    private val aliases: List<String>,
) {
    /** Belirli bir platforma ait olmayan, tüm liste kaynaklarında geçerli ortak kodlar. */
    GENERIC(
        R.string.pf_err_platform_generic,
        "COM",
        listOf("genel", "eşitleme", "analiz", "tamamlandı", "general", "sync", "common", "platform"),
    ),
    ANILIST(
        R.string.pf_err_platform_anilist,
        "ANL",
        listOf("anilist", "ani list", "anilist api"),
    ),
    MAL(
        R.string.pf_err_platform_mal,
        "MAL",
        listOf("myanimelist", "my anime list", "mal", "mal api", "jikan"),
    ),
    KITSU(
        R.string.pf_err_platform_kitsu,
        "KIT",
        listOf("kitsu", "kitsu.io", "kitsu.app"),
    ),
    SHIKIMORI(
        R.string.pf_err_platform_shikimori,
        "SHK",
        listOf("shikimori", "shiki"),
    ),
    BANGUMI(
        R.string.pf_err_platform_bangumi,
        "BGM",
        listOf("bangumi", "bgm.tv", "bgm"),
    ),
    TMDB(
        R.string.pf_err_platform_tmdb,
        "TMD",
        listOf("tmdb", "themoviedb", "the movie db"),
    ),
    SIMKL(
        R.string.pf_err_platform_simkl,
        "SMK",
        listOf("simkl"),
    );

    /** Günlükteki görünen ad bu platforma mı ait? */
    fun matches(label: String?): Boolean {
        val needle = label?.trim()?.lowercase(Locale.ROOT) ?: return false
        if (needle.isEmpty()) return false
        return aliases.any { alias -> aliasHit(needle, alias) }
    }

    companion object {
        private val WORD_SPLIT = charArrayOf(' ', '(', ')', '·', '-', '_', '.', ':', '/', '|', '[')

        /** Kelime sınırında tam eşleşme; çok kelimeli takma adlar için alt dizi kontrolü. */
        private fun aliasHit(needle: String, alias: String): Boolean {
            if (needle == alias) return true
            if (needle.split(*WORD_SPLIT).any { word -> word == alias }) return true
            // "ani list" gibi boşluk içeren, uzun takma adlar için gevşek kontrol.
            return alias.contains(' ') && alias.length >= 5 && needle.contains(alias)
        }

        /** Günlük etiketinden platformu bulur; tanınmıyorsa [GENERIC] döner. */
        fun fromLabel(label: String?): ListPlatform =
            entries.firstOrNull { it != GENERIC && it.matches(label) } ?: GENERIC

        /** Metnin içinde geçen platform adını tahmin eder (ör. serbest hata mesajları). */
        fun detect(text: String?): ListPlatform {
            val haystack = text?.lowercase(Locale.ROOT) ?: return GENERIC
            if (haystack.isBlank()) return GENERIC
            return entries.firstOrNull { platform ->
                platform != GENERIC && platform.matches(haystack)
            } ?: GENERIC
        }

        /** `KTS-MAL-3001` → [MAL]. */
        fun fromErrorCode(code: String?): ListPlatform {
            val prefix = code?.trim()?.uppercase(Locale.ROOT)
                ?.split('-')
                ?.getOrNull(1)
                ?: return GENERIC
            return entries.firstOrNull { it.codePrefix == prefix } ?: GENERIC
        }
    }
}
