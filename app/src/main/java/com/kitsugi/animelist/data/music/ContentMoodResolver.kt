package com.kitsugi.animelist.data.music

/**
 * 🎯 İçerik → Duygu Eşleme Motoru
 *
 * **Sorun:** Yalnızca `GenreMoodMapper`'ın tablosuna bakmak yetmiyordu.
 * Gerçek dünyada manga kaynakları:
 *  - Türkçe/İngilizce karışık tür verir ("Romantizm", "Slice of Life")
 *  - Çoğu zaman tür vermez, yalnızca **açıklama** verir
 *  - Tür yerine etiket verir ("Manhwa", "Regresyon", "Seviye atlama")
 *  - "Webtoon", "Manhwa" gibi **format** adları verir (tür değil)
 *
 * **Çözüm:** üç katmanlı sinyal toplama. Hepsi aynı `MoodProfile`'a
 * katkı verir, ağırlıkları farklıdır:
 *
 * ```
 * 1. TÜRLER        ağırlık 2.0  (en güvenilir — editör etiketi)
 * 2. ETİKETLER     ağırlık 1.0  (tür listesindeki bilinmeyenler)
 * 3. AÇIKLAMA      ağırlık 0.6  (anahtar kelime taraması)
 * ```
 *
 * **Hiçbiri eşleşmezse** `Neutral` döner ve çağıran taraf bunu bilir
 * (`confidence = 0`). Sessizce "rastgele" öneri yapmaz.
 */
object ContentMoodResolver {

    /**
     * Eşleşme güveni.
     *
     * - `NONE`     hiçbir sinyal yok → nötr başlangıç
     * - `LOW`      yalnızca açıklama eşleşti
     * - `MEDIUM`   etiket eşleşti
     * - `HIGH`     tür eşleşti
     */
    enum class Confidence { NONE, LOW, MEDIUM, HIGH }

    data class Result(
        val mood: MoodProfile,
        val matchedGenres: List<String>,
        val matchedKeywords: List<String>,
        val confidence: Confidence,
        /** Kullanıcıya gösterilecek kısa gerekçe. */
        val reason: String
    )

