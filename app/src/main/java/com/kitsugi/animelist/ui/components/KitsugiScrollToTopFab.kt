package com.kitsugi.animelist.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.Icon
import com.kitsugi.animelist.ui.theme.gradient.FloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.R
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.theme.onAccentColor
import com.kitsugi.animelist.utils.ScrollVisibilityState
import androidx.compose.ui.res.stringResource

/**
 * FAB'ın sık değişen alt-bar state'ini ağır ekran composable'larından izole eder.
 * State yalnız bu küçük restart scope içinde okunur; Keşfet/Arama listesi yeniden
 * compose edilmez. Padding animasyonu da yalnız bu düğümü ölçer.
 */
@Composable
fun KitsugiScrollToTopFab(
    visible: Boolean,
    isLandscape: Boolean,
    bottomBarScrollState: ScrollVisibilityState?,
    fallbackBottomBarVisible: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bottomBarVisible = bottomBarScrollState?.isVisible ?: fallbackBottomBarVisible
    val navigationPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomPadding by animateDpAsState(
        targetValue = when {
            isLandscape -> 16.dp
            bottomBarVisible -> 80.dp + navigationPadding
            else -> 16.dp + navigationPadding
        },
        animationSpec = tween(200),
        label = "scroll_to_top_bottom_padding"
    )
    val accent = LocalKitsugiAccent.current

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + scaleIn(),
        exit = fadeOut() + scaleOut(),
        modifier = modifier.padding(end = 20.dp, bottom = bottomPadding)
    ) {
        FloatingActionButton(
            onClick = onClick,
            containerColor = accent,
            contentColor = onAccentColor(accent),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.size(52.dp)
        ) {
            Icon(Icons.Default.ArrowUpward, contentDescription = stringResource(R.string.yukari_cik))
        }
    }
}
