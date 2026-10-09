package com.kitsugi.animelist.data.remote

import com.kitsugi.animelist.KitsugiApplication
import com.kitsugi.animelist.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.net.URL
import com.kitsugi.animelist.utils.*

class KitsugiStaffClient {

    private companion object {
        /**
         * Bangumi ekip adları için AniList köprüsü süre tavanı. Süre dolarsa ya da istek
         * başarısız olursa liste orijinal (özgün) adlarla döner.
         */
        const val BANGUMI_NAME_BRIDGE_TIMEOUT_MS = 8_000L
    }

    suspend fun fetchStaff(
        source: String,
        externalId: Int?,
        mediaType: MediaType,
        tmdbId: Int? = null,
        realMalId: Int? = null
    ): List<KitsugiStaff> {
        return withContext(Dispatchers.IO) {
            if (externalId == null || externalId <= 0) {
                return@withContext emptyList()
            }

            when (MalJikanMediaSupport.canonicalSource(source)) {
                "bangumi", "bgm" -> {
                    // 1) Bangumi'nin kendi ekip listesi (p1 /staffs/persons veya v0 /subjects/{id}/persons).
                    val native = runCatching { KitsugiBangumiDetailClient.fetchStaff(externalId, mediaType) }
                        .getOrNull().orEmpty()
                        .ifEmpty {
                            runCatching { KitsugiBangumiCreditsClient.fetchSubjectStaff(externalId) }.getOrNull().orEmpty()
                        }
                    if (native.isNotEmpty()) {
                        // Bangumi liste uçları infobox döndürmez → ekip adları kanji kalır.
                        // AniList köprüsüyle (özgün ada birebir eşleme) romaji/İngilizce
                        // adlar doldurulur; başlık dili ROMAJI/ENGLISH olduğunda gösterilir.
                        val malIdForNames = KitsugiBangumiDetailClient.sanitizeMalId(realMalId)
                            ?: runCatching { KitsugiBangumiDetailClient.resolveCrossIds(externalId, mediaType) }
                                .getOrNull()?.malId
                        return@withContext enrichBangumiStaffNames(native, mediaType, malIdForNames)
                    }

                    // 2) Yedek: Çözülen MAL kimliğiyle MAL/Jikan ekibi (stableId MAL ID değildir).
                    val cross = runCatching {
                        KitsugiBangumiDetailClient.resolveCrossIds(externalId, mediaType)
                    }.getOrNull()
                    val malId = cross?.malId ?: KitsugiBangumiDetailClient.sanitizeMalId(realMalId)
                    if (malId != null && malId > 0 && (mediaType == MediaType.Anime || mediaType == MediaType.Manga)) {
                        val jikanList = fetchStaff("jikan", malId, mediaType, null, null)
                        if (jikanList.isNotEmpty()) return@withContext jikanList
                    }
                    val resolvedTmdb = tmdbId ?: cross?.tmdbId
                    if (resolvedTmdb != null && resolvedTmdb > 0 && mediaType != MediaType.Manga) {
                        val (_, tmdbStaff) = TmdbApiClient().fetchCredits(resolvedTmdb, mediaType == MediaType.Movie)
                        if (tmdbStaff.isNotEmpty()) return@withContext tmdbStaff
                    }
                    emptyList()
                }
                "shikimori" -> {
                    // 1) Shikimori'nin kendi `/roles` ucu personel kayıtlarını (yönetmen,
                    //    senarist …) ve seiyuu kayıtlarını içerir.
                    val shikiStaff = KitsugiShikimoriClient.fetchStaff(mediaType, externalId)
                    if (shikiStaff.isNotEmpty()) return@withContext shikiStaff

                    // 2) Shikimori'de personel kaydı YOKSA (küçük/az bakımlı yapımlarda sık
                    //    görülür) Ekip sekmesi boş kalmasın: gerçek MAL ID'si çözülüp
                    //    MAL/Jikan personel listesi gösterilir. Not: Shikimori ID'si MAL ID
                    //    DEĞİLDİR; bu yüzden ARM/Shikimori API eşlemesi kullanılır.
                    val malId = DetailCache.getMediaDetail("shikimori", externalId)?.realMalId?.takeIf { it > 0 }
                        ?: KitsugiIdResolver.resolveMalIdFromShikimori(externalId)
                    if (malId != null && malId > 0) {
                        val jikanList = fetchStaff(
                            source = "jikan",
                            externalId = malId,
                            mediaType = if (mediaType == MediaType.Manga) MediaType.Manga else MediaType.Anime
                        )
                        if (jikanList.isNotEmpty()) return@withContext jikanList
                    }
                    shikiStaff
                }
                "simkl" -> {
                    val malId = realMalId ?: DetailCache.getMediaDetail("simkl", externalId)?.realMalId
                    if (malId != null && malId > 0) {
                        val malList = fetchStaff("jikan", malId, mediaType, null, null)
                        if (malList.isNotEmpty()) return@withContext malList
                    }
                    val resolvedTmdb = tmdbId ?: run {
                        val malIdForResolve = realMalId ?: DetailCache.getMediaDetail("simkl", externalId)?.realMalId
                        KitsugiIdResolver.resolveIds(malId = malIdForResolve, aniListId = null, tmdbId = tmdbId).tmdbId
                    }
                    if (resolvedTmdb != null && resolvedTmdb > 0) {
                        val isMovie = mediaType == MediaType.Movie
                        val (_, tmdbStaff) = TmdbApiClient().fetchCredits(resolvedTmdb, isMovie)
                        if (tmdbStaff.isNotEmpty()) return@withContext tmdbStaff
                    }
                    emptyList()
                }

                "tmdb" -> {
                    val effectiveTmdbId = tmdbId ?: externalId
                    if (effectiveTmdbId > 0) {
                        val isMovie = mediaType == MediaType.Movie
                        val (_, tmdbStaff) = TmdbApiClient().fetchCredits(effectiveTmdbId, isMovie)
                        tmdbStaff
                    } else emptyList()
                }
                "jikan", "mal" -> {
                    val jikanId = MalJikanMediaSupport.resolveMalId(source, externalId, realMalId)
                        ?: return@withContext emptyList()
                    val endpoint = MalJikanMediaSupport.jikanEndpoint(mediaType)
                    val url = URL("https://api.jikan.moe/v4/$endpoint/$jikanId/staff")
                    val jikanList = runCatching {
                        KitsugiApiBase.runWithRateLimit {
                            // 429/5xx dayanıklı — rate-limit'te ekip listesi eksik dönmüş olmasın
                            val response = KitsugiApiBase.executeGetRequestResilient(url) ?: return@runWithRateLimit emptyList()
                            val root = JSONObject(response)
                            val data = root.optJSONArray("data") ?: return@runWithRateLimit emptyList()
                            val list = mutableListOf<KitsugiStaff>()
                            for (i in 0 until data.length()) {
                                val item = data.optJSONObject(i) ?: continue
                                val personObj = item.optJSONObject("person") ?: continue
                                val id = personObj.optInt("mal_id")
                                val name = (personObj.optNullableString("name") ?: "Bilinmeyen").toFriendlyName()
                                val positions = item.optJSONArray("positions")
                                val role = if (positions != null && positions.length() > 0) {
                                    val posList = mutableListOf<String>()
                                    for (j in 0 until positions.length()) {
                                        val pos = positions.optString(j)
                                        if (pos.isNotBlank() && pos != "null") posList.add(pos.toTurkishStaffRole())
                                    }
                                    posList.joinToString(", ")
                                } else "Ekip Üyesi"
                                val imageUrl = personObj.optJSONObject("images")?.optJSONObject("jpg")?.optNullableString("image_url")
                                list.add(KitsugiStaff(id, name, role, imageUrl, source = "jikan"))
                            }
                            list
                        }
                    }.getOrElse { emptyList() }

                    if (jikanList.isNotEmpty()) {
                        jikanList
                    } else {
                        // Jikan boşsa aynı doğrulanmış MAL ID'siyle AniList'i dene; bu
                        // kimlik köprüsü ARM/fuzzy aramadan daha hızlı ve daha güvenlidir.
                        val aniListList = runCatching {
                            fetchStaff("anilist", jikanId, mediaType)
                        }.getOrNull().orEmpty()
                        if (aniListList.isNotEmpty()) {
                            aniListList
                        } else {
                            android.util.Log.w("KitsugiStaffClient", "Jikan/AniList ekip listesi boş; Shikimori fallback deneniyor")
                            // Shikimori kendi ID'sini bekler — MAL ID'si ARM ile çevrilir.
                            val shikiId = KitsugiIdResolver.resolveShikimoriIdFromMal(jikanId)
                            if (shikiId != null && shikiId > 0) {
                                KitsugiShikimoriClient.fetchStaff(mediaType, shikiId)
                            } else {
                                emptyList()
                            }
                        }
                    }
                }

                "kitsu" -> {
                    // externalId = kitsuStableId = kitsuNumericId + 300_000_000
                    val kitsuOffset = 300_000_000
                    val kitsuNumericId = if (externalId >= kitsuOffset) externalId - kitsuOffset else externalId
                    android.util.Log.d("KitsugiStaffClient", "Kitsu staff fetch: stableId=$externalId, kitsuNumericId=$kitsuNumericId")
                    if (kitsuNumericId <= 0) return@withContext emptyList()

                    // Kitsu has no native staff endpoint — resolve to AniList/Jikan via ARM
                    val resolved = runCatching {
                        KitsugiIdResolver.resolveIds(malId = realMalId, aniListId = null, kitsuId = kitsuNumericId)
                    }.getOrNull()

                    val aniListId = resolved?.aniListId
                    if (aniListId != null && aniListId > 0) {
                        // Encode aniListId with the 100M offset so fetchStaff("anilist") handles it correctly
                        val encoded = 100_000_000 + aniListId
                        val aniList = fetchStaff("anilist", encoded, mediaType, tmdbId, realMalId ?: resolved?.malId)
                        if (aniList.isNotEmpty()) return@withContext aniList.map { it.copy(source = "kitsu") }
                    }

                    val jikanId = realMalId?.takeIf { it > 0 } ?: resolved?.malId?.takeIf { it > 0 }
                    if (jikanId != null) {
                        val jikanList = fetchStaff("jikan", jikanId, mediaType, tmdbId, null)
                        if (jikanList.isNotEmpty()) return@withContext jikanList.map { it.copy(source = "kitsu") }
                    }

                    android.util.Log.w("KitsugiStaffClient", "Kitsu staff: AniList ve Jikan fallback boş döndü (kitsuId=$kitsuNumericId)")
                    emptyList()
                }

                "anilist" -> {
                    val aniListId = if (externalId >= 100_000_000) externalId - 100_000_000 else null
                    val idParam = if (aniListId != null) "\$id: Int" else "\$idMal: Int"
                    val idFilter = if (aniListId != null) "id: \$id" else "idMal: \$idMal"
                    val query = """
                        query (${'$'}type: MediaType, $idParam) {
                            Media($idFilter, type: ${'$'}type) {
                                staff(page: 1, perPage: 24) {
                                    edges {
                                        role
                                        node {
                                            id
                                            name { userPreferred full first middle last native alternative }
                                            image { medium }
                                        }
                                    }
                                }
                            }
                        }
                    """.trimIndent()
                    val variables = JSONObject().put("type", MalJikanMediaSupport.aniListMediaType(mediaType))
                    if (aniListId != null) variables.put("id", aniListId) else variables.put("idMal", externalId)

                    runCatching {
                        val response = KitsugiApiBase.executeAniListQuery(query, variables) ?: return@runCatching emptyList()
                        val root = JSONObject(response)
                        val edges = root.optJSONObject("data")?.optJSONObject("Media")?.optJSONObject("staff")?.optJSONArray("edges") ?: return@runCatching emptyList()
                        val list = mutableListOf<KitsugiStaff>()
                        for (i in 0 until edges.length()) {
                            val edge = edges.optJSONObject(i) ?: continue
                            val role = (edge.optNullableString("role") ?: "Ekip Üyesi").toTurkishStaffRole()
                            val node = edge.optJSONObject("node") ?: continue
                            val id = node.optInt("id")
                            val nameObj = node.optJSONObject("name")
                            val personName = nameObj.aniListPersonName()
                            val name = personName.preferred
                            val imageUrl = node.optJSONObject("image")?.optNullableString("medium")
                            list.add(KitsugiStaff(id, name, role, imageUrl, source = "anilist", romanizedName = personName.romanized, nativeName = personName.native))
                        }
                        list
                    }.getOrElse { emptyList() }
                }

                else -> emptyList()
            }
        }
    }

