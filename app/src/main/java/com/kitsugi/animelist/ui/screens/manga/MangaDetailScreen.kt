package com.kitsugi.animelist.ui.screens.manga

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kitsugi.animelist.R
import com.kitsugi.animelist.data.fandom.FandomMediaQueries
import com.kitsugi.animelist.data.manga.MangaChapter
import com.kitsugi.animelist.data.manga.MangaChapterGrouping
import com.kitsugi.animelist.data.manga.MangaDetails
import com.kitsugi.animelist.data.manga.MangaSource
import com.kitsugi.animelist.data.manga.MangaStatus
import com.kitsugi.animelist.data.manga.download.MangaDownload
import com.kitsugi.animelist.data.manga.download.MangaDownloadManager
import com.kitsugi.animelist.data.manga.stableSourceKey
import com.kitsugi.animelist.ui.components.KitsugiButton
import com.kitsugi.animelist.ui.components.KitsugiDetailActionButton
import com.kitsugi.animelist.ui.components.KitsugiPlasmaLoader
import com.kitsugi.animelist.ui.components.KitsugiSheetOrDialog
import com.kitsugi.animelist.ui.components.safeBlur
import com.kitsugi.animelist.ui.screens.detail.wiki.FandomMediaWikiViewModel
import com.kitsugi.animelist.ui.screens.detail.wiki.MediaWikiSectionCard
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.Text
import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.utils.tvClickable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

private enum class ChapterSortMode { OLDEST_FIRST, NEWEST_FIRST, RECENTLY_UPDATED }
private enum class ChapterReadFilter { ALL, UNREAD, READ }

