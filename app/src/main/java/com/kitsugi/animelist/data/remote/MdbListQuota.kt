package com.kitsugi.animelist.data.remote

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import okhttp3.Headers
import org.json.JSONObject
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * MDBList günlük API kotasının **gerçek** durumu.
 *
 * MDBList her yanıtta `X-RateLimit-Limit`, `X-RateLimit-Remaining` ve `X-RateLimit-Reset`
 * başlıklarını döndürür; ayrıca `GET https://api.mdblist.com/user` uç noktası
 * `rate_limit`, `rate_limit_remaining`, `rate_limit_reset` alanlarıyla aynı değerleri
 * verir. Bu sınıf o değerleri taşır.
 *
 * Kotanın "kalan" değeri iki parçadan oluşur:
 *  - [serverRemaining]: sunucudan okunan son kesin değer,
 *  - [pendingRequests]: o okumadan bu yana **bu uygulamanın** yaptığı istek sayısı.
 *
 * Böylece sunucu başlığı dönmese bile (eski uç nokta) gösterilen sayı zamanla düşer;
 * başlık döndüğü anda değer yeniden kesinleşir. `X-RateLimit-*` gelmediği sürece
 * [authoritative] `false` kalır ve arayüz bunu "tahmini" olarak etiketler — böylece
 * 1.000/1.000 gibi sabit, yanıltıcı bir değer asla gösterilmez.
 */
data class MdbListQuota(
    /** Hesabın günlük istek tavanı (Free: 1.000, Supporter kademelerine göre artar). */
    val dailyLimit: Int,
    /** Sunucudan okunan son kesin kalan istek sayısı. */
    val serverRemaining: Int,
    /** O okumadan sonra bu uygulamanın yaptığı istekler. */
    val pendingRequests: Int = 0,
    /** Kotanın sıfırlanacağı an (Unix saniye, 00:00 UTC). */
    val resetAtEpochSeconds: Long,
    /** Son **sunucu** okumasının zamanı (sistem saati, ms). */
    val observedAtMillis: Long,
    /** Hesabın plan adı (`/user` yanıtından gelirse). */
    val plan: String? = null,
    /** Değer doğrudan MDBList sunucusundan mı okundu? */
    val authoritative: Boolean = true
) {
    /** Şu anki kalan istek sayısı (yerel istekler düşülmüş hâli). */
    val remaining: Int get() = (serverRemaining - pendingRequests).coerceAtLeast(0)

    /** Bugün tüketilen istek sayısı. */
    val used: Int get() = (dailyLimit - remaining).coerceAtLeast(0)

    /** Tüketim oranı (0 = boş, 1 = tamamen dolu). */
    val remainingFraction: Float get() =
        if (dailyLimit <= 0) 0f else (remaining.toFloat() / dailyLimit).coerceIn(0f, 1f)

    val isExhausted: Boolean get() = remaining <= 0

    /**
     * Değer hiç yerel tahmin içermiyor mu?
     * Sunucudan okunan değerden bu uygulama istek yaptıkça [pendingRequests] artar; o anda
     * sayı hâlâ sunucuya dayanır ama başka cihaz/uygulamaların tüketimini bilmez.
     */
    val isExact: Boolean get() = authoritative && pendingRequests == 0

    /**
     * Sıfırlanma anı geçtiyse kotayı yeni güne taşır: kalan = günlük limit.
     * Sunucudan yeni değer okunmadığı için [authoritative] `false` olur.
     */
    fun updatedFor(nowMillis: Long): MdbListQuota {
        val nowSeconds = nowMillis / 1000L
        if (resetAtEpochSeconds > nowSeconds) return this
        // observedAtMillis bilinçli olarak korunur: "son sunucu okuması" gerçeği değişmez,
        // böylece 15 dakikalık otomatik doğrulama gece devrinden hemen sonra çalışabilir.
        return copy(
            serverRemaining = dailyLimit,
            pendingRequests = 0,
            resetAtEpochSeconds = rollResetForward(resetAtEpochSeconds, nowMillis),
            authoritative = false
        )
    }

    /**
     * Bir istek daha yapıldı (sunucudan başlık gelmediğinde kullanılır).
     *
     * [observedAtMillis] bilinçli olarak **güncellenmez**: o alan son *sunucu* okumasının
     * zamanıdır. Böylece "son güncelleme" saati doğru kalır ve 15 dakikalık otomatik
     * yeniden doğrulama, yerel sayım yüzünden sürekli ertelenmez.
     */
    fun afterRequest(nowMillis: Long): MdbListQuota {
        val current = updatedFor(nowMillis)
        return current.copy(pendingRequests = current.pendingRequests + 1)
    }

    /** 429 alındı: kota kesin olarak doldu. */
    fun onRateLimited(nowMillis: Long, retryAfterSeconds: Long?): MdbListQuota {
        val reset = retryAfterSeconds
            ?.takeIf { it > 0 }
            ?.let { rollResetForward(nowMillis / 1000L + it, nowMillis) }
            ?: rollResetForward(resetAtEpochSeconds, nowMillis)
        return copy(
            serverRemaining = 0,
            pendingRequests = 0,
            resetAtEpochSeconds = reset,
            observedAtMillis = nowMillis,
            authoritative = true
        )
    }
}

