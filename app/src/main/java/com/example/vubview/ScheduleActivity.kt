package com.example.vubview

import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.vubview.databinding.ActivityScheduleBinding
import java.text.SimpleDateFormat
import java.util.*

class ScheduleActivity : AppCompatActivity() {
    private lateinit var binding: ActivityScheduleBinding
    private lateinit var dataStore: VubPreferences
    private val upcomingAdapter = EventsAdapter()
    private val pastAdapter = EventsAdapter()
    private var allEvents = listOf<NextEvent>()
    private var weekOffset = 0
    private var isCalendarView = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScheduleBinding.inflate(layoutInflater)
        setContentView(binding.root)

        dataStore = VubPreferences(this)
        
        binding.backToHome.setOnClickListener { finish() }

        binding.scheduleRecycler.layoutManager = LinearLayoutManager(this)
        binding.scheduleRecycler.adapter = upcomingAdapter
        
        binding.pastRecycler.layoutManager = LinearLayoutManager(this)
        binding.pastRecycler.adapter = pastAdapter

        setupToggles()
        setupNav()
        loadSchedule()
    }

    private fun setupToggles() {
        // MaterialButtonToggleGroup handles its own child button backgrounds. 
        // Manually calling setBackgroundResource on children causes a crash.
        binding.viewToggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                isCalendarView = (checkedId == R.id.viewCalendar)
                updateViewContent()
            }
        }
        
        binding.btnShowPast.setOnClickListener {
            val isHidden = binding.pastRecycler.visibility == View.GONE
            binding.pastRecycler.visibility = if (isHidden) View.VISIBLE else View.GONE
            
            val now = Calendar.getInstance().timeInMillis
            val pastEventsCount = allEvents.count { it.dateTimeMillis() < now && it.dateTimeMillis() != 0L }
            
            binding.btnShowPast.text = if (isHidden) "Verberg vorige afspraken ($pastEventsCount)" else "Toon vorige afspraken ($pastEventsCount)"
        }
    }

    private fun updateViewContent() {
        if (isCalendarView) {
            binding.calendarControls.visibility = View.VISIBLE
            binding.timetableScroll.visibility = View.VISIBLE
            binding.listContainer.visibility = View.GONE
            binding.pageTitle.text = "Wekelijkse uurrooster"
            renderWeek() 
        } else {
            binding.calendarControls.visibility = View.GONE
            binding.timetableScroll.visibility = View.GONE
            binding.listContainer.visibility = View.VISIBLE
            binding.pageTitle.text = "Lijstweergave"
            renderList()
        }
    }

    private fun setupNav() {
        binding.prevWeek.setOnClickListener {
            weekOffset--
            renderWeek()
        }
        binding.nextWeek.setOnClickListener {
            weekOffset++
            renderWeek()
        }
        binding.todayWeek.setOnClickListener {
            weekOffset = 0
            renderWeek()
        }
    }

    private fun loadSchedule() {
        val url = dataStore.classesUrl
        if (url.isNullOrBlank()) {
            binding.emptyView.text = getString(R.string.no_url_defined)
            binding.emptyView.visibility = View.VISIBLE
            return
        }

        Thread {
            try {
                val text = NetworkHelper.fetchUrl(url)
                allEvents = CsvParser.parseScheduleCsv(text)
                runOnUiThread {
                    // Set initial selection
                    binding.viewToggleGroup.check(R.id.viewCalendar)
                    updateViewContent()
                    renderWeek()
                    renderList()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    binding.emptyView.text = "Kon gegevens niet laden"
                    binding.emptyView.visibility = View.VISIBLE
                }
            }
        }.start()
    }

    private fun getMonday(offset: Int): Calendar {
        val cal = Calendar.getInstance(Locale("nl", "BE"))
        cal.firstDayOfWeek = Calendar.MONDAY
        val day = cal.get(Calendar.DAY_OF_WEEK)
        val diff = if (day == Calendar.SUNDAY) -6 else Calendar.MONDAY - day
        cal.add(Calendar.DAY_OF_YEAR, diff + offset * 7)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal
    }

    private fun toYMD(cal: Calendar): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return sdf.format(cal.time)
    }

    private fun parseTime(time: String): Int? {
        return try {
            val parts = time.split(":")
            if (parts.size != 2) return null
            val hours = parts[0].toInt()
            val minutes = parts[1].toInt()
            hours * 60 + minutes
        } catch (e: Exception) {
            null
        }
    }

    private fun renderWeek() {
        val monday = getMonday(weekOffset)
        val fmt = SimpleDateFormat("d MMMM", Locale("nl", "BE"))
        val fmtWithYear = SimpleDateFormat("d MMMM yyyy", Locale("nl", "BE"))
        
        val sunday = monday.clone() as Calendar
        sunday.add(Calendar.DAY_OF_YEAR, 6)
        
        binding.weekLabel.text = "${fmt.format(monday.time)} – ${fmtWithYear.format(sunday.time)}"
        
        updateAverageWeeklyHours(monday)

        val weekCalendars = mutableListOf<Calendar>()
        for (i in 0..6) {
            val d = monday.clone() as Calendar
            d.add(Calendar.DAY_OF_YEAR, i)
            weekCalendars.add(d)
        }

        val mondayStart = monday.timeInMillis
        val sundayEnd = sunday.timeInMillis + 86400000

        val weekRows = allEvents.filter { event ->
            val eventMillis = event.dateTimeMillis()
            eventMillis >= mondayStart && eventMillis < sundayEnd
        }
        
        val activeDayIndices = (0..6).filter { i ->
            val startOfDay = weekCalendars[i].timeInMillis
            val endOfDay = startOfDay + 86400000
            weekRows.any { it.dateTimeMillis() in startOfDay until endOfDay }
        }

        binding.timetableContainer.removeAllViews()
        
        if (activeDayIndices.isEmpty()) {
            if (isCalendarView) {
                binding.emptyView.visibility = View.VISIBLE
                binding.emptyView.text = "Geen lessen deze week."
            }
            return
        }
        binding.emptyView.visibility = View.GONE

        val today = Calendar.getInstance()
        today.set(Calendar.HOUR_OF_DAY, 0)
        today.set(Calendar.MINUTE, 0)
        today.set(Calendar.SECOND, 0)
        today.set(Calendar.MILLISECOND, 0)
        val todayMillis = today.timeInMillis

        val dayFmt = SimpleDateFormat("EEEE", Locale("nl", "BE"))
        val dateFmt = SimpleDateFormat("d MMM", Locale("nl", "BE"))

        activeDayIndices.forEach { i ->
            val dayCal = weekCalendars[i]
            val startOfDay = dayCal.timeInMillis
            val endOfDay = startOfDay + 86400000
            val dayRows = weekRows.filter { it.dateTimeMillis() in startOfDay until endOfDay }
            
            val colView = LayoutInflater.from(this).inflate(R.layout.item_day_column, binding.timetableContainer, false)
            val dayName = colView.findViewById<TextView>(R.id.dayName)
            val dayDate = colView.findViewById<TextView>(R.id.dayDate)
            val columnBody = colView.findViewById<FrameLayout>(R.id.columnBody)
            val columnHeader = colView.findViewById<LinearLayout>(R.id.columnHeader)

            dayName.text = dayFmt.format(dayCal.time)
            dayDate.text = dateFmt.format(dayCal.time)
            
            if (startOfDay == todayMillis) {
                columnHeader.setBackgroundColor(Color.parseColor("#fff3e0")) 
            }

            renderEventsInColumn(dayRows, columnBody)
            binding.timetableContainer.addView(colView)
        }
    }

    private fun renderEventsInColumn(rows: List<NextEvent>, container: FrameLayout) {
        val dayStart = 8 * 60
        val dayEnd = 22 * 60
        
        data class EventInfo(val row: NextEvent, val startMin: Int, val endMin: Int, var column: Int = 0)
        
        val events = rows.mapNotNull { row ->
            val s = parseTime(row.start)
            val e = parseTime(row.end)
            if (s == null || e == null) return@mapNotNull null
            if (e <= dayStart || s >= dayEnd) return@mapNotNull null
            EventInfo(row, Math.max(dayStart, s), Math.min(dayEnd, e))
        }.sortedBy { it.startMin }

        val columns = mutableListOf<MutableList<EventInfo>>()
        events.forEach { event ->
            var placed = false
            for (i in columns.indices) {
                if (event.startMin >= columns[i].last().endMin) {
                    columns[i].add(event)
                    event.column = i
                    placed = true
                    break
                }
            }
            if (!placed) {
                event.column = columns.size
                columns.add(mutableListOf(event))
            }
        }
        
        val totalCols = columns.size

        events.forEach { eventInfo ->
            val top = eventInfo.startMin - dayStart
            val height = Math.max(64, eventInfo.endMin - eventInfo.startMin)
            val visualTop = top + 2
            val visualHeight = Math.max(52, height - 6)

            val eventView = LayoutInflater.from(this).inflate(R.layout.item_timetable_event, container, false)
            val params = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, visualHeight.toFloat(), resources.displayMetrics).toInt()
            )
            params.topMargin = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, visualTop.toFloat(), resources.displayMetrics).toInt()
            
            eventView.layoutParams = params
            
            if (totalCols > 1) {
                eventView.post {
                    val w = container.width / totalCols
                    val lp = eventView.layoutParams as FrameLayout.LayoutParams
                    lp.width = w - 8
                    lp.leftMargin = eventInfo.column * w + 4
                    eventView.layoutParams = lp
                }
            }

            eventView.findViewById<TextView>(R.id.eventCourse).text = eventInfo.row.title
            eventView.findViewById<TextView>(R.id.eventTime).text = "${eventInfo.row.start} – ${eventInfo.row.end}"
            eventView.findViewById<TextView>(R.id.eventType).text = eventInfo.row.type
            eventView.findViewById<TextView>(R.id.eventRoom).text = eventInfo.row.room
            
            eventView.setOnClickListener { showEventDetails(eventInfo.row) }
            container.addView(eventView)
        }
    }

    private fun showEventDetails(event: NextEvent) {
        val msg = StringBuilder()
        msg.append("Dag: ${event.formattedDate()}\n")
        msg.append("Tijd: ${event.start} – ${event.end}\n")
        if (event.type.isNotBlank()) msg.append("Lestype: ${event.type}\n")
        if (event.room.isNotBlank()) msg.append("Lokaal: ${event.room}")

        AlertDialog.Builder(this)
            .setTitle(event.title)
            .setMessage(msg.toString())
            .setPositiveButton("Sluiten", null)
            .show()
    }

    private fun updateAverageWeeklyHours(monday: Calendar) {
        if (allEvents.isEmpty()) return
        val month = monday.get(Calendar.MONTH)
        val year = monday.get(Calendar.YEAR)
        var totalMinutes = 0
        val weekSet = mutableSetOf<String>()
        allEvents.forEach { event ->
            val cal = Calendar.getInstance()
            cal.timeInMillis = event.dateTimeMillis()
            if (cal.get(Calendar.MONTH) == month && cal.get(Calendar.YEAR) == year) {
                val s = parseTime(event.start)
                val e = parseTime(event.end)
                if (s != null && e != null) {
                    totalMinutes += (e - s)
                    val eventMon = cal.clone() as Calendar
                    eventMon.firstDayOfWeek = Calendar.MONDAY
                    val d = eventMon.get(Calendar.DAY_OF_WEEK)
                    val diff = if (d == Calendar.SUNDAY) -6 else Calendar.MONDAY - d
                    eventMon.add(Calendar.DAY_OF_YEAR, diff)
                    weekSet.add(toYMD(eventMon))
                }
            }
        }
        val weekCount = weekSet.size
        if (weekCount == 0) {
            binding.averageHours.text = "gem. 0.0 u/week"
        } else {
            val avg = totalMinutes.toFloat() / weekCount / 60f
            binding.averageHours.text = String.format(Locale("nl", "BE"), "gem. %.1f u/week", avg)
        }
    }

    private fun renderList() {
        val now = Calendar.getInstance()
        now.set(Calendar.HOUR_OF_DAY, 0)
        now.set(Calendar.MINUTE, 0)
        now.set(Calendar.SECOND, 0)
        now.set(Calendar.MILLISECOND, 0)
        val todayMillis = now.timeInMillis
        
        val upcomingEvents = allEvents.filter { it.dateTimeMillis() >= todayMillis }.sortedBy { it.dateTimeMillis() }
        val pastEvents = allEvents.filter { it.dateTimeMillis() < todayMillis && it.dateTimeMillis() != 0L }.sortedByDescending { it.dateTimeMillis() }
        
        upcomingAdapter.submitList(mapEventsToItems(upcomingEvents))
        pastAdapter.submitList(mapEventsToItems(pastEvents))
        
        binding.btnShowPast.visibility = if (pastEvents.isNotEmpty()) View.VISIBLE else View.GONE
        binding.btnShowPast.text = "Toon vorige afspraken (${pastEvents.size})"
    }

    private fun mapEventsToItems(events: List<NextEvent>): List<ScheduleListItem> {
        val items = mutableListOf<ScheduleListItem>()
        var lastDate: String? = null
        events.forEach { event ->
            val dateKey = event.date
            if (dateKey != lastDate) {
                items.add(ScheduleListItem.Header(event.formattedDate()))
                lastDate = dateKey
            }
            items.add(ScheduleListItem.Event(event))
        }
        return items
    }
}
