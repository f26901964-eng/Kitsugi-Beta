package com.kitsugi.animelist.core.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.kitsugi.animelist.MainActivity
import com.kitsugi.animelist.R
import com.kitsugi.animelist.utils.KitsugiStrings

/**
 * Toplu eklenti kurulum / güncelleme ilerlemesini ve sonucunu Android sistem
 * bildirimi olarak gösterir. Uygulama içinde ekranı kaplayan kalıcı bir kart
 * kullanılmaz; kullanıcı bildirim gölgesinden ilerlemeyi takip eder.
 *
 * Bildirim izni (Android 13+) yoksa veya bildirimler kapalıysa sessizce atlanır;
 * kurulum yine de arka planda devam eder.
 */
object AddonInstallNotifier {

    private const val CHANNEL_ID = "kitsugi_addon_install"
    private const val PROGRESS_NOTIFICATION_ID = 4107
    private const val RESULT_NOTIFICATION_ID = 4108

    /** Kurulum devam ederken: kalıcı, sessiz ilerleme bildirimi. */
    @SuppressLint("MissingPermission") // İzin canNotify() içinde denetlenir.
    fun showProgress(
        context: Context,
        repoName: String,
        done: Int,
        total: Int,
        currentName: String
    ) {
        if (!canNotify(context)) return
        ensureChannel(context)

        val title = context.getString(R.string.eklentiler_kuruluyor)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(context.getString(R.string.indiriliyor_bulkinstallcurrentname, currentName))
            .setSubText(repoName.ifBlank { null })
            .setProgress(total.coerceAtLeast(0), done.coerceIn(0, total.coerceAtLeast(0)), total <= 0)
            .setContentInfo("$done / $total")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setContentIntent(launchAppIntent(context))

        NotificationManagerCompat.from(context).notify(PROGRESS_NOTIFICATION_ID, builder.build())
    }

    /** Kurulum bittiğinde ilerleme bildirimini kaldırır. */
    fun cancelProgress(context: Context) {
        runCatching {
            NotificationManagerCompat.from(context).cancel(PROGRESS_NOTIFICATION_ID)
        }
    }

    /** Kurulum sonucu: dokunulunca kapanan, normal öncelikli bildirim. */
    @SuppressLint("MissingPermission") // İzin canNotify() içinde denetlenir.
    fun showResult(context: Context, message: String) {
        cancelProgress(context)
        if (!canNotify(context)) return
        ensureChannel(context)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(context.getString(R.string.eklentiler_kuruluyor))
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(launchAppIntent(context))

        NotificationManagerCompat.from(context).notify(RESULT_NOTIFICATION_ID, builder.build())
    }

    private fun canNotify(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Eklenti kurulumu",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = KitsugiStrings.get(R.string.eklenti_kurulum_ve_guncelleme_ilerlemesi)
            setShowBadge(false)
        }
        nm.createNotificationChannel(channel)
    }

    private fun launchAppIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
