package com.example.vubview

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class SyncWorker(context: Context, workerParams: WorkerParameters) : Worker(context, workerParams) {

    companion object {
        private const val CHANNEL_ID = "vub_view_updates"
        private const val ID_RESULTS = 1001
        private const val ID_SCHEDULE = 1002
        private const val ID_EXAMS = 1003
        private const val ID_NEXT_CLASS = 1004
    }

    override fun doWork(): androidx.work.ListenableWorker.Result {
        val prefs = VubPreferences(applicationContext)
        
        val mainJsonUrl = prefs.mainJsonUrl
        val classesUrl = prefs.classesUrl
        val examsUrl = prefs.examsUrl

        var anyChanged = false

        if (!mainJsonUrl.isNullOrBlank()) {
            val oldData = DataCacheManager.getMainJson(applicationContext)
            if (syncMainJson(mainJsonUrl, oldData, prefs.notifyResults)) {
                anyChanged = true
            }
        }

        if (!classesUrl.isNullOrBlank()) {
            val oldData = DataCacheManager.getClasses(applicationContext)
            if (syncClasses(classesUrl, oldData, prefs.notifySchedule)) {
                anyChanged = true
            }
        }

        if (!examsUrl.isNullOrBlank()) {
            val oldData = DataCacheManager.getExams(applicationContext)
            if (syncExams(examsUrl, oldData, prefs.notifyExams)) {
                anyChanged = true
            }
        }

        if (anyChanged) {
            Log.d("SyncWorker", "Data changed, updating widgets")
            NextEventWidget.triggerUpdate(applicationContext)
            WeeklyScheduleWidget.triggerUpdate(applicationContext)
        }

        // Check for upcoming classes to notify 15 mins before
        checkAndNotifyUpcomingClass(prefs)

        return androidx.work.ListenableWorker.Result.success()
    }

    private fun checkAndNotifyUpcomingClass(prefs: VubPreferences) {
        if (!prefs.notifyNextLesson) return

        val classesData = DataCacheManager.getClasses(applicationContext)
        if (classesData.isBlank()) return

        val classes = IcalParser.parse(classesData, "class")
        val now = System.currentTimeMillis()
        
        // Find classes starting in the next 75 minutes
        val upcomingClasses = classes.filter { it.kind == "class" }
            .filter { 
                val startTime = it.dateTimeMillis()
                val diff = startTime - now
                diff in 0..75 * 60 * 1000
            }

        upcomingClasses.forEach { lesson ->
            val startTime = lesson.dateTimeMillis()
            val reminderTime = startTime - (15 * 60 * 1000)
            val delay = reminderTime - now

            if (delay <= 0) {
                if (startTime > now && prefs.lastNotifiedLessonTime != startTime) {
                    sendNextLessonNotification("${lesson.title} begint om ${lesson.start}")
                    prefs.lastNotifiedLessonTime = startTime
                }
            } else {
                val inputData = Data.Builder()
                    .putString("message", "${lesson.title} begint om ${lesson.start}")
                    .build()

                val reminderRequest = OneTimeWorkRequestBuilder<LessonReminderWorker>()
                    .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                    .setInputData(inputData)
                    .build()

                WorkManager.getInstance(applicationContext).enqueueUniqueWork(
                    "LessonReminder_${startTime}",
                    ExistingWorkPolicy.KEEP,
                    reminderRequest
                )
            }
        }
    }

    private fun syncMainJson(url: String, oldData: String, shouldNotify: Boolean): Boolean {
        return try {
            val newData = NetworkHelper.fetchUrl(url)
            if (newData.isNotBlank() && newData != oldData) {
                if (shouldNotify && oldData.isNotBlank()) {
                    val (oldResults, _, _) = JsonParser.parseMainJson(oldData)
                    val (newResultsList, _, _) = JsonParser.parseMainJson(newData)

                    val newResults = findNewResults(oldResults, newResultsList)
                    if (newResults.isNotEmpty()) {
                        sendResultsNotification(newResults)
                    }
                }
                DataCacheManager.saveMainJson(applicationContext, newData)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e("SyncWorker", "Error syncing main JSON", e)
            false
        }
    }

    private fun syncClasses(url: String, oldData: String, shouldNotify: Boolean): Boolean {
        return try {
            val newData = NetworkHelper.fetchUrl(url)
            if (newData.isNotBlank() && newData != oldData) {
                if (shouldNotify && oldData.isNotBlank()) {
                    sendUpdateNotification(
                        "Roosterwijziging",
                        "Er zijn wijzigingen in je lesrooster gevonden.",
                        ScheduleActivity::class.java,
                        ID_SCHEDULE,
                        R.drawable.baseline_schedule_24
                    )
                }
                DataCacheManager.saveClasses(applicationContext, newData)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e("SyncWorker", "Error syncing classes", e)
            false
        }
    }

    private fun syncExams(url: String, oldData: String, shouldNotify: Boolean): Boolean {
        return try {
            val newData = NetworkHelper.fetchUrl(url)
            if (newData.isNotBlank() && newData != oldData) {
                if (shouldNotify && oldData.isNotBlank()) {
                    sendUpdateNotification(
                        "Examenrooster gewijzigd",
                        "Er zijn wijzigingen in je examenrooster gevonden.",
                        ExamsActivity::class.java,
                        ID_EXAMS,
                        R.drawable.baseline_assignment_24
                    )
                }
                DataCacheManager.saveExams(applicationContext, newData)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e("SyncWorker", "Error syncing exams", e)
            false
        }
    }

    private fun findNewResults(oldList: List<ResultEntry>, newList: List<ResultEntry>): List<ResultEntry> {
        val oldMap = oldList.associateBy { it.course + it.period.toString() }
        return newList.filter { newItem ->
            val key = newItem.course + newItem.period.toString()
            val oldItem = oldMap[key]
            oldItem == null || (oldItem.grade <= 0f && newItem.grade > 0f)
        }
    }

    private fun sendResultsNotification(newResults: List<ResultEntry>) {
        val title = if (newResults.size == 1) {
            "Nieuw resultaat beschikbaar"
        } else {
            "${newResults.size} nieuwe resultaten beschikbaar"
        }

        val body = newResults.take(3).joinToString(", ") { it.course } + 
                if (newResults.size > 3) " en meer..." else ""

        sendUpdateNotification(title, body, ResultsActivity::class.java, ID_RESULTS, R.drawable.baseline_assessment_24)
    }

    private fun sendUpdateNotification(
        title: String,
        body: String,
        targetActivity: Class<*>,
        notificationId: Int,
        iconRes: Int
    ) {
        createNotificationChannel()

        val context = applicationContext
        val intent = Intent(context, targetActivity).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            context, notificationId, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(iconRes)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        with(NotificationManagerCompat.from(context)) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                notify(notificationId, builder.build())
            }
        }
    }

    private fun sendNextLessonNotification(nextLesson: String) {
        createNotificationChannel()
        val context = applicationContext
        val intent = Intent(context, ScheduleActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            context, ID_NEXT_CLASS, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.baseline_schedule_24)
            .setContentTitle("Volgende les")
            .setContentText(nextLesson)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        with(NotificationManagerCompat.from(context)) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                notify(ID_NEXT_CLASS, builder.build())
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "VUBVIEW Updates"
            val descriptionText = "Meldingen voor nieuwe resultaten en roosterwijzigingen"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}
