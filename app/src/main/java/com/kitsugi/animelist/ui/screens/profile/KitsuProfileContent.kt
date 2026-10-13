@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class
)

package com.kitsugi.animelist.ui.screens.profile

import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.Text

import android.content.Context
import android.widget.Toast
import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.theme.gradient.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.kitsugi.animelist.data.settings.AppSettings
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.ui.app.KitsugiProfileViewModel
import com.kitsugi.animelist.ui.app.KitsuProfileState
import com.kitsugi.animelist.ui.app.ProfileFavoriteItem
import com.kitsugi.animelist.ui.components.KitsugiNsfwImage
import com.kitsugi.animelist.ui.components.KitsugiPlatformLogo
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.utils.KitsugiTranslateUtils.openTranslator
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R
import com.kitsugi.animelist.ui.components.KitsugiTranslateIconButton
import com.kitsugi.animelist.utils.KitsugiStrings

@Composable
fun KitsuProfileContent(
    viewModel: KitsugiProfileViewModel,
    state: KitsuProfileState,
    mediaEntries: List<MediaEntry>,
    appSettings: AppSettings,
    onEntryClick: (MediaEntry) -> Unit,
    onFavoriteMediaClick: (mediaId: Int, mediaType: MediaType, source: String, title: String, imageUrl: String?) -> Unit,
    onOpenFavoriteSheet: (title: String, items: List<ProfileFavoriteItem>, onClick: (ProfileFavoriteItem) -> Unit) -> Unit,
    onOpenStatsClick: (() -> Unit)? = null,
    isLandscape: Boolean,
    accentColor: Color,
    onImageClick: ((urls: List<String>, initialIndex: Int, title: String) -> Unit)? = null
) {
    val context = LocalContext.current
    var activeTab by rememberSaveable { mutableIntStateOf(viewModel.kitsuActiveTab) }
    val coroutineScope = rememberCoroutineScope()
    val pagerState = androidx.compose.foundation.pager.rememberPagerState(
        initialPage = viewModel.kitsuActiveTab.coerceIn(0, 2),
        pageCount = { 3 }
    )
    LaunchedEffect(pagerState.currentPage) {
        activeTab = pagerState.currentPage
        viewModel.kitsuActiveTab = pagerState.currentPage
    }

    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = viewModel.kitsuScrollIndex,
        initialFirstVisibleItemScrollOffset = viewModel.kitsuScrollOffset
    )

    LaunchedEffect(listState) {
        snapshotFlow {
            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        }.collect { (index, offset) ->
            viewModel.updateKitsuScroll(index, offset)
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = if (isLandscape) 18.dp else 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── Banner and Avatar ───────────────────────────────────────────────
        item {
            val avatarUrl = state.avatarUrl?.takeIf { it.isNotBlank() }
            val bannerUrl = state.bannerUrl?.takeIf { it.isNotBlank() }
            val username = state.name.ifBlank { stringResource(R.string.kitsu_kullanicisi) }
            val bannerTitle = stringResource(R.string.profile_banner_title, username)
            val avatarTitle = stringResource(R.string.profile_avatar_title, username)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp)
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            ) {
                if (!bannerUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = bannerUrl,
                        contentDescription = bannerTitle,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color(0xFFFD755C), Color(0xFFF75239), KitsugiColors.SurfaceStrong)
                                )
                            )
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, KitsugiColors.Background.copy(alpha = 0.85f))
                            )
                        )
                )

                // Share butonu
                if (state.name.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(10.dp)
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(KitsugiColors.Background.copy(alpha = 0.6f))
                            .clickable {
                                val url = "https://kitsu.app/users/${state.name}"
                                val sendIntent = android.content.Intent().apply {
                                    action = android.content.Intent.ACTION_SEND
                                    putExtra(android.content.Intent.EXTRA_TEXT, url)
                                    type = "text/plain"
                                }
                                val shareIntent = android.content.Intent.createChooser(sendIntent, KitsugiStrings.get(R.string.profili_paylas))
                                shareIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(shareIntent)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Share,
                            contentDescription = stringResource(R.string.paylas),
                            tint = KitsugiColors.TextPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Avatar and User Info
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = avatarUrl ?: com.kitsugi.animelist.R.drawable.ic_logo_kitsu,
                        contentDescription = avatarTitle,
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .border(3.dp, KitsugiColors.Background, CircleShape)
                            .then(
                                if (!avatarUrl.isNullOrBlank()) {
                                    Modifier.clickable {
                                        onImageClick?.invoke(listOf(avatarUrl), 0, avatarTitle)
                                    }
                                } else Modifier
                            ),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = username,
                                color = KitsugiColors.TextPrimary,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            KitsugiPlatformLogo(platformId = "kitsu", size = 18.dp)
                        }

                        if (!state.waifuOrHusbando.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = stringResource(R.string.profile_favorite_label, state.waifuOrHusbando),
                                color = Color(0xFFFD755C),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        } else if (!state.location.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = state.location,
                                color = KitsugiColors.TextMuted,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
        }

        // ── Sticky Header: Icon Tabs ────────────────────────────────────────
        stickyHeader(key = "kitsu_tabs_header") {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = KitsugiColors.Background
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val tabs = listOf(
                        Icons.Rounded.Info to stringResource(R.string.character_tab_about),
                        Icons.Rounded.BarChart to stringResource(R.string.profile_tab_statistics),
                        Icons.Rounded.Bookmarks to stringResource(R.string.profile_library)
                    )
                    ProfileHeaderIconTabs(
                        tabs = tabs,
                        selectedTab = pagerState.currentPage,
                        onTabSelected = { page ->
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(page)
                            }
                        },
                        accentColor = accentColor
                    )
                }
            }
        }

        // ── Pager Content ───────────────────────────────────────────────────
        item(key = "kitsu_content") {
            val density = androidx.compose.ui.platform.LocalDensity.current
            val pageHeights = viewModel.kitsuPageHeights
            val currentPage = pagerState.currentPage
            val currentPageOffset = pagerState.currentPageOffsetFraction
            val targetPage = if (currentPageOffset > 0f) currentPage + 1 else if (currentPageOffset < 0f) currentPage - 1 else currentPage
            val currentHeightPx = pageHeights[currentPage] ?: 0
            val targetHeightPx = pageHeights[targetPage] ?: currentHeightPx
            val interpolatedHeightDp = remember(currentHeightPx, targetHeightPx, currentPageOffset) {
                val heightPx = if (currentHeightPx > 0 && targetHeightPx > 0) {
                    currentHeightPx + (targetHeightPx - currentHeightPx) * kotlin.math.abs(currentPageOffset)
                } else if (currentHeightPx > 0) currentHeightPx.toFloat() else 0f
                if (heightPx > 0f) with(density) { heightPx.toDp() } else null
            }
            val screenHeightDp = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp

            androidx.compose.foundation.pager.HorizontalPager(
                state = pagerState,
                userScrollEnabled = true,
                beyondViewportPageCount = 0,
                pageSpacing = 12.dp,
                verticalAlignment = Alignment.Top,
                modifier = Modifier
                    .fillMaxWidth()
                    .layout { measurable, constraints ->
                        val minPagerHeightPx = with(density) { (screenHeightDp - 64).dp.roundToPx() }.coerceAtLeast(0)
                        val placeable = measurable.measure(
                            constraints.copy(
                                minHeight = minPagerHeightPx,
                                maxHeight = androidx.compose.ui.unit.Constraints.Infinity
                            )
                        )
                        val height = (interpolatedHeightDp?.roundToPx()?.coerceAtLeast(minPagerHeightPx) ?: placeable.height)
                            .coerceIn(minPagerHeightPx, 16_000_000)
                        layout(placeable.width, height) {
                            placeable.placeRelative(0, 0)
                        }
                    }
                    .clipToBounds()
            ) { page ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { coordinates ->
                            com.kitsugi.animelist.ui.utils.KitsugiScrollDefaults
                                .recordStablePageHeight(pageHeights, page, coordinates.size.height)
                        }
                ) {
                    when (page) {
                        0 -> KitsuAboutTab(
                            state = state,
                            accentColor = accentColor,
                            onFavoriteMediaClick = onFavoriteMediaClick,
                            onOpenFavoriteSheet = onOpenFavoriteSheet
                        )
                        1 -> KitsuStatsTab(state = state, accentColor = accentColor)
                        2 -> KitsuLibraryTab(
                            entries = state.libraryEntries,
                            accentColor = accentColor,
                            onEntryClick = { item ->
                                val idInt = item.id.toIntOrNull() ?: 0
                                onFavoriteMediaClick(idInt, MediaType.Anime, "kitsu", item.title, item.imageUrl)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun KitsuAboutTab(
    state: KitsuProfileState,
    accentColor: Color,
    onFavoriteMediaClick: (mediaId: Int, mediaType: MediaType, source: String, title: String, imageUrl: String?) -> Unit,
    onOpenFavoriteSheet: (title: String, items: List<ProfileFavoriteItem>, onClick: (ProfileFavoriteItem) -> Unit) -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Bio Section
        if (!state.about.isNullOrBlank()) {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = KitsugiColors.Surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.section_biography),
                            color = KitsugiColors.TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(
                                onClick = {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    cm.setPrimaryClip(android.content.ClipData.newPlainText("Kitsu Bio", state.about))
                                    Toast.makeText(context, KitsugiStrings.get(R.string.biyografi_kopyalandi), Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Rounded.ContentCopy, contentDescription = stringResource(R.string.action_copy), tint = KitsugiColors.TextMuted, modifier = Modifier.size(16.dp))
                            }
                            KitsugiTranslateIconButton(
                                onClick = { context.openTranslator(state.about) },
                                modifier = Modifier.size(32.dp),
                                tint = KitsugiColors.TextMuted,
                                contentDescription = stringResource(R.string.cevir),
                                iconSize = 16.dp,
                                ringSize = 26.dp
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = state.about,
                        color = KitsugiColors.TextSecondary,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                }
            }
        }

        // Quick Stats Grid
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            KitsuStatCard(
                title = "Anime",
                value = state.totalAnime.toString(),
                icon = Icons.Rounded.Tv,
                modifier = Modifier.weight(1f),
                accentColor = accentColor
            )
            KitsuStatCard(
                title = "Manga",
                value = state.totalManga.toString(),
                icon = Icons.Rounded.Book,
                modifier = Modifier.weight(1f),
                accentColor = Color(0xFFFD755C)
            )
            KitsuStatCard(
                title = stringResource(R.string.detail_stat_score),
                value = if (state.avgScore > 0) String.format("%.1f", state.avgScore) else "-",
                icon = Icons.Rounded.Star,
                modifier = Modifier.weight(1f),
                accentColor = KitsugiColors.AccentYellow
            )
        }

        // Watch Time & Episodes Card (if available)
        if (!state.timeSpentDaysHours.isNullOrBlank() || (state.episodesWatched != null && state.episodesWatched > 0)) {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = KitsugiColors.Surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!state.timeSpentDaysHours.isNullOrBlank()) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Rounded.Schedule,
                                contentDescription = null,
                                tint = Color(0xFFFD755C),
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = state.timeSpentDaysHours,
                                color = KitsugiColors.TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(R.string.izleme_suresi),
                                color = KitsugiColors.TextMuted,
                                fontSize = 11.sp
                            )
                        }
                    }
                    if (state.episodesWatched != null && state.episodesWatched > 0) {
                        if (!state.timeSpentDaysHours.isNullOrBlank()) {
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(36.dp)
                                    .background(KitsugiColors.SurfaceStrong)
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Rounded.PlayCircle,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.state_episodeswatched_bolum, state.episodesWatched),
                                color = KitsugiColors.TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(R.string.izlenen_bolumler),
                                color = KitsugiColors.TextMuted,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }

        // Favorites Horizontal Section
        if (state.favorites.isNotEmpty()) {
            FavoritesHorizontalSection(
                title = stringResource(R.string.profile_favorites),
                items = state.favorites,
                onSeeAllClick = {
                    onOpenFavoriteSheet(KitsugiStrings.get(R.string.profile_platform_favorites, "Kitsu"), state.favorites) { item ->
                        val idInt = item.id.toIntOrNull() ?: 0
                        onFavoriteMediaClick(idInt, MediaType.Anime, "kitsu", item.title, item.imageUrl)
                    }
                },
                onItemClick = { item ->
                    val idInt = item.id.toIntOrNull() ?: 0
                    onFavoriteMediaClick(idInt, MediaType.Anime, "kitsu", item.title, item.imageUrl)
                }
            )
        }

        // Detailed User Attributes Card
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = KitsugiColors.Surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.hesap_bilgileri),
                    color = KitsugiColors.TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )

                if (!state.waifuOrHusbando.isNullOrBlank()) {
                    AttributeRow(icon = Icons.Rounded.Favorite, label = stringResource(R.string.profile_label_favorite_character), value = state.waifuOrHusbando)
                }
                if (!state.location.isNullOrBlank()) {
                    AttributeRow(icon = Icons.Rounded.LocationOn, label = stringResource(R.string.profile_label_location), value = state.location)
                }
                if (!state.gender.isNullOrBlank()) {
                    AttributeRow(icon = Icons.Rounded.Person, label = stringResource(R.string.profile_label_gender), value = state.gender)
                }
                if (!state.birthday.isNullOrBlank()) {
                    AttributeRow(icon = Icons.Rounded.Cake, label = stringResource(R.string.info_label_birthday), value = state.birthday)
                }
                if (!state.joinedAt.isNullOrBlank()) {
                    val formattedDate = state.joinedAt.take(10)
                    AttributeRow(icon = Icons.Rounded.CalendarToday, label = stringResource(R.string.katilim_tarihi), value = formattedDate)
                }
                AttributeRow(icon = Icons.Rounded.People, label = stringResource(R.string.takipci_takip), value = "${state.followersCount} / ${state.followingCount}")
                AttributeRow(icon = Icons.Rounded.Comment, label = stringResource(R.string.profile_comments), value = state.commentsCount.toString())
            }
        }
    }
}