    /**
     * Bangumi ekip listesindeki CJK adlara AniList köprüsüyle (özgün ada birebir eşleme)
     * romaji/İngilizce karşılık ekler. Eşleşme bulunamazsa liste olduğu gibi döner.
     * Tek önbellekli AniList sorgusu kullanılır; süre tavanı/başarısızlıkta orijinal
     * liste döner (sekme beklemez).
     */
    private suspend fun enrichBangumiStaffNames(
        staff: List<KitsugiStaff>,
        mediaType: MediaType,
        malId: Int?
    ): List<KitsugiStaff> {
        if (staff.isEmpty()) return staff
        val needsEnrich = staff.any { PreferenceHelpers.hasCjkCharacters(it.name) }
        if (!needsEnrich || malId == null || malId <= 0) return staff
        val names = runCatching {
            withTimeoutOrNull(BANGUMI_NAME_BRIDGE_TIMEOUT_MS) {
                KitsugiAniListPersonBridge.fetchMediaNames(malId, mediaType)
            }
        }.getOrNull() ?: return staff
        if (names == null) return staff
        return staff.map { person ->
            if (!PreferenceHelpers.hasCjkCharacters(person.name)) return@map person
            val variant = KitsugiAniListPersonBridge.findByNative(names.staff, person.nativeName, person.name)
                ?: return@map person
            person.copy(
                romanizedName = person.romanizedName ?: variant.romaji,
                englishName = person.englishName ?: variant.english
            )
        }
    }

