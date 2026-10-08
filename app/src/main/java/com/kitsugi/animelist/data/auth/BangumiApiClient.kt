package com.kitsugi.animelist.data.auth

import android.util.Log
import com.kitsugi.animelist.BuildConfig
import com.kitsugi.animelist.core.network.KitsugiHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/**
 * Bangumi 番组计划 (bgm.tv) REST API istemcisi.
 *
 * Kapsam:
 *  - OAuth 2.0 (authorization code grant): yetki URL'si, code→token, refresh, token durumu
 *  - v0 herkese açık veri uçu:条目 arama, göz atma,条目/章节/人物/角色 detayları
 *  - v0 kullanıcı收藏 (kütüphane) uçu: listeleme, tekil okuma, ekleme/güncelleme,
 *    bölüm bazlı ilerleme ("打格子")
 *
 * Referanslar:
 *  - API sözleşmesi:   https://github.com/bangumi/api  (open-api/v0.yaml)
 *  - Sunucu kaynağı:   https://github.com/bangumi/server
 *  - Yetki akışı:      https://github.com/bangumi/api/blob/master/docs-raw/How-to-Auth.md
 *  - Etkileşimli dokü: https://bangumi.github.io/api/
 *
 * NOT: Bangumi PKCE desteklemez; token ucu `client_secret` ister. Bu yüzden kimlik
 * bilgileri ya `local.properties` → BuildConfig ya da kullanıcı tarafından uygulama
 * içinden girilir (bkz. [BangumiAuthStore]).
 */
object BangumiApiClient {
    private const val TAG = "BangumiApiClient"

    // ── Uç noktalar ──────────────────────────────────────────────────────────
    const val API_BASE = "https://api.bgm.tv"
    const val SITE_BASE = "https://bgm.tv"

    /** Yetki sayfası (kullanıcının tarayıcıda "允许 / İzin Ver" dediği yer). */
    private const val OAUTH_AUTHORIZE_URL = "$SITE_BASE/oauth/authorize"

    /** Code→token ve refresh_token→token uçları. Form gövdeli POST. */
    private const val OAUTH_TOKEN_URL = "$SITE_BASE/oauth/access_token"

    /** Verilen access token'ın sahibini/son kullanmasını döndürür. */
    private const val OAUTH_TOKEN_STATUS_URL = "$SITE_BASE/oauth/token_status"

    /** Kullanıcının kendi uygulamalarını yönettiği sayfa (App ID / App Secret buradan alınır). */
    const val DEV_APP_URL = "$SITE_BASE/dev/app"

    /** Kullanıcının elle (OAuth'suz) kalıcı access token üretebildiği sayfa — yalnızca test için. */
    const val PERSONAL_TOKEN_URL = "https://next.bgm.tv/demo/access-token"

    /**
     * OAuth geri dönüş şemaları.
     *
     * Bangumi'de uygulama kaydında "回调地址" alanı **boş bırakılırsa** authorize isteğinde
     * gönderilen `redirect_uri` aynen kullanılır. Bu, özel şema (custom scheme) deep link'lerin
     * çalışmasını sağlar — Aniyomi `aniyomi://bangumi-auth`, Mihon `mihon://bangumi-auth`,
     * Komikku `komikku://bangumi-auth` ile aynı yöntemi kullanır.
     */
    const val DEEP_LINK_REDIRECT_URI = "kitsugi://bangumi-auth"

    /**
     * Yedek şema. Eski Aniyomi kayıtlı uygulamasıyla (`aniyomi://bangumi-auth`) deneme yapmak
     * isteyen kullanıcılar için; AndroidManifest.xml'de her iki host da kayıtlıdır.
     */
    const val FALLBACK_DEEP_LINK_REDIRECT_URI = "aniyomi://bangumi-auth"

    /** `next.bgm.tv/p1` — Bangumi'nin web arayüzünün kullandığı zengin (karakter/öneri/yorum) API'si. */
    const val NEXT_BASE = "https://next.bgm.tv"

    /** `POST /v0/search/subjects` sunucu tarafında en fazla 20 sonuç döndürür (yumuşak sınır). */
    const val SEARCH_PAGE_SIZE = 20

    private val JSON_MEDIA_TYPE = "application/json".toMediaTypeOrNull()

    /**
     * Bangumi, tarayıcı dışı istemcilerden **geliştirici kimliği + uygulama adı + sürüm +
     * proje adresi** içeren bir User-Agent ister; genel kütüphane UA'ları engellenebilir.
     * Bkz. https://github.com/bangumi/api/blob/master/docs-raw/user%20agent.md
     */
    val USER_AGENT: String =
        "f26901964-eng/Kitsugi-Beta/${BuildConfig.VERSION_NAME} (Android) " +
            "(https://github.com/f26901964-eng/Kitsugi-Beta)"

    /**
     * `lain.bgm.tv` görsel sunucusu bazı durumlarda hotlink koruması uygular.
     * Coil/OkHttp görsel isteklerine bu başlığın eklenmesi önerilir.
     */
    const val IMAGE_REFERER = "$SITE_BASE/"

    private val httpClient get() = KitsugiHttpClient.metadataClient

    // ── Modeller ─────────────────────────────────────────────────────────────

    /** `条目类型` (SubjectType): 1=书籍, 2=动画, 3=音乐, 4=游戏, 6=三次元. (`5` yok.) */
    object SubjectType {
        const val BOOK = 1
        const val ANIME = 2
        const val MUSIC = 3
        const val GAME = 4
        const val REAL = 6
    }

    /** `收藏类型` (CollectionType): kullanıcının条目 ile ilişkisi. */
    object CollectionType {
        const val WISH = 1     // 想看  → Planlandı
        const val DONE = 2     // 看过  → Tamamlandı
        const val DOING = 3    // 在看  → İzleniyor / Okunuyor
        const val ON_HOLD = 4  // 搁置  → Durduruldu
        const val DROPPED = 5  // 抛弃  → Bırakıldı
    }

    /** `章节收藏类型` (EpisodeCollectionType): bölüm bazlı "打格子". */
    object EpisodeCollectionType {
        const val NONE = 0
        const val WISH = 1
        const val DONE = 2
        const val DROPPED = 3
    }

    /**
     * `cat` parametresi — tür içi alt kategori (`SubjectCategory` enum'ları).
     * `GET /v0/subjects?type=...&cat=...` ile kullanılır; değerler **sayısal** ama
     * query string olarak gönderilir.
     */
    object Category {
        // 动画 (type = 2)
        const val ANIME_OTHER = "0"
        const val ANIME_TV = "1"
        const val ANIME_OVA = "2"
        const val ANIME_MOVIE = "3"
        const val ANIME_WEB = "5"

        // 书籍 (type = 1)
        const val BOOK_OTHER = "0"
        const val BOOK_COMIC = "1001"
        const val BOOK_NOVEL = "1002"
        const val BOOK_ILLUSTRATION = "1003"

        // 三次元 (type = 6)
        const val REAL_OTHER = "0"
        const val REAL_JP_DRAMA = "1"
        const val REAL_EN_DRAMA = "2"
        const val REAL_CN_DRAMA = "3"
        const val REAL_TV = "6001"
        const val REAL_MOVIE = "6002"
        const val REAL_LIVE = "6003"
        const val REAL_VARIETY = "6004"
    }

    /** Arama/keşfet sıralama seçenekleri (`POST /v0/search/subjects`). */
    object SearchSort {
        /** Meilisearch varsayılanı — eşleşme kalitesi. */
        const val MATCH = "match"

        /** 收藏人数 — koleksiyona ekleyen kullanıcı sayısı (popülerlik). */
        const val HEAT = "heat"

        /** 排名 — kategori içi sıra. */
        const val RANK = "rank"

        /** 评分 — puan. */
        const val SCORE = "score"
    }

    data class BangumiToken(
        val accessToken: String,
        val tokenType: String,
        val expiresIn: Long,
        val refreshToken: String?,
        val userId: Long?,
        val scope: String?
    )

    data class BangumiUser(
        val id: Long,
        /** Kullanıcının benzersiz adı; koleksiyon uçlarında `-` yerine kullanılabilir. */
        val username: String,
        val nickname: String,
        val avatarUrl: String?,
        val userGroup: Int? = null
    )

