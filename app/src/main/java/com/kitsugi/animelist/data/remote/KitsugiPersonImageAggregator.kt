package com.kitsugi.animelist.data.remote

import com.kitsugi.animelist.data.auth.BangumiApiClient
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.net.URLEncoder

/**
 * Karakter / kişi (seslendirmen, oyuncu, personel) görsellerini **tüm kaynaklardan birlikte**
 * toplayan ortak toplayıcı.
 *
 * Tarihsel olarak galeri yalnızca sayfanın açıldığı kaynağın görselini + (kimlik MAL
 * uzayındaysa) Jikan `/pictures` eklerini gösteriyordu. Artık kimlik bilinen/çözümlenebilen
 * her kaynak (MAL/Jikan, AniList, Shikimori, Kitsu, Bangumi, TMDB) katkı verir; her görsel
 * kendi kaynak etiketiyle döner (galeri diyaloğundaki kaynak rozeti bu etiketi gösterir).
 *
 * Güvenilirlik kuralları:
 *  - İsme-dayalı aramalar **sıkı eşleşme** gerektirir (yanlış kişi/karakter görseli göstermek
 *    yerine hiç göstermemek tercih edilir) — bkz. [nameMatches].
 *  - Her kaynak çağrısı bağımsız hata/timeout toleranslıdır; toplam süre [budgetMs] ile
 *    sınırlıdır ve dolan kaynakların görselleri kısmi olarak döner.
 *  - Kurgusal karakterler TMDB'de bulunmadığından TMDB yalnızca [PersonKind.PERSON] için
 *    sorgulanır (kişi profil fotoğrafları); Kitsu yalnızca karakterler için sorgulanır.
 */
object KitsugiPersonImageAggregator {

    enum class PersonKind { CHARACTER, PERSON }

    /** Galeri diyaloğunun anladığı görünür kaynak etiketi. */
    data class SourceImage(val url: String, val source: String)

    private const val SRC_MAL = "MyAnimeList"
    private const val SRC_ANILIST = "AniList"
    private const val SRC_SHIKIMORI = "Shikimori"
    private const val SRC_KITSU = "Kitsu"
    private const val SRC_BANGUMI = "Bangumi"
    private const val SRC_TMDB = "TMDB"

    private const val MAX_TMDB_PROFILES = 12
    private const val SEARCH_NAME_ATTEMPTS = 2

    /**
     * Tüm kaynaklardan görselleri toplar.
     *
     * @param kind       karakter mi gerçek kişi mi
     * @param source     detayın açıldığı kaynak (jikan/mal, anilist, shikimori, kitsu, bangumi, tmdb...)
     * @param id         açılan kaynaktaki kimlik (kaynak uzayında)
     * @param malId      biliniyorsa gerçek MAL kimliği (Jikan + Shikimori sorgularını açar)
     * @param aniListId  biliniyorsa AniList kimliği (doğrudan görsel sorgusu)
     * @param names      aday adlar: birincil ad + native/romanize/ingilizce/alternatif adlar
     * @param tmdbEnabled kullanıcı ayarı; kapalıysa TMDB atlanır
     * @param budgetMs   toplam süre bütçesi (kısmi sonuç döner)
     */
    suspend fun aggregate(
        kind: PersonKind,
        source: String,
        id: Int,
        malId: Int?,
        aniListId: Int?,
        names: List<String>,
        tmdbEnabled: Boolean,
        budgetMs: Long = 9_000L
    ): List<SourceImage> {
        val canonical = MalJikanMediaSupport.canonicalSource(source)
        val isBangumiSource = canonical == "bangumi" || canonical == "bgm"
        val nameCandidates = names.filter { it.isNotBlank() }.map { it.trim() }.distinct()
            .take(SEARCH_NAME_ATTEMPTS)
        val results = mutableListOf<SourceImage>()
        coroutineScope {
            val tasks = mutableListOf<Deferred<List<SourceImage>>>()

            if (malId != null && malId > 0) {
                tasks += async { jikanPictures(kind, malId) }
                tasks += async { shikimoriImage(kind, malId) }
            }
            if ((aniListId != null && aniListId > 0) || nameCandidates.isNotEmpty()) {
                tasks += async { aniListImage(kind, aniListId, nameCandidates) }
            }
            when (kind) {
                PersonKind.CHARACTER -> tasks += async {
                    kitsuCharacterImage(knownId = id.takeIf { canonical == "kitsu" && it > 0 }, nameCandidates)
                }
                PersonKind.PERSON -> Unit // Kitsu personel görseli sağlamaz
            }
            tasks += async {
                bangumiImage(kind, knownId = id.takeIf { isBangumiSource && it > 0 }, nameCandidates)
            }
            if (kind == PersonKind.PERSON && tmdbEnabled) {
                tasks += async {
                    tmdbPersonImages(knownId = id.takeIf { canonical == "tmdb" && it > 0 }, nameCandidates)
                }
            }

            // Kaynak önceliğini koruyarak sırayla topla; bütçe dolunca kalanı iptal et (kısmi sonuç).
            val deadline = System.currentTimeMillis() + budgetMs
            for (task in tasks) {
                val remaining = deadline - System.currentTimeMillis()
                if (remaining <= 300L) {
                    task.cancel()
                    continue
                }
                runCatching { withTimeout(remaining) { task.await() } }
                    .onSuccess { results += it }
            }
        }
        return results.distinctBy { it.url }
    }

