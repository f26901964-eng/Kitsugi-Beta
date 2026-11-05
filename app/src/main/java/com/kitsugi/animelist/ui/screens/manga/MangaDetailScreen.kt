package com.kitsugi.animelist.ui.screens.manga
import com.kitsugi.animelist.ui.components.KitsugiButton

import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.utils.tvClickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import com.kitsugi.animelist.ui.theme.gradient.CircularProgressIndicator
import com.kitsugi.animelist.ui.components.KitsugiPlasmaLoader
import com.kitsugi.animelist.ui.components.KitsugiDetailActionButton
import com.kitsugi.animelist.ui.theme.gradient.Icon
import androidx.compose.material3.IconButton
import com.kitsugi.animelist.ui.theme.gradient.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kitsugi.animelist.data.manga.MangaChapter
import com.kitsugi.animelist.data.manga.MangaDetails
import com.kitsugi.animelist.data.manga.MangaSource
import com.kitsugi.animelist.data.manga.MangaKindDetector
import com.kitsugi.animelist.data.manga.MangaStatus
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.theme.KitsugiColors
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R
import com.kitsugi.animelist.ui.components.safeBlur
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.kitsugi.animelist.data.manga.ChapterFilter
import com.kitsugi.animelist.data.manga.ChapterSort
import com.kitsugi.animelist.ui.screens.manga.components.ChapterFilterChips
import com.kitsugi.animelist.ui.screens.manga.components.ChapterSearchAndSortBar
import com.kitsugi.animelist.ui.screens.manga.components.ChapterSortSheet
import com.kitsugi.animelist.ui.screens.manga.components.label
import com.kitsugi.animelist.ui.components.KitsugiConfirmDialog
import com.kitsugi.animelist.data.fandom.FandomMediaQueries
import com.kitsugi.animelist.ui.screens.detail.wiki.FandomMediaWikiViewModel
import com.kitsugi.animelist.ui.screens.detail.wiki.MediaWikiSectionCard

/**
 * Manga Detay + "Oku" Ekranı.
 *
 * — Üstte bulanık kapak arkaplanı + kapak görseli + başlık/yazar/durum/türler
 * — Büyük "Baştan Oku" / "Devam Et" butonu
 * — Bölüm listesi (okunan bölümler işaretli, devam edilen bölüm vurgulu)
 *
 * Bir bölüme (veya Oku butonuna) tıklanınca [onOpenChapter] ile okuyucu açılır.
 */
