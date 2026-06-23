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
    private val onChat: (Task) -> Unit,
    private val onItemClick: ((Task) -> Unit)? = null
) : RecyclerView.Adapter<TaskFeedAdapter.ViewHolder>() {

    private val userCache = HashMap<String, com.CampusGO.app.model.User>()

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvAvatar: TextView = view.findViewById(R.id.tvAvatar)
        val tvPosterName: TextView = view.findViewById(R.id.tvPosterName)
        val tvRating: TextView = view.findViewById(R.id.tvRating)
        val tvTimePosted: TextView = view.findViewById(R.id.tvTimePosted)
        val tvEmergencyBadge: TextView = view.findViewById(R.id.tvEmergencyBadge)
        val tvCategory: TextView = view.findViewById(R.id.tvCategory)
        val tvTitle: TextView = view.findViewById(R.id.tvTitle)
        val tvDescription: TextView = view.findViewById(R.id.tvDescription)
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

        val posterId = task.posterId
        val db = com.google.firebase.database.FirebaseDatabase.getInstance().reference
        val cachedUser = userCache[posterId]
        if (cachedUser != null) {
            com.CampusGO.app.utils.AvatarHelper.setAvatar(holder.tvAvatar, task.posterName, cachedUser.profilePicture)
        } else {
            com.CampusGO.app.utils.AvatarHelper.setAvatar(holder.tvAvatar, task.posterName, null)
            db.child("users").child(posterId).addListenerForSingleValueEvent(object : com.google.firebase.database.ValueEventListener {
                override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                    val user = snapshot.getValue(com.CampusGO.app.model.User::class.java)
                    if (user != null) {
                        userCache[posterId] = user
                        com.CampusGO.app.utils.AvatarHelper.setAvatar(holder.tvAvatar, task.posterName, user.profilePicture)
                    }
                }
                override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
            })
        }
        holder.tvPosterName.text = task.posterName
        holder.tvRating.text = "★ ${task.posterRating} · ${formatTime(task.createdAt)}"
        holder.tvTimePosted.text = formatAgo(task.createdAt)
        holder.tvTitle.text = task.title
        holder.tvDescription.text = task.description.ifEmpty { "No description provided." }
        holder.tvPrice.text = "RM ${String.format("%.2f", task.price)}"
        holder.tvCategory.text = task.category
        holder.tvNegotiable.visibility = if (task.isNegotiable) View.VISIBLE else View.GONE
        holder.tvEmergencyBadge.visibility = if (task.isEmergency) View.VISIBLE else View.GONE

        holder.btnAccept.text = "Accept"
        holder.btnAccept.setOnClickListener { onAccept(task) }
        holder.btnChat.setOnClickListener { onChat(task) }

        // Make whole card clickable for task detail navigation
        holder.itemView.setOnClickListener { onItemClick?.invoke(task) }
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
