package com.kitsugi.animelist.data.remote

import android.util.Log
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.utils.KitsugiSpoilerRules
import com.kitsugi.animelist.utils.toLatinStudioName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

/**
 * **Detay sayfası tür / etiket / kurum toplayıcısı.**
 *
 * Açılan sayfanın kaynağı ne olursa olsun (AniList, MAL/Jikan, Bangumi, Shikimori, Kitsu,
 * Simkl, TMDB), aynı yapımın **diğer kaynaklardaki** tür, etiket, stüdyo, yapımcı, kanal ve
 * dergi verisi burada toplanır; [KitsugiTaxonomyMerge] ile tek listede birleştirilir.
 *
 * Tasarım kuralları:
 *  • **Ek katmandır, davranış bozmaz.** Her istek `runCatching` + zaman tavanı içindedir;
 *    geciken kaynak atlanır, sayfa asıl veriyle açılır.
 *  • **Paralel.** Kaynaklar `async` ile aynı anda başlar; toplam süre en yavaş kaynak değil
 *    [TOTAL_TIMEOUT_MS] tavanıdır.
 *  • **Önbellekli.** Jikan tarafı [JikanGateway] önbelleğini, AniList tarafı
 *    `KitsugiAniListDetailClient` bellekteki etiket önbelleğini kullanır; ikinci açılışta
 *    ağa çıkılmaz.
 *  • **Kimlik güvenliği.** MAL/Jikan yalnızca 1..99.999.999 aralığındaki gerçek MAL
 *    kimlikleriyle çağrılır. Bangumi'nin 500M+ "stable id"leri ve Shikimori/Kitsu kendi
 *    kimlik uzayları buraya **girmez** (yanlış yapımın türlerinin yapışmasını engeller).
 *  • **Kiril/Çince sızıntısı yok.** Kurum adları Latin ada çevrilir; tür/etiketler
 *    gösterim katmanında çözülür.
 *
 * Kapsam notu: Simkl ve Kitsu detay uçları **kendi kimliklerini** ister; MAL/AniList
 * kimliğinden geri çözüm ucuz değildir. Bu kaynaklar **açılan sayfanın kaynağı olduklarında**
 * kendi verilerini zaten sağlar; burada ek olarak AniList + MAL/Jikan birleşimi toplanır.
 */
internal object KitsugiTaxonomySources {

    private const val TAG = "KitsugiTaxonomySources"

    /** Ağ katmanı için makul toplam tavan — detay sayfasının 20 sn'lik zenginleştirme bütçesinin içinde. */
    private const val TOTAL_TIMEOUT_MS = 6_000L

    /** Tek kaynak için üst sınır. */
    private const val SOURCE_TIMEOUT_MS = 4_500L

    private val REAL_MAL_ID = 1..99_999_999

    /**
     * Tek bir kaynaktan toplanan taksonomi parçası. Boş alanlar `emptyList()`'tir; birleştirme
     * sırasında yalnızca dolu alanlar kullanılır.
     */
    data class Slice(
        val genres: List<String> = emptyList(),
        val tags: List<KitsugiTag> = emptyList(),
        val studios: List<KitsugiStudio> = emptyList(),
        val producers: List<KitsugiStudio> = emptyList(),
        val networks: List<KitsugiStudio> = emptyList(),
        val serializations: List<KitsugiStudio> = emptyList(),
        val demographics: List<String> = emptyList(),
        val themes: List<String> = emptyList()
    ) {
        val isEmpty: Boolean
            get() = genres.isEmpty() && tags.isEmpty() && studios.isEmpty() && producers.isEmpty() &&
                networks.isEmpty() && serializations.isEmpty() &&
                demographics.isEmpty() && themes.isEmpty()

        /** Parçayı birleştirme motorunun beklediği [KitsugiMediaDetail] biçimine çevirir. */
        fun toDetail(): KitsugiMediaDetail = KitsugiMediaDetail(
            synopsis = null,
            genres = genres,
            tags = tags,
            studios = studios,
            producers = producers,
            networks = networks,
            serializations = serializations,
            demographics = demographics,
            themes = themes
        )
    }

    /** İki parçayı birleştirir (aynı kaynak içinde birden çok bölüm geldiyse). */
    fun Slice.plus(other: Slice): Slice = Slice(
        genres = (genres + other.genres).distinct(),
        tags = tags + other.tags,
        studios = studios + other.studios,
        producers = producers + other.producers,
        networks = networks + other.networks,
        serializations = serializations + other.serializations,
        demographics = (demographics + other.demographics).distinct(),
        themes = (themes + other.themes).distinct()
    )

