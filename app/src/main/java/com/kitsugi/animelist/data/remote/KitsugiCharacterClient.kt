package com.kitsugi.animelist.data.remote

import android.util.Log
import com.kitsugi.animelist.KitsugiApplication
import com.kitsugi.animelist.model.MediaType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.net.URL
import com.kitsugi.animelist.utils.*

class KitsugiCharacterClient {

    companion object {
        private const val TAG = "KitsugiCharacterClient"

        /**
         * Bangumi karakter/seslendirmen adları için AniList köprüsü süre tavanı.
         * Süre dolarsa ya da istek başarısız olursa liste orijinal (özgün) adlarla döner.
         */
        private const val BANGUMI_NAME_BRIDGE_TIMEOUT_MS = 8_000L

        /** Kitsu listesi VA'sızken MAL/AniList'ten VA eklemek için bekleme sınırı (yavaşsa VA'sız gösterilir). */
        private const val KITSU_VA_MERGE_TIMEOUT_MS = 5_000L

        /** Shikimori listesinde VA eksiği varsa MAL/AniList yedeği için bekleme sınırı. */
        private const val SHIKIMORI_VA_MERGE_TIMEOUT_MS = 5_000L

        /** Karakter görselleri için yapım düzeyinde denenecek en fazla başlık adayı. */
        private const val MAX_ANILIST_TITLE_LOOKUPS = 4

        /** Görseli boş kalan karakterler için yapılacak en fazla AniList ad araması. */
        private const val MAX_ANILIST_CHAR_IMAGE_LOOKUPS = 8

        /** Karakter adı aramaları için toplam süre bütçesi — sekme bunu beklemez. */
        private const val ANILIST_CHAR_IMAGE_FALLBACK_BUDGET_MS = 6_000L

        /** İsim eşleştirmede "aynı kişi mi" ayrımını bozan sıfatlar. */
        private val ANI_CHAR_NAME_MODIFIERS = setOf(
            "former", "self", "child", "young", "older", "future", "past",
            "baby", "shadow", "clone", "alter", "dark", "fake"
        )
    }

