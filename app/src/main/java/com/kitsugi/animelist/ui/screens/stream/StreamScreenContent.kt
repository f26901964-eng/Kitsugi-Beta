package com.kitsugi.animelist.ui.screens.stream

import com.kitsugi.animelist.ui.theme.gradient.Checkbox
import com.kitsugi.animelist.ui.theme.gradient.CircularProgressIndicator
import com.kitsugi.animelist.ui.theme.gradient.FilterChip
import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.Text
import com.kitsugi.animelist.R
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.ui.components.KitsugiButton

import android.content.SharedPreferences
import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.ExperimentalFoundationApi
import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.theme.gradient.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Launch
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.SubcomposeAsyncImage
import com.kitsugi.animelist.data.repository.StreamProbe
import com.kitsugi.animelist.data.repository.StreamSource
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.utils.tvClickable

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StreamScreenContent(
    title: String,
    posterUrl: String?,
    episode: Int,
    season: Int,
    isMovie: Boolean = false,
    isDownloadMode: Boolean = false,
    imdbId: String?,
    accentColor: Color,
    addonStates: List<AddonFetchState>,
    allStreams: List<StreamSource>,
    isResolvingId: Boolean,
    idResolveFailed: Boolean,
    isAnyLoading: Boolean,
    selectedAddonFilter: String?,
    onAddonFilterChange: (String?) -> Unit,
    onStreamSelected: (StreamSource) -> Unit,
    onDownloadSelected: (StreamSource) -> Unit,
    onBack: () -> Unit,
    resolvingSource: StreamSource?,
    resolvingError: String?,
    onResolvingErrorDismiss: () -> Unit,
    onCancelResolving: () -> Unit = {},
    pendingPlayAction: PendingPlayAction?,
    onPendingDismiss: () -> Unit,
    playerPrefs: SharedPreferences,
    launchPlayer: (StreamSource, String, String) -> Unit,
    onPendingDone: () -> Unit,
    onRememberChoice: (String) -> Unit = {},
    onOpenSettings: (() -> Unit)? = null,
    onVerifyPlugin: ((addonName: String) -> Unit)? = null,
    onRetryPlugin: ((addonName: String) -> Unit)? = null,
    onOpenHistory: (() -> Unit)? = null,
    onOpenDownloads: (() -> Unit)? = null,
    onManualSearchClick: (() -> Unit)? = null
) {
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    // ── Local Search & Tag Filtering State ─────────────────────────────────────
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedTagFilter by rememberSaveable { mutableStateOf<String?>(null) }

    val sortedAddonStates = remember(addonStates) {
        addonStates.sortedWith(
            compareByDescending<AddonFetchState> { it.streams.isNotEmpty() }
                .thenByDescending { it.isLoading }
        )
    }

    // ── Real-time Synchronized Stream & Addon Filtering ─────────────────────────
    val matchingAddonStates = remember(sortedAddonStates, selectedAddonFilter, searchQuery, selectedTagFilter) {
        val query = searchQuery.trim().lowercase()
        val tag = selectedTagFilter?.lowercase()

        // PERF: Aktif filtre yokken akış başına kalite/dil çözümlemesi (regex + önbellek
        // sorgusu) hiç yapılmaz. Kaynaklar çekilirken `addonStates` her eklenti bittiğinde
        // güncelleniyor; eskiden her güncellemede 60+ akış baştan taranıyor ve liste
        // "veri çekerken" kilitleniyordu. Filtresiz durumda liste aynen geçirilir.
        if (query.isEmpty() && tag == null) {
            return@remember sortedAddonStates.filter { addonState ->
                (selectedAddonFilter == null || addonState.addonName == selectedAddonFilter) &&
                    (addonState.isLoading || addonState.error != null || addonState.streams.isNotEmpty())
            }
        }

        sortedAddonStates.mapNotNull { addonState ->
            if (selectedAddonFilter != null && addonState.addonName != selectedAddonFilter) {
                return@mapNotNull null
            }

            val matchingStreams = addonState.streams.filter { stream ->
                // Filtreleme de rozetlerle aynı kanıta dayalı bilgiyi kullanır.
                val qualityInfo = resolveStreamQuality(stream, StreamProbe.cached(stream.url))
                val qual = qualityInfo.label.orEmpty()
                val langInfo = resolveStreamLangInfo(stream, StreamProbe.cached(stream.url))
                val langType = langInfo.kind.toLangType()

                val matchesQuery = query.isEmpty() ||
                    stream.name.lowercase().contains(query) ||
                    stream.title.lowercase().contains(query) ||
                    stream.addonName.lowercase().contains(query) ||
                    (stream.url != null && stream.url.lowercase().contains(query)) ||
                    qual.lowercase().contains(query) ||
                    langType.label.lowercase().contains(query) ||
                    ((query == "tr" || query == "türkçe" || query == "turkce") && (langType == StreamLangType.SUB || langType == StreamLangType.DUB || langType == StreamLangType.DUAL))

                val matchesTag = when (tag) {
                    "altyazı" -> langType == StreamLangType.SUB || langType == StreamLangType.DUAL ||
                        langInfo.subtitleLanguages.isNotEmpty()
                    "dublaj" -> langType == StreamLangType.DUB || langType == StreamLangType.DUAL
                    "1080p" -> qualityInfo.height != null && qualityInfo.height >= 1080
                    "2160p" -> qualityInfo.height != null && qualityInfo.height >= 2160
                    "720p" -> qualityInfo.height == 720
                    "debrid" -> stream.infoHash != null || stream.url?.contains("magnet") == true
                    else -> true
                }

                matchesQuery && matchesTag
            }

            if (matchingStreams.isEmpty() && !addonState.isLoading && addonState.error == null) {
                null
            } else {
                addonState.copy(streams = matchingStreams)
            }
        }
    }

    var qualitySort by rememberSaveable { mutableStateOf(false) }
    val filteredAddonStates = remember(matchingAddonStates, qualitySort) {
        if (!qualitySort) matchingAddonStates else matchingAddonStates.map { state ->
            state.copy(streams = state.streams.sortedByDescending {
                resolveStreamQuality(it, StreamProbe.cached(it.url)).height ?: -1
            })
        }
    }
    val totalFilteredStreamsCount = remember(filteredAddonStates) {
        filteredAddonStates.sumOf { it.streams.size }
    }
    val hasActiveFilter = searchQuery.isNotBlank() || selectedTagFilter != null || selectedAddonFilter != null

    CompositionLocalProvider(LocalStreamSort provides StreamSortState(qualitySort) { qualitySort = !qualitySort }) {
    Box(modifier = Modifier.fillMaxSize().background(KitsugiColors.Background)) {
        if (!posterUrl.isNullOrBlank()) {
            AsyncImage(
                model = posterUrl, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().alpha(0.12f)
            )
        }
        Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(KitsugiColors.Background.copy(alpha = 0.65f), KitsugiColors.Background))))

        if (isLandscape) {
            LandscapeLayout(
                title = title, posterUrl = posterUrl, episode = episode, season = season, isMovie = isMovie,
                imdbId = imdbId, accentColor = accentColor, addonStates = sortedAddonStates,
                allStreams = allStreams, selectedAddonFilter = selectedAddonFilter,
                searchQuery = searchQuery, onQueryChange = { searchQuery = it },
                selectedTagFilter = selectedTagFilter, onTagSelect = { selectedTagFilter = it },
                onAddonFilterChange = onAddonFilterChange, onBack = onBack,
                isResolvingId = isResolvingId, idResolveFailed = idResolveFailed, isAnyLoading = isAnyLoading,
                filteredAddonStates = filteredAddonStates, totalFilteredStreamsCount = totalFilteredStreamsCount,
                hasActiveFilter = hasActiveFilter, onStreamSelected = onStreamSelected,
                onDownloadSelected = onDownloadSelected,
                onVerifyPlugin = onVerifyPlugin, onOpenSettings = onOpenSettings,
                onRetryPlugin = onRetryPlugin,
                onOpenHistory = onOpenHistory,
                onOpenDownloads = onOpenDownloads,
                onManualSearchClick = onManualSearchClick
            )
        } else {
            PortraitLayout(
                title = title, posterUrl = posterUrl, episode = episode, season = season, isMovie = isMovie,
                imdbId = imdbId, accentColor = accentColor, addonStates = sortedAddonStates,
                allStreams = allStreams, selectedAddonFilter = selectedAddonFilter,
                searchQuery = searchQuery, onQueryChange = { searchQuery = it },
                selectedTagFilter = selectedTagFilter, onTagSelect = { selectedTagFilter = it },
                onAddonFilterChange = onAddonFilterChange, onBack = onBack,
                isResolvingId = isResolvingId, idResolveFailed = idResolveFailed, isAnyLoading = isAnyLoading,
                filteredAddonStates = filteredAddonStates, totalFilteredStreamsCount = totalFilteredStreamsCount,
                hasActiveFilter = hasActiveFilter, onStreamSelected = onStreamSelected,
                onDownloadSelected = onDownloadSelected,
                onVerifyPlugin = onVerifyPlugin, onOpenSettings = onOpenSettings,
                onRetryPlugin = onRetryPlugin,
                onOpenHistory = onOpenHistory,
                onOpenDownloads = onOpenDownloads,
                onManualSearchClick = onManualSearchClick
            )
        }

        // Resolving overlay
        if (resolvingSource != null) {
            BackHandler(enabled = true) {
                onCancelResolving()
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.75f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onCancelResolving() },
                contentAlignment = Alignment.Center
            ) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = KitsugiColors.Surface),
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .widthIn(max = 420.dp)
                        .padding(24.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { /* Consume clicks inside card */ }
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            IconButton(
                                onClick = onCancelResolving,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = stringResource(R.string.kapat),
                                    tint = KitsugiColors.TextSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            CircularProgressIndicator(
                                color = accentColor,
                                strokeWidth = 3.dp,
                                modifier = Modifier
                                    .size(40.dp)
                                    .align(Alignment.Center)
                            )
                        }

                        val isDebrid = resolvingSource.infoHash != null || resolvingSource.url?.contains("magnet") == true
                        val message = if (isDebrid) stringResource(R.string.debrid_uzerinden_cozumleniyor) else stringResource(R.string.akis_baglantisi_cozuluyor)
                        Text(
                            text = message,
                            color = KitsugiColors.TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = resolvingSource.title,
                            color = KitsugiColors.TextSecondary,
                            fontSize = 12.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )

                        Spacer(Modifier.height(4.dp))

                        OutlinedButton(
                            onClick = onCancelResolving,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, KitsugiColors.SurfaceStrong),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = KitsugiColors.TextPrimary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(42.dp)
                                .tvClickable(shape = RoundedCornerShape(12.dp)) { onCancelResolving() }
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = KitsugiColors.TextSecondary
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.settings_cancel),
                                color = KitsugiColors.TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }

        // Error dialog
        if (resolvingError != null) {
            AlertDialog(
                onDismissRequest = onResolvingErrorDismiss,
                title = { Text(stringResource(R.string.hata), color = KitsugiColors.TextPrimary) },
                text = { Text(resolvingError, color = KitsugiColors.TextSecondary) },
                confirmButton = { TextButton(onClick = onResolvingErrorDismiss) { Text(stringResource(R.string.action_ok), color = accentColor) } },
                containerColor = KitsugiColors.Surface
            )
        }

        // Player picker dialog
        if (pendingPlayAction != null) {
            val playAction = pendingPlayAction
            var rememberChoice by remember { mutableStateOf(false) }
            AlertDialog(
                onDismissRequest = onPendingDismiss,
                containerColor = KitsugiColors.Surface, titleContentColor = KitsugiColors.TextPrimary,
                textContentColor = KitsugiColors.TextSecondary, shape = RoundedCornerShape(26.dp),
                title = { Text(stringResource(R.string.inline_choose_a_player_70a8dc8), color = KitsugiColors.TextPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
                text = {
                    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.inline_which_player_would_you_like_a9168aa), color = KitsugiColors.TextSecondary)
                        Spacer(Modifier.height(4.dp))
                        PlayerPickerCard(
                            icon = { Icon(Icons.Rounded.PlayCircle, null, tint = accentColor) },
                            label = stringResource(R.string.player_engine_internal_exo), sub = stringResource(R.string.uygulama_ici_kesintisiz_oynatma),
                            accentColor = accentColor,
                            onClick = {
                                if (rememberChoice) onRememberChoice("exoplayer")
                                onPendingDone()
                                launchPlayer(playAction.source, playAction.resolvedUrl, "exoplayer")
                            }
                        )
                        PlayerPickerCard(
                            icon = { Icon(Icons.AutoMirrored.Rounded.Launch, null, tint = accentColor) },
                            label = stringResource(R.string.harici_oynatici_chooser), sub = stringResource(R.string.mx_player_mi_video_veya_digerleri),
                            accentColor = accentColor,
                            onClick = {
                                if (rememberChoice) onRememberChoice("mpv")
                                onPendingDone()
                                launchPlayer(playAction.source, playAction.resolvedUrl, "mpv")
                            }
                        )
                        Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).tvClickable(shape = RoundedCornerShape(12.dp)) { rememberChoice = !rememberChoice }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Checkbox(checked = rememberChoice, onCheckedChange = { rememberChoice = it }, colors = CheckboxDefaults.colors(checkedColor = accentColor, uncheckedColor = KitsugiColors.TextMuted, checkmarkColor = KitsugiColors.Surface))
                            Text(stringResource(R.string.inline_remember_my_choice_a65f9af), color = KitsugiColors.TextPrimary)
                        }
                    }
                },
                confirmButton = {},
                dismissButton = { TextButton(onClick = onPendingDismiss) { Text(stringResource(R.string.settings_cancel), color = accentColor) } }
            )
        }
    }
}
}

