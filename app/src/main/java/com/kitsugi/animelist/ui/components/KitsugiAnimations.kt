package com.kitsugi.animelist.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

// ─── Motion Token'ları ─────────────────────────────────────────────────────
// KitsugiMobile Tokens.kt'deki motion.normalMillis vb. değerlerden uyarlama.
// Merkezi yönetimle tüm tween sürelerini tek yerden güncelleme imkânı sağlar.

object KitsugiMotion {
    /** Hızlı mikro-etkileşimler: buton basımı, chip toggle */
    const val fastMillis = 150

    /** Normal navigasyon geçişleri: sağdan/soldan slayt */
    const val normalMillis = 280

    /** Yavaş modal / bottom sheet açılma */
    const val slowMillis = 380

    /** Tab geçiş crossfade */
    const val tabFadeMillis = 200

    /** Sheet enter — FloatingPrompt gibi sürüklenebilir paneller */
    const val sheetEnterMillis = 420

    /** Detay içindeki açılır alanlar ve inline video alanları */
    const val detailRevealMillis = 320
    const val detailCollapseMillis = 240
    const val detailRevealFadeMillis = 200

    /** Kaydırma ile beliren detay mini başlıkları */
    const val stickyHeaderEnterMillis = 260
    const val stickyHeaderExitMillis = 200

    /** İçerik yüksekliği değişirken (ör. uzun metin / video alanı) kullanılan ortak süre */
    const val detailResizeMillis = 320
}

// ─── Detay sayfası içi ortak açılma / kapanma hareketleri ──────────────────
// Açılır listeler, inline oynatıcılar ve wiki akordiyonları aynı yumuşak ritmi
// paylaşır; görünüm değişmeden yalnızca içerik yüksekliği ve belirme hareketi animasyonlanır.

private fun detailRevealEnterTransition(): EnterTransition =
    expandVertically(
        animationSpec = tween(KitsugiMotion.detailRevealMillis, easing = FastOutSlowInEasing),
        expandFrom = Alignment.Top
    ) +
        fadeIn(
            animationSpec = tween(KitsugiMotion.detailRevealFadeMillis, easing = LinearOutSlowInEasing)
        )

private fun detailRevealExitTransition(): ExitTransition =
    shrinkVertically(
        animationSpec = tween(KitsugiMotion.detailCollapseMillis, easing = FastOutSlowInEasing),
        shrinkTowards = Alignment.Top
    ) +
        fadeOut(
            animationSpec = tween(KitsugiMotion.detailRevealFadeMillis, easing = FastOutLinearInEasing)
        )

private fun stickyHeaderEnterTransition(): EnterTransition =
    expandVertically(
        animationSpec = tween(KitsugiMotion.stickyHeaderEnterMillis, easing = FastOutSlowInEasing),
        expandFrom = Alignment.Top
    ) +
        fadeIn(
            animationSpec = tween(KitsugiMotion.stickyHeaderEnterMillis - 40, easing = LinearOutSlowInEasing)
        ) +
        slideInVertically(
            animationSpec = tween(KitsugiMotion.stickyHeaderEnterMillis, easing = FastOutSlowInEasing)
        ) { fullHeight -> -fullHeight / 3 }

private fun stickyHeaderExitTransition(): ExitTransition =
    shrinkVertically(
        animationSpec = tween(KitsugiMotion.stickyHeaderExitMillis, easing = FastOutSlowInEasing),
        shrinkTowards = Alignment.Top
    ) +
        fadeOut(
            animationSpec = tween(KitsugiMotion.stickyHeaderExitMillis, easing = FastOutLinearInEasing)
        ) +
        slideOutVertically(
            animationSpec = tween(KitsugiMotion.stickyHeaderExitMillis, easing = FastOutSlowInEasing)
        ) { fullHeight -> -fullHeight / 3 }

/** Standard reveal used by detail-page accordions, expandable lists and inline content. */
@Composable
fun KitsugiDetailReveal(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable AnimatedVisibilityScope.() -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = detailRevealEnterTransition(),
        exit = detailRevealExitTransition(),
        content = content
    )
}

