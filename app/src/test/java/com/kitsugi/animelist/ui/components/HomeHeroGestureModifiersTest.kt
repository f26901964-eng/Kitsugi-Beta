package com.kitsugi.animelist.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeHeroGestureModifiersTest {
    @Test fun onlyTheSettledPageIsFullyActive() {
        assertEquals(1f, heroPageVisibility(2, 0f, 2), 0.001f)
        assertEquals(0f, heroPageVisibility(2, 0f, 1), 0.001f)
        assertEquals(0f, heroPageVisibility(2, 0f, 3), 0.001f)
    }

    @Test fun indicatorVisibilityIsContinuousWhenCurrentPageChangesMidSwipe() {
        assertEquals(0.49f, heroPageVisibility(0, 0.49f, 1), 0.001f)
        assertEquals(0.51f, heroPageVisibility(1, -0.49f, 1), 0.001f)
        assertEquals(0.49f, heroPageVisibility(1, -0.49f, 0), 0.001f)
    }

    @Test fun distantIndicatorsNeverReceiveANegativeAlpha() {
        assertEquals(0f, heroPageVisibility(40, 0.2f, 0), 0.001f)
        assertEquals(0f, heroPageVisibility(0, -0.2f, 40), 0.001f)
    }
}
