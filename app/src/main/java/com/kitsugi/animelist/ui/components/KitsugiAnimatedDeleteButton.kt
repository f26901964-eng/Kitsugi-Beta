package com.kitsugi.animelist.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.R
import com.kitsugi.animelist.ui.theme.KitsugiColors

/**
 * Basit silme butonu - animasyonsuz, tek tıklamada siler.
 *
 * @param onDelete          Silme işlemi onaylandığında çalışacak callback.
 * @param modifier          Dış düzenleyici modifier.
 * @param size              Buton boyutu (varsayılan: 36.dp).
 * @param contentDescription Erişilebilirlik açıklaması.
 */
@Composable
fun KitsugiAnimatedDeleteButton(
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    contentDescription: String? = null
) {
    IconButton(
        onClick = onDelete,
        modifier = modifier.size(size)
    ) {
        Icon(
            imageVector = Icons.Rounded.Delete,
            contentDescription = contentDescription ?: stringResource(R.string.action_delete),
            tint = KitsugiColors.AccentRed,
            modifier = Modifier.size((size.value * 0.52f).dp)
        )
    }
}
