@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.kitsugi.animelist.ui.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ManageSearch
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.BuildConfig
import com.kitsugi.animelist.ui.components.BackupImportMode
import com.kitsugi.animelist.ui.components.KitsugiAccountConnectionsDialog
import com.kitsugi.animelist.ui.screens.account.KitsugiAccountContent
import com.kitsugi.animelist.ui.components.KitsugiAddonsSettingsDialog
import com.kitsugi.animelist.ui.components.KitsugiChoiceOption
import com.kitsugi.animelist.ui.components.KitsugiConfirmDialog
import com.kitsugi.animelist.ui.components.KitsugiIntegrationsSettingsDialog
import com.kitsugi.animelist.ui.components.KitsugiPlayerSettingsDialog
import com.kitsugi.animelist.ui.components.KitsugiPreferencesSettingsDialog
import com.kitsugi.animelist.ui.components.KitsugiSettingsDivider
import com.kitsugi.animelist.ui.components.KitsugiSettingsItem
import com.kitsugi.animelist.ui.components.KitsugiSettingsSection
import com.kitsugi.animelist.ui.components.KitsugiSystemSettingsDialog
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent

// ─── Settings Nav Routes ────────────────────────────────────────────────────

internal enum class SettingsRoute {
    // Ana menü
    Main,
    // Alt sayfalar
    KitsugiAccount,
    AccountConnections,
    AniListSettings,
    MalSettings,
    SimklSettings,
    KitsuSettings,
    ShikimoriSettings,
    BangumiSettings,
    CrossSyncSettings,
    AppearancePreferences,
    PlayerSettings,
    AddonsExtensions,
    Integrations,
    DataBackup,
    Downloads,
    About,
    Feedback,
    PluginDiagnostic
}

