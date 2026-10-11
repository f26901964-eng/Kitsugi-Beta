package com.kitsugi.animelist.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.R
import com.kitsugi.animelist.ui.components.KitsugiSettingsDivider
import com.kitsugi.animelist.ui.components.KitsugiSettingsItem
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.Text
import java.util.Locale

/**
 * Ayarlar genelinde arama kaydı. Bir kayıt; bir ayar sayfasını (ve isteğe bağlı olarak
 * içindeki bölümü) temsil eder. [keywords] başlıkta/açıklamada geçmeyen eş anlamlı
 * terimleri (ör. "tmdb", "dns", "altyazı") kapsar.
 */
internal data class SettingsSearchEntry(
    val title: String,
    val description: String,
    val keywords: String,
    val breadcrumb: String,
    val icon: ImageVector,
    val iconColor: Color,
    val onClick: () -> Unit
)

/** Türkçe büyük/küçük harf kurallarına uygun normalizasyon (I → ı, İ → i). */
private fun normalizeForSearch(text: String): String =
    text.lowercase(Locale.forLanguageTag("tr"))
        .replace("ı", "i")
        .replace("ş", "s")
        .replace("ğ", "g")
        .replace("ü", "u")
        .replace("ö", "o")
        .replace("ç", "c")

/** Sorgunun tüm kelimeleri kayıtta (başlık, açıklama, anahtar kelimeler) geçmeli. */
internal fun SettingsSearchEntry.matches(query: String): Boolean {
    val tokens = normalizeForSearch(query).trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (tokens.isEmpty()) return true
    val haystack = normalizeForSearch("$title $description $keywords $breadcrumb")
    return tokens.all { haystack.contains(it) }
}

/**
 * Tüm ayar sayfalarının dizini. Ana menüdeki sayfalar + iç sekmeler (TMDB, DNS, ...).
 * Her kayıt doğru alt sayfaya yönlendirir.
 */
