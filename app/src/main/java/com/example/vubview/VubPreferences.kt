package com.example.vubview

import android.content.Context

class VubPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("vub_prefs", Context.MODE_PRIVATE)

    var resultsUrl: String?
        get() = prefs.getString("results_url", null)
        set(value) = prefs.edit().putString("results_url", value).apply()

    var breakdownUrl: String?
        get() = prefs.getString("breakdown_url", null)
        set(value) = prefs.edit().putString("breakdown_url", value).apply()

    var classesUrl: String?
        get() = prefs.getString("classes_url", null)
        set(value) = prefs.edit().putString("classes_url", value).apply()

    var examsUrl: String?
        get() = prefs.getString("exams_url", null)
        set(value) = prefs.edit().putString("exams_url", value).apply()

    var coursesUrl: String?
        get() = prefs.getString("courses_url", null)
        set(value) = prefs.edit().putString("courses_url", value).apply()

    var notifyResults: Boolean
        get() = prefs.getBoolean("notify_results", true)
        set(value) = prefs.edit().putBoolean("notify_results", value).apply()

    var notifySchedule: Boolean
        get() = prefs.getBoolean("notify_schedule", false)
        set(value) = prefs.edit().putBoolean("notify_schedule", value).apply()

    var notifyExams: Boolean
        get() = prefs.getBoolean("notify_exams", false)
        set(value) = prefs.edit().putBoolean("notify_exams", value).apply()

    var notifyNextLesson: Boolean
        get() = prefs.getBoolean("notify_next_lesson", false)
        set(value) = prefs.edit().putBoolean("notify_next_lesson", value).apply()

    var lastNotifiedLessonTime: Long
        get() = prefs.getLong("last_notified_lesson_time", 0L)
        set(value) = prefs.edit().putLong("last_notified_lesson_time", value).apply()
}
