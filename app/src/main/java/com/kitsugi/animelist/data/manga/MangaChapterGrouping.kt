package com.kitsugi.animelist.data.manga

/**
 * Bölümleri CİLT (volume) gruplarına ayırır.
 *
 * Neden? Mihon/Tachiyomi ve Kotatsu, uzun serilerde (One Piece, 1000+ bölüm)
 * bölüm listesini cilt başlıklarıyla katlar; kullanıcı istediğinde grubu açar.
 * Kitsugi'de bu yoktu → 1000 satırlık düz liste.
 */
object MangaChapterGrouping {

    /** Bir grup (cilt veya "Cilt bilgisi yok"). */
    data class Group<T>(
        val volume: Int?,
        val chapters: List<T>
    ) {
        val title: String get() = volume?.let { "Cilt $it" } ?: "Diğer"
    }

    /**
     * @param volumeOf Satırın cilt numarası (yoksa null).
     * @param number Satırın bölüm numarası (sıralamayı korumak için).
     */
    fun <T> groupByVolume(
        rows: List<T>,
        volumeOf: (T) -> Int?,
        number: (T) -> Float = { -1f }
    ): List<Group<T>> {
        val grouped = LinkedHashMap<Int?, MutableList<T>>()
        rows.forEach { row ->
            grouped.getOrPut(volumeOf(row)) { mutableListOf() }.add(row)
        }
        // Ciltler: bilinenler büyükten küçüğe (yeni bölümler üstte mantığı),
        // "Cilt bilgisi yok" grubu en sonda.
        return grouped.entries
            .sortedWith(compareBy({ it.key == null }, { -(it.key ?: 0) }))
            .map { (volume, items) ->
                Group(volume, items.sortedByDescending { number(it) })
            }
    }

    /** Grup içinde en yüksek bölüm numarası (özet etiketi için). */
    fun <T> rangeLabel(number: (T) -> Float): (Group<T>) -> String = { group ->
        val numbers = group.chapters.map(number).filter { it >= 0f }
        when {
            numbers.isEmpty() -> group.title
            numbers.size == 1 -> group.title
            else -> {
                val high = numbers.max()
                val low = numbers.min()
                val hi = MangaChapterNumberResolver.label(high) ?: ""
                val lo = MangaChapterNumberResolver.label(low) ?: ""
                if (hi == lo) "${group.title} · $lo" else "${group.title} · $lo–$hi"
            }
        }
    }
}
