package com.kitsugi.animelist.ui.components

import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.model.WatchStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KitsugiMediaEntryCardTest {
    @get:Rule val compose = createComposeRule()

    private fun checkLayout(layout: String) {
        var clicks = 0
        compose.setContent {
            MaterialTheme {
                KitsugiMediaEntryCard(
                    entry = MediaEntry(
                        id = 1, title = "A library title", type = MediaType.Anime,
                        status = WatchStatus.Watching, score = 50, progress = 5, total = 12,
                        source = "anilist"
                    ),
                    layoutId = layout,
                    modifier = Modifier.width(if (layout == "grid_2col") 112.dp else 360.dp).testTag("card"),
                    sourceBadges = listOf("anilist", "al", "mal", "jikan", "kitsu", "simkl", "bangumi", "shikimori", "tmdb"),
                    onClick = { clicks++ }
                )
            }
        }
        val card = compose.onNodeWithTag("card")
        val bounds = card.fetchSemanticsNode().boundsInRoot
        listOf("mal", "kitsu", "simkl", "bangumi", "shikimori", "tmdb").forEach { source ->
            val nodes = compose.onAllNodesWithContentDescription(source, useUnmergedTree = true)
            nodes.assertCountEquals(1)
            val badge = nodes[0]
            badge.assertIsDisplayed()
            val badgeBounds = badge.fetchSemanticsNode().boundsInRoot
            assertTrue("$source must stay inside $layout card", bounds.contains(badgeBounds.topLeft) && bounds.contains(badgeBounds.bottomRight))
        }
        card.performClick()
        compose.runOnIdle { assertEquals(1, clicks) }
    }

    @Test fun compactBadgesBelongToTheCard() = checkLayout("compact")
    @Test fun comfortableBadgesBelongToTheCard() = checkLayout("comfortable")
    @Test fun largeBadgesBelongToTheCard() = checkLayout("large")
    @Test fun minimalistBadgesBelongToTheCard() = checkLayout("minimalist")
    @Test fun narrowGridWrapsAllBadgesInsideItsFrame() = checkLayout("grid_2col")
}
