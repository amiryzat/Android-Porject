package com.CampusGO.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.CampusGO.app.databinding.ActivityTaskTrackingBinding
import com.CampusGO.app.model.Task
import com.CampusGO.app.model.TaskStatus
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.text.SimpleDateFormat
import java.util.*

class TaskTrackingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTaskTrackingBinding
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseDatabase.getInstance().reference
    private var taskListener: ValueEventListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTaskTrackingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val taskId = intent.getStringExtra("taskId") ?: run { finish(); return }

        binding.btnBack.setOnClickListener { finish() }
        binding.btnReport.setOnClickListener { showReportDialog(taskId) }

        taskListener = db.child("tasks").child(taskId).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val task = snapshot.getValue(Task::class.java) ?: return
                bindTask(task)
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun bindTask(task: Task) {
        val uid = auth.currentUser?.uid ?: return
        val isRunner = task.runnerId == uid
        val isPoster = task.posterId == uid

        binding.tvTaskNumber.text = "Task · ${task.taskNumber}"
        val displayPrice = if (task.agreedPrice > 0) task.agreedPrice else task.price
        binding.tvFinalPrice.text = "RM ${String.format("%.2f", displayPrice)}"
        binding.tvStatusBadge.text = when (task.status) {
            TaskStatus.ACCEPTED -> "ACCEPTED"
            TaskStatus.ON_THE_WAY -> "ON THE WAY"
            TaskStatus.DELIVERED -> "DELIVERED"
            TaskStatus.COMPLETED -> "COMPLETED ✓"
            TaskStatus.CANCELLED -> "CANCELLED"
            else -> task.status
        }

        updateTimeline(task)
        updateParticipants(task)

        binding.btnChatPoster.setOnClickListener {
            val chatId = if (isRunner) "${task.id}_${uid}" else "${task.id}_${task.runnerId}"
            startActivity(Intent(this, ChatActivity::class.java).apply {
                putExtra("taskId", task.id)
                putExtra("chatId", chatId)
            })
        }

        if (isRunner && (task.status == TaskStatus.ACCEPTED || task.status == TaskStatus.ON_THE_WAY)) {
            binding.llRunnerActions.visibility = View.VISIBLE
            binding.btnPosterConfirm.visibility = View.GONE

            binding.btnCancel.setOnClickListener { confirmCancelAsRunner(task) }

            when (task.status) {
                TaskStatus.ACCEPTED -> {
                    binding.btnAction.text = "On the Way"
                    binding.btnAction.setOnClickListener {
                        db.child("tasks").child(task.id).child("status").setValue(TaskStatus.ON_THE_WAY)
                    }
                }
                TaskStatus.ON_THE_WAY -> {
                    binding.btnAction.text = "Mark Delivered"
                    binding.btnAction.setOnClickListener {
                        db.child("tasks").child(task.id).child("status").setValue(TaskStatus.DELIVERED)
                    }
                }
            }
        } else if (isPoster && task.status == TaskStatus.DELIVERED) {
            binding.llRunnerActions.visibility = View.GONE
            binding.btnPosterConfirm.visibility = View.VISIBLE
            binding.btnPosterConfirm.setOnClickListener { confirmReceivedAsPoster(task) }
        } else {
            binding.llRunnerActions.visibility = View.GONE
            binding.btnPosterConfirm.visibility = View.GONE
        }
    }

    private fun updateTimeline(task: Task) {
        data class StepIds(val dotId: Int, val titleId: Int, val timeId: Int, val lineId: Int?, val status: String)

        val steps = listOf(
            StepIds(R.id.dotPosted, R.id.titlePosted, R.id.timePosted, R.id.linePosted, TaskStatus.OPEN),
            StepIds(R.id.dotAccepted, R.id.titleAccepted, R.id.timeAccepted, R.id.lineAccepted, TaskStatus.ACCEPTED),
            StepIds(R.id.dotOnTheWay, R.id.titleOnTheWay, R.id.timeOnTheWay, R.id.lineOnTheWay, TaskStatus.ON_THE_WAY),
            StepIds(R.id.dotDelivered, R.id.titleDelivered, R.id.timeDelivered, R.id.lineDelivered, TaskStatus.DELIVERED),
            StepIds(R.id.dotCompleted, R.id.titleCompleted, R.id.timeCompleted, null, TaskStatus.COMPLETED)
        )

        val statusOrder = listOf(TaskStatus.OPEN, TaskStatus.ACCEPTED, TaskStatus.ON_THE_WAY, TaskStatus.DELIVERED, TaskStatus.COMPLETED)
        val currentIndex = statusOrder.indexOf(task.status).coerceAtLeast(0)
        val createdTime = formatTime(task.createdAt)

        steps.forEachIndexed { index, step ->
            val tvDot = binding.root.findViewById<TextView>(step.dotId)
            val tvTitle = binding.root.findViewById<TextView>(step.titleId)
            val tvTime = binding.root.findViewById<TextView>(step.timeId)
            step.lineId?.let { binding.root.findViewById<View>(it).visibility = View.VISIBLE }

            when {
                index < currentIndex -> {
                    tvDot.text = "✓"
                    tvDot.setBackgroundResource(R.drawable.bg_status_completed)
                    tvTitle.setTextColor(Color.parseColor("#0F172A"))
                    tvTime.text = if (index == 0) createdTime else ""
                }
                index == currentIndex -> {
                    tvDot.text = "●"
                    tvDot.setBackgroundResource(R.drawable.bg_status_open)
                    tvTitle.setTextColor(Color.parseColor("#0F172A"))
                    tvTime.text = if (index == 0) createdTime else ""
                }
                else -> {
                    tvDot.text = ""
                    tvDot.setBackgroundResource(R.drawable.bg_avatar)
                    tvTitle.setTextColor(Color.parseColor("#94A3B8"))
                    tvTime.text = "–"
                }
            }
        }
    }

    private fun updateParticipants(task: Task) {
        binding.tvPosterAvatar.text = task.posterName.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
        binding.tvPosterName.text = task.posterName
        binding.tvPosterRating.text = "★ ${task.posterRating}"

        if (task.runnerId.isNotEmpty()) {
            binding.tvRunnerAvatar.text = task.runnerName.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
            binding.tvRunnerName.text = task.runnerName
        } else {
            binding.tvRunnerName.text = "Waiting for runner..."
        }
    }

    private fun confirmCancelAsRunner(task: Task) {
        AlertDialog.Builder(this)
            .setTitle("Cancel Task")
            .setMessage("Are you sure you want to cancel this task? Your acceptance will be removed and the task will return to the feed.")
            .setPositiveButton("Yes, Cancel") { _, _ ->
                db.child("tasks").child(task.id).updateChildren(mapOf(
                    "status" to TaskStatus.OPEN,
                    "runnerId" to "",
                    "runnerName" to "",
                    "agreedPrice" to 0.0
                )).addOnSuccessListener {
                    Toast.makeText(this, "Task cancelled. It's back in the feed.", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            .setNegativeButton("Keep Going", null)
            .show()
    }

    private fun confirmReceivedAsPoster(task: Task) {
        AlertDialog.Builder(this)
            .setTitle("Confirm Receipt")
            .setMessage("Confirm that you received \"${task.title}\"? This will complete the task and release payment to the runner.")
            .setPositiveButton("Yes, Received!") { _, _ ->
                db.child("tasks").child(task.id).child("status").setValue(TaskStatus.COMPLETED)
                    .addOnSuccessListener {
                        updateRunnerStats(task)
                        Toast.makeText(this, "Task completed!", Toast.LENGTH_SHORT).show()
                        showRateRunnerDialog(task)
                    }
            }
            .setNegativeButton("Not Yet", null)
            .show()
    }

    private fun updateRunnerStats(task: Task) {
        if (task.runnerId.isEmpty()) return
        db.child("userStats").child(task.runnerId).get().addOnSuccessListener { snap ->
            val completed = (snap.child("completedTasks").getValue(Int::class.java) ?: 0) + 1
            db.child("userStats").child(task.runnerId).child("completedTasks").setValue(completed)
        }
    }

    private fun showRateRunnerDialog(task: Task) {
        if (task.runnerId.isEmpty()) return
        val ratings = arrayOf("★★★★★  5 - Excellent", "★★★★  4 - Good", "★★★  3 - Average", "★★  2 - Poor", "★  1 - Very Poor")
        var selectedRating = 5
        AlertDialog.Builder(this)
            .setTitle("Rate ${task.runnerName}")
            .setSingleChoiceItems(ratings, 0) { _, which -> selectedRating = 5 - which }
            .setPositiveButton("Submit Rating") { _, _ ->
                submitRating(task.runnerId, selectedRating.toDouble())
            }
            .setNegativeButton("Skip", null)
            .show()
    }

    private fun submitRating(runnerId: String, newRating: Double) {
        db.child("userStats").child(runnerId).get().addOnSuccessListener { snap ->
            val total = snap.child("totalReviews").getValue(Int::class.java) ?: 0
            val currentRating = snap.child("rating").getValue(Double::class.java) ?: 5.0
            val updatedTotal = total + 1
            val updatedRating = ((currentRating * total) + newRating) / updatedTotal
            db.child("userStats").child(runnerId).updateChildren(mapOf(
                "rating" to updatedRating,
                "totalReviews" to updatedTotal
            ))
            db.child("users").child(runnerId).updateChildren(mapOf(
                "rating" to updatedRating,
                "totalReviews" to updatedTotal
            ))
            Toast.makeText(this, "Rating submitted!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showReportDialog(taskId: String) {
        val reasons = arrayOf("Item not delivered", "Runner no-show", "Wrong item delivered", "Rude behaviour", "Other")
        var selectedReason = reasons[0]
        AlertDialog.Builder(this)
            .setTitle("Report an Issue")
            .setSingleChoiceItems(reasons, 0) { _, which -> selectedReason = reasons[which] }
            .setPositiveButton("Submit Report") { _, _ ->
                val uid = auth.currentUser?.uid ?: return@setPositiveButton
                val report = mapOf(
                    "taskId" to taskId,
                    "reporterId" to uid,
                    "reason" to selectedReason,
                    "createdAt" to System.currentTimeMillis()
                )
                db.child("reports").push().setValue(report)
                Toast.makeText(this, "Report submitted. Our team will review it.", Toast.LENGTH_LONG).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun formatTime(timestamp: Long): String {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    override fun onDestroy() {
        super.onDestroy()
        val taskId = intent.getStringExtra("taskId") ?: return
        taskListener?.let { db.child("tasks").child(taskId).removeEventListener(it) }
    }
}
