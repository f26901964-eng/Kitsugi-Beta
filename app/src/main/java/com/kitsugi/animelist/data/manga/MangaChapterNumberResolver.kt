package com.kitsugi.animelist.data.manga

/**
 * Bir bölümün NUMARASINI güvenilir biçimde çözer.
 *
 * Neden ayrı bir çözümleyici?
 *  - Bazı HTML kaynakları `chapterNumber = -1` döndürür ve numara yalnızca
 *    bölüm adında bulunur ("Bölüm 137 - Chu Chu Sevgi...", "Ch. 12.5", "Chapter 7").
 *  - Bazı kaynaklar ise adı hiç vermez, sadece numarayı verir.
 *
 * Kural seti:
 *  1. Eklenti geçerli bir numara verdiyse (>= 0) o kullanılır.
 *  2. Değilse [ChapterRecognition] ile ad üzerinden çıkarılır (Mihon paritesi:
 *     "Vol.1 Ch. 4" → 4, "12.5" → 12.5, "Ch 10 extra" → 10.99).
 *  3. Hiçbiri işe yaramazsa -1 (bilinmiyor) döner; liste sıralaması bozulmaz,
 *     yalnızca numara etiketi gösterilmez.
 */
object MangaChapterNumberResolver {

    /**
     * Türkçe kaynaklarda sık görülen "Bölüm 137", "Bolum 137", "Böl. 137",
     * "Bölüm: 137" biçimlerini yakalar. `ChapterRecognition` İngilizce odaklı
     * olduğu için Türkçe ekler burada ayrıca ele alınır.
     */
    private val turkishChapter = Regex(
        """(?i)\b(?:b[oö]l[üu]m|bol\.?|b[oö]l)\s*[:\-]?\s*(\d+(?:[.,]\d+)?)""",
        RegexOption.IGNORE_CASE
    )

    /** "1. Bölüm", "137 - ..." gibi başta sayı gelen başlıklar. */
    private val leadingNumber = Regex("""^\s*(\d+(?:[.,]\d+)?)""")

    /** "Cilt 3 Bölüm 12" gibi başlıklarda cilt numarasını yanlışlıkla almayı önler. */
    private val volumePattern = Regex("""(?i)\b(?:cilt|volume|vol\.?)\s*(\d+(?:[.,]\d+)?)""")

    /**
     * Bölüm numarasını çözer; hiçbir ipucu yoksa `-1f` döner.
     *
     * @param mangaTitle Seri başlığı (numara çıkarımında temizlenir).
     * @param chapterName Eklentiden gelen bölüm adı.
     * @param declaredNumber Eklentinin bildirdiği numara (yoksa negatif).
     */
    fun resolve(
        mangaTitle: String,
        chapterName: String,
        declaredNumber: Float? = null
    ): Float {
        val declared = declaredNumber ?: -1f
        if (declared >= 0f) return declared

        turkishChapter.find(chapterName)?.let { m ->
            parseNumber(m.groupValues[1])?.let { return it }
        }

        val name = chapterName.trim()
        if (name.isEmpty()) return -1f

        val cleanedForRecognition = stripVolume(name)
        val recognized = ChapterRecognition.parseChapterNumber(
            mangaTitle = mangaTitle,
            chapterName = cleanedForRecognition,
            chapterNumber = null
        )
        if (recognized >= 0.0) return recognized.toFloat()

        // Son çare: başlığın en başındaki sayı ("137 - Chu Chu ...").
        leadingNumber.find(name)?.let { m ->
            parseNumber(m.groupValues[1])?.let { return it }
        }

        return -1f
    }

    /**
     * Numarayı kullanıcıya gösterilecek biçime çevirir: `137`, `12.5`, `10.99`.
     * Negatifse `null` döner (rozet çizilmez).
     */
    fun label(number: Float): String? {
        if (number < 0f) return null
        if (number == number.toInt().toFloat()) return number.toInt().toString()
        return String.format(java.util.Locale.US, "%.2f", number)
            .trimEnd('0')
            .trimEnd('.')
    }

    /** "Cilt 2" bilgisini ayıklar; yoksa null. */
    fun volumeOf(chapterName: String): Int? {
        val m = volumePattern.find(chapterName) ?: return null
        return m.groupValues[1].replace(',', '.').toFloatOrNull()?.toInt()
    }

    private fun stripVolume(name: String): String =
        volumePattern.replace(name, " ")

    private fun parseNumber(raw: String): Float? =
        raw.replace(',', '.').toFloatOrNull()
}
