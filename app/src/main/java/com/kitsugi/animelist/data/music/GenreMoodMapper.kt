package com.kitsugi.animelist.data.music

/**
 * 🎼 Tür → Duygu Eşleme
 *
 * Manga türlerini 5 boyutlu [MoodProfile] vektörüne çevirir.
 * Öneri motorunun kalbi burasıdır.
 *
 * Tasarım notu: Eşleme tamamen kural tabanlıdır. LLM, sunucu veya internet
 * gerekmez; çevrimdışı ve anlık çalışır. İleride istenirse bir dil modeli
 * katmanı eklenebilir ama v1 için gerekli değil.
 */
object GenreMoodMapper {

    /**
     * Manga kaynaklarında görülen tür adlarının normalize edilmiş eşlemeleri.
     * Anahtar: küçük harfli tür adı (TR + EN birlikte).
     */
    private val genreTable: Map<String, MoodProfile> = mapOf(
        // ── Aksiyon / Dövüş ──────────────────────────────────────────────
        "action" to MoodProfile(0.85f, 0.62f, 0.58f, 0.42f, 0.65f),
        "aksiyon" to MoodProfile(0.85f, 0.62f, 0.58f, 0.42f, 0.65f),
        "shounen" to MoodProfile(0.88f, 0.70f, 0.55f, 0.45f, 0.60f),
        "shonen" to MoodProfile(0.88f, 0.70f, 0.55f, 0.45f, 0.60f),
        "seinen" to MoodProfile(0.60f, 0.40f, 0.55f, 0.40f, 0.70f),
        "martial arts" to MoodProfile(0.82f, 0.60f, 0.55f, 0.50f, 0.60f),
        "dövüş sanatları" to MoodProfile(0.82f, 0.60f, 0.55f, 0.50f, 0.60f),
        "savaş" to MoodProfile(0.85f, 0.35f, 0.75f, 0.35f, 0.55f),
        "war" to MoodProfile(0.85f, 0.35f, 0.75f, 0.35f, 0.55f),
        "military" to MoodProfile(0.80f, 0.35f, 0.70f, 0.35f, 0.60f),

        // ── Romantizm ─────────────────────────────────────────────────────
        "romance" to MoodProfile(0.28f, 0.78f, 0.15f, 0.88f, 0.85f),
        "romantik" to MoodProfile(0.28f, 0.78f, 0.15f, 0.88f, 0.85f),
        "romantizm" to MoodProfile(0.28f, 0.78f, 0.15f, 0.88f, 0.85f),
        "shoujo" to MoodProfile(0.30f, 0.80f, 0.18f, 0.90f, 0.85f),
        "shojo" to MoodProfile(0.30f, 0.80f, 0.18f, 0.90f, 0.85f),
        "josei" to MoodProfile(0.35f, 0.60f, 0.30f, 0.80f, 0.85f),
        "drama" to MoodProfile(0.30f, 0.42f, 0.45f, 0.72f, 0.85f),
        "dram" to MoodProfile(0.30f, 0.42f, 0.45f, 0.72f, 0.85f),

        // ── Günlük Yaşam ──────────────────────────────────────────────────
        "slice of life" to MoodProfile(0.25f, 0.82f, 0.10f, 0.92f, 0.90f),
        "günlük yaşam" to MoodProfile(0.25f, 0.82f, 0.10f, 0.92f, 0.90f),
        "günlük" to MoodProfile(0.25f, 0.82f, 0.10f, 0.92f, 0.90f),
        "iyileşme" to MoodProfile(0.20f, 0.80f, 0.08f, 0.92f, 0.92f),
        "healing" to MoodProfile(0.20f, 0.80f, 0.08f, 0.92f, 0.92f),
        "comedy" to MoodProfile(0.68f, 0.95f, 0.05f, 0.72f, 0.75f),
        "komedi" to MoodProfile(0.68f, 0.95f, 0.05f, 0.72f, 0.75f),
        "school" to MoodProfile(0.40f, 0.75f, 0.15f, 0.82f, 0.85f),
        "okul" to MoodProfile(0.40f, 0.75f, 0.15f, 0.82f, 0.85f),
        "cooking" to MoodProfile(0.30f, 0.85f, 0.10f, 0.90f, 0.88f),
        "yemek" to MoodProfile(0.30f, 0.85f, 0.10f, 0.90f, 0.88f),

        // ── Korku / Gerilim ──────────────────────────────────────────────
        "horror" to MoodProfile(0.32f, 0.10f, 0.96f, 0.12f, 0.70f),
        "korku" to MoodProfile(0.32f, 0.10f, 0.96f, 0.12f, 0.70f),
        "thriller" to MoodProfile(0.42f, 0.20f, 0.92f, 0.20f, 0.70f),
        "gerilim" to MoodProfile(0.42f, 0.20f, 0.92f, 0.20f, 0.70f),
        "psychological" to MoodProfile(0.25f, 0.18f, 0.88f, 0.15f, 0.88f),
        "psikolojik" to MoodProfile(0.25f, 0.18f, 0.88f, 0.15f, 0.88f),
        "mystery" to MoodProfile(0.35f, 0.28f, 0.82f, 0.28f, 0.80f),
        "gizem" to MoodProfile(0.35f, 0.28f, 0.82f, 0.28f, 0.80f),
        "suspense" to MoodProfile(0.38f, 0.22f, 0.88f, 0.22f, 0.75f),
        "gerilimli" to MoodProfile(0.38f, 0.22f, 0.88f, 0.22f, 0.75f),
        "tragedy" to MoodProfile(0.22f, 0.15f, 0.75f, 0.45f, 0.82f),
        "trajedi" to MoodProfile(0.22f, 0.15f, 0.75f, 0.45f, 0.82f),

        // ── Fantezi / Macera ─────────────────────────────────────────────
        "fantasy" to MoodProfile(0.55f, 0.70f, 0.32f, 0.68f, 0.72f),
        "fantezi" to MoodProfile(0.55f, 0.70f, 0.32f, 0.68f, 0.72f),
        "adventure" to MoodProfile(0.62f, 0.72f, 0.35f, 0.62f, 0.70f),
        "macera" to MoodProfile(0.62f, 0.72f, 0.35f, 0.62f, 0.70f),
        "isekai" to MoodProfile(0.55f, 0.74f, 0.28f, 0.55f, 0.72f),
        "magic" to MoodProfile(0.52f, 0.72f, 0.30f, 0.65f, 0.72f),
        "büyü" to MoodProfile(0.52f, 0.72f, 0.30f, 0.65f, 0.72f),
        "historical" to MoodProfile(0.40f, 0.55f, 0.35f, 0.75f, 0.80f),
        "tarihi" to MoodProfile(0.40f, 0.55f, 0.35f, 0.75f, 0.80f),
        "samurai" to MoodProfile(0.60f, 0.45f, 0.55f, 0.62f, 0.72f),
        "supernatural" to MoodProfile(0.45f, 0.35f, 0.65f, 0.35f, 0.75f),
        "doğaüstü" to MoodProfile(0.45f, 0.35f, 0.65f, 0.35f, 0.75f),

        // ── Bilim Kurgu ───────────────────────────────────────────────────
        "sci-fi" to MoodProfile(0.50f, 0.45f, 0.42f, 0.15f, 0.80f),
        "sci fi" to MoodProfile(0.50f, 0.45f, 0.42f, 0.15f, 0.80f),
        "bilim kurgu" to MoodProfile(0.50f, 0.45f, 0.42f, 0.15f, 0.80f),
        "mecha" to MoodProfile(0.72f, 0.48f, 0.55f, 0.20f, 0.68f),
        "cyberpunk" to MoodProfile(0.62f, 0.35f, 0.60f, 0.15f, 0.75f),
        "space" to MoodProfile(0.35f, 0.45f, 0.35f, 0.10f, 0.90f),
        "uzay" to MoodProfile(0.35f, 0.45f, 0.35f, 0.10f, 0.90f),
        "post-apocalyptic" to MoodProfile(0.50f, 0.25f, 0.70f, 0.25f, 0.75f),

        // ── Spor ──────────────────────────────────────────────────────────
        "sports" to MoodProfile(0.95f, 0.72f, 0.58f, 0.40f, 0.60f),
        "spor" to MoodProfile(0.95f, 0.72f, 0.58f, 0.40f, 0.60f),

        // ── Diğer ─────────────────────────────────────────────────────────
        "ecchi" to MoodProfile(0.50f, 0.78f, 0.25f, 0.80f, 0.75f),
        "harem" to MoodProfile(0.55f, 0.80f, 0.20f, 0.82f, 0.72f),
        "mature" to MoodProfile(0.45f, 0.30f, 0.60f, 0.45f, 0.80f),
        "yetiskin" to MoodProfile(0.45f, 0.30f, 0.60f, 0.45f, 0.80f),
        "one shot" to MoodProfile(0.30f, 0.55f, 0.25f, 0.60f, 0.88f),
        "webtoon" to MoodProfile(0.50f, 0.60f, 0.35f, 0.55f, 0.80f),
        "manhwa" to MoodProfile(0.50f, 0.60f, 0.35f, 0.55f, 0.80f),
        "manhua" to MoodProfile(0.52f, 0.62f, 0.35f, 0.58f, 0.78f),
        "doujinshi" to MoodProfile(0.50f, 0.60f, 0.35f, 0.60f, 0.78f)
    )

