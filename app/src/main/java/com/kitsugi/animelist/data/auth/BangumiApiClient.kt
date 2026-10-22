package com.kitsugi.animelist.data.auth

import android.util.Log
import com.kitsugi.animelist.BuildConfig
import com.kitsugi.animelist.core.network.KitsugiHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import com.kitsugi.animelist.utils.KitsugiStrings
import com.kitsugi.animelist.R

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

    /** Kullanıcının elle (OAuth'suz) kalıcı access token üretebildiği sayfa. */
    const val PERSONAL_TOKEN_URL = "https://next.bgm.tv/demo/access-token"

    /**
     * Kişisel erişim anahtarı **oluşturma** sayfası (个人令牌).
     *
     * Anahtar süresi 7–365 gün arasında seçilir ve yalnızca bir kez gösterilir. Bu
     * anahtarlar OAuth yerine geçer: `Authorization: Bearer <token>` biçiminde
     * doğrudan kullanılır, yenileme akışı yoktur. `scope` alanı boş bırakıldığı için
     * koleksiyon yazma uçlarındaki kapsam denetimine takılmaz — kapsam kaynaklı 403
     * yaşayan kullanıcının çözüm yolu budur (bkz.
     * [com.kitsugi.animelist.data.auth.BangumiAuthManager.connectWithPersonalToken]).
     */
    const val ACCESS_TOKEN_CREATE_URL = "$PERSONAL_TOKEN_URL/create"

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

    /**
     * Koleksiyon (收藏) yazma uçlarının sunucu tarafı sınırları.
     * Kaynak: `bangumi/server` → `web/req/collection.go` → `Validate()`.
     */
    const val MAX_COLLECTION_TAGS = 10
    const val MAX_COLLECTION_COMMENT_CHARS = 380
    const val MIN_COLLECTION_TAG_CHARS = 2

    /**
     * [probeCollectionWrite] için kasten **var olmayan** yapıt kimliği.
     *
     * Yetki ara katmanları (`NeedLogin` → `NeedScope`) handler'dan önce çalıştığı için
     * bu kimlik, gerçek bir koleksiyon satırına dokunmadan "yazma kapısı açık mı?"
     * sorusunu kesin olarak yanıtlar: 404 = kapı açık, 403 = kapı kapalı.
     */
    private const val PROBE_SUBJECT_ID = 999_999_999

    /**
     * Gövdeli isteklerde sunucuya gidecek `Content-Type` değeri — **birebir** `application/json`.
     *
     * Bangumi sunucusu (`bangumi/server` → `web/req/content_type.go` → `req.JSON`) başlığı
     * ön-ek/parametre ayrıştırması yapmadan **tam dize eşitliğiyle** denetler; yalnızca
     * `application/json` ve `application/json; charset=UTF-8` (büyük harf) kabul edilir,
     * aksi hâlde **415 Unsupported Media Type** döner. Bu ara katman tüm koleksiyon yazma
     * rotalarında (`POST/PATCH /v0/users/-/collections/...`, `PUT .../episodes/{id}`,
     * `/v0/indices*`) `NeedLogin`/`NeedScope`'tan **önce** çalışır; `POST /v0/search/subjects`
     * ise bu ara katmanı kullanmaz (bu yüzden arama çalışırken yazma 415 alıyordu).
     *
     * OkHttp tuzağı: `String.toRequestBody(mediaType)` tipte charset yoksa kendisi
     * `; charset=utf-8` (küçük harf) ekler ve `BridgeInterceptor` elle konan `Content-Type`
     * başlığını gövdenin tipiyle **ezer** → sunucu `application/json; charset=utf-8` görür →
     * 415. Çözüm [buildJsonRequestBody]: gövde ham bayt olarak verilir, tip olduğu gibi kalır.
     * Mihon/Aniyomi de aynı sebeple gövdeyi tipsiz kurup başlığı elle `application/json` yapar.
     */
    const val JSON_CONTENT_TYPE = "application/json"

    private val JSON_MEDIA_TYPE = JSON_CONTENT_TYPE.toMediaType()

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

    /**
     * Bangumi istekleri **fail-fast** istemcisini kullanır (bkz. [KitsugiHttpClient.bangumiClient]):
     * 14 sn toplam çağrı tavanı, yeniden deneme yok. Eski `metadataClient` 20 sn × 3 deneme
     * yapıyordu; yavaş bir `next.bgm.tv` yanıtı tek sekme açılışını dakikalara taşıyordu.
     */
    private val httpClient get() = KitsugiHttpClient.bangumiClient

    /**
     * `api.bgm.tv` için **jeton kovalı** hız sınırlayıcı.
     *
     * Eski uygulama her isteğe 450 ms'lik bir "yer ayırma" (reservation) yazıyordu ve bekleme
     * `Thread.sleep` ile IO thread'ini kilitliyordu. Rezervasyon, iptal edilen / zaman aşımına
     * uğrayan isteklerde de ilerlediği için Keşfet gibi 14 Bangumi şeridini aynı anda açan
     * ekranlarda saat **dakikalarca** ileri taşınıyor; ardından gelen her Bangumi isteği
     * (detay, arama, kütüphane) kuyrukta bekliyordu. Kullanıcı raporu: "veri 5 dakika
     * gelmiyor". Ayrıca yüzlerce istek IO thread'lerini bloke edip diğer kaynakları da
     * yavaşlatıyordu.
     *
     * Yeni davranış: saniyede 5 istek (Bangumi'nin resmî tavanı 3000 istek / 10 dk = 5/sn)
     * ve 5 isteklik ani patlama (burst). Bekleyen istek `delay` kullanır → iptal edilirse
     * kredi harcanmaz ve kuyruk asla dakikalarca ileri gitmez.
     */
    private const val THROTTLE_REQUESTS_PER_SECOND = 5.0
    private const val THROTTLE_BURST = 5.0

    /**
     * Toplu/arka plan Bangumi işleri (kütüphane başlığı tazeleme, içe aktarma) için bağlam
     * işareti. Bu işaretle çalışan istekler jeton kovasından **en az 2 jeton varken** pay
     * alır; böylece kullanıcı bir detay sayfası/sekme açtığında arka plan taraması kalan
     * jetonları tüketmiş olmaz. İşaret yoksa istek etkileşimli sayılır.
     */
    object BackgroundTraffic : kotlin.coroutines.CoroutineContext.Element {
        override val key: kotlin.coroutines.CoroutineContext.Key<*> = Key
        object Key : kotlin.coroutines.CoroutineContext.Key<BackgroundTraffic>
    }

    /** [block] bloğunu arka plan önceliğinde çalıştırır (bkz. [BackgroundTraffic]). */
    suspend fun <T> runAsBackground(block: suspend () -> T): T =
        withContext(BackgroundTraffic) { block() }

    private fun monotonicNowMs(): Long = System.nanoTime() / 1_000_000L

    private val throttleMutex = Mutex()
    private var throttleTokens = THROTTLE_BURST
    private var throttleRefilledAt = monotonicNowMs()

    private suspend fun awaitBangumiThrottle() {
        val background = kotlin.coroutines.coroutineContext[BackgroundTraffic.Key] != null
        while (true) {
            val waitMs = throttleMutex.withLock {
                val now = monotonicNowMs()
                val elapsed = (now - throttleRefilledAt).coerceAtLeast(0L)
                if (elapsed > 0L) {
                    throttleTokens = (throttleTokens + elapsed * THROTTLE_REQUESTS_PER_SECOND / 1000.0)
                        .coerceAtMost(THROTTLE_BURST)
                    throttleRefilledAt = now
                }
                // Arka plan trafiği kovanın son jetonlarını almaz: etkileşimli istekler için
                // her zaman en az 1 jeton kalır. Yoksa Keşfet/Listem arka plan taraması
                // sürerken detay sekmeleri saniyelerce bekliyordu.
                val required = if (background) 2.0 else 1.0
                if (throttleTokens >= required) {
                    throttleTokens -= 1.0
                    0L
                } else {
                    (((required - throttleTokens) * 1000.0) / THROTTLE_REQUESTS_PER_SECOND)
                        .toLong()
                        .coerceAtLeast(16L)
                }
            }
            if (waitMs <= 0L) return
            delay(waitMs)
        }
    }

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
        val userGroup: Int? = null,
        /** Kişisel imza (`sign`); profil ekranında gösterilir. */
        val sign: String? = null
    )

    /**
     * Karakter veya kişi favorisi (`UserCharacterCollection` / `UserPersonCollection`).
     * Karakter `type`: 1 角色, 2 机体, 3 舰船, 4 组织. Kişi `type`: 1 个人, 2 公司, 3 组合.
     */
    data class BangumiFavoriteItem(
        val id: Int,
        val name: String,
        val type: Int,
        val imageUrl: String?,
        val createdAt: String?
    )

    data class BangumiImages(
        val large: String? = null,
        val common: String? = null,
        val medium: String? = null,
        val small: String? = null,
        val grid: String? = null
    ) {
        /** Kart/poster için en yüksek kaliteli dikey kapak tercih edilir; yoksa bir alttakine düşer. */
        val poster: String? get() = large ?: common ?: medium ?: grid ?: small
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
        /** Bazı arama yanıtlarında gelen Çince yerel ad (`name_cn` / `nameCN`). */
        val nameCn: String = "",
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
        /** Dil tercihi henüz uygulanmadığında güvenli varsayılan: özgün ad, yoksa Çince adı. */
        val displayTitle: String get() = name.ifBlank { nameCn }
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
        val tags: List<String>,
        val platform: String? = null
    ) {
        val displayTitle: String get() = name.ifBlank { nameCn }
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
        val displayTitle: String get() = name.ifBlank { nameCn }
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
        val displayTitle: String get() = name.ifBlank { nameCn }
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

        /**
         * 415 → sunucunun `req.JSON` ara katmanı `Content-Type` başlığını reddetti.
         * Bu bir **istemci (uygulama) hatasıdır**, token/kapsamla ilgisi yoktur — bkz. [JSON_CONTENT_TYPE].
         */
        val isUnsupportedMediaType: Boolean get() = code == 415
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
    private suspend fun execute(request: Request): String? {
        awaitBangumiThrottle()
        return withContext(Dispatchers.IO) {
            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    if (response.code == 204 || body.isBlank()) return@use null
                    return@use body
                }
                throw parseError(response.code, body)
            }
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

    private suspend fun get(path: String, query: Map<String, String?> = emptyMap(), token: String? = null): String? {
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

    /**
     * JSON gövdesini, telde **tam olarak** `Content-Type: application/json` üretecek biçimde kurar.
     *
     * `ByteArray.toRequestBody(mediaType)` tipi olduğu gibi taşır (charset eklemez); böylece
     * `BridgeInterceptor`'ın başlığa yazdığı `body.contentType().toString()` değeri de
     * [JSON_CONTENT_TYPE] ile birebir aynı olur ve sunucunun tam-eşitlik denetimini geçer.
     * **`String.toRequestBody(...)` bilerek kullanılmaz** — bkz. [JSON_CONTENT_TYPE].
     *
     * @param jsonBody `null` → boş gövde (yine de `application/json` tipli)
     */
    internal fun buildJsonRequestBody(jsonBody: JSONObject?): RequestBody {
        val bytes = (jsonBody?.toString() ?: "").toByteArray(Charsets.UTF_8)
        return bytes.toRequestBody(JSON_MEDIA_TYPE)
    }

    private suspend fun send(
        method: String,
        path: String,
        jsonBody: JSONObject?,
        token: String?,
        query: Map<String, String?> = emptyMap()
    ): String? {
        val body = buildJsonRequestBody(jsonBody)
        val request = Request.Builder()
            .url(buildUrl(path, query))
            .method(method, body)
            .bangumiHeaders(token)
            // Bilgilendirici: BridgeInterceptor bu başlığı body.contentType() ile yeniden yazar;
            // ikisi de JSON_CONTENT_TYPE olduğu için telde tam "application/json" gider.
            .header("Content-Type", JSON_CONTENT_TYPE)
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
        awaitBangumiThrottle()
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
        awaitBangumiThrottle()
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
        awaitBangumiThrottle()
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
                message = KitsugiStrings.get(R.string.bangumi_token_yanitinda_access_token_yok_x, json.optString("error"))
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

    /** `GET /v0/users/{username}/collections/-/characters` — kullanıcının karakter favorileri. */
    suspend fun getUserCharacterFavorites(token: String?, username: String): List<BangumiFavoriteItem> =
        withContext(Dispatchers.IO) {
            fetchFavoriteList("/v0/users/${URLEncoder.encode(username, "UTF-8")}/collections/-/characters", token)
        }

    /** `GET /v0/users/{username}/collections/-/persons` — kullanıcının kişi (kişi/stüdyo) favorileri. */
    suspend fun getUserPersonFavorites(token: String?, username: String): List<BangumiFavoriteItem> =
        withContext(Dispatchers.IO) {
            fetchFavoriteList("/v0/users/${URLEncoder.encode(username, "UTF-8")}/collections/-/persons", token)
        }

    private suspend fun fetchFavoriteList(path: String, token: String?): List<BangumiFavoriteItem> {
        val body = get(path, token = token) ?: return emptyList()
        val array = JSONObject(body).optJSONArray("data") ?: return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val id = item.optInt("id", 0)
            if (id <= 0) return@mapNotNull null
            BangumiFavoriteItem(
                id = id,
                name = item.optString("name").ifBlank { "#$id" },
                type = item.optInt("type", 0),
                imageUrl = parseImages(item.optJSONObject("images"))?.thumb,
                createdAt = item.optString("created_at").ifBlank { null }
            )
        }
    }

    private fun parseUser(json: JSONObject): BangumiUser {
        val avatar = json.optJSONObject("avatar")
        return BangumiUser(
            id = json.optLong("id", 0L),
            username = json.optString("username"),
            nickname = json.optString("nickname").ifBlank { json.optString("username") },
            avatarUrl = avatar?.optString("large")?.ifBlank { null }
                ?: avatar?.optString("medium")?.ifBlank { null },
            userGroup = json.optInt("user_group", 0).takeIf { it > 0 },
            sign = json.optString("sign").ifBlank { null }
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
     * @param type kayıt türü filtresi: `1` = 个人 (kişi), `2` = 公司 (şirket), `3` = 组合 (grup).
     *   Bangumi'de stüdyo/yapımcı/yayıncı ağlar ayrı bir kayıt türü değil, 人物 kayıtlarının
     *   `type = 2` alt kümesidir (bkz. `KitsugiStudioClient.fetchBangumiStudioDetail`). Bu
     *   yüzden kurum adı çözümleme bu uç üzerinden `type = 2` ile yapılır.
     */
    suspend fun searchPersons(
        keyword: String,
        token: String? = null,
        career: List<String> = emptyList(),
        type: Int? = null,
        limit: Int = 25,
        offset: Int = 0
    ): BangumiPage<BangumiEntity> = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("keyword", keyword)
            val filter = JSONObject().apply {
                if (career.isNotEmpty()) put("career", JSONArray(career))
                if (type != null) put("type", JSONArray(listOf(type)))
            }
            if (filter.length() > 0) put("filter", filter)
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
                        type = o.optInt("type", 0),
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

    /**
     * Ham bir JSON dizisi uç noktası (`/v0/subjects/{id}/characters` gibi). Ağ veya HTTP hatasında
     * `null` döner; çağıran taraf boş listeyle devam eder. Böylece tek bir ilişkili uç çökse bile
     * ana detay sayfası açılır.
     */
    suspend fun getJsonArrayOrNull(path: String, token: String? = null): JSONArray? = withContext(Dispatchers.IO) {
        runCatching {
            get(path, token = token)?.let { JSONArray(it) }
        }.getOrElse {
            Log.w(TAG, "GET $path failed: ${it.message}")
            null
        }
    }

    /**
     * `GET /v0/users/{username}/collections/-/characters/{id}` — karakter favori mi?
     * Kayıt yoksa (404) `false`. `username` olarak token sahibi için `-` kullanılabilir.
     */
    suspend fun isCharacterFavourite(token: String, username: String, characterId: Int): Boolean =
        isFavouriteEntity("characters", token, username, characterId)

    /** `GET /v0/users/{username}/collections/-/persons/{id}` — kişi favori mi? */
    suspend fun isPersonFavourite(token: String, username: String, personId: Int): Boolean =
        isFavouriteEntity("persons", token, username, personId)

    private suspend fun isFavouriteEntity(kind: String, token: String, username: String, id: Int): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val path = "/v0/users/${URLEncoder.encode(username, "UTF-8")}/collections/-/$kind/$id"
                get(path, token = token) != null
            } catch (e: BangumiApiException) {
                if (e.isNotFound) false else throw e
            }
        }

    /** `POST /v0/characters/{id}/collect` (favoriye ekle) veya `DELETE` (çıkar). Başarıda 204 döner. */
    suspend fun setCharacterFavourite(token: String, characterId: Int, favourite: Boolean) {
        setFavouriteEntity("characters", token, characterId, favourite)
    }

    /** `POST /v0/persons/{id}/collect` (favoriye ekle) veya `DELETE` (çıkar). */
    suspend fun setPersonFavourite(token: String, personId: Int, favourite: Boolean) {
        setFavouriteEntity("persons", token, personId, favourite)
    }

    private suspend fun setFavouriteEntity(kind: String, token: String, id: Int, favourite: Boolean) {
        withContext(Dispatchers.IO) {
            try {
                send(if (favourite) "POST" else "DELETE", "/v0/$kind/$id/collect", null, token)
            } catch (e: BangumiApiException) {
                // Zaten favori olmayan kaydı silmek istenmiş gibi davranmak: başarı sayılır.
                if (favourite || !e.isNotFound) throw e
            }
            Unit
        }
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
     * Koleksiyon yazma uçlarının ortak gövdesi.
     *
     * Sunucu sözleşmesi (`bangumi/server` → `web/req/collection.go`):
     *  - `type` 1..5 ([CollectionType]) — aralık dışıysa **400 "invalid type"**
     *  - `rate` 0..10 — üstü **400 "rate overflow"**
     *  - `tags` en fazla 10 adet, her biri en az 2 karakter ve `ValidateTag` süzgecinden
     *    geçmeli (zero-width / yazdırılamayan karakter / çift boşluk yasak)
     *  - `comment` en fazla 380 karakter, yazdırılamayan karakter yasak
     *  - `ep_status` / `vol_status` yalnızca kitap条目'ları için anlamlı
     *
     * Alanlardan biri bile kuralı çiğnerse **istek tamamen 400 döner** (kısmi yazma yok),
     * bu yüzden değerler burada da sunucu sınırlarına çekilir.
     */
    private fun buildCollectionPayload(
        type: Int?,
        rate: Int?,
        epStatus: Int?,
        volStatus: Int?,
        comment: String?,
        private: Boolean?,
        tags: List<String>?
    ): JSONObject = JSONObject().apply {
        type?.let { put("type", it) }
        rate?.let { put("rate", it.coerceIn(0, 10)) }
        epStatus?.let { put("ep_status", it.coerceAtLeast(0)) }
        volStatus?.let { put("vol_status", it.coerceAtLeast(0)) }
        comment?.let { put("comment", it) }
        private?.let { put("private", it) }
        tags?.let { list -> put("tags", JSONArray(list)) }
    }

    /**
     * `tags` alanını sunucu kurallarına uydurur (`web/req/collection.go` → `Validate`).
     *
     * Kural dışı **tek** etiket bütün koleksiyon isteğini 400 yaptığı için (ör. "TV" dışında
     * tek karakterli bir etiket ya da görünmez karakter) etiketler gönderilmeden önce
     * süzülür: NFKC normalleştirme, kırpma, boş/1 karakterli/zero-width/çift boşluklu
     * kayıtların atılması, tekilleştirme ve 10 kayıt sınırı.
     *
     * @return `null` → gövdeye `tags` hiç yazılmaz (uzaktaki etiketler **korunur**).
     *         Kitsugi'de "Bangumi etiketlerini temizle" diye bir niyet olmadığı için
     *         süzgeçten hiçbir etiket geçmezse alan tamamen atlanır.
     */
    internal fun sanitizeTagsForWrite(tags: List<String>?): List<String>? {
        if (tags == null) return null
        val cleaned = tags.asSequence()
            .map { java.text.Normalizer.normalize(it, java.text.Normalizer.Form.NFKC).trim() }
            .filter { isWritableTag(it) }
            .distinct()
            .take(MAX_COLLECTION_TAGS)
            .toList()
        // Süzgeçten hiçbir etiket geçmediyse alanı HİÇ gönderme: `"tags": []` sunucuda
        // mevcut etiketleri siler, oysa kullanıcının niyeti "etiket yaz"tır, "sil" değil.
        return cleaned.ifEmpty { null }
    }

    /**
     * `dam.ValidateTag` eşleniği: en az 2 rune, tamamı yazdırılabilir ve **boşluksuz**.
     * (Sunucu `ExtraSpacePattern = "[\u3000 ]"` ile etiketlerde boşluğu ve ideografik
     * boşluğu reddeder; `ZeroWidthPattern` de yalnız L/M/N/P/S/Z sınıflarını geçirir.)
     */
    private fun isWritableTag(tag: String): Boolean {
        if (tag.isEmpty()) return false
        var codePoints = 0
        var index = 0
        while (index < tag.length) {
            val cp = tag.codePointAt(index)
            index += Character.charCount(cp)
            codePoints++
            if (cp == ' '.code || cp == '\u3000'.code) return false
            if (!isServerPrintable(cp)) return false
        }
        return codePoints >= MIN_COLLECTION_TAG_CHARS
    }

    /**
     * `comment` alanını sunucu kurallarına uydurur: kırpılır, yazdırılamayan/zero-width
     * karakterler atılır ve 380 **kod noktasına** kırpılır (sunucu karakter sayısı yerine
     * rune sayar; Kotlin `String.take` UTF-16 birimi saydığı için emoji'li metinlerde
     * yanlış kesim yapardı).
     */
    internal fun sanitizeCommentForWrite(comment: String?): String? {
        if (comment == null) return null
        val builder = StringBuilder(comment.length)
        var index = 0
        while (index < comment.length) {
            val cp = comment.codePointAt(index)
            index += Character.charCount(cp)
            // \n, \t, \r sunucuda serbest (`dam.AllPrintableChar`); diğer yazdırılamayan
            // karakterler gövdenin tamamını 400 yapar.
            val allowed = cp == '\n'.code || cp == '\t'.code || cp == '\r'.code || isServerPrintable(cp)
            if (allowed) builder.appendCodePoint(cp)
        }
        val cleaned = builder.toString().trim()
        if (cleaned.isEmpty()) return null
        return cleaned.takeCodePoints(MAX_COLLECTION_COMMENT_CHARS)
    }

    /**
     * Sunucunun `dam.AllPrintableChar` eşleniği — Go `unicode.IsPrint`:
     * L, M, N, P, S sınıfları + ASCII boşluk. (Z sınıfının diğer üyeleri, kontrol ve
     * biçim karakterleri Go'da yazdırılabilir sayılmaz.)
     */
    private fun isServerPrintable(codePoint: Int): Boolean {
        if (codePoint == ' '.code) return true
        return when (Character.getType(codePoint)) {
            Character.UPPERCASE_LETTER.toInt(),
            Character.LOWERCASE_LETTER.toInt(),
            Character.TITLECASE_LETTER.toInt(),
            Character.MODIFIER_LETTER.toInt(),
            Character.OTHER_LETTER.toInt(),
            Character.NON_SPACING_MARK.toInt(),
            Character.COMBINING_SPACING_MARK.toInt(),
            Character.ENCLOSING_MARK.toInt(),
            Character.DECIMAL_DIGIT_NUMBER.toInt(),
            Character.LETTER_NUMBER.toInt(),
            Character.OTHER_NUMBER.toInt(),
            Character.CONNECTOR_PUNCTUATION.toInt(),
            Character.DASH_PUNCTUATION.toInt(),
            Character.START_PUNCTUATION.toInt(),
            Character.END_PUNCTUATION.toInt(),
            Character.INITIAL_QUOTE_PUNCTUATION.toInt(),
            Character.FINAL_QUOTE_PUNCTUATION.toInt(),
            Character.OTHER_PUNCTUATION.toInt(),
            Character.MATH_SYMBOL.toInt(),
            Character.CURRENCY_SYMBOL.toInt(),
            Character.MODIFIER_SYMBOL.toInt(),
            Character.OTHER_SYMBOL.toInt() -> true
            else -> false
        }
    }

    /** UTF-16 indekslerini bozmadan ilk `max` **kod noktasını** döndürür. */
    private fun String.takeCodePoints(max: Int): String {
        val count = codePointCount(0, length)
        if (count <= max) return this
        return substring(0, offsetByCodePoints(0, max))
    }

    /**
     * `POST /v0/users/-/collections/{subject_id}` — koleksiyon kaydı **yoksa oluşturur,
     * varsa günceller** (upsert). Başarıda sunucu **202** döner (API dokümanındaki "204"
     * güncel değildir — bkz. `bangumi/api` issue #212).
     *
     * Hata durumunda **istisna fırlatır** (401/403/404/429/5xx ve ağ hataları).
     * Eski sürüm hatayı yutup `false` döndürdüğü için arayüzde yalnızca
     * "Bilinmeyen hata" görünüyordu; artık gerçek neden çağırana ulaşır.
     *
     * ÖNEMLİ: `ep_status` / `vol_status` alanları resmî sözleşmede **yalnızca书籍 (kit)
     *条目'ları** için tanımlıdır.动画 ilerlemesi için [markEpisodes] (bölüm bazlı
     * "打格子") kullanılmalıdır; sunucu条目 tamamlanma oranını bölümlerden yeniden hesaplar.
     *
     * @param type       [CollectionType]; null ise durum değiştirilmez
     * @param rate       0..10; `0` puanı siler
     * @param epStatus   yalnızca kitap条目'ları için anlamlı
     * @param tags       null → etiketler korunur; `emptyList()` → tüm etiketleri siler
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
    ): Unit = withContext(Dispatchers.IO) {
        val payload = buildCollectionPayload(type, rate, epStatus, volStatus, comment, private, tags)
        send("POST", "/v0/users/-/collections/$subjectId", payload, token)
    }

    /**
     * `PATCH /v0/users/-/collections/{subject_id}` — yalnızca var olan kaydı günceller.
     *
     * Kayıt yoksa sunucu **404 "subject not collected"** döner; bu durum da istisna olarak
     * yükselir (`isNotFound` ile ayırt edilebilir). Hata yutulmaz.
     */
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
    ): Unit = withContext(Dispatchers.IO) {
        val payload = buildCollectionPayload(type, rate, epStatus, volStatus, comment, private, tags)
        send("PATCH", "/v0/users/-/collections/$subjectId", payload, token)
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
    ): Unit = withContext(Dispatchers.IO) {
        if (episodeIds.isEmpty()) return@withContext
        val payload = JSONObject().apply {
            put("episode_id", JSONArray(episodeIds))
            put("type", type)
        }
        send("PATCH", "/v0/users/-/collections/$subjectId/episodes", payload, token)
    }

    /**
     * İlerlemeyi "N. bölüme kadar izlendi" biçiminde yazar.
     *
     * Bangumi'de kitap dışı video subject'lerinde `ep_status` doğrudan kullanılamadığı için:
     *  1. ana hikâye bölümleri çekilir,
     *  2. `sort <= watchedCount` olanların ID'leri toplanır,
     *  3. [markEpisodes] ile tek istekte işaretlenir,
     *  4. kitap条目'larında ise doğrudan `ep_status` PATCH'lenir.
     *
     * @return bölüm listesi boş olduğunda `ep_status` yedeğinin kullanılıp kullanılmadığı
     */
    suspend fun setProgress(
        token: String,
        subject: BangumiSubject,
        watchedCount: Int
    ): Unit {
        if (watchedCount <= 0) return
        if (subject.isBook) {
            patchCollection(token, subject.id, epStatus = watchedCount)
            return
        }
        val episodes = getAllMainEpisodes(subject.id, token)
        if (episodes.isEmpty()) {
            // Bölüm listesi boşsa (henüz wiki girişi yok) son çare olarak ep_status dene.
            patchCollection(token, subject.id, epStatus = watchedCount)
            return
        }
        val targetIds = episodes
            .filter { it.isMainStory }
            .sortedBy { it.sort }
            .take(watchedCount)
            .map { it.id }
        markEpisodes(token, subject.id, targetIds)
    }

    // ── Yazma yetkisi tanılaması ─────────────────────────────────────────────

    /**
     * [probeCollectionWrite] sonucu: yazma kapısı açık mı, kapalıysa neden?
     *
     * Sınıflandırma **sunucunun yanıt gövdesine** dayanır; böylece 403'ün sebebi
     * ("kapsam yok" / "kullanıcı yasaklı" / "User-Agent reddi") birbirinden ayrılır.
     */
    enum class CollectionWriteProbe {
        /** Yazma kapısı AÇIK: kapsam denetimi geçildi (sunucu yapıtı bulamayıp 404 verdi). */
        WRITE_ALLOWED,

        /** 403 + "scope" → token'da `write:collection` kapsamı yok. Kalıcı; istemci düzeltemez. */
        SCOPE_DENIED,

        /** 401 → access token geçersiz veya süresi dolmuş. */
        UNAUTHORIZED,

        /** 403 ama sebep kapsam değil (yasaklı kullanıcı, reddedilen User-Agent, bölge engeli…). */
        FORBIDDEN_OTHER,

        /**
         * 415 → sunucu isteğin **biçimini** reddetti (`Content-Type` tam `application/json`
         * değil). Token ve kapsam denetimine hiç ulaşılmadı; bu **uygulama tarafı** bir
         * hatadır ve kullanıcı yeniden bağlanarak çözemez. Bkz. [JSON_CONTENT_TYPE].
         */
        REQUEST_REJECTED,

        /** Ağ hatası, 5xx, zaman aşımı ya da beklenmeyen yanıt. */
        FAILED
    }

    /** [probeCollectionWrite] ayrıntılı sonucu. */
    data class CollectionWriteProbeResult(
        val outcome: CollectionWriteProbe,
        val httpCode: Int? = null,
        /** Sunucunun ham `title`/`description` metni — arayüzde kaybolmasın. */
        val detail: String? = null
    )

    /**
     * Koleksiyon **yazma kapısını** kesin olarak ve **yan etkisiz** biçimde sınar.
     *
     * Yöntem: `PATCH /v0/users/-/collections/[PROBE_SUBJECT_ID]` gövdesi `{}`.
     *
     *  - `PATCH`, `POST` ile **aynı** `write:collection` kapsamını ister
     *    (`bangumi/server` → `web/routes.go:92-95`), ama `POST`'un aksine **kayıt
     *    oluşturmaz** (`allowCreate = false`).
     *  - Kimlik olarak [PROBE_SUBJECT_ID] (Bangumi'de var olmayan bir id) kullanılır:
     *    yetki denetimleri (`NeedLogin` → `NeedScope`) **handler'dan önce** çalıştığı
     *    için sonuç yapıtın varlığından bağımsızdır ve sunucu **hiçbir satıra dokunmaz**.
     *  - Gövde `{}` olduğundan hiçbir alan "set" sayılmaz; koleksiyon zamanı/etiketi
     *    değişmez, günlük kaydı üretilmez.
     *
     * Beklenen yanıtlar:
     *  - **404** (yapıt yok) → kapsam denetimi GEÇTİ, yazma yetkisi var → [CollectionWriteProbe.WRITE_ALLOWED]
     *  - **403 + "scope"** → kapsam yok → [CollectionWriteProbe.SCOPE_DENIED]
     *  - **401** → token geçersiz → [CollectionWriteProbe.UNAUTHORIZED]
     *  - **403 (başka sebep)** → [CollectionWriteProbe.FORBIDDEN_OTHER] (ham metin `detail`'de)
     *  - **415** → `Content-Type` reddedildi; token/kapsama hiç bakılmadı →
     *    [CollectionWriteProbe.REQUEST_REJECTED] (istemci hatası; bkz. [JSON_CONTENT_TYPE])
     *
     * Ara katman sırası `req.JSON` → `NeedLogin` → `NeedScope` olduğundan 415, 401/403'ten
     * **önce** gelir: 415 görülüyorsa token hakkında hiçbir şey söylenemez.
     */
    suspend fun probeCollectionWrite(token: String): CollectionWriteProbeResult =
        withContext(Dispatchers.IO) {
            try {
                send("PATCH", "/v0/users/-/collections/$PROBE_SUBJECT_ID", JSONObject(), token)
                // Buraya düşmek neredeyse imkânsız: id yok. Yine de yetki kapısı açık demektir.
                CollectionWriteProbeResult(CollectionWriteProbe.WRITE_ALLOWED, 200, null)
            } catch (error: BangumiApiException) {
                val detail = listOfNotNull(error.title, error.description)
                    .joinToString(" — ")
                    .ifBlank { error.message }
                val haystack = detail.lowercase()
                when {
                    // 415 en önce: req.JSON ara katmanı yetki denetimlerinden önce çalışır;
                    // bu yanıt token'ın durumunu değil, isteğin biçimini anlatır.
                    error.isUnsupportedMediaType -> CollectionWriteProbeResult(
                        CollectionWriteProbe.REQUEST_REJECTED, error.code, detail
                    )
                    error.isUnauthorized -> CollectionWriteProbeResult(
                        CollectionWriteProbe.UNAUTHORIZED, error.code, detail
                    )
                    error.isForbidden && haystack.contains("scope") -> CollectionWriteProbeResult(
                        CollectionWriteProbe.SCOPE_DENIED, error.code, detail
                    )
                    error.isForbidden -> CollectionWriteProbeResult(
                        CollectionWriteProbe.FORBIDDEN_OTHER, error.code, detail
                    )
                    // 404: kapsam denetimi geçildi (handler'a ulaşıldı, yapıt bulunamadı).
                    // Yanlış pozitife karşı koruma: gövde gerçekten Bangumi'nin yapıt
                    // hatasını içermeli ("subject not found"); çerçeve seviyesindeki genel
                    // bir 404 ("Not Found") yolun var olduğunu KANITLAMAZ.
                    error.isNotFound && haystack.contains("subject") -> CollectionWriteProbeResult(
                        CollectionWriteProbe.WRITE_ALLOWED, error.code, detail
                    )
                    else -> CollectionWriteProbeResult(CollectionWriteProbe.FAILED, error.code, detail)
                }
            } catch (error: Throwable) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                Log.w(TAG, "probeCollectionWrite failed: ${error.message}")
                CollectionWriteProbeResult(CollectionWriteProbe.FAILED, null, error.message)
            }
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
                    nameCn = obj.optString("name_cn", obj.optString("nameCN", "")),
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
            type = json.optInt("type", 0),
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
            tags = parseTagNames(json.optJSONArray("tags")),
            platform = json.cleanString("platform").ifBlank { null }
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
            type = json.optInt("type", 0),
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
     * `infobox` wiki alanları `[{ "key": "别名", "value": [{k,v}] | "v": "..." }]` biçimindedir.
     * Türkçe/İngilizce başlık eşleştirmesi ve "diğer adlar" için kullanışlıdır.
     *
     * Bangumi infobox'unu `alan adı → değerler` haritasına çevirir.
     *
     * ÖNEMLİ: `value` öğeleri `{k, v}` biçimindedir; `k` alt alan adıdır. Örneğin bir
     * karakterin infobox'ında üst anahtar `别名` olur, altında ise `英文名`, `罗马字`,
     * `日文名`, `纯假名` gibi **gerçek** alanlar bulunur. Eski kod yalnızca `v` değerini
     * `别名` altına yığıyor, `英文名`/`罗马字` alanlarını hiç görmüyordu; bu yüzden
     * İngilizce/romaji ad çoğu kayıtta "bulunamıyordu". Alt anahtarlar da üst anahtar gibi
     * haritaya yazılır (üst anahtar tüm değerleri korumaya devam eder).
     */
    fun parseInfobox(array: JSONArray?): Map<String, List<String>> {
        if (array == null) return emptyMap()
        val result = linkedMapOf<String, MutableList<String>>()

        fun add(key: String?, value: String?) {
            val cleanKey = key?.trim().orEmpty()
            val cleanValue = value?.trim().orEmpty()
            if (cleanKey.isEmpty() || cleanValue.isEmpty()) return
            val bucket = result.getOrPut(cleanKey) { mutableListOf() }
            if (!bucket.contains(cleanValue)) bucket += cleanValue
        }

        for (i in 0 until array.length()) {
            val entry = array.optJSONObject(i) ?: continue
            val key = entry.optString("key")
            if (key.isBlank()) continue
            var addedAny = false
            entry.optJSONArray("value")?.let { valueArray ->
                for (j in 0 until valueArray.length()) {
                    val item = valueArray.optJSONObject(j)
                    if (item != null) {
                        val text = item.optString("v").ifBlank { item.optString("value") }
                        if (text.isNotBlank()) {
                            add(key, text)
                            val subKey = item.optString("k")
                            if (subKey.isNotBlank() && !subKey.equals(key, ignoreCase = true)) {
                                add(subKey, text)
                            }
                            addedAny = true
                        }
                    } else {
                        val text = valueArray.optString(j)
                        if (text.isNotBlank()) {
                            add(key, text)
                            addedAny = true
                        }
                    }
                }
            }
            if (!addedAny) {
                val single = entry.optString("value")
                if (single.isNotBlank()) add(key, single)
            }
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
        val subjectJson = json.optJSONObject("subject")
        val subjectType = when {
            json.has("subject_type") -> json.optInt("subject_type", 0)
            subjectJson != null && subjectJson.has("type") -> subjectJson.optInt("type", 0)
            else -> 0
        }
        return BangumiUserCollection(
            subjectId = subjectId,
            subjectType = subjectType,
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
            subject = parseSlimSubject(subjectJson)
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
            awaitBangumiThrottle()
            httpClient.newCall(request).execute().use { response ->
                response.isSuccessful || response.code == 404 || response.code == 400
            }
        }.getOrElse { false }
    }
}
