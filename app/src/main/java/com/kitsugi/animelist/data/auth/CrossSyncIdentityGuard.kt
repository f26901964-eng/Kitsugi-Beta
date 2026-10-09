package com.kitsugi.animelist.data.auth

import com.kitsugi.animelist.data.remote.JikanGateway
import com.kitsugi.animelist.data.remote.JikanResult
import android.util.Log
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaIdentity
import com.kitsugi.animelist.model.MediaType
import okhttp3.Request
import org.json.JSONObject
import java.text.Normalizer
import java.util.Locale
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/**
 * Çapraz senkronizasyonda **yanlış içerik eklenmesine** karşı kimlik güvencesi.
 *
 * Sorun: Bir grubun MAL kimliği tek bir sağlayıcı eşlemesinden (Simkl `ids.mal`, Kitsu `mappings`,
 * AniList `idMal`) ya da ARM/Jikan çıkarımından geliyorsa ve bu eşleme hatalıysa, aynı yanlış kimlik
 * MAL, AniList, Kitsu, Shikimori ve Simkl'a "yeni kayıt" olarak yazılır — kullanıcı hiç eklemediği bir
 * anime/manga ile karşılaşır. Güncellemeler platformun kendi kayıt kimliğiyle yapıldığı için güvenlidir;
 * risk yalnızca EKLEME yolundadır.
 *
 * Kural: *Doğrulanamayan kimlikle asla yeni kayıt eklenmez.*
 *  - Grup içinde MAL/Shikimori kaynaklı bir kayıt varsa kimlik doğaldır → güvenilir.
 *  - En az iki farklı platform bağımsız olarak aynı MAL ID'yi bildiriyorsa → güvenilir.
 *  - Aksi halde MAL kataloğundan (resmi v2 API, yedek: Jikan) başlık + yıl çekilir ve yerel kayıtla
 *    karşılaştırılır. Başlık akrabalığı yoksa ya da yıl uyuşmuyorsa [Verdict.Rejected];
 *    katalog ulaşılamazsa [Verdict.Unverifiable] — her iki durumda da ekleme yapılmaz.
 */
object CrossSyncIdentityGuard {

    private const val TAG = "CrossSyncIdentityGuard"

    /** Kimliği "doğal olarak MAL" olan kaynaklar (kayıt kimliği = MAL ID). */
    val NATIVE_MAL_SOURCES: Set<String> = setOf("mal", "myanimelist", "shikimori")

    sealed class Verdict {
        /** Doğrulamaya gerek kalmadan güvenilir (doğal kaynak veya çoklu bağımsız kaynak). */
        data class Trusted(val reason: String) : Verdict()

        /** MAL kataloğu ile başlık ve yıl uyuştu. */
        data class Verified(val remoteTitle: String, val remoteYear: Int?) : Verdict()

        /** MAL kataloğundaki içerik yerel kayıtla uyuşmuyor — kimlik büyük olasılıkla yanlış. */
        data class Rejected(val reason: String, val remoteTitle: String?, val remoteYear: Int?) : Verdict()

        /** Katalog ulaşılamadı / yanıt vermedi; güvenli taraf: ekleme yapılmaz. */
        data class Unverifiable(val reason: String) : Verdict()

        val allowsAdditions: Boolean get() = this is Trusted || this is Verified

        fun describe(): String = when (this) {
            is Trusted -> "güvenilir ($reason)"
            is Verified -> "doğrulandı (MAL: \"$remoteTitle\"${remoteYear?.let { ", $it" } ?: ""})"
            is Rejected -> "REDDEDİLDİ ($reason)"
            is Unverifiable -> "doğrulanamadı ($reason)"
        }
    }

    /** MAL kataloğundan çekilen hafif kimlik bilgisi. */
    data class RemoteIdentity(
        val malId: Int,
        val titles: List<String>,
        val year: Int?,
        val mediaType: String?
    ) {
        val primaryTitle: String get() = titles.firstOrNull().orEmpty()
    }

