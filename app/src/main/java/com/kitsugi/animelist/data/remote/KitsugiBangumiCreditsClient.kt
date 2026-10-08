package com.kitsugi.animelist.data.remote

import com.kitsugi.animelist.data.auth.BangumiApiClient
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.json.JSONArray
import org.json.JSONObject

/**
 * Bangumi karakter, kişi (声优 / スタッフ) ve konu kadrosu → Kitsugi modelleri.
 *
 * Kimlik sözleşmesi (arama sonuçlarıyla birebir aynı):
 *  - Karakter ve kişi kimlikleri **ham Bangumi ID'sidir** (`source = "bangumi"`); karakter ve
 *    kişi detay sayfaları bu ID ile açılır.
 *  - Medya (条目) bağlantıları **stableId** taşır (`subject_id + 500M`, bkz. [BangumiIdNamespace]);
 *    böylece ApiResultDetail akışı diğer Bangumi sonuçlarıyla aynı şekilde çalışır.
 *  - Dışarıdan gelen bir ID stableId aralığındaysa ham ID'ye çevrilir (savunmacı).
 *
 * Bu sınıftaki çağrılar token gerektirmeyen herkese açık uçlardır. Favori durumu ve favori
 * yazımı token'lı olarak ViewModel katmanında [BangumiApiClient] üzerinden yapılır.
 *
 * Kaynak: resmî Bangumi v0 OpenAPI (github.com/bangumi/api, open-api/v0.yaml). Bu kod şemadan
 * yeniden yazılmıştır; üçüncü taraf projelerden kod kopyalanmamıştır.
 */
object KitsugiBangumiCreditsClient {

    private const val SOURCE = BangumiIdNamespace.SOURCE
    private const val ALIAS_KEY = "别名"
    private val CN_NAME_KEYS = listOf("简体中文名", "中文名")

    /** Kitsugi'nin gösterebildiği türler: kitap (manga), anime ve 三次元 (dizi/film). */
    private val SUPPORTED_SUBJECT_TYPES = setOf(
        BangumiApiClient.SubjectType.BOOK,
        BangumiApiClient.SubjectType.ANIME,
        BangumiApiClient.SubjectType.REAL
    )

    // ── Konu kadrosu: Karakterler ve Kadro sekmeleri ─────────────────────────

