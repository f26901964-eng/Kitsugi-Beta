package com.kitsugi.animelist.ui.components.p2p
import com.kitsugi.animelist.ui.components.KitsugiButton

import android.widget.Toast
import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.Switch
import com.kitsugi.animelist.ui.theme.gradient.Text
import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.theme.gradient.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kitsugi.animelist.core.p2p.*
import com.kitsugi.animelist.ui.components.KitsugiDropdownItem
import com.kitsugi.animelist.ui.components.KitsugiDropdownMenu
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R

@Composable
fun P2pSettingsSection(
    onShowConsentDialog: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val accentColor = LocalKitsugiAccent.current

    val p2pState by P2pSettingsRepository.uiState.collectAsState()
    val cacheState by P2pStreamingEngine.cacheState.collectAsState()

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Section Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(accentColor.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.CloudSync,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column {
                Text(
                    text = stringResource(R.string.p2p_torrent_akisi),
                    color = KitsugiColors.TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.inline_free_torrent_streaming_without_a_ac6e8f8),
                    color = KitsugiColors.TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        // Card Container
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(KitsugiColors.SurfaceSoft)
                .border(1.dp, KitsugiColors.Border.copy(alpha = 0.55f), RoundedCornerShape(18.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp)
        ) {
            // 1. P2P Aç / Kapa
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                        text = stringResource(R.string.inline_enable_p2p_streaming_4236ac6),
                        color = KitsugiColors.TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.inline_resolves_and_plays_torrent_links_646789d),
                        color = KitsugiColors.TextSecondary,
                        fontSize = 12.sp
                    )
                }
                Switch(
                    checked = p2pState.p2pEnabled,
                    onCheckedChange = { enable ->
                        if (enable) {
                            if (!p2pState.consentGranted) {
                                onShowConsentDialog()
                            } else {
                                P2pSettingsRepository.setP2pEnabled(true)
                            }
                        } else {
                            P2pSettingsRepository.setP2pEnabled(false)
                        }
                    }
                )
            }

            if (p2pState.p2pEnabled) {
                HorizontalDivider(color = KitsugiColors.Border.copy(alpha = 0.5f))

                // 2. İstatistikleri Gizle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            text = stringResource(R.string.inline_hide_torrent_statistics_9d04d4b),
                            color = KitsugiColors.TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = stringResource(R.string.inline_hide_speed_seed_and_peer_5dcadeb),
                            color = KitsugiColors.TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                    Switch(
                        checked = p2pState.hideTorrentStats,
                        onCheckedChange = { P2pSettingsRepository.setHideTorrentStats(it) }
                    )
                }

                HorizontalDivider(color = KitsugiColors.Border.copy(alpha = 0.5f))

                // 3. Yükleme (Seed) İzni
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            text = stringResource(R.string.inline_allow_uploading_seeding_f95c4ae),
                            color = KitsugiColors.TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = if (p2pState.enableUpload) stringResource(R.string.ui_p2p_upload_enabled) else stringResource(R.string.ui_p2p_download_only),
                            color = if (p2pState.enableUpload) accentColor else KitsugiColors.TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                    Switch(
                        checked = p2pState.enableUpload,
                        onCheckedChange = { P2pSettingsRepository.setEnableUpload(it) }
                    )
                }

                // 4. Yükleme Hız Limiti (Sadece yükleme açıksa)
                if (p2pState.enableUpload) {
                    var uploadDropdownExpanded by remember { mutableStateOf(false) }
                    val uploadLimitOptions = listOf(
                        0L to stringResource(R.string.sinirsiz_yukleme),
                        500L to "500 KB/s",
                        1000L to "1 MB/s",
                        2000L to "2 MB/s",
                        5000L to "5 MB/s",
                    )
                    val currentUploadLabel = uploadLimitOptions.firstOrNull { it.first == p2pState.uploadLimitKbps }?.second ?: "Sınırsız Yükleme"

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = stringResource(R.string.inline_upload_speed_limit_332ad8b),
                            color = KitsugiColors.TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Box(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(KitsugiColors.Surface)
                                    .border(1.dp, KitsugiColors.Border.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
                                    .clickable { uploadDropdownExpanded = true }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(currentUploadLabel, color = KitsugiColors.TextPrimary, fontSize = 13.sp)
                                Icon(Icons.Rounded.ArrowDropDown, contentDescription = null, tint = KitsugiColors.TextSecondary)
                            }
                            KitsugiDropdownMenu(
                                expanded = uploadDropdownExpanded,
                                onDismissRequest = { uploadDropdownExpanded = false }
                            ) {
                                uploadLimitOptions.forEach { (limit, label) ->
                                    KitsugiDropdownItem(
                                        text = label,
                                        selected = limit == p2pState.uploadLimitKbps,
                                        onClick = {
                                            P2pSettingsRepository.setUploadLimitKbps(limit)
                                            uploadDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = KitsugiColors.Border.copy(alpha = 0.5f))

                // 5. Torrent Profili
                var profileDropdownExpanded by remember { mutableStateOf(false) }
                val profileOptions = listOf(
                    P2pTorrentProfile.BALANCED to (stringResource(R.string.dengeli_onerilen) to stringResource(R.string.hizli_baslangic_ve_kararli_baglanti_dengesi)),
                    P2pTorrentProfile.SOFT to ("Hafif (Soft)" to "Düşük pil ve arka plan veri tüketimi"),
                    P2pTorrentProfile.FAST to ("Hızlı (Fast)" to "Yüksek eş sayısı ve agresif indirme")
                )
                val currentProfileLabel = profileOptions.firstOrNull { it.first == p2pState.torrentProfile }?.second?.first ?: "Dengeli"

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Torrent Profili",
                        color = KitsugiColors.TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(KitsugiColors.Surface)
                                .border(1.dp, KitsugiColors.Border.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
                                .clickable { profileDropdownExpanded = true }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(currentProfileLabel, color = KitsugiColors.TextPrimary, fontSize = 13.sp)
                            Icon(Icons.Rounded.ArrowDropDown, contentDescription = null, tint = KitsugiColors.TextSecondary)
                        }
                        KitsugiDropdownMenu(
                            expanded = profileDropdownExpanded,
                            onDismissRequest = { profileDropdownExpanded = false }
                        ) {
                            profileOptions.forEach { (profile, info) ->
                                KitsugiDropdownItem(
                                    text = "${info.first} — ${info.second}",
                                    selected = profile == p2pState.torrentProfile,
                                    onClick = {
                                        P2pSettingsRepository.setTorrentProfile(profile)
                                        profileDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = KitsugiColors.Border.copy(alpha = 0.5f))

                // 6. Önbellek Boyutu
                var cacheDropdownExpanded by remember { mutableStateOf(false) }
                val cacheOptions = listOf(
                    P2pCacheSize.GB_2 to stringResource(R.string.onerilen_2_gb),
                    P2pCacheSize.GB_5 to "5 GB",
                    P2pCacheSize.GB_10 to "10 GB",
                    P2pCacheSize.NONE to "Kalıcı Önbellek Yok"
                )
                val currentCacheLabel = cacheOptions.firstOrNull { it.first == p2pState.cacheSize }?.second ?: "2 GB"

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(R.string.inline_torrent_disk_cache_size_a8b3881),
                        color = KitsugiColors.TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(KitsugiColors.Surface)
                                .border(1.dp, KitsugiColors.Border.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
                                .clickable { cacheDropdownExpanded = true }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(currentCacheLabel, color = KitsugiColors.TextPrimary, fontSize = 13.sp)
                            Icon(Icons.Rounded.ArrowDropDown, contentDescription = null, tint = KitsugiColors.TextSecondary)
                        }
                        KitsugiDropdownMenu(
                            expanded = cacheDropdownExpanded,
                            onDismissRequest = { cacheDropdownExpanded = false }
                        ) {
                            cacheOptions.forEach { (size, label) ->
                                KitsugiDropdownItem(
                                    text = label,
                                    selected = size == p2pState.cacheSize,
                                    onClick = {
                                        P2pSettingsRepository.setCacheSize(size)
                                        cacheDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = KitsugiColors.Border.copy(alpha = 0.5f))

                // 7. Önbelleği Temizle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            text = stringResource(R.string.inline_clear_torrent_cache_7e9e815),
                            color = KitsugiColors.TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        val usageText = if (cacheState.hasMeasurement) {
                            "${formatP2pBytes(cacheState.usedBytes)} önbellek kullanılıyor"
                        } else {
                            "P2P başlatıldıktan sonra kullanım ölçülür"
                        }
                        Text(
                            text = usageText,
                            color = KitsugiColors.TextSecondary,
                            fontSize = 12.sp
                        )
                    }

                    KitsugiButton(
                        onClick = {
                            scope.launch {
                                try {
                                    val res = P2pStreamingEngine.clearCache()
                                    Toast.makeText(
                                        context,
                                        "${formatP2pBytes(res.reclaimedBytes)} torrent önbelleği temizlendi",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } catch (e: Exception) {
                                    Toast.makeText(
                                        context,
                                        "Önbellek temizlenemedi: ${e.message}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        },
                        enabled = !cacheState.isClearing,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (cacheState.isClearing) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = accentColor, strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Rounded.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.temizle), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
