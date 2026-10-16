package com.kitsugi.animelist.data.fandom

/**
 * Vikipedi "İlgili maddeler" seçimi: adayları gövde metnine göre önem sırasına dizer ve
 * "her yapımda bulunan" genel maddeleri eler.
 *
 * Neden gerekli (canlı tr.wikipedia ölçümü, madde "Titana Saldırı"):
 *  - Giriş bölümünün bağlantıları `{{Animanga lisans}}` gibi bilgi kutusu şablonlarından
 *    ÜLKE/BÖLGE bağlantıları üretiyordu ("Amerika Birleşik Devletleri", "Avustralya",
 *    "Güneydoğu Asya", "Türkiye"...). Bunlar "ilgili madde" değildir.
 *  - Adaylar belge sırasına göre alındığı ve API yanıtı pageid'e göre sıralandığı için
 *    şeritte en eski maddeler (ülkeler, "Anime", "Manga", kıtalar) listenin başına
 *    geçiyor; asıl ilgili maddeler (karakter, stüdyo, uyarlama) hiç görünmüyordu.
 *
 * Seçim tamamen çevrimdışıdır (ağ isteği yok): gövde bağlantıları + sıklık + madde adı
 * benzerliği kullanılır. Ağ çağrısı yalnızca seçilen adaylar için yapılır.
 */
internal object WikipediaRelatedSelection {

    /** Şeritte gösterilecek en fazla kart sayısı. */
    const val DEFAULT_LIMIT = 12

    /** Önem sırasını bozan, her maddede geçen ansiklopedi iskeleti başlıklar. */
    private val GENERIC_TITLES = setOf(
        // Kıta / bölge / ülke
        "afrika", "asya", "avrupa", "kuzey amerika", "guney amerika", "okyanusya", "avustralasya",
        "orta dogu", "guneydogu asya", "dogu asya", "bati asya", "karayip", "balkanlar",
        "turkiye", "japonya", "abd", "amerika birlesik devletleri", "birlesik devletler", "kanada",
        "avustralya", "yeni zelanda", "birlesik krallik", "ingiltere", "iskocya", "galler",
        "irlanda", "almanya", "fransa", "italya", "ispanya", "portekiz", "hollanda", "belcika",
        "isvicre", "avusturya", "polonya", "rusya", "sovyetler birligi", "ukrayna", "yunanistan",
        "bulgaristan", "romanya", "macaristan", "cekya", "cek cumhuriyeti", "isvec", "norvec",
        "danimarka", "finlandiya", "izlanda", "cin", "cin halk cumhuriyeti", "tayvan", "hong kong",
        "guney kore", "kore", "kuzey kore", "hindistan", "endonezya", "malezya", "singapur",
        "tayland", "vietnam", "filipinler", "meksika", "brezilya", "arjantin", "sili", "peru",
        "kolombiya", "misir", "fas", "cezayir", "tunus", "guney afrika", "suudi arabistan",
        "iran", "irak", "israil", "lubnan", "suriye", "azerbaycan", "gurcistan", "kazakistan",
        "ozbekistan", "mogolistan", "nepal", "pakistan", "banglade", "sri lanka",
        "united states", "united states of america", "canada", "united kingdom", "japan", "china",
        "south korea", "north korea", "india", "australia", "new zealand", "germany", "france",
        "italy", "spain", "russia", "brazil", "mexico", "europe", "asia", "africa", "north america",
        "south america", "oceania", "southeast asia", "middle east",
        // Dil / yazı sistemi / milliyet sıfatı
        "japonca", "ingilizce", "korece", "cince", "turkce", "almanca", "fransizca", "ispanyolca",
        "italyanca", "portekizce", "rusca", "arapca", "fars", "hintce", "japoncanin romanizasyonu",
        "romaji", "kanji", "hiragana", "katakana", "latin alfabesi", "english", "japanese",
        "korean", "chinese", "turkish", "spanish", "french", "german", "russian", "arabic",
        // Yayın/medya kavramları (yapıma özgü değil, her animede geçer)
        "anime", "manga", "manga cizgi roman", "cizgi roman", "tankobon", "light novel",
        "gorsel roman", "visual novel", "ova", "ona", "televizyon dizisi", "tv dizisi", "dizi",
        "film", "animasyon", "anime filmi", "bilgisayar oyunu", "video oyunu", "oyun", "roman",
        "kitap", "dergi", "seslendirme", "seiyu", "dublaj", "franchise", "medya imtiyazi",
        // Hedef kitle / tür kavramları: hiçbir zaman tek bir yapımın "ilgili maddesi" değildir
        "shonen", "shounen", "shojo", "shoujo", "seinen", "josei", "kodomo", "shonen manga",
        "shojo manga", "seinen manga", "isekai", "mecha", "ecchi", "harem", "yaoi", "yuri",
        "maho shojo", "buyuk robot", "giant robot",
        // Yayıncı/platform/kanal türü genellemeleri (yapıma özgü olmayanlar)
        "televizyon", "radyo", "internet", "streaming medya hizmeti", "blu-ray disc", "dvd",
        // Ödül / kurum iskeleti
        "otorite kontrolu", "uluslararasi sanal otorite dosyasi", "tumlesik otorite dosyasi",
        "milli diyet kutuphanesi", "fransa milli kutuphanesi", "imdb", "vikipedi", "wikipedia"
    )

