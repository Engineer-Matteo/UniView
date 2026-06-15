package com.example.vubview

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.vubview.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnResults.setOnClickListener {
            startActivity(Intent(this, ResultsActivity::class.java))
        }
        binding.btnSchedule.setOnClickListener {
            startActivity(Intent(this, ScheduleActivity::class.java))
        }
        binding.btnExams.setOnClickListener {
            startActivity(Intent(this, ExamsActivity::class.java))
        }
        binding.btnCourses.setOnClickListener {
            startActivity(Intent(this, CoursesActivity::class.java))
        }
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        loadNextEventBanner()
    }

    private fun loadNextEventBanner() {
        val dataStore = VubPreferences(this)
        val examsUrl = dataStore.examsUrl
        val classesUrl = dataStore.classesUrl
        binding.tvNextEventBanner.text = getString(R.string.next_event_loading)

        if (examsUrl.isNullOrBlank() && classesUrl.isNullOrBlank()) {
            binding.tvNextEventBanner.text = getString(R.string.next_event_setup)
            return
        }

        Thread {
            val items = mutableListOf<NextEvent>()

            if (!classesUrl.isNullOrBlank()) {
                val classes = CsvParser.parseScheduleCsv(fetchText(classesUrl))
                items += classes
            }
            if (!examsUrl.isNullOrBlank()) {
                val exams = CsvParser.parseExamsCsv(fetchText(examsUrl))
                items += exams
            }

            val next = items.filter { it.isUpcoming() }.minByOrNull { it.dateTimeMillis() }
            runOnUiThread {
                if (next == null) {
                    binding.tvNextEventBanner.text = getString(R.string.next_event_none)
                } else {
                    binding.tvNextEventBanner.text = getString(R.string.next_event_text, next.title, next.dateLabel(), next.timeLabel())
                }
            }
        }.start()
    }

    private fun fetchText(url: String): String {
        return NetworkHelper.fetchUrl(url)
    }
}
