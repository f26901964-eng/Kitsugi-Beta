package com.kitsugi.animelist.data.auth

import com.kitsugi.animelist.R
import com.kitsugi.animelist.utils.KitsugiStrings

import com.kitsugi.animelist.core.memory.BoundedCache

import android.util.Log
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaIdentity
import com.kitsugi.animelist.model.MediaType
import okhttp3.Request
import org.json.JSONObject
import java.text.Normalizer
import java.util.Locale


/**
 * Çapraz senkronizasyonda **yanlış içerik eklenmesine** karşı kimlik güvencesi.
 *
 * Sorun: Bir grubun MAL kimliği tek bir sağlayıcı eşlemesinden (Simkl `ids.mal`, Kitsu `mappings`,
 * AniList `idMal`) ya da ARM/Jikan çıkarımından geliyorsa ve bu eşleme hatalıysa, hem yeni kayıt
 * eklemek hem de hedef hesaptaki mevcut kayda durum/ilerleme/puan yazmak yanlış içeriği etkileyebilir.
 * Bu nedenle her çapraz hesaplı yazma yolu, yeni veya mevcut hedef olmasından bağımsız olarak hem
 * kaynak MAL kimliğini hem de hedef sağlayıcının native kaydını yeniden doğrulamalıdır.
 *
 * Kurallar:
 *  - Kaynağın veya birden çok sağlayıcının ID iddiası tek başına yeterli değildir.
 *  - Kaynak MAL kimliği, MAL'ın resmî kataloğunda medya türü, başlık ve bilinen yıl ile doğrulanır.
 *  - Hedef sağlayıcı ID'si kendi namespace'inde doğrulanır; Shikimori `target_id` MAL ID sayılmaz.
 *  - Uyuşmazlıkta, eksik yıl/başlıkta veya katalog erişilemediğinde mutasyon durur.
 */
object CrossSyncIdentityGuard {

    private const val TAG = "CrossSyncIdentityGuard"

    /** Gerçek kaynak kaydı kendi MAL ID'siyle tanımlanır; Shikimori ID'leri bu kümeye girmez. */
    val NATIVE_MAL_SOURCES: Set<String> = setOf("mal", "myanimelist")

    sealed class Verdict {
        /** Tanı/geriye dönük uyumluluk için tutulur; cross-account eklemeyi asla yetkilendirmez. */
        data class Trusted(val reason: String) : Verdict()

        /** MAL kataloğu ile başlık, tür ve yıl uyuştu; yazma aramalarında yalnız bu yerel alias'lar kullanılmalı. */
        data class Verified(
            val remoteTitle: String,
            val remoteYear: Int?,
            val matchedLocalTitles: List<String> = emptyList()
        ) : Verdict()

        /** MAL kataloğundaki içerik yerel kayıtla uyuşmuyor — kimlik büyük olasılıkla yanlış. */
        data class Rejected(val reason: String, val remoteTitle: String?, val remoteYear: Int?) : Verdict()

        /** Katalog ulaşılamadı / yanıt vermedi; güvenli taraf: ekleme yapılmaz. */
        data class Unverifiable(val reason: String) : Verdict()

        val allowsAdditions: Boolean get() = this is Verified

