package com.kitsugi.animelist.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kitsugi.animelist.ui.components.*
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalIsTv
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent

// ─────────────────────────────────────────────────────────────────────────────
// 1. Hesap Bağlantıları Ana Hub Sayfası
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun AccountConnectionsHubContent(
    profile: ProfileSettings,
    onNavigate: (SettingsRoute) -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Anime ve manga platformlarınızı bağlayarak kütüphanelerinizi ve izleme geçmişinizi tüm cihazlarınız arasında senkronize tutun.",
            color = KitsugiColors.TextSecondary,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
        )

        SectionHeader("Platformlar")
        SettingsNavCard {
            // AniList
            AccountHubItem(
                title = "AniList",
                subtitle = if (profile.isAniListConnected) "Bağlı (${profile.anilistUsername})" else "Bağlı Değil",
                platformId = "anilist",
                isConnected = profile.isAniListConnected,
                onClick = { onNavigate(SettingsRoute.AniListSettings) }
            )
            KitsugiSettingsDivider()

            // MyAnimeList
            AccountHubItem(
                title = "MyAnimeList",
                subtitle = if (profile.isMalConnected) "Bağlı (${profile.malUsername})" else "Bağlı Değil",
                platformId = "mal",
                isConnected = profile.isMalConnected,
                onClick = { onNavigate(SettingsRoute.MalSettings) }
            )
            KitsugiSettingsDivider()

            // Simkl
            AccountHubItem(
                title = "Simkl",
                subtitle = when {
                    profile.isSimklConnected -> "Bağlı (${profile.simklUsername})"
                    profile.isSimklSessionExpired -> "Oturum Süresi Doldu"
                    else -> "Bağlı Değil"
                },
                platformId = "simkl",
                isConnected = profile.isSimklConnected,
                onClick = { onNavigate(SettingsRoute.SimklSettings) }
            )
            KitsugiSettingsDivider()

            // Kitsu
            AccountHubItem(
                title = "Kitsu",
                subtitle = if (profile.isKitsuConnected) "Bağlı (${profile.kitsuUsername})" else "Bağlı Değil",
                platformId = "kitsu",
                isConnected = profile.isKitsuConnected,
                onClick = { onNavigate(SettingsRoute.KitsuSettings) }
            )
            KitsugiSettingsDivider()

            // Shikimori
            AccountHubItem(
                title = "Shikimori",
                subtitle = if (profile.isShikimoriConnected) "Bağlı (${profile.shikimoriUsername})" else "Bağlı Değil",
                platformId = "shikimori",
                isConnected = profile.isShikimoriConnected,
                onClick = { onNavigate(SettingsRoute.ShikimoriSettings) }
            )
        }

        SectionHeader("Senkronizasyon")
        SettingsNavCard {
            val connectedCount = (if (profile.isAniListConnected) 1 else 0) +
                    (if (profile.isMalConnected) 1 else 0) +
                    (if (profile.isSimklConnected) 1 else 0) +
                    (if (profile.isKitsuConnected) 1 else 0) +
                    (if (profile.isShikimoriConnected) 1 else 0)

            AccountHubItem(
                title = "Çapraz Eşitleme (Cross-Sync)",
                subtitle = if (connectedCount >= 2) "$connectedCount hesap arasında karşılıklı senkronizasyon" else "En az 2 hesap bağlandığında etkinleşir",
                icon = Icons.Rounded.CloudSync,
                iconColor = KitsugiColors.AccentOrange,
                isConnected = connectedCount >= 2,
                onClick = { onNavigate(SettingsRoute.CrossSyncSettings) }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 2. AniList Alt Sayfası
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun AniListSettingsContent(profile: ProfileSettings) {
    val isTv = LocalIsTv.current
    var showTvQrDialog by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ConnectionStatusCard(
            platformName = "AniList",
            platformId = "anilist",
            username = profile.anilistUsername,
            isConnected = profile.isAniListConnected
        )

        SectionHeader("İşlemler")
        SettingsNavCard {
            if (profile.isAniListConnected) {
                KitsugiSettingsItem(
                    title = "Kütüphaneyi İçe Aktar",
                    description = if (profile.isAniListImportRunning) "Senkronize ediliyor..." else "Listenizi AniList'ten içe aktarın",
                    icon = Icons.Rounded.Sync,
                    iconColor = KitsugiColors.AccentGreen,
                    onClick = { if (!profile.isAniListImportRunning) profile.onAniListImportClick() }
                )
                KitsugiSettingsDivider()
                KitsugiSettingsSwitchItem(
                    title = "Otomatik Eşitleme",
                    description = "Kitsugi'deki değişiklikleri AniList'e anında yansıt",
                    icon = Icons.Rounded.CloudSync,
                    iconColor = KitsugiColors.AccentBlue,
                    checked = profile.syncEnabledAnilist,
                    onCheckedChange = profile.onSyncEnabledAnilistChanged
                )
                KitsugiSettingsDivider()
                KitsugiSettingsItem(
                    title = "AniList Bağlantısını Kes",
                    description = "Hesabınızı uygulamadan kaldırır",
                    icon = Icons.Rounded.LinkOff,
                    iconColor = KitsugiColors.AccentRed,
                    onClick = profile.onAniListAuthClick
                )
            } else {
                KitsugiSettingsItem(
                    title = "AniList Hesabını Bağla",
                    description = if (isTv) "TV'de QR kod ile giriş yap" else "OAuth ile güvenli giriş yapın ve listenizi eşitleyin",
                    icon = Icons.Rounded.Link,
                    iconColor = KitsugiColors.AccentBlue,
                    onClick = {
                        if (isTv) showTvQrDialog = true
                        else profile.onAniListAuthClick()
                    }
                )
            }
        }
    }

    if (showTvQrDialog) {
        KitsugiTvQrLoginDialog(onDismiss = { showTvQrDialog = false })
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 3. MyAnimeList Alt Sayfası
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun MalSettingsContent(profile: ProfileSettings) {
    val isTv = LocalIsTv.current
    var showTvQrDialog by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ConnectionStatusCard(
            platformName = "MyAnimeList",
            platformId = "mal",
            username = profile.malUsername,
            isConnected = profile.isMalConnected
        )

        SectionHeader("İşlemler")
        SettingsNavCard {
            if (profile.isMalConnected) {
                KitsugiSettingsItem(
                    title = "Kütüphaneyi İçe Aktar",
                    description = if (profile.isMalImportRunning) "Senkronize ediliyor..." else "Listenizi MyAnimeList'ten içe aktarın",
                    icon = Icons.Rounded.Sync,
                    iconColor = KitsugiColors.AccentGreen,
                    onClick = { if (!profile.isMalImportRunning) profile.onMalImportClick() }
                )
                KitsugiSettingsDivider()
                KitsugiSettingsSwitchItem(
                    title = "Otomatik Eşitleme",
                    description = "Kitsugi'deki değişiklikleri MyAnimeList'e anında yansıt",
                    icon = Icons.Rounded.CloudSync,
                    iconColor = KitsugiColors.AccentBlue,
                    checked = profile.syncEnabledMal,
                    onCheckedChange = profile.onSyncEnabledMalChanged
                )
                KitsugiSettingsDivider()
                KitsugiSettingsItem(
                    title = "MyAnimeList Bağlantısını Kes",
                    description = "Hesabınızı uygulamadan kaldırır",
                    icon = Icons.Rounded.LinkOff,
                    iconColor = KitsugiColors.AccentRed,
                    onClick = profile.onMalAuthClick
                )
            } else {
                KitsugiSettingsItem(
                    title = "MyAnimeList Hesabını Bağla",
                    description = if (isTv) "TV'de QR kod ile giriş yap" else "OAuth ile giriş yapıp listenizi eşitleyin",
                    icon = Icons.Rounded.Link,
                    iconColor = KitsugiColors.AccentBlue,
                    onClick = {
                        if (isTv) showTvQrDialog = true
                        else profile.onMalAuthClick()
                    }
                )
            }
        }
    }

    if (showTvQrDialog) {
        KitsugiTvQrLoginDialog(onDismiss = { showTvQrDialog = false })
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 4. Simkl Alt Sayfası
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun SimklSettingsContent(profile: ProfileSettings) {
    val isTv = LocalIsTv.current
    var showTvQrDialog by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ConnectionStatusCard(
            platformName = "Simkl",
            platformId = "simkl",
            username = profile.simklUsername,
            isConnected = profile.isSimklConnected
        )

        SectionHeader("İşlemler")
        SettingsNavCard {
            if (profile.isSimklConnected) {
                KitsugiSettingsItem(
                    title = "Kütüphaneyi İçe Aktar",
                    description = if (profile.isSimklImportRunning) "Senkronize ediliyor..." else "Listenizi Simkl'dan içe aktarın",
                    icon = Icons.Rounded.Sync,
                    iconColor = KitsugiColors.AccentGreen,
                    onClick = { if (!profile.isSimklImportRunning) profile.onSimklImportClick() }
                )
                KitsugiSettingsDivider()
                KitsugiSettingsSwitchItem(
                    title = "Otomatik Eşitleme",
                    description = "Kitsugi'deki değişiklikleri Simkl'e anında yansıt",
                    icon = Icons.Rounded.CloudSync,
                    iconColor = KitsugiColors.AccentBlue,
                    checked = profile.syncEnabledSimkl,
                    onCheckedChange = profile.onSyncEnabledSimklChanged
                )
                KitsugiSettingsDivider()
                KitsugiSettingsItem(
                    title = "Simkl Bağlantısını Kes",
                    description = "Hesabınızı uygulamadan kaldırır",
                    icon = Icons.Rounded.LinkOff,
                    iconColor = KitsugiColors.AccentRed,
                    onClick = profile.onSimklAuthClick
                )
            } else if (profile.isSimklSessionExpired) {
                KitsugiSettingsItem(
                    title = "Simkl Bağlantısını Yenile (Süresi Doldu)",
                    description = "Oturum süresi doldu. Tekrar giriş yapın.",
                    icon = Icons.Rounded.Warning,
                    iconColor = KitsugiColors.AccentRed,
                    onClick = {
                        if (isTv) showTvQrDialog = true
                        else profile.onSimklAuthClick()
                    }
                )
            } else {
                KitsugiSettingsItem(
                    title = "Simkl Hesabını Bağla",
                    description = if (isTv) "TV'de QR kod ile giriş yap" else "Giriş yap, film, dizi ve animelerinizi eşitleyin",
                    icon = Icons.Rounded.Link,
                    iconColor = KitsugiColors.AccentBlue,
                    onClick = {
                        if (isTv) showTvQrDialog = true
                        else profile.onSimklAuthClick()
                    }
                )
            }
        }
    }

    if (showTvQrDialog) {
        KitsugiTvQrLoginDialog(onDismiss = { showTvQrDialog = false })
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 5. Kitsu Alt Sayfası
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun KitsuSettingsContent(profile: ProfileSettings) {
    var showKitsuLoginDialog by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val kitsuColor = Color(0xFFFD755C)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ConnectionStatusCard(
            platformName = "Kitsu",
            platformId = "kitsu",
            username = profile.kitsuUsername,
            isConnected = profile.isKitsuConnected
        )

        SectionHeader("İşlemler")
        SettingsNavCard {
            if (profile.isKitsuConnected) {
                KitsugiSettingsItem(
                    title = "Kütüphaneyi İçe Aktar",
                    description = if (profile.isKitsuImportRunning) "Senkronize ediliyor..." else "Listenizi Kitsu'dan içe aktarın",
                    icon = Icons.Rounded.Sync,
                    iconColor = KitsugiColors.AccentGreen,
                    onClick = { if (!profile.isKitsuImportRunning) profile.onKitsuImportClick() }
                )
                KitsugiSettingsDivider()
                KitsugiSettingsSwitchItem(
                    title = "Otomatik Eşitleme",
                    description = "Kitsugi'deki değişiklikleri Kitsu'ya anında yansıt",
                    icon = Icons.Rounded.CloudSync,
                    iconColor = kitsuColor,
                    checked = profile.syncEnabledKitsu,
                    onCheckedChange = profile.onSyncEnabledKitsuChanged
                )
                KitsugiSettingsDivider()
                KitsugiSettingsItem(
                    title = "Kitsu Bağlantısını Kes",
                    description = "Hesabınızı uygulamadan kaldırır",
                    icon = Icons.Rounded.LinkOff,
                    iconColor = KitsugiColors.AccentRed,
                    onClick = profile.onKitsuAuthClick
                )
            } else {
                KitsugiSettingsItem(
                    title = "Kitsu Hesabını Bağla",
                    description = "Kullanıcı adı ve şifrenizle giriş yapıp kütüphanenizi senkronize edin",
                    icon = Icons.Rounded.Link,
                    iconColor = kitsuColor,
                    onClick = { showKitsuLoginDialog = true }
                )
            }
        }

        SectionHeader("Kitsu Hakkında")
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = KitsugiColors.Surface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    KitsugiPlatformLogo(platformId = "kitsu", size = 20.dp)
                    Text(
                        text = "Kitsu Entegrasyonu",
                        color = KitsugiColors.TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
                Text(
                    text = "Kitsu Edge API sayesinde anime ve manga kütüphaneniz, detaylı filtreleme, sezon keşfi, favoriler ve harcanan izleme süresi istatistikleriniz kusursuz bir şekilde eşzamanlanır.",
                    color = KitsugiColors.TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
            }
        }
    }

    if (showKitsuLoginDialog) {
        KitsugiKitsuLoginDialog(
            onDismiss = { showKitsuLoginDialog = false },
            onLogin = profile.onLoginKitsu
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 6. Shikimori Alt Sayfası
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun ShikimoriSettingsContent(profile: ProfileSettings) {
    var showShikimoriLoginDialog by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val shikimoriColor = Color(0xFF8E44AD)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ConnectionStatusCard(
            platformName = "Shikimori",
            platformId = "shikimori",
            username = profile.shikimoriUsername,
            isConnected = profile.isShikimoriConnected
        )

        SectionHeader("İşlemler")
        SettingsNavCard {
            if (profile.isShikimoriConnected) {
                KitsugiSettingsItem(
                    title = "Kütüphaneyi İçe Aktar",
                    description = if (profile.isShikimoriImportRunning) "Senkronize ediliyor..." else "Listenizi Shikimori'den içe aktarın",
                    icon = Icons.Rounded.Sync,
                    iconColor = KitsugiColors.AccentGreen,
                    onClick = { if (!profile.isShikimoriImportRunning) profile.onShikimoriImportClick() }
                )
                KitsugiSettingsDivider()
                KitsugiSettingsSwitchItem(
                    title = "Otomatik Eşitleme",
                    description = "Kitsugi'deki değişiklikleri Shikimori'ye anında yansıt",
                    icon = Icons.Rounded.CloudSync,
                    iconColor = shikimoriColor,
                    checked = profile.syncEnabledShikimori,
                    onCheckedChange = profile.onSyncEnabledShikimoriChanged
                )
                KitsugiSettingsDivider()
                KitsugiSettingsItem(
                    title = "Shikimori Bağlantısını Kes",
                    description = "Hesabınızı uygulamadan kaldırır",
                    icon = Icons.Rounded.LinkOff,
                    iconColor = KitsugiColors.AccentRed,
                    onClick = profile.onShikimoriAuthClick
                )
            } else {
                KitsugiSettingsItem(
                    title = "Shikimori Hesabını Bağla",
                    description = "Client kimlik bilgileri veya Yetki Kodu ile giriş yapın",
                    icon = Icons.Rounded.Link,
                    iconColor = shikimoriColor,
                    onClick = { showShikimoriLoginDialog = true }
                )
            }
        }

        SectionHeader("Shikimori Hakkında")
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = KitsugiColors.Surface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    KitsugiPlatformLogo(platformId = "shikimori", size = 20.dp)
                    Text(
                        text = "Shikimori Entegrasyonu",
                        color = KitsugiColors.TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
                Text(
                    text = "Shikimori v2 / v1 REST API ile anime ve manga kullanıcı kayıtları, 1-10 puan dağılımı, aktivite geçmişi ve kapsamlı arama filtreleri desteklenmektedir.",
                    color = KitsugiColors.TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
            }
        }
    }

    if (showShikimoriLoginDialog) {
        KitsugiShikimoriLoginDialog(
            onDismiss = { showShikimoriLoginDialog = false },
            onLogin = profile.onLoginShikimori
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 7. Çapraz Eşitleme (Cross-Sync) Alt Sayfası
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun CrossSyncSettingsContent(profile: ProfileSettings) {
    var showCrossSyncDialog by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    val connectedCount = (if (profile.isAniListConnected) 1 else 0) +
            (if (profile.isMalConnected) 1 else 0) +
            (if (profile.isSimklConnected) 1 else 0) +
            (if (profile.isKitsuConnected) 1 else 0) +
            (if (profile.isShikimoriConnected) 1 else 0)

    val connectedNames = mutableListOf<String>().apply {
        if (profile.isAniListConnected) add("AniList")
        if (profile.isMalConnected) add("MyAnimeList")
        if (profile.isSimklConnected) add("Simkl")
        if (profile.isKitsuConnected) add("Kitsu")
        if (profile.isShikimoriConnected) add("Shikimori")
    }.joinToString(", ")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Bilgilendirme Kartı
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = KitsugiColors.Surface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(KitsugiColors.AccentOrange.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CloudSync,
                            contentDescription = null,
                            tint = KitsugiColors.AccentOrange,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Çapraz Platform Eşitleme",
                            color = KitsugiColors.TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "Bağlı hesaplar: $connectedCount / 5",
                            color = KitsugiColors.TextMuted,
                            fontSize = 12.sp
                        )
                    }
                }
                Text(
                    text = if (connectedCount >= 2)
                        "Bağlı olan $connectedNames hesaplarınızdaki kütüphaneler birbirleriyle iki yönlü eşitlenir. Eksik girişler karşılıklı tamamlanır, asla silme işlemi yapılmaz."
                    else
                        "Çapraz senkronizasyonu kullanabilmek için lütfen en az 2 platform bağlayın (AniList, MyAnimeList, Simkl, Kitsu, Shikimori).",
                    color = KitsugiColors.TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                )
            }
        }

        if (connectedCount >= 2) {
            SectionHeader("Hızlı İşlem")
            SettingsNavCard {
                val syncDesc = when {
                    profile.crossSyncState.isRunning -> "${profile.crossSyncState.currentStep} (%${(profile.crossSyncState.progressPercent * 100).toInt()})"
                    profile.crossSyncState.isCompleted -> "Son eşitleme tamamlandı. Ayrıntılı raporu görmek için dokunun."
                    else -> "Tüm bağlı servisleri tek dokunuşla senkronize edin"
                }

                KitsugiSettingsItem(
                    title = "Tüm Hesapları Birbiriyle Eşitle",
                    description = syncDesc,
                    icon = Icons.Rounded.Cached,
                    iconColor = KitsugiColors.AccentOrange,
                    onClick = {
                        showCrossSyncDialog = true
                        if (!profile.crossSyncState.isRunning) {
                            profile.onCrossSyncClick()
                        }
                    }
                )

                if (profile.crossSyncState.isRunning) {
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { profile.crossSyncState.progressPercent },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = KitsugiColors.AccentOrange,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }

            SectionHeader("Otomatik Eşitleme Tercihleri")
            SettingsNavCard {
                if (profile.isAniListConnected) {
                    KitsugiSettingsSwitchItem(
                        title = "AniList Otomatik Senkronizasyon",
                        description = "Değişiklikleri AniList'e anında aktar",
                        icon = Icons.Rounded.CloudSync,
                        iconColor = KitsugiColors.AccentBlue,
                        checked = profile.syncEnabledAnilist,
                        onCheckedChange = profile.onSyncEnabledAnilistChanged
                    )
                    KitsugiSettingsDivider()
                }
                if (profile.isMalConnected) {
                    KitsugiSettingsSwitchItem(
                        title = "MyAnimeList Otomatik Senkronizasyon",
                        description = "Değişiklikleri MyAnimeList'e anında aktar",
                        icon = Icons.Rounded.CloudSync,
                        iconColor = KitsugiColors.AccentBlue,
                        checked = profile.syncEnabledMal,
                        onCheckedChange = profile.onSyncEnabledMalChanged
                    )
                    KitsugiSettingsDivider()
                }
                if (profile.isSimklConnected) {
                    KitsugiSettingsSwitchItem(
                        title = "Simkl Otomatik Senkronizasyon",
                        description = "Değişiklikleri Simkl'e anında aktar",
                        icon = Icons.Rounded.CloudSync,
                        iconColor = KitsugiColors.AccentBlue,
                        checked = profile.syncEnabledSimkl,
                        onCheckedChange = profile.onSyncEnabledSimklChanged
                    )
                    KitsugiSettingsDivider()
                }
                if (profile.isKitsuConnected) {
                    KitsugiSettingsSwitchItem(
                        title = "Kitsu Otomatik Senkronizasyon",
                        description = "Değişiklikleri Kitsu'ya anında aktar",
                        icon = Icons.Rounded.CloudSync,
                        iconColor = Color(0xFFFD755C),
                        checked = profile.syncEnabledKitsu,
                        onCheckedChange = profile.onSyncEnabledKitsuChanged
                    )
                    KitsugiSettingsDivider()
                }
                if (profile.isShikimoriConnected) {
                    KitsugiSettingsSwitchItem(
                        title = "Shikimori Otomatik Senkronizasyon",
                        description = "Değişiklikleri Shikimori'ye anında aktar",
                        icon = Icons.Rounded.CloudSync,
                        iconColor = Color(0xFF8E44AD),
                        checked = profile.syncEnabledShikimori,
                        onCheckedChange = profile.onSyncEnabledShikimoriChanged
                    )
                }
            }
        }
    }

    if (showCrossSyncDialog) {
        KitsugiCrossSyncDialog(
            state = profile.crossSyncState,
            onDismiss = { showCrossSyncDialog = false }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Yardımcı Ortak Bileşenler
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AccountHubItem(
    title: String,
    subtitle: String,
    platformId: String? = null,
    icon: ImageVector? = null,
    iconColor: Color = LocalKitsugiAccent.current,
    isConnected: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (platformId != null) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(KitsugiColors.SurfaceStrong),
                contentAlignment = Alignment.Center
            ) {
                KitsugiPlatformLogo(platformId = platformId, size = 24.dp)
            }
        } else if (icon != null) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(iconColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = KitsugiColors.TextPrimary,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (isConnected) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(KitsugiColors.AccentGreen)
                    )
                }
                Text(
                    text = subtitle,
                    color = if (isConnected) KitsugiColors.AccentGreen else KitsugiColors.TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = KitsugiColors.TextMuted,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun ConnectionStatusCard(
    platformName: String,
    platformId: String,
    username: String,
    isConnected: Boolean
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = KitsugiColors.Surface),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(KitsugiColors.SurfaceStrong),
                contentAlignment = Alignment.Center
            ) {
                KitsugiPlatformLogo(platformId = platformId, size = 32.dp)
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = platformName,
                    color = KitsugiColors.TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(3.dp))
                if (isConnected && username.isNotBlank()) {
                    Text(
                        text = username,
                        color = KitsugiColors.TextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    Text(
                        text = if (isConnected) "Hesap Bağlı" else "Hesap Bağlı Değil",
                        color = if (isConnected) KitsugiColors.AccentGreen else KitsugiColors.TextMuted,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isConnected) KitsugiColors.AccentGreen.copy(alpha = 0.15f)
                        else KitsugiColors.SurfaceStrong
                    )
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(
                    text = if (isConnected) "Bağlı" else "Bağlı Değil",
                    color = if (isConnected) KitsugiColors.AccentGreen else KitsugiColors.TextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
