package com.example.vubview

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.vubview.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding
    private lateinit var dataStore: VubPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        dataStore = VubPreferences(this)
        
        binding.backToHome.setOnClickListener { finish() }

        loadSavedUrls()
        setupBottomNavigation()

        binding.buttonSaveSettings.setOnClickListener {
            saveUrls()
            binding.statusText.text = getString(R.string.settings_saved)
        }
    }

    private fun setupBottomNavigation() {
        binding.navHome.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
        }
        binding.navSchedule.setOnClickListener {
            startActivity(Intent(this, ScheduleActivity::class.java))
            finish()
        }
        binding.navExams.setOnClickListener {
            startActivity(Intent(this, ExamsActivity::class.java))
            finish()
        }
        binding.navResults.setOnClickListener {
            startActivity(Intent(this, ResultsActivity::class.java))
            finish()
        }
        binding.navCourses.setOnClickListener {
            startActivity(Intent(this, CoursesActivity::class.java))
            finish()
        }
    }

    private fun loadSavedUrls() {
        binding.inputResultsUrl.setText(dataStore.resultsUrl)
        binding.inputBreakdownUrl.setText(dataStore.breakdownUrl)
        binding.inputClassesUrl.setText(dataStore.classesUrl)
        binding.inputExamsUrl.setText(dataStore.examsUrl)
    }

    private fun saveUrls() {
        dataStore.resultsUrl = binding.inputResultsUrl.text.toString().trim()
        dataStore.breakdownUrl = binding.inputBreakdownUrl.text.toString().trim()
        dataStore.classesUrl = binding.inputClassesUrl.text.toString().trim()
        dataStore.examsUrl = binding.inputExamsUrl.text.toString().trim()
    }
}