// ─── Main Screen ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    params: SettingsScreenParameters
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val accentColor = LocalKitsugiAccent.current

    var route by rememberSaveable { mutableStateOf(SettingsRoute.Main) }
    var showDeleteAllConfirm by rememberSaveable { mutableStateOf(false) }
    var systemSettingsInitialPage by rememberSaveable { mutableStateOf(0) }

    // Geri tuşu — hiyerarşik geri navigasyon
    BackHandler(enabled = route != SettingsRoute.Main) {
        route = when (route) {
            SettingsRoute.PluginDiagnostic -> SettingsRoute.AddonsExtensions
            SettingsRoute.AniListSettings,
            SettingsRoute.MalSettings,
            SettingsRoute.SimklSettings,
            SettingsRoute.KitsuSettings,
            SettingsRoute.ShikimoriSettings,
            SettingsRoute.BangumiSettings,
            SettingsRoute.CrossSyncSettings -> SettingsRoute.AccountConnections
            else -> SettingsRoute.Main
        }
    }

    // Alt sayfa açıldığında veya Main'e dönüldüğünde alt bar durumunu navState'e bildir
    LaunchedEffect(route) {
        params.onSubPageOpenChange?.invoke(route != SettingsRoute.Main)
    }

    DisposableEffect(Unit) {
        onDispose {
            params.onSubPageOpenChange?.invoke(false)
        }
    }

    val isGoingForward = route != SettingsRoute.Main

    AnimatedContent(
        targetState = route,
        transitionSpec = {
            if (targetState != SettingsRoute.Main) {
                // İleri: sağdan gir
                (slideInHorizontally { it } + fadeIn()) togetherWith
                        (slideOutHorizontally { -it / 3 } + fadeOut())
            } else {
                // Geri: sola git
                (slideInHorizontally { -it / 3 } + fadeIn()) togetherWith
                        (slideOutHorizontally { it } + fadeOut())
            } using SizeTransform(clip = false)
        },
        label = "settings_route"
    ) { currentRoute ->
        when (currentRoute) {
            SettingsRoute.Main -> {
                SettingsMainPage(
                    params = params,
                    onNavigate = { route = it }
                )
            }

            SettingsRoute.KitsugiAccount -> {
                SettingsSubPage(
                    title = stringResource(R.string.settings_kitsugi_account),
                    onBack = { route = SettingsRoute.Main }
                ) {
                    KitsugiAccountContent()
                }
            }

            SettingsRoute.AccountConnections -> {
                SettingsSubPage(
                    title = stringResource(R.string.settings_account_connections),
                    onBack = { route = SettingsRoute.Main }
                ) {
                    AccountConnectionsHubContent(
                        profile = params.profile,
                        onNavigate = { route = it }
                    )
                }
            }

            SettingsRoute.AniListSettings -> {
                SettingsSubPage(
                    title = "AniList",
                    onBack = { route = SettingsRoute.AccountConnections }
                ) {
                    AniListSettingsContent(profile = params.profile)
                }
            }

            SettingsRoute.MalSettings -> {
                SettingsSubPage(
                    title = "MyAnimeList",
                    onBack = { route = SettingsRoute.AccountConnections }
                ) {
                    MalSettingsContent(profile = params.profile)
                }
            }

            SettingsRoute.SimklSettings -> {
                SettingsSubPage(
                    title = "Simkl",
                    onBack = { route = SettingsRoute.AccountConnections }
                ) {
                    SimklSettingsContent(profile = params.profile)
                }
            }

            SettingsRoute.KitsuSettings -> {
                SettingsSubPage(
                    title = "Kitsu",
                    onBack = { route = SettingsRoute.AccountConnections }
                ) {
                    KitsuSettingsContent(profile = params.profile)
                }
            }

            SettingsRoute.ShikimoriSettings -> {
                SettingsSubPage(
                    title = "Shikimori",
                    onBack = { route = SettingsRoute.AccountConnections }
                ) {
                    ShikimoriSettingsContent(profile = params.profile)
                }
            }

            SettingsRoute.BangumiSettings -> {
                SettingsSubPage(
                    title = "Bangumi",
                    onBack = { route = SettingsRoute.AccountConnections }
                ) {
                    BangumiSettingsContent(profile = params.profile)
                }
            }

            SettingsRoute.CrossSyncSettings -> {
                SettingsSubPage(
                    title = stringResource(R.string.settings_cross_sync),
                    onBack = { route = SettingsRoute.AccountConnections }
                ) {
                    CrossSyncSettingsContent(profile = params.profile)
                }
            }

            SettingsRoute.AppearancePreferences -> {
                SettingsSubPage(
                    title = stringResource(R.string.settings_pref_title),
                    onBack = { route = SettingsRoute.Main }
                ) {
                    SettingsPreferencesContent(
                        general = params.general,
                        integrations = params.integrations
                    )
                }
            }

            SettingsRoute.PlayerSettings -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                ) {
                    SettingsPlayerContent(
                        player = params.player,
                        onDismiss = { route = SettingsRoute.Main }
                    )
                }
            }

            SettingsRoute.AddonsExtensions -> {
                SettingsSubPage(
                    title = stringResource(R.string.settings_addons_sources),
                    onBack = { route = SettingsRoute.Main }
                ) {
                    SettingsAddonsContent(
                        addon = params.addon,
                        manga = params.manga,
                        onOpenDiagnostic = { route = SettingsRoute.PluginDiagnostic },
                        onExplorePlugin = { pluginName ->
                            route = SettingsRoute.Main
                            params.onExplorePlugin?.invoke(pluginName)
                        },
                        onOpenPluginPicker = {
                            route = SettingsRoute.Main
                            params.onOpenPluginPicker?.invoke()
                        }
                    )
                }
            }

            SettingsRoute.Integrations -> {
                SettingsSubPage(
                    title = stringResource(R.string.settings_integrations),
                    onBack = { route = SettingsRoute.Main }
                ) {
                    SettingsIntegrationsContent(integrations = params.integrations)
                }
            }

            SettingsRoute.DataBackup -> {
                SettingsSubPage(
                    title = stringResource(R.string.settings_data_backup),
                    onBack = { route = SettingsRoute.Main }
                ) {
                    KitsugiSystemSettingsDialog(
                        embeddedMode = true,
                        totalEntryCount = params.profile.totalEntryCount,
                        onExportFileClick = params.profile.onExportBackupFileClick,
                        onImportFileClick = params.profile.onImportBackupFileClick,
                        onDeleteAllClick = {
                            showDeleteAllConfirm = true
                        },
                        dnsChoice = params.integrations.dnsChoice,
                        onDnsChoiceSelected = params.integrations.onDnsChoiceSelected,
                        download = params.download,
                        initialPage = 0,
                        onDismiss = { route = SettingsRoute.Main }
                    )
                }
            }

            SettingsRoute.Downloads -> {
                SettingsSubPage(
                    title = stringResource(R.string.settings_download_settings),
                    onBack = { route = SettingsRoute.Main }
                ) {
                    val accentColor = com.kitsugi.animelist.ui.theme.LocalKitsugiAccent.current
                    val storageSettingsScrollState = rememberScrollState()
                    com.kitsugi.animelist.ui.components.StorageSettingsTab(
                        download = params.download,
                        accentColor = accentColor,
                        scrollState = storageSettingsScrollState
                    )
                }
            }

            SettingsRoute.About -> {
                LaunchedEffect(Unit) {
                    route = SettingsRoute.Main
                    (params.onOpenAbout ?: { params.integrations.onOpenAbout() }).invoke()
                }
            }

            SettingsRoute.Feedback -> {
                SettingsSubPage(
                    title = stringResource(R.string.settings_feedback),
                    onBack = { route = SettingsRoute.Main }
                ) {
                    com.kitsugi.animelist.ui.screens.more.FeedbackDialog(
                        embeddedMode = true,
                        onDismiss = { route = SettingsRoute.Main },
                        onSubmit = { title, type, description ->
                            val intent = android.content.Intent(android.content.Intent.ACTION_SENDTO).apply {
                                data = android.net.Uri.parse("mailto:")
                                putExtra(android.content.Intent.EXTRA_EMAIL, arrayOf("kitsugibeta@gmail.com"))
                                val subject = "[Kitsugi Beta Feedback] [$type] $title"
                                val body = context.getString(
                                    R.string.feedback_mail_body,
                                    type,
                                    title,
                                    description,
                                    BuildConfig.VERSION_NAME,
                                    BuildConfig.VERSION_CODE,
                                    "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}",
                                    android.os.Build.VERSION.SDK_INT
                                )
                                putExtra(android.content.Intent.EXTRA_SUBJECT, subject)
                                putExtra(android.content.Intent.EXTRA_TEXT, body)
                            }
                            runCatching {
                                context.startActivity(intent)
                            }.onFailure {
                                android.widget.Toast.makeText(
                                    context,
                                    context.getString(R.string.feedback_no_email_app),
                                    android.widget.Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    )
                }
            }

            SettingsRoute.PluginDiagnostic -> {
                SettingsSubPage(
                    title = stringResource(R.string.settings_plugin_diagnostic),
                    onBack = { route = SettingsRoute.AddonsExtensions }
                ) {
                    CsPluginDiagnosticScreen(
                        embeddedMode = true,
                        onDismiss = { route = SettingsRoute.AddonsExtensions }
                    )
                }
            }
        }
    }

    // Confirm delete all dialog — route-bağımsız, üstte gösterilir
    if (showDeleteAllConfirm) {
        KitsugiConfirmDialog(
            title = stringResource(R.string.settings_delete_all_title),
            message = stringResource(R.string.settings_delete_all_message),
            confirmText = stringResource(R.string.settings_delete_all_confirm),
            isDestructive = true,
            onConfirm = {
                params.profile.onDeleteAllEntries()
                showDeleteAllConfirm = false
            },
            onDismiss = {
                showDeleteAllConfirm = false
            }
        )
    }
}

