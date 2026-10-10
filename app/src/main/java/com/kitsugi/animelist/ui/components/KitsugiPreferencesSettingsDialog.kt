package com.kitsugi.animelist.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import com.kitsugi.animelist.ui.utils.tvClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.Tablet
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.ViewList
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalKitsugiColors
import kotlinx.coroutines.launch

import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext
import com.kitsugi.animelist.ui.components.KitsugiDropdownMenu
import com.kitsugi.animelist.ui.components.KitsugiDropdownItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KitsugiPreferencesSettingsDialog(
    appSettings: com.kitsugi.animelist.data.settings.AppSettings,
    onThemeSelected: (String) -> Unit,
    onThemeModeSelected: (String) -> Unit,
    onAmoledBlackChanged: (Boolean) -> Unit,
    onCustomAccentColorChanged: (Int) -> Unit,
    onDefaultTabSelected: (String) -> Unit,
    onAdultContentChanged: (Boolean) -> Unit,
    onBlurAdultMediaChanged: (Boolean) -> Unit = {},
    onShowAnimeLogosChanged: (Boolean) -> Unit,
    onListLayoutSelected: (String) -> Unit,
    onTitleLanguageSelected: (String) -> Unit,
    onScoreFormatSelected: (String) -> Unit,
    onHideScoresChanged: (Boolean) -> Unit,
    onHomeLayoutSelected: (String) -> Unit,
    onAutoTranslateEnabledChanged: (Boolean) -> Unit,
    onPreferredTranslatorSelected: (String) -> Unit = {},
    onTranslateSourceLanguageSelected: (String) -> Unit = {},
    onTranslateTargetLanguageSelected: (String) -> Unit = {},
    onDismiss: () -> Unit,
    onAppLanguageSelected: (String) -> Unit = {},
    onFixedNavBarChanged: (Boolean) -> Unit = {},
    // T3.3: Yayın takvimi bildirimleri
    onAiringNotificationsChanged: (Boolean) -> Unit = {},
    onAniListNotificationsChanged: (Boolean) -> Unit = {},
    onMalNotificationsChanged: (Boolean) -> Unit = {},
    onSimklNotificationsChanged: (Boolean) -> Unit = {},
    onKitsuNotificationsChanged: (Boolean) -> Unit = {},
    onShikimoriNotificationsChanged: (Boolean) -> Unit = {},
    onNotificationIntervalChanged: (Int) -> Unit = {},
    onSearchHistoryEnabledChanged: (Boolean) -> Unit = {},
    onSplashAnimationEnabledChanged: (Boolean) -> Unit = {},
    onSplashSoundEnabledChanged: (Boolean) -> Unit = {},
    onTmdbLanguageChanged: (String) -> Unit = {},
    // ─── T1-07 – Manga Okuyucu Varsayılan Ayarları ───────────────────────
    onMangaReadingModeSelected: (String) -> Unit = {},
    onMangaColorFilterSelected: (String) -> Unit = {},
    onMangaFitModeSelected: (String) -> Unit = {},
    onMangaBrightnessChanged: (Float) -> Unit = {},
    embeddedMode: Boolean = false
) {
    val pagerState = rememberPagerState(pageCount = { 3 })
    val scope = rememberCoroutineScope()
    val accentColor = LocalKitsugiAccent.current
    val KitsugiColors = LocalKitsugiColors.current

    val appearanceScrollState = rememberScrollState()
    val listScoreScrollState = rememberScrollState()
    val mangaReaderScrollState = rememberScrollState()

    val activeScrollState = when (pagerState.currentPage) {
        0 -> appearanceScrollState
        1 -> listScoreScrollState
        else -> mangaReaderScrollState
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
                            text = stringResource(R.string.settings_pref_title),
                            color = KitsugiColors.textPrimary,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.settings_close), tint = KitsugiColors.textSecondary)
                        }
                    }
                }
                TabRow(
                    selectedTabIndex = pagerState.currentPage,
                    containerColor = KitsugiColors.surface,
                    contentColor = accentColor
                ) {
                    Tab(
                        selected = pagerState.currentPage == 0,
                        onClick = {
                            scope.launch { pagerState.animateScrollToPage(0) }
                        },
                        text = {
                            Text(
                                stringResource(R.string.settings_tab_appearance),
                                fontWeight = if (pagerState.currentPage == 0) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 13.sp
                            )
                        }
                    )
                    Tab(
                        selected = pagerState.currentPage == 1,
                        onClick = {
                            scope.launch { pagerState.animateScrollToPage(1) }
                        },
                        text = {
                            Text(
                                stringResource(R.string.settings_tab_list_score),
                                fontWeight = if (pagerState.currentPage == 1) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 13.sp
                            )
                        }
                    )
                    Tab(
                        selected = pagerState.currentPage == 2,
                        onClick = {
                            scope.launch { pagerState.animateScrollToPage(2) }
                        },
                        text = {
                            Text(
                                stringResource(R.string.settings_tab_manga_reader),
                                fontWeight = if (pagerState.currentPage == 2) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 13.sp
                            )
                        }
                    )
                }
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
                    0 -> AppearanceTab(
                        appSettings = appSettings,
                        onThemeSelected = onThemeSelected,
                        onThemeModeSelected = onThemeModeSelected,
                        onAmoledBlackChanged = onAmoledBlackChanged,
                        onCustomAccentColorChanged = onCustomAccentColorChanged,
                        onDefaultTabSelected = onDefaultTabSelected,
                        onHomeLayoutSelected = onHomeLayoutSelected,
                        onAdultContentChanged = onAdultContentChanged,
                        onBlurAdultMediaChanged = onBlurAdultMediaChanged,
                        onShowAnimeLogosChanged = onShowAnimeLogosChanged,
                        onAppLanguageSelected = onAppLanguageSelected,
                        onFixedNavBarChanged = onFixedNavBarChanged,
                        onSplashAnimationEnabledChanged = onSplashAnimationEnabledChanged,
                        onSplashSoundEnabledChanged = onSplashSoundEnabledChanged,
                        accentColor = accentColor,
                        scrollState = appearanceScrollState
                    )
                    1 -> ListScoreTab(
                        appSettings = appSettings,
                        onListLayoutSelected = onListLayoutSelected,
                        onTitleLanguageSelected = onTitleLanguageSelected,
                        onTmdbLanguageChanged = onTmdbLanguageChanged,
                        onScoreFormatSelected = onScoreFormatSelected,
                        onHideScoresChanged = onHideScoresChanged,
                        onAutoTranslateEnabledChanged = onAutoTranslateEnabledChanged,
                        onPreferredTranslatorSelected = onPreferredTranslatorSelected,
                        onTranslateSourceLanguageSelected = onTranslateSourceLanguageSelected,
                        onTranslateTargetLanguageSelected = onTranslateTargetLanguageSelected,
                        onAiringNotificationsChanged = onAiringNotificationsChanged,
                        onAniListNotificationsChanged = onAniListNotificationsChanged,
                        onMalNotificationsChanged = onMalNotificationsChanged,
                        onSimklNotificationsChanged = onSimklNotificationsChanged,
                        onKitsuNotificationsChanged = onKitsuNotificationsChanged,
                        onShikimoriNotificationsChanged = onShikimoriNotificationsChanged,
                        onNotificationIntervalChanged = onNotificationIntervalChanged,
                        onSearchHistoryEnabledChanged = onSearchHistoryEnabledChanged,
                        accentColor = accentColor,
                        scrollState = listScoreScrollState
                    )
                    2 -> MangaReaderTab(
                        appSettings = appSettings,
                        onMangaReadingModeSelected = onMangaReadingModeSelected,
                        onMangaColorFilterSelected = onMangaColorFilterSelected,
                        onMangaFitModeSelected = onMangaFitModeSelected,
                        onMangaBrightnessChanged = onMangaBrightnessChanged,
                        accentColor = accentColor,
                        scrollState = mangaReaderScrollState
                    )
                }
            }

            if (!embeddedMode) {
                // Footer
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.settings_ok), color = accentColor, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
    }
}


