package com.kitsugi.animelist.utils

import java.nio.charset.StandardCharsets
import java.util.Locale

/**
 * Bangumi şirket adları sözlüğü — **stüdyo / yapımcı / yayıncı ağ / yayımcı / dergi**.
 *
 * Veri kaynağı: `app/src/main/resources/bangumi/orgs.tsv` (`<etiket>\t<anahtar>\t<en>[\t<tr>]`).
 * Classpath kaynağından tembel (`by lazy`) okunur; hem APK'da hem JVM birim testlerinde Context
 * gerektirmeden çalışır ve JVM sınıf sabit havuzu sınırına takılmaz.
 */
internal object BangumiOrgDictionary {

    private const val STORE_RESOURCE = "bangumi/orgs.tsv"

    private class Row(
        val label: String,
        val key: String,
        val english: String,
        val turkish: String
    )

    private val rows: List<Row> by lazy { loadRows() }

    private fun loadRows(): List<Row> {
        val stream = Thread.currentThread().contextClassLoader?.getResourceAsStream(STORE_RESOURCE)
            ?: BangumiOrgDictionary::class.java.getResourceAsStream("/$STORE_RESOURCE")
            ?: error("Bangumi şirket sözlüğü kaynağı bulunamadı: $STORE_RESOURCE")
        val loaded = ArrayList<Row>(65536)
        stream.bufferedReader(StandardCharsets.UTF_8).useLines { lines ->
            for (raw in lines) {
                if (raw.isEmpty()) continue
                val parts = raw.split('\t')
                if (parts.size < 3) continue
                val tr = if (parts.size >= 4 && parts[3].isNotEmpty()) parts[3] else parts[2]
                loaded.add(Row(parts[0], parts[1], parts[2], tr))
            }
        }
        check(loaded.isNotEmpty()) { "Bangumi şirket sözlüğü boş yüklendi: $STORE_RESOURCE" }
        return loaded
    }

    private val byLabel: Map<String, Row> by lazy {
        val map = HashMap<String, Row>(rows.size * 2)
        for (row in rows) {
            val key = normalize(row.label)
            if (key.isNotEmpty() && !map.containsKey(key)) map[key] = row
        }
        map
    }

    private val byCompact: Map<String, Row> by lazy {
        val map = HashMap<String, Row>(rows.size * 2)
        for (row in rows) {
            val key = compact(row.label)
            if (key.length >= 3 && !map.containsKey(key)) map[key] = row
        }
        map
    }

    private val byKey: Map<String, String> by lazy {
        val map = HashMap<String, String>(rows.size * 2)
        for (row in rows) if (!map.containsKey(row.key)) map[row.key] = row.english
        map
    }

    /** Gelen şirket adı için `bangumi_*` dil dosyası anahtarı; bilinmiyorsa `null`. */
    fun resourceKeyFor(raw: String?): String? = rowFor(raw)?.key

    /** Gelen adın küresel İngilizce yazımı (`京阿尼` → `Kyoto Animation`); bilinmiyorsa `null`. */
    fun latinNameFor(raw: String?): String? = rowFor(raw)?.english

    /** Gelen adın arayüz diline göre yerelleştirilmiş gösterimi; bilinmiyorsa `null`. */
    fun localizedNameFor(raw: String?, turkish: Boolean): String? =
        rowFor(raw)?.let { if (turkish) it.turkish else it.english }

    /** Anahtarın küresel İngilizce adı; yoksa `null`. */
    fun canonicalName(key: String): String? = byKey[key]

    /** Eşleşen satır; bilinmiyorsa `null`. */
    private fun rowFor(raw: String?): Row? {
        val cleaned = raw?.trim().orEmpty()
        if (cleaned.isEmpty()) return null
        byLabel[normalize(cleaned)]?.let { return it }
        val compact = compact(cleaned)
        if (compact.length >= 3) byCompact[compact]?.let { return it }
        return null
    }

    /** Girdi sayısı (test/telemetri için). */
    val size: Int get() = rows.size

    /** Boşluk, büyük/küçük harf, tam genişlik noktalama ve sarımları yok sayan anahtar. */
    private fun normalize(value: String): String {
        if (value.isBlank()) return ""
        val sb = StringBuilder(value.length)
        for (ch in value) {
            when (ch) {
                '　' -> sb.append(' ')
                '（', '(', '[', '【', '《', '「', '『' -> Unit
                '）', ')', ']', '】', '》', '」', '』' -> Unit
                '：', ':', '・', '·', '。', '、', '，', ',' -> sb.append(' ')
                else -> sb.append(ch)
            }
        }
        return sb.toString().trim().replace(Regex("\\s+"), " ").lowercase(Locale.ROOT)
    }

    /** Yalnızca harf/rakam bırakan sıkı anahtar: `Production.IG` → `productionig`. */
    private fun compact(value: String): String {
        if (value.isBlank()) return ""
        val sb = StringBuilder(value.length)
        for (ch in value) if (ch.isLetterOrDigit()) sb.append(ch)
        return sb.toString().lowercase(Locale.ROOT)
    }
}
