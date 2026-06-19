package com.example.vubview

import java.text.SimpleDateFormat
import java.util.*

object IcalParser {
    private val icalFormat = SimpleDateFormat("yyyyMMdd'T'HHmmss", Locale.US)
    private val dateOnlyFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val timeOnlyFormat = SimpleDateFormat("HH:mm", Locale.US)

    fun parse(text: String, kind: String): List<NextEvent> {
        val events = mutableListOf<NextEvent>()
        val lines = unfoldLines(text.lines())
        
        var currentSummary = ""
        var currentStart = ""
        var currentEnd = ""
        var currentLocation = ""
        var currentDescription = ""
        var inEvent = false

        for (line in lines) {
            val trimmedLine = line.trim()
            if (trimmedLine.isEmpty()) continue

            when {
                trimmedLine.startsWith("BEGIN:VEVENT") -> {
                    inEvent = true
                    currentSummary = ""
                    currentStart = ""
                    currentEnd = ""
                    currentLocation = ""
                    currentDescription = ""
                }
                trimmedLine.startsWith("END:VEVENT") -> {
                    if (inEvent) {
                        events.add(createEvent(currentSummary, currentStart, currentEnd, currentLocation, currentDescription, kind))
                    }
                    inEvent = false
                }
                inEvent -> {
                    val key = trimmedLine.substringBefore(":")
                    val value = trimmedLine.substringAfter(":", "")
                    
                    when {
                        key.startsWith("SUMMARY") -> currentSummary = unescape(value)
                        key.startsWith("DTSTART") -> currentStart = value
                        key.startsWith("DTEND") -> currentEnd = value
                        key.startsWith("LOCATION") -> currentLocation = unescape(value)
                        key.startsWith("DESCRIPTION") -> currentDescription = unescape(value)
                    }
                }
            }
        }
        return events
    }

    private fun unfoldLines(lines: List<String>): List<String> {
        val unfolded = mutableListOf<String>()
        for (line in lines) {
            if (line.startsWith(" ") || line.startsWith("\t")) {
                if (unfolded.isNotEmpty()) {
                    val last = unfolded.removeAt(unfolded.size - 1)
                    unfolded.add(last + line.substring(1))
                }
            } else {
                unfolded.add(line)
            }
        }
        return unfolded
    }

    private fun createEvent(summary: String, startStr: String, endStr: String, location: String, description: String, kind: String): NextEvent {
        val startDate = parseIcalDate(startStr)
        val endDate = parseIcalDate(endStr)

        return NextEvent(
            title = summary,
            date = startDate?.let { dateOnlyFormat.format(it) } ?: "",
            start = startDate?.let { timeOnlyFormat.format(it) } ?: "",
            end = endDate?.let { timeOnlyFormat.format(it) } ?: "",
            kind = kind,
            type = description,
            room = location
        )
    }

    private fun parseIcalDate(dateStr: String): Date? {
        if (dateStr.isEmpty()) return null
        return try {
            val cleanDate = dateStr.replace("Z", "").substringBefore(";")
            if (cleanDate.contains("T")) {
                icalFormat.parse(cleanDate.take(15))
            } else if (cleanDate.length >= 8) {
                // YYYYMMDD format (all day event)
                SimpleDateFormat("yyyyMMdd", Locale.US).parse(cleanDate.take(8))
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun unescape(text: String): String {
        return text.replace("\\n", "\n")
            .replace("\\N", "\n")
            .replace("\\,", ",")
            .replace("\\;", ";")
            .replace("\\\\", "\\")
            .trim()
    }
}
