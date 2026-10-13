package com.kitsugi.animelist.data.fandom

import kotlin.math.log10

/** Fandom topluluk araması (veya adres denemesi) ile bulunan wiki adayı. */
data class FandomCommunityHit(
    val wikiId: String,
    val name: String,
    val language: String? = null,
    /** Fandom kategorisi: anime / games / tv / books / movies / lifestyle … */
    val hub: String? = null,
    val pageCount: Int? = null,
    val pageViews: Int? = null,
    val description: String? = null
)

/**
 * Bir aday wiki'de kaynaktaki karakterlerin bulunma durumu ("karakter parmak izi").
 *
 * @property nativeConfirmed Bilgi kutusunda/özette kaynaktaki yerel adı (Kanji/Hangul)
 *           birebir geçen karakter sayısı — isimden bağımsız en güçlü kanıt.
 */
data class FandomFingerprint(
    val mainTotal: Int,
    val mainFound: Int,
    val anyTotal: Int,
    val anyFound: Int,
    val nativeConfirmed: Int,
    val creatorPageFound: Boolean = false
)

/** İsim yolunda doğrulama için kaynaktan (kimlikle) bilinen bilgiler. */
data class FandomNameEvidence(
    val titles: List<String>,
    val creators: List<String> = emptyList(),
    val characterNames: List<String> = emptyList(),
    val isManga: Boolean = false
)

enum class FandomDecision { CERTAIN, PROBABLE, REJECT }

data class FandomNameScore(
    val wikiId: String,
    val score: Double,
    val decision: FandomDecision,
    val vetoReason: String? = null
)

/**
 * Son çare isim yolunun puanlaması: **isimle bul, kimlikle doğrula.**
 *
 * | Kanıt                                   | Ağırlık |
 * |-----------------------------------------|---------|
 * | Ana karakter bulunma oranı              | 0,40    |
 * | Yerel ad (Kanji/Hangul) teyidi          | 0,20    |
 * | Wiki adı ↔ başlık benzerliği            | 0,15    |
 * | Hub uyumu (anime/books/tv)              | 0,10    |
 * | Yazar/stüdyo izi                        | 0,10    |
 * | Büyüklük (makale sayısı)                | 0,05    |
 *
 * Vetolar: kara liste, dil, çok küçük wiki, hayran içerik, ana karakterlerin hiç bulunmaması.
 */
object FandomNameMatcher {

    const val CERTAIN_THRESHOLD = 0.85
    const val PROBABLE_THRESHOLD = 0.65
    const val AMBIGUITY_MARGIN = 0.05

    /**
     * Topluluk aramasının `pageCount` alanı gerçek makale sayısı değil KABA bir kovadır: canlı
     * denetimde 74-87 makaleli (Barakamon, Texhnolyze, Uchuu Kyoudai) wikiler 10 döndürdü. Eski
     * eşik (20) bu gerçek wikileri "tiny" diye eliyordu. Yalnızca kovanın altındaki değerler elenir;
     * gerçek boyut siteinfo ile (kimlik kanıtında) ayrıca doğrulanır.
     */
    private const val MIN_PAGE_COUNT = 10
    private val FAN_CONTENT = Regex("(?i)\\b(fanon|fan ?fic(tion)?|role ?play|rp|simulator|tycoon|roblox)\\b")

    /** Ek istek gerektirmeyen ön eleme. Veto nedeni ya da null döner. */
    fun preFilterVeto(hit: FandomCommunityHit): String? {
        if (FandomCandidateSelector.isBlocked(hit.wikiId)) return "blocked"
        // Localized Fandom communities are valid targets too; article/entity evidence, not locale,
        // decides whether the candidate represents this item.
        val pages = hit.pageCount
        if (pages != null && pages < MIN_PAGE_COUNT) return "tiny"
        if (FAN_CONTENT.containsMatchIn(hit.name)) return "fan-content"
        return null
    }

