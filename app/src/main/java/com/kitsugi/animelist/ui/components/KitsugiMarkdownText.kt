package com.kitsugi.animelist.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.utils.KitsugiMarkdownUtils.formatAniListMarkdown
import com.mikepenz.markdown.model.ImageData
import com.mikepenz.markdown.model.ImageTransformer
import com.mikepenz.markdown.coil3.Coil3ImageTransformerImpl
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import kotlinx.coroutines.launch

/**
 * Clickable wrapper over Coil3ImageTransformerImpl that makes inline images and GIFs
 * interactive: tapping opens the Kitsugi image gallery/viewer with full zoom and download support.
 */
private class KitsugiClickableImageTransformer(
    private val allImages: List<String>,
    private val onImageClicked: ((urls: List<String>, index: Int) -> Unit)?
) : ImageTransformer by Coil3ImageTransformerImpl {
    @Composable
    override fun transform(link: String): ImageData {
        val baseData = Coil3ImageTransformerImpl.transform(link)
        if (onImageClicked == null) return baseData

        val clickableModifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable {
                val index = allImages.indexOfFirst {
                    it.equals(link, ignoreCase = true) || it.contains(link) || link.contains(it)
                }.let { if (it >= 0) it else 0 }
                val urls = if (allImages.isNotEmpty()) {
                    if (allImages.any { it.equals(link, ignoreCase = true) }) allImages else listOf(link) + allImages
                } else {
                    listOf(link)
                }
                onImageClicked.invoke(urls, index)
            }

        val modifier = (baseData.modifier ?: Modifier).then(clickableModifier)
        return baseData.copy(modifier = modifier)
    }
}

/**
 * Rich markdown renderer for AniList/MAL bio and comment text.
 *
 * Features:
 *  - Coil3 image transformer: renders images & animated GIFs inline
 *  - Interactive spoiler links → tapped → [InteractiveSpoilerSheet] overlay
 *  - Image links → tapped → gallery opened via [onImageGalleryRequest]
 *  - External links open the system browser
 *  - Styled links (accent color + underline) for visual clarity
 *  - Strikethrough, bold, italic, blockquote, code block support
 *
 * @param text              Raw AniList/MAL markdown or BBCode string.
 * @param modifier          Modifier for the root composable.
 * @param fontSize          Body text font size.
 * @param lineHeight        Body text line height.
 * @param onImageGalleryRequest  Called when an inline image is tapped.
 *                              Receives the ordered URL list and the tapped index.
 */
