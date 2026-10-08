package com.kitsugi.animelist.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.BasicTextField
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Sade, tema uyumlu (koyu/açık) arama çubuğu.
 *
 * Metin girişi için Compose'un yeniden yazılmış durum tabanlı
 * [BasicTextField]'ını kullanır: tek satırlık metin taştığında parmakla
 * sağa/sola kaydırarak gezinmeye (pan) izin verir, böylece uzun
 * aramalarda metni silmeden başına/sonuna ulaşılabilir.
 */
@Composable
fun KitsugiCosmicSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClearQuery: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Tüm platformlarda ara...",
    leadingContent: (@Composable () -> Unit)? = null,
    onFilterClick: (() -> Unit)? = null,
    isFilterActive: Boolean = false,
    height: Dp = 56.dp
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    var isFocused by remember { mutableStateOf(false) }
    val accentColor = LocalKitsugiAccent.current

    // Durum tabanlı metin alanı: yatay kaydırma (pan) desteği sağlar.
    val textFieldState = remember { TextFieldState(query) }
    val latestQuery by rememberUpdatedState(query)
    val latestOnQueryChange by rememberUpdatedState(onQueryChange)

    // Dışarıdan gelen query değişikliklerini (temizle, geçmiş, vb.) içeri yansıt.
    LaunchedEffect(query) {
        if (query != textFieldState.text.toString()) {
            textFieldState.setTextAndPlaceCursorAtEnd(query)
        }
    }

    // İçeride yazılan metni dışarıya aktar.
    LaunchedEffect(Unit) {
        snapshotFlow { textFieldState.text.toString() }
            .collect { t ->
                if (t != latestQuery) latestOnQueryChange(t)
            }
    }

    val shape = RoundedCornerShape(16.dp)

    Box(
        modifier = modifier
            .height(height)
            .clip(shape)
            .onFocusChanged { isFocused = it.hasFocus }
            .background(KitsugiColors.Surface, shape)
            .border(
                width = if (isFocused) 1.5.dp else 1.dp,
                color = if (isFocused) accentColor else KitsugiColors.Border,
                shape = shape
            ),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (leadingContent != null) {
                leadingContent()
            } else {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = "Ara",
                    tint = if (isFocused) accentColor else KitsugiColors.TextMuted,
                    modifier = Modifier.size(20.dp)
                )
            }

            BasicTextField(
                state = textFieldState,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 4.dp),
                singleLine = true,
                cursorBrush = SolidColor(accentColor),
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = KitsugiColors.TextPrimary,
                    fontWeight = FontWeight.Normal,
                    fontSize = 15.sp
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        onSearch()
                        keyboardController?.hide()
                    }
                ),
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (textFieldState.text.isEmpty()) {
                            Text(
                                text = placeholder,
                                color = KitsugiColors.TextMuted,
                                style = MaterialTheme.typography.bodyMedium,
                                fontSize = 14.sp,
                                maxLines = 1
                            )
                        }
                        innerTextField()
                    }
                }
            )

            // Temizle Butonu ("X")
            AnimatedVisibility(
                visible = query.isNotEmpty(),
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut()
            ) {
                IconButton(
                    onClick = {
                        onClearQuery()
                        keyboardController?.hide()
                    },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Temizle",
                        tint = KitsugiColors.TextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            if (onFilterClick != null) {
                SimpleFilterIconButton(
                    onClick = onFilterClick,
                    isActive = isFilterActive
                )
            }
        }
    }
}

/**
 * Sade, tema uyumlu filtre butonu.
 */
@Composable
fun SimpleFilterIconButton(
    onClick: () -> Unit,
    isActive: Boolean = false,
    modifier: Modifier = Modifier
) {
    val accentColor = LocalKitsugiAccent.current
    val filterShape = RoundedCornerShape(12.dp)
    Box(
        modifier = modifier
            .size(38.dp)
            .clip(filterShape)
            .background(
                if (isActive) accentColor.copy(alpha = 0.15f) else KitsugiColors.SurfaceSoft,
                filterShape
            )
            .border(
                width = if (isActive) 1.5.dp else 1.dp,
                color = if (isActive) accentColor else KitsugiColors.Border,
                shape = filterShape
            )
            .clip(filterShape)
            .androidx.compose.foundation.clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.Tune,
            contentDescription = "Filtreler",
            tint = if (isActive) accentColor else KitsugiColors.TextMuted,
            modifier = Modifier.size(18.dp)
        )
    }
}

/**
 * Arama çubuğunun yanındaki kare yardımcı buton (Eklenti Portalı vb.).
 * Sade, tema uyumlu yüzey + kenarlık.
 */
@Composable
fun CosmicCompanionButton(
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    isActive: Boolean = false
) {
    val accentColor = LocalKitsugiAccent.current
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(KitsugiColors.Surface, shape)
            .border(
                width = if (isActive) 1.5.dp else 1.dp,
                color = if (isActive) accentColor else KitsugiColors.Border,
                shape = shape
            )
            .clip(shape)
            .androidx.compose.foundation.clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        icon()
    }
}

// Geriye uyumluluk: eski isim hâlâ referanslanıyorsa diye basit yönlendirme.
@Composable
fun CosmicFilterIconButton(
    onClick: () -> Unit,
    isActive: Boolean = false,
    modifier: Modifier = Modifier
) {
    SimpleFilterIconButton(onClick = onClick, isActive = isActive, modifier = modifier)
}