/** UTC gece yarısını (MDBList'in sıfırlama anı) Unix saniye olarak döndürür. */
internal fun nextUtcMidnightEpochSeconds(nowMillis: Long): Long =
    ((nowMillis / 86_400_000L) + 1L) * 86_400L

/**
 * Sunucudan gelen sıfırlama değerini normalize eder.
 *
 * MDBList `X-RateLimit-Reset` alanını Unix saniye olarak belgeler; ancak vekil/proxy
 * katmanları bazen epoch-milis ya da "kalan saniye" biçiminde döndürebiliyor. Üç biçim
 * de doğru anlaşılır ve geçmişte kalan bir değer bir sonraki güne taşınır.
 */
internal fun normalizeResetEpochSeconds(raw: Long?, nowMillis: Long): Long {
    if (raw == null || raw <= 0L) return nextUtcMidnightEpochSeconds(nowMillis)
    val nowSeconds = nowMillis / 1000L
    val epochSeconds = when {
        raw >= 100_000_000_000L -> raw / 1000L   // epoch milis
        raw >= 1_000_000_000L -> raw             // epoch saniye
        raw <= 172_800L -> nowSeconds + raw      // "şu andan itibaren saniye" (en fazla 2 gün)
        else -> raw
    }
    return rollResetForward(epochSeconds, nowMillis)
}

/** Geçmişte kalmış bir sıfırlama anını, gelecekteki ilk eşdeğer ana taşır. */
internal fun rollResetForward(resetEpochSeconds: Long, nowMillis: Long): Long {
    val nowSeconds = nowMillis / 1000L
    if (resetEpochSeconds > nowSeconds) return resetEpochSeconds
    val elapsedDays = (nowSeconds - resetEpochSeconds) / 86_400L + 1L
    return resetEpochSeconds + elapsedDays * 86_400L
}

/**
 * MDBList yanıtlarından kotayı ayrıştırır. Ağ erişimi yoktur; birim testleriyle
 * doğrulanabilir (bkz. `MdbListQuotaTest`).
 */
internal object MdbListQuotaParser {

    private val limitHeaders = arrayOf("X-RateLimit-Limit", "RateLimit-Limit", "X-Rate-Limit-Limit")
    private val remainingHeaders = arrayOf("X-RateLimit-Remaining", "RateLimit-Remaining", "X-Rate-Limit-Remaining")
    private val resetHeaders = arrayOf("X-RateLimit-Reset", "RateLimit-Reset", "X-Rate-Limit-Reset")

    /** `X-RateLimit-*` başlıkları varsa kesin (authoritative) kota üretir. */
    fun fromHeaders(headers: Headers, nowMillis: Long): MdbListQuota? {
        val limit = headers.firstInt(*limitHeaders) ?: return null
        val remaining = headers.firstInt(*remainingHeaders) ?: return null
        if (limit <= 0 || remaining < 0) return null
        val reset = headers.firstLong(*resetHeaders)
        return MdbListQuota(
            dailyLimit = limit,
            serverRemaining = remaining.coerceAtMost(limit),
            resetAtEpochSeconds = normalizeResetEpochSeconds(reset, nowMillis),
            observedAtMillis = nowMillis,
            authoritative = true
        )
    }

    /** `GET https://api.mdblist.com/user` yanıtını ayrıştırır. */
    fun fromUserJson(json: String, nowMillis: Long): MdbListQuota? {
        return try {
            val root = JSONObject(json)
            val limit = root.optInt("rate_limit", 0).takeIf { it > 0 }
                ?: root.optInt("api_requests", 0).takeIf { it > 0 }
                ?: return null
            val remaining = when {
                root.has("rate_limit_remaining") && !root.isNull("rate_limit_remaining") ->
                    root.optInt("rate_limit_remaining", limit)
                root.has("api_requests_count") && !root.isNull("api_requests_count") ->
                    limit - root.optInt("api_requests_count", 0)
                else -> limit
            }.coerceIn(0, limit)
            val plan = root.optString("plan").trim()
                .takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }
            MdbListQuota(
                dailyLimit = limit,
                serverRemaining = remaining,
                resetAtEpochSeconds = normalizeResetEpochSeconds(
                    root.optLong("rate_limit_reset", 0L).takeIf { it > 0L },
                    nowMillis
                ),
                observedAtMillis = nowMillis,
                plan = plan,
                authoritative = true
            )
        } catch (e: Exception) {
            Log.w("MdbListQuota", "Kota JSON ayrıştırılamadı (${e.javaClass.simpleName})")
            null
        }
    }

    private fun Headers.firstInt(vararg names: String): Int? {
        for (name in names) {
            val raw = get(name)?.trim() ?: continue
            val value = raw.toIntOrNull() ?: raw.toDoubleOrNull()?.toInt() ?: continue
            return value
        }
        return null
    }

    private fun Headers.firstLong(vararg names: String): Long? {
        for (name in names) {
            val raw = get(name)?.trim() ?: continue
            val value = raw.toLongOrNull() ?: raw.toDoubleOrNull()?.toLong() ?: continue
            return value
        }
        return null
    }
}