    // ───────────────────────────── Başlık / yıl karşılaştırma ─────────────────────────────

    /**
     * Yapısal kelimeler: sezon/bölüm/film ekleri ve Japonca/İngilizce edatlar. Bunlar tek başına iki
     * başlığı "akraba" yapmaz (aksi halde her "Season 2" her "Movie" ile eşleşirdi).
     */
    private val structuralTokens = setOf(
        "the", "a", "an", "of", "and", "or", "in", "on", "at", "for", "with", "from", "by",
        "no", "wa", "ga", "to", "ni", "de", "wo", "o", "e", "ha", "mo", "ya", "da", "na", "ne", "yo", "ka",
        "x", "vs", "feat",
        "season", "seasons", "part", "parts", "cour", "arc", "chapter", "chapters", "volume",
        "movie", "movies", "film", "films", "gekijouban", "gekijou", "ban", "hen",
        "animation", "anime", "tv", "ova", "oad", "ona", "special", "specials", "series", "edition",
        "final", "first", "second", "third", "fourth", "fifth", "new", "shin", "zoku", "kai", "re", "remake",
        "1st", "2nd", "3rd", "4th", "5th", "6th", "nd", "rd", "th", "i", "ii", "iii", "iv", "v", "vi"
    )

    private val nonWordCharacters = Regex("[^\\p{L}\\p{N}]+")
    private val yearRegex = Regex("^(\\d{4})")

    /** Küçük harf + NFKC; noktalama boşluğa dönüşür, kelime sınırları korunur (tokenizasyon için). */
    fun tokenNormalize(title: String): String = Normalizer.normalize(title, Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT)
        .replace(nonWordCharacters, " ")
        .trim()

    /** Yapısal kelimeler ve çıplak sayılar atıldıktan sonra kalan ayırt edici kelimeler. */
    fun significantTokens(title: String): Set<String> = tokenNormalize(title)
        .split(' ')
        .asSequence()
        .filter { it.isNotBlank() }
        .filterNot { it in structuralTokens }
        .filterNot { tok -> tok.all { it.isDigit() } }
        .toSet()

    fun aliasesOf(entry: MediaEntry): List<String> =
        listOfNotNull(entry.title, entry.titleEnglish, entry.titleJapanese)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()

    /** Yıl bilinmiyorsa uyumlu sayılır; ikisi de biliniyorsa en fazla 1 yıl fark (sezon geçişleri, bölgesel tarihler). */
    fun yearsCompatible(a: Int?, b: Int?): Boolean = a == null || b == null || abs(a - b) <= 1

    /**
     * Güçlü akrabalık: normalize başlıklar eşit, biri diğerinin ön eki ya da (yeterince uzunsa) içinde.
     * CJK başlıklarda boşluk olmadığı için kelime bazlı karşılaştırma çalışmaz; bu kontrol onu da kapsar.
     */
    fun titlesStronglyRelated(a: String, b: String): Boolean {
        val na = MediaIdentity.normalizedTitle(a)
        val nb = MediaIdentity.normalizedTitle(b)
        if (na.isBlank() || nb.isBlank()) return false
        if (na == nb) return true
        val shorter = if (na.length <= nb.length) na else nb
        val longer = if (na.length <= nb.length) nb else na
        // CJK başlıklar çok kısa olabilir (ör. 鬼父); Latin alfabesinde en az 3, CJK'da en az 2 karakter aranır.
        val isCjk = shorter.any { it.code >= 0x2E80 }
        val minPrefixLength = if (isCjk) 2 else 3
        val minContainsLength = if (isCjk) 3 else 5
        if (shorter.length < minPrefixLength) return false
        return longer.startsWith(shorter) || (shorter.length >= minContainsLength && longer.contains(shorter))
    }

