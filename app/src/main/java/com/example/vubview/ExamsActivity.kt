package com.example.vubview

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.vubview.databinding.ActivityExamsBinding

class ExamsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityExamsBinding
    private lateinit var dataStore: VubPreferences
    private val upcomingAdapter = EventsAdapter()
    private val pastAdapter = EventsAdapter()
    private var allEvents = listOf<NextEvent>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityExamsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        dataStore = VubPreferences(this)
        
        binding.backToHome.setOnClickListener { finish() }

        // Setup Upcoming Recycler
        binding.examsRecycler.layoutManager = LinearLayoutManager(this)
        binding.examsRecycler.adapter = upcomingAdapter

        // Setup Past Recycler
        binding.pastExamsRecycler.layoutManager = LinearLayoutManager(this)
        binding.pastExamsRecycler.adapter = pastAdapter

        binding.btnShowPast.setOnClickListener {
            val isHidden = binding.pastExamsRecycler.visibility == View.GONE
            binding.pastExamsRecycler.visibility = if (isHidden) View.VISIBLE else View.GONE
            
            val pastCount = allEvents.count { !it.isUpcoming() }
            binding.btnShowPast.text = if (isHidden) "Verberg geschiedenis ($pastCount)" else "Toon verleden ($pastCount)"
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
            try {
                val text = NetworkHelper.fetchUrl(url)
                allEvents = CsvParser.parseExamsCsv(text)
                runOnUiThread {
                    updateUI()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    binding.emptyView.text = "Kon examengegevens niet laden"
                    binding.emptyView.visibility = View.VISIBLE
                }
            }
        }.start()
    }

    private fun updateUI() {
        val future = allEvents.filter { it.isUpcoming() }.sortedBy { it.dateTimeMillis() }
        val past = allEvents.filter { !it.isUpcoming() }.sortedByDescending { it.dateTimeMillis() }
        
        upcomingAdapter.submitList(mapEventsToItems(future))
        pastAdapter.submitList(mapEventsToItems(past))
        
        binding.emptyView.visibility = if (future.isEmpty() && past.isEmpty()) View.VISIBLE else View.GONE
        
        // Show/Update history button
        if (past.isNotEmpty()) {
            binding.btnShowPast.visibility = View.VISIBLE
            binding.btnShowPast.text = "Toon verleden (${past.size})"
        } else {
            binding.btnShowPast.visibility = View.GONE
        }
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