    // ─────────────────────────────────────────────────────────────────────
    //  Anahtar kelime tablosu
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Açıklama/etiket içinde geçen ifade → duygu profili.
     *
     * **Neden tür tablosundan ayrı:** bunlar tür değil, *içerik işareti*.
     * "Ölümünden sonra geri döner" cümlesi tür değil ama güçlü bir
     * "regresyon/intikam" sinyali.
     */
    private val keywordTable: Map<String, MoodProfile> = mapOf(
        // ── Aksiyon / dövüş ──────────────────────────────────────────────
        "savaş" to MoodProfile(0.90f, 0.35f, 0.80f, 0.35f, 0.75f),
        "dövüş" to MoodProfile(0.88f, 0.38f, 0.75f, 0.35f, 0.78f),
        "kılıç" to MoodProfile(0.80f, 0.42f, 0.65f, 0.40f, 0.80f),
        "güç" to MoodProfile(0.82f, 0.45f, 0.60f, 0.40f, 0.75f),
        "seviye" to MoodProfile(0.78f, 0.50f, 0.55f, 0.40f, 0.85f),
        "zindan" to MoodProfile(0.85f, 0.35f, 0.75f, 0.30f, 0.80f),
        "canavar" to MoodProfile(0.80f, 0.30f, 0.70f, 0.30f, 0.75f),
        "avcı" to MoodProfile(0.82f, 0.35f, 0.70f, 0.32f, 0.78f),
        "turnuva" to MoodProfile(0.85f, 0.50f, 0.65f, 0.40f, 0.80f),
        "savaşçı" to MoodProfile(0.88f, 0.38f, 0.75f, 0.35f, 0.78f),
        "ordu" to MoodProfile(0.85f, 0.30f, 0.80f, 0.35f, 0.70f),
        "kral" to MoodProfile(0.70f, 0.45f, 0.65f, 0.55f, 0.70f),
        "imparator" to MoodProfile(0.68f, 0.45f, 0.65f, 0.55f, 0.70f),

        // ── Karanlık / gerilim ───────────────────────────────────────────
        "ölüm" to MoodProfile(0.45f, 0.15f, 0.85f, 0.25f, 0.60f),
        "intikam" to MoodProfile(0.70f, 0.20f, 0.85f, 0.30f, 0.65f),
        "ihanet" to MoodProfile(0.55f, 0.15f, 0.85f, 0.28f, 0.62f),
        "kan" to MoodProfile(0.55f, 0.12f, 0.88f, 0.25f, 0.58f),
        "kabus" to MoodProfile(0.40f, 0.12f, 0.90f, 0.20f, 0.55f),
        "zombi" to MoodProfile(0.65f, 0.12f, 0.90f, 0.20f, 0.60f),
        "kıyamet" to MoodProfile(0.60f, 0.15f, 0.88f, 0.22f, 0.60f),
        "hayalet" to MoodProfile(0.35f, 0.18f, 0.85f, 0.30f, 0.55f),
        "lanet" to MoodProfile(0.45f, 0.18f, 0.82f, 0.30f, 0.58f),
        "gizem" to MoodProfile(0.45f, 0.32f, 0.75f, 0.35f, 0.72f),
        "cinayet" to MoodProfile(0.50f, 0.15f, 0.88f, 0.28f, 0.62f),
        "vampir" to MoodProfile(0.55f, 0.25f, 0.78f, 0.35f, 0.60f),

        // ── Romantik / duygusal ──────────────────────────────────────────
        "aşk" to MoodProfile(0.35f, 0.80f, 0.25f, 0.72f, 0.55f),
        "sevgi" to MoodProfile(0.32f, 0.82f, 0.20f, 0.75f, 0.55f),
        "kalp" to MoodProfile(0.35f, 0.75f, 0.28f, 0.78f, 0.55f),
        "evlilik" to MoodProfile(0.38f, 0.70f, 0.30f, 0.75f, 0.58f),
        "düğün" to MoodProfile(0.40f, 0.75f, 0.25f, 0.78f, 0.55f),
        "özlem" to MoodProfile(0.30f, 0.45f, 0.40f, 0.60f, 0.60f),
        "hasret" to MoodProfile(0.28f, 0.40f, 0.42f, 0.58f, 0.60f),
        "gözyaşı" to MoodProfile(0.30f, 0.30f, 0.55f, 0.65f, 0.55f),
        "dostluk" to MoodProfile(0.45f, 0.70f, 0.22f, 0.72f, 0.65f),

        // ── Huzur / günlük yaşam ─────────────────────────────────────────
        "okul" to MoodProfile(0.35f, 0.70f, 0.28f, 0.70f, 0.80f),
        "günlük" to MoodProfile(0.22f, 0.72f, 0.10f, 0.72f, 0.75f),
        "yemek" to MoodProfile(0.30f, 0.75f, 0.12f, 0.78f, 0.70f),
        "kahve" to MoodProfile(0.25f, 0.72f, 0.15f, 0.78f, 0.75f),
        "kafe" to MoodProfile(0.25f, 0.72f, 0.15f, 0.75f, 0.75f),
        "huzur" to MoodProfile(0.18f, 0.75f, 0.08f, 0.72f, 0.80f),
        "dinlenme" to MoodProfile(0.15f, 0.70f, 0.08f, 0.70f, 0.75f),

        // ── Fantastik / macera ───────────────────────────────────────────
        "büyü" to MoodProfile(0.60f, 0.72f, 0.40f, 0.55f, 0.85f),
        "sihir" to MoodProfile(0.62f, 0.70f, 0.40f, 0.55f, 0.85f),
        "ejderha" to MoodProfile(0.75f, 0.60f, 0.55f, 0.45f, 0.75f),
        "krallık" to MoodProfile(0.55f, 0.60f, 0.45f, 0.55f, 0.75f),
        "rehber" to MoodProfile(0.55f, 0.55f, 0.40f, 0.55f, 0.75f),
        "yolculuk" to MoodProfile(0.55f, 0.65f, 0.35f, 0.55f, 0.75f),
        "macera" to MoodProfile(0.68f, 0.70f, 0.42f, 0.52f, 0.78f),
        "reankarnasyon" to MoodProfile(0.55f, 0.55f, 0.45f, 0.55f, 0.78f),
        "yeniden doğ" to MoodProfile(0.55f, 0.55f, 0.45f, 0.55f, 0.78f),
        "geri dön" to MoodProfile(0.55f, 0.45f, 0.55f, 0.50f, 0.75f),
        "gelecek" to MoodProfile(0.60f, 0.50f, 0.50f, 0.40f, 0.82f),
        "uzay" to MoodProfile(0.45f, 0.45f, 0.45f, 0.32f, 0.88f),
        "robot" to MoodProfile(0.60f, 0.45f, 0.50f, 0.35f, 0.85f),

        // ── Spor / rekabet ───────────────────────────────────────────────
        "spor" to MoodProfile(0.88f, 0.72f, 0.55f, 0.50f, 0.82f),
        "futbol" to MoodProfile(0.90f, 0.70f, 0.55f, 0.50f, 0.80f),
        "basketbol" to MoodProfile(0.88f, 0.70f, 0.52f, 0.50f, 0.80f),
        "oyun" to MoodProfile(0.70f, 0.60f, 0.45f, 0.42f, 0.85f),
        "müzik" to MoodProfile(0.45f, 0.75f, 0.22f, 0.72f, 0.72f),
        "dans" to MoodProfile(0.60f, 0.78f, 0.25f, 0.70f, 0.68f),

        // ── Komedi ───────────────────────────────────────────────────────
        "komik" to MoodProfile(0.55f, 0.85f, 0.15f, 0.72f, 0.55f),
        "eğlence" to MoodProfile(0.60f, 0.85f, 0.15f, 0.70f, 0.55f),
        "parodi" to MoodProfile(0.58f, 0.88f, 0.12f, 0.68f, 0.52f)
    )

