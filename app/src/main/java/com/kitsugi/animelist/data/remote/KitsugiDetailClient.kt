package com.kitsugi.animelist.data.remote

import com.kitsugi.animelist.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import com.kitsugi.animelist.utils.*

class KitsugiDetailClient {

    /**
     * Birincil detay zincirinin (kaynak + fallback'ler) toplam zaman tavanı.
     * Yavaş/askıda kalan istekler sayfanın önünü bloklamaz; eskimeş önbellek devreye girer.
     */
    private val PRIMARY_FETCH_TIMEOUT_MS = 45_000L

    /**
     * Kitsu özet temizliğinin (şüpheli özet → doğrulanmış kaynak) kendi zaman tavanı.
     * Birincil zincirin tavanından AYRI tutulur: temizleme yarıda kesilirse bozuk özet
     * yine de gösterilmez (özet boşaltılır), sayfa asla beklemez.
     */
    private val KITSU_SANITIZE_TIMEOUT_MS = 15_000L

    private suspend fun getTurkishMetadataFromTmdb(
        source: String,
        externalId: Int,
        mediaType: MediaType,
        providedTmdbId: Int? = null,
        providedRealMalId: Int? = null
    ): KitsugiMediaDetail? {
        if (mediaType == MediaType.Manga) return null

        val tmdbId = providedTmdbId ?: run {
            // Source'a göre doğru ID tipini belirle
            val malIdForResolve: Int? = when (MalJikanMediaSupport.canonicalSource(source)) {
                "simkl" -> providedRealMalId
                "jikan", "mal" -> externalId
                "kitsu" -> providedRealMalId
                // Bangumi stableId'si (500M+) MAL ID DEĞİLDİR; yalnızca çözülmüş gerçek MAL ID kullanılır.
                "bangumi" -> KitsugiBangumiDetailClient.sanitizeMalId(providedRealMalId)
                "anilist" -> {
                    // stableId < 100_000_000 → AniList arama sonucu MAL ID ile döndü
                    // Bu durumda MAL ID olarak çözümle, AniList ID olarak değil
                    if (externalId < 100_000_000) externalId else providedRealMalId
                }
                else -> null
            }
            val aniListIdForResolve: Int? = if (source.lowercase() == "anilist" && externalId >= 100_000_000) {
                // 100_000_000+ offset'li stableId → gerçek AniList ID'yi çıkar
                externalId - 100_000_000
            } else null
            val kitsuIdForResolve: Int? = if (source.lowercase() == "kitsu" && mediaType != MediaType.Manga) {
                // Yalnızca gerçek Kitsu stableId aralığındaki değer çözülür; altındaki
                // değerler MAL ID'dir ve kitsuId olarak asla yorumlanmamalıdır.
                KitsuIdNamespace.rawIdFromStable(externalId)
            } else null
            
            KitsugiIdResolver.resolveIds(
                malId = malIdForResolve,
                aniListId = aniListIdForResolve,
                tmdbId = providedTmdbId,
                mediaType = mediaType,
                kitsuId = kitsuIdForResolve
            ).tmdbId
        }

        if (tmdbId != null && tmdbId > 0) {
            val isMovie = mediaType == MediaType.Movie
            val firstTry = TmdbApiClient().fetchMediaDetail(tmdbId, isMovie)
            if (firstTry != null) return firstTry

            // Anime veya TMDB kaynaklı içeriklerde film/dizi ayrımı yanlış yapılmış olabilir.
            // İlk deneme null dönerse, diğer formatta tekrar çekmeyi dene (TV -> Movie veya Movie -> TV).
            if (mediaType != MediaType.Manga) {
                return TmdbApiClient().fetchMediaDetail(tmdbId, !isMovie)
            }
        }
        return null
    }

    /**
     * Simkl kayıtları için TMDB tabanlı ayrıntı çözümü.
     *
     * Simkl API'si yavaş ve gecikmeli veri döndürdüğü için Simkl kütüphanesindeki
     * kayıtların ayrıntı sayfası önce TMDB üzerinden açılır:
     *  1. Bilinen/çözülen TMDB ID ile doğrudan TMDB,
     *  2. Olmazsa ters medya türüyle (film ↔ dizi) TMDB,
     *  3. TMDB ID hiç yoksa başlıkla TMDB araması.
     * Hiçbiri tutmazsa null döner; çağıran taraf MAL (Jikan) → Simkl zincirini dener.
     */
    private suspend fun fetchSimklDetailViaTmdb(
        tmdbId: Int?,
        mediaType: MediaType,
        title: String?
    ): KitsugiMediaDetail? {
        if (mediaType == MediaType.Manga) return null

        if (tmdbId != null && tmdbId > 0) {
            val isMovie = mediaType == MediaType.Movie
            TmdbApiClient().fetchMediaDetail(tmdbId, isMovie)?.let { return it }
            if (mediaType != MediaType.Manga) {
                TmdbApiClient().fetchMediaDetail(tmdbId, !isMovie)?.let { return it }
            }
        }

        // TMDB ID çözülemedi → başlıkla ara (dizi/film başlıkları TMDB'de en güvenilir sonucu verir)
        val searchTitle = title?.trim().orEmpty()
        if (searchTitle.isBlank()) return null

        val results = runCatching { TmdbApiClient().search(searchTitle) }.getOrNull().orEmpty()
        if (results.isEmpty()) return null

        // Beklenen tür önce denenir; anime kayıtları TMDB'de dizi ya da film olarak görünür.
        val preferredTypes = when (mediaType) {
            MediaType.Movie -> listOf(MediaType.Movie, MediaType.TvShow)
            MediaType.TvShow -> listOf(MediaType.TvShow, MediaType.Movie)
            else -> listOf(MediaType.TvShow, MediaType.Movie, MediaType.Anime)
        }

        for (type in preferredTypes) {
            val candidate = results.firstOrNull { it.type == type && (it.tmdbId ?: 0) > 0 } ?: continue
            TmdbApiClient().fetchMediaDetail(candidate.tmdbId!!, type == MediaType.Movie)?.let { return it }
        }
        return null
    }