    /**
     * `GET /v0/subjects/{id}/characters` — konunun karakterleri ve her karakterin seslendirmenleri.
     * Dönen karakter kimliği ham ID'dir; karakter detayı bu ID ile açılır.
     */
    suspend fun fetchSubjectCharacters(subjectIdOrStable: Int): List<KitsugiCharacter> {
        val subjectId = rawId(subjectIdOrStable)
        if (subjectId <= 0) return emptyList()
        val array = BangumiApiClient.getJsonArrayOrNull("/v0/subjects/$subjectId/characters")
            ?: return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val characterId = item.optInt("id", 0).takeIf { it > 0 } ?: return@mapNotNull null
            val actors = item.optJSONArray("actors")
            val voiceActors: List<KitsugiVoiceActor> = if (actors == null) {
                emptyList()
            } else {
                (0 until actors.length())
                    .mapNotNull { j -> actors.optJSONObject(j)?.let { parseVoiceActor(it) } }
                    .distinctBy { it.id }
            }
            val characterName = localizedName(item)
            KitsugiCharacter(
                id = characterId,
                name = characterName.display.ifBlank { "#$characterId" },
                role = characterRoleLabel(item.optString("relation")),
                imageUrl = pickImage(item.optJSONObject("images"), preferSmall = true),
                voiceActors = voiceActors,
                source = SOURCE,
                romanizedName = characterName.romaji,
                nativeName = characterName.native,
                englishName = characterName.english
            )
        }
    }

    /**
     * `GET /v0/subjects/{id}/persons` — konunun kadrosu (原画, 导演, 动画制作 ... ve seslendirmenler).
     * Aynı kişi birden çok görevde geçiyorsa görevler tek kartta birleştirilir.
     */
    suspend fun fetchSubjectStaff(subjectIdOrStable: Int): List<KitsugiStaff> {
        val subjectId = rawId(subjectIdOrStable)
        if (subjectId <= 0) return emptyList()
        val array = BangumiApiClient.getJsonArrayOrNull("/v0/subjects/$subjectId/persons")
            ?: return emptyList()
        val firstSeen = LinkedHashMap<Int, JSONObject>()
        val rolesByPerson = HashMap<Int, LinkedHashSet<String>>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val personId = item.optInt("id", 0).takeIf { it > 0 } ?: continue
            if (!firstSeen.containsKey(personId)) firstSeen[personId] = item
            val roles = rolesByPerson.getOrPut(personId) { LinkedHashSet() }
            val relation = item.optString("relation").trim()
            if (relation.isNotEmpty()) roles.add(relation)
        }
        return firstSeen.map { (personId, item) ->
            val roles: Set<String> = rolesByPerson[personId].orEmpty()
            val personName = localizedName(item)
            KitsugiStaff(
                id = personId,
                name = personName.display.ifBlank { "#$personId" },
                role = roles.joinToString(" · ")
                    .ifBlank { occupationLabel(item.optJSONArray("career")) }
                    .ifBlank { "Ekip Üyesi" },
                imageUrl = pickImage(item.optJSONObject("images"), preferSmall = true),
                source = SOURCE,
                romanizedName = personName.romaji,
                nativeName = personName.native,
                englishName = personName.english
            )
        }
    }

    // ── Karakter ve kişi detayları ───────────────────────────────────────────

    /**
     * Karakter detayı: `/v0/characters/{id}` + yer aldığı konular + seslendirmenler.
     *
     * Ana uç başarısızsa `null` döner (UI hata durumunu gösterir). Yan uçlar (konular ve
     * seslendirmenler) paralel çekilir; başarısız olursa yalnızca ilgili bölüm boş kalır.
     */
    suspend fun fetchCharacterDetail(
        characterId: Int,
        name: String?,
        fallbackImageUrl: String?
    ): KitsugiCharacterDetail? {
        val rawCharacterId = rawId(characterId)
        if (rawCharacterId <= 0) return null
        val root = BangumiApiClient.getCharacter(rawCharacterId) ?: return null
        val (subjects, persons) = coroutineScope {
            val subjectsCall = async {
                BangumiApiClient.getJsonArrayOrNull("/v0/characters/$rawCharacterId/subjects")
            }
            val personsCall = async {
                BangumiApiClient.getJsonArrayOrNull("/v0/characters/$rawCharacterId/persons")
            }
            subjectsCall.await() to personsCall.await()
        }
        val infobox = BangumiApiClient.parseInfobox(root.optJSONArray("infobox"))
        val localized = BangumiNameLocalizer.entity(
            name = root.optString("name"),
            nameCn = root.optString("name_cn", root.optString("nameCN", "")).ifBlank { infoboxFirst(infobox, CN_NAME_KEYS) ?: "" },
            aliases = infobox[ALIAS_KEY].orEmpty(),
            englishAliases = infobox["英文名"].orEmpty() + infobox["英語名"].orEmpty(),
            romajiAliases = infobox["罗马字"].orEmpty() + infobox["羅馬字"].orEmpty()
        )
        val primaryName = localized.display.takeIf { it.isNotBlank() && it != "?" }
            ?: name?.trim()?.ifBlank { null }
            ?: "Bilinmeyen"

        return KitsugiCharacterDetail(
            id = rawCharacterId,
            name = primaryName,
            nativeName = localized.native,
            alternativeNames = localized.alternatives,
            imageUrl = pickImage(root.optJSONObject("images"), preferSmall = false)
                ?: BangumiApiClient.absoluteImageUrl(fallbackImageUrl?.ifBlank { null }),
            gender = genderLabel(root.optString("gender"))
                ?: genderLabel(infoboxFirst(infobox, listOf("性别"))),
            age = null,
            birthday = birthdayText(root, infobox),
            bloodType = bloodTypeLabel(root.optInt("blood_type", 0))
                ?: infoboxFirst(infobox, listOf("血型")),
            biography = root.optString("summary").trim().ifBlank { null },
            voiceActors = persons?.let { parseCharacterVoiceActors(it) }.orEmpty(),
            mediaAppearances = subjects?.let { parseCharacterAppearances(it) }.orEmpty(),
            isFavourite = false,
            aniListId = null,
            source = SOURCE,
            romanizedName = localized.romaji,
            englishName = localized.english
        )
    }

    /**
     * Kişi detayı (seslendirmen / yapımcı / sanatçı): `/v0/persons/{id}` + yer aldığı konular
     * + canlandırdığı karakterler. Yan uç hataları yalnızca ilgili bölümü boş bırakır.
     */
    suspend fun fetchPersonDetail(personId: Int, name: String?): KitsugiStaffDetail? {
        val rawPersonId = rawId(personId)
        if (rawPersonId <= 0) return null
        val root = BangumiApiClient.getPerson(rawPersonId) ?: return null
        val (subjects, characters) = coroutineScope {
            val subjectsCall = async {
                BangumiApiClient.getJsonArrayOrNull("/v0/persons/$rawPersonId/subjects")
            }
            val charactersCall = async {
                BangumiApiClient.getJsonArrayOrNull("/v0/persons/$rawPersonId/characters")
            }
            subjectsCall.await() to charactersCall.await()
        }
        val infobox = BangumiApiClient.parseInfobox(root.optJSONArray("infobox"))
        val localized = BangumiNameLocalizer.entity(
            name = root.optString("name"),
            nameCn = root.optString("name_cn", root.optString("nameCN", "")).ifBlank { infoboxFirst(infobox, CN_NAME_KEYS) ?: "" },
            aliases = infobox[ALIAS_KEY].orEmpty(),
            englishAliases = infobox["英文名"].orEmpty() + infobox["英語名"].orEmpty(),
            romajiAliases = infobox["罗马字"].orEmpty() + infobox["羅馬字"].orEmpty()
        )
        val primaryName = localized.display.takeIf { it.isNotBlank() && it != "?" }
            ?: name?.trim()?.ifBlank { null }
            ?: "Bilinmeyen"

        return KitsugiStaffDetail(
            id = rawPersonId,
            name = primaryName,
            nativeName = localized.native,
            alternativeNames = localized.alternatives,
            imageUrl = pickImage(root.optJSONObject("images"), preferSmall = false),
            biography = root.optString("summary").trim().ifBlank { null },
            occupation = occupationLabel(root.optJSONArray("career")).ifBlank { null },
            birthday = birthdayText(root, infobox),
            age = null,
            gender = genderLabel(root.optString("gender"))
                ?: genderLabel(infoboxFirst(infobox, listOf("性别"))),
            homeTown = infoboxFirst(infobox, listOf("出生地", "出身地", "故乡")),
            characterRoles = characters?.let { parseCharacterRoles(it) }.orEmpty(),
            mediaWorks = subjects?.let { parseMediaWorks(it) }.orEmpty(),
            isFavourite = false,
            aniListId = null,
            romanizedName = localized.romaji,
            englishName = localized.english
        )
    }

    // ── JSON → model ayrıştırma ──────────────────────────────────────────────

    private fun parseVoiceActor(item: JSONObject): KitsugiVoiceActor? {
        val id = item.optInt("id", 0).takeIf { it > 0 } ?: return null
        val name = localizedName(item)
        return KitsugiVoiceActor(
            id = id,
            name = name.display.ifBlank { "#$id" },
            // Bangumi, seslendirmenin dilini ayrıca vermez; bilinmeyen değer olarak bırakılır.
            language = "",
            imageUrl = pickImage(item.optJSONObject("images"), preferSmall = true),
            source = SOURCE,
            romanizedName = name.romaji,
            nativeName = name.native,
            englishName = name.english
        )
    }

    private fun parseCharacterVoiceActors(array: JSONArray): List<KitsugiVoiceActor> {
        val seen = HashSet<Int>()
        return (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val personId = item.optInt("id", 0).takeIf { it > 0 } ?: return@mapNotNull null
            if (!seen.add(personId)) return@mapNotNull null
            parseVoiceActor(item)
        }
    }

    /** `/v0/characters/{id}/subjects` → karakterin yer aldığı anime / manga / dizi kayıtları. */
    private fun parseCharacterAppearances(array: JSONArray): List<KitsugiCharacterMediaAppearance> {
        val seen = HashSet<Int>()
        return (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val subjectId = item.optInt("id", 0).takeIf { it > 0 } ?: return@mapNotNull null
            val subjectType = item.optInt("type", 0)
            if (subjectType !in SUPPORTED_SUBJECT_TYPES) return@mapNotNull null
            val mediaId = BangumiIdNamespace.stableIdFromRaw(subjectId) ?: return@mapNotNull null
            if (!seen.add(subjectId)) return@mapNotNull null
            val title = BangumiNameLocalizer.entity(item.optString("name"), item.optString("name_cn"))
            KitsugiCharacterMediaAppearance(
                mediaId = mediaId,
                title = title.display.ifBlank { "#$subjectId" },
                imageUrl = BangumiApiClient.absoluteImageUrl(item.optString("image").trim().ifBlank { null }),
                mediaType = mediaTypeKey(subjectType),
                characterRole = characterRoleLabel(item.optString("staff")),
                source = SOURCE,
                titleEnglish = title.english,
                titleJapanese = title.native,
                titleRomaji = title.romaji
            )
        }
    }

    /** `/v0/persons/{id}/characters` → kişinin canlandırdığı karakterler (konu bağlamıyla). */
    private fun parseCharacterRoles(array: JSONArray): List<KitsugiStaffCharacterRole> {
        val seen = HashSet<Pair<Int, Int>>()
        return (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val characterId = item.optInt("id", 0).takeIf { it > 0 } ?: return@mapNotNull null
            val subjectId = item.optInt("subject_id", 0).takeIf { it > 0 } ?: return@mapNotNull null
            val subjectType = item.optInt("subject_type", 0)
            if (subjectType !in SUPPORTED_SUBJECT_TYPES) return@mapNotNull null
            val mediaId = BangumiIdNamespace.stableIdFromRaw(subjectId) ?: return@mapNotNull null
            if (!seen.add(characterId to subjectId)) return@mapNotNull null
            val characterName = localizedName(item)
            val mediaTitle = BangumiNameLocalizer.entity(
                name = item.optString("subject_name"),
                nameCn = item.optString("subject_name_cn")
            )
            KitsugiStaffCharacterRole(
                characterId = characterId,
                characterName = characterName.display.ifBlank { "#$characterId" },
                characterImageUrl = pickImage(item.optJSONObject("images"), preferSmall = true),
                characterSource = SOURCE,
                mediaId = mediaId,
                mediaTitle = mediaTitle.display.ifBlank { "#$subjectId" },
                mediaImageUrl = null,
                mediaType = mediaTypeKey(subjectType),
                characterRole = characterRoleLabel(item.optString("staff")),
                mediaSource = SOURCE,
                characterRomanizedName = characterName.romaji,
                characterNativeName = characterName.native,
                characterEnglishName = characterName.english,
                mediaTitleEnglish = mediaTitle.english,
                mediaTitleJapanese = mediaTitle.native,
                mediaTitleRomaji = mediaTitle.romaji
            )
        }
    }

    /** `/v0/persons/{id}/subjects` → kişinin çalıştığı anime / manga / dizi kayıtları. */
    private fun parseMediaWorks(array: JSONArray): List<KitsugiStaffMediaWork> {
        val seen = HashSet<Int>()
        return (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val subjectId = item.optInt("id", 0).takeIf { it > 0 } ?: return@mapNotNull null
            val subjectType = item.optInt("type", 0)
            if (subjectType !in SUPPORTED_SUBJECT_TYPES) return@mapNotNull null
            val mediaId = BangumiIdNamespace.stableIdFromRaw(subjectId) ?: return@mapNotNull null
            if (!seen.add(subjectId)) return@mapNotNull null
            val title = BangumiNameLocalizer.entity(item.optString("name"), item.optString("name_cn"))
            KitsugiStaffMediaWork(
                mediaId = mediaId,
                mediaTitle = title.display.ifBlank { "#$subjectId" },
                mediaImageUrl = BangumiApiClient.absoluteImageUrl(item.optString("image").trim().ifBlank { null }),
                mediaType = mediaTypeKey(subjectType),
                staffRole = item.optString("staff").trim().ifBlank { "Ekip Üyesi" },
                source = SOURCE,
                titleEnglish = title.english,
                titleJapanese = title.native,
                titleRomaji = title.romaji
            )
        }
    }

    // ── Yardımcılar ──────────────────────────────────────────────────────────

    /** V0 kısa karakter/kişi nesnesinin dil alanlarını korur. */
    private fun localizedName(item: JSONObject): BangumiLocalizedName = BangumiNameLocalizer.entity(
        name = item.optString("name"),
        nameCn = item.optString("name_cn", item.optString("nameCN", ""))
    )

    /** stableId (500M+) gelirse ham Bangumi ID'sine çevirir; ham ID'yi olduğu gibi döndürür. */
    private fun rawId(idOrStable: Int): Int = BangumiIdNamespace.rawIdFromStable(idOrStable) ?: idOrStable

    /** Bangumi konu türü → Kitsugi medya türü anahtarı ([KitsugiCharacterMediaAppearance.mediaType]). */
    private fun mediaTypeKey(subjectType: Int): String = when (subjectType) {
        BangumiApiClient.SubjectType.BOOK -> "manga"
        BangumiApiClient.SubjectType.REAL -> "tv"
        else -> "anime"
    }

    /** Bangumi karakter rolü (主角 / 配角 / 客串) → Türkçe etiket. Bilinmeyen değer aynen geçer. */
    private fun characterRoleLabel(raw: String?): String {
        return when (val value = raw?.trim().orEmpty()) {
            "主角" -> "Ana Karakter"
            "配角" -> "Yardımcı Karakter"
            "客串" -> "Konuk Karakter"
            "" -> "Bilinmeyen"
            else -> value
        }
    }

    /** Bangumi `career` listesi → Türkçe meslek etiketleri. */
    private fun occupationLabel(career: JSONArray?): String {
        if (career == null) return ""
        return (0 until career.length())
            .mapNotNull { index ->
                when (val value = career.optString(index).trim()) {
                    "" -> null
                    "producer" -> "Yapımcı"
                    "mangaka" -> "Mangaka"
                    "artist" -> "Sanatçı"
                    "seiyu" -> "Seslendirmen"
                    "writer" -> "Yazar"
                    "illustrator" -> "İllüstratör"
                    "actor" -> "Oyuncu"
                    else -> value
                }
            }
            .distinct()
            .joinToString(" · ")
    }

    private fun genderLabel(raw: String?): String? {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty() || value == "null") return null
        return when (value.lowercase()) {
            "male", "男" -> "Erkek"
            "female", "女" -> "Kadın"
            else -> value
        }
    }

    private fun bloodTypeLabel(code: Int): String? = when (code) {
        1 -> "A"
        2 -> "B"
        3 -> "AB"
        4 -> "O"
        else -> null
    }

    /**
     * Doğum tarihi metni. Tam tarih `YYYY-MM-DD` olarak verilir (yaş hesabı için);
     * yalnızca ay/gün varsa JSON biçimi kullanılır — [KitsugiDateUtils] yıl alanını opsiyonel okur.
     */
    private fun birthdayText(root: JSONObject, infobox: Map<String, List<String>>): String? {
        val year = root.optInt("birth_year", 0)
        val month = root.optInt("birth_mon", 0)
        val day = root.optInt("birth_day", 0)
        val hasMonthDay = month in 1..12 && day in 1..31
        if (year > 0 && hasMonthDay) {
            return "$year-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"
        }
        if (hasMonthDay) {
            return JSONObject().put("month", month).put("day", day).toString()
        }
        return infoboxFirst(infobox, listOf("生日"))
    }

    /** Verilen anahtarlardan ilk dolu infobox değerini döndürür. */
    private fun infoboxFirst(infobox: Map<String, List<String>>, keys: List<String>): String? {
        for (key in keys) {
            val value = infobox[key]?.firstOrNull()?.trim()
            if (!value.isNullOrEmpty()) return value
        }
        return null
    }

    /**
     * Bangumi görseli: `medium`/`large` tercih edilir (liste kartları için `medium` önce gelir).
     * JSON `null` alanları atlanır; mutlak olmayan URL'ler [BangumiApiClient.absoluteImageUrl] ile tamamlanır.
     */
    private fun pickImage(images: JSONObject?, preferSmall: Boolean): String? {
        if (images == null) return null
        val order = if (preferSmall) {
            listOf("medium", "large", "small", "grid")
        } else {
            listOf("large", "medium", "small", "grid")
        }
        for (key in order) {
            if (images.isNull(key)) continue
            val url = images.optString(key).trim()
            if (url.isNotEmpty()) return BangumiApiClient.absoluteImageUrl(url)
        }
        return null
    }
}
