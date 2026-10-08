package com.kitsugi.animelist.ui.components

import android.content.ClipData
import android.content.Intent
import android.content.res.Configuration
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
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.LocalConfiguration
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

/**
 * Çapraz eşitleme paneli — tam ekran dialog.
 *
 * Dikey: ilerleme kartı → hesap özetleri (yatay kaydırma) → günlük (kalan tüm yükseklik) → alt eylem çubuğu.
 * Yatay: sol sütun (ilerleme, hesaplar, eylemler; dikey kaydırılabilir) + sağ sütun (filtreler ve günlük).
 * Düzen [LocalConfiguration] üzerinden yönlendirme değişiminde otomatik olarak yeniden kurulur.
 */
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
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isCompactHeight = configuration.screenHeightDp < 480
    // Yatay düzende sol sütun (ilerleme + hesaplar + eylemler) ekranın %40'ı kadar, ancak 300–460 dp
    // aralığında tutulur; böylece geniş tabletlerde günlük listesi alanın çoğunu alır, telefonlarda
    // sütun okunabilir kalır.
    val landscapeSidePaneWidth = (configuration.screenWidthDp * 0.4f).dp.coerceIn(300.dp, 460.dp)
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
    val errorLogCount = remember(sourceLogs) { sourceLogs.count { it.isError } }
    val warningLogCount = remember(sourceLogs) { sourceLogs.count { it.isWarning } }

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

    val hasReportContent = state.totalEventCount > 0 || state.logs.isNotEmpty() || state.errorMessage != null
    val statusColor = when {
        state.isCompleted -> KitsugiColors.AccentGreen
        state.errorMessage != null -> ERROR_COLOR
        state.isRunning -> accentColor
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val statusSubtitle = when {
        state.isCompleted -> "Tamamlandı · ${formatDuration(elapsedMs)}" +
            (if (state.issueCount > 0) " · ${state.issueCount} sorun" else " · sorun yok")
        state.isRunning -> "${formatDuration(elapsedMs)} geçti · ${state.currentStep.ifBlank { "Hazırlanıyor" }}"
        state.errorMessage != null -> "Hata oluştu · rapor hazır"
        state.currentStep == "Eşitleme durduruldu" -> "Durduruldu · kısmi rapor hazır"
        else -> "Son eşitleme durumu"
    }

    KitsugiSheetOrDialog(
        onDismiss = onDismiss,
        fullScreen = true
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .displayCutoutPadding()
        ) {
            CrossSyncHeader(
                state = state,
                statusColor = statusColor,
                subtitle = statusSubtitle,
                compact = isLandscape && isCompactHeight,
                onDismiss = onDismiss
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

            if (isLandscape) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .width(landscapeSidePaneWidth)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ProgressCard(
                            state = state,
                            accentColor = accentColor,
                            elapsedMs = elapsedMs,
                            estimatedRemainingMs = estimatedRemainingMs,
                            compact = true
                        )
                        if (looksStalled) StalledBanner()
                        if (!state.errorMessage.isNullOrBlank()) ErrorBanner(state.errorMessage)
                        PlatformStatsSection(state = state, layoutAsGrid = true)
                        ActionButtons(
                            state = state,
                            hasReportContent = hasReportContent,
                            stacked = true,
                            onShare = ::shareReport,
                            onSave = ::saveReport,
                            onCancelRequest = { showCancelConfirmation = true },
                            onDismiss = onDismiss,
                            onStart = onStart
                        )
                        SafetyNote()
                        ReportLocationNote(state.reportSavedTo)
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        LogHeader(
                            state = state,
                            visibleCount = visibleLogs.size,
                            errorCount = errorLogCount,
                            warningCount = warningLogCount,
                            selected = logFilter,
                            onSelect = { logFilter = it }
                        )
                        LogList(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            visibleLogs = visibleLogs,
                            hasAnyLogs = sourceLogs.isNotEmpty(),
                            listState = logsListState
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ProgressCard(
                        state = state,
                        accentColor = accentColor,
                        elapsedMs = elapsedMs,
                        estimatedRemainingMs = estimatedRemainingMs,
                        compact = false
                    )
                    if (looksStalled) StalledBanner()
                    if (!state.errorMessage.isNullOrBlank()) ErrorBanner(state.errorMessage)
                    PlatformStatsSection(state = state, layoutAsGrid = false)
                    LogHeader(
                        state = state,
                        visibleCount = visibleLogs.size,
                        errorCount = errorLogCount,
                        warningCount = warningLogCount,
                        selected = logFilter,
                        onSelect = { logFilter = it }
                    )
                    LogList(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        visibleLogs = visibleLogs,
                        hasAnyLogs = sourceLogs.isNotEmpty(),
                        listState = logsListState
                    )
                    ReportLocationNote(state.reportSavedTo)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                    ActionButtons(
                        state = state,
                        hasReportContent = hasReportContent,
                        stacked = false,
                        onShare = ::shareReport,
                        onSave = ::saveReport,
                        onCancelRequest = { showCancelConfirmation = true },
                        onDismiss = onDismiss,
                        onStart = onStart
                    )
                    SafetyNote()
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

// ─────────────────────────────────────────────────────────────────────────────
// Bölümler
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CrossSyncHeader(
    state: CrossSyncProgressState,
    statusColor: Color,
    subtitle: String,
    compact: Boolean,
    onDismiss: () -> Unit
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 14.dp, end = 4.dp, top = if (compact) 4.dp else 8.dp, bottom = if (compact) 4.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(if (compact) 30.dp else 36.dp)
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
                    .size(if (compact) 18.dp else 21.dp)
                    .rotate(if (state.isRunning) rotation else 0f)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Çok Yönlü Eşitleme",
                style = (if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium)
                    .copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = statusColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (state.platformStats.isNotEmpty()) {
            Text(
                text = "${state.platformStats.size} hesap · ${state.totalEventCount} olay",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        IconButton(onClick = onDismiss) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = "Kapat",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ProgressCard(
    state: CrossSyncProgressState,
    accentColor: Color,
    elapsedMs: Long,
    estimatedRemainingMs: Long?,
    compact: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.24f))
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = if (compact) 8.dp else 10.dp)) {
            Text(
                text = state.currentStep.ifBlank { "İşlem hazırlanıyor..." },
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = if (compact) 1 else 2,
                overflow = TextOverflow.Ellipsis
            )
            if (state.currentDetail.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = state.currentDetail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (compact) 2 else 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(if (compact) 6.dp else 8.dp))

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

            Spacer(modifier = Modifier.height(5.dp))
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
                Spacer(modifier = Modifier.height(3.dp))
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
}

@Composable
private fun StalledBanner() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
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

@Composable
private fun ErrorBanner(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = ERROR_COLOR.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, ERROR_COLOR.copy(alpha = 0.3f))
    ) {
        Text(
            text = "Hata: $message",
            modifier = Modifier.padding(10.dp),
            style = MaterialTheme.typography.bodySmall,
            color = ERROR_COLOR,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun PlatformStatsSection(
    state: CrossSyncProgressState,
    layoutAsGrid: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Bağlı Hesaplar",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val totalErrors = state.platformStats.values.sumOf { it.errorCount }
            val totalSkipped = state.platformStats.values.sumOf { it.skippedCount }
            if (state.platformStats.isNotEmpty()) {
                Text(
                    text = "$totalSkipped atlandı · $totalErrors hata",
                    style = MaterialTheme.typography.labelSmall,
                    color = when {
                        totalErrors > 0 -> ERROR_COLOR
                        totalSkipped > 0 -> WARNING_COLOR
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }

        if (state.platformStats.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(9.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
            ) {
                Text(
                    "Bağlı ve eşitlemesi açık hesaplar belirleniyor...",
                    modifier = Modifier.padding(10.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else if (layoutAsGrid) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                maxItemsInEachRow = 2
            ) {
                state.platformStats.values.forEach { stats ->
                    PlatformStatChip(
                        stats = stats,
                        modifier = Modifier
                            .weight(1f)
                            .widthIn(min = 120.dp)
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                state.platformStats.values.forEach { stats ->
                    PlatformStatChip(stats = stats, modifier = Modifier.width(132.dp))
                }
            }
        }
    }
}

@Composable
private fun PlatformStatChip(
    stats: CrossPlatformStats,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(9.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                KitsugiPlatformLogo(platformId = stats.platformName, size = 16.dp)
                Text(
                    text = stats.platformName,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
            }
            Text(
                text = "${stats.initialCount} kayıt · +${stats.addedCount} · ~${stats.updatedCount}",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                color = if (stats.addedCount > 0 || stats.updatedCount > 0) Color(0xFF42A5F5) else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = when {
                    stats.skippedCount > 0 || stats.errorCount > 0 -> "${stats.skippedCount} atlandı · ${stats.errorCount} hata"
                    stats.initialCount > 0 -> "Sorun yok"
                    else -> "Kayıt yok"
                },
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp, fontWeight = FontWeight.Bold),
                color = when {
                    stats.errorCount > 0 -> ERROR_COLOR
                    stats.skippedCount > 0 -> WARNING_COLOR
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun LogHeader(
    state: CrossSyncProgressState,
    visibleCount: Int,
    errorCount: Int,
    warningCount: Int,
    selected: CrossSyncLogFilter,
    onSelect: (CrossSyncLogFilter) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = if (state.isRunning) "Canlı İşlem Günlüğü" else "İşlem Günlüğü",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "$visibleCount / ${state.totalEventCount}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            CrossSyncLogFilter.entries.forEach { filter ->
                val label = when (filter) {
                    CrossSyncLogFilter.All -> "Tümü"
                    CrossSyncLogFilter.Issues -> "Sorunlar${if (errorCount + warningCount > 0) " (${errorCount + warningCount})" else ""}"
                    CrossSyncLogFilter.Errors -> "Hatalar${if (errorCount > 0) " ($errorCount)" else ""}"
                    CrossSyncLogFilter.Warnings -> "Uyarılar${if (warningCount > 0) " ($warningCount)" else ""}"
                }
                FilterChip(
                    selected = selected == filter,
                    onClick = { onSelect(filter) },
                    label = { Text(label, fontSize = 11.sp) },
                    modifier = Modifier.height(30.dp)
                )
            }
        }
    }
}

@Composable
private fun LogList(
    modifier: Modifier,
    visibleLogs: List<CrossSyncLogEntry>,
    hasAnyLogs: Boolean,
    listState: androidx.compose.foundation.lazy.LazyListState
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    ) {
        if (visibleLogs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (!hasAnyLogs) "Henüz kayıt yok. Eşitleme adımları burada görünecek." else "Bu filtrede gösterilecek olay yok.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(visibleLogs, key = { it.id }) { log ->
                    LogItemRow(log = log)
                }
            }
        }
    }
}

@Composable
private fun ReportLocationNote(path: String?) {
    if (path == null) return
    Text(
        text = "Otomatik rapor: $path",
        modifier = Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun SafetyNote() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = Icons.Rounded.Security,
            contentDescription = null,
            tint = KitsugiColors.AccentGreen,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = "Veri silinmez; yalnızca güvenli eşleşmeler eklenir/güncellenir. Raporda erişim anahtarları gizlenir.",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        CrossSyncDisclaimerText(
            full = true,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun ColumnScope.ActionButtons(
    state: CrossSyncProgressState,
    hasReportContent: Boolean,
    stacked: Boolean,
    onShare: () -> Unit,
    onSave: () -> Unit,
    onCancelRequest: () -> Unit,
    onDismiss: () -> Unit,
    onStart: () -> Unit
) {
    val buttonHeight = 40.dp
    if (state.isRunning) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onShare,
                modifier = Modifier
                    .weight(1f)
                    .height(buttonHeight),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(5.dp))
                Text("Kısmi Rapor", maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp)
            }
            OutlinedButton(
                onClick = onCancelRequest,
                modifier = Modifier
                    .weight(1f)
                    .height(buttonHeight),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(5.dp))
                Text("Durdur", maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp)
            }
        }
        TextButton(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Arka Planda Devam Et", fontSize = 12.sp)
        }
    } else {
        if (stacked) {
            OutlinedButton(
                onClick = onSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(buttonHeight),
                shape = RoundedCornerShape(10.dp),
                enabled = hasReportContent
            ) {
                Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(5.dp))
                Text("Dosyaya Kaydet", maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp)
            }
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedButton(
                onClick = onShare,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(buttonHeight),
                shape = RoundedCornerShape(10.dp),
                enabled = hasReportContent
            ) {
                Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(5.dp))
                Text("Raporu Paylaş", maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp)
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                OutlinedButton(
                    onClick = onSave,
                    modifier = Modifier
                        .weight(1f)
                        .height(buttonHeight),
                    shape = RoundedCornerShape(10.dp),
                    enabled = hasReportContent
                ) {
                    Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Kaydet", maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = onShare,
                    modifier = Modifier
                        .weight(1f)
                        .height(buttonHeight),
                    shape = RoundedCornerShape(10.dp),
                    enabled = hasReportContent
                ) {
                    Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Paylaş", maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(0.8f)
                        .height(buttonHeight),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Kapat", maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp)
                }
            }
        }
        if (state.startedAt != null) {
            TextButton(
                onClick = onStart,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Rounded.Sync, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(5.dp))
                Text("Yeni Eşitleme Başlat", fontSize = 12.sp)
            }
        } else if (stacked) {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Kapat", fontSize = 12.sp)
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
        log.isError -> Icons.Rounded.ErrorOutline
        log.isWarning -> Icons.Rounded.WarningAmber
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
        color = when {
            log.isError -> ERROR_COLOR.copy(alpha = 0.07f)
            log.isWarning -> WARNING_COLOR.copy(alpha = 0.06f)
            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
        }
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (icon != null) {
                    Icon(icon, contentDescription = null, tint = tagColor, modifier = Modifier.size(13.dp))
                }
                Text(
                    text = log.platform,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
                    color = tagColor,
                    maxLines = 1,
                    modifier = Modifier.width(70.dp),
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
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = log.details,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 24,
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
