package com.example.vubview

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

object JsonParser {
    private const val TAG = "JsonParser"

    fun parseMainJson(jsonStr: String): Triple<List<ResultEntry>, List<BreakdownEntry>, List<CourseEntry>> {
        val results = mutableListOf<ResultEntry>()
        val breakdowns = mutableListOf<BreakdownEntry>()
        val courses = mutableListOf<CourseEntry>()

        if (jsonStr.isBlank()) {
            Log.w(TAG, "JSON string is blank")
            return Triple(results, breakdowns, courses)
        }

        try {
            // Remove Byte Order Mark (BOM) and common invisible characters
            val cleanStr = jsonStr.replace("\uFEFF", "").trim()
            
            // Find the actual start of JSON content
            val startIndex = cleanStr.indexOfAny(charArrayOf('[', '{'))
            if (startIndex == -1) {
                Log.e(TAG, "No JSON start character found in response")
                return Triple(results, breakdowns, courses)
            }
            
            // Sanitize common syntax errors (trailing/missing commas)
            val sanitized = sanitizeJson(cleanStr.substring(startIndex))
            Log.d(TAG, "Starting to parse sanitized JSON of length: ${sanitized.length}")
            
            val root = JSONArray(sanitized)
            
            for (i in 0 until root.length()) {
                val courseObj = root.getJSONObject(i)
                
                val courseName = courseObj.optString("courseName", "Onbekend")
                val ectsStr = courseObj.optString("ects", "0")
                // ECTS is an Int. Strip decimal parts if present.
                val ects = ectsStr.replace(",", ".").split(".")[0].toIntOrNull() ?: 0
                
                val scoreStr = courseObj.optString("score", "")
                val score = scoreStr.replace(",", ".").toFloatOrNull() ?: 0f
                val description = courseObj.optString("description", "")
                val professor = courseObj.optString("professor", "")
                
                val year = courseObj.optString("year", "2024-2025")
                val semester = courseObj.optString("semester", "1")
                val program = courseObj.optString("program", "")
                val location = courseObj.optString("location", "")

                // 1. Create CourseEntry
                courses.add(CourseEntry(
                    name = courseName,
                    year = year,
                    semester = semester,
                    ects = ects,
                    professor = professor,
                    description = description,
                    program = program,
                    location = location
                ))

                // 2. Create ResultEntry (only if score is not empty)
                if (scoreStr.isNotBlank()) {
                    results.add(ResultEntry(
                        course = courseName,
                        grade = score,
                        period = Period(semester, year),
                        notes = "",
                        ects = ects
                    ))
                }

                // 3. Create BreakdownEntries
                if (courseObj.has("partials")) {
                    val partials = courseObj.getJSONArray("partials")
                    for (j in 0 until partials.length()) {
                        val partial = partials.getJSONObject(j)
                        val pName = partial.optString("name", "Deel")
                        val pWeightStr = partial.optString("weight", "")
                        val pScoreStr = partial.optString("score", "")
                        val pScore = pScoreStr.replace(",", ".").toFloatOrNull() ?: 0f
                        
                        val weightDisplay = try {
                            if (pWeightStr.isNotEmpty()) {
                                val w = pWeightStr.replace(",", ".").toFloat()
                                "${(w * 100).toInt()}%"
                            } else ""
                        } catch (e: Exception) {
                            pWeightStr
                        }

                        breakdowns.add(BreakdownEntry(
                            course = courseName,
                            subTitle = pName,
                            score = pScore,
                            weight = weightDisplay
                        ))
                    }
                }
            }
            Log.d(TAG, "Successfully parsed ${courses.size} courses, ${results.size} results, ${breakdowns.size} partials")
        } catch (e: Exception) {
            Log.e(TAG, "JSON Syntax Error: ${e.message}")
            if (jsonStr.length > 50) {
                Log.e(TAG, "Preview: ${jsonStr.take(100)}")
            }
            e.printStackTrace()
        }

        return Triple(results, breakdowns, courses)
    }

    /**
     * Attempts to fix common JSON syntax errors.
     * Escaping ] and } to avoid PatternSyntaxException on some Android versions.
     */
    private fun sanitizeJson(json: String): String {
        return json.trim()
            .replace(Regex(",\\s*\\]"), "]")
            .replace(Regex(",\\s*\\}"), "}")
            .replace(Regex("(\"|\\d|true|false|null)\\s*\\n\\s*\""), "$1,\n\"")
    }
}
