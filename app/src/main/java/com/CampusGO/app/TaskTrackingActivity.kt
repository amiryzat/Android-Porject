package com.CampusGO.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.CampusGO.app.databinding.ActivityTaskTrackingBinding
import com.CampusGO.app.model.PaymentStatus
import com.CampusGO.app.model.Task
import com.CampusGO.app.model.TaskStatus
import com.CampusGO.app.model.WalletTransactionStatus
import com.CampusGO.app.model.WalletTransactionType
import com.CampusGO.app.util.ChatIdHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TaskTrackingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTaskTrackingBinding

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseDatabase.getInstance().reference

    private var taskListener: ValueEventListener? = null
    private var walletListener: ValueEventListener? = null

    private var currentTask: Task? = null
    private var posterWalletBalance = 0.0
    private var isPaymentInProgress = false

    companion object {
        private const val TAG = "TaskTrackingActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTaskTrackingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val taskId = intent.getStringExtra("taskId") ?: run {
            finish()
            return
        }

        binding.btnBack.setOnClickListener { finish() }
        binding.btnReport.setOnClickListener { showReportDialog(taskId) }

        taskListener = db.child("tasks").child(taskId)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val task = snapshot.getValue(Task::class.java) ?: return
                    bindTask(task)
                }
                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "Failed to load task: ${error.message}", error.toException())
                    Toast.makeText(this@TaskTrackingActivity, "Failed to load task: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun bindTask(task: Task) {
        currentTask = task
        val uid = auth.currentUser?.uid ?: return
        val isRunner = task.runnerId == uid
        val isPoster = task.posterId == uid

        if (isPoster && walletListener == null) {
            listenPosterWallet(uid)
        }

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

        binding.btnChatPoster.setOnClickListener { openChat(task) }

        if (isRunner && (task.status == TaskStatus.ACCEPTED || task.status == TaskStatus.ON_THE_WAY)) {
            binding.llRunnerActions.visibility = View.VISIBLE
            binding.sectionPaymentPrompt.visibility = View.GONE
            binding.sectionPaymentDone.visibility = View.GONE

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
        } else {
            binding.llRunnerActions.visibility = View.GONE
            renderPaymentSection(task)
        }
    }

    private fun listenPosterWallet(uid: String) {
        walletListener = db.child("wallets").child(uid)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    posterWalletBalance = snapshot.child("balance").getValue(Double::class.java) ?: 0.0
                    currentTask?.let { task ->
                        if (task.status == TaskStatus.DELIVERED && task.paymentStatus != PaymentStatus.PAID) {
                            binding.tvPaymentWalletBalance.text = "Your Balance: RM ${String.format("%.2f", posterWalletBalance)}"
                        }
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun renderPaymentSection(task: Task) {
        val uid = auth.currentUser?.uid ?: return
        val isPoster = task.posterId == uid

        when {
            isPoster && task.status == TaskStatus.DELIVERED && task.paymentStatus != PaymentStatus.PAID -> {
                binding.sectionPaymentPrompt.visibility = View.VISIBLE
                binding.sectionPaymentDone.visibility = View.GONE
                val amount = if (task.agreedPrice > 0) task.agreedPrice else task.price
                binding.tvPaymentAmount.text = "RM ${String.format("%.2f", amount)}"
                binding.tvPaymentRunnerName.text = task.runnerName.ifEmpty { "Runner" }
                binding.tvPaymentWalletBalance.text = "Your Balance: RM ${String.format("%.2f", posterWalletBalance)}"
                binding.btnPosterConfirm.text = "Confirm & Pay RM ${String.format("%.2f", amount)}"
                binding.btnPosterConfirm.isEnabled = !isPaymentInProgress
                binding.btnPosterConfirm.setOnClickListener {
                    if (!isPaymentInProgress) initiatePayment(task)
                }
            }
            isPoster && task.status == TaskStatus.COMPLETED && task.paymentStatus == PaymentStatus.PAID -> {
                binding.sectionPaymentPrompt.visibility = View.GONE
                binding.sectionPaymentDone.visibility = View.VISIBLE
                binding.tvPaymentDoneDetail.text = "✓ Payment Sent: RM ${String.format("%.2f", task.paymentAmount)} to ${task.runnerName}"
            }
            else -> {
                binding.sectionPaymentPrompt.visibility = View.GONE
                binding.sectionPaymentDone.visibility = View.GONE
            }
        }
    }

    private fun initiatePayment(task: Task) {
        if (isPaymentInProgress) return
        if (task.paymentStatus == PaymentStatus.PAID) return
        if (task.runnerId.isBlank()) {
            Toast.makeText(this, "No runner assigned.", Toast.LENGTH_SHORT).show()
            return
        }

        val amount = if (task.agreedPrice > 0) task.agreedPrice else task.price
        if (amount <= 0) {
            Toast.makeText(this, "Invalid payment amount.", Toast.LENGTH_SHORT).show()
            return
        }

        if (posterWalletBalance < amount) {
            showInsufficientBalanceDialog(amount, posterWalletBalance)
            return
        }

        isPaymentInProgress = true
        binding.btnPosterConfirm.isEnabled = false
        binding.btnPosterConfirm.text = "Processing..."

        db.child("wallets").child(task.runnerId).get()
            .addOnSuccessListener { snap ->
                val runnerBalance = snap.child("balance").getValue(Double::class.java) ?: 0.0
                val runnerTotalEarned = snap.child("totalEarned").getValue(Double::class.java) ?: 0.0
                executePayment(task, amount, runnerBalance, runnerTotalEarned)
            }
            .addOnFailureListener { e ->
                isPaymentInProgress = false
                binding.btnPosterConfirm.isEnabled = true
                binding.btnPosterConfirm.text = "Confirm & Pay RM ${String.format("%.2f", amount)}"
                Toast.makeText(this, "Failed to process payment: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun executePayment(task: Task, amount: Double, runnerBalance: Double, runnerTotalEarned: Double) {
        val uid = auth.currentUser?.uid ?: return
        val now = System.currentTimeMillis()
        val txId = db.push().key ?: return

        val posterNewBalance = posterWalletBalance - amount
        val runnerNewBalance = runnerBalance + amount
        val runnerNewTotalEarned = runnerTotalEarned + amount

        val posterTx = mapOf(
            "id" to txId,
            "type" to WalletTransactionType.TASK_PAYMENT_SENT,
            "amount" to amount,
            "status" to WalletTransactionStatus.COMPLETED,
            "description" to "Payment for Task ${task.taskNumber}: ${task.title}",
            "taskId" to task.id,
            "taskNumber" to task.taskNumber,
            "otherUserId" to task.runnerId,
            "otherUserName" to task.runnerName,
            "createdAt" to now
        )

        val runnerTx = mapOf(
            "id" to txId,
            "type" to WalletTransactionType.TASK_PAYMENT_RECEIVED,
            "amount" to amount,
            "status" to WalletTransactionStatus.COMPLETED,
            "description" to "Earned from Task ${task.taskNumber}: ${task.title}",
            "taskId" to task.id,
            "taskNumber" to task.taskNumber,
            "otherUserId" to uid,
            "otherUserName" to (auth.currentUser?.displayName?.takeIf { it.isNotBlank() } ?: "Poster"),
            "createdAt" to now
        )

        val updates = mutableMapOf<String, Any?>()
        updates["wallets/$uid/balance"] = posterNewBalance
        updates["wallets/$uid/updatedAt"] = now
        updates["wallets/${task.runnerId}/balance"] = runnerNewBalance
        updates["wallets/${task.runnerId}/totalEarned"] = runnerNewTotalEarned
        updates["wallets/${task.runnerId}/userId"] = task.runnerId
        updates["wallets/${task.runnerId}/updatedAt"] = now
        updates["walletTransactions/$uid/$txId"] = posterTx
        updates["walletTransactions/${task.runnerId}/$txId"] = runnerTx
        updates["tasks/${task.id}/status"] = TaskStatus.COMPLETED
        updates["tasks/${task.id}/paymentStatus"] = PaymentStatus.PAID
        updates["tasks/${task.id}/paymentAmount"] = amount
        updates["tasks/${task.id}/paymentTransferredAt"] = now

        db.updateChildren(updates)
            .addOnSuccessListener {
                isPaymentInProgress = false
                Toast.makeText(this, "Payment sent! Task completed.", Toast.LENGTH_LONG).show()
                updateRunnerStats(task)
                showRateRunnerDialog(task)
            }
            .addOnFailureListener { e ->
                isPaymentInProgress = false
                binding.btnPosterConfirm.isEnabled = true
                binding.btnPosterConfirm.text = "Confirm & Pay RM ${String.format("%.2f", amount)}"
                Toast.makeText(this, "Payment failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showInsufficientBalanceDialog(required: Double, current: Double) {
        AlertDialog.Builder(this)
            .setTitle("Insufficient Wallet Balance")
            .setMessage(
                "You need RM ${String.format("%.2f", required)} to pay the runner.\n" +
                "Current Balance: RM ${String.format("%.2f", current)}\n\n" +
                "Please top up your wallet to continue."
            )
            .setPositiveButton("Top Up Wallet") { _, _ ->
                startActivity(Intent(this, WalletTopUpActivity::class.java))
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun openChat(task: Task) {
        val uid = auth.currentUser?.uid ?: run {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show()
            return
        }

        if (task.runnerId.isEmpty()) {
            Toast.makeText(this, "No runner assigned yet", Toast.LENGTH_SHORT).show()
            return
        }

        val chatId = ChatIdHelper.buildChatId(
            taskId = task.id,
            userA = task.posterId,
            userB = task.runnerId
        )

        Log.d(TAG, "Opening chat from tracking — chatId: $chatId")
        ensureChatExists(task, chatId)
    }

    private fun ensureChatExists(task: Task, chatId: String) {
        val runnerName = task.runnerName.ifEmpty { "Runner" }

        db.child("chats").child(chatId).get()
            .addOnSuccessListener { snap ->
                if (!snap.exists()) {
                    val chat = mapOf(
                        "id" to chatId,
                        "taskId" to task.id,
                        "taskTitle" to task.title,
                        "taskNumber" to task.taskNumber,
                        "posterId" to task.posterId,
                        "posterName" to task.posterName,
                        "runnerId" to task.runnerId,
                        "runnerName" to runnerName,
                        "participants" to mapOf(task.posterId to true, task.runnerId to true),
                        "lastMessage" to "",
                        "lastMessageTime" to 0L,
                        "finalPrice" to if (task.agreedPrice > 0) task.agreedPrice else 0.0
                    )
                    db.child("chats").child(chatId).setValue(chat)
                        .addOnSuccessListener { openChatActivity(task.id, chatId) }
                        .addOnFailureListener { e ->
                            Toast.makeText(this, "Failed to create chat: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                } else {
                    openChatActivity(task.id, chatId)
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to open chat: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun openChatActivity(taskId: String, chatId: String) {
        startActivity(Intent(this, ChatActivity::class.java).apply {
            putExtra("taskId", taskId)
            putExtra("chatId", chatId)
        })
    }

    private fun updateTimeline(task: Task) {
        data class StepIds(
            val dotId: Int,
            val titleId: Int,
            val timeId: Int,
            val lineId: Int?,
            val status: String
        )

        val steps = listOf(
            StepIds(R.id.dotPosted, R.id.titlePosted, R.id.timePosted, R.id.linePosted, TaskStatus.OPEN),
            StepIds(R.id.dotAccepted, R.id.titleAccepted, R.id.timeAccepted, R.id.lineAccepted, TaskStatus.ACCEPTED),
            StepIds(R.id.dotOnTheWay, R.id.titleOnTheWay, R.id.timeOnTheWay, R.id.lineOnTheWay, TaskStatus.ON_THE_WAY),
            StepIds(R.id.dotDelivered, R.id.titleDelivered, R.id.timeDelivered, R.id.lineDelivered, TaskStatus.DELIVERED),
            StepIds(R.id.dotCompleted, R.id.titleCompleted, R.id.timeCompleted, null, TaskStatus.COMPLETED)
        )

        val statusOrder = listOf(
            TaskStatus.OPEN, TaskStatus.ACCEPTED, TaskStatus.ON_THE_WAY, TaskStatus.DELIVERED, TaskStatus.COMPLETED
        )

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
                db.child("tasks").child(task.id).updateChildren(
                    mapOf("status" to TaskStatus.OPEN, "runnerId" to "", "runnerName" to "", "agreedPrice" to 0.0)
                ).addOnSuccessListener {
                    Toast.makeText(this, "Task cancelled. It's back in the feed.", Toast.LENGTH_SHORT).show()
                    finish()
                }.addOnFailureListener { e ->
                    Toast.makeText(this, "Failed to cancel task: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Keep Going", null)
            .show()
    }

    private fun updateRunnerStats(task: Task) {
        if (task.runnerId.isEmpty()) return
        db.child("userStats").child(task.runnerId).get()
            .addOnSuccessListener { snap ->
                val completed = (snap.child("completedTasks").getValue(Int::class.java) ?: 0) + 1
                db.child("userStats").child(task.runnerId).child("completedTasks").setValue(completed)
            }
    }

    private fun showRateRunnerDialog(task: Task) {
        if (task.runnerId.isEmpty() || isFinishing || isDestroyed) return

        val ratings = arrayOf(
            "★★★★★  5 - Excellent",
            "★★★★  4 - Good",
            "★★★  3 - Average",
            "★★  2 - Poor",
            "★  1 - Very Poor"
        )
        var selectedRating = 5

        AlertDialog.Builder(this)
            .setTitle("Rate ${task.runnerName}")
            .setSingleChoiceItems(ratings, 0) { _, which -> selectedRating = 5 - which }
            .setPositiveButton("Submit Rating") { _, _ -> submitRating(task.runnerId, selectedRating.toDouble()) }
            .setNegativeButton("Skip", null)
            .show()
    }

    private fun submitRating(runnerId: String, newRating: Double) {
        db.child("userStats").child(runnerId).get()
            .addOnSuccessListener { snap ->
                val total = snap.child("totalReviews").getValue(Int::class.java) ?: 0
                val currentRating = snap.child("rating").getValue(Double::class.java) ?: 5.0
                val updatedTotal = total + 1
                val updatedRating = ((currentRating * total) + newRating) / updatedTotal

                db.child("userStats").child(runnerId).updateChildren(
                    mapOf("rating" to updatedRating, "totalReviews" to updatedTotal)
                )
                db.child("users").child(runnerId).updateChildren(
                    mapOf("rating" to updatedRating, "totalReviews" to updatedTotal)
                )
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
        val uid = auth.currentUser?.uid
        if (uid != null) walletListener?.let { db.child("wallets").child(uid).removeEventListener(it) }
    }
}
