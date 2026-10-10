package com.kitsugi.animelist.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.compose.foundation.clickable
import com.kitsugi.animelist.ui.utils.tvClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.ui.theme.LocalIsTv
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccentBrush
import com.kitsugi.animelist.ui.theme.LocalKitsugiOnAccent
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.Switch
import com.kitsugi.animelist.ui.theme.gradient.Text
import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.theme.gradient.border
import com.kitsugi.animelist.ui.theme.isSameAccentRgb
import androidx.compose.ui.graphics.vector.ImageVector

@Composable
fun KitsugiSettingsSection(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        Text(
            text = title.uppercase(),
            color = KitsugiColors.TextMuted,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
        )

        if (subtitle != null) {
            Text(
                text = subtitle,
                color = KitsugiColors.TextSecondary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 4.dp, bottom = 12.dp)
            )
        } else {
            Spacer(modifier = Modifier.height(4.dp))
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = KitsugiColors.Surface
            )
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                content = content
            )
        }
    }
}

@Composable
fun KitsugiSettingsItem(
    title: String,
    description: String,
    icon: ImageVector,
    iconColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    val isTv = LocalIsTv.current
    val accentColor = LocalKitsugiAccent.current
    var isFocused by remember { mutableStateOf(false) }

    val focusModifier = if (isTv) {
        Modifier
            .onFocusChanged { isFocused = it.isFocused }
            .background(
                if (isFocused) accentColor.copy(alpha = 0.12f)
                else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
    } else {
        Modifier
    }

    Row(
        modifier = modifier
            .then(focusModifier)
            .fillMaxWidth()
            .tvClickable(shape = RoundedCornerShape(12.dp), onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingsIcon(
            icon = icon,
            color = iconColor
        )

        Spacer(modifier = Modifier.width(14.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                color = KitsugiColors.TextPrimary,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(3.dp))

            Text(
                text = description,
                color = KitsugiColors.TextSecondary,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Text(
            text = "›",
            color = KitsugiColors.TextMuted,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun KitsugiSettingsSwitchItem(
    title: String,
    description: String,
    icon: ImageVector,
    iconColor: Color,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val isTv = LocalIsTv.current
    val accentColor = LocalKitsugiAccent.current
    var isFocused by remember { mutableStateOf(false) }

    val focusModifier = if (isTv) {
        Modifier
            .onFocusChanged { isFocused = it.isFocused }
            .background(
                if (isFocused && enabled) accentColor.copy(alpha = 0.12f)
                else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
    } else {
        Modifier
    }

    Row(
        modifier = modifier
            .then(focusModifier)
            .fillMaxWidth()
            .tvClickable(shape = RoundedCornerShape(12.dp), enabled = enabled) {
                onCheckedChange(!checked)
            }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val finalIconColor = if (enabled) iconColor else KitsugiColors.TextMuted
        SettingsIcon(
            icon = icon,
            color = finalIconColor
        )

        Spacer(modifier = Modifier.width(14.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                color = if (enabled) KitsugiColors.TextPrimary else KitsugiColors.TextMuted,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(3.dp))

            Text(
                text = description,
                color = if (enabled) KitsugiColors.TextSecondary else KitsugiColors.TextMuted,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = KitsugiColors.Background,
                checkedTrackColor = accentColor,
                uncheckedThumbColor = KitsugiColors.TextSecondary,
                uncheckedTrackColor = KitsugiColors.SurfaceStrong
            )
        )
    }
}

/**
 * Uiverse (Galahhad) Sun/Moon animasyonlu tema geçiş butonu ile donatılmış ayar satırı.
 */
@Composable
fun KitsugiThemeModeSwitchItem(
    title: String,
    description: String,
    icon: ImageVector,
    iconColor: Color,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val finalIconColor = if (enabled) iconColor else KitsugiColors.TextMuted
        SettingsIcon(
            icon = icon,
            color = finalIconColor
        )

        Spacer(modifier = Modifier.width(14.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                color = if (enabled) KitsugiColors.TextPrimary else KitsugiColors.TextMuted,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(3.dp))

            Text(
                text = description,
                color = if (enabled) KitsugiColors.TextSecondary else KitsugiColors.TextMuted,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        KitsugiThemeSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
    }
}


@Composable
fun KitsugiSettingsListItem(
    title: String,
    description: String,
    value: String,
    icon: ImageVector,
    iconColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    val isTv = LocalIsTv.current
    val accentColor = LocalKitsugiAccent.current
    var isFocused by remember { mutableStateOf(false) }

    val focusModifier = if (isTv) {
        Modifier
            .onFocusChanged { isFocused = it.isFocused }
            .background(
                if (isFocused) accentColor.copy(alpha = 0.12f)
                else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
    } else {
        Modifier
    }

    Row(
        modifier = modifier
            .then(focusModifier)
            .fillMaxWidth()
            .tvClickable(shape = RoundedCornerShape(12.dp), onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingsIcon(
            icon = icon,
            color = iconColor
        )

        Spacer(modifier = Modifier.width(14.dp))

        // Title + description + value stacked in one column — no layout fight
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                color = KitsugiColors.TextPrimary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(1.dp))

            Text(
                text = description,
                color = KitsugiColors.TextSecondary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Value badge — accent-tinted pill
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(accentColor.copy(alpha = 0.15f))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = value,
                    color = accentColor,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = "›",
            color = KitsugiColors.TextMuted,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

/** Açılır menünün çıpa noktasından boşluğu. */
private val KitsugiMenuGap = 6.dp

/** Menünün ekran kenarına en az bırakılan boşluğu. */
private val KitsugiMenuScreenMargin = 8.dp

/**
 * Menü konumlandırıcı: çıpanın (tetikleyicinin) altına açar; alt tarafa sığmazsa üstüne
 * açar. Yatayda ekran kenarlarına taşmayacak şekilde sıkıştırır.
 */
private class KitsugiMenuPositionProvider(
    private val gapPx: Int,
    private val marginPx: Int
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val maxX = (windowSize.width - popupContentSize.width - marginPx).coerceAtLeast(marginPx)
        val x = anchorBounds.left.coerceIn(marginPx, maxX)

        val below = anchorBounds.bottom + gapPx
        val maxY = windowSize.height - popupContentSize.height - marginPx
        val y = if (below <= maxY) {
            below
        } else {
            val above = anchorBounds.top - gapPx - popupContentSize.height
            if (above >= marginPx) above else maxY.coerceAtLeast(marginPx)
        }
        return IntOffset(x, y)
    }
}

/**
 * Uygulamanın yüzey diliyle uyumlu açılır menü (Material'ın varsayılan DropdownMenu'su yerine).
 * - Yüzey: [KitsugiColors.SurfaceStrong], 20dp köşe, ince [KitsugiColors.Border] çerçeve, yumuşak gölge
 * - Animasyon: fade + hafif scale, üst kenardan açılıp kapanır
 * - Uzun listeler kaydırılır; [modifier] iç sütuna uygulanır (genişlik/yükseklik sınırı için)
 *
 * Tüm açılır menüler (ayarlar, listeler, arama, detay) bu bileşeni kullanır.
 */
@Composable
fun KitsugiDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val visibleState = remember { MutableTransitionState(false) }
    visibleState.targetState = expanded
    // Kapanış animasyonu bitene kadar popup'ı canlı tut; sonra tamamen kaldır.
    if (!visibleState.currentState && !visibleState.targetState) return

    val density = LocalDensity.current
    val gapPx = with(density) { KitsugiMenuGap.roundToPx() }
    val marginPx = with(density) { KitsugiMenuScreenMargin.roundToPx() }
    val positionProvider = remember(gapPx, marginPx) {
        KitsugiMenuPositionProvider(gapPx = gapPx, marginPx = marginPx)
    }
    val shape = RoundedCornerShape(20.dp)

    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(focusable = true)
    ) {
        AnimatedVisibility(
            visibleState = visibleState,
            enter = fadeIn(animationSpec = tween(durationMillis = 170, easing = FastOutSlowInEasing)) +
                scaleIn(
                    animationSpec = tween(durationMillis = 170, easing = FastOutSlowInEasing),
                    initialScale = 0.92f,
                    transformOrigin = TransformOrigin(0.5f, 0f)
                ),
            exit = fadeOut(animationSpec = tween(durationMillis = 120)) +
                scaleOut(
                    animationSpec = tween(durationMillis = 120),
                    targetScale = 0.95f,
                    transformOrigin = TransformOrigin(0.5f, 0f)
                )
        ) {
            Box(
                modifier = Modifier
                    .shadow(elevation = 12.dp, shape = shape, clip = false)
                    .clip(shape)
                    .background(KitsugiColors.SurfaceStrong, shape)
                    .border(1.dp, KitsugiColors.Border, shape)
            ) {
                Column(
                    modifier = modifier
                        .widthIn(min = 168.dp, max = 320.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(6.dp),
                    content = content
                )
            }
        }
    }
}

/**
 * Açılır menü satırı. [text] herhangi bir composable olabilir.
 * [selected] true ise satır accent renkli yarı saydam zemine oturur ve sağda onay işareti çıkar
 * (özel [trailingIcon] verilmediyse). [leadingIcon] solda gösterilir.
 */
@Composable
fun KitsugiMenuItem(
    text: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    selected: Boolean = false,
    enabled: Boolean = true
) {
    val accentColor = LocalKitsugiAccent.current
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(shape)
            .background(
                if (selected) accentColor.copy(alpha = 0.15f) else Color.Transparent,
                shape
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .alpha(if (enabled) 1f else 0.38f),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leadingIcon != null) {
            leadingIcon()
            Spacer(modifier = Modifier.width(12.dp))
        }
        Box(modifier = Modifier.weight(1f)) {
            // Satır içeriğinin varsayılan rengi uygulama metin rengi olsun (Popup'ta tema rengi gelmez).
            CompositionLocalProvider(
                LocalContentColor provides (if (selected) accentColor else KitsugiColors.TextPrimary)
            ) {
                text()
            }
        }
        when {
            trailingIcon != null -> {
                Spacer(modifier = Modifier.width(8.dp))
                trailingIcon()
            }
            selected -> {
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/**
 * Metin tabanlı açılır menü satırı. [selected] true ise accent renk + kalın yazı + onay işareti.
 */
@Composable
fun KitsugiDropdownItem(
    text: String,
    onClick: () -> Unit,
    selected: Boolean = false
) {
    val accentColor = LocalKitsugiAccent.current
    KitsugiMenuItem(
        text = {
            Text(
                text = text,
                color = if (selected) accentColor else KitsugiColors.TextPrimary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
            )
        },
        onClick = onClick,
        selected = selected
    )
}

@Composable
fun KitsugiSettingsDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .padding(start = 74.dp)
            .background(KitsugiColors.Border)
    )
}

@Composable
private fun SettingsIcon(
    icon: ImageVector,
    color: Color
) {
    // Vurgu rengi kullanılıyorsa düz/gradyan tema fırçası; aksi halde düz renk.
    // İkon tint'i, zeminin koyuluğuna göre otomatik siyah-beyaz kontrast alır.
    val accentColor = LocalKitsugiAccent.current
    val isAccent = color.isSameAccentRgb(accentColor)
    val bgBrush = if (isAccent) LocalKitsugiAccentBrush.current else androidx.compose.ui.graphics.SolidColor(color)
    val tint = if (isAccent) LocalKitsugiOnAccent.current else com.kitsugi.animelist.ui.theme.onAccentColor(color)
    Box(
        modifier = Modifier
            .size(42.dp)
            .background(
                brush = bgBrush,
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
    }
}