    // ── MAL / Jikan — /pictures ekleri ──────────────────────────────────────────

    private suspend fun jikanPictures(kind: PersonKind, malId: Int): List<SourceImage> =
        withContext(Dispatchers.IO) {
            val endpoint = if (kind == PersonKind.CHARACTER) "characters" else "people"
            runCatching {
                val text = (JikanGateway.fetchBlocking(
                    "https://api.jikan.moe/v4/$endpoint/$malId/pictures",
                    JikanGateway.Priority.UI
                ) as? JikanResult.Ok)?.body ?: return@runCatching emptyList()
                val dataArr = JSONObject(text).optJSONArray("data") ?: return@runCatching emptyList()
                val urls = mutableListOf<SourceImage>()
                for (i in 0 until dataArr.length()) {
                    val obj = dataArr.getJSONObject(i)
                    val webp = obj.optJSONObject("webp")
                    val jpg = obj.optJSONObject("jpg")
                    val picUrl = webp?.optString("large_image_url")?.takeIf { it.isNotBlank() }
                        ?: webp?.optString("image_url")?.takeIf { it.isNotBlank() }
                        ?: jpg?.optString("large_image_url")?.takeIf { it.isNotBlank() }
                        ?: jpg?.optString("image_url")?.takeIf { it.isNotBlank() }
                    if (!picUrl.isNullOrBlank()) urls += SourceImage(picUrl, SRC_MAL)
                }
                urls
            }.getOrElse { emptyList() }
        }

    // ── AniList — doğrudan kimlik veya isim araması ────────────────────────────

    private suspend fun aniListImage(
        kind: PersonKind,
        aniListId: Int?,
        names: List<String>
    ): List<SourceImage> = withContext(Dispatchers.IO) {
        val node = if (kind == PersonKind.CHARACTER) "Character" else "Staff"
        runCatching {
            if (aniListId != null && aniListId > 0) {
                val query = "query (\$id: Int) { $node(id: \$id) { image { large } } }"
                val response = KitsugiApiBase.executeAniListQuery(query, JSONObject().put("id", aniListId))
                    ?: return@runCatching emptyList()
                val img = JSONObject(response).optJSONObject("data")?.optJSONObject(node)
                    ?.optJSONObject("image")?.optNullableString("large")
                if (!img.isNullOrBlank()) listOf(SourceImage(img, SRC_ANILIST)) else emptyList()
            } else if (names.isNotEmpty()) {
                val pageField = if (kind == PersonKind.CHARACTER) "characters" else "staff"
                for (name in names) {
                    val query = """
                        query (${'$'}search: String) {
                            Page(page: 1, perPage: 5) {
                                $pageField(search: ${'$'}search) {
                                    id name { full userPreferred native } image { large }
                                }
                            }
                        }
                    """.trimIndent()
                    val response = KitsugiApiBase.executeAniListQuery(query, JSONObject().put("search", name))
                        ?: continue
                    val arr = JSONObject(response).optJSONObject("data")?.optJSONObject("Page")
                        ?.optJSONArray(pageField) ?: continue
                    for (i in 0 until arr.length()) {
                        val obj = arr.optJSONObject(i) ?: continue
                        val nameObj = obj.optJSONObject("name")
                        val candidateNames = listOfNotNull(
                            nameObj?.optNullableString("userPreferred"),
                            nameObj?.optNullableString("full"),
                            nameObj?.optNullableString("native")
                        )
                        val matched = names.any { q -> candidateNames.any { nameMatches(q, it) } }
                        if (matched) {
                            val img = obj.optJSONObject("image")?.optNullableString("large")
                            if (!img.isNullOrBlank()) return@runCatching listOf(SourceImage(img, SRC_ANILIST))
                        }
                    }
                }
                emptyList()
            } else emptyList()
        }.getOrElse { emptyList() }
    }