    suspend fun fetchSynopsis(
        source: String,
        externalId: Int?,
        mediaType: MediaType
    ): String? {
        return withContext(Dispatchers.IO) {
            if (externalId == null || externalId <= 0) {
                return@withContext null
            }

            if (mediaType != MediaType.Manga) {
                // Simkl kayıtlarında TMDB çözümü için önbellekteki gerçek ID'ler kullanılır,
                // böylece özet de Simkl API'si yerine TMDB'den gelebilir.
                // Bangumi için de aynı yol: önbellekteki çözülmüş TMDB/MAL kimlikleri kullanılır.
                val cachedSimklDetail = when {
                    source.equals("simkl", ignoreCase = true) -> DetailCache.getMediaDetail("simkl", externalId)
                    source.equals("bangumi", ignoreCase = true) -> DetailCache.getMediaDetail("bangumi", externalId)
                    else -> null
                }
                val trMeta = getTurkishMetadataFromTmdb(
                    source = source,
                    externalId = externalId,
                    mediaType = mediaType,
                    providedTmdbId = cachedSimklDetail?.tmdbId,
                    providedRealMalId = cachedSimklDetail?.realMalId
                )
                if (trMeta != null && !trMeta.synopsis.isNullOrBlank()) {
                    return@withContext trMeta.synopsis
                }
            }

            when (MalJikanMediaSupport.canonicalSource(source)) {
                "jikan", "mal" -> KitsugiMalDetailClient.fetchSynopsis(
                    malId = externalId,
                    mediaType = mediaType
                )

                "anilist" -> KitsugiAniListDetailClient.fetchSynopsis(
                    stableId = externalId,
                    mediaType = mediaType
                )

                "simkl" -> KitsugiSimklDetailClient.fetchSimklDetailDirect(externalId, mediaType)?.synopsis

                "bangumi" -> KitsugiBangumiDetailClient.fetchDetail(externalId, mediaType)?.synopsis

                "kitsu" -> {
                    // Kitsu özeti ancak sağlıklıysa döner. Bozuk/alakasız özetler
                    // (örn. "ROAR" 2004 kaydındaki 1997 Fox TV dizisi özeti) asla
                    // gösterilmez — bu durumda null döner, UI "Açıklama bulunamadı" der.
                    val kitsuDetail = KitsuExploreClient.fetchDetailByStableId(externalId, mediaType)
                    val synopsis = kitsuDetail?.synopsis?.takeIf { it.isNotBlank() }
                    if (synopsis != null &&
                        KitsuSynopsisValidator.suspiciousReason(synopsis, kitsuDetail?.year, mediaType) != null
                    ) {
                        android.util.Log.w(
                            "KitsugiDetailClient",
                            "Kitsu fetchSynopsis: bozuk özet gösterilmedi ('${kitsuDetail?.title}', ${kitsuDetail?.year})"
                        )
                        null
                    } else {
                        synopsis
                    }
                }

                "shikimori" -> {
                    // Shikimori detayı özet taşır (Türkçeye çevrilir). Özet yoksa gerçek
                    // MAL ID'si çözülüp MAL/Jikan özeti denenir — Shikimori ID'si MAL
                    // ID'si yerine kullanılmaz.
                    val shiki = KitsugiShikimoriClient.fetchDetail(externalId, mediaType)
                    val shikiSynopsis = shiki?.synopsis?.takeIf { it.isNotBlank() }
                    if (shikiSynopsis != null) {
                        shikiSynopsis
                    } else {
                        val malId = shiki?.realMalId?.takeIf { it > 0 }
                            ?: KitsugiIdResolver.resolveMalIdFromShikimori(externalId)
                        if (malId != null && malId > 0) {
                            KitsugiMalDetailClient.fetchSynopsis(malId = malId, mediaType = mediaType)
                        } else null
                    }
                }

                else -> null
            }
        }
    }

