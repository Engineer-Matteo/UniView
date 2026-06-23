package com.uniview.uniview

import android.content.Context
import android.content.Intent
import android.net.Uri
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
                Log.d(TAG, "Checking for updates at $GITHUB_API_URL")
                val response = NetworkHelper.fetchUrl(GITHUB_API_URL)
                if (response.isBlank()) return@Thread

                val releases = JSONArray(response)
                if (releases.length() == 0) return@Thread

                // Get the most recent release
                val latestRelease = releases.getJSONObject(0)
                val latestVersionTag = latestRelease.getString("tag_name")
                val latestVersion = latestVersionTag.removePrefix("v")
                val downloadUrl = latestRelease.getString("html_url")
                
                val currentVersion = try {
                    context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0.0.0"
                } catch (e: Exception) { "0.0.0" }
                
                Log.d(TAG, "Version comparison: Local=$currentVersion, Remote=$latestVersion")
                
                if (isNewerVersion(currentVersion, latestVersion)) {
                    (context as? MainActivity)?.runOnUiThread {
                        if (!(context as MainActivity).isFinishing && !(context as MainActivity).isDestroyed) {
                            showUpdateDialog(context, latestVersion, downloadUrl)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Update check failed: ${e.message}")
            }
        }.start()
    }

    /**
     * Compares version strings. Handles basic semver and beta tags.
     * Returns true if [latest] is a higher version than [current].
     */
    private fun isNewerVersion(current: String, latest: String): Boolean {
        try {
            // Clean versions (e.g. "1.2-beta" -> "1.2", "0.2.0-beta.1" -> "0.2.0")
            val currClean = current.split("-")[0].split(".").map { it.toIntOrNull() ?: 0 }
            val lateClean = latest.split("-")[0].split(".").map { it.toIntOrNull() ?: 0 }
            
            val maxParts = maxOf(currClean.size, lateClean.size)
            for (i in 0 until maxParts) {
                val c = currClean.getOrElse(i) { 0 }
                val l = lateClean.getOrElse(i) { 0 }
                if (l > c) return true
                if (l < c) return false
            }
            
            // If numbers match exactly, check for beta vs stable
            // Usually, "1.2" is newer than "1.2-beta"
            if (current.contains("-") && !latest.contains("-") && current.startsWith(latest)) return true
            
            return false
        } catch (e: Exception) {
            return latest != current && !current.startsWith(latest)
        }
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
