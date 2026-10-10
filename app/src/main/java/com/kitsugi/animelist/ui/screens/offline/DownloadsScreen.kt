package com.kitsugi.animelist.ui.screens.offline

import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.LinearProgressIndicator
import com.kitsugi.animelist.ui.theme.gradient.TabRow
import com.kitsugi.animelist.ui.theme.gradient.Text

import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.os.StrictMode
import android.widget.Toast
import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.theme.gradient.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil3.compose.AsyncImage
import com.kitsugi.animelist.core.player.OfflinePlaybackHelper
import com.kitsugi.animelist.data.local.AnimeDownloadManager
import com.kitsugi.animelist.data.model.AnimeDownload
import com.kitsugi.animelist.data.remote.GalleryCategory
import com.kitsugi.animelist.data.remote.GalleryItem
import com.kitsugi.animelist.ui.components.KitsugiImageGalleryDialog
import com.kitsugi.animelist.ui.components.KitsugiAnimatedDeleteButton
import com.kitsugi.animelist.ui.screens.fullscreen.KitsugiFullscreenPlayerActivity
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.utils.KitsugiImageDownloadHelper
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Model representing any downloaded subtitle on disk (standalone or episode-bound).
 */
data class DownloadedSubtitleItem(
    val title: String,
    val language: String,
    val format: String,
    val fileSizeBytes: Long,
    val lastModified: Long,
    val file: File,
    val animeTitle: String? = null,
    val episode: Int? = null,
    val isStandalone: Boolean = false
)

/**
 * Model representing a downloaded gallery image on disk.
 * Images are saved by [KitsugiImageDownloadHelper] into Downloads/Kitsugi/Images.
 */
data class DownloadedImageItem(
    val file: File,
    val title: String,          // Parsed from filename (e.g. "Kitsugi_Naruto_...")
    val fileSizeBytes: Long,
    val lastModified: Long,
    val width: Int = 0,
    val height: Int = 0
)

/**
 * İndirilen resimleri içerik adına (anime / dizi / film) göre gruplar.
 * Başlık olarak en güncel kaydın adı, bölüm olarak o içeriğin resimleri gösterilir.
 */
data class DownloadedImageGroup(
    val displayName: String,
    val items: List<DownloadedImageItem>
)

