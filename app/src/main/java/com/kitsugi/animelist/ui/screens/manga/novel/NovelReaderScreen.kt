package com.kitsugi.animelist.ui.screens.manga.novel

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.FormatAlignLeft
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kitsugi.animelist.R
import com.kitsugi.animelist.data.manga.MangaChapter
import com.kitsugi.animelist.data.manga.MangaDetails
import com.kitsugi.animelist.data.manga.MangaSource
import com.kitsugi.animelist.data.manga.novel.NovelBlock
import com.kitsugi.animelist.data.manga.novel.NovelBlockStyle
import com.kitsugi.animelist.data.manga.novel.NovelChapterContent
import com.kitsugi.animelist.data.manga.novel.NovelChapterParser
import com.kitsugi.animelist.data.manga.novel.NovelReaderStyle
import com.kitsugi.animelist.data.manga.novel.NovelTheme
import com.kitsugi.animelist.ui.components.KitsugiButton
import com.kitsugi.animelist.ui.components.KitsugiPlasmaLoader
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.Text
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * LIGHT NOVEL / WEB NOVEL okuyucu.
 *
 * Kullanıcı raporu: "novel ve light novel türleri de desteklenmeli". Türkçe
 * kaynaklarda (ör. araznovel) novel bölümü tek bir HTML sayfasıdır; manga
 * sayfa okuyucu bunu görsel sanıp boş ekran gösteriyordu.
 *
 * Bu ekran LNReader (açık kaynak light novel okuyucu) özellik setinin sade
 * karşılığını sunar:
 *  - Temiz metin çıkarımı (reklam/menü atılır),
 *  - Yazı boyutu / satır aralığı / hizalama / genişlik ayarı,
 *  - 4 tema (gece, sepya, gündüz, sistem),
 *  - RTL (Arapça/Farsça) desteği,
 *  - Kaydırma yüzdesine göre ilerleme + okuma süresi tahmini,
 *  - Bölüm sonunda önceki/sonraki bölüme geçiş.
 */
