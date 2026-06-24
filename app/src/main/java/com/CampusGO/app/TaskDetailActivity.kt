package com.CampusGO.app

import android.content.Intent
import android.os.Bundle
import android.util.Log // CHANGE: Added for Logcat checking
import android.view.View
import android.widget.PopupMenu
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.CampusGO.app.databinding.ActivityTaskDetailBinding
import com.CampusGO.app.model.Task
import com.CampusGO.app.model.TaskStatus
import com.CampusGO.app.util.ChatIdHelper // CHANGE: Added ChatIdHelper import
import com.CampusGO.app.util.applyStatusBarInsets
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Marker
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import android.content.Context

class TaskDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTaskDetailBinding
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseDatabase.getInstance().reference

    private var currentTask: Task? = null
    private var taskListener: ValueEventListener? = null
    private var googleMap: GoogleMap? = null
    private var pickupMarker: Marker? = null
    private var dropoffMarker: Marker? = null

    companion object {
        private const val TAG = "TaskDetailActivity" // CHANGE: Added TAG for Logcat
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTaskDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.llHeader.applyStatusBarInsets()

        binding.mapView.onCreate(savedInstanceState)
        binding.mapView.setOnTouchListener { _, event ->
            binding.mapView.parent.requestDisallowInterceptTouchEvent(true)
            false
        }
        binding.mapView.getMapAsync { map ->
            googleMap = map
            currentTask?.let { updateMap(it) }
        }

        val taskId = intent.getStringExtra("taskId") ?: run {
            finish()
            return
        }

        binding.btnBack.setOnClickListener {
            finishWithTransition()
        }

        // Handle system back button with smooth transition
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finishWithTransition()
            }
        })

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

        taskListener = db.child("tasks")
            .child(taskId)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val task = snapshot.getValue(Task::class.java) ?: return
                    currentTask = task
                    bindTask(task)
                }

                override fun onCancelled(error: DatabaseError) {
                    // CHANGE: Added error message instead of leaving empty
                    Log.e(TAG, "Failed to load task: ${error.message}", error.toException())
                    Toast.makeText(
                        this@TaskDetailActivity,
                        "Failed to load task: ${error.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })
    }

    private fun bindTask(task: Task) {
        val uid = auth.currentUser?.uid ?: return
        val isMyTask = task.posterId == uid
        val isRunner = task.runnerId == uid

        googleMap?.let { updateMap(task) }

        binding.tvTaskNumber.text = "Task · ${task.taskNumber}"
        binding.tvTitle.text = task.title
        binding.tvPickup.text = formatLocationDisplay(task.pickup)
        binding.tvDropoff.text = formatLocationDisplay(task.dropoff)

        binding.llPickupRow.setOnClickListener {
            if (task.pickupLatitude != 0.0 && task.pickupLongitude != 0.0) {
                googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(
                    LatLng(task.pickupLatitude, task.pickupLongitude), 17.5f
                ))
                pickupMarker?.showInfoWindow()
            }
        }

        binding.llDropoffRow.setOnClickListener {
            if (task.dropoffLatitude != 0.0 && task.dropoffLongitude != 0.0) {
                googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(
                    LatLng(task.dropoffLatitude, task.dropoffLongitude), 17.5f
                ))
                dropoffMarker?.showInfoWindow()
            }
        }
        binding.tvDescription.text = task.description.ifEmpty { "No description provided." }
        binding.tvPrice.text = "RM ${String.format("%.2f", task.price)}"
        binding.tvCategoryTag.text = task.category
        binding.tvEmergencyTag.visibility = if (task.isEmergency) View.VISIBLE else View.GONE
        binding.tvNegotiable.visibility = if (task.isNegotiable) View.VISIBLE else View.GONE

        db.child("users").child(task.posterId).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val user = snapshot.getValue(com.CampusGO.app.model.User::class.java)
                val pic = user?.profilePicture
                com.CampusGO.app.utils.AvatarHelper.setAvatar(binding.tvPosterAvatar, task.posterName, pic)
            }
            override fun onCancelled(error: DatabaseError) {
                com.CampusGO.app.utils.AvatarHelper.setAvatar(binding.tvPosterAvatar, task.posterName, null)
            }
        })
        binding.tvPosterName.text = task.posterName
        binding.tvPosterStats.text = "★ ${task.posterRating} (${task.posterReviews} reviews) · posted"

        binding.btnAccept.text = "Accept @ RM ${String.format("%.2f", task.price)}"

        when {
            isMyTask -> {
                binding.llRunnerActions.visibility = View.GONE
                binding.llPosterActions.visibility =
                    if (task.status != TaskStatus.OPEN) View.VISIBLE else View.GONE

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
                    // CHANGE:
                    // OLD CODE:
                    // val chatId = "${task.id}_${uid}"
                    //
                    // PROBLEM:
                    // Runner gets taskId_runnerId.
                    // Poster gets taskId_posterId.
                    // That creates different chat rooms.
                    //
                    // NEW FIX:
                    // Use taskId + posterId + runnerId.
                    // ChatIdHelper sorts user IDs, so both users get the same chatId.
                    val chatId = ChatIdHelper.buildChatId(
                        taskId = task.id,
                        userA = task.posterId,
                        userB = uid
                    )

                    // CHANGE: Added Logcat checking
                    Log.d(TAG, "Opening negotiate chat")
                    Log.d(TAG, "Current user/runnerId: $uid")
                    Log.d(TAG, "Poster ID: ${task.posterId}")
                    Log.d(TAG, "Generated chatId: $chatId")

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

        db.child("chats")
            .child(chatId)
            .get()
            .addOnSuccessListener { snap ->
                if (!snap.exists()) {
                    // CHANGE:
                    // Chat is now saved using the fixed chatId.
                    // Also added participants, useful later for Firebase rules.
                    val chat = mapOf(
                        "id" to chatId,
                        "taskId" to task.id,
                        "taskTitle" to task.title,
                        "taskNumber" to task.taskNumber,

                        "posterId" to task.posterId,
                        "posterName" to task.posterName,

                        "runnerId" to uid,
                        "runnerName" to runnerName,

                        // CHANGE: Added participants map
                        "participants" to mapOf(
                            task.posterId to true,
                            uid to true
                        ),

                        "lastMessage" to "",
                        "lastMessageTime" to 0L,
                        "finalPrice" to 0.0
                    )

                    db.child("chats")
                        .child(chatId)
                        .setValue(chat)
                        .addOnSuccessListener {
                            Log.d(TAG, "Chat created successfully: $chatId")
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "Failed to create chat", e)
                            Toast.makeText(
                                this,
                                "Failed to create chat: ${e.message}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                } else {
                    Log.d(TAG, "Chat already exists: $chatId")
                    val existingTaskId = snap.child("taskId").getValue(String::class.java)
                    if (existingTaskId != task.id) {
                        db.child("chats").child(chatId).updateChildren(
                            mapOf(
                                "taskId" to task.id,
                                "taskTitle" to task.title,
                                "taskNumber" to task.taskNumber,
                                "finalPrice" to 0.0,
                                "lastMessage" to "Negotiating task ${task.taskNumber}",
                                "lastMessageTime" to System.currentTimeMillis()
                            )
                        ).addOnFailureListener { e ->
                            Log.e(TAG, "Failed to update chat details for new task", e)
                        }
                    }
                }
            }
            .addOnFailureListener { e ->
                // CHANGE: Added failure handling
                Log.e(TAG, "Failed to check chat", e)
                Toast.makeText(
                    this,
                    "Failed to open chat: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    private fun confirmCancelTask(task: Task) {
        android.app.AlertDialog.Builder(this)
            .setTitle("Cancel Task")
            .setMessage("Are you sure you want to cancel \"${task.title}\"? It will be removed from the feed.")
            .setPositiveButton("Cancel Task") { _, _ ->
                db.child("tasks")
                    .child(task.id)
                    .child("status")
                    .setValue(TaskStatus.CANCELLED)
                    .addOnSuccessListener {
                        Toast.makeText(this, "Task cancelled.", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(
                            this,
                            "Failed to cancel task: ${e.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
            }
            .setNegativeButton("Keep It") { d, _ ->
                d.dismiss()
            }
            .show()
    }

    private fun finishWithTransition() {
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
    }

    /**
     * Rearranges address so the human-readable name appears first.
     *
     * Input:  "5694+72, 35400 Tapah Road, Perak, Malaysia (Taman perwira)"
     * Output: "Taman perwira — 35400 Tapah Road, Perak, Malaysia"
     *
     * Input:  "56PC+27, Kampung Baharu Batu Empat, 35400 Tapah Road, Perak, Malaysia (Speedmart terdekat)"
     * Output: "Speedmart terdekat — Kampung Baharu Batu Empat, 35400 Tapah Road, Perak, Malaysia"
     */
    private fun formatLocationDisplay(address: String): String {
        if (address.isBlank()) return address

        // 1. Extract the user-given name from parentheses (e.g. "Taman perwira")
        val parenRegex = """\(([^)]+)\)""".toRegex()
        val match = parenRegex.find(address)
        val locationName = match?.groupValues?.get(1)?.trim()

        // 2. Remove parenthetical from the remaining address
        var remaining = if (match != null) {
            address.replace(match.value, "").trim()
        } else {
            address.trim()
        }

        // 3. Strip Plus Code from the start (patterns like "56PC+27, " or "5694+72, ")
        remaining = remaining.replace(Regex("""^[A-Za-z0-9]{4,8}\+[A-Za-z0-9]{2,4},?\s*"""), "").trim()

        // 4. Clean up trailing/leading commas or spaces
        remaining = remaining.trimEnd(',', ' ').trimStart(',', ' ')

        // 5. Assemble: name first, then address
        return if (locationName != null && remaining.isNotBlank()) {
            "$locationName — $remaining"
        } else if (locationName != null) {
            locationName
        } else {
            remaining.ifBlank { address }
        }
    }

    override fun onStart() {
        super.onStart()
        binding.mapView.onStart()
    }

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        binding.mapView.onPause()
    }

    override fun onStop() {
        super.onStop()
        binding.mapView.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.mapView.onDestroy()

        val taskId = intent.getStringExtra("taskId") ?: return

        taskListener?.let {
            db.child("tasks")
                .child(taskId)
                .removeEventListener(it)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        binding.mapView.onSaveInstanceState(outState)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        binding.mapView.onLowMemory()
    }

    private fun updateMap(task: Task) {
        val map = googleMap ?: return
        pickupMarker = null
        dropoffMarker = null
        map.clear()

        val pickupLatLng = LatLng(task.pickupLatitude, task.pickupLongitude)
        val dropoffLatLng = LatLng(task.dropoffLatitude, task.dropoffLongitude)

        val hasPickup = task.pickupLatitude != 0.0 && task.pickupLongitude != 0.0
        val hasDropoff = task.dropoffLatitude != 0.0 && task.dropoffLongitude != 0.0

        // Apply dark/light theme style matching the app settings
        val prefs = getSharedPreferences("CampusGO_Prefs", Context.MODE_PRIVATE)
        val savedTheme = prefs.getString("theme", "light") ?: "light"
        val isDarkTheme = when (savedTheme) {
            "dark" -> true
            "light" -> false
            else -> {
                val currentNightMode = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
                currentNightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES
            }
        }

        if (isDarkTheme) {
            try {
                map.setMapStyle(MapStyleOptions(DARK_MAP_STYLE_JSON))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to apply dark map style", e)
            }
        } else {
            map.setMapStyle(null)
        }

        // Enable My Location button if permission is granted
        try {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                map.isMyLocationEnabled = true
                map.uiSettings.isMyLocationButtonEnabled = true
            } else {
                map.isMyLocationEnabled = false
                map.uiSettings.isMyLocationButtonEnabled = false
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException enabling location on map", e)
        }

        map.uiSettings.isZoomControlsEnabled = false
        map.uiSettings.isMapToolbarEnabled = true

        if (hasPickup) {
            pickupMarker = map.addMarker(
                MarkerOptions()
                    .position(pickupLatLng)
                    .title("Pickup: ${task.pickup}")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
            )
        }

        if (hasDropoff) {
            dropoffMarker = map.addMarker(
                MarkerOptions()
                    .position(dropoffLatLng)
                    .title("Drop-off: ${task.dropoff}")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
            )
        }

        if (hasPickup && hasDropoff) {
            val bounds = LatLngBounds.Builder()
                .include(pickupLatLng)
                .include(dropoffLatLng)
                .build()

            binding.mapView.post {
                try {
                    val density = resources.displayMetrics.density
                    val padding = (50 * density).toInt()
                    map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, padding))
                } catch (e: Exception) {
                    try {
                        map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 0))
                    } catch (ex: Exception) {
                        Log.e(TAG, "Failed to bounds-center camera", ex)
                    }
                }
            }
        } else if (hasPickup) {
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(pickupLatLng, 15f))
        } else if (hasDropoff) {
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(dropoffLatLng, 15f))
        }
    }
}