@Composable
fun KitsugiMarkdownText(
    text: String?,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = LocalTextStyle.current.fontSize,
    lineHeight: TextUnit = LocalTextStyle.current.lineHeight,
    onImageGalleryRequest: ((urls: List<String>, index: Int) -> Unit)? = null,
) {
    val context  = LocalContext.current
    val accent   = LocalKitsugiAccent.current

    // Spoiler sheet state
    var activeSpoiler by remember { mutableStateOf<String?>(null) }

    val rendered = remember(text) { text?.formatAniListMarkdown().orEmpty() }

    val allImageUrls = remember(text, rendered) {
        val fromMarkdown = Regex("""!\[.*?\]\((https?://[^\s)]+)\)""").findAll(rendered).map { it.groupValues[1].trim() }.toList()
        val fromAniList = Regex("""img\d*%*[({\[](https?://[^\s)}\]]+)[)}\]]""").findAll(text.orEmpty()).map { it.groupValues[1].trim() }.toList()
        val fromRaw = Regex("""https?://[^\s<>"'\)]+\.(?:jpe?g|png|gif|webp)(?:\?[^\s<>"'\)]*)?""", RegexOption.IGNORE_CASE).findAll(text.orEmpty()).map { it.value.trim() }.toList()
        (fromMarkdown + fromAniList + fromRaw).distinct()
    }

    val imageTransformer = remember(allImageUrls, onImageGalleryRequest) {
        KitsugiClickableImageTransformer(
            allImages = allImageUrls,
            onImageClicked = onImageGalleryRequest
        )
    }

    // Build our URI handler — routes spoilers to local sheet, images to gallery
    val uriHandler = remember(context, accent, onImageGalleryRequest) {
        KitsugiMarkdownUriHandler(
            context = context,
            onSpoilerClicked = { spoilerText ->
                activeSpoiler = spoilerText
            },
            onImageClicked = { urls, index ->
                onImageGalleryRequest?.invoke(urls, index)
            },
        )
    }

    CompositionLocalProvider(LocalUriHandler provides uriHandler) {
        val parts = remember(rendered) { rendered.split("~~~") }
        if (parts.size <= 1) {
            Markdown(
                content = rendered,
                colors = markdownColor(
                    text             = KitsugiColors.TextPrimary,
                    codeBackground   = KitsugiColors.SurfaceStrong,
                    inlineCodeBackground = KitsugiColors.Surface,
                    dividerColor     = KitsugiColors.Border,
                    tableBackground  = KitsugiColors.Surface,
                ),
                typography = markdownTypography(
                    text = MaterialTheme.typography.bodyMedium.copy(
                        fontSize   = fontSize,
                        lineHeight = lineHeight,
                        color      = KitsugiColors.TextPrimary,
                    ),
                    link = MaterialTheme.typography.bodyMedium.copy(
                        fontSize       = fontSize,
                        lineHeight     = lineHeight,
                        color          = accent,
                        fontWeight     = FontWeight.Medium,
                        textDecoration = TextDecoration.Underline,
                    ),
                    code = TextStyle(
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        fontSize   = (fontSize.value * 0.88f).sp,
                        color      = KitsugiColors.TextSecondary,
                    ),
                    h1 = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color      = KitsugiColors.TextPrimary,
                    ),
                    h2 = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color      = KitsugiColors.TextPrimary,
                    ),
                    h3 = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color      = KitsugiColors.TextPrimary,
                    ),
                    quote = MaterialTheme.typography.bodyMedium.copy(
                        fontSize   = fontSize,
                        color      = KitsugiColors.TextSecondary,
                        fontStyle  = androidx.compose.ui.text.font.FontStyle.Italic,
                    ),
                ),
                imageTransformer = imageTransformer,
                modifier = modifier,
            )
        } else {
            Column(
                modifier = modifier,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val normalTypography = markdownTypography(
                    text = MaterialTheme.typography.bodyMedium.copy(
                        fontSize   = fontSize,
                        lineHeight = lineHeight,
                        color      = KitsugiColors.TextPrimary,
                    ),
                    link = MaterialTheme.typography.bodyMedium.copy(
                        fontSize       = fontSize,
                        lineHeight     = lineHeight,
                        color          = accent,
                        fontWeight     = FontWeight.Medium,
                        textDecoration = TextDecoration.Underline,
                    ),
                    code = TextStyle(
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        fontSize   = (fontSize.value * 0.88f).sp,
                        color      = KitsugiColors.TextSecondary,
                    ),
                    h1 = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color      = KitsugiColors.TextPrimary,
                    ),
                    h2 = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color      = KitsugiColors.TextPrimary,
                    ),
                    h3 = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color      = KitsugiColors.TextPrimary,
                    ),
                    quote = MaterialTheme.typography.bodyMedium.copy(
                        fontSize   = fontSize,
                        color      = KitsugiColors.TextSecondary,
                        fontStyle  = androidx.compose.ui.text.font.FontStyle.Italic,
                    ),
                )

                val centeredTypography = markdownTypography(
                    text = MaterialTheme.typography.bodyMedium.copy(
                        fontSize   = fontSize,
                        lineHeight = lineHeight,
                        color      = KitsugiColors.TextPrimary,
                        textAlign  = androidx.compose.ui.text.style.TextAlign.Center,
                    ),
                    link = MaterialTheme.typography.bodyMedium.copy(
                        fontSize       = fontSize,
                        lineHeight     = lineHeight,
                        color          = accent,
                        fontWeight     = FontWeight.Medium,
                        textDecoration = TextDecoration.Underline,
                        textAlign      = androidx.compose.ui.text.style.TextAlign.Center,
                    ),
                    code = TextStyle(
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        fontSize   = (fontSize.value * 0.88f).sp,
                        color      = KitsugiColors.TextSecondary,
                        textAlign  = androidx.compose.ui.text.style.TextAlign.Center,
                    ),
                    h1 = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color      = KitsugiColors.TextPrimary,
                        textAlign  = androidx.compose.ui.text.style.TextAlign.Center,
                    ),
                    h2 = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color      = KitsugiColors.TextPrimary,
                        textAlign  = androidx.compose.ui.text.style.TextAlign.Center,
                    ),
                    h3 = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color      = KitsugiColors.TextPrimary,
                        textAlign  = androidx.compose.ui.text.style.TextAlign.Center,
                    ),
                    quote = MaterialTheme.typography.bodyMedium.copy(
                        fontSize   = fontSize,
                        color      = KitsugiColors.TextSecondary,
                        fontStyle  = androidx.compose.ui.text.font.FontStyle.Italic,
                        textAlign  = androidx.compose.ui.text.style.TextAlign.Center,
                    ),
                )

                parts.forEachIndexed { index, part ->
                    if (part.isNotBlank()) {
                        val isCentered = index % 2 == 1
                        Markdown(
                            content = part,
                            colors = markdownColor(
                                text             = KitsugiColors.TextPrimary,
                                codeBackground   = KitsugiColors.SurfaceStrong,
                                inlineCodeBackground = KitsugiColors.Surface,
                                dividerColor     = KitsugiColors.Border,
                                tableBackground  = KitsugiColors.Surface,
                            ),
                            typography = if (isCentered) centeredTypography else normalTypography,
                            imageTransformer = imageTransformer,
                            modifier = if (isCentered) Modifier.fillMaxWidth() else Modifier,
                        )
                    }
                }
            }
        }
    }

    // Spoiler overlay sheet
    activeSpoiler?.let { spoilerContent ->
        InteractiveSpoilerSheet(
            spoilerText = spoilerContent,
            onDismiss   = { activeSpoiler = null },
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Spoiler overlay composables
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Full-screen-aware spoiler reveal sheet that renders the spoiler content
 * with the same markdown renderer (so images/links inside spoilers also work).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InteractiveSpoilerSheet(
    spoilerText: String,
    onDismiss: () -> Unit,
) {
    var currentText by remember(spoilerText) { mutableStateOf(spoilerText) }
    var isTranslating by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    KitsugiSheetOrDialog(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            val accent = LocalKitsugiAccent.current
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Visibility,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Spoiler İçerik",
                        color = accent,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }

                IconButton(
                    onClick = {
                        if (!isTranslating) {
                            coroutineScope.launch {
                                isTranslating = true
                                val translationManager = com.kitsugi.animelist.data.local.TranslationManager(context)
                                val translated = translationManager.translate(currentText)
                                if (translated.isNotBlank()) {
                                    currentText = translated
                                }
                                isTranslating = false
                            }
                        }
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    if (isTranslating) {
                        CircularProgressIndicator(
                            color = accent,
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Translate,
                            contentDescription = "Çevir",
                            tint = accent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
            HorizontalDivider(color = KitsugiColors.Border)
            Spacer(Modifier.height(16.dp))
            // Render spoiler content itself with full markdown support
            KitsugiMarkdownText(
                text = currentText,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * Inline spoiler box — kept for backwards-compat with any code that still
 * instantiates it directly. New code should prefer [InteractiveSpoilerSheet].
 */
@Composable
fun InteractiveSpoilerBox(
    spoilerText: String,
    modifier: Modifier = Modifier,
) {
    var isRevealed by remember { mutableStateOf(false) }
    val accentColor = LocalKitsugiAccent.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(KitsugiColors.Surface)
            .border(
                width = 1.dp,
                color = if (isRevealed) accentColor.copy(alpha = 0.5f)
                        else KitsugiColors.AccentOrange.copy(alpha = 0.4f),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable { isRevealed = !isRevealed }
            .padding(14.dp)
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (isRevealed) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                    contentDescription = null,
                    tint = if (isRevealed) accentColor else KitsugiColors.AccentOrange,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = if (isRevealed) "Spoiler (Gizlemek için dokunun)"
                           else "⚠️ Spoiler İçerik (Görmek için dokunun)",
                    color = if (isRevealed) accentColor else KitsugiColors.AccentOrange,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (isRevealed) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = spoilerText,
                    color = KitsugiColors.TextPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = 20.sp,
                )
            }
        }
    }
}