@Composable
fun NovelReaderScreen(
    source: MangaSource,
    mangaDetails: MangaDetails,
    chapter: MangaChapter,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: NovelReaderViewModel = viewModel(
        key = "novel_${source.name}_${chapter.url}",
        factory = NovelReaderViewModel.Factory(context, source, mangaDetails, chapter)
    )
    val state by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()

    var menuVisible by remember { mutableStateOf(true) }
    var showAppearance by remember { mutableStateOf(false) }

    LaunchedEffect(state.content) {
        // Bölüm değişince başa dön.
        runCatching { listState.scrollToItem(0) }
    }

    // Kaydırma yüzdesi → ilerleme çubuğu.
    val progress by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val total = info.totalItemsCount
            if (total <= 1) 0f
            else (info.visibleItemsInfo.lastOrNull()?.index ?: 0).toFloat() / (total - 1)
        }
    }

    val palette = state.style.theme.palette()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { offset ->
                    // Metnin ortasına dokun → menüyü aç/kapat (LNReader davranışı).
                    val w = size.width.toFloat()
                    if (offset.x > w * 0.25f && offset.x < w * 0.75f) menuVisible = !menuVisible
                })
            }
    ) {
        when {
            state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    KitsugiPlasmaLoader(size = 46.dp)
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.novel_loading_chapter), color = palette.secondary, fontSize = 12.sp)
                }
            }

            state.content?.error != null -> Box(Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.content?.error ?: stringResource(R.string.novel_no_text), color = palette.secondary, fontSize = 13.sp, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    KitsugiButton(onClick = { viewModel.reload() }) { Text("Tekrar Dene") }
                    Spacer(Modifier.height(10.dp))
                    TextButton(onClick = onBack) { Text("Geri dön", color = palette.secondary) }
                }
            }

            else -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = if (menuVisible) 88.dp else 32.dp,
                    bottom = 120.dp
                ),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                item(key = "novel_header") {
                    Column(
                        modifier = Modifier.widthIn(max = state.style.maxContentWidthDp.dp).fillMaxWidth()
                    ) {
                        Text(
                            text = mangaDetails.title,
                            color = palette.accent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = state.content?.title ?: chapter.name,
                            color = palette.foreground,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.novel_reading_time, state.wordCount, state.readingMinutes) +
                                (chapter.scanlator?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
                            color = palette.secondary,
                            fontSize = 11.sp
                        )
                        Spacer(Modifier.height(18.dp))
                    }
                }

                val blocks = state.content?.blocks.orEmpty()
                itemsIndexed(
                    items = blocks,
                    key = { index, _ -> "b_$index" }
                ) { _, block ->
                    NovelBlockView(
                        block = block,
                        style = state.style,
                        palette = palette,
                        modifier = Modifier.widthIn(max = state.style.maxContentWidthDp.dp)
                    )
                }

                item(key = "novel_footer") {
                    Column(
                        modifier = Modifier.widthIn(max = state.style.maxContentWidthDp.dp).fillMaxWidth()
                            .padding(top = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (state.hasPrevious) {
                                TextButton(onClick = { viewModel.goPrevious() }) {
                                    Text("← " + (state.previousName ?: "Önceki bölüm"), color = palette.accent, fontSize = 12.sp)
                                }
                            }
                            Spacer(Modifier.weight(1f))
                            if (state.hasNext) {
                                TextButton(onClick = { viewModel.goNext() }) {
                                    Text((state.nextName ?: "Sonraki bölüm") + " →", color = palette.accent, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        // ─── İnce ilerleme çubuğu ────────────────────────────────────────────
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(2.dp)
                .background(palette.accent.copy(alpha = progress))
        )

        // ─── Üst bar ────────────────────────────────────────────────────────
        if (menuVisible) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(palette.background.copy(alpha = 0.92f))
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri", tint = palette.foreground)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = mangaDetails.title,
                        color = palette.foreground,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = chapter.name,
                        color = palette.secondary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = { viewModel.toggleJustified() }) {
                    Icon(Icons.AutoMirrored.Rounded.FormatAlignLeft, contentDescription = "Hizalama", tint = palette.foreground)
                }
                IconButton(onClick = { showAppearance = true }) {
                    Icon(Icons.Rounded.TextFields, contentDescription = "Görünüm", tint = palette.foreground)
                }
                IconButton(onClick = { menuVisible = false }) {
                    Icon(Icons.Rounded.Close, contentDescription = "Menüyü kapat", tint = palette.foreground)
                }
            }
        } else {
            // Menüsüz modda ortaya dokununca menü açılır.
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .size(56.dp)
                    .clickable { menuVisible = true }
            )
        }

        // ─── Alt gezinme ────────────────────────────────────────────────────
        if (menuVisible) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(palette.background.copy(alpha = 0.92f))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { viewModel.goPrevious() }, enabled = state.hasPrevious) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Önceki bölüm",
                        tint = if (state.hasPrevious) palette.foreground else palette.secondary.copy(0.4f)
                    )
                }
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${(progress * 100).toInt()}%", color = palette.foreground, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(state.style.theme.label(), color = palette.secondary, fontSize = 10.sp)
                }
                IconButton(onClick = { viewModel.goNext() }, enabled = state.hasNext) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Sonraki bölüm",
                        tint = if (state.hasNext) palette.foreground else palette.secondary.copy(0.4f)
                    )
                }
            }
        }
    }

    // ─── Görünüm paneli ─────────────────────────────────────────────────────
    if (showAppearance) {
        com.kitsugi.animelist.ui.components.KitsugiSheetOrDialog(onDismiss = { showAppearance = false }) {
            NovelAppearancePanel(
                style = state.style,
                palette = palette,
                onChange = viewModel::setStyle
            )
        }
    }
}

