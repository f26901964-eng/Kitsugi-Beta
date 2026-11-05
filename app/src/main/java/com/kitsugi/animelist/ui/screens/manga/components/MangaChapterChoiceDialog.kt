package com.kitsugi.animelist.ui.screens.manga.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.kitsugi.animelist.data.manga.ChapterChoiceReason
import com.kitsugi.animelist.data.manga.ChapterChoiceRequest
import com.kitsugi.animelist.data.manga.ChapterVariantInfo
import com.kitsugi.animelist.data.manga.MangaChapter
import com.kitsugi.animelist.ui.components.KitsugiButton
import com.kitsugi.animelist.ui.components.KitsugiSheetOrDialog
import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.Switch
import com.kitsugi.animelist.ui.theme.gradient.Text
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val ChoiceAccent = Color(0xFF7C4DFF)

/**
 * "Aynı bölümün birden çok çevirmeni var" seçim penceresi.
 *
 * Neden var?
 * ----------
 * Kaynaklar aynı bölümü birden fazla çevirmen (AnimeWho, WebtoonTR,
 * Tempest Fansub…) üzerinden sunuyor. Okuyucuda "sonraki bölüm"e basıldığında
 * hangi çevirmenle devam edileceği belirsizdi; kullanıcı farkında olmadan
 * başka bir çevirmene atlıyordu. Bu pencere:
 *
 *  • her çevirmenin adını, bölüm adını, yükleme tarihini ve **kaldığı sayfayı**
 *    gösterir,
 *  • hatırlanan çevirmeni ön seçili getirir,
 *  • "Bu çevirmeni hatırla" ve "Bir daha sorma" anahtarlarıyla kalıcı tercih sunar.
 */
@Composable
fun MangaChapterChoiceDialog(
    request: ChapterChoiceRequest,
    onConfirm: (chapter: MangaChapter, remember: Boolean, autoContinue: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val preselected = request.preselected
    var selectedUrl by remember(request.groupKey) {
        mutableStateOf(preselected?.chapter?.url)
    }
    var rememberScanlator by remember(request.groupKey) { mutableStateOf(true) }
    var autoContinue by remember(request.groupKey) { mutableStateOf(false) }

    val selected = request.variants.firstOrNull { it.chapter.url == selectedUrl } ?: preselected

    KitsugiSheetOrDialog(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = stringResource(R.string.manga_choice_title),
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(
                    R.string.manga_choice_subtitle,
                    request.chapterName,
                    request.variants.size,
                ),
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 12.sp,
            )

            if (request.reason != ChapterChoiceReason.Manual) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (request.isForward) {
                        stringResource(R.string.manga_choice_reason_next, request.chapterName)
                    } else {
                        stringResource(R.string.manga_choice_reason_previous, request.chapterName)
                    },
                    color = ChoiceAccent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
            }

            Spacer(Modifier.height(14.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                request.variants.forEach { variant ->
                    VariantCard(
                        variant = variant,
                        selected = variant.chapter.url == selected?.chapter?.url,
                        isPreferred = isPreferred(request, variant),
                        onSelect = { selectedUrl = variant.chapter.url },
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            ToggleLine(
                title = stringResource(R.string.manga_choice_remember),
                description = stringResource(R.string.manga_choice_remember_desc),
                checked = rememberScanlator || autoContinue,
                enabled = !autoContinue,
                onCheckedChange = { rememberScanlator = it },
            )
            ToggleLine(
                title = stringResource(R.string.manga_choice_auto),
                description = stringResource(R.string.manga_choice_auto_desc),
                checked = autoContinue,
                enabled = true,
                onCheckedChange = { autoContinue = it },
            )

            Spacer(Modifier.height(8.dp))

            KitsugiButton(
                onClick = {
                    selected?.let { chosen ->
                        onConfirm(chosen.chapter, rememberScanlator || autoContinue, autoContinue)
                    }
                },
                enabled = selected != null,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ChoiceAccent,
                    contentColor = Color.White,
                ),
            ) {
                Text(stringResource(R.string.manga_choice_continue))
            }

            Spacer(Modifier.height(4.dp))

            KitsugiButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF26263B),
                    contentColor = Color.White.copy(alpha = 0.85f),
                ),
            ) {
                Text(stringResource(R.string.manga_choice_cancel))
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}

private fun isPreferred(request: ChapterChoiceRequest, variant: ChapterVariantInfo): Boolean {
    val preferred = request.preferredScanlator?.trim()?.lowercase().orEmpty()
    if (preferred.isEmpty()) return false
    return variant.scanlator?.trim()?.lowercase() == preferred
}

@Composable
private fun VariantCard(
    variant: ChapterVariantInfo,
    selected: Boolean,
    isPreferred: Boolean,
    onSelect: () -> Unit,
) {
    val borderColor = if (selected) ChoiceAccent.copy(alpha = 0.75f) else Color.White.copy(alpha = 0.10f)
    val backgroundColor = if (selected) ChoiceAccent.copy(alpha = 0.16f) else Color(0xFF1B1B28)
    val shape = RoundedCornerShape(12.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(backgroundColor)
            .border(1.dp, borderColor, shape)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (selected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (selected) ChoiceAccent else Color.White.copy(alpha = 0.35f),
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = variant.scanlator ?: stringResource(R.string.manga_choice_unknown_scanlator),
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (isPreferred) {
                    Spacer(Modifier.width(6.dp))
                    Badge(text = stringResource(R.string.manga_choice_preferred), color = ChoiceAccent)
                }
            }

            // Bölüm adı çevirmene göre farklı yazılmış olabilir; göster.
            if (variant.chapter.name.isNotBlank() && variant.chapter.name != variant.scanlator) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = variant.chapter.name,
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            val dateText = formatUploadDate(variant.chapter.uploadDate)
            val progressText = when {
                variant.isCompleted -> stringResource(R.string.okundu)
                variant.hasProgress -> stringResource(
                    R.string.manga_choice_page_progress,
                    variant.lastPageIndex + 1,
                    variant.totalPages,
                )
                else -> null
            }
            if (dateText != null || progressText != null) {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (progressText != null) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(12.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = progressText,
                            color = Color(0xFF4CAF50),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    if (progressText != null && dateText != null) {
                        Text(
                            text = " • ",
                            color = Color.White.copy(alpha = 0.3f),
                            fontSize = 11.sp,
                        )
                    }
                    if (dateText != null) {
                        Text(
                            text = dateText,
                            color = Color.White.copy(alpha = 0.45f),
                            fontSize = 11.sp,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier.size(36.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.manga_choice_select),
                color = if (selected) ChoiceAccent else Color.White.copy(alpha = 0.5f),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun Badge(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.18f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(text = text, color = color, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ToggleLine(
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                color = if (enabled) Color.White else Color.White.copy(alpha = 0.5f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = description,
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 10.sp,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = if (enabled) onCheckedChange else null,
            enabled = enabled,
        )
    }
}

private fun formatUploadDate(raw: Long): String? {
    if (raw <= 0L) return null
    val millis = if (raw > 10_000_000_000L) raw else raw * 1000L
    return runCatching {
        SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(millis))
    }.getOrNull()
}