// ─── Ana Menü Sayfası ─────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsMainPage(
    params: SettingsScreenParameters,
    onNavigate: (SettingsRoute) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val accentColor = LocalKitsugiAccent.current
    val scrollState = rememberLazyListState()

    val selectedTheme = themeOptions.firstOrNull { it.id == params.general.selectedThemeId }
        ?: themeOptions.first()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        // Top App Bar
        TopAppBar(
            title = {
                Text(
                    text = stringResource(R.string.tab_settings),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = KitsugiColors.TextPrimary
                )
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = KitsugiColors.Background
            )
        )

        LazyColumn(
            state = scrollState,
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
        ) {
            // ── Hesap Bağlantıları ──────────────────────────────────────────
            item {
                Spacer(Modifier.height(8.dp))
                SectionHeader(stringResource(R.string.settings_section_account))
                SettingsNavCard {
                    KitsugiSettingsItem(
                        title = stringResource(R.string.settings_kitsugi_account),
                        description = stringResource(R.string.settings_kitsugi_account_desc),
                        icon = Icons.Rounded.AccountCircle,
                        iconColor = KitsugiColors.AccentGreen,
                        onClick = { onNavigate(SettingsRoute.KitsugiAccount) }
                    )
                }
                Spacer(Modifier.height(8.dp))
                SettingsNavCard {
                    KitsugiSettingsItem(
                        title = stringResource(R.string.settings_account_connections),
                        description = stringResource(R.string.settings_account_connections_desc),
                        icon = Icons.Rounded.Person,
                        iconColor = KitsugiColors.AccentBlue,
                        onClick = { onNavigate(SettingsRoute.AccountConnections) }
                    )
                }
                Spacer(Modifier.height(16.dp))
            }

            // ── Uygulama Ayarları ───────────────────────────────────────────
            item {
                SectionHeader(stringResource(R.string.settings_section_app))
                SettingsNavCard {
                    KitsugiSettingsItem(
                        title = stringResource(R.string.settings_pref_title),
                        description = stringResource(R.string.settings_pref_desc),
                        icon = Icons.Rounded.Palette,
                        iconColor = selectedTheme.color ?: accentColor,
                        onClick = { onNavigate(SettingsRoute.AppearancePreferences) }
                    )
                    KitsugiSettingsDivider()
                    KitsugiSettingsItem(
                        title = stringResource(R.string.settings_player_title),
                        description = stringResource(R.string.settings_player_desc),
                        icon = Icons.Rounded.PlayCircle,
                        iconColor = KitsugiColors.AccentOrange,
                        onClick = { onNavigate(SettingsRoute.PlayerSettings) }
                    )
                    KitsugiSettingsDivider()
                    KitsugiSettingsItem(
                        title = stringResource(R.string.settings_watch_history),
                        description = stringResource(R.string.settings_watch_history_desc),
                        icon = Icons.Rounded.History,
                        iconColor = KitsugiColors.AccentTeal,
                        onClick = { params.onOpenWatchHistory?.invoke() }
                    )
                }
                Spacer(Modifier.height(16.dp))
            }

            // ── Kaynaklar & Entegrasyonlar ──────────────────────────────────
            item {
                SectionHeader(stringResource(R.string.settings_section_sources))
                SettingsNavCard {
                    KitsugiSettingsItem(
                        title = stringResource(R.string.settings_addons_streams),
                        description = stringResource(R.string.settings_addons_streams_desc),
                        icon = Icons.Rounded.Extension,
                        iconColor = KitsugiColors.AccentPurple,
                        onClick = { onNavigate(SettingsRoute.AddonsExtensions) }
                    )
                    KitsugiSettingsDivider()
                    KitsugiSettingsItem(
                        title = stringResource(R.string.settings_integrations),
                        description = stringResource(R.string.settings_integrations_desc),
                        icon = Icons.Rounded.Hub,
                        iconColor = KitsugiColors.AccentBlue,
                        onClick = { onNavigate(SettingsRoute.Integrations) }
                    )
                }
                Spacer(Modifier.height(16.dp))
            }

            // ── Veri & Depolama ─────────────────────────────────────────────
            item {
                SectionHeader(stringResource(R.string.settings_section_data))
                SettingsNavCard {
                    KitsugiSettingsItem(
                        title = stringResource(R.string.settings_data_backup),
                        description = stringResource(R.string.settings_data_backup_desc),
                        icon = Icons.Rounded.Storage,
                        iconColor = KitsugiColors.AccentGreen,
                        onClick = { onNavigate(SettingsRoute.DataBackup) }
                    )
                    KitsugiSettingsDivider()
                    KitsugiSettingsItem(
                        title = stringResource(R.string.settings_download_settings),
                        description = stringResource(R.string.settings_download_settings_desc),
                        icon = Icons.Rounded.Tune,
                        iconColor = KitsugiColors.AccentGreen,
                        onClick = { onNavigate(SettingsRoute.Downloads) }
                    )
                    KitsugiSettingsDivider()
                    KitsugiSettingsItem(
                        title = stringResource(R.string.settings_downloads),
                        description = stringResource(R.string.settings_downloads_desc),
                        icon = Icons.Rounded.Download,
                        iconColor = KitsugiColors.AccentGreen,
                        onClick = {
                            if (params.onOpenDownloads != null) {
                                params.onOpenDownloads.invoke()
                            } else {
                                context.startActivity(
                                    android.content.Intent(
                                        context,
                                        com.kitsugi.animelist.ui.screens.offline.DownloadsActivity::class.java
                                    )
                                )
                            }
                        }
                    )
                }
                Spacer(Modifier.height(16.dp))
            }

            // ── Hakkında & Destek ───────────────────────────────────────────
            item {
                SectionHeader(stringResource(R.string.settings_section_support))
                SettingsNavCard {
                    KitsugiSettingsItem(
                        title = stringResource(R.string.settings_about),
                        description = stringResource(R.string.settings_about_desc, BuildConfig.VERSION_NAME),
                        icon = Icons.Rounded.Info,
                        iconColor = KitsugiColors.AccentIndigo,
                        onClick = {
                            (params.onOpenAbout ?: { params.integrations.onOpenAbout() }).invoke()
                        }
                    )
                    KitsugiSettingsDivider()
                    KitsugiSettingsItem(
                        title = stringResource(R.string.settings_feedback),
                        description = stringResource(R.string.settings_feedback_desc),
                        icon = Icons.Rounded.Feedback,
                        iconColor = KitsugiColors.AccentBlue,
                        onClick = { onNavigate(SettingsRoute.Feedback) }
                    )
                }
                Spacer(Modifier.height(80.dp))
            }
        }
    }
}

