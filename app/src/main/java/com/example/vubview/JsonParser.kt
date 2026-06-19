package com.example.vubview

import org.json.JSONArray
import org.json.JSONObject

object JsonParser {
    fun parseMainJson(jsonStr: String): Triple<List<ResultEntry>, List<BreakdownEntry>, List<CourseEntry>> {
        val results = mutableListOf<ResultEntry>()
        val breakdowns = mutableListOf<BreakdownEntry>()
        val courses = mutableListOf<CourseEntry>()

        try {
            val root = JSONArray(jsonStr)
            for (i in 0 until root.length()) {
                val courseObj = root.getJSONObject(i)
                val courseName = courseObj.optString("courseName", "")
                val ects = courseObj.optString("ects", "0").replace(",", ".").toFloatOrNull() ?: 0f
                val score = courseObj.optString("score", "").replace(",", ".").toFloatOrNull() ?: 0f
                val description = courseObj.optString("description", "")
                val professor = courseObj.optString("professor", "")
                
                // For existing UI compatibility, use default period if missing
                val year = courseObj.optString("year", "2023-2024")
                val semester = courseObj.optString("semester", "Onbekend")

                // 1. Create CourseEntry
                courses.add(CourseEntry(
                    name = courseName,
                    year = year,
                    semester = semester,
                    ects = courseObj.optString("ects", "0"),
                    professor = professor,
                    description = description
                ))

                // 2. Create ResultEntry (if score is present)
                if (courseObj.has("score") && courseObj.getString("score").isNotBlank()) {
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
                        val pWeight = partial.optString("weight", "")
                        val pScoreStr = partial.optString("score", "")
                        val pScore = pScoreStr.replace(",", ".").toFloatOrNull() ?: 0f
                        
                        breakdowns.add(BreakdownEntry(
                            course = courseName,
                            subTitle = pName,
                            score = pScore,
                            weight = if (pWeight.isNotEmpty()) "${(pWeight.toFloatOrNull() ?: 0f) * 100}%" else ""
                        ))
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return Triple(results, breakdowns, courses)
    }
}