        fun describe(): String = when (this) {
            is Trusted -> KitsugiStrings.get(R.string.cross_sync_identity_trusted, reason)
            is Verified -> KitsugiStrings.get(
                R.string.cross_sync_identity_verified,
                remoteTitle,
                remoteYear?.let { KitsugiStrings.get(R.string.cross_sync_identity_year_suffix, it) }.orEmpty()
            )
            is Rejected -> KitsugiStrings.get(R.string.cross_sync_identity_rejected, reason)
            is Unverifiable -> KitsugiStrings.get(R.string.cross_sync_identity_unverifiable, reason)
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

    /** Görünen + İngilizce + özgün Japonca başlıklar (bkz. aşağıdaki not). */
    fun aliasesOf(entry: MediaEntry): List<String> =
        // titleJapanese ŞART: Bangumi satırlarında görünen başlık çoğu zaman Çince (name_cn),
        // İngilizce başlık da çoğu zaman yoktur; satırın taşıdığı tek ortak başlık özgün
        // Japonca addır. O dahil edilmediğinde MAL/AniList kimlik doğrulaması Bangumi
        // kökenli kayıtlarda neredeyse her zaman düşüyor ve canlı yayılım sessizce atlanıyordu.
        listOfNotNull(entry.title, entry.titleEnglish, entry.titleJapanese)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()

    /** Cross-sync writes require two known, positive, exactly matching release years. */
    fun yearsCompatible(a: Int?, b: Int?): Boolean = a != null && a > 0 && b != null && b > 0 && a == b

    /** A catalog result from another MAL media namespace can never authorize a write. */
    fun mediaTypesCompatible(local: MediaType, remoteType: String?): Boolean {
        val normalized = remoteType?.lowercase(Locale.ROOT)?.filter { it.isLetterOrDigit() } ?: return false
        return when (local) {
            MediaType.Manga -> normalized in setOf("manga", "manhwa", "manhua", "oneshot", "doujinshi", "novel", "lightnovel")
            MediaType.Anime -> normalized in setOf("tv", "movie", "ova", "ona", "special", "music", "pv", "cm", "anime")
            MediaType.Movie -> normalized == "movie"
            MediaType.TvShow -> normalized in setOf("tv", "series")
        }
    }

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

    /**
     * Cross-sync yazma/cluster kararı için katı başlık eşdeğerliği. Tek bir ortak kelime veya genel
     * prefix yeterli değildir; numaralı sezonlar/remake'ler de sayısal belirteçleri aynı olmalıdır.
     */
    fun titlesAreIdentityEquivalent(a: String, b: String): Boolean {
        val na = MediaIdentity.normalizedTitle(a)
        val nb = MediaIdentity.normalizedTitle(b)
        if (na.isBlank() || nb.isBlank()) return false
        if (na == nb) return true
        if (isKnown86TitleExpansion(na, nb)) return true
        if (numericTokens(a) != numericTokens(b)) return false

        // Keep every word, including articles and season/movie descriptors. Removing common
        // tokens can collapse distinct titles ("No Game No Life" vs "Game Life").
        // The only special spelling normalization is the audited Mayoiga/Mayohiga alias.
        fun strictCompactKey(value: String): String = rawTokens(value)
            .map { if (it == "mayohiga") "mayoiga" else it }
            .joinToString("")

        val ka = strictCompactKey(a)
        val kb = strictCompactKey(b)
        if (ka.isEmpty() || kb.isEmpty()) return false
        if (ka == kb) return true
        // One explicitly narrow romaji variant: an inserted intervocalic "h" (Mayoiga/Mayohiga).
        // General edit-distance matches are intentionally rejected for cross-account identity.
        val intervocalicH = Regex("(?<=[aeiou])h(?=[aeiou])")
        return intervocalicH.replace(ka, "") == intervocalicH.replace(kb, "")
    }

    private fun isKnown86TitleExpansion(a: String, b: String): Boolean {
        val marker = "86eightysix"
        fun collapseKnownExpansion(value: String): String =
            if (value.startsWith(marker)) "86${value.removePrefix(marker)}" else value
        return (a.startsWith(marker) || b.startsWith(marker)) && collapseKnownExpansion(a) == collapseKnownExpansion(b)
    }

    fun matchingLocalTitleAliases(localTitles: Collection<String>, remoteTitles: Collection<String>): List<String> {
        val remote = remoteTitles.filter { it.isNotBlank() }
        return localTitles.filter { local ->
            local.isNotBlank() && remote.any { title -> titlesAreIdentityEquivalent(local, title) }
        }.distinct()
    }

    fun titlesAreIdentityEquivalent(a: Collection<String>, b: Collection<String>): Boolean =
        matchingLocalTitleAliases(a, b).isNotEmpty()

    /** Same-ID cluster relation; unlike the UI/detail heuristic this never accepts one shared word. */
    fun entriesLookRelated(a: MediaEntry, b: MediaEntry): Boolean =
        titlesAreIdentityEquivalent(aliasesOf(a), aliasesOf(b))

    // ───────────────────────────── MAL kimlik doğrulama ─────────────────────────────

    private val identityCache = BoundedCache<String, RemoteIdentity>("crossSync.identity.officialMal.v2", 500)

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
        if (titles.isEmpty()) return Verdict.Unverifiable(KitsugiStrings.get(R.string.cross_sync_identity_no_local_title))

        val remote = fetchRemoteIdentity(malId, mediaType)
            ?: return Verdict.Unverifiable(KitsugiStrings.get(R.string.cross_sync_identity_remote_title_unavailable, malId))
        if (remote.titles.isEmpty()) return Verdict.Unverifiable(KitsugiStrings.get(R.string.cross_sync_identity_remote_empty_title, malId))

        val typeOk = mediaTypesCompatible(mediaType, remote.mediaType)
        val yearOk = yearsCompatible(localYear, remote.year)
        val matchedLocalTitles = matchingLocalTitleAliases(titles, remote.titles)
        val related = matchedLocalTitles.isNotEmpty()

        return when {
            !typeOk -> Verdict.Rejected(
                reason = KitsugiStrings.get(
                    R.string.cross_sync_identity_type_mismatch,
                    malId,
                    remote.mediaType ?: "unknown",
                    mediaType.name,
                    remote.primaryTitle
                ),
                remoteTitle = remote.primaryTitle,
                remoteYear = remote.year
            )
            related && yearOk -> Verdict.Verified(remote.primaryTitle, remote.year, matchedLocalTitles)
            !related -> Verdict.Rejected(
                reason = KitsugiStrings.get(
                    R.string.cross_sync_identity_title_mismatch,
                    malId,
                    remote.primaryTitle,
                    remote.year?.let { KitsugiStrings.get(R.string.cross_sync_identity_year_suffix, it) }.orEmpty(),
                    titles.first()
                ),
                remoteTitle = remote.primaryTitle,
                remoteYear = remote.year
            )
            localYear == null || localYear <= 0 || remote.year == null || remote.year <= 0 ->
                Verdict.Unverifiable(KitsugiStrings.get(R.string.cross_sync_identity_year_unavailable))
            else -> Verdict.Rejected(
                reason = KitsugiStrings.get(
                    R.string.cross_sync_identity_year_mismatch,
                    malId,
                    remote.primaryTitle,
                    remote.year ?: 0,
                    localYear ?: 0
                ),
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
        identityCache[key]?.let { return it }

        // Cross-account writes require the authoritative MAL catalog. Jikan is a useful metadata
        // mirror, but it is not strong enough to authorize an account mutation when MAL is down or
        // its client configuration is missing. Fail closed instead of substituting a second source.
        val fetched = fetchFromOfficialMal(malId, endpoint)
            ?.takeIf { it.titles.isNotEmpty() && !it.mediaType.isNullOrBlank() }
        // Cache successful official responses only; transient failures may be retried later.
        if (fetched != null) identityCache[key] = fetched
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
                if (json.optInt("id", 0) != malId) {
                    Log.w(TAG, "official MAL returned a different ID than requested for $endpoint/$malId")
                    return null
                }
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


}