// ─── Sub-page shell ──────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSubPage(
    title: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        TopAppBar(
            title = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = KitsugiColors.TextPrimary
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.action_back),
                        tint = KitsugiColors.TextPrimary
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = KitsugiColors.Background
            )
        )
        HorizontalDivider(color = KitsugiColors.Border.copy(alpha = 0.5f))
        content()
    }
}

// ─── Yardımcı bileşenler ──────────────────────────────────────────────────────

@Composable
internal fun SectionHeader(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = KitsugiColors.TextMuted,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
    )
}

@Composable
internal fun SettingsNavCard(content: @Composable () -> Unit) {
    androidx.compose.material3.Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(24.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = KitsugiColors.Surface
        )
    ) {
        content()
    }
}

// ─── Alt sayfa içerikleri ─────────────────────────────────────────────────────

@Composable
private fun SettingsPreferencesContent(
    general: GeneralSettings,
    integrations: IntegrationsSettings
) {
    val localSettings = androidx.compose.runtime.remember(
        general.selectedThemeId, general.themeMode, general.amoledBlack, general.customAccentColor, general.defaultTab,
        general.showAdultContent, general.blurAdultMedia, general.showAnimeLogos, general.selectedListLayoutId, general.selectedHomeLayoutId,
        general.titleLanguage, general.scoreFormat, general.hideScores, integrations.autoTranslateEnabled, integrations.preferredTranslator,
        integrations.translateSourceLanguage, integrations.translateTargetLanguage, general.appLanguage, general.fixedNavBar,
        general.airingNotificationsEnabled, general.splashAnimationEnabled, general.splashSoundEnabled,
        general.mangaReadingMode, general.mangaColorFilter, general.mangaFitMode, general.mangaBrightness,
        general.aniListNotificationsEnabled, general.malNotificationsEnabled, general.simklNotificationsEnabled,
        general.kitsuNotificationsEnabled, general.shikimoriNotificationsEnabled, general.notificationInterval,
        integrations.tmdbLanguage
    ) {
        com.kitsugi.animelist.data.settings.AppSettings(
            selectedThemeId = general.selectedThemeId,
            themeMode = general.themeMode,
            amoledBlack = general.amoledBlack,
            customAccentColor = general.customAccentColor,
            defaultTab = general.defaultTab,
            showAdultContent = general.showAdultContent,
            blurAdultMedia = general.blurAdultMedia,
            showAnimeLogos = general.showAnimeLogos,
            selectedListLayoutId = general.selectedListLayoutId,
            selectedHomeLayoutId = general.selectedHomeLayoutId,
            titleLanguage = general.titleLanguage,
            tmdbLanguage = integrations.tmdbLanguage,
            scoreFormat = general.scoreFormat,
            hideScores = general.hideScores,
            autoTranslateEnabled = integrations.autoTranslateEnabled,
            preferredTranslator = integrations.preferredTranslator,
            translateSourceLanguage = integrations.translateSourceLanguage,
            translateTargetLanguage = integrations.translateTargetLanguage,
            appLanguage = general.appLanguage,
            fixedNavBar = general.fixedNavBar,
            airingNotificationsEnabled = general.airingNotificationsEnabled,
            aniListNotificationsEnabled = general.aniListNotificationsEnabled,
            malNotificationsEnabled = general.malNotificationsEnabled,
            simklNotificationsEnabled = general.simklNotificationsEnabled,
            kitsuNotificationsEnabled = general.kitsuNotificationsEnabled,
            shikimoriNotificationsEnabled = general.shikimoriNotificationsEnabled,
            notificationInterval = general.notificationInterval,
            splashAnimationEnabled = general.splashAnimationEnabled,
            splashSoundEnabled = general.splashSoundEnabled,
            searchHistoryEnabled = general.searchHistoryEnabled,
            mangaReadingMode = general.mangaReadingMode,
            mangaColorFilter = general.mangaColorFilter,
            mangaFitMode = general.mangaFitMode,
            mangaBrightness = general.mangaBrightness
        )
    }
    KitsugiPreferencesSettingsDialog(
        embeddedMode = true,
        appSettings = localSettings,
        onThemeSelected = general.onThemeSelected,
        onThemeModeSelected = general.onThemeModeSelected,
        onAmoledBlackChanged = general.onAmoledBlackChanged,
        onCustomAccentColorChanged = general.onCustomAccentColorChanged,
        onDefaultTabSelected = general.onDefaultTabSelected,
        onAdultContentChanged = general.onAdultContentChanged,
        onBlurAdultMediaChanged = general.onBlurAdultMediaChanged,
        onShowAnimeLogosChanged = general.onShowAnimeLogosChanged,
        onListLayoutSelected = general.onListLayoutSelected,
        onTitleLanguageSelected = general.onTitleLanguageSelected,
        onScoreFormatSelected = general.onScoreFormatSelected,
        onHideScoresChanged = general.onHideScoresChanged,
        onHomeLayoutSelected = general.onHomeLayoutSelected,
        onAutoTranslateEnabledChanged = integrations.onAutoTranslateEnabledChanged,
        onPreferredTranslatorSelected = integrations.onPreferredTranslatorSelected,
        onTranslateSourceLanguageSelected = integrations.onTranslateSourceLanguageSelected,
        onTranslateTargetLanguageSelected = integrations.onTranslateTargetLanguageSelected,
        onAppLanguageSelected = general.onAppLanguageSelected,
        onFixedNavBarChanged = general.onFixedNavBarChanged,
        onAiringNotificationsChanged = general.onAiringNotificationsChanged,
        onAniListNotificationsChanged = general.onAniListNotificationsChanged,
        onMalNotificationsChanged = general.onMalNotificationsChanged,
        onSimklNotificationsChanged = general.onSimklNotificationsChanged,
        onKitsuNotificationsChanged = general.onKitsuNotificationsChanged,
        onShikimoriNotificationsChanged = general.onShikimoriNotificationsChanged,
        onNotificationIntervalChanged = general.onNotificationIntervalChanged,
        onSplashAnimationEnabledChanged = general.onSplashAnimationEnabledChanged,
        onSplashSoundEnabledChanged = general.onSplashSoundEnabledChanged,
        onTmdbLanguageChanged = integrations.onTmdbLanguageChanged,
        onSearchHistoryEnabledChanged = general.onSearchHistoryEnabledChanged,
        onMangaReadingModeSelected = general.onMangaReadingModeSelected,
        onMangaColorFilterSelected = general.onMangaColorFilterSelected,
        onMangaFitModeSelected = general.onMangaFitModeSelected,
        onMangaBrightnessChanged = general.onMangaBrightnessChanged,
        onDismiss = {}  // Sub-sayfada dismiss = no-op; geri butonu ile çıkılır
    )
}

