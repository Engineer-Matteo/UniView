package com.example.vubview

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.vubview.databinding.ActivityCoursesBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.*

class CoursesActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCoursesBinding
    private lateinit var dataStore: VubPreferences
    private val adapter = CoursesAdapter { showCourseDetailDialog(it) }
    private val allCourses = mutableListOf<CourseEntry>()

    override fun onCreate(savedInstanceState: Bundle?) {
        dataStore = VubPreferences(this)
        dataStore.applyTheme()

        super.onCreate(savedInstanceState)
        binding = ActivityCoursesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        binding.backToHome.setOnClickListener { finish() }
        
        binding.coursesRecycler.layoutManager = LinearLayoutManager(this)
        binding.coursesRecycler.adapter = adapter

        binding.filterYear.onItemSelectedListener = SimpleSpinnerListener { loadFilteredCourses() }
        binding.filterSemester.onItemSelectedListener = SimpleSpinnerListener { loadFilteredCourses() }

        binding.searchCourse.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                loadFilteredCourses()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        setupNavigation()
        loadCachedData()
        refreshCourses()
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
            val intent = Intent(this, ResultsActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            startActivity(intent)
        }
        binding.navCourses.setOnClickListener { /* Already here */ }
    }

    private fun loadCachedData() {
        val cachedJson = DataCacheManager.getMainJson(this)
        if (cachedJson.isNotBlank()) {
            val (_, _, courses) = JsonParser.parseMainJson(cachedJson)
            allCourses.clear()
            allCourses.addAll(courses)
            updateFilters()
            loadFilteredCourses()
        }
    }

    private fun refreshCourses() {
        val url = dataStore.mainJsonUrl
        if (url.isNullOrBlank()) {
            if (allCourses.isEmpty()) {
                binding.emptyView.text = getString(R.string.no_url_defined)
                binding.emptyView.visibility = View.VISIBLE
            }
            return
        }

        binding.progressBar.visibility = View.VISIBLE
        Thread {
            try {
                val jsonText = NetworkHelper.fetchUrl(url)
                if (jsonText.isNotBlank()) {
                    DataCacheManager.saveMainJson(this, jsonText)
                    val (_, _, courses) = JsonParser.parseMainJson(jsonText)
                    allCourses.clear()
                    allCourses.addAll(courses)
                }

                runOnUiThread {
                    updateFilters()
                    loadFilteredCourses()
                    binding.progressBar.visibility = View.GONE
                }
            } catch (e: Exception) {
                runOnUiThread {
                    binding.progressBar.visibility = View.GONE
                    if (allCourses.isEmpty()) {
                        binding.emptyView.text = "Kon gegevens niet laden"
                        binding.emptyView.visibility = View.VISIBLE
                    }
                }
            }
        }.start()
    }

    private fun updateFilters() {
        val programs = allCourses.map { it.program }.filter { it.isNotBlank() }.distinct().sorted()
        val semesters = allCourses.map { it.semester }.filter { it.isNotBlank() }.distinct().sorted()

        val allYearsText = getString(R.string.all_years)
        val allSemestersText = getString(R.string.all_semesters)

        val yearAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, listOf(allYearsText) + programs)
        yearAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.filterYear.adapter = yearAdapter

        val semesterAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, listOf(allSemestersText) + semesters)
        semesterAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.filterSemester.adapter = semesterAdapter
    }

    private fun loadFilteredCourses() {
        val allYearsText = getString(R.string.all_years)
        val allSemestersText = getString(R.string.all_semesters)
        
        val program = binding.filterYear.selectedItem?.toString().takeIf { it != allYearsText }
        val semester = binding.filterSemester.selectedItem?.toString().takeIf { it != allSemestersText }
        val query = binding.searchCourse.text.toString().lowercase()

        val filtered = allCourses.filter { course ->
            val matchesProgram = program?.let { course.program == it } ?: true
            val matchesSemester = semester?.let { course.semester == it } ?: true
            val matchesQuery = course.name.lowercase().contains(query) || course.professor.lowercase().contains(query)
            matchesProgram && matchesSemester && matchesQuery
        }
        
        adapter.submitList(filtered)
        binding.emptyView.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun showCourseDetailDialog(course: CourseEntry) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_course_detail, null)
        
        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .create()
            
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogView.findViewById<TextView>(R.id.detailCourseName).text = course.name
        dialogView.findViewById<TextView>(R.id.detailProfessor).text = course.professor
        dialogView.findViewById<TextView>(R.id.detailMeta).text = "${course.program} · ${course.semester} · ${course.ects} ECTS"
        
        val yearLocation = if (course.location.isNullOrBlank()) {
            course.year
        } else {
            "${course.year} · ${course.location}"
        }
        dialogView.findViewById<TextView>(R.id.detailYearLocation).text = yearLocation

        dialogView.findViewById<TextView>(R.id.detailDescription).text = course.description

        dialogView.findViewById<ImageView>(R.id.detailClose).setOnClickListener { dialog.dismiss() }

        dialog.show()

        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }
}
