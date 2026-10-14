package com.kitsugi.animelist.data.fandom

/**
 * Karakter / ekip / seslendirme sanatçısı wiki eşleşmesi için saf kanıt kuralları.
 *
 * Bir kişi adı (ör. "Kenji Nojima") tek başına kabul edilmez: sayfanın başlığı ad varyantlarıyla
 * uyuşmalı VE özet ya da bilgi kutusunda ya yerel ad (Kanji/Hangul) ya da kişinin çalıştığı bir
 * yapımın adı bulunmalıdır. Böylece aynı adı taşıyan başka bir kişi yanlışlıkla eşleşmez.
 *
 * Bu nesne ağ/Android bağımlılığı taşımaz; kurallar birim testleriyle doğrulanabilir.
 */
internal object FandomPersonMatcher {

    /** Bundan kısa yapım anahtarları genel kelime sayılır ve kanıt olarak kullanılmaz. */
    private const val MIN_WORK_KEY_LENGTH = 4
    private const val MAX_WORK_KEYS = 8
    private const val MAX_NAME_VARIANTS = 6
    private val PARENTHETICAL_SUFFIX = Regex("\\s*\\([^)]*\\)\\s*$")

    /** Kanıt türü: yerel ad, yapım adı ya da hiçbiri. */
    enum class Proof { NATIVE, WORK, NONE }

    /** Yapım adlarının karşılaştırma anahtarları (kısa/boş olanlar elenir, tekrarsız). */
    fun workKeys(titles: List<String>): List<String> =
        titles.asSequence()
            .map { FandomNameVariants.normalizeForCompare(it) }
            .filter { it.length >= MIN_WORK_KEY_LENGTH }
            .distinct()
            .take(MAX_WORK_KEYS)
            .toList()

    /** Kişi adının (Ad Soyad / Soyad Ad / tire ve uzun ünlü varyantları) karşılaştırma anahtarları. */
    fun nameKeys(names: List<String>): Set<String> =
        names.asSequence()
            .flatMap { FandomNameVariants.characterTitleVariants(it, max = MAX_NAME_VARIANTS).asSequence() }
            .map { FandomNameVariants.normalizeForCompare(it) }
            .filter { it.isNotEmpty() }
            .toSet()

    /** Sayfa başlığı (sondaki parantezli nitelik hariç, ör. "(voice actor)") ad varyantlarından biriyle aynı mı? */
    fun titleMatchesName(pageTitle: String, nameKeys: Set<String>): Boolean {
        if (nameKeys.isEmpty()) return false
        val base = pageTitle.replace(PARENTHETICAL_SUFFIX, "")
        return FandomNameVariants.normalizeForCompare(base) in nameKeys
    }

    /** Özet/bilgi kutusu metninde bir yapım adı TAM KELİME olarak geçiyor mu? ("Titans" ≠ "Titan") */
    fun mentionsWork(evidence: String, workKeys: List<String>): Boolean {
        if (workKeys.isEmpty() || evidence.isBlank()) return false
        val haystack = " " + FandomNameVariants.normalizeForCompare(evidence) + " "
        return workKeys.any { key -> key.isNotEmpty() && haystack.contains(" $key ") }
    }

    /**
     * Bir sayfanın kişi/karakterle eşleştiğinin kanıtı.
     * Önce başlık adla uyuşmalı; sonra yerel ad (en güçlü) ya da yapım adı aranır.
     */
    fun proofOf(
        pageTitle: String,
        evidence: String,
        nameKeys: Set<String>,
        nativeName: String?,
        workKeys: List<String>
    ): Proof {
        if (!titleMatchesName(pageTitle, nameKeys)) return Proof.NONE
        if (FandomNameVariants.nativeAppearsIn(nativeName, evidence)) return Proof.NATIVE
        if (mentionsWork(evidence, workKeys)) return Proof.WORK
        return Proof.NONE
    }

    /**
     * Birden çok aday arasından tek bir kazananı seçer.
     * - Yerel adı teyit eden TEK aday varsa o (kesin).
     * - Birden çok yerel-ad adayı belirsizdir → null.
     * - Yerel ad yoksa yalnızca TEK yapım-kanıtlı aday kabul edilir (belirsiz → null).
     */
    fun <T> pickUnique(candidates: List<Pair<T, Proof>>): Pair<T, Proof>? {
        val natives = candidates.filter { it.second == Proof.NATIVE }
        if (natives.size == 1) return natives.single()
        if (natives.isNotEmpty()) return null
        return candidates.filter { it.second == Proof.WORK }.singleOrNull()
    }

    /**
     * Vikipedi'de kişi aramak için sorgular (kişi, seslendirme sanatçısı / ekip).
     * Dile göre meslek ifadesi seçilir; çalıştığı yapımla birlikte aranan sorgu en güçlüsüdür.
     */
    fun personSearchQueries(name: String, works: List<String>, turkish: Boolean, limit: Int = 4): List<String> {
        val out = LinkedHashSet<String>()
        if (turkish) {
            out += "$name seslendirme sanatçısı"
            out += "$name oyuncu"
        } else {
            out += "$name voice actor"
            out += "$name actor"
        }
        works.take(2).forEach { out += "$name $it" }
        return out.take(limit)
    }

    /** Vikipedi'de karakter aramak için sorgular: karakter adı + yapım adı, sonra çıplak ad. */
    fun characterSearchQueries(name: String, works: List<String>, limit: Int = 4): List<String> {
        val out = LinkedHashSet<String>()
        works.take(2).forEach { out += "$name $it" }
        out += name
        return out.take(limit)
    }
}
