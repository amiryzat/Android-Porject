package com.CampusGO.app

import android.content.Intent
import android.graphics.Paint
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.CampusGO.app.databinding.ActivityConfirmAcceptBinding
import com.CampusGO.app.model.Task
import com.CampusGO.app.model.TaskStatus
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class ConfirmAcceptActivity : AppCompatActivity() {

    private lateinit var binding: ActivityConfirmAcceptBinding
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseDatabase.getInstance().reference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityConfirmAcceptBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val taskId = intent.getStringExtra("taskId") ?: run { finish(); return }
        val agreedPrice = intent.getDoubleExtra("agreedPrice", 0.0)

        binding.btnBack.setOnClickListener { finish() }
        binding.btnBackToNeg.setOnClickListener { finish() }

        db.child("tasks").child(taskId).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val task = snapshot.getValue(Task::class.java) ?: return
                bindTask(task, agreedPrice)
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun bindTask(task: Task, agreedPrice: Double) {
        val finalPrice = if (agreedPrice > 0) agreedPrice else task.price

        binding.tvTaskTitle.text = task.title
        binding.tvPostedBy.text = "${task.posterName} ★ ${task.posterRating}"
        binding.tvOriginalPrice.text = "RM ${String.format("%.2f", task.price)}"
        binding.tvFinalPrice.text = "RM ${String.format("%.2f", finalPrice)}"

        if (finalPrice != task.price) {
            binding.tvOriginalPrice.paintFlags = binding.tvOriginalPrice.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            binding.tvPriceSource.text = "↑ via chat negotiation"
        } else {
            binding.tvPriceSource.text = "listed price"
        }

        binding.btnConfirm.setOnClickListener {
            acceptTask(task, finalPrice)
        }
    }

    private fun acceptTask(task: Task, finalPrice: Double) {
        val uid = auth.currentUser?.uid ?: return
        val runnerName = auth.currentUser?.displayName ?: "Unknown"

        binding.btnConfirm.isEnabled = false
        binding.btnConfirm.text = "Confirming..."

        val updates = mapOf(
            "status" to TaskStatus.ACCEPTED,
            "runnerId" to uid,
            "runnerName" to runnerName,
            "agreedPrice" to finalPrice
        )

        db.child("tasks").child(task.id).updateChildren(updates)
            .addOnSuccessListener {
                db.child("userStats").child(uid).child("acceptedTasks").get()
                    .addOnSuccessListener { snap ->
                        val count = snap.getValue(Int::class.java) ?: 0
                        db.child("userStats").child(uid).child("acceptedTasks").setValue(count + 1)
                    }

                Toast.makeText(this, "Task accepted! Good luck, runner!", Toast.LENGTH_LONG).show()
                startActivity(Intent(this, TaskTrackingActivity::class.java).apply {
                    putExtra("taskId", task.id)
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                })
                finish()
            }
            .addOnFailureListener { e ->
                binding.btnConfirm.isEnabled = true
                binding.btnConfirm.text = "Confirm & Accept"
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
