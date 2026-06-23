package com.CampusGO.app

import android.content.Intent
import android.graphics.Paint
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.CampusGO.app.databinding.ActivityConfirmAcceptBinding
import com.CampusGO.app.model.Task
import com.CampusGO.app.model.TaskStatus
import com.CampusGO.app.util.ChatIdHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlin.math.abs

class ConfirmAcceptActivity : AppCompatActivity() {

    private lateinit var binding: ActivityConfirmAcceptBinding

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseDatabase.getInstance().reference

    companion object {
        private const val TAG = "ConfirmAcceptActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityConfirmAcceptBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val taskId = intent.getStringExtra("taskId") ?: run {
            finish()
            return
        }

        val agreedPrice = intent.getDoubleExtra("agreedPrice", 0.0)

        // CHANGE:
        // This is optional.
        // If user comes from chat negotiation, ChatActivity sends chatId.
        val chatIdFromIntent = intent.getStringExtra("chatId")?.takeIf { it.isNotBlank() }

        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnBackToNeg.setOnClickListener {
            finish()
        }

        db.child("tasks")
            .child(taskId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val task = snapshot.getValue(Task::class.java) ?: run {
                        Toast.makeText(
                            this@ConfirmAcceptActivity,
                            "Task not found",
                            Toast.LENGTH_SHORT
                        ).show()
                        finish()
                        return
                    }

                    bindTask(task, agreedPrice, chatIdFromIntent)
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "Failed to load task: ${error.message}", error.toException())

                    Toast.makeText(
                        this@ConfirmAcceptActivity,
                        "Failed to load task: ${error.message}",
                        Toast.LENGTH_SHORT
                    ).show()