@Composable
private fun SettingsPlayerContent(
    player: PlayerSettings,
    onDismiss: () -> Unit
) {
    KitsugiPlayerSettingsDialog(
        embeddedMode = true,
        playerPreference = player.playerPreference,
        preferredExternalPlayerPackage = player.preferredExternalPlayerPackage,
        isAutoplayEnabled = player.isAutoplayEnabled,
        skipIntroDurationSec = player.skipIntroDurationSec,
        defaultSubtitleSize = player.defaultSubtitleSize,
        defaultSubtitleColor = player.defaultSubtitleColor,
        subtitleBold = player.subtitleBold,
        subtitleOutlineEnabled = player.subtitleOutlineEnabled,
        defaultAudioBoost = player.defaultAudioBoost,
        defaultAudioDelayMs = player.defaultAudioDelayMs,
        minBufferMs = player.minBufferMs,
        maxBufferMs = player.maxBufferMs,
        bufferForPlaybackMs = player.bufferForPlaybackMs,
        bufferForPlaybackAfterRebufferMs = player.bufferForPlaybackAfterRebufferMs,
        backBufferDurationMs = player.backBufferDurationMs,
        dv7HandlingMode = player.dv7HandlingMode,
        stripHdr10PlusSei = player.stripHdr10PlusSei,
        preferredSubtitleLanguages = player.preferredSubtitleLanguages,
        addonSubtitleStartupMode = player.addonSubtitleStartupMode,
        qualityProfileJson = player.qualityProfileJson,
        parallelRangeEnabled = player.parallelRangeEnabled,
        frameRateMatchingMode = player.frameRateMatchingMode,
        resolutionMatchingEnabled = player.resolutionMatchingEnabled,
        gestureVolumeEnabled = player.gestureVolumeEnabled,
        gestureBrightnessEnabled = player.gestureBrightnessEnabled,
        gestureZoomEnabled = player.gestureZoomEnabled,
        doubleTapSeekSeconds = player.doubleTapSeekSeconds,
        holdSpeedMultiplier = player.holdSpeedMultiplier,
        gestureScrollSensitivity = player.gestureScrollSensitivity,
        onPlayerPreferenceSelected = player.onPlayerPreferenceSelected,
        onPreferredExternalPlayerPackageSelected = player.onPreferredExternalPlayerPackageSelected,
        onAutoplayEnabledChanged = player.onAutoplayEnabledChanged,
        onSkipIntroDurationSecSelected = player.onSkipIntroDurationSecSelected,
        onDefaultSubtitleSizeSelected = player.onDefaultSubtitleSizeSelected,
        onDefaultSubtitleColorSelected = player.onDefaultSubtitleColorSelected,
        onSubtitleBoldChanged = player.onSubtitleBoldChanged,
        onSubtitleOutlineEnabledChanged = player.onSubtitleOutlineEnabledChanged,
        onDefaultAudioBoostSelected = player.onDefaultAudioBoostSelected,
        onDefaultAudioDelayMsSelected = player.onDefaultAudioDelayMsSelected,
        onPreferredSubtitleLanguagesSelected = player.onPreferredSubtitleLanguagesSelected,
        onAddonSubtitleStartupModeSelected = player.onAddonSubtitleStartupModeSelected,
        onBufferSettingsChanged = player.onBufferSettingsChanged,
        onDv7HandlingModeSelected = player.onDv7HandlingModeSelected,
        onStripHdr10PlusSeiChanged = player.onStripHdr10PlusSeiChanged,
        onQualityProfileSelected = player.onQualityProfileSelected,
        onParallelRangeEnabledChanged = player.onParallelRangeEnabledChanged,
        onFrameRateMatchingModeSelected = player.onFrameRateMatchingModeSelected,
        onResolutionMatchingEnabledChanged = player.onResolutionMatchingEnabledChanged,
        onGestureVolumeEnabledChanged = player.onGestureVolumeEnabledChanged,
        onGestureBrightnessEnabledChanged = player.onGestureBrightnessEnabledChanged,
        onGestureZoomEnabledChanged = player.onGestureZoomEnabledChanged,
        onDoubleTapSeekSecondsSelected = player.onDoubleTapSeekSecondsSelected,
        onHoldSpeedMultiplierSelected = player.onHoldSpeedMultiplierSelected,
        onGestureScrollSensitivityChanged = player.onGestureScrollSensitivityChanged,
        previewSeekbarEnabled = player.previewSeekbarEnabled,
        onPreviewSeekbarEnabledChanged = player.onPreviewSeekbarEnabledChanged,
        aspectMode = player.aspectMode,
        onAspectModeSelected = player.onAspectModeSelected,
        liveHelperEnabled = player.liveHelperEnabled,
        onLiveHelperEnabledChanged = player.onLiveHelperEnabledChanged,
        enableAssExtractor = player.enableAssExtractor,
        onEnableAssExtractorChanged = player.onEnableAssExtractorChanged,
        showPlayerTitle = player.showPlayerTitle,
        onShowPlayerTitleChanged = player.onShowPlayerTitleChanged,
        showPlayerResolution = player.showPlayerResolution,
        onShowPlayerResolutionChanged = player.onShowPlayerResolutionChanged,
        showMediaInfo = player.showMediaInfo,
        onShowMediaInfoChanged = player.onShowMediaInfoChanged,
        stillWatchingEnabled = player.stillWatchingEnabled,
        onStillWatchingEnabledChanged = player.onStillWatchingEnabledChanged,
        stillWatchingThresholdMinutes = player.stillWatchingThresholdMinutes,
        onStillWatchingThresholdMinutesChanged = player.onStillWatchingThresholdMinutesChanged,
        postPlayMode = player.postPlayMode,
        onPostPlayModeChanged = player.onPostPlayModeChanged,
        autoplaySessionLimit = player.autoplaySessionLimit,
        onAutoplaySessionLimitChanged = player.onAutoplaySessionLimitChanged,
        gainBoostDb = player.gainBoostDb,
        onGainBoostDbChanged = player.onGainBoostDbChanged,
        subtitleDelayMs = player.subtitleDelayMs,
        onSubtitleDelayMsChanged = player.onSubtitleDelayMsChanged,
        decoderPriority = player.decoderPriority,
        onDecoderPriorityChanged = player.onDecoderPriorityChanged,
        onDismiss = onDismiss
    )
}

