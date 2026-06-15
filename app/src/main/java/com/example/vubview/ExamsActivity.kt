package com.example.vubview

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.vubview.databinding.ActivityExamsBinding

class ExamsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityExamsBinding
    private lateinit var dataStore: VubPreferences
    private val adapter = EventsAdapter()
    private var allEvents = listOf<NextEvent>()
    private var showingPast = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityExamsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        dataStore = VubPreferences(this)
        
        binding.backToHome.setOnClickListener { finish() }

        binding.examsRecycler.layoutManager = LinearLayoutManager(this)
        binding.examsRecycler.adapter = adapter

        binding.btnShowPast.setOnClickListener {
            showingPast = !showingPast
            updateDisplayedEvents()
            binding.btnShowPast.text = if (showingPast) "Verberg geschiedenis" else "Toon verleden"
        }

        loadExams()
    }

    private fun loadExams() {
        val url = dataStore.examsUrl
        if (url.isNullOrBlank()) {
            binding.emptyView.text = getString(R.string.no_url_defined)
            binding.emptyView.visibility = View.VISIBLE
            binding.btnShowPast.visibility = View.GONE
            return
        }

        Thread {
            val text = NetworkHelper.fetchUrl(url)
            allEvents = CsvParser.parseExamsCsv(text)
            runOnUiThread {
                updateDisplayedEvents()
            }
        }.start()
    }

    private fun updateDisplayedEvents() {
        val future = allEvents.filter { it.isUpcoming() }.sortedBy { it.dateTimeMillis() }
        val past = allEvents.filter { !it.isUpcoming() }.sortedByDescending { it.dateTimeMillis() }
        
        val filtered = if (showingPast) {
            future + past // Show everything: upcoming followed by full history
        } else {
            future
        }
        
        adapter.submitList(mapEventsToItems(filtered))
        binding.emptyView.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        
        // Dynamic title
        binding.pageTitle.text = if (showingPast) "Examen geschiedenis" else "Komende examens"
        
        // Only show history button if there are actually past events
        val hasPast = allEvents.any { !it.isUpcoming() }
        binding.btnShowPast.visibility = if (hasPast) View.VISIBLE else View.GONE
    }

    private fun mapEventsToItems(events: List<NextEvent>): List<ScheduleListItem> {
        val items = mutableListOf<ScheduleListItem>()
        var lastDate: String? = null
        events.forEach { event ->
            if (event.date != lastDate) {
                items.add(ScheduleListItem.Header(event.formattedDate()))
                lastDate = event.date
            }
            items.add(ScheduleListItem.Event(event))
        }
        return items
    }
}
