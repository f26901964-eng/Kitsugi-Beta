package com.kitsugi.animelist.data.fandom

import android.content.Context
import android.util.Log
import com.kitsugi.animelist.core.memory.BoundedCache
import com.kitsugi.animelist.data.local.KitsugiDatabase
import com.kitsugi.animelist.data.local.PersistentDetailCacheDao
import com.kitsugi.animelist.data.local.PersistentDetailCacheEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
    private const val GUIDE_TTL_MS = 7L * DAY_MS
    private const val ARTICLE_TTL_MS = 7L * DAY_MS
    /** Evren rehberi çiplerinde bir seferde çekilen kategori üyesi sayısı. */
    private const val GUIDE_PAGE_SIZE = 14
    private const val BLURB_TTL_MS = 7L * DAY_MS
    private const val NEGATIVE_TTL_MS = 3L * DAY_MS
    private const val TRANSIENT_NEGATIVE_TTL_MS = 10L * 60L * 1000L
    private const val SEARCH_MEMO_TTL_MS = 5L * 60L * 1000L

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

    @Serializable
    private data class StoredGuide(val guide: FandomWorldGuide? = null)

    @Serializable
    private data class StoredBlurb(val blurb: FandomPageBlurb? = null)

    @Serializable
    private data class StoredArticle(val article: FandomArticlePage? = null)

    private data class Memo(val value: Any?, val expiresAt: Long)

    private val memory = BoundedCache<String, Memo>("fandom_repository", 128)

    // Ayrı kilit dizileri: karakter çözümlemesi (char kilidi) içinden wiki çözümlemesi
    // (wiki kilidi) çağrılır; aynı dizi kullanılsaydı aynı şeride düşen anahtarlar kilitlenirdi.
    private val wikiLocks = Array(16) { Mutex() }
    private val characterLocks = Array(16) { Mutex() }
    private val sectionLocks = Array(16) { Mutex() }

    private fun lockFor(locks: Array<Mutex>, key: String): Mutex = locks[(key.hashCode() and Int.MAX_VALUE) % locks.size]

    /**
     * "Tekrar dene": bellek içi "wiki yok" zehir kayıtları temizlenir.
     */
    fun forgetMissingWikis() {
        FandomWikiResolver.forgetMissingWikis()
    }

    // ── Yapım → wiki ─────────────────────────────────────────────────────────

    suspend fun resolveMediaWiki(
        context: Context,
        query: FandomMediaQuery,
        allowNameFallback: Boolean,
        forceRefresh: Boolean = false
    ): FandomWikiRef? {
        val appContext = context.applicationContext
        val effectiveQuery = query.normalizedMediaKind()
        val prefs = FandomWikiPrefs.state(appContext)
        if (!prefs.enabled) return null
        val mediaKey = effectiveQuery.cacheKey() ?: return null

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
        val resolutionKey = effectiveQuery.resolutionKey() ?: mediaKey
        val cacheKey = wikiCacheKey(resolutionKey, nameAllowed)

        return lockFor(wikiLocks, cacheKey).withLock {
            val cached = if (forceRefresh) null else readCached(appContext, cacheKey, positiveTtl = WIKI_TTL_MS) { body ->
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
                }.resolve(effectiveQuery, nameAllowed)
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
        "${KEY_PREFIX}wiki:v3:$mediaKey:${if (nameAllowed) "n1" else "n0"}"

    /**
     * "Yanlışsa bildir" (yapım sekmesi): wiki bu yapım için reddedilir, wiki eşleşme
     * önbelleği düşürülür; sonraki çözümlemede sıradaki aday değerlendirilir.
     */
    suspend fun rejectMediaWiki(context: Context, query: FandomMediaQuery, ref: FandomWikiRef) {
        val appContext = context.applicationContext
        val mediaKey = query.cacheKey() ?: return
        val resolutionKey = query.resolutionKey() ?: mediaKey
        FandomUserChoices.rejectWiki(appContext, mediaKey, ref.wikiId)
        val keys = listOf(
            wikiCacheKey(resolutionKey, nameAllowed = true),
            wikiCacheKey(resolutionKey, nameAllowed = false)
        )
        keys.forEach { memory.remove(it) }
        val dao = dao(appContext) ?: return
        keys.forEach { key -> runCatchingDb { dao.deleteDetail(key) } }
    }

    /**
     * "Hayır, yanlış"ın geri alınması (yapım wiki sekmesi): wiki reddi kaldırılır
     * ve ilgili önbellek kayıtları düşürülür ki reddedilen wiki tekrar çözülebilsin.
     */
    suspend fun undoRejectMediaWiki(context: Context, query: FandomMediaQuery, ref: FandomWikiRef) {
        val appContext = context.applicationContext
        val mediaKey = query.cacheKey() ?: return
        val resolutionKey = query.resolutionKey() ?: mediaKey
        FandomUserChoices.unrejectWiki(appContext, mediaKey, ref.wikiId)
        val keys = listOf(
            wikiCacheKey(resolutionKey, nameAllowed = true),
            wikiCacheKey(resolutionKey, nameAllowed = false)
        )
        keys.forEach { memory.remove(it) }
        val dao = dao(appContext) ?: return
        keys.forEach { key -> runCatchingDb { dao.deleteDetail(key) } }
    }

    /**
     * Kullanıcı "Tekrar dene" dediğinde: yapımın wiki önbelleğini (bellek + kalıcı, olumlu veya
     * olumsuz) siler ki çözümleme gerçekten yeniden yapılsın. Kullanıcı kararları korunur.
     */
    suspend fun forgetMediaWiki(context: Context, query: FandomMediaQuery) {
        val appContext = context.applicationContext
        val mediaKey = query.cacheKey() ?: return
        val resolutionKey = query.resolutionKey() ?: mediaKey
        val keys = listOf(
            wikiCacheKey(resolutionKey, nameAllowed = true),
            wikiCacheKey(resolutionKey, nameAllowed = false)
        )
        keys.forEach { memory.remove(it) }
        val dao = dao(appContext) ?: return
        keys.forEach { key -> runCatchingDb { dao.deleteDetail(key) } }
    }

    // ── Evren rehberi ve kısa açıklamalar (Faz 2) ────────────────────────────

    /**
     * Yapım wiki'sinin evren rehberi: grup başına tek dolu kategori seçilir ve üyeleri
     * çekilir. Kategori var/yok kararı tek `categoryinfo` isteğiyle toplu verilir.
     * Ağ hatasında boş rehber döner (hata önbelleğe yazılmaz).
     */
    suspend fun loadWorldGuide(context: Context, wikiId: String): FandomWorldGuide {
        val appContext = context.applicationContext
        // v2: rehber kayıtları artık `cmcontinue` jetonunu da taşıyor. Eski kayıtlarda
        // jeton olmadığı için ("Devamını göster" çalışmazdı) anahtar sürümlendi.
        val key = "${KEY_PREFIX}guide:v2:$wikiId"
        return lockFor(sectionLocks, key).withLock {
            val cached = readCached(appContext, key, positiveTtl = GUIDE_TTL_MS) { body ->
                json.decodeFromString(StoredGuide.serializer(), body).guide
            }
            (cached?.value as? FandomWorldGuide)?.let { return@withLock it }
            val guide = try {
                buildWorldGuide(wikiId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Evren rehberi yüklenemedi ($wikiId): ${e.message}")
                null
            }
            if (guide != null) {
                writeCached(
                    context = appContext,
                    key = key,
                    value = guide,
                    encoded = json.encodeToString(StoredGuide.serializer(), StoredGuide(guide)),
                    persist = true,
                    ttl = GUIDE_TTL_MS
                )
            }
            guide ?: FandomWorldGuide()
        }
    }

    private suspend fun buildWorldGuide(wikiId: String): FandomWorldGuide? {
        val info = FandomWikiResolver.siteInfoCached(wikiId) ?: return null
        val catalog = FandomGuideCatalog.allCandidates(info.lang)
        val categoryTitles = catalog.values.flatten().distinct().map { "Category:$it" }
        if (categoryTitles.isEmpty()) return FandomWorldGuide()
        val existing = FandomApi.categoryInfo(wikiId, categoryTitles)
        val claimed = mutableSetOf<String>()
        val groups = catalog.mapNotNull { (group, names) ->
            val resolved = existing.entries.firstOrNull { (title, size) ->
                size > 0 &&
                    title !in claimed &&
                    names.any { title.equals("Category:$it", ignoreCase = true) }
            }?.key ?: return@mapNotNull null
            claimed += resolved
            // İlk sayfa + `cmcontinue` jetonu: "Devamını göster" sonraki 14'lük sayfayı çeker.
            val page = FandomApi.categoryMembersPage(wikiId, resolved, limit = GUIDE_PAGE_SIZE)
            if (page.items.isEmpty()) {
                null
            } else {
                FandomGuideGroupContent(
                    group = group,
                    category = resolved.removePrefix("Category:"),
                    items = page.items.map(::FandomGuideItem),
                    nextToken = page.nextToken
                )
            }
        }
        return FandomWorldGuide(groups)
    }

    /**
     * Evren rehberi grubunun sonraki sayfası ("Devamını göster").
     *
     * Rehberin ilk sayfası kalıcı önbellektedir; devamı ise yalnızca oturum belleğine
     * eklenir (VM durumunda tutulur) — bu yüzden önbellek yazımı yapılmaz, yalnızca
     * ağ isteği yapılır. Ağ hatasında `null` döner ve kullanıcı yeniden deneyebilir.
     */
    suspend fun loadMoreGuideItems(
        context: Context,
        wikiId: String,
        category: String,
        continueToken: String?
    ): FandomCategoryMembersPage? {
        if (continueToken.isNullOrBlank() || category.isBlank()) return null
        val title = if (category.startsWith("Category:")) category else "Category:$category"
        return try {
            FandomApi.categoryMembersPage(
                wikiId = wikiId,
                categoryTitle = title,
                limit = GUIDE_PAGE_SIZE,
                continueToken = continueToken
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Rehber devamı yüklenemedi ($wikiId/$category): ${e.message}")
            null
        }
    }

    /**
     * Bir rehber grubunun KALAN tüm sayfalarını tek seferde çeker ("Devamını göster"
     * artık her dokunuşta bir sonraki sayfa değil, tümünü sırayla çeker). Sonuçlar
     * tek bir `FandomCategoryMembersPage` olarak birleştirilir; ağ hatasında şimdiye
     * kadar çekilenler döndürülür, hata bayrağı için `null` bırakılır (çağıran VM
     * istisna durumunu mevcut state'den ayırt eder).
     */
    suspend fun loadAllRemainingGuideItems(
        wikiId: String,
        category: String,
        startToken: String?
    ): FandomCategoryMembersPage {
        if (startToken.isNullOrBlank() || category.isBlank()) return FandomCategoryMembersPage()
        val title = if (category.startsWith("Category:")) category else "Category:$category"
        val all = ArrayList<String>()
        var token: String? = startToken
        var safety = 0
        try {
            while (!token.isNullOrBlank() && safety < 40) {
                val page = FandomApi.categoryMembersPage(
                    wikiId = wikiId,
                    categoryTitle = title,
                    limit = GUIDE_PAGE_SIZE,
                    continueToken = token
                )
                all += page.items
                token = page.nextToken
                safety++
                if (token.isNullOrBlank()) break
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Rehber tüm sayfalar yüklenemedi ($wikiId/$category): ${e.message}")
        }
        return FandomCategoryMembersPage(items = all, nextToken = token)
    }

    /**
     * Uygulama içi wiki okuyucu için bir sayfanın üst verileri (başlık, kapak, açıklama,
     * içindekiler). Kısa ömürlü bellek önbelleği kullanır; kalıcı yazmaz.
     */
    suspend fun loadFullPageMeta(context: Context, wikiId: String, title: String): FandomFullPage? {
        val appContext = context.applicationContext
        val clean = title.trim()
        if (clean.isEmpty()) return null
        val key = "${KEY_PREFIX}meta:$wikiId|${clean.lowercase()}"
        return lockFor(sectionLocks, key).withLock {
            memory[key]?.let { memo ->
                if (memo.expiresAt > System.currentTimeMillis()) {
                    @Suppress("UNCHECKED_CAST")
                    return@withLock (memo.value as? FandomFullPage)
                }
                memory.remove(key)
            }
            val meta = try {
                FandomApi.pageMeta(wikiId, clean)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Sayfa meta yüklenemedi ($wikiId/$clean): ${e.message}")
                null
            } ?: return@withLock null
            memory[key] = Memo(meta, System.currentTimeMillis() + BLURB_TTL_MS)
            meta
        }
    }

    /**
     * "İlgili wiki sayfaları" kartları için önizleme listesi: başlık, küçük açıklama,
     * kapak. Yalnızca oturum belleğinde tutulur.
     */
    suspend fun loadRelatedPages(context: Context, wikiId: String, titles: List<String>): List<FandomRelatedPage> {
        val appContext = context.applicationContext
        val clean = titles.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (clean.isEmpty()) return emptyList()
        val key = "${KEY_PREFIX}rel:$wikiId|${clean.sorted().joinToString("|").lowercase().hashCode()}"
        memory[key]?.let { memo ->
            if (memo.expiresAt > System.currentTimeMillis()) {
                @Suppress("UNCHECKED_CAST")
                return (memo.value as? List<FandomRelatedPage>).orEmpty()
            }
            memory.remove(key)
        }
        val pages = try {
            FandomApi.pagePreviews(wikiId, clean, thumbWidth = 320)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "İlgili sayfalar yüklenemedi ($wikiId): ${e.message}")
            emptyList()
        }
        memory[key] = Memo(pages, System.currentTimeMillis() + SEARCH_MEMO_TTL_MS)
        return pages
    }

    /** Bir sayfanın kısa açıklaması (`fandomdescription`); yönlendirmeler çözülür. */
    suspend fun loadPageBlurb(context: Context, wikiId: String, title: String): FandomPageBlurb? {
        val appContext = context.applicationContext
        val clean = title.trim()
        if (clean.isEmpty()) return null
        val key = "${KEY_PREFIX}blurb:$wikiId|${clean.lowercase()}"
        return lockFor(sectionLocks, key).withLock {
            val cached = readCached(appContext, key, positiveTtl = BLURB_TTL_MS) { body ->
                json.decodeFromString(StoredBlurb.serializer(), body).blurb
            }
            (cached?.value as? FandomPageBlurb)?.let { return@withLock it }
            val lookup = try {
                FandomApi.lookupTitles(wikiId, listOf(clean))[clean]
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Sayfa açıklaması yüklenemedi ($wikiId/$clean): ${e.message}")
                null
            }
            val blurb = lookup?.takeIf { it.exists }?.let {
                FandomPageBlurb(title = clean, resolvedTitle = it.resolvedTitle, description = it.description)
            } ?: return@withLock null
            writeCached(
                context = appContext,
                key = key,
                value = blurb,
                encoded = json.encodeToString(StoredBlurb.serializer(), StoredBlurb(blurb)),
                persist = true,
                ttl = BLURB_TTL_MS
            )
            blurb
        }
    }

    /** Wiki ana sayfasının kısa açıklaması — yapım sekmesinde "seri özeti". */
    suspend fun loadMainPageBlurb(context: Context, wikiId: String): FandomPageBlurb? {
        val mainPage = FandomWikiResolver.siteInfoCached(wikiId)?.mainPage ?: return null
        return loadPageBlurb(context, wikiId, mainPage)
    }

    /**
     * Wiki içi arama (arama kutusu). Kalıcı önbelleğe yazılmaz; aynı sorgu süreç boyunca
     * kısa bir süre bellekten servis edilir (yazım düzeltirken tekrar tekrar istek atılmaz).
     */
    suspend fun searchWiki(context: Context, wikiId: String, query: String): List<FandomSearchHit> {
        val clean = query.trim()
        if (clean.isBlank()) return emptyList()
        val key = "search:$wikiId|${clean.lowercase()}"
        memory[key]?.let { memo ->
            if (memo.expiresAt > System.currentTimeMillis()) {
                @Suppress("UNCHECKED_CAST")
                return (memo.value as? List<FandomSearchHit>).orEmpty()
            }
            memory.remove(key)
        }
        val hits = FandomApi.searchHits(wikiId, clean, limit = 8)
        memory[key] = Memo(hits, System.currentTimeMillis() + SEARCH_MEMO_TTL_MS)
        return hits
    }

    /**
     * Wiki makalesinin uygulama içi okuma görünümü: açıklama + infobox + içindekiler.
     * Makale gövdeleri burada topluca indirilmez; bölümler [loadSection] ile kullanıcı
     * açtıkça alınır. Böylece büyük wiki sayfalarında gereksiz veri ve görsel trafiği olmaz.
     */
    suspend fun loadArticle(context: Context, wikiId: String, title: String): FandomArticlePage? {
        val appContext = context.applicationContext
        val clean = title.trim()
        if (clean.isEmpty()) return null
        val key = "${KEY_PREFIX}article:v1:$wikiId|${clean.lowercase()}"
        return lockFor(sectionLocks, key).withLock {
            val cached = readCached(appContext, key, positiveTtl = ARTICLE_TTL_MS) { body ->
                json.decodeFromString(StoredArticle.serializer(), body).article
            }
            if (cached != null) return@withLock cached.value as? FandomArticlePage

            val failuresBefore = FandomHttp.failureCount()
            val lookup = try {
                FandomApi.lookupTitles(wikiId, listOf(clean))[clean]
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Makale özeti yüklenemedi ($wikiId/$clean): ${e.message}")
                null
            } ?: return@withLock null

            if (!lookup.exists) {
                val transientFailure = FandomHttp.failureCount() != failuresBefore
                if (!transientFailure) {
                    writeCached(
                        context = appContext,
                        key = key,
                        value = null,
                        encoded = json.encodeToString(StoredArticle.serializer(), StoredArticle()),
                        persist = true,
                        ttl = NEGATIVE_TTL_MS
                    )
                }
                return@withLock null
            }

            val pageTitle = lookup.resolvedTitle ?: return@withLock null
            val sections = try {
                FandomApi.sections(wikiId, pageTitle)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Makale içindekileri yüklenemedi ($wikiId/$pageTitle): ${e.message}")
                null
            }
            val article = FandomArticlePage(
                wikiId = wikiId,
                pageTitle = pageTitle,
                siteName = FandomWikiResolver.siteInfoCached(wikiId)?.siteName,
                description = lookup.description?.let { FandomNameVariants.cleanDescription(it) }?.takeIf { it.isNotBlank() },
                infobox = FandomInfoboxParser.parse(lookup.infoboxJson, wikiId),
                sections = sections.orEmpty(),
                sectionsFailed = sections == null
            )
            // An incomplete TOC is shown with a retry action, but is not cached so that retry
            // can actually reach MediaWiki again instead of replaying the same partial result.
            if (sections == null) return@withLock article
            writeCached(
                context = appContext,
                key = key,
                value = article,
                encoded = json.encodeToString(StoredArticle.serializer(), StoredArticle(article)),
                persist = true,
                ttl = ARTICLE_TTL_MS
            )
            article
        }
    }

    // ── Yapım wiki maddesi (bilgi kutusu + bütün bölümler) ───────────────────

    /**
     * Resolve and cache the actual Fandom article for a media item. The wiki itself has already
     * been identity-validated by [resolveMediaWiki]; this method matches the item's title inside
     * that wiki, rejects disambiguation pages, prefers an existing Turkish language link, and
     * returns the same rich page model used by the character article UI.
     */
    suspend fun resolveMediaArticle(
        context: Context,
        query: FandomMediaQuery,
        ref: FandomWikiRef
    ): FandomCharacterPage? {
        val appContext = context.applicationContext
        val mediaKey = query.cacheKey() ?: return null
        val resolutionKey = query.resolutionKey() ?: mediaKey
        val prefs = FandomWikiPrefs.state(appContext)
        val cacheKey = mediaArticleCacheKey(resolutionKey, ref.wikiId, prefs.preferTurkish)

        return lockFor(characterLocks, cacheKey).withLock {
            val cached = readCached(appContext, cacheKey, positiveTtl = PAGE_TTL_MS) { body ->
                json.decodeFromString(StoredCharacter.serializer(), body).page
            }
            if (cached != null) {
                val page = cached.value as? FandomCharacterPage ?: return@withLock null
                return@withLock page.withMediaRef(ref, mediaKey)
            }

            val failuresBefore = FandomHttp.failureCount()
            val page = try {
                buildMediaArticle(appContext, query, ref, mediaKey, prefs.preferTurkish)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Yapım wiki maddesi yüklenemedi ($mediaKey/${ref.wikiId}): ${e.message}")
                null
            }
            val transientFailure = FandomHttp.failureCount() != failuresBefore
            writeCached(
                context = appContext,
                key = cacheKey,
                value = page,
                encoded = json.encodeToString(StoredCharacter.serializer(), StoredCharacter(page)),
                persist = !transientFailure,
                ttl = when {
                    transientFailure -> TRANSIENT_NEGATIVE_TTL_MS
                    page != null -> PAGE_TTL_MS
                    else -> NEGATIVE_TTL_MS
                }
            )
            page?.withMediaRef(ref, mediaKey)
        }
    }

    suspend fun forgetMediaArticle(context: Context, query: FandomMediaQuery, wikiId: String) {
        val mediaKey = query.cacheKey() ?: return
        val resolutionKey = query.resolutionKey() ?: mediaKey
        val keys = listOf(
            mediaArticleCacheKey(resolutionKey, wikiId, preferTurkish = true),
            mediaArticleCacheKey(resolutionKey, wikiId, preferTurkish = false)
        )
        keys.forEach { memory.remove(it) }
        val dao = dao(context.applicationContext) ?: return
        keys.forEach { key -> runCatchingDb { dao.deleteDetail(key) } }
    }

    private fun mediaArticleCacheKey(mediaKey: String, wikiId: String, preferTurkish: Boolean): String =
        "${KEY_PREFIX}media-page:v5:$mediaKey:$wikiId:${if (preferTurkish) "tr1" else "tr0"}"

    private suspend fun buildMediaArticle(
        context: Context,
        query: FandomMediaQuery,
        ref: FandomWikiRef,
        mediaKey: String,
        preferTurkish: Boolean
    ): FandomCharacterPage? {
        var sourceTitles = query.fallbackTitles
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy(FandomNameVariants::normalizeForCompare)
        var catalogTitlesLoaded = false
        var titleCandidates = FandomNameVariants.mediaTitleVariants(sourceTitles)
        // Aynı adı taşıyan yapımlar Fandom'da "Ad (YIL)" ile ayrılır: düz "Resident Evil"
        // 2002 oyununun makalesidir, 2026 dizisi "Resident Evil (2026)"dır. Yayın yılı
        // biliniyorsa önce yıllı başlık denenir; düz ad çakışıp yanlış makaleyi ya da
        // "bulunamadı" sonucunu üretmesin.
        val yearMatch: FandomMediaArticleMatcher.Match? = query.releaseYear?.let { year ->
            val yearTitles = sourceTitles.take(3).map { "$it ($year)" }.distinct()
            if (yearTitles.isEmpty()) {
                null
            } else {
                FandomApi.lookupTitles(
                    wikiId = ref.wikiId,
                    titles = yearTitles,
                    withTurkishLink = preferTurkish
                ).values
                    .firstOrNull { lookup ->
                        lookup.exists && !lookup.isDisambiguation &&
                            lookup.resolvedTitle?.contains("($year)") == true
                    }
                    ?.let { FandomMediaArticleMatcher.Match(it, FandomConfidence.CERTAIN, 1.0) }
            }
        }
        var match: FandomMediaArticleMatcher.Match? = ref.pageTitle
            ?.takeIf { it.isNotBlank() }
            ?.let { title ->
                FandomApi.lookupTitles(
                    wikiId = ref.wikiId,
                    titles = listOf(title),
                    withTurkishLink = preferTurkish
                )[title]
            }
            ?.takeIf { it.exists && !it.isDisambiguation }
            ?.let { FandomMediaArticleMatcher.Match(it, FandomConfidence.CERTAIN, 1.0) }
        match = yearMatch ?: match
        if (titleCandidates.isEmpty() && match == null &&
            (query.ids.aniListId != null || query.ids.malId != null)
        ) {
            val catalogTitles = FandomAniListSource.load(query.ids.aniListId, query.ids.malId, query.isManga)
                ?.let { evidence -> evidence.titles + listOfNotNull(evidence.nativeTitle) }
                .orEmpty()
            catalogTitlesLoaded = true
            sourceTitles = catalogTitles.distinctBy(FandomNameVariants::normalizeForCompare)
            titleCandidates = FandomNameVariants.mediaTitleVariants(sourceTitles)
        }
        if (titleCandidates.isEmpty() && match == null) return null

        var directLookups: Map<String, FandomTitleLookup> = if (titleCandidates.isEmpty() || match != null) {
            emptyMap()
        } else {
            FandomApi.lookupTitles(
                wikiId = ref.wikiId,
                titles = titleCandidates,
                withTurkishLink = preferTurkish
            )
        }
        if (match == null) match = FandomMediaArticleMatcher.choose(sourceTitles, directLookups.values)

        // The detail screen already supplies cross-provider titles. Fetch AniList aliases only
        // if those did not resolve an article, avoiding an extra catalog request on the hot path.
        if (match == null && !catalogTitlesLoaded &&
            (query.ids.aniListId != null || query.ids.malId != null)
        ) {
            val catalogTitles = FandomAniListSource.load(query.ids.aniListId, query.ids.malId, query.isManga)
                ?.let { evidence -> evidence.titles + listOfNotNull(evidence.nativeTitle) }
                .orEmpty()
            if (catalogTitles.isNotEmpty()) {
                sourceTitles = (catalogTitles + sourceTitles)
                    .distinctBy(FandomNameVariants::normalizeForCompare)
                titleCandidates = FandomNameVariants.mediaTitleVariants(sourceTitles)
                directLookups = FandomApi.lookupTitles(
                    wikiId = ref.wikiId,
                    titles = titleCandidates,
                    withTurkishLink = preferTurkish
                )
                match = FandomMediaArticleMatcher.choose(sourceTitles, directLookups.values)
            }
        }
        if (match == null) {
            val searchTitles = coroutineScope {
                titleCandidates.take(4)
                    .map { title -> async { FandomApi.searchPages(ref.wikiId, title, limit = 6) } }
                    .awaitAll()
                    .flatten()
                    .distinct()
                    .take(24)
            }
            if (searchTitles.isNotEmpty()) {
                val searched = FandomApi.lookupTitles(
                    wikiId = ref.wikiId,
                    titles = searchTitles,
                    withTurkishLink = preferTurkish
                )
                match = FandomMediaArticleMatcher.choose(sourceTitles, searched.values)
            }
        }
        val selected = match ?: return null
        var wikiId = ref.wikiId
        var lookup = selected.lookup
        var siteName = ref.siteName
        var englishWikiId: String? = null
        var englishPageTitle: String? = null
        val englishTitle = lookup.resolvedTitle ?: return null

        if (preferTurkish && FandomUrls.languageOf(wikiId) != "tr") {
            val turkishLink = lookup.turkishLink ?: run {
                FandomApi.lookupTitles(ref.wikiId, listOf(englishTitle), withTurkishLink = true)[englishTitle]?.turkishLink
            }
            if (turkishLink != null) {
                val (trWiki, trTitle) = turkishLink
                val trLookup = FandomApi.lookupTitles(trWiki, listOf(trTitle))[trTitle]
                // Some Turkish pages contain their useful content only in article sections;
                // the language link itself confirms the page exists, and those sections are loaded below.
                val usableTurkishPage = trLookup != null && trLookup.exists && !trLookup.isDisambiguation
                if (usableTurkishPage && trLookup != null) {
                    englishWikiId = wikiId
                    englishPageTitle = englishTitle
                    wikiId = trWiki
                    lookup = trLookup
                    siteName = null
                }
            }
        }

        val pageTitle = lookup.resolvedTitle ?: return null
        val sections = FandomApi.sections(wikiId, pageTitle).orEmpty()
        val resolvedSiteName = siteName ?: FandomWikiResolver.siteInfoCached(wikiId)?.siteName
        val articleConfidence = if (selected.confidence == FandomConfidence.PROBABLE ||
            ref.confidence == FandomConfidence.PROBABLE
        ) FandomConfidence.PROBABLE else FandomConfidence.CERTAIN
        val page = FandomCharacterPage(
            wikiId = wikiId,
            pageTitle = pageTitle,
            siteName = resolvedSiteName,
            description = lookup.description
                ?.let(FandomNameVariants::cleanDescription)
                ?.takeIf { it.isNotBlank() },
            infobox = FandomInfoboxParser.parse(lookup.infoboxJson, wikiId),
            sections = sections,
            matchSource = ref.source,
            confidence = articleConfidence,
            englishWikiId = englishWikiId,
            englishPageTitle = englishPageTitle,
            mediaKey = mediaKey,
            wikiSource = ref.source,
            wikiConfidence = ref.confidence
        )
        return enrichArticlePage(context.applicationContext, page)
    }

    private fun FandomCharacterPage.withMediaRef(ref: FandomWikiRef, mediaKey: String): FandomCharacterPage {
        val combinedConfidence = when {
            // An explicit user confirmation covers the selected article as well as the wiki.
            ref.source == FandomMatchSource.USER -> FandomConfidence.CERTAIN
            confidence == FandomConfidence.PROBABLE || ref.confidence == FandomConfidence.PROBABLE -> FandomConfidence.PROBABLE
            else -> FandomConfidence.CERTAIN
        }
        return copy(
            matchSource = ref.source,
            confidence = combinedConfidence,
            mediaKey = mediaKey,
            wikiSource = ref.source,
            wikiConfidence = ref.confidence,
            siteName = siteName ?: ref.siteName
        )
    }

    // ── Karakter → sayfa ─────────────────────────────────────────────────────

    /**
     * [forceRefresh] = "Tekrar dene": karakter ve yapım wiki önbellekleri (olumsuz kayıtlar
     * dahil) okunmaz; çözümleme gerçekten baştan yapılır.
     */
    suspend fun resolveCharacterPage(
        context: Context,
        query: FandomCharacterQuery,
        forceRefresh: Boolean = false
    ): FandomCharacterPage? {
        val appContext = context.applicationContext
        val prefs = FandomWikiPrefs.state(appContext)
        if (!prefs.enabled) return null
        val characterKey = query.cacheKey()
        val cacheKey = characterCacheKey(characterKey, prefs)

        return lockFor(characterLocks, cacheKey).withLock {
            val cached = if (forceRefresh) null else readCached(appContext, cacheKey, positiveTtl = PAGE_TTL_MS) { body ->
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
                resolver.resolve(query, forceRefresh)?.let { buildPage(appContext, it, prefs) }?.let { applyAcceptance(appContext, characterKey, it) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Karakter sayfası çözümlemesi başarısız ($characterKey): ${e.message}")
                null
            }
            val transientFailure = FandomHttp.failureCount() != failuresBefore
            writeCached(
                context = appContext,
                key = cacheKey,
                value = page,
                encoded = json.encodeToString(StoredCharacter.serializer(), StoredCharacter(page)),
                persist = !transientFailure,
                ttl = when {
                    transientFailure -> TRANSIENT_NEGATIVE_TTL_MS
                    page != null -> PAGE_TTL_MS
                    else -> NEGATIVE_TTL_MS
                }
            )
            page
        }
    }

    private fun characterCacheKey(characterKey: String, prefs: FandomWikiPrefs.State): String =
        "${KEY_PREFIX}char:v4:$characterKey:" +
            (if (prefs.preferTurkish) "tr1" else "tr0") + ":" +
            (if (prefs.nameFallback) "n1" else "n0")

    /** Sayfa özetini oluşturur; tercih açıksa ve sayfanın Türkçe karşılığı doluysa onu kullanır. */
    private suspend fun buildPage(
        context: Context,
        resolved: FandomResolvedCharacter,
        prefs: FandomWikiPrefs.State
    ): FandomCharacterPage? {
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
                val usableTurkishPage = trLookup != null && trLookup.exists && !trLookup.isDisambiguation
                if (usableTurkishPage && trLookup != null) {
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
        val page = FandomCharacterPage(
            wikiId = wikiId,
            pageTitle = title,
            siteName = resolvedSiteName,
            description = lookup.description?.let { FandomNameVariants.cleanDescription(it) }?.takeIf { it.isNotBlank() },
            infobox = FandomInfoboxParser.parse(lookup.infoboxJson, wikiId),
            sections = sections,
            matchSource = resolved.source,
            confidence = resolved.confidence,
            englishWikiId = englishWikiId,
            englishPageTitle = englishPageTitle,
            mediaKey = resolved.mediaKey,
            wikiSource = resolved.wikiSource,
            wikiConfidence = resolved.wikiConfidence
        )
        return enrichArticlePage(context.applicationContext, page)
    }

    /**
     * Build a small, typed relationship graph from linked infobox values and the Wiki's own
     * cast/character/staff sections; also resolve page/gallery thumbnails through imageinfo.
     */
    private suspend fun enrichArticlePage(context: Context, rawPage: FandomCharacterPage): FandomCharacterPage {
        val page = applyLead(context, rawPage)
        val relationSections = page.sections.mapNotNull { section ->
            FandomRelationClassifier.forSection(section.title)?.let { section to it }
        }
        val (galleryImages, sectionLinks) = coroutineScope {
            val imageDeferred = async {
                try {
                    FandomApi.pageImages(page.wikiId, page.pageTitle)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Wiki görselleri yüklenemedi (${page.wikiId}/${page.pageTitle}): ${e.message}")
                    emptyList()
                }
            }
            val linkDeferred = relationSections.map { (section, _) ->
                async {
                    try {
                        loadSection(context, page.wikiId, page.pageTitle, section.index, section.title)
                            ?.linkedEntities.orEmpty()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w(TAG, "Wiki ilişkileri çıkarılamadı (${page.pageTitle}/${section.title}): ${e.message}")
                        emptyList()
                    }
                }
            }
            imageDeferred.await() to linkDeferred.awaitAll().flatten()
        }
        val infoboxLinks = page.infobox?.allFields()?.flatMap { it.links }.orEmpty()
        val currentPageTitle = FandomNameVariants.normalizeForCompare(page.pageTitle)
        val links = (infoboxLinks + sectionLinks)
            .distinctBy(::relationKey)
            .filterNot {
                it.wikiId == page.wikiId &&
                    FandomNameVariants.normalizeForCompare(it.pageTitle) == currentPageTitle
            }
        if (links.isEmpty()) {
            return page.copy(galleryImages = galleryImages)
        }

        val lookupsByWiki = coroutineScope {
            links.groupBy { it.wikiId }.map { (wikiId, wikiLinks) ->
                async {
                    val lookups = try {
                        FandomApi.lookupTitles(wikiId, wikiLinks.map { it.pageTitle }.distinct())
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w(TAG, "İlişkili Wiki maddeleri çözümlenemedi ($wikiId): ${e.message}")
                        emptyMap()
                    }
                    wikiId to lookups
                }
            }.awaitAll().toMap()
        }

        fun enrich(link: FandomWikiLink): FandomWikiLink? {
            val lookup = lookupsByWiki[link.wikiId]?.get(link.pageTitle)
            if (lookup != null && (!lookup.exists || lookup.isDisambiguation)) return null
            return link.copy(
                pageTitle = lookup?.resolvedTitle ?: link.pageTitle,
                description = lookup?.description?.let(FandomNameVariants::cleanDescription),
                imageUrl = lookup?.thumbnailUrl
            )
        }
        val relatedEntities = links.mapNotNull(::enrich)
        val enrichedInfobox = page.infobox?.let { infobox ->
            infobox.copy(groups = infobox.groups.map { group ->
                group.copy(fields = group.fields.map { field ->
                    field.copy(links = field.links.mapNotNull(::enrich))
                })
            })
        }
        return page.copy(
            infobox = enrichedInfobox,
            galleryImages = galleryImages,
            relatedEntities = relatedEntities
        )
    }

    /**
     * Giriş bölümünü (section 0) çeker; tam giriş metnini ve eksiksiz bilgi kutusunu sayfaya işler.
     * Ağ/ayrıştırma hatasında sayfa olduğu gibi kalır (yalnızca eski davranış).
     */
    private suspend fun applyLead(context: Context, page: FandomCharacterPage): FandomCharacterPage {
        val lead = try {
            loadLead(context, page.wikiId, page.pageTitle)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Wiki girişi yüklenemedi (${page.wikiId}/${page.pageTitle}): ${e.message}")
            null
        } ?: return page
        return page.copy(
            description = FandomLeadParser.mergeDescription(page.description, lead.intro),
            infobox = FandomLeadParser.mergeInfobox(page.infobox, lead.infobox)
        )
    }

    @Serializable
    private data class StoredLead(
        val infobox: FandomInfobox? = null,
        val intro: String? = null,
        val found: Boolean = false
    )

    private suspend fun loadLead(context: Context, wikiId: String, pageTitle: String): FandomLeadParser.Lead? {
        val appContext = context.applicationContext
        val key = "${KEY_PREFIX}lead:v1:$wikiId|$pageTitle"
        return lockFor(sectionLocks, key).withLock {
            val cached = readCached(appContext, key, positiveTtl = SECTION_TTL_MS) { body ->
                json.decodeFromString(StoredLead.serializer(), body).takeIf { it.found }
            }
            (cached?.value as? StoredLead)?.let { return@withLock FandomLeadParser.Lead(it.infobox, it.intro) }
            if (cached != null) return@withLock null
            val failuresBefore = FandomHttp.failureCount()
            val html = FandomApi.sectionHtml(wikiId, pageTitle, 0)
            if (html == null) return@withLock null
            val lead = FandomLeadParser.parse(html, wikiId)
            val stored = StoredLead(lead.infobox, lead.intro, found = !lead.isEmpty)
            writeCached(
                context = appContext,
                key = key,
                value = stored.takeIf { it.found },
                encoded = json.encodeToString(StoredLead.serializer(), stored),
                persist = FandomHttp.failureCount() == failuresBefore,
                ttl = if (stored.found) SECTION_TTL_MS else NEGATIVE_TTL_MS
            )
            lead.takeUnless { it.isEmpty }
        }
    }

    private fun relationKey(link: FandomWikiLink): String =
        "${link.wikiId}|${FandomNameVariants.normalizeForCompare(link.pageTitle)}|${link.role}|${link.language.orEmpty()}"

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

    suspend fun loadSection(
        context: Context,
        wikiId: String,
        pageTitle: String,
        index: Int,
        sectionTitle: String? = null
    ): FandomSectionContent? {
        val appContext = context.applicationContext
        // v6: kartlar (CARD), bağlantı grupları (LINK_GROUP), bağlantılı görsel hedefleri ve
        // satır içi bağlantılar eklendi; navbox'lar artık düşmüyor. Eski (v5) kayıtlar bu
        // yüzden geçersiz sayılır. (v5 öncesi: yüzde-kodu çözülmemiş bağlantı başlıkları.)
        val cacheKey = "${KEY_PREFIX}sec:v6:$wikiId|$pageTitle|$index"
        return lockFor(sectionLocks, cacheKey).withLock {
            val cached = readCached(appContext, cacheKey, positiveTtl = SECTION_TTL_MS) { body ->
                json.decodeFromString(StoredSection.serializer(), body).content
            }
            val cachedContent = cached?.value as? FandomSectionContent
            if (cachedContent != null) return@withLock cachedContent

            val html = FandomApi.sectionHtml(wikiId, pageTitle, index) ?: return@withLock null
            val relationType = sectionTitle?.let(FandomRelationClassifier::forSection)
                ?: FandomRelationType.RELATED
            val content = FandomSectionContent(
                blocks = FandomHtmlText.blocks(
                    html = html,
                    maxBlocks = FandomHtmlText.SECTION_MAX_BLOCKS,
                    wikiId = wikiId
                ),
                linkedEntities = FandomHtmlText.wikiLinks(
                    html = html,
                    defaultWikiId = wikiId,
                    role = relationType,
                    language = sectionTitle?.let(FandomRelationClassifier::languageHint)
                ),
                links = FandomHtmlText.internalWikiLinks(html)
            )
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
        listOf("tr1:n1", "tr1:n0", "tr0:n1", "tr0:n0").mapTo(keys) { "${KEY_PREFIX}char:v4:$characterKey:$it" }
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

    /**
     * "Hayır, yanlış"ın geri alınması (karakter/kişi sekmesi): sayfa reddi kaldırılır;
     * wiki isimle bulunup kesinleşmemişse wiki reddi de kaldırılır. İlgili önbellek
     * kayıtları düşürülür ki reddedilen sayfa tekrar çözülebilsin.
     */
    suspend fun undoRejectCharacterPage(context: Context, query: FandomCharacterQuery, page: FandomCharacterPage) {
        val appContext = context.applicationContext
        val characterKey = query.cacheKey()
        FandomUserChoices.unrejectPage(appContext, characterKey, page.matchedWikiId, page.matchedPageTitle)
        val keys = mutableListOf<String>()
        listOf("tr1:n1", "tr1:n0", "tr0:n1", "tr0:n0").mapTo(keys) { "${KEY_PREFIX}char:v4:$characterKey:$it" }
        val mediaKey = page.mediaKey
        if (mediaKey != null && page.isWikiUnconfirmedNameMatch) {
            FandomUserChoices.unrejectWiki(appContext, mediaKey, page.matchedWikiId)
            keys += wikiCacheKey(mediaKey, nameAllowed = true)
            keys += wikiCacheKey(mediaKey, nameAllowed = false)
        }
        keys.forEach { memory.remove(it) }
        val dao = dao(appContext) ?: return
        keys.forEach { key -> runCatchingDb { dao.deleteDetail(key) } }
    }

    /**
     * Ayarlardaki "Reddedilen eşleşmeleri geri getir": tüm sayfa/wiki retleri silinir ve
     * Fandom önbelleği düşürülür; böylece reddedilenler her yerde tekrar önerilir.
     * Onaylanan eşleşmeler korunur. Geri alma kartı kapatılmış olsa bile kurtarma yolu budur.
     */
    suspend fun restoreRejectedMatches(context: Context) {
        val appContext = context.applicationContext
        memory.clear()
        FandomUserChoices.clearRejections(appContext)
        val dao = dao(appContext) ?: return
        runCatchingDb { dao.deleteByPrefix(KEY_PREFIX) }
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