@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun AppearanceTab(
    appSettings: com.kitsugi.animelist.data.settings.AppSettings,
    onThemeSelected: (String) -> Unit,
    onThemeModeSelected: (String) -> Unit,
    onAmoledBlackChanged: (Boolean) -> Unit,
    onCustomAccentColorChanged: (Int) -> Unit,
    onDefaultTabSelected: (String) -> Unit,
    onHomeLayoutSelected: (String) -> Unit,
    onAdultContentChanged: (Boolean) -> Unit,
    onBlurAdultMediaChanged: (Boolean) -> Unit = {},
    onShowAnimeLogosChanged: (Boolean) -> Unit,
    onAppLanguageSelected: (String) -> Unit,
    onFixedNavBarChanged: (Boolean) -> Unit,
    onSplashAnimationEnabledChanged: (Boolean) -> Unit = {},
    onSplashSoundEnabledChanged: (Boolean) -> Unit = {},
    accentColor: Color,
    scrollState: ScrollState = rememberScrollState()
) {
    val KitsugiColors = LocalKitsugiColors.current
    val selectedThemeId = appSettings.selectedThemeId
    val themeMode = appSettings.themeMode
    val amoledBlack = appSettings.amoledBlack
    val customAccentColor = appSettings.customAccentColor
    val defaultTab = appSettings.defaultTab
    val selectedHomeLayoutId = appSettings.selectedHomeLayoutId
    val showAdultContent = appSettings.showAdultContent
    val blurAdultMedia = appSettings.blurAdultMedia
    val showAnimeLogos = appSettings.showAnimeLogos
    val fixedNavBar = appSettings.fixedNavBar
    val splashAnimationEnabled = appSettings.splashAnimationEnabled
    val splashSoundEnabled = appSettings.splashSoundEnabled

    val themeModeOptions = listOf(
        KitsugiChoiceOption(id = "FOLLOW_SYSTEM", title = stringResource(R.string.option_follow_system), description = ""),
        KitsugiChoiceOption(id = "LIGHT", title = stringResource(R.string.option_light), description = ""),
        KitsugiChoiceOption(id = "DARK", title = stringResource(R.string.option_dark), description = "")
    )

    val defaultTabOptions = listOf(
        KitsugiChoiceOption(id = "LAST_USED", title = stringResource(R.string.option_last_used), description = ""),
        KitsugiChoiceOption(id = "Explore", title = stringResource(R.string.tab_explore), description = ""),
        KitsugiChoiceOption(id = "MyList", title = stringResource(R.string.tab_mylist), description = ""),
        KitsugiChoiceOption(id = "Search", title = stringResource(R.string.tab_search), description = "")
    )


    val themeOptions = listOf(
        KitsugiChoiceOption(id = "mint", title = stringResource(R.string.color_mint), description = "", color = Color(0xFFC8F4EF)),
        KitsugiChoiceOption(id = "pink", title = stringResource(R.string.color_pink), description = "", color = com.kitsugi.animelist.ui.theme.KitsugiColors.AccentPink),
        KitsugiChoiceOption(id = "purple", title = stringResource(R.string.color_purple), description = "", color = com.kitsugi.animelist.ui.theme.KitsugiColors.AccentPurple),
        KitsugiChoiceOption(id = "blue", title = stringResource(R.string.color_blue), description = "", color = com.kitsugi.animelist.ui.theme.KitsugiColors.AccentBlue),
        KitsugiChoiceOption(id = "green", title = stringResource(R.string.color_green), description = "", color = com.kitsugi.animelist.ui.theme.KitsugiColors.AccentGreen),
        KitsugiChoiceOption(id = "red", title = stringResource(R.string.color_red), description = "", color = com.kitsugi.animelist.ui.theme.KitsugiColors.AccentRed),
        KitsugiChoiceOption(id = "orange", title = stringResource(R.string.color_orange), description = "", color = com.kitsugi.animelist.ui.theme.KitsugiColors.AccentOrange),
        KitsugiChoiceOption(id = "yellow", title = stringResource(R.string.color_yellow), description = "", color = com.kitsugi.animelist.ui.theme.KitsugiColors.AccentYellow),
        KitsugiChoiceOption(id = "teal", title = stringResource(R.string.color_teal), description = "", color = com.kitsugi.animelist.ui.theme.KitsugiColors.AccentTeal),
        KitsugiChoiceOption(id = "indigo", title = stringResource(R.string.color_indigo), description = "", color = com.kitsugi.animelist.ui.theme.KitsugiColors.AccentIndigo)
    )

    val appLanguageOptions = listOf(
        KitsugiChoiceOption(id = "system", title = stringResource(R.string.option_follow_system), description = ""),
        KitsugiChoiceOption(id = "tr", title = "Türkçe", description = ""),
        KitsugiChoiceOption(id = "en", title = "English", description = "")
    )

    var showColorPicker by remember { mutableStateOf(false) }
    var colorInputText by remember { mutableStateOf(if (customAccentColor != 0) String.format("#%06X", 0xFFFFFF and customAccentColor) else "") }

    var showThemeModeMenu by remember { mutableStateOf(false) }
    var showLanguageMenu by remember { mutableStateOf(false) }
    var showDefaultTabMenu by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // TEMA & RENKLER
        KitsugiSettingsSection(title = stringResource(R.string.settings_section_theme_look)) {
            val isDarkThemeActive = when (themeMode) {
                "LIGHT" -> false
                "DARK" -> true
                else -> isSystemInDarkTheme()
            }

            // Uiverse (Galahhad) Sun/Moon animasyonlu tema geçiş butonu
            KitsugiThemeModeSwitchItem(
                title = stringResource(R.string.settings_dark_theme),
                description = if (themeMode == "DARK") stringResource(R.string.settings_dark_theme_night)
                else if (themeMode == "LIGHT") stringResource(R.string.settings_dark_theme_day)
                else stringResource(R.string.settings_dark_theme_system),
                icon = if (isDarkThemeActive) Icons.Rounded.DarkMode else Icons.Rounded.LightMode,
                iconColor = accentColor,
                checked = isDarkThemeActive,
                onCheckedChange = { isDark ->
                    onThemeModeSelected(if (isDark) "DARK" else "LIGHT")
                }
            )

            KitsugiSettingsDivider()

            Box {
                KitsugiSettingsListItem(
                    title = stringResource(R.string.settings_theme_mode),
                    description = stringResource(R.string.settings_theme_mode_desc),
                    value = themeModeOptions.find { it.id == themeMode }?.title ?: "",
                    icon = Icons.Rounded.Palette,
                    iconColor = accentColor,
                    onClick = { showThemeModeMenu = true }
                )
                KitsugiDropdownMenu(expanded = showThemeModeMenu, onDismissRequest = { showThemeModeMenu = false }) {
                    themeModeOptions.forEach { opt ->
                        KitsugiDropdownItem(
                            text = opt.title,
                            selected = opt.id == themeMode,
                            onClick = {
                                onThemeModeSelected(opt.id)
                                showThemeModeMenu = false
                            }
                        )
                    }
                }
            }

            KitsugiSettingsDivider()

            KitsugiSettingsSwitchItem(
                title = stringResource(R.string.settings_amoled_black),
                description = stringResource(R.string.settings_amoled_black_desc),
                icon = Icons.Rounded.Palette,
                iconColor = accentColor,
                checked = amoledBlack,
                enabled = themeMode != "LIGHT",
                onCheckedChange = onAmoledBlackChanged
            )

            KitsugiSettingsDivider()

            // Inline Accent Color Selector
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    text = stringResource(R.string.settings_theme_color),
                    color = KitsugiColors.textPrimary,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.settings_theme_color_desc),
                    color = KitsugiColors.textSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(12.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    themeOptions.forEach { themeOpt ->
                        val isSelected = themeOpt.id == selectedThemeId && customAccentColor == 0
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .background(
                                    color = if (isSelected) KitsugiColors.surfaceSoft else Color.Transparent,
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .tvClickable(shape = RoundedCornerShape(14.dp)) {
                                    onCustomAccentColorChanged(0)
                                    onThemeSelected(themeOpt.id)
                                }
                                .padding(5.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(themeOpt.color ?: accentColor, shape = CircleShape)
                            )
                        }
                    }

                    // Özel Renk Seçici Kutusu
                    val isCustomSelected = customAccentColor != 0
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .background(
                                color = if (isCustomSelected) KitsugiColors.surfaceSoft else Color.Transparent,
                                shape = RoundedCornerShape(14.dp)
                            )
                            .tvClickable(shape = RoundedCornerShape(14.dp)) {
                                showColorPicker = true
                            }
                            .padding(5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    color = if (isCustomSelected) Color(customAccentColor) else KitsugiColors.surfaceStrong,
                                    shape = CircleShape
                                )
                                .border(
                                    width = 1.5.dp,
                                    color = if (isCustomSelected) Color.White else KitsugiColors.textMuted,
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "+",
                                color = if (isCustomSelected) Color.White else KitsugiColors.textPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                        }
                    }
                }
            }
        }

        // KULLANICI ARAYÜZÜ & DAVRANIŞ
        KitsugiSettingsSection(title = stringResource(R.string.settings_section_nav_start)) {
            Box {
                KitsugiSettingsListItem(
                    title = stringResource(R.string.settings_app_language),
                    description = stringResource(R.string.settings_app_language_desc),
                    value = appLanguageOptions.find { it.id == appSettings.appLanguage }?.title ?: "",
                    icon = Icons.Rounded.Translate,
                    iconColor = accentColor,
                    onClick = { showLanguageMenu = true }
                )
                KitsugiDropdownMenu(expanded = showLanguageMenu, onDismissRequest = { showLanguageMenu = false }) {
                    appLanguageOptions.forEach { opt ->
                        KitsugiDropdownItem(
                            text = opt.title,
                            selected = opt.id == appSettings.appLanguage,
                            onClick = {
                                onAppLanguageSelected(opt.id)
                                showLanguageMenu = false
                            }
                        )
                    }
                }
            }

            KitsugiSettingsDivider()

            Box {
                KitsugiSettingsListItem(
                    title = stringResource(R.string.option_last_used),
                    description = stringResource(R.string.settings_default_tab_desc),
                    value = defaultTabOptions.find { it.id == defaultTab }?.title ?: "",
                    icon = Icons.Rounded.Settings,
                    iconColor = accentColor,
                    onClick = { showDefaultTabMenu = true }
                )
                KitsugiDropdownMenu(expanded = showDefaultTabMenu, onDismissRequest = { showDefaultTabMenu = false }) {
                    defaultTabOptions.forEach { opt ->
                        KitsugiDropdownItem(
                            text = opt.title,
                            selected = opt.id == defaultTab,
                            onClick = {
                                onDefaultTabSelected(opt.id)
                                showDefaultTabMenu = false
                            }
                        )
                    }
                }
            }

            KitsugiSettingsDivider()

            KitsugiSettingsSwitchItem(
                title = stringResource(R.string.settings_fixed_nav_bar),
                description = stringResource(R.string.settings_fixed_nav_bar_desc),
                icon = Icons.Rounded.Settings,
                iconColor = accentColor,
                checked = fixedNavBar,
                onCheckedChange = onFixedNavBarChanged
            )

            KitsugiSettingsDivider()

            KitsugiSettingsSwitchItem(
                title = stringResource(R.string.settings_adult_content),
                description = if (showAdultContent) stringResource(R.string.settings_adult_content_enabled_desc) else stringResource(R.string.settings_adult_content_disabled_desc),
                icon = Icons.Rounded.Palette,
                iconColor = accentColor,
                checked = showAdultContent,
                onCheckedChange = onAdultContentChanged
            )

            KitsugiSettingsDivider()

            KitsugiSettingsSwitchItem(
                title = stringResource(R.string.settings_blur_adult),
                description = if (blurAdultMedia) stringResource(R.string.settings_blur_adult_enabled_desc) else stringResource(R.string.settings_blur_adult_disabled_desc),
                icon = Icons.Rounded.Palette,
                iconColor = accentColor,
                checked = blurAdultMedia,
                onCheckedChange = onBlurAdultMediaChanged
            )

            KitsugiSettingsDivider()

            KitsugiSettingsSwitchItem(
                title = stringResource(R.string.settings_anime_logos),
                description = if (showAnimeLogos) stringResource(R.string.settings_anime_logos_enabled_desc) else stringResource(R.string.settings_anime_logos_disabled_desc),
                icon = Icons.Rounded.Palette,
                iconColor = accentColor,
                checked = showAnimeLogos,
                onCheckedChange = onShowAnimeLogosChanged
            )
        }

        // AÇILIŞ EKRANI
        KitsugiSettingsSection(title = stringResource(R.string.settings_splash_screen)) {
            KitsugiSettingsSwitchItem(
                title = stringResource(R.string.settings_splash_animation_and_sound),
                description = if (splashAnimationEnabled) stringResource(R.string.settings_splash_animation_and_sound_enabled) else stringResource(R.string.settings_splash_animation_and_sound_disabled),
                icon = Icons.Rounded.Palette,
                iconColor = accentColor,
                checked = splashAnimationEnabled,
                onCheckedChange = { enabled ->
                    onSplashAnimationEnabledChanged(enabled)
                    onSplashSoundEnabledChanged(enabled)
                }
            )
        }
    }

    if (showColorPicker) {
        CustomColorPickerDialog(
            colorInputText = colorInputText,
            onColorInputTextChange = { colorInputText = it },
            accentColor = accentColor,
            onDismissRequest = { showColorPicker = false },
            onConfirm = { parsedColor ->
                onCustomAccentColorChanged(parsedColor)
                showColorPicker = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ListScoreTab(
    appSettings: com.kitsugi.animelist.data.settings.AppSettings,
    onListLayoutSelected: (String) -> Unit,
    onTitleLanguageSelected: (String) -> Unit,
    onTmdbLanguageChanged: (String) -> Unit = {},
    onScoreFormatSelected: (String) -> Unit,
    onHideScoresChanged: (Boolean) -> Unit,
    onAutoTranslateEnabledChanged: (Boolean) -> Unit,
    onPreferredTranslatorSelected: (String) -> Unit = {},
    onTranslateSourceLanguageSelected: (String) -> Unit = {},
    onTranslateTargetLanguageSelected: (String) -> Unit = {},
    onAiringNotificationsChanged: (Boolean) -> Unit = {},
    onAniListNotificationsChanged: (Boolean) -> Unit = {},
    onMalNotificationsChanged: (Boolean) -> Unit = {},
    onSimklNotificationsChanged: (Boolean) -> Unit = {},
    onKitsuNotificationsChanged: (Boolean) -> Unit = {},
    onShikimoriNotificationsChanged: (Boolean) -> Unit = {},
    onNotificationIntervalChanged: (Int) -> Unit = {},
    onSearchHistoryEnabledChanged: (Boolean) -> Unit = {},
    accentColor: Color,
    scrollState: ScrollState = rememberScrollState()
) {
    val KitsugiColors = LocalKitsugiColors.current
    val selectedListLayoutId = appSettings.selectedListLayoutId
    val titleLanguage = appSettings.titleLanguage
    val tmdbLanguage = appSettings.tmdbLanguage
    val scoreFormat = appSettings.scoreFormat
    val hideScores = appSettings.hideScores
    val autoTranslateEnabled = appSettings.autoTranslateEnabled

    val listLayoutOptions = listOf(
        KitsugiChoiceOption(id = "compact", title = stringResource(R.string.option_layout_compact), description = stringResource(R.string.option_layout_compact_desc)),
        KitsugiChoiceOption(id = "comfortable", title = stringResource(R.string.option_layout_comfortable), description = stringResource(R.string.option_layout_comfortable_desc)),
        KitsugiChoiceOption(id = "large", title = stringResource(R.string.option_layout_large), description = stringResource(R.string.option_layout_large_desc)),
        KitsugiChoiceOption(id = "grid_2col", title = stringResource(R.string.option_layout_grid_2col), description = stringResource(R.string.option_layout_grid_2col_desc))
    )

    val titleLanguageOptions = listOf(
        KitsugiChoiceOption(id = "ROMAJI", title = stringResource(R.string.option_title_lang_romaji), description = ""),
        KitsugiChoiceOption(id = "ENGLISH", title = stringResource(R.string.option_title_lang_english), description = ""),
        KitsugiChoiceOption(id = "NATIVE", title = stringResource(R.string.option_title_lang_native), description = ""),
        KitsugiChoiceOption(id = "JAPANESE_STAFF", title = stringResource(R.string.option_title_lang_japanese_staff), description = "")
    )

    val scoreFormatOptions = listOf(
        KitsugiChoiceOption(id = "POINT_100", title = stringResource(R.string.option_score_100), description = stringResource(R.string.option_score_100_desc)),
        KitsugiChoiceOption(id = "POINT_10_DECIMAL", title = stringResource(R.string.option_score_10_decimal), description = stringResource(R.string.option_score_10_decimal_desc)),
        KitsugiChoiceOption(id = "POINT_10", title = stringResource(R.string.option_score_10), description = stringResource(R.string.option_score_10_desc)),
        KitsugiChoiceOption(id = "POINT_5", title = stringResource(R.string.option_score_5), description = stringResource(R.string.option_score_5_desc)),
        KitsugiChoiceOption(id = "POINT_3", title = stringResource(R.string.option_score_3), description = stringResource(R.string.option_score_3_desc)),
        KitsugiChoiceOption(id = "STARS", title = stringResource(R.string.option_score_stars), description = stringResource(R.string.option_score_stars_desc))
    )

    val context = LocalContext.current

    var showTitleLanguageMenu by remember { mutableStateOf(false) }
    var showTmdbLanguageMenu by remember { mutableStateOf(false) }
    var showListLayoutMenu by remember { mutableStateOf(false) }
    var showScoreFormatMenu by remember { mutableStateOf(false) }

    // ─── T1-15: POST_NOTIFICATIONS – Android 13+ için bildirim izni ────────────────
    val notificationPermissionLauncher = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) onAiringNotificationsChanged(true)
        }
    } else null

    val aniListPermissionLauncher = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) onAniListNotificationsChanged(true)
        }
    } else null

    val malPermissionLauncher = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) onMalNotificationsChanged(true)
        }
    } else null

    val simklPermissionLauncher = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) onSimklNotificationsChanged(true)
        }
    } else null

    val kitsuPermissionLauncher = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) onKitsuNotificationsChanged(true)
        }
    } else null

    val shikimoriPermissionLauncher = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) onShikimoriNotificationsChanged(true)
        }
    } else null

    fun handleAiringNotificationsToggle(enabled: Boolean) {
        if (!enabled) {
            onAiringNotificationsChanged(false)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (hasPermission) {
                onAiringNotificationsChanged(true)
            } else {
                notificationPermissionLauncher?.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            onAiringNotificationsChanged(true)
        }
    }

    fun handleAniListNotificationsToggle(enabled: Boolean) {
        if (!enabled) {
            onAniListNotificationsChanged(false)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (hasPermission) {
                onAniListNotificationsChanged(true)
            } else {
                aniListPermissionLauncher?.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            onAniListNotificationsChanged(true)
        }
    }

    fun handleMalNotificationsToggle(enabled: Boolean) {
        if (!enabled) {
            onMalNotificationsChanged(false)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (hasPermission) {
                onMalNotificationsChanged(true)
            } else {
                malPermissionLauncher?.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            onMalNotificationsChanged(true)
        }
    }

    fun handleSimklNotificationsToggle(enabled: Boolean) {
        if (!enabled) {
            onSimklNotificationsChanged(false)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (hasPermission) {
                onSimklNotificationsChanged(true)
            } else {
                simklPermissionLauncher?.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            onSimklNotificationsChanged(true)
        }
    }

    fun handleKitsuNotificationsToggle(enabled: Boolean) {
        if (!enabled) {
            onKitsuNotificationsChanged(false)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (hasPermission) {
                onKitsuNotificationsChanged(true)
            } else {
                kitsuPermissionLauncher?.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            onKitsuNotificationsChanged(true)
        }
    }

    fun handleShikimoriNotificationsToggle(enabled: Boolean) {
        if (!enabled) {
            onShikimoriNotificationsChanged(false)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (hasPermission) {
                onShikimoriNotificationsChanged(true)
            } else {
                shikimoriPermissionLauncher?.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            onShikimoriNotificationsChanged(true)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // BAŞLIK DİLLERİ & MEDYA GÖRÜNÜMÜ
        KitsugiSettingsSection(title = stringResource(R.string.settings_section_title_languages)) {
            // Anime & Manga Başlık Dili
            Box {
                KitsugiSettingsListItem(
                    title = stringResource(R.string.settings_anime_title_language),
                    description = stringResource(R.string.settings_anime_title_language_desc),
                    value = titleLanguageOptions.find { it.id == titleLanguage }?.title ?: "",
                    icon = Icons.Rounded.Translate,
                    iconColor = accentColor,
                    onClick = { showTitleLanguageMenu = true }
                )
                KitsugiDropdownMenu(expanded = showTitleLanguageMenu, onDismissRequest = { showTitleLanguageMenu = false }) {
                    titleLanguageOptions.forEach { opt ->
                        KitsugiDropdownItem(
                            text = opt.title,
                            selected = opt.id == titleLanguage,
                            onClick = {
                                onTitleLanguageSelected(opt.id)
                                showTitleLanguageMenu = false
                            }
                        )
                    }
                }
            }

            KitsugiSettingsDivider()

            // Dizi & Film Başlık Dili
            Box {
                val currentTmdbLang = AVAILABLE_TMDB_LANGUAGES.find { it.code == tmdbLanguage }?.displayName ?: tmdbLanguage.uppercase()
                KitsugiSettingsListItem(
                    title = stringResource(R.string.settings_movies_title_language),
                    description = stringResource(R.string.settings_movies_title_language_desc),
                    value = currentTmdbLang,
                    icon = Icons.Rounded.Movie,
                    iconColor = accentColor,
                    onClick = { showTmdbLanguageMenu = true }
                )
                KitsugiDropdownMenu(expanded = showTmdbLanguageMenu, onDismissRequest = { showTmdbLanguageMenu = false }) {
                    AVAILABLE_TMDB_LANGUAGES.forEach { lang ->
                        KitsugiDropdownItem(
                            text = lang.displayName,
                            selected = lang.code == tmdbLanguage,
                            onClick = {
                                onTmdbLanguageChanged(lang.code)
                                showTmdbLanguageMenu = false
                            }
                        )
                    }
                }
            }

            KitsugiSettingsDivider()

            // Liste Düzeni
            Box {
                KitsugiSettingsListItem(
                    title = stringResource(R.string.settings_list_layout),
                    description = stringResource(R.string.settings_list_layout_desc),
                    value = listLayoutOptions.find { it.id == selectedListLayoutId }?.title ?: "",
                    icon = Icons.Rounded.ViewList,
                    iconColor = accentColor,
                    onClick = { showListLayoutMenu = true }
                )
                KitsugiDropdownMenu(expanded = showListLayoutMenu, onDismissRequest = { showListLayoutMenu = false }) {
                    listLayoutOptions.forEach { opt ->
                        KitsugiDropdownItem(
                            text = opt.title,
                            selected = opt.id == selectedListLayoutId,
                            onClick = {
                                onListLayoutSelected(opt.id)
                                showListLayoutMenu = false
                            }
                        )
                    }
                }
            }
        }

        // DEĞERLENDİRME & BİLDİRİMLER
        KitsugiSettingsSection(title = stringResource(R.string.settings_section_rating_notifs)) {
            Box {
                KitsugiSettingsListItem(
                    title = stringResource(R.string.settings_score_format),
                    description = stringResource(R.string.settings_score_format_desc),
                    value = scoreFormatOptions.find { it.id == scoreFormat }?.title ?: "",
                    icon = Icons.Rounded.Settings,
                    iconColor = accentColor,
                    onClick = { showScoreFormatMenu = true }
                )
                KitsugiDropdownMenu(expanded = showScoreFormatMenu, onDismissRequest = { showScoreFormatMenu = false }) {
                    scoreFormatOptions.forEach { opt ->
                        KitsugiDropdownItem(
                            text = opt.title,
                            selected = opt.id == scoreFormat,
                            onClick = {
                                onScoreFormatSelected(opt.id)
                                showScoreFormatMenu = false
                            }
                        )
                    }
                }
            }

            KitsugiSettingsDivider()

            KitsugiSettingsSwitchItem(
                title = stringResource(R.string.settings_hide_scores),
                description = if (hideScores) stringResource(R.string.settings_hide_scores_enabled_desc) else stringResource(R.string.settings_hide_scores_disabled_desc),
                icon = Icons.Rounded.Settings,
                iconColor = accentColor,
                checked = hideScores,
                onCheckedChange = onHideScoresChanged
            )

            KitsugiSettingsDivider()

            KitsugiSettingsSwitchItem(
                title = stringResource(R.string.settings_auto_translate),
                description = stringResource(R.string.settings_auto_translate_desc),
                icon = Icons.Rounded.Translate,
                iconColor = accentColor,
                checked = autoTranslateEnabled,
                onCheckedChange = onAutoTranslateEnabledChanged
            )

            KitsugiSettingsDivider()

            val preferredTranslatorOptions = listOf(
                KitsugiChoiceOption(id = "DEFAULT", title = stringResource(R.string.translator_option_default), description = stringResource(R.string.translator_default_desc)),
                KitsugiChoiceOption(id = "GOOGLE", title = "Google Translate", description = stringResource(R.string.translator_google_desc)),
                KitsugiChoiceOption(id = "DEEPL", title = "DeepL", description = stringResource(R.string.translator_deepl_desc)),
                KitsugiChoiceOption(id = "TRANSLATE_YOU", title = "TranslateYou", description = stringResource(R.string.translator_translateyou_desc))
            )
            var showTranslatorDialog by remember { mutableStateOf(false) }

            KitsugiSettingsListItem(
                title = stringResource(R.string.settings_translator),
                description = stringResource(R.string.settings_translator_desc),
                value = preferredTranslatorOptions.find { it.id == appSettings.preferredTranslator }?.title ?: stringResource(R.string.translator_option_default),
                icon = Icons.Rounded.Translate,
                iconColor = accentColor,
                onClick = { showTranslatorDialog = true }
            )

            if (showTranslatorDialog) {
                AlertDialog(
                    onDismissRequest = { showTranslatorDialog = false },
                    containerColor = KitsugiColors.surface,
                    title = {
                        Text(
                            text = stringResource(R.string.settings_translator),
                            color = KitsugiColors.textPrimary,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            preferredTranslatorOptions.forEach { option ->
                                val isSelected = option.id == appSettings.preferredTranslator
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (isSelected) KitsugiColors.surfaceSoft else Color.Transparent)
                                        .tvClickable(shape = RoundedCornerShape(12.dp)) {
                                            onPreferredTranslatorSelected(option.id)
                                            showTranslatorDialog = false
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = {
                                            onPreferredTranslatorSelected(option.id)
                                            showTranslatorDialog = false
                                        },
                                        colors = RadioButtonDefaults.colors(
                                            selectedColor = accentColor,
                                            unselectedColor = KitsugiColors.textMuted
                                        )
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = option.title,
                                            color = KitsugiColors.textPrimary,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                        if (option.description.isNotBlank()) {
                                            Text(
                                                text = option.description,
                                                color = KitsugiColors.textSecondary,
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showTranslatorDialog = false }) {
                            Text("Kapat", color = accentColor, fontWeight = FontWeight.Bold)
                        }
                    }
                )
            }

            // Google Translate'in desteklediği TÜM diller — kaynak tarafta ek olarak
            // Otomatik (Algıla) seçeneği vardır; hedef dil uygulama diline göre çözülür.
            val translateSourceLanguages = listOf(
                KitsugiChoiceOption(id = "auto", title = stringResource(R.string.translate_auto_detect), description = "")
            ) + com.kitsugi.animelist.utils.KitsugiTranslateUtils.googleLanguageCatalog.map { (code, name) ->
                KitsugiChoiceOption(id = code, title = name, description = "")
            }

            val translateTargetLanguages =
                com.kitsugi.animelist.utils.KitsugiTranslateUtils.googleLanguageCatalog.map { (code, name) ->
                    KitsugiChoiceOption(id = code, title = name, description = "")
                }

            var showSourceLangDialog by remember { mutableStateOf(false) }
            var showTargetLangDialog by remember { mutableStateOf(false) }

            KitsugiSettingsDivider()

            KitsugiSettingsListItem(
                title = stringResource(R.string.settings_translate_source),
                description = stringResource(R.string.settings_translate_source_desc),
                value = translateSourceLanguages.find { it.id == appSettings.translateSourceLanguage }?.title ?: stringResource(R.string.translate_auto_detect),
                icon = Icons.Rounded.Translate,
                iconColor = accentColor,
                onClick = { showSourceLangDialog = true }
            )

            if (showSourceLangDialog) {
                AlertDialog(
                    onDismissRequest = { showSourceLangDialog = false },
                    containerColor = KitsugiColors.surface,
                    title = {
                        Text(
                            text = stringResource(R.string.settings_translate_source),
                            color = KitsugiColors.textPrimary,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    text = {
                        Column(
                            modifier = Modifier.verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            translateSourceLanguages.forEach { option ->
                                val isSelected = option.id == appSettings.translateSourceLanguage
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (isSelected) KitsugiColors.surfaceSoft else Color.Transparent)
                                        .tvClickable(shape = RoundedCornerShape(12.dp)) {
                                            onTranslateSourceLanguageSelected(option.id)
                                            showSourceLangDialog = false
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = {
                                            onTranslateSourceLanguageSelected(option.id)
                                            showSourceLangDialog = false
                                        },
                                        colors = RadioButtonDefaults.colors(
                                            selectedColor = accentColor,
                                            unselectedColor = KitsugiColors.textMuted
                                        )
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = option.title,
                                        color = KitsugiColors.textPrimary,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showSourceLangDialog = false }) {
                            Text("Kapat", color = accentColor, fontWeight = FontWeight.Bold)
                        }
                    }
                )
            }

            KitsugiSettingsDivider()

            KitsugiSettingsListItem(
                title = stringResource(R.string.settings_translate_target),
                description = stringResource(R.string.settings_translate_target_desc),
                value = translateTargetLanguages.find { it.id == appSettings.translateTargetLanguage }?.title ?: "Türkçe",
                icon = Icons.Rounded.Translate,
                iconColor = accentColor,
                onClick = { showTargetLangDialog = true }
            )

            if (showTargetLangDialog) {
                AlertDialog(
                    onDismissRequest = { showTargetLangDialog = false },
                    containerColor = KitsugiColors.surface,
                    title = {
                        Text(
                            text = stringResource(R.string.settings_translate_target),
                            color = KitsugiColors.textPrimary,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    text = {
                        Column(
                            modifier = Modifier.verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            translateTargetLanguages.forEach { option ->
                                val isSelected = option.id == appSettings.translateTargetLanguage
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (isSelected) KitsugiColors.surfaceSoft else Color.Transparent)
                                        .tvClickable(shape = RoundedCornerShape(12.dp)) {
                                            onTranslateTargetLanguageSelected(option.id)
                                            showTargetLangDialog = false
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = {
                                            onTranslateTargetLanguageSelected(option.id)
                                            showTargetLangDialog = false
                                        },
                                        colors = RadioButtonDefaults.colors(
                                            selectedColor = accentColor,
                                            unselectedColor = KitsugiColors.textMuted
                                        )
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = option.title,
                                        color = KitsugiColors.textPrimary,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showTargetLangDialog = false }) {
                            Text("Kapat", color = accentColor, fontWeight = FontWeight.Bold)
                        }
                    }
                )
            }

            KitsugiSettingsDivider()

            KitsugiSettingsSwitchItem(
                title = stringResource(R.string.settings_airing_notifications),
                description = if (appSettings.airingNotificationsEnabled)
                    stringResource(R.string.settings_airing_notifications_desc_enabled)
                else
                    stringResource(R.string.settings_airing_notifications_desc_disabled),
                icon = Icons.Rounded.Settings,
                iconColor = accentColor,
                checked = appSettings.airingNotificationsEnabled,
                onCheckedChange = { handleAiringNotificationsToggle(it) }
            )

            KitsugiSettingsDivider()

            KitsugiSettingsSwitchItem(
                title = stringResource(R.string.settings_notif_anilist),
                description = if (appSettings.aniListNotificationsEnabled)
                    stringResource(R.string.settings_notif_anilist_on)
                else
                    stringResource(R.string.settings_notif_anilist_off),
                icon = Icons.Rounded.Settings,
                iconColor = accentColor,
                checked = appSettings.aniListNotificationsEnabled,
                onCheckedChange = { handleAniListNotificationsToggle(it) }
            )

            KitsugiSettingsDivider()

            KitsugiSettingsSwitchItem(
                title = stringResource(R.string.settings_notif_mal),
                description = if (appSettings.malNotificationsEnabled)
                    stringResource(R.string.settings_notif_mal_on)
                else
                    stringResource(R.string.settings_notif_mal_off),
                icon = Icons.Rounded.Settings,
                iconColor = accentColor,
                checked = appSettings.malNotificationsEnabled,
                onCheckedChange = { handleMalNotificationsToggle(it) }
            )

            KitsugiSettingsDivider()

            KitsugiSettingsSwitchItem(
                title = stringResource(R.string.settings_notif_simkl),
                description = if (appSettings.simklNotificationsEnabled)
                    stringResource(R.string.settings_notif_simkl_on)
                else
                    stringResource(R.string.settings_notif_simkl_off),
                icon = Icons.Rounded.Settings,
                iconColor = accentColor,
                checked = appSettings.simklNotificationsEnabled,
                onCheckedChange = { handleSimklNotificationsToggle(it) }
            )

            KitsugiSettingsDivider()

            KitsugiSettingsSwitchItem(
                title = stringResource(R.string.settings_notif_kitsu),
                description = if (appSettings.kitsuNotificationsEnabled)
                    stringResource(R.string.settings_notif_kitsu_on)
                else
                    stringResource(R.string.settings_notif_kitsu_off),
                icon = Icons.Rounded.Settings,
                iconColor = accentColor,
                checked = appSettings.kitsuNotificationsEnabled,
                onCheckedChange = { handleKitsuNotificationsToggle(it) }
            )

            KitsugiSettingsDivider()

            KitsugiSettingsSwitchItem(
                title = stringResource(R.string.settings_notif_shikimori),
                description = if (appSettings.shikimoriNotificationsEnabled)
                    stringResource(R.string.settings_notif_shikimori_on)
                else
                    stringResource(R.string.settings_notif_shikimori_off),
                icon = Icons.Rounded.Settings,
                iconColor = accentColor,
                checked = appSettings.shikimoriNotificationsEnabled,
                onCheckedChange = { handleShikimoriNotificationsToggle(it) }
            )

            KitsugiSettingsDivider()

            val intervalOptions = listOf(
                KitsugiChoiceOption(id = "30", title = stringResource(R.string.notif_interval_30), description = stringResource(R.string.notif_interval_30_desc)),
                KitsugiChoiceOption(id = "60", title = stringResource(R.string.notif_interval_60), description = stringResource(R.string.notif_interval_60_desc)),
                KitsugiChoiceOption(id = "180", title = stringResource(R.string.notif_interval_180), description = stringResource(R.string.notif_interval_180_desc)),
                KitsugiChoiceOption(id = "360", title = stringResource(R.string.notif_interval_360), description = stringResource(R.string.notif_interval_360_desc)),
                KitsugiChoiceOption(id = "720", title = stringResource(R.string.notif_interval_720), description = stringResource(R.string.notif_interval_720_desc)),
                KitsugiChoiceOption(id = "1440", title = stringResource(R.string.notif_interval_1440), description = stringResource(R.string.notif_interval_1440_desc))
            )
            var showIntervalDialog by remember { mutableStateOf(false) }

            KitsugiSettingsListItem(
                title = stringResource(R.string.settings_notif_interval),
                description = stringResource(R.string.settings_notif_interval_desc),
                value = intervalOptions.find { it.id == appSettings.notificationInterval.toString() }?.title ?: "${appSettings.notificationInterval} Dakika",
                icon = Icons.Rounded.Settings,
                iconColor = accentColor,
                onClick = { showIntervalDialog = true }
            )

            if (showIntervalDialog) {
                AlertDialog(
                    onDismissRequest = { showIntervalDialog = false },
                    containerColor = KitsugiColors.surface,
                    title = {
                        Text(
                            text = stringResource(R.string.settings_notif_interval_sheet),
                            color = KitsugiColors.textPrimary,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            intervalOptions.forEach { option ->
                                val isSelected = option.id == appSettings.notificationInterval.toString()
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (isSelected) KitsugiColors.surfaceSoft else Color.Transparent)
                                        .tvClickable(shape = RoundedCornerShape(12.dp)) {
                                            onNotificationIntervalChanged(option.id.toInt())
                                            showIntervalDialog = false
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = {
                                            onNotificationIntervalChanged(option.id.toInt())
                                            showIntervalDialog = false
                                        },
                                        colors = RadioButtonDefaults.colors(
                                            selectedColor = accentColor,
                                            unselectedColor = KitsugiColors.textMuted
                                        )
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = option.title,
                                            color = KitsugiColors.textPrimary,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                        if (option.description.isNotBlank()) {
                                            Text(
                                                text = option.description,
                                                color = KitsugiColors.textSecondary,
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showIntervalDialog = false }) {
                            Text("Kapat", color = accentColor, fontWeight = FontWeight.Bold)
                        }
                    }
                )
            }

            KitsugiSettingsDivider()

            KitsugiSettingsSwitchItem(
                title = stringResource(R.string.settings_save_search_history),
                description = if (appSettings.searchHistoryEnabled)
                    stringResource(R.string.settings_save_search_history_desc_enabled)
                else
                    stringResource(R.string.settings_save_search_history_desc_disabled),
                icon = Icons.Rounded.Settings,
                iconColor = accentColor,
                checked = appSettings.searchHistoryEnabled,
                onCheckedChange = onSearchHistoryEnabledChanged
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomColorPickerDialog(
    colorInputText: String,
    onColorInputTextChange: (String) -> Unit,
    accentColor: Color,
    onDismissRequest: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    val KitsugiColors = LocalKitsugiColors.current
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(R.string.settings_custom_color_title_input), color = KitsugiColors.textPrimary) },
        text = {
            Column {
                Text(stringResource(R.string.settings_custom_color_desc_input), color = KitsugiColors.textSecondary, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = colorInputText,
                    onValueChange = onColorInputTextChange,
                    placeholder = { Text("#000000", color = KitsugiColors.textMuted) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = KitsugiColors.textPrimary,
                        unfocusedTextColor = KitsugiColors.textPrimary,
                        cursorColor = accentColor,
                        focusedBorderColor = accentColor,
                        unfocusedBorderColor = KitsugiColors.border
                    )
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val parsedColor = runCatching {
                        val hex = colorInputText.trim().removePrefix("#")
                        val colorLong = hex.toLong(16)
                        val finalColor = if (hex.length == 6) {
                            (0xFF000000 or colorLong).toInt()
                        } else {
                            colorLong.toInt()
                        }
                        finalColor
                    }.getOrNull()

                    if (parsedColor != null) {
                        onConfirm(parsedColor)
                    }
                }
            ) {
                Text(stringResource(R.string.settings_custom_color_apply), color = accentColor, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(R.string.settings_cancel), color = KitsugiColors.textSecondary)
            }
        },
        containerColor = KitsugiColors.surface,
        shape = RoundedCornerShape(20.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun MangaReaderTab(
    appSettings: com.kitsugi.animelist.data.settings.AppSettings,
    onMangaReadingModeSelected: (String) -> Unit,
    onMangaColorFilterSelected: (String) -> Unit,
    onMangaFitModeSelected: (String) -> Unit,
    onMangaBrightnessChanged: (Float) -> Unit,
    accentColor: Color,
    scrollState: ScrollState = rememberScrollState()
) {
    val KitsugiColors = LocalKitsugiColors.current

    val readingModeOptions = listOf(
        KitsugiChoiceOption(id = "RightToLeft",  title = stringResource(R.string.manga_direction_rtl),    description = stringResource(R.string.manga_direction_rtl_desc)),
        KitsugiChoiceOption(id = "LeftToRight",  title = stringResource(R.string.manga_direction_ltr),    description = stringResource(R.string.manga_direction_ltr_desc)),
        KitsugiChoiceOption(id = "Vertical",     title = stringResource(R.string.manga_direction_vertical), description = stringResource(R.string.manga_direction_vertical_desc)),
        KitsugiChoiceOption(id = "WebtoonZoom",  title = stringResource(R.string.manga_direction_webtoon_zoom),   description = stringResource(R.string.manga_direction_webtoon_zoom_desc))
    )

    val colorFilterOptions = listOf(
        KitsugiChoiceOption(id = "Normal",    title = stringResource(R.string.manga_filter_normal),    description = ""),
        KitsugiChoiceOption(id = "Warm",      title = stringResource(R.string.manga_filter_warm),     description = ""),
        KitsugiChoiceOption(id = "Cool",      title = stringResource(R.string.manga_filter_cool),     description = ""),
        KitsugiChoiceOption(id = "GrayScale", title = stringResource(R.string.manga_filter_grayscale),   description = ""),
        KitsugiChoiceOption(id = "Inverted",  title = stringResource(R.string.manga_filter_inverted), description = "")
    )

    val fitModeOptions = listOf(
        KitsugiChoiceOption(id = "FitScreen", title = stringResource(R.string.manga_fit_screen),      description = ""),
        KitsugiChoiceOption(id = "FitWidth",  title = stringResource(R.string.manga_fit_width),  description = ""),
        KitsugiChoiceOption(id = "FitHeight", title = stringResource(R.string.manga_fit_height), description = "")
    )

    var showReadingModeMenu by remember { mutableStateOf(false) }
    var showColorFilterMenu by remember { mutableStateOf(false) }
    var showFitModeMenu by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        KitsugiSettingsSection(title = stringResource(R.string.settings_section_manga_reader)) {
            Box {
                KitsugiSettingsListItem(
                    title = stringResource(R.string.manga_reading_direction),
                    description = stringResource(R.string.manga_reading_direction_desc),
                    value = readingModeOptions.find { it.id == appSettings.mangaReadingMode }?.title ?: "",
                    icon = Icons.Rounded.Settings,
                    iconColor = accentColor,
                    onClick = { showReadingModeMenu = true }
                )
                KitsugiDropdownMenu(expanded = showReadingModeMenu, onDismissRequest = { showReadingModeMenu = false }) {
                    readingModeOptions.forEach { opt ->
                        KitsugiDropdownItem(
                            text = opt.title,
                            selected = opt.id == appSettings.mangaReadingMode,
                            onClick = {
                                onMangaReadingModeSelected(opt.id)
                                showReadingModeMenu = false
                            }
                        )
                    }
                }
            }

            KitsugiSettingsDivider()

            Box {
                KitsugiSettingsListItem(
                    title = stringResource(R.string.manga_color_filter),
                    description = stringResource(R.string.manga_color_filter_desc),
                    value = colorFilterOptions.find { it.id == appSettings.mangaColorFilter }?.title ?: "",
                    icon = Icons.Rounded.Palette,
                    iconColor = accentColor,
                    onClick = { showColorFilterMenu = true }
                )
                KitsugiDropdownMenu(expanded = showColorFilterMenu, onDismissRequest = { showColorFilterMenu = false }) {
                    colorFilterOptions.forEach { opt ->
                        KitsugiDropdownItem(
                            text = opt.title,
                            selected = opt.id == appSettings.mangaColorFilter,
                            onClick = {
                                onMangaColorFilterSelected(opt.id)
                                showColorFilterMenu = false
                            }
                        )
                    }
                }
            }

            KitsugiSettingsDivider()

            Box {
                KitsugiSettingsListItem(
                    title = stringResource(R.string.manga_page_fit),
                    description = stringResource(R.string.manga_page_fit_desc),
                    value = fitModeOptions.find { it.id == appSettings.mangaFitMode }?.title ?: "",
                    icon = Icons.Rounded.Tablet,
                    iconColor = accentColor,
                    onClick = { showFitModeMenu = true }
                )
                KitsugiDropdownMenu(expanded = showFitModeMenu, onDismissRequest = { showFitModeMenu = false }) {
                    fitModeOptions.forEach { opt ->
                        KitsugiDropdownItem(
                            text = opt.title,
                            selected = opt.id == appSettings.mangaFitMode,
                            onClick = {
                                onMangaFitModeSelected(opt.id)
                                showFitModeMenu = false
                            }
                        )
                    }
                }
            }

            KitsugiSettingsDivider()

            // Parlaklık Ayarı
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.manga_brightness),
                        color = KitsugiColors.textPrimary,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "${(appSettings.mangaBrightness * 100).toInt()}%",
                        color = KitsugiColors.textSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.manga_brightness_desc),
                    color = KitsugiColors.textSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(10.dp))
                Slider(
                    value = appSettings.mangaBrightness,
                    onValueChange = onMangaBrightnessChanged,
                    valueRange = 0.1f..1.0f,
                    steps = 8,
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(
                        thumbColor = accentColor,
                        activeTrackColor = accentColor,
                        inactiveTrackColor = KitsugiColors.surfaceSoft
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
