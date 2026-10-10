package com.kitsugi.animelist.ui.utils

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.collect

/**
 * Kaydırma konumu tutucusu.
 *
 * BİLİNÇLİ OLARAK Compose state DEĞİL düz bir nesne: konum her kaydırma karesinde
 * yazılıyor. Compose state olsaydı, bu değeri okuyan composable (Keşfet / Arama
 * ekranının tamamı) her karede yeniden kompoze olurdu → kaydırma kasmasının kök nedeni.
 * Değer yalnızca kaydetme (save-instance-state) anında okunur.
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
    val position = rememberSaveable(saver = RetainedScrollPositionSaver) {
        RetainedScrollPosition(initialIndex, initialOffset)
    }
    // Yalnızca ilk oluşturmada okunur; sonrasında gözlemlenmeyen düz alanlar okunduğu için
    // bu satır kompozisyonu geçersiz kılmaz.
    val state = rememberLazyListState(position.index, position.offset)
    var restored by remember { mutableStateOf(false) }

    LaunchedEffect(state, contentReady) {
        if (contentReady && !restored) {
            state.scrollToItem(position.index, position.offset)
            restored = true
        }
    }
    LaunchedEffect(state, contentReady, restored) {
        if (contentReady && restored) {
            snapshotFlow { state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset }
                .collect { (index, offset) ->
                    position.index = index
                    position.offset = offset
                }
        }
    }
    return state
}
