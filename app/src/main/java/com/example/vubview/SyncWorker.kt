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
import java.util.*
import java.util.concurrent.TimeUnit

class SyncWorker(context: Context, workerParams: WorkerParameters) : Worker(context, workerParams) {

    companion object {
        private const val CHANNEL_ID = "vub_view_updates"
        private const val ID_RESULTS = 1001
        private const val ID_SCHEDULE = 1002
        private const val ID_EXAMS = 1003
        private const val ID_NEXT_CLASS = 1004
        private const val TAG = "SyncWorker"
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
            Log.d(TAG, "Data changed, updating widgets")
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
                val (oldResults, oldBreakdowns, oldCourses) = JsonParser.parseMainJson(oldData)
                val (newResultsList, newBreakdowns, newCourses) = JsonParser.parseMainJson(newData)

                // Comparison excluding potentially volatile descriptions or order
                val contentChanged = oldResults.toSet() != newResultsList.toSet() || 
                                     oldBreakdowns.toSet() != newBreakdowns.toSet() || 
                                     oldCourses.size != newCourses.size

                if (shouldNotify && oldData.isNotBlank() && contentChanged) {
                    val newResults = findNewResults(oldResults, newResultsList)
                    if (newResults.isNotEmpty()) {
                        sendResultsNotification(newResults)
                    }
                }
                DataCacheManager.saveMainJson(applicationContext, newData)
                contentChanged
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing main JSON", e)
            false
        }
    }

    private fun syncClasses(url: String, oldData: String, shouldNotify: Boolean): Boolean {
        return try {
            val newData = NetworkHelper.fetchUrl(url)
            if (newData.isNotBlank() && newData != oldData) {
                val oldEvents = IcalParser.parse(oldData, "class")
                val newEvents = IcalParser.parse(newData, "class")
                
                val now = System.currentTimeMillis()
                val oldFingerprints = getFingerprints(oldEvents, now)
                val newFingerprints = getFingerprints(newEvents, now)
                
                val added = newFingerprints - oldFingerprints
                val removed = oldFingerprints - newFingerprints
                val contentChanged = added.isNotEmpty() || removed.isNotEmpty()

                if (contentChanged) {
                    Log.d(TAG, "Schedule changed: ${added.size} added, ${removed.size} removed")
                    
                    val hasFutureChanges = (added + removed).any { isFingerprintFuture(it, now) }

                    if (shouldNotify && oldData.isNotBlank() && hasFutureChanges) {
                        sendUpdateNotification(
                            "Roosterwijziging",
                            "Er zijn wijzigingen in je lesrooster gevonden.",
                            ScheduleActivity::class.java,
                            ID_SCHEDULE,
                            R.drawable.baseline_schedule_24
                        )
                    }
                }
                DataCacheManager.saveClasses(applicationContext, newData)
                contentChanged
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing classes", e)
            false
        }
    }

    private fun syncExams(url: String, oldData: String, shouldNotify: Boolean): Boolean {
        return try {
            val newData = NetworkHelper.fetchUrl(url)
            if (newData.isNotBlank() && newData != oldData) {
                val oldEvents = IcalParser.parse(oldData, "exam")
                val newEvents = IcalParser.parse(newData, "exam")
                
                val now = System.currentTimeMillis()
                val oldFingerprints = getFingerprints(oldEvents, now)
                val newFingerprints = getFingerprints(newEvents, now)
                
                val added = newFingerprints - oldFingerprints
                val removed = oldFingerprints - newFingerprints
                val contentChanged = added.isNotEmpty() || removed.isNotEmpty()

                if (contentChanged) {
                    Log.d(TAG, "Exam schedule changed: ${added.size} added, ${removed.size} removed")
                    
                    val hasFutureChanges = (added + removed).any { isFingerprintFuture(it, now) }

                    if (shouldNotify && oldData.isNotBlank() && hasFutureChanges) {
                        sendUpdateNotification(
                            "Examenrooster gewijzigd",
                            "Er zijn wijzigingen in je examenrooster gevonden.",
                            ExamsActivity::class.java,
                            ID_EXAMS,
                            R.drawable.baseline_assignment_24
                        )
                    }
                }
                DataCacheManager.saveExams(applicationContext, newData)
                contentChanged
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing exams", e)
            false
        }
    }

    private fun getFingerprints(events: List<NextEvent>, now: Long): Set<String> {
        // Only consider events that are still relevant (ended less than 24h ago)
        return events.filter { it.endDateTimeMillis() > now - 86400000 }
            .map { "${it.title.trim()}|${it.date}|${it.start}|${it.end}|${it.room.trim()}" }
            .toSet()
    }

    private fun isFingerprintFuture(fingerprint: String, now: Long): Boolean {
        val parts = fingerprint.split("|")
        if (parts.size < 3) return false
        val date = parts[1]
        val time = parts[2]
        return try {
            val dateParts = date.split(Regex("[^0-9]+")).filter { it.isNotBlank() }.map { it.toInt() }
            val cal = Calendar.getInstance()
            if (dateParts[0] > 1000) { cal.set(dateParts[0], dateParts[1] - 1, dateParts[2]) }
            else if (dateParts[2] > 1000) { cal.set(dateParts[2], dateParts[1] - 1, dateParts[0]) }
            else { cal.set(2000 + dateParts[2], dateParts[1] - 1, dateParts[0]) }
            
            val timeParts = time.split(":").filter { it.isNotBlank() }.map { it.toInt() }
            if (timeParts.size >= 2) {
                cal.set(Calendar.HOUR_OF_DAY, timeParts[0])
                cal.set(Calendar.MINUTE, timeParts[1])
            } else {
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
            }
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            cal.timeInMillis > now
        } catch (e: Exception) { false }
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
