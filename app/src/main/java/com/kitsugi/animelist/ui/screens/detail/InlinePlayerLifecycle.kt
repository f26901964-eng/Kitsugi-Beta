package com.kitsugi.animelist.ui.screens.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/**
 * Detay sayfalarındaki satır içi (inline) oynatıcılar için yaşam döngüsü kapısı.
 *
 * Döndürülen durum `true` iken uygulama ön plandadır (RESUMED). Uygulama alt sekmeye,
 * arka plana veya başka bir pencereye alındığında (ON_PAUSE / ON_STOP) durum `false`
 * olur; çağıran taraf bu anda oynatıcıyı durdurmalıdır. Uygulama geri geldiğinde
 * oynatma kendiliğinden devam ETMEZ — kullanıcı oynat düğmesine basana kadar durur.
 */
@Composable
internal fun rememberInlinePlayerResumed(): State<Boolean> {
    val lifecycleOwner = LocalLifecycleOwner.current
    val resumed = remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> resumed.value = true
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> resumed.value = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return resumed
}
