package com.kitsugi.animelist.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.kitsugi.animelist.BuildConfig
import com.kitsugi.animelist.core.diagnostics.KitsugiCrashLogger
import com.kitsugi.animelist.ui.screens.crash.KitsugiCrashActivity
import com.kitsugi.animelist.ui.theme.KitsugiColors

@Composable
fun KitsugiCrashRecoveryDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val crashLog = remember { KitsugiCrashLogger.readCrashLog(context) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = KitsugiColors.Surface,
            border = BorderStroke(1.dp, KitsugiColors.AccentRed.copy(alpha = 0.6f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // İkon
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(KitsugiColors.SurfaceSoft),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.BugReport,
                        contentDescription = null,
                        tint = KitsugiColors.AccentRed,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(Modifier.height(12.dp))

                Text(
                    text = "Beklenmedik Kapanma Tespit Edildi",
                    color = KitsugiColors.TextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(4.dp))

                Text(
                    text = "Kitsugi son oturumda kapandı veya çöktü. Hata raporu cihazınıza kaydedildi. Paylaşarak sorunun hızlıca çözülmesini sağlayabilirsiniz.",
                    color = KitsugiColors.TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )

                Spacer(Modifier.height(12.dp))

                // Hata Özeti Kutusu
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = KitsugiColors.SurfaceSoft,
                    border = BorderStroke(1.dp, KitsugiColors.Border),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 140.dp)
                ) {
                    Text(
                        text = crashLog.take(1500),
                        color = KitsugiColors.AccentRed.copy(alpha = 0.9f),
                        fontSize = 10.5.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 14.sp,
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(10.dp)
                    )
                }

                Spacer(Modifier.height(16.dp))

                // Butonlar
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Satır 1: Kopyala + Paylaş
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Kitsugi Crash", crashLog))
                                Toast.makeText(context, "Hata raporu panoya kopyalandı ✓", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, KitsugiColors.Border),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = KitsugiColors.TextPrimary),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.ContentCopy, null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Kopyala", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }

                        Button(
                            onClick = {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, crashLog)
                                    putExtra(Intent.EXTRA_SUBJECT, "Kitsugi Hata Raporu v${BuildConfig.VERSION_NAME}")
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Çökme Raporunu Paylaş"))
                                KitsugiCrashLogger.markCrashAsRead(context)
                                onDismiss()
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = KitsugiColors.AccentRed),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.Share, null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Paylaş", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Satır 2: Detaylı Rapor Ekranı
                    OutlinedButton(
                        onClick = {
                            val intent = Intent(context, KitsugiCrashActivity::class.java).apply {
                                putExtra("has_crash", true)
                                putExtra("crash_summary", crashLog.take(4096))
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                            KitsugiCrashLogger.markCrashAsRead(context)
                            onDismiss()
                        },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, KitsugiColors.AccentTeal.copy(alpha = 0.5f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = KitsugiColors.AccentTeal),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.OpenInNew, null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Detaylı Çökme Ekranını Aç", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }

                    // Satır 3: İndirilenlere Kaydet
                    OutlinedButton(
                        onClick = {
                            val file = KitsugiCrashLogger.exportReportToDownloads(context)
                            if (file != null) {
                                Toast.makeText(context, "Rapor kaydedildi: ${file.name} (İndirilenler) ✓", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, "Kaydedilemedi", Toast.LENGTH_SHORT).show()
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, KitsugiColors.Border),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = KitsugiColors.TextSecondary),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.Download, null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("İndirilenler Klasörüne Kaydet (.txt)", fontSize = 11.5.sp)
                    }

                    // Kapat
                    TextButton(
                        onClick = {
                            KitsugiCrashLogger.markCrashAsRead(context)
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Kapat ve Bir Daha Gösterme", color = KitsugiColors.TextMuted, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
