package com.kitsugi.animelist.ui.screens.manga.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kitsugi.animelist.R
import com.kitsugi.animelist.data.manga.ColorFilterType
import com.kitsugi.animelist.data.manga.MangaFitMode
import com.kitsugi.animelist.data.manga.MangaPage
import com.kitsugi.animelist.data.manga.ReadingMode
import com.kitsugi.animelist.data.manga.loader.MangaPageLoaderV2
import com.kitsugi.animelist.ui.components.KitsugiButton
import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.Text
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Sürekli dikey kaydırmalı Webtoon / Manhwa okuma modu.
 *
 * — Sayfalar LazyColumn içinde üst üste dizilir; aralarında 4 dp ayırıcı var.
 * — Her sayfa [MangaPageItem] üzerinden ZoomableAsyncImage ile çizilir.
 * — Webtoon görsellerinin en-boy oranı çok uzun olabilir (1:10+); Telephoto bunu
 *   kiremit (tile) döşeyerek çizer → büyük görsellerde RAM taşması yaşanmaz.
 * — Bölüm sonunda geçiş kartı gösterilir (kapalıysa yalnızca "son bölüm" yazısı).
 *
 * NOT: Sayfa yüklemesi artık tek yerden ([MangaPageItem] içindeki LaunchedEffect)
 * yapılıyor; burada ikinci kez `loader.loadPage` çağrılıyordu (kopya iş).
 */
@Composable
fun MangaWebtoonReader(
    pages: List<MangaPage>,
    listState: LazyListState,
    loader: MangaPageLoaderV2,
    colorFilterType: ColorFilterType = ColorFilterType.Normal,
    fitMode: MangaFitMode = MangaFitMode.FitScreen,
    mirrorPages: Boolean = false,
    tapZonesEnabled: Boolean = true,
    invertTapZones: Boolean = false,
    onCenterTap: () -> Unit = {},
    onLongPress: () -> Unit = {},
    onPageChanged: (Int) -> Unit = {},
    onNextPage: () -> Unit = {},
    onPrevPage: () -> Unit = {},
    hasNextChapter: Boolean = false,
    nextChapterName: String? = null,
    showChapterTransition: Boolean = true,
    onGoToNextChapter: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val reportPageChanged by rememberUpdatedState(onPageChanged)

    // Görünen ilk öğeyi takip et ve üst katmana bildir (ilerleme + prefetch)
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { index -> reportPageChanged(index) }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize().background(Color.Black),
    ) {
        itemsIndexed(
            items = pages,
            key = { _, page -> page.index },
        ) { _, page ->
            MangaPageItem(
                page = page,
                loader = loader,
                readingMode = ReadingMode.Webtoon,
                colorFilterType = colorFilterType,
                fitMode = fitMode,
                mirrorPages = mirrorPages,
                showPageNumber = false,
                tapZonesEnabled = tapZonesEnabled,
                invertTapZones = invertTapZones,
                onCenterTap = onCenterTap,
                onLongPress = onLongPress,
                onNextPage = onNextPage,
                onPrevPage = onPrevPage,
                modifier = Modifier.fillMaxWidth(),
            )

            // Sayfalar arası ince ayırıcı
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(Color(0xFF1A1A1A))
            )
        }

        // ─── Bölüm Sonu Geçiş Kartı ───────────────────────────────────────────
        item(key = "chapter_transition_footer") {
            ChapterTransitionFooter(
                hasNextChapter = hasNextChapter,
                nextChapterName = nextChapterName,
                compact = !showChapterTransition,
                onGoToNextChapter = onGoToNextChapter,
            )
        }
    }
}

@Composable
private fun ChapterTransitionFooter(
    hasNextChapter: Boolean,
    nextChapterName: String?,
    compact: Boolean,
    onGoToNextChapter: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (compact) 120.dp else 220.dp)
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 24.dp),
        ) {
            if (hasNextChapter) {
                Text(
                    text = stringResource(R.string.inline_end_of_episode_928d999),
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(12.dp))
                KitsugiButton(
                    onClick = onGoToNextChapter,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF7C4DFF),
                        contentColor = Color.White,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        text = nextChapterName?.let { stringResource(R.string.manga_reader_next_chapter, it) }
                            ?: stringResource(R.string.inline_next_episode_ab03b91)
                    )
                }
            } else {
                Text(
                    text = stringResource(R.string.inline_you_re_on_the_last_4e9fc31),
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 14.sp,
                )
            }
        }
    }
}
