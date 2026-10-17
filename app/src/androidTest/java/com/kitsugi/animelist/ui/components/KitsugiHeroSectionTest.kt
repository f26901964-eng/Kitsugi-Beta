package com.kitsugi.animelist.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kitsugi.animelist.data.remote.JikanSearchResult
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.ui.theme.LocalIsTvDevice
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Device-only pager/layout regressions. Network image contents are not asserted by these tests. */
@RunWith(AndroidJUnit4::class)
class KitsugiHeroSectionTest {
    @get:Rule val compose = createComposeRule()

    private fun item(id: Int) = JikanSearchResult(
        malId = id, title = "Hero $id", subtitle = if (id % 2 == 0) "" else "Anime, 2026",
        type = MediaType.Anime, total = null, score = null, isAdult = false,
        imageUrl = "https://example.test/poster_$id.jpg", year = 2026, source = "hero_test",
        backdropUrl = "https://example.test/wide_$id.jpg"
    )

    private fun setHero(landscape: Boolean = false, onClick: (JikanSearchResult) -> Unit = {}) {
        val config = Configuration().apply {
            screenWidthDp = if (landscape) 844 else 390
            screenHeightDp = if (landscape) 390 else 844
            orientation = if (landscape) Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT
        }
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalConfiguration provides config, LocalIsTvDevice provides false) {
                    KitsugiHeroSection(
                        items = (1..4).map(::item), alreadyInList = { false }, onInfoClick = onClick,
                        artworkPrepared = true, isVisible = false
                    )
                }
            }
        }
    }

    private fun assertSwipeKeepsHeight(landscape: Boolean) {
        setHero(landscape)
        val hero = compose.onNodeWithTag("home_hero")
        val height = hero.fetchSemanticsNode().boundsInRoot.height
        repeat(3) { index ->
            hero.performTouchInput { swipeLeft(durationMillis = 650) }
            compose.waitForIdle()
            compose.onNodeWithText("Hero ${index + 2}", useUnmergedTree = true).assertIsDisplayed()
            assertEquals(height, hero.fetchSemanticsNode().boundsInRoot.height, 0.1f)
        }
        repeat(3) {
            hero.performTouchInput { swipeRight(durationMillis = 650) }
            compose.waitForIdle()
            assertEquals(height, hero.fetchSemanticsNode().boundsInRoot.height, 0.1f)
        }
        compose.onNodeWithText("Hero 1", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun portraitSwipesKeepOneFixedBand() = assertSwipeKeepsHeight(landscape = false)
    @Test fun landscapeSwipesKeepOneFixedBand() = assertSwipeKeepsHeight(landscape = true)

    @Test fun clickAfterSwipeOpensTheActuallyVisibleItem() {
        var opened: Int? = null
        setHero(onClick = { opened = it.malId })
        val hero = compose.onNodeWithTag("home_hero")
        hero.performTouchInput { swipeLeft(durationMillis = 650) }
        compose.waitForIdle()
        hero.performTouchInput { click(center.copy(y = 30f)) }
        compose.runOnIdle { assertEquals(2, opened) }
    }

    @Test fun titleTapIsNotCancelledByThePointerDownRecomposition() {
        var opened: Int? = null
        setHero(onClick = { opened = it.malId })
        compose.onNodeWithText("Hero 1", useUnmergedTree = true).performTouchInput { click() }
        compose.waitUntil(timeoutMillis = 2_000) { opened == 1 }
    }

    @Test fun rightSwipeAtTheFirstPageDoesNotWrapOrRaceThroughTheCarousel() {
        setHero()
        compose.onNodeWithTag("home_hero").performTouchInput { swipeRight(durationMillis = 650) }
        compose.waitForIdle()
        compose.onNodeWithText("Hero 1", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun logoLoadingAndFailureKeepTheTitleSlotAndFallbackText() {
        val logo = mutableStateOf<String?>(null)
        compose.setContent {
            MaterialTheme {
                HeroTitleSlot(
                    title = "A long title that must not move the carousel", logoUrl = logo.value,
                    isLandscape = false,
                    modifier = Modifier.width(300.dp).height(84.dp).testTag("hero_title_slot"), onClick = {}
                )
            }
        }
        val slot = compose.onNodeWithTag("hero_title_slot")
        val bounds = slot.fetchSemanticsNode().boundsInRoot
        compose.runOnIdle { logo.value = "https://example.test/unavailable_logo.png" }
        compose.waitForIdle()
        assertEquals(bounds, slot.fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithText("A long title that must not move the carousel").assertIsDisplayed()
    }

    @Test fun addingACompletedSourceKeepsTheActiveItemKey() {
        val items = mutableStateOf((1..3).map(::item))
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalIsTvDevice provides false) {
                    KitsugiHeroSection(
                        items = items.value, alreadyInList = { false }, onInfoClick = {},
                        artworkPrepared = true, isVisible = false
                    )
                }
            }
        }
        val hero = compose.onNodeWithTag("home_hero")
        hero.performTouchInput { swipeLeft(durationMillis = 650) }
        compose.waitForIdle()
        compose.runOnIdle { items.value = listOf(item(4)) + items.value }
        compose.waitForIdle()
        compose.onNodeWithText("Hero 2", useUnmergedTree = true).assertIsDisplayed()
    }
}
