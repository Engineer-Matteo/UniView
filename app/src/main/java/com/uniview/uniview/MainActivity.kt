package com.uniview.uniview

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
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
    private val TAG = "MainActivity"

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            setupBackgroundSync()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "onCreate started")
        val dataStore = Preferences(this)
        dataStore.applyTheme()

        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupButtonListeners()
        setupNavigation()
        
        // Spread out startup tasks to avoid main thread congestion
        val handler = Handler(Looper.getMainLooper())
        
        handler.postDelayed({
            Log.d(TAG, "Executing delayed startup tasks")
            checkPermissionsAndSync()
            loadNextEventBanner()
        }, 500)

        handler.postDelayed({
            if (!isFinishing && !isDestroyed) {
                Log.d(TAG, "Triggering update check")
                UpdateChecker.checkForUpdates(this)
            }
        }, 3000) 
        
        Log.d(TAG, "onCreate finished")
    }

    override fun onResume() {
        super.onResume() // Fixed: was calling super.onStart()
        Log.d(TAG, "onResume reached")
    }

    private fun setupButtonListeners() {
        binding.btnResults.setOnClickListener {
            startActivity(Intent(this, ResultsActivity::class.java))
        }
        binding.btnSchedule.setOnClickListener {
            startActivity(Intent(this, ScheduleActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            })
        }
        binding.btnExams.setOnClickListener {
            startActivity(Intent(this, ExamsActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            })
        }
        binding.btnCourses.setOnClickListener {
            startActivity(Intent(this, CoursesActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            })
        }
        binding.btnSync.setOnClickListener { forceSync() }
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    private fun setupNavigation() {
        binding.navHome.setOnClickListener { /* Already Home */ }
        binding.navSchedule.setOnClickListener {
            startActivity(Intent(this, ScheduleActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            })
        }
        binding.navExams.setOnClickListener {
            startActivity(Intent(this, ExamsActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            })
        }
        binding.navResults.setOnClickListener {
            startActivity(Intent(this, ResultsActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            })
        }
        binding.navCourses.setOnClickListener {
            startActivity(Intent(this, CoursesActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            })
        }
    }

    private fun checkPermissionsAndSync() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
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
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>().setConstraints(constraints).build()
        val wm = WorkManager.getInstance(applicationContext)
        wm.enqueueUniqueWork("ManualSync", ExistingWorkPolicy.REPLACE, syncRequest)
        wm.getWorkInfoByIdLiveData(syncRequest.id).observe(this) { info ->
            if (info?.state?.isFinished == true) {
                val msg = if (info.state == WorkInfo.State.SUCCEEDED) R.string.sync_completed else R.string.sync_failed
                Toast.makeText(this, getString(msg), Toast.LENGTH_SHORT).show()
                if (info.state == WorkInfo.State.SUCCEEDED) loadNextEventBanner()
            }
        }
    }

    private fun loadNextEventBanner() {
        val prefs = Preferences(this)
        binding.tvNextEventBanner.text = getString(R.string.next_event_loading)
        Thread {
            val items = mutableListOf<NextEvent>()
            DataCacheManager.getClasses(this).also { if(it.isNotBlank()) items += IcalParser.parse(it, "class") }
            DataCacheManager.getExams(this).also { if(it.isNotBlank()) items += IcalParser.parse(it, "exam") }

            val next = items.filter { it.isFuture() }.minByOrNull { it.dateTimeMillis() }
            runOnUiThread {
                if (next == null) {
                    binding.tvNextEventLabel.visibility = View.GONE
                    binding.tvNextEventBanner.text = if (prefs.classesUrl.isNullOrBlank() && prefs.examsUrl.isNullOrBlank()) 
                        getString(R.string.next_event_setup) else getString(R.string.next_event_none)
                } else {
                    binding.tvNextEventLabel.visibility = View.VISIBLE
                    binding.tvNextEventLabel.text = getString(if (next.kind == "exam") R.string.label_next_exam else R.string.label_next_lesson)
                    binding.tvNextEventBanner.text = getString(R.string.next_event_text, next.title, next.dateLabel(this), next.timeLabel())
                }
            }
        }.start()
    }
}
