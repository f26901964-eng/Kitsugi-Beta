package com.kitsugi.animelist.ui.screens.mylist

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.ui.components.KitsugiEmptyState

/**
 * Empty state shown when the user hasn't connected their account.
 */
@Composable
internal fun MyListNotConnectedState(
    selectedTabIndex: Int,
    isSimklSessionExpired: Boolean,
    onLogin: () -> Unit
) {
    val title = when (selectedTabIndex) {
        MY_LIST_ANILIST_TAB_INDEX -> "AniList Bağlı Değil"
        MY_LIST_MAL_TAB_INDEX -> "MyAnimeList Bağlı Değil"
        MY_LIST_SIMKL_TAB_INDEX -> {
            if (isSimklSessionExpired) "Simkl Oturum Süresi Doldu"
            else "Simkl Bağlı Değil"
        }
        MY_LIST_KITSU_TAB_INDEX -> "Kitsu Bağlı Değil"
        MY_LIST_SHIKIMORI_TAB_INDEX -> "Shikimori Bağlı Değil"
        MY_LIST_BANGUMI_TAB_INDEX -> "Bangumi Bağlı Değil"
        else -> "Hesap Bağlı Değil"
    }

    val subtitle = when (selectedTabIndex) {
        MY_LIST_ANILIST_TAB_INDEX -> "AniList kütüphanenizi görüntülemek için hesabınızı bağlayın."
        MY_LIST_MAL_TAB_INDEX -> "MyAnimeList kütüphanenizi görüntülemek için hesabınızı bağlayın."
        MY_LIST_SIMKL_TAB_INDEX -> {
            if (isSimklSessionExpired)
                "Simkl oturum süresi doldu. Senkronizasyonu sürdürmek için tekrar bağlayın."
            else
                "Simkl kütüphanenizi görüntülemek için hesabınızı bağlayın."
        }
        MY_LIST_KITSU_TAB_INDEX -> "Kitsu kütüphanenizi görüntülemek için hesabınızı bağlayın."
        MY_LIST_SHIKIMORI_TAB_INDEX -> "Shikimori kütüphanenizi görüntülemek için hesabınızı bağlayın."
        MY_LIST_BANGUMI_TAB_INDEX -> "Bangumi (bgm.tv) koleksiyonunuzu görüntülemek için hesabınızı bağlayın."
        else -> "Kütüphanenizi görüntülemek için hesabınızı bağlayın."
    }

    val isSimklTab = selectedTabIndex == MY_LIST_SIMKL_TAB_INDEX
    val actionText = if (isSimklSessionExpired && isSimklTab) "Yeniden Bağlan" else "Hesabı Bağla"

    KitsugiEmptyState(
        title = title,
        subtitle = subtitle,
        icon = if (isSimklSessionExpired && isSimklTab) Icons.Rounded.Refresh else Icons.Rounded.AccountCircle,
        actionText = actionText,
        onActionClick = onLogin
    )
    Spacer(modifier = Modifier.height(18.dp))
}

/**
 * Empty state shown when the connected list has no entries yet.
 */
@Composable
internal fun MyListSyncPromptState() {
    KitsugiEmptyState(
        title = "Listeniz boş",
        subtitle = "Hesabınızdaki verileri çekmek için \"Senkronize Et\" butonunu kullanın.",
        icon = Icons.Rounded.FormatListBulleted
    )
    Spacer(modifier = Modifier.height(90.dp))
}