@Composable
private fun SettingsAddonsContent(
    addon: AddonSettings,
    manga: MangaSettings,
    onOpenDiagnostic: () -> Unit,
    onExplorePlugin: (String) -> Unit,
    onOpenPluginPicker: () -> Unit
) {
    KitsugiAddonsSettingsDialog(
        embeddedMode = true,
        addons = addon.addons,
        initialDebridToken = addon.debridToken,
        repos = addon.repos,
        repoPlugins = addon.repoPlugins,
        repoLoadingState = addon.repoLoadingState,
        csPlugins = addon.csPlugins,
        onAddAddon = addon.onAddAddon,
        onToggleAddon = addon.onToggleAddon,
        onDeleteAddon = addon.onDeleteAddon,
        onSaveDebridToken = addon.onSaveDebridToken,
        onAddRepo = addon.onAddRepo,
        onDeleteRepo = addon.onDeleteRepo,
        onFetchRepoPlugins = addon.onFetchRepoPlugins,
        onInstallPlugin = addon.onInstallPlugin,
        onInstallAllPlugins = addon.onInstallAllPlugins,
        onUpdateAllPlugins = addon.onUpdateAllPlugins,
        bulkInstallRepoUrl = addon.bulkInstallRepoUrl,
        bulkInstallRepoName = addon.bulkInstallRepoName,
        bulkInstallDone = addon.bulkInstallDone,
        bulkInstallTotal = addon.bulkInstallTotal,
        bulkInstallCurrentName = addon.bulkInstallCurrentName,
        bulkInstallResultMessage = addon.bulkInstallResultMessage,
        onClearBulkInstallResult = addon.onClearBulkInstallResult,
        onToggleCsPlugin = addon.onToggleCsPlugin,
        onUninstallCsPlugin = addon.onUninstallCsPlugin,
        useGithubProxy = addon.useGithubProxy,
        onUseGithubProxyChanged = addon.onUseGithubProxyChanged,
        isCheckingUpdates = addon.isCheckingUpdates,
        availableUpdatesCount = addon.availableUpdatesCount,
        onCheckForUpdates = addon.onCheckForUpdates,
        onUpdateAllPendingPlugins = addon.onUpdateAllPendingPlugins,
        onRefreshRepo = addon.onRefreshRepo,
        mangaSources = manga.mangaSources,
        onInstallMangaExtension = manga.onInstallMangaExtension,
        onDeleteMangaExtension = manga.onDeleteMangaExtension,
        mangaRepos = manga.mangaRepos,
        mangaRepoExtensions = manga.mangaRepoExtensions,
        mangaRepoLoadingState = manga.mangaRepoLoadingState,
        onAddMangaRepo = manga.onAddMangaRepo,
        onDeleteMangaRepo = manga.onDeleteMangaRepo,
        onFetchMangaRepo = manga.onFetchMangaRepo,
        onInstallMangaApk = manga.onInstallMangaApk,
        onInstallAllMangaExtensions = manga.onInstallAllMangaExtensions,
        onUpdateAllMangaExtensions = manga.onUpdateAllMangaExtensions,
        mangaBulkInstallRepoUrl = manga.mangaBulkInstallRepoUrl,
        mangaBulkInstallDone = manga.mangaBulkInstallDone,
        mangaBulkInstallTotal = manga.mangaBulkInstallTotal,
        mangaBulkInstallCurrentName = manga.mangaBulkInstallCurrentName,
        onGetInstalledMangaVersionCode = manga.onGetInstalledMangaVersionCode,
        onGetInstalledMangaVersion = manga.onGetInstalledMangaVersion,
        mangaSourceStateReport = manga.mangaSourceStateReport,
        onGetMangaSourceHealthStatus = manga.onGetMangaSourceHealthStatus,
        onGetMangaSourceRuntimeStats = manga.onGetMangaSourceRuntimeStats,
        onGetMangaConfiguredDomain = manga.onGetMangaConfiguredDomain,
        onGetMangaConfiguredBaseUrl = manga.onGetMangaConfiguredBaseUrl,
        onGetMangaSourceUserAgent = manga.onGetMangaSourceUserAgent,
        onGetMangaSourceSlowdownEnabled = manga.onGetMangaSourceSlowdownEnabled,
        onSetMangaSourceUserAgent = manga.onSetMangaSourceUserAgent,
        onSetMangaSourceSlowdownEnabled = manga.onSetMangaSourceSlowdownEnabled,
        onSetMangaSourceDomain = manga.onSetMangaSourceDomain,
        onResetMangaSourceDiagnostics = manga.onResetMangaSourceDiagnostics,
        onClearAllMangaSourceDiagnostics = manga.onClearAllMangaSourceDiagnostics,
        onIsMangaSourceBusy = manga.onIsMangaSourceBusy,
        onQuickCheckMangaSource = manga.onQuickCheckMangaSource,
        onRefreshMangaSourceMirror = manga.onRefreshMangaSourceMirror,
        onClearMangaSourceMirror = manga.onClearMangaSourceMirror,
        onOpenMangaSourceHealthScreen = manga.onOpenMangaSourceHealthScreen,
        onForceCheckMangaUpdates = manga.onForceCheckMangaUpdates,
        untrustedRepoToConfirm = manga.untrustedRepoToConfirm,
        untrustedSignatureToConfirm = manga.untrustedSignatureToConfirm,
        onConfirmUntrustedRepo = manga.onConfirmUntrustedRepo,
        onDismissUntrustedRepo = manga.onDismissUntrustedRepo,
        onConfirmUntrustedSignature = manga.onConfirmUntrustedSignature,
        onDismissUntrustedSignature = manga.onDismissUntrustedSignature,
        onOpenDiagnostic = onOpenDiagnostic,
        onExplorePlugin = onExplorePlugin,
        onOpenPluginPicker = onOpenPluginPicker,
        onDismiss = {}
    )
}

