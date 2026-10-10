package com.kitsugi.animelist.ui.screens.offline

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.core.view.WindowCompat
import com.kitsugi.animelist.ui.screens.offline.DownloadsScreen
import com.kitsugi.animelist.ui.theme.KitsugiAnimeListTheme
import com.kitsugi.animelist.DeviceFormFactor
import com.kitsugi.animelist.DeviceProfile

/**
 * Standalone activity for the Downloads screen.
 * Launched from KitsugiStreamActivity/Screen when a download starts so the user
 * can view progress without leaving the stream picker flow, and go back to it.
 */
class DownloadsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val isTv = DeviceProfile.detect(this) == DeviceFormFactor.TV
        setContent {
            val settingsStore = remember { com.kitsugi.animelist.data.settings.SettingsDataStore(applicationContext) }
            val appSettings by settingsStore.settingsFlow.collectAsState(
                initial = com.kitsugi.animelist.data.settings.AppSettings()
            )
            val darkTheme = when (appSettings.themeMode) {
                "LIGHT" -> false
                "DARK" -> true
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }
            KitsugiAnimeListTheme(
                darkTheme = darkTheme,
                amoledBlack = appSettings.amoledBlack,
                selectedThemeId = appSettings.selectedThemeId,
                customAccentColor = appSettings.customAccentColor,
                customAccentColor2 = appSettings.customAccentColor2,
                customAccentGradientAngle = appSettings.customAccentGradientAngle,
                isTv = isTv
            ) {
                DownloadsScreen(
                    onBack = { finish() }
                )
            }
        }
    }
}
