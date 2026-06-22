package com.CampusGO.app

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.CampusGO.app.databinding.ActivityCreateTaskBinding
import com.CampusGO.app.model.Task
import com.CampusGO.app.model.TaskCategory
import com.CampusGO.app.model.TaskStatus
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class CreateTaskActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCreateTaskBinding
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseDatabase.getInstance().reference

    private var isNegotiable = false
    private var isEmergency = false

    private val bannedKeywords = setOf(
        "kill", "murder", "harm", "attack", "bomb", "explosive",
        "cocaine", "heroin", "meth", "fentanyl",
        "porn", "pornography", "nude", "naked", "sexual",
        "weapon", "firearm", "pistol", "rifle"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCreateTaskBinding.inflate(layoutInflater)
        setContentView(binding.root)

        InsetsHelper.applyTopInsetPadding(binding.toolbarContainer)
        binding.btnBack.setOnClickListener { finish() }

        binding.btnNegotiable.setOnClickListener {
            isNegotiable = !isNegotiable
            updateToggleState()
        }

        binding.btnEmergency.setOnClickListener {
            isEmergency = !isEmergency
            updateToggleState()
        }

        binding.btnPost.setOnClickListener { validateAndPost() }
    }

    private fun updateToggleState() {
        if (isNegotiable) {
            binding.btnNegotiable.text = "NEGOTIABLE ✓"
            binding.btnNegotiable.setBackgroundColor(getColor(R.color.campusgo_text_primary))
            binding.btnNegotiable.setTextColor(getColor(android.R.color.white))
        } else {
            binding.btnNegotiable.text = "NEGOTIABLE"
            binding.btnNegotiable.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            binding.btnNegotiable.setTextColor(getColor(R.color.campusgo_text_primary))
        }
        if (isEmergency) {
            binding.btnEmergency.text = "!! EMERGENCY ✓"
            binding.btnEmergency.setBackgroundColor(getColor(R.color.campusgo_text_primary))
            binding.btnEmergency.setTextColor(getColor(android.R.color.white))
        } else {
            binding.btnEmergency.text = "!! EMERGENCY"
            binding.btnEmergency.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            binding.btnEmergency.setTextColor(getColor(R.color.campusgo_text_primary))
        }
    }

    private fun validateAndPost() {
        val title = binding.etTitle.text.toString().trim()
        val pickup = binding.etPickup.text.toString().trim()
        val dropoff = binding.etDropoff.text.toString().trim()
        val description = binding.etDescription.text.toString().trim()
        val priceStr = binding.etPrice.text.toString().trim()

        if (title.isEmpty()) { binding.layoutTitle.error = "Task title is required"; return }
        binding.layoutTitle.error = null

        if (pickup.isEmpty()) { binding.layoutPickup.error = "Pickup location is required"; return }
        binding.layoutPickup.error = null

        if (dropoff.isEmpty()) { binding.layoutDropoff.error = "Drop-off location is required"; return }
        binding.layoutDropoff.error = null

        val price = priceStr.toDoubleOrNull()
        if (price == null || price <= 0) {
            Toast.makeText(this, "Please enter a valid price", Toast.LENGTH_SHORT).show()
            return
        }

        val allText = "$title $description".lowercase()
        if (bannedKeywords.any { allText.contains(it) }) {
            showModerationError()
            return
        }

        val category = getSelectedCategory()
        postTask(title, category, pickup, dropoff, description, price)
    }

    private fun showModerationError() {
        android.app.AlertDialog.Builder(this)
            .setTitle("Task Not Approved")
            .setMessage("Your task was not approved by our system. Please ensure your request follows campus community guidelines.\n\nTasks involving harmful, sexual, or illegal content are not permitted.")
            .setPositiveButton("Understood") { d, _ -> d.dismiss() }
            .show()
    }

    private fun getSelectedCategory(): String {
        return when (binding.chipGroupCategory.checkedChipId) {
            R.id.chipFood -> TaskCategory.FOOD
            R.id.chipPrint -> TaskCategory.PRINT
            R.id.chipParcel -> TaskCategory.PARCEL
            R.id.chipErrand -> TaskCategory.ERRAND
            else -> TaskCategory.OTHER
        }
    }

    private fun postTask(title: String, category: String, pickup: String, dropoff: String, description: String, price: Double) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            Toast.makeText(this, "You must be signed in to post a task.", Toast.LENGTH_SHORT).show()
            return
        }
        val userName = auth.currentUser?.displayName?.takeIf { it.isNotBlank() } ?: "Anonymous"

        val taskRef = db.child("tasks").push()
        val taskId = taskRef.key
        if (taskId == null) {
            Toast.makeText(this, "Could not generate task ID. Check your connection.", Toast.LENGTH_SHORT).show()
            return
        }

        binding.btnPost.isEnabled = false
        binding.btnPost.text = "Posting..."

        val taskNumber = "CG-${taskId.takeLast(4).uppercase()}"
        val task = Task(
            id = taskId,
            taskNumber = taskNumber,
            title = title,
            category = category,
            pickup = pickup,
            dropoff = dropoff,
            description = description,
            price = price,
            isNegotiable = isNegotiable,
            isEmergency = isEmergency,
            status = TaskStatus.OPEN,
            posterId = uid,
            posterName = userName,
            posterRating = 5.0,
            posterReviews = 0,
            createdAt = System.currentTimeMillis()
        )

        taskRef.setValue(task)
            .addOnSuccessListener {
                // Increment posted-task counter in background; don't block finish()
                db.child("userStats").child(uid).child("postedTasks").get()
                    .addOnSuccessListener { s ->
                        val count = s.getValue(Int::class.java) ?: 0
                        db.child("userStats").child(uid).child("postedTasks").setValue(count + 1)
                    }
                if (!isFinishing && !isDestroyed) {
                    Toast.makeText(this, "Task posted! Runners will see it now.", Toast.LENGTH_LONG).show()
                }
                finish()
            }
            .addOnFailureListener { e ->
                if (!isFinishing && !isDestroyed) {
                    binding.btnPost.isEnabled = true
                    binding.btnPost.text = "Post to Feed"
                    Toast.makeText(this, "Failed to post: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
    }
}
