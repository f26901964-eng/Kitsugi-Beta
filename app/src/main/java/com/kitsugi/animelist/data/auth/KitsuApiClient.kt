package com.kitsugi.animelist.data.auth

import android.util.Log
import com.kitsugi.animelist.core.network.KitsugiHttpClient
import com.kitsugi.animelist.data.remote.KitsuClient
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.model.WatchStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * Kitsu.io JSON-API İstemcisi.
 * Kitsu OAuth token alma, kullanıcı profili çekme, kütüphane listesini okuma
 * ve kütüphane kayıtlarını ekleme/güncelleme/silme işlemlerini yönetir.
 */
object KitsuApiClient {
    private const val TAG = "KitsuApiClient"

    // Açık kaynak standart Kitsu OAuth kimlik bilgileri (Tachiyomi / Aniyomi referansı)
    const val DEFAULT_CLIENT_ID = "dd031b32d2f56c990b1425efe6c42ad847e7fe3ab46bf1299f05ecd856bdb7dd"
    const val DEFAULT_CLIENT_SECRET = "54d7307928f63414defd96399fc31ba847961ceaecef3a5fd93144e960c0e151"

    private const val OAUTH_URL = "https://kitsu.app/api/oauth/token"
    private const val BASE_URL = "https://kitsu.app/api/edge"
    private val JSON_MEDIA_TYPE = "application/vnd.api+json".toMediaTypeOrNull()

    data class KitsuTokenResponse(
        val accessToken: String,
        val refreshToken: String,
        val expiresIn: Long
    )

    data class KitsuUser(
        val id: String,
        val name: String,
        val avatarUrl: String?
    )

    data class KitsuFullProfile(
        val id: String,
        val name: String,
        val avatarUrl: String?,
        val bannerUrl: String?,
        val about: String?,
        val location: String?,
        val gender: String?,
        val birthday: String?,
        val joinedAt: String?,
        val waifuOrHusbando: String?,
        val followersCount: Int = 0,
        val followingCount: Int = 0,
        val commentsCount: Int = 0,
        val reviewsCount: Int = 0
    )

    data class KitsuLibraryEntry(
        val id: String,
        val status: String,
        val progress: Int,
        val ratingTwenty: Int?,
        val animeId: Int?,
        val mangaId: Int?,
        val updatedAt: Long,
        val title: String = "",
        val titleEnglish: String? = null,
        val titleJapanese: String? = null,
        val imageUrl: String? = null,
        val total: Int? = null,
        /** Kitsu mappings'ten çözümlenen gerçek MAL ID (null olabilir) */
        val realMalId: Int? = null,
        /** Sonraki bölümün yayın zamanı (ISO-8601, Kitsu `nextRelease` alanı) */
        val nextRelease: String? = null,
        /** Kitsu `startDate` alanından yayın yılı (çapraz eşitlemede kimlik doğrulaması için). */
        val startYear: Int? = null,
        /** +18 içerik mi? (ageRating=R18 / nsfw / hentai) — bulanıklık için kullanılır */
        val isAdult: Boolean = false,
        /** Kullanıcı notu (`notes`). */
        val notes: String? = null,
        /** Gizli kayıt mı (`private`). */
        val isPrivate: Boolean = false,
        /** Başlangıç tarihi, yyyy-MM-dd (`startedAt`). */
        val startedAt: String? = null,
        /** Bitiş tarihi, yyyy-MM-dd (`finishedAt`). */
        val finishedAt: String? = null
    )

    /**
     * Kitsu kütüphane kaydında ana durum/ilerleme/puan dışında yazılabilen ek alanlar.
     * Null olan alanlar istekte hiç gönderilmez (uzak değer değişmez).
     */
    data class KitsuEntryExtras(
        val startedAt: String? = null,
        val finishedAt: String? = null,
        val notes: String? = null,
        val isPrivate: Boolean? = null
    )

    /** `yyyy-MM-dd` → Kitsu'nun beklediği ISO-8601 datetime (`yyyy-MM-ddT00:00:00.000Z`). */
    fun toKitsuDateTime(date: String?): String? {
        val d = date?.trim().orEmpty()
        return if (KITSU_DATE_REGEX.matches(d)) "${d}T00:00:00.000Z" else null
    }

    /** Kitsu'dan gelen ISO datetime → `yyyy-MM-dd` (yalnızca tarih kısmı). */
    private fun fromKitsuDateTime(value: String?): String? {
        val head = value?.trim().orEmpty().take(10)
        return if (KITSU_DATE_REGEX.matches(head)) head else null
    }

    private val KITSU_DATE_REGEX = Regex("""\d{4}-\d{2}-\d{2}""")

