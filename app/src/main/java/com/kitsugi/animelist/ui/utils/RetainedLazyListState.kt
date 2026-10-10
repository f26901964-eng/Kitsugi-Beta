package com.kitsugi.animelist.ui.utils

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow

/**
 * Scroll konumunu snapshot state'te tutma. Aksi halde her piksel yazımı
 * [rememberLazyListState] argümanını yeniden okur, sayfa yeniden kurulur ve
 * liste başlangıç ofsetine geri zıplar.
 */
private class RetainedScrollPosition(var index: Int, var offset: Int)

private val RetainedScrollPositionSaver = listSaver<RetainedScrollPosition, Int>(
    save = { listOf(it.index, it.offset) },
    restore = { RetainedScrollPosition(it[0], it[1]) }
)

/**
 * Lazy lists clamp a restored index to zero if their loading placeholder has fewer items.
 * Keep the last real position outside LazyListState and replay it after results return.
 * SaveableStateProvider at the navigation level retains this snapshot across push/pop.
 */
@Composable
fun rememberRetainedLazyListState(
    contentReady: Boolean,
    initialIndex: Int = 0,
    initialOffset: Int = 0
): LazyListState {
    val retained = rememberSaveable(saver = RetainedScrollPositionSaver) {
        RetainedScrollPosition(initialIndex, initialOffset)
    }
    val state = remember { LazyListState(retained.index, retained.offset) }
    var restored by remember { mutableStateOf(false) }

    LaunchedEffect(state, contentReady) {
        if (contentReady && !restored) {
            if (state.firstVisibleItemIndex != retained.index ||
                state.firstVisibleItemScrollOffset != retained.offset
            ) {
                state.scrollToItem(retained.index, retained.offset)
            }
            restored = true
        }
    }
    LaunchedEffect(state, contentReady, restored) {
        if (contentReady && restored) {
            snapshotFlow { state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset }
                .collect { (index, offset) ->
                    retained.index = index
                    retained.offset = offset
                }
        }
    }
    return state
}