/** Manga details and a grouped, searchable chapter list. */
@Composable
fun MangaDetailScreen(
    source: MangaSource,
    mangaDetails: MangaDetails,
    onOpenChapter: (MangaChapter) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val accent = LocalKitsugiAccent.current
    val viewModel: MangaDetailViewModel = viewModel(
        key = "${source.name}_${mangaDetails.url}",
        factory = MangaDetailViewModel.Factory(context, source, mangaDetails),
    )
    val uiState by viewModel.uiState.collectAsState()
    val mangaDownloads by MangaDownloadManager.downloads.collectAsState()
    val details = uiState.details

    LaunchedEffect(context) {
        withContext(Dispatchers.IO) { MangaDownloadManager.initialize(context) }
    }

    var sortMode by rememberSaveable { mutableStateOf(ChapterSortMode.OLDEST_FIRST) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var readFilter by rememberSaveable { mutableStateOf(ChapterReadFilter.ALL) }
    var showSortSheet by rememberSaveable { mutableStateOf(false) }
    var showSearchSheet by rememberSaveable { mutableStateOf(false) }
    var selectedGroup by remember { mutableStateOf<MangaDetailViewModel.ChapterGroupRow?>(null) }

    val visibleGroups = remember(
        uiState.chapterGroups,
        sortMode,
        searchQuery,
        readFilter,
    ) {
        val query = searchQuery.trim().lowercase(Locale.ROOT)
        uiState.chapterGroups
            .filter { group ->
                val matchesRead = when (readFilter) {
                    ChapterReadFilter.ALL -> true
                    ChapterReadFilter.UNREAD -> !group.isCompleted
                    ChapterReadFilter.READ -> group.isCompleted
                }
                val matchesQuery = query.isBlank() || buildString {
                    append(group.displayName)
                    append(' ')
                    group.number?.let { append(MangaChapterGrouping.formatNumber(it)) }
                    append(' ')
                    group.variants.forEach { variant ->
                        append(variant.chapter.scanlator.orEmpty())
                        append(' ')
                    }
                }.lowercase(Locale.ROOT).contains(query)
                matchesRead && matchesQuery
            }
            .let { groups ->
                when (sortMode) {
                    ChapterSortMode.OLDEST_FIRST -> groups.sortedWith(
                        compareBy<MangaDetailViewModel.ChapterGroupRow> { it.number == null }
                            .thenBy { it.number ?: Double.MAX_VALUE }
                            .thenBy { it.displayName },
                    )
                    ChapterSortMode.NEWEST_FIRST -> groups.sortedWith(
                        compareByDescending<MangaDetailViewModel.ChapterGroupRow> {
                            it.number ?: Double.NEGATIVE_INFINITY
                        }.thenBy { it.displayName },
                    )
                    ChapterSortMode.RECENTLY_UPDATED -> groups.sortedWith(
                        compareByDescending<MangaDetailViewModel.ChapterGroupRow> { it.uploadDate }
                            .thenByDescending { it.number ?: Double.NEGATIVE_INFINITY },
                    )
                }
            }
    }

    val downloadsByUrl = remember(mangaDownloads, source) {
        val sourceKey = source.stableSourceKey()
        mangaDownloads.filter { it.sourceKey == sourceKey }.associateBy { it.chapterUrl }
    }

    fun handleDownload(chapter: MangaChapter) {
        val current = MangaDownloadManager.get(source, chapter)
        when (current?.status) {
            MangaDownload.Status.COMPLETED -> Toast.makeText(
                context,
                context.getString(R.string.manga_chapter_download_already),
                Toast.LENGTH_SHORT,
            ).show()
            MangaDownload.Status.DOWNLOADING,
            MangaDownload.Status.QUEUE,
            -> {
                MangaDownloadManager.pause(context, current!!.id)
                Toast.makeText(context, R.string.manga_chapter_download_paused, Toast.LENGTH_SHORT).show()
            }
            MangaDownload.Status.PAUSED,
            MangaDownload.Status.ERROR,
            -> {
                MangaDownloadManager.resume(context, current!!.id, source, details)
                Toast.makeText(context, R.string.manga_chapter_download_resumed, Toast.LENGTH_SHORT).show()
            }
            null -> {
                MangaDownloadManager.enqueue(context, source, details, chapter)
                Toast.makeText(context, R.string.manga_chapter_download_enqueued, Toast.LENGTH_SHORT).show()
            }
        }
    }

    val wikiViewModel: FandomMediaWikiViewModel = viewModel(
        key = "fandom_manga_${source.name}_${mangaDetails.url}",
    )
    val wikiQuery = remember(details.title) {
        FandomMediaQueries.fromMedia(
            source = null,
            mediaId = null,
            isManga = true,
            isMovie = false,
            titles = listOf(details.title),
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(KitsugiColors.Background),
    ) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item { MangaDetailHeader(details = details, accent = accent) }

            item {
                val resume = uiState.resumeChapter
                KitsugiDetailActionButton(
                    text = when {
                        resume != null -> stringResource(R.string.devam_et_x, resume.name)
                        else -> stringResource(R.string.ui_manga_resume_from_beginning)
                    },
                    icon = if (resume != null) Icons.Rounded.PlayArrow else Icons.AutoMirrored.Rounded.MenuBook,
                    onClick = { viewModel.chapterToOpen()?.let(onOpenChapter) },
                    enabled = uiState.chapterGroups.isNotEmpty(),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }

            if (!details.description.isNullOrBlank()) {
                item {
                    Text(
                        text = details.description!!,
                        color = KitsugiColors.TextSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }

            item(key = "fandom_wiki") {
                MediaWikiSectionCard(
                    viewModel = wikiViewModel,
                    query = wikiQuery,
                    accentColor = accent,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            item(key = "chapter_header") {
                val unreadCount = uiState.chapterGroups.count { !it.isCompleted }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 6.dp, top = 12.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.bolumler) +
                                if (uiState.chapterGroups.isNotEmpty()) " (${uiState.chapterGroups.size})" else "",
                            color = KitsugiColors.TextPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        if (uiState.chapterGroups.isNotEmpty()) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "${sortMode.title()} · $unreadCount ${stringResource(R.string.manga_chapter_unread).lowercase(Locale.getDefault())}",
                                color = KitsugiColors.TextMuted,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    IconButton(onClick = { showSearchSheet = true }) {
                        Icon(
                            Icons.Rounded.Search,
                            contentDescription = stringResource(R.string.manga_chapter_search_title),
                            tint = if (searchQuery.isNotBlank() || readFilter != ChapterReadFilter.ALL) {
                                accent
                            } else KitsugiColors.TextSecondary,
                            modifier = Modifier.size(21.dp),
                        )
                    }
                    IconButton(onClick = { showSortSheet = true }) {
                        Icon(
                            Icons.Rounded.Sort,
                            contentDescription = stringResource(R.string.manga_chapter_sort),
                            tint = if (sortMode != ChapterSortMode.OLDEST_FIRST) accent else KitsugiColors.TextSecondary,
                            modifier = Modifier.size(21.dp),
                        )
                    }
                    IconButton(onClick = { viewModel.loadChapters() }) {
                        Icon(
                            Icons.Rounded.Refresh,
                            contentDescription = stringResource(R.string.yenile),
                            tint = KitsugiColors.TextSecondary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }

            when {
                uiState.isLoadingChapters -> item {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) { KitsugiPlasmaLoader(size = 46.dp) }
                }

                uiState.error != null -> item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            uiState.error!!,
                            color = KitsugiColors.TextSecondary,
                            fontSize = 13.sp,
                        )
                        uiState.errorCode?.let { code ->
                            com.kitsugi.animelist.ui.components.errors.PlatformErrorExplanationBlock(code = code)
                        }
                        Spacer(Modifier.height(12.dp))
                        KitsugiButton(onClick = { viewModel.loadChapters() }) {
                            Text(stringResource(R.string.yenile), fontWeight = FontWeight.Bold)
                        }
                    }
                }

                visibleGroups.isEmpty() -> item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 36.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            Icons.Rounded.Search,
                            contentDescription = null,
                            tint = KitsugiColors.TextMuted,
                            modifier = Modifier.size(34.dp),
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            stringResource(R.string.manga_chapter_search_empty),
                            color = KitsugiColors.TextMuted,
                            fontSize = 13.sp,
                        )
                        if (searchQuery.isNotBlank() || readFilter != ChapterReadFilter.ALL) {
                            TextButton(onClick = {
                                searchQuery = ""
                                readFilter = ChapterReadFilter.ALL
                            }) { Text(stringResource(R.string.tumu), color = accent) }
                        }
                    }
                }

                else -> items(visibleGroups, key = { it.key }) { group ->
                    ChapterGroupListItem(
                        group = group,
                        accent = accent,
                        download = preferredGroupDownload(group, downloadsByUrl),
                        onClick = {
                            if (group.variants.size > 1) selectedGroup = group
                            else onOpenChapter(group.primary.chapter)
                        },
                        onDownload = {
                            if (group.variants.size > 1) selectedGroup = group
                            else handleDownload(group.primary.chapter)
                        },
                    )
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }

        IconButton(
            onClick = onBack,
            modifier = Modifier
                .padding(4.dp)
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(0.35f)),
        ) {
            Icon(
                Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.geri),
                tint = Color.White,
            )
        }
    }

    if (showSortSheet) {
        ChapterSortSheet(
            selected = sortMode,
            onSelect = { sortMode = it },
            onDismiss = { showSortSheet = false },
        )
    }

    if (showSearchSheet) {
        ChapterSearchSheet(
            query = searchQuery,
            onQueryChange = { searchQuery = it },
            filter = readFilter,
            onFilterChange = { readFilter = it },
            resultCount = visibleGroups.size,
            accent = accent,
            onDismiss = { showSearchSheet = false },
        )
    }

    selectedGroup?.let { group ->
        ChapterVariantSheet(
            title = group.displayName,
            chapters = group.variants.map { it.chapter },
            downloadsByChapterUrl = downloadsByUrl,
            onOpenChapter = onOpenChapter,
            onDownloadChapter = ::handleDownload,
            onDismiss = { selectedGroup = null },
        )
    }
}