@Composable
private fun KitsuStatsTab(
    state: KitsuProfileState,
    accentColor: Color
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Watch Time & Episodes Card (if available)
        if (!state.timeSpentDaysHours.isNullOrBlank() || (state.episodesWatched != null && state.episodesWatched > 0)) {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = KitsugiColors.Surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!state.timeSpentDaysHours.isNullOrBlank()) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Rounded.Schedule,
                                contentDescription = null,
                                tint = Color(0xFFFD755C),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = state.timeSpentDaysHours,
                                color = KitsugiColors.TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(R.string.izleme_suresi),
                                color = KitsugiColors.TextMuted,
                                fontSize = 11.sp
                            )
                        }
                    }
                    if (state.episodesWatched != null && state.episodesWatched > 0) {
                        if (!state.timeSpentDaysHours.isNullOrBlank()) {
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(40.dp)
                                    .background(KitsugiColors.SurfaceStrong)
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Rounded.PlayCircle,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.state_episodeswatched_bolum, state.episodesWatched),
                                color = KitsugiColors.TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(R.string.izlenen_bolumler),
                                color = KitsugiColors.TextMuted,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }

        // Status Distribution
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = KitsugiColors.Surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = stringResource(R.string.inline_library_status_6c45df9),
                    color = KitsugiColors.TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )

                // Visual progress bar
                val total = (state.watching + state.completed + state.planned + state.paused + state.dropped).coerceAtLeast(1)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                ) {
                    if (state.watching > 0) Box(modifier = Modifier.weight(state.watching.toFloat() / total).fillMaxHeight().background(KitsugiColors.AccentGreen))
                    if (state.completed > 0) Box(modifier = Modifier.weight(state.completed.toFloat() / total).fillMaxHeight().background(accentColor))
                    if (state.planned > 0) Box(modifier = Modifier.weight(state.planned.toFloat() / total).fillMaxHeight().background(KitsugiColors.AccentYellow))
                    if (state.paused > 0) Box(modifier = Modifier.weight(state.paused.toFloat() / total).fillMaxHeight().background(KitsugiColors.AccentOrange))
                    if (state.dropped > 0) Box(modifier = Modifier.weight(state.dropped.toFloat() / total).fillMaxHeight().background(KitsugiColors.AccentRed))
                }

                StatusRow(label = stringResource(R.string.system_status_current), count = state.watching, color = KitsugiColors.AccentGreen)
                StatusRow(label = stringResource(R.string.tamamlandi), count = state.completed, color = accentColor)
                StatusRow(label = stringResource(R.string.planlandi), count = state.planned, color = KitsugiColors.AccentYellow)
                StatusRow(label = stringResource(R.string.beklemede), count = state.paused, color = KitsugiColors.AccentOrange)
                StatusRow(label = stringResource(R.string.birakildi), count = state.dropped, color = KitsugiColors.AccentRed)
            }
        }

        // Overall Score Card
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = KitsugiColors.Surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.ortalama_puan),
                        color = KitsugiColors.TextMuted,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (state.avgScore > 0) String.format("%.2f / 10", state.avgScore) else stringResource(R.string.ui_rating_unrated),
                        color = KitsugiColors.TextPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Icon(
                    imageVector = Icons.Rounded.Star,
                    contentDescription = null,
                    tint = KitsugiColors.AccentYellow,
                    modifier = Modifier.size(36.dp)
                )
            }
        }
    }
}

