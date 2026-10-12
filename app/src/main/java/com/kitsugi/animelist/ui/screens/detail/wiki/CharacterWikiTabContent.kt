package com.kitsugi.animelist.ui.screens.detail.wiki

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.kitsugi.animelist.R
import com.kitsugi.animelist.data.fandom.FandomBlockKind
import com.kitsugi.animelist.data.fandom.FandomCharacterPage
import com.kitsugi.animelist.data.fandom.FandomConfidence
import com.kitsugi.animelist.data.fandom.FandomInfobox
import com.kitsugi.animelist.data.fandom.FandomInfoboxField
import com.kitsugi.animelist.data.fandom.FandomSectionContent
import com.kitsugi.animelist.data.fandom.FandomSectionRef
import com.kitsugi.animelist.data.fandom.FandomTextBlock
import com.kitsugi.animelist.data.fandom.FandomUrls
import com.kitsugi.animelist.data.remote.GalleryCategory
import com.kitsugi.animelist.data.remote.GalleryItem
import com.kitsugi.animelist.ui.components.KitsugiTranslateIconButton
import com.kitsugi.animelist.ui.screens.detail.wiki.FandomCharacterWikiViewModel.Companion.KEY_DESCRIPTION
import com.kitsugi.animelist.ui.screens.detail.wiki.FandomCharacterWikiViewModel.Companion.sectionKey
import com.kitsugi.animelist.ui.screens.detail.wiki.FandomCharacterWikiViewModel.SectionState
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.Text
import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.utils.tvClickable

private const val COLLAPSED_FIELD_COUNT = 8
private const val FANDOM_GALLERY_SOURCE = "Fandom"
private val PARAGRAPH_SPLIT = Regex("\\n\\s*\\n")

/**
 * Karakter detayındaki "Wiki" sekmesi.
 *
 * Bilgi kutusu (spoiler alanları dokunana kadar gizli), kısa özet, açılır bölümler
 * (spoiler bölümleri uyarıyla, metin açıldıkça yüklenir), kaynak/lisans satırı ve
 * "Yanlışsa bildir" bağlantısı. Dikey düzende LazyColumn öğesi, yatay/TV düzeninde
 * kaydırılabilir Column içinde yer aldığı için kendi kaydırması yoktur.
 */
