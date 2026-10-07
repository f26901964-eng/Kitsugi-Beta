package com.kitsugi.animelist.ui.components

import android.content.ClipData
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.kitsugi.animelist.data.auth.CrossSyncReportFormatter
import com.kitsugi.animelist.data.auth.CrossSyncReportStore
import com.kitsugi.animelist.model.CrossPlatformStats
import com.kitsugi.animelist.model.CrossSyncLogEntry
import com.kitsugi.animelist.model.CrossSyncProgressState
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class CrossSyncLogFilter { All, Issues, Errors, Warnings }

@Composable
fun KitsugiCrossSyncDialog(
    state: CrossSyncProgressState,
    onDismiss: () -> Unit,
    onCancel: () -> Unit = {},
    onStart: () -> Unit = {}
) {
    val accentColor = LocalKitsugiAccent.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val logsListState = rememberLazyListState()
    val reportSnapshot = remember { mutableStateOf("") }
    var showCancelConfirmation by remember { mutableStateOf(false) }
    var logFilter by remember { mutableStateOf(CrossSyncLogFilter.All) }
    var clockNow by remember { mutableStateOf(System.currentTimeMillis()) }

    LaunchedEffect(state.isRunning, state.startedAt, state.finishedAt) {
        if (state.isRunning) {
            while (true) {
                clockNow = System.currentTimeMillis()
                delay(1_000L)
            }
        } else {
            clockNow = state.finishedAt ?: System.currentTimeMillis()
        }
    }

    val saveReportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null) {
            val report = reportSnapshot.value
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        val output = context.contentResolver.openOutputStream(uri)
                            ?: error("Seçilen dosya açılamadı")
                        output.bufferedWriter(Charsets.UTF_8).use { it.write(report) }
                    }
                    Toast.makeText(context, "Eşitleme raporu dosyaya kaydedildi", Toast.LENGTH_SHORT).show()
                } catch (error: Exception) {
                    Toast.makeText(context, "Rapor kaydedilemedi: ${error.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val elapsedMs = state.startedAt?.let { (clockNow - it).coerceAtLeast(0L) } ?: 0L
    val phaseElapsedMs = state.progressPhaseStartedAt?.let { (clockNow - it).coerceAtLeast(0L) } ?: elapsedMs
    val estimatedRemainingMs = if (state.totalItems > 0 && state.processedItems > 0 && state.isRunning) {
        (phaseElapsedMs.toDouble() / state.processedItems * (state.totalItems - state.processedItems).coerceAtLeast(0)).toLong()
    } else null
    val lastMeaningfulUpdate = state.lastUpdatedAt ?: state.startedAt
    val looksStalled = state.isRunning && lastMeaningfulUpdate != null &&
        clockNow - lastMeaningfulUpdate > STALLED_WARNING_AFTER_MS

    val sourceLogs = if (!state.isRunning && state.reportLogs.isNotEmpty()) state.reportLogs else state.logs
    val visibleLogs = remember(sourceLogs, logFilter) {
        when (logFilter) {
            CrossSyncLogFilter.All -> sourceLogs
            CrossSyncLogFilter.Issues -> sourceLogs.filter { it.isError || it.isWarning }
            CrossSyncLogFilter.Errors -> sourceLogs.filter { it.isError }
            CrossSyncLogFilter.Warnings -> sourceLogs.filter { it.isWarning }
        }
    }

    // En yeni olayı görünür tut; filtre değişince listenin yeni sonuna kaydır.
    LaunchedEffect(visibleLogs.size, visibleLogs.lastOrNull()?.id, logFilter) {
        if (visibleLogs.isNotEmpty()) logsListState.scrollToItem(visibleLogs.lastIndex)
    }

    fun shareReport() {
        val reportState = state
        scope.launch {
            try {
                val file = withContext(Dispatchers.IO) {
                    CrossSyncReportStore.createShareableReport(context, reportState)
                }
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(
                        Intent.EXTRA_SUBJECT,
                        "Kitsugi Çapraz Eşitleme Raporu${if (reportState.isRunning) " (Devam ediyor)" else ""}"
                    )
                    putExtra(
                        Intent.EXTRA_TEXT,
                        "Kitsugi çapraz platform eşitleme tanılama raporu. " +
                            "${if (reportState.isRunning) "Bu, devam eden eşitlemenin kısmi raporudur." else ""}"
                    )
                    clipData = ClipData.newUri(context.contentResolver, file.name, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Eşitleme raporunu paylaş"))
            } catch (error: Exception) {
                Toast.makeText(context, "Rapor paylaşılamadı: ${error.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun saveReport() {
        val reportState = state
        val timestamp = reportState.finishedAt ?: reportState.startedAt ?: System.currentTimeMillis()
        val filename = CrossSyncReportFormatter.fileName(timestamp)
        scope.launch {
            try {
                reportSnapshot.value = withContext(Dispatchers.IO) {
                    CrossSyncReportStore.format(context, reportState)
                }
                saveReportLauncher.launch(filename)
            } catch (error: Exception) {
                Toast.makeText(context, "Rapor hazırlanamadı: ${error.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    KitsugiSheetOrDialog(
        onDismiss = onDismiss,
        heightFraction = 0.94f,
        fullScreen = false
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val infiniteTransition = rememberInfiniteTransition(label = "sync_spin")
                    val rotation by infiniteTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = 360f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1200, easing = LinearEasing),
                            repeatMode = RepeatMode.Restart
                        ),
                        label = "spin_angle"
                    )
                    val statusColor = when {
                        state.isCompleted -> KitsugiColors.AccentGreen
                        state.errorMessage != null -> ERROR_COLOR
                        state.isRunning -> accentColor
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }

                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(statusColor.copy(alpha = 0.17f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when {
                                state.isCompleted -> Icons.Rounded.CheckCircle
                                state.errorMessage != null -> Icons.Rounded.ErrorOutline
                                else -> Icons.Rounded.Sync
                            },
                            contentDescription = null,
                            tint = statusColor,
                            modifier = Modifier
                                .size(22.dp)
                                .rotate(if (state.isRunning) rotation else 0f)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Çok Yönlü Eşitleme",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = when {
                                state.isCompleted -> "Tamamlandı${if (state.issueCount > 0) " · ${state.issueCount} sorun" else ""}"
                                state.isRunning -> "${formatDuration(elapsedMs)} geçti · ${state.currentStep.ifBlank { "Hazırlanıyor" }}"
                                state.errorMessage != null -> "Hata oluştu · rapor hazır"
                                state.currentStep == "Eşitleme durduruldu" -> "Durduruldu · kısmi rapor hazır"
                                else -> "Son eşitleme durumu"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = statusColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Kapat",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.24f))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = state.currentStep.ifBlank { "İşlem hazırlanıyor..." },
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (state.currentDetail.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = state.currentDetail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.height(9.dp))

                    if (state.isRunning && state.totalItems <= 0) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = accentColor,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    } else {
                        LinearProgressIndicator(
                            progress = { if (state.isCompleted) 1f else state.progressPercent },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = if (state.isCompleted) KitsugiColors.AccentGreen else accentColor,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(7.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (state.totalItems > 0 || state.isCompleted) "%${(state.progressPercent * 100).toInt()}" else "İşlem sürüyor",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = accentColor
                        )
                        Text(
                            text = if (state.totalItems > 0) {
                                "${state.processedItems} / ${state.totalItems} ${state.progressUnit}"
                            } else {
                                "Ağ / hazırlık aşaması"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (state.isRunning) {
                        Spacer(modifier = Modifier.height(5.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Geçen: ${formatDuration(elapsedMs)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (estimatedRemainingMs != null) {
                                Text(
                                    text = "Tahmini kalan: ${formatDuration(estimatedRemainingMs)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            if (looksStalled) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = WARNING_COLOR.copy(alpha = 0.1f),
                    border = BorderStroke(1.dp, WARNING_COLOR.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Rounded.WarningAmber, contentDescription = null, tint = WARNING_COLOR, modifier = Modifier.size(18.dp))
                        Text(
                            "İlerleme 90 saniyedir güncellenmedi. Kısmi raporu paylaşabilir veya eşitlemeyi durdurabilirsiniz.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            if (!state.errorMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = ERROR_COLOR.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, ERROR_COLOR.copy(alpha = 0.3f))
                ) {
                    Text(
                        text = "Hata: ${state.errorMessage}",
                        modifier = Modifier.padding(10.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = ERROR_COLOR
                    )
                }
            }

            Spacer(modifier = Modifier.height(9.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = KitsugiColors.AccentGreen.copy(alpha = 0.08f),
                border = BorderStroke(1.dp, KitsugiColors.AccentGreen.copy(alpha = 0.25f))
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Security,
                        contentDescription = null,
                        tint = KitsugiColors.AccentGreen,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Veri silinmez; yalnızca güvenli eşleşmeler eklenir/güncellenir. Rapor başlık ve platform kimliklerini içerir; erişim anahtarları otomatik gizlenir.",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Bağlı Hesap İstatistikleri",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${state.platformStats.size} hesap · ${state.totalEventCount} olay · ${state.issueCount} sorun",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            if (state.platformStats.isEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(9.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                ) {
                    Text(
                        "Bağlı ve eşitlemesi açık hesaplar belirleniyor...",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    state.platformStats.values.forEach { stats ->
                        PlatformMiniCard(stats = stats, modifier = Modifier.width(112.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (state.isRunning) "Canlı İşlem Günlüğü" else "Eşitleme İşlem Günlüğü",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${visibleLogs.size} gösteriliyor / ${state.totalEventCount} toplam",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                CrossSyncLogFilter.entries.forEach { filter ->
                    val label = when (filter) {
                        CrossSyncLogFilter.All -> "Tümü"
                        CrossSyncLogFilter.Issues -> "Sorunlar"
                        CrossSyncLogFilter.Errors -> "Hatalar"
                        CrossSyncLogFilter.Warnings -> "Uyarılar"
                    }
                    FilterChip(
                        selected = logFilter == filter,
                        onClick = { logFilter = filter },
                        label = { Text(label, fontSize = 11.sp) }
                    )
                }
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            ) {
                if (visibleLogs.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = when {
                                sourceLogs.isEmpty() -> "Henüz kayıt yok. Eşitleme adımları burada görünecek."
                                else -> "Bu filtrede gösterilecek olay yok."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        state = logsListState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(7.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        items(visibleLogs, key = { it.id }) { log ->
                            LogItemRow(log = log)
                        }
                    }
                }
            }

            if (state.reportSavedTo != null) {
                Spacer(modifier = Modifier.height(5.dp))
                Text(
                    text = "Otomatik rapor: ${state.reportSavedTo}",
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            if (state.isRunning) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = ::shareReport,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(modifier = Modifier.width(5.dp))
                        Text("Kısmi Raporu Paylaş", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    OutlinedButton(
                        onClick = { showCancelConfirmation = true },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(modifier = Modifier.width(5.dp))
                        Text("Eşitlemeyi Durdur", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Arka Planda Devam Et")
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    OutlinedButton(
                        onClick = ::saveReport,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        shape = RoundedCornerShape(10.dp),
                        enabled = state.totalEventCount > 0 || state.logs.isNotEmpty() || state.errorMessage != null
                    ) {
                        Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Dosyaya Kaydet", maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 11.sp)
                    }
                    OutlinedButton(
                        onClick = ::shareReport,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        shape = RoundedCornerShape(10.dp),
                        enabled = state.totalEventCount > 0 || state.logs.isNotEmpty() || state.errorMessage != null
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Raporu Paylaş", maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 11.sp)
                    }
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(0.8f)
                            .height(44.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Kapat", maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 11.sp)
                    }
                }
                if (state.startedAt != null) {
                    TextButton(
                        onClick = onStart,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Yeni Eşitleme Başlat")
                    }
                }
            }
        }
    }

    if (showCancelConfirmation) {
        AlertDialog(
            onDismissRequest = { showCancelConfirmation = false },
            title = { Text("Eşitleme durdurulsun mu?") },
            text = {
                Text(
                    "Tamamlanmış uzak sunucu güncellemeleri geri alınamaz. İşlem durdurulur ve şimdiye kadarki günlükle kısmi rapor saklanır."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCancelConfirmation = false
                        onCancel()
                    }
                ) {
                    Text("Durdur", color = ERROR_COLOR)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelConfirmation = false }) {
                    Text("Devam Et")
                }
            }
        )
    }
}

@Composable
private fun PlatformMiniCard(
    modifier: Modifier = Modifier,
    stats: CrossPlatformStats
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(9.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = stats.platformName,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${stats.initialCount} başlangıç kaydı",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (stats.addedCount > 0 || stats.updatedCount > 0) {
                Text(
                    text = "+${stats.addedCount} eklendi · ~${stats.updatedCount} güncellendi",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                    color = Color(0xFF42A5F5),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (stats.skippedCount > 0 || stats.errorCount > 0) {
                Text(
                    text = "${stats.skippedCount} atlandı · ${stats.errorCount} hata",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                    color = if (stats.errorCount > 0) ERROR_COLOR else WARNING_COLOR,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (stats.addedCount == 0 && stats.updatedCount == 0 && stats.skippedCount == 0 && stats.errorCount == 0) {
                Text(
                    text = if (stats.initialCount > 0) "Değişiklik bekleniyor" else "Kayıt yok",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun LogItemRow(log: CrossSyncLogEntry) {
    var expanded by remember(log.id) { mutableStateOf(false) }
    val tagColor = when {
        log.isError -> ERROR_COLOR
        log.isWarning -> WARNING_COLOR
        log.isAddition -> KitsugiColors.AccentGreen
        log.isUpdate -> Color(0xFF42A5F5)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val icon = when {
        log.isError || log.isWarning -> Icons.Rounded.ErrorOutline
        log.isAddition -> Icons.Rounded.AddCircleOutline
        log.isUpdate -> Icons.Rounded.Edit
        else -> null
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { expanded = !expanded },
        shape = RoundedCornerShape(8.dp),
        color = if (log.isError) ERROR_COLOR.copy(alpha = 0.07f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (icon != null) {
                    Icon(icon, contentDescription = null, tint = tagColor, modifier = Modifier.size(14.dp))
                }
                Text(
                    text = log.platform,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
                    color = tagColor,
                    maxLines = 1,
                    modifier = Modifier.width(74.dp),
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = log.message,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = if (log.isError || log.isWarning) tagColor else MaterialTheme.colorScheme.onSurface,
                    maxLines = if (expanded) 8 else 2,
                    modifier = Modifier.weight(1f),
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = timeOfDay(log.timestamp),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (expanded && !log.details.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(5.dp))
                Text(
                    text = log.details,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 14,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (expanded && log.details.isNullOrBlank()) {
                Text(
                    text = "Bu olay için ek teknik ayrıntı yok.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun formatDuration(milliseconds: Long): String {
    val totalSeconds = (milliseconds.coerceAtLeast(0L) / 1_000L)
    val hours = totalSeconds / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%02d:%02d".format(minutes, seconds)
}

private fun timeOfDay(timestamp: Long): String =
    SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestamp))

private const val STALLED_WARNING_AFTER_MS = 90_000L
private val ERROR_COLOR = Color(0xFFEF5350)
private val WARNING_COLOR = Color(0xFFFFA726)