    // ─────────────────────────────────────────────────────────────────────
    //  Çok dilli tür eşlemesi
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Kaynak sitelerin **kendi dilinde** döndürdüğü tür adları.
     *
     * **Neden gerekli:** Kotatsu/Mihon kaynaklarından gelen `MangaTag.title`
     * kaynağın dilinde olur. Rusça sitede "Романтика", Endonezce sitede
     * "Romantis" gelir. `SearchTranslation` yalnızca TR+EN bildiği ve
     * `BangumiTagDictionary` CJK ağırlıklı olduğu için (58k satırda
     * yalnızca 15 Kiril kaydı, 0 Arapça) bu diller **boşta kalıyordu**.
     *
     * Tablo **küçük ve sabit** tutuldu: her dilde en çok kullanılan 25 tür.
     * Amaç sözlük olmak değil, en sık 10 dili köprülemek.
     */
    private val multilingualGenres: Map<String, String> = mapOf(
        // ── RUSÇA ────────────────────────────────────────────────────────
        "романтика" to "Romance",
        "боевик" to "Action",
        "приключения" to "Adventure",
        "комедия" to "Comedy",
        "драма" to "Drama",
        "фэнтези" to "Fantasy",
        "ужасы" to "Horror",
        "мистика" to "Mystery",
        "триллер" to "Thriller",
        "психология" to "Psychological",
        "спорт" to "Sports",
        "школа" to "School",
        "повседневность" to "Slice of Life",
        "сверхъестественное" to "Supernatural",
        "исторический" to "Historical",
        "военное" to "Military",
        "космос" to "Space",
        "меха" to "Mecha",
        "музыка" to "Music",
        "вампиры" to "Vampire",
        "зомби" to "Zombie",
        "postапокалипсис" to "Post-Apocalyptic",
        "постапокалипсис" to "Post-Apocalyptic",
        "боевые искусства" to "Martial Arts",
        "сэйнэн" to "Seinen",
        "сёнэн" to "Shounen",
        "сёдзё" to "Shoujo",
        "дзёсэй" to "Josei",
        "этти" to "Ecchi",
        "гарем" to "Harem",

        // ── ENDONEZCE ────────────────────────────────────────────────────
        "romantis" to "Romance",
        "aksi" to "Action",
        "petualangan" to "Adventure",
        "komedi" to "Comedy",
        "drama" to "Drama",
        "fantasi" to "Fantasy",
        "horor" to "Horror",
        "misteri" to "Mystery",
        "thriller" to "Thriller",
        "psikologis" to "Psychological",
        "olahraga" to "Sports",
        "sekolah" to "School",
        "kehidupan sehari-hari" to "Slice of Life",
        "supranatural" to "Supernatural",
        "sejarah" to "Historical",
        "militer" to "Military",
        "luar angkasa" to "Space",
        "mecha" to "Mecha",
        "musik" to "Music",
        "vampir" to "Vampire",
        "zombi" to "Zombie",
        "pasca-apokaliptik" to "Post-Apocalyptic",
        "seni bela diri" to "Martial Arts",
        "sein" to "Seinen",
        "shonen" to "Shounen",
        "shojo" to "Shoujo",
        "josei" to "Josei",
        "ekusi" to "Ecchi",
        "harem" to "Harem",

        // ── İSPANYOLCA ───────────────────────────────────────────────────
        "romance" to "Romance",
        "acción" to "Action",
        "aventura" to "Adventure",
        "comedia" to "Comedy",
        "fantasía" to "Fantasy",
        "terror" to "Horror",
        "misterio" to "Mystery",
        "suspenso" to "Thriller",
        "psicológico" to "Psychological",
        "deportes" to "Sports",
        "escolar" to "School",
        "recuentos de la vida" to "Slice of Life",
        "sobrenatural" to "Supernatural",
        "histórico" to "Historical",
        "militar" to "Military",
        "espacio" to "Space",
        "música" to "Music",
        "vampiro" to "Vampire",
        "apocalíptico" to "Post-Apocalyptic",
        "artes marciales" to "Martial Arts",
        "seinen" to "Seinen",

        // ── PORTEKİZCE ───────────────────────────────────────────────────
        "ação" to "Action",
        "comédia" to "Comedy",
        "fantasia" to "Fantasy",
        "mistério" to "Mystery",
        "suspense" to "Thriller",
        "esportes" to "Sports",
        "escola" to "School",
        "espaço" to "Space",
        "zumbi" to "Zombie",
        "artes marciais" to "Martial Arts",

        // ── FRANSIZCA ────────────────────────────────────────────────────
        "action" to "Action",
        "aventure" to "Adventure",
        "comédie" to "Comedy",
        "drame" to "Drama",
        "fantastique" to "Fantasy",
        "horreur" to "Horror",
        "mystère" to "Mystery",
        "psychologique" to "Psychological",
        "sport" to "Sports",
        "école" to "School",
        "tranche de vie" to "Slice of Life",
        "surnaturel" to "Supernatural",
        "historique" to "Historical",
        "militaire" to "Military",
        "espace" to "Space",
        "musique" to "Music",
        "vampire" to "Vampire",
        "zombie" to "Zombie",
        "arts martiaux" to "Martial Arts",

        // ── ALMANCA ──────────────────────────────────────────────────────
        "aktion" to "Action",
        "abenteuer" to "Adventure",
        "komödie" to "Comedy",
        "fantasie" to "Fantasy",
        "horror" to "Horror",
        "mystery" to "Mystery",
        "psychologisch" to "Psychological",
        "schule" to "School",
        "alltag" to "Slice of Life",
        "übernatürlich" to "Supernatural",
        "historisch" to "Historical",
        "militär" to "Military",
        "weltraum" to "Space",
        "kampfkunst" to "Martial Arts",

        // ── İTALYANCA ────────────────────────────────────────────────────
        "azione" to "Action",
        "avventura" to "Adventure",
        "commedia" to "Comedy",
        "dramma" to "Drama",
        "fantasy" to "Fantasy",
        "orrore" to "Horror",
        "mistero" to "Mystery",
        "psicologico" to "Psychological",
        "scuola" to "School",
        "fetta di vita" to "Slice of Life",
        "soprannaturale" to "Supernatural",
        "storico" to "Historical",
        "militare" to "Military",
        "spazio" to "Space",
        "musica" to "Music",
        "arti marziali" to "Martial Arts",

        // ── ARAPÇA ───────────────────────────────────────────────────────
        "رومانسي" to "Romance",
        "أكشن" to "Action",
        "مغامرة" to "Adventure",
        "كوميدي" to "Comedy",
        "دراما" to "Drama",
        "فانتازيا" to "Fantasy",
        "رعب" to "Horror",
        "غموض" to "Mystery",
        "إثارة" to "Thriller",
        "رياضة" to "Sports",
        "مدرسة" to "School",
        "خارق للطبيعة" to "Supernatural",
        "تاريخي" to "Historical",
        "عسكري" to "Military",
        "فضاء" to "Space",
        "موسيقى" to "Music",
        "فنون قتالية" to "Martial Arts",

        // ── TÜRKÇE (SearchTranslation'ın kaçırdığı varyantlar) ───────────
        "aşk" to "Romance",
        "romantizm" to "Romance",
        "bilimkurgu" to "Sci-Fi",
        "bilim kurgu" to "Sci-Fi",
        "yaşam dilimi" to "Slice of Life",
        "gündelik yaşam" to "Slice of Life",
        "macera" to "Adventure",
        "psikolojik" to "Psychological",
        "gerilim" to "Thriller",
        "gizem" to "Mystery",
        "korku" to "Horror",
        "aksiyon" to "Action",
        "fantastik" to "Fantasy",
        "fantezi" to "Fantasy",
        "dövüş sanatları" to "Martial Arts",
        "doğaüstü" to "Supernatural",
        "okul hayatı" to "School",
        "günlük yaşam" to "Slice of Life",
        "tarihi" to "Historical",
        "askeri" to "Military",
        "uzay" to "Space",
        "müzik" to "Music",
        "trajedi" to "Tragedy",
        "intikam" to "Revenge",
        "ihanet" to "Betrayal",
        "büyü" to "Magic",
        "sihir" to "Magic",
        "ejderha" to "Fantasy",
        "zindan" to "Fantasy",
        "seviye atlama" to "Leveling",
        "reankarnasyon" to "Reincarnation",
        "yeniden doğuş" to "Reincarnation",
        "regresyon" to "Regression",
        "zamanda geri" to "Regression",
        "gurme" to "Gourmet",
        "yemek" to "Gourmet",
        "spor" to "Sports",
        "futbol" to "Sports",
        "okul" to "School",
        "lise" to "School",
        "üniversite" to "School",
        "dilim" to "Slice of Life",
        "acı" to "Tragedy",
        "melankoli" to "Tragedy"
    )