@Composable
internal fun CharacterWikiTabContent(
    page: FandomCharacterPage,
    sections: Map<Int, SectionState>,
    translations: Map<String, String>,
    translatingKeys: Set<String>,
    spoilerGuard: Boolean,
    accentColor: Color,
    onLoadSection: (Int) -> Unit,
    onTranslate: (String) -> Unit,
    onShowOriginal: (String) -> Unit,
    onOpenGallery: (List<GalleryItem>, Int) -> Unit,
    onConfirm: () -> Unit,
    onReportWrong: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pageKey = page.wikiId + "|" + page.pageTitle
    val expanded = remember(pageKey) { mutableStateMapOf<Int, Boolean>() }
    val revealed = remember(pageKey) { mutableStateMapOf<String, Boolean>() }
    var showAllFields by remember(pageKey) { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (page.needsConfirmation) {
            WikiConfirmCard(accentColor = accentColor, onConfirm = onConfirm, onReject = onReportWrong)
        }

        WikiInfoboxCard(
            page = page,
            infobox = page.infobox,
            spoilerGuard = spoilerGuard,
            revealed = revealed,
            showAll = showAllFields,
            onToggleShowAll = { showAllFields = !showAllFields },
            accentColor = accentColor,
            onOpenGallery = onOpenGallery
        )

        page.description?.let { description ->
            WikiCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.fandom_summary_title),
                        color = KitsugiColors.TextPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    TranslateToggle(
                        key = KEY_DESCRIPTION,
                        isTranslated = translations[KEY_DESCRIPTION] != null,
                        isLoading = KEY_DESCRIPTION in translatingKeys,
                        accentColor = accentColor,
                        onTranslate = onTranslate,
                        onShowOriginal = onShowOriginal
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                SelectionContainer {
                    Text(
                        text = translations[KEY_DESCRIPTION] ?: description,
                        color = KitsugiColors.TextPrimary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        val topLevel = page.sections.minOfOrNull { it.level }
        val visibleSections = page.sections.filter { it.level == topLevel }
        if (visibleSections.isNotEmpty()) {
            WikiCard {
                Text(
                    text = stringResource(R.string.fandom_contents_title),
                    color = KitsugiColors.TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                visibleSections.forEach { section ->
                    val isExpanded = expanded[section.index] == true
                    val revealKey = "section:${section.index}"
                    val key = sectionKey(section.index)
                    WikiSectionRow(
                        section = section,
                        expanded = isExpanded,
                        gated = spoilerGuard && section.spoiler && revealed[revealKey] != true,
                        state = sections[section.index],
                        translated = translations[key],
                        translating = key in translatingKeys,
                        accentColor = accentColor,
                        onToggle = { expanded[section.index] = !isExpanded },
                        onReveal = { revealed[revealKey] = true },
                        onLoad = { onLoadSection(section.index) },
                        onTranslate = { onTranslate(key) },
                        onShowOriginal = { onShowOriginal(key) }
                    )
                }
            }
        }

        WikiFooter(page = page, accentColor = accentColor, onReportWrong = onReportWrong)
    }
}

/** Otomatik (kesinleşmemiş) eşleşmede bir kerelik onay kartı. */
@Composable
private fun WikiConfirmCard(accentColor: Color, onConfirm: () -> Unit, onReject: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(accentColor.copy(alpha = 0.12f))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = stringResource(R.string.fandom_confirm_title),
            color = KitsugiColors.TextPrimary,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = stringResource(R.string.fandom_confirm_desc),
            color = KitsugiColors.TextSecondary,
            style = MaterialTheme.typography.bodySmall
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onReject) {
                Text(
                    text = stringResource(R.string.fandom_confirm_no),
                    color = KitsugiColors.TextSecondary,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.fandom_confirm_yes),
                    color = accentColor,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun WikiCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(KitsugiColors.Surface)
            .padding(16.dp),
        content = content
    )
}

@Composable
private fun WikiInfoboxCard(
    page: FandomCharacterPage,
    infobox: FandomInfobox?,
    spoilerGuard: Boolean,
    revealed: MutableMap<String, Boolean>,
    showAll: Boolean,
    onToggleShowAll: () -> Unit,
    accentColor: Color,
    onOpenGallery: (List<GalleryItem>, Int) -> Unit
) {
    val images = infobox?.images.orEmpty()
    val galleryItems = remember(images) {
        images.map { image ->
            GalleryItem(
                url = image.url,
                source = FANDOM_GALLERY_SOURCE,
                category = GalleryCategory.CHARACTER,
                description = image.caption
            )
        }
    }
    val fields = remember(infobox) {
        infobox?.groups.orEmpty().flatMap { group -> group.fields.map { field -> group.header to field } }
    }
    val visibleCount = if (showAll) fields.size else minOf(fields.size, COLLAPSED_FIELD_COUNT)

    WikiCard {
        Row(verticalAlignment = Alignment.Top) {
            images.firstOrNull()?.let { image ->
                AsyncImage(
                    model = FandomUrls.scaledImage(image.url, 360),
                    contentDescription = infobox?.title ?: page.pageTitle,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .width(92.dp)
                        .height(126.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(KitsugiColors.SurfaceStrong)
                        .tvClickable(shape = RoundedCornerShape(14.dp)) { onOpenGallery(galleryItems, 0) }
                )
                Spacer(modifier = Modifier.width(14.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = infobox?.title ?: page.pageTitle,
                    color = KitsugiColors.TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = page.siteName ?: page.wikiId,
                    color = accentColor,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (images.size > 1) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        images.forEachIndexed { index, image ->
                            if (index == 0) return@forEachIndexed
                            AsyncImage(
                                model = FandomUrls.scaledImage(image.url, 160),
                                contentDescription = image.caption,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(KitsugiColors.SurfaceStrong)
                                    .tvClickable(shape = RoundedCornerShape(10.dp)) { onOpenGallery(galleryItems, index) }
                            )
                        }
                    }
                }
            }
        }

        if (fields.isNotEmpty()) {
            Spacer(modifier = Modifier.height(14.dp))
            var lastHeader: String? = null
            fields.take(visibleCount).forEachIndexed { index, (header, field) ->
                if (header != null && header != lastHeader) {
                    Text(
                        text = header,
                        color = KitsugiColors.TextMuted,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = if (index == 0) 0.dp else 10.dp, bottom = 6.dp)
                    )
                }
                lastHeader = header
                val revealKey = "field:$index"
                WikiFieldRow(
                    field = field,
                    hidden = spoilerGuard && field.spoiler && revealed[revealKey] != true,
                    onReveal = { revealed[revealKey] = true }
                )
                Spacer(modifier = Modifier.height(6.dp))
            }
            if (fields.size > COLLAPSED_FIELD_COUNT) {
                TextButton(onClick = onToggleShowAll) {
                    Text(
                        text = stringResource(if (showAll) R.string.fandom_show_less else R.string.fandom_show_all_fields),
                        color = accentColor,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun WikiFieldRow(field: FandomInfoboxField, hidden: Boolean, onReveal: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = field.label,
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
                    .tvClickable(shape = RoundedCornerShape(8.dp)) { onReveal() }
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
                    text = field.value,
                    color = KitsugiColors.TextPrimary,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun TranslateToggle(
    key: String,
    isTranslated: Boolean,
    isLoading: Boolean,
    accentColor: Color,
    onTranslate: (String) -> Unit,
    onShowOriginal: (String) -> Unit
) {
    if (isTranslated) {
        TextButton(onClick = { onShowOriginal(key) }) {
            Text(
                text = stringResource(R.string.fandom_show_original),
                color = accentColor,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
        }
    } else {
        KitsugiTranslateIconButton(
            onClick = { onTranslate(key) },
            modifier = Modifier.size(36.dp),
            isLoading = isLoading,
            tint = accentColor,
            contentDescription = stringResource(R.string.cevir),
            ringSize = 30.dp
        )
    }
}

@Composable
private fun WikiSectionRow(
    section: FandomSectionRef,
    expanded: Boolean,
    gated: Boolean,
    state: SectionState?,
    translated: String?,
    translating: Boolean,
    accentColor: Color,
    onToggle: () -> Unit,
    onReveal: () -> Unit,
    onLoad: () -> Unit,
    onTranslate: () -> Unit,
    onShowOriginal: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .tvClickable(shape = RoundedCornerShape(12.dp)) { onToggle() }
                .padding(vertical = 10.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = localizedSectionTitle(section.title),
                color = KitsugiColors.TextPrimary,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            if (section.spoiler) {
                Text(
                    text = stringResource(R.string.fandom_spoiler_badge),
                    color = KitsugiColors.AccentOrange,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 30.dp, end = 4.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (gated) {
                    SpoilerGate(accentColor = accentColor, onReveal = onReveal)
                } else {
                    LaunchedEffect(section.index) {
                        if (state == null) onLoad()
                    }
                    when (state) {
                        null, is SectionState.Loading -> LoadingLine(accentColor)
                        is SectionState.Failed -> Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stringResource(R.string.fandom_section_error),
                                color = KitsugiColors.TextMuted,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = onLoad) {
                                Text(
                                    text = stringResource(R.string.action_retry_label),
                                    color = accentColor,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        is SectionState.Loaded -> {
                            if (state.content.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.fandom_section_empty),
                                    color = KitsugiColors.TextMuted,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    TranslateToggle(
                                        key = "",
                                        isTranslated = translated != null,
                                        isLoading = translating,
                                        accentColor = accentColor,
                                        onTranslate = { onTranslate() },
                                        onShowOriginal = { onShowOriginal() }
                                    )
                                }
                                SectionBody(content = state.content, translated = translated)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionBody(content: FandomSectionContent, translated: String?) {
    SelectionContainer {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (translated != null) {
                translated.split(PARAGRAPH_SPLIT)
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .forEach { paragraph ->
                        Text(
                            text = paragraph,
                            color = KitsugiColors.TextPrimary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
            } else {
                content.blocks.forEach { block -> WikiBlock(block) }
            }
        }
    }
}

@Composable
private fun WikiBlock(block: FandomTextBlock) {
    when (block.kind) {
        FandomBlockKind.HEADING -> Text(
            text = localizedSectionTitle(block.text),
            color = KitsugiColors.TextPrimary,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 4.dp)
        )
        FandomBlockKind.PARAGRAPH -> Text(
            text = block.text,
            color = KitsugiColors.TextPrimary,
            style = MaterialTheme.typography.bodyMedium
        )
        FandomBlockKind.BULLET -> Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "•",
                color = KitsugiColors.TextSecondary,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(end = 8.dp)
            )
            Text(
                text = block.text,
                color = KitsugiColors.TextPrimary,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
        }
        FandomBlockKind.QUOTE -> Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(KitsugiColors.SurfaceSoft)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                text = block.text,
                color = KitsugiColors.TextSecondary,
                style = MaterialTheme.typography.bodyMedium,
                fontStyle = FontStyle.Italic
            )
        }
    }
}

@Composable
private fun SpoilerGate(accentColor: Color, onReveal: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(KitsugiColors.SurfaceStrong)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.Warning,
                contentDescription = null,
                tint = KitsugiColors.AccentOrange,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.fandom_spoiler_section_warning),
                color = KitsugiColors.TextSecondary,
                style = MaterialTheme.typography.bodySmall
            )
        }
        TextButton(onClick = onReveal) {
            Text(
                text = stringResource(R.string.fandom_spoiler_reveal),
                color = accentColor,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun LoadingLine(accentColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            color = accentColor,
            strokeWidth = 2.dp
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.fandom_section_loading),
            color = KitsugiColors.TextMuted,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun WikiFooter(page: FandomCharacterPage, accentColor: Color, onReportWrong: () -> Unit) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.fandom_source_label, page.siteName ?: page.wikiId),
                color = KitsugiColors.TextSecondary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = { openExternal(context, page.pageUrl) }) {
                Icon(
                    imageVector = Icons.Rounded.OpenInNew,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.fandom_open_in_browser),
                    color = accentColor,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Text(
            text = stringResource(R.string.fandom_license_note),
            color = KitsugiColors.TextMuted,
            style = MaterialTheme.typography.labelSmall
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Kullanıcı onayladıysa (CERTAIN) etiket gösterilmez; "Yanlışsa bildir" her zaman açıktır.
            val automatic = page.confidence == FandomConfidence.PROBABLE
            if (automatic) {
                Text(
                    text = stringResource(R.string.fandom_auto_match),
                    color = KitsugiColors.TextMuted,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.weight(1f)
                )
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
            TextButton(onClick = onReportWrong) {
                Text(
                    text = stringResource(R.string.fandom_report_wrong),
                    color = KitsugiColors.TextMuted,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

/** İngilizce wiki bölüm başlıklarını arayüz diline çevirir; bilinmeyenler aynen kalır. */
@Composable
private fun localizedSectionTitle(raw: String): String {
    val resource = FandomSectionTitles.resId(raw)
    return if (resource != null) stringResource(resource) else raw
}

private fun openExternal(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        Toast.makeText(context, context.getString(R.string.fandom_open_failed), Toast.LENGTH_SHORT).show()
    }
}