    /**
     * Tüm ikincil kaynaklardan taksonomi parçası toplar.
     *
     * @param malId gerçek MAL kimliği (1..99.999.999) — değilse MAL/Jikan atlanır.
     * @param aniListId AniList kimliği (100M+ "stable" biçimi de kabul edilir).
     * @param mediaType tür; Manga için MAL ucu `manga` olur.
     */
    suspend fun collect(
        malId: Int? = null,
        aniListId: Int? = null,
        mediaType: MediaType = MediaType.Anime
    ): Slice {
        val safeMalId = malId?.takeIf { it in REAL_MAL_ID }
        val safeAniListId = aniListId?.takeIf { it > 0 }
        if (safeMalId == null && safeAniListId == null) return Slice()

        val slice = withTimeoutOrNull(TOTAL_TIMEOUT_MS) {
            coroutineScope {
                val jikan = async(Dispatchers.IO) {
                    withTimeoutOrNull(SOURCE_TIMEOUT_MS) {
                        runCatching { fetchJikan(safeMalId, mediaType) }.getOrNull()
                    }
                }
                val aniList = async(Dispatchers.IO) {
                    withTimeoutOrNull(SOURCE_TIMEOUT_MS) {
                        runCatching {
                            KitsugiAniListDetailClient.fetchTagsAndAiringInfo(
                                aniListId = safeAniListId,
                                malId = safeMalId,
                                mediaType = mediaType
                            )?.let { info ->
                                Slice(
                                    genres = info.genres,
                                    tags = info.tags,
                                    studios = info.studios
                                )
                            }
                        }.getOrNull()
                    }
                }
                val jikanSlice = jikan.await()
                val aniListSlice = aniList.await()
                when {
                    jikanSlice == null -> aniListSlice ?: Slice()
                    aniListSlice == null -> jikanSlice
                    else -> jikanSlice.plus(aniListSlice)
                }
            }
        }
        if (slice == null || slice.isEmpty) return Slice()
        Log.d(TAG, "Taksonomi toplandı: tür=${slice.genres.size} etiket=${slice.tags.size} " +
            "stüdyo=${slice.studios.size} yapımcı=${slice.producers.size}")
        return slice
    }

    // ── MAL / Jikan ────────────────────────────────────────────────────────────

    /**
     * MAL/Jikan detayından **yalnızca taksonomi** alanlarını okur.
     *
     * Ağır detay istemcisi ([KitsugiMalDetailClient]) karakter/ekip/video/şarkı uçlarına da
     * gider; burada tek ve önbellekli `anime|manga/{id}` yanıtı yeterlidir.
     */
    private suspend fun fetchJikan(malId: Int?, mediaType: MediaType): Slice? {
        if (malId == null) return null
        val path = if (mediaType == MediaType.Manga) "manga" else "anime"
        val url = "https://api.jikan.moe/v4/$path/$malId"
        val body = when (val result = JikanGateway.fetch(url)) {
            is JikanResult.Ok -> result.body
            else -> null
        } ?: return null

        val data = JSONObject(body).optJSONObject("data") ?: return null
        val genres = mutableListOf<String>()
        val themes = mutableListOf<String>()
        val demographics = mutableListOf<String>()
        val tags = mutableListOf<KitsugiTag>()

        fun readNamedArray(name: String, collectTo: MutableList<String>, tagSource: String? = null) {
            val arr = data.optJSONArray(name) ?: return
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val value = obj.optString("name", "").trim()
                if (value.isEmpty()) continue
                collectTo.add(value)
                if (tagSource != null) {
                    tags.add(
                        KitsugiTag(
                            id = obj.optInt("mal_id", 0).takeIf { it > 0 },
                            name = value,
                            isSpoiler = KitsugiSpoilerRules.isIntrinsicSpoiler(value),
                            source = tagSource
                        )
                    )
                }
            }
        }

        readNamedArray("genres", genres, tagSource = null)
        readNamedArray("explicit_genres", genres, tagSource = null)
        readNamedArray("themes", themes, tagSource = "mal")
        readNamedArray("demographics", demographics, tagSource = "mal")
        genres.addAll(themes)
        genres.addAll(demographics)

        fun readOrgs(name: String, role: StudioRole): List<KitsugiStudio> {
            val arr = data.optJSONArray(name) ?: return emptyList()
            val out = ArrayList<KitsugiStudio>(arr.length())
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val value = obj.optString("name", "").trim()
                if (value.isEmpty()) continue
                out.add(
                    KitsugiStudio(
                        id = obj.optInt("mal_id", 0).takeIf { it > 0 } ?: 0,
                        name = value.toLatinStudioName(),
                        isMain = role == StudioRole.STUDIO,
                        source = "mal",
                        role = role
                    )
                )
            }
            return out
        }

        val studios = readOrgs("studios", StudioRole.STUDIO)
        val producers = readOrgs("producers", StudioRole.PRODUCER)
            .filter { candidate -> studios.none { it.name.equals(candidate.name, ignoreCase = true) } }
        val licensors = readOrgs("licensors", StudioRole.LICENSOR)

        return Slice(
            genres = genres.distinct(),
            tags = KitsugiSpoilerRules.normalizeAndMergeTags(tags),
            studios = studios,
            producers = producers + licensors,
            demographics = demographics,
            themes = themes
        )
    }

}
