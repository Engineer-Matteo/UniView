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
import androidx.work.Worker
import androidx.work.WorkerParameters

class SyncWorker(context: Context, workerParams: WorkerParameters) : Worker(context, workerParams) {

    companion object {
        private const val CHANNEL_ID = "vub_view_updates"
        private const val ID_RESULTS = 1001
        private const val ID_SCHEDULE = 1002
        private const val ID_EXAMS = 1003
    }

    override fun doWork(): androidx.work.ListenableWorker.Result {
        val prefs = VubPreferences(applicationContext)
        
        val resultsUrl = prefs.resultsUrl
        val breakdownUrl = prefs.breakdownUrl
        val classesUrl = prefs.classesUrl
        val examsUrl = prefs.examsUrl
        val coursesUrl = prefs.coursesUrl

        var anyChanged = false

        if (!resultsUrl.isNullOrBlank()) {
            val oldData = CsvCacheManager.getResults(applicationContext)
            if (syncResults(resultsUrl, oldData, prefs.notifyResults)) {
                anyChanged = true
            }
        }

        if (!breakdownUrl.isNullOrBlank()) {
            if (syncFile(breakdownUrl, CsvCacheManager.getBreakdown(applicationContext)) {
                CsvCacheManager.saveBreakdown(applicationContext, it)
            }) {
                anyChanged = true
            }
        }

        if (!classesUrl.isNullOrBlank()) {
            val oldData = CsvCacheManager.getClasses(applicationContext)
            if (syncClasses(classesUrl, oldData, prefs.notifySchedule)) {
                anyChanged = true
            }
        }

        if (!examsUrl.isNullOrBlank()) {
            val oldData = CsvCacheManager.getExams(applicationContext)
            if (syncExams(examsUrl, oldData, prefs.notifyExams)) {
                anyChanged = true
            }
        }

        if (!coursesUrl.isNullOrBlank()) {
            if (syncFile(coursesUrl, CsvCacheManager.getCourses(applicationContext)) {
                CsvCacheManager.saveCourses(applicationContext, it)
            }) {
                anyChanged = true
            }
        }

        if (anyChanged) {
            Log.d("SyncWorker", "Data changed, updating widgets")
            NextEventWidget.triggerUpdate(applicationContext)
        }

        return androidx.work.ListenableWorker.Result.success()
    }

    private fun syncResults(url: String, oldData: String, shouldNotify: Boolean): Boolean {
        return try {
            val newData = NetworkHelper.fetchUrl(url)
            if (newData.isNotBlank() && newData != oldData) {
                if (shouldNotify && oldData.isNotBlank()) {
                    val oldList = CsvParser.parseResultsCsv(oldData)
                    val newList = CsvParser.parseResultsCsv(newData)

                    val newResults = findNewResults(oldList, newList)
                    if (newResults.isNotEmpty()) {
                        sendResultsNotification(newResults)
                    }
                }
                CsvCacheManager.saveResults(applicationContext, newData)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e("SyncWorker", "Error syncing results", e)
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
                CsvCacheManager.saveClasses(applicationContext, newData)
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
                CsvCacheManager.saveExams(applicationContext, newData)
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
            oldItem == null || (oldItem.grade == 0f && newItem.grade > 0f)
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

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "VUB VIEW Updates"
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

    private fun syncFile(url: String, cachedData: String, saveAction: (String) -> Unit): Boolean {
        return try {
            val newData = NetworkHelper.fetchUrl(url)
            if (newData.isNotBlank() && newData != cachedData) {
                saveAction(newData)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e("SyncWorker", "Error syncing $url", e)
            false
        }
    }
}
