package com.kitsugi.animelist.data.fandom

import android.content.Context
import android.util.Log
import com.kitsugi.animelist.core.memory.BoundedCache
import com.kitsugi.animelist.data.local.KitsugiDatabase
import com.kitsugi.animelist.data.local.PersistentDetailCacheDao
import com.kitsugi.animelist.data.local.PersistentDetailCacheEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Fandom Wiki entegrasyonunun arayüze açılan tek giriş noktası.
 *
 * Önbellek katmanları:
 *  - Bellek: [BoundedCache] (süreç boyunca, bellek baskısında küçülür).
 *  - Kalıcı: `persistent_details_cache` tablosu, `fandom:v1:` önekli anahtarlar.
 *    Mevcut otomatik temizlik (MappingSyncWorker + StorageJanitor, 30 gün) bu kayıtları da
 *    kapsar; Room şeması değişmez. Ham HTML değil, sadeleştirilmiş JSON saklanır.
 *
 * Süreler: wiki eşleşmesi 30 gün, karakter sayfası ve bölümler 7 gün, "bulunamadı" 3 gün.
 * Çözümleme sırasında ağ hatası olduysa "bulunamadı" sonucu kalıcı önbelleğe yazılmaz.
 */
object FandomRepository {

    private const val TAG = "FandomRepository"
    const val KEY_PREFIX = "fandom:v1:"

    private const val DAY_MS = 24L * 60L * 60L * 1000L
    private const val WIKI_TTL_MS = 30L * DAY_MS
    private const val PAGE_TTL_MS = 7L * DAY_MS
    private const val SECTION_TTL_MS = 7L * DAY_MS
    private const val NEGATIVE_TTL_MS = 3L * DAY_MS
    private const val TRANSIENT_NEGATIVE_TTL_MS = 10L * 60L * 1000L

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Serializable
    private data class StoredWiki(val ref: FandomWikiRef? = null)

    @Serializable
    private data class StoredCharacter(val page: FandomCharacterPage? = null)

    @Serializable
    private data class StoredSection(val content: FandomSectionContent? = null)

    private data class Memo(val value: Any?, val expiresAt: Long)

    private val memory = BoundedCache<String, Memo>("fandom_repository", 128)

    // Ayrı kilit dizileri: karakter çözümlemesi (char kilidi) içinden wiki çözümlemesi
    // (wiki kilidi) çağrılır; aynı dizi kullanılsaydı aynı şeride düşen anahtarlar kilitlenirdi.
    private val wikiLocks = Array(16) { Mutex() }
    private val characterLocks = Array(16) { Mutex() }
    private val sectionLocks = Array(16) { Mutex() }

    private fun lockFor(locks: Array<Mutex>, key: String): Mutex = locks[(key.hashCode() and Int.MAX_VALUE) % locks.size]

    // ── Yapım → wiki ─────────────────────────────────────────────────────────

    suspend fun resolveMediaWiki(context: Context, query: FandomMediaQuery, allowNameFallback: Boolean): FandomWikiRef? {
        val appContext = context.applicationContext
        val prefs = FandomWikiPrefs.state(appContext)
        if (!prefs.enabled) return null
        val mediaKey = query.cacheKey() ?: return null

        // Kullanıcının onayladığı wiki her şeyden önce gelir.
        FandomUserChoices.acceptedWiki(appContext, mediaKey)?.let { accepted ->
            val info = FandomWikiResolver.siteInfoCached(accepted)
            return FandomWikiRef(
                wikiId = info?.wikiId ?: accepted,
                source = FandomMatchSource.USER,
                confidence = FandomConfidence.CERTAIN,
                siteName = info?.siteName
            )
        }

        val nameAllowed = allowNameFallback && prefs.nameFallback
        val cacheKey = wikiCacheKey(mediaKey, nameAllowed)

        return lockFor(wikiLocks, cacheKey).withLock {
            val cached = readCached(appContext, cacheKey, positiveTtl = WIKI_TTL_MS) { body ->
                json.decodeFromString(StoredWiki.serializer(), body).ref
            }
            if (cached != null) {
                val ref = cached.value as? FandomWikiRef ?: return@withLock null
                if (!FandomUserChoices.isWikiRejected(appContext, mediaKey, ref.wikiId)) return@withLock ref
                // Kullanıcı bu wiki'yi reddetti: önbellek yok sayılır, sıradaki aday için yeniden çözülür.
            }
            val failuresBefore = FandomHttp.failureCount()
            val found = try {
                FandomWikiResolver(appContext) { wikiId ->
                    FandomUserChoices.isWikiRejected(appContext, mediaKey, wikiId)
                }.resolve(query, nameAllowed)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Wiki çözümlemesi başarısız ($mediaKey): ${e.message}")
                null
            }
            val resolved = found?.takeUnless { FandomUserChoices.isWikiRejected(appContext, mediaKey, it.wikiId) }
            val transientFailure = resolved == null && FandomHttp.failureCount() != failuresBefore
            writeCached(
                context = appContext,
                key = cacheKey,
                value = resolved,
                encoded = json.encodeToString(StoredWiki.serializer(), StoredWiki(resolved)),
                persist = !transientFailure,
                ttl = when {
                    resolved != null -> WIKI_TTL_MS
                    transientFailure -> TRANSIENT_NEGATIVE_TTL_MS
                    else -> NEGATIVE_TTL_MS
                }
            )
            resolved
        }
    }

