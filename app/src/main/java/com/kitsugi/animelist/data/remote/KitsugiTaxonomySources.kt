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
import java.net.URL

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
 * Toplanan kaynaklar (hepsi MAL kimliğinden erişilebilir):
 *  • **MAL/Jikan** — `genres`, `explicit_genres`, `themes`, `demographics`,
 *    `studios`, `producers`, `licensors`
 *  • **AniList** — `genres`, `tags` (spoiler/rank dahil), `studios` (isMain dahil)
 *  • **Shikimori** — `genres` (`kind`: genre/theme/demographic) + `studios`
 *  • **Kitsu** — `categories` + `animeProductions.producer` (rol: studio/producer/licensor)
 *
 * Kapsam notu: **Simkl** API'si kurum (stüdyo/yapımcı) alanı yayınlamaz, yalnızca tür
 * listesi verir; bu yüzden buraya eklenmez (sayfa Simkl'den açıldığında kendi türleri
 * zaten gelir). **TMDB** zenginleştirmesi [KitsugiDetailClient.enrichDetail] içinde ayrı
 * yürür (yapım şirketleri + kanallar + anahtar kelimeler).
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
                // Shikimori ve Kitsu kendi kimlik uzaylarını kullanır; MAL kimliğinden
                // deterministik çözüm yapılır (Shikimori yanıtı doğrulanır, Kitsu `mappings`).
                val shikimori = async(Dispatchers.IO) {
                    withTimeoutOrNull(SOURCE_TIMEOUT_MS) {
                        runCatching { fetchShikimori(safeMalId, mediaType) }.getOrNull()
                    }
                }
                val kitsu = async(Dispatchers.IO) {
                    withTimeoutOrNull(SOURCE_TIMEOUT_MS) {
                        runCatching { fetchKitsu(safeMalId, mediaType) }.getOrNull()
                    }
                }
                val parts = linkedMapOf(
                    "jikan" to jikan.await(),
                    "anilist" to aniList.await(),
                    "shikimori" to shikimori.await(),
                    "kitsu" to kitsu.await()
                )
                if (parts.values.all { it == null }) return@coroutineScope null
                // Kaynak bazlı sayaç: cihaz günlüğünde hangi kaynağın ne kadar veri
                // verdiği görülebilsin (teşhis ve regresyon takibi).
                Log.d(
                    TAG,
                    "Kaynak bazlı: " + parts.entries.joinToString(" | ") { (name, part) ->
                        if (part == null) "$name=yok"
                        else "$name=${part.genres.size}t/${part.tags.size}e/" +
                            "${part.studios.size + part.producers.size}k"
                    }
                )
                parts.values.filterNotNull()
                    .fold(Slice()) { acc, part -> if (acc.isEmpty) part else acc.plus(part) }
            }
        }
        if (slice == null || slice.isEmpty) return Slice()
        Log.d(TAG, "Taksonomi toplandı: tür=${slice.genres.size} etiket=${slice.tags.size} " +
            "stüdyo=${slice.studios.size} yapımcı=${slice.producers.size}")
        return slice
    }

    // ── Shikimori (Rusça) ──────────────────────────────────────────────────────

    /**
     * Shikimori detayından tür ve stüdyo bilgisini okur.
     *
     * **Kimlik güvenliği:** Shikimori kimliği MAL kimliği değildir. İstek MAL kimliğiyle
     * yapılır (Shikimori veritabanı MAL'dan türediği için çoğu kayıtta aynıdır) ve yanıttaki
     * `myanimelist_id` farklıysa **veri kullanılmaz** — yanlış yapımın türleri yapışmasın.
     *
     * `kind` alanı korunur: `genre` → tür, `theme` → tema etiketi, `demographic` → hedef kitle.
     * Rusça adlar gösterim katmanında çözülür (`RussianTaxonomyDictionary`).
     */
    private suspend fun fetchShikimori(malId: Int?, mediaType: MediaType): Slice? {
        if (malId == null) return null
        val endpoint = if (mediaType == MediaType.Manga) "mangas" else "animes"
        val body = KitsugiApiBase.executeGetRequestResilient(
            URL("https://shikimori.io/api/$endpoint/$malId")
        ) ?: return null
        val data = runCatching { JSONObject(body) }.getOrNull() ?: return null

        val realMalId = data.optInt("myanimelist_id", 0)
        if (realMalId > 0 && realMalId != malId) return null

        val genres = mutableListOf<String>()
        val themes = mutableListOf<String>()
        val demographics = mutableListOf<String>()
        val tags = mutableListOf<KitsugiTag>()

        data.optJSONArray("genres")?.let { arr ->
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val name = obj.optString("name", "").trim()
                if (name.isEmpty()) continue
                when (obj.optString("kind", "").lowercase()) {
                    "theme" -> {
                        themes.add(name)
                        genres.add(name)
                    }
                    "demographic" -> {
                        demographics.add(name)
                        genres.add(name)
                    }
                    else -> genres.add(name)
                }
                tags.add(
                    KitsugiTag(
                        id = obj.optInt("id", 0).takeIf { it > 0 },
                        name = name,
                        isSpoiler = KitsugiSpoilerRules.isIntrinsicSpoiler(name),
                        source = "shikimori"
                    )
                )
            }
        }

        val studios = mutableListOf<KitsugiStudio>()
        data.optJSONArray("studios")?.let { arr ->
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val name = obj.optString("name", "").trim()
                if (name.isEmpty()) continue
                studios.add(
                    KitsugiStudio(
                        id = obj.optInt("id", 0).takeIf { it > 0 } ?: 0,
                        name = name.toLatinStudioName(),
                        isMain = true,
                        source = "shikimori",
                        role = StudioRole.STUDIO
                    )
                )
            }
        }

        if (genres.isEmpty() && studios.isEmpty()) return null
        return Slice(
            genres = genres.distinct(),
            tags = KitsugiSpoilerRules.normalizeAndMergeTags(tags),
            studios = studios,
            themes = themes.distinct(),
            demographics = demographics.distinct()
        )
    }

    // ── Kitsu ──────────────────────────────────────────────────────────────────

    /**
     * Kitsu detayından kategori ve **yapım şirketlerini** okur.
     *
     * Kitsu, MAL kimliğini `mappings` tablosuyla deterministik olarak çözer
     * ([com.kitsugi.animelist.data.auth.KitsuApiClient.lookupKitsuIdByExternalMapping]).
     * Kurumlar `animeProductions.producer` ilişkisinden gelir; `role` alanı
     * `studio` / `producer` / `licensor` olarak korunur. Bu katman olmadan Kitsu kaynaklı
     * sayfalarda **hiç stüdyo görünmüyordu**.
     */
    private suspend fun fetchKitsu(malId: Int?, mediaType: MediaType): Slice? {
        if (malId == null) return null
        val isManga = mediaType == MediaType.Manga
        val site = if (isManga) "myanimelist/manga" else "myanimelist/anime"
        val kitsuId = runCatching {
            com.kitsugi.animelist.data.auth.KitsuApiClient
                .lookupKitsuIdByExternalMapping(site, malId)
        }.getOrNull() ?: return null
        if (kitsuId <= 0) return null

        val endpoint = if (isManga) "manga" else "anime"
        val base = "${KitsuApiHost.base}/$endpoint/$kitsuId"
        // Manga tarafında yapım ilişkisi garanti değil: kategori ile başla, anime ise
        // yapım şirketlerini de iste; uç reddederse (400) ilişkisiz isteğe düşülür.
        val withProductions = if (isManga) {
            "$base?include=categories,mangaProductions.producer"
        } else {
            "$base?include=categories,animeProductions.producer"
        }
        val body = KitsugiApiBase.executeGetRequestResilient(URL(withProductions))
            ?: KitsugiApiBase.executeGetRequestResilient(URL("$base?include=categories"))
            ?: return null
        val root = runCatching { JSONObject(body) }.getOrNull() ?: return null
        val included = root.optJSONArray("included") ?: return null

        val genres = mutableListOf<String>()
        val tags = mutableListOf<KitsugiTag>()
        val studios = mutableListOf<KitsugiStudio>()
        val producers = mutableListOf<KitsugiStudio>()

        // 1) Kategoriler (tür + etiket)
        val producerNames = HashMap<String, Pair<String, String>>()   // id -> (name, role)
        val producerRefs = mutableListOf<Pair<String, String>>()      // (producerId, role)

        for (i in 0 until included.length()) {
            val inc = included.optJSONObject(i) ?: continue
            val type = inc.optString("type", "")
            val attrs = inc.optJSONObject("attributes")
            when (type) {
                "categories" -> {
                    val title = attrs?.optString("title", "")?.trim().orEmpty()
                    if (title.isEmpty()) continue
                    genres.add(title)
                    tags.add(
                        KitsugiTag(
                            id = inc.optString("id", "").toIntOrNull(),
                            name = title,
                            isSpoiler = KitsugiSpoilerRules.isIntrinsicSpoiler(title),
                            source = "kitsu"
                        )
                    )
                }
                "animeProductions", "mangaProductions" -> {
                    val role = attrs?.optString("role", "").orEmpty().trim()
                    val producerId = inc.optJSONObject("relationships")
                        ?.optJSONObject("producer")
                        ?.optJSONObject("data")
                        ?.optString("id", "")
                        .orEmpty()
                    if (producerId.isNotEmpty()) producerRefs.add(producerId to role)
                }
                "producers" -> {
                    val name = attrs?.optString("name", "")?.trim().orEmpty()
                    if (name.isEmpty()) continue
                    producerNames[inc.optString("id", "")] = name to ""
                }
            }
        }

        // 2) Yapım şirketleri (rol bilgisiyle)
        for ((producerId, role) in producerRefs) {
            val name = producerNames[producerId]?.first ?: continue
            val studioRole = when (role.lowercase()) {
                "studio" -> StudioRole.STUDIO
                "licensor" -> StudioRole.LICENSOR
                "publisher" -> StudioRole.PUBLISHER
                "magazine" -> StudioRole.MAGAZINE
                "network" -> StudioRole.NETWORK
                else -> StudioRole.PRODUCER
            }
            val org = KitsugiStudio(
                id = producerId.toIntOrNull() ?: 0,
                name = name.toLatinStudioName(),
                isMain = studioRole == StudioRole.STUDIO,
                source = "kitsu",
                role = studioRole
            )
            if (studioRole == StudioRole.STUDIO) studios.add(org) else producers.add(org)
        }

        if (genres.isEmpty() && studios.isEmpty() && producers.isEmpty()) return null
        return Slice(
            genres = genres.distinct(),
            tags = KitsugiSpoilerRules.normalizeAndMergeTags(tags),
            studios = studios,
            producers = producers
        )
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
