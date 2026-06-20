package com.example.uniview

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

class Preferences(context: Context) {
    private val prefs = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)

    var mainJsonUrl: String?
        get() = prefs.getString("main_json_url", null)
        set(value) = prefs.edit().putString("main_json_url", value).apply()

    var classesUrl: String?
        get() = prefs.getString("classes_url", null)
        set(value) = prefs.edit().putString("classes_url", value).apply()

    var examsUrl: String?
        get() = prefs.getString("exams_url", null)
        set(value) = prefs.edit().putString("exams_url", value).apply()

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

    /**
     * Theme preference:
     * 0: Follow System
     * 1: Light Mode (Default)
     * 2: Dark Mode
     */
    var themeMode: Int
        get() = prefs.getInt("theme_mode", 1)
        set(value) = prefs.edit().putInt("theme_mode", value).apply()

    fun applyTheme() {
        val mode = when (themeMode) {
            1 -> AppCompatDelegate.MODE_NIGHT_NO
            2 -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(mode)
    }
}