    suspend fun fetchStaffDetail(
        source: String,
        staffId: Int,
        name: String? = null
    ): KitsugiStaffDetail? {
        return withContext(Dispatchers.IO) {
            if (staffId <= 0) return@withContext null
            when (MalJikanMediaSupport.canonicalSource(source)) {
                "bangumi", "bgm" -> {
                    KitsugiBangumiCreditsClient.fetchPersonDetail(staffId, name)
                        ?: KitsugiBangumiDetailClient.fetchStaffDetail(staffId)
                }
                "shikimori" -> {
                    KitsugiShikimoriClient.fetchStaffDetail(staffId)
                }
                "jikan", "mal" -> {
                    var detail: KitsugiStaffDetail? = null
                    var networkError: Throwable? = null

                    // 1. MAL / Jikan API
                    val url = URL("https://api.jikan.moe/v4/people/$staffId/full")
                    val jikanRes = runCatching {
                        KitsugiApiBase.runWithRateLimit {
                            val response = KitsugiApiBase.executeGetRequestOrThrow(url)
                            val root = JSONObject(response)
                            val data = root.optJSONObject("data") ?: return@runWithRateLimit null

                            val staffName = (data.optNullableString("name") ?: "Bilinmeyen").toFriendlyName()
                            val nativeName = data.optNullableString("given_name") ?: data.optNullableString("family_name")

                            val alternativeNames = mutableListOf<String>()
                            val altArray = data.optJSONArray("alternate_names")
                            if (altArray != null) {
                                for (i in 0 until altArray.length()) {
                                    val alt = altArray.optString(i)
                                    if (alt.isNotBlank() && alt != "null") alternativeNames.add(alt)
                                }
                            }

                            val imageUrl = data.optJSONObject("images")?.optJSONObject("jpg")?.optNullableString("image_url")
                            val biography = data.optNullableString("about")?.cleanApiText()?.takeIf { it.isNotBlank() }

                            val rawBirthday = data.optNullableString("birthday")
                            val (birthday, age) = com.kitsugi.animelist.utils.KitsugiDateUtils.formatBirthdayAndCalculateAge(rawBirthday, null)

                            val characterRoles = mutableListOf<KitsugiStaffCharacterRole>()
                            val voicesArray = data.optJSONArray("voices")
                            if (voicesArray != null) {
                                for (i in 0 until voicesArray.length()) {
                                    val item = voicesArray.optJSONObject(i) ?: continue
                                    val role = (item.optNullableString("role") ?: "Bilinmeyen").toTurkishCharacterRole()

                                    val animeObj = item.optJSONObject("anime")
                                    val animeId = animeObj?.optInt("mal_id") ?: 0
                                    val animeTitle = animeObj?.optNullableString("title") ?: "Bilinmeyen"
                                    val animeImg = animeObj?.optJSONObject("images")?.optJSONObject("jpg")?.optNullableString("image_url")

                                    val charObj = item.optJSONObject("character") ?: continue
                                    val charId = charObj.optInt("mal_id")
                                    val charName = (charObj.optNullableString("name") ?: "Bilinmeyen").toFriendlyName()
                                    val charImg = charObj.optJSONObject("images")?.optJSONObject("jpg")?.optNullableString("image_url")

                                    if (charId <= 0 && charName.isBlank()) continue

                                    characterRoles.add(KitsugiStaffCharacterRole(
                                        characterId = charId,
                                        characterName = charName.ifBlank { "Bilinmeyen" },
                                        characterImageUrl = charImg,
                                        characterSource = "jikan",
                                        mediaId = animeId,
                                        mediaTitle = animeTitle,
                                        mediaImageUrl = animeImg,
                                        mediaType = "anime".toTurkishMediaTypeString(),
                                        characterRole = role,
                                        mediaSource = "jikan"
                                    ))
                                }
                            }

                            val mediaWorks = mutableListOf<KitsugiStaffMediaWork>()
                            val animeWorks = data.optJSONArray("anime")
                            if (animeWorks != null) {
                                for (i in 0 until animeWorks.length()) {
                                    val item = animeWorks.optJSONObject(i) ?: continue
                                    val position = (item.optNullableString("position") ?: "Ekip Üyesi").toTurkishStaffRole()
                                    val animeObj = item.optJSONObject("anime")
                                    val animeId = animeObj?.optInt("mal_id") ?: 0
                                    val animeTitle = animeObj?.optNullableString("title") ?: "Bilinmeyen"
                                    val animeImg = animeObj?.optJSONObject("images")?.optJSONObject("jpg")?.optNullableString("image_url")
                                    mediaWorks.add(KitsugiStaffMediaWork(
                                        mediaId = animeId,
                                        mediaTitle = animeTitle,
                                        mediaImageUrl = animeImg,
                                        mediaType = "anime".toTurkishMediaTypeString(),
                                        staffRole = position,
                                        source = "jikan"
                                    ))
                                }
                            }

                            KitsugiStaffDetail(
                                id = staffId,
                                name = staffName,
                                nativeName = nativeName,
                                alternativeNames = alternativeNames,
                                imageUrl = imageUrl,
                                biography = biography,
                                occupation = null,
                                birthday = birthday,
                                age = age,
                                gender = null,
                                homeTown = null,
                                characterRoles = characterRoles,
                                mediaWorks = mediaWorks
                            )
                        }
                    }
                    jikanRes.onSuccess {
                        detail = it
                    }.onFailure { err ->
                        if (err !is ResourceNotFoundException) {
                            networkError = err
                        }
                        android.util.Log.e("KitsugiStaffClient", "Jikan fetch failed: ${err.message}", err)
                    }

                    // 2. Shikimori Fallback / Merge
                    runCatching {
                        KitsugiShikimoriClient.fetchStaffDetail(staffId)
                    }.onSuccess { shikiDetail ->
                        if (shikiDetail != null) {
                            detail = if (detail == null) shikiDetail else detail!!.mergeWith(shikiDetail)
                        }
                    }.onFailure { err ->
                        android.util.Log.e("KitsugiStaffClient", "Shikimori staff detail fallback failed: ${err.message}", err)
                    }

                    // 3. AniList Fallback / Merge
                    val targetName = detail?.name ?: name
                    if (!targetName.isNullOrBlank()) {
                        runCatching {
                            fetchAniListStaffByName(targetName)
                        }.onSuccess { aniListDetail ->
                            if (aniListDetail != null) {
                                detail = if (detail == null) aniListDetail else detail!!.mergeWith(aniListDetail)
                            }
                        }.onFailure { err ->
                            android.util.Log.e("KitsugiStaffClient", "AniList staff detail fallback failed: ${err.message}", err)
                        }
                    }

                    if (detail == null && networkError != null) {
                        throw networkError!!
                    }
                    detail
                }

                "anilist" -> {
                    val query = """
                        query (${'$'}id: Int) {
                            Staff(id: ${'$'}id) {
                                id
                                isFavourite
                                name {
                                    userPreferred
                                    full first middle last
                                    native
                                    alternative
                                }
                                image { large }
                                description
                                primaryOccupations
                                gender
                                dateOfBirth { year month day }
                                age
                                homeTown
                                characterMedia(page: 1, perPage: 24) {
                                    edges {
                                        characterRole
                                        node {
                                            id
                                            idMal
                                            title { userPreferred english romaji native }
                                            coverImage { large }
                                            type
                                        }
                                        characters {
                                            id
                                            name { userPreferred full first middle last native alternative }
                                            image { medium }
                                        }
                                    }
                                }
                                staffMedia(page: 1, perPage: 24) {
                                    edges {
                                        staffRole
                                        node {
                                            id
                                            idMal
                                            title { userPreferred english romaji native }
                                            coverImage { large }
                                            type
                                        }
                                    }
                                }
                            }
                        }
                    """.trimIndent()
                    val variables = JSONObject().put("id", staffId)
                    runCatching {
                        val response = KitsugiApiBase.executeAniListQuery(query, variables) ?: return@runCatching null
                        val root = JSONObject(response)
                        val data = root.optJSONObject("data")?.optJSONObject("Staff") ?: return@runCatching null

                        val nameObj = data.optJSONObject("name")
                        val personName = nameObj.aniListPersonName()
                        val name = personName.preferred
                        val nativeName = nameObj?.optNullableString("native")

                        val alternativeNames = mutableListOf<String>()
                        val altArray = nameObj?.optJSONArray("alternative")
                        if (altArray != null) {
                            for (i in 0 until altArray.length()) {
                                val alt = altArray.optString(i)
                                if (alt.isNotBlank() && alt != "null") alternativeNames.add(alt)
                            }
                        }

                        val imageUrl = data.optJSONObject("image")?.optNullableString("large")
                        val biography = data.optNullableString("description")?.cleanApiText()?.takeIf { it.isNotBlank() }

                        val gender = data.optNullableString("gender")?.toTurkishGender()
                        val age = data.optNullableString("age")
                        val homeTown = data.optNullableString("homeTown")

                        val occupArray = data.optJSONArray("primaryOccupations")
                        val occupation = if (occupArray != null && occupArray.length() > 0) {
                            val list = mutableListOf<String>()
                            for (i in 0 until occupArray.length()) {
                                val occ = occupArray.optString(i)
                                if (occ.isNotBlank() && occ != "null") list.add(occ.toTurkishStaffRole())
                            }
                            list.joinToString(", ")
                        } else null

                        val dobObj = data.optJSONObject("dateOfBirth")
                        val birthday = if (dobObj != null) {
                            val d = dobObj.optInt("day", 0)
                            val m = dobObj.optInt("month", 0)
                            val y = dobObj.optInt("year", 0)
                            if (d > 0 && m > 0) {
                                if (y > 0) "$d/$m/$y" else "$d/$m"
                            } else null
                        } else null

                        val characterRoles = mutableListOf<KitsugiStaffCharacterRole>()
                        val charMediaEdges = data.optJSONObject("characterMedia")?.optJSONArray("edges")
                        if (charMediaEdges != null) {
                            for (i in 0 until charMediaEdges.length()) {
                                val edge = charMediaEdges.optJSONObject(i) ?: continue
                                val charRole = (edge.optNullableString("characterRole") ?: "Bilinmeyen").toTurkishCharacterRole()
                                val mediaNode = edge.optJSONObject("node") ?: continue
                                val mediaId = mediaNode.optInt("idMal").takeIf { it > 0 } ?: (100_000_000 + mediaNode.optInt("id"))
                                val mediaTitleObj = mediaNode.optJSONObject("title")
                                val mediaTitle = mediaTitleObj?.optNullableString("userPreferred") ?: "Bilinmeyen"
                                val mediaTitleEnglish = mediaTitleObj?.optNullableString("english")
                                val mediaTitleNative = mediaTitleObj?.optNullableString("native")
                                val mediaTitleRomaji = mediaTitleObj?.optNullableString("romaji")
                                val mediaImg = mediaNode.optJSONObject("coverImage")?.optNullableString("large")
                                val mediaType = mediaNode.optNullableString("type").orEmpty().toTurkishMediaTypeString()

                                val chars = edge.optJSONArray("characters")
                                if (chars != null && chars.length() > 0) {
                                    val charObj = chars.optJSONObject(0) ?: continue
                                    val charId = charObj.optInt("id")
                                    val charPersonName = charObj.optJSONObject("name").aniListPersonName()
                                    val charName = charPersonName.preferred
                                    val charImg = charObj.optJSONObject("image")?.optNullableString("medium")

                                    characterRoles.add(KitsugiStaffCharacterRole(
                                        characterId = charId,
                                        characterName = charName,
                                        characterRomanizedName = charPersonName.romanized,
                                        characterNativeName = charPersonName.native,
                                        characterImageUrl = charImg,
                                        characterSource = "anilist",
                                        mediaId = mediaId,
                                        mediaTitle = mediaTitle,
                                        mediaImageUrl = mediaImg,
                                        mediaType = mediaType,
                                        characterRole = charRole,
                                        mediaSource = "anilist"
                                    ))
                                }
                            }
                        }

                        val mediaWorks = mutableListOf<KitsugiStaffMediaWork>()
                        val staffMediaEdges = data.optJSONObject("staffMedia")?.optJSONArray("edges")
                        if (staffMediaEdges != null) {
                            for (i in 0 until staffMediaEdges.length()) {
                                val edge = staffMediaEdges.optJSONObject(i) ?: continue
                                val staffRole = (edge.optNullableString("staffRole") ?: "Ekip Üyesi").toTurkishStaffRole()
                                val mediaNode = edge.optJSONObject("node") ?: continue
                                val mediaId = mediaNode.optInt("idMal").takeIf { it > 0 } ?: (100_000_000 + mediaNode.optInt("id"))
                                val staffTitleObj = mediaNode.optJSONObject("title")
                                val mediaTitle = staffTitleObj?.optNullableString("userPreferred") ?: "Bilinmeyen"
                                val mediaTitleEnglish = staffTitleObj?.optNullableString("english")
                                val mediaTitleNative = staffTitleObj?.optNullableString("native")
                                val mediaTitleRomaji = staffTitleObj?.optNullableString("romaji")
                                val mediaImg = mediaNode.optJSONObject("coverImage")?.optNullableString("large")
                                val mediaType = mediaNode.optNullableString("type").orEmpty().toTurkishMediaTypeString()

                                mediaWorks.add(KitsugiStaffMediaWork(
                                    mediaId = mediaId,
                                    mediaTitle = mediaTitle,
                                    mediaImageUrl = mediaImg,
                                    mediaType = mediaType,
                                    staffRole = staffRole,
                                    source = "anilist",
                                    titleEnglish = mediaTitleEnglish,
                                    titleJapanese = mediaTitleNative,
                                    titleRomaji = mediaTitleRomaji
                                ))
                            }
                        }

                        val isFavourite = data.optBoolean("isFavourite", false)
                        KitsugiStaffDetail(
                            id = staffId,
                            name = name,
                            nativeName = nativeName,
                            alternativeNames = alternativeNames,
                            imageUrl = imageUrl,
                            biography = biography,
                            occupation = occupation,
                            birthday = birthday,
                            age = age,
                            gender = gender,
                            homeTown = homeTown,
                            characterRoles = characterRoles,
                            mediaWorks = mediaWorks,
                            isFavourite = isFavourite,
                            aniListId = staffId,
                            romanizedName = personName.romanized
                        )
                    }.getOrNull()
                }
                "tmdb" -> {
                    val tmdbRes = TmdbApiClient().fetchPersonStaffDetail(staffId)
                    if (tmdbRes != null && KitsugiApplication.getInstance()?.let { com.kitsugi.animelist.data.auth.ExternalAuthManager.getAniListToken(it) } != null) {
                        val aniListDetail = fetchAniListStaffByName(tmdbRes.name)
                        if (aniListDetail != null) {
                            tmdbRes.copy(isFavourite = aniListDetail.isFavourite, aniListId = aniListDetail.id)
                        } else {
                            tmdbRes
                        }
                    } else {
                        tmdbRes
                    }
                }
                "kitsu" -> {
                    var targetName = name
                    if (targetName.isNullOrBlank() && staffId > 0) {
                        targetName = runCatching {
                            val req = okhttp3.Request.Builder()
                                .url("https://kitsu.io/api/edge/people/$staffId")
                                .header("Accept", "application/vnd.api+json")
                                .header("User-Agent", "KitsugiApp/2.4")
                                .build()
                            com.kitsugi.animelist.core.network.KitsugiHttpClient.client.newCall(req).execute().use { resp ->
                                if (resp.isSuccessful) {
                                    val root = org.json.JSONObject(resp.body?.string().orEmpty())
                                    root.optJSONObject("data")?.optJSONObject("attributes")?.optString("name")
                                } else null
                            }
                        }.getOrNull()
                    }
                    if (!targetName.isNullOrBlank()) {
                        val aniStaff = fetchAniListStaffByName(targetName)
                        if (aniStaff != null) return@withContext aniStaff
                    }
                    if (staffId > 0) {
                        runCatching {
                            fetchStaffDetail("jikan", staffId, targetName)
                        }.getOrNull()
                    } else null
                }
                else -> null
            }
        }
    }

