package com.example.uniview

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

sealed class ScheduleListItem {
    data class Header(val dateLabel: String) : ScheduleListItem()
    data class Event(val event: NextEvent) : ScheduleListItem()
}

class EventsAdapter : ListAdapter<ScheduleListItem, RecyclerView.ViewHolder>(ScheduleDiffCallback()) {
    
    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_EVENT = 1
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is ScheduleListItem.Header -> TYPE_HEADER
            is ScheduleListItem.Event -> TYPE_EVENT
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_HEADER) {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_list_day_header, parent, false)
            HeaderViewHolder(view)
        } else {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_event, parent, false)
            EventViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        if (holder is HeaderViewHolder && item is ScheduleListItem.Header) {
            holder.bind(item)
        } else if (holder is EventViewHolder && item is ScheduleListItem.Event) {
            holder.bind(item)
        }
    }

    class HeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val heading = itemView.findViewById<TextView>(R.id.dayHeading)
        fun bind(item: ScheduleListItem.Header) {
            heading.text = item.dateLabel
        }
    }

    class EventViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val title = itemView.findViewById<TextView>(R.id.eventTitle)
        private val details = itemView.findViewById<TextView>(R.id.eventDetails)

        fun bind(item: ScheduleListItem.Event) {
            val event = item.event
            title.text = event.title
            
            val meta = StringBuilder()
            meta.append("${event.start} – ${event.end}")
            if (event.type.isNotBlank()) meta.append(" · ${event.type}")
            if (event.room.isNotBlank()) meta.append(" · ${event.room}")
            
            details.text = meta.toString()
        }
    }

    class ScheduleDiffCallback : DiffUtil.ItemCallback<ScheduleListItem>() {
        override fun areItemsTheSame(oldItem: ScheduleListItem, newItem: ScheduleListItem): Boolean {
            if (oldItem is ScheduleListItem.Header && newItem is ScheduleListItem.Header) {
                return oldItem.dateLabel == newItem.dateLabel
            }
            if (oldItem is ScheduleListItem.Event && newItem is ScheduleListItem.Event) {
                return oldItem.event.title == newItem.event.title && 
                       oldItem.event.date == newItem.event.date && 
                       oldItem.event.start == newItem.event.start
            }
            return false
        }

        override fun areContentsTheSame(oldItem: ScheduleListItem, newItem: ScheduleListItem): Boolean {
            return oldItem == newItem
        }
    }
}