    /** Zayıf akrabalık: en az bir ayırt edici kelime ortak (ör. "Oni Chichi" ↔ "Oni Chichi: Re-born"). */
    fun titlesShareDistinctiveToken(a: String, b: String): Boolean {
        val ta = significantTokens(a)
        val tb = significantTokens(b)
        if (ta.isEmpty() || tb.isEmpty()) return false
        return ta.any { it in tb }
    }

    /**
     * İki alias kümesi akraba mı? [strict] = true ise yalnızca güçlü akrabalık kabul edilir
     * (yıl bilinmediğinde kullanılır: kelime ortaklığı tek başına yeterli kanıt değildir).
     */
    fun titlesLookRelated(a: Collection<String>, b: Collection<String>, strict: Boolean = false): Boolean {
        val left = a.filter { it.isNotBlank() }
        val right = b.filter { it.isNotBlank() }
        if (left.isEmpty() || right.isEmpty()) return false
        for (x in left) for (y in right) {
            if (titlesStronglyRelated(x, y)) return true
        }
        if (strict) return false
        for (x in left) for (y in right) {
            if (titlesShareDistinctiveToken(x, y)) return true
        }
        // Gevşek ama güvenli varyant kontrolü (yalnızca yıllar biliniyorken): çeviri/romanizasyon farkları
        // (Mayoiga ↔ Mayohiga, Onee-san ↔ Oneesan), sayısal başlığın İngilizce biçimi (86 ↔ 86: Eighty Six)
        // ve kelime eklemesi (86 Part 2 ↔ 86: Eighty Six Part 2). Sezon/bölüm numaraları birebir aynı olmalı.
        for (x in left) for (y in right) {
            if (titlesVariantRelated(x, y)) return true
        }
        return false
    }

    private fun rawTokens(title: String): List<String> =
        tokenNormalize(title).split(' ').filter { it.isNotBlank() }

    /** Sayısal kelimeler (sezon/bölüm numarası gibi): "Part 2" ↔ "Part 3" farkını yakalamak için. */
    private fun numericTokens(title: String): Set<String> =
        rawTokens(title).filter { tok -> tok.all { it.isDigit() } }.toSet()

    /** Yapısal kelimeler atılmış, boşluksuz karşılaştırma anahtarı; Hepburn romanizasyon farklarını katlar. */
    private fun compactKey(title: String): String = rawTokens(title)
        .filterNot { it in structuralTokens }
        .joinToString("")
        .replace("ou", "o")
        .replace("oo", "o")
        .replace("uu", "u")
        .replace("ei", "e")

