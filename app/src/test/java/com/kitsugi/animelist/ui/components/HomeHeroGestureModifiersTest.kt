package com.kitsugi.animelist.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeHeroGestureModifiersTest {
    @Test
    fun `front hero uses neighbors from the newly settled page`() {
        assertEquals(
            2,
            frontHeroPageIndex(
                currentPage = 2,
                currentPageOffsetFraction = 0f,
                visiblePageIndices = listOf(2, 1, 3)
            )
        )
    }

    @Test
    fun `front hero follows the visually dominant page during a swipe`() {
        assertEquals(
            2,
            frontHeroPageIndex(
                currentPage = 2,
                currentPageOffsetFraction = -0.25f,
                visiblePageIndices = listOf(2, 1, 3)
            )
        )
    }
}
