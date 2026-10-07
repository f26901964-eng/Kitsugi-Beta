package com.kitsugi.animelist.model

data class CrossPlatformStats(
    val platformName: String,
    val initialCount: Int = 0,
    val addedCount: Int = 0,
    val updatedCount: Int = 0,
    val errorCount: Int = 0,
    val skippedCount: Int = 0
)

data class CrossSyncLogEntry(
    val id: Long = System.nanoTime(),
    val timestamp: Long = System.currentTimeMillis(),
    val platform: String,
    val message: String,
    val isError: Boolean = false,
    val isAddition: Boolean = false,
    val isUpdate: Boolean = false,
    val isWarning: Boolean = false,
    val details: String? = null
)

data class CrossSyncProgressState(
    val isRunning: Boolean = false,
    val isCompleted: Boolean = false,
    val currentStep: String = "",
    val currentDetail: String = "",
    val processedItems: Int = 0,
    val totalItems: Int = 0,
    /** Unit represented by processedItems/totalItems in the current phase. */
    val progressUnit: String = "İçerik",
    /** Start time of the current determinate phase, so estimates do not include earlier phases. */
    val progressPhaseStartedAt: Long? = null,
    /** Updated on each meaningful phase/progress update; used to warn about stalled runs. */
    val lastUpdatedAt: Long? = null,
    /** Complete run counters, even while the visible live log is bounded to its latest entries. */
    val totalEventCount: Int = 0,
    val issueCount: Int = 0,
    val platformStats: Map<String, CrossPlatformStats> = emptyMap(),
    /** Bounded list used by the live dialog. */
    val logs: List<CrossSyncLogEntry> = emptyList(),
    /** Complete, unbounded run history used only after the sync finishes and for export. */
    val reportLogs: List<CrossSyncLogEntry> = emptyList(),
    val errorMessage: String? = null,
    val startedAt: Long? = null,
    val finishedAt: Long? = null,
    val reportSavedTo: String? = null
) {
    val progressPercent: Float
        get() = if (totalItems > 0) (processedItems.toFloat() / totalItems).coerceIn(0f, 1f) else 0f
}