    private fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        var prev = IntArray(b.length + 1) { it }
        var curr = IntArray(b.length + 1)
        for (i in 1..a.length) {
            curr[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                curr[j] = minOf(curr[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
            }
            val tmp = prev; prev = curr; curr = tmp
        }
        return prev[b.length]
    }

    /** Yalnızca varyant/çeviri farkı olan iki başlık (sayısal kimlikler aynı olmak zorunda). */
    fun titlesVariantRelated(a: String, b: String): Boolean {
        if (numericTokens(a) != numericTokens(b)) return false
        val ka = compactKey(a)
        val kb = compactKey(b)
        if (ka.isEmpty() || kb.isEmpty()) return false

        // (1) Sayısal başlık + İngilizce açılımı: "86" ↔ "86 Eighty Six" (tüm kelimeler sırayla bulunmalı)
        val ta = rawTokens(a)
        val tb = rawTokens(b)
        val (shortT, longT) = if (ta.size <= tb.size) ta to tb else tb to ta
        if (shortT.isNotEmpty()) {
            var idx = 0
            for (tok in longT) {
                if (idx < shortT.size && tok == shortT[idx]) idx++
            }
            if (idx == shortT.size && (longT.size - shortT.size) <= 4) return true
        }

        // (2) Kısa romanizasyon/yazım farkı: en fazla 1 karakter ve uzunluğun %15'i kadar fark
        if (ka.length >= 6 && kb.length >= 6) {
            val dist = levenshtein(ka, kb)
            if (dist <= 1 || (dist <= 2 && dist.toDouble() / maxOf(ka.length, kb.length) <= 0.15)) return true
        }
        return false
    }

    /** Aynı sağlayıcı kimliğini paylaşan iki yerel kaydın başlıkları aynı eserin varyantı gibi görünüyor mu? */
    fun entriesLookRelated(a: MediaEntry, b: MediaEntry): Boolean {
        val strict = a.year == null || b.year == null
        return titlesLookRelated(aliasesOf(a), aliasesOf(b), strict = strict)
    }

    // ───────────────────────────── MAL kimlik doğrulama ─────────────────────────────

    private val identityCache = ConcurrentHashMap<String, Optional<RemoteIdentity>>()

    /**
     * Grubun MAL kimliğini kataloğa karşı doğrular. Ağ hatalarında istisna fırlatmaz; [Verdict.Unverifiable] döner.
     *
     * @param localTitles  gruptaki tüm kayıtların başlık/alias'ları
     * @param localYear    grupta bilinen yıl (varsa)
     */
    suspend fun verifyMalId(
        malId: Int,
        mediaType: MediaType,
        localTitles: Collection<String>,
        localYear: Int?
    ): Verdict {
        val titles = localTitles.filter { it.isNotBlank() }.distinct()
        if (titles.isEmpty()) return Verdict.Unverifiable("yerel kayıtta karşılaştırılacak başlık yok")

        val remote = fetchRemoteIdentity(malId, mediaType)
            ?: return Verdict.Unverifiable("MAL #$malId kataloğundan başlık alınamadı")
        if (remote.titles.isEmpty()) return Verdict.Unverifiable("MAL #$malId için başlık bilgisi boş")

        val yearOk = yearsCompatible(localYear, remote.year)
        val strict = localYear == null || remote.year == null
        val related = titlesLookRelated(titles, remote.titles, strict = strict)

        return when {
            related && yearOk -> Verdict.Verified(remote.primaryTitle, remote.year)
            !related -> Verdict.Rejected(
                reason = "MAL #$malId = \"${remote.primaryTitle}\"${remote.year?.let { " ($it)" } ?: ""}; " +
                    "yerel başlık \"${titles.first()}\" ile akraba değil",
                remoteTitle = remote.primaryTitle,
                remoteYear = remote.year
            )
            else -> Verdict.Rejected(
                reason = "MAL #$malId = \"${remote.primaryTitle}\" yılı ${remote.year} ↔ yerel yıl $localYear uyuşmuyor",
                remoteTitle = remote.primaryTitle,
                remoteYear = remote.year
            )
        }
    }

    /** Test/teşhis amaçlı: önbelleği boşaltır. */
    fun clearCache() = identityCache.clear()

    private suspend fun fetchRemoteIdentity(malId: Int, mediaType: MediaType): RemoteIdentity? {
        val endpoint = if (mediaType == MediaType.Manga) "manga" else "anime"
        val key = "$endpoint:$malId"
        identityCache[key]?.let { return it.orElse(null) }

        val fetched = fetchFromOfficialMal(malId, endpoint) ?: fetchFromJikan(malId, endpoint)
        // Yalnızca başarılı yanıtlar önbelleğe alınır; geçici ağ hataları bir sonraki grupta tekrar denensin.
        if (fetched != null) identityCache[key] = Optional.of(fetched)
        return fetched
    }

    private suspend fun fetchFromOfficialMal(malId: Int, endpoint: String): RemoteIdentity? {
        val clientId = runCatching { com.kitsugi.animelist.BuildConfig.MAL_CLIENT_ID }.getOrNull()
        if (clientId.isNullOrBlank()) return null
        PlatformRateLimiter.acquire("mal")
        val url = "https://api.myanimelist.net/v2/$endpoint/$malId?fields=id,title,alternative_titles,start_date,media_type"
        val request = Request.Builder()
            .url(url)
            .header("X-MAL-CLIENT-ID", clientId)
            .header("Accept", "application/json")
            .header("User-Agent", "KitsugiAnimeList/1.0")
            .build()
        return try {
            com.kitsugi.animelist.core.network.KitsugiHttpClient.client.newCall(request).execute().use { response ->
                if (response.code == 429) PlatformRateLimiter.notifyRateLimited("mal")
                if (!response.isSuccessful) {
                    Log.w(TAG, "official MAL identity HTTP ${response.code} for $endpoint/$malId")
                    return null
                }
                val text = response.body?.string()
                if (text.isNullOrBlank()) return null
                val json = JSONObject(text)
                val titles = mutableListOf<String>()
                json.optString("title").takeIf { it.isNotBlank() }?.let { titles += it }
                json.optJSONObject("alternative_titles")?.let { alt ->
                    alt.optString("en").takeIf { it.isNotBlank() }?.let { titles += it }
                    alt.optString("ja").takeIf { it.isNotBlank() }?.let { titles += it }
                    alt.optJSONArray("synonyms")?.let { arr ->
                        for (i in 0 until arr.length()) arr.optString(i).takeIf { it.isNotBlank() }?.let { titles += it }
                    }
                }
                RemoteIdentity(
                    malId = malId,
                    titles = titles.distinct(),
                    year = yearRegex.find(json.optString("start_date"))?.groupValues?.get(1)?.toIntOrNull(),
                    mediaType = json.optString("media_type").takeIf { it.isNotBlank() }
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "official MAL identity fetch failed for $endpoint/$malId: ${e.message}")
            null
        }
    }

    private suspend fun fetchFromJikan(malId: Int, endpoint: String): RemoteIdentity? {
        // Kimlik doğrulaması arka plan işi: JikanGateway'de BACKGROUND önceliği (kullanıcı
        // ekranlarının kotasını yemez) ve önbellek/tekilleştirme burada da geçerlidir.
        return try {
            val text = when (val r = JikanGateway.fetch(
                "https://api.jikan.moe/v4/$endpoint/$malId",
                JikanGateway.Priority.BACKGROUND
            )) {
                is JikanResult.Ok -> r.body
                else -> {
                    Log.w(TAG, "Jikan identity ${r.javaClass.simpleName} for $endpoint/$malId")
                    return null
                }
            }
            if (text.isBlank()) return null
            run {
                val data = JSONObject(text).optJSONObject("data") ?: return null
                val titles = mutableListOf<String>()
                data.optString("title").takeIf { it.isNotBlank() }?.let { titles += it }
                data.optString("title_english").takeIf { it.isNotBlank() }?.let { titles += it }
                data.optString("title_japanese").takeIf { it.isNotBlank() }?.let { titles += it }
                data.optJSONArray("titles")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        arr.optJSONObject(i)?.optString("title")?.takeIf { it.isNotBlank() }?.let { titles += it }
                    }
                }
                data.optJSONArray("title_synonyms")?.let { arr ->
                    for (i in 0 until arr.length()) arr.optString(i).takeIf { it.isNotBlank() }?.let { titles += it }
                }
                val year = data.optInt("year", 0).takeIf { it > 0 }
                    ?: yearRegex.find(data.optJSONObject("aired")?.optString("from") ?: data.optJSONObject("published")?.optString("from").orEmpty())
                        ?.groupValues?.get(1)?.toIntOrNull()
                RemoteIdentity(
                    malId = malId,
                    titles = titles.distinct(),
                    year = year,
                    mediaType = data.optString("type").takeIf { it.isNotBlank() }
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Jikan identity fetch failed for $endpoint/$malId: ${e.message}")
            null
        }
    }
}
