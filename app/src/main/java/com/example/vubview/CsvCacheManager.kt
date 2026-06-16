package com.example.vubview

import android.content.Context
import java.io.File

object CsvCacheManager {
    private const val RESULTS_FILE = "results_cache.csv"
    private const val BREAKDOWN_FILE = "breakdown_cache.csv"
    private const val CLASSES_FILE = "classes_cache.csv"
    private const val EXAMS_FILE = "exams_cache.csv"

    fun saveResults(context: Context, data: String) = saveFile(context, RESULTS_FILE, data)
    fun getResults(context: Context): String = readFile(context, RESULTS_FILE)

    fun saveBreakdown(context: Context, data: String) = saveFile(context, BREAKDOWN_FILE, data)
    fun getBreakdown(context: Context): String = readFile(context, BREAKDOWN_FILE)

    fun saveClasses(context: Context, data: String) = saveFile(context, CLASSES_FILE, data)
    fun getClasses(context: Context): String = readFile(context, CLASSES_FILE)

    fun saveExams(context: Context, data: String) = saveFile(context, EXAMS_FILE, data)
    fun getExams(context: Context): String = readFile(context, EXAMS_FILE)

    private fun saveFile(context: Context, fileName: String, data: String) {
        context.openFileOutput(fileName, Context.MODE_PRIVATE).use {
            it.write(data.toByteArray())
        }
    }

    private fun readFile(context: Context, fileName: String): String {
        val file = File(context.filesDir, fileName)
        if (!file.exists()) return ""
        return context.openFileInput(fileName).bufferedReader().use { it.readText() }
    }
}
