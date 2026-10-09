package com.kitsugi.animelist.ui.screens.profile.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.ui.app.AniListProfileState
import com.kitsugi.animelist.ui.app.KitsugiProfileViewModel
import com.kitsugi.animelist.ui.app.ProfileFavoriteItem
import com.kitsugi.animelist.ui.screens.profile.ProfileFavoritesListemContent
import com.kitsugi.animelist.ui.theme.KitsugiColors

@Composable
fun AniListFavoritesTab(
    state: AniListProfileState,
    accentColor: Color,
    isLandscape: Boolean,
    favoritesFilter: Int,
    layoutId: String,
    blurAdultMedia: Boolean,
    viewModel: KitsugiProfileViewModel,
    onFavoriteMediaClick: (mediaId: Int, mediaType: MediaType, source: String, title: String, imageUrl: String?) -> Unit,
    onFavoriteCharacterClick: (charId: Int, source: String, name: String?, imageUrl: String?) -> Unit,
    onFavoriteStaffClick: (staffId: Int, source: String, name: String?, imageUrl: String?) -> Unit,
    onFavoriteStudioClick: ((studioId: Int, source: String, name: String?, imageUrl: String?) -> Unit)? = null,
    onOpenFavoriteSheet: (title: String, items: List<ProfileFavoriteItem>, onClick: (ProfileFavoriteItem) -> Unit) -> Unit
) {
    val currentFavList = when (favoritesFilter) {
        0 -> state.favoriteAnime; 1 -> state.favoriteManga; 2 -> state.favoriteCharacters
        3 -> state.favoriteStaff; 4 -> state.favoriteStudios; else -> emptyList()
    }
    val filterTitle = when (favoritesFilter) {
        0 -> "Favori Animeler"; 1 -> "Favori Mangalar"; 2 -> "Favori Karakterler"
        3 -> "Favori Ekip"; 4 -> "Favori Stüdyolar"; else -> "Favoriler"
    }

    // Tıklama: sekmeye göre doğru detay callback'i
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
        Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
            Text(text = "Favori öge bulunamadı.", color = KitsugiColors.TextMuted)
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
                            onOpenFavoriteSheet(filterTitle, currentFavList, onItemClick)
                        }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(text = "Tümünü Gör", color = accentColor, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
            }

            // Listem ile aynı kart düzenleri; sayfalama dış listede otomatik (buton yok)
            ProfileFavoritesListemContent(
                entries = currentFavList,
                layoutId = layoutId,
                blurAdultMedia = blurAdultMedia,
                isLandscape = isLandscape,
                onItemClick = onItemClick
            )
        }
    }
}
