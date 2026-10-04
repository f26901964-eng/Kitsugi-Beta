@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class
)

package com.kitsugi.animelist.ui.screens.profile

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.kitsugi.animelist.data.settings.AppSettings
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.ui.app.KitsugiProfileViewModel
import com.kitsugi.animelist.ui.app.ProfileFavoriteItem
import com.kitsugi.animelist.ui.app.ShikimoriProfileState
import com.kitsugi.animelist.ui.components.KitsugiPlatformLogo
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.utils.KitsugiTranslateUtils.openTranslator
import kotlinx.coroutines.launch

@Composable
fun ShikimoriProfileContent(
    viewModel: KitsugiProfileViewModel,
    state: ShikimoriProfileState,
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
    var activeTab by rememberSaveable { mutableIntStateOf(viewModel.shikimoriActiveTab) }
    val coroutineScope = rememberCoroutineScope()
    val pagerState = androidx.compose.foundation.pager.rememberPagerState(
        initialPage = viewModel.shikimoriActiveTab.coerceIn(0, 2),
        pageCount = { 3 }
    )
    LaunchedEffect(pagerState.currentPage) {
        activeTab = pagerState.currentPage
        viewModel.shikimoriActiveTab = pagerState.currentPage
    }

    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = viewModel.shikimoriScrollIndex,
        initialFirstVisibleItemScrollOffset = viewModel.shikimoriScrollOffset
    )

    LaunchedEffect(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) {
        viewModel.updateShikimoriScroll(
            listState.firstVisibleItemIndex,
            listState.firstVisibleItemScrollOffset
        )
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
            val username = state.name.ifBlank { "Shikimori Kullanıcısı" }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp)
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFF2E7D32), Color(0xFF1B5E20), KitsugiColors.SurfaceStrong)
                            )
                        )
                )

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
                                val url = "https://shikimori.one/${state.name}"
                                val sendIntent = android.content.Intent().apply {
                                    action = android.content.Intent.ACTION_SEND
                                    putExtra(android.content.Intent.EXTRA_TEXT, url)
                                    type = "text/plain"
                                }
                                val shareIntent = android.content.Intent.createChooser(sendIntent, "Profili Paylaş")
                                shareIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(shareIntent)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Share,
                            contentDescription = "Paylaş",
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
                        model = avatarUrl ?: com.kitsugi.animelist.R.drawable.ic_logo_shikimori,
                        contentDescription = "Shikimori Avatar",
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .border(3.dp, KitsugiColors.Background, CircleShape)
                            .then(
                                if (!avatarUrl.isNullOrBlank()) {
                                    Modifier.clickable {
                                        onImageClick?.invoke(listOf(avatarUrl), 0, "$username Profil Resmi")
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
                            KitsugiPlatformLogo(platformId = "shikimori", size = 18.dp)
                        }

                        if (!state.location.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = state.location,
                                color = KitsugiColors.TextMuted,
                                style = MaterialTheme.typography.labelSmall
                            )
                        } else if (!state.lastOnline.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "Son çevrimiçi: ${state.lastOnline.take(10)}",
                                color = KitsugiColors.TextMuted,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
        }

        // ── Sticky Header: Icon Tabs ────────────────────────────────────────
        stickyHeader(key = "shiki_tabs_header") {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = KitsugiColors.Background
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val tabs = listOf(
                        Icons.Rounded.Info to "Hakkında",
                        Icons.Rounded.BarChart to "İstatistikler",
                        Icons.Rounded.Bookmarks to "Kayıtlar"
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
        item(key = "shiki_content") {
            val density = androidx.compose.ui.platform.LocalDensity.current
            val screenHeightDp = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp

            androidx.compose.foundation.pager.HorizontalPager(
                state = pagerState,
                userScrollEnabled = true,
                beyondViewportPageCount = 1,
                pageSpacing = 12.dp,
                verticalAlignment = Alignment.Top,
                modifier = Modifier
                    .fillMaxWidth()
                    .layout { measurable, constraints ->
                        val minPagerHeightPx = with(density) { (screenHeightDp - 64).dp.roundToPx() }
                        val placeable = measurable.measure(
                            constraints.copy(
                                minHeight = minPagerHeightPx,
                                maxHeight = androidx.compose.ui.unit.Constraints.Infinity
                            )
                        )
                        layout(placeable.width, placeable.height) {
                            placeable.placeRelative(0, 0)
                        }
                    }
            ) { page ->
                when (page) {
                    0 -> ShikimoriAboutTab(state = state, accentColor = accentColor)
                    1 -> ShikimoriStatsTab(state = state, accentColor = accentColor)
                    2 -> ShikimoriRatesTab(
                        entries = state.recentRates,
                        accentColor = accentColor,
                        onEntryClick = { item ->
                            val idInt = item.id.toIntOrNull() ?: 0
                            onFavoriteMediaClick(idInt, MediaType.Anime, "mal", item.title, item.imageUrl)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ShikimoriAboutTab(
    state: ShikimoriProfileState,
    accentColor: Color
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Bio Section
        if (!state.bio.isNullOrBlank()) {
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
                            text = "Hakkında",
                            color = KitsugiColors.TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(
                                onClick = {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    cm.setPrimaryClip(android.content.ClipData.newPlainText("Shikimori Bio", state.bio))
                                    Toast.makeText(context, "Metin kopyalandı", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Rounded.ContentCopy, contentDescription = "Kopyala", tint = KitsugiColors.TextMuted, modifier = Modifier.size(16.dp))
                            }
                            IconButton(
                                onClick = { context.openTranslator(state.bio) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Rounded.Translate, contentDescription = "Çevir", tint = KitsugiColors.TextMuted, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = state.bio,
                        color = KitsugiColors.TextSecondary,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                }
            }
        }

        // Quick Stats Grid
        val totalAnime = state.watchingAnime + state.completedAnime + state.onHoldAnime + state.droppedAnime + state.plannedAnime
        val totalManga = state.readingManga + state.completedManga + state.onHoldManga + state.droppedManga + state.plannedManga

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ShikimoriStatCard(
                title = "Anime",
                value = totalAnime.toString(),
                icon = Icons.Rounded.Tv,
                modifier = Modifier.weight(1f),
                accentColor = accentColor
            )
            ShikimoriStatCard(
                title = "Manga",
                value = totalManga.toString(),
                icon = Icons.Rounded.Book,
                modifier = Modifier.weight(1f),
                accentColor = Color(0xFF2E7D32)
            )
            ShikimoriStatCard(
                title = "Puan",
                value = if (state.avgScore > 0) String.format("%.1f", state.avgScore) else "-",
                icon = Icons.Rounded.Star,
                modifier = Modifier.weight(1f),
                accentColor = KitsugiColors.AccentYellow
            )
        }

        // Account Attributes Card
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
                    text = "Profil Detayları",
                    color = KitsugiColors.TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )

                if (!state.location.isNullOrBlank()) {
                    ShikiAttributeRow(icon = Icons.Rounded.LocationOn, label = "Konum", value = state.location)
                }
                if (!state.website.isNullOrBlank()) {
                    ShikiAttributeRow(icon = Icons.Rounded.Language, label = "Web Sitesi", value = state.website)
                }
                if (!state.sex.isNullOrBlank() || state.fullYears != null) {
                    val sexStr = when (state.sex) { "male" -> "Erkek"; "female" -> "Kadın"; else -> state.sex ?: "" }
                    val ageStr = if (state.fullYears != null) "${state.fullYears} Yaşında" else ""
                    val combo = listOf(sexStr, ageStr).filter { it.isNotBlank() }.joinToString(" • ")
                    ShikiAttributeRow(icon = Icons.Rounded.Person, label = "Cinsiyet / Yaş", value = combo)
                }
                if (!state.lastOnline.isNullOrBlank()) {
                    ShikiAttributeRow(icon = Icons.Rounded.Schedule, label = "Son Giriş", value = state.lastOnline.take(16).replace("T", " "))
                }
                ShikiAttributeRow(icon = Icons.Rounded.CheckCircle, label = "Tamamlanan Anime", value = state.completedAnime.toString())
                ShikiAttributeRow(icon = Icons.Rounded.CheckCircle, label = "Tamamlanan Manga", value = state.completedManga.toString())
            }
        }
    }
}

@Composable
private fun ShikimoriStatsTab(
    state: ShikimoriProfileState,
    accentColor: Color
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Anime Status Breakdown
        val totalAnime = (state.watchingAnime + state.completedAnime + state.plannedAnime + state.onHoldAnime + state.droppedAnime).coerceAtLeast(1)
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
                    text = "Anime Durum Dağılımı",
                    color = KitsugiColors.TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                ) {
                    if (state.watchingAnime > 0) Box(modifier = Modifier.weight(state.watchingAnime.toFloat() / totalAnime).fillMaxHeight().background(KitsugiColors.AccentGreen))
                    if (state.completedAnime > 0) Box(modifier = Modifier.weight(state.completedAnime.toFloat() / totalAnime).fillMaxHeight().background(accentColor))
                    if (state.plannedAnime > 0) Box(modifier = Modifier.weight(state.plannedAnime.toFloat() / totalAnime).fillMaxHeight().background(KitsugiColors.AccentYellow))
                    if (state.onHoldAnime > 0) Box(modifier = Modifier.weight(state.onHoldAnime.toFloat() / totalAnime).fillMaxHeight().background(KitsugiColors.AccentOrange))
                    if (state.droppedAnime > 0) Box(modifier = Modifier.weight(state.droppedAnime.toFloat() / totalAnime).fillMaxHeight().background(KitsugiColors.AccentRed))
                }

                ShikiStatusRow(label = "İzleniyor", count = state.watchingAnime, color = KitsugiColors.AccentGreen)
                ShikiStatusRow(label = "Tamamlandı", count = state.completedAnime, color = accentColor)
                ShikiStatusRow(label = "Planlandı", count = state.plannedAnime, color = KitsugiColors.AccentYellow)
                ShikiStatusRow(label = "Beklemede", count = state.onHoldAnime, color = KitsugiColors.AccentOrange)
                ShikiStatusRow(label = "Bırakıldı", count = state.droppedAnime, color = KitsugiColors.AccentRed)
            }
        }

        // Manga Status Breakdown
        val totalManga = (state.readingManga + state.completedManga + state.plannedManga + state.onHoldManga + state.droppedManga).coerceAtLeast(1)
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
                    text = "Manga Durum Dağılımı",
                    color = KitsugiColors.TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                ) {
                    if (state.readingManga > 0) Box(modifier = Modifier.weight(state.readingManga.toFloat() / totalManga).fillMaxHeight().background(KitsugiColors.AccentGreen))
                    if (state.completedManga > 0) Box(modifier = Modifier.weight(state.completedManga.toFloat() / totalManga).fillMaxHeight().background(accentColor))
                    if (state.plannedManga > 0) Box(modifier = Modifier.weight(state.plannedManga.toFloat() / totalManga).fillMaxHeight().background(KitsugiColors.AccentYellow))
                    if (state.onHoldManga > 0) Box(modifier = Modifier.weight(state.onHoldManga.toFloat() / totalManga).fillMaxHeight().background(KitsugiColors.AccentOrange))
                    if (state.droppedManga > 0) Box(modifier = Modifier.weight(state.droppedManga.toFloat() / totalManga).fillMaxHeight().background(KitsugiColors.AccentRed))
                }

                ShikiStatusRow(label = "Okunuyor", count = state.readingManga, color = KitsugiColors.AccentGreen)
                ShikiStatusRow(label = "Tamamlandı", count = state.completedManga, color = accentColor)
                ShikiStatusRow(label = "Planlandı", count = state.plannedManga, color = KitsugiColors.AccentYellow)
                ShikiStatusRow(label = "Beklemede", count = state.onHoldManga, color = KitsugiColors.AccentOrange)
                ShikiStatusRow(label = "Bırakıldı", count = state.droppedManga, color = KitsugiColors.AccentRed)
            }
        }
    }
}

@Composable
private fun ShikimoriRatesTab(
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
                    text = "Shikimori listesinde kayıt bulunamadı.",
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
                        AsyncImage(
                            model = item.imageUrl,
                            contentDescription = item.title,
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
private fun ShikimoriStatCard(
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
private fun ShikiAttributeRow(
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
private fun ShikiStatusRow(
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
