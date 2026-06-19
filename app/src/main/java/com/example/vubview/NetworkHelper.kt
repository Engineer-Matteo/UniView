package com.example.vubview

import android.util.Log
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

object NetworkHelper {
    private const val TAG = "NetworkHelper"

    fun fetchUrl(urlString: String): String {
        Log.d(TAG, "Fetching URL: $urlString")
        var currentUrl = urlString
        var connection: HttpURLConnection? = null
        
        try {
            // Manually follow redirects (up to 5 times)
            for (i in 0..5) {
                val url = URL(currentUrl)
                connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 15000
                connection.readTimeout = 15000
                connection.instanceFollowRedirects = true // Try auto first
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                
                val responseCode = connection.responseCode
                Log.d(TAG, "Response Code: $responseCode for $currentUrl")
                
                if (responseCode == HttpURLConnection.HTTP_MOVED_TEMP || 
                    responseCode == HttpURLConnection.HTTP_MOVED_PERM || 
                    responseCode == 307 || responseCode == 308) {
                    val newUrl = connection.getHeaderField("Location")
                    Log.d(TAG, "Redirecting to: $newUrl")
                    currentUrl = newUrl
                    connection.disconnect()
                    continue
                }
                
                if (responseCode !in 200..299) {
                    throw Exception("Server returned code: $responseCode")
                }
                
                val content = connection.inputStream.bufferedReader().use(BufferedReader::readText)
                
                // If it looks like HTML, Google Drive might be showing a "scanning for viruses" page or login page
                if (content.trim().startsWith("<!DOCTYPE") || content.trim().startsWith("<html")) {
                    Log.w(TAG, "Received HTML instead of JSON. Check if your Drive link is a direct download link.")
                }
                
                return content
            }
            throw Exception("Too many redirects")
        } finally {
            connection?.disconnect()
        }
    }
}
