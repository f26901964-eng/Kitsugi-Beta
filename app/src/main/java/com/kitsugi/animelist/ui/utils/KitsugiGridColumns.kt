package com.kitsugi.animelist.ui.utils

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration

/**
 * Medya kutucuk ızgaraları için tek merkezden duyarlı sütun sayısı.
 *
 * - Dar ekran (telefon dikey, < 600dp): 2 sütun
 * - Orta ekran (telefon yatay / küçük tablet, 600–839dp): 3 sütun
 * - Geniş ekran (tablet yatay, ≥ 840dp): 4 sütun
 *
 * Dikey ve yatay modda aynı kurala uyar; ekran döndüğünde ızgara otomatik yeniden hesaplanır.
 */
fun kitsugiGridColumnsForWidth(screenWidthDp: Int): Int = when {
    screenWidthDp >= 840 -> 4
    screenWidthDp >= 600 -> 3
    else -> 2
}

@Composable
fun rememberKitsugiGridColumns(): Int {
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    return kitsugiGridColumnsForWidth(screenWidthDp)
}
