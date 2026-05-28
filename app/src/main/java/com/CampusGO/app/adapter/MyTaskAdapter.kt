package com.CampusGO.app.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.CampusGO.app.R
import com.CampusGO.app.model.Task
import com.CampusGO.app.model.TaskStatus

class MyTaskAdapter(
    private var tasks: List<Task>,
    private val onClick: (Task) -> Unit
) : RecyclerView.Adapter<MyTaskAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTaskNumber: TextView = view.findViewById(R.id.tvTaskNumber)
        val tvStatus: TextView = view.findViewById(R.id.tvStatus)
        val tvTitle: TextView = view.findViewById(R.id.tvTitle)
        val tvCategory: TextView = view.findViewById(R.id.tvCategory)
        val tvPrice: TextView = view.findViewById(R.id.tvPrice)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_my_task, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val task = tasks[position]
        holder.tvTaskNumber.text = "#${task.taskNumber}"
        holder.tvTitle.text = task.title
        holder.tvCategory.text = task.category

        val displayPrice = if (task.agreedPrice > 0) task.agreedPrice else task.price
        holder.tvPrice.text = "RM ${String.format("%.2f", displayPrice)}"

        val (statusText, bgRes, textColor) = when (task.status) {
            TaskStatus.OPEN -> Triple("OPEN", R.drawable.bg_status_open, Color.parseColor("#3730A3"))
            TaskStatus.ACCEPTED -> Triple("ACCEPTED", R.drawable.bg_status_accepted, Color.parseColor("#9A3412"))
            TaskStatus.ON_THE_WAY -> Triple("ON THE WAY", R.drawable.bg_status_accepted, Color.parseColor("#9A3412"))
            TaskStatus.DELIVERED -> Triple("DELIVERED", R.drawable.bg_status_open, Color.parseColor("#3730A3"))
            TaskStatus.COMPLETED -> Triple("COMPLETED", R.drawable.bg_status_completed, Color.parseColor("#166534"))
            TaskStatus.CANCELLED -> Triple("CANCELLED", R.drawable.bg_emergency_badge, Color.parseColor("#991B1B"))
            else -> Triple(task.status, R.drawable.bg_tag, Color.BLACK)
        }
        holder.tvStatus.text = statusText
        holder.tvStatus.setBackgroundResource(bgRes)
        holder.tvStatus.setTextColor(textColor)

        holder.itemView.setOnClickListener { onClick(task) }
    }

    override fun getItemCount() = tasks.size

    fun updateTasks(newTasks: List<Task>) {
        tasks = ArrayList(newTasks)
        notifyDataSetChanged()
    }
}
