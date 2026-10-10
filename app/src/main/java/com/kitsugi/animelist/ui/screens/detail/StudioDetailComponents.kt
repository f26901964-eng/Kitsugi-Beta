package com.kitsugi.animelist.ui.screens.detail

import com.kitsugi.animelist.ui.components.KitsugiGalleryIconButton
import androidx.compose.foundation.background
import com.kitsugi.animelist.ui.utils.tvClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.kitsugi.animelist.data.remote.KitsugiStudioDetail
import com.kitsugi.animelist.data.remote.KitsugiStaffMediaWork
import com.kitsugi.animelist.data.remote.GalleryItem
import com.kitsugi.animelist.data.remote.GalleryCategory
import com.kitsugi.animelist.data.remote.JikanSearchResult
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.utils.PreferenceHelpers.getDisplayTitle
import com.kitsugi.animelist.utils.toFriendlySourceLabel
import com.kitsugi.animelist.utils.parseToMediaType
import com.kitsugi.animelist.ui.components.KitsugiMarkdownText
import com.kitsugi.animelist.ui.components.KitsugiSheetOrDialog
import com.kitsugi.animelist.ui.components.KitsugiButton
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.FilterAlt
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.ui.unit.sp

// Icons
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Share

/**
 * Stüdyo kuruluş tarihini "1 Ekim 1998" biçiminde gösterir — ham ISO
 * ("1998-10-01T00:00:00+00:00") dizgesi asla olduğu gibi gösterilmez.
 */
internal fun formatStudioEstablished(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    return com.kitsugi.animelist.utils.KitsugiDateUtils
        .formatBirthdayAndCalculateAge(raw, null)
        .first ?: raw
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StudioHeroHeader(
    detail: KitsugiStudioDetail,
    source: String,
    accentColor: Color,
    onBackClick: () -> Unit,
    isFavourite: Boolean = false,
    showFavouriteButton: Boolean = false,
    onToggleFavourite: () -> Unit = {},
    onGalleryClick: (() -> Unit)? = null,
    height: Dp = 340.dp
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
    ) {
        // Logo / Fallback box
        if (!detail.imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = detail.imageUrl,
                contentDescription = detail.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
            // Blur or dark layer over image for visibility
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                KitsugiColors.Background.copy(alpha = 0.4f),
                                KitsugiColors.Background
                            )
                        )
                    )
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                accentColor.copy(alpha = 0.2f),
                                KitsugiColors.Background
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = detail.name.take(2).uppercase(),
                    color = accentColor,
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(bottom = 40.dp)
                )
            }
        }

        // Top Action Bar: Back (left) + Share & Favourite (right)
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, top = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(KitsugiColors.Background.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Geri",
                        tint = KitsugiColors.TextPrimary
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onGalleryClick != null) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(accentColor.copy(alpha = 0.22f)),
                        contentAlignment = Alignment.Center
                    ) {
                        KitsugiGalleryIconButton(onClick = onGalleryClick, accentColor = accentColor)
                    }
                }
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(KitsugiColors.Background.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(onClick = {
                        val url = com.kitsugi.animelist.utils.ShareUtils.buildStudioUrl(source, detail.id)
                        com.kitsugi.animelist.utils.ShareUtils.shareText(context, detail.name, url)
                    }) {
                        Icon(
                            imageVector = Icons.Rounded.Share,
                            contentDescription = "Paylaş",
                            tint = KitsugiColors.TextPrimary
                        )
                    }
                }

                if (showFavouriteButton) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(KitsugiColors.Background.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center
                    ) {
                        IconButton(onClick = onToggleFavourite) {
                            Icon(
                                imageVector = if (isFavourite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                contentDescription = if (isFavourite) "Favoriden Çıkar" else "Favori Yap",
                                tint = if (isFavourite) accentColor else KitsugiColors.TextPrimary
                            )
                        }
                    }
                }
            }
        }

        // Details Column (Name & Info Row)
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(20.dp)
        ) {
            Text(
                text = detail.name,
                color = KitsugiColors.TextPrimary,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black
            )

            Spacer(modifier = Modifier.height(12.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DetailPill(
                    text = if (detail.isMain) "Ana Stüdyo" else "Yapımcı",
                    color = accentColor
                )

                DetailSourcePill(
                    source = source,
                    color = if (source.equals("tmdb", ignoreCase = true)) Color(0xFFFFB800) else KitsugiColors.TextSecondary
                )

                if (detail.established != null) {
                    DetailPill(
                        text = "Kuruluş: ${formatStudioEstablished(detail.established) ?: detail.established}",
                        color = KitsugiColors.TextSecondary
                    )
                }

                if (detail.favorites != null && detail.favorites > 0) {
                    DetailPill(
                        text = "★ ${detail.favorites} Favori",
                        color = accentColor
                    )
                }
            }
        }
    }
}

