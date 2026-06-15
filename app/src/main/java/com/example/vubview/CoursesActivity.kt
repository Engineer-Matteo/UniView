package com.example.vubview

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.vubview.databinding.ActivityCoursesBinding

class CoursesActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCoursesBinding
    private lateinit var dataStore: VubPreferences
    private val adapter = ResultsAdapter { showBreakdownDialog(it) }
    private val allBreakdowns = mutableListOf<BreakdownEntry>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCoursesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        dataStore = VubPreferences(this)
        
        binding.backToHome.setOnClickListener { finish() }
        
        binding.coursesRecycler.layoutManager = LinearLayoutManager(this)
        binding.coursesRecycler.adapter = adapter

        binding.progressBar.visibility = View.VISIBLE

        loadCourses()
    }

    private fun loadCourses() {
        val resultsUrl = dataStore.resultsUrl
        val breakdownUrl = dataStore.breakdownUrl
        
        if (resultsUrl.isNullOrBlank()) {
            binding.emptyView.text = getString(R.string.no_url_defined)
            binding.emptyView.visibility = View.VISIBLE
            binding.progressBar.visibility = View.GONE
            return
        }

        Thread {
            val resultsText = NetworkHelper.fetchUrl(resultsUrl)
            val breakdownText = if (!breakdownUrl.isNullOrBlank()) NetworkHelper.fetchUrl(breakdownUrl) else ""
            
            val items = CsvParser.parseResultsCsv(resultsText)
            val breakdowns = CsvParser.parseBreakdownCsv(breakdownText)
            
            allBreakdowns.clear()
            allBreakdowns.addAll(breakdowns)
            
            runOnUiThread {
                adapter.submitList(items)
                binding.emptyView.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
                binding.progressBar.visibility = View.GONE
            }
        }.start()
    }

    private fun showBreakdownDialog(result: ResultEntry) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_breakdown, null)
        val dialog = AlertDialog.Builder(this, android.R.style.Theme_Translucent_NoTitleBar)
            .setView(dialogView)
            .create()

        dialogView.findViewById<TextView>(R.id.breakdownCourseTitle).text = result.course
        dialogView.findViewById<TextView>(R.id.breakdownFinalScore).text = String.format("%.1f / 20", result.grade)
        dialogView.findViewById<TextView>(R.id.breakdownPeriod).text = "${result.period.semester} ${result.period.year}"
        dialogView.findViewById<TextView>(R.id.breakdownEcts).text = "${result.ects} studiepunten"
        
        val container = dialogView.findViewById<LinearLayout>(R.id.breakdownItemsContainer)
        val items = allBreakdowns.filter { it.course == result.course }

        if (items.isEmpty()) {
            val emptyText = TextView(this)
            emptyText.text = "Geen deelscores gevonden."
            emptyText.setPadding(0, 16, 0, 0)
            container.addView(emptyText)
        } else {
            items.forEach { item ->
                val itemView = LayoutInflater.from(this).inflate(R.layout.item_breakdown, container, false)
                itemView.findViewById<TextView>(R.id.breakdownItemTitle).text = item.subTitle
                itemView.findViewById<TextView>(R.id.breakdownItemScore).text = String.format("%.1f / 20 (%s)", item.score, item.weight)
                
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
