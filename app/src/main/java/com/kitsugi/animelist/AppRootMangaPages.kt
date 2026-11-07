package com.kitsugi.animelist

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.kitsugi.animelist.data.manga.stableSourceKey
import com.kitsugi.animelist.ui.app.AppNavigationState
import com.kitsugi.animelist.ui.app.MangaViewModel
import com.kitsugi.animelist.ui.screens.manga.MangaBrowseViewModel
import com.kitsugi.animelist.ui.screens.manga.MangaReaderScreen
import com.kitsugi.animelist.ui.screens.manga.MangaDetailScreen
import com.kitsugi.animelist.ui.screens.manga.MangaSourceHealthScreen
import com.kitsugi.animelist.ui.screens.manga.MangaBrowseScreen
import com.kitsugi.animelist.ui.screens.manga.MangaSourceExploreScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun AppRootMangaPages(
    key: AppStateKey,
    navState: AppNavigationState,
    mangaViewModel: MangaViewModel,
    mangaBrowseViewModel: MangaBrowseViewModel,
    coroutineScope: CoroutineScope,
    onExportMangaSourceReportFile: (String) -> Unit
) {
    when (key) {
        is AppStateKey.MangaReader -> {
            val readerState = navState.mangaReaderNavState
            if (readerState != null) {
                MangaReaderScreen(
                    chapter          = readerState.chapter,
                    mangaDetails     = readerState.mangaDetails,
                    source           = readerState.source,
                    onBack           = { navState.mangaReaderNavState = null }
                )
            }
        }

        is AppStateKey.MangaDetail -> {
            val detailState = navState.mangaDetailNavState
            if (detailState != null) {
                MangaDetailScreen(
                    source        = detailState.source,
                    mangaDetails  = detailState.mangaDetails,
                    onOpenChapter = { chapter ->
                        navState.mangaReaderNavState = MangaReaderNavState(
                            source       = detailState.source,
                            mangaDetails = detailState.mangaDetails,
                            chapter      = chapter
                        )
                    },
                    onBack        = { navState.mangaDetailNavState = null }
                )
            }
        }

        is AppStateKey.MangaSourceHealth -> {
            MangaSourceHealthScreen(
                report = mangaViewModel.mangaSourceStateReport,
                installedSources = mangaViewModel.mangaSources,
                onBack = { navState.closeMangaSourceHealth() },
                onExportReportFile = { text -> onExportMangaSourceReportFile(text) },
                onQuickCheckSource = { src -> mangaViewModel.quickCheckSource(src) },
                onRefreshSourceMirror = { src -> mangaViewModel.refreshSourceMirror(src) },
                onResetSourceDiagnostics = { src -> mangaViewModel.resetSourceDiagnostics(src) },
                onClearSourceMirror = { src -> mangaViewModel.clearSourceMirror(src) },
                onClearAllSourceDiagnostics = { mangaViewModel.clearAllSourceDiagnostics() },
                onClearAllSourceConfigs = { mangaViewModel.clearAllSourceConfigs() },
                onIsSourceBusy = { src -> mangaViewModel.isSourceBusy(src) },
                onGetSourceFailureStreak = { src -> mangaViewModel.getSourceFailureStreak(src) },
                onGetSourceCooldownUntil = { src -> mangaViewModel.getSourceCooldownUntil(src) },
                onGetMangaConfiguredDomain = { src -> mangaViewModel.getConfiguredSourceDomain(src) ?: "" },
                onGetMangaConfiguredBaseUrl = { src -> mangaViewModel.getConfiguredSourceBaseUrl(src) ?: "" },
                onSetMangaSourceDomain = { src, value -> mangaViewModel.setSourceDomainOverride(src, value) },
            )
        }

        is AppStateKey.MangaSourceExplore -> {
            // Eklenti portalı → manga eklentisi → Keşfet.
            // Kaynak nesnesi yerine kararlı anahtar taşındığı için kurulu
            // kaynak listesinden çözülür (video tarafındaki APIHolder çözümüyle aynı mantık).
            val exploreKey = "manga_source_explore_${key.depth}_${key.sourceKey}"
            navState.stateHolder.SaveableStateProvider(key = exploreKey) {
                val source = mangaViewModel.mangaSources
                    .firstOrNull { it.stableSourceKey() == key.sourceKey }
                if (source != null) {
                    MangaSourceExploreScreen(
                        source = source,
                        repository = mangaViewModel.mangaRepository,
                        onMangaClick = { clickedSource, mangaDetails ->
                            // Kaynağın kendi detay + bölüm listesi sayfası açılır.
                            navState.mangaDetailNavState = MangaDetailNavState(
                                source = clickedSource,
                                mangaDetails = mangaDetails
                            )
                        },
                        onBack = { navState.popDetailStack() }
                    )
                } else {
                    // Eklenti bu arada kaldırıldıysa boş ekranda bırakma.
                    LaunchedEffect(Unit) { navState.popDetailStack() }
                }
            }
        }

        is AppStateKey.MangaBrowse -> {
            MangaBrowseScreen(
                repository    = mangaViewModel.mangaRepository,
                initialQuery  = navState.mangaBrowseQuery,
                vm            = mangaBrowseViewModel,
                onMangaClick  = { source, mangaDetails ->
                    val targetMediaId = navState.mangaBrowseMediaId
                    if (targetMediaId != null) {
                        coroutineScope.launch {
                            mangaViewModel.mangaRepository.saveMangaMapping(
                                mediaId = targetMediaId,
                                source = source.name,
                                url = mangaDetails.url,
                                title = mangaDetails.title,
                                thumbnailUrl = mangaDetails.thumbnailUrl
                            )
                        }
                        mangaBrowseViewModel.reset()
                        navState.closeMangaBrowse()
                    }
                    navState.mangaDetailNavState = MangaDetailNavState(
                        source       = source,
                        mangaDetails = mangaDetails
                    )
                },
                onExploreMangaSource = { exploreSource ->
                    // Keşfet sayfası bir DetailScreen olarak itilir; MangaBrowse açıkken
                    // öncelik onu gölgeleyeceği için önce tarama sayfası kapatılır.
                    mangaBrowseViewModel.reset()
                    navState.closeMangaBrowse()
                    navState.navigateToDetail(
                        DetailScreen.MangaSourceExplore(sourceKey = exploreSource.stableSourceKey())
                    )
                },
                onBack        = {
                    mangaBrowseViewModel.reset()
                    navState.closeMangaBrowse()
                }
            )
        }
        else -> {}
    }
}