    /**
     * Manga tür listesinden ağırlıklı ortalama duygu profili üretir.
     *
     * Listenin önündeki türler daha ağırlıklıdır — manga kaynakları türleri
     * önem sırasına göre dizer. Ağırlık: 1. tür ×2, 2. tür ×1.5, sonrası ×1.
     *
     * @param genres MangaDetails.genre listesi (TR ve/veya EN)
     * @return Tespit edilen türler ve hesaplanan profil
     */
    fun map(genres: List<String>): MoodMappingResult {
        if (genres.isEmpty()) {
            return MoodMappingResult(MoodProfile.Neutral, emptyList())
        }

        val matches = mutableListOf<Pair<String, MoodProfile>>()
        var totalWeight = 0f
        var e = 0f
        var v = 0f
        var t = 0f
        var w = 0f
        var f = 0f

        genres.take(6).forEachIndexed { index, rawGenre ->
            val normalized = normalize(rawGenre)
            val profile = genreTable[normalized] ?: return@forEachIndexed
            val weight = when (index) {
                0 -> 2.0f
                1 -> 1.5f
                else -> 1.0f
            }
            matches += normalized to profile
            totalWeight += weight
            e += profile.energy * weight
            v += profile.valence * weight
            t += profile.tension * weight
            w += profile.warmth * weight
            f += profile.focus * weight
        }

        if (totalWeight <= 0f) {
            return MoodMappingResult(MoodProfile.Neutral, emptyList())
        }

        val mood = MoodProfile(
            energy = (e / totalWeight).coerceIn(0f, 1f),
            valence = (v / totalWeight).coerceIn(0f, 1f),
            tension = (t / totalWeight).coerceIn(0f, 1f),
            warmth = (w / totalWeight).coerceIn(0f, 1f),
            focus = (f / totalWeight).coerceIn(0f, 1f)
        )
        return MoodMappingResult(mood, matches.map { it.first })
    }

    /**
     * Tür adını normalize eder: küçük harf, kırpma, fazla boşluk temizliği.
     */
    fun normalize(raw: String): String = raw
        .trim()
        .lowercase()
        .replace(Regex("\\s+"), " ")
        .replace("_", " ")

    /**
     * Tek bir türün profilini döndürür (yoksa null).
     */
    fun profileOf(genre: String): MoodProfile? = genreTable[normalize(genre)]

    /** Eşlenebilen tür sayısı (teşhis/log için). */
    val knownGenreCount: Int get() = genreTable.size
}

/**
 * [GenreMoodMapper.map] sonucu.
 *
 * @param mood   Hesaplanan duygu profili
 * @param genres Eşleşen normalize tür adları
 */
data class MoodMappingResult(
    val mood: MoodProfile,
    val genres: List<String>
)