    data class BangumiImages(
        val large: String? = null,
        val common: String? = null,
        val medium: String? = null,
        val small: String? = null,
        val grid: String? = null
    ) {
        /** Kart/poster için tercih edilen boyut; yoksa bir alttakine düşer. */
        val poster: String? get() = common ?: medium ?: grid ?: large ?: small
        val thumb: String? get() = grid ?: small ?: medium ?: common ?: large
        val backdrop: String? get() = large ?: common ?: medium
    }

    /**
     * Karakter (角色) ve kişi (人物) aramalarının ortak hafif modeli.
     * `Paged_Character.data[]` tam `Character`, `Paged_Person.data[]` tam `Person`
     * nesnesidir; Kitsugi arama listeleri yalnızca kimlik + ad + görsel kullanır.
     */
    data class BangumiEntity(
        val id: Int,
        val name: String,
        /** Karakter için `type` (角色/机体/舰船/组织), kişi için `type` (1=个人, 2=公司, 3=组合). */
        val type: Int = 0,
        val career: List<String> = emptyList(),
        val summary: String? = null,
        val images: BangumiImages? = null
    ) {
        /** Liste kartı için görünen başlık; Çince ad infobox'tan gelirse [name] yeterlidir. */
        val displayTitle: String get() = name.ifBlank { "?" }
    }

    data class BangumiRating(
        val rank: Int = 0,
        val total: Int = 0,
        val score: Double = 0.0,
        /** 1..10 arası puan dağılımı. */
        val count: Map<Int, Int> = emptyMap()
    )

    data class BangumiCollectionCounts(
        val wish: Int = 0,
        val collect: Int = 0,
        val doing: Int = 0,
        val onHold: Int = 0,
        val dropped: Int = 0
    ) {
        val total: Int get() = wish + collect + doing + onHold + dropped
    }

    /**
     * `GET /v0/subjects/{id}` tam条目 modeli.
     *
     * Arama uçları bunun kısaltılmışını (`SlimSubject`) döndürür; [BangumiSlimSubject].
     */
    data class BangumiSubject(
        val id: Int,
        val type: Int,
        val name: String,
        val nameCn: String,
        val summary: String,
        val date: String?,
        val platform: String?,
        val nsfw: Boolean,
        val locked: Boolean,
        val eps: Int,
        val totalEpisodes: Int,
        val volumes: Int,
        val images: BangumiImages?,
        val rating: BangumiRating?,
        val collection: BangumiCollectionCounts?,
        val tags: List<String>,
        val metaTags: List<String>,
        /** `infobox` wiki alanları (别名, 制作公司, 导演, 官方网站 ...). */
        val infobox: Map<String, List<String>>
    ) {
        /** Görüntülenecek başlık: Çince ad varsa önce o (Aniyomi/Mihon ile aynı tercih). */
        val displayTitle: String get() = nameCn.ifBlank { name }
        val isAnime: Boolean get() = type == SubjectType.ANIME
        val isBook: Boolean get() = type == SubjectType.BOOK
        val webUrl: String get() = "$SITE_BASE/subject/$id"
    }

    /** Arama ve koleksiyon listelerinde dönen kısaltılmış条目. */
    data class BangumiSlimSubject(
        val id: Int,
        val type: Int,
        val name: String,
        val nameCn: String,
        val shortSummary: String,
        val date: String?,
        val eps: Int,
        val volumes: Int,
        val images: BangumiImages?,
        val score: Double,
        val rank: Int,
        val collectionTotal: Int,
        val tags: List<String>
    ) {
        val displayTitle: String get() = nameCn.ifBlank { name }
        val isAnime: Boolean get() = type == SubjectType.ANIME
        val isBook: Boolean get() = type == SubjectType.BOOK
        val webUrl: String get() = "$SITE_BASE/subject/$id"
    }

    /** `GET /v0/subjects/{id}/subjects` satırı (`v0_subject_relation`). */
    data class BangumiRelatedSubject(
        val id: Int,
        val type: Int,
        val name: String,
        val nameCn: String,
        /** 续集 / 前传 / 衍生 / 改编 ... */
        val relation: String?,
        val image: String?
    ) {
        val displayTitle: String get() = nameCn.ifBlank { name }
        val webUrl: String get() = "$SITE_BASE/subject/$id"
    }

    data class BangumiEpisode(
        val id: Int,
        val subjectId: Int? = null,
        /** 0=本篇, 1=SP, 2=OP, 3=ED */
        val type: Int,
        val name: String,
        val nameCn: String,
        val sort: Double,
        val ep: Double?,
        val airdate: String?,
        val duration: String?,
        val durationSeconds: Int,
        val desc: String?
    ) {
        val isMainStory: Boolean get() = type == 0
        val displayTitle: String get() = nameCn.ifBlank { name }
    }

    /** `GET /v0/users/{username}/collections` satırı. */
    data class BangumiUserCollection(
        val subjectId: Int,
        val subjectType: Int,
        /** 1..5 — [CollectionType] */
        val type: Int,
        val rate: Int,
        val comment: String?,
        val tags: List<String>,
        val epStatus: Int,
        val volStatus: Int,
        val private: Boolean,
        /** `2022-06-19T18:44:13.614+08:00` biçiminde; koleksiyon zamanı DEĞİLDİR (API notu). */
        val updatedAt: String?,
        val subject: BangumiSlimSubject?
    )

    /** Bölüm bazlı koleksiyon durumu (`GET /v0/users/-/collections/{id}/episodes`). */
    data class BangumiUserEpisodeCollection(
        val episodeId: Int,
        /** [EpisodeCollectionType] */
        val type: Int,
        val episode: BangumiEpisode? = null
    )

    /** Sayfalı yanıtların ortak zarfı (`total` + `limit` + `offset` + `data`). */
    data class BangumiPage<T>(
        val total: Int,
        val limit: Int,
        val offset: Int,
        val data: List<T>
    ) {
        val hasMore: Boolean get() = offset + data.size < total
        val nextOffset: Int get() = offset + data.size
    }

    /** Bangumi hata gövdesi: `{ "title": ..., "description": ... }`. */
    class BangumiApiException(
        val code: Int,
        val title: String?,
        val description: String?,
        override val message: String
    ) : Exception(message) {
        val isUnauthorized: Boolean get() = code == 401
        val isForbidden: Boolean get() = code == 403
        val isNotFound: Boolean get() = code == 404
        val isRateLimited: Boolean get() = code == 429
    }

    // ── HTTP çekirdeği ───────────────────────────────────────────────────────

    private fun Request.Builder.bangumiHeaders(token: String?): Request.Builder = apply {
        header("User-Agent", USER_AGENT)
        header("Accept", "application/json")
        if (!token.isNullOrBlank()) header("Authorization", "Bearer $token")
    }

