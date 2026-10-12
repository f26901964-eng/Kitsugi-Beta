package com.kitsugi.animelist.data.fandom

import android.content.Context
import android.util.Log

/**
 * Elle doğrulanmış "kimlik → wiki" eşleme tablosu (`assets/fandom/fandom_overrides.json`).
 *
 * Wikidata'da Fandom bağlantısı eksik olan popüler yapımlar için kullanılır. Anahtar olarak
 * AniList kimliği, MAL kimliği (anime / manga ayrı) veya Wikidata QID'si verilebilir;
 * seri/franchise QID'si verilirse zincirdeki tüm sezonlar aynı wiki'ye bağlanır.
 */
data class FandomOverrideTable(
    val byAniList: Map<Int, String>,
    val byMalAnime: Map<Int, String>,
    val byMalManga: Map<Int, String>,
    val byQid: Map<String, String>
) {
    fun lookup(ids: FandomSourceIds, isManga: Boolean): String? {
        ids.aniListId?.let { id -> byAniList[id]?.let { return it } }
        ids.malId?.let { id -> (if (isManga) byMalManga[id] else byMalAnime[id])?.let { return it } }
        ids.wikidataId?.let { qid -> byQid[qid]?.let { return it } }
        return null
    }

    fun lookupQid(qid: String): String? = byQid[qid]

    val size: Int
        get() = byAniList.size + byMalAnime.size + byMalManga.size + byQid.size

    companion object {
        val EMPTY = FandomOverrideTable(emptyMap(), emptyMap(), emptyMap(), emptyMap())
    }
}

object FandomOverrides {

    private const val TAG = "FandomOverrides"
    const val ASSET_PATH = "fandom/fandom_overrides.json"

    @Volatile
    private var cached: FandomOverrideTable? = null

    fun table(context: Context): FandomOverrideTable {
        cached?.let { return it }
        val loaded = try {
            context.applicationContext.assets.open(ASSET_PATH).bufferedReader().use { parse(it.readText()) }
        } catch (e: Exception) {
            Log.w(TAG, "Eşleme tablosu okunamadı: ${e.message}")
            FandomOverrideTable.EMPTY
        }
        cached = loaded
        return loaded
    }

    /**
     * ```
     * { "version": 1, "entries": [
     *   { "wiki": "chainsaw-man", "anilist": [127230], "mal": [44511], "qid": ["Q104211858"] } ] }
     * ```
     */
    fun parse(json: String): FandomOverrideTable {
        val entries = parseJsonObject(json)?.optJSONArray("entries") ?: return FandomOverrideTable.EMPTY
        val byAniList = HashMap<Int, String>()
        val byMalAnime = HashMap<Int, String>()
        val byMalManga = HashMap<Int, String>()
        val byQid = HashMap<String, String>()
        for (entry in entries.objects()) {
            val wiki = FandomUrls.normalize(entry.str("wiki")) ?: continue
            if (FandomCandidateSelector.isBlocked(wiki)) continue
            entry.optJSONArray("anilist").positiveInts().forEach { byAniList[it] = wiki }
            entry.optJSONArray("mal").positiveInts().forEach { byMalAnime[it] = wiki }
            entry.optJSONArray("mal_manga").positiveInts().forEach { byMalManga[it] = wiki }
            entry.optJSONArray("qid").strings().filter { WikidataParsers.isQid(it) }.forEach { byQid[it] = wiki }
        }
        return FandomOverrideTable(byAniList, byMalAnime, byMalManga, byQid)
    }
}