/** Gruplama anahtarı: büyük/küçük harf ve boşluk farklarını dengeler. */
private fun normalizeImageGroupKey(title: String): String =
    title.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val accentColor = LocalKitsugiAccent.current
    val downloads by AnimeDownloadManager.downloads.collectAsState()

    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 3 })
    val scope = rememberCoroutineScope()
    var refreshSubsTrigger by remember { mutableIntStateOf(0) }
    var refreshImagesTrigger by remember { mutableIntStateOf(0) }

    val allSubtitles = remember(downloads, refreshSubsTrigger) {
        loadAllDownloadedSubtitles(context, downloads)
    }

    val allImages = remember(refreshImagesTrigger) {
        loadAllDownloadedImages(context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(KitsugiColors.Background)
            .statusBarsPadding()
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Geri",
                    tint = KitsugiColors.TextPrimary
                )
            }
            Text(
                text = "İndirmeler",
                color = KitsugiColors.TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp)
            )

            // Quick open root downloads folder
            IconButton(
                onClick = {
                    val rootDir = when (pagerState.currentPage) {
                        1 -> File(
                            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                            "Kitsugi/Subtitles"
                        ).also { it.mkdirs() }
                        2 -> File(
                            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                            "Kitsugi/Images"
                        ).also { it.mkdirs() }
                        else -> File(
                            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                            "Kitsugi/Video"
                        ).also { it.mkdirs() }
                    }
                    openFolderInFileManager(context, rootDir)
                }
            ) {
                Icon(
                    imageVector = Icons.Rounded.FolderOpen,
                    contentDescription = "İndirme Klasörünü Aç",
                    tint = accentColor
                )
            }
        }

        // Tabs: Videolar | Altyazılar | Resimler
        TabRow(
            selectedTabIndex = pagerState.currentPage,
            containerColor = KitsugiColors.Background,
            contentColor = KitsugiColors.TextPrimary,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[pagerState.currentPage]),
                    color = accentColor,
                    height = 3.dp
                )
            },
            divider = {
                HorizontalDivider(color = KitsugiColors.Border)
            }
        ) {
            Tab(
                selected = pagerState.currentPage == 0,
                onClick = { scope.launch { pagerState.animateScrollToPage(0) } },
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.VideoLibrary,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = if (pagerState.currentPage == 0) accentColor else KitsugiColors.TextMuted
                        )
                        Text(
                            text = "Videolar (${downloads.size})",
                            fontWeight = if (pagerState.currentPage == 0) FontWeight.Bold else FontWeight.Normal,
                            color = if (pagerState.currentPage == 0) KitsugiColors.TextPrimary else KitsugiColors.TextMuted
                        )
                    }
                }
            )
            Tab(
                selected = pagerState.currentPage == 1,
                onClick = { scope.launch { pagerState.animateScrollToPage(1) } },
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Subtitles,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = if (pagerState.currentPage == 1) accentColor else KitsugiColors.TextMuted
                        )
                        Text(
                            text = "Altyazılar (${allSubtitles.size})",
                            fontWeight = if (pagerState.currentPage == 1) FontWeight.Bold else FontWeight.Normal,
                            color = if (pagerState.currentPage == 1) KitsugiColors.TextPrimary else KitsugiColors.TextMuted
                        )
                    }
                }
            )
            Tab(
                selected = pagerState.currentPage == 2,
                onClick = { scope.launch { pagerState.animateScrollToPage(2) } },
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Image,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = if (pagerState.currentPage == 2) accentColor else KitsugiColors.TextMuted
                        )
                        Text(
                            text = "Resimler (${allImages.size})",
                            fontWeight = if (pagerState.currentPage == 2) FontWeight.Bold else FontWeight.Normal,
                            color = if (pagerState.currentPage == 2) KitsugiColors.TextPrimary else KitsugiColors.TextMuted
                        )
                    }
                }
            )
        }

        // Content
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) { page ->
            when (page) {
                0 -> {
                    // ── VIDEOLAR TAB ──────────────────────────────────────────────
                    if (downloads.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Rounded.DownloadForOffline,
                            contentDescription = null,
                            tint = KitsugiColors.TextMuted,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Henüz indirilmiş veya sıraya alınmış video yok.",
                            color = KitsugiColors.TextMuted,
                            fontSize = 14.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(downloads, key = { "${it.animeId}_${it.episode}" }) { download ->
                        DownloadItemRow(
                            download = download,
                            accentColor = accentColor,
                            onPlay = {
                                val safeAnimeId = download.animeId.ifBlank {
                                    download.animeTitle.lowercase().replace(Regex("[^a-z0-9]"), "_").trim('_').take(30).ifBlank { "media" }
                                }
                                val mediaId = "${safeAnimeId}_ep${download.episode}"
                                val localMedia = OfflinePlaybackHelper.getLocalMedia(context, mediaId)
                                val params = localMedia?.let { OfflinePlaybackHelper.buildPlayerParams(it) }
                                if (params != null) {
                                    KitsugiFullscreenPlayerActivity.startWithStreamUrls(
                                        context = context,
                                        videoUrl = params.videoUri,
                                        title = params.title,
                                        headers = params.headers,
                                        subtitles = params.subtitles
                                    )
                                } else {
                                    download.localPath?.let { path ->
                                        val videoFile = File(path)
                                        val parentDir = videoFile.parentFile
                                        val baseName = videoFile.nameWithoutExtension
                                        val companionSubs = if (parentDir != null && parentDir.exists()) {
                                            parentDir.listFiles()?.filter { f ->
                                                f.isFile && f.name.startsWith(baseName) && f.extension.lowercase() in listOf("srt", "ass", "ssa", "vtt")
                                            }?.map { subFile ->
                                                val lang = subFile.nameWithoutExtension.removePrefix(baseName).trim('.', '_')
                                                com.kitsugi.animelist.core.player.SubtitleInput(
                                                    url = "file://${subFile.absolutePath}",
                                                    name = lang.ifBlank { "Altyazı" },
                                                    lang = lang.ifBlank { "tr" }
                                                )
                                            } ?: emptyList()
                                        } else emptyList()

                                        KitsugiFullscreenPlayerActivity.startWithStreamUrls(
                                            context = context,
                                            videoUrl = "file://$path",
                                            title = "${download.animeTitle} - Bölüm ${download.episode}",
                                            headers = emptyMap(),
                                            subtitles = companionSubs
                                        )
                                    }
                                }
                            },
                            onPause = {
                                AnimeDownloadManager.pauseDownload(download.animeId, download.episode)
                            },
                            onResume = {
                                AnimeDownloadManager.resumeDownload(context, download.animeId, download.episode)
                            },
                            onDelete = {
                                AnimeDownloadManager.deleteDownload(context, download.animeId, download.episode)
                                refreshSubsTrigger++
                            },
                            onOpenFolder = {
                                val safeAnimeId = download.animeId.ifBlank {
                                    download.animeTitle.lowercase().replace(Regex("[^a-z0-9]"), "_").trim('_').take(30).ifBlank { "media" }
                                }
                                val mediaId = "${safeAnimeId}_ep${download.episode}"
                                val targetDir = download.localPath?.let { File(it).parentFile }
                                    ?: File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Kitsugi/Video").takeIf { it.exists() }
                                    ?: File(OfflinePlaybackHelper.getDownloadsDir(context), mediaId)
                                openFolderInFileManager(context, targetDir)
                            },
                            onOpenSubFolder = { subFile ->
                                openFolderInFileManager(context, subFile.parentFile ?: subFile)
                            }
                        )
                    }
                }
            }
        }
        1 -> {
            // ── ALTYAZILAR TAB ───────────────────────────────────────────
            Column(modifier = Modifier.fillMaxSize()) {
                if (allSubtitles.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Rounded.Subtitles,
                                contentDescription = null,
                                tint = KitsugiColors.TextMuted,
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Henüz indirilmiş altyazı bulunamadı.",
                                color = KitsugiColors.TextMuted,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Oynatıcıdaki altyazı listesinden indirdiğiniz altyazılar burada görünür.",
                                color = KitsugiColors.TextMuted.copy(alpha = 0.7f),
                                fontSize = 12.sp
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(allSubtitles, key = { it.file.absolutePath }) { subItem ->
                            SubtitleCardItem(
                                item = subItem,
                                accentColor = accentColor,
                                onOpenFolder = {
                                    openFolderInFileManager(context, subItem.file.parentFile ?: subItem.file)
                                },
                                onDelete = {
                                    if (subItem.file.exists()) {
                                        subItem.file.delete()
                                        Toast.makeText(context, "Altyazı silindi: ${subItem.title}", Toast.LENGTH_SHORT).show()
                                        refreshSubsTrigger++
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
        2 -> {
            // ── RESİMLER TAB ──────────────────────────────────────────────
                    DownloadedImagesTab(
                        images = allImages,
                        accentColor = accentColor,
                        onRefresh = { refreshImagesTrigger++ }
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// DOWNLOADED IMAGES TAB
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun DownloadedImagesTab(
    images: List<DownloadedImageItem>,
    accentColor: Color,
    onRefresh: () -> Unit
) {
    val context = LocalContext.current
    val tabScope = rememberCoroutineScope()
    var galleryGroup by remember { mutableStateOf<DownloadedImageGroup?>(null) }
    var galleryInitialIndex by remember { mutableIntStateOf(0) }

    // İndirilen resimler içerik adına (anime / dizi / film) göre gruplanır.
    // Gruplar en yeni indirmenin tarihine göre sıralanır.
    val groups = remember(images) {
        images
            .groupBy { normalizeImageGroupKey(it.title) }
            .map { (_, list) ->
                DownloadedImageGroup(
                    displayName = list.maxByOrNull { it.lastModified }?.title?.trim()
                        ?.ifBlank { "Bilinmeyen İçerik" } ?: "Bilinmeyen İçerik",
                    items = list.sortedByDescending { it.lastModified }
                )
            }
            .sortedByDescending { g -> g.items.maxOfOrNull { it.lastModified } ?: 0L }
    }

    // Açılan galeri: yalnızca ilgili grubun resimleri gösterilir.
    // allowDownload=false → indirme butonu gösterilmez (içerik zaten cihazda);
    // karşılığında galerideki SİL butonu aktif olur — indirilen kopya doğrudan
    // buradan kaldırılabilir (İndirmeler ekranına geri dönmeden).
    val activeGroup = galleryGroup
    var deletedPaths by remember { mutableStateOf(setOf<String>()) }
    val visibleGalleryItems = remember(activeGroup, deletedPaths) {
        activeGroup?.items?.filterNot { img ->
            ("file://" + img.file.absolutePath) in deletedPaths
        } ?: emptyList()
    }
    // Grubun tüm resimleri silindiyse galeriyi kapat.
    if (activeGroup != null && visibleGalleryItems.isEmpty() && deletedPaths.isNotEmpty()) {
        LaunchedEffect(Unit) {
            galleryGroup = null
            deletedPaths = emptySet()
        }
    }
    if (activeGroup != null && visibleGalleryItems.isNotEmpty()) {
        val galleryItems = remember(visibleGalleryItems) {
            visibleGalleryItems.map { img ->
                GalleryItem(
                    url = "file://${img.file.absolutePath}",
                    source = "Kitsugi",
                    category = GalleryCategory.OTHER,
                    description = img.title,
                    width = img.width.takeIf { it > 0 },
                    height = img.height.takeIf { it > 0 }
                )
            }
        }
        KitsugiImageGalleryDialog(
            galleryItems = galleryItems,
            initialIndex = galleryInitialIndex.coerceIn(0, galleryItems.size - 1),
            title = activeGroup.displayName,
            allowDownload = false,
            allowDelete = true,
            onItemsDeleted = { urls ->
                deletedPaths = deletedPaths + urls.toSet()
                onRefresh()
            },
            onDismiss = {
                galleryGroup = null
                deletedPaths = emptySet()
            }
        )
    }

    if (images.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Rounded.ImageNotSupported,
                    contentDescription = null,
                    tint = KitsugiColors.TextMuted,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Henüz indirilmiş resim bulunamadı.",
                    color = KitsugiColors.TextMuted,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Galeri ekranından resimleri indirdiğinizde burada görünür.",
                    color = KitsugiColors.TextMuted.copy(alpha = 0.7f),
                    fontSize = 12.sp
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            groups.forEach { group ->
                // ── Grup başlığı: içerik adı + resim sayısı ──
                item(key = "group_header_${group.displayName}") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(accentColor.copy(alpha = 0.15f))
                                .border(1.dp, accentColor.copy(alpha = 0.3f), RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.VideoLibrary,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        Text(
                            text = group.displayName,
                            color = KitsugiColors.TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(KitsugiColors.SurfaceStrong)
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "${group.items.size} resim",
                                color = KitsugiColors.TextSecondary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        // Grup bazlı silme: bu içeriğin indirilmiş TÜM resimleri tek
                        // dokunuşla kaldırılır (iki aşamalı onay — yanlışlıkla silme yok).
                        KitsugiAnimatedDeleteButton(
                            onDelete = {
                                val files = group.items.map { it.file }
                                tabScope.launch {
                                    var deleted = 0
                                    files.forEach { file ->
                                        val outcome = KitsugiImageDownloadHelper.deleteImageForUrl(
                                            context,
                                            "file://" + file.absolutePath
                                        )
                                        if (outcome is KitsugiImageDownloadHelper.GalleryDeleteOutcome.Success) deleted++
                                        else if (file.exists()) runCatching { file.delete() }
                                    }
                                    if (deleted > 0) {
                                        galleryGroup = null
                                        deletedPaths = emptySet()
                                    }
                                    Toast.makeText(
                                        context,
                                        if (deleted > 0) "$deleted resim silindi." else "Resimler silinemedi.",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    onRefresh()
                                }
                            },
                            size = 26.dp,
                            expandedWidth = 78.dp,
                            contentDescription = "Bu içeriğin tüm indirilen resimlerini sil"
                        )
                    }
                }
                // ── Grup resimleri ──
                item(key = "group_grid_${group.displayName}") {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        group.items.forEachIndexed { index, img ->
                            DownloadedImageCard(
                                item = img,
                                accentColor = accentColor,
                                modifier = Modifier.width(160.dp),
                                onClick = {
                                    galleryInitialIndex = index
                                    galleryGroup = group
                                },
                                onOpenFolder = {
                                    openFolderInFileManager(context, img.file.parentFile ?: img.file)
                                },
                                onDelete = {
                                    if (img.file.exists()) {
                                        img.file.delete()
                                        // Index'ten de düş — istenirse resim tekrar indirilebilir
                                        KitsugiImageDownloadHelper.unmarkImageDownloadedByFileName(context, img.file.name)
                                        Toast.makeText(context, "Resim silindi: ${img.title}", Toast.LENGTH_SHORT).show()
                                        onRefresh()
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DownloadedImageCard(
    item: DownloadedImageItem,
    accentColor: Color,
    onClick: () -> Unit,
    onOpenFolder: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dateStr = remember(item.lastModified) {
        if (item.lastModified > 0)
            SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(item.lastModified))
        else ""
    }
    val sizeStr = formatBytes(item.fileSizeBytes)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(KitsugiColors.SurfaceSoft)
            .border(1.dp, KitsugiColors.Border.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        Column {
            // Image thumbnail
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
            ) {
                AsyncImage(
                    model = item.file,
                    contentDescription = item.title,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)),
                    contentScale = ContentScale.Crop
                )
                // Gradient overlay at bottom of thumb
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, KitsugiColors.SurfaceSoft)
                            )
                        )
                )
                // Size badge
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(KitsugiColors.SurfaceStrong.copy(alpha = 0.82f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = sizeStr,
                        color = KitsugiColors.TextSecondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Info row
            Column(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Text(
                    text = item.title,
                    color = KitsugiColors.TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 16.sp
                )
                if (dateStr.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = dateStr,
                        color = KitsugiColors.TextMuted,
                        fontSize = 10.sp
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Open folder
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(accentColor.copy(alpha = 0.12f))
                            .clickable(onClick = onOpenFolder),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.FolderOpen,
                            contentDescription = "Klasörü Aç",
                            tint = accentColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    // Delete (Animated Uiverse)
                    KitsugiAnimatedDeleteButton(
                        onDelete = onDelete,
                        size = 30.dp
                    )
                }
            }
        }
    }
}

@Composable
fun DownloadItemRow(
    download: AnimeDownload,
    accentColor: Color,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onDelete: () -> Unit,
    onOpenFolder: () -> Unit,
    onOpenSubFolder: (File) -> Unit
) {
    val context = LocalContext.current
    var isExpanded by remember { mutableStateOf(true) }

    val safeAnimeId = download.animeId.ifBlank {
        download.animeTitle.lowercase().replace(Regex("[^a-z0-9]"), "_").trim('_').take(30).ifBlank { "media" }
    }
    val mediaId = "${safeAnimeId}_ep${download.episode}"
    val localMedia = remember(download) { OfflinePlaybackHelper.getLocalMedia(context, mediaId) }
    val companionSubs = remember(download.localPath) {
        download.localPath?.let { path ->
            val vFile = File(path)
            val pDir = vFile.parentFile
            val bName = vFile.nameWithoutExtension
            if (pDir != null && pDir.exists()) {
                pDir.listFiles()?.filter { f ->
                    f.isFile && f.name.startsWith(bName) && f.extension.lowercase() in listOf("srt", "ass", "ssa", "vtt")
                }?.map { subFile ->
                    val lang = subFile.nameWithoutExtension.removePrefix(bName).trim('.', '_')
                    OfflinePlaybackHelper.LocalSubtitle(
                        language = lang.ifBlank { "tr" },
                        label = subFile.nameWithoutExtension,
                        uri = "file://${subFile.absolutePath}"
                    )
                } ?: emptyList()
            } else emptyList()
        } ?: emptyList()
    }
    val localSubs = if (localMedia?.subtitles.isNullOrEmpty()) companionSubs else localMedia?.subtitles ?: emptyList()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(KitsugiColors.SurfaceSoft, shape = RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Anime Poster
            AsyncImage(
                model = download.posterUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(width = 60.dp, height = 90.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(KitsugiColors.SurfaceStrong),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Info & Progress
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = download.animeTitle,
                    color = KitsugiColors.TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!download.streamName.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Yayın: ${download.streamName}",
                        color = accentColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (!download.streamTitle.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = download.streamTitle,
                        color = KitsugiColors.TextMuted,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Bölüm ${download.episode} • ${download.quality}",
                        color = KitsugiColors.TextSecondary,
                        fontSize = 13.sp
                    )
                    if (!download.source.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(KitsugiColors.AccentPurple.copy(alpha = 0.12f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = download.source,
                                color = KitsugiColors.AccentPurple,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                when (download.status) {
                    AnimeDownload.Status.DOWNLOADING -> {
                        LinearProgressIndicator(
                            progress = { download.progress / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp),
                            color = accentColor,
                            trackColor = KitsugiColors.SurfaceStrong
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        val sizeText = if (download.totalBytes > 0) {
                            "${formatBytes(download.downloadedBytes)} / ${formatBytes(download.totalBytes)}"
                        } else {
                            formatBytes(download.downloadedBytes)
                        }
                        Text(
                            text = "İndiriliyor... $sizeText (%${download.progress})",
                            color = accentColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    AnimeDownload.Status.QUEUE -> {
                        Text(
                            text = "Sırada bekliyor...",
                            color = KitsugiColors.TextMuted,
                            fontSize = 12.sp
                        )
                    }
                    AnimeDownload.Status.PAUSED -> {
                        Text(
                            text = "Duraklatıldı",
                            color = KitsugiColors.TextMuted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    AnimeDownload.Status.COMPLETED -> {
                        Text(
                            text = "Tamamlandı",
                            color = KitsugiColors.AccentGreen,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    AnimeDownload.Status.ERROR -> {
                        Text(
                            text = "Hata oluştu!",
                            color = KitsugiColors.AccentRed,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Actions
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // Klasör simgesi: İndirilen videonun klasörünü dosya yöneticisinde açar
                IconButton(
                    onClick = onOpenFolder,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FolderOpen,
                        contentDescription = "Klasörü Aç",
                        tint = KitsugiColors.AccentBlue,
                        modifier = Modifier.size(20.dp)
                    )
                }

                when (download.status) {
                    AnimeDownload.Status.DOWNLOADING -> {
                        IconButton(onClick = onPause, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Rounded.Pause, contentDescription = "Duraklat", tint = KitsugiColors.TextPrimary, modifier = Modifier.size(20.dp))
                        }
                    }
                    AnimeDownload.Status.PAUSED, AnimeDownload.Status.ERROR, AnimeDownload.Status.QUEUE -> {
                        IconButton(onClick = onResume, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = "Başlat", tint = accentColor, modifier = Modifier.size(20.dp))
                        }
                    }
                    AnimeDownload.Status.COMPLETED -> {
                        IconButton(onClick = onPlay, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = "Oynat", tint = KitsugiColors.AccentGreen, modifier = Modifier.size(22.dp))
                        }
                    }
                }

                KitsugiAnimatedDeleteButton(
                    onDelete = onDelete,
                    size = 36.dp
                )
            }
        }

        // Subtitles belonging to this video - fully visible with folder icons
        if (localSubs.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = KitsugiColors.Border.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Subtitles,
                        contentDescription = null,
                        tint = KitsugiColors.TextSecondary,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = "İndirilen Altyazılar (${localSubs.size})",
                        color = KitsugiColors.TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                    contentDescription = null,
                    tint = KitsugiColors.TextMuted,
                    modifier = Modifier.size(18.dp)
                )
            }

            if (isExpanded) {
                Spacer(modifier = Modifier.height(6.dp))
                localSubs.forEach { sub ->
                    val subFile = remember(sub.uri) { File(sub.uri.removePrefix("file://")) }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(KitsugiColors.SurfaceStrong.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = KitsugiColors.AccentGreen,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = sub.label,
                            color = KitsugiColors.TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = sub.uri.substringAfterLast(".").uppercase(),
                            color = KitsugiColors.TextSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .background(KitsugiColors.SurfaceStrong, shape = RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        // Altyazı Klasör Simgesi: Altyazının indiği konumu açar
                        IconButton(
                            onClick = { onOpenSubFolder(subFile) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.FolderOpen,
                                contentDescription = "Altyazı Klasörünü Aç",
                                tint = KitsugiColors.AccentOrange,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
    }
}

@Composable
fun SubtitleCardItem(
    item: DownloadedSubtitleItem,
    accentColor: Color,
    onOpenFolder: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = KitsugiColors.SurfaceSoft,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(KitsugiColors.AccentOrange.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Subtitles,
                    contentDescription = null,
                    tint = KitsugiColors.AccentOrange,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    color = KitsugiColors.TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Badge: Harici vs Anime Bölümü
                    val badgeText = if (item.animeTitle != null) {
                        "${item.animeTitle} • Ep ${item.episode ?: 1}"
                    } else {
                        "Harici Altyazı"
                    }
                    Text(
                        text = badgeText,
                        color = accentColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .background(accentColor.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 1.dp)
                    )

                    // Format Badge
                    Text(
                        text = item.format,
                        color = KitsugiColors.TextSecondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .background(KitsugiColors.SurfaceStrong, RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    )

                    if (item.fileSizeBytes > 0) {
                        Text(
                            text = formatBytes(item.fileSizeBytes),
                            color = KitsugiColors.TextMuted,
                            fontSize = 11.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))
                val dateStr = remember(item.lastModified) {
                    if (item.lastModified > 0) {
                        SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault()).format(Date(item.lastModified))
                    } else ""
                }
                if (dateStr.isNotEmpty()) {
                    Text(
                        text = dateStr,
                        color = KitsugiColors.TextMuted.copy(alpha = 0.8f),
                        fontSize = 10.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Action Buttons: Klasör Simgesi + Sil
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = onOpenFolder,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FolderOpen,
                        contentDescription = "Klasörü Aç",
                        tint = KitsugiColors.AccentBlue,
                        modifier = Modifier.size(20.dp)
                    )
                }

                KitsugiAnimatedDeleteButton(
                    onDelete = onDelete,
                    size = 34.dp,
                    contentDescription = "Altyazıyı Sil"
                )
            }
        }
    }
}

private fun parseSubtitleFileInfo(nameWithoutExt: String): Triple<String?, Int?, String> {
    // Örnek: "Solo Leveling - Bölüm 3.tr" veya "Solo Leveling - Bölüm 3.tr_Türkçe"
    val epRegex = Regex("""^(.*?)\s*-\s*Bölüm\s*(\d+)(?:\.([a-zA-Z0-9_ -]+))?$""", RegexOption.IGNORE_CASE)
    val epMatch = epRegex.find(nameWithoutExt)
    if (epMatch != null) {
        val anime = epMatch.groupValues[1].trim()
        val ep = epMatch.groupValues[2].toIntOrNull()
        val lang = epMatch.groupValues.getOrNull(3)?.substringBefore("_")?.trim() ?: "auto"
        return Triple(anime, ep, lang)
    }

    // Örnek: "Spirited Away.tr"
    val dotRegex = Regex("""^(.*?)\.([a-zA-Z0-9_ -]+)$""")
    val dotMatch = dotRegex.find(nameWithoutExt)
    if (dotMatch != null) {
        val anime = dotMatch.groupValues[1].trim()
        val lang = dotMatch.groupValues[2].substringBefore("_").trim()
        return Triple(anime, null, lang)
    }

    return Triple(null, null, "auto")
}

/**
 * Loads all downloaded subtitles across:
 * 1. Offline media episode downloads (<cacheDir>/Kitsugi_downloads/<mediaId>/subs/)
 * 2. Standalone subtitles downloaded via player (Downloads/Kitsugi/Subtitles/)
 * 3. App cache subtitles (<cacheDir>/subtitles/)
 */
fun loadAllDownloadedSubtitles(
    context: Context,
    downloads: List<AnimeDownload>
): List<DownloadedSubtitleItem> {
    val results = mutableListOf<DownloadedSubtitleItem>()
    val subExtensions = setOf("srt", "ass", "ssa", "vtt", "ttml")

    // 1. Episode-bound subtitles
    val downloadedMediaList = OfflinePlaybackHelper.listDownloadedMedia(context)
    downloadedMediaList.forEach { media ->
        val matchingDownload = downloads.firstOrNull { "${it.animeId}_ep${it.episode}" == media.mediaId }
        media.subtitles.forEach { sub ->
            val subFile = File(sub.uri.removePrefix("file://"))
            if (subFile.exists() && subFile.isFile) {
                results.add(
                    DownloadedSubtitleItem(
                        title = sub.label,
                        language = sub.language,
                        format = subFile.extension.uppercase(),
                        fileSizeBytes = subFile.length(),
                        lastModified = subFile.lastModified(),
                        file = subFile,
                        animeTitle = matchingDownload?.animeTitle ?: media.title,
                        episode = matchingDownload?.episode,
                        isStandalone = false
                    )
                )
            }
        }
    }

    // 2. Standalone & companion subtitles in public Downloads/Kitsugi/Subtitles and Downloads/Kitsugi/Video
    val publicDirsToScan = listOf(
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Kitsugi/Subtitles"),
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Kitsugi/Video")
    )
    for (publicDir in publicDirsToScan) {
        try {
            if (publicDir.exists()) {
                publicDir.listFiles()?.filter { it.isFile && it.extension.lowercase() in subExtensions }?.forEach { f ->
                    if (results.none { it.file.absolutePath == f.absolutePath }) {
                        val (parsedAnime, parsedEp, parsedLang) = parseSubtitleFileInfo(f.nameWithoutExtension)
                        results.add(
                            DownloadedSubtitleItem(
                                title = f.nameWithoutExtension,
                                language = if (parsedLang.isNotBlank() && parsedLang != "auto") parsedLang else "auto",
                                format = f.extension.uppercase(),
                                fileSizeBytes = f.length(),
                                lastModified = f.lastModified(),
                                file = f,
                                animeTitle = parsedAnime,
                                episode = parsedEp,
                                isStandalone = true
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {}
    }

    // 3. App internal cache subtitles
    try {
        val cacheSubDir = File(context.cacheDir, "subtitles")
        if (cacheSubDir.exists()) {
            cacheSubDir.listFiles()?.filter { it.isFile && it.extension.lowercase() in subExtensions }?.forEach { f ->
                if (results.none { it.file.name == f.name }) {
                    val (parsedAnime, parsedEp, parsedLang) = parseSubtitleFileInfo(f.nameWithoutExtension)
                    results.add(
                        DownloadedSubtitleItem(
                            title = f.nameWithoutExtension,
                            language = if (parsedLang.isNotBlank() && parsedLang != "auto") parsedLang else "auto",
                            format = f.extension.uppercase(),
                            fileSizeBytes = f.length(),
                            lastModified = f.lastModified(),
                            file = f,
                            animeTitle = parsedAnime,
                            episode = parsedEp,
                            isStandalone = true
                        )
                    )
                }
            }
        }
    } catch (_: Exception) {}

    return results.sortedByDescending { it.lastModified }
}

/**
 * Loads all images downloaded by [KitsugiImageDownloadHelper] from:
 * - Downloads/Kitsugi/Images (new dedicated folder)
 * - Downloads/Kitsugi (legacy flat storage)
 */
fun loadAllDownloadedImages(context: Context): List<DownloadedImageItem> {
    // İndirilen görseller ORİJİNAL formatıyla kaydedilir (KitsugiImageDownloadHelper
    // GIF/PNG/AVIF/HEIC dönüşümü yapmaz) → tarayıcı da aynı listeyi bilmeli, aksi
    // halde örneğin indirilmiş bir HEIC/AVIF ne İndirmeler'de görünür ne de silinebilir.
    val imageExtensions = setOf(
        "jpg", "jpeg", "png", "webp", "gif", "avif", "heic", "heif", "bmp", "tiff"
    )
    val results = mutableListOf<DownloadedImageItem>()
    val dirsToScan = listOfNotNull(
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Kitsugi/Images"),
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Kitsugi"),   // legacy
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Kitsugi/Images"),
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Kitsugi"),
        context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.let { File(it, "Kitsugi/Images") },
        context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)?.let { File(it, "Kitsugi/Images") }
    )
    for (dir in dirsToScan) {
        try {
            if (!dir.exists()) continue
            dir.listFiles()?.filter { f ->
                f.isFile &&
                f.extension.lowercase() in imageExtensions &&
                f.name.startsWith("Kitsugi_") &&
                results.none { it.file.absolutePath == f.absolutePath }
            }?.forEach { f ->
                // Title: strip "Kitsugi_" prefix and trailing timestamp+extension
                // (birden fazla alt çizgiyi tek boşluğa indir — Unicode içerik adları düzgün görünsün)
                val rawName = f.nameWithoutExtension.removePrefix("Kitsugi_")
                val title = rawName.replace(Regex("_\\d{13}$"), "").replace(Regex("_+"), " ").trim()
                results.add(
                    DownloadedImageItem(
                        file = f,
                        title = title.ifBlank { f.nameWithoutExtension },
                        fileSizeBytes = f.length(),
                        lastModified = f.lastModified()
                    )
                )
            }
        } catch (_: Exception) {}
    }
    return results.sortedByDescending { it.lastModified }
}

/**
 * Robust helper function that opens the folder containing a downloaded file or directory
 * in the user's external file manager app.
 */
fun openFolderInFileManager(context: Context, targetFileOrDir: File) {
    val folder = if (targetFileOrDir.isDirectory) targetFileOrDir else (targetFileOrDir.parentFile ?: targetFileOrDir)
    if (!folder.exists()) {
        folder.mkdirs()
    }

    // Copy absolute path to clipboard for quick paste in any file explorer
    runCatching {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = ClipData.newPlainText("Klasör Konumu", folder.absolutePath)
        clipboard?.setPrimaryClip(clip)
    }

    val pm = context.packageManager
    val extraIntents = mutableListOf<Intent>()

    val oldPolicy = StrictMode.getVmPolicy()
    StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder().build())

    try {
        val fileUri = Uri.fromFile(folder)

        // 1. Direct explicit package intents for popular file managers (ZArchiver, MiXplorer, Solid, etc.)
        val knownFileManagerPkgs = listOf(
            "ru.zdevs.zarchiver",
            "ru.zdevs.zarchiver.pro",
            "com.mixplorer",
            "com.mixplorer.silver",
            "pl.solidexplorer2",
            "com.sec.android.app.myfiles",
            "com.mi.android.globalFileexplorer",
            "com.google.android.apps.nbu.files",
            "com.estrongs.android.pop",
            "com.alphainventor.filemanager"
        )

        for (pkg in knownFileManagerPkgs) {
            val isInstalled = runCatching {
                pm.getPackageInfo(pkg, 0)
                true
            }.getOrDefault(false)

            if (isInstalled) {
                val directIntent = Intent(Intent.ACTION_VIEW).apply {
                    setPackage(pkg)
                    setDataAndType(fileUri, "resource/folder")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                extraIntents.add(directIntent)
            }
        }

        // 2. Intent with resource/folder (widely supported by Android file explorers)
        val genericFolderIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(fileUri, "resource/folder")
            addCategory(Intent.CATEGORY_DEFAULT)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        // 3. Intent with vnd.android.cursor.dir/*
        val cursorDirIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(fileUri, "vnd.android.cursor.dir/*")
            addCategory(Intent.CATEGORY_DEFAULT)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        extraIntents.add(cursorDirIntent)

        // 4. FileProvider intent
        val contentUri = runCatching {
            FileProvider.getUriForFile(context, "com.kitsugi.animelist.fileprovider", folder)
        }.getOrNull()
        if (contentUri != null) {
            val fpIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "resource/folder")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            extraIntents.add(fpIntent)
        }

        // 5. Android SAF DocumentsContract intent (for system "Dosyalar" / DocumentsUI)
        val extStorage = Environment.getExternalStorageDirectory()
        if (folder.absolutePath.startsWith(extStorage.absolutePath)) {
            val relPath = folder.absolutePath.removePrefix(extStorage.absolutePath).trimStart('/', '\\')
            val encoded = Uri.encode(relPath)
            val docUri = Uri.parse("content://com.android.externalstorage.documents/document/primary%3A$encoded")
            val safIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(docUri, "vnd.android.document/directory")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            extraIntents.add(safIntent)
        }

        // 6. DownloadManager intent if inside Downloads folder
        val publicDownloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (folder.absolutePath.startsWith(publicDownloadsDir.absolutePath)) {
            val dlIntent = Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            extraIntents.add(dlIntent)
        }

        val chooser = Intent.createChooser(genericFolderIntent, "Klasörü Dosya Yöneticisi ile Aç").apply {
            if (extraIntents.isNotEmpty()) {
                putExtra(Intent.EXTRA_INITIAL_INTENTS, extraIntents.toTypedArray())
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
        Toast.makeText(context, "Klasör açılıyor: ${folder.name}", Toast.LENGTH_SHORT).show()
        return
    } catch (_: Exception) {}
    finally {
        StrictMode.setVmPolicy(oldPolicy)
    }

    Toast.makeText(context, "Klasör konumu: ${folder.absolutePath}", Toast.LENGTH_LONG).show()
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
    return String.format(Locale.US, "%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}
