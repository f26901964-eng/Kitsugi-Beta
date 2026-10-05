package com.kitsugi.animelist.ui.components.p2p
import com.kitsugi.animelist.ui.components.KitsugiButton

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kitsugi.animelist.core.p2p.P2pSettingsRepository
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent

@Composable
fun P2pConsentDialog(
    onDismiss: () -> Unit,
    onConsentApproved: () -> Unit,
) {
    val accentColor = LocalKitsugiAccent.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.72f))
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 520.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(KitsugiColors.Surface)
                    .border(1.dp, KitsugiColors.Border, RoundedCornerShape(24.dp))
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(KitsugiColors.AccentOrange.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Security,
                            contentDescription = "P2P Güvenlik",
                            tint = KitsugiColors.AccentOrange,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "P2P Torrent Akışı",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = KitsugiColors.TextPrimary
                        )
                        Text(
                            text = "Kullanıcı Sözleşmesi ve Güvenlik Bilgisi",
                            style = MaterialTheme.typography.bodySmall,
                            color = KitsugiColors.TextSecondary
                        )
                    }
                }

                HorizontalDivider(color = KitsugiColors.Border)

                // Scrollable Warning Points
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    P2pDisclaimerItem(
                        icon = Icons.Rounded.Share,
                        title = "Eşler Arası (P2P) Bağlantı",
                        description = "Bu özellik, harici Debrid servislerine gerek kalmadan video akışını doğrudan BitTorrent ağı üzerinden indirerek oynatır."
                    )

                    P2pDisclaimerItem(
                        icon = Icons.Rounded.VpnKey,
                        title = "IP Adresi Görünürlüğü",
                        description = "BitTorrent protokolünün çalışma prensibi gereği IP adresiniz swarm havuzundaki diğer eşler tarafından görülebilir. Tam gizlilik için cihazınızda VPN kullanmanız önerilir."
                    )

                    P2pDisclaimerItem(
                        icon = Icons.Rounded.Security,
                        title = "Yasal Uygunluk & Sorumluluk",
                        description = "Kitsugi hiçbir torrent veya medya içeriği barındırmaz ve kontrol etmez. Eriştiğiniz içeriklerin bulunduğunuz bölgedeki yasal uygunluğu tamamen sizin sorumluluğunuzdadır."
                    )

                    P2pDisclaimerItem(
                        icon = Icons.Rounded.Info,
                        title = "Yükleme (Seed) & Ayarlar",
                        description = "Torrent ağında veri paylaşımını (upload) ve hız limitlerini Ayarlar bölümünden dilediğiniz zaman kapatabilir veya değiştirebilirsiniz."
                    )
                }

                HorizontalDivider(color = KitsugiColors.Border)

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = KitsugiColors.TextSecondary
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, KitsugiColors.Border)
                    ) {
                        Text("Vazgeç", fontWeight = FontWeight.SemiBold)
                    }

                    KitsugiButton(
                        onClick = {
                            P2pSettingsRepository.setConsentGranted(true)
                            P2pSettingsRepository.setP2pEnabled(true)
                            onConsentApproved()
                        },
                        modifier = Modifier.weight(1.3f),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Onayla ve Etkinleştir", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun P2pDisclaimerItem(
    icon: ImageVector,
    title: String,
    description: String,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = KitsugiColors.TextSecondary,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(18.dp)
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                color = KitsugiColors.TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = description,
                color = KitsugiColors.TextSecondary,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
        }
    }
}
