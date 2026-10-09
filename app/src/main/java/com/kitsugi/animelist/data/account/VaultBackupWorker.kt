package com.kitsugi.animelist.data.account

import android.content.Context
import androidx.work.*
import io.github.jan.supabase.auth.auth
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

/** Work data contains only an owner ID, never credentials, passwords or vault keys. */
class VaultBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val owner = inputData.getString("owner") ?: return Result.failure()
        if (LocalVaultKeyStore.owner(applicationContext) != owner) return Result.success()
        val auth = KitsugiAccountClient.client.auth
        try { auth.awaitInitialization() }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { return Result.retry() }
        val current = auth.currentUserOrNull()?.id ?: return Result.retry()
        if (owner != current) return Result.success() // Never send A's queued job into B's account.
        val result = LinkedAccountVault.backupNow(applicationContext, expectedOwner = owner)
        return when (result.exceptionOrNull()) {
            null -> Result.success()
            is VaultConflictException, is VaultLockedException -> Result.failure() // User action required.
            else -> Result.retry() // Network/server errors retain work across process death and reboot.
        }
    }

    companion object {
        private fun name(uid: String) = "kitsugi-vault-$uid"
        private val constraints get() = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        fun enqueue(context: Context, uid: String, startup: Boolean = false) {
            val request = OneTimeWorkRequestBuilder<VaultBackupWorker>()
                .setInputData(workDataOf("owner" to uid))
                .setConstraints(constraints)
                .setInitialDelay(3, TimeUnit.SECONDS)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .addTag(name(uid)).build()
            // Append changes even when an upload is already in flight; KEEP could lose its successor.
            WorkManager.getInstance(context).enqueueUniqueWork(name(uid),
                if (startup) ExistingWorkPolicy.KEEP else ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }

        fun periodic(context: Context, uid: String) {
            val request = PeriodicWorkRequestBuilder<VaultBackupWorker>(15, TimeUnit.MINUTES)
                .setInputData(workDataOf("owner" to uid)).setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .addTag(name(uid)).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("${name(uid)}-periodic",
                ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun cancel(context: Context, uid: String) {
            WorkManager.getInstance(context).cancelAllWorkByTag(name(uid))
        }
    }
}