    // ── Shikimori — kimlik MAL uzayıyla aynı ────────────────────────────────────

    private suspend fun shikimoriImage(kind: PersonKind, malId: Int): List<SourceImage> =
        withContext(Dispatchers.IO) {
            runCatching {
                val segment = if (kind == PersonKind.CHARACTER) "characters" else "people"
                val response = KitsugiApiBase.executeGetRequestResilient(
                    URL("https://shikimori.io/api/$segment/$malId")
                ) ?: return@runCatching emptyList()
                val relative = JSONObject(response).optJSONObject("image")?.optString("original")
                ShikimoriPosterResolver.absoluteUrl(relative)
                    ?.let { listOf(SourceImage(it, SRC_SHIKIMORI)) }
                    ?: emptyList()
            }.getOrElse { emptyList() }
        }

    // ── Kitsu — yalnızca karakterler ────────────────────────────────────────────

    private fun kitsuGet(urlStr: String): JSONObject? {
        val request = okhttp3.Request.Builder()
            .url(urlStr)
            .header("Accept", "application/vnd.api+json")
            .header("User-Agent", "Kitsugi/1.0 (Android)")
            .build()
        return runCatching {
            com.kitsugi.animelist.core.network.KitsugiHttpClient.client.newCall(request).execute()
                .use { response ->
                    if (!response.isSuccessful) null
                    else response.body?.string()?.takeIf { it.isNotBlank() }?.let { JSONObject(it) }
                }
        }.getOrNull()
    }

    private fun kitsuImageOf(attrs: JSONObject?): String? {
        val imageObj = attrs?.optJSONObject("image") ?: return null
        return imageObj.optString("original").takeIf { it.isNotBlank() }
            ?: imageObj.optString("large").takeIf { it.isNotBlank() }
            ?: imageObj.optString("medium").takeIf { it.isNotBlank() }
    }

    private suspend fun kitsuCharacterImage(
        knownId: Int?,
        names: List<String>
    ): List<SourceImage> = withContext(Dispatchers.IO) {
        runCatching {
            if (knownId != null && knownId > 0) {
                val root = kitsuGet("https://kitsu.io/api/edge/characters/$knownId")
                val attrs = root?.optJSONObject("data")?.optJSONObject("attributes")
                kitsuImageOf(attrs)?.let { return@runCatching listOf(SourceImage(it, SRC_KITSU)) }
                return@runCatching emptyList()
            }
            for (name in names) {
                val encoded = URLEncoder.encode(name, "UTF-8")
                val root = kitsuGet("https://kitsu.io/api/edge/characters?filter[name]=$encoded&page[limit]=5")
                    ?: continue
                val data = root.optJSONArray("data") ?: continue
                for (i in 0 until data.length()) {
                    val attrs = data.optJSONObject(i)?.optJSONObject("attributes") ?: continue
                    val candidateNames = listOfNotNull(
                        attrs.optNullableString("name"),
                        attrs.optNullableString("canonicalName")
                    )
                    val matched = names.any { q -> candidateNames.any { nameMatches(q, it) } }
                    if (matched) {
                        kitsuImageOf(attrs)?.let { return@runCatching listOf(SourceImage(it, SRC_KITSU)) }
                    }
                }
            }
            emptyList()
        }.getOrElse { emptyList() }
    }

    // ── Bangumi — kimlik veya arama (karakter + kişi) ───────────────────────────

    private fun bangumiImageOf(images: JSONObject?): String? {
        if (images == null) return null
        for (key in listOf("large", "common", "medium", "small", "grid")) {
            if (images.isNull(key)) continue
            val url = BangumiApiClient.absoluteImageUrl(images.optString(key).trim().ifBlank { null })
            if (!url.isNullOrBlank()) return url
        }
        return null
    }

