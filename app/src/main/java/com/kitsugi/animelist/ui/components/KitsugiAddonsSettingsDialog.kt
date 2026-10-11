package com.kitsugi.animelist.ui.components

import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.Text

import android.net.Uri
import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.theme.gradient.border
import com.kitsugi.animelist.ui.utils.tvClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kitsugi.animelist.data.local.CloudstreamRepoEntity
import com.kitsugi.animelist.data.local.CsPluginEntity
import com.kitsugi.animelist.data.local.ManagedAddonEntity
import com.kitsugi.animelist.data.local.MangaSourceStateEntity
import com.kitsugi.animelist.data.manga.MangaSource
import com.kitsugi.animelist.data.remote.CsPlugin
import com.kitsugi.animelist.ui.components.addons.CloudstreamExtensionsTab
import com.kitsugi.animelist.ui.components.addons.MangaExtensionsTab
import com.kitsugi.animelist.ui.components.addons.StremioTab
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.theme.KitsugiColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R
import com.kitsugi.animelist.utils.KitsugiStrings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KitsugiAddonsSettingsDialog(
    addons: List<ManagedAddonEntity>,
    initialDebridToken: String,
    repos: List<CloudstreamRepoEntity>,
    repoPlugins: Map<String, List<CsPlugin>?>,
    repoLoadingState: Map<String, Boolean>,
    csPlugins: List<CsPluginEntity> = emptyList(),
    onAddAddon: (String) -> Unit,
    onToggleAddon: (ManagedAddonEntity, Boolean) -> Unit,
    onDeleteAddon: (ManagedAddonEntity) -> Unit,
    onSaveDebridToken: (String) -> Unit,
    onAddRepo: (String) -> Unit,
    onDeleteRepo: (CloudstreamRepoEntity) -> Unit,
    onFetchRepoPlugins: (String) -> Unit,
    onInstallPlugin: (CsPlugin, ((Boolean) -> Unit)?) -> Unit,
    onInstallAllPlugins: (repoUrl: String, repoName: String, plugins: List<CsPlugin>) -> Unit = { _, _, _ -> },
    onUpdateAllPlugins: (repoUrl: String, repoName: String, plugins: List<CsPlugin>) -> Unit = { _, _, _ -> },
    bulkInstallRepoUrl: String? = null,
    bulkInstallRepoName: String? = null,
    bulkInstallDone: Int = 0,
    bulkInstallTotal: Int = 0,
    bulkInstallCurrentName: String = "",
    bulkInstallResultMessage: String? = null,
    onClearBulkInstallResult: () -> Unit = {},
    onToggleCsPlugin: (CsPluginEntity, Boolean) -> Unit = { _, _ -> },
    onUninstallCsPlugin: (CsPluginEntity) -> Unit = {},
    isCheckingUpdates: Boolean = false,
    availableUpdatesCount: Int = 0,
    onCheckForUpdates: () -> Unit = {},
    onUpdateAllPendingPlugins: () -> Unit = {},
    onRefreshRepo: ((String) -> Unit)? = null,
    // Manga extensions
    mangaSources: List<MangaSource> = emptyList(),
    onInstallMangaExtension: (Uri) -> Unit = {},
    onDeleteMangaExtension: (MangaSource) -> Unit = {},
    // Manga repos (Keiyoushi / native)
    mangaRepos: List<String> = emptyList(),
    mangaRepoExtensions: Map<String, List<com.kitsugi.animelist.data.remote.MangaExtensionInfo>?> = emptyMap(),
    mangaRepoLoadingState: Map<String, Boolean> = emptyMap(),
    onAddMangaRepo: (String) -> Unit = {},
    onDeleteMangaRepo: (String) -> Unit = {},
    onFetchMangaRepo: (String) -> Unit = {},
    onInstallMangaApk: (com.kitsugi.animelist.data.remote.MangaExtensionInfo, ((Boolean) -> Unit)?) -> Unit = { _, _ -> },
    onInstallAllMangaExtensions: (repoUrl: String, extensions: List<com.kitsugi.animelist.data.remote.MangaExtensionInfo>) -> Unit = { _, _ -> },
    onUpdateAllMangaExtensions: (repoUrl: String, extensions: List<com.kitsugi.animelist.data.remote.MangaExtensionInfo>) -> Unit = { _, _ -> },
    mangaBulkInstallRepoUrl: String? = null,
    mangaBulkInstallDone: Int = 0,
    mangaBulkInstallTotal: Int = 0,
    mangaBulkInstallCurrentName: String = "",
    /** versionCode karşılaştırması için (Mihon uyumlu) */
    onGetInstalledMangaVersionCode: (String) -> Long = { -1L },
    /** Görüntü amacıyla versionName metni */
    onGetInstalledMangaVersion: (String) -> String? = { null },
    onGetMangaSourceHealthStatus: (MangaSource) -> com.kitsugi.animelist.data.manga.model.SourceHealthStatus = { com.kitsugi.animelist.data.manga.model.SourceHealthStatus.Unknown },
    onGetMangaSourceRuntimeStats: (MangaSource) -> com.kitsugi.animelist.data.manga.model.SourceRuntimeStats = { com.kitsugi.animelist.data.manga.model.SourceRuntimeStats() },
    onGetMangaConfiguredDomain: (MangaSource) -> String? = { null },
    onGetMangaConfiguredBaseUrl: (MangaSource) -> String = { it.baseUrl },
    onGetMangaSourceUserAgent: (MangaSource) -> String? = { null },
    onGetMangaSourceSlowdownEnabled: (MangaSource) -> Boolean = { false },
    onSetMangaSourceUserAgent: (MangaSource, String?) -> Unit = { _, _ -> },
    onSetMangaSourceSlowdownEnabled: (MangaSource, Boolean) -> Unit = { _, _ -> },
    onResetMangaSourceDiagnostics: (MangaSource) -> Unit = {},
    onClearAllMangaSourceDiagnostics: () -> Unit = {},
    onIsMangaSourceBusy: (MangaSource) -> Boolean = { false },
    onQuickCheckMangaSource: (MangaSource) -> Unit = {},
    onRefreshMangaSourceMirror: (MangaSource) -> Unit = {},
    onClearMangaSourceMirror: (MangaSource) -> Unit = {},
    onSetMangaSourceDomain: (MangaSource, String?) -> Unit = { _, _ -> },
    mangaSourceStateReport: List<MangaSourceStateEntity> = emptyList(),
    onOpenMangaSourceHealthScreen: () -> Unit = {},
    onForceCheckMangaUpdates: (onResult: (String) -> Unit) -> Unit = {},
    untrustedRepoToConfirm: String? = null,
    untrustedSignatureToConfirm: Pair<com.kitsugi.animelist.data.remote.MangaExtensionInfo, String>? = null,
    onConfirmUntrustedRepo: (String) -> Unit = {},
    onDismissUntrustedRepo: () -> Unit = {},
    onConfirmUntrustedSignature: (com.kitsugi.animelist.data.remote.MangaExtensionInfo, String) -> Unit = { _, _ -> },
    onDismissUntrustedSignature: () -> Unit = {},
    initialTab: Int = 0,
    onOpenDiagnostic: (() -> Unit)? = null,
    useGithubProxy: Boolean = false,
    onUseGithubProxyChanged: (Boolean) -> Unit = {},
    onExplorePlugin: ((String) -> Unit)? = null,
    onOpenPluginPicker: (() -> Unit)? = null,
    embeddedMode: Boolean = false,
    onDismiss: () -> Unit
) {
    val accentColor = LocalKitsugiAccent.current
    var webViewDialogUrl by remember { mutableStateOf<String?>(null) }
    var webViewDialogTitle by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val pagerState = rememberPagerState(initialPage = initialTab, pageCount = { 3 })

    val stremioListState = rememberLazyListState()
    val cloudstreamListState = rememberLazyListState()
    val mangaListState = rememberLazyListState()

    val activeListState = when (pagerState.currentPage) {
        0 -> stremioListState
        1 -> cloudstreamListState
        else -> mangaListState
    }

    KitsugiSheetOrDialog(
        onDismiss = onDismiss,
        fullScreen = true,
        embeddedMode = embeddedMode,
        innerScrollState = activeListState,
        sheetGesturesEnabled = false
    ) {
            // Header content (Title + Close button)
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
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.eklenti_akis_ayarlari),
                                color = KitsugiColors.TextPrimary,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(R.string.kaynaklari_ekleyin_guncelleyin_ve_yonetin),
                                color = KitsugiColors.TextMuted,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.kapat), tint = KitsugiColors.TextSecondary)
                        }
                    }
                }
                Spacer(Modifier.height(if (embeddedMode) 8.dp else 12.dp))
                AddonsSegmentedTabs(
                    selectedIndex = pagerState.currentPage,
                    accentColor = accentColor,
                    onSelect = { index -> scope.launch { pagerState.animateScrollToPage(index) } }
                )
            }

            // Body content (HorizontalPager)
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp)
            ) { page ->
                Column(modifier = Modifier.fillMaxSize()) {
                    // ── Bulk install result banner ──────────────────────────────
                    if (bulkInstallResultMessage != null) {
                        val isError = bulkInstallResultMessage.startsWith("❌") || bulkInstallResultMessage.startsWith("⚠️")
                        val isInfo = bulkInstallResultMessage.startsWith("ℹ️")
                        val bannerColor = when {
                            isError -> Color(0xFFB71C1C).copy(alpha = 0.15f)
                            isInfo  -> Color(0xFF1565C0).copy(alpha = 0.15f)
                            else    -> Color(0xFF1B5E20).copy(alpha = 0.15f)
                        }
                        val textColor = when {
                            isError -> Color(0xFFEF9A9A)
                            isInfo  -> Color(0xFF90CAF9)
                            else    -> Color(0xFFA5D6A7)
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(bannerColor)
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = bulkInstallResultMessage,
                                color = textColor,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = onClearBulkInstallResult,
                                modifier = Modifier.size(20.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.Close,
                                    contentDescription = stringResource(R.string.kapat),
                                    tint = textColor,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    
                    // Tab Content based on page index
                    when (page) {
                        0 -> StremioTab(
                            addons = addons,
                            initialDebridToken = initialDebridToken,
                            accentColor = accentColor,
                            onAddAddon = onAddAddon,
                            onToggleAddon = onToggleAddon,
                            onDeleteAddon = onDeleteAddon,
                            onSaveDebridToken = onSaveDebridToken,
                            listState = stremioListState
                        )
                        1 -> CloudstreamExtensionsTab(
                            repos = repos,
                            addons = addons,
                            csPlugins = csPlugins,
                            repoPlugins = repoPlugins,
                            repoLoadingState = repoLoadingState,
                            accentColor = accentColor,
                            onAddRepo = onAddRepo,
                            onDeleteRepo = onDeleteRepo,
                            onFetchRepoPlugins = onFetchRepoPlugins,
                            onInstallPlugin = onInstallPlugin,
                            onInstallAllPlugins = onInstallAllPlugins,
                            onUpdateAllPlugins = onUpdateAllPlugins,
                            bulkInstallRepoUrl = bulkInstallRepoUrl,
                            bulkInstallDone = bulkInstallDone,
                            bulkInstallTotal = bulkInstallTotal,
                            bulkInstallCurrentName = bulkInstallCurrentName,
                            onToggleCsPlugin = onToggleCsPlugin,
                            onUninstallCsPlugin = onUninstallCsPlugin,
                            useGithubProxy = useGithubProxy,
                            onUseGithubProxyChanged = onUseGithubProxyChanged,
                            isCheckingUpdates = isCheckingUpdates,
                            availableUpdatesCount = availableUpdatesCount,
                            onCheckForUpdates = onCheckForUpdates,
                            onUpdateAllPendingPlugins = onUpdateAllPendingPlugins,
                            onRefreshRepo = onRefreshRepo,
                            onVerifyPlugin = { pluginId, pluginName ->
                                scope.launch(Dispatchers.IO) {
                                    val cs3File = java.io.File(context.filesDir, "cs_extensions/${pluginId}.cs3")
                                    var loadedPlugin = com.kitsugi.animelist.data.cloudstream.CsPluginLoader.loadedPlugins[cs3File.absolutePath] as? com.lagradost.cloudstream3.plugins.Plugin
                                    if (loadedPlugin == null) {
                                        com.kitsugi.animelist.data.cloudstream.CsPluginLoader.loadExtension(context, pluginId)
                                    }
                                    val api = com.lagradost.cloudstream3.APIHolder.allProviders.firstOrNull { it.sourcePlugin == cs3File.absolutePath }
                                    val url = api?.mainUrl
                                    withContext(Dispatchers.Main) {
                                        if (!url.isNullOrBlank()) {
                                            webViewDialogUrl = url
                                            webViewDialogTitle = pluginName
                                        } else {
                                            android.widget.Toast.makeText(context, KitsugiStrings.get(R.string.eklenti_adresi_bulunamadi), android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            },
                            onOpenDiagnostic = onOpenDiagnostic,
                            listState = cloudstreamListState,
                            onExplorePlugin = onExplorePlugin,
                            onOpenPluginPicker = onOpenPluginPicker
                        )
                        2 -> MangaExtensionsTab(
                            sources = mangaSources,
                            onVerifySource = { url, name ->
                                webViewDialogUrl = url
                                webViewDialogTitle = name
                            },
                            accentColor = accentColor,
                            onInstallExtension = onInstallMangaExtension,
                            onDeleteExtension = onDeleteMangaExtension,
                            repos = mangaRepos,
                            repoExtensions = mangaRepoExtensions,
                            repoLoadingState = mangaRepoLoadingState,
                            onAddRepo = onAddMangaRepo,
                            onDeleteRepo = onDeleteMangaRepo,
                            onFetchRepo = onFetchMangaRepo,
                            onInstallApk = onInstallMangaApk,
                            onInstallAllExtensions = onInstallAllMangaExtensions,
                            onUpdateAllExtensions = onUpdateAllMangaExtensions,
                            bulkInstallRepoUrl = mangaBulkInstallRepoUrl,
                            bulkInstallDone = mangaBulkInstallDone,
                            bulkInstallTotal = mangaBulkInstallTotal,
                            bulkInstallCurrentName = mangaBulkInstallCurrentName,
                            onGetInstalledVersionCode = onGetInstalledMangaVersionCode,
                            onGetInstalledVersion = onGetInstalledMangaVersion,
                            sourceStateReport = mangaSourceStateReport,
                            onGetSourceHealthStatus = onGetMangaSourceHealthStatus,
                            onGetSourceRuntimeStats = onGetMangaSourceRuntimeStats,
                            onGetConfiguredDomain = onGetMangaConfiguredDomain,
                            onGetConfiguredBaseUrl = onGetMangaConfiguredBaseUrl,
                            onSetSourceDomain = onSetMangaSourceDomain,
                            onGetSourceUserAgent = onGetMangaSourceUserAgent,
                            onGetSourceSlowdownEnabled = onGetMangaSourceSlowdownEnabled,
                            onSetSourceUserAgent = onSetMangaSourceUserAgent,
                            onSetSourceSlowdownEnabled = onSetMangaSourceSlowdownEnabled,
                            onResetSourceDiagnostics = onResetMangaSourceDiagnostics,
                            onClearAllSourceDiagnostics = onClearAllMangaSourceDiagnostics,
                            onIsSourceBusy = onIsMangaSourceBusy,
                            onQuickCheckSource = onQuickCheckMangaSource,
                            onRefreshSourceMirror = onRefreshMangaSourceMirror,
                            onClearSourceMirror = onClearMangaSourceMirror,
                            onOpenSourceHealthScreen = onOpenMangaSourceHealthScreen,
                            onForceCheckUpdates = onForceCheckMangaUpdates,
                            listState = mangaListState
                        )
                    }
                }
            }

            // Footer (Tamam button)
            // GÖMÜLÜ MOD: Ayarlar alt sayfasında zaten üstte geri oku vardır; burada ikinci
            // bir "Tamam" düğmesi (ve altında kalan boş şerit) gösterilmez.
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

    if (webViewDialogUrl != null) {
        KitsugiWebViewDialog(
            title = webViewDialogTitle ?: "",
            url = webViewDialogUrl!!,
            onDismiss = {
                webViewDialogUrl = null
                webViewDialogTitle = null
            }
        )
    }

    if (untrustedRepoToConfirm != null) {
        AlertDialog(
            onDismissRequest = onDismissUntrustedRepo,
            title = { Text(stringResource(R.string.guvenli_olmayan_kaynak), color = KitsugiColors.TextPrimary) },
            text = {
                Text(
                    stringResource(R.string.eklemek_istediginiz_kaynak_guvenilir_eklenti_havuzlari_listesind, untrustedRepoToConfirm),
                    color = KitsugiColors.TextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = { onConfirmUntrustedRepo(untrustedRepoToConfirm) }) {
                    Text(stringResource(R.string.evet_ekle), color = accentColor, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissUntrustedRepo) {
                    Text(stringResource(R.string.vazgec), color = KitsugiColors.TextSecondary)
                }
            },
            containerColor = KitsugiColors.SurfaceStrong
        )
    }

    if (untrustedSignatureToConfirm != null) {
        val (ext, hash) = untrustedSignatureToConfirm
        AlertDialog(
            onDismissRequest = onDismissUntrustedSignature,
            title = { Text(stringResource(R.string.guvenli_olmayan_eklenti_imzasi), color = KitsugiColors.TextPrimary) },
            text = {
                Text(
                    stringResource(R.string.yuklemek_istediginiz_eklentinin_imzasi_guvenilir_imzalar_listesi, ext.name, ext.pkg, hash),
                    color = KitsugiColors.TextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = { onConfirmUntrustedSignature(ext, hash) }) {
                    Text(stringResource(R.string.guven_ve_yukle), color = accentColor, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissUntrustedSignature) {
                    Text(stringResource(R.string.iptal_et), color = KitsugiColors.TextSecondary)
                }
            },
            containerColor = KitsugiColors.SurfaceStrong
        )
    }
}

/**
 * Sekme seçici — "Torrent & Akış / Video Kaynakları / Manga Kaynakları".
 *
 * Material `TabRow` yerine yuvarlatılmış segment kullanılır: etiketler ikonla birlikte
 * alt alta durur, bu yüzden uzun Türkçe adlar dar telefonlarda dahi kesilmeden sığar ve
 * seçili sekme kartın içinde vurgulu bir kapsül olarak belirginleşir.
 */
@Composable
private fun AddonsSegmentedTabs(
    selectedIndex: Int,
    accentColor: Color,
    onSelect: (Int) -> Unit
) {
    // Sekme başlıkları kısa/madde madde tutulur: "Video Kaynakları" (Cloudstream) ve
    // "Manga Kaynakları" (Mihon/Tachiyomi) ayrımı ilk bakışta anlaşılır olsun.
    val tabs = listOf(
        stringResource(R.string.inline_torrent_streaming_89e2cac) to Icons.Rounded.Bolt,
        "Video Kaynakları" to Icons.Rounded.PlayCircle,
        "Manga Kaynakları" to Icons.Rounded.AutoStories
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(KitsugiColors.Surface)
            .border(1.dp, KitsugiColors.Border.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        tabs.forEachIndexed { index, (label, icon) ->
            val selected = selectedIndex == index
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(13.dp))
                    .background(if (selected) accentColor.copy(alpha = 0.16f) else Color.Transparent)
                    .border(
                        width = 1.dp,
                        color = if (selected) accentColor.copy(alpha = 0.45f) else Color.Transparent,
                        shape = RoundedCornerShape(13.dp)
                    )
                    .tvClickable(shape = RoundedCornerShape(13.dp)) { onSelect(index) }
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (selected) accentColor else KitsugiColors.TextMuted,
                    modifier = Modifier.size(17.dp)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = label,
                    color = if (selected) KitsugiColors.TextPrimary else KitsugiColors.TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