    /**
     * [realMalId] — AniList kaynaklı entrylerde ARM veya detailState'ten gelen gerçek MAL ID.
     * Bu değer varsa Jikan fetch'i için [externalId] yerine kullanılır.
     */
    suspend fun fetchCharacters(
        source: String,
        externalId: Int?,
        mediaType: MediaType,
        realMalId: Int? = null,
        tmdbId: Int? = null,
        title: String? = null
    ): List<KitsugiCharacter> {
        return withContext(Dispatchers.IO) {
            if (externalId == null || externalId <= 0) {
                Log.w(TAG, "fetchCharacters: externalId geçersiz ($externalId), source=$source")
                return@withContext emptyList()
            }

            val srcLower = MalJikanMediaSupport.canonicalSource(source)
            Log.d(TAG, "fetchCharacters başladı: source=$source, externalId=$externalId, realMalId=$realMalId, mediaType=$mediaType, tmdbId=$tmdbId, title=$title")

            when (srcLower) {
                "bangumi", "bgm" -> {
                    // 1) Bangumi'nin KENDİ karakter + seslendirmen listesi (p1): eşleme gerektirmez.
                    val native = runCatching { KitsugiBangumiDetailClient.fetchCharacters(externalId, mediaType) }
                        .getOrNull().orEmpty()
                        .ifEmpty {
                            runCatching { KitsugiBangumiCreditsClient.fetchSubjectCharacters(externalId) }.getOrNull().orEmpty()
                        }
                    if (native.isNotEmpty()) {
                        // Bangumi liste uçları infobox döndürmez → adlar kanji kalır. AniList
                        // köprüsüyle (özgün ada birebir eşleme) romaji/İngilizce adlar doldurulur;
                        // başlık dili ROMAJI/ENGLISH olduğunda arayüz bunları gösterir.
                        val malIdForNames = KitsugiBangumiDetailClient.sanitizeMalId(realMalId)
                            ?: runCatching { KitsugiBangumiDetailClient.resolveCrossIds(externalId, mediaType) }
                                .getOrNull()?.malId
                        return@withContext enrichBangumiCharacterNames(native, mediaType, malIdForNames)
                    }

                    // 2) Yedek: AniList araması → MAL kimliği çözülüp MAL/AniList karakterleri.
                    //    Bangumi stableId'si (500M+) ASLA MAL ID olarak kullanılmaz.
                    val cross = runCatching {
                        KitsugiBangumiDetailClient.resolveCrossIds(externalId, mediaType)
                    }.getOrNull()
                    val malId = cross?.malId ?: KitsugiBangumiDetailClient.sanitizeMalId(realMalId)
                    if (malId != null && malId > 0 && mediaType != MediaType.Movie && mediaType != MediaType.TvShow) {
                        fetchJikanOrAniListCharacters(malId, mediaType, tmdbId ?: cross?.tmdbId, title)
                    } else {
                        val resolvedTmdb = tmdbId ?: cross?.tmdbId
                        if (resolvedTmdb != null && resolvedTmdb > 0 && mediaType != MediaType.Manga) {
                            val (tmdbChars, _) = TmdbApiClient().fetchCredits(resolvedTmdb, mediaType == MediaType.Movie)
                            tmdbChars.map { it.copy(isRealMediaRole = true) }
                        } else emptyList()
                    }
                }
                "shikimori" -> {
                    // 1) Shikimori kendi karakter + seiyuu listesini verir: tek istek.
                    val shikiChars = KitsugiShikimoriClient.fetchCharacters(mediaType, externalId)

                    // 2) Seslendirmen eksiği yoksa MAL/AniList birleştirmesi HİÇ yapılmaz.
                    //    Eskiden her seferinde ARM + Jikan/AniList zinciri bekleniyordu ve
                    //    sekme bu yüzden onlarca saniye skeleton'da kalıyordu.
                    val needsVaMerge = shikiChars.isNotEmpty() && shikiChars.any { it.voiceActors.isEmpty() }
                    if (!needsVaMerge || mediaType == MediaType.Manga) {
                        return@withContext shikiChars
                    }

                    // 3) NOT: externalId burada Shikimori ID'sidir — MAL ID'si olarak KULLANILMAZ.
                    //    Gerçek MAL ID'si detay önbelleğinden (myanimelist_id) ya da ARM/Shikimori
                    //    API zincirinden çözülür.
                    val malId = realMalId?.takeIf { it > 0 }
                        ?: DetailCache.getMediaDetail("shikimori", externalId)?.realMalId
                        ?: KitsugiIdResolver.resolveMalIdFromShikimori(externalId)
                    if (malId == null || malId <= 0) {
                        return@withContext shikiChars
                    }

                    // 4) Yedek liste AYRI kapsamda başlatılır ve süreyle sınırlanır; yavaşsa
                    //    Shikimori listesi VA'sız hâliyle hemen gösterilir.
                    val refDeferred = vaMergeScope.async {
                        runCatching {
                            fetchCharacters("jikan", malId, mediaType, malId, tmdbId, title)
                        }.getOrNull()?.takeIf { it.isNotEmpty() }
                            ?: runCatching {
                                fetchCharacters("anilist", malId, mediaType, malId, tmdbId, title)
                            }.getOrNull()
                    }
                    val refChars = withTimeoutOrNull(SHIKIMORI_VA_MERGE_TIMEOUT_MS) { refDeferred.await() }
                        ?: run {
                            refDeferred.cancel()
                            emptyList<KitsugiCharacter>()
                        }
                    if (refChars.isEmpty()) {
                        shikiChars
                    } else {
                        mergeVoiceActorsIntoCharacters(shikiChars, refChars)
                    }
                }
                "simkl" -> {
                    val simklDetail = DetailCache.getMediaDetail("simkl", externalId)
                    val malId = realMalId?.takeIf { it > 0 } ?: simklDetail?.realMalId
                    if (malId != null && malId > 0) {
                        val malList = fetchCharacters("jikan", malId, MediaType.Anime, malId, null, title)
                        if (malList.isNotEmpty()) return@withContext malList
                        val aniList = fetchCharacters("anilist", malId, MediaType.Anime, malId, null, title)
                        if (aniList.isNotEmpty()) return@withContext aniList
                    }
                    val resolvedTmdb = tmdbId ?: run {
                        val malIdForResolve = malId ?: simklDetail?.realMalId
                        KitsugiIdResolver.resolveIds(malId = malIdForResolve, aniListId = null, tmdbId = tmdbId).tmdbId
                    }
                    if (resolvedTmdb != null && resolvedTmdb > 0) {
                        val isMovie = mediaType == MediaType.Movie
                        val isRealMedia = simklDetail?.type == MediaType.TvShow || simklDetail?.type == MediaType.Movie ||
                                          mediaType == MediaType.TvShow || (mediaType == MediaType.Movie && malId == null)
                        val (tmdbChars, _) = TmdbApiClient().fetchCredits(resolvedTmdb, isMovie)
                        val titleCandidates = buildTitleCandidates(title, simklDetail)
                        if (tmdbChars.isNotEmpty()) {
                            return@withContext if (isRealMedia) {
                                tmdbChars.map { it.copy(isRealMediaRole = true) }
                            } else {
                                enrichCharactersWithAnimeImages(tmdbChars, titleCandidates, malId)
                            }
                        }
                    }
                    emptyList()
                }

                "tmdb" -> {
                    val effectiveTmdbId = tmdbId ?: externalId
                    if (effectiveTmdbId > 0) {
                        val isMovie = mediaType == MediaType.Movie
                        val isRealMedia = mediaType == MediaType.TvShow || (mediaType == MediaType.Movie && realMalId == null)
                        val (tmdbChars, _) = TmdbApiClient().fetchCredits(effectiveTmdbId, isMovie)
                        val mediaDetail = DetailCache.getMediaDetail("tmdb", effectiveTmdbId)
                        // Başlık adayları: Türkçe başlık AniList'te bulunmaz; romaji/İngilizce/
                        // Japonca başlıklar sırayla denenir (bkz. enrichCharactersWithAnimeImages).
                        val titleCandidates = buildTitleCandidates(title, mediaDetail)
                        val effectiveMalId = realMalId ?: mediaDetail?.realMalId
                        if (tmdbChars.isNotEmpty()) {
                            if (isRealMedia) {
                                tmdbChars.map { it.copy(isRealMediaRole = true) }
                            } else {
                                enrichCharactersWithAnimeImages(tmdbChars, titleCandidates, effectiveMalId)
                            }
                        } else {
                            tmdbChars
                        }
                    } else emptyList()
                }
                "jikan", "mal" -> {
                    val jikanId = MalJikanMediaSupport.resolveMalId(source, externalId, realMalId)
                        ?: return@withContext emptyList()
                    val endpoint = MalJikanMediaSupport.jikanEndpoint(mediaType)
                    val url = URL("https://api.jikan.moe/v4/$endpoint/$jikanId/characters")
                    Log.d(TAG, "Jikan isteği: $url")
                    val jikanList = runCatching {
                        KitsugiApiBase.runWithRateLimit {
                            // 429/5xx'te kısa bekleme ile yeniden dene — Jikan rate-limit'inde
                            // karakter listesi "eksik" dönmüş olmasın.
                            val response = KitsugiApiBase.executeGetRequestResilient(url)
                            if (response == null) {
                                Log.w(TAG, "Jikan yanıt null: $url")
                                return@runWithRateLimit emptyList()
                            }
                            if (!response.trimStart().startsWith('{')) {
                                Log.e(TAG, "Jikan HTML/CF yanıtı (ilk 200 char): ${response.take(200)}")
                                return@runWithRateLimit emptyList()
                            }
                            val root = JSONObject(response)
                            val data = root.optJSONArray("data")
                            if (data == null) {
                                Log.w(TAG, "Jikan 'data' alanı yok. Root keys: ${root.keys().asSequence().toList()}")
                                return@runWithRateLimit emptyList()
                            }
                            Log.d(TAG, "Jikan karakter sayısı: ${data.length()}")
                            val list = mutableListOf<KitsugiCharacter>()
                            for (i in 0 until data.length()) {
                                val item = data.optJSONObject(i) ?: continue
                                val charObj = item.optJSONObject("character") ?: continue
                                val id = charObj.optInt("mal_id")
                                val name = (charObj.optNullableString("name") ?: "Bilinmeyen").toFriendlyName()
                                val role = (item.optNullableString("role") ?: "Bilinmeyen").toTurkishCharacterRole()
                                val imageUrl = charObj.optJSONObject("images")?.optJSONObject("jpg")?.optNullableString("image_url")

                                val vaList = mutableListOf<KitsugiVoiceActor>()
                                val vaArray = item.optJSONArray("voice_actors")
                                if (vaArray != null) {
                                    for (j in 0 until vaArray.length()) {
                                        val vaItem = vaArray.optJSONObject(j) ?: continue
                                        val vaPerson = vaItem.optJSONObject("person") ?: continue
                                        val vaId = vaPerson.optInt("mal_id")
                                        val vaName = (vaPerson.optNullableString("name") ?: "Bilinmeyen").toFriendlyName()
                                        val vaLang = (vaItem.optNullableString("language") ?: "Bilinmeyen").toTurkishLanguage()
                                        val vaImageUrl = vaPerson.optJSONObject("images")?.optJSONObject("jpg")?.optNullableString("image_url")
                                        vaList.add(KitsugiVoiceActor(vaId, vaName, vaLang, vaImageUrl, source = "jikan"))
                                    }
                                }
                                list.add(KitsugiCharacter(id, name, role, imageUrl, vaList, source = "jikan"))
                            }
                            list
                        }
                    }.getOrElse { err ->
                        Log.e(TAG, "Jikan fetch exception: ${err.javaClass.simpleName}: ${err.message}", err)
                        emptyList()
                    }

                    if (jikanList.isNotEmpty()) {
                        jikanList
                    } else {
                        // MAL and Jikan are the same identity namespace. If Jikan is
                        // temporarily empty/unavailable, query AniList by this exact MAL ID
                        // before paying for a Shikimori ID-resolution fallback.
                        val aniListList = runCatching {
                            fetchCharacters("anilist", jikanId, mediaType, null, tmdbId, title)
                        }.getOrNull().orEmpty()
                        if (aniListList.isNotEmpty()) {
                            aniListList
                        } else {
                            Log.w(TAG, "Jikan/AniList karakter listesi boş; Shikimori fallback deneniyor")
                            // Shikimori endpoint'i KENDİ ID'sini bekler — MAL ID'si ARM ile
                            // Shikimori ID'sine çevrilmeden çağrılırsa YANLIŞ yapımın verisi gelir.
                            val shikiId = KitsugiIdResolver.resolveShikimoriIdFromMal(jikanId)
                            if (shikiId != null && shikiId > 0) {
                                KitsugiShikimoriClient.fetchCharacters(mediaType, shikiId)
                            } else {
                                emptyList()
                            }
                        }
                    }
                }

                "anilist" -> {
                    // externalId >= 100_000_000 ise AniList internal ID encode edilmiş demek
                    val aniListId = when {
                        externalId >= 100_000_000 -> externalId - 100_000_000
                        else -> null  // idMal kullan
                    }
                    Log.d(TAG, "AniList fetch: externalId=$externalId, aniListId=$aniListId")
                    val idParam = if (aniListId != null) "\$id: Int" else "\$idMal: Int"
                    val idFilter = if (aniListId != null) "id: \$id" else "idMal: \$idMal"
                    val query = """
                        query (${'$'}type: MediaType, $idParam) {
                            Media($idFilter, type: ${'$'}type) {
                                characters(page: 1, perPage: 24, sort: [RELEVANCE, ROLE, FAVOURITES_DESC]) {
                                    edges {
                                        role
                                        node {
                                            id
                                            name { userPreferred full first middle last native alternative }
                                            image { medium }
                                        }
                                        voiceActors(sort: [RELEVANCE, LANGUAGE]) {
                                            id
                                            name { userPreferred full first middle last native alternative }
                                            image { medium }
                                            languageV2
                                        }
                                    }
                                }
                            }
                        }
                    """.trimIndent()
                    val variables = JSONObject().put("type", MalJikanMediaSupport.aniListMediaType(mediaType))
                    if (aniListId != null) variables.put("id", aniListId) else variables.put("idMal", externalId)

                    runCatching {
                        val response = KitsugiApiBase.executeAniListQuery(query, variables)
                        if (response == null) {
                            Log.w(TAG, "AniList yanıt null: idParam=$idParam, value=${if (aniListId != null) aniListId else externalId}")
                            return@runCatching emptyList<KitsugiCharacter>()
                        }
                        // GraphQL hata kontrolu
                        if (response.contains("\"errors\"")) {
                            Log.e(TAG, "AniList GraphQL hata yanıtı: ${response.take(300)}")
                        }
                        val root = JSONObject(response)
                        val edges = root.optJSONObject("data")
                            ?.optJSONObject("Media")
                            ?.optJSONObject("characters")
                            ?.optJSONArray("edges")
                        if (edges == null) {
                            Log.w(TAG, "AniList edges null. data.Media null mu: ${root.optJSONObject("data")?.optJSONObject("Media") == null}")
                            return@runCatching emptyList<KitsugiCharacter>()
                        }
                        Log.d(TAG, "AniList karakter sayısı: ${edges.length()}")
                        val list = mutableListOf<KitsugiCharacter>()
                        for (i in 0 until edges.length()) {
                            val edge = edges.optJSONObject(i) ?: continue
                            val role = (edge.optNullableString("role") ?: "Bilinmeyen").toTurkishCharacterRole()
                            val node = edge.optJSONObject("node") ?: continue
                            val id = node.optInt("id")
                            val nameObj = node.optJSONObject("name")
                            val personName = nameObj.aniListPersonName()
                            val name = personName.preferred
                            val imageUrl = node.optJSONObject("image")?.optNullableString("medium")

                            val vaList = mutableListOf<KitsugiVoiceActor>()
                            val vaArray = edge.optJSONArray("voiceActors")
                            if (vaArray != null) {
                                for (j in 0 until vaArray.length()) {
                                    val vaItem = vaArray.optJSONObject(j) ?: continue
                                    val vaId = vaItem.optInt("id")
                                    val vaPersonName = vaItem.optJSONObject("name").aniListPersonName()
                                    val vaName = vaPersonName.preferred
                                    val vaLang = (vaItem.optNullableString("languageV2")
                                        ?: vaItem.optNullableString("language")
                                        ?: "Japanese").toTurkishLanguage()
                                    val vaImageUrl = vaItem.optJSONObject("image")?.optNullableString("medium")
                                    vaList.add(KitsugiVoiceActor(vaId, vaName, vaLang, vaImageUrl, source = "anilist", romanizedName = vaPersonName.romanized, nativeName = vaPersonName.native))
                                }
                            }
                            list.add(KitsugiCharacter(id, name, role, imageUrl, vaList, source = "anilist", romanizedName = personName.romanized, nativeName = personName.native))
                        }
                        list
                    }.getOrElse { err ->
                        Log.e(TAG, "AniList fetch exception: ${err.javaClass.simpleName}: ${err.message}", err)
                        emptyList()
                    }
                }


                "kitsu" -> {
                    // externalId = kitsuStableId = kitsuNumericId + 300_000_000
                    val kitsuOffset = 300_000_000
                    val kitsuNumericId = if (externalId >= kitsuOffset) externalId - kitsuOffset else externalId
                    Log.d(TAG, "Kitsu karakter fetch: stableId=$externalId, kitsuNumericId=$kitsuNumericId")
                    if (kitsuNumericId <= 0) return@withContext emptyList()

                    val kitsuList = runCatching {
                        KitsuClient.fetchKitsuCharacters(kitsuNumericId)
                    }.getOrElse { err ->
                        Log.e(TAG, "Kitsu karakter fetch hatası: ${err.message}", err)
                        emptyList()
                    }

                    // Kitsu listesi seslendirmenleriyle birlikte hazırsa HEMEN dön. Eski akış, VA
                    // eşlemesi için ARM + Jikan + AniList'i sırayla bekliyordu; Jikan yavaşken
                    // Kitsu karakter sekmesi dakikalarca skeleton'da kalıyordu.
                    if (kitsuList.isNotEmpty() && kitsuList.any { it.voiceActors.isNotEmpty() }) {
                        Log.d(TAG, "Kitsu karakter listesi başarılı: ${kitsuList.size} karakter")
                        return@withContext kitsuList
                    }

                    // Kitsu'da hiç VA yoksa ya da liste boşsa MAL/AniList yedeğine bak.
                    val effectiveMalId = realMalId?.takeIf { it > 0 }
                        ?: DetailCache.getMediaDetail("kitsu", externalId)?.realMalId
                        ?: runCatching {
                            KitsugiIdResolver.resolveIds(malId = null, aniListId = null, kitsuId = kitsuNumericId).malId
                        }.getOrNull()?.takeIf { it > 0 }

                    if (effectiveMalId == null || effectiveMalId <= 0) {
                        return@withContext kitsuList
                    }

                    if (kitsuList.isEmpty()) {
                        // Kitsu boş: MAL/AniList yedeği tek başına sonuçtur (süre sınırı VM tarafında).
                        Log.w(TAG, "Kitsu boş döndü, MAL/AniList yedeği deneniyor (malId=$effectiveMalId)")
                        val fallback = fetchJikanOrAniListCharacters(effectiveMalId, mediaType, tmdbId, title)
                        fallback.map { it.copy(source = "kitsu", voiceActors = it.voiceActors.map { va -> va.copy(source = "kitsu") }) }
                    } else {
                        // Kitsu listesi var ama VA'sız: yedek yalnızca VA eklemek için; kısa süre bekle,
                        // yavaşsa Kitsu listesini VA'sız olarak göster.
                        // Ayrı kapsamda başlatılır: withContext/withTimeoutOrNull bloklayan ağ çağrısını
                        // beklemeden dönemez. Süre dolunca UI Kitsu listesini VA'sız gösterir.
                        val refDeferred = vaMergeScope.async {
                            fetchJikanOrAniListCharacters(effectiveMalId, mediaType, tmdbId, title)
                        }
                        val refChars = withTimeoutOrNull(KITSU_VA_MERGE_TIMEOUT_MS) { refDeferred.await() }
                            ?: run {
                                refDeferred.cancel()
                                emptyList<KitsugiCharacter>()
                            }
                        if (refChars.isEmpty()) {
                            kitsuList
                        } else {
                            val kitsuRefChars = refChars.map { rc ->
                                rc.copy(voiceActors = rc.voiceActors.map { va -> va.copy(source = "kitsu") })
                            }
                            mergeVoiceActorsIntoCharacters(kitsuList, kitsuRefChars)
                        }
                    }
                }

                else -> {
                    // Bilinmeyen source — Jikan ile dene (MAL ID varsa)
                    val jikanId = realMalId?.takeIf { it > 0 } ?: externalId.takeIf { it > 0 && it < 100_000_000 }
                    if (jikanId != null) {
                        Log.w(TAG, "Bilinmeyen source '$source', Jikan fallback ile deneniyor: id=$jikanId")
                        fetchCharacters("jikan", jikanId, mediaType, null)
                    } else {
                        Log.e(TAG, "Bilinmeyen source '$source' ve geçerli MAL ID yok. Boş liste döndürülüyor.")
                        emptyList()
                    }
                }
            }
        }
    }

