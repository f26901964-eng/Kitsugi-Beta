package com.kitsugi.animelist.data.manga

import androidx.compose.runtime.snapshots.Snapshot
import org.junit.Assert.*
import org.junit.Test

class MangaPageStateTest {
    @Test fun statusIsObservableWithoutReplacingThePageList() {
        val page = MangaPage(0, "/chapter/1")
        var observedReads = 0
        Snapshot.observe(readObserver = { observedReads++ }) {
            assertEquals(MangaPageStatus.Queue, page.status)
        }
        assertTrue("Compose must subscribe to status reads", observedReads > 0)
        page.status = MangaPageStatus.Ready
        assertEquals(MangaPageStatus.Ready, page.status)
    }

    @Test fun pagesInDifferentChaptersHaveIndependentState() {
        val old = MangaPage(0, "/chapter/1")
        val next = MangaPage(0, "/chapter/2")
        old.status = MangaPageStatus.Error
        assertEquals(MangaPageStatus.Queue, next.status)
        assertNotSame(old, next)
    }
}
