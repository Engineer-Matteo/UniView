package com.example.uniview

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

object IcalParser {
    private val dateOnlyFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val timeOnlyFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun parse(text: String, kind: String): List<NextEvent> {
        val events = mutableListOf<NextEvent>()
        val lines = unfoldLines(text.lines())
        
        // Find global calendar timezone if specified
        var calendarTimeZone: ZoneId? = null
        for (line in lines) {
            if (line.startsWith("X-WR-TIMEZONE:")) {
                val tzName = line.substringAfter("X-WR-TIMEZONE:").trim()
                calendarTimeZone = try { ZoneId.of(tzName) } catch (e: Exception) { null }
                break
            }
        }

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
                        events.add(createEvent(currentSummary, currentStart, currentEnd, currentLocation, currentDescription, kind, calendarTimeZone))
                    }
                    inEvent = false
                }
                inEvent -> {
                    val keyPart = trimmedLine.substringBefore(":")
                    val value = trimmedLine.substringAfter(":", "")
                    
                    when {
                        keyPart.startsWith("SUMMARY") -> currentSummary = unescape(value)
                        keyPart.startsWith("DTSTART") -> currentStart = trimmedLine
                        keyPart.startsWith("DTEND") -> currentEnd = trimmedLine
                        keyPart.startsWith("LOCATION") -> currentLocation = unescape(value)
                        keyPart.startsWith("DESCRIPTION") -> currentDescription = unescape(value)
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

    private fun createEvent(summary: String, startLine: String, endLine: String, location: String, description: String, kind: String, calendarTimeZone: ZoneId?): NextEvent {
        val startZdt = parseIcalDateTime(startLine, calendarTimeZone)
        val endZdt = parseIcalDateTime(endLine, calendarTimeZone)

        // Convert to local timezone for display. This handles CEST/CET automatically.
        val localZone = ZoneId.systemDefault()
        val localStart = startZdt?.withZoneSameInstant(localZone)
        val localEnd = endZdt?.withZoneSameInstant(localZone)

        return NextEvent(
            title = summary,
            date = localStart?.format(dateOnlyFormatter) ?: "",
            start = localStart?.format(timeOnlyFormatter) ?: "",
            end = localEnd?.format(timeOnlyFormatter) ?: "",
            kind = kind,
            type = description,
            room = location
        )
    }

    private fun parseIcalDateTime(line: String, calendarTimeZone: ZoneId?): ZonedDateTime? {
        if (line.isEmpty()) return null
        return try {
            val value = line.substringAfterLast(":")
            val isUtc = value.endsWith("Z")
            val cleanValue = value.replace("Z", "")
            
            val tzid = if (line.contains("TZID=")) {
                line.substringAfter("TZID=").substringBefore(":")
            } else null

            if (cleanValue.contains("T")) {
                val dtPattern = "yyyyMMdd'T'HHmmss"
                val ldt = LocalDateTime.parse(cleanValue.take(15), DateTimeFormatter.ofPattern(dtPattern))
                
                when {
                    isUtc -> ldt.atZone(ZoneId.of("UTC"))
                    tzid != null -> try {
                        ldt.atZone(ZoneId.of(tzid))
                    } catch (e: Exception) {
                        ldt.atZone(calendarTimeZone ?: ZoneId.of("UTC")) // Fallback to calendar TZ or UTC
                    }
                    else -> ldt.atZone(calendarTimeZone ?: ZoneId.of("UTC")) // If no TZ info, assume calendar TZ or UTC
                }
            } else if (cleanValue.length >= 8) {
                // All day event
                val ldt = LocalDateTime.parse(cleanValue.take(8) + "T000000", DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss"))
                ldt.atZone(ZoneId.systemDefault())
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
