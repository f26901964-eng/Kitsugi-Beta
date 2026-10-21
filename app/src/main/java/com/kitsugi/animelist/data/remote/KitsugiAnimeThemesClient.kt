package com.kitsugi.animelist.data.remote

import org.json.JSONObject
import java.net.URL

/**
 * AnimeThemes.moe API'sinden açılış/kapanış müziklerini çeker.
 * [KitsugiMalDetailClient] ve [KitsugiAniListDetailClient] tarafından paylaşılır.
 */
internal object KitsugiAnimeThemesClient {

    suspend fun fetchAnimeThemes(externalId: Int, site: String): Pair<List<KitsugiTheme>, List<KitsugiTheme>> {
        val urlString = "https://api.animethemes.moe/anime?" +
            "filter[has]=resources&filter[site]=$site&filter[external_id]=$externalId" +
            "&include=animethemes.song.artists,animethemes.animethemeentries.videos"
        val url = runCatching { URL(urlString) }.getOrNull() ?: return Pair(emptyList(), emptyList())

        val response = KitsugiApiBase.executeGetRequest(url) ?: return Pair(emptyList(), emptyList())

        return try {
            val root = JSONObject(response)
            val animeArr = root.optJSONArray("anime") ?: return Pair(emptyList(), emptyList())
            if (animeArr.length() == 0) return Pair(emptyList(), emptyList())

            val animeObj = animeArr.getJSONObject(0)
            val themesArr = animeObj.optJSONArray("animethemes") ?: return Pair(emptyList(), emptyList())

            val openings = mutableListOf<KitsugiTheme>()
            val endings = mutableListOf<KitsugiTheme>()

            for (i in 0 until themesArr.length()) {
                val themeObj = themesArr.getJSONObject(i)
                val type = themeObj.optString("type")
                val slug = themeObj.optNullableString("slug") ?: ""

                val songObj = themeObj.optJSONObject("song")
                val title = songObj?.optNullableString("title") ?: continue

                val artists = mutableListOf<String>()
                songObj.optJSONArray("artists")?.let { artistsArr ->
                    for (j in 0 until artistsArr.length()) {
                        val artistObj = artistsArr.getJSONObject(j)
                        val name = artistObj.optString("name")
                        if (name.isNotBlank()) artists.add(name)
                    }
                }

                // En iyi video URL'ini çek.
                //
                // ÖNEMLİ: Eski kod yalnızca `animethemeentries[0]` içine bakıyordu. Bir
                // temanın birden çok kaydı olabiliyor (TV sürümü, BD sürümü, özel bölüm
                // aralıkları) ve ilk kaydın `videos` dizisi çoğu zaman BOŞ oluyor. Bu
                // yüzden satır "Oynat" yerine "YouTube'da ara" olarak düşüyor, bazı
                // yapımlarda ise yalnızca ağır/uyumsuz bir dosya seçiliyordu. Artık tüm
                // kayıtlar ve tüm videolar taranıp en uygunu seçiliyor.
                val candidates = mutableListOf<ThemeVideoCandidate>()
                themeObj.optJSONArray("animethemeentries")?.let { entriesArr ->
                    for (e in 0 until entriesArr.length()) {
                        val videos = entriesArr.getJSONObject(e).optJSONArray("videos") ?: continue
                        for (v in 0 until videos.length()) {
                            val vid = videos.getJSONObject(v)
                            val link = vid.optNullableString("link")?.takeIf { it.isNotBlank() } ?: continue
                            candidates.add(
                                ThemeVideoCandidate(
                                    link = link,
                                    resolution = vid.optInt("resolution", 0),
                                    isSubbed = vid.optBoolean("subbed", false),
                                    isNc = vid.optBoolean("nc", false),
                                    source = vid.optString("source")
                                )
                            )
                        }
                    }
                }
                val videoUrl = pickBestThemeVideoLink(candidates)

                val label = buildString {
                    append("\"$title\"")
                    if (artists.isNotEmpty()) {
                        append(" by ")
                        append(artists.joinToString(", "))
                    }
                    if (slug.isNotBlank()) append(" ($slug)")
                }

                val theme = KitsugiTheme(label = label, videoUrl = videoUrl)
                if (type == "OP") openings.add(theme)
                else if (type == "ED") endings.add(theme)
            }

            Pair(openings, endings)
        } catch (e: Exception) {
            Pair(emptyList(), emptyList())
        }
    }

