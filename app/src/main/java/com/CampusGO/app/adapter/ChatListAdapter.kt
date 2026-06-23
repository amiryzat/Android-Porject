package com.CampusGO.app.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.CampusGO.app.R
import com.CampusGO.app.model.Chat
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class ChatListAdapter(
    private var chats: List<Chat>,
    private val currentUserId: String,
    private val onClick: (Chat) -> Unit,
    private val onLongClick: (Chat) -> Unit
) : RecyclerView.Adapter<ChatListAdapter.ViewHolder>() {

    private val userCache = HashMap<String, com.CampusGO.app.model.User>()

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvAvatar: TextView = view.findViewById(R.id.tvAvatar)
        val tvName: TextView = view.findViewById(R.id.tvName)
        val tvTaskRef: TextView = view.findViewById(R.id.tvTaskRef)
        val tvLastMessage: TextView = view.findViewById(R.id.tvLastMessage)
        val tvTime: TextView = view.findViewById(R.id.tvTime)

        var presenceListener: ValueEventListener? = null
        var presenceRef: DatabaseReference? = null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_chat_list, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val chat = chats[position]
        val otherUserId = if (chat.posterId == currentUserId) chat.runnerId else chat.posterId
        val otherName = if (chat.posterId == currentUserId) chat.runnerName else chat.posterName
        
        val db = com.google.firebase.database.FirebaseDatabase.getInstance().reference
        val cachedUser = userCache[otherUserId]
        if (cachedUser != null) {
            com.CampusGO.app.utils.AvatarHelper.setAvatar(holder.tvAvatar, otherName, cachedUser.profilePicture)
        } else {
            com.CampusGO.app.utils.AvatarHelper.setAvatar(holder.tvAvatar, otherName, null)
            if (otherUserId.isNotEmpty()) {
                db.child("users").child(otherUserId).addListenerForSingleValueEvent(object : com.google.firebase.database.ValueEventListener {
                    override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                        val user = snapshot.getValue(com.CampusGO.app.model.User::class.java)
                        if (user != null) {
                            userCache[otherUserId] = user
                            com.CampusGO.app.utils.AvatarHelper.setAvatar(holder.tvAvatar, otherName, user.profilePicture)
                        }
                    }
                    override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
                })
            }
        }
        holder.tvName.text = otherName
        holder.tvLastMessage.text = chat.lastMessage.ifEmpty { "Start a conversation..." }
        holder.tvTime.text = formatAgo(chat.lastMessageTime)
        
        holder.itemView.setOnClickListener { onClick(chat) }
        holder.itemView.setOnLongClickListener {
            onLongClick(chat)
            true
        }

        // Clean up previous presence listener on this ViewHolder if any
        holder.presenceListener?.let { listener ->
            holder.presenceRef?.removeEventListener(listener)
        }
        holder.presenceListener = null
        holder.presenceRef = null

        // Show presence/last seen status below the name (reusing tvTaskRef space as a modern capsule badge)
        holder.tvTaskRef.visibility = View.VISIBLE
        holder.tvTaskRef.setBackgroundResource(R.drawable.bg_presence_badge)
        holder.tvTaskRef.textSize = 10f
        
        holder.tvTaskRef.layoutParams = holder.tvTaskRef.layoutParams.apply {
            width = ViewGroup.LayoutParams.WRAP_CONTENT
        }
        
        val density = holder.itemView.context.resources.displayMetrics.density
        val pxHorizontal = (8 * density).toInt()
        val pxVertical = (2 * density).toInt()
        holder.tvTaskRef.setPadding(pxHorizontal, pxVertical, pxHorizontal, pxVertical)
        
        if (otherUserId.isNotEmpty()) {
            val ref = FirebaseDatabase.getInstance().getReference("presence/$otherUserId")
            holder.presenceRef = ref
            
            val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val online = snapshot.child("online").getValue(Boolean::class.java) ?: false
                    val lastSeen = snapshot.child("lastSeen").getValue(Long::class.java) ?: 0L
                    
                    if (online) {
                        holder.tvTaskRef.text = "online"
                        holder.tvTaskRef.setTextColor(Color.parseColor("#059669"))
                        holder.tvTaskRef.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#D1FAE5"))
                    } else {
                        if (lastSeen > 0L) {
                            holder.tvTaskRef.text = "last seen ${formatLastSeenTime(lastSeen)}"
                        } else {
                            holder.tvTaskRef.text = "offline"
                        }
                        holder.tvTaskRef.setTextColor(Color.parseColor("#475569"))
                        holder.tvTaskRef.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#F1F5F9"))
                    }
                }

                override fun onCancelled(error: DatabaseError) {}
            }
            holder.presenceListener = listener
            ref.addValueEventListener(listener)
        } else {
            holder.tvTaskRef.visibility = View.GONE
        }
    }

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        holder.presenceListener?.let { listener ->
            holder.presenceRef?.removeEventListener(listener)
        }
        holder.presenceListener = null
        holder.presenceRef = null
    }

    override fun getItemCount() = chats.size

    fun updateChats(newChats: List<Chat>) {
        chats = ArrayList(newChats)
        notifyDataSetChanged()
    }

    private fun formatAgo(timestamp: Long): String {
        if (timestamp == 0L) return ""
        val diff = System.currentTimeMillis() - timestamp
        val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
        return when {
            minutes < 1 -> "now"
            minutes < 60 -> "${minutes}m"
            minutes < 1440 -> "${minutes / 60}h"
            else -> "${minutes / 1440}d"
        }
    }

    private fun formatLastSeenTime(timestamp: Long): String {
        val diff = System.currentTimeMillis() - timestamp
        val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
        return when {
            minutes < 1 -> "just now"
            minutes < 60 -> "${minutes}m ago"
            minutes < 1440 -> "${minutes / 60}h ago"
            else -> {
                val sdf = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault())
                sdf.format(Date(timestamp))
            }
        }
    }
}
