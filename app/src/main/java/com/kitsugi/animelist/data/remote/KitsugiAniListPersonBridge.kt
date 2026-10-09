package com.kitsugi.animelist.data.remote

import com.kitsugi.animelist.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer
import java.util.concurrent.ConcurrentHashMap

/**
 * AniList ad köprüsü: Bangumi liste uçlarındaki (Çince/Japonca) kişi adlarına romaji ve
 * İngilizce karşılık kazandırır.
 *
 * Bangumi'nin liste uçları (`/v0/subjects/{id}/characters`, `/v0/subjects/{id}/persons`,
 * `/p1/subjects/{id}/staffs/persons`) infobox döndürmez; bu yüzden karakter, seslendirmen
 * ve kadro adları listelerde özgün (kanji) kalır. AniList'te aynı yapım (`idMal`) için
 * adlar `full` (romaji) + `native` (özgün) + `alternative` olarak gelir; özgün ada birebir
 * eşleme yapılır — uydurma transliterasyon/çeviri üretilmez. Eşleşmeyen adlar olduğu gibi
 * kalır.
 *
 * Sonuçlar bellek içinde önbelleklenir; sekme kapansa bile aynı yapım için tekrar sorgu
 * yapılmaz.
 */
internal object KitsugiAniListPersonBridge {

    /** Bir kişi için AniList ad varyantları. */
    data class NameVariant(
        val full: String,
        val native: String?,
        val alternatives: List<String>
    ) {
        /** Romaji adayı: `full` Latin alfabesindeyse alınır. */
        val romaji: String?
            get() = full.takeIf { isLatinName(it) }

        /** İngilizce adayı: `full` dışındaki ilk Latin alternatif. */
        val english: String?
            get() = alternatives.firstOrNull { isLatinName(it) && !it.equals(full, ignoreCase = true) }

        /** Eşleme anahtarları: özgün ad + full + tüm alternatifler (normalleştirilmiş). */
        val matchKeys: Set<String> = (listOfNotNull(native, full) + alternatives)
            .mapNotNull { normalizeKey(it) }
            .toSet()
    }

    /** Bir Media kaydının AniList ad listeleri. */
    data class MediaNames(
        val characters: List<NameVariant>,
        val voiceActors: List<NameVariant>,
        val staff: List<NameVariant>
    )

    private val cache = ConcurrentHashMap<Int, MediaNames>()

    private fun isLatinName(text: String): Boolean =
        text.any { it.isLetter() } &&
            !com.kitsugi.animelist.utils.PreferenceHelpers.hasCjkCharacters(text)

    private fun normalizeKey(text: String): String? {
        val normalized = Normalizer.normalize(text.trim(), Normalizer.Form.NFKC)
            .lowercase()
            .replace(Regex("[\\s\\p{Punct}]+"), "")
        return normalized.takeIf { it.isNotEmpty() }
    }

    /**
     * AniList `Media(idMal:)` sorgusuyla karakter + seslendirmen + kadro adlarını çeker.
     * Önbelleklidir; ağ/ayrıştırma hatasında `null` döner.
     */
    suspend fun fetchMediaNames(malId: Int, mediaType: MediaType): MediaNames? {
        if (malId <= 0) return null
        cache[malId]?.let { return it }
        val query = """
            query (${'$'}idMal: Int, ${'$'}type: MediaType) {
                Media(idMal: ${'$'}idMal, type: ${'$'}type) {
                    characters(page: 1, perPage: 50, sort: [RELEVANCE, ROLE]) {
                        edges {
                            node { id name { full native alternative } }
                            voiceActors { id name { full native alternative } }
                        }
                    }
                    staff(page: 1, perPage: 50) {
                        edges {
                            node { id name { full native alternative } }
                        }
                    }
                }
            }
        """.trimIndent()
        val variables = JSONObject()
            .put("idMal", malId)
            .put("type", MalJikanMediaSupport.aniListMediaType(mediaType))
        val response = runCatching {
            withContext(Dispatchers.IO) {
                KitsugiApiBase.executeAniListQuery(query, variables)
            }
        }.getOrNull() ?: return null
        val media = runCatching {
            JSONObject(response).optJSONObject("data")?.optJSONObject("Media")
        }.getOrNull() ?: return null

        fun parseNameObject(name: JSONObject?): NameVariant? {
            if (name == null) return null
            val full = name.optString("full").trim()
            if (full.isEmpty()) return null
            val native = name.optString("native").trim().takeIf { it.isNotEmpty() }
            val alternatives = mutableListOf<String>()
            name.optJSONArray("alternative")?.let { alt ->
                for (j in 0 until alt.length()) {
                    alt.optString(j).trim().takeIf { it.isNotEmpty() }?.let { alternatives.add(it) }
                }
            }
            return NameVariant(full = full, native = native, alternatives = alternatives)
        }

        fun parseEdges(edges: JSONArray?): List<NameVariant> =
            edges?.let { arr ->
                (0 until arr.length()).mapNotNull { i ->
                    parseNameObject(arr.optJSONObject(i)?.optJSONObject("node")?.optJSONObject("name"))
                }
            }.orEmpty()

        val charactersEdges = media.optJSONObject("characters")?.optJSONArray("edges")
        val staffEdges = media.optJSONObject("staff")?.optJSONArray("edges")
        val characters = parseEdges(charactersEdges)
        val staff = parseEdges(staffEdges)

        val voiceActors = mutableListOf<NameVariant>()
        if (charactersEdges != null) {
            for (i in 0 until charactersEdges.length()) {
                val vaArray = charactersEdges.optJSONObject(i)?.optJSONArray("voiceActors") ?: continue
                for (j in 0 until vaArray.length()) {
                    val variant = parseNameObject(vaArray.optJSONObject(j)?.optJSONObject("name")) ?: continue
                    voiceActors.add(variant)
                }
            }
        }

        val result = MediaNames(
            characters = characters,
            voiceActors = voiceActors.distinctBy { it.matchKeys },
            staff = staff
        )
        if (cache.size > 128) cache.clear()
        cache[malId] = result
        return result
    }

    /**
     * Özgün (native) ada göre birebir eşleşme arar; `fallbackName` (örn. liste adının
     * kendisi) de anahtar olarak denenir. Bulunamazsa `null` döner.
     */
    fun findByNative(
        list: List<NameVariant>,
        nativeName: String?,
        fallbackName: String? = null
    ): NameVariant? {
        val keys = listOfNotNull(
            nativeName?.trim()?.takeIf { it.isNotEmpty() },
            fallbackName?.trim()?.takeIf { it.isNotEmpty() }
        ).mapNotNull { normalizeKey(it) }.toSet()
        if (keys.isEmpty()) return null
        return list.firstOrNull { variant -> variant.matchKeys.any { it in keys } }
    }
}
