package com.kitsugi.animelist.ui.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews
import com.kitsugi.animelist.R
import com.kitsugi.animelist.data.local.AnimeDownloadStore

/**
 * Ana ekran widget'ı: Kitsugi indirme sayısını gösterir.
 * Gerçek indirme verisi [AnimeDownloadStore] üzerinden güncellenir.
 */
class DownloadWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val count = runCatching {
            AnimeDownloadStore(context).getDownloads().size
        }.getOrDefault(0)

        for (appWidgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_downloads)
            views.setTextViewText(R.id.widget_title, "Kitsugi İndirmeleri")
            views.setTextViewText(R.id.widget_count, "$count indirme")
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
    }
}