@Composable
internal fun rememberSettingsSearchEntries(
    params: SettingsScreenParameters,
    onNavigate: (SettingsRoute) -> Unit
): List<SettingsSearchEntry> {
    val accent = LocalKitsugiAccent.current
    val accountBc = stringResource(R.string.settings_section_account)
    val appBc = stringResource(R.string.settings_section_app)
    val sourcesBc = stringResource(R.string.settings_section_sources)
    val dataBc = stringResource(R.string.settings_section_data)
    val supportBc = stringResource(R.string.settings_section_support)

    return remember(params, accent, onNavigate) {
        listOf(
            SettingsSearchEntry(
                title = "Kitsugi Hesabı",
                description = "Giriş yap, arama geçmişini cihazlar arasında eşitle",
                keywords = "hesap giriş login sync senkron",
                breadcrumb = accountBc,
                icon = Icons.Rounded.AccountCircle,
                iconColor = KitsugiColors.AccentGreen,
                onClick = { onNavigate(SettingsRoute.KitsugiAccount) }
            ),
            SettingsSearchEntry(
                title = "Hesap Bağlantıları",
                description = "AniList, MyAnimeList, Simkl, Kitsu ve Shikimori hesap eşitlemeleri",
                keywords = "anilist mal myanimelist simkl kitsu shikimori bangumi oauth bağlan",
                breadcrumb = accountBc,
                icon = Icons.Rounded.Person,
                iconColor = KitsugiColors.AccentBlue,
                onClick = { onNavigate(SettingsRoute.AccountConnections) }
            ),
            SettingsSearchEntry(
                title = "AniList",
                description = "AniList hesap ayarları",
                keywords = "anilist bildirim",
                breadcrumb = "Hesap Bağlantıları",
                icon = Icons.Rounded.Person,
                iconColor = KitsugiColors.AccentBlue,
                onClick = { onNavigate(SettingsRoute.AniListSettings) }
            ),
            SettingsSearchEntry(
                title = "MyAnimeList",
                description = "MyAnimeList hesap ayarları",
                keywords = "mal myanimelist bildirim",
                breadcrumb = "Hesap Bağlantıları",
                icon = Icons.Rounded.Person,
                iconColor = KitsugiColors.AccentBlue,
                onClick = { onNavigate(SettingsRoute.MalSettings) }
            ),
            SettingsSearchEntry(
                title = "Simkl",
                description = "Simkl hesap ayarları",
                keywords = "simkl bildirim",
                breadcrumb = "Hesap Bağlantıları",
                icon = Icons.Rounded.Person,
                iconColor = KitsugiColors.AccentBlue,
                onClick = { onNavigate(SettingsRoute.SimklSettings) }
            ),
            SettingsSearchEntry(
                title = "Kitsu",
                description = "Kitsu hesap ayarları",
                keywords = "kitsu bildirim",
                breadcrumb = "Hesap Bağlantıları",
                icon = Icons.Rounded.Person,
                iconColor = KitsugiColors.AccentBlue,
                onClick = { onNavigate(SettingsRoute.KitsuSettings) }
            ),
            SettingsSearchEntry(
                title = "Shikimori",
                description = "Shikimori hesap ayarları",
                keywords = "shikimori bildirim",
                breadcrumb = "Hesap Bağlantıları",
                icon = Icons.Rounded.Person,
                iconColor = KitsugiColors.AccentBlue,
                onClick = { onNavigate(SettingsRoute.ShikimoriSettings) }
            ),
            SettingsSearchEntry(
                title = "Bangumi",
                description = "Bangumi hesap ayarları",
                keywords = "bangumi",
                breadcrumb = "Hesap Bağlantıları",
                icon = Icons.Rounded.Person,
                iconColor = KitsugiColors.AccentBlue,
                onClick = { onNavigate(SettingsRoute.BangumiSettings) }
            ),
            SettingsSearchEntry(
                title = stringResource(R.string.settings_cross_sync),
                description = "Platformlar arası liste eşitleme",
                keywords = "cross sync eşitleme senkron",
                breadcrumb = "Hesap Bağlantıları",
                icon = Icons.Rounded.Sync,
                iconColor = KitsugiColors.AccentBlue,
                onClick = { onNavigate(SettingsRoute.CrossSyncSettings) }
            ),
            SettingsSearchEntry(
                title = stringResource(R.string.settings_pref_title),
                description = stringResource(R.string.settings_pref_desc),
                keywords = "tema karanlık amoled renk vurgu kart çerçeve dil başlık puan skor liste " +
                    "manga okuyucu bildirim splash açılış ekran navigasyon alt bar +18 yetişkin " +
                    "çeviri translate tmdb dil varsayılan sekme",
                breadcrumb = appBc,
                icon = Icons.Rounded.Palette,
                iconColor = accent,
                onClick = { onNavigate(SettingsRoute.AppearancePreferences) }
            ),
            SettingsSearchEntry(
                title = stringResource(R.string.settings_player_title),
                description = stringResource(R.string.settings_player_desc),
                keywords = "oynatıcı player altyazı subtitle ses audio codec mpv hwdec jest hareket " +
                    "intro atlama kısayol buton lua conf",
                breadcrumb = appBc,
                icon = Icons.Rounded.PlayCircle,
                iconColor = KitsugiColors.AccentOrange,
                onClick = { onNavigate(SettingsRoute.PlayerSettings) }
            ),
            SettingsSearchEntry(
                title = stringResource(R.string.settings_watch_history),
                description = stringResource(R.string.settings_watch_history_desc),
                keywords = "izleme geçmişi history devam et",
                breadcrumb = appBc,
                icon = Icons.Rounded.History,
                iconColor = KitsugiColors.AccentTeal,
                onClick = { params.onOpenWatchHistory?.invoke() }
            ),
            SettingsSearchEntry(
                title = stringResource(R.string.settings_addons_streams),
                description = stringResource(R.string.settings_addons_streams_desc),
                keywords = "eklenti addon torrent akış stream kaynak manga cloudstream plugin tanılama",
                breadcrumb = sourcesBc,
                icon = Icons.Rounded.Extension,
                iconColor = KitsugiColors.AccentPurple,
                onClick = { onNavigate(SettingsRoute.AddonsExtensions) }
            ),
            SettingsSearchEntry(
                title = stringResource(R.string.settings_integrations),
                description = stringResource(R.string.settings_integrations_desc),
                keywords = "entegrasyon tmdb api anahtar mdblist fanart fanart.tv aniskip skip intro outro " +
                    "animeskip",
                breadcrumb = sourcesBc,
                icon = Icons.Rounded.Hub,
                iconColor = KitsugiColors.AccentBlue,
                onClick = { onNavigate(SettingsRoute.Integrations) }
            ),
            SettingsSearchEntry(
                title = stringResource(R.string.platform_error_catalog_open),
                description = stringResource(R.string.platform_error_catalog_desc),
                keywords = "hata kodu error kod ktS-csp katalog",
                breadcrumb = sourcesBc,
                icon = Icons.Rounded.Info,
                iconColor = KitsugiColors.AccentOrange,
                onClick = { onNavigate(SettingsRoute.PlatformErrorCodes) }
            ),
            SettingsSearchEntry(
                title = stringResource(R.string.settings_data_backup),
                description = stringResource(R.string.settings_data_backup_desc),
                keywords = "yedek backup json dışa aktar içe aktar sil tüm liste geliştirici logcat " +
                    "dns doh google cloudflare adguard dns.watch",
                breadcrumb = dataBc,
                icon = Icons.Rounded.Storage,
                iconColor = KitsugiColors.AccentGreen,
                onClick = { onNavigate(SettingsRoute.DataBackup) }
            ),
            SettingsSearchEntry(
                title = stringResource(R.string.storage_title),
                description = stringResource(R.string.storage_menu_desc),
                keywords = "depolama önbellek cache temizle yer disk görsel altyazı veritabanı",
                breadcrumb = dataBc,
                icon = Icons.Rounded.CleaningServices,
                iconColor = KitsugiColors.AccentTeal,
                onClick = { onNavigate(SettingsRoute.Storage) }
            ),
            SettingsSearchEntry(
                title = stringResource(R.string.settings_download_settings),
                description = stringResource(R.string.settings_download_settings_desc),
                keywords = "indirme download wifi harici indirici hız sınırı cbz konum klasör",
                breadcrumb = dataBc,
                icon = Icons.Rounded.Tune,
                iconColor = KitsugiColors.AccentGreen,
                onClick = { onNavigate(SettingsRoute.Downloads) }
            ),
            SettingsSearchEntry(
                title = stringResource(R.string.settings_downloads),
                description = stringResource(R.string.settings_downloads_desc),
                keywords = "indirilenler videolar altyazılar resimler dosya",
                breadcrumb = dataBc,
                icon = Icons.Rounded.Download,
                iconColor = KitsugiColors.AccentGreen,
                onClick = { params.onOpenDownloads?.invoke() }
            ),
            SettingsSearchEntry(
                title = stringResource(R.string.settings_about),
                description = "Sürüm ve açık kaynak bilgisi",
                keywords = "hakkında sürüm güncelleme update github",
                breadcrumb = supportBc,
                icon = Icons.Rounded.Info,
                iconColor = KitsugiColors.AccentIndigo,
                onClick = { (params.onOpenAbout ?: { params.integrations.onOpenAbout() }).invoke() }
            ),
            SettingsSearchEntry(
                title = stringResource(R.string.settings_feedback),
                description = stringResource(R.string.settings_feedback_desc),
                keywords = "geri bildirim hata bildirimi öneri feedback bug",
                breadcrumb = supportBc,
                icon = Icons.Rounded.Feedback,
                iconColor = KitsugiColors.AccentBlue,
                onClick = { onNavigate(SettingsRoute.Feedback) }
            )
        )
    }
}

