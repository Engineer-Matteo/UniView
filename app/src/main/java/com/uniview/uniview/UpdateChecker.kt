package com.uniview.uniview

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.content.pm.PackageManager
import android.util.Log
import androidx.appcompat.app.AlertDialog
import org.json.JSONArray
import org.json.JSONObject

object UpdateChecker {
    private const val TAG = "UpdateChecker"
    private const val GITHUB_API_URL = "https://api.github.com/repos/Engineer-Matteo/UniView/releases"

    fun checkForUpdates(context: Context) {
        Thread {
            try {
                val currentVersion = try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0)).versionName
                    } else {
                        @Suppress("DEPRECATION")
                        context.packageManager.getPackageInfo(context.packageName, 0).versionName
                    } ?: "0.0.0"
                } catch (e: Exception) { "0.0.0" }

                Log.d(TAG, "Local version: $currentVersion")
                Log.d(TAG, "Fetching updates from: $GITHUB_API_URL")
                
                val response = NetworkHelper.fetchUrl(GITHUB_API_URL)
                if (response.isBlank()) {
                    Log.w(TAG, "Empty response from GitHub")
                    return@Thread
                }

                val releases = JSONArray(response)
                if (releases.length() == 0) {
                    Log.d(TAG, "No releases found")
                    return@Thread
                }

                // GitHub API returns releases sorted by creation date (descending)
                val latestRelease = releases.getJSONObject(0)
                val latestVersionTag = latestRelease.getString("tag_name")
                val downloadUrl = latestRelease.getString("html_url")
                
                Log.d(TAG, "Latest remote version: $latestVersionTag")
                
                if (isNewerVersion(currentVersion, latestVersionTag)) {
                    Log.i(TAG, "Update available! Showing dialog.")
                    (context as? MainActivity)?.runOnUiThread {
                        if (!(context as MainActivity).isFinishing && !(context as MainActivity).isDestroyed) {
                            showUpdateDialog(context, latestVersionTag, downloadUrl)
                        }
                    }
                } else {
                    Log.d(TAG, "App is up to date.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Update check failed: ${e.message}", e)
            }
        }.start()
    }

    private fun isNewerVersion(current: String, latest: String): Boolean {
        val curr = current.removePrefix("v").trim()
        val late = latest.removePrefix("v").trim()
        
        if (curr == late) return false
        
        try {
            val currParts = curr.split("-")
            val lateParts = late.split("-")
            
            // Compare main version numbers (e.g., 0.3.1 vs 0.3.0)
            val currNums = currParts[0].split(".").map { it.toIntOrNull() ?: 0 }
            val lateNums = lateParts[0].split(".").map { it.toIntOrNull() ?: 0 }
            
            val maxParts = maxOf(currNums.size, lateNums.size)
            for (i in 0 until maxParts) {
                val c = currNums.getOrElse(i) { 0 }
                val l = lateNums.getOrElse(i) { 0 }
                if (l > c) return true
                if (l < c) return false
            }
            
            // Numbers are equal, handle suffixes (e.g., 0.3.0 vs 0.3.0-beta)
            val currHasSuffix = currParts.size > 1
            val lateHasSuffix = lateParts.size > 1
            
            // Stable is newer than beta (0.3.0 > 0.3.0-beta)
            if (currHasSuffix && !lateHasSuffix) return true
            if (!currHasSuffix && lateHasSuffix) return false
            
            // Both have suffixes (beta.2 vs beta.1)
            if (currHasSuffix && lateHasSuffix) {
                return compareSuffix(currParts[1], lateParts[1]) > 0
            }
        } catch (e: Exception) {
            Log.e(TAG, "Comparison error", e)
            return late != curr
        }
        return false
    }

    private fun compareSuffix(curr: String, late: String): Int {
        val currParts = curr.split(".")
        val lateParts = late.split(".")
        for (i in 0 until maxOf(currParts.size, lateParts.size)) {
            val c = currParts.getOrNull(i) ?: ""
            val l = lateParts.getOrNull(i) ?: ""
            val cNum = c.filter { it.isDigit() }.toIntOrNull()
            val lNum = l.filter { it.isDigit() }.toIntOrNull()
            
            if (cNum != null && lNum != null) {
                if (lNum != cNum) return lNum - cNum
            } else if (c != l) {
                return l.compareTo(c)
            }
        }
        return 0
    }

    private fun showUpdateDialog(context: Context, version: String, url: String) {
        AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.update_available_title))
            .setMessage(context.getString(R.string.update_available_message, version))
            .setPositiveButton(context.getString(R.string.update_btn_download)) { _, _ ->
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                } catch (e: Exception) {
                    Log.e(TAG, "Browser error", e)
                }
            }
            .setNegativeButton(context.getString(R.string.update_btn_later), null)
            .show()
    }
}
