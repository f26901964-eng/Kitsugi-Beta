package com.kitsugi.animelist.data.fandom

import android.util.Log
import com.kitsugi.animelist.core.memory.BoundedCache
import com.kitsugi.animelist.data.remote.KitsugiApiBase
import kotlinx.coroutines.CancellationException
import org.json.JSONObject

/**
 * Doğrulama verisini AniList'ten kimlikle çeker: başlıklar, yaratıcılar, karakterler
 * (yerel adlarıyla) ve ilişkiler. Uygulamanın ortak, hız sınırlı AniList kapısını
 * (`KitsugiApiBase.executeAniListQuery`) kullanır.
 */
internal object FandomAniListSource {

    private const val TAG = "FandomAniList"

    private val QUERY = """
        query (${'$'}id: Int, ${'$'}idMal: Int, ${'$'}type: MediaType) {
          Media(id: ${'$'}id, idMal: ${'$'}idMal, type: ${'$'}type) {
            id
            idMal
            type
            title { romaji english native }
            synonyms
            relations { edges { relationType node { id idMal type } } }
            characters(sort: [ROLE, RELEVANCE, ID], perPage: 12) {
              edges { role node { id name { full native alternative } } }
            }
            staff(sort: [RELEVANCE, ID], perPage: 10) {
              edges { role node { name { full } } }
            }
          }
        }
    """.trimIndent()

    private val cache = BoundedCache<String, FandomMediaEvidence>("fandom_anilist_evidence", 48)

    suspend fun load(aniListId: Int?, malId: Int?, isManga: Boolean): FandomMediaEvidence? {
        val key = when {
            aniListId != null -> "al:$aniListId"
            malId != null -> "mal:${if (isManga) "m" else "a"}:$malId"
            else -> return null
        }
        cache[key]?.let { return it }
        val variables = JSONObject()
        if (aniListId != null) {
            variables.put("id", aniListId)
        } else if (malId != null) {
            variables.put("idMal", malId)
            variables.put("type", if (isManga) "MANGA" else "ANIME")
        }
        val body = try {
            KitsugiApiBase.executeAniListQuery(QUERY, variables)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "AniList doğrulama verisi alınamadı ($key): ${e.message}")
            null
        }
        // Yanıt yoksa (429 cooldown, ağ, zaman aşımı) bu geçici bir hatadır: çağıran
        // "bulunamadı" sonucunu kalıcı önbelleğe yazmasın diye işaretlenir.
        if (body == null) FandomHttp.markFailure()
        val evidence = FandomAniListParser.parse(body, isManga) ?: return null
        cache[key] = evidence
        return evidence
    }
}