@Composable
private fun NovelBlockView(
    block: NovelBlock,
    style: NovelReaderStyle,
    palette: NovelPalette,
    modifier: Modifier = Modifier
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val maxWidth = with(density) { style.maxContentWidthDp.dp }
    when (block.style) {
        NovelBlockStyle.Image -> {
            // Light novel illüstrasyonları: genişliğe sığdır, yüksekliği koru.
            coil3.compose.AsyncImage(
                model = block.text,
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.FillWidth,
                modifier = modifier
                    .fillMaxWidth()
                    .heightIn(min = 80.dp)
                    .padding(vertical = 10.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
        }
        else -> Text(
            text = block.text,
            color = if (block.style == NovelBlockStyle.Quote) palette.secondary else palette.foreground,
            fontSize = when (block.style) {
                NovelBlockStyle.Title -> (style.fontSizeSp + 5).sp
                NovelBlockStyle.Heading -> (style.fontSizeSp + 2).sp
                else -> style.fontSizeSp.sp
            },
            fontWeight = when (block.style) {
                NovelBlockStyle.Title, NovelBlockStyle.Heading -> FontWeight.Bold
                else -> FontWeight.Normal
            },
            fontStyle = if (block.style == NovelBlockStyle.Quote) FontStyle.Italic else FontStyle.Normal,
            lineHeight = (style.fontSizeSp * style.lineHeightMultiplier).sp,
            textAlign = when {
                style.rtl -> TextAlign.Right
                style.justified -> TextAlign.Justify
                else -> TextAlign.Start
            },
            modifier = modifier
                .fillMaxWidth()
                .padding(
                    top = if (block.style == NovelBlockStyle.Paragraph && style.paragraphSpacing) 2.dp else 8.dp,
                    bottom = if (block.style == NovelBlockStyle.Paragraph && style.paragraphSpacing) 10.dp else 4.dp
                )
        )
    }
}

/** Okuma paneli: yazı boyutu, satır aralığı, genişlik, tema, hizalama. */
@Composable
private fun NovelAppearancePanel(
    style: NovelReaderStyle,
    palette: NovelPalette,
    onChange: (NovelReaderStyle) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Text(stringResource(R.string.novel_appearance), color = palette.foreground, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(14.dp))

        Text(stringResource(R.string.novel_font_size) + ": ${style.fontSizeSp} sp", color = palette.secondary, fontSize = 12.sp)
        androidx.compose.material3.Slider(
            value = style.fontSizeSp.toFloat(),
            onValueChange = { onChange(style.copy(fontSizeSp = it.toInt().coerceIn(12, 30))) },
            valueRange = 12f..30f,
            steps = 17
        )

        Text(stringResource(R.string.novel_line_height) + ": " + String.format(java.util.Locale.US, "%.1f", style.lineHeightMultiplier) + "×", color = palette.secondary, fontSize = 12.sp)
        androidx.compose.material3.Slider(
            value = style.lineHeightMultiplier,
            onValueChange = { onChange(style.copy(lineHeightMultiplier = (it * 10).toInt() / 10f)) },
            valueRange = 1.2f..2.4f,
            steps = 11
        )

        Text(stringResource(R.string.novel_content_width) + ": ${style.maxContentWidthDp} dp", color = palette.secondary, fontSize = 12.sp)
        androidx.compose.material3.Slider(
            value = style.maxContentWidthDp.toFloat(),
            onValueChange = { onChange(style.copy(maxContentWidthDp = it.toInt().coerceIn(480, 1200))) },
            valueRange = 480f..1200f,
            steps = 11
        )

        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.novel_appearance), color = palette.secondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
            NovelTheme.entries.forEach { theme ->
                val selected = style.theme == theme
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (selected) palette.accent.copy(0.22f) else palette.background.copy(0.5f))
                        .clickable { onChange(style.copy(theme = theme)) }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(theme.label(), color = if (selected) palette.accent else palette.foreground, fontSize = 11.sp)
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onChange(style.copy(justified = !style.justified)) }
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.novel_justify), color = palette.foreground, fontSize = 13.sp, modifier = Modifier.weight(1f))
            androidx.compose.material3.Switch(checked = style.justified, onCheckedChange = { onChange(style.copy(justified = it)) })
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onChange(style.copy(rtl = !style.rtl)) }
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.novel_rtl), color = palette.foreground, fontSize = 13.sp, modifier = Modifier.weight(1f))
            androidx.compose.material3.Switch(checked = style.rtl, onCheckedChange = { onChange(style.copy(rtl = it)) })
        }
        Spacer(Modifier.height(10.dp))
    }
}

// ─── Palet ──────────────────────────────────────────────────────────────────

data class NovelPalette(
    val background: Color,
    val foreground: Color,
    val secondary: Color,
    val accent: Color
)

@Composable
fun NovelTheme.label(): String = when (this) {
    NovelTheme.Dark -> stringResource(R.string.novel_theme_dark)
    NovelTheme.Sepia -> stringResource(R.string.novel_theme_sepia)
    NovelTheme.Light -> stringResource(R.string.novel_theme_light)
    NovelTheme.System -> stringResource(R.string.novel_theme_system)
}

@Composable
fun NovelTheme.palette(): NovelPalette = when (this) {
    NovelTheme.Dark -> NovelPalette(Color(0xFF0B0B0F), Color(0xFFE7E7EC), Color(0xFF9A9AA8), Color(0xFF7C4DFF))
    NovelTheme.Sepia -> NovelPalette(Color(0xFFF3E7D3), Color(0xFF3B2F22), Color(0xFF7A6A55), Color(0xFF9A6B2F))
    NovelTheme.Light -> NovelPalette(Color(0xFFFFFFFF), Color(0xFF14161A), Color(0xFF6B7280), Color(0xFF4F46E5))
    NovelTheme.System -> NovelPalette(KitsugiColors.Background, KitsugiColors.TextPrimary, KitsugiColors.TextSecondary, KitsugiColors.Accent)
}

