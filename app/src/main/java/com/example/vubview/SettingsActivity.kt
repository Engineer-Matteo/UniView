package com.example.vubview

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

        binding.buttonSaveSettings.setOnClickListener {
            saveUrls()
            binding.statusText.text = getString(R.string.settings_saved)
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
