package com.uniview.uniview

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.uniview.uniview.databinding.ActivityMainBinding
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
        val dataStore = Preferences(this)
        dataStore.applyTheme()

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
        binding.btnSync.setOnClickListener { 
            forceSync()
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
            "UniViewDataSync",
            ExistingPeriodicWorkPolicy.KEEP,
            syncRequest
        )
    }

    private fun forceSync() {
        Toast.makeText(this, getString(R.string.sync_in_progress), Toast.LENGTH_SHORT).show()
        
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .build()

        val workManager = WorkManager.getInstance(applicationContext)
        workManager.enqueueUniqueWork(
            "ManualSync",
            ExistingWorkPolicy.REPLACE,
            syncRequest
        )

        // Observe the work status to update the UI (banner) when finished
        workManager.getWorkInfoByIdLiveData(syncRequest.id).observe(this) { workInfo ->
            if (workInfo != null && workInfo.state.isFinished) {
                if (workInfo.state == WorkInfo.State.SUCCEEDED) {
                    Toast.makeText(this, getString(R.string.sync_completed), Toast.LENGTH_SHORT).show()
                    loadNextEventBanner()
                } else {
                    Toast.makeText(this, getString(R.string.sync_failed), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun loadNextEventBanner() {
        val dataStore = Preferences(this)
        val examsUrl = dataStore.examsUrl
        val classesUrl = dataStore.classesUrl
        binding.tvNextEventBanner.text = getString(R.string.next_event_loading)

        Thread {
            val items = mutableListOf<NextEvent>()

            // Try to load from cache first for immediate display
            val cachedClasses = DataCacheManager.getClasses(this)
            if (cachedClasses.isNotBlank()) {
                items += IcalParser.parse(cachedClasses, "class")
            }
            val cachedExams = DataCacheManager.getExams(this)
            if (cachedExams.isNotBlank()) {
                items += IcalParser.parse(cachedExams, "exam")
            }

            // Also try to fetch latest if urls available
            if (items.isEmpty()) {
                if (!classesUrl.isNullOrBlank()) {
                    try {
                        val text = NetworkHelper.fetchUrl(classesUrl)
                        DataCacheManager.saveClasses(this, text)
                        items += IcalParser.parse(text, "class")
                    } catch (e: Exception) {}
                }
                if (!examsUrl.isNullOrBlank()) {
                    try {
                        val text = NetworkHelper.fetchUrl(examsUrl)
                        DataCacheManager.saveExams(this, text)
                        items += IcalParser.parse(text, "exam")
                    } catch (e: Exception) {}
                }
            }

            // For the main banner, we show the next event that hasn't started yet
            val next = items.filter { it.isFuture() }.minByOrNull { it.dateTimeMillis() }
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
                    binding.tvNextEventLabel.text = if (next.kind == "exam") getString(R.string.label_next_exam) else getString(R.string.label_next_lesson)
                    binding.tvNextEventBanner.text = getString(R.string.next_event_text, next.title, next.dateLabel(this), next.timeLabel())
                }
            }
        }.start()
    }
}
