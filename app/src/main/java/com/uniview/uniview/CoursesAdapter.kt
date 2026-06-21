package com.uniview.uniview

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

class CoursesAdapter(
    private val onCourseClick: (CourseEntry) -> Unit
) : ListAdapter<CourseEntry, CoursesAdapter.CourseViewHolder>(CourseDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CourseViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_course, parent, false)
        return CourseViewHolder(view, onCourseClick)
    }

    override fun onBindViewHolder(holder: CourseViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class CourseViewHolder(
        itemView: View,
        private val onCourseClick: (CourseEntry) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val name = itemView.findViewById<TextView>(R.id.courseName)
        private val professor = itemView.findViewById<TextView>(R.id.courseProfessor)
        private val meta = itemView.findViewById<TextView>(R.id.courseMeta)

        fun bind(item: CourseEntry) {
            val context = itemView.context
            name.text = item.name
            professor.text = item.professor
            // Use format_course_meta: %1$s · %2$s · %3$d ECTS
            meta.text = context.getString(R.string.format_course_meta, item.program, item.semester, item.ects)
            
            itemView.setOnClickListener { onCourseClick(item) }
        }
    }

    class CourseDiffCallback : DiffUtil.ItemCallback<CourseEntry>() {
        override fun areItemsTheSame(oldItem: CourseEntry, newItem: CourseEntry): Boolean {
            return oldItem.name == newItem.name && oldItem.program == newItem.program
        }

        override fun areContentsTheSame(oldItem: CourseEntry, newItem: CourseEntry): Boolean {
            return oldItem == newItem
        }
    }
}
