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
    val isUpdate: Boolean = false
)

data class CrossSyncProgressState(
    val isRunning: Boolean = false,
    val isCompleted: Boolean = false,
    val currentStep: String = "",
    val currentDetail: String = "",
    val processedItems: Int = 0,
    val totalItems: Int = 0,
    val platformStats: Map<String, CrossPlatformStats> = emptyMap(),
    val logs: List<CrossSyncLogEntry> = emptyList(),
    val errorMessage: String? = null
) {
    val progressPercent: Float
        get() = if (totalItems > 0) (processedItems.toFloat() / totalItems).coerceIn(0f, 1f) else 0f
}