@Composable
private fun ChapterSortSheet(
    selected: ChapterSortMode,
    onSelect: (ChapterSortMode) -> Unit,
    onDismiss: () -> Unit,
) {
    KitsugiSheetOrDialog(onDismiss = onDismiss) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 8.dp, top = 4.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.manga_chapter_sort),
                color = KitsugiColors.TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onDismiss) {
                Icon(Icons.Rounded.Close, stringResource(R.string.kapat), tint = KitsugiColors.TextSecondary)
            }
        }
        HorizontalDivider(color = KitsugiColors.SurfaceStrong)
        ChapterSortMode.entries.forEach { mode ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .tvClickable { onSelect(mode); onDismiss() }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = selected == mode,
                    onClick = { onSelect(mode); onDismiss() },
                    colors = RadioButtonDefaults.colors(selectedColor = LocalKitsugiAccent.current),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    mode.title(),
                    color = if (selected == mode) KitsugiColors.TextPrimary else KitsugiColors.TextSecondary,
                    fontSize = 15.sp,
                    fontWeight = if (selected == mode) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.weight(1f),
                )
                if (selected == mode) {
                    Icon(
                        Icons.Rounded.Check,
                        contentDescription = null,
                        tint = LocalKitsugiAccent.current,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun ChapterSearchSheet(
    query: String,
    onQueryChange: (String) -> Unit,
    filter: ChapterReadFilter,
    onFilterChange: (ChapterReadFilter) -> Unit,
    resultCount: Int,
    accent: Color,
    onDismiss: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    KitsugiSheetOrDialog(onDismiss = onDismiss) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 8.dp, top = 4.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.manga_chapter_search_title),
                color = KitsugiColors.TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onDismiss) {
                Icon(Icons.Rounded.Close, stringResource(R.string.kapat), tint = KitsugiColors.TextSecondary)
            }
        }
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .focusRequester(focusRequester),
            placeholder = {
                androidx.compose.material3.Text(stringResource(R.string.manga_chapter_search_hint))
            },
            leadingIcon = {
                Icon(Icons.Rounded.Search, contentDescription = null, tint = KitsugiColors.TextMuted)
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Rounded.Close, stringResource(R.string.kapat), tint = KitsugiColors.TextMuted)
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = accent,
                cursorColor = accent,
                focusedTextColor = KitsugiColors.TextPrimary,
                unfocusedTextColor = KitsugiColors.TextPrimary,
            ),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.manga_chapter_read_filter),
            color = KitsugiColors.TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ChapterReadFilter.entries.forEach { item ->
                FilterChip(
                    selected = filter == item,
                    onClick = { onFilterChange(item) },
                    label = {
                        androidx.compose.material3.Text(
                            when (item) {
                                ChapterReadFilter.ALL -> stringResource(R.string.tumu)
                                ChapterReadFilter.UNREAD -> stringResource(R.string.manga_chapter_unread)
                                ChapterReadFilter.READ -> stringResource(R.string.okundu)
                            },
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = accent.copy(alpha = 0.18f),
                        selectedLabelColor = accent,
                        containerColor = KitsugiColors.SurfaceSoft,
                        labelColor = KitsugiColors.TextSecondary,
                    ),
                )
            }
        }
        Text(
            stringResource(R.string.manga_chapter_search_summary, resultCount),
            color = KitsugiColors.TextMuted,
            fontSize = 12.sp,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 18.dp),
        )
    }
}