@Composable
internal fun StudioMediaGridItem(
    work: KitsugiStaffMediaWork,
    titleLanguage: String = "ROMAJI",
    onMediaClick: (mediaId: Int, mediaType: String, mediaSource: String) -> Unit
) {
    val displayTitle = work.getDisplayTitle(titleLanguage)

    Column(
        modifier = Modifier
            .padding(horizontal = 8.dp)
            .width(105.dp)
            .tvClickable { onMediaClick(work.mediaId, work.mediaType, work.source) },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .aspectRatio(11f / 16f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(KitsugiColors.Surface)
        ) {
            if (!work.mediaImageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = work.mediaImageUrl,
                    contentDescription = displayTitle,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = displayTitle.take(2).uppercase(),
                        color = KitsugiColors.TextMuted,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = displayTitle,
            color = KitsugiColors.TextPrimary,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Stüdyo "Hakkında" kartı — detay sayfasındaki Açıklama kartıyla (DetailSynopsisCard)
 * BİREBİR aynı davranış: otomatik çeviri açıksa çevrilmiş metin, çeviri (3. parti)
 * butonu, kopyala butonu ve uzun metinlerde "Daha fazla / Daha az" genişletme.
 */
@Composable
internal fun StudioAboutSection(
    about: String,
    onGalleryClick: (List<GalleryItem>, Int) -> Unit,
    modifier: Modifier = Modifier,
    translatedAbout: String? = null,
    onTranslateClick: ((String) -> Unit)? = null,
    onCopyClick: ((String) -> Unit)? = null
) {
    Box(modifier = modifier) {
        DetailSynopsisCard(
            title = "Hakkında",
            synopsisState = SynopsisState.Success(translatedAbout ?: about),
            originalText = about,
            onTranslateClick = onTranslateClick,
            onCopyClick = onCopyClick,
            onImageGalleryRequest = { urls, idx ->
                val items = urls.map { url -> GalleryItem(url = url, category = GalleryCategory.OTHER, source = "Hakkında") }
                onGalleryClick(items, idx)
            }
        )
    }
}

@Composable
internal fun StudioDetailLeftPanel(
    detail: KitsugiStudioDetail,
    source: String,
    studioId: Int,
    accentColor: Color,
    isFavourite: Boolean,
    showFavouriteButton: Boolean,
    galleryItems: List<GalleryItem>,
    onBackClick: () -> Unit,
    onToggleFavourite: () -> Unit,
    onGalleryClick: (List<GalleryItem>, Int) -> Unit,
    modifier: Modifier = Modifier,
    translatedAbout: String? = null,
    onTranslateClick: ((String) -> Unit)? = null,
    onCopyClick: ((String) -> Unit)? = null
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .background(KitsugiColors.Background)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
        ) {
            if (!detail.imageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = detail.imageUrl,
                    contentDescription = detail.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
                Box(
                    modifier = Modifier.fillMaxSize().background(
                        Brush.verticalGradient(listOf(KitsugiColors.Background.copy(alpha = 0.4f), KitsugiColors.Background))
                    )
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize().background(
                        Brush.verticalGradient(listOf(accentColor.copy(alpha = 0.2f), KitsugiColors.Background))
                    ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(detail.name.take(2).uppercase(), color = accentColor, style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Black)
                }
            }
            // Top Action Bar: Back (left) + Share & Favourite (right)
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, top = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(KitsugiColors.Background.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Geri",
                            tint = KitsugiColors.TextPrimary
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (galleryItems.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(accentColor.copy(alpha = 0.22f)),
                            contentAlignment = Alignment.Center
                        ) {
                            KitsugiGalleryIconButton(onClick = {
                                onGalleryClick(galleryItems, 0)
                            }, accentColor = accentColor)
                        }
                    }
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(KitsugiColors.Background.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center
                    ) {
                        IconButton(onClick = {
                            val url = com.kitsugi.animelist.utils.ShareUtils.buildStudioUrl(source, studioId)
                            com.kitsugi.animelist.utils.ShareUtils.shareText(context, detail.name, url)
                        }) {
                            Icon(
                                imageVector = Icons.Rounded.Share,
                                contentDescription = "Paylaş",
                                tint = KitsugiColors.TextPrimary
                            )
                        }
                    }

                    if (showFavouriteButton) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(KitsugiColors.Background.copy(alpha = 0.45f)),
                            contentAlignment = Alignment.Center
                        ) {
                            IconButton(onClick = onToggleFavourite) {
                                Icon(
                                    imageVector = if (isFavourite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                    contentDescription = if (isFavourite) "Favoriden Çıkar" else "Favori Yap",
                                    tint = if (isFavourite) accentColor else KitsugiColors.TextPrimary
                                )
                            }
                        }
                    }
                }
            }
        }
        Column(modifier = Modifier.padding(16.dp)) {
            Text(detail.name, color = KitsugiColors.TextPrimary, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            Spacer(modifier = Modifier.height(12.dp))
            DetailPill(text = if (detail.isMain) "Ana Stüdyo" else "Yapımcı", color = accentColor)
            Spacer(modifier = Modifier.height(8.dp))
            if (detail.established != null) {
                DetailPill(
                    text = "Kuruluş: ${formatStudioEstablished(detail.established) ?: detail.established}",
                    color = KitsugiColors.TextSecondary
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            if (detail.favorites != null && detail.favorites > 0) {
                DetailPill(text = "★ ${detail.favorites} Favori", color = accentColor)
                Spacer(modifier = Modifier.height(8.dp))
            }
            if (!detail.about.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                StudioAboutSection(
                    about = detail.about,
                    onGalleryClick = onGalleryClick,
                    translatedAbout = translatedAbout,
                    onTranslateClick = onTranslateClick,
                    onCopyClick = onCopyClick
                )
            }
        }
    }
}

// ── Keşfet "Tümünü Gör" sayfalarıyla birebir aynı filtreleme dili ──────────────

/** Stüdyo yapımları için tür çipi (keşfet sayfasındaki emojili tür çiplerinin karşılığı). */
internal data class StudioTypeFilter(val id: String, val emoji: String, val name: String) {
    val displayLabel: String get() = "$emoji $name"
}

internal val STUDIO_TYPE_FILTERS = listOf(
    StudioTypeFilter("ALL", "✨", "Tümü"),
    StudioTypeFilter("ANIME", "🎬", "Anime"),
    StudioTypeFilter("MANGA", "📖", "Manga"),
    StudioTypeFilter("MOVIE", "🎥", "Film"),
    StudioTypeFilter("TV", "📺", "Dizi")
)

internal enum class StudioSortOption(val emoji: String, val title: String) {
    DEFAULT("🏆", "Varsayılan Sıralama"),
    TITLE_ASC("🔤", "İsim: A'dan Z'ye"),
    TITLE_DESC("🔠", "İsim: Z'den A'ya");

    val displayLabel: String get() = "$emoji $title"
}

/** Stüdyo yapımını keşfet kartlarıyla aynı görsel dili paylaşan JikanSearchResult'e çevirir. */
internal fun KitsugiStaffMediaWork.toSearchResult(): JikanSearchResult = JikanSearchResult(
    malId = mediaId,
    title = mediaTitle,
    subtitle = staffRole,
    type = mediaType.parseToMediaType(),
    total = null,
    score = null,
    isAdult = false,
    imageUrl = mediaImageUrl,
    year = null,
    source = source,
    titleEnglish = titleEnglish,
    titleJapanese = titleJapanese,
    titleRomaji = titleRomaji
)

internal fun studioTypeMatches(typeId: String, type: MediaType): Boolean = when (typeId) {
    "ALL" -> true
    "ANIME" -> type == MediaType.Anime
    "MANGA" -> type == MediaType.Manga
    "MOVIE" -> type == MediaType.Movie
    "TV" -> type == MediaType.TvShow
    else -> true
}

/**
 * Stüdyo/yapımcı detay sayfası için "Filtre ve Sıralama" bottom sheet'i —
 * [com.kitsugi.animelist.ui.components.KitsugiMediaFilterBottomSheet] ile aynı
 * görsel dili kullanır (sıralama + tür çipleri), ancak stüdyo verisine uyarlanır.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun KitsugiStudioFilterBottomSheet(
    initialTypeId: String,
    initialSortOption: StudioSortOption,
    onDismissRequest: () -> Unit,
    onApply: (typeId: String, sortOption: StudioSortOption) -> Unit,
    onReset: () -> Unit,
    typeFilters: List<StudioTypeFilter> = STUDIO_TYPE_FILTERS
) {
    val accentColor = LocalKitsugiAccent.current

    var selectedTypeId by remember { mutableStateOf(initialTypeId) }
    var selectedSortOption by remember { mutableStateOf(initialSortOption) }

    KitsugiSheetOrDialog(onDismiss = onDismissRequest) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = {
                        selectedTypeId = "ALL"
                        selectedSortOption = StudioSortOption.DEFAULT
                        onReset()
                        onDismissRequest()
                    }
                ) {
                    Text(
                        text = "Sıfırla",
                        color = KitsugiColors.TextMuted,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }

                Text(
                    text = "Filtre ve Sıralama",
                    color = KitsugiColors.TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                KitsugiButton(
                    onClick = {
                        onApply(selectedTypeId, selectedSortOption)
                        onDismissRequest()
                    },
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Text(
                        text = "Uygula",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Sort,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Sıralama",
                        color = KitsugiColors.TextPrimary,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StudioSortOption.entries.forEach { sort ->
                        val isSelected = selectedSortOption == sort
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (isSelected) accentColor.copy(alpha = 0.22f)
                                    else KitsugiColors.SurfaceSoft
                                )
                                .border(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) accentColor else Color.Transparent,
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .clickable { selectedSortOption = sort }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = sort.displayLabel,
                                    color = if (isSelected) accentColor else KitsugiColors.TextPrimary,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 13.sp
                                )
                                if (isSelected) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = null,
                                        tint = accentColor,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.FilterAlt,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "İçerik Türü",
                        color = KitsugiColors.TextPrimary,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    typeFilters.forEach { typeFilter ->
                        val isSelected = selectedTypeId == typeFilter.id
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (isSelected) accentColor.copy(alpha = 0.22f)
                                    else KitsugiColors.SurfaceSoft
                                )
                                .border(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) accentColor else Color.Transparent,
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .clickable { selectedTypeId = typeFilter.id }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = typeFilter.displayLabel,
                                    color = if (isSelected) accentColor else KitsugiColors.TextPrimary,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 13.sp
                                )
                                if (isSelected) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = null,
                                        tint = accentColor,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
