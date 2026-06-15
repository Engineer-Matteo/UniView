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
}
