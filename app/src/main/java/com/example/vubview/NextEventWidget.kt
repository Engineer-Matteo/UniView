package com.example.vubview

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews

/**
 * Implementation of App Widget functionality.
 */
class NextEventWidget : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // There may be multiple widgets active, so update all of them
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onEnabled(context: Context) {
        // Enter relevant functionality for when the first widget is created
    }

    override fun onDisabled(context: Context) {
        // Enter relevant functionality for when the last widget is disabled
    }

    companion object {
        fun triggerUpdate(context: Context) {
            val intent = Intent(context, NextEventWidget::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            }
            val ids = AppWidgetManager.getInstance(context)
                .getAppWidgetIds(ComponentName(context, NextEventWidget::class.java))
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            context.sendBroadcast(intent)
        }
    }
}

internal fun updateAppWidget(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetId: Int
) {
    val views = RemoteViews(context.packageName, R.layout.next_event_widget)

    // Setup click intent to open MainActivity
    val intent = Intent(context, MainActivity::class.java)
    val pendingIntent = PendingIntent.getActivity(
        context, 0, intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    views.setOnClickPendingIntent(R.id.widget_container, pendingIntent)

    // Load data from cache
    val cachedClasses = CsvCacheManager.getClasses(context)
    val cachedExams = CsvCacheManager.getExams(context)

    val items = mutableListOf<NextEvent>()
    if (cachedClasses.isNotBlank()) {
        items += IcalParser.parse(cachedClasses, "class")
    }
    if (cachedExams.isNotBlank()) {
        items += IcalParser.parse(cachedExams, "exam")
    }

    // Use isFuture() to show the next event that hasn't started yet
    val next = items.filter { it.isFuture() }.minByOrNull { it.dateTimeMillis() }

    if (next == null) {
        views.setViewVisibility(R.id.widget_label, View.GONE)
        val prefs = VubPreferences(context)
        if (prefs.examsUrl.isNullOrBlank() && prefs.classesUrl.isNullOrBlank()) {
            views.setTextViewText(R.id.widget_content, "Voer URL's in de app in.")
        } else {
            views.setTextViewText(R.id.widget_content, "Geen komende evenementen.")
        }
    } else {
        views.setViewVisibility(R.id.widget_label, View.VISIBLE)
        views.setTextViewText(R.id.widget_label, if (next.kind == "exam") "Volgend examen:" else "Volgende les:")
        
        val content = "${next.title}\n${next.dateLabel()} om ${next.timeLabel()}"
        views.setTextViewText(R.id.widget_content, content)
    }

    // Instruct the widget manager to update the widget
    appWidgetManager.updateAppWidget(appWidgetId, views)
}