    private fun wikiCacheKey(mediaKey: String, nameAllowed: Boolean): String =
        "${KEY_PREFIX}wiki:$mediaKey:${if (nameAllowed) "n1" else "n0"}"

    // ── Karakter → sayfa ─────────────────────────────────────────────────────

    suspend fun resolveCharacterPage(context: Context, query: FandomCharacterQuery): FandomCharacterPage? {
        val appContext = context.applicationContext
        val prefs = FandomWikiPrefs.state(appContext)
        if (!prefs.enabled) return null
        val characterKey = query.cacheKey()
        val cacheKey = characterCacheKey(characterKey, prefs)

        return lockFor(characterLocks, cacheKey).withLock {
            val cached = readCached(appContext, cacheKey, positiveTtl = PAGE_TTL_MS) { body ->
                json.decodeFromString(StoredCharacter.serializer(), body).page
            }
            if (cached != null) {
                val page = cached.value as? FandomCharacterPage ?: return@withLock null
                if (!isRejected(appContext, characterKey, page)) return@withLock applyAcceptance(appContext, characterKey, page)
                // Reddedilmiş eşleşme: önbellek yok sayılır, yeniden çözülür.
            }
            val failuresBefore = FandomHttp.failureCount()
            val page = try {
                val resolver = FandomCharacterResolver(
                    context = appContext,
                    allowNameFallback = prefs.nameFallback,
                    isRejected = { wikiId, title -> FandomUserChoices.isPageRejected(appContext, characterKey, wikiId, title) }
                )
                resolver.resolve(query)?.let { buildPage(it, prefs) }?.let { applyAcceptance(appContext, characterKey, it) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Karakter sayfası çözümlemesi başarısız ($characterKey): ${e.message}")
                null
            }
            val transientFailure = page == null && FandomHttp.failureCount() != failuresBefore
            writeCached(
                context = appContext,
                key = cacheKey,
                value = page,
                encoded = json.encodeToString(StoredCharacter.serializer(), StoredCharacter(page)),
                persist = !transientFailure,
                ttl = when {
                    page != null -> PAGE_TTL_MS
                    transientFailure -> TRANSIENT_NEGATIVE_TTL_MS
                    else -> NEGATIVE_TTL_MS
                }
            )
            page
        }
    }

    private fun characterCacheKey(characterKey: String, prefs: FandomWikiPrefs.State): String =
        "${KEY_PREFIX}char:$characterKey:" +
            (if (prefs.preferTurkish) "tr1" else "tr0") + ":" +
            (if (prefs.nameFallback) "n1" else "n0")

    /** Sayfa özetini oluşturur; tercih açıksa ve sayfanın Türkçe karşılığı doluysa onu kullanır. */
    private suspend fun buildPage(resolved: FandomResolvedCharacter, prefs: FandomWikiPrefs.State): FandomCharacterPage? {
        val englishTitle = resolved.lookup.resolvedTitle ?: return null
        var wikiId = resolved.wikiId
        var lookup = resolved.lookup
        var siteName = resolved.siteName
        var englishWikiId: String? = null
        var englishPageTitle: String? = null

        if (prefs.preferTurkish && FandomUrls.languageOf(wikiId) != "tr") {
            val turkishLink = lookup.turkishLink
                ?: FandomApi.lookupTitles(wikiId, listOf(englishTitle), withTurkishLink = true)[englishTitle]?.turkishLink
            if (turkishLink != null) {
                val (trWiki, trTitle) = turkishLink
                val trLookup = FandomApi.lookupTitles(trWiki, listOf(trTitle))[trTitle]
                val hasContent = trLookup != null && trLookup.exists &&
                    (trLookup.infoboxJson != null || (trLookup.description?.length ?: 0) >= 80)
                if (hasContent && trLookup != null) {
                    englishWikiId = wikiId
                    englishPageTitle = englishTitle
                    wikiId = trWiki
                    lookup = trLookup
                    siteName = null
                }
            }
        }

        val title = lookup.resolvedTitle ?: return null
        val sections = FandomApi.sections(wikiId, title).orEmpty()
        val resolvedSiteName = siteName ?: FandomWikiResolver.siteInfoCached(wikiId)?.siteName
        return FandomCharacterPage(
            wikiId = wikiId,
            pageTitle = title,
            siteName = resolvedSiteName,
            description = lookup.description?.let { FandomNameVariants.cleanDescription(it) }?.takeIf { it.isNotBlank() },
            infobox = FandomInfoboxParser.parse(lookup.infoboxJson),
            sections = sections,
            matchSource = resolved.source,
            confidence = resolved.confidence,
            englishWikiId = englishWikiId,
            englishPageTitle = englishPageTitle,
            mediaKey = resolved.mediaKey,
            wikiSource = resolved.wikiSource,
            wikiConfidence = resolved.wikiConfidence
        )
    }

    private fun isRejected(context: Context, characterKey: String, page: FandomCharacterPage): Boolean {
        if (FandomUserChoices.isPageRejected(context, characterKey, page.matchedWikiId, page.matchedPageTitle)) return true
        val mediaKey = page.mediaKey ?: return false
        return page.wikiSource != FandomMatchSource.USER &&
            FandomUserChoices.isWikiRejected(context, mediaKey, page.matchedWikiId)
    }

    /** Kullanıcının onayladığı otomatik eşleşme kesin sayılır. */
    private fun applyAcceptance(context: Context, characterKey: String, page: FandomCharacterPage): FandomCharacterPage {
        if (!page.needsConfirmation) return page
        val accepted = FandomUserChoices.isPageAccepted(context, characterKey, page.matchedWikiId, page.matchedPageTitle)
        return if (accepted) {
            page.copy(confidence = FandomConfidence.CERTAIN, wikiConfidence = FandomConfidence.CERTAIN)
        } else {
            page
        }
    }

    // ── Bölüm metni ──────────────────────────────────────────────────────────

    suspend fun loadSection(context: Context, wikiId: String, pageTitle: String, index: Int): FandomSectionContent? {
        val appContext = context.applicationContext
        val cacheKey = "${KEY_PREFIX}sec:$wikiId|$pageTitle|$index"
        return lockFor(sectionLocks, cacheKey).withLock {
            val cached = readCached(appContext, cacheKey, positiveTtl = SECTION_TTL_MS) { body ->
                json.decodeFromString(StoredSection.serializer(), body).content
            }
            val cachedContent = cached?.value as? FandomSectionContent
            if (cachedContent != null) return@withLock cachedContent

            val html = FandomApi.sectionHtml(wikiId, pageTitle, index) ?: return@withLock null
            val content = FandomSectionContent(FandomHtmlText.blocks(html))
            writeCached(
                context = appContext,
                key = cacheKey,
                value = content,
                encoded = json.encodeToString(StoredSection.serializer(), StoredSection(content)),
                persist = true,
                ttl = SECTION_TTL_MS
            )
            content
        }
    }

    // ── Kullanıcı bildirimi ve temizlik ──────────────────────────────────────

    /**
     * "Bu eşleşme doğru": sayfa (ve isimle bulunmuş wiki) kesin kabul edilir. Wiki onayı,
     * aynı yapımın diğer karakterlerinde de kullanılır.
     */
    fun confirmCharacterPage(context: Context, query: FandomCharacterQuery, page: FandomCharacterPage): FandomCharacterPage {
        val appContext = context.applicationContext
        val characterKey = query.cacheKey()
        FandomUserChoices.acceptPage(appContext, characterKey, page.matchedWikiId, page.matchedPageTitle)
        val mediaKey = page.mediaKey
        if (mediaKey != null && page.wikiSource == FandomMatchSource.NAME) {
            FandomUserChoices.acceptWiki(appContext, mediaKey, page.matchedWikiId)
        }
        return page.copy(confidence = FandomConfidence.CERTAIN, wikiConfidence = FandomConfidence.CERTAIN)
    }

    /**
     * "Bu eşleşme yanlış": sayfa bu karakter için bir daha önerilmez. Wiki isimle bulunup
     * kesinleşmemişse wiki de o yapım için reddedilir (sıradaki aday değerlendirilir).
     * İlgili önbellek kayıtları düşürülür.
     */
    suspend fun rejectCharacterPage(context: Context, query: FandomCharacterQuery, page: FandomCharacterPage) {
        val appContext = context.applicationContext
        val characterKey = query.cacheKey()
        FandomUserChoices.rejectPage(appContext, characterKey, page.matchedWikiId, page.matchedPageTitle)
        val keys = mutableListOf<String>()
        listOf("tr1:n1", "tr1:n0", "tr0:n1", "tr0:n0").mapTo(keys) { "${KEY_PREFIX}char:$characterKey:$it" }
        val mediaKey = page.mediaKey
        if (mediaKey != null && page.isWikiUnconfirmedNameMatch) {
            FandomUserChoices.rejectWiki(appContext, mediaKey, page.matchedWikiId)
            keys += wikiCacheKey(mediaKey, nameAllowed = true)
            keys += wikiCacheKey(mediaKey, nameAllowed = false)
        }
        keys.forEach { memory.remove(it) }
        val dao = dao(appContext) ?: return
        keys.forEach { key -> runCatchingDb { dao.deleteDetail(key) } }
    }

    /** Ayarlardaki "Wiki önbelleğini temizle": önbellek + eşleşme bildirimleri. */
    suspend fun clearAll(context: Context) {
        val appContext = context.applicationContext
        memory.clear()
        FandomUserChoices.clear(appContext)
        val dao = dao(appContext) ?: return
        runCatchingDb { dao.deleteByPrefix(KEY_PREFIX) }
    }

    // ── Önbellek yardımcıları ────────────────────────────────────────────────

    /** Önbellekte kayıt varsa (değer null = "bulunamadı" olabilir) döner; yoksa null. */
    private suspend fun readCached(
        context: Context,
        key: String,
        positiveTtl: Long,
        decode: (String) -> Any?
    ): Memo? {
        val now = System.currentTimeMillis()
        memory[key]?.let { memo ->
            if (memo.expiresAt > now) return memo
            memory.remove(key)
        }
        val dao = dao(context) ?: return null
        val entity = runCatchingDb { dao.getDetail(key) } ?: return null
        val value = try {
            decode(entity.detailJson)
        } catch (e: Exception) {
            Log.w(TAG, "Bozuk önbellek kaydı atlandı: $key")
            runCatchingDb { dao.deleteDetail(key) }
            return null
        }
        val ttl = if (value != null) positiveTtl else NEGATIVE_TTL_MS
        val expiresAt = entity.cachedAtMs + ttl
        if (expiresAt <= now) return null
        val memo = Memo(value, expiresAt)
        memory[key] = memo
        return memo
    }

    private suspend fun writeCached(
        context: Context,
        key: String,
        value: Any?,
        encoded: String,
        persist: Boolean,
        ttl: Long
    ) {
        val now = System.currentTimeMillis()
        memory[key] = Memo(value, now + ttl)
        if (!persist) return
        val dao = dao(context) ?: return
        runCatchingDb { dao.insertDetail(PersistentDetailCacheEntity(cacheKey = key, detailJson = encoded, cachedAtMs = now)) }
    }

    private fun dao(context: Context): PersistentDetailCacheDao? = try {
        KitsugiDatabase.getDatabase(context).persistentDetailCacheDao()
    } catch (e: Exception) {
        Log.w(TAG, "Veritabanına erişilemedi: ${e.message}")
        null
    }

    private suspend fun <T> runCatchingDb(block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Önbellek işlemi başarısız: ${e.message}")
        null
    }
}
