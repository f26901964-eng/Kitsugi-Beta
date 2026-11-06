package com.kitsugi.animelist.ui.screens.offline

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.kitsugi.animelist.R
import com.kitsugi.animelist.data.manga.download.MangaDownload
import com.kitsugi.animelist.data.manga.download.MangaDownloadManager
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.LinearProgressIndicator
import com.kitsugi.animelist.ui.theme.gradient.Text
import com.kitsugi.animelist.ui.theme.gradient.background
import java.io.File
import java.util.Locale

@Composable
fun MangaDownloadsTab(
    downloads: List<MangaDownload>,
    accentColor: Color,
    onRead: (MangaDownload) -> Unit,
    onOpenFolder: (File) -> Unit,
) {
    val context = LocalContext.current
    var pendingDelete by remember { mutableStateOf<MangaDownload?>(null) }
    val groups = remember(downloads) {
        downloads.groupBy { "${it.sourceKey}|${it.mangaUrl}" }.values.toList()
    }

    if (downloads.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier.padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.MenuBook,
                    contentDescription = null,
                    tint = KitsugiColors.TextMuted,
                    modifier = Modifier.size(64.dp),
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.downloads_manga_empty),
                    color = KitsugiColors.TextSecondary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.downloads_manga_empty_description),
                    color = KitsugiColors.TextMuted,
                    fontSize = 12.sp,
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            groups.forEach { group ->
                val first = group.first()
                item(key = "header_${first.sourceKey}_${first.mangaUrl}") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AsyncImage(
                            model = first.mangaPosterUrl,
                            contentDescription = first.mangaTitle,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .width(38.dp)
                                .height(52.dp)
                                .clip(RoundedCornerShape(7.dp))
                                .background(KitsugiColors.SurfaceSoft),
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                first.mangaTitle,
                                color = KitsugiColors.TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "${first.sourceName} • ${stringResource(R.string.downloads_manga_chapters_count, group.size)}",
                                color = KitsugiColors.TextMuted,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                items(group, key = { it.id }) { download ->
                    MangaDownloadChapterCard(
                        download = download,
                        accentColor = accentColor,
                        onRead = {
                            if (download.status == MangaDownload.Status.COMPLETED) onRead(download)
                        },
                        onPause = { MangaDownloadManager.pause(context, download.id) },
                        onResume = { MangaDownloadManager.resume(context, download.id) },
                        onDelete = { pendingDelete = download },
                        onOpenFolder = {
                            val local = download.localPath?.let(::File)
                            val target = when {
                                local?.isFile == true -> local.parentFile
                                local != null -> local
                                else -> null
                            }
                            if (target != null) onOpenFolder(target)
                        },
                    )
                }
            }
        }
    }

    pendingDelete?.let { download ->
        val variant = download.scanlator?.takeIf { it.isNotBlank() }
            ?: stringResource(R.string.manga_chapter_unknown_scanlator)
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.downloads_manga_delete_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.downloads_manga_delete_message,
                        download.mangaTitle,
                        download.chapterName,
                        "$variant • ${download.sourceName}",
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    MangaDownloadManager.delete(context, download.id)
                    pendingDelete = null
                    Toast.makeText(context, R.string.downloads_manga_deleted, Toast.LENGTH_SHORT).show()
                }) {
                    Text(stringResource(R.string.action_delete), color = KitsugiColors.AccentOrange)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.iptal))
                }
            },
        )
    }
}

@Composable
private fun MangaDownloadChapterCard(
    download: MangaDownload,
    accentColor: Color,
    onRead: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onDelete: () -> Unit,
    onOpenFolder: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(KitsugiColors.SurfaceSoft)
            .clickable(
                enabled = download.status == MangaDownload.Status.COMPLETED,
                onClick = onRead,
            )
            .padding(start = 14.dp, end = 4.dp, top = 12.dp, bottom = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    download.chapterName,
                    color = KitsugiColors.TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                val scanlator = download.scanlator?.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.manga_chapter_unknown_scanlator)
                Text(
                    "$scanlator • ${download.sourceName}",
                    color = KitsugiColors.TextMuted,
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            when (download.status) {
                MangaDownload.Status.DOWNLOADING,
                MangaDownload.Status.QUEUE,
                -> IconButton(onClick = onPause) {
                    Icon(
                        Icons.Rounded.Pause,
                        contentDescription = stringResource(R.string.downloads_manga_pause),
                        tint = accentColor,
                    )
                }
                MangaDownload.Status.PAUSED -> IconButton(onClick = onResume) {
                    Icon(
                        Icons.Rounded.PlayArrow,
                        contentDescription = stringResource(R.string.downloads_manga_resume),
                        tint = accentColor,
                    )
                }
                MangaDownload.Status.ERROR -> IconButton(onClick = onResume) {
                    Icon(
                        Icons.Rounded.Refresh,
                        contentDescription = stringResource(R.string.downloads_manga_retry),
                        tint = KitsugiColors.AccentOrange,
                    )
                }
                MangaDownload.Status.COMPLETED -> IconButton(
                    onClick = onOpenFolder,
                    enabled = !download.localPath.isNullOrBlank(),
                ) {
                    Icon(
                        Icons.Rounded.FolderOpen,
                        contentDescription = stringResource(R.string.indirme_klasorunu_ac),
                        tint = accentColor,
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Rounded.DeleteOutline,
                    contentDescription = stringResource(R.string.downloads_manga_delete),
                    tint = KitsugiColors.TextMuted,
                )
            }
        }

        Spacer(Modifier.height(6.dp))
        when (download.status) {
            MangaDownload.Status.DOWNLOADING -> {
                LinearProgressIndicator(
                    progress = { download.progress / 100f },
                    modifier = Modifier
                        .padding(end = 12.dp)
                        .fillMaxWidth()
                        .height(4.dp),
                    color = accentColor,
                    trackColor = KitsugiColors.SurfaceStrong,
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    "${stringResource(R.string.manga_chapter_download_progress, download.downloadedPages, download.totalPages)} · ${download.progress}%",
                    color = accentColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                download.error?.let {
                    Spacer(Modifier.height(2.dp))
                    Text(it, color = KitsugiColors.TextSecondary, fontSize = 11.sp)
                }
            }
            MangaDownload.Status.QUEUE -> Text(
                download.error ?: stringResource(R.string.manga_chapter_download_waiting),
                color = KitsugiColors.TextMuted,
                fontSize = 11.sp,
            )
            MangaDownload.Status.PAUSED -> Text(
                "${stringResource(R.string.duraklatildi)} · ${stringResource(R.string.manga_chapter_download_progress, download.downloadedPages, download.totalPages)}",
                color = KitsugiColors.TextMuted,
                fontSize = 11.sp,
            )
            MangaDownload.Status.COMPLETED -> Text(
                "${stringResource(R.string.downloads_manga_offline_ready)} · ${stringResource(R.string.manga_chapter_download_progress, download.totalPages, download.totalPages)} · ${formatMangaBytes(download.totalBytes)}",
                color = KitsugiColors.AccentGreen,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            )
            MangaDownload.Status.ERROR -> Text(
                download.error ?: stringResource(R.string.ui_error_generic),
                color = KitsugiColors.AccentOrange,
                fontSize = 11.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(4.dp))
    }
}

private fun formatMangaBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 MB"
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 100.0) {
        String.format(Locale.getDefault(), "%.0f MB", mb)
    } else {
        String.format(Locale.getDefault(), "%.1f MB", mb)
    }
}