// ─── ViewModel ──────────────────────────────────────────────────────────────

/**
 * Novel bölümünü indirir/ayrıştırır, komşu bölümlere geçişi yönetir ve
 * görünüm tercihlerini bellekte tutar.
 */
class NovelReaderViewModel(
    private val context: Context,
    val source: MangaSource,
    val mangaDetails: MangaDetails,
    initialChapter: MangaChapter
) : ViewModel() {

    data class UiState(
        val chapter: MangaChapter = initialChapter,
        val isLoading: Boolean = true,
        val content: NovelChapterContent? = null,
        val style: NovelReaderStyle = NovelReaderStyle(),
        val chapterList: List<MangaChapter> = emptyList(),
        val wordCount: Int = 0,
        val readingMinutes: Int = 1
    ) {
        val hasNext: Boolean
            get() {
                val idx = chapterList.indexOfFirst { it.url == chapter.url }
                return idx > 0
            }
        val hasPrevious: Boolean
            get() {
                val idx = chapterList.indexOfFirst { it.url == chapter.url }
                return idx >= 0 && idx < chapterList.size - 1
            }
        val nextName: String? get() = chapterList.getOrNull(chapterList.indexOfFirst { it.url == chapter.url } - 1)?.name
        val previousName: String? get() {
            val idx = chapterList.indexOfFirst { it.url == chapter.url }
            return if (idx >= 0) chapterList.getOrNull(idx + 1)?.name else null
        }
    }

    private val parser = NovelChapterParser()
    private val settingsDataStore = com.kitsugi.animelist.data.settings.SettingsDataStore(context)
    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        restoreStyle()
        loadChapter(initialChapter)
        loadChapterList()
    }

    /** Kullanıcının son seçtiği tipografi/tema ayarlarını geri yükler. */
    private fun restoreStyle() {
        viewModelScope.launch {
            runCatching {
                settingsDataStore.settingsFlow.first().let { settings ->
                    _uiState.update {
                        it.copy(
                            style = NovelReaderStyle(
                                fontSizeSp = settings.novelFontSize,
                                lineHeightMultiplier = settings.novelLineHeight,
                                maxContentWidthDp = settings.novelMaxWidth,
                                theme = NovelTheme.fromId(settings.novelTheme),
                                justified = settings.novelJustified,
                                rtl = settings.novelRtl
                            )
                        )
                    }
                }
            }
        }
    }

    fun reload() = loadChapter(_uiState.value.chapter)

    fun goNext() {
        val state = _uiState.value
        val idx = state.chapterList.indexOfFirst { it.url == state.chapter.url }
        if (idx > 0) loadChapter(state.chapterList[idx - 1])
    }

    fun goPrevious() {
        val state = _uiState.value
        val idx = state.chapterList.indexOfFirst { it.url == state.chapter.url }
        if (idx >= 0 && idx < state.chapterList.size - 1) loadChapter(state.chapterList[idx + 1])
    }

    fun setStyle(style: NovelReaderStyle) {
        _uiState.update { it.copy(style = style) }
        viewModelScope.launch {
            settingsDataStore.setNovelStyle(
                fontSizeSp = style.fontSizeSp,
                lineHeight = style.lineHeightMultiplier,
                maxWidthDp = style.maxContentWidthDp,
                theme = style.theme.name,
                justified = style.justified,
                rtl = style.rtl
            )
        }
    }

    fun toggleJustified() {
        _uiState.update { it.copy(style = it.style.copy(justified = !it.style.justified)) }
    }

    private fun loadChapter(chapter: MangaChapter) {
        viewModelScope.launch {
            _uiState.update { it.copy(chapter = chapter, isLoading = true, content = null) }
            val content = parser.load(url = chapter.url, referer = mangaDetails.url)
            val words = NovelChapterParser.wordCountOf(content.blocks)
            _uiState.update {
                it.copy(
                    isLoading = false,
                    content = content,
                    wordCount = words,
                    readingMinutes = NovelChapterParser.estimateReadingMinutes(words)
                )
            }
        }
    }

    private fun loadChapterList() {
        viewModelScope.launch {
            runCatching { source.fetchChapterList(mangaDetails.url) }
                .onSuccess { list -> _uiState.update { it.copy(chapterList = list) } }
        }
    }

    class Factory(
        private val context: Context,
        private val source: MangaSource,
        private val mangaDetails: MangaDetails,
        private val initialChapter: MangaChapter
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            NovelReaderViewModel(context, source, mangaDetails, initialChapter) as T
    }
}