    /**
     * Tür adını İngilizce karşılığına çözer.
     *
     * **Neden 4 kademe:** her kaynak farklı dilde etiket veriyor. Tek bir
     * tabloya güvenmek, o tablonun kapsamadığı dilde tüm sinyali
     * kaybetmek demek. Kademeler ucuzdan pahalıya sıralı; ilk eşleşen
     * döner.
     */
    private fun resolveToEnglish(raw: String): String {
        val cleaned = raw.trim()
        if (cleaned.isEmpty()) return cleaned

        // 1) Bangumi sözlüğü — kanonik anahtar (İngilizce) döner
        runCatching {
            com.kitsugi.animelist.utils.BangumiTagDictionary.entryFor(cleaned)
        }.getOrNull()?.let { entry ->
            if (entry.english.isNotBlank()) return entry.english
        }

        // 2) Çok dilli tablo
        multilingualGenres[cleaned.lowercase()]?.let { return it }

        // 3) Uygulamanın kendi çeviri altyapısı (TR ↔ EN)
        val viaSearch = runCatching {
            com.kitsugi.animelist.ui.screens.search.SearchTranslation
                .translateToEnglishForSearch(cleaned)
        }.getOrDefault(cleaned)
        if (!viaSearch.equals(cleaned, ignoreCase = true)) return viaSearch

        // 4) Dokunma — zaten İngilizce
        return cleaned
    }

