package com.uniview.uniview

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.uniview.uniview.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding
    private lateinit var dataStore: Preferences

    override fun onCreate(savedInstanceState: Bundle?) {
        dataStore = Preferences(this)
        dataStore.applyTheme()

        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        binding.backToHome.setOnClickListener { finish() }

        setupNavigation()
        loadSavedUrls()
        loadNotificationSettings()
        loadThemeSetting()

        binding.buttonSaveSettings.setOnClickListener {
            saveUrls()
            saveNotificationSettings()
            saveThemeSetting()
            binding.statusText.text = getString(R.string.settings_saved)
        }
    }

    private fun setupNavigation() {
        binding.llFooter.findViewById<View>(R.id.navHome).setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
        }
        binding.llFooter.findViewById<View>(R.id.navSchedule).setOnClickListener {
            startActivity(Intent(this, ScheduleActivity::class.java))
            finish()
        }
        binding.llFooter.findViewById<View>(R.id.navExams).setOnClickListener {
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
        binding.inputMainJsonUrl.setText(dataStore.mainJsonUrl)
        binding.inputClassesUrl.setText(dataStore.classesUrl)
        binding.inputExamsUrl.setText(dataStore.examsUrl)
    }

    private fun loadNotificationSettings() {
        binding.switch1.isChecked = dataStore.notifyResults
        binding.switch2.isChecked = dataStore.notifySchedule
        binding.switch3.isChecked = dataStore.notifyExams
        binding.switch4.isChecked = dataStore.notifyNextLesson
    }

    private fun loadThemeSetting() {
        when (dataStore.themeMode) {
            1 -> binding.radioButton.isChecked = true
            2 -> binding.radioButton2.isChecked = true
            else -> binding.radioButton3.isChecked = true
        }
    }

    private fun saveUrls() {
        dataStore.mainJsonUrl = binding.inputMainJsonUrl.text.toString().trim()
        dataStore.classesUrl = binding.inputClassesUrl.text.toString().trim()
        dataStore.examsUrl = binding.inputExamsUrl.text.toString().trim()
    }

    private fun saveNotificationSettings() {
        dataStore.notifyResults = binding.switch1.isChecked
        dataStore.notifySchedule = binding.switch2.isChecked
        dataStore.notifyExams = binding.switch3.isChecked
        dataStore.notifyNextLesson = binding.switch4.isChecked
    }

    private fun saveThemeSetting() {
        val selectedMode = when (binding.themeRadioGroup.checkedRadioButtonId) {
            R.id.radioButton -> 1
            R.id.radioButton2 -> 2
            else -> 0
        }
        dataStore.themeMode = selectedMode
        dataStore.applyTheme()
    }
}