@Composable
private fun KitsuLibraryTab(
    entries: List<ProfileFavoriteItem>,
    accentColor: Color,
    onEntryClick: (ProfileFavoriteItem) -> Unit
) {
    if (entries.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 48.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Rounded.Bookmarks,
                    contentDescription = null,
                    tint = KitsugiColors.TextMuted,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.inline_no_library_entry_found_8619075),
                    color = KitsugiColors.TextMuted,
                    fontSize = 14.sp
                )
            }
        }
    } else {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            entries.forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(KitsugiColors.Surface)
                        .clickable { onEntryClick(item) }
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!item.imageUrl.isNullOrBlank()) {
                        // 18+ blur: profil kütüphanesi kartları da kullanıcı ayarına uyar.
                        KitsugiNsfwImage(
                            model = item.imageUrl,
                            contentDescription = item.title,
                            isAdult = item.isAdult,
                            modifier = Modifier
                                .size(44.dp, 60.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(44.dp, 60.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(KitsugiColors.SurfaceStrong),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.Movie, contentDescription = null, tint = KitsugiColors.TextMuted)
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = item.title,
                        color = KitsugiColors.TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun KitsuStatCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    accentColor: Color
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = KitsugiColors.Surface),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = accentColor,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                color = KitsugiColors.TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = title,
                color = KitsugiColors.TextMuted,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun AttributeRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(imageVector = icon, contentDescription = label, tint = KitsugiColors.TextMuted, modifier = Modifier.size(16.dp))
            Text(text = label, color = KitsugiColors.TextSecondary, fontSize = 13.sp)
        }
        Text(
            text = value,
            color = KitsugiColors.TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun StatusRow(
    label: String,
    count: Int,
    color: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Text(text = label, color = KitsugiColors.TextSecondary, fontSize = 13.sp)
        }
        Text(
            text = count.toString(),
            color = KitsugiColors.TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
