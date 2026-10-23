package com.kitsugi.animelist.ui.screens.detail.wiki

internal data class WikiDomText(val id: Int, val revision: Int, val text: String)
internal data class WikiDomResult(val item: WikiDomText, val translated: String?)

/** Batches nearby text slots for context; validates every delimiter before assigning a
 * result. A damaged/reordered delimiter can never put text into a different link/node.
 *
 * Aynı metin bir sayfada birden çok kez geçebilir (gezinme etiketleri, tekrarlanan
 * tablo satırları, "devamını oku" gibi kalıplar). Bu durumda ağ isteği metin başına
 * BİR kez yapılır ve sonuç tüm eşleşen düğümlere dağıtılır; çeviri belirgin biçimde
 * hızlanır, Google'a giden gereksiz istek sayısı düşer.
 */
internal object WikiDomTranslationBatch {
    /** dom-translator.js `take()` ile aynı üst sınır. */
    const val MAX_ROWS = 12

    private val marker = Regex("⟪(/?)K([0-9]+)⟫")

    fun pack(rows: List<WikiDomText>): String = rows.mapIndexed { index, row ->
        "⟪K$index⟫${row.text}⟪/K$index⟫"
    }.joinToString("\n")

    fun unpack(text: String, count: Int): List<String>? {
        val matches = marker.findAll(text).toList()
        if (matches.size != count * 2) return null
        val values = ArrayList<String>(count)
        var previousEnd = 0
        for (index in 0 until count) {
            val open = matches[index * 2]
            val close = matches[index * 2 + 1]
            if (open.value != "⟪K$index⟫" || close.value != "⟪/K$index⟫") return null
            if (text.substring(previousEnd, open.range.first).isNotBlank()) return null
            val value = text.substring(open.range.last + 1, close.range.first).trim()
            if (value.isBlank()) return null
            values.add(value)
            previousEnd = close.range.last + 1
        }
        return values.takeIf { text.substring(previousEnd).isBlank() }
    }

    suspend fun translate(rows: List<WikiDomText>, translate: suspend (String) -> String?): List<WikiDomResult> {
        require(rows.size in 1..MAX_ROWS)
        require(rows.all { it.text.length in 1..8000 })
        // Aynı kaynak metin → aynı çeviri. Tekrarları istekten önce ele.
        val unique = LinkedHashMap<String, WikiDomText>()
        rows.forEach { unique.putIfAbsent(it.text, it) }
        val uniqueRows = unique.values.toList()
        val translations = when {
            uniqueRows.size == 1 -> mapOf(uniqueRows[0].text to translate(uniqueRows[0].text))
            // Literal marker text must not be confused with our transport markers.
            uniqueRows.none { marker.containsMatchIn(it.text) } -> {
                val response = translate(pack(uniqueRows))
                val values = response?.let { unpack(it, uniqueRows.size) }
                if (values != null) {
                    uniqueRows.mapIndexed { index, row -> row.text to values[index] }.toMap()
                } else {
                    // Provider changed delimiters: bounded, individual fallback, never positional guessing.
                    uniqueRows.associate { it.text to translate(it.text) }
                }
            }
            else -> uniqueRows.associate { it.text to translate(it.text) }
        }
        return rows.map { row -> WikiDomResult(row, translations[row.text]) }
    }
}
