package com.uniview.uniview

import android.content.Intent
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
import androidx.recyclerview.widget.LinearLayoutManager
import com.uniview.uniview.databinding.ActivityScheduleBinding
import java.text.SimpleDateFormat
import java.util.*

class ScheduleActivity : AppCompatActivity() {
    private lateinit var binding: ActivityScheduleBinding
    private lateinit var dataStore: Preferences
    private val upcomingAdapter = EventsAdapter()
    private val pastAdapter = EventsAdapter()
    private var allEvents = listOf<NextEvent>()
    private var weekOffset = 0
    private var isCalendarView = true

    override fun onCreate(savedInstanceState: Bundle?) {
        dataStore = Preferences(this)
        dataStore.applyTheme()

        super.onCreate(savedInstanceState)
        binding = ActivityScheduleBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        binding.backToHome.setOnClickListener { finish() }

        binding.scheduleRecycler.layoutManager = LinearLayoutManager(this)
        binding.scheduleRecycler.adapter = upcomingAdapter
        
        binding.pastRecycler.layoutManager = LinearLayoutManager(this)
        binding.pastRecycler.adapter = pastAdapter

        setupToggles()
        setupNav()
        setupBottomNavigation()
        loadSchedule()
    }

    private fun setupBottomNavigation() {
        binding.llFooter.findViewById<View>(R.id.navHome).setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
        }
        binding.llFooter.findViewById<View>(R.id.navSchedule).setOnClickListener { /* Already here */ }
        binding.llFooter.findViewById<View>(R.id.navExams).setOnClickListener {
            startActivity(Intent(this, ExamsActivity::class.java))
            finish()
        }
        binding.llFooter.findViewById<View>(R.id.navResults).setOnClickListener {
            startActivity(Intent(this, ResultsActivity::class.java))
            finish()
        }
        binding.llFooter.findViewById<View>(R.id.navCourses).setOnClickListener {
            startActivity(Intent(this, CoursesActivity::class.java))
            finish()
        }
    }

    private fun setupToggles() {
        binding.viewToggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                isCalendarView = (checkedId == R.id.viewCalendar)
                updateViewContent()
            }
        }
        
        binding.btnShowPast.setOnClickListener {
            val isHidden = binding.pastRecycler.visibility == View.GONE
            binding.pastRecycler.visibility = if (isHidden) View.VISIBLE else View.GONE
            
            val pastEventsCount = allEvents.count { !it.isUpcoming() }
            
            binding.btnShowPast.text = if (isHidden) {
                getString(R.string.btn_show_past_appointments_count, pastEventsCount)
            } else {
                getString(R.string.btn_hide_past_appointments, pastEventsCount)
            }
        }
    }

    private fun updateViewContent() {
        if (isCalendarView) {
            binding.calendarControls.visibility = View.VISIBLE
            binding.timetableScroll.visibility = View.VISIBLE
            binding.listContainer.visibility = View.GONE
            binding.pageTitle.text = getString(R.string.title_weekly_schedule)
            renderWeek() 
        } else {
            binding.calendarControls.visibility = View.GONE
            binding.timetableScroll.visibility = View.GONE
            binding.listContainer.visibility = View.VISIBLE
            binding.pageTitle.text = getString(R.string.title_list_view)
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
        
        // Load from cache first
        val cached = DataCacheManager.getClasses(this)
        if (cached.isNotBlank()) {
            allEvents = IcalParser.parse(cached, "class")
            binding.viewToggleGroup.check(R.id.viewCalendar)
            updateViewContent()
        }

        if (url.isNullOrBlank()) {
            if (allEvents.isEmpty()) {
                binding.emptyView.text = getString(R.string.no_url_defined)
                binding.emptyView.visibility = View.VISIBLE
            }
            return
        }

        Thread {
            try {
                val text = NetworkHelper.fetchUrl(url)
                if (text.isNotBlank()) {
                    DataCacheManager.saveClasses(this, text)
                    allEvents = IcalParser.parse(text, "class")
                    runOnUiThread {
                        binding.emptyView.visibility = View.GONE
                        binding.viewToggleGroup.check(R.id.viewCalendar)
                        updateViewContent()
                        renderWeek()
                        renderList()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    if (allEvents.isEmpty()) {
                        binding.emptyView.text = getString(R.string.error_loading_data)
                        binding.emptyView.visibility = View.VISIBLE
                    }
                }
            }
        }.start()
    }

    private fun getAppLocale(): Locale {
        return Locale(getString(R.string.locale_lang), getString(R.string.locale_country))
    }

    private fun getMonday(offset: Int): Calendar {
        val cal = Calendar.getInstance(getAppLocale())
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
        val locale = getAppLocale()
        val monday = getMonday(weekOffset)
        val fmt = SimpleDateFormat(getString(R.string.date_format_month_day), locale)
        val fmtWithYear = SimpleDateFormat(getString(R.string.date_format_month_day_year), locale)
        
        val sunday = monday.clone() as Calendar
        sunday.add(Calendar.DAY_OF_YEAR, 6)
        
        binding.weekLabel.text = getString(R.string.date_format_week_label, fmt.format(monday.time), fmtWithYear.format(sunday.time))
        
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
                binding.emptyView.text = getString(R.string.empty_week)
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

        val dayFmt = SimpleDateFormat(getString(R.string.date_format_day_only), locale)
        val dateFmt = SimpleDateFormat(getString(R.string.date_format_day_short), locale)

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
            eventView.findViewById<TextView>(R.id.eventTime).text = getString(R.string.format_time_range, eventInfo.row.start, eventInfo.row.end)
            eventView.findViewById<TextView>(R.id.eventType).text = eventInfo.row.type
            eventView.findViewById<TextView>(R.id.eventRoom).text = eventInfo.row.room
            
            eventView.setOnClickListener { showEventDetails(eventInfo.row) }
            container.addView(eventView)
        }
    }

    private fun showEventDetails(event: NextEvent) {
        val msg = StringBuilder()
        msg.append(getString(R.string.label_day, event.formattedDate(this))).append("\n")
        msg.append(getString(R.string.label_time_range, event.start, event.end)).append("\n")
        if (event.type.isNotBlank()) msg.append(getString(R.string.label_lesson_type, event.type)).append("\n")
        if (event.room.isNotBlank()) msg.append(getString(R.string.label_room, event.room))

        AlertDialog.Builder(this)
            .setTitle(event.title)
            .setMessage(msg.toString())
            .setPositiveButton(getString(R.string.btn_close), null)
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
            binding.averageHours.text = getString(R.string.label_avg_hours, 0.0f)
        } else {
            val avg = totalMinutes.toFloat() / weekCount / 60f
            binding.averageHours.text = String.format(getAppLocale(), getString(R.string.label_avg_hours), avg)
        }
    }

    private fun renderList() {
        val upcomingEvents = allEvents.filter { it.isUpcoming() }.sortedBy { it.dateTimeMillis() }
        val pastEvents = allEvents.filter { !it.isUpcoming() }.sortedByDescending { it.dateTimeMillis() }
        
        upcomingAdapter.submitList(mapEventsToItems(upcomingEvents))
        pastAdapter.submitList(mapEventsToItems(pastEvents))
        
        binding.btnShowPast.visibility = if (pastEvents.isNotEmpty()) View.VISIBLE else View.GONE
        binding.btnShowPast.text = getString(R.string.btn_show_past_appointments_count, pastEvents.size)
    }

    private fun mapEventsToItems(events: List<NextEvent>): List<ScheduleListItem> {
        val items = mutableListOf<ScheduleListItem>()
        var lastDate: String? = null
        events.forEach { event ->
            val dateKey = event.date
            if (dateKey != lastDate) {
                items.add(ScheduleListItem.Header(event.formattedDate(this)))
                lastDate = dateKey
            }
            items.add(ScheduleListItem.Event(event))
        }
        return items
    }
}