@Composable
private fun SettingsIntegrationsContent(integrations: IntegrationsSettings) {
    KitsugiIntegrationsSettingsDialog(
        embeddedMode = true,
        tmdbEnabled = integrations.tmdbEnabled,
        onTmdbEnabledChanged = integrations.onTmdbEnabledChanged,
        tmdbApiKey = integrations.tmdbApiKey,
        onTmdbApiKeyChanged = integrations.onTmdbApiKeyChanged,
        tmdbModernHomeEnabled = integrations.tmdbModernHomeEnabled,
        onTmdbModernHomeEnabledChanged = integrations.onTmdbModernHomeEnabledChanged,
        tmdbEnrichContinueWatching = integrations.tmdbEnrichContinueWatching,
        onTmdbEnrichContinueWatchingChanged = integrations.onTmdbEnrichContinueWatchingChanged,
        tmdbLanguage = integrations.tmdbLanguage,
        onTmdbLanguageChanged = integrations.onTmdbLanguageChanged,
        tmdbUseArtwork = integrations.tmdbUseArtwork,
        onTmdbUseArtworkChanged = integrations.onTmdbUseArtworkChanged,
        tmdbUseBasicInfo = integrations.tmdbUseBasicInfo,
        onTmdbUseBasicInfoChanged = integrations.onTmdbUseBasicInfoChanged,
        tmdbUseDetails = integrations.tmdbUseDetails,
        onTmdbUseDetailsChanged = integrations.onTmdbUseDetailsChanged,
        tmdbUseReleaseDates = integrations.tmdbUseReleaseDates,
        onTmdbUseReleaseDatesChanged = integrations.onTmdbUseReleaseDatesChanged,
        tmdbUseCredits = integrations.tmdbUseCredits,
        onTmdbUseCreditsChanged = integrations.onTmdbUseCreditsChanged,
        tmdbUseProductions = integrations.tmdbUseProductions,
        onTmdbUseProductionsChanged = integrations.onTmdbUseProductionsChanged,
        tmdbUseNetworks = integrations.tmdbUseNetworks,
        onTmdbUseNetworksChanged = integrations.onTmdbUseNetworksChanged,
        tmdbUseEpisodes = integrations.tmdbUseEpisodes,
        onTmdbUseEpisodesChanged = integrations.onTmdbUseEpisodesChanged,
        tmdbUseTrailers = integrations.tmdbUseTrailers,
        onTmdbUseTrailersChanged = integrations.onTmdbUseTrailersChanged,
        tmdbUseMoreLikeThis = integrations.tmdbUseMoreLikeThis,
        onTmdbUseMoreLikeThisChanged = integrations.onTmdbUseMoreLikeThisChanged,
        tmdbUseCollections = integrations.tmdbUseCollections,
        onTmdbUseCollectionsChanged = integrations.onTmdbUseCollectionsChanged,
        mdbListEnabled = integrations.mdbListEnabled,
        onMdbListEnabledChanged = integrations.onMdbListEnabledChanged,
        mdbListApiKey = integrations.mdbListApiKey,
        onMdbListApiKeyChanged = integrations.onMdbListApiKeyChanged,
        mdbListShowImdb = integrations.mdbListShowImdb,
        onMdbListShowImdbChanged = integrations.onMdbListShowImdbChanged,
        mdbListShowTomatoes = integrations.mdbListShowTomatoes,
        onMdbListShowTomatoesChanged = integrations.onMdbListShowTomatoesChanged,
        mdbListShowMetacritic = integrations.mdbListShowMetacritic,
        onMdbListShowMetacriticChanged = integrations.onMdbListShowMetacriticChanged,
        mdbListShowAudience = integrations.mdbListShowAudience,
        onMdbListShowAudienceChanged = integrations.onMdbListShowAudienceChanged,
        mdbListShowLetterboxd = integrations.mdbListShowLetterboxd,
        onMdbListShowLetterboxdChanged = integrations.onMdbListShowLetterboxdChanged,
        mdbListShowTmdb = integrations.mdbListShowTmdb,
        onMdbListShowTmdbChanged = integrations.onMdbListShowTmdbChanged,
        mdbListShowTrakt = integrations.mdbListShowTrakt,
        onMdbListShowTraktChanged = integrations.onMdbListShowTraktChanged,
        aniSkipEnabled = integrations.aniSkipEnabled,
        onAniSkipEnabledChanged = integrations.onAniSkipEnabledChanged,
        aniSkipAutoSkip = integrations.aniSkipAutoSkip,
        onAniSkipAutoSkipChanged = integrations.onAniSkipAutoSkipChanged,
        animeSkipClientId = integrations.animeSkipClientId,
        onAnimeSkipClientIdChanged = integrations.onAnimeSkipClientIdChanged,
        fanartTvEnabled = integrations.fanartTvEnabled,
        onFanartTvEnabledChanged = integrations.onFanartTvEnabledChanged,
        fanartTvApiKey = integrations.fanartTvApiKey,
        onFanartTvApiKeyChanged = integrations.onFanartTvApiKeyChanged,
        onDismiss = {}
    )
}


