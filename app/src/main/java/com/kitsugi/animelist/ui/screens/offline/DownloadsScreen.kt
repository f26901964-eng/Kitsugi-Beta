package com.kitsugi.animelist.ui.screens.offline

import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.os.StrictMode
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.kitsugi.animelist.ui.screens.fullscreen.KitsugiFullscreenPlayerActivity
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val accentColor = LocalKitsugiAccent.current
    val downloads by AnimeDownloadManager.downloads.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    var refreshSubsTrigger by remember { mutableIntStateOf(0) }

    val allSubtitles = remember(downloads, refreshSubsTrigger) {
        loadAllDownloadedSubtitles(context, downloads)
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
                    val rootDir = if (selectedTab == 1) {
                        File(
                            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                            "Kitsugi/Subtitles"
                        ).also { it.mkdirs() }
                    } else {
                        OfflinePlaybackHelper.getDownloadsDir(context)
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

        // Tabs: Videolar vs Altyazılar
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = KitsugiColors.Background,
            contentColor = KitsugiColors.TextPrimary,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color = accentColor,
                    height = 3.dp
                )
            },
            divider = {
                HorizontalDivider(color = KitsugiColors.Border)
            }
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.VideoLibrary,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = if (selectedTab == 0) accentColor else KitsugiColors.TextMuted
                        )
                        Text(
                            text = "Videolar (${downloads.size})",
                            fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal,
                            color = if (selectedTab == 0) KitsugiColors.TextPrimary else KitsugiColors.TextMuted
                        )
                    }
                }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Subtitles,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = if (selectedTab == 1) accentColor else KitsugiColors.TextMuted
                        )
                        Text(
                            text = "Altyazılar (${allSubtitles.size})",
                            fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal,
                            color = if (selectedTab == 1) KitsugiColors.TextPrimary else KitsugiColors.TextMuted
                        )
                    }
                }
            )
        }

        // Content
        if (selectedTab == 0) {
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
                                val mediaId = "${download.animeId}_ep${download.episode}"
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
                                        KitsugiFullscreenPlayerActivity.startWithStreamUrls(
                                            context = context,
                                            videoUrl = "file://$path",
                                            title = "${download.animeTitle} - Bölüm ${download.episode}",
                                            headers = emptyMap(),
                                            subtitles = emptyList()
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
                                val targetDir = download.localPath?.let { File(it).parentFile }
                                    ?: File(OfflinePlaybackHelper.getDownloadsDir(context), "${download.animeId}_ep${download.episode}")
                                openFolderInFileManager(context, targetDir)
                            },
                            onOpenSubFolder = { subFile ->
                                openFolderInFileManager(context, subFile.parentFile ?: subFile)
                            }
                        )
                    }
                }
            }
        } else {
            // ── ALTYAZILAR TAB ───────────────────────────────────────────
            Column(modifier = Modifier.fillMaxSize()) {
                // Standalone subtitles directory banner
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            val subDir = File(
                                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                                "Kitsugi/Subtitles"
                            ).also { it.mkdirs() }
                            openFolderInFileManager(context, subDir)
                        },
                    color = KitsugiColors.SurfaceSoft,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(accentColor.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.FolderOpen,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Harici Altyazı Klasörü",
                                color = KitsugiColors.TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Downloads/Kitsugi/Subtitles",
                                color = KitsugiColors.TextMuted,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Icon(
                            imageVector = Icons.Rounded.OpenInNew,
                            contentDescription = "Klasörü Aç",
                            tint = accentColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

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

    val mediaId = "${download.animeId}_ep${download.episode}"
    val localMedia = remember(download) { OfflinePlaybackHelper.getLocalMedia(context, mediaId) }
    val localSubs = localMedia?.subtitles ?: emptyList()

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

                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Rounded.Delete, contentDescription = "Sil", tint = KitsugiColors.AccentRed, modifier = Modifier.size(20.dp))
                }
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

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Delete,
                        contentDescription = "Altyazıyı Sil",
                        tint = KitsugiColors.AccentRed,
                        modifier = Modifier.size(18.dp)
                    )
                }
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

    // 2. Standalone subtitles in public Downloads/Kitsugi/Subtitles
    try {
        val publicDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "Kitsugi/Subtitles"
        )
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

    val extStorage = Environment.getExternalStorageDirectory()
    val isExternal = folder.absolutePath.startsWith(extStorage.absolutePath)

    // 1. Android SAF / DocumentsContract directory intent for external storage (e.g. Downloads folder)
    if (isExternal) {
        try {
            val relPath = folder.absolutePath.removePrefix(extStorage.absolutePath).trimStart('/', '\\')
            val encoded = Uri.encode(relPath)
            val docUri = Uri.parse("content://com.android.externalstorage.documents/document/primary%3A$encoded")
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(docUri, "vnd.android.document/directory")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Klasörü Dosya Yöneticisinde Aç"))
            Toast.makeText(context, "Klasör konumu: ${folder.name}", Toast.LENGTH_SHORT).show()
            return
        } catch (_: Exception) {}
    }

    // 2. FileProvider with resource/folder MIME
    try {
        val targetUri = FileProvider.getUriForFile(
            context,
            "com.kitsugi.animelist.fileprovider",
            if (targetFileOrDir.exists() && targetFileOrDir.isFile) targetFileOrDir else folder
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(targetUri, "resource/folder")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "Klasörü Dosya Yöneticisi ile Aç"))
        Toast.makeText(context, "Klasör açılıyor: ${folder.name}", Toast.LENGTH_SHORT).show()
        return
    } catch (_: Exception) {}

    // 3. FileProvider with target file / MIME
    try {
        val fileToOpen = if (targetFileOrDir.exists() && targetFileOrDir.isFile) targetFileOrDir else {
            folder.listFiles()?.firstOrNull { it.isFile }
        }
        if (fileToOpen != null) {
            val fileUri = FileProvider.getUriForFile(
                context,
                "com.kitsugi.animelist.fileprovider",
                fileToOpen
            )
            val mime = when (fileToOpen.extension.lowercase()) {
                "mp4" -> "video/mp4"
                "mkv" -> "video/x-matroska"
                "srt" -> "application/x-subrip"
                "ass", "ssa" -> "text/x-ass"
                "vtt" -> "text/vtt"
                else -> "*/*"
            }
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(fileUri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Konumu Dosya Yöneticisi ile Aç"))
            Toast.makeText(context, "Dosya: ${fileToOpen.name}", Toast.LENGTH_SHORT).show()
            return
        }
    } catch (_: Exception) {}

    // 4. ACTION_VIEW_DOWNLOADS fallback
    try {
        val dlIntent = Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(dlIntent)
        Toast.makeText(context, "İndirilenler klasörü açıldı: ${folder.name}", Toast.LENGTH_SHORT).show()
        return
    } catch (_: Exception) {}

    // 5. file:// URI with relaxed VmPolicy fallback
    try {
        val oldPolicy = StrictMode.getVmPolicy()
        StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder().build())
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.fromFile(folder), "resource/folder")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Klasörü Aç"))
            return
        } finally {
            StrictMode.setVmPolicy(oldPolicy)
        }
    } catch (_: Exception) {}

    Toast.makeText(context, "Klasör konumu: ${folder.absolutePath}", Toast.LENGTH_LONG).show()
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
    return String.format(Locale.US, "%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}
