package com.kitsugi.animelist.data.fandom

import org.json.JSONObject

/** Doğrulama için kaynaktaki bir karakter. */
data class FandomCharacterEvidence(
    val aniListId: Int?,
    val fullName: String,
    val nativeName: String?,
    val alternatives: List<String>,
    val isMain: Boolean
)

/** AniList ilişkisi (önceki/sonraki sezon, kaynak eser…). */
data class FandomRelatedMedia(
    val aniListId: Int,
    val malId: Int?,
    val isManga: Boolean,
    val relationType: String
)

/**
 * Bir yapım hakkında kimlikle bilinen bilgiler (AniList). İlişki yürüyüşü ve son çare
 * isim yolunun doğrulaması bu verilerle yapılır.
 */
data class FandomMediaEvidence(
    val aniListId: Int?,
    val malId: Int?,
    val isManga: Boolean,
    /** Latin başlıklar: İngilizce, Romaji, eş anlamlılar (sıra korunur). */
    val titles: List<String>,
    val nativeTitle: String?,
    val creators: List<String>,
    val characters: List<FandomCharacterEvidence>,
    val relations: List<FandomRelatedMedia>
)

/** AniList GraphQL `Media` yanıtının ayrıştırılması (saf fonksiyonlar). */
object FandomAniListParser {

    /** Aynı franchise'ı paylaşan ilişki türleri, wiki bulma önceliğine göre. */
    val RELATION_PRIORITY = listOf(
        "PARENT", "PREQUEL", "SOURCE", "SEQUEL", "ADAPTATION", "SIDE_STORY",
        "ALTERNATIVE", "SUMMARY", "COMPILATION", "SPIN_OFF"
    )

    /** Eserin yaratıcısı sayılan roller (anime: Original Creator; manga: Story & Art…). */
    private val CREATOR_ROLE = Regex("(?i)^\\s*(original (creator|story|work)|story( ?& ?art)?|art|author)\\b")

    fun parse(json: String?, fallbackIsManga: Boolean): FandomMediaEvidence? {
        val media = parseJsonObject(json)?.optJSONObject("data")?.optJSONObject("Media") ?: return null
        val title = media.optJSONObject("title")
        val titles = buildList {
            title?.str("english")?.let { add(it) }
            title?.str("romaji")?.let { add(it) }
            addAll(media.optJSONArray("synonyms").strings())
        }.filter { FandomNameVariants.isMostlyLatin(it) }.distinct()
        val type = media.str("type")
        return FandomMediaEvidence(
            aniListId = media.positiveInt("id"),
            malId = media.positiveInt("idMal"),
            isManga = type?.equals("MANGA", ignoreCase = true) ?: fallbackIsManga,
            titles = titles,
            nativeTitle = title?.str("native"),
            creators = parseCreators(media.optJSONObject("staff")),
            characters = parseCharacters(media.optJSONObject("characters")),
            relations = parseRelations(media.optJSONObject("relations"))
        )
    }

    private fun parseCharacters(connection: JSONObject?): List<FandomCharacterEvidence> {
        val result = mutableListOf<FandomCharacterEvidence>()
        for (edge in connection?.optJSONArray("edges").objects()) {
            val node = edge.optJSONObject("node") ?: continue
            val name = node.optJSONObject("name") ?: continue
            val full = name.str("full") ?: continue
            result += FandomCharacterEvidence(
                aniListId = node.positiveInt("id"),
                fullName = full,
                nativeName = name.str("native"),
                alternatives = name.optJSONArray("alternative").strings()
                    .filter { FandomNameVariants.isMostlyLatin(it) }
                    .take(3),
                isMain = edge.str("role")?.equals("MAIN", ignoreCase = true) == true
            )
        }
        return result.distinctBy { it.aniListId?.toString() ?: it.fullName }
    }

    private fun parseCreators(connection: JSONObject?): List<String> {
        val creators = LinkedHashSet<String>()
        for (edge in connection?.optJSONArray("edges").objects()) {
            val role = edge.str("role") ?: continue
            if (!CREATOR_ROLE.containsMatchIn(role)) continue
            edge.optJSONObject("node")?.optJSONObject("name")?.str("full")?.let { creators += it }
        }
        return creators.take(3)
    }

    private fun parseRelations(connection: JSONObject?): List<FandomRelatedMedia> {
        val result = mutableListOf<FandomRelatedMedia>()
        for (edge in connection?.optJSONArray("edges").objects()) {
            val type = edge.str("relationType")?.uppercase() ?: continue
            if (type !in RELATION_PRIORITY) continue
            val node = edge.optJSONObject("node") ?: continue
            val id = node.positiveInt("id") ?: continue
            result += FandomRelatedMedia(
                aniListId = id,
                malId = node.positiveInt("idMal"),
                isManga = node.str("type")?.equals("MANGA", ignoreCase = true) == true,
                relationType = type
            )
        }
        return result
            .sortedBy { RELATION_PRIORITY.indexOf(it.relationType) }
            .distinctBy { it.aniListId }
    }
}
