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
        private const val NOTIFICATION_ID = 1001
    }

    override fun doWork(): androidx.work.ListenableWorker.Result {
        val prefs = VubPreferences(applicationContext)
        
        val resultsUrl = prefs.resultsUrl
        val breakdownUrl = prefs.breakdownUrl
        val classesUrl = prefs.classesUrl
        val examsUrl = prefs.examsUrl

        var anyChanged = false

        if (!resultsUrl.isNullOrBlank()) {
            val oldData = CsvCacheManager.getResults(applicationContext)
            if (syncResults(resultsUrl, oldData)) {
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
            if (syncFile(classesUrl, CsvCacheManager.getClasses(applicationContext)) {
                CsvCacheManager.saveClasses(applicationContext, it)
            }) {
                anyChanged = true
            }
        }

        if (!examsUrl.isNullOrBlank()) {
            if (syncFile(examsUrl, CsvCacheManager.getExams(applicationContext)) {
                CsvCacheManager.saveExams(applicationContext, it)
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

    private fun syncResults(url: String, oldData: String): Boolean {
        return try {
            val newData = NetworkHelper.fetchUrl(url)
            if (newData.isNotBlank() && newData != oldData) {
                // Only notify if we already had some data (avoid notifying for everything on first load)
                if (oldData.isNotBlank()) {
                    val oldList = CsvParser.parseResultsCsv(oldData)
                    val newList = CsvParser.parseResultsCsv(newData)

                    val newResults = findNewResults(oldList, newList)
                    if (newResults.isNotEmpty()) {
                        sendNotification(newResults)
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

    private fun findNewResults(oldList: List<ResultEntry>, newList: List<ResultEntry>): List<ResultEntry> {
        val oldMap = oldList.associateBy { it.course + it.period.toString() }
        return newList.filter { newItem ->
            val key = newItem.course + newItem.period.toString()
            val oldItem = oldMap[key]
            // It is considered new if it didn't exist before OR if it went from no grade (0) to having a grade
            oldItem == null || (oldItem.grade == 0f && newItem.grade > 0f)
        }
    }

    private fun sendNotification(newResults: List<ResultEntry>) {
        createNotificationChannel()

        val context = applicationContext
        val intent = Intent(context, ResultsActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val title = if (newResults.size == 1) {
            "Nieuw resultaat beschikbaar"
        } else {
            "${newResults.size} nieuwe resultaten beschikbaar"
        }

        val body = newResults.take(3).joinToString(", ") { it.course } + 
                if (newResults.size > 3) " en meer..." else ""

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.baseline_assessment_24)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        with(NotificationManagerCompat.from(context)) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                notify(NOTIFICATION_ID, builder.build())
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Resultaat Updates"
            val descriptionText = "Meldingen for nieuwe studieresultaten"
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
