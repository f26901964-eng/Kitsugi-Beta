package com.kitsugi.animelist.ui.utils

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.collect
import androidx.compose.runtime.snapshotFlow

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
    var savedIndex by rememberSaveable { mutableIntStateOf(initialIndex) }
    var savedOffset by rememberSaveable { mutableIntStateOf(initialOffset) }
    val state = rememberLazyListState(savedIndex, savedOffset)
    var restored by androidx.compose.runtime.remember { mutableStateOf(false) }

    LaunchedEffect(state, contentReady) {
        if (contentReady && !restored) {
            state.scrollToItem(savedIndex, savedOffset)
            restored = true
        }
    }
    LaunchedEffect(state, contentReady, restored) {
        if (contentReady && restored) {
            snapshotFlow { state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset }
                .collect { (index, offset) ->
                    savedIndex = index
                    savedOffset = offset
                }
        }
    }
    return state
}
