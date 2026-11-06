package com.kitsugi.animelist.core.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.kitsugi.animelist.R
import com.kitsugi.animelist.data.manga.download.MangaDownload
import com.kitsugi.animelist.data.manga.download.MangaDownloadManager
import com.kitsugi.animelist.ui.screens.offline.DownloadsActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Keeps active chapter downloads alive while Kitsugi is in the background. */
class MangaDownloadService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var observeJob: Job? = null
    private var lastNotificationAt = 0L

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        observeDownloads()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(
            NOTIFICATION_ID,
            notification(getString(R.string.manga_download_notification_preparing)),
        )
        if (observeJob == null) observeDownloads()
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun observeDownloads() {
        if (observeJob?.isActive == true) return
        observeJob = serviceScope.launch {
            MangaDownloadManager.downloads.collect { records ->
                val active = records.filter {
                    it.status == MangaDownload.Status.DOWNLOADING ||
                        it.status == MangaDownload.Status.QUEUE
                }
                if (active.isEmpty()) {
                    delay(1_500L)
                    val stillIdle = MangaDownloadManager.downloads.value.none {
                        it.status == MangaDownload.Status.DOWNLOADING ||
                            it.status == MangaDownload.Status.QUEUE
                    }
                    if (stillIdle) {
                        stopSelf()
                        return@collect
                    }
                } else {
                    val now = System.currentTimeMillis()
                    if (now - lastNotificationAt >= 1_000L) {
                        lastNotificationAt = now
                        val current = active.firstOrNull {
                            it.status == MangaDownload.Status.DOWNLOADING
                        } ?: active.first()
                        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                        manager.notify(NOTIFICATION_ID, notification(current))
                    }
                }
            }
        }
    }

    private fun notification(record: MangaDownload): Notification {
        val status = when (record.status) {
            MangaDownload.Status.DOWNLOADING -> "${record.progress}% · ${record.downloadedPages}/${record.totalPages}"
            else -> getString(R.string.manga_chapter_download_waiting)
        }
        val variant = record.scanlator?.takeIf { it.isNotBlank() }
            ?: getString(R.string.manga_chapter_unknown_scanlator)
        val text = "${record.chapterName} • $variant\n${record.sourceName} · $status"
        return baseNotification(record.mangaTitle)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setProgress(100, record.progress, record.status != MangaDownload.Status.DOWNLOADING)
            .build()
    }

    private fun notification(text: String): Notification = baseNotification(
        getString(R.string.manga_download_notification_title),
    )
        .setContentText(text)
        .setProgress(100, 0, true)
        .build()

    private fun baseNotification(title: String): NotificationCompat.Builder {
        val openDownloads = PendingIntent.getActivity(
            this,
            0,
            Intent(this, DownloadsActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(openDownloads)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.manga_download_notification_title),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.manga_download_notification_channel_description)
                setShowBadge(false)
            },
        )
    }

    override fun onDestroy() {
        observeJob?.cancel()
        serviceScope.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "manga_chapter_downloads"
        private const val NOTIFICATION_ID = 10003

        fun start(context: Context) {
            runCatching {
                context.startForegroundService(
                    Intent(context.applicationContext, MangaDownloadService::class.java),
                )
            }
        }
    }
}