    /**
     * İsteği çalıştırır ve gövdeyi döndürür. 2xx dışında [BangumiApiException] fırlatır.
     * 204 (gövdesiz başarı) ve boş gövde `null` olarak döner.
     */
    private fun execute(request: Request): String? {
        httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (response.isSuccessful) {
                if (response.code == 204 || body.isBlank()) return null
                return body
            }
            throw parseError(response.code, body)
        }
    }

    private fun parseError(code: Int, body: String): BangumiApiException {
        var title: String? = null
        var description: String? = null
        runCatching {
            val json = JSONObject(body)
            title = json.optString("title").ifBlank { null }
            description = json.optString("description").ifBlank { null }
        }
        val message = buildString {
            append("Bangumi HTTP $code")
            title?.let { append(" — ").append(it) }
            description?.let { append(" (").append(it).append(")") }
        }
        return BangumiApiException(code, title, description, message)
    }

    private fun get(path: String, query: Map<String, String?> = emptyMap(), token: String? = null): String? {
        val url = buildUrl(path, query)
        val request = Request.Builder().url(url).get().bangumiHeaders(token).build()
        return execute(request)
    }

    /**
     * Kimlik doğrulamasız/isteğe bağlı token'lı genel GET. Gövdeyi ham metin olarak döndürür.
     *
     * @param next `true` → `next.bgm.tv/p1` (zengin web API'si), `false` → `api.bgm.tv`
     *             (v0 + legacy uçlar). 404 ve diğer hatalar [BangumiApiException] fırlatır.
     */
    suspend fun getRaw(
        path: String,
        query: Map<String, String?> = emptyMap(),
        token: String? = null,
        next: Boolean = false
    ): String? = withContext(Dispatchers.IO) {
        val url = if (next) buildUrl("/p1$path", query, NEXT_BASE) else buildUrl(path, query)
        val request = Request.Builder().url(url).get().bangumiHeaders(token).build()
        execute(request)
    }

    private fun send(
        method: String,
        path: String,
        jsonBody: JSONObject?,
        token: String?,
        query: Map<String, String?> = emptyMap()
    ): String? {
        val body = (jsonBody?.toString() ?: "").toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url(buildUrl(path, query))
            .method(method, body)
            .bangumiHeaders(token)
            .header("Content-Type", "application/json")
            .build()
        return execute(request)
    }

    private fun buildUrl(path: String, query: Map<String, String?>, base: String = API_BASE): String {
        val sb = StringBuilder(base).append(path)
        var first = true
        query.forEach { (key, value) ->
            if (value.isNullOrBlank()) return@forEach
            sb.append(if (first) '?' else '&').append(key).append('=')
                .append(URLEncoder.encode(value, "UTF-8"))
            first = false
        }
        return sb.toString()
    }

    // ── OAuth 2.0 ────────────────────────────────────────────────────────────

    /**
     * Kullanıcıyı Bangumi yetki sayfasına götürecek URL'yi kurar.
     *
     * `scope` Bangumi'nin klasik OAuth akışında **uygulanmaz** (dokümanda "尚未实现");
     * scope'suz üretilen token'lar "legacy" sayılır ve yazma uçlarında scope kontrolünü
     * geçer (`Auth.HasScope` → `Legacy || Scope == nil`). Yeni `next.bgm.tv` token
     * sayfası ise `write:collection` gibi dar kapsamlı token üretir.
     */
    fun buildAuthorizeUrl(
        clientId: String,
        redirectUri: String = DEEP_LINK_REDIRECT_URI,
        state: String? = null
    ): String {
        val sb = StringBuilder(OAUTH_AUTHORIZE_URL)
            .append("?client_id=").append(URLEncoder.encode(clientId.trim(), "UTF-8"))
            .append("&response_type=code")
            .append("&redirect_uri=").append(URLEncoder.encode(redirectUri.trim(), "UTF-8"))
        if (!state.isNullOrBlank()) {
            sb.append("&state=").append(URLEncoder.encode(state, "UTF-8"))
        }
        return sb.toString()
    }

    /**
     * Yetkilendirme kodunu access token'a çevirir.
     *
     * Kod **60 saniye** geçerlidir ve tek kullanımlıktır. `redirect_uri` authorize
     * adımında kullanılan değerle birebir aynı olmalıdır.
     */
    suspend fun exchangeCodeForToken(
        clientId: String,
        clientSecret: String,
        code: String,
        redirectUri: String = DEEP_LINK_REDIRECT_URI
    ): BangumiToken = withContext(Dispatchers.IO) {
        val form = FormBody.Builder()
            .add("grant_type", "authorization_code")
            .add("client_id", clientId.trim())
            .add("client_secret", clientSecret.trim())
            .add("code", code.trim())
            .add("redirect_uri", redirectUri.trim())
            .build()
        val request = Request.Builder()
            .url(OAUTH_TOKEN_URL)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .post(form)
            .build()
        httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw parseError(response.code, body)
            parseToken(body)
        }
    }

    /**
     * Süresi dolan access token'ı yeniler. Bangumi **yeni bir refresh token da** döndürür;
     * eskisi geçersiz olur, bu yüzden ikisi birlikte saklanmalıdır.
     */
    suspend fun refreshToken(
        clientId: String,
        clientSecret: String,
        refreshToken: String,
        redirectUri: String = DEEP_LINK_REDIRECT_URI
    ): BangumiToken = withContext(Dispatchers.IO) {
        val form = FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("client_id", clientId.trim())
            .add("client_secret", clientSecret.trim())
            .add("refresh_token", refreshToken.trim())
            .add("redirect_uri", redirectUri.trim())
            .build()
        val request = Request.Builder()
            .url(OAUTH_TOKEN_URL)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .post(form)
            .build()
        httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw parseError(response.code, body)
            parseToken(body)
        }
    }

    /** `POST /oauth/token_status` — token'ın sahibini ve son kullanma zamanını doğrular. */
    suspend fun tokenStatus(accessToken: String): JSONObject? = withContext(Dispatchers.IO) {
        val form = FormBody.Builder().add("access_token", accessToken).build()
        val request = Request.Builder()
            .url(OAUTH_TOKEN_STATUS_URL)
            .header("User-Agent", USER_AGENT)
            .post(form)
            .build()
        httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw parseError(response.code, body)
            runCatching { JSONObject(body) }.getOrNull()
        }
    }

    private fun parseToken(body: String): BangumiToken {
        val json = JSONObject(body)
        val access = json.optString("access_token")
        if (access.isBlank()) {
            throw BangumiApiException(
                code = 0,
                title = json.optString("error").ifBlank { null },
                description = json.optString("error_description").ifBlank { null },
                message = "Bangumi token yanıtında access_token yok: ${json.optString("error")}"
            )
        }
        return BangumiToken(
            accessToken = access,
            tokenType = json.optString("token_type", "Bearer"),
            expiresIn = json.optLong("expires_in", 604800L),
            refreshToken = json.optString("refresh_token").ifBlank { null },
            userId = json.optLong("user_id", 0L).takeIf { it > 0 },
            scope = json.optString("scope").ifBlank { null }
        )
    }

    /** Kullanıcının yapıştırdığı metinden (tam URL olabilir) auth code'u ayıklar. */
    fun sanitizeAuthCode(rawInput: String): String {
        var value = rawInput.trim().trim('"', '\'', '<', '>', ' ')
        if (value.isBlank()) return value
        val codeParam = Regex("[?&]code=([^&#\\s]+)").find(value)?.groupValues?.getOrNull(1)
        if (!codeParam.isNullOrBlank()) value = codeParam
        return runCatching { java.net.URLDecoder.decode(value, "UTF-8") }.getOrDefault(value).trim()
    }

    // ── Kullanıcı ────────────────────────────────────────────────────────────

    /** `GET /v0/me` — token sahibi kullanıcı. Giriş sonrası kimliği doğrulamak için kullanılır. */
    suspend fun getMe(token: String): BangumiUser = withContext(Dispatchers.IO) {
        val body = get("/v0/me", token = token) ?: throw BangumiApiException(0, null, null, "Bangumi /v0/me boş yanıt")
        parseUser(JSONObject(body))
    }

    /** `GET /v0/users/{username}` — başka bir kullanıcının açık profili. */
    suspend fun getUser(token: String?, username: String): BangumiUser? = withContext(Dispatchers.IO) {
        runCatching {
            val body = get("/v0/users/${URLEncoder.encode(username, "UTF-8")}", token = token) ?: return@runCatching null
            parseUser(JSONObject(body))
        }.getOrNull()
    }

    private fun parseUser(json: JSONObject): BangumiUser {
        val avatar = json.optJSONObject("avatar")
        return BangumiUser(
            id = json.optLong("id", 0L),
            username = json.optString("username"),
            nickname = json.optString("nickname").ifBlank { json.optString("username") },
            avatarUrl = avatar?.optString("large")?.ifBlank { null }
                ?: avatar?.optString("medium")?.ifBlank { null },
            userGroup = json.optInt("user_group", 0).takeIf { it > 0 }
        )
    }

    // ── Arama (arama sekmesi) ────────────────────────────────────────────────

    /**
     * `POST /v0/search/subjects` — deneysel ama 2022'den beri kararlı olan条目 arama ucu.
     *
     * @param types    [SubjectType] değerleri; boş liste = tüm türler. Çoklu değerler `VEYA`.
     * @param sort     [SearchSort]; diğer filtreler `VE` ilişkili.
     * @param tags     `tag` filtresi (kullanıcı etiketleri). `-etiket` biçimi hariç tutar.
     * @param airDate  `>=2020-07-01`, `<2020-10-01` gibi aralık ifadeleri.
     * @param rating   `>=8` gibi puan ifadeleri.
     * @param nsfw     JSON **boolean**: `false` → yalnız R18 olmayanlar, `true` → yalnız R18,
     *                 `null` → filtre yok (R18 yalnızca yetkili hesaplara döner).
     *                 DİKKAT: sunucu bu alanı `null.Bool` olarak çözer; eski sürümdeki
     *                 `"nsfw":"include"` metni HTTP 400 döndürüp aramanın tamamen boş
     *                 görünmesine yol açıyordu.
     * @param limit    Sunucu `limit` değerini 20'ye KISAR (yumuşak sınır); sayfalama
     *                 `offset`'i istemci limitine göre hesaplarsa kayıt atlar. Bu yüzden
     *                 burada 1..20 aralığına sabitlenir ve çağıranlar aynı değeri kullanmalıdır.
     */
    suspend fun searchSubjects(
        keyword: String,
        token: String? = null,
        types: List<Int> = emptyList(),
        sort: String = SearchSort.MATCH,
        tags: List<String> = emptyList(),
        airDate: List<String> = emptyList(),
        rating: List<String> = emptyList(),
        rank: List<String> = emptyList(),
        nsfw: Boolean? = null,
        limit: Int = SEARCH_PAGE_SIZE,
        offset: Int = 0
    ): BangumiPage<BangumiSubject> = withContext(Dispatchers.IO) {
        val pageLimit = limit.coerceIn(1, SEARCH_PAGE_SIZE)
        val payload = buildSearchSubjectsPayload(keyword, sort, types, tags, airDate, rating, rank, nsfw)
        val body = send(
            method = "POST",
            path = "/v0/search/subjects",
            jsonBody = payload,
            token = token,
            query = mapOf("limit" to pageLimit.toString(), "offset" to offset.toString())
        ) ?: return@withContext BangumiPage(0, pageLimit, offset, emptyList())
        // Arama ucu `Paged_Subject` döndürür: öğeler TAM条目 modelidir (nsfw, rating, tags dahil).
        parseSubjectPage(JSONObject(body), pageLimit, offset)
    }

    /**
     * `POST /v0/search/subjects` gövdesi. `filter.nsfw` bir JSON **boolean**'dır; sunucu bunu
     * `null.Bool` olarak çözer. Metin (`"include"`) göndermek HTTP 400 verir.
     */
    internal fun buildSearchSubjectsPayload(
        keyword: String,
        sort: String,
        types: List<Int>,
        tags: List<String>,
        airDate: List<String>,
        rating: List<String>,
        rank: List<String>,
        nsfw: Boolean?
    ): JSONObject = JSONObject().apply {
        put("keyword", keyword)
        put("sort", sort)
        val filter = JSONObject()
        var hasFilter = false
        if (types.isNotEmpty()) {
            filter.put("type", JSONArray(types))
            hasFilter = true
        }
        if (tags.isNotEmpty()) {
            filter.put("tag", JSONArray(tags))
            hasFilter = true
        }
        if (airDate.isNotEmpty()) {
            filter.put("air_date", JSONArray(airDate))
            hasFilter = true
        }
        if (rating.isNotEmpty()) {
            filter.put("rating", JSONArray(rating))
            hasFilter = true
        }
        if (rank.isNotEmpty()) {
            filter.put("rank", JSONArray(rank))
            hasFilter = true
        }
        if (nsfw != null) {
            filter.put("nsfw", nsfw)
            hasFilter = true
        }
        if (hasFilter) put("filter", filter)
    }

    /**
     * Eski (legacy) arama ucu: `GET /search/subject/{keyword}?type=2&responseGroup=large`.
     *
     * `POST /v0/search/subjects` "deneysel"dir ve hata verirse ya da boş dönerse arama ekranı
     * tamamen boş kalıyordu. Aniyomi/Mihon'un da kullandığı bu uç, Latin harfli sorgularda
     * (örn. "date a live") takma adlardan eşleşir ve yedek olarak kullanılır.
     * Sonuç bulunamazsa sunucu 404 ya da `list=null` döndürebilir → boş sayfa.
     *
     * @param type [SubjectType] (null = tümü)
     */
    suspend fun searchSubjectsLegacy(
        keyword: String,
        token: String? = null,
        type: Int? = null,
        limit: Int = SEARCH_PAGE_SIZE,
        offset: Int = 0
    ): BangumiPage<BangumiSubject> = withContext(Dispatchers.IO) {
        val query = linkedMapOf(
            "type" to type?.toString(),
            "responseGroup" to "large",
            "start" to offset.toString(),
            "max_results" to limit.coerceIn(1, 25).toString()
        )
        val path = "/search/subject/" + URLEncoder.encode(keyword.trim(), "UTF-8").replace("+", "%20")
        val body = try {
            get(path, query, token)
        } catch (e: BangumiApiException) {
            if (e.isNotFound) null else throw e
        } ?: return@withContext BangumiPage(0, limit, offset, emptyList())
        parseLegacySearchResponse(body, limit, offset)
    }

    /** Legacy arama yanıtı (`{results, list:[...]}`) → [BangumiPage]. Bozuk/boş gövde boş sayfa verir. */
    internal fun parseLegacySearchResponse(body: String, limit: Int, offset: Int): BangumiPage<BangumiSubject> {
        val json = runCatching { JSONObject(body) }.getOrNull()
            ?: return BangumiPage(0, limit, offset, emptyList())
        val array = json.optJSONArray("list") ?: JSONArray()
        val items = (0 until array.length()).mapNotNull { index ->
            val raw = array.optJSONObject(index) ?: return@mapNotNull null
            // Legacy öğede tarih `air_date` ("0000-00-00" = bilinmiyor); v0 ayrıştırıcısı `date` bekler.
            if (!raw.has("date") || raw.isNull("date")) {
                val airDate = raw.cleanString("air_date")
                raw.put("date", if (airDate.startsWith("0000")) "" else airDate)
            }
            parseSubjectOrNull(raw)
        }
        return BangumiPage(
            total = json.optInt("results", items.size),
            limit = limit,
            offset = offset,
            data = items
        )
    }

    /** Yalnızca动画 arar (anime sekmesi kısayolu). */
    suspend fun searchAnime(
        keyword: String,
        token: String? = null,
        sort: String = SearchSort.MATCH,
        limit: Int = SEARCH_PAGE_SIZE,
        offset: Int = 0
    ): BangumiPage<BangumiSubject> =
        searchSubjects(keyword, token, listOf(SubjectType.ANIME), sort, limit = limit, offset = offset)

    /** Yalnızca书籍 arar ve `platform == "漫画"` olmayanları eler (manga sekmesi kısayolu). */
    suspend fun searchBooks(
        keyword: String,
        token: String? = null,
        sort: String = SearchSort.MATCH,
        limit: Int = SEARCH_PAGE_SIZE,
        offset: Int = 0
    ): BangumiPage<BangumiSubject> =
        searchSubjects(keyword, token, listOf(SubjectType.BOOK), sort, limit = limit, offset = offset)

    /**
     * `POST /v0/search/characters` — karakter (角色) araması. **Deneysel uçtur**:
     * şema ve davranış değişebilir; bu yüzden hata durumunda boş liste döner.
     *
     * @param nsfw `true` → yalnız R18, `false` → yalnız R18 olmayan, `null` → hepsi
     *        (yetkisiz istekte R18 zaten hiç dönmez).
     */
    suspend fun searchCharacters(
        keyword: String,
        token: String? = null,
        nsfw: Boolean? = null,
        limit: Int = 25,
        offset: Int = 0
    ): BangumiPage<BangumiEntity> = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("keyword", keyword)
            if (nsfw != null) put("filter", JSONObject().put("nsfw", nsfw))
        }
        val body = send(
            method = "POST",
            path = "/v0/search/characters",
            jsonBody = payload,
            token = token,
            query = mapOf("limit" to limit.toString(), "offset" to offset.toString())
        ) ?: return@withContext BangumiPage(0, limit, offset, emptyList())
        parseEntityPage(JSONObject(body), limit, offset)
    }

    /**
     * `POST /v0/search/persons` — kişi/seslendirmen (人物) araması. **Deneysel uçtur.**
     *
     * @param career meslek filtresi (`artist`, `director`, `seiyu`...); çoklu değerler `VE` ilişkili.
     */
    suspend fun searchPersons(
        keyword: String,
        token: String? = null,
        career: List<String> = emptyList(),
        limit: Int = 25,
        offset: Int = 0
    ): BangumiPage<BangumiEntity> = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("keyword", keyword)
            if (career.isNotEmpty()) put("filter", JSONObject().put("career", JSONArray(career)))
        }
        val body = send(
            method = "POST",
            path = "/v0/search/persons",
            jsonBody = payload,
            token = token,
            query = mapOf("limit" to limit.toString(), "offset" to offset.toString())
        ) ?: return@withContext BangumiPage(0, limit, offset, emptyList())
        parseEntityPage(JSONObject(body), limit, offset)
    }

    // ── Keşfet / göz atma ────────────────────────────────────────────────────

    /**
     * `GET /v0/subjects` — takvim/yıl bazlı göz atma ("分类浏览" eşleniği).
     *
     * Keşfet şeritleri için en uygun uç: sezon (yıl+ay), tür ve sıralama (`date|rank`)
     * verilebilir. İlk sayfa 24 saat, sonraki sayfalar 1 saat sunucu tarafında önbelleklenir;
     * bu yüzden keşfet akışında düşük maliyetlidir.
     */
    suspend fun browseSubjects(
        type: Int,
        token: String? = null,
        cat: String? = null,
        sort: String? = null,
        year: Int? = null,
        month: Int? = null,
        series: Boolean? = null,
        limit: Int = 25,
        offset: Int = 0
    ): BangumiPage<BangumiSubject> = withContext(Dispatchers.IO) {
        val query = linkedMapOf(
            "type" to type.toString(),
            "cat" to cat,
            "sort" to sort,
            "year" to year?.toString(),
            "month" to month?.toString(),
            "series" to series?.toString(),
            "limit" to limit.toString(),
            "offset" to offset.toString()
        )
        val body = get("/v0/subjects", query, token)
            ?: return@withContext BangumiPage(0, limit, offset, emptyList())
        parseSubjectPage(JSONObject(body), limit, offset)
    }

    /**
     * Sezon keşfi: verilen yıl/ay aralığında动画条目'ları yayın tarihine göre sıralar.
     * `month` verilmezse tüm yıl döner (Bangumi ayı zorunlu tutmaz).
     */
    suspend fun browseSeason(
        year: Int,
        month: Int? = null,
        token: String? = null,
        limit: Int = 25,
        offset: Int = 0
    ): BangumiPage<BangumiSubject> =
        browseSubjects(SubjectType.ANIME, token, sort = "date", year = year, month = month, limit = limit, offset = offset)

    /**
     * Kategori içi en yüksek sıralı ("排行榜") liste.
     *
     * `GET /v0/subjects?sort=rank` resmî olarak desteklenen sıralama枚举值'larından biridir
     * (`{date|rank}`) ve sunucu tarafında önbelleklenir; bu yüzden keşfet şeritleri için
     * boş anahtar kelimeli arama isteğinden daha güvenlidir.
     */
    suspend fun topRanked(
        type: Int,
        token: String? = null,
        limit: Int = 25,
        offset: Int = 0
    ): BangumiPage<BangumiSubject> =
        browseSubjects(type = type, token = token, sort = "rank", limit = limit, offset = offset)

    /**
     * En çok koleksiyona eklenen ("近期注目" benzeri popülerlik) liste.
     *
     * Önce boş anahtar kelimeli `sort=heat` araması denenir; arama ucu deneysel olduğu ve
     * boş kelimeyi reddedebildiği için başarısızlıkta yayın tarihine göre göz atmaya düşülür.
     * Böylece keşfet şeridi hiçbir durumda boş kalmaz.
     */
    suspend fun mostCollected(
        type: Int,
        token: String? = null,
        limit: Int = 25,
        offset: Int = 0
    ): BangumiPage<BangumiSubject> {
        val byHeat = runCatching {
            searchSubjects(
                keyword = "",
                token = token,
                types = listOf(type),
                sort = SearchSort.HEAT,
                limit = limit,
                offset = offset
            )
        }.getOrNull()
        if (byHeat != null && byHeat.data.isNotEmpty()) return byHeat
        Log.w(TAG, "mostCollected(type=$type): heat araması boş/hatalı, tarih sıralamasına düşülüyor")
        return browseSubjects(type = type, token = token, sort = "date", limit = limit, offset = offset)
    }

    // ── Detay ────────────────────────────────────────────────────────────────

    /** `GET /v0/subjects/{id}` — tam条目 (300 sn sunucu önbelleği). */
    suspend fun getSubject(subjectId: Int, token: String? = null): BangumiSubject = withContext(Dispatchers.IO) {
        val body = get("/v0/subjects/$subjectId", token = token)
            ?: throw BangumiApiException(404, "Not Found", "条目 $subjectId bulunamadı", "Bangumi条目 $subjectId boş yanıt")
        parseSubject(JSONObject(body))
    }

    /** `GET /v0/episodes?subject_id=...&type=0` — ana hikâye bölümleri. */
    suspend fun getEpisodes(
        subjectId: Int,
        token: String? = null,
        episodeType: Int? = 0,
        limit: Int = 200,
        offset: Int = 0
    ): BangumiPage<BangumiEpisode> = withContext(Dispatchers.IO) {
        val query = linkedMapOf(
            "subject_id" to subjectId.toString(),
            "type" to episodeType?.toString(),
            "limit" to limit.coerceAtMost(200).toString(),
            "offset" to offset.toString()
        )
        val body = get("/v0/episodes", query, token)
            ?: return@withContext BangumiPage(0, limit, offset, emptyList())
        val json = JSONObject(body)
        val array = json.optJSONArray("data") ?: JSONArray()
        val items = (0 until array.length()).mapNotNull { parseEpisode(array.optJSONObject(it), subjectId) }
        BangumiPage(
            total = json.optInt("total", items.size),
            limit = json.optInt("limit", limit),
            offset = json.optInt("offset", offset),
            data = items
        )
    }

    /**
     * Bir条目的 tüm ana hikâye bölümlerini sayfalayarak toplar.
     * Bölüm sayısı yüksek yapımlarda (örn. 500+ bölüm) gereksiz istek atmamak için
     * `maxPages` sınırı vardır.
     */
    suspend fun getAllMainEpisodes(subjectId: Int, token: String? = null, maxPages: Int = 6): List<BangumiEpisode> {
        val all = mutableListOf<BangumiEpisode>()
        var offset = 0
        var page = 0
        while (page < maxPages) {
            val result = getEpisodes(subjectId, token, episodeType = 0, limit = 200, offset = offset)
            all += result.data
            if (!result.hasMore || result.data.isEmpty()) break
            offset = result.nextOffset
            page++
        }
        return all
    }

    /** `GET /v0/subjects/{id}/subjects` — ilişkili条目'lar (devam sezonu, yan hikâye, kitap uyarlaması). */
    suspend fun getRelatedSubjects(subjectId: Int, token: String? = null): List<BangumiRelatedSubject> =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = get("/v0/subjects/$subjectId/subjects", token = token)
                    ?: return@runCatching emptyList<BangumiRelatedSubject>()
                val array = JSONArray(body)
                (0 until array.length()).mapNotNull { i ->
                    val o = array.optJSONObject(i) ?: return@mapNotNull null
                    val id = o.optInt("id", 0).takeIf { it > 0 } ?: return@mapNotNull null
                    BangumiRelatedSubject(
                        id = id,
                        type = o.optInt("type", SubjectType.ANIME),
                        name = o.optString("name"),
                        nameCn = o.optString("name_cn"),
                        relation = o.optString("relation").ifBlank { null },
                        image = absoluteImageUrl(o.optString("image").ifBlank { null })
                    )
                }
            }.getOrElse {
                Log.w(TAG, "getRelatedSubjects($subjectId) failed: ${it.message}")
                emptyList()
            }
        }

    /** `GET /v0/subjects/{id}/persons` — yapım kadrosu (制作公司, 导演, 声优...). */
    suspend fun getSubjectPersons(subjectId: Int, token: String? = null): List<Pair<Int, String>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = get("/v0/subjects/$subjectId/persons", token = token) ?: return@runCatching emptyList()
                val array = JSONArray(body)
                (0 until array.length()).mapNotNull { i ->
                    val o = array.optJSONObject(i) ?: return@mapNotNull null
                    val id = o.optInt("id", 0).takeIf { it > 0 } ?: return@mapNotNull null
                    id to "${o.optString("name")} · ${o.optString("relation")}"
                }
            }.getOrElse { emptyList() }
        }

    /** `GET /v0/subjects/{id}/characters` — karakterler. */
    suspend fun getSubjectCharacters(subjectId: Int, token: String? = null): List<Pair<Int, String>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = get("/v0/subjects/$subjectId/characters", token = token) ?: return@runCatching emptyList()
                val array = JSONArray(body)
                (0 until array.length()).mapNotNull { i ->
                    val o = array.optJSONObject(i) ?: return@mapNotNull null
                    val id = o.optInt("id", 0).takeIf { it > 0 } ?: return@mapNotNull null
                    id to "${o.optString("name")} · ${o.optString("relation")}"
                }
            }.getOrElse { emptyList() }
        }

    /** `GET /v0/characters/{id}` — karakter detayı (Kitsugi karakter sayfaları için). */
    suspend fun getCharacter(characterId: Int, token: String? = null): JSONObject? = withContext(Dispatchers.IO) {
        runCatching {
            get("/v0/characters/$characterId", token = token)?.let { JSONObject(it) }
        }.getOrNull()
    }

    /** `GET /v0/persons/{id}` — kişi/ses sanatçısı detayı. */
    suspend fun getPerson(personId: Int, token: String? = null): JSONObject? = withContext(Dispatchers.IO) {
        runCatching {
            get("/v0/persons/$personId", token = token)?.let { JSONObject(it) }
        }.getOrNull()
    }

    // ── Kullanıcı kütüphanesi (收藏) ─────────────────────────────────────────

    /**
     * `GET /v0/users/{username}/collections` — kullanıcının koleksiyonu, sayfalı.
     *
     * `username` GERÇEK kullanıcı adı (ya da sayısal ID) olmalıdır: sunucu bu okuma ucunda
     * `-` takma adını KABUL ETMEZ (404 "user doesn't exist or has been removed" döner).
     * Token sahibinin adı için [BangumiAuthStore.resolveUsername] kullanılır. Özel (private)
     * 收藏'ları görmek için token zorunludur.
     *
     * @param subjectType null = tüm türler, [SubjectType.ANIME], [SubjectType.BOOK] ...
     * @param collectionType null = tüm durumlar, [CollectionType.DOING] ...
     */
    suspend fun getUserCollections(
        token: String,
        username: String,
        subjectType: Int? = null,
        collectionType: Int? = null,
        limit: Int = 50,
        offset: Int = 0
    ): BangumiPage<BangumiUserCollection> = withContext(Dispatchers.IO) {
        val query = linkedMapOf(
            "subject_type" to subjectType?.toString(),
            "type" to collectionType?.toString(),
            "limit" to limit.coerceIn(1, 50).toString(),
            "offset" to offset.toString()
        )
        val body = get("/v0/users/${URLEncoder.encode(username, "UTF-8")}/collections", query, token)
            ?: return@withContext BangumiPage(0, limit, offset, emptyList())
        val json = JSONObject(body)
        val array = json.optJSONArray("data") ?: JSONArray()
        val items = (0 until array.length()).mapNotNull { parseUserCollection(array.optJSONObject(it)) }
        BangumiPage(
            total = json.optInt("total", items.size),
            limit = json.optInt("limit", limit),
            offset = json.optInt("offset", offset),
            data = items
        )
    }

    /** Kullanıcının tüm koleksiyonunu sayfalayarak toplar (içe aktarma için). */
    suspend fun getAllUserCollections(
        token: String,
        username: String,
        subjectType: Int? = null,
        maxPages: Int = 200
    ): List<BangumiUserCollection> {
        val all = mutableListOf<BangumiUserCollection>()
        var offset = 0
        var page = 0
        while (page < maxPages) {
            val result = getUserCollections(token, username, subjectType, limit = 50, offset = offset)
            all += result.data
            if (!result.hasMore || result.data.isEmpty()) break
            offset = result.nextOffset
            page++
        }
        return all
    }

    /**
     * `GET /v0/users/{username}/collections/{subject_id}` — tek条目 koleksiyon kaydı.
     * `username` gerçek kullanıcı adı olmalıdır (`-` bu okuma ucunda 404 verir).
     * Kayıt yoksa `404` döner ve bu fonksiyon **null** verir (hata sayılmaz).
     */
    suspend fun getUserCollection(
        token: String?,
        subjectId: Int,
        username: String
    ): BangumiUserCollection? = withContext(Dispatchers.IO) {
        try {
            val body = get(
                "/v0/users/${URLEncoder.encode(username, "UTF-8")}/collections/$subjectId",
                token = token
            ) ?: return@withContext null
            parseUserCollection(JSONObject(body))
        } catch (e: BangumiApiException) {
            if (e.isNotFound) null else throw e
        }
    }

    /**
     * `POST /v0/users/-/collections/{subject_id}` — koleksiyon kaydı **yoksa oluşturur,
     * varsa günceller** (upsert). Başarıda 204 döner.
     *
     * ÖNEMLİ: `ep_status` / `vol_status` alanları resmî sözleşmede **yalnızca书籍 (kit)
     *条目'ları** için tanımlıdır.动画 ilerlemesi için [markEpisodes] (bölüm bazlı
     * "打格子") kullanılmalıdır; sunucu条目 tamamlanma oranını bölümlerden yeniden hesaplar.
     *
     * @param type       [CollectionType]; null ise durum değiştirilmez
     * @param rate       0..10; `0` puanı siler
     * @param epStatus   yalnızca kitap条目'ları için anlamlı
     * @param tags       null/boş → yok sayılır; `emptyList()` → tüm etiketleri siler
     */
    suspend fun upsertCollection(
        token: String,
        subjectId: Int,
        type: Int? = null,
        rate: Int? = null,
        epStatus: Int? = null,
        volStatus: Int? = null,
        comment: String? = null,
        private: Boolean? = null,
        tags: List<String>? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            type?.let { put("type", it) }
            rate?.let { put("rate", it.coerceIn(0, 10)) }
            epStatus?.let { put("ep_status", it.coerceAtLeast(0)) }
            volStatus?.let { put("vol_status", it.coerceAtLeast(0)) }
            comment?.let { put("comment", it) }
            private?.let { put("private", it) }
            tags?.let { list -> put("tags", JSONArray(list)) }
        }
        runCatching {
            send("POST", "/v0/users/-/collections/$subjectId", payload, token)
            true
        }.getOrElse { error ->
            Log.e(TAG, "upsertCollection($subjectId) failed: ${error.message}")
            false
        }
    }

    /** `PATCH /v0/users/-/collections/{subject_id}` — yalnızca var olan kaydı günceller. */
    suspend fun patchCollection(
        token: String,
        subjectId: Int,
        type: Int? = null,
        rate: Int? = null,
        epStatus: Int? = null,
        volStatus: Int? = null,
        comment: String? = null,
        private: Boolean? = null,
        tags: List<String>? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            type?.let { put("type", it) }
            rate?.let { put("rate", it.coerceIn(0, 10)) }
            epStatus?.let { put("ep_status", it.coerceAtLeast(0)) }
            volStatus?.let { put("vol_status", it.coerceAtLeast(0)) }
            comment?.let { put("comment", it) }
            private?.let { put("private", it) }
            tags?.let { list -> put("tags", JSONArray(list)) }
        }
        runCatching {
            send("PATCH", "/v0/users/-/collections/$subjectId", payload, token)
            true
        }.getOrElse { error ->
            if (error is BangumiApiException && error.isNotFound) {
                // Kayıt henüz yok → PATCH 404 verir; çağıran taraf POST'a düşebilir.
                Log.i(TAG, "patchCollection($subjectId): kayıt yok (404)")
            } else {
                Log.e(TAG, "patchCollection($subjectId) failed: ${error.message}")
            }
            false
        }
    }

    /**
     * `PATCH /v0/users/-/collections/{subject_id}/episodes` — bölümleri toplu işaretler
     * ve条目 tamamlanma oranını yeniden hesaplar.动画 ilerlemesinin **doğru** yolu budur.
     *
     * @param episodeIds Bangumi bölüm ID'leri (`Episode.id`, sıralama numarası DEĞİL)
     */
    suspend fun markEpisodes(
        token: String,
        subjectId: Int,
        episodeIds: List<Int>,
        type: Int = EpisodeCollectionType.DONE
    ): Boolean = withContext(Dispatchers.IO) {
        if (episodeIds.isEmpty()) return@withContext true
        val payload = JSONObject().apply {
            put("episode_id", JSONArray(episodeIds))
            put("type", type)
        }
        runCatching {
            send("PATCH", "/v0/users/-/collections/$subjectId/episodes", payload, token)
            true
        }.getOrElse { error ->
            Log.e(TAG, "markEpisodes($subjectId, ${episodeIds.size} ep) failed: ${error.message}")
            false
        }
    }

    /**
     * İlerlemeyi "N. bölüme kadar izlendi" biçiminde yazar.
     *
     * Bangumi'de动画条目'larında `ep_status` doğrudan kullanılamadığı için:
     *  1. ana hikâye bölümleri çekilir,
     *  2. `sort <= watchedCount` olanların ID'leri toplanır,
     *  3. [markEpisodes] ile tek istekte işaretlenir,
     *  4. kitap条目'larında ise doğrudan `ep_status` PATCH'lenir.
     *
     * @return başarılı bölüm işaretleme sayısı (kitaplarda `epStatus`)
     */
    suspend fun setProgress(
        token: String,
        subject: BangumiSubject,
        watchedCount: Int
    ): Boolean {
        if (watchedCount <= 0) return true
        if (subject.isBook) {
            return patchCollection(token, subject.id, epStatus = watchedCount)
        }
        val episodes = getAllMainEpisodes(subject.id, token)
        if (episodes.isEmpty()) {
            // Bölüm listesi boşsa (henüz wiki girişi yok) son çare olarak ep_status dene.
            return patchCollection(token, subject.id, epStatus = watchedCount)
        }
        val targetIds = episodes
            .filter { it.isMainStory }
            .sortedBy { it.sort }
            .take(watchedCount)
            .map { it.id }
        return markEpisodes(token, subject.id, targetIds)
    }

    /** `GET /v0/users/-/collections/{subject_id}/episodes` — bölüm bazlı işaret durumları. */
    suspend fun getUserEpisodeCollections(
        token: String,
        subjectId: Int,
        limit: Int = 1000,
        offset: Int = 0
    ): List<BangumiUserEpisodeCollection> = withContext(Dispatchers.IO) {
        runCatching {
            val query = linkedMapOf(
                "limit" to limit.coerceIn(1, 1000).toString(),
                "offset" to offset.toString()
            )
            val body = get("/v0/users/-/collections/$subjectId/episodes", query, token)
                ?: return@runCatching emptyList<BangumiUserEpisodeCollection>()
            val array = JSONObject(body).optJSONArray("data") ?: JSONArray()
            (0 until array.length()).mapNotNull { i ->
                val o = array.optJSONObject(i) ?: return@mapNotNull null
                BangumiUserEpisodeCollection(
                    episodeId = o.optInt("episode_id", o.optInt("id", 0)),
                    type = o.optInt("type", EpisodeCollectionType.NONE),
                    episode = o.optJSONObject("episode")?.let { parseEpisode(it, subjectId) }
                )
            }
        }.getOrElse { error ->
            Log.w(TAG, "getUserEpisodeCollections($subjectId) failed: ${error.message}")
            emptyList()
        }
    }

    /** `PUT /v0/users/-/collections/-/episodes/{episode_id}` — tek bölüm işaretle. */
    suspend fun putEpisodeCollection(
        token: String,
        episodeId: Int,
        type: Int = EpisodeCollectionType.DONE
    ): Boolean = withContext(Dispatchers.IO) {
        val payload = JSONObject().put("type", type)
        runCatching {
            send("PUT", "/v0/users/-/collections/-/episodes/$episodeId", payload, token)
            true
        }.getOrElse { false }
    }

    // ── Katalog (目录) — "liste" özelliği için ───────────────────────────────

    /**
     * `POST /v0/indices` — yeni bir kullanıcı kataloğu (bangumi "目录") oluşturur.
     * Kitsugi'nin özel listeleri Bangumi tarafına taşınmak istenirse bu uç kullanılır.
     */
    suspend fun createIndex(token: String, title: String, description: String? = null): Int? =
        withContext(Dispatchers.IO) {
            runCatching {
                val payload = JSONObject().apply {
                    put("title", title)
                    description?.let { put("desc", it) }
                }
                val body = send("POST", "/v0/indices", payload, token) ?: return@runCatching null
                JSONObject(body).optInt("id", 0).takeIf { it > 0 }
            }.getOrElse { error ->
                Log.w(TAG, "createIndex failed: ${error.message}")
                null
            }
        }

    /**
     * `GET /v0/indices/{id}/subjects` — katalog içeriği.
     * Yanıt satırları katalog girdisidir;条目 verisi `subject` alt nesnesinde gelir.
     */
    suspend fun getIndexSubjects(
        indexId: Int,
        token: String? = null,
        subjectType: Int? = null,
        limit: Int = 50,
        offset: Int = 0
    ): BangumiPage<BangumiSubject> = withContext(Dispatchers.IO) {
        runCatching {
            val query = linkedMapOf(
                "type" to subjectType?.toString(),
                "limit" to limit.toString(),
                "offset" to offset.toString()
            )
            val body = get("/v0/indices/$indexId/subjects", query, token)
                ?: return@runCatching BangumiPage<BangumiSubject>(0, limit, offset, emptyList())
            val json = JSONObject(body)
            val array = json.optJSONArray("data") ?: JSONArray()
            val items = (0 until array.length()).mapNotNull { i ->
                val row = array.optJSONObject(i) ?: return@mapNotNull null
                parseSubjectOrNull(row.optJSONObject("subject") ?: row)
            }
            BangumiPage(
                total = json.optInt("total", items.size),
                limit = json.optInt("limit", limit),
                offset = json.optInt("offset", offset),
                data = items
            )
        }.getOrElse { BangumiPage(0, limit, offset, emptyList()) }
    }

    /** `POST /v0/indices/{id}/subjects` — kataloğa条目 ekler. */
    suspend fun addSubjectToIndex(token: String, indexId: Int, subjectId: Int, sort: Int? = null): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val payload = JSONObject().apply {
                    put("subject_id", subjectId)
                    sort?.let { put("sort", it) }
                }
                send("POST", "/v0/indices/$indexId/subjects", payload, token)
                true
            }.getOrElse { false }
        }

    // ── Ayrıştırıcılar ───────────────────────────────────────────────────────

    private fun parseSubjectPage(json: JSONObject, fallbackLimit: Int, fallbackOffset: Int):
        BangumiPage<BangumiSubject> {
        val array = json.optJSONArray("data") ?: JSONArray()
        return BangumiPage(
            total = json.optInt("total", array.length()),
            limit = json.optInt("limit", fallbackLimit),
            offset = json.optInt("offset", fallbackOffset),
            data = (0 until array.length()).mapNotNull { parseSubjectOrNull(array.optJSONObject(it)) }
        )
    }

    /** Karakter/kişi arama sayfasını çözer (`Paged_Character` / `Paged_Person`). */
    private fun parseEntityPage(json: JSONObject, fallbackLimit: Int, fallbackOffset: Int):
        BangumiPage<BangumiEntity> {
        val array = json.optJSONArray("data") ?: JSONArray()
        val items = mutableListOf<BangumiEntity>()
        for (index in 0 until array.length()) {
            val obj = array.optJSONObject(index) ?: continue
            val id = obj.optInt("id", 0)
            if (id <= 0) continue
            val careerArray = obj.optJSONArray("career")
            val career = if (careerArray == null) emptyList() else
                (0 until careerArray.length()).mapNotNull { careerArray.optString(it).takeIf(String::isNotBlank) }
            items.add(
                BangumiEntity(
                    id = id,
                    name = obj.optString("name", ""),
                    type = obj.optInt("type", 0),
                    career = career,
                    summary = obj.optString("summary", "").ifBlank {
                        obj.optString("short_summary", "").takeIf { it.isNotBlank() }
                    },
                    images = obj.optJSONObject("images")?.let { parseImages(it) }
                )
            )
        }
        return BangumiPage(
            total = json.optInt("total", items.size),
            limit = json.optInt("limit", fallbackLimit),
            offset = json.optInt("offset", fallbackOffset),
            data = items
        )
    }

    /** `id` alanı geçersizse null döner (eksik/bozuk satırlar listeyi bozmasın). */
    private fun parseSubjectOrNull(json: JSONObject?): BangumiSubject? {
        if (json == null) return null
        if (json.optInt("id", 0) <= 0) return null
        return parseSubject(json)
    }

    private fun parseSlimArray(array: JSONArray): List<BangumiSlimSubject> =
        (0 until array.length()).mapNotNull { i -> parseSlimSubject(array.optJSONObject(i)) }

    private fun parseSlimSubject(json: JSONObject?): BangumiSlimSubject? {
        if (json == null) return null
        val id = json.optInt("id", 0)
        if (id <= 0) return null
        val rating = json.optJSONObject("rating")
        return BangumiSlimSubject(
            id = id,
            type = json.optInt("type", SubjectType.ANIME),
            name = json.optString("name"),
            nameCn = json.optString("name_cn"),
            shortSummary = json.optString("short_summary", json.optString("summary")),
            date = json.optString("date").ifBlank { null },
            eps = json.optInt("eps", 0),
            volumes = json.optInt("volumes", 0),
            images = parseImages(json.optJSONObject("images")),
            score = rating?.optDouble("score", 0.0) ?: json.optDouble("score", 0.0),
            rank = rating?.optInt("rank", 0) ?: json.optInt("rank", 0),
            collectionTotal = json.optInt("collection_total", 0),
            tags = parseTagNames(json.optJSONArray("tags"))
        )
    }

    internal fun parseSubject(json: JSONObject): BangumiSubject {
        val ratingJson = json.optJSONObject("rating")
        val counts = mutableMapOf<Int, Int>()
        ratingJson?.optJSONObject("count")?.let { countJson ->
            countJson.keys().forEach { key ->
                key.toIntOrNull()?.let { counts[it] = countJson.optInt(key, 0) }
            }
        }
        val collectionJson = json.optJSONObject("collection")
        return BangumiSubject(
            id = json.optInt("id", 0),
            type = json.optInt("type", SubjectType.ANIME),
            name = json.optString("name"),
            nameCn = json.optString("name_cn"),
            summary = json.optString("summary"),
            date = json.cleanString("date").ifBlank { null },
            platform = json.cleanString("platform").ifBlank { null },
            nsfw = json.optBoolean("nsfw", false),
            locked = json.optBoolean("locked", false),
            eps = json.optInt("eps", 0),
            totalEpisodes = json.optInt("total_episodes", json.optInt("eps", 0)),
            volumes = json.optInt("volumes", 0),
            images = parseImages(json.optJSONObject("images")),
            rating = ratingJson?.let {
                BangumiRating(
                    rank = it.optInt("rank", 0),
                    total = it.optInt("total", 0),
                    score = it.optDouble("score", 0.0),
                    count = counts
                )
            },
            collection = collectionJson?.let {
                BangumiCollectionCounts(
                    wish = it.optInt("wish", 0),
                    collect = it.optInt("collect", 0),
                    doing = it.optInt("doing", 0),
                    onHold = it.optInt("on_hold", 0),
                    dropped = it.optInt("dropped", 0)
                )
            },
            tags = parseTagNames(json.optJSONArray("tags")),
            metaTags = json.optJSONArray("meta_tags")?.let { array ->
                (0 until array.length()).mapNotNull { array.optString(it).ifBlank { null } }
            } ?: emptyList(),
            infobox = parseInfobox(json.optJSONArray("infobox"))
        )
    }

    /**
     * `infobox` wiki alanları `[{ "key": "别名", "value": [...] | "v": "..." }]` biçimindedir.
     * Türkçe/İngilizce başlık eşleştirmesi ve "diğer adlar" için kullanışlıdır.
     */
    private fun parseInfobox(array: JSONArray?): Map<String, List<String>> {
        if (array == null) return emptyMap()
        val result = linkedMapOf<String, MutableList<String>>()
        for (i in 0 until array.length()) {
            val entry = array.optJSONObject(i) ?: continue
            val key = entry.optString("key")
            if (key.isBlank()) continue
            val values = mutableListOf<String>()
            entry.optJSONArray("value")?.let { valueArray ->
                for (j in 0 until valueArray.length()) {
                    val item = valueArray.optJSONObject(j)
                    val text = if (item != null) {
                        item.optString("v").ifBlank { item.optString("value") }
                    } else {
                        valueArray.optString(j)
                    }
                    if (text.isNotBlank()) values += text
                }
            }
            if (values.isEmpty()) {
                val single = entry.optString("value")
                if (single.isNotBlank()) values += single
            }
            if (values.isNotEmpty()) result[key] = values
        }
        return result
    }

    private fun parseEpisode(json: JSONObject?, fallbackSubjectId: Int?): BangumiEpisode? {
        if (json == null) return null
        val id = json.optInt("id", 0)
        if (id <= 0) return null
        return BangumiEpisode(
            id = id,
            subjectId = json.optInt("subject_id", 0).takeIf { it > 0 } ?: fallbackSubjectId,
            type = json.optInt("type", 0),
            name = json.optString("name"),
            nameCn = json.optString("name_cn"),
            sort = json.optDouble("sort", 0.0),
            ep = json.optDouble("ep", Double.NaN).takeIf { !it.isNaN() },
            airdate = json.optString("airdate").ifBlank { null },
            duration = json.optString("duration").ifBlank { null },
            durationSeconds = json.optInt("duration_seconds", 0),
            desc = json.optString("desc").ifBlank { null }
        )
    }

    /** Android `optString` JSON `null` için "null" metni döndürür; bunu boş metne çevirir. */
    private fun JSONObject.cleanString(key: String): String =
        if (isNull(key)) "" else optString(key, "")

    private fun parseImages(json: JSONObject?): BangumiImages? {
        if (json == null) return null
        val large = json.optString("large").ifBlank { null }
        val common = json.optString("common").ifBlank { null }
        val medium = json.optString("medium").ifBlank { null }
        val small = json.optString("small").ifBlank { null }
        val grid = json.optString("grid").ifBlank { null }
        if (listOfNotNull(large, common, medium, small, grid).isEmpty()) return null
        return BangumiImages(large, common, medium, small, grid)
    }

    private fun parseTagNames(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        return (0 until array.length()).mapNotNull { i ->
            val item = array.optJSONObject(i)
            val name = if (item != null) item.optString("name") else array.optString(i)
            name.ifBlank { null }
        }
    }

    private fun parseUserCollection(json: JSONObject?): BangumiUserCollection? {
        if (json == null) return null
        val subjectId = json.optInt("subject_id", 0)
        if (subjectId <= 0) return null
        return BangumiUserCollection(
            subjectId = subjectId,
            subjectType = json.optInt("subject_type", SubjectType.ANIME),
            type = json.optInt("type", CollectionType.WISH),
            rate = json.optInt("rate", 0),
            comment = json.optString("comment").ifBlank { null },
            tags = json.optJSONArray("tags")?.let { array ->
                (0 until array.length()).mapNotNull { array.optString(it).ifBlank { null } }
            } ?: emptyList(),
            epStatus = json.optInt("ep_status", 0),
            volStatus = json.optInt("vol_status", 0),
            private = json.optBoolean("private", false),
            updatedAt = json.optString("updated_at").ifBlank { null },
            subject = parseSlimSubject(json.optJSONObject("subject"))
        )
    }

    // ── Yardımcılar ──────────────────────────────────────────────────────────

    /** Görsel URL'lerini `https:` şemasına tamamlar (API bazen protokol-göreli dönebilir). */
    fun absoluteImageUrl(url: String?): String? {
        if (url.isNullOrBlank()) return null
        return when {
            url.startsWith("//") -> "https:$url"
            url.startsWith("/") -> "$SITE_BASE$url"
            // Eski (legacy) arama ucu görselleri `http://lain.bgm.tv/...` verir; Android düz HTTP
            // trafiğini engelleyebilir ve Bangumi HTTPS sunar → şemayı yükselt.
            url.startsWith("http://", ignoreCase = true) -> "https://" + url.substring(7)
            else -> url
        }
    }

    /**
     * Basit bağlantı denetimi. Ayarlar ekranındaki "Bağlantıyı Test Et" düğmesi için.
     * `api.bgm.tv` kökü 404 döndürür; bu yüzden 404 de "erişilebilir" sayılır.
     */
    suspend fun testConnection(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("$API_BASE/v0/subjects/1")
                .get()
                .bangumiHeaders(null)
                .build()
            httpClient.newCall(request).execute().use { response ->
                response.isSuccessful || response.code == 404 || response.code == 400
            }
        }.getOrElse { false }
    }
}
