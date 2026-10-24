package com.kitsugi.animelist.ui.screens.detail

import com.kitsugi.animelist.ui.utils.localizedLabel
import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.utils.tvClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import com.kitsugi.animelist.ui.theme.gradient.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.kitsugi.animelist.data.remote.KitsugiRelation
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.components.KitsugiNsfwImage
import com.kitsugi.animelist.utils.PreferenceHelpers.getDisplayTitle

import com.kitsugi.animelist.ui.components.GalleryThumbnailImagePolicy
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import coil3.request.ImageRequest
import coil3.request.crossfade
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement

@Composable
fun RelationsTabContent(
    state: DetailTabState<List<KitsugiRelation>>,
    titleLanguage: String = "ROMAJI",
    blurAdultMedia: Boolean = false,
    onRelationClick: (KitsugiRelation) -> Unit
) {
    when (state) {
        is DetailTabState.Loading -> {
            DetailRelationsLoadingSkeleton(itemCount = 3)
        }
        is DetailTabState.Error -> {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.inline_an_error_occurred_while_loading_2c6d77b),
                    color = KitsugiColors.AccentRed,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
                DetailTabErrorCodeChip(platformErrorCode = state.platformErrorCode)
            }
        }
        is DetailTabState.Success -> {
            // +18 UYUMU (2026-10-14): ilişkili başlıklar da göster/gizle sözleşmesine uyar.
            val showAdult = com.kitsugi.animelist.ui.theme.LocalShowAdultContent.current
            val list = remember(state.data, showAdult) {
                if (showAdult) state.data else state.data.filter { !it.isAdult }
            }
            if (list.isEmpty()) {
                Text(
                    text = stringResource(R.string.inline_no_related_titles_found_736beb1),
                    color = KitsugiColors.TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            } else {
                var selectedChunk by remember(list.size) { mutableIntStateOf(0) }
                val chunkCount = (list.size + DETAIL_TAB_PAGE_CHUNK_SIZE - 1) / DETAIL_TAB_PAGE_CHUNK_SIZE
                val safeChunk = selectedChunk.coerceIn(0, (chunkCount - 1).coerceAtLeast(0))
                val visibleList = remember(list, safeChunk) {
                    val start = safeChunk * DETAIL_TAB_PAGE_CHUNK_SIZE
                    val end = minOf(start + DETAIL_TAB_PAGE_CHUNK_SIZE, list.size)
                    if (start < list.size) list.subList(start, end) else emptyList()
                }
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DetailChunkSelectorRow(
                        totalCount = list.size,
                        selectedChunk = safeChunk,
                        onSelectChunk = { selectedChunk = it }
                    )
                    visibleList.forEachIndexed { idx, rel ->
                        key("${rel.source}_${rel.malId}_${rel.relationType}_${safeChunk}_$idx") {
                            RelationCard(rel, titleLanguage, blurAdultMedia, onRelationClick)
                        }
                    }
                    DetailChunkSelectorRow(
                        totalCount = list.size,
                        selectedChunk = safeChunk,
                        onSelectChunk = { selectedChunk = it }
                    )
                }
            }
        }
    }
}

@Composable
fun RelationCard(
    rel: KitsugiRelation,
    titleLanguage: String = "ROMAJI",
    blurAdultMedia: Boolean = false,
    onClick: (KitsugiRelation) -> Unit
) {
    val accentColor = LocalKitsugiAccent.current
    val displayTitle = rel.getDisplayTitle(titleLanguage)
    val context = androidx.compose.ui.platform.LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(KitsugiColors.Surface)
            .tvClickable(shape = RoundedCornerShape(16.dp), onClick = { onClick(rel) })
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(width = 50.dp, height = 70.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(KitsugiColors.SurfaceSoft)
        ) {
            val relThumb = GalleryThumbnailImagePolicy.rememberThumbnailImageRequest(
                url = rel.imageUrl,
                widthDp = 50.dp,
                heightDp = 70.dp
            )
            KitsugiNsfwImage(
                model = relThumb ?: rel.imageUrl,
                contentDescription = displayTitle,
                isAdult = rel.isAdult,
                blurAdultMedia = blurAdultMedia,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                initials = displayTitle,
                initialsColor = KitsugiColors.TextMuted,
                initialsStyle = MaterialTheme.typography.titleSmall
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = displayTitle,
                color = KitsugiColors.TextPrimary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            val typeLabel = rel.mediaType.localizedLabel()
            Text(
                text = "${rel.relationType} • $typeLabel",
                color = accentColor,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
