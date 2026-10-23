package com.kitsugi.animelist.ui.components

import com.kitsugi.animelist.ui.theme.gradient.CircularProgressIndicator
import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.Text

import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R
import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.utils.tvClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.theme.KitsugiColors
import kotlinx.coroutines.launch

data class TmdbLanguage(val code: String, val displayName: String)

val AVAILABLE_TMDB_LANGUAGES = listOf(
    TmdbLanguage("en", "English"),
    TmdbLanguage("tr", "Türkçe"),
    TmdbLanguage("ja", "日本語"),
    TmdbLanguage("fr", "Français"),
    TmdbLanguage("de", "Deutsch"),
    TmdbLanguage("es", "Español"),
    TmdbLanguage("it", "Italiano"),
    TmdbLanguage("ru", "Русский"),
    TmdbLanguage("zh", "中文")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KitsugiIntegrationsSettingsDialog(
    tmdbEnabled: Boolean,
    onTmdbEnabledChanged: (Boolean) -> Unit,
    tmdbApiKey: String,
    onTmdbApiKeyChanged: (String) -> Unit,
    tmdbModernHomeEnabled: Boolean,
    onTmdbModernHomeEnabledChanged: (Boolean) -> Unit,
    tmdbEnrichContinueWatching: Boolean,
    onTmdbEnrichContinueWatchingChanged: (Boolean) -> Unit,
    tmdbLanguage: String,
    onTmdbLanguageChanged: (String) -> Unit,
    tmdbUseArtwork: Boolean,
    onTmdbUseArtworkChanged: (Boolean) -> Unit,
    tmdbUseBasicInfo: Boolean,
    onTmdbUseBasicInfoChanged: (Boolean) -> Unit,
    tmdbUseDetails: Boolean,
    onTmdbUseDetailsChanged: (Boolean) -> Unit,
    tmdbUseReleaseDates: Boolean,
    onTmdbUseReleaseDatesChanged: (Boolean) -> Unit,
    tmdbUseCredits: Boolean,
    onTmdbUseCreditsChanged: (Boolean) -> Unit,
    tmdbUseProductions: Boolean,
    onTmdbUseProductionsChanged: (Boolean) -> Unit,
    tmdbUseNetworks: Boolean,
    onTmdbUseNetworksChanged: (Boolean) -> Unit,
    tmdbUseEpisodes: Boolean,
    onTmdbUseEpisodesChanged: (Boolean) -> Unit,
    tmdbUseTrailers: Boolean,
    onTmdbUseTrailersChanged: (Boolean) -> Unit,
    tmdbUseMoreLikeThis: Boolean,
    onTmdbUseMoreLikeThisChanged: (Boolean) -> Unit,
    tmdbUseCollections: Boolean,
    onTmdbUseCollectionsChanged: (Boolean) -> Unit,
    mdbListEnabled: Boolean,
    onMdbListEnabledChanged: (Boolean) -> Unit,
    mdbListApiKey: String,
    onMdbListApiKeyChanged: (String) -> Unit,
    mdbListShowImdb: Boolean,
    onMdbListShowImdbChanged: (Boolean) -> Unit,
    mdbListShowTomatoes: Boolean,
    onMdbListShowTomatoesChanged: (Boolean) -> Unit,
    mdbListShowMetacritic: Boolean,
    onMdbListShowMetacriticChanged: (Boolean) -> Unit,
    mdbListShowAudience: Boolean,
    onMdbListShowAudienceChanged: (Boolean) -> Unit,
    mdbListShowLetterboxd: Boolean,
    onMdbListShowLetterboxdChanged: (Boolean) -> Unit,
    mdbListShowTmdb: Boolean,
    onMdbListShowTmdbChanged: (Boolean) -> Unit,
    mdbListShowTrakt: Boolean,
    onMdbListShowTraktChanged: (Boolean) -> Unit,
    aniSkipEnabled: Boolean,
    onAniSkipEnabledChanged: (Boolean) -> Unit,
    aniSkipAutoSkip: Boolean,
    onAniSkipAutoSkipChanged: (Boolean) -> Unit,
    animeSkipClientId: String,
    onAnimeSkipClientIdChanged: (String) -> Unit,
    fanartTvEnabled: Boolean,
    onFanartTvEnabledChanged: (Boolean) -> Unit,
    fanartTvApiKey: String,
    onFanartTvApiKeyChanged: (String) -> Unit,
    embeddedMode: Boolean = false,
    initialPage: Int = 0,
    onDismiss: () -> Unit
) {
    val accentColor = LocalKitsugiAccent.current
    val pagerState = rememberPagerState(initialPage = initialPage.coerceIn(0, 3), pageCount = { 4 })
    val scope = rememberCoroutineScope()
    LaunchedEffect(initialPage) {
        val targetPage = initialPage.coerceIn(0, 3)
        if (pagerState.currentPage != targetPage) pagerState.animateScrollToPage(targetPage)
    }

    // API anahtarı silinirse akıştaki son kota değeri de temizlenir; aksi hâlde detay
    // sayfasındaki rozet, artık geçerli olmayan bir anahtarın kalan hakkını göstermeye devam ederdi.
    LaunchedEffect(mdbListApiKey) {
        if (mdbListApiKey.isBlank()) {
            com.kitsugi.animelist.data.remote.MdbListClient.loadCachedQuota("")
        }
    }

    val tmdbScrollState = rememberScrollState()
    val mdbListScrollState = rememberScrollState()
    val aniSkipScrollState = rememberScrollState()
    val fanartTvScrollState = rememberScrollState()

    val activeScrollState = when (pagerState.currentPage) {
        0 -> tmdbScrollState
        1 -> mdbListScrollState
        2 -> aniSkipScrollState
        else -> fanartTvScrollState
    }

    KitsugiSheetOrDialog(
        onDismiss = onDismiss,
        fullScreen = true,
        embeddedMode = embeddedMode,
        innerColumnScrollState = activeScrollState,
        sheetGesturesEnabled = false
    ) {
            // Header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = if (embeddedMode) 4.dp else 8.dp)
            ) {
                if (!embeddedMode) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.integrations_title),
                            color = KitsugiColors.TextPrimary,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.action_close), tint = KitsugiColors.TextSecondary)
                        }
                    }
                }
                KitsugiSettingsSegmentedTabs(
                    tabs = listOf(
                        KitsugiSettingsTabOption(stringResource(R.string.integrations_tab_tmdb), Icons.Rounded.Movie),
                        KitsugiSettingsTabOption(stringResource(R.string.integrations_tab_mdblist), Icons.Rounded.Star),
                        KitsugiSettingsTabOption(stringResource(R.string.integrations_tab_skip), Icons.Rounded.SkipNext),
                        KitsugiSettingsTabOption("Fanart.tv", Icons.Rounded.Image)
                    ),
                    selectedIndex = pagerState.currentPage,
                    accentColor = accentColor,
                    onSelect = { index -> scope.launch { pagerState.animateScrollToPage(index) } }
                )
            }

            // Body
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp)
            ) { page ->
                when (page) {
                    0 -> TmdbSettingsTab(
                        enabled = tmdbEnabled,
                        onEnabledChanged = onTmdbEnabledChanged,
                        apiKey = tmdbApiKey,
                        onApiKeyChanged = onTmdbApiKeyChanged,
                        modernHomeEnabled = tmdbModernHomeEnabled,
                        onModernHomeEnabledChanged = onTmdbModernHomeEnabledChanged,
                        enrichContinueWatching = tmdbEnrichContinueWatching,
                        onEnrichContinueWatchingChanged = onTmdbEnrichContinueWatchingChanged,
                        language = tmdbLanguage,
                        onLanguageChanged = onTmdbLanguageChanged,
                        useArtwork = tmdbUseArtwork,
                        onUseArtworkChanged = onTmdbUseArtworkChanged,
                        useBasicInfo = tmdbUseBasicInfo,
                        onUseBasicInfoChanged = onTmdbUseBasicInfoChanged,
                        useDetails = tmdbUseDetails,
                        onUseDetailsChanged = onTmdbUseDetailsChanged,
                        useReleaseDates = tmdbUseReleaseDates,
                        onUseReleaseDatesChanged = onTmdbUseReleaseDatesChanged,
                        useCredits = tmdbUseCredits,
                        onUseCreditsChanged = onTmdbUseCreditsChanged,
                        useProductions = tmdbUseProductions,
                        onUseProductionsChanged = onTmdbUseProductionsChanged,
                        useNetworks = tmdbUseNetworks,
                        onUseNetworksChanged = onTmdbUseNetworksChanged,
                        useEpisodes = tmdbUseEpisodes,
                        onUseEpisodesChanged = onTmdbUseEpisodesChanged,
                        useTrailers = tmdbUseTrailers,
                        onUseTrailersChanged = onTmdbUseTrailersChanged,
                        useMoreLikeThis = tmdbUseMoreLikeThis,
                        onUseMoreLikeThisChanged = onTmdbUseMoreLikeThisChanged,
                        useCollections = tmdbUseCollections,
                        onUseCollectionsChanged = onTmdbUseCollectionsChanged,
                        accentColor = accentColor,
                        scrollState = tmdbScrollState
                    )
                    1 -> MdbListSettingsTab(
                        enabled = mdbListEnabled,
                        onEnabledChanged = onMdbListEnabledChanged,
                        apiKey = mdbListApiKey,
                        onApiKeyChanged = onMdbListApiKeyChanged,
                        showImdb = mdbListShowImdb,
                        onShowImdbChanged = onMdbListShowImdbChanged,
                        showTomatoes = mdbListShowTomatoes,
                        onShowTomatoesChanged = onMdbListShowTomatoesChanged,
                        showMetacritic = mdbListShowMetacritic,
                        onShowMetacriticChanged = onMdbListShowMetacriticChanged,
                        showAudience = mdbListShowAudience,
                        onShowAudienceChanged = onMdbListShowAudienceChanged,
                        showLetterboxd = mdbListShowLetterboxd,
                        onShowLetterboxdChanged = onMdbListShowLetterboxdChanged,
                        showTmdb = mdbListShowTmdb,
                        onShowTmdbChanged = onMdbListShowTmdbChanged,
                        showTrakt = mdbListShowTrakt,
                        onShowTraktChanged = onMdbListShowTraktChanged,
                        accentColor = accentColor,
                        scrollState = mdbListScrollState
                    )
                    2 -> AniSkipSettingsTab(
                        enabled = aniSkipEnabled,
                        onEnabledChanged = onAniSkipEnabledChanged,
                        autoSkip = aniSkipAutoSkip,
                        onAutoSkipChanged = onAniSkipAutoSkipChanged,
                        animeSkipClientId = animeSkipClientId,
                        onAnimeSkipClientIdChanged = onAnimeSkipClientIdChanged,
                        accentColor = accentColor,
                        scrollState = aniSkipScrollState
                    )
                    3 -> FanartTvSettingsTab(
                        enabled = fanartTvEnabled,
                        onEnabledChanged = onFanartTvEnabledChanged,
                        apiKey = fanartTvApiKey,
                        onApiKeyChanged = onFanartTvApiKeyChanged,
                        accentColor = accentColor,
                        scrollState = fanartTvScrollState
                    )
                }
            }

            // Dialog mode owns its own dismissal control; embedded settings pages use the shared back bar.
            if (!embeddedMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.action_ok), color = accentColor, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TmdbSettingsTab(
    enabled: Boolean,
    onEnabledChanged: (Boolean) -> Unit,
    apiKey: String,
    onApiKeyChanged: (String) -> Unit,
    modernHomeEnabled: Boolean,
    onModernHomeEnabledChanged: (Boolean) -> Unit,
    enrichContinueWatching: Boolean,
    onEnrichContinueWatchingChanged: (Boolean) -> Unit,
    language: String,
    onLanguageChanged: (String) -> Unit,
    useArtwork: Boolean,
    onUseArtworkChanged: (Boolean) -> Unit,
    useBasicInfo: Boolean,
    onUseBasicInfoChanged: (Boolean) -> Unit,
    useDetails: Boolean,
    onUseDetailsChanged: (Boolean) -> Unit,
    useReleaseDates: Boolean,
    onUseReleaseDatesChanged: (Boolean) -> Unit,
    useCredits: Boolean,
    onUseCreditsChanged: (Boolean) -> Unit,
    useProductions: Boolean,
    onUseProductionsChanged: (Boolean) -> Unit,
    useNetworks: Boolean,
    onUseNetworksChanged: (Boolean) -> Unit,
    useEpisodes: Boolean,
    onUseEpisodesChanged: (Boolean) -> Unit,
    useTrailers: Boolean,
    onUseTrailersChanged: (Boolean) -> Unit,
    useMoreLikeThis: Boolean,
    onUseMoreLikeThisChanged: (Boolean) -> Unit,
    useCollections: Boolean,
    onUseCollectionsChanged: (Boolean) -> Unit,
    accentColor: Color,
    scrollState: ScrollState = rememberScrollState()
) {
    var tempKey by remember(apiKey) { mutableStateOf(apiKey) }
    var keyVisible by remember { mutableStateOf(false) }
    var keyValidating by remember { mutableStateOf(false) }
    var keyValidationPassed by remember(apiKey) { mutableStateOf<Boolean?>(null) }
    val scope = rememberCoroutineScope()
    var showLanguageDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        KitsugiSettingsSection(title = stringResource(R.string.tmdb_title)) {
            KitsugiSettingsSwitchItem(
                title = stringResource(R.string.etkinlestir),
                description = stringResource(R.string.tmdb_desc),
                icon = Icons.Rounded.Movie,
                iconColor = accentColor,
                checked = enabled,
                onCheckedChange = onEnabledChanged
            )

            if (enabled) {
                KitsugiSettingsDivider()

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    OutlinedTextField(
                        value = tempKey,
                        onValueChange = {
                            tempKey = it
                            keyValidationPassed = null
                        },
                        label = { Text(stringResource(R.string.tmdb_api_key)) },
                        placeholder = { Text(stringResource(R.string.tmdb_api_key_placeholder)) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !keyValidating,
                        singleLine = true,
                        visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { keyVisible = !keyVisible }) {
                                    Icon(
                                        imageVector = if (keyVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                        contentDescription = stringResource(
                                            if (keyVisible) R.string.integration_hide_key else R.string.integration_show_key
                                        ),
                                        tint = KitsugiColors.TextSecondary
                                    )
                                }
                                if (tempKey.isNotEmpty()) {
                                    IconButton(
                                        onClick = {
                                            tempKey = ""
                                            keyValidationPassed = null
                                            onApiKeyChanged("")
                                        },
                                        enabled = !keyValidating
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Close,
                                            contentDescription = stringResource(R.string.action_clear),
                                            tint = KitsugiColors.TextSecondary
                                        )
                                    }
                                }
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = accentColor,
                            unfocusedBorderColor = KitsugiColors.Border,
                            cursorColor = accentColor,
                            focusedTextColor = KitsugiColors.TextPrimary,
                            unfocusedTextColor = KitsugiColors.TextPrimary,
                            focusedLabelColor = accentColor,
                            unfocusedLabelColor = KitsugiColors.TextSecondary
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Text(
                        text = stringResource(R.string.integration_shared_tmdb_key_info),
                        color = KitsugiColors.TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    OutlinedButton(
                        onClick = {
                            keyValidating = true
                            keyValidationPassed = null
                            scope.launch {
                                val valid = runCatching {
                                    com.kitsugi.animelist.data.remote.TmdbApiClient.validateApiKey(tempKey)
                                }.getOrDefault(false)
                                if (valid) onApiKeyChanged(tempKey.trim())
                                keyValidationPassed = valid
                                keyValidating = false
                            }
                        },
                        enabled = !keyValidating,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = accentColor)
                    ) {
                        if (keyValidating) {
                            KitsugiPlasmaLoader(size = 18.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(stringResource(R.string.action_validate_save))
                    }
                    keyValidationPassed?.let { passed ->
                        Text(
                            text = stringResource(
                                if (passed) R.string.integration_key_validated else R.string.integration_key_failed
                            ),
                            color = if (passed) Color(0xFF76D49B) else KitsugiColors.TextSecondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                KitsugiSettingsDivider()

                KitsugiSettingsSwitchItem(
                    title = stringResource(R.string.tmdb_modern_home),
                    description = stringResource(R.string.tmdb_modern_home_desc),
                    icon = Icons.Rounded.Movie,
                    iconColor = accentColor,
                    checked = modernHomeEnabled,
                    onCheckedChange = onModernHomeEnabledChanged
                )

                KitsugiSettingsDivider()

                KitsugiSettingsSwitchItem(
                    title = stringResource(R.string.tmdb_enrich_continue_watching),
                    description = stringResource(R.string.tmdb_enrich_continue_watching_desc),
                    icon = Icons.Rounded.Movie,
                    iconColor = accentColor,
                    checked = enrichContinueWatching,
                    onCheckedChange = onEnrichContinueWatchingChanged
                )

                KitsugiSettingsDivider()

                val currentLangName = AVAILABLE_TMDB_LANGUAGES.find { it.code == language }?.displayName ?: language.uppercase()
                KitsugiSettingsListItem(
                    title = stringResource(R.string.tmdb_language),
                    description = stringResource(R.string.tmdb_language_desc),
                    value = currentLangName,
                    icon = Icons.Rounded.Movie,
                    iconColor = accentColor,
                    onClick = { showLanguageDialog = true }
                )
            }
        }

        if (enabled) {
            KitsugiSettingsSection(title = stringResource(R.string.tmdb_detail_blocks)) {
                KitsugiSettingsSwitchItem(
                    title = stringResource(R.string.tmdb_artwork),
                    description = stringResource(R.string.tmdb_artwork_desc),
                    icon = Icons.Rounded.Movie,
                    iconColor = accentColor,
                    checked = useArtwork,
                    onCheckedChange = onUseArtworkChanged
                )

                KitsugiSettingsDivider()

                KitsugiSettingsSwitchItem(
                    title = stringResource(R.string.tmdb_basic_info),
                    description = stringResource(R.string.tmdb_basic_info_desc),
                    icon = Icons.Rounded.Movie,
                    iconColor = accentColor,
                    checked = useBasicInfo,
                    onCheckedChange = onUseBasicInfoChanged
                )

                KitsugiSettingsDivider()

                KitsugiSettingsSwitchItem(
                    title = stringResource(R.string.tmdb_details),
                    description = stringResource(R.string.tmdb_details_desc),
                    icon = Icons.Rounded.Movie,
                    iconColor = accentColor,
                    checked = useDetails,
                    onCheckedChange = onUseDetailsChanged
                )

                KitsugiSettingsDivider()

                KitsugiSettingsSwitchItem(
                    title = stringResource(R.string.tmdb_release_dates),
                    description = stringResource(R.string.tmdb_release_dates_desc),
                    icon = Icons.Rounded.Movie,
                    iconColor = accentColor,
                    checked = useReleaseDates,
                    onCheckedChange = onUseReleaseDatesChanged
                )

                KitsugiSettingsDivider()

                KitsugiSettingsSwitchItem(
                    title = stringResource(R.string.tmdb_credits),
                    description = stringResource(R.string.tmdb_credits_desc),
                    icon = Icons.Rounded.Movie,
                    iconColor = accentColor,
                    checked = useCredits,
                    onCheckedChange = onUseCreditsChanged
                )

                KitsugiSettingsDivider()

                KitsugiSettingsSwitchItem(
                    title = stringResource(R.string.tmdb_productions),
                    description = stringResource(R.string.tmdb_productions_desc),
                    icon = Icons.Rounded.Movie,
                    iconColor = accentColor,
                    checked = useProductions,
                    onCheckedChange = onUseProductionsChanged
                )

                KitsugiSettingsDivider()

                KitsugiSettingsSwitchItem(
                    title = stringResource(R.string.tmdb_networks),
                    description = stringResource(R.string.tmdb_networks_desc),
                    icon = Icons.Rounded.Movie,
                    iconColor = accentColor,
                    checked = useNetworks,
                    onCheckedChange = onUseNetworksChanged
                )

                KitsugiSettingsDivider()

                KitsugiSettingsSwitchItem(
                    title = stringResource(R.string.tmdb_episodes),
                    description = stringResource(R.string.tmdb_episodes_desc),
                    icon = Icons.Rounded.Movie,
                    iconColor = accentColor,
                    checked = useEpisodes,
                    onCheckedChange = onUseEpisodesChanged
                )

                KitsugiSettingsDivider()

                KitsugiSettingsSwitchItem(
                    title = stringResource(R.string.tmdb_trailers),
                    description = stringResource(R.string.tmdb_trailers_desc),
                    icon = Icons.Rounded.Movie,
                    iconColor = accentColor,
                    checked = useTrailers,
                    onCheckedChange = onUseTrailersChanged
                )

                KitsugiSettingsDivider()

                KitsugiSettingsSwitchItem(
                    title = stringResource(R.string.tmdb_more_like_this),
                    description = stringResource(R.string.tmdb_more_like_this_desc),
                    icon = Icons.Rounded.Movie,
                    iconColor = accentColor,
                    checked = useMoreLikeThis,
                    onCheckedChange = onUseMoreLikeThisChanged
                )

                KitsugiSettingsDivider()

                KitsugiSettingsSwitchItem(
                    title = stringResource(R.string.tmdb_collections),
                    description = stringResource(R.string.tmdb_collections_desc),
                    icon = Icons.Rounded.Movie,
                    iconColor = accentColor,
                    checked = useCollections,
                    onCheckedChange = onUseCollectionsChanged
                )
            }
        }
    }

    if (showLanguageDialog) {
        LanguageSelectionDialog(
            title = stringResource(R.string.tmdb_language_dialog_title),
            selectedLanguage = language,
            onLanguageSelected = {
                onLanguageChanged(it)
                showLanguageDialog = false
            },
            onDismiss = { showLanguageDialog = false }
        )
    }
}

@Composable
private fun LanguageSelectionDialog(
    title: String,
    selectedLanguage: String,
    onLanguageSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = KitsugiColors.TextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AVAILABLE_TMDB_LANGUAGES.forEach { lang ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .tvClickable(shape = RoundedCornerShape(8.dp)) {
                                onLanguageSelected(lang.code)
                            }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = lang.displayName,
                            color = if (lang.code == selectedLanguage) LocalKitsugiAccent.current else KitsugiColors.TextPrimary,
                            fontWeight = if (lang.code == selectedLanguage) FontWeight.Bold else FontWeight.Normal
                        )
                        if (lang.code == selectedLanguage) {
                            Text("✓", color = LocalKitsugiAccent.current, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_close), color = LocalKitsugiAccent.current)
            }
        },
        containerColor = KitsugiColors.Surface,
        textContentColor = KitsugiColors.TextPrimary
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MdbListSettingsTab(
    enabled: Boolean,
    onEnabledChanged: (Boolean) -> Unit,
    apiKey: String,
    onApiKeyChanged: (String) -> Unit,
    showImdb: Boolean,
    onShowImdbChanged: (Boolean) -> Unit,
    showTomatoes: Boolean,
    onShowTomatoesChanged: (Boolean) -> Unit,
    showMetacritic: Boolean,
    onShowMetacriticChanged: (Boolean) -> Unit,
    showAudience: Boolean,
    onShowAudienceChanged: (Boolean) -> Unit,
    showLetterboxd: Boolean,
    onShowLetterboxdChanged: (Boolean) -> Unit,
    showTmdb: Boolean,
    onShowTmdbChanged: (Boolean) -> Unit,
    showTrakt: Boolean,
    onShowTraktChanged: (Boolean) -> Unit,
    accentColor: Color,
    scrollState: ScrollState = rememberScrollState()
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        KitsugiSettingsSection(title = stringResource(R.string.mdblist_title)) {
            KitsugiSettingsSwitchItem(
                title = stringResource(R.string.etkinlestir),
                description = stringResource(R.string.mdblist_desc),
                icon = Icons.Rounded.Movie,
                iconColor = accentColor,
                checked = enabled,
                onCheckedChange = onEnabledChanged
            )

            if (enabled) {
                KitsugiSettingsDivider()

                var showApiKeyDialog by remember { mutableStateOf(false) }

                KitsugiSettingsListItem(
                    title = stringResource(R.string.mdblist_api_key),
                    description = stringResource(R.string.mdblist_api_key_desc),
                    value = maskApiKey(apiKey, stringResource(R.string.value_not_set)),
                    icon = Icons.Rounded.Key,
                    iconColor = accentColor,
                    onClick = { showApiKeyDialog = true }
                )
                if (apiKey.isBlank()) {
                    Text(
                        text = stringResource(R.string.integration_mdb_key_required),
                        color = KitsugiColors.TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                } else {
                    KitsugiSettingsDivider()
                    MdbListQuotaBlock(apiKey = apiKey, accentColor = accentColor)
                }

                if (showApiKeyDialog) {
                    MdbListApiKeyValidationDialog(
                        currentValue = apiKey,
                        onSave = { key ->
                            onApiKeyChanged(key)
                            showApiKeyDialog = false
                        },
                        onDismiss = { showApiKeyDialog = false },
                        accentColor = accentColor
                    )
                }
            }
        }

        if (enabled) {
            KitsugiSettingsSection(title = stringResource(R.string.mdblist_score_services)) {
                KitsugiSettingsSwitchItem(
                    title = stringResource(R.string.mdblist_imdb),
                    description = stringResource(R.string.mdblist_imdb_desc),
                    icon = Icons.Rounded.Movie,
                    iconColor = accentColor,
                    checked = showImdb,
                    onCheckedChange = onShowImdbChanged
                )

                KitsugiSettingsDivider()

                KitsugiSettingsSwitchItem(
                    title = stringResource(R.string.mdblist_rotten_tomatoes),
                    description = stringResource(R.string.mdblist_rotten_tomatoes_desc),
                    icon = Icons.Rounded.Star,
                    iconColor = accentColor,
                    checked = showTomatoes,
                    onCheckedChange = onShowTomatoesChanged
                )

                KitsugiSettingsDivider()

                KitsugiSettingsSwitchItem(
                    title = stringResource(R.string.mdblist_audience),
                    description = stringResource(R.string.mdblist_audience_desc),
                    icon = Icons.Rounded.Star,
                    iconColor = accentColor,
                    checked = showAudience,
                    onCheckedChange = onShowAudienceChanged
                )

                KitsugiSettingsDivider()

                KitsugiSettingsSwitchItem(
                    title = stringResource(R.string.mdblist_metacritic),
                    description = stringResource(R.string.mdblist_metacritic_desc),
                    icon = Icons.Rounded.Star,
                    iconColor = accentColor,
                    checked = showMetacritic,
                    onCheckedChange = onShowMetacriticChanged
                )

                KitsugiSettingsDivider()

                KitsugiSettingsSwitchItem(
                    title = stringResource(R.string.mdblist_letterboxd),
                    description = stringResource(R.string.mdblist_letterboxd_desc),
                    icon = Icons.Rounded.Movie,
                    iconColor = accentColor,
                    checked = showLetterboxd,
                    onCheckedChange = onShowLetterboxdChanged
                )

                KitsugiSettingsDivider()

                KitsugiSettingsSwitchItem(
                    title = stringResource(R.string.mdblist_tmdb_score),
                    description = stringResource(R.string.mdblist_tmdb_score_desc),
                    icon = Icons.Rounded.Movie,
                    iconColor = accentColor,
                    checked = showTmdb,
                    onCheckedChange = onShowTmdbChanged
                )

                KitsugiSettingsDivider()

                KitsugiSettingsSwitchItem(
                    title = stringResource(R.string.mdblist_trakt),
                    description = stringResource(R.string.mdblist_trakt_desc),
                    icon = Icons.Rounded.Star,
                    iconColor = accentColor,
                    checked = showTrakt,
                    onCheckedChange = onShowTraktChanged
                )
            }
        }
    }
}

/**
 * MDBList API kotası bloğu.
 *
 * Ekranda **her zaman gerçek veri** görünür: değer, MDBList yanıtlarındaki
 * `X-RateLimit-*` başlıklarından ve `GET api.mdblist.com/user` uç noktasından okunur.
 * Sabit bir "1.000 / 1.000" yazılmaz; sunucu değeri henüz yoksa tahmini olduğu açıkça
 * belirtilir, yenile düğmesi değeri kesinleştirir.
 */
@Composable
private fun MdbListQuotaBlock(
    apiKey: String,
    accentColor: Color
) {
    val scope = rememberCoroutineScope()
    val quotaFlowValue by com.kitsugi.animelist.data.remote.MdbListClient.quotaFlow.collectAsState()
    val quota = if (com.kitsugi.animelist.data.remote.MdbListClient.quotaBelongsTo(apiKey)) quotaFlowValue else null

    var refreshing by remember(apiKey) { mutableStateOf(false) }
    var loadFailed by remember(apiKey) { mutableStateOf(false) }
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

    val refresh: () -> Unit = {
        if (!refreshing) {
            scope.launch {
                refreshing = true
                loadFailed = false
                val fresh = com.kitsugi.animelist.data.remote.MdbListClient.refreshQuota(apiKey, force = true)
                refreshing = false
                loadFailed = fresh == null
                nowMillis = System.currentTimeMillis()
            }
        }
    }

    // Sekme açıldığında: önce diskteki son değer gösterilir, eskiyse sunucudan tazelenir.
    LaunchedEffect(apiKey) {
        loadFailed = false
        val cached = com.kitsugi.animelist.data.remote.MdbListClient.loadCachedQuota(apiKey)
        if (com.kitsugi.animelist.data.remote.MdbListClient.isQuotaStale(cached)) {
            refreshing = true
            val fresh = com.kitsugi.animelist.data.remote.MdbListClient.refreshQuota(apiKey, force = false)
            refreshing = false
            loadFailed = fresh == null
        }
        nowMillis = System.currentTimeMillis()
    }

    // Geri sayım: sıfırlanma süresi ekranda canlı kalsın.
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(30_000L)
            nowMillis = System.currentTimeMillis()
        }
    }

    val current = quota?.updatedFor(nowMillis)
    val secondsToReset = current
        ?.let { (it.resetAtEpochSeconds - nowMillis / 1000L).coerceAtLeast(0L) }
        ?: 0L
    val barColor = when {
        current == null -> accentColor.copy(alpha = 0.35f)
        current.remainingFraction <= 0.15f -> Color(0xFFFF5B5B)
        current.remainingFraction <= 0.35f -> Color(0xFFFFC14D)
        else -> Color(0xFF4CD964)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(accentColor.copy(alpha = 0.16f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Speed,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.mdblist_quota_title),
                    color = KitsugiColors.TextPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = when {
                        current != null -> stringResource(
                            R.string.mdblist_quota_remaining,
                            formatQuotaCount(current.remaining),
                            formatQuotaCount(current.dailyLimit)
                        )
                        refreshing -> stringResource(R.string.mdblist_quota_loading)
                        else -> stringResource(R.string.mdblist_quota_unavailable)
                    },
                    color = if (current != null) KitsugiColors.TextPrimary else KitsugiColors.TextSecondary,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            IconButton(
                onClick = refresh,
                enabled = !refreshing,
                modifier = Modifier.size(36.dp)
            ) {
                if (refreshing) {
                    KitsugiPlasmaLoader(modifier = Modifier.size(18.dp), size = 18.dp, color = accentColor)
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = stringResource(R.string.mdblist_quota_refresh),
                        tint = KitsugiColors.TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Kalan hakkın oranı: çubuk "kalan"ı gösterir, doldukça hak azalır.
        val remainingFraction = current?.remainingFraction ?: 0f
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(KitsugiColors.Border.copy(alpha = 0.35f))
        ) {
            if (remainingFraction > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(remainingFraction)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(999.dp))
                        .background(barColor)
                )
            }
        }

        if (current != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (current.isExhausted) {
                        stringResource(R.string.mdblist_quota_exhausted, quotaDurationText(secondsToReset))
                    } else {
                        stringResource(R.string.mdblist_quota_resets_in, quotaDurationText(secondsToReset))
                    },
                    color = if (current.isExhausted) Color(0xFFFF5B5B) else KitsugiColors.TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = stringResource(
                        R.string.mdblist_quota_updated,
                        formatQuotaTimestamp(current.observedAtMillis)
                    ),
                    color = KitsugiColors.TextMuted,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1
                )
            }

            current.plan?.let { plan ->
                Text(
                    text = stringResource(
                        R.string.mdblist_quota_plan,
                        plan,
                        formatQuotaCount(current.dailyLimit)
                    ),
                    color = KitsugiColors.TextMuted,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (!current.isExact) {
                Text(
                    text = stringResource(R.string.mdblist_quota_estimated),
                    color = Color(0xFFFFC14D),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        if (loadFailed) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.mdblist_quota_error),
                    color = Color(0xFFFF5B5B),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = refresh, enabled = !refreshing) {
                    Text(
                        text = stringResource(R.string.mdblist_quota_refresh),
                        color = accentColor,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

private fun formatQuotaCount(value: Int): String =
    java.text.NumberFormat.getIntegerInstance(java.util.Locale.getDefault()).format(value.toLong())

/**
 * Sıfırlanmaya kalan süreyi kısa biçimde yazar ("4 sa 12 dk").
 * Metinler dilden bağımsız olsun diye kaynak dizesi kullanılır.
 */
@Composable
private fun quotaDurationText(totalSeconds: Long): String {
    val safe = totalSeconds.coerceAtLeast(0L)
    val hours = safe / 3_600L
    val minutes = (safe % 3_600L) / 60L
    return when {
        hours > 0L -> stringResource(R.string.mdblist_quota_duration_hm, hours.toInt(), minutes.toInt())
        minutes > 0L -> stringResource(R.string.mdblist_quota_duration_m, minutes.toInt())
        else -> stringResource(R.string.mdblist_quota_duration_s, safe.coerceAtLeast(1L).toInt())
    }
}

private fun formatQuotaTimestamp(millis: Long): String = try {
    java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(millis))
} catch (t: Throwable) {
    "--:--"
}

@Composable
private fun AniSkipSettingsTab(
    enabled: Boolean,
    onEnabledChanged: (Boolean) -> Unit,
    autoSkip: Boolean,
    onAutoSkipChanged: (Boolean) -> Unit,
    animeSkipClientId: String,
    onAnimeSkipClientIdChanged: (String) -> Unit,
    accentColor: Color,
    scrollState: ScrollState = rememberScrollState()
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        KitsugiSettingsSection(title = stringResource(R.string.aniskip_title)) {
            KitsugiSettingsSwitchItem(
                title = stringResource(R.string.etkinlestir),
                description = stringResource(R.string.aniskip_desc),
                icon = Icons.Rounded.SkipNext,
                iconColor = accentColor,
                checked = enabled,
                onCheckedChange = onEnabledChanged
            )

            if (enabled) {
                KitsugiSettingsDivider()

                KitsugiSettingsSwitchItem(
                    title = stringResource(R.string.aniskip_auto_skip),
                    description = stringResource(R.string.aniskip_auto_skip_desc),
                    icon = Icons.Rounded.SkipNext,
                    iconColor = accentColor,
                    checked = autoSkip,
                    onCheckedChange = onAutoSkipChanged
                )
                Text(
                    text = stringResource(R.string.integration_skip_sources_info),
                    color = KitsugiColors.TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }

        if (enabled) {
            KitsugiSettingsSection(title = stringResource(R.string.animeskip_integration)) {
                var showClientIdDialog by remember { mutableStateOf(false) }

                KitsugiSettingsListItem(
                    title = stringResource(R.string.animeskip_client_id),
                    description = stringResource(R.string.animeskip_client_id_desc),
                    value = maskApiKey(animeSkipClientId, stringResource(R.string.animeskip_default)),
                    icon = Icons.Rounded.Key,
                    iconColor = accentColor,
                    onClick = { showClientIdDialog = true }
                )

                if (showClientIdDialog) {
                    AnimeSkipClientIdValidationDialog(
                        currentValue = animeSkipClientId,
                        onSave = { clientId ->
                            onAnimeSkipClientIdChanged(clientId)
                            showClientIdDialog = false
                        },
                        onDismiss = { showClientIdDialog = false },
                        accentColor = accentColor
                    )
                }
            }
        }
    }
}

private fun maskApiKey(key: String, placeholder: String): String {
    val trimmed = key.trim()
    if (trimmed.isBlank()) return placeholder
    return if (trimmed.length <= 4) "••••" else "••••••${trimmed.takeLast(4)}"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MdbListApiKeyValidationDialog(
    currentValue: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
    accentColor: Color
) {
    var value by remember(currentValue) { mutableStateOf(currentValue) }
    var keyVisible by remember { mutableStateOf(false) }
    var validating by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.mdblist_api_dialog_title),
                color = KitsugiColors.TextPrimary,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.api_key_dialog_desc),
                    color = KitsugiColors.TextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )

                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text(stringResource(R.string.mdblist_api_key)) },
                    placeholder = { Text(stringResource(R.string.mdblist_api_key_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !validating,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accentColor,
                        unfocusedBorderColor = KitsugiColors.Border,
                        cursorColor = accentColor,
                        focusedTextColor = KitsugiColors.TextPrimary,
                        unfocusedTextColor = KitsugiColors.TextPrimary,
                        focusedLabelColor = accentColor,
                        unfocusedLabelColor = KitsugiColors.TextSecondary
                    ),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { keyVisible = !keyVisible }) {
                                Icon(
                                    imageVector = if (keyVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                    contentDescription = stringResource(
                                        if (keyVisible) R.string.integration_hide_key else R.string.integration_show_key
                                    ),
                                    tint = KitsugiColors.TextSecondary
                                )
                            }
                            if (value.isNotEmpty()) {
                                IconButton(onClick = { value = "" }) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = stringResource(R.string.action_clear),
                                        tint = KitsugiColors.TextSecondary
                                    )
                                }
                            }
                        }
                    }
                )
            }
        },
        confirmButton = {
            KitsugiButton(
                onClick = {
                    if (value.isBlank()) {
                        onSave("")
                    } else {
                        validating = true
                        scope.launch {
                            val valid = com.kitsugi.animelist.data.remote.MdbListClient.validateApiKey(value)
                            validating = false
                            if (valid) {
                                onSave(value.trim())
                                android.widget.Toast.makeText(context, context.getString(R.string.api_key_validated), android.widget.Toast.LENGTH_SHORT).show()
                            } else {
                                android.widget.Toast.makeText(context, context.getString(R.string.api_key_invalid), android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                },
                enabled = !validating
            ) {
                if (validating) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        KitsugiPlasmaLoader(size = 18.dp)
                        Text(stringResource(R.string.action_validating))
                    }
                } else {
                    Text(stringResource(R.string.action_validate_save))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !validating) {
                Text(stringResource(R.string.action_cancel), color = KitsugiColors.TextMuted)
            }
        },
        containerColor = KitsugiColors.Surface,
        textContentColor = KitsugiColors.TextPrimary
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AnimeSkipClientIdValidationDialog(
    currentValue: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
    accentColor: Color
) {
    var value by remember(currentValue) { mutableStateOf(currentValue) }
    var validating by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.animeskip_client_id_dialog_title),
                color = KitsugiColors.TextPrimary,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.animeskip_client_id_dialog_desc),
                    color = KitsugiColors.TextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )

                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text(stringResource(R.string.animeskip_client_id_label)) },
                    placeholder = { Text(stringResource(R.string.animeskip_default)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accentColor,
                        unfocusedBorderColor = KitsugiColors.Border,
                        cursorColor = accentColor,
                        focusedTextColor = KitsugiColors.TextPrimary,
                        unfocusedTextColor = KitsugiColors.TextPrimary,
                        focusedLabelColor = accentColor,
                        unfocusedLabelColor = KitsugiColors.TextSecondary
                    ),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    trailingIcon = {
                        if (value.isNotEmpty()) {
                            IconButton(onClick = { value = "" }) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = stringResource(R.string.action_clear),
                                    tint = KitsugiColors.TextSecondary
                                )
                            }
                        }
                    }
                )
            }
        },
        confirmButton = {
            KitsugiButton(
                onClick = {
                    if (value.isBlank()) {
                        onSave("")
                    } else {
                        validating = true
                        scope.launch {
                            val valid = com.kitsugi.animelist.data.remote.AnimeSkipClient.validateClientId(value)
                            validating = false
                            if (valid) {
                                onSave(value)
                                android.widget.Toast.makeText(context, context.getString(R.string.client_id_validated), android.widget.Toast.LENGTH_SHORT).show()
                            } else {
                                android.widget.Toast.makeText(context, context.getString(R.string.client_id_invalid), android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                },
                enabled = !validating
            ) {
                if (validating) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        KitsugiPlasmaLoader(size = 18.dp)
                        Text(stringResource(R.string.action_validating))
                    }
                } else {
                    Text(stringResource(R.string.action_validate_save))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !validating) {
                Text(stringResource(R.string.action_cancel), color = KitsugiColors.TextMuted)
            }
        },
        containerColor = KitsugiColors.Surface,
        textContentColor = KitsugiColors.TextPrimary
    )
}

@Composable
private fun FanartTvSettingsTab(
    enabled: Boolean,
    onEnabledChanged: (Boolean) -> Unit,
    apiKey: String,
    onApiKeyChanged: (String) -> Unit,
    accentColor: Color,
    scrollState: ScrollState = rememberScrollState()
) {
    var tempKey by remember(apiKey) { mutableStateOf(apiKey) }
    var keyVisible by remember { mutableStateOf(false) }
    var keyValidating by remember { mutableStateOf(false) }
    var keyValidationPassed by remember(apiKey) { mutableStateOf<Boolean?>(null) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        KitsugiSettingsSection(title = stringResource(R.string.fanart_settings_title)) {
            KitsugiSettingsSwitchItem(
                title = stringResource(R.string.etkinlestir),
                description = stringResource(R.string.fanart_settings_desc),
                icon = Icons.Rounded.Movie,
                iconColor = accentColor,
                checked = enabled,
                onCheckedChange = onEnabledChanged
            )

            if (enabled) {
                KitsugiSettingsDivider()

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    OutlinedTextField(
                        value = tempKey,
                        onValueChange = {
                            tempKey = it
                            keyValidationPassed = null
                        },
                        label = { Text(stringResource(R.string.fanart_api_key)) },
                        placeholder = { Text(stringResource(R.string.fanart_api_key_hint)) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !keyValidating,
                        visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = accentColor,
                            unfocusedBorderColor = KitsugiColors.Border,
                            cursorColor = accentColor,
                            focusedTextColor = KitsugiColors.TextPrimary,
                            unfocusedTextColor = KitsugiColors.TextPrimary,
                            focusedLabelColor = accentColor,
                            unfocusedLabelColor = KitsugiColors.TextSecondary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        trailingIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { keyVisible = !keyVisible }) {
                                    Icon(
                                        imageVector = if (keyVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                        contentDescription = stringResource(
                                            if (keyVisible) R.string.integration_hide_key else R.string.integration_show_key
                                        ),
                                        tint = KitsugiColors.TextSecondary
                                    )
                                }
                                if (tempKey.isNotEmpty()) {
                                    IconButton(
                                        onClick = {
                                            tempKey = ""
                                            keyValidationPassed = null
                                            onApiKeyChanged("")
                                        },
                                        enabled = !keyValidating
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Close,
                                            contentDescription = stringResource(R.string.action_clear),
                                            tint = KitsugiColors.TextSecondary
                                        )
                                    }
                                }
                            }
                        }
                    )
                    Text(
                        text = stringResource(R.string.integration_shared_fanart_key_info),
                        color = KitsugiColors.TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    OutlinedButton(
                        onClick = {
                            keyValidating = true
                            keyValidationPassed = null
                            scope.launch {
                                val valid = runCatching {
                                    com.kitsugi.animelist.data.remote.FanartApiClient.validateApiKey(tempKey)
                                }.getOrDefault(false)
                                if (valid) onApiKeyChanged(tempKey.trim())
                                keyValidationPassed = valid
                                keyValidating = false
                            }
                        },
                        enabled = !keyValidating,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = accentColor)
                    ) {
                        if (keyValidating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = accentColor,
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(stringResource(R.string.action_validate_save))
                    }
                    keyValidationPassed?.let { passed ->
                        Text(
                            text = stringResource(
                                if (passed) R.string.integration_key_validated else R.string.integration_key_failed
                            ),
                            color = if (passed) Color(0xFF76D49B) else KitsugiColors.TextSecondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

