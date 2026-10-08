package com.kitsugi.animelist

import android.content.Context
import android.content.res.Configuration
import java.util.Locale
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.kitsugi.animelist.core.deeplink.DeepLinkHandler
import com.kitsugi.animelist.core.recommendations.TvChannelSyncService
import com.kitsugi.animelist.data.auth.ExternalAuthManager
import com.kitsugi.animelist.ui.theme.KitsugiAnimeListTheme
import com.kitsugi.animelist.ui.tv.TvRootScreen
import com.kitsugi.animelist.ui.tv.design.KitsugiTvTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var tvChannelSyncService: TvChannelSyncService

    private val formFactor by lazy { DeviceProfile.detect(this) }

    override fun attachBaseContext(newBase: Context) {
        val tag = LocaleCache.localeTag.takeIf { it != LocaleCache.UNSET }

        if (!tag.isNullOrEmpty()) {
            val locale = Locale.forLanguageTag(tag)
            Locale.setDefault(locale)
            val config = Configuration(newBase.resources.configuration)
            config.setLocale(locale)
            super.attachBaseContext(newBase.createConfigurationContext(config))
        } else {
            super.attachBaseContext(newBase)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_KitsugiAnimeList)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val settingsDataStore = remember { com.kitsugi.animelist.data.settings.SettingsDataStore(applicationContext) }
            val appSettings by settingsDataStore.settingsFlow.collectAsState(initial = com.kitsugi.animelist.data.settings.AppSettings())

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
                isTv = formFactor == DeviceFormFactor.TV
            ) {
                KitsugiPermissionRequester()

                // Çökme kontrolü ANA THREAD'DE DOSYA OKUMASIN (eski hâli arayüzü kilitliyordu).
                var showCrashRecovery by androidx.compose.runtime.remember {
                    androidx.compose.runtime.mutableStateOf(false)
                }
                androidx.compose.runtime.LaunchedEffect(Unit) {
                    val hasUnreported = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        com.kitsugi.animelist.core.diagnostics.KitsugiCrashLogger.hasUnreadCrash(applicationContext)
                    }
                    if (hasUnreported) showCrashRecovery = true
                }
                androidx.compose.runtime.LaunchedEffect(Unit) {
                    // Sessiz çökme (native/ANR/OOM) analizi arka planda tamamlanır; kısa bir
                    // gecikmeyle tekrar bakıp kaçırılan raporu da kullanıcıya göster.
                    kotlinx.coroutines.delay(2_500L)
                    val late = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        com.kitsugi.animelist.core.diagnostics.KitsugiCrashLogger.hasUnreadCrash(applicationContext)
                    }
                    if (late) showCrashRecovery = true
                }

                if (showCrashRecovery) {
                    com.kitsugi.animelist.ui.components.KitsugiCrashRecoveryDialog(
                        onDismiss = { showCrashRecovery = false }
                    )
                }

                when (formFactor) {
                    DeviceFormFactor.TV -> {
                        KitsugiTvTheme {
                            TvRootScreen()
                        }
                    }
                    DeviceFormFactor.TABLET,
                    DeviceFormFactor.PHONE -> AppRoot()
                }
            }
        }

        handleAuthIntent(intent)
        // B1.1: Non-auth deep links (Channels / typed links) park as pending.
        // TvRootScreen drains them once navState and session are ready.
        handleDeepLinkIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        if (formFactor == DeviceFormFactor.TV) {
            // B1.11: Servisi baslatir (ilk cagirida periyodik job planlanir).
            // Sonraki cagirilar no-op'tur (job zaten planlanmis).
            tvChannelSyncService.start()
            tvChannelSyncService.onForegroundChanged(foreground = true)
        }
    }

    override fun onStop() {
        super.onStop()
        if (formFactor == DeviceFormFactor.TV) {
            com.kitsugi.animelist.core.player.TvTrailerPlayerPoolHolder.get(this).yield()
            // B1.11: Arka plana geciste launcher icin son reconcile yapilir
            tvChannelSyncService.onForegroundChanged(foreground = false)
        }
    }

    override fun onDestroy() {
        // Kullanıcı uygulamayı düzgün kapattıysa "temiz kapanış" işaretle —
        // böylece sessiz ölüm dedektörü yanlış alarm vermez.
        if (isFinishing) {
            try {
                com.kitsugi.animelist.core.diagnostics.KitsugiSessionSupervisor.markCleanExit()
            } catch (_: Throwable) {}
        }
        super.onDestroy()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        return try {
            super.dispatchKeyEvent(event)
        } catch (e: IllegalStateException) {
            if (e.message?.contains("FocusRequester is not initialized", ignoreCase = true) == true) {
                android.util.Log.w("MainActivity", "Bypassed uninitialized FocusRequester crash during key event dispatch", e)
                true
            } else {
                throw e
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleAuthIntent(intent)
        // B1.1: Warm-start channel/deep link
        handleDeepLinkIntent(intent)
    }

    // B1.1: Routes versioned deep links (Kitsugianimelist://v1/*) to DeepLinkHandler.
    // Auth links are handled by handleAuthIntent; this returns true for them (ignored).
    // NavState not available at Activity level; parked as pending; TvRootScreen drains.
    private fun handleDeepLinkIntent(intent: Intent?) {
        DeepLinkHandler.handle(intent)
    }

    private fun handleAuthIntent(intent: Intent?) {
        ExternalAuthManager.handleAuthIntent(
            context = this,
            intent = intent,
            onSuccess = { serviceName ->
                runOnUiThread {
                    Toast.makeText(
                        this,
                        when (serviceName) {
                            "anilist"   -> "AniList bağlantısı başarılı."
                            "simkl"     -> "Simkl bağlantısı başarılı."
                            "shikimori" -> "Shikimori bağlantısı başarılı."
                            else        -> "MyAnimeList bağlantısı başarılı."
                        },
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },
            onError = { message ->
                runOnUiThread {
                    Toast.makeText(
                        this,
                        message,
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        )
    }
}

@androidx.compose.runtime.Composable
private fun KitsugiPermissionRequester() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val permissionsToRequest = androidx.compose.runtime.remember {
        buildList {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                // Android 13+
                add(android.Manifest.permission.POST_NOTIFICATIONS)
                add(android.Manifest.permission.READ_MEDIA_IMAGES)
                add(android.Manifest.permission.READ_MEDIA_VIDEO)
                add(android.Manifest.permission.READ_MEDIA_AUDIO)
            } else {
                // Android 12 and below
                add(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                if (android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.P) {
                    add(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }
        }.filter {
            androidx.core.content.ContextCompat.checkSelfPermission(context, it) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }.toTypedArray()
    }

    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (permissionsToRequest.isNotEmpty()) {
            launcher.launch(permissionsToRequest)
        }
    }
}