// ─── Tema seçenekleri (statik) ────────────────────────────────────────────────

private val themeOptions = listOf(
    KitsugiChoiceOption("mint", "Mint", "Mevcut Kitsugi açık mint teması", Color(0xFFC8F4EF)),
    KitsugiChoiceOption("pink", "Pembe", "Canlı pembe vurgu rengi", KitsugiColors.AccentPink),
    KitsugiChoiceOption("purple", "Mor", "Neon ve modern mor görünüm", KitsugiColors.AccentPurple),
    KitsugiChoiceOption("blue", "Mavi", "Sade ve temiz mavi vurgu", KitsugiColors.AccentBlue),
    KitsugiChoiceOption("green", "Yeşil", "Doğal ve dengeli yeşil vurgu", KitsugiColors.AccentGreen),
    KitsugiChoiceOption("red", "Kırmızı", "Tutkulu ve enerjik kırmızı vurgu", KitsugiColors.AccentRed),
    KitsugiChoiceOption("orange", "Turuncu", "Dinamik ve sıcak turuncu vurgu", KitsugiColors.AccentOrange),
    KitsugiChoiceOption("yellow", "Sarı", "Parlak ve neşeli sarı vurgu", KitsugiColors.AccentYellow),
    KitsugiChoiceOption("teal", "Turkuaz", "Ferah ve sakin turkuaz vurgu", KitsugiColors.AccentTeal),
    KitsugiChoiceOption("indigo", "İndigo", "Zengin ve derin indigo vurgu", KitsugiColors.AccentIndigo)
)