package com.example.uniview

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.core.content.ContextCompat
import java.util.*

class WeeklyScheduleWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
        super.onUpdate(context, appWidgetManager, appWidgetIds)
    }

    companion object {
        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val intent = Intent(context, WeeklyScheduleService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }

            val views = RemoteViews(context.packageName, R.layout.weekly_schedule_widget).apply {
                setRemoteAdapter(R.id.widget_list, intent)
                setEmptyView(R.id.widget_list, R.id.widget_empty_view)
            }

            val appIntent = Intent(context, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                context, 0, appIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_title, pendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_list)
        }

        fun triggerUpdate(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val ids = appWidgetManager.getAppWidgetIds(ComponentName(context, WeeklyScheduleWidget::class.java))
            appWidgetManager.notifyAppWidgetViewDataChanged(ids, R.id.widget_list)
        }
    }
}

class WeeklyScheduleService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return WeeklyScheduleFactory(applicationContext)
    }
}

class WeeklyScheduleFactory(private val context: Context) : RemoteViewsService.RemoteViewsFactory {
    private var events: List<NextEvent> = emptyList()

    override fun onCreate() {}

    override fun onDataSetChanged() {
        val cachedClasses = DataCacheManager.getClasses(context)
        val cachedExams = DataCacheManager.getExams(context)

        val allEvents = mutableListOf<NextEvent>()
        if (cachedClasses.isNotBlank()) {
            allEvents += IcalParser.parse(cachedClasses, "class")
        }
        if (cachedExams.isNotBlank()) {
            allEvents += IcalParser.parse(cachedExams, "exam")
        }

        // Start of this week (Monday)
        val startOfWeek = Calendar.getInstance().apply {
            firstDayOfWeek = Calendar.MONDAY
            val day = get(Calendar.DAY_OF_WEEK)
            val diff = if (day == Calendar.SUNDAY) -6 else Calendar.MONDAY - day
            add(Calendar.DAY_OF_YEAR, diff)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        
        // End of this week (Sunday)
        val endOfWeek = startOfWeek.clone() as Calendar
        endOfWeek.add(Calendar.DAY_OF_YEAR, 6)
        endOfWeek.set(Calendar.HOUR_OF_DAY, 23)
        endOfWeek.set(Calendar.MINUTE, 59)
        endOfWeek.set(Calendar.SECOND, 59)
        endOfWeek.set(Calendar.MILLISECOND, 999)

        // Filter for events in this week that haven't started yet
        events = allEvents.filter {
            val time = it.dateTimeMillis()
            time in startOfWeek.timeInMillis..endOfWeek.timeInMillis && it.isFuture()
        }.sortedBy { it.dateTimeMillis() }
    }

    override fun onDestroy() {}

    override fun getCount(): Int = events.size

    override fun getViewAt(position: Int): RemoteViews {
        if (position >= events.size) return RemoteViews(context.packageName, R.layout.widget_item_event)

        val event = events[position]
        val views = RemoteViews(context.packageName, R.layout.widget_item_event)
        
        views.setTextViewText(R.id.event_title, event.title)
        
        val dayName = event.formattedDate().split(" ").firstOrNull() ?: ""
        val info = "${dayName} ${event.start} - ${event.end}\n${event.room}".trim()
        views.setTextViewText(R.id.event_time_location, info)

        val color = if (event.kind == "exam") {
            ContextCompat.getColor(context, R.color.main_orange)
        } else {
            ContextCompat.getColor(context, R.color.main_blue)
        }
        
        views.setInt(R.id.event_indicator, "setBackgroundColor", color)

        return views
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = position.toLong()
    override fun hasStableIds(): Boolean = true
}