    /**
     * Bangumi karakter/seslendirmen listesindeki CJK adlara AniList köprüsüyle romaji ve
     * İngilizce karşılık ekler. Eşleme özgün (native) ada göredir; eşleşmeyen adlar olduğu
     * gibi kalır. Tek önbellekli AniList sorgusu kullanılır; süre tavanı/başarısızlıkta
     * liste orijinal haliyle döner (sekme kanji adlarla açılır, beklemez).
     */
    private suspend fun enrichBangumiCharacterNames(
        characters: List<KitsugiCharacter>,
        mediaType: MediaType,
        malId: Int?
    ): List<KitsugiCharacter> {
        if (characters.isEmpty()) return characters
        val needsEnrich = characters.any { char ->
            PreferenceHelpers.hasCjkCharacters(char.name) ||
                char.voiceActors.any { PreferenceHelpers.hasCjkCharacters(it.name) }
        }
        if (!needsEnrich || malId == null || malId <= 0) return characters
        val names = runCatching {
            withTimeoutOrNull(BANGUMI_NAME_BRIDGE_TIMEOUT_MS) {
                KitsugiAniListPersonBridge.fetchMediaNames(malId, mediaType)
            }
        }.getOrNull() ?: return characters
        if (names == null) return characters
        return characters.map { char ->
            val variant = if (PreferenceHelpers.hasCjkCharacters(char.name)) {
                KitsugiAniListPersonBridge.findByNative(names.characters, char.nativeName, char.name)
            } else null
            val voiceActors = char.voiceActors.map { va ->
                val vaVariant = if (PreferenceHelpers.hasCjkCharacters(va.name)) {
                    KitsugiAniListPersonBridge.findByNative(names.voiceActors, va.nativeName, va.name)
                } else null
                if (vaVariant == null) va else va.copy(
                    romanizedName = va.romanizedName ?: vaVariant.romaji,
                    englishName = va.englishName ?: vaVariant.english
                )
            }
            if (variant == null && voiceActors == char.voiceActors) return@map char
            char.copy(
                romanizedName = char.romanizedName ?: variant?.romaji,
                englishName = char.englishName ?: variant?.english,
                voiceActors = voiceActors
            )
        }
    }

    /**
     * Kitsu VA birleştirme işleri için bağımsız kapsam (ana çağrının kapsamı DEĞİL). Böylece
     * süre dolduğunda ana çağrı bloklanmaz; arka plan işi kendi OkHttp callTimeout'u ile biter.
     */
    private val vaMergeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * MAL ID üzerinden Jikan, yoksa AniList'ten karakter listesi (yedek kaynak).
     */
    private suspend fun fetchJikanOrAniListCharacters(
        malId: Int,
        mediaType: MediaType,
        tmdbId: Int?,
        title: String?
    ): List<KitsugiCharacter> {
        val jikan = runCatching {
            fetchCharacters("jikan", malId, mediaType, malId, tmdbId, title)
        }.getOrNull()
        if (!jikan.isNullOrEmpty()) return jikan
        return runCatching {
            fetchCharacters("anilist", malId, mediaType, malId, tmdbId, title)
        }.getOrNull() ?: emptyList()
    }

