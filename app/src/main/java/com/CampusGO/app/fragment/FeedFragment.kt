package com.CampusGO.app.fragment

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast // CHANGE: Added Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.CampusGO.app.ChatActivity
import com.CampusGO.app.CreateTaskActivity
import com.CampusGO.app.R
import com.CampusGO.app.TaskDetailActivity
import com.CampusGO.app.adapter.TaskFeedAdapter
import com.CampusGO.app.model.Task
import com.CampusGO.app.model.TaskStatus
import com.CampusGO.app.util.ChatIdHelper // CHANGE: Added ChatIdHelper
import com.google.android.material.chip.ChipGroup
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.CampusGO.app.model.TaskCategory

class FeedFragment : Fragment() {

    private lateinit var rvTasks: RecyclerView
    private lateinit var tvTaskCount: TextView
    private lateinit var tvEmpty: TextView
    private lateinit var fabPost: FloatingActionButton
    private lateinit var chipGroupFilters: ChipGroup

    private lateinit var adapter: TaskFeedAdapter
    private var allTasks = mutableListOf<Task>()

    private val db = FirebaseDatabase.getInstance().reference
    private val auth = FirebaseAuth.getInstance()

    private var tasksListener: ValueEventListener? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var lastUserLocation: Location? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            fetchLastLocation {
                applyFilter()
            }
        } else {
            if (isAdded) {
                Toast.makeText(requireContext(), "Location permission required for Nearby filter.", Toast.LENGTH_SHORT).show()
                chipGroupFilters.check(R.id.chipAll)
            }
        }
    }

    companion object {
        private const val TAG = "FeedFragment"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_feed, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        rvTasks = view.findViewById(R.id.rvTasks)
        tvTaskCount = view.findViewById(R.id.tvTaskCount)
        tvEmpty = view.findViewById(R.id.tvEmpty)
        fabPost = view.findViewById(R.id.fabPost)
        chipGroupFilters = view.findViewById(R.id.chipGroupFilters)

        adapter = TaskFeedAdapter(
            emptyList(),
            onAccept = { task ->
                openTaskDetail(task)
            },
            onChat = { task ->
                openChat(task)
            }
        )

        rvTasks.layoutManager = LinearLayoutManager(requireContext())
        rvTasks.adapter = adapter

        fabPost.setOnClickListener {
            startActivity(Intent(requireContext(), CreateTaskActivity::class.java))
        }

        chipGroupFilters.setOnCheckedStateChangeListener { _, _ ->
            applyFilter()
        }

        loadTasks()
    }

    private fun fetchLastLocation(onSuccess: () -> Unit) {
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                if (loc != null) {
                    lastUserLocation = loc
                }
                onSuccess()
            }
        } else {
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private fun loadTasks() {
        tasksListener?.let {
            db.child("tasks").removeEventListener(it)
        }

        tasksListener = db.child("tasks")
            .orderByChild("createdAt")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!isAdded) return

                    allTasks.clear()

                    val currentUid = auth.currentUser?.uid

                    for (child in snapshot.children) {
                        if (child.value !is Map<*, *>) continue
                        val task = child.getValue(Task::class.java) ?: continue

                        if (task.status == TaskStatus.OPEN && task.posterId != currentUid) {
                            allTasks.add(0, task)
                        }
                    }

                    applyFilter()
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "DB error: code=${error.code} msg=${error.message}")

                    if (isAdded) {
                        Toast.makeText(
                            requireContext(),
                            "Failed to load tasks: ${error.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            })
    }

    private fun applyFilter() {
        if (!isAdded) return

        val checkedId = chipGroupFilters.checkedChipId

        val filtered = when (checkedId) {
            R.id.chipEmergency -> allTasks.filter { it.isEmergency }
            R.id.chipHighest -> allTasks.sortedByDescending { it.price }
            R.id.chipNewest -> allTasks.sortedByDescending { it.createdAt }
            R.id.chipNearby -> {
                val loc = lastUserLocation
                if (loc != null) {
                    allTasks.filter {
                        val results = FloatArray(1)
                        Location.distanceBetween(
                            loc.latitude, loc.longitude,
                            it.pickupLatitude, it.pickupLongitude,
                            results
                        )
                        results[0] <= 2000 // 2km
                    }
                } else {
                    fetchLastLocation {
                        applyFilter()
                    }
                    allTasks.toList()
                }
            }
            R.id.chipFood -> allTasks.filter { it.category == TaskCategory.FOOD }
            R.id.chipPrint -> allTasks.filter { it.category == TaskCategory.PRINT }
            R.id.chipParcel -> allTasks.filter { it.category == TaskCategory.PARCEL }
            R.id.chipErrand -> allTasks.filter { it.category == TaskCategory.ERRAND }
            R.id.chipOther -> allTasks.filter { it.category == TaskCategory.OTHER }
            else -> allTasks.toList()
        }

        adapter.updateTasks(filtered)

        tvTaskCount.text = "${filtered.size} tasks live"
        tvEmpty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        rvTasks.visibility = if (filtered.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun openTaskDetail(task: Task) {
        startActivity(Intent(requireContext(), TaskDetailActivity::class.java).apply {
            putExtra("taskId", task.id)
        })
    }

    private fun openChat(task: Task) {
        val currentUser = auth.currentUser ?: run {
            if (isAdded) {
                Toast.makeText(requireContext(), "User not logged in", Toast.LENGTH_SHORT).show()
            }
            return
        }

        val uid = currentUser.uid
        val runnerName = currentUser.displayName ?: "Unknown"

        // CHANGE:
        // OLD CODE:
        // val chatId = "${task.id}_${auth.currentUser?.uid}"
        //
        // PROBLEM:
        // This creates taskId_runnerId for runner,
        // but poster may open taskId_posterId.
        // So both users enter different chat rooms.
        //
        // NEW FIX:
        // Use taskId + posterId + runnerId.
        // ChatIdHelper sorts the two user IDs, so both users get the same chatId.
        val chatId = ChatIdHelper.buildChatId(
            taskId = task.id,
            userA = task.posterId,
            userB = uid
        )

        Log.d(TAG, "Opening chat from FeedFragment")
        Log.d(TAG, "Current user/runnerId: $uid")
        Log.d(TAG, "Poster ID: ${task.posterId}")
        Log.d(TAG, "Generated chatId: $chatId")

        ensureChatExists(
            task = task,
            chatId = chatId,
            runnerId = uid,
            runnerName = runnerName
        )
    }

    private fun ensureChatExists(
        task: Task,
        chatId: String,
        runnerId: String,
        runnerName: String
    ) {
        db.child("chats")
            .child(chatId)
            .get()
            .addOnSuccessListener { snap ->
                if (!isAdded) return@addOnSuccessListener

                if (!snap.exists()) {
                    // CHANGE:
                    // Create the chat using the fixed chatId.
                    // Also add participants so both poster and runner are stored inside the chat.
                    val chat = mapOf(
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

                        "lastMessage" to "",
                        "lastMessageTime" to 0L,
                        "finalPrice" to 0.0
                    )

                    db.child("chats")
                        .child(chatId)
                        .setValue(chat)
                        .addOnSuccessListener {
                            Log.d(TAG, "Chat created successfully: $chatId")
                            openChatActivity(task.id, chatId)
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "Failed to create chat", e)

                            if (isAdded) {
                                Toast.makeText(
                                    requireContext(),
                                    "Failed to create chat: ${e.message}",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                } else {
                    Log.d(TAG, "Chat already exists: $chatId")
                    openChatActivity(task.id, chatId)
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to check chat", e)

                if (isAdded) {
                    Toast.makeText(
                        requireContext(),
                        "Failed to open chat: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
    }

    private fun openChatActivity(taskId: String, chatId: String) {
        if (!isAdded) return

        startActivity(Intent(requireContext(), ChatActivity::class.java).apply {
            putExtra("taskId", taskId)
            putExtra("chatId", chatId)
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()

        tasksListener?.let {
            db.child("tasks").removeEventListener(it)
        }
    }
}