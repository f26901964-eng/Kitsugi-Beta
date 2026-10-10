package com.kitsugi.animelist.core.storage

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Günlük depolama bakımı.
 *
 * ## Neden ayrı bir worker
 * Budama kodu daha önce `MappingSyncWorker` içine gömülüydü ve o iş
 * `NetworkType.UNMETERED` (yalnızca Wi-Fi) + `requiresBatteryNotLow` koşullarıyla planlanıyordu.
 * Mobil veri kullanan ya da şarjı düşük cihazda bu iş **hiç çalışmıyordu**; detay önbelleği ve
 * diğer kayıtlar süresiz birikiyordu.
 *
 * Bu worker **hiç ağ gerektirmez** (tamamen yerel disk işi) ve bu yüzden koşulsuz planlanır:
 *  - `NetworkType.NOT_REQUIRED` (varsayılan) → Wi-Fi beklemez,
 *  - yalnızca `requiresDeviceIdle` gibi ek kısıt yok; sistem uygun bulduğu anda çalıştırır.
 */
class StorageMaintenanceWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        return try {
            val before = StorageJanitor.totalBytes(context)
            StorageJanitor.runMaintenance(context, force = true)
            val after = StorageJanitor.totalBytes(context)
            Log.i(
                TAG,
                "Günlük bakım bitti: ${(before - after).coerceAtLeast(0L) / 1024} KB geri kazanıldı " +
                    "(toplam ${after / 1024} KB)"
            )
            Result.success()
        } catch (t: Throwable) {
            Log.w(TAG, "Günlük bakım başarısız: ${t.message}")
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "StorageMaintenance"
        private const val UNIQUE_WORK_NAME = "kitsugi_storage_maintenance"

        /** Günde bir kez, ağ koşulu olmadan. */
        fun schedule(context: Context) {
            try {
                val request = PeriodicWorkRequestBuilder<StorageMaintenanceWorker>(1, TimeUnit.DAYS)
                    .setConstraints(Constraints.NONE)
                    .build()
                WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                    UNIQUE_WORK_NAME,
                    // UPDATE: koşullar/kod değiştiğinde mevcut plan güncellenir (KEEP eskiyi korurdu).
                    ExistingPeriodicWorkPolicy.UPDATE,
                    request
                )
                Log.d(TAG, "Günlük depolama bakımı planlandı")
            } catch (t: Throwable) {
                Log.w(TAG, "Depolama bakımı planlanamadı: ${t.message}")
            }
        }
    }
}