    private suspend fun bangumiImage(
        kind: PersonKind,
        knownId: Int?,
        names: List<String>
    ): List<SourceImage> = withContext(Dispatchers.IO) {
        runCatching {
            if (knownId != null && knownId > 0) {
                val raw = BangumiIdNamespace.rawIdFromStable(knownId) ?: knownId
                val root = if (kind == PersonKind.CHARACTER) {
                    BangumiApiClient.getCharacter(raw)
                } else {
                    BangumiApiClient.getPerson(raw)
                }
                bangumiImageOf(root?.optJSONObject("images"))
                    ?.let { return@runCatching listOf(SourceImage(it, SRC_BANGUMI)) }
                return@runCatching emptyList()
            }
            for (name in names) {
                val page = if (kind == PersonKind.CHARACTER) {
                    runCatching { BangumiApiClient.searchCharacters(name, limit = 5) }.getOrNull()
                } else {
                    runCatching { BangumiApiClient.searchPersons(name, limit = 5) }.getOrNull()
                } ?: continue
                for (entity in page.data) {
                    val candidates = listOf(entity.name, entity.nameCn).filter { it.isNotBlank() }
                    val matched = names.any { q -> candidates.any { nameMatches(q, it) } }
                    if (matched) {
                        val img = entity.images?.large ?: entity.images?.common
                            ?: entity.images?.medium ?: entity.images?.small ?: entity.images?.grid
                        BangumiApiClient.absoluteImageUrl(img)
                            ?.let { return@runCatching listOf(SourceImage(it, SRC_BANGUMI)) }
                        // Arama yanıtı görsel taşımıyorsa eşleşmeye ait detaydan alınır.
                        val root = if (kind == PersonKind.CHARACTER) {
                            runCatching { BangumiApiClient.getCharacter(entity.id) }.getOrNull()
                        } else {
                            runCatching { BangumiApiClient.getPerson(entity.id) }.getOrNull()
                        }
                        bangumiImageOf(root?.optJSONObject("images"))
                            ?.let { return@runCatching listOf(SourceImage(it, SRC_BANGUMI)) }
                    }
                }
            }
            emptyList()
        }.getOrElse { emptyList() }
    }

    // ── TMDB — yalnızca gerçek kişiler (profil fotoğrafları) ────────────────────

    private suspend fun tmdbPersonImages(
        knownId: Int?,
        names: List<String>
    ): List<SourceImage> = withContext(Dispatchers.IO) {
        runCatching {
            var personId = knownId?.takeIf { it > 0 }
            if (personId == null) {
                // Kimliği isimle çöz: ilk sıkı eşleşme kabul edilir (yanlış kişi riski düşük tutulur).
                personId = resolveTmdbPersonId(names) ?: return@runCatching emptyList()
            }
            val apiKey = TmdbApiClient.getActiveApiKey()
            val url = URL("https://api.themoviedb.org/3/person/$personId/images?api_key=$apiKey")
            val response = KitsugiApiBase.executeGetRequest(url) ?: return@runCatching emptyList()
            val profiles = JSONObject(response).optJSONArray("profiles") ?: return@runCatching emptyList()
            val urls = mutableListOf<SourceImage>()
            for (i in 0 until profiles.length()) {
                val path = profiles.optJSONObject(i)?.optNullableString("file_path") ?: continue
                if (path.isBlank()) continue
                urls += SourceImage("https://image.tmdb.org/t/p/w500$path", SRC_TMDB)
                if (urls.size >= MAX_TMDB_PROFILES) break
            }
            urls
        }.getOrElse { emptyList() }
    }

    private suspend fun resolveTmdbPersonId(names: List<String>): Int? {
        if (names.isEmpty()) return null
        val client = TmdbApiClient()
        for (name in names) {
            val results = runCatching { client.searchPerson(name) }.getOrNull().orEmpty()
            for (result in results) {
                if (names.any { nameMatches(it, result.title) }) {
                    return result.malId.takeIf { it > 0 }
                }
            }
        }
        return null
    }

    // ── Sıkı isim eşleştirme ────────────────────────────────────────────────────

    /**
     * Diakritik-duyarsız, noktalama-duyarsız normalize; CJK karakterleri eler
     * (orijinal Japonca/Çince adlarla muğlak eşleşme yapılmaz — yanlış görsel riski).
     */
    private fun normalizeName(value: String): String = java.text.Normalizer
        .normalize(value, java.text.Normalizer.Form.NFD)
        .replace("\\p{M}+".toRegex(), "")
        .lowercase()
        .replace(Regex("[^a-z0-9\\s]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    /**
     * Sıkı eşleşme: tam normalize eşitlik veya aynı jeton kümesi (ör. "Kagenou Cid" ≡
     * "Cid Kagenou" — Japonca ad sırası farkı). Kısmi/önek eşleşmeler kabul EDİLMEZ
     * (ör. "Yuki" ∉ "Yuki Takeya") — yanlış kişi/karakter görseli riskini dışlar.
     */
    private fun nameMatches(query: String, candidate: String?): Boolean {
        if (candidate.isNullOrBlank()) return false
        val q = normalizeName(query)
        val c = normalizeName(candidate)
        if (q.isEmpty() || c.isEmpty()) return false
        if (q == c) return true
        val qTokens = q.split(" ").filter { it.isNotBlank() }.toSet()
        val cTokens = c.split(" ").filter { it.isNotBlank() }.toSet()
        return qTokens.size >= 2 && qTokens == cTokens
    }
}