    /** Yıl ve tarih maddeleri: "2013", "9 Eylül 2009", "2010'lar"… */
    private val YEAR_TITLE = Regex("^\\d{3,4}(['’]?(ler|lar))?$")
    private val DATE_TITLE = Regex(
        "^\\d{1,2}\\s+(ocak|subat|mart|nisan|mayis|haziran|temmuz|agustos|eylul|ekim|kasim|aralik)$"
    )

    /**
     * Vikipedi kısa tanımındaki (`prop=description`) yer/dil/zaman maddeleri.
     *
     * Canlı tr.wikipedia tanımları: "Kuzey Amerika'da bir ülke" (Kanada), "Japonya'nın
     * başkenti" (Tokyo), "Doğu Asya dili" (Japonca), "Bavyera'da bağımsız şehir (Almanya)"
     * (Nördlingen). Buna karşılık "Japon yayınevi" (Kodansha), "Japon animasyon stüdyosu"
     * (Wit Studio), "Japon manga sanatçısı" (Hajime Isayama) ve "… kurgusal karakter"
     * (Eren Yeager) yapımla İLGİLİDİR; süzgeç bu yüzden yalnızca yer/dil/zaman adlarını
     * arar, şirket/stüdyo/sanatçı/karakter sözcüklerine dokunmaz.
     */
    private val GENERIC_SHORT_DESCRIPTION = listOf(
        // Ülke, bölge, yerleşim, coğrafya
        Regex("\\b(ulke|ulkesi|devlet|devleti|sehir|sehri|kent|kenti|kasaba|kasabasi|belde|" +
            "koy|koyu|ada|adasi|bolge|bolgesi|cografya|cografi|ilce|ilcesi|il|ili|baskent|" +
            "baskenti|nehir|nehri|dag|dagi|gol|golu|ova|ovasi|yayla|kita|kitasi|kiyi|" +
            "milli park|metropol|sehir devleti)\\b"),
        // Dil, yazı sistemi
        Regex("\\b(dil|dili|diller|lehce|lehcesi|alfabe|yazi sistemi|romanizasyon|" +
            "ceviri yazisi|dil ailesi)\\b"),
        // Zaman, takvim, ölçü birimi
        Regex("\\b(yil|yili|yillar|yuzyil|yuzyili|takvim|gun|hafta|ay|mevsim|para birimi|" +
            "olcu birimi)\\b"),
        // Toplum/etnik yapı ve spor takımı klişeleri
        Regex("\\b(halk|halki|millet|milleti|etnik grup|nufus|futbol kulubu|futbol takimi|" +
            "spor kulubu|milli takim)\\b"),
        // İngilizce kısa tanımlar (en.wikipedia)
        Regex("\\b(country|countries|nation|state|province|region|city|town|village|island|" +
            "river|mountain|lake|sea|continent|district|municipality|capital|metropolis|" +
            "language|dialect|alphabet|currency|year|decade|century|calendar|population|" +
            "football club|national team)\\b")
    )

