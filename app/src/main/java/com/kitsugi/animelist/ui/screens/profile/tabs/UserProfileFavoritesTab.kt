package com.kitsugi.animelist.ui.screens.profile.tabs

import com.kitsugi.animelist.ui.theme.gradient.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import com.kitsugi.animelist.ui.theme.gradient.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.data.settings.AppSettings
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.ui.app.ProfileFavoriteItem
import com.kitsugi.animelist.ui.screens.profile.KitsugiUserProfileViewModel
import com.kitsugi.animelist.ui.screens.profile.OtherUserProfileState
import com.kitsugi.animelist.ui.screens.profile.ProfileFavoritesListemContent
import com.kitsugi.animelist.ui.theme.KitsugiColors

/**
 * Diğer kullanıcının favorileri — kendi profil favorileriyle aynı Listem görünümü.
 * Sayfalama dış LazyColumn'da (KitsugiUserProfileScreen) kaydırmaya göre yapılır.
 */
@Composable
fun UserProfileFavoritesTab(
    state: OtherUserProfileState,
    accentColor: Color,
    appSettings: AppSettings,
    viewModel: KitsugiUserProfileViewModel,
    isLandscape: Boolean,
    onFavoriteMediaClick: (Int, MediaType, String, String, String?) -> Unit,
    onFavoriteCharacterClick: (Int, String, String?, String?) -> Unit,
    onFavoriteStaffClick: (Int, String, String?, String?) -> Unit,
    onFavoriteStudioClick: ((Int, String, String?, String?) -> Unit)?,
    onOpenSheet: (String, List<ProfileFavoriteItem>) -> Unit
) {
    val favoritesFilter = viewModel.favoritesFilter
    val currentFavList = when (favoritesFilter) {
        0 -> state.favoriteAnime
        1 -> state.favoriteManga
        2 -> state.favoriteCharacters
        3 -> state.favoriteStaff
        4 -> state.favoriteStudios
        else -> emptyList()
    }

    val filterTitle = when (favoritesFilter) {
        0 -> "Favori Animeler"
        1 -> "Favori Mangalar"
        2 -> "Favori Karakterler"
        3 -> "Favori Ekip"
        4 -> "Favori Stüdyolar"
        else -> "Favoriler"
    }

    val onItemClick: (ProfileFavoriteItem) -> Unit = { item ->
        item.id.toIntOrNull()?.let { id ->
            when (favoritesFilter) {
                0 -> onFavoriteMediaClick(id, MediaType.Anime, "anilist", item.title, item.imageUrl)
                1 -> onFavoriteMediaClick(id, MediaType.Manga, "anilist", item.title, item.imageUrl)
                2 -> onFavoriteCharacterClick(id, "anilist", item.title, item.imageUrl)
                3 -> onFavoriteStaffClick(id, "anilist", item.title, item.imageUrl)
                4 -> onFavoriteStudioClick?.invoke(id, "anilist", item.title, item.imageUrl)
            }
        }
    }

    if (currentFavList.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "Favori öğe bulunamadı.", color = KitsugiColors.TextMuted)
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "$filterTitle (${currentFavList.size})",
                    color = KitsugiColors.TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(KitsugiColors.SurfaceStrong)
                        .clickable {
                            onOpenSheet(filterTitle, currentFavList)
                        }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Tümünü Gör",
                        color = accentColor,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            ProfileFavoritesListemContent(
                entries = currentFavList,
                layoutId = appSettings.selectedListLayoutId,
                blurAdultMedia = appSettings.blurAdultMedia,
                isLandscape = isLandscape,
                onItemClick = onItemClick
            )
        }
    }
}