    /**
     * Format/eşanlam kelimeleri — **duygu katkısı yok**.
     *
     * ⚠️ DİKKAT: `manhwa`, `webtoon`, `manhua` burada **DEĞİL**.
     * Denetimde bulundu: `GenreMoodMapper` bunları hafif olumlu bir
     * profile eşliyor (0.50/0.60/0.35/0.55/0.80). Bu listeye koymak o
     * eşleşmeyi ezer ve Korean/Çin çizgi romanlarında tüm sinyali
     * kaybettirirdi — Türkçe kaynaklarda çok yaygın bir etiket.
     *
     * Buradakiler gerçekten **içerik sinyali taşımayan** idari etiketler.
     */
    private val formatWords = setOf(
        "manga", "comic", "çizgi roman", "colored", "renkli", "türkçe",
        "tamamlandı", "devam ediyor", "ongoing", "completed", "oneshot",
        "one shot", "web comic", "full", "tamamlanmış"
    )

    // ─────────────────────────────────────────────────────────────────────
    //  Ana giriş
    // ─────────────────────────────────────────────────────────────────────

    /**
     * İçerikten duygu profili çıkarır.
     *
     * @param genres      MangaDetails.genre
     * @param title       MangaDetails.title
     * @param description MangaDetails.description
     */
    fun resolve(
        genres: List<String>,
        title: String? = null,
        description: String? = null
    ): Result {
        var e = 0f; var v = 0f; var t = 0f; var w = 0f; var f = 0f
        var totalWeight = 0f
        val matchedGenres = mutableListOf<String>()
        val matchedKeywords = mutableListOf<String>()
        var confidence = Confidence.NONE

        // ── KATMAN 1: türler (ağırlık 2.0, ilk 3 sıralı) ─────────────────
        //
        // 🔑 Uygulamanın MEVCUT çeviri altyapısı kullanılır:
        //    SearchTranslation.translateToEnglishForSearch()
        //    → "Romantizm" = "Romance", "Gerilim" = "Thriller", ...
        //
        // Böylece Türkçe/İngilizce/Çince (Bangumi) tür adlarının hepsi
        // tek bir İngilizce anahtara düşer ve GenreMoodMapper tablosu
        // YALNIZCA İngilizce tutabilir. Ayrı bir Türkçe tablo yok.
        genres.take(6).forEachIndexed { index, raw ->
            if (raw.isBlank()) return@forEachIndexed

            // ── Tür adını İngilizceye çevir (4 kademeli) ────────────────
            // 1. Bangumi sözlüğü (58k satır, CJK + TR + EN)
            // 2. Çok dilli tablo (RU/ID/ES/PT/FR/DE/IT/AR/TR)
            // 3. SearchTranslation (uygulamanın TR↔EN altyapısı)
            // 4. Olduğu gibi (zaten İngilizce olabilir)
            val english = resolveToEnglish(raw)

            val normalized = GenreMoodMapper.normalize(english)
            if (normalized in formatWords) return@forEachIndexed
            if (normalized.isBlank()) return@forEachIndexed

            val profile = GenreMoodMapper.profileOf(english)
            if (profile != null) {
                // İlk türler daha belirleyici (kaynaklar önem sırasına dizer)
                val weight = when (index) {
                    0 -> 2.0f
                    1 -> 1.5f
                    else -> 1.2f
                }
                // Kullanıcıya gösterilecek ad: çevrilmiş Türkçe hâli
                matchedGenres += raw.trim()
                e += profile.energy * weight
                v += profile.valence * weight
                t += profile.tension * weight
                w += profile.warmth * weight
                f += profile.focus * weight
                totalWeight += weight
                if (confidence.ordinal < Confidence.HIGH.ordinal) confidence = Confidence.HIGH
            } else {
                // Tür tablosunda yok → anahtar kelime olarak dene
                keywordTable[normalized]?.let { kw ->
                    val weight = 1.2f
                    matchedKeywords += normalized
                    e += kw.energy * weight
                    v += kw.valence * weight
                    t += kw.tension * weight
                    w += kw.warmth * weight
                    f += kw.focus * weight
                    totalWeight += weight
                    if (confidence.ordinal < Confidence.MEDIUM.ordinal) {
                        confidence = Confidence.MEDIUM
                    }
                }
            }
        }

        // ── KATMAN 2: açıklama + başlık anahtar kelimeleri (ağırlık 0.6) ──
        // Açıklama uzun; her kelimeyi değil, tablodaki ifadeleri arıyoruz.
        val haystack = buildString {
            if (!title.isNullOrBlank()) append(title.lowercase()).append(' ')
            if (!description.isNullOrBlank()) append(description.lowercase())
        }

        if (haystack.isNotBlank()) {
            var descHits = 0
            keywordTable.forEach { (keyword, profile) ->
                if (descHits >= 4) return@forEach   // aşırıya kaçma
                if (keyword in haystack && keyword !in matchedKeywords) {
                    val weight = 0.6f
                    matchedKeywords += keyword
                    e += profile.energy * weight
                    v += profile.valence * weight
                    t += profile.tension * weight
                    w += profile.warmth * weight
                    f += profile.focus * weight
                    totalWeight += weight
                    descHits++
                    if (confidence.ordinal < Confidence.LOW.ordinal) {
                        confidence = Confidence.LOW
                    }
                }
            }
        }

        // ── Sonuç ────────────────────────────────────────────────────────
        if (totalWeight <= 0f) {
            return Result(
                mood = MoodProfile.Neutral,
                matchedGenres = emptyList(),
                matchedKeywords = emptyList(),
                confidence = Confidence.NONE,
                reason = "Tür/açıklama tanınmadı — dengeli başlangıç"
            )
        }

        val mood = MoodProfile(
            energy = (e / totalWeight).coerceIn(0f, 1f),
            valence = (v / totalWeight).coerceIn(0f, 1f),
            tension = (t / totalWeight).coerceIn(0f, 1f),
            warmth = (w / totalWeight).coerceIn(0f, 1f),
            focus = (f / totalWeight).coerceIn(0f, 1f)
        )

        val reason = when {
            matchedGenres.isNotEmpty() ->
                "${matchedGenres.take(2).joinToString(", ")} türüne göre"
            matchedKeywords.isNotEmpty() ->
                "\"${matchedKeywords.take(2).joinToString(", ")}\" içeriğine göre"
            else -> "Dengeli başlangıç"
        }

        return Result(
            mood = mood,
            matchedGenres = matchedGenres,
            matchedKeywords = matchedKeywords,
            confidence = confidence,
            reason = reason
        )
    }
}
