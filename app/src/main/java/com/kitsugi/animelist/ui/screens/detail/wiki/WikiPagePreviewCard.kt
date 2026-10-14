package com.kitsugi.animelist.ui.screens.detail.wiki

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.R
import com.kitsugi.animelist.data.fandom.FandomRelatedPage
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.Text
import com.kitsugi.animelist.ui.utils.tvClickable

private val WikiPagePreviewCardShape = RoundedCornerShape(16.dp)
private val WikiPagePreviewImageShape = RoundedCornerShape(10.dp)

/**
 * One consistent preview card for every related page shown in a Wiki tab or reader.
 * Fixed dimensions keep cast/staff/music/related-page rails visually uniform; the
 * whole card opens the page in the in-app reader, while long details stay in that page.
 */
@Composable
internal fun WikiPagePreviewCard(
    title: String,
    description: String?,
    thumbnailUrl: String?,
    accentColor: Color,
    onClick: () -> Unit,
    subtitle: String? = null,
    onThumbnailClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .width(280.dp)
            .height(144.dp)
            .clip(WikiPagePreviewCardShape)
            .background(KitsugiColors.SurfaceStrong)
            .border(1.dp, KitsugiColors.Border, WikiPagePreviewCardShape)
            .tvClickable(shape = WikiPagePreviewCardShape, onClick = onClick)
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(76.dp)
                .fillMaxHeight()
                .clip(WikiPagePreviewImageShape)
                .background(KitsugiColors.SurfaceSoft)
                .then(
                    if (onThumbnailClick != null && !thumbnailUrl.isNullOrBlank()) {
                        Modifier.tvClickable(shape = WikiPagePreviewImageShape, onClick = onThumbnailClick)
                    } else {
                        Modifier
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            if (!thumbnailUrl.isNullOrBlank()) {
                FandomWikiImage(
                    url = thumbnailUrl,
                    width = 240,
                    contentDescription = title,
                    accentColor = accentColor,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    text = title.trim().firstOrNull()?.uppercase() ?: "?",
                    color = accentColor,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                color = KitsugiColors.TextPrimary,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            subtitle?.takeIf { it.isNotBlank() }?.let { value ->
                Text(
                    text = value,
                    color = accentColor,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            description?.takeIf { it.isNotBlank() }?.let { value ->
                Text(
                    text = value,
                    color = KitsugiColors.TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.MenuBook,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = stringResource(R.string.fandom_open_related_page),
                    color = accentColor,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** Fandom article preview card adapter used by related-page rails. */
@Composable
internal fun WikiRelatedPageCard(
    page: FandomRelatedPage,
    accentColor: Color,
    onClick: () -> Unit,
    onImageClick: ((String) -> Unit)? = null
) {
    WikiPagePreviewCard(
        title = page.title,
        description = page.snippet,
        thumbnailUrl = page.thumbnailUrl,
        accentColor = accentColor,
        onClick = onClick,
        onThumbnailClick = page.thumbnailUrl?.let { url -> onImageClick?.let { callback -> { callback(url) } } }
    )
}
