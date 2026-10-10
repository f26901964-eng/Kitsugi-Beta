package com.kitsugi.animelist.ui.screens.detail

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Detay sayfası galerisinin yükleme takipçisi.
 *
 * Bir galeri; TMDB, Shikimori, Fanart.tv, Jikan, AniList, Bangumi gibi **birden fazla kaynaktan**
 * ve birden fazla işten (detay, galeri, detay sonrası yenileme) oluşur. [loading] yalnızca
 * BÜTÜN işler bittiğinde `false` olur. Bu süre boyunca galeri butonu yükleniyor animasyonunda
 * kalır ve resimlere / galeriye dokunulamaz.
 *
 * Her yeni içerik yüklemesinde [reset] çağrılır ve yeni bir "nesil" (generation) başlar.
 * Eski nesle ait işler bittiğinde [end] onları yok sayar; böylece sayaç bozulmaz.
 *
 * Kullanım:
 * ```
 * val token = tracker.begin()
 * try { ... } finally { tracker.end(token) }
 * ```
 */
class GalleryLoadTracker {

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private var generation = 0
    private var pending = 0

    /** Yeni içerik yüklemesi: bekleyen işleri sıfırlar, nesli artırır ve yükleniyor durumuna geçer. */
    @Synchronized
    fun reset(): Int {
        generation++
        pending = 0
        _loading.value = true
        return generation
    }

    /** Yeni bir galeri işi başladı. Dönen belirteç [end]'e verilmelidir. */
    @Synchronized
    fun begin(): Int {
        pending++
        _loading.value = true
        return generation
    }

    /** Bir galeri işi bitti. Eski nesle ait belirteçler yok sayılır. */
    @Synchronized
    fun end(token: Int) {
        if (token != generation) return
        if (pending > 0) pending--
        if (pending == 0) _loading.value = false
    }
}
