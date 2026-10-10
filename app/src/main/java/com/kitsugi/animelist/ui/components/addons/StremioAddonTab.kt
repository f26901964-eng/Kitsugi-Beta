package com.kitsugi.animelist.ui.components.addons
import com.kitsugi.animelist.ui.components.KitsugiButton

import android.content.Context
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kitsugi.animelist.data.local.ManagedAddonEntity
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.components.KitsugiDropdownMenu
import com.kitsugi.animelist.ui.components.KitsugiDropdownItem
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R

@Composable
internal fun StremioTab(
    addons: List<ManagedAddonEntity>,
    initialDebridToken: String,
    accentColor: Color,
    onAddAddon: (String) -> Unit,
    onToggleAddon: (ManagedAddonEntity, Boolean) -> Unit,
    onDeleteAddon: (ManagedAddonEntity) -> Unit,
    /**
     * NOT: RealDebrid token GİRİŞ ALANI bu sayfadan kaldırıldı (kullanıcı isteği).
     * Kayıtlı token hâlâ [initialDebridToken] üzerinden okunur ve Torrentio önayarı
     * oluşturulurken adrese eklenir; TV arayüzü token girmeye devam eder. Parametre,
     * çağıran ekranların (ayarlar / manga / akış) imzasını bozmamak için tutulur.
     */
    @Suppress("UNUSED_PARAMETER") onSaveDebridToken: (String) -> Unit,
    listState: LazyListState = rememberLazyListState()
) {
    var newManifestUrl by rememberSaveable { mutableStateOf("") }
    // Token yalnızca okunur (giriş alanı kaldırıldı); Torrentio adresinde kullanılır.
    val debridToken = initialDebridToken
    var showP2pConsentDialog by remember { mutableStateOf(false) }

    if (showP2pConsentDialog) {
        com.kitsugi.animelist.ui.components.p2p.P2pConsentDialog(
            onDismiss = { showP2pConsentDialog = false },
            onConsentApproved = { showP2pConsentDialog = false }
        )
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxWidth().fillMaxHeight(),
        contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ── Bilgilendirme şeridi ───────────────────────────────────────────
        item {
            AddonInfoBanner(
                accentColor = accentColor,
                text = "Torrent akışı ve Stremio uyumlu akış eklentilerini bu sekmeden yönetirsiniz."
            )
        }

        // ── P2P Torrent Akışı (kendi bölüm başlığı ve kartı vardır) ─────────
        item {
            com.kitsugi.animelist.ui.components.p2p.P2pSettingsSection(
                onShowConsentDialog = { showP2pConsentDialog = true }
            )
        }

        // ── Oynatma ayarları ───────────────────────────────────────────────
        item {
            AddonSectionCard(
                title = "Oynatma Ayarları",
                subtitle = "Otomatik oynatma ve oynatıcı motoru",
                icon = Icons.Rounded.PlayCircle,
                accentColor = accentColor,
                iconTint = KitsugiColors.AccentBlue
            ) {
                val context = LocalContext.current
                val prefs = remember { context.getSharedPreferences("MyWebViewPrefs", Context.MODE_PRIVATE) }
                var autoplayEnabled by remember { mutableStateOf(prefs.getBoolean("autoplay_enabled", false)) }
                var defaultPlayer by remember { mutableStateOf(prefs.getString("default_player_engine", "exoplayer") ?: "exoplayer") }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text("Otomatik Oynat (Auto-Play)", color = KitsugiColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text("İlk önbellekli akışı otomatik başlatır.", color = KitsugiColors.TextSecondary, fontSize = 12.sp)
                    }
                    Switch(
                        checked = autoplayEnabled,
                        onCheckedChange = { autoplayEnabled = it; prefs.edit().putBoolean("autoplay_enabled", it).apply() },
                        colors = SwitchDefaults.colors(checkedThumbColor = KitsugiColors.Surface, checkedTrackColor = accentColor)
                    )
                }

                HorizontalDivider(color = KitsugiColors.Border.copy(alpha = 0.5f))

                Text("Varsayılan Oynatıcı Motoru", color = KitsugiColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                var dropdownExpanded by remember { mutableStateOf(false) }
                val playerOptions = listOf("exoplayer" to "Dahili Oynatıcı (ExoPlayer)", "mpv" to "Harici Oynatıcı (MPV)", "ask" to "Her Zaman Sor")
                val currentPlayerName = playerOptions.find { it.first == defaultPlayer }?.second ?: "Dahili Oynatıcı (ExoPlayer)"
                Box(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        // Kart zemini zaten SurfaceSoft; bu satır bir ton koyu/açık yüzeyle
                        // ayrışır ve kenarlıkla belirginleşir.
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(KitsugiColors.Surface)
                            .border(1.dp, KitsugiColors.Border.copy(alpha = 0.7f), RoundedCornerShape(16.dp))
                            .tvClickable(shape = RoundedCornerShape(16.dp)) { dropdownExpanded = true }
                            .padding(horizontal = 14.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = currentPlayerName, color = KitsugiColors.TextPrimary, fontSize = 14.sp)
                        Icon(Icons.Rounded.ArrowDropDown, contentDescription = "Seç", tint = KitsugiColors.TextSecondary)
                    }
                    KitsugiDropdownMenu(expanded = dropdownExpanded, onDismissRequest = { dropdownExpanded = false }) {
                        playerOptions.forEach { option ->
                            KitsugiDropdownItem(
                                text = option.second,
                                selected = option.first == defaultPlayer,
                                onClick = { defaultPlayer = option.first; prefs.edit().putString("default_player_engine", option.first).apply(); dropdownExpanded = false }
                            )
                        }
                    }
                }
            }
        }

        // ── Hazır akış eklentileri ─────────────────────────────────────────
        item {
            AddonSectionCard(
                title = "Hazır Akış Eklentileri",
                subtitle = "Tek tıkla kurabileceğiniz güvenilir kaynaklar",
                icon = Icons.Rounded.AutoAwesome,
                accentColor = accentColor,
                iconTint = KitsugiColors.AccentPurple
            ) {
                val torrentioUrl = if (debridToken.isNotBlank()) {
                    "https://torrentio.strem.fun/realdebrid=$debridToken/providers=yts,eztv,rarbg,1337x,thepiratebay,kickasstorrents,torrentproject,torrent9,yts,horriblesubs/manifest.json"
                } else {
                    "https://torrentio.strem.fun/manifest.json"
                }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    // Token bu sayfada girilmez (giriş alanı kaldırıldı); kayıtlı bir debrid
                    // hesabı varsa Torrentio adresine otomatik eklenir.
                    PresetAddonRow("Torrentio (Tavsiye Edilen)", "Ana torrent kaynağı. Kayıtlı debrid hesabınız varsa otomatik bağlanır.", torrentioUrl, addons.any { it.manifestUrl.contains("torrentio") }, accentColor, KitsugiColors.AccentBlue, Icons.Rounded.Download, onAddAddon)
                    PresetAddonRow("Anime Kitsu Kataloğu", "Kitsu üzerinden anime listelerini ve verileri yükler.", "https://anime-kitsu.strem.fun/manifest.json", addons.any { it.manifestUrl.contains("kitsu") }, accentColor, KitsugiColors.AccentPurple, Icons.Rounded.AutoAwesome, onAddAddon)
                    PresetAddonRow("Official Cinemeta", "Film ve dizi meta verilerini çeken resmi katalog eklentisi.", "https://v3-cinemeta.strem.io/manifest.json", addons.any { it.manifestUrl.contains("cinemeta") }, accentColor, KitsugiColors.AccentGreen, Icons.Rounded.Movie, onAddAddon)
                    PresetAddonRow("YouTube Trailers", "Fragmanları ve YouTube videolarını oynatan resmi eklenti.", "https://youtube.strem.io/manifest.json", addons.any { it.manifestUrl.contains("youtube.strem.io") }, accentColor, KitsugiColors.AccentOrange, Icons.Rounded.PlayCircle, onAddAddon)
                }
            }
        }

        // ── Özel eklenti ekle ──────────────────────────────────────────────
        item {
            AddonSectionCard(
                title = "Özel Akış Eklentisi Ekle",
                subtitle = "Manifest adresini yapıştırın",
                icon = Icons.Rounded.AddLink,
                accentColor = accentColor,
                iconTint = KitsugiColors.AccentOrange
            ) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextField(
                        value = newManifestUrl,
                        onValueChange = { newManifestUrl = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        label = { Text("Manifest URL (JSON)") },
                        placeholder = { Text("https://...") },
                        colors = TextFieldDefaults.colors(
                            focusedTextColor = KitsugiColors.TextPrimary, unfocusedTextColor = KitsugiColors.TextPrimary,
                            focusedContainerColor = KitsugiColors.SurfaceSoft, unfocusedContainerColor = KitsugiColors.SurfaceSoft,
                            focusedIndicatorColor = accentColor, unfocusedIndicatorColor = KitsugiColors.Border, cursorColor = accentColor
                        )
                    )
                    KitsugiButton(
                        onClick = { if (newManifestUrl.isNotBlank()) { onAddAddon(newManifestUrl.trim()); newManifestUrl = "" } },
                        shape = RoundedCornerShape(16.dp)
                    ) { Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.ekle), tint = KitsugiColors.Surface) }
                }
            }
        }

        // ── Yüklü eklentiler ───────────────────────────────────────────────
        item {
            AddonListHeader(
                title = "Yüklü Akış Eklentileri",
                count = addons.size,
                accentColor = accentColor
            )
        }

        if (addons.isEmpty()) {
            item {
                AddonEmptyCard(
                    icon = Icons.Rounded.ExtensionOff,
                    text = "Henüz akış eklentisi yüklenmemiş. Yukarıdaki hazır eklentilerden biriyle başlayabilirsiniz."
                )
            }
        } else {
            items(addons) { addon ->
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(KitsugiColors.SurfaceSoft).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(accentColor.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Extension, contentDescription = null, tint = accentColor)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(addon.name, color = KitsugiColors.TextPrimary, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Text(addon.description ?: "Açıklama yok", color = KitsugiColors.TextSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                    }
                    Spacer(Modifier.width(8.dp))
                    Switch(
                        checked = addon.isEnabled,
                        onCheckedChange = { onToggleAddon(addon, it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = KitsugiColors.Surface, checkedTrackColor = accentColor)
                    )
                    Spacer(Modifier.width(4.dp))
                    IconButton(onClick = { onDeleteAddon(addon) }) {
                        Icon(Icons.Rounded.Delete, contentDescription = "Sil", tint = KitsugiColors.AccentRed)
                    }
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════
//  Ortak bölüm bileşenleri (Video Sağlayıcıları / Manga sekmeleriyle aynı dil)
// ══════════════════════════════════════════════════════════════════════════

/**
 * Sekme başındaki kısa bilgilendirme şeridi — kullanıcı bu sekmenin ne yaptığını
 * ilk bakışta görsün diye tek satırlık açıklama + ikon gösterir.
 */
@Composable
internal fun AddonInfoBanner(
    text: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(accentColor.copy(alpha = 0.10f))
            .border(1.dp, accentColor.copy(alpha = 0.22f), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Rounded.Info,
            contentDescription = null,
            tint = accentColor,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = text,
            color = KitsugiColors.TextSecondary,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * Başlık + alt başlık + ikon içeren, gövdesi [content] olan tek tip bölüm kartı.
 * Sekme içindeki tüm ayar grupları bu bileşeni kullanır; böylece kartlar, boşluklar
 * ve tipografi her bölümde aynı görünür.
 */
@Composable
internal fun AddonSectionCard(
    title: String,
    icon: ImageVector,
    accentColor: Color,
    subtitle: String? = null,
    iconTint: Color = accentColor,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(iconTint.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = KitsugiColors.TextPrimary,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        color = KitsugiColors.TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(KitsugiColors.SurfaceSoft)
                .border(1.dp, KitsugiColors.Border.copy(alpha = 0.6f), RoundedCornerShape(18.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}

/** Kart içi küçük açıklama metni. */
@Composable
internal fun AddonHintText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = KitsugiColors.TextMuted,
        style = MaterialTheme.typography.bodySmall,
        modifier = modifier
    )
}

/** Liste bölümlerinin başlığı — sağ tarafta adet rozeti gösterir. */
@Composable
internal fun AddonListHeader(
    title: String,
    count: Int,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            color = KitsugiColors.TextPrimary,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(accentColor.copy(alpha = 0.15f))
                .padding(horizontal = 10.dp, vertical = 3.dp)
        ) {
            Text(
                text = count.toString(),
                color = accentColor,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/** Boş liste durumu — bölüm kartıyla aynı görsel dilde. */
@Composable
internal fun AddonEmptyCard(
    text: String,
    icon: ImageVector = Icons.Rounded.Extension,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(KitsugiColors.SurfaceSoft)
            .border(1.dp, KitsugiColors.Border.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = KitsugiColors.TextMuted,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = text,
            color = KitsugiColors.TextMuted,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun PresetAddonRow(
    title: String,
    subtitle: String,
    manifestUrl: String,
    isInstalled: Boolean,
    accentColor: Color,
    iconColor: Color,
    icon: ImageVector,
    onAddAddon: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(KitsugiColors.Surface)
            .border(1.dp, KitsugiColors.Border.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
            .tvClickable(enabled = !isInstalled, shape = RoundedCornerShape(14.dp)) { onAddAddon(manifestUrl) }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(34.dp).clip(RoundedCornerShape(9.dp)).background(iconColor.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) { Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(18.dp)) }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = KitsugiColors.TextPrimary, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            Text(subtitle, color = KitsugiColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.width(8.dp))
        if (isInstalled) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = KitsugiColors.AccentGreen, modifier = Modifier.size(14.dp))
                Text(
                    text = "Kuruldu",
                    color = KitsugiColors.TextMuted,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        } else {
            Text(
                text = "Kur",
                color = accentColor,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
        }
    }
}