    suspend fun fetchCharacterDetail(
        source: String,
        characterId: Int,
        name: String? = null,
        /**
         * Arayüzden gelen (kartta/tab'da gösterilmiş) görsel. Kaynak veride görsel yoksa
         * karakter detay sayfası boş kalmasın diye son çare olarak kullanılır.
         */
        fallbackImageUrl: String? = null,
        isRealMediaRole: Boolean = false
    ): KitsugiCharacterDetail? {
        return withContext(Dispatchers.IO) {
            if (characterId <= 0) return@withContext null
            when (MalJikanMediaSupport.canonicalSource(source)) {
                "bangumi", "bgm" -> {
                    KitsugiBangumiCreditsClient.fetchCharacterDetail(characterId, name, fallbackImageUrl)
                        ?: KitsugiBangumiDetailClient.fetchCharacterDetail(characterId)
                }
                "shikimori" -> {
                    KitsugiShikimoriClient.fetchCharacterDetail(characterId)
                }
                "jikan", "mal" -> {
                    var detail: KitsugiCharacterDetail? = null
                    var networkError: Throwable? = null

                    // 1. MAL / Jikan API
                    val url = URL("https://api.jikan.moe/v4/characters/$characterId/full")
                    val jikanRes = runCatching {
                        KitsugiApiBase.runWithRateLimit {
                            val response = KitsugiApiBase.executeGetRequestOrThrow(url)
                            val root = JSONObject(response)
                            val data = root.optJSONObject("data") ?: return@runWithRateLimit null

                            val charName = (data.optNullableString("name") ?: "Bilinmeyen").toFriendlyName()
                            val nativeName = data.optNullableString("name_kanji")

                            val nicknamesArray = data.optJSONArray("nicknames")
                            val alternativeNames = mutableListOf<String>()
                            if (nicknamesArray != null) {
                                for (i in 0 until nicknamesArray.length()) {
                                    val nickname = nicknamesArray.optString(i)
                                    if (nickname.isNotBlank() && nickname != "null") {
                                        alternativeNames.add(nickname)
                                    }
                                }
                            }

                            val imageUrl = data.optJSONObject("images")?.optJSONObject("jpg")?.optNullableString("image_url")
                            val biography = data.optNullableString("about")?.cleanApiText()?.takeIf { it.isNotBlank() }

                            // Parse voice actors
                            val voiceActors = mutableListOf<KitsugiVoiceActor>()
                            val voicesArray = data.optJSONArray("voices")
                            if (voicesArray != null) {
                                for (i in 0 until voicesArray.length()) {
                                    val item = voicesArray.optJSONObject(i) ?: continue
                                    val personObj = item.optJSONObject("person") ?: continue
                                    val vaId = personObj.optInt("mal_id")
                                    val vaName = (personObj.optNullableString("name") ?: "Bilinmeyen").toFriendlyName()
                                    val language = (item.optNullableString("language") ?: "Bilinmeyen").toTurkishLanguage()
                                    val vaImg = personObj.optJSONObject("images")?.optJSONObject("jpg")?.optNullableString("image_url")
                                    voiceActors.add(KitsugiVoiceActor(vaId, vaName, language, vaImg))
                                }
                            }

                            // Parse media appearances
                            val mediaAppearances = mutableListOf<KitsugiCharacterMediaAppearance>()
                            val animeArray = data.optJSONArray("anime")
                            if (animeArray != null) {
                                for (i in 0 until animeArray.length()) {
                                    val item = animeArray.optJSONObject(i) ?: continue
                                    val role = (item.optNullableString("role") ?: "Bilinmeyen").toTurkishCharacterRole()
                                    val animeObj = item.optJSONObject("anime") ?: continue
                                    val mediaId = animeObj.optInt("mal_id")
                                    val title = animeObj.optNullableString("title") ?: "Bilinmeyen"
                                    val mediaImg = animeObj.optJSONObject("images")?.optJSONObject("jpg")?.optNullableString("image_url")
                                    mediaAppearances.add(KitsugiCharacterMediaAppearance(
                                        mediaId = mediaId,
                                        title = title,
                                        imageUrl = mediaImg,
                                        mediaType = "anime".toTurkishMediaTypeString(),
                                        characterRole = role,
                                        source = "jikan"
                                    ))
                                }
                            }
                            val mangaArray = data.optJSONArray("manga")
                            if (mangaArray != null) {
                                for (i in 0 until mangaArray.length()) {
                                    val item = mangaArray.optJSONObject(i) ?: continue
                                    val role = (item.optNullableString("role") ?: "Bilinmeyen").toTurkishCharacterRole()
                                    val mangaObj = item.optJSONObject("manga") ?: continue
                                    val mediaId = mangaObj.optInt("mal_id")
                                    val title = mangaObj.optNullableString("title") ?: "Bilinmeyen"
                                    val mediaImg = mangaObj.optJSONObject("images")?.optJSONObject("jpg")?.optNullableString("image_url")
                                    mediaAppearances.add(KitsugiCharacterMediaAppearance(
                                        mediaId = mediaId,
                                        title = title,
                                        imageUrl = mediaImg,
                                        mediaType = "manga".toTurkishMediaTypeString(),
                                        characterRole = role,
                                        source = "jikan"
                                    ))
                                }
                            }

                            KitsugiCharacterDetail(
                                id = characterId,
                                name = charName,
                                nativeName = nativeName,
                                alternativeNames = alternativeNames,
                                imageUrl = imageUrl,
                                gender = null,
                                age = null,
                                birthday = null,
                                bloodType = null,
                                biography = biography,
                                voiceActors = voiceActors.sortedByLanguagePreference(),
                                mediaAppearances = mediaAppearances
                            )
                        }
                    }
                    jikanRes.onSuccess {
                        detail = it
                    }.onFailure { err ->
                        if (err !is ResourceNotFoundException) {
                            networkError = err
                        }
                        Log.e(TAG, "Jikan fetch failed: ${err.message}", err)
                    }

                    // 2. Shikimori Fallback / Merge
                    runCatching {
                        KitsugiShikimoriClient.fetchCharacterDetail(characterId)
                    }.onSuccess { shikiDetail ->
                        if (shikiDetail != null) {
                            detail = if (detail == null) shikiDetail else detail!!.mergeWith(shikiDetail)
                        }
                    }.onFailure { err ->
                        Log.e(TAG, "Shikimori character detail fallback failed: ${err.message}", err)
                    }

                    // 3. AniList Fallback / Merge
                    val targetName = detail?.name ?: name
                    if (!targetName.isNullOrBlank()) {
                        runCatching {
                            fetchAniListCharacterByName(targetName)
                        }.onSuccess { aniListDetail ->
                            if (aniListDetail != null) {
                                detail = if (detail == null) aniListDetail else detail!!.mergeWith(aniListDetail)
                            }
                        }.onFailure { err ->
                            Log.e(TAG, "AniList character detail fallback failed: ${err.message}", err)
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
                            Character(id: ${'$'}id) {
                                id
                                isFavourite
                                name {
                                    userPreferred
                                    full first middle last
                                    native
                                    alternative
                                    alternativeSpoiler
                                }
                                image {
                                    large
                                }
                                description
                                gender
                                dateOfBirth {
                                    year
                                    month
                                    day
                                }
                                age
                                bloodType
                                media(page: 1, perPage: 25) {
                                    edges {
                                        characterRole
                                        node {
                                            id
                                            idMal
                                            title { userPreferred english romaji native }
                                            coverImage { large }
                                            type
                                        }
                                        voiceActors {
                                            id
                                            name { userPreferred full first middle last native alternative }
                                            image { medium }
                                            language
                                            languageV2
                                        }
                                    }
                                }
                            }
                        }
                    """.trimIndent()
                    val variables = JSONObject().put("id", characterId)
                    runCatching {
                        val response = KitsugiApiBase.executeAniListQuery(query, variables) ?: return@runCatching null
                        val root = JSONObject(response)
                        val data = root.optJSONObject("data")?.optJSONObject("Character") ?: return@runCatching null

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
                        val altSpoilerArray = nameObj?.optJSONArray("alternativeSpoiler")
                        if (altSpoilerArray != null) {
                            for (i in 0 until altSpoilerArray.length()) {
                                val alt = altSpoilerArray.optString(i)
                                if (alt.isNotBlank() && alt != "null") alternativeNames.add(alt)
                            }
                        }

                        val imageUrl = data.optJSONObject("image")?.optNullableString("large")
                        val biography = data.optNullableString("description")?.cleanApiText()?.takeIf { it.isNotBlank() }
                        val gender = data.optNullableString("gender")?.toTurkishGender()
                        val age = data.optNullableString("age")
                        val bloodType = data.optNullableString("bloodType")

                        val dobObj = data.optJSONObject("dateOfBirth")
                        val birthday = if (dobObj != null) {
                            val d = dobObj.optInt("day", 0)
                            val m = dobObj.optInt("month", 0)
                            val y = dobObj.optInt("year", 0)
                            if (d > 0 && m > 0) {
                                if (y > 0) "$d/$m/$y" else "$d/$m"
                            } else null
                        } else null

                        // Parse media appearances & collect unique voice actors
                        val mediaAppearances = mutableListOf<KitsugiCharacterMediaAppearance>()
                        val vaMap = mutableMapOf<Int, KitsugiVoiceActor>()

                        val mediaEdges = data.optJSONObject("media")?.optJSONArray("edges")
                        if (mediaEdges != null) {
                            for (i in 0 until mediaEdges.length()) {
                                val edge = mediaEdges.optJSONObject(i) ?: continue
                                val characterRole = (edge.optNullableString("characterRole") ?: "Bilinmeyen").toTurkishCharacterRole()
                                val node = edge.optJSONObject("node") ?: continue

                                val rawId = node.optInt("id")
                                val idMal = node.optInt("idMal")
                                val mediaId = if (idMal > 0) idMal else (100_000_000 + rawId)
                                val titleObj = node.optJSONObject("title")
                                val titleRomaji = titleObj?.optNullableString("romaji")
                                val titleEnglish = titleObj?.optNullableString("english")
                                val titleNative = titleObj?.optNullableString("native")
                                val title = titleObj?.optNullableString("userPreferred")
                                    ?: titleRomaji ?: titleEnglish ?: titleNative ?: "Bilinmeyen"
                                val mediaImg = node.optJSONObject("coverImage")?.optNullableString("large")
                                val mediaType = node.optNullableString("type").orEmpty().toTurkishMediaTypeString()

                                mediaAppearances.add(KitsugiCharacterMediaAppearance(
                                    mediaId = mediaId,
                                    title = title,
                                    imageUrl = mediaImg,
                                    mediaType = mediaType,
                                    characterRole = characterRole,
                                    source = "anilist",
                                    titleEnglish = titleEnglish,
                                    titleJapanese = titleNative,
                                    titleRomaji = titleRomaji
                                ))

                                val vaArray = edge.optJSONArray("voiceActors")
                                if (vaArray != null) {
                                    for (j in 0 until vaArray.length()) {
                                        val vaItem = vaArray.optJSONObject(j) ?: continue
                                        val vaId = vaItem.optInt("id")
                                        val vaPersonName = vaItem.optJSONObject("name").aniListPersonName()
                                        val vaName = vaPersonName.preferred
                                        val vaLang = (vaItem.optNullableString("languageV2")
                                            ?: vaItem.optNullableString("language")
                                            ?: "Japanese").toTurkishLanguage()
                                        val vaImageUrl = vaItem.optJSONObject("image")?.optNullableString("medium")
                                        vaMap[vaId] = KitsugiVoiceActor(vaId, vaName, vaLang, vaImageUrl, source = "anilist", romanizedName = vaPersonName.romanized, nativeName = vaPersonName.native)
                                    }
                                }
                            }
                        }

                        val isFavourite = data.optBoolean("isFavourite", false)
                        KitsugiCharacterDetail(
                            id = characterId,
                            name = name,
                            nativeName = nativeName,
                            alternativeNames = alternativeNames,
                            imageUrl = imageUrl,
                            gender = gender,
                            age = age,
                            birthday = birthday,
                            bloodType = bloodType,
                            biography = biography,
                            voiceActors = vaMap.values.toList(),
                            mediaAppearances = mediaAppearances,
                            isFavourite = isFavourite,
                            aniListId = characterId,
                            romanizedName = personName.romanized
                        )
                    }.getOrNull()
                }
                "tmdb" -> {
                    if (isRealMediaRole) {
                        val personDetail = TmdbApiClient().fetchPersonCharacterDetail(characterId)
                        return@withContext if (personDetail != null && personDetail.imageUrl.isNullOrBlank() && !fallbackImageUrl.isNullOrBlank()) {
                            personDetail.copy(imageUrl = fallbackImageUrl, source = "tmdb")
                        } else personDetail
                    }

                    // Kurgusal/anime karakteri ise (name parametresi varsa) seslendirmen biyografisi yerine
                    // AniList veya Jikan üzerinden gerçek karakter profilini yükle
                    val cleanCharName = name?.replace(Regex("\\s*\\((?:voice|uncredited|uncredited voice|child)\\)", RegexOption.IGNORE_CASE), "")?.trim()
                    if (!cleanCharName.isNullOrBlank()) {
                        val aniDetail = fetchAniListCharacterByName(cleanCharName)
                        if (aniDetail != null) {
                            // Kaynakta görsel yoksa arayüzden gelen görseli tamamla.
                            return@withContext if (aniDetail.imageUrl.isNullOrBlank() && !fallbackImageUrl.isNullOrBlank()) {
                                aniDetail.copy(imageUrl = fallbackImageUrl)
                            } else aniDetail
                        }
                        val jikanDetail = runCatching {
                            val jikanSearch = JikanApiClient().searchMalCharacters(cleanCharName, page = 1)
                            val firstMalId = jikanSearch.firstOrNull()?.malId
                            if (firstMalId != null && firstMalId > 0) {
                                fetchCharacterDetail("jikan", firstMalId, cleanCharName, fallbackImageUrl)
                            } else null
                        }.getOrNull()
                        if (jikanDetail != null) {
                            return@withContext if (jikanDetail.imageUrl.isNullOrBlank() && !fallbackImageUrl.isNullOrBlank()) {
                                jikanDetail.copy(imageUrl = fallbackImageUrl)
                            } else jikanDetail
                        }
                    }

                    // TMDB credits listesinde "karakter" kaydı, kaydın arkasındaki GERÇEK kişiyi
                    // (oyuncu/seiyuu) taşır. Eski davranış bu ayrımı kaybediyordu:
                    //  - görsel HER durumda null'lanıyordu → karakter sayfası resimsiz açılıyordu
                    //    (kullanıcı şikâyeti: hayali karakterler ve gerçek kişiler dâhil),
                    //  - gerçek kişi kaydı ile kurgusal karakter kaydı aynı biçimde dönüyordu.
                    val personDetail = TmdbApiClient().fetchPersonCharacterDetail(characterId)
                    if (personDetail != null) {
                        // Son çare görsel sırası: karttan gelen görsel → kişinin TMDB fotoğrafı.
                        val resolvedImage = fallbackImageUrl?.takeIf { it.isNotBlank() }
                            ?: personDetail.imageUrl

                        if (!cleanCharName.isNullOrBlank()) {
                            // KURGUSAL KARAKTER: kimlik karakter adıyla korunur, oyuncu/seiyuu
                            // seslendirmen olarak iliştirilir. Görsel boş bırakılmaz.
                            val va = KitsugiVoiceActor(
                                id = characterId,
                                name = personDetail.name,
                                language = "Japonca",
                                imageUrl = personDetail.imageUrl,
                                source = "tmdb"
                            )
                            KitsugiCharacterDetail(
                                id = characterId,
                                name = cleanCharName,
                                nativeName = null,
                                alternativeNames = personDetail.alternativeNames,
                                imageUrl = resolvedImage,
                                gender = null,
                                age = null,
                                birthday = null,
                                bloodType = null,
                                biography = personDetail.biography
                                    ?: "Bu kurgusal karakter için ek biyografi bilgisi bulunmuyor.",
                                voiceActors = listOf(va),
                                mediaAppearances = personDetail.mediaAppearances,
                                source = "tmdb"
                            )
                        } else {
                            // GERÇEK KİŞİ: sayfa kişinin kendi profili; görsel/biyografi ondan gelir.
                            if (personDetail.imageUrl.isNullOrBlank() && !fallbackImageUrl.isNullOrBlank()) {
                                personDetail.copy(imageUrl = fallbackImageUrl, source = "tmdb")
                            } else personDetail
                        }
                    } else null
                }
                "kitsu" -> {
                    val kitsuDetail = runCatching {
                        val request = okhttp3.Request.Builder()
                            .url("https://kitsu.io/api/edge/characters/$characterId")
                            .header("Accept", "application/vnd.api+json")
                            .header("User-Agent", "Kitsugi/1.0 (Android)")
                            .build()
                        val responseBody = com.kitsugi.animelist.core.network.KitsugiHttpClient.client.newCall(request).execute().use { response ->
                            if (!response.isSuccessful) throw java.io.IOException("HTTP error ${response.code}")
                            response.body?.string() ?: ""
                        }
                        val root = JSONObject(responseBody)
                        val data = root.optJSONObject("data") ?: return@runCatching null
                        val attrs = data.optJSONObject("attributes") ?: return@runCatching null
                        val name = attrs.optString("name", attrs.optString("canonicalName", "Bilinmeyen"))
                        val description = attrs.optNullableString("description")?.cleanApiText()
                        val imageObj = attrs.optJSONObject("image")
                        val imageUrl = imageObj?.optString("original")
                            ?: imageObj?.optString("large")
                            ?: imageObj?.optString("medium")
                        val malId = attrs.optInt("malId", 0)

                        KitsuCharacterParsed(name, description, imageUrl, malId)
                    }.getOrNull()

                    if (kitsuDetail != null && kitsuDetail.malId > 0) {
                        val jikanDetail = runCatching {
                            fetchCharacterDetail("jikan", kitsuDetail.malId)
                        }.getOrNull()
                        if (jikanDetail != null) {
                            jikanDetail
                        } else {
                            KitsugiCharacterDetail(
                                id = characterId,
                                name = kitsuDetail.name,
                                nativeName = null,
                                alternativeNames = emptyList(),
                                imageUrl = kitsuDetail.imageUrl,
                                gender = null,
                                age = null,
                                birthday = null,
                                bloodType = null,
                                biography = kitsuDetail.description,
                                voiceActors = emptyList(),
                                mediaAppearances = emptyList(),
                                source = "kitsu"
                            )
                        }
                    } else if (kitsuDetail != null) {
                        KitsugiCharacterDetail(
                            id = characterId,
                            name = kitsuDetail.name,
                            nativeName = null,
                            alternativeNames = emptyList(),
                            imageUrl = kitsuDetail.imageUrl,
                            gender = null,
                            age = null,
                            birthday = null,
                            bloodType = null,
                            biography = kitsuDetail.description,
                            voiceActors = emptyList(),
                            mediaAppearances = emptyList(),
                            source = "kitsu"
                        )
                    } else {
                        null
                    }
                }
                else -> null
            }
        }
    }

    private suspend fun fetchAniListCharacterByName(name: String): KitsugiCharacterDetail? {
        val query = """
            query (${'$'}search: String) {
                Page(page: 1, perPage: 1) {
                    characters(search: ${'$'}search) {
                        id
                        isFavourite
                        name {
                            userPreferred
                            full first middle last
                            native
                            alternative
                            alternativeSpoiler
                        }
                        image {
                            large
                        }
                        description
                        gender
                        dateOfBirth {
                            year
                            month
                            day
                        }
                        age
                        bloodType
                        media(page: 1, perPage: 25) {
                            edges {
                                characterRole
                                node {
                                    id
                                    idMal
                                    title { userPreferred english romaji native }
                                    coverImage { large }
                                    type
                                }
                                voiceActors {
                                    id
                                    name { userPreferred full first middle last native alternative }
                                    image { medium }
                                    language
                                    languageV2
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
            val charactersArr = root.optJSONObject("data")?.optJSONObject("Page")?.optJSONArray("characters") ?: return@runCatching null
            if (charactersArr.length() == 0) return@runCatching null
            val data = charactersArr.getJSONObject(0)

            val nameObj = data.optJSONObject("name")
            val personName = nameObj.aniListPersonName()
            val charName = personName.preferred
            val nativeName = nameObj?.optNullableString("native")

            val alternativeNames = mutableListOf<String>()
            val altArray = nameObj?.optJSONArray("alternative")
            if (altArray != null) {
                for (i in 0 until altArray.length()) {
                    val alt = altArray.optString(i)
                    if (alt.isNotBlank() && alt != "null") alternativeNames.add(alt)
                }
            }
            val altSpoilerArray = nameObj?.optJSONArray("alternativeSpoiler")
            if (altSpoilerArray != null) {
                for (i in 0 until altSpoilerArray.length()) {
                    val alt = altSpoilerArray.optString(i)
                    if (alt.isNotBlank() && alt != "null") alternativeNames.add(alt)
                }
            }

            val imageUrl = data.optJSONObject("image")?.optNullableString("large")
            val biography = data.optNullableString("description")?.cleanApiText()

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
            val bloodType = data.optNullableString("bloodType")

            val mediaAppearances = mutableListOf<KitsugiCharacterMediaAppearance>()
            val voiceActors = mutableListOf<KitsugiVoiceActor>()

            val mediaObj = data.optJSONObject("media")
            val edges = mediaObj?.optJSONArray("edges")
            if (edges != null) {
                for (i in 0 until edges.length()) {
                    val edge = edges.optJSONObject(i) ?: continue
                    val node = edge.optJSONObject("node") ?: continue
                    val role = (edge.optNullableString("characterRole") ?: "SUPPORTING").toTurkishCharacterRole()
                    val mediaId = node.optInt("id")
                    val idMal = node.optionalPositiveInt("idMal")
                    val title = node.optJSONObject("title")?.optNullableString("userPreferred") ?: "Bilinmeyen"
                    val titleEnglish = node.optJSONObject("title")?.optNullableString("english")
                    val titleNative = node.optJSONObject("title")?.optNullableString("native")
                    val titleRomaji = node.optJSONObject("title")?.optNullableString("romaji")
                    val imgUrl = node.optJSONObject("coverImage")?.optNullableString("large")
                    val type = node.optNullableString("type").orEmpty().lowercase()

                    val stableId = idMal ?: (100_000_000 + mediaId)

                    mediaAppearances.add(
                        KitsugiCharacterMediaAppearance(
                            mediaId = stableId,
                            title = title,
                            imageUrl = imgUrl,
                            mediaType = type.toTurkishMediaTypeString(),
                            characterRole = role,
                            source = if (idMal != null) "jikan" else "anilist",
                            titleEnglish = titleEnglish,
                            titleJapanese = titleNative,
                            titleRomaji = titleRomaji
                        )
                    )

                    val vaArr = edge.optJSONArray("voiceActors")
                    if (vaArr != null) {
                        for (j in 0 until vaArr.length()) {
                            val va = vaArr.optJSONObject(j) ?: continue
                            val vaId = va.optInt("id")
                            val vaPersonName = va.optJSONObject("name").aniListPersonName()
                            val vaName = vaPersonName.preferred
                            val vaImg = va.optJSONObject("image")?.optNullableString("medium")
                            val vaLang = (va.optNullableString("languageV2")
                                ?: va.optNullableString("language")
                                ?: "Japanese").toTurkishLanguage()
                            voiceActors.add(
                                KitsugiVoiceActor(
                                    id = vaId,
                                    name = vaName,
                                    romanizedName = vaPersonName.romanized,
                                    nativeName = vaPersonName.native,
                                    language = vaLang,
                                    imageUrl = vaImg,
                                    source = "anilist"
                                )
                            )
                        }
                    }
                }
            }

            val isFavourite = data.optBoolean("isFavourite", false)
            KitsugiCharacterDetail(
                id = data.optInt("id"),
                name = charName,
                nativeName = nativeName,
                alternativeNames = alternativeNames,
                imageUrl = imageUrl,
                biography = biography,
                gender = gender,
                birthday = birthday,
                age = age,
                bloodType = bloodType,
                mediaAppearances = mediaAppearances,
                voiceActors = voiceActors,
                isFavourite = isFavourite,
                aniListId = data.optInt("id"),
                romanizedName = personName.romanized
            )
        }.getOrNull()
    }

    private suspend fun enrichCharactersWithAnimeImages(
        characters: List<KitsugiCharacter>,
        titleCandidates: List<String>,
        realMalId: Int?
    ): List<KitsugiCharacter> {
        if (characters.isEmpty()) return characters
        // Başlık adayları: TMDB kaynaklı içerikte başlık Türkçeleştirilmiş olabildiği için
        // tek başlıkla arama sık sık boş dönüyor ve karakter görselleri hiç dolmuyordu.
        val cleanTitles = titleCandidates
            .map { it.replace(Regex("\\s*\\(.*?\\)"), "").trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(MAX_ANILIST_TITLE_LOOKUPS)
        if (cleanTitles.isEmpty() && (realMalId == null || realMalId <= 0)) {
            return characters
        }

        return runCatching {
            // Aday sorgular: önce MAL ID'si (kesin), sonra başlık aramaları. Karakter
            // adlarıyla eşleşme üreten İLK aday kullanılır — yanlış yapımın karakterleriyle
            // görsel doldurmamak için eşleşme şartı aranır.
            val lookups = buildList {
                if (realMalId != null && realMalId > 0) add(JSONObject().put("idMal", realMalId))
                cleanTitles.forEach { add(JSONObject().put("search", it)) }
            }

            var aniChars: List<AniCharInfo> = emptyList()
            var firstNonEmpty: List<AniCharInfo> = emptyList()
            for (variables in lookups) {
                val list = runCatching { fetchAniListMediaCharacters(variables) }.getOrNull().orEmpty()
                if (list.isEmpty()) continue
                if (firstNonEmpty.isEmpty()) firstNonEmpty = list
                if (characters.any { char -> matchAniListCharacter(char.name, list) != null }) {
                    aniChars = list
                    break
                }
            }
            if (aniChars.isEmpty()) aniChars = firstNonEmpty

            fun norm(s: String) = s.lowercase()
                .replace(Regex("\\s*\\((?:voice|uncredited|uncredited voice|child)\\)", RegexOption.IGNORE_CASE), "")
                .replace("ou", "o").replace("oo", "o").replace("oh", "o").replace("uu", "u")
                .replace(Regex("[^a-z0-9]"), "")

            fun getTokens(s: String): List<String> = s.lowercase()
                .replace(Regex("\\s*\\((?:voice|uncredited|uncredited voice|child)\\)", RegexOption.IGNORE_CASE), "")
                .replace("'", "")
                .replace("’", "")
                .split(Regex("[^a-z0-9]+"))
                .filter { it.isNotBlank() }

            val modifiers = setOf("former", "self", "child", "young", "older", "future", "past", "baby", "shadow", "clone", "alter", "dark", "fake")

            val fromAnime = characters.map { char ->
                val cleanName = char.name
                    .replace(Regex("\\s*\\((?:voice|uncredited|uncredited voice|child)\\)", RegexOption.IGNORE_CASE), "")
                    .trim()
                val isActorImage = !char.imageUrl.isNullOrBlank() &&
                    char.imageUrl == char.voiceActors.firstOrNull()?.imageUrl

                if (!char.imageUrl.isNullOrBlank() && !isActorImage) {
                    return@map char.copy(name = cleanName)
                }

                val targetNorm = norm(cleanName)
                if (targetNorm.length < 2) {
                    // Eşleşme aranamayacak kadar kısa ad: mevcut görsel (gerçek kişi
                    // kadrosunda oyuncu fotoğrafı) korunur — eskiden null'lanıyordu.
                    return@map char.copy(name = cleanName)
                }

                val targetTokens = getTokens(cleanName)
                val targetMods = targetTokens.filter { it in modifiers }.toSet()

                // 1. Birebir tam eşleşme (Tam ad veya alternatif isimler)
                val exactMatch = aniChars.firstOrNull { ani ->
                    val aniMods = getTokens(ani.full).filter { it in modifiers }.toSet()
                    if (targetMods != aniMods) return@firstOrNull false
                    norm(ani.full) == targetNorm || ani.alternatives.any { norm(it) == targetNorm }
                }
                if (exactMatch != null) {
                    return@map char.copy(
                        id = if (exactMatch.id > 0) exactMatch.id else char.id,
                        name = cleanName,
                        imageUrl = exactMatch.imageUrl,
                        source = if (exactMatch.id > 0) "anilist" else char.source
                    )
                }

                // 2. Token seti eşleşmesi (Japonca/Batı isim sırası tersliği: "Takeshi Gouda" vs "Gouda Takeshi")
                val tokenSetMatch = aniChars.firstOrNull { ani ->
                    val aniTokens = getTokens(ani.full)
                    val aniMods = aniTokens.filter { it in modifiers }.toSet()
                    if (targetMods != aniMods) return@firstOrNull false
                    aniTokens.toSet() == targetTokens.toSet()
                }
                if (tokenSetMatch != null) {
                    return@map char.copy(
                        id = if (tokenSetMatch.id > 0) tokenSetMatch.id else char.id,
                        name = cleanName,
                        imageUrl = tokenSetMatch.imageUrl,
                        source = if (tokenSetMatch.id > 0) "anilist" else char.source
                    )
                }

                // 3. İsim altkümesi eşleşmesi (Örn. "Eris Boreas Greyrat" vs "Eris Greyrat" veya "Nobita" vs "Nobita Nobi")
                // KRİTİK KORUMA: Karakterler mutlaka ayırt edici İLK/ÖZ İSMİ paylaşmalıdır.
                // Sadece soyadı ("Greyrat") ortaklığı ASLA eşleşme kabul edilmez!
                val subsetMatch = aniChars.firstOrNull { ani ->
                    val aniTokens = getTokens(ani.full)
                    val aniMods = aniTokens.filter { it in modifiers }.toSet()
                    if (targetMods != aniMods) return@firstOrNull false
                    if (aniTokens.isEmpty() || targetTokens.isEmpty()) return@firstOrNull false

                    val firstA = aniTokens.first()
                    val firstT = targetTokens.first()
                    val sharesGivenName = (firstA in targetTokens) || (firstT in aniTokens)
                    if (!sharesGivenName) return@firstOrNull false

                    val intersection = aniTokens.toSet().intersect(targetTokens.toSet())
                    val union = aniTokens.toSet().union(targetTokens.toSet())
                    if (union.isEmpty()) return@firstOrNull false
                    val jaccard = intersection.size.toDouble() / union.size.toDouble()
                    jaccard >= 0.5
                }
                if (subsetMatch != null) {
                    return@map char.copy(
                        id = if (subsetMatch.id > 0) subsetMatch.id else char.id,
                        name = cleanName,
                        imageUrl = subsetMatch.imageUrl,
                        source = if (subsetMatch.id > 0) "anilist" else char.source
                    )
                }

                // 4. Alternatif isimler (takma ad / alias) eşleşmesi (Örn. "Gian" -> "Takeshi Gouda")
                val aliasMatch = aniChars.firstOrNull { ani ->
                    val aniTokens = getTokens(ani.full)
                    val aniMods = aniTokens.filter { it in modifiers }.toSet()
                    if (targetMods != aniMods) return@firstOrNull false
                    ani.alternatives.any { alt ->
                        norm(alt) == targetNorm || getTokens(alt).toSet() == targetTokens.toSet()
                    }
                }
                if (aliasMatch != null) {
                    return@map char.copy(
                        id = if (aliasMatch.id > 0) aliasMatch.id else char.id,
                        name = cleanName,
                        imageUrl = aliasMatch.imageUrl,
                        source = if (aliasMatch.id > 0) "anilist" else char.source
                    )
                }

                // Eşleşme yok: MEVCUT görsel korunur. Gerçek kişi (live-action) kadrosunda
                // karakter görseli yerine oyuncu fotoğrafı gösterilir; eskiden bu görsel
                // null'lanıyor ve kartlarda yalnızca baş harfler kalıyordu.
                char.copy(name = cleanName)
            }
            if (fromAnime.none { it.imageUrl.isNullOrBlank() }) {
                fromAnime
            } else {
                // Anime düzeyindeki eşleşme görsel getirmediyse karakter ADIYLA AniList
                // araması yapılır (sınırlı sayıda ve süre bütçesiyle).
                enrichMissingImagesFromCharacterSearch(fromAnime, realMalId)
            }
        }.getOrElse { e ->
            Log.e(TAG, "AniList character image enrichment error: ${e.message}", e)
            characters
        }
    }

    /**
     * AniList araması için başlık adayları: ekrandaki başlık + detaydaki tüm başlık
     * varyantları (romaji/İngilizce/Japonca/eşanlamlılar). TMDB kaynaklı içerikte gösterilen
     * başlık Türkçeleştirilmiş olabildiği için tek başlıkla arama sık sık boş dönüyor ve
     * karakter görselleri hiç dolmuyordu.
     */
    private fun buildTitleCandidates(title: String?, detail: KitsugiMediaDetail?): List<String> {
        val candidates = mutableListOf<String?>()
        candidates.add(title)
        detail?.let { d ->
            candidates.add(d.title)
            candidates.add(d.titleRomaji)
            candidates.add(d.titleEnglish)
            candidates.add(d.titleJapanese)
            candidates.add(d.titleNative)
            d.synonyms.forEach { candidates.add(it) }
        }
        return candidates
            .mapNotNull { it?.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
    }

    /** AniList `Media(idMal|search).characters` sorgusunu çalıştırıp karakterleri döner. */
    private suspend fun fetchAniListMediaCharacters(variables: JSONObject): List<AniCharInfo> {
        val query = """
            query (${'$'}idMal: Int, ${'$'}search: String) {
                Media(idMal: ${'$'}idMal, search: ${'$'}search, type: ANIME) {
                    characters(perPage: 50, sort: [ROLE, RELEVANCE]) {
                        edges {
                            role
                            node {
                                id
                                name {
                                    full
                                    native
                                    alternative
                                }
                                image {
                                    large
                                    medium
                                }
                            }
                        }
                    }
                }
            }
        """.trimIndent()

        val response = KitsugiApiBase.executeAniListQuery(query, variables) ?: return emptyList()
        val edges = runCatching {
            JSONObject(response).optJSONObject("data")
                ?.optJSONObject("Media")
                ?.optJSONObject("characters")
                ?.optJSONArray("edges")
        }.getOrNull() ?: return emptyList()

        val result = mutableListOf<AniCharInfo>()
        for (i in 0 until edges.length()) {
            val edge = edges.optJSONObject(i) ?: continue
            val node = edge.optJSONObject("node") ?: continue
            val nameObj = node.optJSONObject("name") ?: continue
            val full = nameObj.optString("full", "")
            val native = nameObj.optNullableString("native")
            val altArray = nameObj.optJSONArray("alternative")
            val alternatives = mutableListOf<String>()
            if (altArray != null) {
                for (j in 0 until altArray.length()) {
                    val alt = altArray.optString(j)
                    if (alt.isNotBlank()) alternatives.add(alt)
                }
            }
            val imageObj = node.optJSONObject("image")
            val img = imageObj?.optNullableString("large")
                ?: imageObj?.optNullableString("medium")
            val id = node.optInt("id", 0)
            if (full.isNotBlank() && !img.isNullOrBlank()) {
                result.add(AniCharInfo(id, full, native, alternatives, img))
            }
        }
        return result
    }

    /**
     * Bir karakter adını AniList karakter listesiyle eşler (birebir → token seti →
     * altküme → alternatif isim sırası). Eşleşme yoksa null.
     */
    private fun matchAniListCharacter(
        rawName: String,
        aniChars: List<AniCharInfo>
    ): AniCharInfo? {
        if (aniChars.isEmpty()) return null

        fun norm(s: String) = s.lowercase()
            .replace(Regex("\\s*\\((?:voice|uncredited|uncredited voice|child)\\)", RegexOption.IGNORE_CASE), "")
            .replace("ou", "o").replace("oo", "o").replace("oh", "o").replace("uu", "u")
            .replace(Regex("[^a-z0-9]"), "")

        fun getTokens(s: String): List<String> = s.lowercase()
            .replace(Regex("\\s*\\((?:voice|uncredited|uncredited voice|child)\\)", RegexOption.IGNORE_CASE), "")
            .replace("'", "")
            .replace("’", "")
            .split(Regex("[^a-z0-9]+"))
            .filter { it.isNotBlank() }

        val cleanName = rawName
            .replace(Regex("\\s*\\((?:voice|uncredited|uncredited voice|child)\\)", RegexOption.IGNORE_CASE), "")
            .trim()
        val targetNorm = norm(cleanName)
        if (targetNorm.length < 2) return null

        val targetTokens = getTokens(cleanName)
        val targetMods = targetTokens.filter { it in ANI_CHAR_NAME_MODIFIERS }.toSet()

        // 1. Birebir tam eşleşme (tam ad veya alternatif isimler)
        aniChars.firstOrNull { ani ->
            val aniMods = getTokens(ani.full).filter { it in ANI_CHAR_NAME_MODIFIERS }.toSet()
            if (targetMods != aniMods) return@firstOrNull false
            norm(ani.full) == targetNorm || ani.alternatives.any { norm(it) == targetNorm }
        }?.let { return it }

        // 2. Token seti eşleşmesi (Japonca/Batı isim sırası tersliği)
        aniChars.firstOrNull { ani ->
            val aniTokens = getTokens(ani.full)
            val aniMods = aniTokens.filter { it in ANI_CHAR_NAME_MODIFIERS }.toSet()
            if (targetMods != aniMods) return@firstOrNull false
            aniTokens.toSet() == targetTokens.toSet()
        }?.let { return it }

        // 3. İsim altkümesi eşleşmesi — ayırt edici ilk/öz isim paylaşılmalıdır.
        aniChars.firstOrNull { ani ->
            val aniTokens = getTokens(ani.full)
            val aniMods = aniTokens.filter { it in ANI_CHAR_NAME_MODIFIERS }.toSet()
            if (targetMods != aniMods) return@firstOrNull false
            if (aniTokens.isEmpty() || targetTokens.isEmpty()) return@firstOrNull false
            val sharesGivenName = (aniTokens.first() in targetTokens) || (targetTokens.first() in aniTokens)
            if (!sharesGivenName) return@firstOrNull false
            val intersection = aniTokens.toSet().intersect(targetTokens.toSet())
            val union = aniTokens.toSet().union(targetTokens.toSet())
            if (union.isEmpty()) return@firstOrNull false
            intersection.size.toDouble() / union.size.toDouble() >= 0.5
        }?.let { return it }

        // 4. Alternatif isim (takma ad) eşleşmesi
        return aniChars.firstOrNull { ani ->
            val aniTokens = getTokens(ani.full)
            val aniMods = aniTokens.filter { it in ANI_CHAR_NAME_MODIFIERS }.toSet()
            if (targetMods != aniMods) return@firstOrNull false
            ani.alternatives.any { alt ->
                norm(alt) == targetNorm || getTokens(alt).toSet() == targetTokens.toSet()
            }
        }
    }

    /**
     * Görseli boş kalan karakterler için AniList KARAKTER araması.
     *
     * Yapım düzeyindeki sorgu (MAL ID'si/başlık) tutmadığında ya da ad eşleşmediğinde
     * karakter kartları baş harfe düşüyordu. Bu adım yalnızca:
     *  - görseli boş, adı en az 3 karakter olan karakterler için,
     *  - en fazla [MAX_ANILIST_CHAR_IMAGE_LOOKUPS] kayıt için,
     *  - toplam [ANILIST_CHAR_IMAGE_FALLBACK_BUDGET_MS] bütçesiyle
     * çalışır; sonuç gelmezse liste olduğu gibi kalır (sekme beklemez).
     */
    private suspend fun enrichMissingImagesFromCharacterSearch(
        characters: List<KitsugiCharacter>,
        realMalId: Int?
    ): List<KitsugiCharacter> {
        val targets = characters
            .filter { it.imageUrl.isNullOrBlank() && it.name.trim().length >= 3 }
            .take(MAX_ANILIST_CHAR_IMAGE_LOOKUPS)
        if (targets.isEmpty()) return characters

        val resolved = HashMap<Int, AniCharInfo>()
        runCatching {
            withTimeoutOrNull(ANILIST_CHAR_IMAGE_FALLBACK_BUDGET_MS) {
                coroutineScope {
                    for (char in targets) {
                        val info = runCatching {
                            searchAniListCharacterByName(char.name, realMalId)
                        }.getOrNull()
                        if (info != null) resolved[char.id] = info
                    }
                }
            }
        }
        if (resolved.isEmpty()) return characters

        return characters.map { char ->
            val info = resolved[char.id] ?: return@map char
            char.copy(
                id = if (info.id > 0) info.id else char.id,
                imageUrl = info.imageUrl,
                source = if (info.id > 0) "anilist" else char.source
            )
        }
    }

    /**
     * AniList `Page.characters(search:)` ile karakter arar; bulunan karakterin yer aldığı
     * yapımlar [realMalId] ile (varsa) doğrulanır, böylece aynı adlı farklı karakterlerin
     * görseli yanlış karta takılmaz.
     */
    private suspend fun searchAniListCharacterByName(name: String, realMalId: Int?): AniCharInfo? {
        val query = """
            query (${'$'}search: String) {
                Page(perPage: 6) {
                    characters(search: ${'$'}search, sort: [ROLE, RELEVANCE]) {
                        id
                        name {
                            full
                            native
                            alternative
                        }
                        image {
                            large
                            medium
                        }
                        media(perPage: 8, sort: [POPULARITY_DESC]) {
                            nodes {
                                id
                                idMal
                                type
                            }
                        }
                    }
                }
            }
        """.trimIndent()

        val variables = JSONObject().put("search", name.trim())
        val response = KitsugiApiBase.executeAniListQuery(query, variables) ?: return null
        val nodes = runCatching {
            JSONObject(response).optJSONObject("data")
                ?.optJSONObject("Page")
                ?.optJSONArray("characters")
        }.getOrNull() ?: return null

        for (i in 0 until nodes.length()) {
            val node = nodes.optJSONObject(i) ?: continue
            val nameObj = node.optJSONObject("name") ?: continue
            val full = nameObj.optString("full", "")
            if (full.isBlank()) continue
            val altArray = nameObj.optJSONArray("alternative")
            val alternatives = mutableListOf<String>()
            if (altArray != null) {
                for (j in 0 until altArray.length()) {
                    val alt = altArray.optString(j)
                    if (alt.isNotBlank()) alternatives.add(alt)
                }
            }
            val imageObj = node.optJSONObject("image")
            val img = imageObj?.optNullableString("large")
                ?: imageObj?.optNullableString("medium")
            if (img.isNullOrBlank()) continue

            val info = AniCharInfo(node.optInt("id", 0), full, nameObj.optNullableString("native"), alternatives, img)

            // Ad eşleşmesi ZORUNLU: AniList araması alakasız karakterler döndürebilir.
            if (matchAniListCharacter(name, listOf(info)) == null) continue

            // Doğrulama: karakterin yer aldığı yapımlardan biri hedef yapım mı?
            val mediaNodes = node.optJSONObject("media")?.optJSONArray("nodes")
            if (realMalId != null && realMalId > 0 && mediaNodes != null) {
                var matchesTarget = false
                var hasIdMal = false
                for (m in 0 until mediaNodes.length()) {
                    val media = mediaNodes.optJSONObject(m) ?: continue
                    val idMal = media.optInt("idMal", 0)
                    if (idMal > 0) hasIdMal = true
                    if (idMal == realMalId) {
                        matchesTarget = true
                        break
                    }
                }
                // idMal bilgisi olan yapımlar var ama hedefle uyuşmuyorsa bu karakteri atla.
                if (!matchesTarget && hasIdMal) continue
            }
            return info
        }
        return null
    }

    private fun mergeVoiceActorsIntoCharacters(
        characters: List<KitsugiCharacter>,
        referenceCharacters: List<KitsugiCharacter>
    ): List<KitsugiCharacter> {
        if (referenceCharacters.isEmpty()) return characters

        fun norm(s: String): String = s.lowercase()
            .replace(Regex("\\s*\\((?:voice|uncredited|uncredited voice|child)\\)", RegexOption.IGNORE_CASE), "")
            .replace("ou", "o")
            .replace("oo", "o")
            .replace("oh", "o")
            .replace("uu", "u")
            .replace(Regex("[^a-z0-9]"), "")

        fun getTokens(s: String): Set<String> = s.lowercase()
            .replace(Regex("\\s*\\((?:voice|uncredited|uncredited voice|child)\\)", RegexOption.IGNORE_CASE), "")
            .split(Regex("[\\s,\\-_.]+"))
            .filter { it.length >= 3 }
            .map { it.replace("ou", "o").replace("oo", "o").replace("oh", "o").replace("uu", "u") }
            .toSet()

        return characters.map { char ->
            if (char.voiceActors.isNotEmpty()) return@map char

            val targetNorm = norm(char.name)
            val targetTokens = getTokens(char.name)

            // 1. Match by ID (if positive and equal)
            var matchedRef = referenceCharacters.firstOrNull { ref ->
                char.id > 0 && ref.id > 0 && char.id == ref.id && ref.voiceActors.isNotEmpty()
            }

            // 2. Match by normalized name (exact)
            if (matchedRef == null && targetNorm.length >= 2) {
                matchedRef = referenceCharacters.firstOrNull { ref ->
                    norm(ref.name) == targetNorm && ref.voiceActors.isNotEmpty()
                }
            }

            // 3. Match by reversed name tokens (e.g. "Yoshino Himekawa" vs "Himekawa Yoshino")
            if (matchedRef == null && targetTokens.size >= 2) {
                matchedRef = referenceCharacters.firstOrNull { ref ->
                    val refTokens = getTokens(ref.name)
                    refTokens == targetTokens && ref.voiceActors.isNotEmpty()
                }
            }

            // 4. Match by token subset / containment (e.g. "Yoshino" vs "Yoshino Himekawa")
            if (matchedRef == null && targetTokens.isNotEmpty()) {
                matchedRef = referenceCharacters.firstOrNull { ref ->
                    val refTokens = getTokens(ref.name)
                    (targetTokens.all { it in refTokens } || refTokens.all { it in targetTokens }) && ref.voiceActors.isNotEmpty()
                }
            }

            // 5. Match by substring if name is long enough
            if (matchedRef == null && targetNorm.length >= 4) {
                matchedRef = referenceCharacters.firstOrNull { ref ->
                    val refNorm = norm(ref.name)
                    refNorm.length >= 4 && (refNorm.contains(targetNorm) || targetNorm.contains(refNorm)) && ref.voiceActors.isNotEmpty()
                }
            }

            if (matchedRef != null) {
                char.copy(voiceActors = matchedRef.voiceActors)
            } else {
                char
            }
        }
    }

    private data class AniCharInfo(
        val id: Int,
        val full: String,
        val native: String?,
        val alternatives: List<String>,
        val imageUrl: String
    )
}

/** Geçici parse sonucu — fetchCharacterDetail içinde Kitsu API yanıtını taşır. */
private data class KitsuCharacterParsed(
    val name: String,
    val description: String?,
    val imageUrl: String?,
    val malId: Int
)

