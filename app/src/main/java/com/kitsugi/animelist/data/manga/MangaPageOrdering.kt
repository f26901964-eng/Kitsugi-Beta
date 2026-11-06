package com.kitsugi.animelist.data.manga

/** Numeric page ordering shared by persistent downloads, offline directories and ZIP/CBZ readers. */
internal object MangaPageOrdering {
    val comparator = Comparator<String>(::compare)

    fun compare(left: String, right: String): Int {
        val leftNumber = pageNumber(left)
        val rightNumber = pageNumber(right)
        when {
            leftNumber != null && rightNumber != null -> {
                val numeric = leftNumber.compareTo(rightNumber)
                if (numeric != 0) return numeric
            }
            leftNumber != null -> return -1
            rightNumber != null -> return 1
        }
        val insensitive = left.compareTo(right, ignoreCase = true)
        return if (insensitive != 0) insensitive else left.compareTo(right)
    }

    private fun pageNumber(value: String): Long? = value
        .substringAfterLast('/')
        .substringAfterLast('\\')
        .substringBeforeLast('.')
        .toLongOrNull()
}
