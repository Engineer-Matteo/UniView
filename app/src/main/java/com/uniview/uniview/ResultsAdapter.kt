package com.uniview.uniview

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

class ResultsAdapter(
    private val onDetailClick: (ResultEntry) -> Unit
) : ListAdapter<ResultEntry, ResultsAdapter.ResultViewHolder>(ResultDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ResultViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_result, parent, false)
        return ResultViewHolder(view, onDetailClick)
    }

    override fun onBindViewHolder(holder: ResultViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ResultViewHolder(
        itemView: View,
        private val onDetailClick: (ResultEntry) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val title = itemView.findViewById<TextView>(R.id.resultTitle)
        private val details = itemView.findViewById<TextView>(R.id.resultDetails)
        private val progress = itemView.findViewById<ProgressBar>(R.id.resultProgress)

        fun bind(item: ResultEntry) {
            title.text = item.course
            details.text = "Score: ${item.grade} / 20 - ${item.period.semester} ${item.period.year} - ${item.ects} ECTS"
            
            progress.progress = (item.grade * 5).toInt()
            
            val context = itemView.context
            val progressDrawable = when {
                item.grade < 10 -> R.drawable.bg_progress_red
                item.grade < 15 -> R.drawable.bg_progress_yellow
                else -> R.drawable.bg_progress_green
            }
            progress.progressDrawable = ContextCompat.getDrawable(context, progressDrawable)

            itemView.setOnClickListener { onDetailClick(item) }
        }
    }

    class ResultDiffCallback : DiffUtil.ItemCallback<ResultEntry>() {
        override fun areItemsTheSame(oldItem: ResultEntry, newItem: ResultEntry): Boolean {
            return oldItem.course == newItem.course && oldItem.period == newItem.period
        }

        override fun areContentsTheSame(oldItem: ResultEntry, newItem: ResultEntry): Boolean {
            return oldItem == newItem
        }
    }
}
