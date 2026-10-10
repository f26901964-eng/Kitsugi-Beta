package com.kitsugi.animelist.ui.components

import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.Text

import com.kitsugi.animelist.ui.theme.gradient.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
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
import com.kitsugi.animelist.ui.theme.LocalIsTv

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KitsugiAccountConnectionsDialog(
    isAniListConnected: Boolean,
    anilistUsername: String,
    isAniListImportRunning: Boolean,
    onAniListImportClick: () -> Unit,
    onAniListAuthClick: () -> Unit,
    isMalConnected: Boolean,
    malUsername: String,
    isMalImportRunning: Boolean,
    onMalImportClick: () -> Unit,
    onMalAuthClick: () -> Unit,
    isSimklConnected: Boolean = false,
    simklUsername: String = "",
    isSimklImportRunning: Boolean = false,
    isSimklSessionExpired: Boolean = false,
    onSimklImportClick: () -> Unit = {},
    onSimklAuthClick: () -> Unit = {},
    isKitsuConnected: Boolean = false,
    kitsuUsername: String = "",
    isKitsuImportRunning: Boolean = false,
    onKitsuImportClick: () -> Unit = {},
    onKitsuAuthClick: () -> Unit = {},
    syncEnabledKitsu: Boolean = false,
    onSyncEnabledKitsuChanged: (Boolean) -> Unit = {},
    onLoginKitsu: (username: String, password: String, onComplete: (Boolean, String?) -> Unit) -> Unit = { _, _, _ -> },
    isShikimoriConnected: Boolean = false,
    shikimoriUsername: String = "",
    isShikimoriImportRunning: Boolean = false,
    onShikimoriImportClick: () -> Unit = {},
    onShikimoriAuthClick: () -> Unit = {},
    syncEnabledShikimori: Boolean = false,
    onSyncEnabledShikimoriChanged: (Boolean) -> Unit = {},
    onLoginShikimori: (clientId: String, clientSecret: String, authCode: String, onComplete: (Boolean, String?) -> Unit) -> Unit = { _, _, _, _ -> },
    isCrossSyncRunning: Boolean = false,
    crossSyncState: com.kitsugi.animelist.model.CrossSyncProgressState = com.kitsugi.animelist.model.CrossSyncProgressState(),
    onCrossSyncClick: () -> Unit = {},
    onCrossSyncCancel: () -> Unit = {},
    syncEnabledAnilist: Boolean = false,
    onSyncEnabledAnilistChanged: (Boolean) -> Unit = {},
    syncEnabledMal: Boolean = false,
    onSyncEnabledMalChanged: (Boolean) -> Unit = {},
    syncEnabledSimkl: Boolean = false,
    onSyncEnabledSimklChanged: (Boolean) -> Unit = {},
    embeddedMode: Boolean = false,
    onDismiss: () -> Unit
) {
    val accentColor = LocalKitsugiAccent.current
    val scrollState = rememberScrollState()

    KitsugiSheetOrDialog(
        onDismiss = onDismiss,
        fullScreen = true,
        embeddedMode = embeddedMode,
        innerColumnScrollState = scrollState
    ) {
        if (!embeddedMode) {
            // Header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Hesap Bağlantıları",
                        color = KitsugiColors.TextPrimary,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "Kapat", tint = KitsugiColors.TextSecondary)
                    }
                }
            }
        }

        // Body
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 16.dp)
        ) {
            AccountConnectionsTab(
                isAniListConnected = isAniListConnected,
                anilistUsername = anilistUsername,
                isAniListImportRunning = isAniListImportRunning,
                onAniListImportClick = onAniListImportClick,
                onAniListAuthClick = onAniListAuthClick,
                isMalConnected = isMalConnected,
                malUsername = malUsername,
                isMalImportRunning = isMalImportRunning,
                onMalImportClick = onMalImportClick,
                onMalAuthClick = onMalAuthClick,
                isSimklConnected = isSimklConnected,
                simklUsername = simklUsername,
                isSimklImportRunning = isSimklImportRunning,
                isSimklSessionExpired = isSimklSessionExpired,
                onSimklImportClick = onSimklImportClick,
                onSimklAuthClick = onSimklAuthClick,
                isKitsuConnected = isKitsuConnected,
                kitsuUsername = kitsuUsername,
                isKitsuImportRunning = isKitsuImportRunning,
                onKitsuImportClick = onKitsuImportClick,
                onKitsuAuthClick = onKitsuAuthClick,
                syncEnabledKitsu = syncEnabledKitsu,
                onSyncEnabledKitsuChanged = onSyncEnabledKitsuChanged,
                onLoginKitsu = onLoginKitsu,
                isShikimoriConnected = isShikimoriConnected,
                shikimoriUsername = shikimoriUsername,
                isShikimoriImportRunning = isShikimoriImportRunning,
                onShikimoriImportClick = onShikimoriImportClick,
                onShikimoriAuthClick = onShikimoriAuthClick,
                syncEnabledShikimori = syncEnabledShikimori,
                onSyncEnabledShikimoriChanged = onSyncEnabledShikimoriChanged,
                onLoginShikimori = onLoginShikimori,
                isCrossSyncRunning = isCrossSyncRunning,
                crossSyncState = crossSyncState,
                onCrossSyncClick = onCrossSyncClick,
                onCrossSyncCancel = onCrossSyncCancel,
                syncEnabledAnilist = syncEnabledAnilist,
                onSyncEnabledAnilistChanged = onSyncEnabledAnilistChanged,
                syncEnabledMal = syncEnabledMal,
                onSyncEnabledMalChanged = onSyncEnabledMalChanged,
                syncEnabledSimkl = syncEnabledSimkl,
                onSyncEnabledSimklChanged = onSyncEnabledSimklChanged,
                accentColor = accentColor,
                scrollState = scrollState
            )
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
                    Text("Tamam", color = accentColor, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun AccountConnectionsTab(
    isAniListConnected: Boolean,
    anilistUsername: String,
    isAniListImportRunning: Boolean,
    onAniListImportClick: () -> Unit,
    onAniListAuthClick: () -> Unit,
    isMalConnected: Boolean,
    malUsername: String,
    isMalImportRunning: Boolean,
    onMalImportClick: () -> Unit,
    onMalAuthClick: () -> Unit,
    isSimklConnected: Boolean,
    simklUsername: String,
    isSimklImportRunning: Boolean,
    isSimklSessionExpired: Boolean,
    onSimklImportClick: () -> Unit,
    onSimklAuthClick: () -> Unit,
    isKitsuConnected: Boolean,
    kitsuUsername: String,
    isKitsuImportRunning: Boolean,
    onKitsuImportClick: () -> Unit,
    onKitsuAuthClick: () -> Unit,
    syncEnabledKitsu: Boolean,
    onSyncEnabledKitsuChanged: (Boolean) -> Unit,
    onLoginKitsu: (username: String, password: String, onComplete: (Boolean, String?) -> Unit) -> Unit,
    isShikimoriConnected: Boolean,
    shikimoriUsername: String,
    isShikimoriImportRunning: Boolean,
    onShikimoriImportClick: () -> Unit,
    onShikimoriAuthClick: () -> Unit,
    syncEnabledShikimori: Boolean,
    onSyncEnabledShikimoriChanged: (Boolean) -> Unit,
    onLoginShikimori: (clientId: String, clientSecret: String, authCode: String, onComplete: (Boolean, String?) -> Unit) -> Unit,
    isCrossSyncRunning: Boolean,
    crossSyncState: com.kitsugi.animelist.model.CrossSyncProgressState,
    onCrossSyncClick: () -> Unit,
    onCrossSyncCancel: () -> Unit,
    syncEnabledAnilist: Boolean,
    onSyncEnabledAnilistChanged: (Boolean) -> Unit,
    syncEnabledMal: Boolean,
    onSyncEnabledMalChanged: (Boolean) -> Unit,
    syncEnabledSimkl: Boolean,
    onSyncEnabledSimklChanged: (Boolean) -> Unit,
    accentColor: Color,
    scrollState: androidx.compose.foundation.ScrollState
) {
    val isTv = LocalIsTv.current
    var showTvQrDialog by remember { mutableStateOf(false) }
    var showKitsuLoginDialog by remember { mutableStateOf(false) }
    var showShikimoriLoginDialog by remember { mutableStateOf(false) }
    var showCrossSyncDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // AniList
        KitsugiSettingsSection(title = "AniList Hesabı") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                if (isAniListConnected) {
                    KitsugiSettingsItem(
                        title = "AniList Hesabı ($anilistUsername)",
                        description = if (isAniListImportRunning) "Senkronize ediliyor..." else "Listenizi AniList'ten içe aktarın",
                        icon = Icons.Rounded.Sync,
                        iconColor = KitsugiColors.AccentGreen,
                        onClick = { if (!isAniListImportRunning) onAniListImportClick() }
                    )
                    KitsugiSettingsDivider()
                    KitsugiSettingsSwitchItem(
                        title = "Otomatik Eşitleme",
                        description = "Kitsugi'deki değişiklikleri AniList'e otomatik yansıt",
                        icon = Icons.Rounded.CloudSync,
                        iconColor = KitsugiColors.AccentBlue,
                        checked = syncEnabledAnilist,
                        onCheckedChange = onSyncEnabledAnilistChanged
                    )
                    KitsugiSettingsDivider()
                    KitsugiSettingsItem(
                        title = "AniList Bağlantısını Kes",
                        description = "Hesabınızı uygulamadan kaldırır",
                        icon = Icons.Rounded.LinkOff,
                        iconColor = KitsugiColors.AccentRed,
                        onClick = onAniListAuthClick
                    )
                } else {
                    KitsugiSettingsItem(
                        title = "AniList Hesabını Bağla",
                        description = if (isTv) "TV'de QR kod ile giriş yap" else "Giriş yap ve listenizi eşitle",
                        icon = Icons.Rounded.Link,
                        iconColor = KitsugiColors.AccentBlue,
                        onClick = {
                            if (isTv) showTvQrDialog = true
                            else onAniListAuthClick()
                        }
                    )
                }
            }
        }

        // MyAnimeList
        KitsugiSettingsSection(title = "MyAnimeList Hesabı") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                if (isMalConnected) {
                    KitsugiSettingsItem(
                        title = "MyAnimeList Hesabı ($malUsername)",
                        description = if (isMalImportRunning) "Senkronize ediliyor..." else "Listenizi MyAnimeList'ten içe aktarın",
                        icon = Icons.Rounded.Sync,
                        iconColor = KitsugiColors.AccentGreen,
                        onClick = { if (!isMalImportRunning) onMalImportClick() }
                    )
                    KitsugiSettingsDivider()
                    KitsugiSettingsSwitchItem(
                        title = "Otomatik Eşitleme",
                        description = "Kitsugi'deki değişiklikleri MyAnimeList'e otomatik yansıt",
                        icon = Icons.Rounded.CloudSync,
                        iconColor = KitsugiColors.AccentBlue,
                        checked = syncEnabledMal,
                        onCheckedChange = onSyncEnabledMalChanged
                    )
                    KitsugiSettingsDivider()
                    KitsugiSettingsItem(
                        title = "MyAnimeList Bağlantısını Kes",
                        description = "Hesabınızı uygulamadan kaldırır",
                        icon = Icons.Rounded.LinkOff,
                        iconColor = KitsugiColors.AccentRed,
                        onClick = onMalAuthClick
                    )
                } else {
                    KitsugiSettingsItem(
                        title = "MyAnimeList Hesabını Bağla",
                        description = if (isTv) "TV'de QR kod ile giriş yap" else "Giriş yap ve listenizi eşitle",
                        icon = Icons.Rounded.Link,
                        iconColor = KitsugiColors.AccentBlue,
                        onClick = {
                            if (isTv) showTvQrDialog = true
                            else onMalAuthClick()
                        }
                    )
                }
            }
        }

        // Simkl
        KitsugiSettingsSection(title = "Simkl Hesabı") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                if (isSimklConnected) {
                    KitsugiSettingsItem(
                        title = "Simkl Hesabı ($simklUsername)",
                        description = if (isSimklImportRunning) "Senkronize ediliyor..." else "Listenizi Simkl'dan içe aktarın",
                        icon = Icons.Rounded.Sync,
                        iconColor = KitsugiColors.AccentGreen,
                        onClick = { if (!isSimklImportRunning) onSimklImportClick() }
                    )
                    KitsugiSettingsDivider()
                    KitsugiSettingsSwitchItem(
                        title = "Otomatik Eşitleme",
                        description = "Kitsugi'deki değişiklikleri Simkl'e otomatik yansıt",
                        icon = Icons.Rounded.CloudSync,
                        iconColor = KitsugiColors.AccentBlue,
                        checked = syncEnabledSimkl,
                        onCheckedChange = onSyncEnabledSimklChanged
                    )
                    KitsugiSettingsDivider()
                    KitsugiSettingsItem(
                        title = "Simkl Bağlantısını Kes",
                        description = "Hesabınızı uygulamadan kaldırır",
                        icon = Icons.Rounded.LinkOff,
                        iconColor = KitsugiColors.AccentRed,
                        onClick = onSimklAuthClick
                    )
                } else if (isSimklSessionExpired) {
                    KitsugiSettingsItem(
                        title = "Simkl Bağlantısını Yenile (Süresi Doldu)",
                        description = "Oturum süresi doldu. Tekrar giriş yapın.",
                        icon = Icons.Rounded.Warning,
                        iconColor = KitsugiColors.AccentRed,
                        onClick = {
                            if (isTv) showTvQrDialog = true
                            else onSimklAuthClick()
                        }
                    )
                } else {
                    KitsugiSettingsItem(
                        title = "Simkl Hesabını Bağla",
                        description = if (isTv) "TV'de QR kod ile giriş yap" else "Giriş yap, film, dizi ve animelerini eşitle",
                        icon = Icons.Rounded.Link,
                        iconColor = KitsugiColors.AccentBlue,
                        onClick = {
                            if (isTv) showTvQrDialog = true
                            else onSimklAuthClick()
                        }
                    )
                }
            }
        }

        // Kitsu
        val kitsuColor = Color(0xFFFD755C)
        KitsugiSettingsSection(title = "Kitsu Hesabı") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                if (isKitsuConnected) {
                    KitsugiSettingsItem(
                        title = "Kitsu Hesabı ($kitsuUsername)",
                        description = if (isKitsuImportRunning) "Senkronize ediliyor..." else "Listenizi Kitsu'dan içe aktarın",
                        icon = Icons.Rounded.Sync,
                        iconColor = KitsugiColors.AccentGreen,
                        onClick = { if (!isKitsuImportRunning) onKitsuImportClick() }
                    )
                    KitsugiSettingsDivider()
                    KitsugiSettingsSwitchItem(
                        title = "Otomatik Eşitleme",
                        description = "Kitsugi'deki değişiklikleri Kitsu'ya otomatik yansıt",
                        icon = Icons.Rounded.CloudSync,
                        iconColor = kitsuColor,
                        checked = syncEnabledKitsu,
                        onCheckedChange = onSyncEnabledKitsuChanged
                    )
                    KitsugiSettingsDivider()
                    KitsugiSettingsItem(
                        title = "Kitsu Bağlantısını Kes",
                        description = "Hesabınızı uygulamadan kaldırır",
                        icon = Icons.Rounded.LinkOff,
                        iconColor = KitsugiColors.AccentRed,
                        onClick = onKitsuAuthClick
                    )
                } else {
                    KitsugiSettingsItem(
                        title = "Kitsu Hesabını Bağla",
                        description = "Kullanıcı adı ve şifrenizle giriş yapıp listenizi eşitleyin",
                        icon = Icons.Rounded.Link,
                        iconColor = kitsuColor,
                        onClick = { showKitsuLoginDialog = true }
                    )
                }
            }
        }

        // Shikimori
        val shikimoriColor = Color(0xFF8E44AD)
        KitsugiSettingsSection(title = "Shikimori Hesabı") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                if (isShikimoriConnected) {
                    KitsugiSettingsItem(
                        title = "Shikimori Hesabı ($shikimoriUsername)",
                        description = if (isShikimoriImportRunning) "Senkronize ediliyor..." else "Listenizi Shikimori'den içe aktarın",
                        icon = Icons.Rounded.Sync,
                        iconColor = KitsugiColors.AccentGreen,
                        onClick = { if (!isShikimoriImportRunning) onShikimoriImportClick() }
                    )
                    KitsugiSettingsDivider()
                    KitsugiSettingsSwitchItem(
                        title = "Otomatik Eşitleme",
                        description = "Kitsugi'deki değişiklikleri Shikimori'ye otomatik yansıt",
                        icon = Icons.Rounded.CloudSync,
                        iconColor = shikimoriColor,
                        checked = syncEnabledShikimori,
                        onCheckedChange = onSyncEnabledShikimoriChanged
                    )
                    KitsugiSettingsDivider()
                    KitsugiSettingsItem(
                        title = "Shikimori Bağlantısını Kes",
                        description = "Hesabınızı uygulamadan kaldırır",
                        icon = Icons.Rounded.LinkOff,
                        iconColor = KitsugiColors.AccentRed,
                        onClick = onShikimoriAuthClick
                    )
                } else {
                    KitsugiSettingsItem(
                        title = "Shikimori Hesabını Bağla",
                        description = "OAuth2 yetkilendirme ile Shikimori listenizi eşitleyin",
                        icon = Icons.Rounded.Link,
                        iconColor = shikimoriColor,
                        onClick = { showShikimoriLoginDialog = true }
                    )
                }
            }
        }

        // Çok Yönlü Eşitleme (En az 2 hesap bağlıysa göster)
        val context = androidx.compose.ui.platform.LocalContext.current
        val isBangumiConnected = com.kitsugi.animelist.data.auth.BangumiAuthStore.isConnected(context)
        val connectedCount = (if (isAniListConnected) 1 else 0) +
                (if (isMalConnected) 1 else 0) +
                (if (isSimklConnected) 1 else 0) +
                (if (isKitsuConnected) 1 else 0) +
                (if (isShikimoriConnected) 1 else 0) +
                (if (isBangumiConnected) 1 else 0)

        if (connectedCount >= 2) {
            val connectedNames = mutableListOf<String>().apply {
                if (isAniListConnected) add("AniList")
                if (isMalConnected) add("MyAnimeList")
                if (isSimklConnected) add("Simkl")
                if (isKitsuConnected) add("Kitsu")
                if (isShikimoriConnected) add("Shikimori")
                if (isBangumiConnected) add("Bangumi")
            }.joinToString(", ")

            KitsugiSettingsSection(title = "Çok Yönlü Eşitleme") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                ) {
                    val syncDesc = when {
                        crossSyncState.isRunning -> crossSyncState.currentStep +
                            if (crossSyncState.totalItems > 0) " (%${(crossSyncState.progressPercent * 100).toInt()})" else ""
                        crossSyncState.startedAt != null -> "Son eşitleme raporunu, sorunları ve platform ayrıntılarını görüntüle"
                        else -> "Deneysel · $connectedNames verilerini karşılıklı senkronize edin (Asla silme yapılmaz)"
                    }

                    KitsugiSettingsItem(
                        title = when {
                            crossSyncState.isRunning -> "Eşitleme Ayrıntılarını Aç ($connectedCount/6)"
                            crossSyncState.startedAt != null -> "Son Eşitleme Raporu ($connectedCount/6)"
                            else -> "Tüm Hesapları Birbiriyle Eşitle ($connectedCount/6)"
                        },
                        description = syncDesc,
                        icon = Icons.Rounded.Cached,
                        iconColor = KitsugiColors.AccentOrange,
                        onClick = {
                            showCrossSyncDialog = true
                            if (!crossSyncState.isRunning && crossSyncState.startedAt == null) {
                                onCrossSyncClick()
                            }
                        }
                    )

                    CrossSyncDisclaimerText(
                        full = false,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )

                    if (crossSyncState.isRunning) {
                        Spacer(modifier = Modifier.height(4.dp))
                        if (crossSyncState.totalItems <= 0) {
                            com.kitsugi.animelist.ui.theme.gradient.LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(2.dp)),
                                color = KitsugiColors.AccentOrange,
                                trackColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant
                            )
                        } else {
                            com.kitsugi.animelist.ui.theme.gradient.LinearProgressIndicator(
                                progress = { crossSyncState.progressPercent },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(2.dp)),
                                color = KitsugiColors.AccentOrange,
                                trackColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    if (showTvQrDialog) {
        KitsugiTvQrLoginDialog(
            onDismiss = { showTvQrDialog = false }
        )
    }

    if (showKitsuLoginDialog) {
        KitsugiKitsuLoginDialog(
            onDismiss = { showKitsuLoginDialog = false },
            onLogin = onLoginKitsu
        )
    }

    if (showShikimoriLoginDialog) {
        KitsugiShikimoriLoginDialog(
            onDismiss = { showShikimoriLoginDialog = false },
            onLogin = onLoginShikimori
        )
    }

    if (showCrossSyncDialog) {
        KitsugiCrossSyncDialog(
            state = crossSyncState,
            onDismiss = { showCrossSyncDialog = false },
            onCancel = onCrossSyncCancel,
            onStart = onCrossSyncClick
        )
    }
}
