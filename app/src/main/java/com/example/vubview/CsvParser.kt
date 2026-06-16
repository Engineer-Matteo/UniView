package com.example.vubview

import java.util.*

object CsvParser {
    private fun parseDecimal(value: String): Float? {
        if (value.isBlank()) return null
        return try {
            val firstPart = value.split('/')[0].trim()
            val cleaned = firstPart.replace(Regex("[^0-9,.-]"), "")
            if (cleaned.isEmpty()) return null
            cleaned.replace(',', '.').toFloatOrNull()
        } catch (e: Exception) {
            null
        }
    }

    private fun detectDelimiter(text: String): Char {
        val lines = text.lines().filter { it.isNotBlank() }.take(15)
        if (lines.isEmpty()) return ','
        val delimiters = listOf(';', ',', '\t', '|')
        return delimiters.maxByOrNull { d ->
            val counts = lines.map { splitCsvLine(it, d).size }
            val valid = counts.filter { it > 1 }
            if (valid.isEmpty()) 0 else valid.groupingBy { it }.eachCount().values.maxOrNull()!! * 10 + valid.maxOrNull()!!
        } ?: ','
    }

    private fun splitCsvLine(line: String, delimiter: Char): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val char = line[i]
            if (char == '"') {
                if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                    current.append('"')
                    i++
                } else {
                    inQuotes = !inQuotes
                }
            } else if (char == delimiter && !inQuotes) {
                result.add(current.toString().trim())
                current.setLength(0)
            } else {
                current.append(char)
            }
            i++
        }
        result.add(current.toString().trim())
        return result.map { it.removePrefix("\"").removeSuffix("\"").trim() }
    }

    fun parseResultsCsv(text: String): List<ResultEntry> {
        val cleanText = text.replace("\uFEFF", "").replace("\r\n", "\n").replace("\r", "\n")
        val lines = cleanText.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (lines.isEmpty()) return emptyList()

        val delimiter = detectDelimiter(cleanText)
        val header = splitCsvLine(lines[0], delimiter)
        val isHeader = header.any { it.lowercase() in listOf("vak", "course", "grade", "score", "ects") }
        
        var courseIdx = 0; var gradeIdx = 1; var periodIdx = 2; var ectsIdx = 4
        if (isHeader) {
            header.forEachIndexed { i, s ->
                val col = s.lowercase()
                if (col.contains("vak") || col.contains("course")) courseIdx = i
                if (col.contains("grade") || col.contains("score") || col.contains("result")) gradeIdx = i
                if (col.contains("period") || col.contains("periode")) periodIdx = i
                if (col.contains("ects") || col.contains("punten")) ectsIdx = i
            }
        }

        return lines.drop(if (isHeader) 1 else 0).mapNotNull { line ->
            val cols = splitCsvLine(line, delimiter)
            if (cols.size <= courseIdx || cols[courseIdx].isBlank()) return@mapNotNull null
            ResultEntry(
                cols[courseIdx],
                cols.getOrNull(gradeIdx)?.let { parseDecimal(it) } ?: 0f,
                parsePeriod(cols.getOrNull(periodIdx) ?: ""),
                cols.getOrNull(3) ?: "",
                cols.getOrNull(ectsIdx)?.let { parseDecimal(it) } ?: 0f
            )
        }
    }

    fun parseBreakdownCsv(text: String): List<BreakdownEntry> {
        val cleanText = text.replace("\uFEFF", "").replace("\r\n", "\n").replace("\r", "\n")
        val lines = cleanText.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (lines.isEmpty()) return emptyList()

        val delimiter = detectDelimiter(cleanText)
        val header = splitCsvLine(lines[0], delimiter)
        val isHeader = header.any { it.lowercase() in listOf("vak", "course", "sub", "item", "onderdeel") }

        var courseIdx = 0; var itemIdx = 1; var scoreIdx = 2; var weightIdx = 3
        if (isHeader) {
            header.forEachIndexed { i, s ->
                val col = s.lowercase()
                if (col.contains("vak") || col.contains("course")) courseIdx = i
                if (col.contains("item") || col.contains("sub") || col.contains("deel") || col.contains("detail")) itemIdx = i
                if (col.contains("score") || col.contains("grade") || col.contains("result")) scoreIdx = i
                if (col.contains("weight") || col.contains("gewicht") || col.contains("weging")) weightIdx = i
            }
        }

        return lines.drop(if (isHeader) 1 else 0).mapNotNull { line ->
            val cols = splitCsvLine(line, delimiter)
            if (cols.size <= courseIdx || cols[courseIdx].isBlank()) return@mapNotNull null
            BreakdownEntry(
                cols[courseIdx],
                cols.getOrNull(itemIdx) ?: "Onderdeel",
                cols.getOrNull(scoreIdx)?.let { parseDecimal(it) } ?: 0f,
                cols.getOrNull(weightIdx) ?: ""
            )
        }
    }

    fun parseCoursesCsv(text: String): List<CourseEntry> {
        val cleanText = text.replace("\uFEFF", "").replace("\r\n", "\n").replace("\r", "\n")
        val lines = cleanText.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (lines.isEmpty()) return emptyList()

        val delimiter = detectDelimiter(cleanText)
        val header = splitCsvLine(lines[0], delimiter)
        // name,year,semester,ects,professor,description
        val isHeader = header.any { it.lowercase() in listOf("name", "naam", "year", "ects", "professor") }

        return lines.drop(if (isHeader) 1 else 0).mapNotNull { line ->
            val cols = splitCsvLine(line, delimiter)
            if (cols.size < 4) return@mapNotNull null
            CourseEntry(
                name = cols.getOrNull(0) ?: "",
                year = cols.getOrNull(1) ?: "",
                semester = cols.getOrNull(2) ?: "",
                ects = cols.getOrNull(3) ?: "",
                professor = cols.getOrNull(4) ?: "",
                description = cols.getOrNull(5) ?: ""
            )
        }
    }

    private fun parsePeriod(period: String): Period {
        val parts = period.trim().split(Regex("[\\s,;/-]+"))
        var semester = ""; var year = ""
        parts.forEach { part ->
            val p = part.uppercase()
            if (p.contains("SEM") || p.contains("1E") || p.contains("2E")) semester = p
            if (p.contains(Regex("\\d{4}"))) {
                if (year.isEmpty()) year = p else if (!year.contains(p)) year = "$year-$p"
            }
        }
        return Period(semester.ifEmpty { parts.firstOrNull() ?: "" }, year.ifEmpty { parts.lastOrNull() ?: "" })
    }

    fun weightedAverage(list: List<ResultEntry>): Float {
        val valid = list.filter { it.ects > 0f && it.grade > 0f }
        if (valid.isEmpty()) return 0f
        val total = valid.sumOf { (it.grade * it.ects).toDouble() }
        val weight = valid.sumOf { it.ects.toDouble() }
        return (total / weight).toFloat()
    }

    fun parseScheduleCsv(text: String) = parseEvents(text, "class")
    fun parseExamsCsv(text: String) = parseEvents(text, "exam")

    private fun parseEvents(text: String, kind: String): List<NextEvent> {
        val cleanText = text.replace("\uFEFF", "").replace("\r\n", "\n").replace("\r", "\n")
        val lines = cleanText.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (lines.isEmpty()) return emptyList()

        val delimiter = detectDelimiter(cleanText)
        val header = splitCsvLine(lines[0], delimiter)
        val isHeader = header.any { it.lowercase() in listOf("date", "datum", "time", "start") }

        var dateIdx = 0; var startIdx = 2; var endIdx = 3; var titleIdx = 4; var typeIdx = 5; var roomIdx = 6
        if (isHeader) {
            header.forEachIndexed { i, s ->
                val col = s.lowercase()
                if (col.contains("date") || col.contains("datum")) dateIdx = i
                if (col.contains("start") || col.contains("begin")) startIdx = i
                if (col.contains("end") || col.contains("eind")) endIdx = i
                if (col.contains("subject") || col.contains("vak") || col.contains("omschrijving") || col.contains("course")) titleIdx = i
                if (col.contains("type")) typeIdx = i
                if (col.contains("room") || col.contains("lokaal")) roomIdx = i
            }
        }

        return lines.drop(if (isHeader) 1 else 0).mapNotNull { line ->
            val cols = splitCsvLine(line, delimiter)
            if (cols.size <= dateIdx || cols[dateIdx].isBlank()) return@mapNotNull null
            NextEvent(
                title = cols.getOrNull(titleIdx) ?: "Event",
                date = cols[dateIdx],
                start = cols.getOrNull(startIdx) ?: "",
                end = cols.getOrNull(endIdx) ?: "",
                kind = kind,
                type = cols.getOrNull(typeIdx) ?: "",
                room = cols.getOrNull(roomIdx) ?: ""
            )
        }
    }
}
