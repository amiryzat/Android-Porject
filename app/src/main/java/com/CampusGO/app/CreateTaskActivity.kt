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

import android.content.Intent
import androidx.activity.result.contract.ActivityResultContracts
import com.CampusGO.app.model.TaskPriority
import com.CampusGO.app.service.MapService

class CreateTaskActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCreateTaskBinding
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseDatabase.getInstance().reference
    private val mapService = MapService()

    private var pickupLat: Double? = null
    private var pickupLon: Double? = null
    private var pickupAddress: String? = null

    private var dropoffLat: Double? = null
    private var dropoffLon: Double? = null
    private var dropoffAddress: String? = null

    private val pickupPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            pickupLat = result.data!!.getDoubleExtra("latitude", 0.0)
            pickupLon = result.data!!.getDoubleExtra("longitude", 0.0)
            pickupAddress = result.data!!.getStringExtra("address")
            binding.etPickupArea.setText(pickupAddress)
        }
    }

    private val dropoffPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            dropoffLat = result.data!!.getDoubleExtra("latitude", 0.0)
            dropoffLon = result.data!!.getDoubleExtra("longitude", 0.0)
            dropoffAddress = result.data!!.getStringExtra("address")
            binding.etDropoffArea.setText(dropoffAddress)
        }
    }

    private val topUpLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            binding.btnPost.isEnabled = true
            binding.btnPost.text = "Post to Feed"
            android.widget.Toast.makeText(this, "Wallet topped up! Tap Post to continue.", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

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

        binding.etPickupArea.setOnClickListener {
            val intent = Intent(this, LocationPickerActivity::class.java)
            pickupPickerLauncher.launch(intent)
        }

        binding.etDropoffArea.setOnClickListener {
            val intent = Intent(this, LocationPickerActivity::class.java)
            dropoffPickerLauncher.launch(intent)
        }

        // Pickup presets click listeners
        binding.chipPickupMcD.setOnClickListener { setPickupPreset("McDonald's Cyberjaya Drive-Thru", 2.9224, 101.6508) }
        binding.chipPickupLibrary.setOnClickListener { setPickupPreset("Central Library Hub", 2.9276, 101.6418) }
        binding.chipPickupFSKTM.setOnClickListener { setPickupPreset("FSKTM Faculty Building", 2.9268, 101.6402) }
        binding.chipPickupStudent.setOnClickListener { setPickupPreset("Student Activity Center", 2.9282, 101.6428) }
        binding.chipPickupKK4.setOnClickListener { setPickupPreset("Residential College 4 (KK4)", 2.9295, 101.6438) }
        binding.chipPickupKK8.setOnClickListener { setPickupPreset("Residential College 8 (KK8)", 2.9250, 101.6390) }
        binding.chipPickupKK12.setOnClickListener { setPickupPreset("Residential College 12 (KK12)", 2.9242, 101.6375) }

        // Drop-off presets click listeners
        binding.chipDropoffKK4.setOnClickListener { setDropoffPreset("Residential College 4 (KK4)", 2.9295, 101.6438) }
        binding.chipDropoffKK8.setOnClickListener { setDropoffPreset("Residential College 8 (KK8)", 2.9250, 101.6390) }
        binding.chipDropoffKK12.setOnClickListener { setDropoffPreset("Residential College 12 (KK12)", 2.9242, 101.6375) }
        binding.chipDropoffLibrary.setOnClickListener { setDropoffPreset("Central Library Hub", 2.9276, 101.6418) }
        binding.chipDropoffFSKTM.setOnClickListener { setDropoffPreset("FSKTM Faculty Building", 2.9268, 101.6402) }
        binding.chipDropoffStudent.setOnClickListener { setDropoffPreset("Student Activity Center", 2.9282, 101.6428) }
        binding.chipDropoffMcD.setOnClickListener { setDropoffPreset("McDonald's Cyberjaya Drive-Thru", 2.9224, 101.6508) }

        // Price presets click listeners
        binding.chipPriceRM5.setOnClickListener { setPricePreset("5.00") }
        binding.chipPriceRM8.setOnClickListener { setPricePreset("8.00") }
        binding.chipPriceRM12.setOnClickListener { setPricePreset("12.00") }
        binding.chipPriceRM15.setOnClickListener { setPricePreset("15.00") }
        binding.chipPricePlus2.setOnClickListener { incrementPrice(2.00) }
        binding.chipPricePlus5.setOnClickListener { incrementPrice(5.00) }

        // Category change listener for title template auto-fill
        binding.chipGroupCategory.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isNotEmpty()) {
                val checkedId = checkedIds.first()
                val currentTitle = binding.etTitle.text.toString().trim()
                val isTitleEmpty = currentTitle.isEmpty() || isTitleADefaultTemplate(currentTitle)
                
                if (isTitleEmpty) {
                    val template = when (checkedId) {
                        R.id.chipFood -> "Deliver Food from McD"
                        R.id.chipPrint -> "Print lecture notes at FSKTM"
                        R.id.chipParcel -> "Collect parcel from KK4"
                        R.id.chipErrand -> "Errand: Buy items at grocery"
                        else -> ""
                    }
                    binding.etTitle.setText(template)
                }
            }
        }

        // Initialize toggle states UI
        updateToggleState()

        // Check for pre-filled data (Repost task flow)
        if (intent.hasExtra("repostTitle")) {
            binding.etTitle.setText(intent.getStringExtra("repostTitle"))
            binding.etDescription.setText(intent.getStringExtra("repostDescription"))
            val priceVal = intent.getDoubleExtra("repostPrice", 0.0)
            if (priceVal > 0) {
                binding.etPrice.setText(String.format(java.util.Locale.US, "%.2f", priceVal))
            }
            
            isNegotiable = intent.getBooleanExtra("repostIsNegotiable", false)
            isEmergency = intent.getBooleanExtra("repostIsEmergency", false)
            updateToggleState()

            val cat = intent.getStringExtra("repostCategory")
            if (cat != null) {
                val chipId = when (cat) {
                    TaskCategory.FOOD -> R.id.chipFood
                    TaskCategory.PRINT -> R.id.chipPrint
                    TaskCategory.PARCEL -> R.id.chipParcel
                    TaskCategory.ERRAND -> R.id.chipErrand
                    else -> R.id.chipFood
                }
                binding.chipGroupCategory.check(chipId)
            }

            if (intent.hasExtra("repostPickupLat")) {
                pickupLat = intent.getDoubleExtra("repostPickupLat", 0.0)
                pickupLon = intent.getDoubleExtra("repostPickupLon", 0.0)
                val fullPickup = intent.getStringExtra("repostPickupAddress") ?: ""
                val addressPart = fullPickup.substringBefore(" (")
                val detailsPart = if (fullPickup.contains(" (")) fullPickup.substringAfter(" (").removeSuffix(")") else ""
                pickupAddress = addressPart
                binding.etPickupArea.setText(addressPart)
                binding.etPickup.setText(detailsPart)
            }

            if (intent.hasExtra("repostDropoffLat")) {
                dropoffLat = intent.getDoubleExtra("repostDropoffLat", 0.0)
                dropoffLon = intent.getDoubleExtra("repostDropoffLon", 0.0)
                val fullDropoff = intent.getStringExtra("repostDropoffAddress") ?: ""
                val addressPart = fullDropoff.substringBefore(" (")
                val detailsPart = if (fullDropoff.contains(" (")) fullDropoff.substringAfter(" (").removeSuffix(")") else ""
                dropoffAddress = addressPart
                binding.etDropoffArea.setText(addressPart)
                binding.etDropoff.setText(detailsPart)
            }
        }
    }

    private fun updateToggleState() {
        if (isNegotiable) {
            binding.btnNegotiable.text = "NEGOTIABLE ✓"
            binding.btnNegotiable.setBackgroundColor(getColor(R.color.campusgo_primary))
            binding.btnNegotiable.setTextColor(getColor(android.R.color.white))
            binding.btnNegotiable.strokeWidth = 0
        } else {
            binding.btnNegotiable.text = "NEGOTIABLE"
            binding.btnNegotiable.setBackgroundColor(getColor(android.R.color.transparent))
            binding.btnNegotiable.setTextColor(getColor(R.color.campusgo_primary))
            binding.btnNegotiable.strokeWidth = 2
        }
        if (isEmergency) {
            binding.btnEmergency.text = "!! EMERGENCY ✓"
            binding.btnEmergency.setBackgroundColor(getColor(R.color.campusgo_error))
            binding.btnEmergency.setTextColor(getColor(android.R.color.white))
            binding.btnEmergency.strokeWidth = 0
        } else {
            binding.btnEmergency.text = "!! EMERGENCY"
            binding.btnEmergency.setBackgroundColor(getColor(android.R.color.transparent))
            binding.btnEmergency.setTextColor(getColor(R.color.campusgo_error))
            binding.btnEmergency.strokeWidth = 2
        }
    }

    private fun setPickupPreset(address: String, lat: Double, lon: Double) {
        pickupLat = lat
        pickupLon = lon
        pickupAddress = address
        binding.etPickupArea.setText(address)
        binding.layoutPickupArea.error = null
        Toast.makeText(this, "Pickup set: $address", Toast.LENGTH_SHORT).show()
    }

    private fun setDropoffPreset(address: String, lat: Double, lon: Double) {
        dropoffLat = lat
        dropoffLon = lon
        dropoffAddress = address
        binding.etDropoffArea.setText(address)
        binding.layoutDropoffArea.error = null
        Toast.makeText(this, "Drop-off set: $address", Toast.LENGTH_SHORT).show()
    }

    private fun setPricePreset(valStr: String) {
        binding.etPrice.setText(valStr)
    }

    private fun incrementPrice(amount: Double) {
        val currentPrice = binding.etPrice.text.toString().toDoubleOrNull() ?: 0.0
        val newPrice = currentPrice + amount
        binding.etPrice.setText(String.format("%.2f", newPrice))
    }

    private fun isTitleADefaultTemplate(title: String): Boolean {
        val templates = listOf(
            "Deliver Food from McD",
            "Print lecture notes at FSKTM",
            "Collect parcel from KK4",
            "Errand: Buy items at grocery"
        )
        return templates.contains(title)
    }

    private fun validateAndPost() {
        val title = binding.etTitle.text.toString().trim()
        val pickupDetails = binding.etPickup.text.toString().trim()
        val dropoffDetails = binding.etDropoff.text.toString().trim()
        val description = binding.etDescription.text.toString().trim()
        val priceStr = binding.etPrice.text.toString().trim()

        if (title.isEmpty()) { binding.layoutTitle.error = "Task title is required"; return }
        binding.layoutTitle.error = null

        val pLat = pickupLat
        val pLon = pickupLon
        val pAddr = pickupAddress
        if (pLat == null || pLon == null || pAddr.isNullOrEmpty()) {
            Toast.makeText(this, "Please select a pickup location on the map", Toast.LENGTH_SHORT).show()
            return
        }

        if (pickupDetails.isEmpty()) { binding.layoutPickup.error = "Pickup details are required"; return }
        binding.layoutPickup.error = null

        val dLat = dropoffLat
        val dLon = dropoffLon
        val dAddr = dropoffAddress
        if (dLat == null || dLon == null || dAddr.isNullOrEmpty()) {
            Toast.makeText(this, "Please select a drop-off location on the map", Toast.LENGTH_SHORT).show()
            return
        }

        if (dropoffDetails.isEmpty()) { binding.layoutDropoff.error = "Drop-off details are required"; return }
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
        
        binding.btnPost.isEnabled = false
        binding.btnPost.text = "Checking wallet..."

        val fullPickup = "$pAddr ($pickupDetails)"
        val fullDropoff = "$dAddr ($dropoffDetails)"

        val uid = auth.currentUser?.uid
        if (uid == null) {
            Toast.makeText(this, "You must be signed in to post a task.", Toast.LENGTH_SHORT).show()
            binding.btnPost.isEnabled = true
            binding.btnPost.text = "Post to Feed"
            return
        }

        db.child("wallets").child(uid).get()
            .addOnSuccessListener { snapshot ->
                val wallet = snapshot.getValue(com.CampusGO.app.model.Wallet::class.java)
                val balance = wallet?.balance ?: 0.0
                if (balance < price) {
                    binding.btnPost.isEnabled = true
                    binding.btnPost.text = "Post to Feed"
                    showInsufficientBalanceDialog(price, balance)
                } else {
                    binding.btnPost.text = "Posting task..."
                    postTask(
                        title, category, fullPickup, fullDropoff, description, price,
                        pLat, pLon, dLat, dLon
                    )
                }
            }
            .addOnFailureListener {
                binding.btnPost.isEnabled = true
                binding.btnPost.text = "Post to Feed"
                showInsufficientBalanceDialog(price, 0.0)
            }
    }

    private fun showInsufficientBalanceDialog(required: Double, current: Double) {
        android.app.AlertDialog.Builder(this)
            .setTitle("Insufficient Wallet Balance")
            .setMessage(
                "You need RM ${String.format(java.util.Locale.US, "%.2f", required)} to post this task.\n" +
                "Current Balance: RM ${String.format(java.util.Locale.US, "%.2f", current)}\n\n" +
                "Please top up your wallet first."
            )
            .setPositiveButton("Top Up Wallet") { _, _ ->
                topUpLauncher.launch(Intent(this, WalletTopUpActivity::class.java).apply {
                    putExtra("returnTo", "CREATE_TASK")
                })
            }
            .setNegativeButton("Cancel", null)
            .show()
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

    private fun postTask(
        title: String, category: String, pickup: String, dropoff: String,
        description: String, price: Double,
        pLat: Double, pLon: Double, dLat: Double, dLon: Double
    ) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            Toast.makeText(this, "You must be signed in to post a task.", Toast.LENGTH_SHORT).show()
            binding.btnPost.isEnabled = true
            binding.btnPost.text = "Post to Feed"
            return
        }
        val userName = auth.currentUser?.displayName?.takeIf { it.isNotBlank() } ?: "Anonymous"

        val taskRef = db.child("tasks").push()
        val taskId = taskRef.key
        if (taskId == null) {
            Toast.makeText(this, "Could not generate task ID. Check your connection.", Toast.LENGTH_SHORT).show()
            binding.btnPost.isEnabled = true
            binding.btnPost.text = "Post to Feed"
            return
        }

        binding.btnPost.isEnabled = false
        binding.btnPost.text = "Posting..."

        val taskNumber = "CG-${taskId.takeLast(4).uppercase()}"
        val priority = if (isEmergency) TaskPriority.HIGH else TaskPriority.MEDIUM

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
            createdAt = System.currentTimeMillis(),
            pickupLatitude = pLat,
            pickupLongitude = pLon,
            dropoffLatitude = dLat,
            dropoffLongitude = dLon,
            priority = priority
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
