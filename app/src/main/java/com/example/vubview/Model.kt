package com.example.vubview

import java.text.SimpleDateFormat
import java.util.*

data class Period(val semester: String, val year: String)

data class ResultEntry(
    val course: String,
    val grade: Float,
    val period: Period,
    val notes: String,
    val ects: Float
)

data class BreakdownEntry(
    val course: String,
    val subTitle: String,
    val score: Float,
    val weight: String
)

data class CourseEntry(
    val name: String,
    val year: String,
    val semester: String,
    val ects: String,
    val professor: String,
    val description: String
)

data class NextEvent(
    val title: String,
    val date: String,
    val start: String,
    val end: String,
    val kind: String,
    val type: String = "",
    val room: String = ""
) {
    fun isUpcoming(): Boolean {
        return try {
            val eventDate = dateTimeMillis()
            if (eventDate == 0L) return true
            
            val cal = Calendar.getInstance()
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            
            // Upcoming means today or in the future
            eventDate >= cal.timeInMillis
        } catch (e: Exception) {
            true
        }
    }

    fun dateTimeMillis(): Long {
        return try {
            // Match any non-digit separator
            val parts = date.split(Regex("[^0-9]+")).filter { it.isNotBlank() }.map { it.toInt() }
            if (parts.size < 3) return 0L
            
            val cal = Calendar.getInstance()
            val y: Int
            val m: Int
            val d: Int
            
            if (parts[0] > 1000) { // YYYY-MM-DD
                y = parts[0]
                m = parts[1] - 1
                d = parts[2]
            } else if (parts[2] > 1000) { // DD-MM-YYYY
                y = parts[2]
                m = parts[1] - 1
                d = parts[0]
            } else {
                // assume DD-MM-YY (20xx)
                d = parts[0]
                m = parts[1] - 1
                y = 2000 + parts[2]
            }
            
            cal.set(y, m, d)

            // Parse time if available
            val timeParts = start.split(Regex("[^0-9]+")).filter { it.isNotBlank() }.map { it.toInt() }
            if (timeParts.size >= 2) {
                cal.set(Calendar.HOUR_OF_DAY, timeParts[0])
                cal.set(Calendar.MINUTE, timeParts[1])
            } else {
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
            }
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            cal.timeInMillis
        } catch (e: Exception) {
            0L
        }
    }

    fun formattedDate(): String {
        return try {
            val millis = dateTimeMillis()
            if (millis == 0L) return date
            val dutchLocale = Locale("nl", "BE")
            val sdf = SimpleDateFormat("EEEE d MMMM yyyy", dutchLocale)
            val result = sdf.format(Date(millis))
            result.replaceFirstChar { if (it.isLowerCase()) it.titlecase(dutchLocale) else it.toString() }
        } catch (e: Exception) {
            date
        }
    }

    fun dateLabel(): String = formattedDate()
    fun timeLabel(): String = start
}
