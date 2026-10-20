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
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
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
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.delay
import com.kitsugi.animelist.R

/**
 * Kart görüş alanına girdikten sonra ölçüm başlatmadan önce beklenen süre.
 * Hızlı kaydırma sırasında kartlar bu süre dolmadan görüş alanından çıkar; ölçüm
 * hiç başlamaz (iptal edilen coroutine = sıfır ağ isteği).
 */
private const val PROBE_SETTLE_DELAY_MS = 350L

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
    onDownloadClick: () -> Unit,
    /**
     * Ölçüm (ağ isteği) izni. Liste, kartı YALNIZCA görüş alanındayken true geçer.
     *
     * NEDEN: 60+ akış sonucu olan listelerde her kart compose edilir edilmez ölçüm
     * başlatıyordu. Hızlı kaydırmada onlarca ölçüm aynı anda kuyruğa girip OkHttp
     * havuzunu ve eşzamanlılık kapısını dolduruyor, liste kilitleniyordu.
     */
    probeEnabled: Boolean = true
) {
    // ── Gerçek (ölçülen) akış bilgisi ────────────────────────────────────────
    // Kaynak kalite/dil bildirmediyse tahmin ETMİYORUZ; bunun yerine akışın kendisini
    // ölçüyoruz (HLS master playlist → gerçek çözünürlükler ve ses/altyazı dilleri).
    // Ölçüm yalnızca (1) kart görünürken ve (2) kaynak meta verisi eksikken yapılır.
    val measured by produceState<MeasuredStreamInfo?>(
        initialValue = StreamProbe.cached(source.url),
        key1 = source.url,
        key2 = probeEnabled
    ) {
        if (value == null && probeEnabled && StreamProbe.isProbeNeeded(source)) {
            // Yerleşme gecikmesi: hızlı kaydırmada kart görüş alanından çıkar ve coroutine
            // iptal edilir — hiç ağ isteği başlatılmamış olur (boşa ölçüm = kasma + veri).
            delay(PROBE_SETTLE_DELAY_MS)
            value = runCatching { StreamProbe.probe(source) }.getOrNull()
        }
    }

    val qualityInfo = remember(source, measured) { resolveStreamQuality(source, measured) }
    val langInfo = remember(source, measured) { resolveStreamLangInfo(source, measured) }
    val size = remember(source, measured) { resolveStreamSizeLabel(source, measured) }
    val cacheState = remember(source) { getCacheState(source) }

    var expanded by androidx.compose.runtime.saveable.rememberSaveable(source.url, source.name) { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = KitsugiColors.Surface)
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(source.name, color = KitsugiColors.TextPrimary,
                        style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(source.addonName, color = accentColor,
                        style = MaterialTheme.typography.labelSmall, maxLines = 1,
                        overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = onClick, modifier = Modifier.size(48.dp)
                    .clip(RoundedCornerShape(16.dp)).background(accentColor.copy(alpha = 0.14f))) {
                    Icon(Icons.Rounded.PlayArrow, stringResource(R.string.oynat), tint = accentColor)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f).clickable { expanded = !expanded }.padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    StreamBadge(qualityInfo.label ?: if (qualityInfo.isAdaptive) "HLS" else "—",
                        accentColor, 0.12f, accentColor,
                        qualityInfo.origin == StreamInfoOrigin.MEASURED)
                    Text(when (langInfo.kind) {
                        StreamAudioKind.DUB -> stringResource(R.string.stream_lang_dub)
                        StreamAudioKind.SUB -> stringResource(R.string.stream_lang_sub)
                        StreamAudioKind.DUAL -> stringResource(R.string.stream_lang_dual)
                        else -> stringResource(R.string.stream_details)
                    }, color = KitsugiColors.TextSecondary, style = MaterialTheme.typography.labelSmall,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    Icon(if (expanded) androidx.compose.material.icons.Icons.Rounded.ExpandLess
                        else androidx.compose.material.icons.Icons.Rounded.ExpandMore,
                        stringResource(R.string.stream_details), tint = KitsugiColors.TextMuted,
                        modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onDownloadClick, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.Download, stringResource(R.string.indir), tint = KitsugiColors.TextSecondary)
                }
            }
            androidx.compose.animation.AnimatedVisibility(visible = expanded) {
                Column(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(getCleanVideoSourceLabel(source), color = KitsugiColors.TextSecondary,
                        style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (langInfo.audioLanguages.isNotEmpty()) StreamBadge(
                            "Audio " + langInfo.audioLanguages.joinToString("/"), accentColor, 0.1f, accentColor, true)
                        if (langInfo.subtitleLanguages.isNotEmpty()) StreamBadge(
                            "CC " + langInfo.subtitleLanguages.joinToString("/"), accentColor, 0.1f, accentColor, true)
                        if (!size.isNullOrBlank()) StreamBadge(size, KitsugiColors.TextSecondary, 0.1f, accentColor)
                        if (source.isTorrent) StreamBadge(when(cacheState) {
                            DebridCacheState.CACHED -> stringResource(R.string.stream_cached)
                            DebridCacheState.NOT_CACHED -> "Debrid"
                            DebridCacheState.P2P -> "P2P"
                        }, accentColor, 0.1f, accentColor)
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


