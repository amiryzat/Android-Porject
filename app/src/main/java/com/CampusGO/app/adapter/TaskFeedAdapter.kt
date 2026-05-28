package com.CampusGO.app.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.CampusGO.app.R
import com.CampusGO.app.model.Task
import com.CampusGO.app.model.TaskCategory
import com.google.android.material.button.MaterialButton
import java.util.concurrent.TimeUnit

class TaskFeedAdapter(
    private var tasks: List<Task>,
    private val onAccept: (Task) -> Unit,
    private val onChat: (Task) -> Unit
) : RecyclerView.Adapter<TaskFeedAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvAvatar: TextView = view.findViewById(R.id.tvAvatar)
        val tvPosterName: TextView = view.findViewById(R.id.tvPosterName)
        val tvRating: TextView = view.findViewById(R.id.tvRating)
        val tvTimePosted: TextView = view.findViewById(R.id.tvTimePosted)
        val tvEmergencyBadge: TextView = view.findViewById(R.id.tvEmergencyBadge)
        val tvCategory: TextView = view.findViewById(R.id.tvCategory)
        val tvTitle: TextView = view.findViewById(R.id.tvTitle)
        val tvDescription: TextView = view.findViewById(R.id.tvDescription)
        val tvRoute: TextView = view.findViewById(R.id.tvRoute)
        val tvPrice: TextView = view.findViewById(R.id.tvPrice)
        val tvNegotiable: TextView = view.findViewById(R.id.tvNegotiable)
        val btnChat: MaterialButton = view.findViewById(R.id.btnChat)
        val btnAccept: MaterialButton = view.findViewById(R.id.btnAccept)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_task_feed, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val task = tasks[position]

        holder.tvAvatar.text = task.posterName.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
        holder.tvPosterName.text = task.posterName
        holder.tvRating.text = "★ ${task.posterRating} · ${formatTime(task.createdAt)}"
        holder.tvTimePosted.text = formatAgo(task.createdAt)
        holder.tvTitle.text = task.title
        holder.tvDescription.text = task.description.ifEmpty { "No description provided." }
        holder.tvRoute.text = "OFFER · ${task.pickup} → ${task.dropoff}"
        holder.tvPrice.text = "RM ${String.format("%.2f", task.price)}"
        holder.tvCategory.text = task.category
        holder.tvNegotiable.visibility = if (task.isNegotiable) View.VISIBLE else View.GONE
        holder.tvEmergencyBadge.visibility = if (task.isEmergency) View.VISIBLE else View.GONE

        holder.btnAccept.text = "Accept"
        holder.btnAccept.setOnClickListener { onAccept(task) }
        holder.btnChat.setOnClickListener { onChat(task) }
    }

    override fun getItemCount() = tasks.size

    fun updateTasks(newTasks: List<Task>) {
        tasks = ArrayList(newTasks)
        notifyDataSetChanged()
    }

    private fun formatTime(timestamp: Long): String {
        val diff = System.currentTimeMillis() - timestamp
        val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
        return "${minutes}m"
    }

    private fun formatAgo(timestamp: Long): String {
        val diff = System.currentTimeMillis() - timestamp
        val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
        return when {
            minutes < 1 -> "just now"
            minutes < 60 -> "${minutes}m ago"
            minutes < 1440 -> "${minutes / 60}h ago"
            else -> "${minutes / 1440}d ago"
        }
    }
}
