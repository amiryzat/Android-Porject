package com.CampusGO.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.CampusGO.app.databinding.ActivityTaskTrackingBinding
import com.CampusGO.app.model.PaymentStatus
import com.CampusGO.app.model.Task
import com.CampusGO.app.model.TaskStatus
import com.CampusGO.app.model.Wallet
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
    private var posterWalletBalance: Double = 0.0
    private var isPaymentInProgress = false

    private val topUpLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            isPaymentInProgress = false
        }
    }

    companion object {
        private const val TAG = "TaskTrackingActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityTaskTrackingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val taskId = intent.getStringExtra("taskId") ?: run { finish(); return }

        binding.btnBack.setOnClickListener { finish() }
        binding.btnReport.setOnClickListener { showReportDialog(taskId) }

        val uid = auth.currentUser?.uid
        if (uid != null) {
            listenPosterWallet(uid)
        }

        taskListener = db.child("tasks").child(taskId)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val task = snapshot.getValue(Task::class.java) ?: return
                    if (task.id.isBlank()) task.id = snapshot.key ?: ""
                    currentTask = task
                    bindTask(task)
                }
                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "Failed to load task: ${error.message}", error.toException())
                    Toast.makeText(this@TaskTrackingActivity,
                        "Failed to load task: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun listenPosterWallet(uid: String) {
        walletListener = db.child("wallets").child(uid)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val wallet = snapshot.getValue(Wallet::class.java)
                    posterWalletBalance = wallet?.balance ?: 0.0
                    currentTask?.let { renderPaymentSection(it) }
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun bindTask(task: Task) {
        val uid = auth.currentUser?.uid ?: return
        val isRunner = task.runnerId == uid

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

    private fun renderPaymentSection(task: Task) {
        val uid = auth.currentUser?.uid ?: return
        val isPoster = task.posterId == uid

        val displayPrice = if (task.agreedPrice > 0) task.agreedPrice else task.price
        val amountStr = "RM ${String.format("%.2f", displayPrice)}"

        when {
            isPoster && task.status == TaskStatus.DELIVERED && task.paymentStatus != PaymentStatus.PAID -> {
                binding.sectionPaymentPrompt.visibility = View.VISIBLE
                binding.sectionPaymentDone.visibility = View.GONE

                binding.tvPaymentAmount.text = amountStr
                binding.tvPaymentRunnerName.text = task.runnerName.ifBlank { "Runner" }
                binding.tvPaymentWalletBalance.text = "RM ${String.format("%.2f", posterWalletBalance)}"
                binding.btnPosterConfirm.text = "Confirm & Pay $amountStr"
                binding.btnPosterConfirm.isEnabled = !isPaymentInProgress

                binding.btnPosterConfirm.setOnClickListener {
                    if (!isPaymentInProgress) initiatePayment(task)
                }
            }

            isPoster && task.status == TaskStatus.COMPLETED && task.paymentStatus == PaymentStatus.PAID -> {
                binding.sectionPaymentPrompt.visibility = View.GONE
                binding.sectionPaymentDone.visibility = View.VISIBLE

                val paidAmt = if (task.paymentAmount > 0) task.paymentAmount else displayPrice
                binding.tvPaymentDoneDetail.text =
                    "Paid RM ${String.format("%.2f", paidAmt)} to ${task.runnerName.ifBlank { "Runner" }}"
            }

            else -> {
                binding.sectionPaymentPrompt.visibility = View.GONE
                binding.sectionPaymentDone.visibility = View.GONE
            }
        }
    }

    private fun initiatePayment(task: Task) {
        if (task.paymentStatus == PaymentStatus.PAID) return
        if (task.runnerId.isBlank()) {
            Toast.makeText(this, "Runner information is missing.", Toast.LENGTH_SHORT).show()
            return
        }

        val paymentAmount = if (task.agreedPrice > 0) task.agreedPrice else task.price
        if (paymentAmount <= 0) {
            Toast.makeText(this, "Invalid task price.", Toast.LENGTH_SHORT).show()
            return
        }

        if (posterWalletBalance < paymentAmount) {
            showInsufficientBalanceDialog(paymentAmount, posterWalletBalance)
            return
        }

        isPaymentInProgress = true
        binding.btnPosterConfirm.isEnabled = false
        binding.btnPosterConfirm.text = "Processing…"

        val posterId = task.posterId
        val runnerId = task.runnerId

        db.child("wallets").child(runnerId).get()
            .addOnSuccessListener { runnerSnap ->
                val runnerWallet = runnerSnap.getValue(Wallet::class.java)
                val runnerBalance = runnerWallet?.balance ?: 0.0
                val runnerEarned = runnerWallet?.totalEarned ?: 0.0

                executePayment(
                    task = task,
                    paymentAmount = paymentAmount,
                    posterId = posterId,
                    posterBalance = posterWalletBalance,
                    runnerId = runnerId,
                    runnerBalance = runnerBalance,
                    runnerEarned = runnerEarned
                )
            }
            .addOnFailureListener { e ->
                isPaymentInProgress = false
                binding.btnPosterConfirm.isEnabled = true
                binding.btnPosterConfirm.text = "Confirm & Pay ${"RM ${String.format("%.2f", paymentAmount)}"}"
                Toast.makeText(this, "Could not load runner wallet: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun executePayment(
        task: Task,
        paymentAmount: Double,
        posterId: String,
        posterBalance: Double,
        runnerId: String,
        runnerBalance: Double,
        runnerEarned: Double
    ) {
        val posterTxRef = db.child("walletTransactions").child(posterId).push()
        val runnerTxRef = db.child("walletTransactions").child(runnerId).push()
        val posterTxId = posterTxRef.key
        val runnerTxId = runnerTxRef.key

        if (posterTxId == null || runnerTxId == null) {
            isPaymentInProgress = false
            binding.btnPosterConfirm.isEnabled = true
            binding.btnPosterConfirm.text = "Confirm & Pay RM ${String.format("%.2f", paymentAmount)}"
            Toast.makeText(this, "Payment setup failed. Please try again.", Toast.LENGTH_SHORT).show()
            return
        }

        val now = System.currentTimeMillis()

        val posterTx = mapOf(
            "id" to posterTxId,
            "type" to WalletTransactionType.TASK_PAYMENT_SENT,
            "amount" to paymentAmount,
            "status" to WalletTransactionStatus.COMPLETED,
            "description" to "Paid ${task.runnerName} for task ${task.taskNumber}",
            "taskId" to task.id,
            "taskNumber" to task.taskNumber,
            "otherUserId" to runnerId,
            "otherUserName" to task.runnerName,
            "createdAt" to now
        )

        val runnerTx = mapOf(
            "id" to runnerTxId,
            "type" to WalletTransactionType.TASK_PAYMENT_RECEIVED,
            "amount" to paymentAmount,
            "status" to WalletTransactionStatus.COMPLETED,
            "description" to "Received payment from ${task.posterName} for task ${task.taskNumber}",
            "taskId" to task.id,
            "taskNumber" to task.taskNumber,
            "otherUserId" to posterId,
            "otherUserName" to task.posterName,
            "createdAt" to now
        )

        val updates: Map<String, Any> = mapOf(
            "wallets/$posterId/balance" to (posterBalance - paymentAmount),
            "wallets/$posterId/totalSpent" to paymentAmount,
            "wallets/$posterId/updatedAt" to now,
            "wallets/$runnerId/balance" to (runnerBalance + paymentAmount),
            "wallets/$runnerId/totalEarned" to (runnerEarned + paymentAmount),
            "wallets/$runnerId/userId" to runnerId,
            "wallets/$runnerId/updatedAt" to now,
            "walletTransactions/$posterId/$posterTxId" to posterTx,
            "walletTransactions/$runnerId/$runnerTxId" to runnerTx,
            "tasks/${task.id}/status" to TaskStatus.COMPLETED,
            "tasks/${task.id}/paymentStatus" to PaymentStatus.PAID,
            "tasks/${task.id}/paymentAmount" to paymentAmount,
            "tasks/${task.id}/paymentTransferredAt" to now
        )

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
                binding.btnPosterConfirm.text = "Confirm & Pay RM ${String.format("%.2f", paymentAmount)}"
                Log.e(TAG, "Payment failed", e)
                Toast.makeText(this, "Payment failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun showInsufficientBalanceDialog(required: Double, current: Double) {
        AlertDialog.Builder(this)
            .setTitle("Insufficient Wallet Balance")
            .setMessage(
                "You need RM ${String.format("%.2f", required)} to complete this task.\n" +
                "Current Balance: RM ${String.format("%.2f", current)}\n\n" +
                "Please top up your wallet before confirming completion."
            )
            .setPositiveButton("Top Up Wallet") { _, _ ->
                topUpLauncher.launch(Intent(this, WalletTopUpActivity::class.java).apply {
                    putExtra("returnTo", "TASK_TRACKING")
                })
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
                    val existingTaskId = snap.child("taskId").getValue(String::class.java)
                    if (existingTaskId != task.id) {
                        db.child("chats").child(chatId).updateChildren(
                            mapOf(
                                "taskId" to task.id,
                                "taskTitle" to task.title,
                                "taskNumber" to task.taskNumber,
                                "finalPrice" to if (task.agreedPrice > 0) task.agreedPrice else 0.0
                            )
                        ).addOnSuccessListener {
                            openChatActivity(task.id, chatId)
                        }.addOnFailureListener { e ->
                            Log.e(TAG, "Failed to update chat details for tracking", e)
                            openChatActivity(task.id, chatId)
                        }
                    } else {
                        openChatActivity(task.id, chatId)
                    }
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
            val dotId: Int, val titleId: Int, val timeId: Int, val lineId: Int?, val status: String
        )

        val steps = listOf(
            StepIds(R.id.dotPosted, R.id.titlePosted, R.id.timePosted, R.id.linePosted, TaskStatus.OPEN),
            StepIds(R.id.dotAccepted, R.id.titleAccepted, R.id.timeAccepted, R.id.lineAccepted, TaskStatus.ACCEPTED),
            StepIds(R.id.dotOnTheWay, R.id.titleOnTheWay, R.id.timeOnTheWay, R.id.lineOnTheWay, TaskStatus.ON_THE_WAY),
            StepIds(R.id.dotDelivered, R.id.titleDelivered, R.id.timeDelivered, R.id.lineDelivered, TaskStatus.DELIVERED),
            StepIds(R.id.dotCompleted, R.id.titleCompleted, R.id.timeCompleted, null, TaskStatus.COMPLETED)
        )

        val statusOrder = listOf(
            TaskStatus.OPEN, TaskStatus.ACCEPTED, TaskStatus.ON_THE_WAY,
            TaskStatus.DELIVERED, TaskStatus.COMPLETED
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
        // Fetch Poster Avatar
        db.child("users").child(task.posterId).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val user = snapshot.getValue(com.CampusGO.app.model.User::class.java)
                com.CampusGO.app.utils.AvatarHelper.setAvatar(binding.tvPosterAvatar, task.posterName, user?.profilePicture)
            }
            override fun onCancelled(error: DatabaseError) {
                com.CampusGO.app.utils.AvatarHelper.setAvatar(binding.tvPosterAvatar, task.posterName, null)
            }
        })
        binding.tvPosterName.text = task.posterName
        binding.tvPosterRating.text = "★ ${task.posterRating}"

        if (task.runnerId.isNotEmpty()) {
            db.child("users").child(task.runnerId).addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val user = snapshot.getValue(com.CampusGO.app.model.User::class.java)
                    com.CampusGO.app.utils.AvatarHelper.setAvatar(binding.tvRunnerAvatar, task.runnerName, user?.profilePicture)
                }
                override fun onCancelled(error: DatabaseError) {
                    com.CampusGO.app.utils.AvatarHelper.setAvatar(binding.tvRunnerAvatar, task.runnerName, null)
                }
            })
            binding.tvRunnerName.text = task.runnerName
        } else {
            binding.tvRunnerName.text = "Waiting for runner..."
            binding.tvRunnerAvatar.text = "?"
            binding.tvRunnerAvatar.setBackgroundResource(R.drawable.bg_avatar)
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
            "★★★★★  5 - Excellent", "★★★★  4 - Good", "★★★  3 - Average",
            "★★  2 - Poor", "★  1 - Very Poor"
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
        val reasons = arrayOf(
            "Item not delivered", "Runner no-show", "Wrong item delivered",
            "Rude behaviour", "Other"
        )
        var selectedReason = reasons[0]

        AlertDialog.Builder(this)
            .setTitle("Report an Issue")
            .setSingleChoiceItems(reasons, 0) { _, which -> selectedReason = reasons[which] }
            .setPositiveButton("Submit Report") { _, _ ->
                val uid = auth.currentUser?.uid ?: return@setPositiveButton
                val report = mapOf(
                    "taskId" to taskId, "reporterId" to uid,
                    "reason" to selectedReason, "createdAt" to System.currentTimeMillis()
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
        val uid = auth.currentUser?.uid ?: return
        walletListener?.let { db.child("wallets").child(uid).removeEventListener(it) }
    }
}
