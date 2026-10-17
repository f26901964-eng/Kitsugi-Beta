package com.kitsugi.animelist.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.R
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.utils.tvClickable

/**
 * Poster'in sağ üst köşesine binen kompakt "listeye ekle" kısayolu.
 *
 * "Tümünü gör" sayfasının grid VE liste görünümünde aynı yerde (poster üzerinde)
 * durduğu için layout yer kaplamaz; her iki görünüme de mevcut rozet diline uyum sağlar.
 * Kayıt listede ise yeşil tik gösterir ve dokunma düzenleme panelini açar
 * (davranış üst bileşende kararlaştırılır).
 */
@Composable
fun KitsugiQuickAddButton(
    inList: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accentColor = LocalKitsugiAccent.current
    val shape = remember { CircleShape }
    Box(
        modifier = modifier
            .size(30.dp)
            .clip(shape)
            .background(
                if (inList) {
                    KitsugiColors.AccentGreen.copy(alpha = 0.92f)
                } else {
                    accentColor.copy(alpha = 0.92f)
                }
            )
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.45f),
                shape = shape
            )
            .tvClickable(shape = shape, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (inList) Icons.Rounded.Check else Icons.Rounded.Add,
            contentDescription = if (inList) {
                stringResource(R.string.duzenle)
            } else {
                stringResource(R.string.listeye_ekle)
            },
            tint = Color.White,
            modifier = Modifier.size(16.dp)
        )
    }
}
