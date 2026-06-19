package com.example.vubview

import android.content.Context
import java.io.File

object DataCacheManager {
    private const val MAIN_JSON_FILE = "main_data_cache.json"
    private const val CLASSES_FILE = "classes_cache.ics"
    private const val EXAMS_FILE = "exams_cache.ics"

    fun saveMainJson(context: Context, data: String) = saveFile(context, MAIN_JSON_FILE, data)
    fun getMainJson(context: Context): String = readFile(context, MAIN_JSON_FILE)

    fun saveClasses(context: Context, data: String) = saveFile(context, CLASSES_FILE, data)
    fun getClasses(context: Context): String = readFile(context, CLASSES_FILE)

    fun saveExams(context: Context, data: String) = saveFile(context, EXAMS_FILE, data)
    fun getExams(context: Context): String = readFile(context, EXAMS_FILE)

    private fun saveFile(context: Context, fileName: String, data: String) {
        try {
            context.openFileOutput(fileName, Context.MODE_PRIVATE).use {
                it.write(data.toByteArray())
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun readFile(context: Context, fileName: String): String {
        val file = File(context.filesDir, fileName)
        if (!file.exists()) return ""
        return try {
            context.openFileInput(fileName).bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            ""
        }
    }
}