/**
 * Son okunan kotayı API anahtarı bazında saklar.
 *
 * SharedPreferences erişimi başlatılmamışsa (birim testleri, önizleme, erken çağrı)
 * bellek içi harita kullanılır; bu sayede sınıf hiçbir koşulda çökmez.
 */
internal object MdbListQuotaStore {

    const val DEFAULT_DAILY_LIMIT = 1000

    private const val TAG = "MdbListQuota"
    private const val PREFS_NAME = "kitsugi_mdblist_quota"
    private const val KEY_LIMIT = "limit"
    private const val KEY_SERVER_REMAINING = "serverRemaining"
    private const val KEY_PENDING = "pending"
    private const val KEY_RESET = "reset"
    private const val KEY_OBSERVED_AT = "observedAt"
    private const val KEY_PLAN = "plan"
    private const val KEY_AUTHORITATIVE = "authoritative"

    @Volatile
    private var prefs: SharedPreferences? = null
    private val memory = ConcurrentHashMap<String, String>()

    fun initialize(context: Context) {
        if (prefs != null) return
        prefs = try {
            context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        } catch (t: Throwable) {
            Log.w(TAG, "Kota deposu açılamadı (${t.javaClass.simpleName})")
            null
        }
    }

    /** API anahtarının kendisi diske yazılmaz; yalnızca SHA-256 parmak izi kullanılır. */
    fun fingerprint(apiKey: String): String {
        val trimmed = apiKey.trim()
        return try {
            MessageDigest.getInstance("SHA-256")
                .digest(trimmed.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
        } catch (t: Throwable) {
            trimmed.hashCode().toString()
        }
    }

    fun load(apiKey: String, nowMillis: Long): MdbListQuota? {
        if (apiKey.isBlank()) return null
        val key = fingerprint(apiKey)
        val raw = try {
            prefs?.getString(key, null)
        } catch (t: Throwable) {
            null
        } ?: memory[key] ?: return null
        val quota = decode(raw) ?: return null
        return quota.updatedFor(nowMillis)
    }

    fun save(apiKey: String, quota: MdbListQuota) {
        if (apiKey.isBlank()) return
        val key = fingerprint(apiKey)
        val raw = encode(quota)
        memory[key] = raw
        try {
            prefs?.edit()?.putString(key, raw)?.apply()
        } catch (t: Throwable) {
            Log.w(TAG, "Kota kaydedilemedi (${t.javaClass.simpleName})")
        }
    }

    fun clear(apiKey: String) {
        val key = fingerprint(apiKey)
        memory.remove(key)
        try {
            prefs?.edit()?.remove(key)?.apply()
        } catch (_: Throwable) {
        }
    }

    /** Hiç sunucu değeri yoksa günlük limitten başlayan (authoritative olmayan) bir taban üretir. */
    fun base(apiKey: String, nowMillis: Long): MdbListQuota {
        val known = load(apiKey, nowMillis)
        if (known != null) return known
        val limit = DEFAULT_DAILY_LIMIT
        return MdbListQuota(
            dailyLimit = limit,
            serverRemaining = limit,
            pendingRequests = 0,
            resetAtEpochSeconds = nextUtcMidnightEpochSeconds(nowMillis),
            observedAtMillis = nowMillis,
            authoritative = false
        )
    }

    internal fun encode(quota: MdbListQuota): String = JSONObject().apply {
        put(KEY_LIMIT, quota.dailyLimit)
        put(KEY_SERVER_REMAINING, quota.serverRemaining)
        put(KEY_PENDING, quota.pendingRequests)
        put(KEY_RESET, quota.resetAtEpochSeconds)
        put(KEY_OBSERVED_AT, quota.observedAtMillis)
        put(KEY_PLAN, quota.plan ?: JSONObject.NULL)
        put(KEY_AUTHORITATIVE, quota.authoritative)
    }.toString()

    internal fun decode(raw: String): MdbListQuota? = try {
        val root = JSONObject(raw)
        val limit = root.optInt(KEY_LIMIT, 0)
        if (limit <= 0) null else MdbListQuota(
            dailyLimit = limit,
            serverRemaining = root.optInt(KEY_SERVER_REMAINING, limit).coerceIn(0, limit),
            pendingRequests = root.optInt(KEY_PENDING, 0).coerceAtLeast(0),
            resetAtEpochSeconds = root.optLong(KEY_RESET, 0L),
            observedAtMillis = root.optLong(KEY_OBSERVED_AT, 0L),
            plan = root.optString(KEY_PLAN).takeIf { it.isNotEmpty() && it != "null" },
            authoritative = root.optBoolean(KEY_AUTHORITATIVE, false)
        )
    } catch (e: Exception) {
        Log.w(TAG, "Kota kaydı okunamadı (${e.javaClass.simpleName})")
        null
    }
}