private const val DARK_MAP_STYLE_JSON = """
[
  {
    "elementType": "geometry",
    "stylers": [
      { "color": "#1e293b" }
    ]
  },
  {
    "elementType": "labels.icon",
    "stylers": [
      { "visibility": "off" }
    ]
  },
  {
    "elementType": "labels.text.fill",
    "stylers": [
      { "color": "#94a3b8" }
    ]
  },
  {
    "elementType": "labels.text.stroke",
    "stylers": [
      { "color": "#0f172a" }
    ]
  },
  {
    "featureType": "administrative",
    "elementType": "geometry",
    "stylers": [
      { "color": "#475569" }
    ]
  },
  {
    "elementType": "landscape",
    "elementType": "geometry",
    "stylers": [
      { "color": "#0f172a" }
    ]
  },
  {
    "featureType": "poi",
    "elementType": "geometry",
    "stylers": [
      { "color": "#1e293b" }
    ]
  },
  {
    "featureType": "poi",
    "elementType": "labels.text.fill",
    "stylers": [
      { "color": "#cbd5e1" }
    ]
  },
  {
    "featureType": "road",
    "elementType": "geometry.fill",
    "stylers": [
      { "color": "#334155" }
    ]
  },
  {
    "featureType": "road",
    "elementType": "labels.text.fill",
    "stylers": [
      { "color": "#94a3b8" }
    ]
  },
  {
    "featureType": "road.highway",
    "elementType": "geometry",
    "stylers": [
      { "color": "#475569" }
    ]
  },
  {
    "featureType": "water",
    "elementType": "geometry",
    "stylers": [
      { "color": "#020617" }
    ]
  }
]
"""