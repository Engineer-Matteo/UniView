package com.example.vubview

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.vubview.databinding.ActivityResultsBinding
import java.util.*

class ResultsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityResultsBinding
    private val adapter = ResultsAdapter { showBreakdownDialog(it) }
    private lateinit var dataStore: VubPreferences
    private val allResults = mutableListOf<ResultEntry>()
    private val allBreakdowns = mutableListOf<BreakdownEntry>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityResultsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        dataStore = VubPreferences(this)
        binding.resultsRecycler.layoutManager = LinearLayoutManager(this)
        binding.resultsRecycler.adapter = adapter

        binding.backToHome.setOnClickListener { finish() }

        binding.filterYear.onItemSelectedListener = SimpleSpinnerListener { loadFilteredResults() }
        binding.filterSemester.onItemSelectedListener = SimpleSpinnerListener { loadFilteredResults() }

        binding.searchCourse.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                loadFilteredResults()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        refreshResults()
    }

    private fun refreshResults() {
        binding.progressBar.visibility = View.VISIBLE
        Thread {
            val resultsUrl = dataStore.resultsUrl
            val breakdownUrl = dataStore.breakdownUrl
            
            val resultsText = if (!resultsUrl.isNullOrBlank()) NetworkHelper.fetchUrl(resultsUrl) else ""
            val breakdownText = if (!breakdownUrl.isNullOrBlank()) NetworkHelper.fetchUrl(breakdownUrl) else ""
            
            val rows = CsvParser.parseResultsCsv(resultsText)
            val breakdownRows = CsvParser.parseBreakdownCsv(breakdownText)
            
            allResults.clear()
            allResults.addAll(rows)
            
            allBreakdowns.clear()
            allBreakdowns.addAll(breakdownRows)

            val years = allResults.map { it.period.year }.filter { it.isNotBlank() }.distinct().sorted()
            val semesters = allResults.map { it.period.semester }.filter { it.isNotBlank() }.distinct().sorted()

            runOnUiThread {
                binding.filterYear.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, listOf(getString(R.string.all_years)) + years).apply {
                    setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                }
                binding.filterSemester.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, listOf(getString(R.string.all_semesters)) + semesters).apply {
                    setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                }
                loadFilteredResults()
                binding.progressBar.visibility = View.GONE
            }
        }.start()
    }

    private fun loadFilteredResults() {
        val year = binding.filterYear.selectedItem?.toString().takeIf { it != getString(R.string.all_years) }
        val semester = binding.filterSemester.selectedItem?.toString().takeIf { it != getString(R.string.all_semesters) }
        val query = binding.searchCourse.text.toString().lowercase()

        val filtered = allResults.filter { result ->
            val matchesYear = year?.let { result.period.year == it } ?: true
            val matchesSemester = semester?.let { result.period.semester == it } ?: true
            val matchesQuery = result.course.lowercase().contains(query)
            matchesYear && matchesSemester && matchesQuery
        }
        adapter.submitList(filtered)
        binding.emptyView.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE

        val average = CsvParser.weightedAverage(filtered)
        binding.averageBadge.text = String.format(Locale("nl", "BE"), "gem. %.1f /20", average)
    }

    private fun showBreakdownDialog(result: ResultEntry) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_breakdown, null)
        val dialog = AlertDialog.Builder(this, android.R.style.Theme_Translucent_NoTitleBar)
            .setView(dialogView)
            .create()

        val locale = Locale("nl", "BE")
        dialogView.findViewById<TextView>(R.id.breakdownCourseTitle).text = result.course
        dialogView.findViewById<TextView>(R.id.breakdownFinalScore).text = String.format(locale, "%.1f / 20", result.grade)
        dialogView.findViewById<TextView>(R.id.breakdownPeriod).text = "${result.period.semester} ${result.period.year}"
        dialogView.findViewById<TextView>(R.id.breakdownEcts).text = String.format(locale, "%.1f studiepunten", result.ects)
        
        val container = dialogView.findViewById<LinearLayout>(R.id.breakdownItemsContainer)
        // Use lenient matching for course name
        val items = allBreakdowns.filter { it.course.trim().equals(result.course.trim(), ignoreCase = true) }

        if (items.isEmpty()) {
            val emptyText = TextView(this)
            emptyText.text = "Geen deelscores gevonden."
            emptyText.setPadding(0, 16, 0, 0)
            container.addView(emptyText)
        } else {
            items.forEach { item ->
                val itemView = LayoutInflater.from(this).inflate(R.layout.item_breakdown, container, false)
                itemView.findViewById<TextView>(R.id.breakdownItemTitle).text = item.subTitle
                itemView.findViewById<TextView>(R.id.breakdownItemScore).text = String.format(locale, "%.1f / 20 (%s)", item.score, item.weight)
                
                val progress = itemView.findViewById<ProgressBar>(R.id.breakdownItemProgress)
                progress.progress = (item.score * 5).toInt()
                
                val progressDrawable = when {
                    item.score < 10 -> R.drawable.bg_progress_red
                    item.score < 14 -> R.drawable.bg_progress_orange
                    else -> R.drawable.bg_progress_green
                }
                progress.progressDrawable = ContextCompat.getDrawable(this, progressDrawable)
                
                container.addView(itemView)
            }
        }

        dialogView.findViewById<ImageView>(R.id.breakdownClose).setOnClickListener { dialog.dismiss() }
        dialog.show()
    }
}