    /**
     * "… bir ülkedir", "… is a country" gibi yapımdan bağımsız genel tanımlar.
     *
     * Metin karşılaştırmadan önce [FandomNameVariants.normalizeForCompare] ile sadeleştirilir
     * (Türkçe karakterler ASCII'ye katlanır, küçük harfe iner), bu yüzden desenler sade yazılır.
     */
    private val GENERIC_DESCRIPTION = listOf(
        Regex("\\bbir (ulke|devlet|kita|cografya|bolge|sehir|baskent|dil|diller|il|ilce|koy|ada|" +
            "nehir|dag|gol|ova|milli takim|futbol takimi)(dir|tir|sidir|si)?\\b"),
        Regex("\\b(is|was) an? (country|continent|region|language|city|town|province|state|island|" +
            "river|mountain|currency|disambiguation|village)\\b"),
        // "Tokyo, Japonya'nın başkenti ve en kalabalık şehridir." gibi "bir …" kalıbına
        // girmeyen tanımlar.
        Regex("\\b(baskenti|baskentidir|en (kalabalik|buyuk) (sehir|kent)\\w*)\\b"),
        Regex("\\bis (the )?(capital|largest|most populous)\\b|\\bis a (state|province|district) in\\b"),
        Regex("\\bpara birimidir\\b|\\bcurrency of\\b|\\bnational language\\b")
    )

    fun isGenericTitle(title: String): Boolean {
        val normalized = FandomNameVariants.normalizeForCompare(title)
        if (normalized.isEmpty()) return true
        if (normalized in GENERIC_TITLES) return true
        if (YEAR_TITLE.matches(normalized)) return true
        if (DATE_TITLE.matches(normalized)) return true
        return false
    }

    /** Özet metni yapımdan bağımsız genel bir maddeyi anlatıyorsa kart gösterilmez. */
    fun looksGenericDescription(description: String?): Boolean {
        val text = description?.trim().orEmpty()
        if (text.length < 20) return false
        // Uzun anlatım yerine maddenin ne olduğunu söyleyen ilk cümleler ölçülür.
        val head = FandomNameVariants.normalizeForCompare(text.take(400))
        return GENERIC_DESCRIPTION.any { it.containsMatchIn(head) }
    }

    /**
     * Aday "her maddede geçen" genel bir konu mu (ülke, şehir, dil, yıl…)?
     *
     * Önce Vikipedi kısa tanımı okunur; kısa tanım yoksa (eski önbellek, kısa tanımı
     * girilmemiş madde) özet metnin ilk cümlelerine düşülür.
     */
    fun looksGenericTopic(shortDescription: String?, snippet: String?): Boolean {
        val short = shortDescription?.trim().orEmpty()
        if (short.isEmpty()) return looksGenericDescription(snippet)
        val normalized = FandomNameVariants.normalizeForCompare(short)
        return GENERIC_SHORT_DESCRIPTION.any { it.containsMatchIn(normalized) }
    }

    /**
     * Adayları önem sırasına dizer.
     *
     * @param leadCounts giriş (0. bölüm) gövdesindeki bağlantı sıklıkları.
     * @param bodyCounts sonraki bölümlerin gövde bağlantı sıklıkları.
     * @param selfTitles maddenin kendi başlıkları (yönlendirmeler dahil) — kendini önermesin.
     * @param workTitles yapımın bilinen adları: "Attack on Titan: Lost Girls" gibi aynı
     *   adı taşıyan uyarlamalar üst sıraya çıkar.
     */
    fun rank(
        leadCounts: Map<String, Int>,
        bodyCounts: Map<String, Int>,
        selfTitles: Set<String>,
        workTitles: Collection<String> = emptyList(),
        limit: Int = DEFAULT_LIMIT
    ): List<String> {
        val self = selfTitles.map(FandomNameVariants::normalizeForCompare).toSet()
        val workTokens = workTitles
            .flatMap { FandomNameVariants.tokens(it) }
            .filter { it.length >= 3 }
            .toSet()
        val all = LinkedHashMap<String, Int>()
        leadCounts.forEach { (title, count) ->
            all[title] = count * 4
        }
        bodyCounts.forEach { (title, count) ->
            all[title] = (all[title] ?: 0) + count * 2
        }
        val scored = all.mapNotNull { (title, base) ->
            val normalized = FandomNameVariants.normalizeForCompare(title)
            if (normalized.isEmpty() || normalized in self) return@mapNotNull null
            if (isGenericTitle(title)) return@mapNotNull null
            if (title.length > 120) return@mapNotNull null
            val tokens = FandomNameVariants.tokens(title).toSet()
            // Yapımın adını taşıyan maddeler (cilt listesi, yan hikâye, sezon maddesi…)
            // doğrudan ilgilidir; isim kesişimi puanı yükseltir.
            val overlap = tokens.count { it in workTokens }
            val bonus = overlap * 6
            title to (base + bonus)
        }
        return scored
            .sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first.length })
            .take(limit.coerceIn(1, 40))
            .map { it.first }
    }
}
