package com.kitsugi.animelist.ui.screens.stream

import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.Text

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.utils.tvClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material.icons.rounded.Close
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import coil3.compose.AsyncImage
import com.kitsugi.animelist.data.repository.MeasuredStreamInfo
import com.kitsugi.animelist.data.repository.StreamAudioKind
import com.kitsugi.animelist.data.repository.StreamInfoOrigin
import com.kitsugi.animelist.data.repository.StreamProbe
import com.kitsugi.animelist.data.repository.StreamSource
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalBlurAdultMedia
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.clickable
import com.kitsugi.animelist.ui.theme.gradient.border
import com.kitsugi.animelist.ui.components.KitsugiImageGalleryDialog
import com.kitsugi.animelist.ui.components.KitsugiNsfwImage

/**
 * A single stream card showing quality/cache/addon badges and triggering playback on tap.
 *
 * Overhauled layout:
 *   [Thumbnail 85×120dp, click to zoom]  ->  [Text column (vertical stack)]  ->  [Action buttons]
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StreamCard(
    source: StreamSource,
    accentColor: Color,
    onClick: () -> Unit,
    onDownloadClick: () -> Unit
) {
    // ── Gerçek (ölçülen) akış bilgisi ────────────────────────────────────────
    // Kaynak kalite/dil bildirmediyse tahmin ETMİYORUZ; bunun yerine akışın kendisini
    // ölçüyoruz (HLS master playlist → gerçek çözünürlükler ve ses/altyazı dilleri).
    val measured by produceState<MeasuredStreamInfo?>(
        initialValue = StreamProbe.cached(source.url),
        key1 = source.url
    ) {
        if (value == null && StreamProbe.isProbeable(source)) {
            value = runCatching { StreamProbe.probe(source) }.getOrNull()
        }
    }

    val qualityInfo = remember(source, measured) { resolveStreamQuality(source, measured) }
    val langInfo = remember(source, measured) { resolveStreamLangInfo(source, measured) }
    val size = remember(source, measured) { resolveStreamSizeLabel(source, measured) }
    val cacheState = remember(source) { getCacheState(source) }

    var showImageDialog by remember { mutableStateOf(false) }

    // Fullscreen thumbnail preview using the app's standard image gallery dialog
    if (showImageDialog && !source.thumbnailUrl.isNullOrBlank()) {
        KitsugiImageGalleryDialog(
            imageUrls = listOf(source.thumbnailUrl!!),
            initialIndex = 0,
            title = source.name,
            onDismiss = { showImageDialog = false }
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .tvClickable(shape = RoundedCornerShape(16.dp), onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = KitsugiColors.SurfaceStrong.copy(alpha = 0.5f)
        )
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val isCompact = maxWidth < 400.dp
            val imgWidth = if (isCompact) 95.dp else 115.dp
            val imgHeight = if (isCompact) 135.dp else 162.dp

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // ── Thumbnail (optional, poster style) ───────────────────
                if (!source.thumbnailUrl.isNullOrBlank()) {
                    val blurAdultMedia = LocalBlurAdultMedia.current
                    val isClickable = !(blurAdultMedia && source.isAdultContent)
                    Box(
                        modifier = Modifier
                            .width(imgWidth)
                            .height(imgHeight)
                            .clip(RoundedCornerShape(10.dp))
                            .background(KitsugiColors.Surface)
                            .clickable(enabled = isClickable) { showImageDialog = true }
                    ) {
                        KitsugiNsfwImage(
                            model = source.thumbnailUrl,
                            contentDescription = source.name,
                            isAdult = source.isAdultContent,
                            modifier = Modifier.fillMaxSize()
                        )
                        // Subtle bottom gradient
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(30.dp)
                                .align(Alignment.BottomCenter)
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))
                                    )
                                )
                        )

                    }
                }

                // ── Text column (weight=1f, stacked vertically for responsiveness) ──
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .align(Alignment.Top)
                ) {
                    // Badge row
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(bottom = 6.dp)
                    ) {
                        // ── Kalite rozeti ───────────────────────────────────
                        // Yalnızca gerçekten bilinen kalite gösterilir. Bilinmiyorsa
                        // "Kalite ?" yazılır; uydurma bir değer (eski "400p" / "1080p (HD)") basılmaz.
                        val qualityHeight = qualityInfo.height ?: 0
                        val qualityColor = when {
                            !qualityInfo.isKnown -> KitsugiColors.TextMuted
                            qualityHeight >= 2160 -> KitsugiColors.AccentRed
                            qualityHeight >= 1080 -> KitsugiColors.AccentBlue
                            qualityHeight >= 720  -> KitsugiColors.AccentGreen
                            else -> KitsugiColors.AccentOrange
                        }
                        val qualityText = when {
                            qualityInfo.isKnown && qualityInfo.isAdaptive -> "${qualityInfo.label} · oto"
                            qualityInfo.isKnown -> qualityInfo.label.orEmpty()
                            qualityInfo.isAdaptive -> "Oto (HLS)"
                            else -> "Kalite ?"
                        }
                        StreamBadge(
                            text = qualityText,
                            color = qualityColor,
                            bgAlpha = if (qualityInfo.isKnown) 0.15f else 0.08f,
                            bgColor = qualityColor,
                            isVerified = qualityInfo.origin == StreamInfoOrigin.MEASURED
                        )

                        // ── Dil rozeti — yalnızca kanıt varsa ───────────────
                        val langText = when (langInfo.kind) {
                            StreamAudioKind.DUB  -> "🎙️ Dublaj"
                            StreamAudioKind.SUB  -> "💬 Altyazılı"
                            StreamAudioKind.DUAL -> "🌐 Çift dil"
                            StreamAudioKind.UNKNOWN -> null
                        }
                        val langColor = when (langInfo.kind) {
                            StreamAudioKind.DUB  -> KitsugiColors.AccentOrange
                            StreamAudioKind.SUB  -> KitsugiColors.AccentBlue
                            StreamAudioKind.DUAL -> KitsugiColors.AccentPurple
                            StreamAudioKind.UNKNOWN -> KitsugiColors.TextMuted
                        }
                        if (langText != null) {
                            StreamBadge(
                                text = langText,
                                color = langColor,
                                bgAlpha = 0.15f,
                                bgColor = langColor,
                                isVerified = langInfo.origin == StreamInfoOrigin.MEASURED ||
                                    langInfo.origin == StreamInfoOrigin.PROVIDER
                            )
                        }

                        // Gerçekten tespit edilen ses dilleri (HLS EXT-X-MEDIA)
                        if (langInfo.audioLanguages.isNotEmpty()) {
                            StreamBadge(
                                text = "🔊 " + langInfo.audioLanguages.joinToString("/"),
                                color = KitsugiColors.AccentOrange,
                                bgAlpha = 0.12f,
                                bgColor = KitsugiColors.AccentOrange,
                                isVerified = true
                            )
                        }

                        // Kaynağın gerçekten verdiği altyazı dosyaları
                        if (langInfo.subtitleLanguages.isNotEmpty()) {
                            StreamBadge(
                                text = "CC " + langInfo.subtitleLanguages.joinToString("/"),
                                color = KitsugiColors.AccentBlue,
                                bgAlpha = 0.12f,
                                bgColor = KitsugiColors.AccentBlue,
                                isVerified = true
                            )
                        }

                        StreamBadge(
                            text = source.addonName,
                            color = KitsugiColors.AccentPurple,
                            bgAlpha = 0.10f,
                            bgColor = KitsugiColors.AccentPurple
                        )

                        // Only show Cache State badge if it is a torrent/debrid stream (saves space!)
                        val isTorrent = source.isTorrent
                        if (isTorrent) {
                            val (cacheText, cacheColor) = when (cacheState) {
                                DebridCacheState.CACHED     -> "Önbellekte"    to KitsugiColors.AccentGreen
                                DebridCacheState.NOT_CACHED -> "İndirilecek"   to KitsugiColors.AccentOrange
                                DebridCacheState.P2P        -> "Torrent (P2P)" to KitsugiColors.AccentBlue
                            }
                            StreamBadge(text = cacheText, color = cacheColor, bgAlpha = 0.15f, bgColor = cacheColor)
                        }

                        if (!size.isNullOrBlank()) {
                            Text(
                                text = size,
                                color = KitsugiColors.TextMuted,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.align(Alignment.CenterVertically),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Source name (Sitenin asıl yayın adı)
                    Text(
                        text = source.name,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Clean video source details label (filename or hosting domain)
                    val sourceLabel = remember(source) { getCleanVideoSourceLabel(source) }
                    if (sourceLabel.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(KitsugiColors.Surface.copy(alpha = 0.5f))
                                .border(
                                    width = 1.dp,
                                    color = KitsugiColors.SurfaceStrong.copy(alpha = 0.8f),
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = sourceLabel,
                                color = KitsugiColors.TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Action buttons inside column for compact screens
                    if (isCompact) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            IconButton(
                                onClick = onDownloadClick,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Download,
                                    contentDescription = "İndir",
                                    tint = KitsugiColors.TextSecondary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(accentColor.copy(alpha = 0.1f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PlayArrow,
                                    contentDescription = "Oynat",
                                    tint = accentColor,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                // Action buttons on the right for wide screens
                if (!isCompact) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.align(Alignment.CenterVertically)
                    ) {
                        IconButton(
                            onClick = onDownloadClick,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Download,
                                contentDescription = "İndir",
                                tint = KitsugiColors.TextSecondary,
                                modifier = Modifier.size(22.dp)
                                )
                            }

                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(accentColor.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = "Oynat",
                                tint = accentColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Rozet. [isVerified] true ise bilgi kaynağın meta verisinden ya da gerçek akış ölçümünden
 * gelir ve "✓" ile işaretlenir; false ise yalnızca dosya adında yazdığı için gösterilir.
 */
@Composable
private fun StreamBadge(
    text: String,
    color: Color,
    bgAlpha: Float,
    bgColor: Color,
    isVerified: Boolean = false
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor.copy(alpha = bgAlpha))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = if (isVerified) "$text ✓" else text,
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            softWrap = false
        )
    }
}