/** Same shared motion, with a slightly quicker down/up sweep for sticky mini toolbars. */
@Composable
fun KitsugiStickyHeaderReveal(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable AnimatedVisibilityScope.() -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = stickyHeaderEnterTransition(),
        exit = stickyHeaderExitTransition(),
        content = content
    )
}

/** Shared resize spec for detail sections whose children are added/removed in place. */
fun Modifier.animateDetailContentSize(): Modifier =
    animateContentSize(
        animationSpec = tween(
            durationMillis = KitsugiMotion.detailResizeMillis,
            easing = FastOutSlowInEasing
        )
    )

/**
 * Hysteresis for mini toolbars tied to a LazyColumn: show after the hero boundary and
 * hide only after returning above it, so a boundary index cannot flicker the header.
 */
@Composable
fun rememberDetailStickyHeaderVisible(
    listState: LazyListState,
    showAtIndex: Int,
    hideAtIndex: Int
): Boolean {
    require(hideAtIndex < showAtIndex) { "hideAtIndex must be smaller than showAtIndex" }

    var isVisible by remember(listState, showAtIndex, hideAtIndex) { mutableStateOf(false) }
    LaunchedEffect(listState, showAtIndex, hideAtIndex) {
        snapshotFlow { listState.firstVisibleItemIndex }.collect { index ->
            when {
                index >= showAtIndex -> isVisible = true
                index <= hideAtIndex -> isVisible = false
            }
        }
    }
    return isVisible
}

// ─── Detay Sayfasına Giriş Geçişi ─────────────────────────────────────────
// Sağdan sola süzülerek giren detay sayfası (Android default NavHost hareketi).

fun enterDetailTransition(): EnterTransition =
    slideInHorizontally(tween(KitsugiMotion.normalMillis)) { it } +
        fadeIn(tween(KitsugiMotion.normalMillis))

fun exitForDetailTransition(): ExitTransition =
    slideOutHorizontally(tween(KitsugiMotion.normalMillis)) { -it / 4 } +
        fadeOut(tween(KitsugiMotion.fastMillis + 50))

// ─── Detay Sayfasından Çıkış Geçişi ───────────────────────────────────────
// Detay kapanırken soldan geri döner, önceki içerik sağa çekilir.

fun enterFromDetailTransition(): EnterTransition =
    slideInHorizontally(tween(KitsugiMotion.normalMillis)) { -it / 4 } +
        fadeIn(tween(KitsugiMotion.normalMillis))

fun exitDetailTransition(): ExitTransition =
    slideOutHorizontally(tween(KitsugiMotion.normalMillis)) { it } +
        fadeOut(tween(KitsugiMotion.fastMillis + 50))

// ─── Tab Geçiş Animasyonu ─────────────────────────────────────────────────
// Tab değişimlerinde sade crossfade.

fun tabEnterTransition(): EnterTransition =
    fadeIn(tween(KitsugiMotion.tabFadeMillis))

fun tabExitTransition(): ExitTransition =
    fadeOut(tween(KitsugiMotion.tabFadeMillis - 20))

// ─── ContentTransform Yardımcıları ────────────────────────────────────────
// AppRoot.kt'nin AnimatedContent transitionSpec'inde kullanım kolaylığı sağlar.

fun detailEnterContentTransform(): ContentTransform =
    enterDetailTransition() togetherWith exitForDetailTransition()

fun detailExitContentTransform(): ContentTransform =
    enterFromDetailTransition() togetherWith exitDetailTransition()

fun tabContentTransform(): ContentTransform =
    tabEnterTransition() togetherWith tabExitTransition()

// ─── Sayfa İçi Fade-In Geçiş ──────────────────────────────────────────────
// KitsugiMobile PersonDetailContent'teki AnimatedVisibility(visible=true, enter=fadeIn())
// uyarlaması — detay sayfası içeriği ilk kez render edildiğinde yumuşak belirme.

fun pageContentEnterTransition(): EnterTransition =
    fadeIn(tween(KitsugiMotion.slowMillis)) +
        slideInVertically(tween(KitsugiMotion.slowMillis)) { it / 8 }