    /**
     * AnimeThemes video adayları arasından oynatmaya en uygun olanı seçer.
     *
     * Sıralama ölçütleri (en önemliden):
     * 1. Altyazısı gömülü olmayan (temiz) dosya,
     * 2. MP4/H.264 (Android'de her cihazda donanım çözülür — küçük oynatıcıda takılmaz),
     * 3. Creditsiz (`nc`) sürüm,
     * 4. WEB kaynağı (BD/DVD dosyalarına göre çok daha küçük ve akıcı),
     * 5. 1080p'yi aşmayan en yüksek çözünürlük.
     *
     * AnimeThemes ağırlıklı olarak VP9 `.webm` dağıtır; cihazında VP9 çözücüsü olmayan
     * kullanıcılar için Media3 sessizce ses-only oynatıyordu. Bu yüzden H.264 varsa her
     * zaman ona öncelik verilir.
     */
    internal fun pickBestThemeVideoLink(candidates: List<ThemeVideoCandidate>): String? =
        candidates
            .filter { it.link.isNotBlank() }
            .minWithOrNull(
                compareBy<ThemeVideoCandidate>(
                    { it.isSubbed },                                    // false (temiz) önce
                    { if (it.link.isMp4Link()) 0 else 1 },              // MP4/H.264 önce
                    { if (it.isNc) 0 else 1 },                          // creditsiz önce
                    { if (it.source.equals("WEB", ignoreCase = true)) 0 else 1 },
                    { if (it.resolution in 1..1080) 0 else 1 },          // 2K/4K dosyaları sona at
                    { -it.resolution.coerceAtMost(1080) }               // en yüksek çözünürlük
                )
            )
            ?.link

    /**
     * AniList streaming episode başlıklarından bölüm numarasını çıkarmaya çalışır.
     * Örnek: "Episode 5", "Ep. 12", "#7 – Title", "S1E4", "Bölüm 3"
     */
    fun parseEpisodeNumberFromTitle(title: String): Int? {
        val patterns = listOf(
            Regex("""[Ss]\d+[Ee](\d+)"""),                                    // S1E5
            Regex("""[Ee]p(?:isode)?\.?\s*(\d+)""", RegexOption.IGNORE_CASE), // Episode 5, Ep. 5
            Regex("""#(\d+)"""),                                               // #7 – Title
            Regex("""B[öo]l[üu]m\s+(\d+)""", RegexOption.IGNORE_CASE),        // Bölüm 3
            Regex("""^(\d+)\s*[-–—.]"""),                                      // "5 - Title"
            Regex("""\b(\d+)\b""")                                             // son çare: herhangi sayı
        )
        for (pattern in patterns) {
            val num = pattern.find(title)?.groupValues?.getOrNull(1)?.toIntOrNull()
            if (num != null && num > 0 && num < 5000) return num
        }
        return null
    }
}

/** AnimeThemes'in video kaydı — seçim mantığını test edilebilir kılmak için ayrı tip. */
internal data class ThemeVideoCandidate(
    val link: String,
    val resolution: Int,
    val isSubbed: Boolean,
    val isNc: Boolean,
    val source: String
)

/** URL uzantısına bakmadan sorgu parametrelerini de tolere eden MP4/H.264 kontrolü. */
internal fun String.isMp4Link(): Boolean {
    val path = substringBefore('?').substringBefore('#')
    return path.endsWith(".mp4", ignoreCase = true) || path.endsWith(".m4v", ignoreCase = true)
}
