package com.kitsugi.animelist.ui.screens.detail.wiki

internal data class WikiDomText(val id: Int, val revision: Int, val text: String)
internal data class WikiDomResult(val item: WikiDomText, val translated: String?)

/** Batches nearby text slots for context; validates every delimiter before assigning a
 * result. A damaged/reordered delimiter can never put text into a different link/node.
 */
internal object WikiDomTranslationBatch {
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
        require(rows.size in 1..8)
        require(rows.all { it.text.length in 1..8000 })
        if (rows.size == 1) return listOf(WikiDomResult(rows[0], translate(rows[0].text)))
        // Literal marker text must not be confused with our transport markers.
        if (rows.none { marker.containsMatchIn(it.text) }) {
            val response = translate(pack(rows))
                ?: return rows.map { WikiDomResult(it, null) } // outage: do not fan out eight more requests
            val values = unpack(response, rows.size)
            if (values != null) return rows.mapIndexed { i, row -> WikiDomResult(row, values[i]) }
        }
        // Provider changed delimiters: bounded, individual fallback, never positional guessing.
        return rows.map { WikiDomResult(it, translate(it.text)) }
    }
}