    /**
     * Kullanıcı adı / e-posta ve şifre ile Kitsu OAuth Bearer token alır.
     */
    suspend fun loginWithPassword(
        username: String,
        password: String,
        clientId: String = DEFAULT_CLIENT_ID,
        clientSecret: String = DEFAULT_CLIENT_SECRET
    ): KitsuTokenResponse = withContext(Dispatchers.IO) {
        val formBody = okhttp3.FormBody.Builder()
            .add("grant_type", "password")
            .add("client_id", clientId)
            .add("client_secret", clientSecret)
            .add("username", username.trim())
            .add("password", password)
            .build()

        val request = Request.Builder()
            .url(OAUTH_URL)
            .addHeader("Accept", "application/json")
            .addHeader("User-Agent", "KitsugiApp/2.4")
            .post(formBody)
            .build()

        KitsugiHttpClient.client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val errorType = runCatching { JSONObject(body).optString("error", "") }.getOrDefault("")
                val errorDesc = runCatching { JSONObject(body).optString("error_description", "") }.getOrDefault("")
                val localizedMsg = when {
                    errorType == "invalid_grant" || errorDesc.contains("authorization grant is invalid", ignoreCase = true) ->
                        "E-posta veya şifre hatalı. Lütfen bilgilerinizi kontrol edip tekrar deneyin."
                    errorType == "invalid_client" ->
                        "Kitsu sunucu bağlantı hatası. Lütfen daha sonra tekrar deneyin."
                    errorDesc.isNotBlank() -> errorDesc
                    else -> "Kitsu girişi başarısız oldu (${response.code})"
                }
                throw Exception(localizedMsg)
            }
            val json = JSONObject(body)
            KitsuTokenResponse(
                accessToken = json.getString("access_token"),
                refreshToken = json.optString("refresh_token", ""),
                expiresIn = json.optLong("expires_in", 2592000L)
            )
        }
    }

    /**
     * Mevcut oturum açmış kullanıcının kimlik ve profil bilgilerini alır.
     */
    suspend fun getCurrentUser(token: String): KitsuUser = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$BASE_URL/users?filter[self]=true")
            .addHeader("Accept", "application/vnd.api+json")
            .addHeader("Authorization", "Bearer $token")
            .addHeader("User-Agent", "KitsugiApp/2.4")
            .get()
            .build()

        KitsugiHttpClient.client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw Exception("Kullanıcı bilgisi alınamadı (${response.code})")
            val data = JSONObject(body).getJSONArray("data")
            if (data.length() == 0) throw Exception("Kullanıcı bulunamadı")
            val userObj = data.getJSONObject(0)
            val userId = userObj.getString("id")
            val attrs = userObj.optJSONObject("attributes")
            val name = attrs?.optString("name", "Kitsu User") ?: "Kitsu User"
            val avatarObj = attrs?.optJSONObject("avatar")
            val avatarUrl = avatarObj?.optString("medium") ?: avatarObj?.optString("original")
            KitsuUser(id = userId, name = name, avatarUrl = avatarUrl)
        }
    }

    /**
     * Oturum açmış kullanıcının ayrıntılı profil bilgilerini (biyografi, banner, takipçi sayıları vb.) alır.
     */
    suspend fun fetchFullUserProfile(token: String): KitsuFullProfile = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$BASE_URL/users?filter[self]=true")
            .addHeader("Accept", "application/vnd.api+json")
            .addHeader("Authorization", "Bearer $token")
            .addHeader("User-Agent", "KitsugiApp/2.4")
            .get()
            .build()

        KitsugiHttpClient.client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw Exception("Kitsu profil bilgisi alınamadı (${response.code})")
            val data = JSONObject(body).getJSONArray("data")
            if (data.length() == 0) throw Exception("Kullanıcı bulunamadı")
            val userObj = data.getJSONObject(0)
            val userId = userObj.getString("id")
            val attrs = userObj.optJSONObject("attributes")
            val name = attrs?.optString("name", "Kitsu Kullanıcısı") ?: "Kitsu Kullanıcısı"
            val avatarObj = attrs?.optJSONObject("avatar")
            val avatarUrl = avatarObj?.optString("original")
                ?: avatarObj?.optString("large")
                ?: avatarObj?.optString("medium")
            val coverObj = attrs?.optJSONObject("coverImage")
            val bannerUrl = coverObj?.optString("original")
                ?: coverObj?.optString("large")
            val about = attrs?.optString("about")?.takeIf { it.isNotBlank() }
            val location = attrs?.optString("location")?.takeIf { it.isNotBlank() }
            val gender = attrs?.optString("gender")?.takeIf { it.isNotBlank() }
            val birthday = attrs?.optString("birthday")?.takeIf { it.isNotBlank() }
            val createdAt = attrs?.optString("createdAt")?.takeIf { it.isNotBlank() }
            val waifuOrHusbando = attrs?.optString("waifuOrHusbando")?.takeIf { it.isNotBlank() }
            val followersCount = attrs?.optInt("followersCount", 0) ?: 0
            val followingCount = attrs?.optInt("followingCount", 0) ?: 0
            val commentsCount = attrs?.optInt("commentsCount", 0) ?: 0
            val reviewsCount = attrs?.optInt("reviewsCount", 0) ?: 0

            KitsuFullProfile(
                id = userId,
                name = name,
                avatarUrl = avatarUrl,
                bannerUrl = bannerUrl,
                about = about,
                location = location,
                gender = gender,
                birthday = birthday,
                joinedAt = createdAt,
                waifuOrHusbando = waifuOrHusbando,
                followersCount = followersCount,
                followingCount = followingCount,
                commentsCount = commentsCount,
                reviewsCount = reviewsCount
            )
        }
    }

    /**
     * Kitsu kullanıcısının tüm kütüphanesini sayfalayarak çeker.
     */
    suspend fun fetchAllLibraryEntries(token: String, userId: String): List<KitsuLibraryEntry> = withContext(Dispatchers.IO) {
        val result = mutableListOf<KitsuLibraryEntry>()
        var offset = 0
        val limit = 500

        while (true) {
            val url = "$BASE_URL/library-entries?filter[userId]=$userId&page[limit]=$limit&page[offset]=$offset&include=anime,manga,anime.mappings,manga.mappings"
            val request = Request.Builder()
                .url(url)
                .addHeader("Accept", "application/vnd.api+json")
                .addHeader("Authorization", "Bearer $token")
                .addHeader("User-Agent", "KitsugiApp/2.4")
                .get()
                .build()

            val fetchedCount = KitsugiHttpClient.client.newCall(request).execute().use { response ->
                check(response.isSuccessful) { "Kitsu liste okuma hatası: HTTP ${response.code} (offset=$offset)" }
                val body = response.body?.string().orEmpty()
                val json = JSONObject(body)
                val data = json.getJSONArray("data")

                val included = json.optJSONArray("included") ?: JSONArray()
                data class KitsuMediaInfo(
                    val title: String,
                    val titleEn: String?,
                    val titleJp: String?,
                    val imageUrl: String?,
                    val mappingIds: List<String> = emptyList(),
                    /** Kitsu anime `nextRelease` (ISO-8601) — sonraki bölümün yayın zamanı. */
                    val nextRelease: String? = null,
                    val startYear: Int? = null,
                    /** +18 içerik mi? (ageRating=R18 / nsfw / hentai) */
                    val isAdult: Boolean = false
                )
                val mediaInfoMap = mutableMapOf<String, KitsuMediaInfo>()
                val totalMap = mutableMapOf<String, Int?>()
                // mapping id -> externalId ("myanimelist/anime" veya "myanimelist/manga" namespace için)
                val mappingMalMap = mutableMapOf<String, Int>() // mappingId -> realMalId

                // İlk geçişte tüm mapping objelerini çöz
                for (j in 0 until included.length()) {
                    val inc = included.getJSONObject(j)
                    val incType = inc.optString("type")
                    if (incType == "mappings") {
                        val mappingId = inc.optString("id")
                        val mappingAttrs = inc.optJSONObject("attributes") ?: continue
                        val externalSite = mappingAttrs.optString("externalSite", "")
                        val externalId = mappingAttrs.optString("externalId", "")
                        if ((externalSite == "myanimelist/anime" || externalSite == "myanimelist/manga") && externalId.isNotBlank()) {
                            externalId.toIntOrNull()?.let { malId ->
                                if (malId in 1..99_999_999) mappingMalMap[mappingId] = malId
                            }
                        }
                    }
                }

                // İkinci geçişte anime/manga objelerini işle
                for (j in 0 until included.length()) {
                    val inc = included.getJSONObject(j)
                    val incType = inc.optString("type")
                    if (incType != "anime" && incType != "manga") continue
                    val incId = inc.optString("id")
                    val incAttrs = inc.optJSONObject("attributes") ?: JSONObject()
                    val canonical = incAttrs.optString("canonicalTitle", "")
                    val titlesObj = incAttrs.optJSONObject("titles")
                    val titleEn = titlesObj?.optString("en")?.takeIf { it.isNotBlank() }
                    val titleRomaji = titlesObj?.optString("en_jp")?.takeIf { it.isNotBlank() }
                    val titleJp = titlesObj?.optString("ja_jp")?.takeIf { it.isNotBlank() }

                    val mainTitle = titleRomaji ?: canonical
                    val effectiveEn = titleEn ?: if (canonical != mainTitle) canonical else null

                    val poster = incAttrs.optJSONObject("posterImage")
                    val img = poster?.optString("medium") ?: poster?.optString("original")
                    // Kitsu, sonraki bölüm yayın zamanını anime kaynağında `nextRelease` alanıyla verir.
                    val nextRelease = incAttrs.optString("nextRelease").takeIf { it.isNotBlank() && it != "null" }
                    val total = if (incType == "anime") incAttrs.optInt("episodeCount", 0).takeIf { it > 0 }
                    else incAttrs.optInt("chapterCount", 0).takeIf { it > 0 }
                    val startYear = incAttrs.optString("startDate", "").takeIf { it.isNotBlank() && it != "null" }
                        ?.take(4)?.toIntOrNull()?.takeIf { it in 1900..2100 }
                    // mappings ilişkisinden bağlantılı mapping ID'lerini topla
                    val mappingRels = inc.optJSONObject("relationships")
                        ?.optJSONObject("mappings")
                        ?.optJSONArray("data")
                    val mappingIds = mutableListOf<String>()
                    if (mappingRels != null) {
                        for (m in 0 until mappingRels.length()) {
                            mappingRels.optJSONObject(m)?.optString("id")?.let { mappingIds.add(it) }
                        }
                    }

                    val key = "${incType}_$incId"
                    mediaInfoMap[key] = KitsuMediaInfo(
                        title = mainTitle,
                        titleEn = effectiveEn,
                        titleJp = titleJp,
                        imageUrl = img,
                        mappingIds = mappingIds,
                        nextRelease = nextRelease,
                        startYear = startYear,
                        isAdult = com.kitsugi.animelist.data.remote.KitsuAdultFlags.isAdult(incAttrs)
                    )
                    totalMap[key] = total
                }

                for (i in 0 until data.length()) {
                    val item = data.getJSONObject(i)
                    val entryId = item.getString("id")
                    val attrs = item.optJSONObject("attributes") ?: JSONObject()
                    val status = attrs.optString("status", "planned")
                    val progress = attrs.optInt("progress", 0)
                    val ratingTwenty = if (attrs.has("ratingTwenty") && !attrs.isNull("ratingTwenty")) attrs.optInt("ratingTwenty") else null
                    val updatedAtStr = attrs.optString("updatedAt", "")
                    val updatedAt = runCatching {
                        java.time.Instant.parse(updatedAtStr).epochSecond
                    }.getOrDefault(0L)
                    val notes = if (attrs.isNull("notes")) null else attrs.optString("notes").takeIf { it.isNotBlank() }
                    val isPrivate = attrs.optBoolean("private", false)
                    val startedAt = fromKitsuDateTime(if (attrs.isNull("startedAt")) null else attrs.optString("startedAt"))
                    val finishedAt = fromKitsuDateTime(if (attrs.isNull("finishedAt")) null else attrs.optString("finishedAt"))

                    val rels = item.optJSONObject("relationships")
                    val animeData = rels?.optJSONObject("anime")?.optJSONObject("data")
                    val mangaData = rels?.optJSONObject("manga")?.optJSONObject("data")

                    val animeId = animeData?.optString("id")?.toIntOrNull()
                    val mangaId = mangaData?.optString("id")?.toIntOrNull()

                    val mediaKey = if (animeId != null) "anime_$animeId" else if (mangaId != null) "manga_$mangaId" else ""
                    val info = mediaInfoMap[mediaKey]

                    // Bu media'nın mapping'lerinden MAL ID çöz
                    val realMalId = info?.mappingIds?.firstNotNullOfOrNull { mappingMalMap[it] }

                    result.add(
                        KitsuLibraryEntry(
                            id = entryId,
                            status = status,
                            progress = progress,
                            ratingTwenty = ratingTwenty,
                            animeId = animeId,
                            mangaId = mangaId,
                            updatedAt = updatedAt,
                            title = info?.title ?: if (animeId != null) "Kitsu Anime #$animeId" else "Kitsu Manga #$mangaId",
                            titleEnglish = info?.titleEn,
                            titleJapanese = info?.titleJp,
                            imageUrl = info?.imageUrl,
                            total = totalMap[mediaKey],
                            realMalId = realMalId,
                            nextRelease = info?.nextRelease,
                            startYear = info?.startYear,
                            isAdult = info?.isAdult == true,
                            notes = notes,
                            isPrivate = isPrivate,
                            startedAt = startedAt,
                            finishedAt = finishedAt
                        )
                    )
                }
                data.length()
            }

            if (fetchedCount < limit) break
            offset += fetchedCount
        }

        result
    }

    /** Detailed outcome of a Kitsu write so callers can report the real HTTP reason. */
    data class KitsuWriteResult(
        val success: Boolean,
        val entryId: String? = null,
        val errorMessage: String? = null,
        val rateLimited: Boolean = false,
        val httpCode: Int? = null
    )

    private fun errorSnippet(body: String): String {
        if (body.isBlank()) return ""
        return runCatching {
            val errors = JSONObject(body).optJSONArray("errors")
            if (errors != null && errors.length() > 0) {
                val first = errors.getJSONObject(0)
                listOf(first.optString("title", ""), first.optString("detail", ""))
                    .filter { it.isNotBlank() }
                    .joinToString(": ")
            } else body
        }.getOrDefault(body).replace('\n', ' ').take(160)
    }

    /**
     * Kullanıcının kütüphanesinde belirli bir medya için mevcut kaydı arar.
     * 429 yanıtlarında kısa bekleme ile en fazla üç kez yeniden dener; başka HTTP
     * hatalarında kodu ve sunucu mesajını içeren bir [IllegalStateException] fırlatır.
     */
    suspend fun findLibraryEntryId(
        token: String,
        userId: String,
        kitsuMediaId: Int,
        isAnime: Boolean
    ): String? = withContext(Dispatchers.IO) {
        val relKey = if (isAnime) "animeId" else "mangaId"
        val url = "$BASE_URL/library-entries?filter[userId]=$userId&filter[$relKey]=$kitsuMediaId&page[limit]=1"
        val request = Request.Builder()
            .url(url)
            .addHeader("Accept", "application/vnd.api+json")
            .addHeader("Authorization", "Bearer $token")
            .addHeader("User-Agent", "KitsugiApp/2.4")
            .get()
            .build()
        var attempt = 0
        while (attempt < 3) {
            attempt++
            PlatformRateLimiter.acquire("kitsu")
            val outcome: Result<String?>? = KitsugiHttpClient.client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                when {
                    response.code == 429 -> {
                        PlatformRateLimiter.notifyRateLimited("kitsu")
                        Log.w(TAG, "Kitsu findLibraryEntryId rate limited (429), retrying (deneme $attempt/3)...")
                        null
                    }
                    !response.isSuccessful -> {
                        val snippet = errorSnippet(body)
                        error("Kitsu kayıt sorgusu: HTTP ${response.code}${if (snippet.isNotBlank()) " · $snippet" else ""}")
                    }
                    else -> {
                        val data = JSONObject(body).optJSONArray("data")
                        Result.success(
                            if (data != null && data.length() > 0) {
                                data.getJSONObject(0).optString("id").takeIf { it.isNotBlank() }
                            } else null
                        )
                    }
                }
            }
            if (outcome != null) return@withContext outcome.getOrNull()
            if (attempt < 3) kotlinx.coroutines.delay(2000L * attempt)
        }
        error("Kitsu kayıt sorgusu: HTTP 429 (hız sınırı, 3 deneme sonrası vazgeçildi)")
    }

    /** Yalnızca null olmayan ek alanları JSON:API attributes nesnesine ekler. */
    private fun JSONObject.applyKitsuExtras(extras: KitsuEntryExtras?) {
        if (extras == null) return
        extras.startedAt?.let { put("startedAt", it) }
        extras.finishedAt?.let { put("finishedAt", it) }
        extras.notes?.let { put("notes", it) }
        extras.isPrivate?.let { put("private", it) }
    }

    /**
     * Kitsu'da kütüphane kaydı oluşturur (POST). Başarıda yeni kaydın ID'sini döner.
     */
    suspend fun createLibraryEntry(
        token: String,
        userId: String,
        kitsuMediaId: Int,
        isAnime: Boolean,
        status: String,
        progress: Int,
        ratingTwenty: Int?
    ): String? = createLibraryEntryDetailed(token, userId, kitsuMediaId, isAnime, status, progress, ratingTwenty).entryId

    /**
     * Kitsu'da kütüphane kaydı oluşturur ve HTTP kodu/sunucu mesajı ile ayrıntılı sonuç döner.
     */
    suspend fun createLibraryEntryDetailed(
        token: String,
        userId: String,
        kitsuMediaId: Int,
        isAnime: Boolean,
        status: String,
        progress: Int,
        ratingTwenty: Int?,
        extras: KitsuEntryExtras? = null
    ): KitsuWriteResult = withContext(Dispatchers.IO) {
        val relType = if (isAnime) "anime" else "manga"
        val payload = JSONObject().apply {
            put("data", JSONObject().apply {
                put("type", "libraryEntries")
                put("attributes", JSONObject().apply {
                    put("status", status)
                    put("progress", progress)
                    if (ratingTwenty != null && ratingTwenty > 0) {
                        put("ratingTwenty", ratingTwenty)
                    }
                    applyKitsuExtras(extras)
                })
                put("relationships", JSONObject().apply {
                    put("user", JSONObject().apply {
                        put("data", JSONObject().apply {
                            put("type", "users")
                            put("id", userId)
                        })
                    })
                    put(relType, JSONObject().apply {
                        put("data", JSONObject().apply {
                            put("type", relType)
                            put("id", kitsuMediaId.toString())
                        })
                    })
                })
            })
        }

        var attempt = 0
        var lastResult = KitsuWriteResult(success = false, errorMessage = "Kitsu isteği gönderilemedi")
        while (attempt < 3) {
            attempt++
            PlatformRateLimiter.acquire("kitsu")
            val request = Request.Builder()
                .url("$BASE_URL/library-entries")
                .addHeader("Accept", "application/vnd.api+json")
                .addHeader("Content-Type", "application/vnd.api+json")
                .addHeader("Authorization", "Bearer $token")
                .addHeader("User-Agent", "KitsugiApp/2.4")
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            lastResult = KitsugiHttpClient.client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                when {
                    response.code == 429 -> {
                        PlatformRateLimiter.notifyRateLimited("kitsu")
                        Log.w(TAG, "Kitsu createLibraryEntry rate limited (429), retrying (deneme $attempt/3)...")
                        KitsuWriteResult(success = false, errorMessage = "HTTP 429 (hız sınırı)", rateLimited = true, httpCode = 429)
                    }
                    response.isSuccessful -> {
                        val id = JSONObject(body).optJSONObject("data")?.optString("id")?.takeIf { it.isNotBlank() }
                        if (id != null) KitsuWriteResult(success = true, entryId = id, httpCode = response.code)
                        else KitsuWriteResult(success = false, errorMessage = "HTTP ${response.code}: yanıtta kayıt kimliği yok", httpCode = response.code)
                    }
                    else -> {
                        Log.e(TAG, "createLibraryEntry failed: ${response.code} $body")
                        val snippet = errorSnippet(body)
                        KitsuWriteResult(
                            success = false,
                            errorMessage = "HTTP ${response.code}${if (snippet.isNotBlank()) ": $snippet" else ""}",
                            httpCode = response.code
                        )
                    }
                }
            }
            if (!lastResult.rateLimited) return@withContext lastResult
            kotlinx.coroutines.delay(2000L * attempt)
        }
        lastResult
    }

    /**
     * Kitsu medyasının uzunluğunu (anime: episodeCount, manga: chapterCount) döner.
     * Bilinmiyorsa (ör. devam eden yayın) veya istek başarısızsa null.
     */
    suspend fun fetchMediaLength(kitsuMediaId: Int, isAnime: Boolean): Int? = withContext(Dispatchers.IO) {
        val endpoint = if (isAnime) "anime" else "manga"
        val field = if (isAnime) "episodeCount" else "chapterCount"
        PlatformRateLimiter.acquire("kitsu")
        val request = Request.Builder()
            .url("$BASE_URL/$endpoint/$kitsuMediaId?fields[$endpoint]=$field")
            .addHeader("Accept", "application/vnd.api+json")
            .addHeader("User-Agent", "KitsugiApp/2.4")
            .build()
        try {
            KitsugiHttpClient.client.newCall(request).execute().use { response ->
                if (response.code == 429) PlatformRateLimiter.notifyRateLimited("kitsu")
                if (!response.isSuccessful) return@use null
                val body = response.body?.string()
                if (body.isNullOrBlank()) return@use null
                JSONObject(body).optJSONObject("data")?.optJSONObject("attributes")
                    ?.optInt(field, 0)?.takeIf { it > 0 }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Kitsu media length lookup failed for $endpoint/$kitsuMediaId: ${e.message}")
            null
        }
    }

    /** Kitsu, ilerlemenin medya uzunluğunu aşmasını HTTP 422 "cannot exceed length of media" ile reddeder. */
    fun isMediaLengthError(message: String?): Boolean =
        message?.contains("exceed length", ignoreCase = true) == true

    /**
     * Mevcut bir Kitsu kütüphane kaydını günceller (PATCH).
     */
    suspend fun updateLibraryEntry(
        token: String,
        entryId: String,
        status: String,
        progress: Int,
        ratingTwenty: Int?
    ): Boolean = updateLibraryEntryDetailed(token, entryId, status, progress, ratingTwenty).success

    /**
     * Mevcut bir Kitsu kütüphane kaydını günceller ve HTTP kodu/sunucu mesajı ile ayrıntılı sonuç döner.
     */
    suspend fun updateLibraryEntryDetailed(
        token: String,
        entryId: String,
        status: String,
        progress: Int,
        ratingTwenty: Int?,
        extras: KitsuEntryExtras? = null
    ): KitsuWriteResult = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("data", JSONObject().apply {
                put("id", entryId)
                put("type", "libraryEntries")
                put("attributes", JSONObject().apply {
                    put("status", status)
                    put("progress", progress)
                    if (ratingTwenty != null && ratingTwenty > 0) {
                        put("ratingTwenty", ratingTwenty)
                    } else {
                        put("ratingTwenty", JSONObject.NULL)
                    }
                    applyKitsuExtras(extras)
                })
            })
        }

        var attempt = 0
        var lastResult = KitsuWriteResult(success = false, errorMessage = "Kitsu isteği gönderilemedi")
        while (attempt < 3) {
            attempt++
            PlatformRateLimiter.acquire("kitsu")
            val request = Request.Builder()
                .url("$BASE_URL/library-entries/$entryId")
                .addHeader("Accept", "application/vnd.api+json")
                .addHeader("Content-Type", "application/vnd.api+json")
                .addHeader("Authorization", "Bearer $token")
                .addHeader("User-Agent", "KitsugiApp/2.4")
                .patch(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            lastResult = KitsugiHttpClient.client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                when {
                    response.code == 429 -> {
                        PlatformRateLimiter.notifyRateLimited("kitsu")
                        Log.w(TAG, "Kitsu updateLibraryEntry rate limited (429), retrying (deneme $attempt/3)...")
                        KitsuWriteResult(success = false, errorMessage = "HTTP 429 (hız sınırı)", rateLimited = true, httpCode = 429)
                    }
                    response.isSuccessful -> KitsuWriteResult(success = true, entryId = entryId, httpCode = response.code)
                    else -> {
                        Log.e(TAG, "updateLibraryEntry failed: ${response.code} $body")
                        val snippet = errorSnippet(body)
                        KitsuWriteResult(
                            success = false,
                            errorMessage = "HTTP ${response.code}${if (snippet.isNotBlank()) ": $snippet" else ""}",
                            httpCode = response.code
                        )
                    }
                }
            }
            if (!lastResult.rateLimited) return@withContext lastResult
            kotlinx.coroutines.delay(2000L * attempt)
        }
        lastResult
    }

    /**
     * Kitsu kütüphanesinden kaydı siler (DELETE).
     */
    suspend fun deleteLibraryEntry(token: String, entryId: String): Boolean = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$BASE_URL/library-entries/$entryId")
            .addHeader("Accept", "application/vnd.api+json")
            .addHeader("Authorization", "Bearer $token")
            .addHeader("User-Agent", "KitsugiApp/2.4")
            .delete()
            .build()

        KitsugiHttpClient.client.newCall(request).execute().use { response ->
            response.isSuccessful || response.code == 404
        }
    }

    /**
     * Kitsu'nun resmi `mappings` tablosu üzerinden harici bir kimliği (MAL/AniList) Kitsu medya ID'sine çevirir.
     * Başlık aramasından farklı olarak bu eşleme deterministiktir; bulunamazsa null döner.
     *
     * @param externalSite `myanimelist/anime`, `myanimelist/manga`, `anilist/anime`, `anilist/manga`
     */
    suspend fun lookupKitsuIdByExternalMapping(externalSite: String, externalId: Int): Int? = withContext(Dispatchers.IO) {
        if (externalId <= 0 || externalSite.isBlank()) return@withContext null
        val encodedSite = java.net.URLEncoder.encode(externalSite, "UTF-8")
        val url = "$BASE_URL/mappings?filter[externalSite]=$encodedSite&filter[externalId]=$externalId&include=item&fields[mappings]=externalSite,externalId,item&page[limit]=1"
        val request = Request.Builder()
            .url(url)
            .addHeader("Accept", "application/vnd.api+json")
            .addHeader("User-Agent", "KitsugiApp/2.4")
            .get()
            .build()
        var attempt = 0
        while (attempt < 3) {
            attempt++
            PlatformRateLimiter.acquire("kitsu")
            val outcome = runCatching {
                KitsugiHttpClient.client.newCall(request).execute().use { response ->
                    if (response.code == 429) {
                        PlatformRateLimiter.notifyRateLimited("kitsu")
                        return@use null
                    }
                    if (!response.isSuccessful) return@use Result.success<Int?>(null)
                    val body = response.body?.string().orEmpty()
                    val root = JSONObject(body)
                    val data = root.optJSONArray("data") ?: return@use Result.success<Int?>(null)
                    if (data.length() == 0) return@use Result.success<Int?>(null)
                    val expectedType = externalSite.substringAfter('/', "").lowercase()
                    val mapping = data.getJSONObject(0)
                    val rel = mapping.optJSONObject("relationships")?.optJSONObject("item")?.optJSONObject("data")
                    val relId = rel?.optString("id")?.toIntOrNull()
                    val relType = rel?.optString("type")?.lowercase()
                    if (relId != null && relId > 0 && (expectedType.isBlank() || relType == null || relType == expectedType)) {
                        return@use Result.success<Int?>(relId)
                    }
                    // Fallback: `included` listesinden tipi uyuşan ilk öğe
                    val included = root.optJSONArray("included")
                    if (included != null) {
                        for (i in 0 until included.length()) {
                            val item = included.getJSONObject(i)
                            val type = item.optString("type").lowercase()
                            if (expectedType.isBlank() || type == expectedType) {
                                val id = item.optString("id").toIntOrNull()
                                if (id != null && id > 0) return@use Result.success<Int?>(id)
                            }
                        }
                    }
                    Result.success<Int?>(null)
                }
            }.getOrElse { e ->
                Log.w(TAG, "Kitsu mappings lookup failed for $externalSite/$externalId: ${e.message}")
                Result.success<Int?>(null)
            }
            if (outcome != null) return@withContext outcome.getOrNull()
            kotlinx.coroutines.delay(1500L * attempt)
        }
        null
    }

    /**
     * Başlık veya yıl ile Kitsu medya numeric ID'sini doğrulamalı olarak arar.
     *
     * Birden fazla aday aynı en yüksek puanı alıyorsa (ör. aynı isimli sezonlar/yeniden çevrimler)
     * ve yıl bilgisi ayrıştırmıyorsa null döner; yanlış kayda yazmaktansa kaydı atlamak tercih edilir.
     */
    suspend fun lookupKitsuId(title: String, isAnime: Boolean, expectedYear: Int? = null): Int? {
        // +18 (R18/nsfw) Kitsu kayıtları anonim aramalarda gizlenir. Aranan kayıt
        // kullanıcının KENDİ listesinden geldiği için oturum varsa jetonla arıyoruz;
        // sonuç çıkmazsa anonim aramaya düşüyoruz.
        val authToken = runCatching { KitsuClient.authTokenOrNull() }.getOrNull()
        if (authToken != null) {
            lookupKitsuIdMatching(title, isAnime, expectedYear, authToken)?.let { return it }
        }
        return lookupKitsuIdMatching(title, isAnime, expectedYear, null)
    }

    private suspend fun lookupKitsuIdMatching(
        title: String,
        isAnime: Boolean,
        expectedYear: Int?,
        authToken: String?
    ): Int? = withContext(Dispatchers.IO) {
        if (title.isBlank()) return@withContext null
        PlatformRateLimiter.acquire("kitsu")
        val endpoint = if (isAnime) "anime" else "manga"
        val encoded = java.net.URLEncoder.encode(title.trim(), "UTF-8")
        val url = "$BASE_URL/$endpoint?filter[text]=$encoded&page[limit]=5&fields[$endpoint]=canonicalTitle,titles,abbreviatedTitles,startDate"

        val builder = Request.Builder()
            .url(url)
            .addHeader("Accept", "application/vnd.api+json")
            .addHeader("User-Agent", "KitsugiApp/2.4")
        if (authToken != null) builder.addHeader("Authorization", "Bearer $authToken")
        val request = builder.get().build()

        runCatching {
            KitsugiHttpClient.client.newCall(request).execute().use { response ->
                if (response.code == 429) {
                    PlatformRateLimiter.notifyRateLimited("kitsu")
                    return@use null
                }
                if (!response.isSuccessful) return@use null
                val body = response.body?.string().orEmpty()
                val data = JSONObject(body).optJSONArray("data") ?: return@use null
                if (data.length() == 0) return@use null

                val cleanTarget = title.lowercase().filter { it.isLetterOrDigit() }
                if (cleanTarget.isBlank()) return@use null
                var bestId: Int? = null
                var bestMatchScore = -1
                var bestScoreTies = 0

                for (i in 0 until data.length()) {
                    val item = data.getJSONObject(i)
                    val id = item.optString("id").toIntOrNull() ?: continue
                    val attrs = item.optJSONObject("attributes") ?: continue
                    val titlesObj = attrs.optJSONObject("titles")
                    val canonicalTitle = attrs.optString("canonicalTitle", "")
                    val startDate = attrs.optString("startDate", "")
                    val itemYear = startDate.take(4).toIntOrNull()

                    val candidateTitles = buildList {
                        if (canonicalTitle.isNotBlank()) add(canonicalTitle)
                        if (titlesObj != null) {
                            val keys = titlesObj.keys()
                            while (keys.hasNext()) {
                                val t = titlesObj.optString(keys.next(), "")
                                if (t.isNotBlank()) add(t)
                            }
                        }
                        val abbreviated = attrs.optJSONArray("abbreviatedTitles")
                        if (abbreviated != null) {
                            for (j in 0 until abbreviated.length()) {
                                val t = abbreviated.optString(j, "")
                                if (t.isNotBlank()) add(t)
                            }
                        }
                    }

                    var exactAlias = false
                    var partialAlias = false
                    for (cand in candidateTitles) {
                        val cleanCand = cand.lowercase().filter { it.isLetterOrDigit() }
                        if (cleanCand.isBlank()) continue
                        if (cleanCand == cleanTarget) {
                            exactAlias = true
                        } else if (cleanCand.contains(cleanTarget) || cleanTarget.contains(cleanCand)) {
                            partialAlias = true
                        }
                    }

                    // GÜVENLİK (yanlış içerik eklenmesine karşı): başlık araması yalnızca EKLEME öncesi son çare
                    // olarak kullanılır; bu yüzden kabul kuralı bilinçli olarak dardır.
                    //  • Birebir alias eşleşmesi: yıl bilinmiyor ya da en fazla 1 yıl fark → kabul.
                    //  • Kısmi eşleşme ("Oni Chichi" ⊂ "Oni Chichi 2", "Berserk" ⊂ "Berserk: Ougon Jidai-hen"):
                    //    yalnızca her iki yıl biliniyor ve birebir aynıysa kabul; aksi halde sekans/film seçilebilir.
                    val yearsKnown = expectedYear != null && itemYear != null
                    val yearGap = if (yearsKnown) kotlin.math.abs(expectedYear!! - itemYear!!) else null
                    val matchScore = when {
                        exactAlias && (yearGap == null || yearGap <= 1) -> if (yearGap == 0) 120 else 100
                        partialAlias && yearGap == 0 -> 60
                        else -> 0
                    }

                    if (matchScore < 50) continue
                    if (matchScore > bestMatchScore) {
                        bestMatchScore = matchScore
                        bestId = id
                        bestScoreTies = 0
                    } else if (matchScore == bestMatchScore) {
                        bestScoreTies++
                    }
                }

                when {
                    bestId != null && bestScoreTies == 0 -> bestId
                    bestId != null -> {
                        Log.i(TAG, "Kitsu lookup ambiguous for '$title' (${bestScoreTies + 1} candidates share score $bestMatchScore); skipping")
                        null
                    }
                    else -> null
                }
            }
        }.getOrNull()
    }

    data class KitsuUserStats(
        val timeConsumedSeconds: Long = 0L,
        val episodesWatched: Int = 0,
        val completedCount: Int = 0,
        val mediaCount: Int = 0
    )

    /**
     * Kitsu kullanıcısının detaylı izleme tüketim istatistiklerini çeker.
     * /users/{id}/stats uç noktasından 'anime-amount-consumed' verisini okur.
     */
    suspend fun fetchUserStats(userId: String): KitsuUserStats? = withContext(Dispatchers.IO) {
        val url = "$BASE_URL/users/$userId/stats"
        val request = Request.Builder()
            .url(url)
            .addHeader("Accept", "application/vnd.api+json")
            .addHeader("User-Agent", "KitsugiApp/2.4")
            .get()
            .build()

        runCatching {
            KitsugiHttpClient.client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = response.body?.string().orEmpty()
                val json = JSONObject(body)
                val data = json.optJSONArray("data") ?: return@use null

                for (i in 0 until data.length()) {
                    val item = data.getJSONObject(i)
                    val attrs = item.optJSONObject("attributes") ?: continue
                    val kind = attrs.optString("kind", "")
                    if (kind == "anime-amount-consumed") {
                        val statsData = attrs.optJSONObject("statsData") ?: continue
                        val time = statsData.optLong("time", 0L)
                        val units = statsData.optInt("units", 0)
                        val completed = statsData.optInt("completed", 0)
                        val media = statsData.optInt("media", 0)
                        return@use KitsuUserStats(
                            timeConsumedSeconds = time,
                            episodesWatched = units,
                            completedCount = completed,
                            mediaCount = media
                        )
                    }
                }
                null
            }
        }.getOrNull()
    }

    /**
     * Kitsu kullanıcısının favorilerini (anime, manga, karakter) çeker.
     */
    suspend fun fetchUserFavorites(userId: String, limit: Int = 30): List<com.kitsugi.animelist.ui.app.ProfileFavoriteItem> = withContext(Dispatchers.IO) {
        val url = "$BASE_URL/users/$userId/favorites?include=item&page[limit]=$limit"
        val request = Request.Builder()
            .url(url)
            .addHeader("Accept", "application/vnd.api+json")
            .addHeader("User-Agent", "KitsugiApp/2.4")
            .get()
            .build()

        runCatching {
            KitsugiHttpClient.client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use emptyList()
                val body = response.body?.string().orEmpty()
                val json = JSONObject(body)
                val included = json.optJSONArray("included") ?: org.json.JSONArray()
                val results = mutableListOf<com.kitsugi.animelist.ui.app.ProfileFavoriteItem>()

                for (i in 0 until included.length()) {
                    val inc = included.getJSONObject(i)
                    val id = inc.optString("id", "")
                    val attrs = inc.optJSONObject("attributes") ?: continue
                    val title = attrs.optString("canonicalTitle", attrs.optString("name", "Favori"))
                    val poster = attrs.optJSONObject("posterImage") ?: attrs.optJSONObject("image")
                    val img = poster?.optString("medium") ?: poster?.optString("original")

                    if (id.isNotBlank() && title.isNotBlank()) {
                        results.add(
                            com.kitsugi.animelist.ui.app.ProfileFavoriteItem(
                                id = id,
                                title = title,
                                imageUrl = img ?: ""
                            )
                        )
                    }
                }
                results
            }
        }.getOrDefault(emptyList())
    }
}
