package com.kitsugi.animelist.ui.components.addons

import com.kitsugi.animelist.ui.theme.gradient.CircularProgressIndicator
import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.Text
import com.kitsugi.animelist.R
import com.kitsugi.animelist.ui.components.KitsugiButton

import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.theme.gradient.border
import com.kitsugi.animelist.ui.utils.tvClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kitsugi.animelist.data.local.CloudstreamRepoEntity
import com.kitsugi.animelist.data.local.CsPluginEntity
import com.kitsugi.animelist.data.local.ManagedAddonEntity
import com.kitsugi.animelist.data.remote.CsPlugin
import com.kitsugi.animelist.ui.theme.KitsugiColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class CsTabSection { REPOS, INSTALLED }

@Composable
internal fun CloudstreamExtensionsTab(
    repos: List<CloudstreamRepoEntity>,
    addons: List<ManagedAddonEntity>,
    csPlugins: List<CsPluginEntity>,
    repoPlugins: Map<String, List<CsPlugin>?>,
    repoLoadingState: Map<String, Boolean>,
    accentColor: Color,
    onAddRepo: (String) -> Unit,
    onDeleteRepo: (CloudstreamRepoEntity) -> Unit,
    onFetchRepoPlugins: (String) -> Unit,
    onInstallPlugin: (CsPlugin, ((Boolean) -> Unit)?) -> Unit,
    onInstallAllPlugins: (repoUrl: String, repoName: String, plugins: List<CsPlugin>) -> Unit,
    onUpdateAllPlugins: (repoUrl: String, repoName: String, plugins: List<CsPlugin>) -> Unit,
    bulkInstallRepoUrl: String?,
    bulkInstallDone: Int,
    bulkInstallTotal: Int,
    bulkInstallCurrentName: String,
    onToggleCsPlugin: (CsPluginEntity, Boolean) -> Unit,
    onUninstallCsPlugin: (CsPluginEntity) -> Unit,
    onVerifyPlugin: (pluginId: String, pluginName: String) -> Unit,
    onOpenDiagnostic: (() -> Unit)? = null,
    useGithubProxy: Boolean = false,
    onUseGithubProxyChanged: (Boolean) -> Unit = {},
    isCheckingUpdates: Boolean = false,
    availableUpdatesCount: Int = 0,
    onCheckForUpdates: () -> Unit = {},
    onUpdateAllPendingPlugins: () -> Unit = {},
    onRefreshRepo: ((String) -> Unit)? = null,
    listState: LazyListState = rememberLazyListState(),
    onExplorePlugin: ((String) -> Unit)? = null,
    onOpenPluginPicker: (() -> Unit)? = null
) {
    var activeSection by rememberSaveable { mutableStateOf(CsTabSection.REPOS) }
    var newRepoUrl by rememberSaveable { mutableStateOf("") }

    val pluginInstallStates = remember { mutableStateMapOf<String, PluginInstallState>() }
    val reinstallStates = remember { mutableStateMapOf<String, PluginInstallState>() }
    val disabledPluginCount = csPlugins.count { !it.enabled }
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        try {
            val text = clipboardManager.getText()?.text?.trim() ?: ""
            if (text.isNotBlank()) {
                val isCode = com.kitsugi.animelist.utils.CloudstreamUrlHelper.isShortCode(text)
                val normalized = com.kitsugi.animelist.utils.CloudstreamUrlHelper.normalizeUrl(text)
                val isRepoUrl = normalized.startsWith("http://") || normalized.startsWith("https://") || isCode
                if (isRepoUrl && newRepoUrl.isBlank()) {
                    newRepoUrl = text
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("KitsugiAddonsSettingsDialog", "Failed to auto-read clipboard", e)
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // ── Bilgilendirme şeridi (Eklentiler sayfasının ortak bileşeni) ────
        item {
            AddonInfoBanner(
                accentColor = accentColor,
                text = stringResource(R.string.video_kaynaklari_cloudstream_depolarindan_gelir_depo_ekleyip_ekl)
            )
        }

        // ─ GitHub Vekil Sunucu (jsDelivr) Toggle ────────────────────────────────────────
        item {
            androidx.compose.foundation.layout.Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(KitsugiColors.SurfaceSoft)
            ) {
                com.kitsugi.animelist.ui.components.KitsugiSwitchSettingItem(
                    title = "🌐 GitHub Vekil Sunucu (jsDelivr)",
                    description = stringResource(R.string.raw_githubusercontent_com_url_lerini_jsdelivr_cdn_e_yonlendirir),
                    checked = useGithubProxy,
                    onCheckedChange = onUseGithubProxyChanged
                )
            }
        }

        // ─ Plugin Tanı Modu Bantı ─────────────────────────────────────────────────────
        if (onOpenDiagnostic != null) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
                        .background(accentColor.copy(alpha = 0.10f))
                        .border(
                            1.dp,
                            accentColor.copy(alpha = 0.30f),
                            androidx.compose.foundation.shape.RoundedCornerShape(14.dp)
                        )
                        .tvClickable(
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)
                        ) { onOpenDiagnostic() }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("🔬", fontSize = 20.sp)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.inline_plugin_diagnostics_mode_bea2832),
                            color = accentColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Text(
                            stringResource(R.string.inline_test_all_201_extensions_with_3be6c40),
                            color = KitsugiColors.TextMuted,
                            fontSize = 10.sp
                        )
                    }
                    Icon(
                        Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Bölüm Başlık Sekmesi
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(KitsugiColors.SurfaceSoft)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(
                    CsTabSection.REPOS to "🌐 Repolar (${repos.size})",
                    CsTabSection.INSTALLED to "✅ Kurulu (${csPlugins.size})"
                ).forEach { (section, label) ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (activeSection == section) accentColor.copy(alpha = 0.2f) else Color.Transparent)
                            .border(
                                1.dp,
                                if (activeSection == section) accentColor else Color.Transparent,
                                RoundedCornerShape(10.dp)
                            )
                            .tvClickable(shape = RoundedCornerShape(10.dp)) { activeSection = section }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            label,
                            color = if (activeSection == section) accentColor else KitsugiColors.TextSecondary,
                            fontWeight = if (activeSection == section) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 12.sp
                        )
                    }
                }

                // Check for updates button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(accentColor.copy(alpha = 0.15f))
                        .border(1.dp, accentColor.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                        .tvClickable(shape = RoundedCornerShape(10.dp)) {
                            onCheckForUpdates()
                        }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (isCheckingUpdates) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), color = accentColor, strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Rounded.Refresh, contentDescription = null, tint = accentColor, modifier = Modifier.size(15.dp))
                        }
                        Text(stringResource(R.string.yenile), color = accentColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (availableUpdatesCount > 0) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(KitsugiColors.AccentOrange.copy(alpha = 0.15f))
                        .border(1.dp, KitsugiColors.AccentOrange.copy(alpha = 0.40f), RoundedCornerShape(14.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Rounded.ArrowCircleUp, contentDescription = null, tint = KitsugiColors.AccentOrange, modifier = Modifier.size(22.dp))
                        Column {
                            Text(
                                stringResource(R.string.inline_1_s_extension_updates_available_1f54c44, availableUpdatesCount),
                                color = KitsugiColors.TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                stringResource(R.string.inline_you_can_update_everything_with_cce121c),
                                color = KitsugiColors.TextSecondary,
                                fontSize = 10.sp
                            )
                        }
                    }
                    KitsugiButton(
                        onClick = onUpdateAllPendingPlugins,
                        colors = ButtonDefaults.buttonColors(containerColor = KitsugiColors.AccentOrange),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(stringResource(R.string.tumunu_guncelle), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (activeSection == CsTabSection.REPOS) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(KitsugiColors.SurfaceSoft)
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        stringResource(R.string.inline_add_video_provider_repository_cs_1231a08),
                        color = KitsugiColors.TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Text(
                        stringResource(R.string.inline_enter_an_extension_repository_repo_68adbb5),
                        color = KitsugiColors.TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                    
                    // Predefined repos
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val predefinedRepos = listOf(
                            Triple(stringResource(R.string.kitsugi_eklentileri_onerilen), stringResource(R.string.dizipal_rectv_ddizi_dizilla_vb_160_derlenmis_ve_guncel_turkce_ek), "https://raw.githubusercontent.com/f26901964-eng/Kitsugi-Plugins/builds/repo.json"),
                            Triple("⚡ Kraptor (Kraptor123)", stringResource(R.string.animecix_animeler_animeelysium_eklentilerini_iceren_dizi_film_ve), "https://raw.githubusercontent.com/Kraptor123/cs-kraptor/builds/plugins.json"),
                            Triple("⚡ Kekik (feroxx)", stringResource(R.string.aktif_kekik_eklenti_deposu_derlemeleri_builds_ve_turkce_topluluk), "https://raw.githubusercontent.com/feroxx/Kekik-cloudstream/refs/heads/builds/repo.json"),
                            Triple("⚡ CS-Karma (Kraptor123)", stringResource(R.string.kraptor_un_alternatif_karma_turkce_eklenti_deposu), "https://raw.githubusercontent.com/Kraptor123/Cs-Karma/builds/plugins.json"),
                            Triple("⚡ KerimmKirac (cs-kekikanime)", stringResource(R.string.turkanime_eklentisi_ve_kraptor_ortakligindaki_turkce_anime_dizi), "https://raw.githubusercontent.com/Kraptor123/cs-kekikanime/builds/plugins.json"),
                            Triple("⚡ Nikyokki (nik-cloudstream)", stringResource(R.string.animpow_asyaanimeleri_eklentilerini_iceren_turkce_eklenti_deposu), "https://raw.githubusercontent.com/nikyokki/nik-cloudstream/master/repo.json"),
                            Triple("⚡ Makoto2 (Sertel392)", stringResource(R.string.makoto2_gecici_turkce_dizi_film_eklenti_deposu), "https://raw.githubusercontent.com/Sertel392/Makotogecici/main/repo.json"),
                            Triple("🔗 CS3 Hexated (Stremio Bridge)", stringResource(R.string.stremio_koprusu_ve_uluslararasi_saglayicilari_iceren_hexated_dep), "https://raw.githubusercontent.com/hexated/cloudstream-extensions-hexated/builds/plugins.json"),
                            Triple("🌍 ReCloudStream Eklentileri", stringResource(R.string.uluslararasi_icerikler_icin_resmi_cloudstream_eklentileri_deposu), "https://raw.githubusercontent.com/recloudstream/extensions/master/repo.json")
                        )
                        predefinedRepos.forEach { (name, desc, url) ->
                            val isAdded = repos.any { it.repoUrl.trim().lowercase() == url.trim().lowercase() }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(accentColor.copy(alpha = 0.08f))
                                    .tvClickable(shape = RoundedCornerShape(10.dp)) {
                                        if (!isAdded) onAddRepo(url)
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Rounded.Language,
                                    contentDescription = null,
                                    tint = accentColor,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(name, color = accentColor, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                    Text(desc, color = KitsugiColors.TextMuted, fontSize = 10.sp)
                                }
                                Text(
                                    if (isAdded) "✓ Eklendi" else stringResource(R.string.ekle_64b2d35),
                                    color = if (isAdded) KitsugiColors.TextMuted else accentColor,
                                    fontSize = 11.sp, fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(4.dp))

                    TextField(
                        value = newRepoUrl,
                        onValueChange = { newRepoUrl = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = false,
                        minLines = 2,
                        maxLines = 6,
                        shape = RoundedCornerShape(18.dp),
                        label = { Text(stringResource(R.string.inline_repository_url_or_short_code_b3aa7d2)) },
                        placeholder = { Text("https://raw.githubusercontent.com/...\nkekikdevam") },
                        colors = TextFieldDefaults.colors(
                            focusedTextColor = KitsugiColors.TextPrimary, unfocusedTextColor = KitsugiColors.TextPrimary,
                            focusedContainerColor = KitsugiColors.SurfaceStrong, unfocusedContainerColor = KitsugiColors.SurfaceStrong,
                            focusedIndicatorColor = accentColor, unfocusedIndicatorColor = Color.Transparent, cursorColor = accentColor
                        )
                    )
                    KitsugiButton(
                        onClick = {
                            if (newRepoUrl.isNotBlank()) {
                                newRepoUrl.lines()
                                    .map { it.trim() }
                                    .filter { it.isNotBlank() }
                                    .forEach { url -> onAddRepo(url) }
                                newRepoUrl = ""
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.ekle), tint = KitsugiColors.Surface)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.ekle), color = KitsugiColors.Surface, fontWeight = FontWeight.Bold)
                    }
                }
            }

            item {
                Spacer(Modifier.height(4.dp))
                AddonListHeader(
                    title = stringResource(R.string.eklenti_depolari),
                    count = repos.size,
                    accentColor = accentColor
                )
            }

            if (repos.isEmpty()) {
                item {
                    AddonEmptyCard(text = stringResource(R.string.henuz_eklenti_deposu_eklenmemis_yukaridaki_alandan_bir_depo_adre))
                }
            } else {
                items(repos, key = { it.repoUrl }) { repo ->
                    val plugins = repoPlugins[repo.repoUrl]
                    val hasAttemptedFetch = repoPlugins.containsKey(repo.repoUrl)
                    val isLoading = repoLoadingState[repo.repoUrl] == true
                    val isBulkInstalling = bulkInstallRepoUrl == repo.repoUrl

                    CsRepoCard(
                        repo = repo,
                        plugins = plugins,
                        hasAttemptedFetch = hasAttemptedFetch,
                        isLoading = isLoading,
                        addons = addons,
                        csPlugins = csPlugins,
                        accentColor = accentColor,
                        onDeleteRepo = { onDeleteRepo(repo) },
                        onFetchRepoPlugins = { onFetchRepoPlugins(repo.repoUrl) },
                        onRefreshRepo = { if (onRefreshRepo != null) onRefreshRepo(repo.repoUrl) else onFetchRepoPlugins(repo.repoUrl) },
                        onInstallAllPlugins = { pluginsToInstall ->
                            onInstallAllPlugins(repo.repoUrl, repo.name, pluginsToInstall)
                        },
                        onUpdateAllPlugins = { pluginsToUpdate ->
                            onUpdateAllPlugins(repo.repoUrl, repo.name, pluginsToUpdate)
                        },
                        isBulkInstalling = isBulkInstalling,
                        bulkInstallDone = bulkInstallDone,
                        bulkInstallTotal = bulkInstallTotal,
                        bulkInstallCurrentName = bulkInstallCurrentName,
                        pluginInstallStates = pluginInstallStates,
                        onInstallPlugin = onInstallPlugin,
                        onStartInstall = { pluginId ->
                            pluginInstallStates[pluginId] = PluginInstallState.LOADING
                        },
                        onInstallResult = { pluginId, success ->
                            pluginInstallStates[pluginId] = if (success) PluginInstallState.SUCCESS else PluginInstallState.FAILURE
                            if (success) {
                                scope.launch {
                                    delay(1500)
                                    pluginInstallStates.remove(pluginId)
                                }
                            }
                        }
                    )
                }
            }
        } else {
            // Yüklü CS Eklentileri
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(stringResource(R.string.inline_installed_video_providers_d43a80f), color = KitsugiColors.TextPrimary, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(accentColor.copy(alpha = 0.15f))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = csPlugins.size.toString(),
                                    color = accentColor,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            stringResource(R.string.inline_video_provider_cs3_extensions_used_8c0e4e8),
                            color = KitsugiColors.TextSecondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (availableUpdatesCount > 0) {
                            KitsugiButton(
                                onClick = onUpdateAllPendingPlugins,
                                colors = ButtonDefaults.buttonColors(containerColor = KitsugiColors.AccentOrange),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Rounded.ArrowCircleUp, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.inline_update_all_1_s_1a95494, availableUpdatesCount), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        if (onOpenPluginPicker != null) {
                            KitsugiButton(
                                onClick = onOpenPluginPicker,
                                colors = ButtonDefaults.buttonColors(containerColor = accentColor.copy(alpha = 0.15f)),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Rounded.Extension, contentDescription = null, tint = accentColor, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.eklenti_portali), color = accentColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            if (csPlugins.isEmpty()) {
                item {
                    AddonEmptyCard(
                        icon = Icons.Rounded.ExtensionOff,
                        text = stringResource(R.string.henuz_eklenti_kurulmamis_depolar_sekmesinden_eklenti_kurabilirsi)
                    )
                }
            } else {
                if (disabledPluginCount > 0) {
                    item(key = "reenable_all_disabled_cs_plugins") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(accentColor.copy(alpha = 0.10f))
                                .border(1.dp, accentColor.copy(alpha = 0.30f), RoundedCornerShape(12.dp))
                                .tvClickable(shape = RoundedCornerShape(12.dp)) {
                                    csPlugins.filterNot { it.enabled }.forEach { onToggleCsPlugin(it, true) }
                                }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = accentColor, modifier = Modifier.size(20.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.cs_reenable_disabled_plugins_title), color = accentColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(
                                    stringResource(R.string.cs_reenable_disabled_plugins_description, disabledPluginCount),
                                    color = KitsugiColors.TextMuted,
                                    fontSize = 11.sp
                                )
                            }
                            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = accentColor, modifier = Modifier.size(20.dp))
                        }
                    }
                }
                items(csPlugins, key = { it.id }) { plugin ->
                    val reinstallState = reinstallStates[plugin.id] ?: PluginInstallState.IDLE
                    val latestPlugin = repoPlugins.values.filterNotNull().flatten().find { it.internalName == plugin.id }

                    CsInstalledPluginRow(
                        plugin = plugin,
                        accentColor = accentColor,
                        reinstallState = reinstallState,
                        latestPlugin = latestPlugin,
                        onInstallPlugin = onInstallPlugin,
                        onToggleCsPlugin = onToggleCsPlugin,
                        onUninstallCsPlugin = onUninstallCsPlugin,
                        onVerifyPlugin = onVerifyPlugin,
                        onExplorePlugin = onExplorePlugin,
                        onStartReinstall = {
                            reinstallStates[plugin.id] = PluginInstallState.LOADING
                        },
                        onReinstallResult = { success ->
                            reinstallStates[plugin.id] = if (success) PluginInstallState.SUCCESS else PluginInstallState.FAILURE
                            if (success) {
                                scope.launch {
                                    delay(1500)
                                    reinstallStates.remove(plugin.id)
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}