    /**
     * Wiki adı ile başlıklar arasındaki kelime örtüşmesi. Wiki adındaki yazar adı
     * ("Naoki Urasawa's Monster Wiki") fazladan kelime sayılmaz; başka fazladan kelimeler
     * ("ARISE", "Simulator") benzerliği düşürür.
     */
    fun titleSimilarity(wikiName: String, titles: List<String>, creators: List<String> = emptyList()): Double {
        val wikiTokens = FandomNameVariants.tokens(wikiName).toSet()
        if (wikiTokens.isEmpty()) return 0.0
        val creatorTokens = creators.flatMap { FandomNameVariants.tokens(it) }.toSet()
        var best = 0.0
        for (title in titles) {
            val titleTokens = FandomNameVariants.tokens(title).toSet()
            if (titleTokens.isEmpty()) continue
            val shared = titleTokens.count { it in wikiTokens }
            if (shared == 0) continue
            val extra = wikiTokens.count { it !in titleTokens && it !in creatorTokens }
            val similarity = shared.toDouble() / (titleTokens.size + extra)
            if (similarity > best) best = similarity
        }
        return best.coerceIn(0.0, 1.0)
    }

    fun hubScore(hub: String?, isManga: Boolean): Double = when (hub?.trim()?.lowercase()) {
        "anime" -> 1.0
        "books", "comics" -> if (isManga) 1.0 else 0.7
        "tv", "movies", "film" -> 0.6
        null, "" -> 0.5
        "games", "gaming" -> 0.1
        "lifestyle", "music" -> 0.0
        else -> 0.3
    }

    fun sizeScore(pageCount: Int?): Double {
        if (pageCount == null || pageCount <= 0) return 0.3
        return (log10(pageCount.toDouble()) / 4.0).coerceIn(0.0, 1.0)
    }

    fun creatorTrace(hit: FandomCommunityHit, creators: List<String>, fingerprint: FandomFingerprint?): Boolean {
        if (fingerprint?.creatorPageFound == true) return true
        if (creators.isEmpty()) return false
        val haystack = " " + FandomNameVariants.normalizeForCompare("${hit.name} ${hit.description.orEmpty()}") + " "
        return creators.any { creator ->
            val needle = FandomNameVariants.normalizeForCompare(creator)
            needle.length >= 4 && haystack.contains(" $needle ")
        }
    }

    /**
     * Native-script verification cannot depend on title-token score: an English-named wiki
     * may host an article whose exact title is Japanese/Korean/Chinese. Keep a wider, bounded
     * candidate pool so this exact-page check can run before fingerprint fallback.
     */
    fun nativeTitleVerificationCandidates(
        hits: Collection<FandomCommunityHit>,
        rejectedWikiIds: Set<String> = emptySet(),
        limit: Int = 8
    ): List<FandomCommunityHit> = hits
        .filter { it.wikiId !in rejectedWikiIds && preFilterVeto(it) == null }
        .distinctBy { it.wikiId }
        .take(limit.coerceAtLeast(0))

    /** Parmak izi isteği atılacak adayları sıralamak için ucuz ön puan. */
    fun quickScore(hit: FandomCommunityHit, evidence: FandomNameEvidence): Double {
        val title = titleSimilarity(hit.name, evidence.titles, evidence.creators)
        val hub = hubScore(hit.hub, evidence.isManga)
        val size = sizeScore(hit.pageCount)
        val description = " " + FandomNameVariants.normalizeForCompare(hit.description.orEmpty()) + " "
        val mentions = (evidence.characterNames.take(6) + evidence.creators).count { name ->
            val needle = FandomNameVariants.normalizeForCompare(name)
            needle.length >= 4 && description.contains(" $needle ")
        }
        return 0.5 * title + 0.25 * hub + 0.15 * size + 0.10 * (mentions.coerceAtMost(2) / 2.0)
    }

