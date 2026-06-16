package com.example.vubview

import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters

class SyncWorker(context: Context, workerParams: WorkerParameters) : Worker(context, workerParams) {

    override fun doWork(): androidx.work.ListenableWorker.Result {
        val prefs = VubPreferences(applicationContext)
        
        val resultsUrl = prefs.resultsUrl
        val breakdownUrl = prefs.breakdownUrl
        val classesUrl = prefs.classesUrl
        val examsUrl = prefs.examsUrl

        var changed = false

        if (!resultsUrl.isNullOrBlank()) {
            if (syncFile(resultsUrl, CsvCacheManager.getResults(applicationContext)) {
                CsvCacheManager.saveResults(applicationContext, it)
            }) changed = true
        }

        if (!breakdownUrl.isNullOrBlank()) {
            if (syncFile(breakdownUrl, CsvCacheManager.getBreakdown(applicationContext)) {
                CsvCacheManager.saveBreakdown(applicationContext, it)
            }) changed = true
        }

        if (!classesUrl.isNullOrBlank()) {
            if (syncFile(classesUrl, CsvCacheManager.getClasses(applicationContext)) {
                CsvCacheManager.saveClasses(applicationContext, it)
            }) changed = true
        }

        if (!examsUrl.isNullOrBlank()) {
            if (syncFile(examsUrl, CsvCacheManager.getExams(applicationContext)) {
                CsvCacheManager.saveExams(applicationContext, it)
            }) changed = true
        }

        if (changed) {
            Log.d("SyncWorker", "Data changed and updated in background")
        }

        return androidx.work.ListenableWorker.Result.success()
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