    private suspend fun fetchAniListStaffByName(name: String): KitsugiStaffDetail? {
        val query = """
            query (${'$'}search: String) {
                Page(page: 1, perPage: 1) {
                    staff(search: ${'$'}search) {
                        id
                        isFavourite
                        name {
                            userPreferred
                            full first middle last
                            native
                            alternative
                        }
                        image { large }
                        description
                        primaryOccupations
                        gender
                        dateOfBirth { year month day }
                        age
                        homeTown
                        characterMedia(page: 1, perPage: 24) {
                            edges {
                                characterRole
                                node {
                                    id
                                    idMal
                                    title { userPreferred english romaji native }
                                    coverImage { large }
                                    type
                                }
                                characters {
                                    id
                                    name { userPreferred full first middle last native alternative }
                                    image { medium }
                                }
                            }
                        }
                        staffMedia(page: 1, perPage: 24) {
                            edges {
                                staffRole
                                node {
                                    id
                                    idMal
                                    title { userPreferred english romaji native }
                                    coverImage { large }
                                    type
                                }
                            }
                        }
                    }
                }
            }
        """.trimIndent()
        val variables = JSONObject().put("search", name)
        return runCatching {
            val response = KitsugiApiBase.executeAniListQuery(query, variables) ?: return@runCatching null
            val root = JSONObject(response)
            val staffArr = root.optJSONObject("data")?.optJSONObject("Page")?.optJSONArray("staff") ?: return@runCatching null
            if (staffArr.length() == 0) return@runCatching null
            val data = staffArr.getJSONObject(0)

            val nameObj = data.optJSONObject("name")
            val personName = nameObj.aniListPersonName()
            val staffName = personName.preferred
            val nativeName = nameObj?.optNullableString("native")

            val alternativeNames = mutableListOf<String>()
            val altArray = nameObj?.optJSONArray("alternative")
            if (altArray != null) {
                for (i in 0 until altArray.length()) {
                    val alt = altArray.optString(i)
                    if (alt.isNotBlank() && alt != "null") alternativeNames.add(alt)
                }
            }

            val imageUrl = data.optJSONObject("image")?.optNullableString("large")
            val biography = data.optNullableString("description")?.cleanApiText()

            val primaryOccupations = mutableListOf<String>()
            data.optJSONArray("primaryOccupations")?.let { arr ->
                for (i in 0 until arr.length()) primaryOccupations.add(arr.getString(i))
            }
            val occupation = primaryOccupations.joinToString(", ").takeIf { it.isNotBlank() }

            val gender = data.optNullableString("gender")?.toTurkishGender()
            val birthday = buildString {
                val dob = data.optJSONObject("dateOfBirth")
                if (dob != null) {
                    val d = dob.optionalPositiveInt("day")
                    val m = dob.optionalPositiveInt("month")
                    val y = dob.optionalPositiveInt("year")
                    if (d != null && m != null) {
                        append("$d.$m")
                        if (y != null) append(".$y")
                    } else if (y != null) {
                        append(y)
                    }
                }
            }.takeIf { it.isNotBlank() }

            val age = data.optNullableString("age")
            val homeTown = data.optNullableString("homeTown")

            val characterRoles = mutableListOf<KitsugiStaffCharacterRole>()
            val charMedia = data.optJSONObject("characterMedia")
            val charEdges = charMedia?.optJSONArray("edges")
            if (charEdges != null) {
                for (i in 0 until charEdges.length()) {
                    val edge = charEdges.optJSONObject(i) ?: continue
                    val node = edge.optJSONObject("node") ?: continue
                    val charRole = (edge.optNullableString("characterRole") ?: "SUPPORTING").toTurkishCharacterRole()
                    val mediaId = node.optInt("id")
                    val idMal = node.optionalPositiveInt("idMal")
                    val mediaTitle = node.optJSONObject("title")?.optNullableString("userPreferred") ?: "Bilinmeyen"
                    val mediaImg = node.optJSONObject("coverImage")?.optNullableString("large")
                    val type = node.optNullableString("type").orEmpty().lowercase()

                    val stableMediaId = idMal ?: (100_000_000 + mediaId)

                    val charArr = edge.optJSONArray("characters")
                    if (charArr != null && charArr.length() > 0) {
                        val charObj = charArr.getJSONObject(0)
                        val charId = charObj.optInt("id")
                        val charPersonName = charObj.optJSONObject("name").aniListPersonName()
                        val charName = charPersonName.preferred
                        val charImg = charObj.optJSONObject("image")?.optNullableString("medium")

                        characterRoles.add(
                            KitsugiStaffCharacterRole(
                                characterId = charId,
                                characterName = charName,
                                characterRomanizedName = charPersonName.romanized,
                                characterNativeName = charPersonName.native,
                                characterImageUrl = charImg,
                                characterSource = "anilist",
                                mediaId = stableMediaId,
                                mediaTitle = mediaTitle,
                                mediaImageUrl = mediaImg,
                                mediaType = type.toTurkishMediaTypeString(),
                                characterRole = charRole
                            )
                        )
                    }
                }
            }

            val mediaWorks = mutableListOf<KitsugiStaffMediaWork>()
            val staffMedia = data.optJSONObject("staffMedia")
            val staffEdges = staffMedia?.optJSONArray("edges")
            if (staffEdges != null) {
                for (i in 0 until staffEdges.length()) {
                    val edge = staffEdges.optJSONObject(i) ?: continue
                    val node = edge.optJSONObject("node") ?: continue
                    val staffRole = (edge.optNullableString("staffRole") ?: "Staff").toTurkishStaffRole()
                    val mediaId = node.optInt("id")
                    val idMal = node.optionalPositiveInt("idMal")
                    val staffTitleObj = node.optJSONObject("title")
                    val mediaTitle = staffTitleObj?.optNullableString("userPreferred") ?: "Bilinmeyen"
                    val mediaTitleEnglish = staffTitleObj?.optNullableString("english")
                    val mediaTitleNative = staffTitleObj?.optNullableString("native")
                    val mediaTitleRomaji = staffTitleObj?.optNullableString("romaji")
                    val mediaImg = node.optJSONObject("coverImage")?.optNullableString("large")
                    val type = node.optNullableString("type").orEmpty().lowercase()

                    val stableMediaId = idMal ?: (100_000_000 + mediaId)

                    mediaWorks.add(
                        KitsugiStaffMediaWork(
                            mediaId = stableMediaId,
                            mediaTitle = mediaTitle,
                            mediaImageUrl = mediaImg,
                            mediaType = type.toTurkishMediaTypeString(),
                            staffRole = staffRole,
                            source = if (idMal != null) "jikan" else "anilist",
                            titleEnglish = mediaTitleEnglish,
                            titleJapanese = mediaTitleNative,
                            titleRomaji = mediaTitleRomaji
                        )
                    )
                }
            }

            val isFavourite = data.optBoolean("isFavourite", false)
            KitsugiStaffDetail(
                id = data.optInt("id"),
                name = staffName,
                nativeName = nativeName,
                alternativeNames = alternativeNames,
                imageUrl = imageUrl,
                biography = biography,
                occupation = occupation,
                birthday = birthday,
                age = age,
                gender = gender,
                homeTown = homeTown,
                characterRoles = characterRoles,
                mediaWorks = mediaWorks,
                isFavourite = isFavourite,
                aniListId = data.optInt("id"),
                romanizedName = personName.romanized
            )
        }.getOrNull()
    }
}