    fun score(
        hit: FandomCommunityHit,
        evidence: FandomNameEvidence,
        fingerprint: FandomFingerprint?,
        exactLink: Boolean = false
    ): FandomNameScore {
        preFilterVeto(hit)?.let { reason -> return FandomNameScore(hit.wikiId, 0.0, FandomDecision.REJECT, reason) }
        if (exactLink) return FandomNameScore(hit.wikiId, 1.0, FandomDecision.CERTAIN)

        val fp = fingerprint
        if (fp != null) {
            if (fp.mainTotal >= 2 && fp.mainFound == 0) {
                return FandomNameScore(hit.wikiId, 0.0, FandomDecision.REJECT, "no-main-characters")
            }
            if (fp.mainTotal < 2 && fp.anyTotal >= 3 && fp.anyFound == 0) {
                return FandomNameScore(hit.wikiId, 0.0, FandomDecision.REJECT, "no-characters")
            }
        }

        val share = when {
            fp == null -> 0.0
            fp.mainTotal > 0 -> fp.mainFound.toDouble() / fp.mainTotal
            fp.anyTotal > 0 -> fp.anyFound.toDouble() / fp.anyTotal
            else -> 0.0
        }
        val nativeConfirmed = fp?.nativeConfirmed ?: 0
        val native = nativeConfirmed.coerceAtMost(2) / 2.0
        val title = titleSimilarity(hit.name, evidence.titles, evidence.creators)
        val hub = hubScore(hit.hub, evidence.isManga)
        val creator = if (creatorTrace(hit, evidence.creators, fp)) 1.0 else 0.0
        val size = sizeScore(hit.pageCount)
        val total = (0.40 * share + 0.20 * native + 0.15 * title + 0.10 * hub + 0.10 * creator + 0.05 * size)
            .coerceIn(0.0, 1.0)

        val hasEvidence = fp != null && fp.anyTotal > 0
        // Yerel ad yoksa: en az 4 karakter sayfası + yüksek oran + birebir wiki adı + oyun dışı hub.
        val strongWithoutNative = fp != null && fp.anyTotal > 0 && fp.anyFound >= 4 &&
            share >= 0.75 && title >= 0.9 && hub >= 0.5
        // Canlı denetim: başlığı wiki adından farklı (Uchuu Kyoudai = Space Brothers, kiss×sis) ya da
        // az karakter sayfalı wikilerde kanıt ezici olduğu halde toplam puan 0.53-0.59'da kalıp
        // reddediliyordu. Ana karakterlerin TAMAMI + karakterlerin çoğu bulunduysa ya da wiki adı
        // birebir olup karakterlerin yarısı bulunduysa kabul edilir (yalnızca PROBABLE).
        val overwhelmingOverlap = fp != null && hub >= 0.5 && (
            (fp.mainTotal >= 2 && fp.mainFound == fp.mainTotal && fp.anyFound >= 4 &&
                fp.anyFound.toDouble() / fp.anyTotal >= 0.6) ||
                (fp.anyFound >= 3 && fp.anyTotal > 0 && fp.anyFound.toDouble() / fp.anyTotal >= 0.5 && title >= 0.99) ||
                // Yerel (CJK/Hangul) karakter adlarının ≥3'ü wiki makalelerinde doğrulandıysa tesadüf değildir.
                (fp.nativeConfirmed >= 3 && fp.anyTotal > 0 && fp.anyFound.toDouble() / fp.anyTotal >= 0.5)
            )
        val decision = when {
            hasEvidence && total >= CERTAIN_THRESHOLD && nativeConfirmed >= 1 -> FandomDecision.CERTAIN
            strongWithoutNative -> FandomDecision.CERTAIN
            total >= PROBABLE_THRESHOLD -> FandomDecision.PROBABLE
            overwhelmingOverlap -> FandomDecision.PROBABLE
            else -> FandomDecision.REJECT
        }
        return FandomNameScore(hit.wikiId, total, decision)
    }

    /**
     * En iyi adayı seçer. İlk iki aday birbirine çok yakınsa (belirsizlik) kesin sonuç
     * "muhtemel"e düşürülür.
     */
    fun pickBest(scores: List<FandomNameScore>): FandomNameScore? {
        val viable = scores.filter { it.decision != FandomDecision.REJECT }.sortedByDescending { it.score }
        val best = viable.firstOrNull() ?: return null
        val runnerUp = viable.getOrNull(1)
        if (runnerUp != null && best.decision == FandomDecision.CERTAIN && best.score < 1.0 &&
            best.score - runnerUp.score < AMBIGUITY_MARGIN
        ) {
            return best.copy(decision = FandomDecision.PROBABLE)
        }
        return best
    }
}
