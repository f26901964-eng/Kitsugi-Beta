package com.kitsugi.animelist.ui.screens.detail.wiki

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.R
import com.kitsugi.animelist.data.fandom.FandomPageProfile
import com.kitsugi.animelist.data.fandom.FandomProfileField
import com.kitsugi.animelist.data.fandom.FandomRelatedPage
import com.kitsugi.animelist.data.fandom.FandomWikiLink
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.gradient.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.OpenInNew

/**
 * Related-article preview. It shows the intro and every available infobox value
 * inline; opening the full article remains an optional secondary action.
 */
@Composable
internal fun WikiRelatedPageDetailCard(
    page: FandomRelatedPage,
    accentColor: Color,
    spoilerGuard: Boolean = true,
    modifier: Modifier = Modifier,
    onOpenPage: () -> Unit,
    onOpenImage: ((String) -> Unit)? = null
) {
    val imageUrl = page.thumbnailUrl?.takeIf { it.isNotBlank() }
        ?: page.profile?.images?.firstOrNull()?.url
    WikiInlineProfileCard(
        title = page.profile?.title?.takeIf { it.isNotBlank() } ?: page.title,
        imageUrl = imageUrl,
        intro = page.profile?.intro?.takeIf { it.isNotBlank() } ?: page.snippet,
        profile = page.profile,
        context = null,
        accentColor = accentColor,
        spoilerGuard = spoilerGuard,
        onOpenPage = onOpenPage,
        onOpenImage = onOpenImage,
        modifier = modifier
    )
}

@Composable
internal fun WikiRelatedEntityDetailCard(
    entity: FandomWikiLink,
    accentColor: Color,
    spoilerGuard: Boolean,
    modifier: Modifier = Modifier,
    onOpenPage: () -> Unit,
    onOpenImage: ((String) -> Unit)? = null
) {
    val imageUrl = entity.imageUrl?.takeIf { it.isNotBlank() }
        ?: entity.profile?.images?.firstOrNull()?.url
    WikiInlineProfileCard(
        title = entity.profile?.title?.takeIf { it.isNotBlank() } ?: entity.label,
        imageUrl = imageUrl,
        intro = entity.profile?.intro?.takeIf { it.isNotBlank() } ?: entity.description,
        profile = entity.profile,
        context = listOfNotNull(entity.context, entity.language?.uppercase())
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(" · ")
            .takeIf { it.isNotBlank() },
        accentColor = accentColor,
        spoilerGuard = spoilerGuard,
        onOpenPage = onOpenPage,
        onOpenImage = onOpenImage,
        modifier = modifier
    )
}

@Composable
private fun WikiInlineProfileCard(
    title: String,
    imageUrl: String?,
    intro: String?,
    profile: FandomPageProfile?,
    context: String?,
    accentColor: Color,
    spoilerGuard: Boolean,
    onOpenPage: () -> Unit,
    onOpenImage: ((String) -> Unit)?,
    modifier: Modifier
) {
    val revealed = remember(title, imageUrl) { mutableStateMapOf<Int, Boolean>() }
    val fields = profile?.groups.orEmpty().flatMap { group -> group.fields.map { group.header to it } }
    val hasVisibleDetails = !intro.isNullOrBlank() || fields.isNotEmpty()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(KitsugiColors.SurfaceStrong)
            .border(1.dp, accentColor.copy(alpha = 0.16f), RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            if (!imageUrl.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .size(width = 76.dp, height = 104.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(KitsugiColors.SurfaceSoft)
                        .then(
                            if (onOpenImage != null) Modifier.clickable { onOpenImage(imageUrl) }
                            else Modifier
                        )
                ) {
                    FandomWikiImage(
                        url = imageUrl,
                        width = 240,
                        contentDescription = title,
                        accentColor = accentColor,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().height(104.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    color = KitsugiColors.TextPrimary,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                context?.takeIf { it.isNotBlank() }?.let { relationContext ->
                    Text(
                        text = relationContext,
                        color = accentColor,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                if (!hasVisibleDetails) {
                    Text(
                        text = stringResource(R.string.fandom_related_details_unavailable),
                        color = KitsugiColors.TextMuted,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        if (!intro.isNullOrBlank()) {
            SelectionContainer {
                Text(
                    text = intro,
                    color = KitsugiColors.TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        if (fields.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(KitsugiColors.Surface)
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                var previousHeader: String? = null
                fields.forEachIndexed { index, (header, field) ->
                    if (!header.isNullOrBlank() && header != previousHeader) {
                        Text(
                            text = profileHeader(header),
                            color = KitsugiColors.TextMuted,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = if (index == 0) 0.dp else 6.dp)
                        )
                    }
                    previousHeader = header
                    val hidden = spoilerGuard && field.spoiler && revealed[index] != true
                    ProfileFieldRow(
                        field = field,
                        hidden = hidden,
                        onReveal = { revealed[index] = true }
                    )
                }
            }
        }

        TextButton(
            onClick = onOpenPage,
            modifier = Modifier.align(Alignment.End)
        ) {
            Icon(
                imageVector = Icons.Rounded.OpenInNew,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.fandom_open_related_page),
                color = accentColor,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun ProfileFieldRow(
    field: FandomProfileField,
    hidden: Boolean,
    onReveal: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = profileLabel(field),
            color = KitsugiColors.TextSecondary,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .weight(0.38f)
                .padding(end = 8.dp)
        )
        if (hidden) {
            Box(
                modifier = Modifier
                    .weight(0.62f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(KitsugiColors.SurfaceStrong)
                    .clickable(onClick = onReveal)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = stringResource(R.string.fandom_spoiler_hidden),
                    color = KitsugiColors.TextMuted,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        } else {
            SelectionContainer(modifier = Modifier.weight(0.62f)) {
                Text(
                    text = profileValue(field.value),
                    color = KitsugiColors.TextPrimary,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun profileLabel(field: FandomProfileField): String {
    val resource = FandomInfoboxLabels.labelResId(field.label, field.source)
    return if (resource != null) stringResource(resource) else field.label
}

@Composable
private fun profileHeader(header: String): String {
    val resource = FandomInfoboxLabels.headerResId(header)
    return if (resource != null) stringResource(resource) else header
}

@Composable
private fun profileValue(value: String): String {
    val resource = FandomInfoboxLabels.valueResId(value)
    return if (resource != null) stringResource(resource) else value
}
