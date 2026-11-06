package com.kitsugi.animelist.ui.screens.manga

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.NavigateNext
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kitsugi.animelist.R
import com.kitsugi.animelist.data.manga.MangaChapter
import com.kitsugi.animelist.data.manga.download.MangaDownload
import com.kitsugi.animelist.ui.components.KitsugiSheetOrDialog
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.theme.gradient.CircularProgressIndicator
import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.Text
import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.utils.tvClickable
import java.text.DateFormat
import java.util.Date

@Composable
fun ChapterDownloadIconButton(
    download: MangaDownload?,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val status = download?.status
    val description = when (status) {
        MangaDownload.Status.DOWNLOADING -> stringResource(R.string.downloads_manga_pause)
        MangaDownload.Status.PAUSED -> stringResource(R.string.downloads_manga_resume)
        MangaDownload.Status.ERROR -> stringResource(R.string.downloads_manga_retry)
        MangaDownload.Status.COMPLETED -> stringResource(R.string.manga_chapter_downloaded)
        MangaDownload.Status.QUEUE -> stringResource(R.string.manga_chapter_download_waiting)
        null -> stringResource(R.string.manga_chapter_download)
    }
    IconButton(onClick = onClick, modifier = modifier.size(40.dp)) {
        when (status) {
            MangaDownload.Status.DOWNLOADING -> Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { (download.progress / 100f).coerceIn(0.02f, 1f) },
                    modifier = Modifier.size(30.dp),
                    color = accent,
                    strokeWidth = 2.dp,
                )
                Icon(
                    Icons.Rounded.Pause,
                    contentDescription = description,
                    tint = accent,
                    modifier = Modifier.size(15.dp),
                )
            }
            MangaDownload.Status.COMPLETED -> Icon(
                Icons.Rounded.DownloadDone,
                contentDescription = description,
                tint = KitsugiColors.AccentGreen,
                modifier = Modifier.size(22.dp),
            )
            MangaDownload.Status.QUEUE -> Icon(
                Icons.Rounded.HourglassTop,
                contentDescription = description,
                tint = accent,
                modifier = Modifier.size(21.dp),
            )
            MangaDownload.Status.PAUSED -> Icon(
                Icons.Rounded.PlayArrow,
                contentDescription = description,
                tint = accent,
                modifier = Modifier.size(23.dp),
            )
            MangaDownload.Status.ERROR -> Icon(
                Icons.Rounded.Refresh,
                contentDescription = description,
                tint = KitsugiColors.AccentOrange,
                modifier = Modifier.size(21.dp),
            )
            null -> Icon(
                Icons.Rounded.Download,
                contentDescription = description,
                tint = KitsugiColors.TextMuted,
                modifier = Modifier.size(21.dp),
            )
        }
    }
}

/** Shared translator picker used by both the detail list and next/previous chapter navigation. */
@Composable
fun ChapterVariantSheet(
    title: String,
    chapters: List<MangaChapter>,
    downloadsByChapterUrl: Map<String, MangaDownload> = emptyMap(),
    message: String? = null,
    onOpenChapter: (MangaChapter) -> Unit,
    onDownloadChapter: ((MangaChapter) -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    val accent = LocalKitsugiAccent.current
    KitsugiSheetOrDialog(onDismiss = onDismiss) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 8.dp, top = 4.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.manga_chapter_choose_scanlator),
                    color = KitsugiColors.TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = title,
                    color = KitsugiColors.TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.kapat),
                    tint = KitsugiColors.TextSecondary,
                )
            }
        }
        if (!message.isNullOrBlank()) {
            Text(
                text = message,
                color = KitsugiColors.TextSecondary,
                fontSize = 13.sp,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 14.dp),
            )
        }
        HorizontalDivider(color = KitsugiColors.SurfaceStrong)
        LazyColumn(
            modifier = Modifier.weight(1f, fill = false),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 12.dp,
                end = 12.dp,
                top = 10.dp,
                bottom = 18.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(chapters, key = { it.url }) { chapter ->
                val scanlator = chapter.scanlator?.trim().takeUnless { it.isNullOrBlank() }
                    ?: stringResource(R.string.manga_chapter_unknown_scanlator)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(KitsugiColors.SurfaceSoft)
                        .tvClickable(shape = RoundedCornerShape(14.dp)) {
                            onOpenChapter(chapter)
                            onDismiss()
                        }
                        .padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = scanlator,
                            color = KitsugiColors.TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(3.dp))
                        val variantSubtitle = buildString {
                            if (!chapter.name.equals(title, ignoreCase = true)) {
                                append(chapter.name)
                            }
                            if (chapter.uploadDate > 0L) {
                                if (isNotEmpty()) append(" • ")
                                append(DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(chapter.uploadDate)))
                            }
                        }
                        val readLabel = stringResource(R.string.oku)
                        Text(
                            text = variantSubtitle.ifBlank { readLabel },
                            color = KitsugiColors.TextMuted,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (onDownloadChapter != null) {
                        ChapterDownloadIconButton(
                            download = downloadsByChapterUrl[chapter.url],
                            accent = accent,
                            onClick = { onDownloadChapter(chapter) },
                        )
                    } else {
                        Icon(
                            Icons.AutoMirrored.Rounded.NavigateNext,
                            contentDescription = stringResource(R.string.oku),
                            tint = KitsugiColors.TextMuted,
                            modifier = Modifier.padding(horizontal = 12.dp),
                        )
                    }
                }
            }
        }
    }
}
