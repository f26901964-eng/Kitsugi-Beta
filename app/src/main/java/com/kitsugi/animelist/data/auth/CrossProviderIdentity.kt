package com.kitsugi.animelist.data.auth

import android.content.Context
import android.util.Log
import com.kitsugi.animelist.data.local.NetworkMonitor
import com.kitsugi.animelist.data.remote.BangumiIdNamespace
import com.kitsugi.animelist.data.remote.BangumiLocalMappingCache
import com.kitsugi.animelist.data.remote.KitsuIdNamespace
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaIdentity
import com.kitsugi.animelist.model.MediaType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * ALT KAYNAKLI ÇAPRAZ KİMLİK.
 *
 * Kitsugi'de bir satırın `malId` alanı **kendi kaynağının** kimliğini taşır (offset'li stableId).
 * Eski davranışta çapraz eşitleme yalnızca iki alanı okuyabiliyordu: gerçek MAL ID ve
 * `source == "anilist"` satırlarındaki AniList offset'i. Simkl (150M), Shikimori (400M) ve
 * Bangumi (500M) satırlarında kimlik hiç çözülemediği için Kitsu / Shikimori / Bangumi
 * eşitlemeleri **sessizce atlanıyordu**. Bu sınıf, satır hangi kaynaktan gelirse gelsin
 * altı kimliği birden (MAL, AniList, Kitsu, Shikimori, Bangumi, TMDB/Simkl) tek yerden çözer.
 *
 * @param malId gerçek MAL ID (1..99M) — MAL API'si ve Shikimori `target_id` bunu bekler
 * @param aniListId AniList medya kimliği (offset'siz)
 * @param kitsuId Kitsu medya kimliği (offset'siz)
 * @param shikimoriId Shikimori'nin KENDİ numeric kimliği — MAL ID değildir
 * @param bangumiSubjectId Bangumi `subject_id` (offset'siz ham değer)
 */
data class CrossProviderIds(
    val malId: Int? = null,
    val aniListId: Int? = null,
    val kitsuId: Int? = null,
    val shikimoriId: Int? = null,
    val bangumiSubjectId: Int? = null,
    val tmdbId: Int? = null,
    val simklId: Int? = null,
    val via: Map<String, String> = emptyMap()
) {
    /** AniList / Kitsu / Shikimori gibi anime-manga servislerinin yazabileceği bir kimlik var mı? */
    val hasAnimeMangaIdentity: Boolean
        get() = malId != null || aniListId != null || kitsuId != null

    /** TMDB/Simkl hattının (film & dizi) kullanabileceği bir kimlik var mı? */
    val hasVideoIdentity: Boolean
        get() = tmdbId != null || simklId != null

    /** Hiçbir kimlik çözülemedi — çağıran taraf kaydı "atlandı" saymalı, hata saymamalı. */
    val isEmpty: Boolean
        get() = !hasAnimeMangaIdentity && !hasVideoIdentity && bangumiSubjectId == null && shikimoriId == null

    fun withVia(idName: String, source: String): CrossProviderIds =
        copy(via = via + (idName to source))

    /**
     * İki kimlik kümesi aynı yapımı gösteriyor mu? Yalnızca AYNI uzayda eşit ve dolu bir
     * kimlik çifti kanıt sayılır (başlık benzerliği asla). Yerel kardeş satırların
     * (aynı yapımın farklı kaynaklardaki kopyaları) bulunmasında kullanılır.
     */
    fun sharesAnyIdWith(other: CrossProviderIds, includeTmdb: Boolean = true): Boolean =
        (malId != null && malId == other.malId) ||
            (aniListId != null && aniListId == other.aniListId) ||
            (kitsuId != null && kitsuId == other.kitsuId) ||
            (shikimoriId != null && shikimoriId == other.shikimoriId) ||
            (bangumiSubjectId != null && bangumiSubjectId == other.bangumiSubjectId) ||
            // TMDB kimliği anime/mangada sezonları tek dizide toplar; orada kanıt sayılmaz.
            (includeTmdb && tmdbId != null && tmdbId == other.tmdbId) ||
            (simklId != null && simklId == other.simklId)

    /** Bu kayıtta dolu olan değerleri korur, boş olanları [other] ile doldurur. */
    fun mergedWith(other: CrossProviderIds?): CrossProviderIds {
        if (other == null) return this
        return CrossProviderIds(
            malId = malId ?: other.malId,
            aniListId = aniListId ?: other.aniListId,
            kitsuId = kitsuId ?: other.kitsuId,
            shikimoriId = shikimoriId ?: other.shikimoriId,
            bangumiSubjectId = bangumiSubjectId ?: other.bangumiSubjectId,
            tmdbId = tmdbId ?: other.tmdbId,
            simklId = simklId ?: other.simklId,
            via = other.via + via
        )
    }

    fun toJson(): JSONObject = JSONObject().apply {
        malId?.let { put(KEY_MAL, it) }
        aniListId?.let { put(KEY_ANILIST, it) }
        kitsuId?.let { put(KEY_KITSU, it) }
        shikimoriId?.let { put(KEY_SHIKIMORI, it) }
        bangumiSubjectId?.let { put(KEY_BANGUMI, it) }
        tmdbId?.let { put(KEY_TMDB, it) }
        simklId?.let { put(KEY_SIMKL, it) }
        if (via.isNotEmpty()) {
            put(KEY_VIA, JSONObject().apply { via.forEach { (name, source) -> put(name, source) } })
        }
    }

    companion object {
        const val ANILIST_OFFSET = 100_000_000
        const val SIMKL_OFFSET = 150_000_000
        const val SHIKIMORI_OFFSET = 400_000_000

        const val REAL_MAL_MIN = 1
        const val REAL_MAL_MAX = 99_999_999

        /** `MediaIdentity.keys` ile birebir aynı pencereler; Simkl/AniList örtüşmesi kaynakla ayrışır. */
        const val ANILIST_STABLE_MIN = 100_000_001
        const val ANILIST_STABLE_MAX = 299_999_999
        const val SIMKL_STABLE_MIN = 150_000_001
        const val SIMKL_STABLE_MAX = 249_999_999

        internal const val KEY_MAL = "mal"
        internal const val KEY_ANILIST = "anilist"
        internal const val KEY_KITSU = "kitsu"
        internal const val KEY_SHIKIMORI = "shikimori"
        internal const val KEY_BANGUMI = "bangumi"
        internal const val KEY_TMDB = "tmdb"
        internal const val KEY_SIMKL = "simkl"
        internal const val KEY_VIA = "via"
        internal const val KEY_AT = "at"
        internal const val KEY_COMPLETE = "complete"

        /** Bir değerin gerçek MAL ID aralığında olup olmadığı. */
        fun realMalIdOf(value: Int?): Int? = value?.takeIf { it in REAL_MAL_MIN..REAL_MAL_MAX }

        /** Kaynak adını kanonik biçime çevirir (`canonicalMediaSourceId` ile aynı sözlük, saf Kotlin). */
        fun normalizeSource(source: String?): String = when (val value = source?.trim()?.lowercase()) {
            "al", "anilist" -> "anilist"
            "mal", "myanimelist", "jikan", "jikan (mal)", "mal (jikan)" -> "mal"
            "simkl" -> "simkl"
            "tmdb", "themoviedb" -> "tmdb"
            "kitsu" -> "kitsu"
            "shiki", "shikimori" -> "shikimori"
            "bgm", "bgm.tv", "bangumi" -> "bangumi"
            else -> value.orEmpty()
        }

        /**
         * Bir satırın `malId` alanında saklı değeri yorumlar.
         *
         * AniList (100M..299M) ile Simkl (150M..249M) pencereleri **örtüşür**; hangi kimlik
         * alanına düşeceği satırın kaynağından bellidir. Kaynak bilgisi yoksa 100M..149M
         * aralığı AniList kabul edilir (Simkl kimlikleri ayrıca `simklId` alanında taşınır).
         */
        fun decode(
            storedId: Int?,
            source: String?,
            type: MediaType?,
            entrySimklId: Int? = null,
            entryTmdbId: Int? = null
        ): CrossProviderIds {
            val simklFromRow = entrySimklId?.takeIf { it > 0 }
            val tmdbFromRow = entryTmdbId?.takeIf { it > 0 }
            val normalizedSource = normalizeSource(source)
            if (type != MediaType.Anime && type != MediaType.Manga) {
                // Film/dizi satırlarında malId alanı MAL kimliği değildir (TMDB/Simkl kimlik uzayı
                // çakışır). Ancak Bangumi film/dizi satırının subject kimliği KORUNUR: yoksa
                // Bangumi çapraz köprüsü hiç çalışamıyor ve içerik Simkl'e yayılamıyordu.
                val bangumiForVideo = storedId?.let { id ->
                    BangumiIdNamespace.rawIdFromStable(id)
                        ?: id.takeIf { normalizedSource == "bangumi" && it in REAL_MAL_MIN..REAL_MAL_MAX }
                }
                return CrossProviderIds(
                    tmdbId = tmdbFromRow,
                    simklId = simklFromRow,
                    bangumiSubjectId = bangumiForVideo?.takeIf { it > 0 }
                )
            }

            var aniListId: Int? = null
            var kitsuId: Int? = null
            var shikimoriId: Int? = null
            var bangumiSubjectId: Int? = null
            var simklId = simklFromRow
            val via = mutableMapOf<String, String>()

            storedId?.let { id ->
                when {
                    // 100M altı değer, satırın KAYNAĞINA göre yorumlanır (MediaIdentity.keys ile
                    // birebir aynı sözleşme): Shikimori içe aktarması ham `target_id` yazar, eski
                    // Kitsu/Bangumi satırları da ham kimlik taşıyabilir. Bunlar MAL kimliği SAYILMAZ;
                    // eski decode hepsini MAL saydığı için Shikimori satırlarında MAL çözümleme
                    // adımı hiç çalışmıyor, eski Bangumi satırlarında ise subject_id yanlışlıkla
                    // MAL kimliği gibi kullanılıp yayılım kapısı sahte kimlikle açılıyordu.
                    id in REAL_MAL_MIN..REAL_MAL_MAX && normalizedSource == "shikimori" -> {
                        shikimoriId = id
                        via["shikimori"] = "kayit"
                    }
                    id in REAL_MAL_MIN..REAL_MAL_MAX && normalizedSource == "bangumi" -> {
                        bangumiSubjectId = id
                        via["bangumi"] = "kayit"
                    }
                    id in REAL_MAL_MIN..REAL_MAL_MAX && normalizedSource == "kitsu" -> {
                        kitsuId = id
                        via["kitsu"] = "kayit"
                    }
                    id in REAL_MAL_MIN..REAL_MAL_MAX -> via["mal"] = "kayit"
                    normalizedSource == "anilist" && id in ANILIST_STABLE_MIN..ANILIST_STABLE_MAX -> {
                        aniListId = id - ANILIST_OFFSET
                        via["anilist"] = "kayit"
                    }
                    normalizedSource == "simkl" && id in SIMKL_STABLE_MIN..SIMKL_STABLE_MAX -> {
                        simklId = id - SIMKL_OFFSET
                        via["simkl"] = "kayit"
                    }
                    id in ANILIST_STABLE_MIN until SIMKL_STABLE_MIN -> {
                        aniListId = id - ANILIST_OFFSET
                        via["anilist"] = "kayit"
                    }
                    else -> {
                        KitsuIdNamespace.rawIdFromStable(id)?.let {
                            kitsuId = it
                            via["kitsu"] = "kayit"
                        }
                        if (id in (SHIKIMORI_OFFSET + 1)..(SHIKIMORI_OFFSET + 99_999_999)) {
                            shikimoriId = id - SHIKIMORI_OFFSET
                            via["shikimori"] = "kayit"
                        }
                        BangumiIdNamespace.rawIdFromStable(id)?.let {
                            bangumiSubjectId = it
                            via["bangumi"] = "kayit"
                        }
                    }
                }
            }

            // Gerçek MAL kimliği yalnızca MAL kimlik uzayını kullanan kaynaklarda (<100M ham
            // değer) geçerlidir; Shikimori/Bangumi/Kitsu ham değerleri yukarıda kendi alanlarına
            // yerleştirildi, burada MAL sayılmazlar.
            val decodedMalId = realMalIdOf(storedId)?.takeIf {
                normalizedSource !in setOf("shikimori", "bangumi", "kitsu")
            }
            return CrossProviderIds(
                malId = decodedMalId,
                aniListId = aniListId?.takeIf { it > 0 },
                kitsuId = kitsuId?.takeIf { it > 0 },
                shikimoriId = shikimoriId?.takeIf { it > 0 },
                bangumiSubjectId = bangumiSubjectId?.takeIf { it > 0 },
                tmdbId = tmdbFromRow,
                simklId = simklId?.takeIf { it > 0 },
                via = via
            )
        }

        /**
         * Bir yapımın kararlı önbellek anahtarı — satır hangi kaynaktan gelirse gelsin aynı olmalı.
         *
         * [source] verilirse 100M altı ham değerler [decode] ile aynı kuralla yorumlanır:
         * Shikimori/Bangumi/Kitsu ham kimlikleri "mal:" uzayına YAZILMAZ (eski anahtar, aynı
         * sayıdaki bambaşka bir MAL yapımının çözülmüş kimliğini bu satıra yapıştırabiliyordu).
         */
        fun cacheKey(
            type: MediaType?,
            storedId: Int?,
            title: String?,
            titleEnglish: String?,
            year: Int?,
            source: String? = null
        ): String {
            val normalizedSource = normalizeSource(source)
            val namespaceKey = storedId?.let { id ->
                when {
                    id in REAL_MAL_MIN..REAL_MAL_MAX && normalizedSource == "shikimori" -> "shikimori:$id"
                    id in REAL_MAL_MIN..REAL_MAL_MAX && normalizedSource == "bangumi" -> "bangumi:$id"
                    id in REAL_MAL_MIN..REAL_MAL_MAX && normalizedSource == "kitsu" -> "kitsu:$id"
                    id in REAL_MAL_MIN..REAL_MAL_MAX -> "mal:$id"
                    // AniList (100M..299M) ile Simkl (150M..249M) pencereleri örtüşür; kaynak
                    // biliniyorsa ayrım onunla yapılır (kaynak bilinmiyorsa eski sıra korunur).
                    normalizedSource == "anilist" && id in ANILIST_STABLE_MIN..ANILIST_STABLE_MAX ->
                        "anilist:${id - ANILIST_OFFSET}"
                    normalizedSource == "simkl" && id in SIMKL_STABLE_MIN..SIMKL_STABLE_MAX ->
                        "simkl:${id - SIMKL_OFFSET}"
                    id in SIMKL_STABLE_MIN..SIMKL_STABLE_MAX -> "simkl:${id - SIMKL_OFFSET}"
                    id in ANILIST_STABLE_MIN..ANILIST_STABLE_MAX -> "anilist:${id - ANILIST_OFFSET}"
                    KitsuIdNamespace.rawIdFromStable(id) != null -> "kitsu:${KitsuIdNamespace.rawIdFromStable(id)}"
                    BangumiIdNamespace.rawIdFromStable(id) != null -> "bangumi:${BangumiIdNamespace.rawIdFromStable(id)}"
                    id in (SHIKIMORI_OFFSET + 1)..(SHIKIMORI_OFFSET + 99_999_999) -> "shikimori:${id - SHIKIMORI_OFFSET}"
                    else -> null
                }
            }
            val fallbackTitle = listOfNotNull(titleEnglish?.takeIf { it.isNotBlank() }, title?.takeIf { it.isNotBlank() })
                .firstOrNull()
                ?.let { MediaIdentity.normalizedTitle(it) }
                .orEmpty()
            return "${type?.name ?: MediaType.Anime.name}:${namespaceKey ?: "t:$fallbackTitle:${year ?: 0}"}"
        }
    }
}

/**
 * Kullanıcının gerçekten bağlı olduğu kaynaklar.
 *
 * Bağlı olmayan kaynak için kimlik araması yapılmaz: örneğin Bangumi hesabı yoksa
 * bgm.tv'de başlık araması yapmak hem boşuna ağ trafiğiydi hem de eşitlemeyi geciktiriyordu.
 */
data class ConnectedSources(
    val aniList: Boolean = true,
    val mal: Boolean = true,
    val kitsu: Boolean = true,
    val shikimori: Boolean = true,
    val simkl: Boolean = true,
    val bangumi: Boolean = true
) {
    companion object {
        val All = ConnectedSources()
    }
}

/**
 * Çapraz eşitleme kararları. Android'a bağımlı değildir; birim testleriyle doğrudan sınanır.
 */
object CrossSyncPolicy {

    private val bangumiTypes = setOf(
        MediaType.Anime, MediaType.Manga, MediaType.Movie, MediaType.TvShow
    )

    fun supportsAniList(type: MediaType?): Boolean = type == MediaType.Anime || type == MediaType.Manga

    fun supportsMal(type: MediaType?): Boolean = type == MediaType.Anime || type == MediaType.Manga

    fun supportsKitsu(type: MediaType?): Boolean = type == MediaType.Anime || type == MediaType.Manga

    fun supportsShikimori(type: MediaType?): Boolean = type == MediaType.Anime || type == MediaType.Manga

    fun supportsSimkl(type: MediaType?): Boolean = type == MediaType.Anime || type == MediaType.Movie || type == MediaType.TvShow

    fun supportsBangumi(type: MediaType?): Boolean = type != null && type in bangumiTypes

    /**
     * Bangumi satırlarının diğer beş kaynağa yazılıp yazılmayacağı.
     *
     * Eskiden Bangumi satırları **hiç** yayılmazdı (`subject_id` başka kaynaklarda anlamsız).
     * Artık kimlik, Bangumi çapraz eşleştirme katmanının (AniList/Jikan yıl+başlık ağırlıklı
     * eşleşmesi) çözdüğü doğrulanmış bir kimlikten (MAL/AniList/Kitsu ya da film/dizide
     * TMDB/Simkl) geliyorsa yayılır; çözülemezse yalnızca Bangumi'ye yazılır — yanlış yapımı
     * başka bir listeye eklememek için. Her kaynak bloğu kendi kimliğini ayrıca denetler.
     */
    fun allowFanOut(isBangumiRow: Boolean, ids: CrossProviderIds): Boolean =
        !isBangumiRow || ids.hasAnimeMangaIdentity || ids.hasVideoIdentity

    /**
     * Yalnızca "atlandı" (kimlik çözülemedi) varsa işlem yeniden denenmez.
     * Kimlik çözümünün düzeltemeyeceği durumlarda bekleme kuyruğu sonsuza kadar şişiyordu.
     */
    fun shouldRetry(errors: List<String>, skipped: List<String>): Boolean = errors.isNotEmpty()

    /** Eşitleme özetinde gösterilecek sonuç satırı. */
    fun summarize(outcomes: Map<String, String>): String =
        outcomes.entries.joinToString(", ") { (platform, outcome) -> "$platform: $outcome" }
}

/**
 * Bir yapımın altı kimliğini birden çözer ve kalıcı olarak önbelleğe alır.
 *
 * Kademe (güvenden ucuza): satırdaki kendi kimliği → kalıcı çapraz önbellek → ARM
 * (myanimelist/anilist/kitsu/themoviedb + Bangumi çapraz köprüsü) → Kitsu `mappings` →
 * Bangumi eşleme önbelleği / sıkı başlık araması → Simkl → son çare sıkı Jikan araması.
 *
 * Buradan dönen hiçbir kimlik "aramada ilk sonuç" değildir: eşik altı eşleşme üretilmez,
 * çözülemeyen alan `null` kalır ve çağıran taraf o kaynağı atlar.
 */
object CrossProviderIdentityResolver {
    private const val TAG = "CrossProviderIdentity"

    suspend fun resolve(
        context: Context,
        entry: MediaEntry,
        allowNetwork: Boolean = true,
        connected: ConnectedSources = ConnectedSources.All
    ): CrossProviderIds = withContext(Dispatchers.IO) {
        val decoded = CrossProviderIds.decode(
            storedId = entry.malId,
            source = entry.source,
            type = entry.type,
            entrySimklId = entry.simklId,
            entryTmdbId = entry.tmdbId
        )
        val cacheKey = CrossProviderIds.cacheKey(
            type = entry.type,
            storedId = entry.malId,
            title = entry.title,
            titleEnglish = entry.titleEnglish,
            year = entry.year,
            source = entry.source
        )
        val cached = CrossProviderIdentityCache.read(context, cacheKey)
        val ids = decoded.mergedWith(cached?.ids)

        if (!allowNetwork || isCompleteFor(entry, ids, connected) || CrossProviderIdentityCache.inRetryCooldown(cached)) {
            return@withContext ids
        }

        // Çevrimdışı: ağ denemesi YAPMA ve eksik (olumsuz) kayıt YAZMA. Bugün ağ adımı
        // başarısız olsa bile `resolveUncached` normal dönüşle bittiği için eksik kayıt
        // yazılıyor ve 6 saatlik soğuma başlıyordu; bağlantı gelince kuyruktaki yeniden
        // denemeler bile bu soğuma yüzünden kimliksiz kalıyor, yayılım kalıcı olarak
        // kayboluyordu (bkz. MediaEntryRepository.syncEntryIfPossible kuyruğu).
        if (!runCatching { NetworkMonitor(context).isCurrentlyOnline() }.getOrDefault(true)) {
            return@withContext ids
        }

        val resolution = runSyncCatching { resolveUncached(context, entry, ids, connected) }
        resolution.exceptionOrNull()?.let { if (it is CancellationException) throw it }
        val resolved = resolution.getOrNull() ?: ids
        val complete = isCompleteFor(entry, resolved, connected)
        CrossProviderIdentityCache.write(context, cacheKey, resolved, complete)
        // Aynı yapım başka bir kaynaktan geldiğinde eşleme ağ isteği olmadan bulunsun diye
        // çözülen kanonik MAL kimliğiyle de ikinci bir kayıt yazılır.
        val canonicalKey = CrossProviderIds.cacheKey(
            type = entry.type,
            storedId = resolved.malId,
            title = entry.title,
            titleEnglish = entry.titleEnglish,
            year = entry.year
        )
        if (resolved.malId != null && canonicalKey != cacheKey) {
            CrossProviderIdentityCache.write(context, canonicalKey, resolved, complete)
        }
        // Bangumi ↔ MAL köprüsü iki yönde de kalıcı olsun: içe aktarılmış bir条目 satırının MAL
        // kimliği yalnızca bu tur için bellekte çözülmüştü. Çift yazılmazsa birleşik kütüphane
        // ("Listem → Tümü") o satırı hiçbir zaman MAL/AniList grubuna katamıyor ve sayaç
        // Bangumi kadar şişiyor — bkz. MyListIdentityCrosswalk.
        val learnedMalId = resolved.malId
        val learnedSubjectId = resolved.bangumiSubjectId
        if (learnedMalId != null && learnedSubjectId != null && connected.bangumi &&
            CrossSyncPolicy.supportsBangumi(entry.type)
        ) {
            runSyncCatching {
                BangumiLocalMappingCache.put(
                    context = context,
                    malId = learnedMalId,
                    subjectType = BangumiIdNamespace.subjectTypeFor(entry.type),
                    subjectId = learnedSubjectId
                )
            }
        }
        if (resolved.isEmpty) Log.d(TAG, "Kimlik çözülemedi (atlanacak kaynaklar olacak): ${entry.title}")
        resolved
    }

    /**
     * Bağlı tüm kaynaklar için kimlik biliniyor mu? Bilinmiyorsa tek seferlik pahalı
     * tarama gerekir; biliniyorsa eşitleme ağa çıkmadan tamamlanır.
     */
    private fun isCompleteFor(
        entry: MediaEntry,
        ids: CrossProviderIds,
        connected: ConnectedSources = ConnectedSources.All
    ): Boolean = when (entry.type) {
        MediaType.Anime, MediaType.Manga ->
            (ids.malId != null || !connected.mal) &&
                (ids.aniListId != null || !connected.aniList) &&
                (ids.kitsuId != null || !connected.kitsu) &&
                (ids.bangumiSubjectId != null || !connected.bangumi) &&
                (!connected.simkl || !CrossSyncPolicy.supportsSimkl(entry.type) || ids.simklId != null)
        else -> (ids.tmdbId != null || ids.simklId != null)
    }

    private suspend fun resolveUncached(
        context: Context,
        entry: MediaEntry,
        start: CrossProviderIds,
        connected: ConnectedSources = ConnectedSources.All
    ): CrossProviderIds {
        var ids = start

        // 1) Shikimori satırları: Shikimori numeric ID ≠ MAL ID. Önce oradan MAL'e in.
        if (ids.malId == null && ids.shikimoriId != null) {
            val malFromShiki = runSyncCatching {
                com.kitsugi.animelist.data.remote.KitsugiIdResolver.resolveMalIdFromShikimori(ids.shikimoriId)
            }.getOrNull()?.let { CrossProviderIds.realMalIdOf(it) }
            if (malFromShiki != null) ids = ids.copy(malId = malFromShiki).withVia("mal", "Shikimori")
        }

        // 2) ARM kavşağı: MAL ↔ AniList ↔ Kitsu ↔ TMDB; başka kimlik yoksa Bangumi çapraz köprüsü.
        if (ids.malId == null || ids.aniListId == null || ids.kitsuId == null || ids.tmdbId == null) {
            val arm = runSyncCatching {
                com.kitsugi.animelist.data.remote.KitsugiIdResolver.resolveIds(
                    malId = ids.malId,
                    aniListId = ids.aniListId,
                    tmdbId = ids.tmdbId,
                    mediaType = entry.type,
                    kitsuId = ids.kitsuId,
                    bangumiId = if (ids.malId == null && ids.aniListId == null && ids.kitsuId == null && ids.tmdbId == null) {
                        ids.bangumiSubjectId
                    } else null
                )
            }.getOrNull()
            if (arm != null) {
                val before = ids
                var withArm = ids.copy(
                    malId = ids.malId ?: CrossProviderIds.realMalIdOf(arm.malId),
                    aniListId = ids.aniListId ?: arm.aniListId?.takeIf { it > 0 },
                    kitsuId = ids.kitsuId ?: arm.kitsuId?.takeIf { it > 0 },
                    tmdbId = ids.tmdbId ?: arm.tmdbId?.takeIf { it > 0 }
                )
                if (withArm.malId != before.malId) withArm.malId?.let { withArm = withArm.withVia("mal", "ARM") }
                if (withArm.aniListId != before.aniListId) withArm.aniListId?.let { withArm = withArm.withVia("anilist", "ARM") }
                if (withArm.kitsuId != before.kitsuId) withArm.kitsuId?.let { withArm = withArm.withVia("kitsu", "ARM") }
                ids = withArm
            }
        }

        val isAnime = entry.type != MediaType.Manga

        // 3) Kitsu: MAL veya AniList kimliğinden resmi `mappings` tablosu (+ kullanıcı önbelleği).
        if (ids.kitsuId == null && connected.kitsu && CrossSyncPolicy.supportsKitsu(entry.type)) {
            ids.malId?.let { mal ->
                val cachedKitsu = runSyncCatching {
                    ExternalAuthManager.getKitsuMediaIdForMal(context, mal, isAnime)
                }.getOrNull()?.takeIf { it > 0 }
                if (cachedKitsu != null) {
                    ids = ids.copy(kitsuId = cachedKitsu).withVia("kitsu", "Kitsu eşleme önbelleği")
                } else {
                    val mapped = runSyncCatching {
                        KitsuApiClient.lookupKitsuIdByExternalMapping(
                            "myanimelist/${if (isAnime) "anime" else "manga"}",
                            mal
                        )
                    }.getOrNull()?.takeIf { it > 0 }
                    if (mapped != null) {
                        runSyncCatching { ExternalAuthManager.saveKitsuMediaIdForMal(context, mal, isAnime, mapped) }
                        ids = ids.copy(kitsuId = mapped).withVia("kitsu", "Kitsu mappings")
                    }
                }
            }
            if (ids.kitsuId == null && ids.aniListId != null) {
                val aniListId = ids.aniListId!!
                val mapped = runSyncCatching {
                    KitsuApiClient.lookupKitsuIdByExternalMapping(
                        "anilist/${if (isAnime) "anime" else "manga"}",
                        aniListId
                    )
                }.getOrNull()?.takeIf { it > 0 }
                if (mapped != null) ids = ids.copy(kitsuId = mapped).withVia("kitsu", "Kitsu mappings")
            }
        }

        // 4) Bangumi: MAL→Bangumi eşleme önbelleği, olmazsa sıkı başlık araması (tek ve net eşleşme).
        if (ids.bangumiSubjectId == null && connected.bangumi && CrossSyncPolicy.supportsBangumi(entry.type)) {
            val subjectType = BangumiIdNamespace.subjectTypeFor(entry.type)
            val fromCache = ids.malId?.let { mal ->
                runSyncCatching { BangumiLocalMappingCache.get(context, mal, subjectType) }.getOrNull()
            }
            if (fromCache != null && fromCache > 0) {
                ids = ids.copy(bangumiSubjectId = fromCache).withVia("bangumi", "MAL eşleme önbelleği")
            } else {
                val knownMalId = ids.malId
                for (candidateTitle in entryTitleCandidates(entry)) {
                    val resolved = runSyncCatching {
                        BangumiIdNamespace.resolveBangumiId(
                            context = context,
                            storedId = knownMalId,
                            title = candidateTitle,
                            expectedYear = entry.year,
                            isAnime = isAnime,
                            subjectType = subjectType
                        )
                    }.getOrNull()?.takeIf { it > 0 }
                    if (resolved != null) {
                        ids = ids.copy(bangumiSubjectId = resolved).withVia("bangumi", "Bangumi arama")
                        // AniList/MAL ↔ Bangumi köprüsü de öğrenilsin: sonraki eşitleme ağa çıkmaz.
                        if (knownMalId != null) {
                            runSyncCatching {
                                BangumiLocalMappingCache.put(context, knownMalId, subjectType, resolved)
                            }
                        }
                        break
                    }
                }
            }
        }

        // 5) Simkl (manga hariç): tek istek, yalnızca birebir kimliklerle.
        if (ids.simklId == null && connected.simkl && CrossSyncPolicy.supportsSimkl(entry.type) &&
            (ids.malId != null || ids.tmdbId != null || ids.aniListId != null)
        ) {
            val simkl = runSyncCatching {
                com.kitsugi.animelist.data.remote.SimklApiClient().lookupSimklId(
                    malId = ids.malId,
                    tmdbId = ids.tmdbId,
                    aniListId = ids.aniListId,
                    title = entry.titleEnglish?.takeIf { it.isNotBlank() } ?: entry.title,
                    year = entry.year,
                    mediaType = entry.type
                )
            }.getOrNull()?.takeIf { it > 0 }
            if (simkl != null) ids = ids.copy(simklId = simkl).withVia("simkl", "Simkl")
        }

        // 6) Son çare: MAL kataloğunda sıkı başlık araması (tek ve net eşleşme).
        // Adaylar sırayla denenir: Bangumi satırında İngilizce çoğu zaman yoktur ve görünen
        // başlık Çince olabilir; Jikan'da net eşleşme verecek alan özgün Japonca başlıktır.
        if (ids.malId == null && connected.mal && CrossSyncPolicy.supportsMal(entry.type)) {
            val searchTitles = listOfNotNull(entry.titleEnglish, entry.titleJapanese, entry.title)
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
            for (searchTitle in searchTitles) {
                val jikanMal = runSyncCatching { malIdFromStrictJikanSearch(entry, searchTitle) }.getOrNull()
                if (jikanMal != null) {
                    ids = ids.copy(malId = jikanMal).withVia("mal", "Jikan başlık araması")
                    break
                }
            }
        }

        return ids
    }

    private fun entryTitleCandidates(entry: MediaEntry): List<String> =
        listOfNotNull(entry.title, entry.titleEnglish, entry.titleJapanese)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()

    private suspend fun malIdFromStrictJikanSearch(entry: MediaEntry, searchTitle: String): Int? {
        val results = com.kitsugi.animelist.data.remote.JikanSearchClient().searchMALOnly(
            query = searchTitle,
            mediaType = entry.type
        )
        return results.filter { result ->
            result.type == entry.type &&
                (entry.year == null || result.year == entry.year) &&
                listOfNotNull(result.title, result.titleEnglish, result.titleJapanese).any {
                    MediaIdentity.normalizedTitle(it) == MediaIdentity.normalizedTitle(searchTitle)
                }
        }.singleOrNull()?.let { result ->
            CrossProviderIds.realMalIdOf(result.realMalId ?: result.malId)
        }
    }
}

/**
 * Çapraz kimlik önbelleği (kalıcı).
 *
 * Bir kez çözülen eşleşme, ilerleme her değiştiğinde yeniden ağ istekleri üretmez.
 * Hiçbir şey çözülemezse olumsuz kayıt yazılır ve [RETRY_COOLDOWN_MS] boyunca
 * tekrar denenmez — katalogda karşılığı olmayan yapımlarda kuyruk şişmesin diye.
 */
object CrossProviderIdentityCache {
    private const val PREFS_NAME = "cross_provider_identity_v1"
    private const val RETRY_COOLDOWN_MS = 6L * 60L * 60L * 1000L

    /** @param complete hedeflenen tüm kimlikler çözüldü mü; çözülemediyse cooldown sonrası tekrar denenir. */
    data class Cached(val ids: CrossProviderIds, val complete: Boolean, val at: Long)

    private fun prefs(context: Context): android.content.SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun read(context: Context, key: String): Cached? = runCatching {
        val raw = prefs(context).getString(key, null) ?: return null
        val json = JSONObject(raw)
        val viaObject = json.optJSONObject(CrossProviderIds.KEY_VIA)
        val via: Map<String, String> = if (viaObject == null) {
            emptyMap()
        } else {
            buildMap {
                viaObject.keys().forEach { name -> put(name, viaObject.optString(name)) }
            }
        }
        Cached(
            ids = CrossProviderIds(
                malId = json.optPositiveId(CrossProviderIds.KEY_MAL),
                aniListId = json.optPositiveId(CrossProviderIds.KEY_ANILIST),
                kitsuId = json.optPositiveId(CrossProviderIds.KEY_KITSU),
                shikimoriId = json.optPositiveId(CrossProviderIds.KEY_SHIKIMORI),
                bangumiSubjectId = json.optPositiveId(CrossProviderIds.KEY_BANGUMI),
                tmdbId = json.optPositiveId(CrossProviderIds.KEY_TMDB),
                simklId = json.optPositiveId(CrossProviderIds.KEY_SIMKL),
                via = via
            ),
            complete = json.optBoolean(CrossProviderIds.KEY_COMPLETE, false),
            at = json.optLong(CrossProviderIds.KEY_AT, 0L)
        )
    }.getOrNull()

    /**
     * Çözüm eksik kaldıysa (ör. Bangumi eşleşmesi bulunamadı) aynı kayıt için
     * tekrar tekrar tarama yapılmaz; [RETRY_COOLDOWN_MS] sonra bir kez daha denenir.
     */
    fun inRetryCooldown(cached: Cached?): Boolean {
        if (cached == null || cached.complete) return false
        return System.currentTimeMillis() - cached.at < RETRY_COOLDOWN_MS
    }

    fun write(context: Context, key: String, ids: CrossProviderIds, complete: Boolean) {
        runCatching {
            val json = ids.toJson()
                .put(CrossProviderIds.KEY_AT, System.currentTimeMillis())
                .put(CrossProviderIds.KEY_COMPLETE, complete)
            prefs(context).edit().putString(key, json.toString()).apply()
        }
    }

    fun clear(context: Context) {
        runCatching { prefs(context).edit().clear().apply() }
    }

    private fun JSONObject.optPositiveId(name: String): Int? = optInt(name, 0).takeIf { it > 0 }
}