                    finish()
                }
            })
    }

    private fun bindTask(
        task: Task,
        agreedPrice: Double,
        chatIdFromIntent: String?
    ) {
        val uid = auth.currentUser?.uid ?: run {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // CHANGE:
        // Task poster cannot accept their own task.
        if (task.posterId == uid) {
            Toast.makeText(this, "You cannot accept your own task", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // CHANGE:
        // Only OPEN task can be accepted.
        if (task.status != TaskStatus.OPEN) {
            Toast.makeText(this, "This task is no longer available", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val finalPrice = if (agreedPrice > 0) agreedPrice else task.price

        binding.tvTaskTitle.text = task.title
        binding.tvPostedBy.text = "${task.posterName} ★ ${task.posterRating}"
        binding.tvOriginalPrice.text = "RM ${String.format("%.2f", task.price)}"
        binding.tvFinalPrice.text = "RM ${String.format("%.2f", finalPrice)}"

        if (abs(finalPrice - task.price) > 0.001) {
            binding.tvOriginalPrice.paintFlags =
                binding.tvOriginalPrice.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG

            binding.tvPriceSource.text = "↑ via chat negotiation"
        } else {
            binding.tvPriceSource.text = "listed price"
        }

        binding.btnConfirm.setOnClickListener {
            acceptTask(task, finalPrice, chatIdFromIntent)
        }
    }

    private fun acceptTask(
        task: Task,
        finalPrice: Double,
        chatIdFromIntent: String?
    ) {
        val currentUser = auth.currentUser ?: run {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show()
            return
        }

        val uid = currentUser.uid
        val runnerName = currentUser.displayName ?: "Unknown"

        setConfirmLoading(true)

        // CHANGE:
        // Recheck task from Firebase before accepting.
        // This prevents accepting an old task that was already accepted by someone else.
        db.child("tasks")
            .child(task.id)
            .get()
            .addOnSuccessListener { snap ->
                val latestTask = snap.getValue(Task::class.java) ?: run {
                    failConfirm("Task not found")
                    return@addOnSuccessListener
                }

                // CHANGE:
                // Final backend check: poster cannot accept own task.
                if (latestTask.posterId == uid) {
                    failConfirm("You cannot accept your own task")
                    return@addOnSuccessListener
                }

                // CHANGE:
                // Final backend check: task must still be OPEN.
                if (latestTask.status != TaskStatus.OPEN) {
                    failConfirm("This task is no longer available")
                    return@addOnSuccessListener
                }

                if (finalPrice <= 0.0) {
                    failConfirm("Invalid price")
                    return@addOnSuccessListener
                }

                // Check poster's wallet balance
                db.child("wallets").child(latestTask.posterId).get()
                    .addOnSuccessListener { walletSnap ->
                        val wallet = walletSnap.getValue(com.CampusGO.app.model.Wallet::class.java)
                        val balance = wallet?.balance ?: 0.0
                        if (balance < finalPrice) {
                            failConfirm("Poster's wallet balance is insufficient")
                            showInsufficientPosterBalanceDialog(latestTask.posterName, finalPrice)
                        } else {
                            continueAcceptFlow(latestTask, finalPrice, chatIdFromIntent, uid, runnerName)
                        }
                    }
                    .addOnFailureListener { e ->
                        failConfirm("Failed to verify poster wallet: ${e.message}")
                    }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to recheck task", e)
                failConfirm("Failed to accept task: ${e.message}")
            }
    }

    private fun continueAcceptFlow(
        latestTask: Task,
        finalPrice: Double,
        chatIdFromIntent: String?,
        uid: String,
        runnerName: String
    ) {
        val isNegotiatedPrice = abs(finalPrice - latestTask.price) > 0.001

        if (isNegotiatedPrice) {
            // CHANGE:
            // If price is negotiated, it must come from a real chat.
            if (chatIdFromIntent.isNullOrBlank()) {
                failConfirm("Negotiated chat record is missing")
                return
            }

            verifyNegotiatedPrice(
                task = latestTask,
                finalPrice = finalPrice,
                chatId = chatIdFromIntent,
                runnerId = uid,
                runnerName = runnerName
            )
        } else {
            performAccept(
                task = latestTask,
                finalPrice = finalPrice,
                chatIdFromIntent = chatIdFromIntent,
                runnerId = uid,
                runnerName = runnerName
            )
        }
    }

    private fun showInsufficientPosterBalanceDialog(posterName: String, price: Double) {
        android.app.AlertDialog.Builder(this)
            .setTitle("Poster Balance Insufficient")
            .setMessage(
                "The task poster ($posterName) does not currently have enough balance in their CampusGO Wallet to cover the agreed price of RM ${String.format(java.util.Locale.US, "%.2f", price)}.\n\n" +
                "To protect your payment, please notify the poster via chat to top up their wallet before you accept this task."
            )
            .setPositiveButton("OK", null)
            .show()
    }

    private fun verifyNegotiatedPrice(
        task: Task,
        finalPrice: Double,
        chatId: String,
        runnerId: String,
        runnerName: String
    ) {
        db.child("chats")
            .child(chatId)
            .get()
            .addOnSuccessListener { snap ->
                if (!snap.exists()) {
                    failConfirm("Chat negotiation record not found")
                    return@addOnSuccessListener
                }

                val chatPosterId = snap.child("posterId").getValue(String::class.java) ?: ""
                val chatRunnerId = snap.child("runnerId").getValue(String::class.java) ?: ""
                val chatFinalPrice = snap.child("finalPrice").getValue(Double::class.java) ?: 0.0

                // CHANGE:
                // Make sure this negotiated price belongs to this poster and runner.
                if (chatPosterId != task.posterId || chatRunnerId != runnerId) {
                    failConfirm("Invalid negotiation chat")
                    return@addOnSuccessListener
                }

                // CHANGE:
                // Make sure agreed price is the same as Firebase chat finalPrice.
                if (chatFinalPrice <= 0.0 || abs(chatFinalPrice - finalPrice) > 0.001) {
                    failConfirm("Negotiated price does not match")
                    return@addOnSuccessListener
                }

                performAccept(
                    task = task,
                    finalPrice = chatFinalPrice,
                    chatIdFromIntent = chatId,
                    runnerId = runnerId,
                    runnerName = runnerName
                )
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to verify negotiated price", e)
                failConfirm("Failed to verify price: ${e.message}")
            }
    }

    private fun performAccept(
        task: Task,
        finalPrice: Double,
        chatIdFromIntent: String?,
        runnerId: String,
        runnerName: String
    ) {
        val updates = mapOf(
            "status" to TaskStatus.ACCEPTED,
            "runnerId" to runnerId,
            "runnerName" to runnerName,
            "agreedPrice" to finalPrice
        )

        db.child("tasks")
            .child(task.id)
            .updateChildren(updates)
            .addOnSuccessListener {
                updateAcceptedTaskCount(runnerId)

                // Save to accepted_tasks collection
                val acceptedTask = mapOf(
                    "taskId" to task.id,
                    "runnerId" to runnerId,
                    "runnerName" to runnerName,
                    "acceptedAt" to System.currentTimeMillis(),
                    "price" to finalPrice
                )
                db.child("accepted_tasks").child(task.id).setValue(acceptedTask)

                // Make sure the chat exists after task is accepted.
                // This helps TaskTrackingActivity open the same chat later.
                val chatId = chatIdFromIntent ?: ChatIdHelper.buildChatId(
                    taskId = task.id,
                    userA = task.posterId,
                    userB = runnerId
                )

                ensureAcceptedChatExists(
                    task = task,
                    chatId = chatId,
                    runnerId = runnerId,
                    runnerName = runnerName,
                    finalPrice = finalPrice
                )
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to accept task", e)
                failConfirm("Error: ${e.message}")
            }
    }

    private fun ensureAcceptedChatExists(
        task: Task,
        chatId: String,
        runnerId: String,
        runnerName: String,
        finalPrice: Double
    ) {
        val chatUpdate = mapOf(
            "id" to chatId,
            "taskId" to task.id,
            "taskTitle" to task.title,
            "taskNumber" to task.taskNumber,

            "posterId" to task.posterId,
            "posterName" to task.posterName,

            "runnerId" to runnerId,
            "runnerName" to runnerName,

            "participants" to mapOf(
                task.posterId to true,
                runnerId to true
            ),

            "finalPrice" to finalPrice,
            "lastMessage" to "Task accepted",
            "lastMessageTime" to System.currentTimeMillis()
        )

        db.child("chats")
            .child(chatId)
            .updateChildren(chatUpdate)
            .addOnSuccessListener {
                Toast.makeText(this, "Task accepted! Good luck, runner!", Toast.LENGTH_LONG).show()

                NotificationHelper.sendNotificationToUser(
                    receiverUserId = task.posterId,
                    title = "Task Accepted! 🚀",
                    body = "$runnerName accepted your task: ${task.title}",
                    type = "TASK_STATUS",
                    taskId = task.id,
                    chatId = chatId
                )

                startActivity(Intent(this, TaskTrackingActivity::class.java).apply {
                    putExtra("taskId", task.id)
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                })

                finish()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Task accepted but failed to update chat", e)

                Toast.makeText(
                    this,
                    "Task accepted, but chat update failed: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()

                startActivity(Intent(this, TaskTrackingActivity::class.java).apply {
                    putExtra("taskId", task.id)
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                })

                finish()
            }
    }

    private fun updateAcceptedTaskCount(runnerId: String) {
        db.child("userStats")
            .child(runnerId)
            .child("acceptedTasks")
            .get()
            .addOnSuccessListener { snap ->
                val count = snap.getValue(Int::class.java) ?: 0

                db.child("userStats")
                    .child(runnerId)
                    .child("acceptedTasks")
                    .setValue(count + 1)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to update accepted task count", e)
            }
    }

    private fun setConfirmLoading(isLoading: Boolean) {
        binding.btnConfirm.isEnabled = !isLoading
        binding.btnConfirm.text = if (isLoading) {
            "Confirming..."
        } else {
            "Confirm & Accept"
        }
    }

    private fun failConfirm(message: String) {
        setConfirmLoading(false)
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}