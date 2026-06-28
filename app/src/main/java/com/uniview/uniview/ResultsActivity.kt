package com.uniview.uniview

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.uniview.uniview.databinding.ActivityResultsBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.*

class ResultsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityResultsBinding
    private val adapter = ResultsAdapter { showBreakdownDialog(it) }
    private lateinit var dataStore: Preferences
    private val allResults = mutableListOf<ResultEntry>()
    private val allBreakdowns = mutableListOf<BreakdownEntry>()

    override fun onCreate(savedInstanceState: Bundle?) {
        dataStore = Preferences(this)
        dataStore.applyTheme()

        super.onCreate(savedInstanceState)
        binding = ActivityResultsBinding.inflate(layoutInflater)
        setContentView(binding.root)

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

        setupNavigation()
        loadCachedData()
        refreshResults()
    }

    private fun setupNavigation() {
        binding.navHome.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            startActivity(intent)
        }
        binding.navSchedule.setOnClickListener {
            val intent = Intent(this, ScheduleActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            startActivity(intent)
        }
        binding.navExams.setOnClickListener {
            val intent = Intent(this, ExamsActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            startActivity(intent)
        }
        binding.navResults.setOnClickListener {
            // Already here
        }
        binding.navCourses.setOnClickListener {
            val intent = Intent(this, CoursesActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            startActivity(intent)
        }
    }

    private fun loadCachedData() {
        val cachedJson = DataCacheManager.getMainJson(this)
        if (cachedJson.isNotBlank()) {
            val (results, breakdowns, _) = JsonParser.parseMainJson(cachedJson)
            allResults.clear()
            allResults.addAll(results)
            allBreakdowns.clear()
            allBreakdowns.addAll(breakdowns)
            updateFilters()
            loadFilteredResults()
        }
    }

    private fun refreshResults() {
        val jsonUrl = dataStore.mainJsonUrl
        if (jsonUrl.isNullOrBlank()) return

        binding.progressBar.visibility = View.VISIBLE
        Thread {
            try {
                val jsonText = NetworkHelper.fetchUrl(jsonUrl)
                if (jsonText.isNotBlank()) {
                    DataCacheManager.saveMainJson(this, jsonText)
                    val (results, breakdowns, _) = JsonParser.parseMainJson(jsonText)
                    allResults.clear()
                    allResults.addAll(results)
                    allBreakdowns.clear()
                    allBreakdowns.addAll(breakdowns)
                }

                runOnUiThread {
                    updateFilters()
                    loadFilteredResults()
                    binding.progressBar.visibility = View.GONE
                }
            } catch (e: Exception) {
                runOnUiThread {
                    binding.progressBar.visibility = View.GONE
                }
            }
        }.start()
    }

    private fun updateFilters() {
        val years = allResults.map { it.period.year }.filter { it.isNotBlank() }.distinct().sorted()
        val semesters = allResults.map { it.period.semester }.filter { it.isNotBlank() }.distinct().sorted()

        binding.filterYear.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, listOf(getString(R.string.all_years)) + years).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        binding.filterSemester.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, listOf(getString(R.string.all_semesters)) + semesters).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
    }

    private fun loadFilteredResults() {
        val allYearsText = getString(R.string.all_years)
        val allSemestersText = getString(R.string.all_semesters)

        val year = binding.filterYear.selectedItem?.toString().takeIf { it != allYearsText }
        val semester = binding.filterSemester.selectedItem?.toString().takeIf { it != allSemestersText }
        val query = binding.searchCourse.text.toString().lowercase()

        val filtered = allResults.filter { result ->
            val matchesYear = year?.let { result.period.year == it } ?: true
            val matchesSemester = semester?.let { result.period.semester == it } ?: true
            val matchesQuery = result.course.lowercase().contains(query)
            matchesYear && matchesSemester && matchesQuery
        }
        adapter.submitList(filtered)
        binding.emptyView.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE

        val average = ResultUtils.calculateWeightedAverage(filtered)
        binding.averageBadge.text = String.format(Locale(getString(R.string.locale_lang), getString(R.string.locale_country)), getString(R.string.average_score_text), average)
    }

    private fun showBreakdownDialog(result: ResultEntry) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_breakdown, null)
        
        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .create()
            
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val locale = Locale(getString(R.string.locale_lang), getString(R.string.locale_country))
        dialogView.findViewById<TextView>(R.id.breakdownCourseTitle).text = result.course.ifBlank { getString(R.string.course_name_unknown) }
        dialogView.findViewById<TextView>(R.id.breakdownFinalScore).text = String.format(locale, getString(R.string.format_score_over_20), result.grade)
        dialogView.findViewById<TextView>(R.id.breakdownPeriod).text = getString(R.string.format_semester_year, result.period.semester, result.period.year)
        dialogView.findViewById<TextView>(R.id.breakdownEcts).text = getString(R.string.label_ects_count, result.ects)
        
        val container = dialogView.findViewById<LinearLayout>(R.id.breakdownItemsContainer)
        val items = allBreakdowns.filter { it.course.trim().equals(result.course.trim(), ignoreCase = true) }

        if (items.isEmpty()) {
            val emptyText = TextView(this)
            emptyText.text = getString(R.string.empty_breakdown)
            emptyText.setPadding(0, 16, 0, 0)
            container.addView(emptyText)
        } else {
            items.forEach { item ->
                val itemView = LayoutInflater.from(this).inflate(R.layout.item_breakdown, container, false)
                itemView.findViewById<TextView>(R.id.breakdownItemTitle).text = item.subTitle.ifBlank { getString(R.string.partial_name_default) }
                itemView.findViewById<TextView>(R.id.breakdownItemScore).text = String.format(locale, getString(R.string.format_score_weight), item.score, item.weight)
                
                val progress = itemView.findViewById<ProgressBar>(R.id.breakdownItemProgress)
                progress.progress = (item.score * 5).toInt()
                
                val progressDrawable = when {
                    item.score < 10 -> R.drawable.bg_progress_red
                    item.score < 15 -> R.drawable.bg_progress_yellow
                    else -> R.drawable.bg_progress_green
                }
                progress.progressDrawable = ContextCompat.getDrawable(this, progressDrawable)
                
                container.addView(itemView)
            }
        }

        dialogView.findViewById<ImageView>(R.id.breakdownClose).setOnClickListener { dialog.dismiss() }
        
        dialog.show()

        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }
}