@Composable
fun MangaDetailScreen(
    source: MangaSource,
    mangaDetails: MangaDetails,
    onOpenChapter: (MangaChapter) -> Unit,
    onBack: () -> Unit,
    /**
     * Light novel / web novel türü algılandığında bölüm bu geri çağrı ile
     * metin okuyucuda açılır. Verilmezse normal sayfa okuyucuya düşer.
     */
    onOpenNovel: ((MangaChapter) -> Unit)? = null
) {
    val context = LocalContext.current
    val accent = LocalKitsugiAccent.current
    val viewModel: MangaDetailViewModel = viewModel(
        key = "${source.name}_${mangaDetails.url}",
        factory = MangaDetailViewModel.Factory(context, source, mangaDetails)
    )
    val uiState by viewModel.uiState.collectAsState()
    val details = uiState.details
    // Bölüm araması (yerel filtre — ek ağ isteği yok) ve sıralama paneli durumu.
    var chapterQuery by remember { mutableStateOf("") }
    var showSortSheet by remember { mutableStateOf(false) }
    // Cilt gruplamasında açık gruplar (varsayılan: en yeni cilt açık).
    var expandedVolumes by remember { mutableStateOf(setOf<String>()) }
    val visibleChapters = remember(uiState.chapters, chapterQuery) {
        val q = chapterQuery.trim()
        if (q.isEmpty()) uiState.chapters
        else uiState.chapters.filter { row ->
            row.chapter.name.contains(q, ignoreCase = true) ||
                row.numberLabel == q ||
                (row.chapter.scanlator?.contains(q, ignoreCase = true) == true)
        }
    }
    // Fandom Wiki: eklenti kaynağında kimlik yoktur; seri başlığıyla (isim yolu) çözülür.
    val wikiViewModel: FandomMediaWikiViewModel = viewModel(key = "fandom_manga_${source.name}_${mangaDetails.url}")
    val wikiQuery = remember(details.title) {
        FandomMediaQueries.fromMedia(
            source = null,
            mediaId = null,
            isManga = true,
            isMovie = false,
            titles = listOf(details.title)
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(KitsugiColors.Background)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize()
        ) {
            // ─── Hero / Kapak Başlık ───────────────────────────────────────────
            item {
                MangaDetailHeader(details = details, accent = accent, kind = uiState.kind)
            }

            // ─── Oku / Devam Et Butonu ─────────────────────────────────────────
            item {
                val resume = uiState.resumeChapter
                val canRead = uiState.chapters.isNotEmpty()
                KitsugiDetailActionButton(
                    text = when {
                        resume != null -> stringResource(R.string.devam_et_x, resume.name)
                        else -> "Baştan Oku"
                    },
                    icon = if (resume != null) Icons.Rounded.PlayArrow else Icons.AutoMirrored.Rounded.MenuBook,
                    onClick = {
                        viewModel.chapterToOpen()?.let { target ->
                            if (uiState.kind == MangaKindDetector.Kind.LightNovel && onOpenNovel != null) {
                                onOpenNovel(target)
                            } else {
                                onOpenChapter(target)
                            }
                        }
                    },
                    enabled = canRead,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }

            // ─── Açıklama ──────────────────────────────────────────────────────
            if (!details.description.isNullOrBlank()) {
                item {
                    Text(
                        text = details.description!!,
                        color = KitsugiColors.TextSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
            }

            // ─── Fandom Wiki (açılır kart; içerik kullanıcı açınca yüklenir) ─────
            item(key = "fandom_wiki") {
                MediaWikiSectionCard(
                    viewModel = wikiViewModel,
                    query = wikiQuery,
                    accentColor = accent,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            // ─── Bölümler Başlığı + Sıralama ───────────────────────────────────
            // HATA DÜZELTMESİ: Buradaki ok ikonu (⇄) daha önce HİÇBİR ŞEY yapmıyordu;
            // sadece süs olarak duruyordu. Artık gerçek bir "sırala" düğmesi:
            //   • tek dokunuş  → sıralama yönünü çevirir (önce 1→137, sonra 137→1)
            //   • uzun basış   → tüm sıralama seçenekleri paneli açılır
            //   • yanındaki sayaç → toplam bölüm / gösterilen bölüm
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.bolumler) + if (uiState.allChapters.isNotEmpty()) " (${uiState.chapters.size}/${uiState.allChapters.size})" else "",
                            color = KitsugiColors.TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (uiState.allChapters.isNotEmpty()) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = stringResource(
                                    R.string.chapter_list_summary,
                                    uiState.unreadCount,
                                    uiState.scanlators.size
                                ),
                                color = KitsugiColors.TextMuted,
                                fontSize = 11.sp
                            )
                        }
                    }

                    // Sıralama düğmesi (işaretlediğin ⇄ butonu)
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(accent.copy(alpha = 0.12f))
                            .tvClickable(
                                shape = CircleShape,
                                onLongClick = { showSortSheet = true }
                            ) { viewModel.toggleChapterSortDirection() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.Sort,
                            contentDescription = stringResource(R.string.chapter_sort),
                            tint = accent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(Modifier.width(2.dp))
                    IconButton(onClick = { viewModel.loadChapters() }) {
                        Icon(
                            Icons.Rounded.Refresh,
                            contentDescription = stringResource(R.string.yenile),
                            tint = KitsugiColors.TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // ─── Arama + Filtre Çipleri ────────────────────────────────────────
            item(key = "chapter_controls") {
                if (uiState.allChapters.isNotEmpty()) {
                    ChapterSearchAndSortBar(
                        query = chapterQuery,
                        onQueryChange = { chapterQuery = it },
                        sort = uiState.chapterSort,
                        onSortClick = { showSortSheet = true },
                        onToggleDirection = { viewModel.toggleChapterSortDirection() },
                        accent = accent
                    )
                    Spacer(Modifier.height(8.dp))
                    ChapterFilterChips(
                        filter = uiState.chapterFilter,
                        scanlators = uiState.scanlators,
                        selectedScanlator = uiState.scanlatorFilter,
                        unreadCount = uiState.unreadCount,
                        onFilterSelected = { viewModel.setChapterFilter(it) },
                        onScanlatorSelected = { viewModel.setScanlatorFilter(it) },
                        accent = accent
                    )
                    // ─── Cilt gruplaması + Tümünü okundu işaretle ─────────────
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ChapterUtilityChip(
                            label = if (uiState.groupByVolume) "Cilt grupları açık" else "Ciltlere göre grupla",
                            selected = uiState.groupByVolume,
                            accent = accent
                        ) { viewModel.toggleVolumeGrouping() }
                        if (uiState.unreadCount > 0) {
                            ChapterUtilityChip(
                                label = "Tümünü okundu yap",
                                selected = false,
                                accent = accent
                            ) { viewModel.markAllRead() }
                        }
                        Spacer(Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }

            // ─── Bölüm Durumları ───────────────────────────────────────────────
            when {
                uiState.isLoadingChapters -> item {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        KitsugiPlasmaLoader(size = 46.dp)
                    }
                }

                uiState.error != null -> item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            uiState.error!!,
                            color = KitsugiColors.TextSecondary,
                            fontSize = 13.sp
                        )
                        uiState.errorCode?.let { code -> com.kitsugi.animelist.ui.components.errors.PlatformErrorExplanationBlock(code = code) }
                        Spacer(Modifier.height(12.dp))
                        KitsugiButton(
                            onClick = { viewModel.loadChapters() }
                        ) {
                            Text("Tekrar Dene", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                else -> if (visibleChapters.isEmpty()) {
                    item(key = "chapters_empty_filtered") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = stringResource(R.string.chapter_filter_empty),
                                color = KitsugiColors.TextSecondary,
                                fontSize = 13.sp
                            )
                            Spacer(Modifier.height(12.dp))
                            KitsugiButton(
                                onClick = {
                                    chapterQuery = ""
                                    viewModel.clearChapterFilters()
                                }
                            ) {
                                Text(stringResource(R.string.chapter_filter_clear), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else if (uiState.groupByVolume) {
                    // Cilt grupları: katlanabilir başlık + bölümler.
                    val groups = remember(visibleChapters, uiState.groupByVolume) {
                        com.kitsugi.animelist.data.manga.MangaChapterGrouping.groupByVolume(
                            rows = visibleChapters,
                            volumeOf = { it.volume },
                            number = { it.resolvedNumber }
                        )
                    }
                    val rangeLabel = com.kitsugi.animelist.data.manga.MangaChapterGrouping
                        .rangeLabel<MangaDetailViewModel.ChapterRow> { it.resolvedNumber }
                    groups.forEach { group ->
                        val key = "vol_${group.volume ?: "none"}"
                        item(key = "hdr_$key") {
                            VolumeGroupHeader(
                                title = rangeLabel(group),
                                count = group.chapters.size,
                                expanded = expandedVolumes.contains(key),
                                accent = accent
                            ) {
                                expandedVolumes = if (expandedVolumes.contains(key)) {
                                    expandedVolumes - key
                                } else {
                                    expandedVolumes + key
                                }
                            }
                        }
                        if (expandedVolumes.contains(key)) {
                            items(
                                items = group.chapters,
                                key = { it.chapter.url }
                            ) { row ->
                                ChapterListItem(
                                    row = row,
                                    accent = accent,
                                    onClick = {
                                        if (uiState.kind == MangaKindDetector.Kind.LightNovel && onOpenNovel != null) {
                                            onOpenNovel(row.chapter)
                                        } else {
                                            onOpenChapter(row.chapter)
                                        }
                                    },
                                    onToggleRead = {
                                        if (row.isCompleted) viewModel.markChapterUnread(row)
                                        else viewModel.markChapterRead(row)
                                    }
                                )
                            }
                        }
                    }
                } else {
                    items(
                        items = visibleChapters,
                        key = { it.chapter.url }
                    ) { row ->
                        ChapterListItem(
                            row = row,
                            accent = accent,
                            onClick = {
                                if (uiState.kind == MangaKindDetector.Kind.LightNovel && onOpenNovel != null) {
                                    onOpenNovel(row.chapter)
                                } else {
                                    onOpenChapter(row.chapter)
                                }
                            },
                            onToggleRead = {
                                if (row.isCompleted) viewModel.markChapterUnread(row)
                                else viewModel.markChapterRead(row)
                            }
                        )
                    }
                }
            }
        }

        // ─── Sıralama Paneli ──────────────────────────────────────────────────
        if (showSortSheet) {
            ChapterSortSheet(
                current = uiState.chapterSort,
                onSelect = { viewModel.setChapterSort(it) },
                onDismiss = { showSortSheet = false }
            )
        }

        // ─── Geri Butonu (üstte sabit) ─────────────────────────────────────────
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .padding(4.dp)
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(0.35f))
        ) {
            Icon(
                Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.geri),
                tint = Color.White
            )
        }
    }
}

@Composable
private fun MangaDetailHeader(
    details: MangaDetails,
    accent: Color,
    kind: MangaKindDetector.Kind = MangaKindDetector.Kind.Unknown
) {
    // +18 türü/source → bulanıklık her koşulda uygulanır
    val isAdultContent = details.isAdultContent
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp)
    ) {
        // Bulanık arkaplan kapak
        if (!details.thumbnailUrl.isNullOrBlank()) {
            com.kitsugi.animelist.ui.components.KitsugiNsfwImage(
                model = details.thumbnailUrl,
                contentDescription = "",
                isAdult = isAdultContent,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    // ÇÖKME ÖNLEMİ: koşulsuz `.blur(24.dp)` → güvenli sarmalayıcı.
                    // 24dp, MIUI libhwui'de RenderThread SIGSEGV üreten yarıçaplardan;
                    // safeBlur hem API 31 altını atlar hem yarıçapı 16dp'ye sınırlar.
                    .safeBlur(16.dp)
            )
        }
        // Karartma gradyanı
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            KitsugiColors.Background.copy(0.55f),
                            KitsugiColors.Background.copy(0.85f),
                            KitsugiColors.Background
                        )
                    )
                )
        )

        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            // Kapak
            com.kitsugi.animelist.ui.components.KitsugiNsfwImage(
                model = details.thumbnailUrl,
                contentDescription = details.title,
                isAdult = isAdultContent,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(120.dp)
                    .height(170.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(KitsugiColors.SurfaceSoft)
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = details.title,
                    color = KitsugiColors.TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                if (!details.author.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = details.author!!,
                        color = KitsugiColors.TextSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(accent.copy(0.15f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = details.status.label(),
                            color = accent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (details.source.isNotBlank()) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = details.source,
                            color = KitsugiColors.TextMuted,
                            fontSize = 11.sp
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
                        overflow = TextOverflow.Ellipsis
                    )
                }
                // ─── Tür rozeti (Manga / Manhwa / Manhua / Webtoon / Light Novel) ──
                // Kotatsu-Redo'nun "türü algıla, doğru modu öner" davranışının
                // Kitsugi karşılığı: kullanıcı hangi formatta okuduğunu görür.
                if (kind != MangaKindDetector.Kind.Unknown) {
                    Spacer(Modifier.height(6.dp))
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(KitsugiColors.SurfaceStrong.copy(alpha = 0.65f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = MangaKindDetector.describe(kind),
                            color = KitsugiColors.TextSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChapterListItem(
    row: MangaDetailViewModel.ChapterRow,
    accent: Color,
    onClick: () -> Unit,
    onToggleRead: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .tvClickable(shape = RoundedCornerShape(8.dp)) { onClick() }
            .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // ─── Bölüm numarası rozeti ──────────────────────────────────────────
        // Kullanıcı isteği: "Bölüm sayısı sıralaması düzeltilsin, sayfa başı
        // sayısal olarak 1'den başlamalı." Ad'daki numara ("Bölüm 137 - ...")
        // artık ayrıştırılıp solda hizalı, sabit genişlikli rozet olarak çizilir;
        // böylece liste okunabilir bir sayı sütunu kazanır.
        Box(
            modifier = Modifier
                .width(44.dp)
                .height(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    when {
                        row.isInProgress -> accent.copy(alpha = 0.16f)
                        row.isCompleted -> KitsugiColors.SurfaceSoft.copy(alpha = 0.5f)
                        else -> KitsugiColors.SurfaceSoft.copy(alpha = 0.85f)
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            val label = row.numberLabel
            if (label != null) {
                Text(
                    text = label,
                    color = when {
                        row.isInProgress -> accent
                        row.isCompleted -> KitsugiColors.TextMuted
                        else -> KitsugiColors.TextPrimary
                    },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            } else {
                Icon(
                    Icons.AutoMirrored.Rounded.MenuBook,
                    contentDescription = null,
                    tint = KitsugiColors.TextMuted,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        Spacer(Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.chapter.name,
                color = if (row.isCompleted) KitsugiColors.TextMuted else KitsugiColors.TextPrimary,
                fontSize = 14.sp,
                fontWeight = if (row.isInProgress) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            val subtitle = buildString {
                if (row.isInProgress && row.totalPages > 0) {
                    append("Sayfa ${row.lastPageIndex + 1}/${row.totalPages}")
                } else if (!row.chapter.scanlator.isNullOrBlank()) {
                    append(row.chapter.scanlator)
                }
            }
            if (subtitle.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = if (row.isInProgress) accent else KitsugiColors.TextMuted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // ─── Okundu işaretleme kısayolu ──────────────────────────────────────
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .tvClickable(shape = CircleShape) { onToggleRead() },
            contentAlignment = Alignment.Center
        ) {
            if (row.isCompleted) {
                Icon(
                    Icons.Rounded.CheckCircle,
                    contentDescription = stringResource(R.string.okundu),
                    tint = KitsugiColors.AccentGreen.copy(0.9f),
                    modifier = Modifier.size(19.dp)
                )
            } else {
                // Boş halka: "okunmadı" durumu — dokununca okundu olarak işaretlenir.
                Box(
                    Modifier
                        .size(17.dp)
                        .clip(CircleShape)
                        .background(Color.Transparent)
                        .then(
                            Modifier.border(
                                width = 1.5.dp,
                                color = KitsugiColors.TextMuted.copy(0.7f),
                                shape = CircleShape
                            )
                        )
                )
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

/** Bölüm yardımcı çipi (cilt gruplama, tümünü okundu yap). */
@Composable
private fun ChapterUtilityChip(
    label: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) accent.copy(alpha = 0.18f) else KitsugiColors.Surface.copy(0.4f))
            .tvClickable(shape = RoundedCornerShape(50)) { onClick() }
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = if (selected) accent else KitsugiColors.TextSecondary,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

/** Cilt grubu başlığı — dokununca grubu açıp kapatır. */
@Composable
private fun VolumeGroupHeader(
    title: String,
    count: Int,
    expanded: Boolean,
    accent: Color,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(KitsugiColors.SurfaceSoft.copy(alpha = 0.55f))
            .tvClickable(shape = RoundedCornerShape(10.dp)) { onToggle() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = KitsugiColors.TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "$count",
            color = KitsugiColors.TextMuted,
            fontSize = 11.sp
        )
        Spacer(Modifier.width(6.dp))
        Icon(
            imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(18.dp)
        )
    }
}
