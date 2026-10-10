package com.kitsugi.animelist.ui.utils

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * KitsugiScrollDefaults — TV-native Scroll Davranışı
 *
 * KitsugiTV-dev referansı: BringIntoViewSpec / ScrollBehavior.
 *
 * Android TV D-pad navigasyonunda fokus alan öğe, liste sınırında
 * kısmi görünürken kesilmeden tam olarak ekrana getirilir.
 *
 * Kullanım — LazyRow/LazyColumn'da CompositionLocalProvider ile:
 * ```kotlin
 * val tvSpec = KitsugiScrollDefaults.rememberTvCenteredSpec()
 * CompositionLocalProvider(LocalBringIntoViewSpec provides tvSpec) {
 *     LazyRow(...) { ... }
 * }
 * ```
 *
 * ⚠️ Bu API @ExperimentalFoundationApi kapsamındadır.
 */
object KitsugiScrollDefaults {

    /**
     * Yatay kart satırları için "tık tık" sola yapışan kaydırma.
     *
     * Parmak bırakıldığında satır, en yakın kartın sol kenarı içerik
     * dolgusunun (contentPadding) başına oturacak şekilde durur. Fling
     * davranışı yalnızca satırın kendi (yatay) ekseninde çalışır; dikey
     * kaydırmayı etkilemez.
     */
    @Composable
    fun rememberRowSnapFling(state: LazyListState): FlingBehavior =
        rememberSnapFlingBehavior(
            lazyListState = state,
            snapPosition = SnapPosition.Start
        )

    /**
     * Fokus alan öğeyi viewport'un yatay merkezine getirir.
     * TV D-pad sola/sağa gezinimde doğal, yumuşak scroll sağlar.
     *
     * @param animationSpec Scroll geçiş animasyonu — varsayılan spring ile TV-native hissiyat.
     * @param centeringFraction 0f=sol kenar, 0.5f=merkez, 1f=sağ kenar. TV için 0.5f.
     */
    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    fun rememberTvCenteredSpec(
        animationSpec: AnimationSpec<Float> = spring(),
        centeringFraction: Float = 0.5f
    ): BringIntoViewSpec {
        return remember(centeringFraction, animationSpec) {
            object : BringIntoViewSpec {
                override val scrollAnimationSpec: AnimationSpec<Float> = animationSpec

                override fun calculateScrollDistance(
                    offset: Float,
                    size: Float,
                    containerSize: Float
                ): Float {
                    // Öğe merkezini hesapla
                    val itemCenter = offset + size / 2f
                    // Viewport'un hedef merkezi
                    val viewportCenter = containerSize * centeringFraction
                    val scrollNeeded = itemCenter - viewportCenter

                    return when {
                        offset < 0f -> scrollNeeded                    // Sol kenardan taşıyor
                        offset + size > containerSize -> scrollNeeded  // Sağ kenardan taşıyor
                        else -> 0f                                     // Görünür, scroll gereksiz
                    }
                }
            }
        }
    }

    /**
     * Dokunmatik kaydırmada odaklanan kartın listeyi geri çekmesini kapatır.
     *
     * `clickable` / `combinedClickable` öğeleri odaklanabilir. Varsayılan
     * [BringIntoViewSpec], kısmen ekrandan çıkan odaklı kartı yay animasyonuyla
     * tekrar içeri alır. Parmak kaydırmasıyla bu animasyon çarpışınca sayfa ve
     * kutucuklar gidip gelir, dikey kaydırma kilitlenir. TV'de D-pad spec'i
     * kullanılmaya devam eder; telefonda kaydırma tamamen kullanıcıya aittir.
     */
    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    fun rememberTouchPassthroughSpec(): BringIntoViewSpec {
        return remember {
            object : BringIntoViewSpec {
                override val scrollAnimationSpec: AnimationSpec<Float> = spring()

                override fun calculateScrollDistance(
                    offset: Float,
                    size: Float,
                    containerSize: Float
                ): Float = 0f
            }
        }
    }

    /**
     * Pager yüksekliğini ölçülen içerikten beslerken 1px'lik ölçüm gürültüsünü yok say.
     * Aksi halde yükseklik yazımı sayfayı yeniden ölçer, ölçüm tekrar değişir ve
     * dikey kaydırma gidip gelir.
     */
    fun recordStablePageHeight(heights: MutableMap<Int, Int>, page: Int, measuredPx: Int) {
        val previous = heights[page]
        if (previous == null || kotlin.math.abs(previous - measuredPx) > 4) {
            heights[page] = measuredPx
        }
    }

    /**
     * Fokus alan satırın (row) ekranın dikey koordinatında belirli bir inset'te (örneğin 280.dp)
     * kalmasını sağlayan dikey scroll davranışı.
     */
    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    fun rememberTvVerticalInsetSpec(
        insetDp: androidx.compose.ui.unit.Dp,
        canScrollBackwardProvider: () -> Boolean,
        animationSpec: AnimationSpec<Float> = spring()
    ): BringIntoViewSpec {
        val density = androidx.compose.ui.platform.LocalDensity.current
        return remember(insetDp, density, animationSpec) {
            val insetPx = with(density) { insetDp.toPx() }
            object : BringIntoViewSpec {
                override val scrollAnimationSpec: AnimationSpec<Float> = animationSpec

                override fun calculateScrollDistance(
                    offset: Float,
                    size: Float,
                    containerSize: Float
                ): Float {
                    val currentLeadingEdge = offset
                    if (kotlin.math.abs(currentLeadingEdge - insetPx) < 1f) return 0f
                    val distance = currentLeadingEdge - insetPx
                    if (distance < 0f && !canScrollBackwardProvider()) return 0f
                    return distance
                }
            }
        }
    }
}
