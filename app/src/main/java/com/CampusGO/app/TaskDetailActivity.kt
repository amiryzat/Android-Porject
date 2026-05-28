package com.CampusGO.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.CampusGO.app.databinding.ActivityTaskDetailBinding
import com.CampusGO.app.model.Task
import com.CampusGO.app.model.TaskStatus
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class TaskDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTaskDetailBinding
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseDatabase.getInstance().reference
    private var currentTask: Task? = null
    private var taskListener: ValueEventListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTaskDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val taskId = intent.getStringExtra("taskId") ?: run { finish(); return }

        binding.btnBack.setOnClickListener { finish() }

        binding.btnMore.setOnClickListener {
            val task = currentTask ?: return@setOnClickListener
            val uid = auth.currentUser?.uid ?: return@setOnClickListener
            if (task.posterId == uid) {
                val popup = PopupMenu(this, binding.btnMore)
                popup.menu.add("Cancel Task")
                popup.setOnMenuItemClickListener {
                    confirmCancelTask(task)
                    true
                }
                popup.show()
            }
        }

        taskListener = db.child("tasks").child(taskId).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val task = snapshot.getValue(Task::class.java) ?: return
                currentTask = task
                bindTask(task)
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun bindTask(task: Task) {
        val uid = auth.currentUser?.uid ?: return
        val isMyTask = task.posterId == uid
        val isRunner = task.runnerId == uid

        binding.tvTaskNumber.text = "Task · ${task.taskNumber}"
        binding.tvTitle.text = task.title
        binding.tvPickup.text = task.pickup
        binding.tvDropoff.text = task.dropoff
        binding.tvDescription.text = task.description.ifEmpty { "No description provided." }
        binding.tvPrice.text = "RM ${String.format("%.2f", task.price)}"
        binding.tvCategoryTag.text = task.category
        binding.tvEmergencyTag.visibility = if (task.isEmergency) View.VISIBLE else View.GONE
        binding.tvNegotiable.visibility = if (task.isNegotiable) View.VISIBLE else View.GONE

        val posterInitial = task.posterName.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
        binding.tvPosterAvatar.text = posterInitial
        binding.tvPosterName.text = task.posterName
        binding.tvPosterStats.text = "★ ${task.posterRating} (${task.posterReviews} reviews) · posted"

        binding.btnAccept.text = "Accept @ RM ${String.format("%.2f", task.price)}"

        when {
            isMyTask -> {
                binding.llRunnerActions.visibility = View.GONE
                binding.llPosterActions.visibility = if (task.status != TaskStatus.OPEN) View.VISIBLE else View.GONE
                binding.btnTrack.setOnClickListener {
                    startActivity(Intent(this, TaskTrackingActivity::class.java).apply {
                        putExtra("taskId", task.id)
                    })
                }
            }
            task.status != TaskStatus.OPEN -> {
                binding.llRunnerActions.visibility = View.GONE
                if (isRunner) {
                    binding.llPosterActions.visibility = View.VISIBLE
                    binding.btnTrack.setOnClickListener {
                        startActivity(Intent(this, TaskTrackingActivity::class.java).apply {
                            putExtra("taskId", task.id)
                        })
                    }
                }
            }
            else -> {
                binding.llRunnerActions.visibility = View.VISIBLE
                binding.llPosterActions.visibility = View.GONE

                binding.btnNegotiate.setOnClickListener {
                    val chatId = "${task.id}_${uid}"
                    ensureChatExists(task, chatId)
                    startActivity(Intent(this, ChatActivity::class.java).apply {
                        putExtra("taskId", task.id)
                        putExtra("chatId", chatId)
                    })
                }

                binding.btnAccept.setOnClickListener {
                    startActivity(Intent(this, ConfirmAcceptActivity::class.java).apply {
                        putExtra("taskId", task.id)
                        putExtra("agreedPrice", task.price)
                    })
                }
            }
        }
    }

    private fun ensureChatExists(task: Task, chatId: String) {
        val uid = auth.currentUser?.uid ?: return
        val runnerName = auth.currentUser?.displayName ?: "Unknown"
        db.child("chats").child(chatId).get().addOnSuccessListener { snap ->
            if (!snap.exists()) {
                val chat = mapOf(
                    "id" to chatId,
                    "taskId" to task.id,
                    "taskTitle" to task.title,
                    "taskNumber" to task.taskNumber,
                    "posterId" to task.posterId,
                    "posterName" to task.posterName,
                    "runnerId" to uid,
                    "runnerName" to runnerName,
                    "lastMessage" to "",
                    "lastMessageTime" to 0L,
                    "finalPrice" to 0.0
                )
                db.child("chats").child(chatId).setValue(chat)
            }
        }
    }

    private fun confirmCancelTask(task: Task) {
        android.app.AlertDialog.Builder(this)
            .setTitle("Cancel Task")
            .setMessage("Are you sure you want to cancel \"${task.title}\"? It will be removed from the feed.")
            .setPositiveButton("Cancel Task") { _, _ ->
                db.child("tasks").child(task.id).child("status").setValue(TaskStatus.CANCELLED)
                    .addOnSuccessListener {
                        Toast.makeText(this, "Task cancelled.", Toast.LENGTH_SHORT).show()
                        finish()
                    }
            }
            .setNegativeButton("Keep It") { d, _ -> d.dismiss() }
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        val taskId = intent.getStringExtra("taskId") ?: return
        taskListener?.let { db.child("tasks").child(taskId).removeEventListener(it) }
    }
}