private fun preferredGroupDownload(
    group: MangaDetailViewModel.ChapterGroupRow,
    downloads: Map<String, MangaDownload>,
): MangaDownload? {
    val variants = group.variants.mapNotNull { downloads[it.chapter.url] }
    return variants.firstOrNull {
        it.status == MangaDownload.Status.DOWNLOADING || it.status == MangaDownload.Status.QUEUE
    } ?: variants.firstOrNull { it.status == MangaDownload.Status.COMPLETED }
        ?: variants.firstOrNull { it.status == MangaDownload.Status.PAUSED }
        ?: variants.firstOrNull { it.status == MangaDownload.Status.ERROR }
}

@Composable
private fun ChapterGroupListItem(
    group: MangaDetailViewModel.ChapterGroupRow,
    accent: Color,
    download: MangaDownload?,
    onClick: () -> Unit,
    onDownload: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(shape)
            .background(
                if (group.isInProgress) accent.copy(alpha = 0.10f) else KitsugiColors.SurfaceSoft,
            )
            .tvClickable(shape = shape) { onClick() }
            .padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = group.displayName,
                    color = if (group.isCompleted) KitsugiColors.TextMuted else KitsugiColors.TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = if (group.isInProgress) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (group.variants.size > 1) {
                    val translatorCount = group.variants
                        .map { it.chapter.scanlator.orEmpty().trim().lowercase(Locale.ROOT) }
                        .distinct()
                        .size
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (translatorCount > 1) {
                            stringResource(R.string.manga_chapter_variant_count, translatorCount)
                        } else {
                            stringResource(R.string.manga_chapter_version_count, group.variants.size)
                        },
                        color = accent,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(accent.copy(alpha = 0.12f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
            val unknownScanlator = stringResource(R.string.manga_chapter_unknown_scanlator)
            val translatorText = group.variants
                .map { it.chapter.scanlator?.trim().takeUnless { name -> name.isNullOrBlank() } }
                .distinctBy { it?.lowercase(Locale.ROOT) }
                .mapIndexed { index, name ->
                    name ?: if (index == 0) unknownScanlator else null
                }
                .filterNotNull()
                .take(3)
                .joinToString(" • ")
            val subtitle = buildString {
                val progress = group.variants.firstOrNull { it.isInProgress }
                if (progress != null && progress.totalPages > 0) {
                    append("Sayfa ${progress.lastPageIndex + 1}/${progress.totalPages}")
                    if (translatorText.isNotBlank()) append(" • ")
                }
                append(translatorText)
            }
            if (subtitle.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = subtitle,
                    color = if (group.isInProgress) accent else KitsugiColors.TextMuted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        ChapterDownloadIconButton(
            download = download,
            accent = accent,
            onClick = onDownload,
        )
        if (group.isCompleted) {
            Icon(
                Icons.Rounded.CheckCircle,
                contentDescription = stringResource(R.string.okundu),
                tint = KitsugiColors.AccentGreen.copy(alpha = 0.85f),
                modifier = Modifier
                    .padding(end = 8.dp)
                    .size(18.dp),
            )
        } else {
            Spacer(Modifier.width(8.dp))
        }
    }
}

@Composable
private fun ChapterSortMode.title(): String = stringResource(
    when (this) {
        ChapterSortMode.OLDEST_FIRST -> R.string.manga_chapter_sort_oldest
        ChapterSortMode.NEWEST_FIRST -> R.string.manga_chapter_sort_newest
        ChapterSortMode.RECENTLY_UPDATED -> R.string.manga_chapter_sort_recent
    },
)

@Composable
private fun MangaDetailHeader(details: MangaDetails, accent: Color) {
    val isAdultContent = details.isAdultContent
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp),
    ) {
        if (!details.thumbnailUrl.isNullOrBlank()) {
            com.kitsugi.animelist.ui.components.KitsugiNsfwImage(
                model = details.thumbnailUrl,
                contentDescription = "",
                isAdult = isAdultContent,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .safeBlur(16.dp),
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            KitsugiColors.Background.copy(0.55f),
                            KitsugiColors.Background.copy(0.85f),
                            KitsugiColors.Background,
                        ),
                    ),
                ),
        )

        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            com.kitsugi.animelist.ui.components.KitsugiNsfwImage(
                model = details.thumbnailUrl,
                contentDescription = details.title,
                isAdult = isAdultContent,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(120.dp)
                    .height(170.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(KitsugiColors.SurfaceSoft),
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = details.title,
                    color = KitsugiColors.TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!details.author.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = details.author!!,
                        color = KitsugiColors.TextSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(accent.copy(0.15f))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    ) {
                        Text(
                            text = details.status.label(),
                            color = accent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    if (details.source.isNotBlank()) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = details.source,
                            color = KitsugiColors.TextMuted,
                            fontSize = 11.sp,
                        )
                    }
                }
                if (details.genre.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = details.genre.take(3).joinToString(" • "),
                        color = KitsugiColors.TextMuted,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private fun MangaStatus.label(): String = when (this) {
    MangaStatus.Ongoing -> "Devam Ediyor"
    MangaStatus.Completed -> "Tamamlandı"
    MangaStatus.Licensed -> "Lisanslı"
    MangaStatus.PublicationComplete -> "Yayın Tamamlandı"
    MangaStatus.Cancelled -> "İptal Edildi"
    MangaStatus.OnHiatus -> "Ara Verildi"
    MangaStatus.Unknown -> "Bilinmiyor"
}