/** Ayarlar üst çubuğundaki arama alanı. */
@Composable
internal fun SettingsSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = LocalKitsugiAccent.current
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        singleLine = true,
        shape = RoundedCornerShape(20.dp),
        placeholder = {
            Text(
                text = "Ayarlarda ara (ör. tmdb, altyazı, dns)",
                color = KitsugiColors.TextMuted,
                style = MaterialTheme.typography.bodyMedium
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                tint = KitsugiColors.TextSecondary
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Temizle",
                        tint = KitsugiColors.TextSecondary
                    )
                }
            }
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = KitsugiColors.Surface,
            unfocusedContainerColor = KitsugiColors.Surface,
            focusedBorderColor = accent,
            unfocusedBorderColor = KitsugiColors.Border,
            focusedTextColor = KitsugiColors.TextPrimary,
            unfocusedTextColor = KitsugiColors.TextPrimary,
            cursorColor = accent
        )
    )
}

/** Arama sonuçları listesi. Eşleşme yoksa boş durum gösterir. */
@Composable
internal fun SettingsSearchResults(
    query: String,
    entries: List<SettingsSearchEntry>,
    modifier: Modifier = Modifier
) {
    val results = remember(query, entries) { entries.filter { it.matches(query) } }

    if (results.isEmpty()) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.SearchOff,
                contentDescription = null,
                tint = KitsugiColors.TextMuted,
                modifier = Modifier.size(48.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "\"$query\" için sonuç bulunamadı",
                color = KitsugiColors.TextSecondary,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
    ) {
        item {
            Text(
                text = "${results.size} sonuç",
                color = KitsugiColors.TextMuted,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            )
        }
        items(results.size) { index ->
            val entry = results[index]
            Column(modifier = Modifier.fillMaxWidth()) {
                KitsugiSettingsItem(
                    title = entry.title,
                    description = "${entry.breadcrumb} • ${entry.description}",
                    icon = entry.icon,
                    iconColor = entry.iconColor,
                    onClick = entry.onClick
                )
                if (index != results.lastIndex) KitsugiSettingsDivider()
            }
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}