@Composable
private fun PlayerPickerCard(icon: @Composable () -> Unit, label: String, sub: String, accentColor: Color, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().tvClickable(shape = RoundedCornerShape(16.dp)) { onClick() }, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = KitsugiColors.SurfaceStrong.copy(alpha = 0.5f))) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(modifier = Modifier.size(36.dp).clip(RoundedCornerShape(18.dp)).background(accentColor.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) { icon() }
            Column { Text(label, color = KitsugiColors.TextPrimary, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold); Text(sub, color = KitsugiColors.TextSecondary, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun StreamSearchBar(
    searchQuery: String,
    onQueryChange: (String) -> Unit,
    selectedTagFilter: String?,
    onTagSelect: (String?) -> Unit,
    accentColor: Color
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onQueryChange,
            placeholder = {
                Text(
                    stringResource(R.string.kaynak_eklenti_veya_video_ara_dizilla_sibnet_dublaj),
                    color = KitsugiColors.TextMuted,
                    fontSize = 12.sp
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null,
                    tint = if (searchQuery.isNotBlank()) accentColor else KitsugiColors.TextMuted,
                    modifier = Modifier.size(20.dp)
                )
            },
            trailingIcon = {
                if (searchQuery.isNotBlank()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.temizle),
                            tint = KitsugiColors.TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = KitsugiColors.SurfaceStrong.copy(alpha = 0.5f),
                unfocusedContainerColor = KitsugiColors.Surface.copy(alpha = 0.3f),
                focusedBorderColor = accentColor,
                unfocusedBorderColor = KitsugiColors.SurfaceStrong,
                focusedTextColor = KitsugiColors.TextPrimary,
                unfocusedTextColor = KitsugiColors.TextPrimary
            ),
            modifier = Modifier.fillMaxWidth().height(50.dp)
        )

        val tags = listOf(
            "altyazı" to stringResource(R.string.altyazi),
            "Dublaj" to "🎙️ Dublaj",
            "2160p" to "4K",
            "1080p" to "1080p+",
            "720p" to "🎬 720p",
            "debrid" to "⚡ Debrid"
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(horizontal = 2.dp)
        ) {
            items(tags) { (tagKey, tagLabel) ->
                val isSelected = selectedTagFilter == tagKey
                FilterChip(
                    selected = isSelected,
                    onClick = { onTagSelect(if (isSelected) null else tagKey) },
                    label = { Text(tagLabel, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = accentColor.copy(alpha = 0.25f),
                        selectedLabelColor = accentColor,
                        containerColor = KitsugiColors.SurfaceStrong.copy(alpha = 0.3f),
                        labelColor = KitsugiColors.TextSecondary
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = isSelected,
                        borderColor = KitsugiColors.SurfaceStrong,
                        selectedBorderColor = accentColor.copy(alpha = 0.6f)
                    ),
                    shape = RoundedCornerShape(10.dp)
                )
            }
        }
    }
}

@Composable
private fun FilteredEmptyState(
    searchQuery: String,
    tagFilter: String?,
    accentColor: Color,
    onClearFilters: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = KitsugiColors.SurfaceStrong.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.SearchOff,
                contentDescription = null,
                tint = KitsugiColors.TextMuted,
                modifier = Modifier.size(40.dp)
            )
            Text(
                text = stringResource(R.string.inline_no_source_matching_your_search_1a6da33),
                color = KitsugiColors.TextPrimary,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            val desc = buildString {
                if (searchQuery.isNotBlank()) append("\"$searchQuery\" ")
                if (tagFilter != null) append("[$tagFilter] ")
                append("kriterlerine uygun video akışı veya eklenti mevcut değil.")
            }
            Text(
                text = desc,
                color = KitsugiColors.TextSecondary,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(4.dp))
            KitsugiButton(
                onClick = onClearFilters,
                colors = ButtonDefaults.buttonColors(containerColor = accentColor.copy(alpha = 0.2f), contentColor = accentColor),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Rounded.FilterAltOff, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Filtreleri Temizle", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LandscapeLayout(
    title: String, posterUrl: String?, episode: Int, season: Int, isMovie: Boolean = false,
    isDownloadMode: Boolean = false, imdbId: String?,
    accentColor: Color, addonStates: List<AddonFetchState>, allStreams: List<StreamSource>,
    selectedAddonFilter: String?, searchQuery: String, onQueryChange: (String) -> Unit,
    selectedTagFilter: String?, onTagSelect: (String?) -> Unit,
    onAddonFilterChange: (String?) -> Unit, onBack: () -> Unit,
    isResolvingId: Boolean, idResolveFailed: Boolean, isAnyLoading: Boolean,
    filteredAddonStates: List<AddonFetchState>, totalFilteredStreamsCount: Int, hasActiveFilter: Boolean,
    onStreamSelected: (StreamSource) -> Unit, onDownloadSelected: (StreamSource) -> Unit, onVerifyPlugin: ((String) -> Unit)?, onOpenSettings: (() -> Unit)?,
    onRetryPlugin: ((String) -> Unit)? = null,
    onOpenHistory: (() -> Unit)? = null,
    onOpenDownloads: (() -> Unit)? = null,
    onManualSearchClick: (() -> Unit)? = null
) {
    val listState = rememberLazyListState()

    Row(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
        // Left Column: Fixed compact detail sidebar & back navigation
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .widthIn(min = 240.dp, max = 300.dp)
                .fillMaxWidth(0.34f)
                .verticalScroll(rememberScrollState())
                .background(KitsugiColors.Surface.copy(alpha = 0.2f))
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    colors = IconButtonDefaults.iconButtonColors(containerColor = KitsugiColors.SurfaceStrong.copy(alpha = 0.5f)),
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Geri", tint = KitsugiColors.TextPrimary, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.action_back),
                    color = KitsugiColors.TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (onOpenDownloads != null) {
                    IconButton(
                        onClick = onOpenDownloads,
                        colors = IconButtonDefaults.iconButtonColors(containerColor = KitsugiColors.SurfaceStrong.copy(alpha = 0.5f)),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Rounded.Download, "İndirmeler", tint = KitsugiColors.TextSecondary, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(4.dp))
                }
                if (onOpenHistory != null) {
                    IconButton(
                        onClick = onOpenHistory,
                        colors = IconButtonDefaults.iconButtonColors(containerColor = KitsugiColors.SurfaceStrong.copy(alpha = 0.5f)),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Rounded.History, "İzleme Geçmişi", tint = KitsugiColors.TextSecondary, modifier = Modifier.size(18.dp))
                    }
                }
                if (onManualSearchClick != null) {
                    Spacer(Modifier.width(4.dp))
                    IconButton(
                        onClick = onManualSearchClick,
                        colors = IconButtonDefaults.iconButtonColors(containerColor = KitsugiColors.SurfaceStrong.copy(alpha = 0.5f)),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Rounded.Search, "Manuel Arama", tint = KitsugiColors.TextSecondary, modifier = Modifier.size(18.dp))
                    }
                }
            }


            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    title,
                    color = KitsugiColors.TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    if (isMovie) {
                        if (isDownloadMode) stringResource(R.string.ui2_movie_download) else stringResource(R.string.bangumi_tag_movie)
                    } else {
                        if (isDownloadMode) stringResource(R.string.ui2_stream_download_season_episode, season, episode) else stringResource(R.string.ui2_stream_season_episode, season, episode)
                    },
                    color = KitsugiColors.TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center
                )
                imdbId?.let { ImdbBadge(it) }
            }
            StreamSearchBar(searchQuery, onQueryChange, selectedTagFilter, onTagSelect, accentColor)
            AddonChipsRow(addonStates, allStreams, selectedAddonFilter, searchQuery, accentColor, onAddonFilterChange)
        }

        // Results retain their own scroll position while the control rail stays accessible.
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxHeight().weight(1f).padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp)
        ) {
            // Content Items (Streams / Skeleton / Empty state / Error state)
            when {
                isResolvingId -> {
                    item(key = "land_resolving_id") { ResolvingIdState() }
                }
                addonStates.isEmpty() && !isResolvingId -> {
                    item(key = "land_no_addons") { NoAddonsState(onOpenSettings = onOpenSettings) }
                }
                !isAnyLoading && allStreams.isEmpty() -> {
                    item(key = "land_empty_streams") { EmptyStreamsState(onOpenSettings = onOpenSettings) }
                }
                !isAnyLoading && totalFilteredStreamsCount == 0 && hasActiveFilter -> {
                    item(key = "land_filtered_empty") {
                        FilteredEmptyState(
                            searchQuery = searchQuery,
                            tagFilter = selectedTagFilter,
                            accentColor = accentColor,
                            onClearFilters = {
                                onQueryChange("")
                                onTagSelect(null)
                                onAddonFilterChange(null)
                            }
                        )
                    }
                }
                else -> {
                    renderStreamResultsItems(
                        addonStates = filteredAddonStates,
                        accentColor = accentColor,
                        onStreamSelected = onStreamSelected,
                        onDownloadSelected = onDownloadSelected,
                        onVerifyPlugin = onVerifyPlugin,
                        onOpenSettings = onOpenSettings,
                        onRetryPlugin = onRetryPlugin,
                        // Ölçüm yalnızca görüş alanındaki kartlarda yapılır (kasma önlemi).
                        listState = listState
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PortraitLayout(
    title: String, posterUrl: String?, episode: Int, season: Int, isMovie: Boolean = false,
    isDownloadMode: Boolean = false, imdbId: String?,
    accentColor: Color, addonStates: List<AddonFetchState>, allStreams: List<StreamSource>,
    selectedAddonFilter: String?, searchQuery: String, onQueryChange: (String) -> Unit,
    selectedTagFilter: String?, onTagSelect: (String?) -> Unit,
    onAddonFilterChange: (String?) -> Unit, onBack: () -> Unit,
    isResolvingId: Boolean, idResolveFailed: Boolean, isAnyLoading: Boolean,
    filteredAddonStates: List<AddonFetchState>, totalFilteredStreamsCount: Int, hasActiveFilter: Boolean,
    onStreamSelected: (StreamSource) -> Unit, onDownloadSelected: (StreamSource) -> Unit, onVerifyPlugin: ((String) -> Unit)?, onOpenSettings: (() -> Unit)?,
    onRetryPlugin: ((String) -> Unit)? = null,
    onOpenHistory: (() -> Unit)?,
    onOpenDownloads: (() -> Unit)? = null,
    onManualSearchClick: (() -> Unit)? = null
) {
    val listState = rememberLazyListState()
    var showFloatingHeader by remember { mutableStateOf(false) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }.collect { index ->
            if (!showFloatingHeader && index >= 2) showFloatingHeader = true
            else if (showFloatingHeader && index == 0) showFloatingHeader = false
        }
    }

    Box(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // Item 1: Top Navigation Header (Initial view)
            item(key = "top_header") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        colors = IconButtonDefaults.iconButtonColors(containerColor = KitsugiColors.SurfaceStrong.copy(alpha = 0.5f)),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Geri", tint = KitsugiColors.TextPrimary, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(title, color = KitsugiColors.TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            if (isMovie) {
                                if (isDownloadMode) stringResource(R.string.ui2_movie_download) else stringResource(R.string.bangumi_tag_movie)
                            } else {
                                if (isDownloadMode) stringResource(R.string.ui2_stream_download_season_episode, season, episode) else stringResource(R.string.ui2_stream_season_episode, season, episode)
                            },
                            color = KitsugiColors.TextSecondary, style = MaterialTheme.typography.bodySmall
                        )
                    }
                    imdbId?.let { ImdbBadge(it) }
                    if (onOpenDownloads != null) {
                        Spacer(Modifier.width(4.dp))
                        IconButton(
                            onClick = onOpenDownloads,
                            colors = IconButtonDefaults.iconButtonColors(containerColor = KitsugiColors.SurfaceStrong.copy(alpha = 0.5f)),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Rounded.Download, "İndirmeler", tint = KitsugiColors.TextSecondary, modifier = Modifier.size(18.dp))
                        }
                    }
                    if (onOpenHistory != null) {
                        Spacer(Modifier.width(4.dp))
                        IconButton(
                            onClick = onOpenHistory,
                            colors = IconButtonDefaults.iconButtonColors(containerColor = KitsugiColors.SurfaceStrong.copy(alpha = 0.5f)),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Rounded.History, "İzleme Geçmişi", tint = KitsugiColors.TextSecondary, modifier = Modifier.size(18.dp))
                        }
                    }
                    if (onManualSearchClick != null) {
                        Spacer(Modifier.width(4.dp))
                        IconButton(
                            onClick = onManualSearchClick,
                            colors = IconButtonDefaults.iconButtonColors(containerColor = KitsugiColors.SurfaceStrong.copy(alpha = 0.5f)),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Rounded.Search, "Manuel Arama", tint = KitsugiColors.TextSecondary, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            // Item 3: Stream Search Bar & Quick Tags
            item(key = "search_bar") {
                StreamSearchBar(
                    searchQuery = searchQuery,
                    onQueryChange = onQueryChange,
                    selectedTagFilter = selectedTagFilter,
                    onTagSelect = onTagSelect,
                    accentColor = accentColor
                )
            }

            // Sticky Header: Floating Bar (when scrolled) + Addon Chips Row
            stickyHeader(key = "addon_chips_sticky") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(KitsugiColors.Background.copy(alpha = 0.95f))
                        .padding(vertical = 4.dp)
                ) {
                    AnimatedVisibility(
                        visible = showFloatingHeader,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .padding(bottom = 6.dp)
                        ) {
                            IconButton(
                                onClick = onBack,
                                colors = IconButtonDefaults.iconButtonColors(containerColor = KitsugiColors.SurfaceStrong.copy(alpha = 0.6f)),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Geri", tint = KitsugiColors.TextPrimary, modifier = Modifier.size(18.dp))
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = title,
                                    color = KitsugiColors.TextPrimary,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (isMovie) {
                                        if (isDownloadMode) stringResource(R.string.ui2_movie_download) else stringResource(R.string.bangumi_tag_movie)
                                    } else {
                                        if (isDownloadMode) stringResource(R.string.ui2_stream_download_season_episode, season, episode) else stringResource(R.string.ui2_stream_season_episode, season, episode)
                                    },
                                    color = KitsugiColors.TextSecondary,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                            imdbId?.let { ImdbBadge(it) }
                        }
                    }

                    AddonChipsRow(
                        addonStates = addonStates,
                        allStreams = allStreams,
                        selectedAddonFilter = selectedAddonFilter,
                        searchQuery = searchQuery,
                        accentColor = accentColor,
                        onFilterChange = onAddonFilterChange
                    )
                    HorizontalDivider(color = KitsugiColors.SurfaceStrong.copy(alpha = 0.5f))
                }
            }

            // Content Items (Streams / Skeleton / Empty state / Error state)
            when {
                isResolvingId -> {
                    item(key = "resolving_id") { ResolvingIdState() }
                }
                addonStates.isEmpty() && !isResolvingId -> {
                    item(key = "no_addons") { NoAddonsState(onOpenSettings = onOpenSettings) }
                }
                !isAnyLoading && allStreams.isEmpty() -> {
                    item(key = "empty_streams") { EmptyStreamsState(onOpenSettings = onOpenSettings) }
                }
                !isAnyLoading && totalFilteredStreamsCount == 0 && hasActiveFilter -> {
                    item(key = "filtered_empty") {
                        FilteredEmptyState(
                            searchQuery = searchQuery,
                            tagFilter = selectedTagFilter,
                            accentColor = accentColor,
                            onClearFilters = {
                                onQueryChange("")
                                onTagSelect(null)
                                onAddonFilterChange(null)
                            }
                        )
                    }
                }
                else -> {
                    renderStreamResultsItems(
                        addonStates = filteredAddonStates,
                        accentColor = accentColor,
                        onStreamSelected = onStreamSelected,
                        onDownloadSelected = onDownloadSelected,
                        onVerifyPlugin = onVerifyPlugin,
                        onOpenSettings = onOpenSettings,
                        onRetryPlugin = onRetryPlugin,
                        // Ölçüm yalnızca görüş alanındaki kartlarda yapılır (kasma önlemi).
                        listState = listState
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddonChipsRow(
    addonStates: List<AddonFetchState>,
    allStreams: List<StreamSource>,
    selectedAddonFilter: String?,
    searchQuery: String,
    accentColor: Color,
    onFilterChange: (String?) -> Unit
) {
    var showPicker by remember { mutableStateOf(false) }
    var addonQuery by remember { mutableStateOf("") }
    val loadingCount = addonStates.count { it.isLoading }
    val readyCount = addonStates.count { it.streams.isNotEmpty() }
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Surface(
            onClick = { showPicker = true },
            shape = RoundedCornerShape(14.dp),
            color = KitsugiColors.SurfaceStrong,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Tune, null, tint = accentColor)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(selectedAddonFilter ?: stringResource(R.string.stream_all_sources),
                        color = KitsugiColors.TextPrimary, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(stringResource(R.string.stream_source_summary, readyCount, addonStates.size, allStreams.size),
                        color = KitsugiColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
                }
                if (loadingCount > 0) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = accentColor, strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Icon(Icons.Rounded.ExpandMore, stringResource(R.string.stream_choose_source), tint = accentColor)
            }
        }
        val sort = LocalStreamSort.current
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = sort.toggle, modifier = Modifier.weight(1f)) {
                Icon(Icons.Rounded.Sort, null, modifier = Modifier.size(16.dp), tint = accentColor)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(if (sort.byQuality) R.string.stream_sort_quality else R.string.stream_sort_default),
                    color = accentColor, style = MaterialTheme.typography.labelSmall)
            }
        if (selectedAddonFilter != null) {
            TextButton(onClick = { onFilterChange(null) }) {
                Text(stringResource(R.string.stream_all_sources), color = accentColor)
            }
        }
        }
    }
    if (showPicker) {
        ModalBottomSheet(
            onDismissRequest = { showPicker = false },
            containerColor = KitsugiColors.Background
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Text(stringResource(R.string.stream_choose_source), color = KitsugiColors.TextPrimary,
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = addonQuery, onValueChange = { addonQuery = it }, singleLine = true,
                    placeholder = { Text(stringResource(R.string.stream_search_addons)) },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    trailingIcon = {
                        if (addonQuery.isNotEmpty()) IconButton(onClick = { addonQuery = "" }) {
                            Icon(Icons.Rounded.Close, stringResource(R.string.temizle))
                        }
                    },
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = KitsugiColors.TextPrimary,
                        unfocusedTextColor = KitsugiColors.TextPrimary,
                        focusedBorderColor = accentColor)
                )
                val visible = remember(addonStates, addonQuery) {
                    addonStates.filter { it.addonName.contains(addonQuery.trim(), ignoreCase = true) }
                }
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 440.dp),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    item(key = "all") {
                        SourcePickerRow(stringResource(R.string.stream_all_sources),
                            stringResource(R.string.stream_video_count, allStreams.size),
                            selectedAddonFilter == null, accentColor) {
                            onFilterChange(null); showPicker = false
                        }
                    }
                    items(visible, key = { it.addonName }) { state ->
                        val status = when {
                            state.isLoading -> stringResource(R.string.stream_source_loading)
                            state.requiresVerification -> stringResource(R.string.stream_source_verify)
                            state.error != null -> stringResource(R.string.stream_source_error)
                            else -> stringResource(R.string.stream_video_count, state.streams.size)
                        }
                        SourcePickerRow(state.addonName, status,
                            selectedAddonFilter == state.addonName, accentColor) {
                            onFilterChange(state.addonName); showPicker = false
                        }
                    }
                    if (visible.isEmpty()) item {
                        Text(stringResource(R.string.stream_no_addon_match),
                            modifier = Modifier.padding(16.dp), color = KitsugiColors.TextSecondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun SourcePickerRow(
    name: String, status: String, selected: Boolean, accentColor: Color, onClick: () -> Unit
) {
    Surface(onClick = onClick, shape = RoundedCornerShape(12.dp),
        color = if (selected) accentColor.copy(alpha = 0.12f) else Color.Transparent,
        modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(name, color = KitsugiColors.TextPrimary, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(status, color = KitsugiColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
            if (selected) Icon(Icons.Rounded.Check, null, tint = accentColor)
        }
    }
}

@Composable
private fun ImdbBadge(imdbId: String) {
    Box(modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Color(0xFFF5C518)).padding(horizontal = 8.dp, vertical = 3.dp)) {
        Text(imdbId, color = Color.Black, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black)
    }
}


private data class StreamSortState(val byQuality: Boolean, val toggle: () -> Unit)
private val LocalStreamSort = compositionLocalOf { StreamSortState(false) {} }
