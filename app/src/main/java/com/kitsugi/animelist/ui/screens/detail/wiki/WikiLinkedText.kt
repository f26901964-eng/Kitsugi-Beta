package com.kitsugi.animelist.ui.screens.detail.wiki

import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import com.kitsugi.animelist.data.fandom.FandomTextBlock
import com.kitsugi.animelist.ui.theme.KitsugiColors

/**
 * Wiki metin bloğunu, içindeki GERÇEK bağlantılar tıklanabilecek şekilde çizer.
 *
 * Neden gerekli: bölüm gövdesi düz metne dönüştürülürken `<a>` hedefleri kayboluyordu;
 * kullanıcı yalnızca bölüm sonundaki çiplerden (en fazla 12) başka sayfaya gidebiliyordu.
 * Artık [FandomTextBlock.linkSpans] içindeki her bağlantı metin içinde tıklanabilir.
 *
 * Çeviri metni verildiğinde bağlantı konumları geçerli değildir (metin tamamen değişir);
 * bu durumda düz metin çizilir.
 */
@Composable
internal fun WikiLinkedText(
    block: FandomTextBlock,
    accentColor: Color,
    onOpenLink: (String) -> Unit,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = KitsugiColors.TextPrimary,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    prefix: String? = null,
    suffix: String? = null
) {
    val spans = block.linkSpans
    if (spans.isEmpty()) {
        Text(
            text = buildString {
                prefix?.let { append(it) }
                append(block.text)
                suffix?.let { append(it) }
            },
            color = color,
            style = style,
            fontStyle = fontStyle,
            fontWeight = fontWeight,
            modifier = modifier
        )
        return
    }
    val annotated = buildAnnotatedString {
        val offset = if (prefix != null) {
            append(prefix)
            length
        } else {
            0
        }
        append(block.text)
        suffix?.let { append(it) }
        addStyle(SpanStyle(color = color), 0, length)
        fontStyle?.let { addStyle(SpanStyle(fontStyle = it), 0, length) }
        fontWeight?.let { addStyle(SpanStyle(fontWeight = it), 0, length) }
        for (span in spans) {
            val start = offset + span.start
            val end = offset + span.end
            if (start < 0 || end > length || start >= end) continue
            addStyle(
                SpanStyle(color = accentColor, textDecoration = TextDecoration.Underline),
                start,
                end
            )
            addStringAnnotation(
                tag = WIKI_LINK_TAG,
                annotation = span.title,
                start = start,
                end = end
            )
        }
    }
    ClickableText(
        text = annotated,
        style = style,
        modifier = modifier,
        onClick = { position ->
            annotated.getStringAnnotations(tag = WIKI_LINK_TAG, start = position, end = position)
                .firstOrNull()
                ?.let { annotation -> onOpenLink(annotation.item) }
        }
    )
}

private const val WIKI_LINK_TAG = "kitsugi-wiki-link"
