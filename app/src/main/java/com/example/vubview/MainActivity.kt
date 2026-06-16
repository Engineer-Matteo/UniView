package com.example.vubview

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.vubview.databinding.ActivityMainBinding
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            setupBackgroundSync()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Force Light Mode globally
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)

        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnResults.setOnClickListener {
            startActivity(Intent(this, ResultsActivity::class.java))
        }
        binding.btnSchedule.setOnClickListener {
            val intent = Intent(this, ScheduleActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            startActivity(intent)
        }
        binding.btnExams.setOnClickListener {
            val intent = Intent(this, ExamsActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            startActivity(intent)
        }
        binding.btnCourses.setOnClickListener {
            val intent = Intent(this, CoursesActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            startActivity(intent)
        }
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        setupNavigation()
        checkPermissionsAndSync()
        loadNextEventBanner()
    }

    private fun setupNavigation() {
        binding.navHome.setOnClickListener { /* Already on Home */ }
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
        binding.navCourses.setOnClickListener {
            val intent = Intent(this, CoursesActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            startActivity(intent)
        }
    }

    private fun checkPermissionsAndSync() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                setupBackgroundSync()
            } else {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            setupBackgroundSync()
        }
    }

    private fun setupBackgroundSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(1, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
            "VubDataSync",
            ExistingPeriodicWorkPolicy.KEEP,
            syncRequest
        )
    }

    private fun loadNextEventBanner() {
        val dataStore = VubPreferences(this)
        val examsUrl = dataStore.examsUrl
        val classesUrl = dataStore.classesUrl
        binding.tvNextEventBanner.text = getString(R.string.next_event_loading)

        Thread {
            val items = mutableListOf<NextEvent>()

            // Try to load from cache first for immediate display
            val cachedClasses = CsvCacheManager.getClasses(this)
            if (cachedClasses.isNotBlank()) {
                items += CsvParser.parseScheduleCsv(cachedClasses)
            }
            val cachedExams = CsvCacheManager.getExams(this)
            if (cachedExams.isNotBlank()) {
                items += CsvParser.parseExamsCsv(cachedExams)
            }

            // Also try to fetch latest if urls available
            if (items.isEmpty()) {
                if (!classesUrl.isNullOrBlank()) {
                    try {
                        val text = NetworkHelper.fetchUrl(classesUrl)
                        CsvCacheManager.saveClasses(this, text)
                        items += CsvParser.parseScheduleCsv(text)
                    } catch (e: Exception) {}
                }
                if (!examsUrl.isNullOrBlank()) {
                    try {
                        val text = NetworkHelper.fetchUrl(examsUrl)
                        CsvCacheManager.saveExams(this, text)
                        items += CsvParser.parseExamsCsv(text)
                    } catch (e: Exception) {}
                }
            }

            val next = items.filter { it.isUpcoming() }.minByOrNull { it.dateTimeMillis() }
            runOnUiThread {
                if (next == null) {
                    binding.tvNextEventLabel.visibility = View.GONE
                    if (examsUrl.isNullOrBlank() && classesUrl.isNullOrBlank()) {
                        binding.tvNextEventBanner.text = getString(R.string.next_event_setup)
                    } else {
                        binding.tvNextEventBanner.text = getString(R.string.next_event_none)
                    }
                } else {
                    binding.tvNextEventLabel.visibility = View.VISIBLE
                    binding.tvNextEventLabel.text = if (next.kind == "exam") "Volgend examen:" else "Volgende les:"
                    binding.tvNextEventBanner.text = getString(R.string.next_event_text, next.title, next.dateLabel(), next.timeLabel())
                }
            }
        }.start()
    }
}