    /**
     * Birincil detayı çeker: kaynak istemcisi + fallback zinciri + Room önbelleği.
     *
     * TMDB zenginleştirmesi BURADA yapılmaz — o adım [enrichDetail] ile ayrı ve
     * (ViewModel tarafında) zaman tavanlı çalışır. Böylece detay sayfası asıl veriyle
     * HEMEN açılır; Türkçe/TMDB metaları arka planda gelince eklenir.
     */
    suspend fun fetchPrimaryDetail(
        source: String,
        externalId: Int?,
        mediaType: MediaType,
        tmdbId: Int? = null,
        realMalId: Int? = null,
        title: String? = null
    ): KitsugiMediaDetail? {
        return withContext(Dispatchers.IO) {
            // Kitsu kayıtlarında kimlik alanı boş olabilir (eski içe aktarmalar, kimlik onarımı
            // ya da hiç çözülememiş eşleme). Bu durumda detayı KAYIT BAŞLIĞINDAN çözmeye
            // çalışırız; kimlik yok diye sayfayı düşürmeyiz. Diğer kaynaklarda kimlik yoksa
            // güvenli bir çıkış yolu yoktur → eskisi gibi null.
            val hasUsableId = externalId != null && externalId > 0
            val isKitsuSource = source.lowercase() == "kitsu"
            if (!hasUsableId && !(isKitsuSource && !title.isNullOrBlank())) {
                return@withContext null
            }
            // Kimlik yoksa 0: aşağıda hiçbir yerde ham sayı Kitsu/MAL kimliği gibi
            // yorumlanmaz, yalnızca başlık çözümlemesi devreye girer.
            val extId: Int = externalId?.takeIf { it > 0 } ?: 0

            val context = com.kitsugi.animelist.KitsugiApplication.getInstance()?.applicationContext

            // Kitsu kayıtlarında kimlik alanı her zaman offset'li stableId taşımak zorunda.
            // Eski sürümlerde (veya MAL eşleşmesi bilinen kayıtlarda) bu alana gerçek MAL ID
            // yazıldığı için 35658 gibi bir MAL ID'si "Kitsu ID" diye yorumlanıp ALAKASIZ bir
            // yapımın ayrıntısı açılıyordu. Kimliği önce Kitsu uzayına kanonikleştiriyoruz.
            val kitsuCanonicalId = if (isKitsuSource) {
                KitsuIdNamespace.stableIdOrNull(extId)
                    ?: KitsuIdNamespace.resolveCanonicalStableId(
                        context = context,
                        storedId = null, // çözümleme realMalId/başlık üzerinden yapılsın
                        realMalId = realMalId ?: KitsuIdNamespace.realMalIdOf(extId),
                        isAnime = KitsuIdNamespace.isAnimeType(mediaType),
                        title = title
                    )
            } else {
                extId
            }

            val mediaTypeStr = mediaType.name.lowercase()
            val keyId = if (isKitsuSource) kitsuCanonicalId ?: extId else extId
            // Kimliği çözülememiş Kitsu kayıtlarında önbellek KULLANMIYORUZ: alan adı
            // (source + id) tek başına kimliği garanti etmiyor ve yanlış anahtar, yanlış veriyi
            // besleyebiliyor (gerçek hayatta görülen "alakasız veri" hatasının ikinci bacağı).
            val cacheKey = if (isKitsuSource && kitsuCanonicalId == null) {
                null
            } else {
                detailCacheKey(source, mediaTypeStr, keyId)
            }
            val legacyKey = if (cacheKey == null) {
                null
            } else if (source.lowercase() == "bangumi") {
                // Eski sürümler Bangumi kayıtlarını Kitsu başlık aramasıyla (alakasız/eksik veri)
                // önbelleğe yazıyordu; eski anahtar bilerek OKUNMAZ.
                null
            } else if (source.lowercase() == "kitsu") {
                // ks1: bozuk Kitsu özetleri temizlenmeye başlandı — eski (sürüm suffix'i
                // olmayan) anahtarlar bozuk özet taşıyabilir, bu yüzden bilerek OKUNMAZ.
                null
            } else if (source.lowercase() == "tmdb") {
                val typeStr = if (mediaType == MediaType.Movie) "movie" else "tv"
                "tmdb_${typeStr}_$extId"
            } else {
                "${source.lowercase()}_$keyId"
            }
            val db = context?.let { com.kitsugi.animelist.data.local.KitsugiDatabase.getDatabase(it) }
            val gson = com.google.gson.Gson()

            // 1. Fresh Cache Check (Room - 24 hours threshold)
            if (db != null && !cacheKey.isNullOrBlank()) {
                try {
                    val cached = db.persistentDetailCacheDao().getDetail(cacheKey)
                        // TMDB/Simkl için eski (sürümsüz) anahtarlar okunmaz: o kayıtlar
                        // düzeltme öncesi CJK başlıkları taşıyor olabilir ve 24 saat boyunca
                        // ekrana Japonca başlık düşmesine yol açardı.
                        ?: legacyKey?.takeIf { !MediaTitleResolver.isLatinPreferredSource(source) }
                            ?.let { db.persistentDetailCacheDao().getDetail(it) }
                    if (cached != null) {
                        val isFresh = (System.currentTimeMillis() - cached.cachedAtMs) < 24 * 60 * 60 * 1000L
                        if (isFresh) {
                            val detail = gson.fromJson(cached.detailJson, KitsugiMediaDetail::class.java)
                            if (detail != null) {
                                // Eski nsfw!="white" hatasından dolayı yanlışlıkla isAdult=true kalan içerikleri düzelt
                                val isActuallyAdult = detail.isAdult && (
                                    detail.rating?.contains("rx", ignoreCase = true) == true ||
                                    detail.rating?.contains("hentai", ignoreCase = true) == true ||
                                    detail.genres.any { it.contains("hentai", ignoreCase = true) }
                                )
                                val cleanDetail = if (detail.isAdult && !isActuallyAdult) {
                                    detail.copy(isAdult = false)
                                } else {
                                    detail
                                }

                                if (!title.isNullOrBlank() && !detail.title.isNullOrBlank() &&
                                    (source.lowercase() == "tmdb" || isKitsuSource)
                                ) {
                                    // TMDB ve Kitsu'da önbellek anahtarı yanlış kimlikten etkilenebildiği
                                    // için başlık doğrulaması yapıyoruz; uyuşmazsa önbelleği tamamen siliyor,
                                    // "geçersiz kılıp bir daha denemek"le uğraşmıyoruz.
                                    val orig = title.trim().lowercase()
                                    val dTitle = detail.title?.trim()?.lowercase().orEmpty()
                                    val dEnTitle = detail.titleEnglish?.trim()?.lowercase().orEmpty()
                                    val matches = dTitle.contains(orig) || orig.contains(dTitle) || dEnTitle.contains(orig) || orig.contains(dEnTitle)
                                    if (!matches) {
                                        android.util.Log.w("KitsugiDetailClient", "Cached $source detail title mismatch: expected '$title', got '${detail.title}'. Invalidating cache.")
                                        db.persistentDetailCacheDao().deleteDetail(cacheKey)
                                        legacyKey?.let { db.persistentDetailCacheDao().deleteDetail(it) }
                                        if (source.lowercase() == "tmdb") db.persistentDetailCacheDao().deleteDetail("tmdb_$extId")
                                    } else {
                                        android.util.Log.d("KitsugiDetailClient", "Serving fresh detail from Room cache for $cacheKey")
                                        return@withContext cleanDetail
                                    }
                                } else {
                                    android.util.Log.d("KitsugiDetailClient", "Serving fresh detail from Room cache for $cacheKey")
                                    return@withContext cleanDetail
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("KitsugiDetailClient", "Error reading detail cache: ${e.message}")
                }
            }

            // 2. Primary source fetch — toplam zaman tavanı: yavaş/hanging zincirler
            // (Jikan → ARM → TMDB → Kitsu ...) sayfanın önünü bloklamaz.
            val detail = withTimeoutOrNull(PRIMARY_FETCH_TIMEOUT_MS) { when (MalJikanMediaSupport.canonicalSource(source)) {
                "jikan", "mal" -> KitsugiMalDetailClient.fetchDetail(extId, mediaType)
                "shikimori" -> {
                    // NOT: externalId Shikimori ID'sidir — MAL ID'si olarak KULLANILMAZ!
                    // Shikimori başarısızsa gerçek MAL ID'si ARM ile çözülüp dene.
                    KitsugiShikimoriClient.fetchDetail(extId, mediaType)
                        ?: run {
                            val malId = KitsugiIdResolver.resolveMalIdFromShikimori(extId)
                            if (malId != null && malId > 0) KitsugiMalDetailClient.fetchDetail(malId, mediaType) else null
                        }
                }
                "anilist" -> KitsugiAniListDetailClient.fetchDetail(extId, mediaType)
                // Bangumi: yerel (v0 + p1) veri; MAL/AniList/TMDB ile birleştirme enrichDetail'de.
                "bangumi" -> KitsugiBangumiDetailClient.fetchDetail(extId, mediaType)
                // Kitsu keşfet fallback öğeleri: stableId = kitsuId + 300_000_000
                "kitsu" -> {
                    // Kanonik (300M aralığındaki) Kitsu stableId'si olmadan asla ham ID ile
                    // çekmeyiz — olmayan bir kimlikle çekmek alakasız bir yapımın verisini getirir.
                    val canonicalKitsuId = kitsuCanonicalId
                    android.util.Log.d(
                        "KitsugiDetailClient",
                        if (canonicalKitsuId != null) "Fetching Kitsu detail for stableId=$canonicalKitsuId"
                        else "Kitsu identity unresolved for stored id=$extId ('$title'); resolving by title"
                    )
                    val rawKitsuDetail = canonicalKitsuId?.let { KitsuExploreClient.fetchDetailByStableId(it, mediaType) }

                    // ── Yedek zinciri ────────────────────────────────────────────────────
                    // Kitsu kaydı veri döndürmediğinde (Kitsu'da silinmiş/18+ gizlenmiş kayıt,
                    // 404, eşleşmemiş kimlik) sayfa "yüklenemedi" ekranına düşmemelidir.
                    // Sıra: (a) kaydın/belleğin bildiği gerçek MAL ID → Jikan,
                    //       (b) sıkı başlık eşleşmeli MAL araması → Jikan,
                    //       (c) film/dizi ise başlıktan TMDB.
                    // Hiçbir adım başlığı doğrulanmamış bir yapımı kabul etmez.
                    var resolved: KitsugiMediaDetail? = rawKitsuDetail
                    val malIdFallback = realMalId?.takeIf { it in 1..99_999_999 }
                        ?: KitsuIdNamespace.realMalIdOf(extId)
                    if (resolved == null && malIdFallback != null) {
                        resolved = KitsugiMalDetailClient.fetchDetail(malIdFallback, mediaType)
                    }
                    if (resolved == null) {
                        resolved = fetchMalDetailByTitle(title, mediaType)
                    }
                    if (resolved == null) {
                        resolved = fetchTmdbDetailByTitle(title, mediaType)
                    }
                    // Bozuk/alakasız Kitsu özeti (örn. "ROAR" 2004 kaydı 1997 Fox TV dizisinin
                    // özetini taşıyor): şüpheli özeti doğrulanmış bir kaynakla değiştirir;
                    // doğrulanamazsa özet boşaltılır — asla alakasız özet gösterilmez.
                    resolved = sanitizeKitsuSynopsis(resolved, mediaType, malIdFallback, title, keyId)
                    val kitsuDetail = resolved
                    // AnimeThemes entegrasyonu: Kitsu ID'si ile tema müziklerini çek
                    if (kitsuDetail != null && rawKitsuDetail != null && mediaType != MediaType.Manga) {
                        val kitsuNumericId = KitsuIdNamespace.rawIdFromStable(canonicalKitsuId ?: extId)
                        if (kitsuNumericId != null && kitsuNumericId > 0) {
                            try {
                                val themes = KitsugiAnimeThemesClient.fetchAnimeThemes(kitsuNumericId, "Kitsu")
                                if (themes.first.isNotEmpty() || themes.second.isNotEmpty()) {
                                    android.util.Log.d("KitsugiDetailClient", "AnimeThemes Kitsu: ${themes.first.size} OP, ${themes.second.size} ED")
                                    kitsuDetail.copy(openings = themes.first, endings = themes.second)
                                } else kitsuDetail
                            } catch (e: Exception) {
                                android.util.Log.w("KitsugiDetailClient", "AnimeThemes Kitsu fetch failed: ${e.message}")
                                kitsuDetail
                            }
                        } else kitsuDetail
                    } else kitsuDetail
                }
                // TMDB discovery öğeleri: malId aslında tmdbId, doğrudan TMDB'den çek
                "tmdb" -> {
                    val effectiveTmdbId = tmdbId ?: extId
                    if (effectiveTmdbId > 0) {
                        val isMovie = mediaType == MediaType.Movie
                        val firstTry = TmdbApiClient().fetchMediaDetail(effectiveTmdbId, isMovie)
                        if (firstTry != null && !title.isNullOrBlank()) {
                            val originalTitle = title.trim().lowercase()
                            val fetchedTitle = firstTry.title?.trim()?.lowercase().orEmpty()
                            val fetchedEnTitle = firstTry.titleEnglish?.trim()?.lowercase().orEmpty()
                            val matches = fetchedTitle.contains(originalTitle) || originalTitle.contains(fetchedTitle) ||
                                          fetchedEnTitle.contains(originalTitle) || originalTitle.contains(fetchedEnTitle)
                            if (matches) {
                                firstTry
                            } else {
                                android.util.Log.w("KitsugiDetailClient", "TMDB detail title mismatch: expected '$title', got '$fetchedTitle'. Trying alternative type (!isMovie)...")
                                val secondTry = TmdbApiClient().fetchMediaDetail(effectiveTmdbId, !isMovie)
                                secondTry ?: firstTry
                            }
                        } else {
                            firstTry ?: TmdbApiClient().fetchMediaDetail(effectiveTmdbId, !isMovie)
                        }
                    } else null
                }
                "simkl" -> {
                    // ── Öncelik zinciri (TMDB ilk sıradadır) ────────────────────────────
                    // Simkl API'si verileri gecikmeli döndürdüğü için Simkl kayıtlarının
                    // ayrıntı sayfası önce TMDB üzerinden açılır. TMDB çözülemezse
                    // anime kayıtlarında Jikan (MAL), son çare olarak Simkl kullanılır.
                    val malIdForResolve = realMalId?.takeIf { it > 0 }
                        ?: DetailCache.getMediaDetail("simkl", extId)?.realMalId
                    val resolvedTmdb = tmdbId?.takeIf { it > 0 } ?: run {
                        KitsugiIdResolver.resolveIds(
                            malId = malIdForResolve,
                            aniListId = null,
                            tmdbId = null,
                            mediaType = mediaType
                        ).tmdbId
                    }

                    val tmdbDetail = fetchSimklDetailViaTmdb(
                        tmdbId = resolvedTmdb,
                        mediaType = mediaType,
                        title = title
                    )
                    if (tmdbDetail != null) {
                        tmdbDetail
                    } else if (mediaType == MediaType.Anime && malIdForResolve != null && malIdForResolve > 0) {
                        KitsugiMalDetailClient.fetchDetail(malIdForResolve, mediaType)
                            ?: KitsugiSimklDetailClient.fetchSimklDetailDirect(extId, mediaType)
                    } else {
                        KitsugiSimklDetailClient.fetchSimklDetailDirect(extId, mediaType)
                    }
                }
                else -> null
            } }

            var finalDetail = detail
            // Yedek zincirde Kitsu'dan gelen detayların bozuk özet taşıyıp taşımadığını
            // sonradan kontrol edebilmek için işaret.
            var detailFromKitsuFallback = false

            // 3. Fallback Client Chains (Live Backups) — aynı tavanla sınırlı
            if (finalDetail == null && !isKitsuSource) {
                withTimeoutOrNull(PRIMARY_FETCH_TIMEOUT_MS) {
                    if (mediaType == MediaType.Movie || mediaType == MediaType.TvShow) {
                        // TMDB Fallback: TVmaze for TV Shows
                        if (mediaType == MediaType.TvShow && !title.isNullOrBlank()) {
                            android.util.Log.d("KitsugiDetailClient", "TMDB returned null. Trying TVmaze fallback for TV show: $title")
                            finalDetail = TvMazeClient.fetchShowDetailByTitle(title)
                        }
                        // TMDB Fallback: Search fallback by title
                        if (finalDetail == null && !title.isNullOrBlank()) {
                            android.util.Log.d("KitsugiDetailClient", "Trying direct TMDB search fallback for: $title")
                            val searchResults = TmdbApiClient().search(title)
                            val matchedResult = searchResults.firstOrNull { it.type == mediaType }
                            if (matchedResult != null && matchedResult.tmdbId != null && matchedResult.tmdbId > 0) {
                                finalDetail = TmdbApiClient().fetchMediaDetail(matchedResult.tmdbId, mediaType == MediaType.Movie)
                            }
                        }
                    } else if (mediaType == MediaType.Anime) {
                        // Anime Fallback: Kitsu
                        android.util.Log.d("KitsugiDetailClient", "AniList/MAL detail returned null. Trying Kitsu fallback.")
                        val kitsuId = if (db != null) {
                            val resolvedEntity = if (source.lowercase() == "anilist") {
                                db.mediaMetaCacheDao().getByAniListId(extId)
                            } else {
                                db.mediaMetaCacheDao().getByMalId(extId)
                            }
                            resolvedEntity?.kitsuId
                        } else null

                        if (!kitsuId.isNullOrBlank()) {
                            android.util.Log.d("KitsugiDetailClient", "Fetching Kitsu detail via resolved kitsuId: $kitsuId")
                            finalDetail = KitsuClient.fetchAnimeDetail(kitsuId)
                            detailFromKitsuFallback = finalDetail != null
                        }
                        if (finalDetail == null && !title.isNullOrBlank()) {
                            android.util.Log.d("KitsugiDetailClient", "Fetching Kitsu detail via title search: $title")
                            finalDetail = KitsuClient.fetchAnimeDetailByTitle(title)
                            detailFromKitsuFallback = finalDetail != null
                        }
                        if (finalDetail == null && !title.isNullOrBlank()) {
                            runCatching {
                                val simklResults = SimklApiClient().search(title, type = "anime", limit = 1)
                                val matched = simklResults.firstOrNull()
                                if (matched != null && matched.malId > 0) {
                                    finalDetail = KitsugiSimklDetailClient.fetchSimklDetailDirect(matched.malId, mediaType)
                                }
                            }
                        }
                        if (finalDetail == null && !title.isNullOrBlank()) {
                            runCatching {
                                val jikanResults = JikanApiClient().search(title, MediaType.Anime)
                                val matched = jikanResults.firstOrNull()
                                if (matched != null && matched.malId > 0) {
                                    finalDetail = KitsugiMalDetailClient.fetchDetail(matched.malId, mediaType)
                                }
                            }
                        }
                    }
                }
            }

            // 3b. Yedek zincir Kitsu'dan geldiyse ve özet şüpheliyse temizle.
            // (AniList/MAL birincil veri vermediyse Kitsu yedeği devreye girer; o kayıt
            // da bozuk/alakasız özet taşıyabilir — örn. başlık aramasıyla "ROAR".)
            if (detailFromKitsuFallback) {
                val current = finalDetail
                if (current != null &&
                    KitsuSynopsisValidator.suspiciousReason(current.synopsis, current.year, mediaType) != null
                ) {
                    finalDetail = withTimeoutOrNull(KITSU_SANITIZE_TIMEOUT_MS) {
                        sanitizeKitsuSynopsis(current, mediaType, realMalId, title, keyId, source)
                    } ?: current.copy(synopsis = null)
                }
            }

            // 4. Stale Cache Fallback (If all network attempts returned null, check cache again even if expired)
            if (finalDetail == null && db != null && !cacheKey.isNullOrBlank()) {
                try {
                    val cached = db.persistentDetailCacheDao().getDetail(cacheKey)
                    if (cached != null) {
                        finalDetail = gson.fromJson(cached.detailJson, KitsugiMediaDetail::class.java)
                        if (finalDetail != null) {
                            android.util.Log.d("KitsugiDetailClient", "Serving stale detail from Room cache for $cacheKey")
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("KitsugiDetailClient", "Error reading stale cache: ${e.message}")
                }
            }

            // 5. Cache update on success
            val currentFinal = finalDetail
            if (currentFinal != null && !cacheKey.isNullOrBlank()) {
                saveToRoomCache(source, mediaType, keyId, currentFinal)
            }

            finalDetail
        }
    }


    /**
     * Kitsu kaydının özeti bozuksa düzeltir.
     *
     * Kitsu'de vandalize edilmiş/hatalı birleştirilmiş kayıtlar (örn. "ROAR" 2004 — özet
     * 1997 Fox TV dizisini anlatıyor) bambaşka bir yapımın özetini taşıyabilir. Bu kayıtlar
     * olduğu gibi gösterilmez:
     *  1. Özet sağlıklıysa → dokunulmaz.
     *  2. Şüpheliyse → MAL/Jikan'dan temiz özet aranır: önce kaydın gerçek MAL ID'si,
     *     sonra sıkı başlık eşleşmesi. Aday özet de şüpheli ise ya da başlık bu kayıtla
     *     akraba değilse (kimlik karışıklığı) reddedilir.
     *  3. Doğrulanmış temiz özet bulunursa → yer değiştirir; bulunamazsa özet boşaltılır
     *     (UI "Açıklama bulunamadı" gösterir — alakasız bir özeti göstermekten iyidir).
     */
    private suspend fun sanitizeKitsuSynopsis(
        detail: KitsugiMediaDetail?,
        mediaType: MediaType,
        realMalId: Int?,
        title: String?,
        cacheId: Int,
        cacheSource: String = "kitsu"
    ): KitsugiMediaDetail? {
        if (detail == null) return null
        val reason = KitsuSynopsisValidator.suspiciousReason(detail.synopsis, detail.year, mediaType)
        if (reason == null) return detail
        android.util.Log.w(
            "KitsugiDetailClient",
            "Kitsu bozuk özet tespit edildi ('${detail.title}', ${detail.year}): $reason — düzeltme deneniyor"
        )
        // Bozuk özetin eski (otomatik) çevirisi de önbellekte kalmış olabilir — temizle.
        DetailCache.removeTranslation("synopsis", cacheSource, cacheId)

        // Adaylar: (a) gerçek MAL ID ile Jikan, (b) sıkı başlık eşleşmesi ile Jikan.
        val candidates = mutableListOf<KitsugiMediaDetail>()
        val malId = realMalId?.takeIf { it in 1..99_999_999 }
        if (malId != null) {
            runCatching { KitsugiMalDetailClient.fetchDetail(malId, mediaType) }
                .getOrNull()?.let { candidates.add(it) }
        }
        if (candidates.isEmpty() && !title.isNullOrBlank()) {
            runCatching { fetchMalDetailByTitle(title, mediaType) }
                .getOrNull()?.let { candidates.add(it) }
        }

        val detailTitles = listOfNotNull(detail.title, detail.titleEnglish, detail.titleRomaji, detail.titleJapanese)
        for (candidate in candidates) {
            val candSynopsis = candidate.synopsis?.takeIf { it.isNotBlank() } ?: continue
            // Aday özet de şüpheli mi? (MAL'deki kayıt da aynı yabancı diziyi anlatıyor olabilir.)
            if (KitsuSynopsisValidator.suspiciousReason(candSynopsis, candidate.year, mediaType) != null) continue
            // Kimlik karışıklığı olmasın: aday başlık bu kayıtla akraba olmalı.
            val candTitles = listOfNotNull(candidate.title, candidate.titleEnglish, candidate.titleRomaji, candidate.titleJapanese)
            val related = detailTitles.isNotEmpty() && candTitles.isNotEmpty() &&
                com.kitsugi.animelist.data.auth.CrossSyncIdentityGuard.titlesLookRelated(detailTitles, candTitles, strict = true)
            if (!related) continue
            android.util.Log.i(
                "KitsugiDetailClient",
                "Kitsu özeti doğrulanmış kaynakla değiştirildi: '${detail.title}' ← '${candidate.title}' (MAL)"
            )
            return detail.copy(synopsis = candSynopsis)
        }

        android.util.Log.w(
            "KitsugiDetailClient",
            "Kitsu bozuk özeti doğrulanamadı; özet boşaltıldı ('${detail.title}')"
        )
        return detail.copy(synopsis = null)
    }

    /**
     * MAL (Jikan) başlık araması: yalnızca başlığı **doğrulanan** ilk sonucu kabul eder.
     *
     * Kitsu gibi birincil kaynağı veri döndürmeyen kayıtlar (Kitsu'da silinmiş ya da
     * gizlenmiş 18+ yapımlar) için güvenli yedektir; başlığı tutmayan hiçbir sonucu
     * kabul etmediği için kullanıcıya asla alakasız bir yapım göstermez.
     */
    private suspend fun fetchMalDetailByTitle(title: String?, mediaType: MediaType): KitsugiMediaDetail? {
        val query = title?.trim().orEmpty()
        if (query.isBlank()) return null
        val candidates = runCatching {
            JikanApiClient().searchMALOnly(query, mediaType, showAdultContent = true)
        }.getOrNull().orEmpty()
        if (candidates.isEmpty()) return null
        val match = candidates.firstOrNull { candidate ->
            com.kitsugi.animelist.data.auth.CrossSyncIdentityGuard.titlesLookRelated(
                listOfNotNull(candidate.title, candidate.titleEnglish, candidate.titleJapanese),
                listOf(query),
                strict = true
            )
        } ?: return null
        android.util.Log.d(
            "KitsugiDetailClient",
            "MAL başlık yedeği: '$query' → MAL ${match.malId} ('${match.title}')"
        )
        return KitsugiMalDetailClient.fetchDetail(match.malId, mediaType)
    }

    /**
     * TMDB başlık araması (yalnızca film/dizi) — medya türü eşleşen ilk sonuç kullanılır.
     * Kaynak verisi tükenmiş film/dizi kayıtlarında sayfanın boş kalmamasını sağlar.
     */
    private suspend fun fetchTmdbDetailByTitle(title: String?, mediaType: MediaType): KitsugiMediaDetail? {
        if (mediaType != MediaType.Movie && mediaType != MediaType.TvShow) return null
        val query = title?.trim().orEmpty()
        if (query.isBlank()) return null
        val candidate = runCatching { TmdbApiClient().search(query) }.getOrNull().orEmpty()
            .firstOrNull { it.type == mediaType && (it.tmdbId ?: 0) > 0 } ?: return null
        val tmdbId = candidate.tmdbId ?: return null
        return runCatching {
            TmdbApiClient().fetchMediaDetail(tmdbId, mediaType == MediaType.Movie)
        }.getOrNull()
    }

    /**
     * TMDB zenginleştirmesi (TR meta, görseller, puanlar) + next-airing çözümü.
     *
     * Birincil detaydan SONRA çağrılır; ApiResultDetailViewModel bu adımı zaman
     * tavanıyla (~20 sn) çalıştırır. Başarısızsa/timeout'ta [detail] aynen döner —
     * sayfa asla asıl veriye kavuşmamış durumuna düşmez.
     */
    suspend fun enrichDetail(
        source: String,
        externalId: Int?,
        mediaType: MediaType,
        detail: KitsugiMediaDetail,
        tmdbId: Int? = null,
        realMalId: Int? = null,
        title: String? = null
    ): KitsugiMediaDetail? = withContext(Dispatchers.IO) {
        val isBangumi = source.equals("bangumi", ignoreCase = true)
        // Bangumi: çapraz kimlik çözümü (AniList → MAL → ARM) + MAL/AniList/TMDB birleştirmesi.
        // Bu adım ViewModel tarafında zaman tavanlıdır; başarısız olursa yerel detay aynen kalır.
        val baseDetail = if (isBangumi && externalId != null && externalId > 0) {
            runCatching { KitsugiBangumiDetailClient.enrich(externalId, mediaType, detail) }.getOrDefault(detail)
        } else {
            detail
        }
        if (externalId == null || externalId <= 0 || mediaType == MediaType.Manga) {
            if (isBangumi && externalId != null && externalId > 0 && baseDetail !== detail) {
                saveToRoomCache(source, mediaType, externalId, baseDetail)
            }
            return@withContext baseDetail
        }

        val currentDetail = baseDetail

        // Bangumi stableId'si (500M+) gerçek MAL ID değildir; çağıranın verdiği değer süzülür.
        val safeRealMalId = if (isBangumi) KitsugiBangumiDetailClient.sanitizeMalId(realMalId) else realMalId

        // TMDB zenginleştirmesi için en iyi MAL ID'yi bul
        val effectiveRealMalId = safeRealMalId
            ?: currentDetail.realMalId
            ?: if (source.lowercase() == "anilist" && externalId < 100_000_000) externalId else null

        var resolvedTmdbId = tmdbId ?: currentDetail.tmdbId

        // Fallback scenario 2: Primary detail is not null, but tmdbId is missing -> Try direct TMDB search fallback by title!
        if ((resolvedTmdbId == null || resolvedTmdbId <= 0) && (mediaType == MediaType.Movie || mediaType == MediaType.TvShow)) {
            val searchTitle = title ?: currentDetail.title ?: currentDetail.titleEnglish
            if (!searchTitle.isNullOrBlank()) {
                android.util.Log.d("KitsugiDetailClient", "Primary resolution has no tmdbId. Triggering TMDB search for title: $searchTitle")
                val searchResults = TmdbApiClient().search(searchTitle)
                val matchedResult = searchResults.firstOrNull { it.type == mediaType }
                if (matchedResult != null && matchedResult.tmdbId != null && matchedResult.tmdbId > 0) {
                    resolvedTmdbId = matchedResult.tmdbId
                    android.util.Log.d("KitsugiDetailClient", "Resolved tmdbId = $resolvedTmdbId via search for title: $searchTitle")
                }
            }
        }

        val trMeta = getTurkishMetadataFromTmdb(source, externalId, mediaType, resolvedTmdbId, effectiveRealMalId)
        var mergedDetail = if (trMeta != null) {
            val updatedSynopsis = if (!trMeta.synopsis.isNullOrBlank()) trMeta.synopsis else currentDetail.synopsis
            // Kaynak Otoritesi Kuralı (Source Authority Preservation):
            // Birincil kaynağın başlıkları, türleri, stüdyoları ve kapak görseli her zaman önceliklidir!
            val updatedTitle = if (!currentDetail.title.isNullOrBlank()) currentDetail.title else trMeta.title
            val updatedTitleEnglish = if (!currentDetail.titleEnglish.isNullOrBlank()) currentDetail.titleEnglish else trMeta.titleEnglish
            val updatedGenres = if (currentDetail.genres.isNotEmpty()) currentDetail.genres else trMeta.genres
            val combinedPictures = (currentDetail.pictures.orEmpty() + trMeta.pictures.orEmpty()).distinct()
            val mergedStudios = if (currentDetail.studios.isNotEmpty()) currentDetail.studios else trMeta.studios
            val mergedProducers = if (currentDetail.producers.isNotEmpty()) currentDetail.producers else trMeta.producers
            val mergedRating = if (!currentDetail.rating.isNullOrBlank()) currentDetail.rating else trMeta.rating
            val updatedImageUrl = if (!currentDetail.imageUrl.isNullOrBlank()) currentDetail.imageUrl else trMeta.imageUrl
            currentDetail.copy(
                synopsis = updatedSynopsis,
                title = updatedTitle,
                titleEnglish = updatedTitleEnglish,
                genres = updatedGenres,
                tmdbId = resolvedTmdbId ?: currentDetail.tmdbId,
                imageUrl = updatedImageUrl,
                pictures = combinedPictures,
                studios = mergedStudios,
                producers = mergedProducers,
                rating = mergedRating,
                totalSeasons = if (source.lowercase() == "tmdb" || source.lowercase() == "simkl") {
                    trMeta.totalSeasons ?: currentDetail.totalSeasons
                } else {
                    currentDetail.totalSeasons ?: 1
                },
                meanScore = currentDetail.meanScore ?: trMeta.meanScore,
                averageScore = currentDetail.averageScore ?: trMeta.averageScore,
                popularity = currentDetail.popularity ?: trMeta.popularity,
                favorites = currentDetail.favorites ?: trMeta.favorites,
                rank = currentDetail.rank ?: trMeta.rank,
                popularityRank = currentDetail.popularityRank ?: trMeta.popularityRank,
                scoredBy = currentDetail.scoredBy ?: trMeta.scoredBy,
                members = currentDetail.members ?: trMeta.members,
                nextAiringEpisode = currentDetail.nextAiringEpisode ?: trMeta.nextAiringEpisode
            )
        } else {
            currentDetail
        }

        // Eğer nextAiringEpisode hâlâ null ise AniList üzerinden çöz ve çek
        if (mergedDetail.nextAiringEpisode == null) {
            val malIdForResolve = when (MalJikanMediaSupport.canonicalSource(source)) {
                "simkl" -> realMalId ?: mergedDetail.realMalId
                "jikan", "mal" -> externalId
                "anilist" -> if (externalId < 100_000_000) externalId else realMalId ?: mergedDetail.realMalId
                "kitsu" -> realMalId ?: mergedDetail.realMalId
                "bangumi" -> mergedDetail.realMalId
                else -> null
            }
            val resolvedAniListId = runCatching {
                KitsugiIdResolver.resolveIds(malId = malIdForResolve, aniListId = null, mediaType = mediaType).aniListId
            }.getOrNull()
            if (resolvedAniListId != null && resolvedAniListId > 0) {
                val nextAiring = KitsugiAniListDetailClient.fetchNextAiringEpisodeOnly(resolvedAniListId)
                if (nextAiring != null) {
                    mergedDetail = mergedDetail.copy(nextAiringEpisode = nextAiring)
                }
            }
        }

        val isKitsuSource = source.lowercase() == "kitsu"
        val keyId = if (isKitsuSource) {
            KitsuIdNamespace.stableIdOrNull(externalId) ?: externalId
        } else {
            externalId
        }
        saveToRoomCache(source, mediaType, keyId, mergedDetail)
        mergedDetail
    }

    /**
     * Eski davranışı koruyan tam akış (TV / entry detay sayfası): birincil detay +
     * TMDB zenginleştirmesi tek çağrıda. Kademeli akış için
     * [fetchPrimaryDetail] + [enrichDetail] ayrımı kullanılır.
     */
    suspend fun fetchDetail(
        source: String,
        externalId: Int?,
        mediaType: MediaType,
        tmdbId: Int? = null,
        realMalId: Int? = null,
        title: String? = null
    ): KitsugiMediaDetail? {
        val primary = fetchPrimaryDetail(source, externalId, mediaType, tmdbId, realMalId, title) ?: return null
        return runCatching {
            enrichDetail(source, externalId, mediaType, primary, tmdbId, realMalId, title)
        }.getOrNull() ?: primary
    }

    /**
     * Detay önbelleği anahtarı.
     *
     * TMDB/Simkl kayıtlarında anahtara [MediaTitleResolver.VERSION] eklenir: böylece
     * başlık çözümleme mantığı düzeltildiğinde eski (Japonça başlık taşıyan)
     * kayıtlar 24 saatlik tazelik penceresi boyunca servis edilmez.
     */
    private fun detailCacheKey(source: String, mediaTypeStr: String, keyId: Int): String {
        val base = "${source.lowercase()}_${mediaTypeStr}_$keyId"
        return when {
            // v2: Bangumi'nin English / romaji / özgün adları ayrı taşınır. Önceki
            // sürümde Çince ad ana başlığa yazıldığı için eski satırlar okunmaz.
            source.equals("bangumi", ignoreCase = true) -> "${base}_bgm2"
            // ks1: bozuk Kitsu özetleri temizlenmeye başlandı (KitsuSynopsisValidator).
            // Eski satırlar bozuk özet taşıyabileceği için yeni anahtar kullanılır.
            source.equals("kitsu", ignoreCase = true) -> "${base}_ks1"
            MediaTitleResolver.isLatinPreferredSource(source) -> "${base}_vl${MediaTitleResolver.VERSION}"
            else -> base
        }
    }

    /** Birincil detay + zenginleşmiş detay ortak Room önbellek yazarı. */
    private suspend fun saveToRoomCache(
        source: String,
        mediaType: MediaType,
        externalId: Int,
        detail: KitsugiMediaDetail
    ) {
        val context = com.kitsugi.animelist.KitsugiApplication.getInstance()?.applicationContext ?: return
        val db = com.kitsugi.animelist.data.local.KitsugiDatabase.getDatabase(context) ?: return
        try {
            val mediaTypeStr = mediaType.name.lowercase()
            val cacheKey = detailCacheKey(source, mediaTypeStr, externalId)
            val entity = com.kitsugi.animelist.data.local.PersistentDetailCacheEntity(
                cacheKey = cacheKey,
                detailJson = com.google.gson.Gson().toJson(detail),
                cachedAtMs = System.currentTimeMillis()
            )
            db.persistentDetailCacheDao().insertDetail(entity)
        } catch (e: Exception) {
            android.util.Log.e("KitsugiDetailClient", "Error writing detail cache: ${e.message}")
        }
    }
}
