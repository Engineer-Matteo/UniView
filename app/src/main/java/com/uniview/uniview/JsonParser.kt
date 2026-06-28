package com.uniview.uniview

import android.util.Log
import org.json.JSONArray

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
            val cleanStr = jsonStr.replace("\uFEFF", "").trim()
            val startIndex = cleanStr.indexOfAny(charArrayOf('[', '{'))
            if (startIndex == -1) return Triple(results, breakdowns, courses)
            
            val sanitized = sanitizeJson(cleanStr.substring(startIndex))
            val root = JSONArray(sanitized)
            
            for (i in 0 until root.length()) {
                val courseObj = root.getJSONObject(i)
                
                val courseName = courseObj.optString("courseName", "")
                val ectsStr = courseObj.optString("ects", "0")
                val ects = ectsStr.replace(",", ".").split(".")[0].toIntOrNull() ?: 0
                
                val scoreStr = courseObj.optString("score", "")
                val score = scoreStr.replace(",", ".").toFloatOrNull() ?: 0f
                val description = courseObj.optString("description", "")
                val professor = courseObj.optString("professor", "")
                
                val year = courseObj.optString("year", "")
                val semester = courseObj.optString("semester", "")
                val program = courseObj.optString("program", "")
                val location = courseObj.optString("location", "")

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

                // The critical logic: only add to results if a score exists
                if (scoreStr.isNotBlank()) {
                    results.add(ResultEntry(
                        course = courseName,
                        grade = score,
                        period = Period(semester, year),
                        notes = "",
                        ects = ects
                    ))
                }

                if (courseObj.has("partials")) {
                    val partials = courseObj.getJSONArray("partials")
                    for (j in 0 until partials.length()) {
                        val partial = partials.getJSONObject(j)
                        val pName = partial.optString("name", "")
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
        } catch (e: Exception) {
            Log.e(TAG, "JSON Error", e)
        }

        return Triple(results, breakdowns, courses)
    }

    private fun sanitizeJson(json: String): String {
        return json.trim()
            .replace(Regex(",\\s*\\]"), "]")
            .replace(Regex(",\\s*\\}"), "}")
            .replace(Regex("(\"|\\d|true|false|null)\\s*\\n\\s*\""), "$1,\n\"")
    }
}
