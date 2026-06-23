package com.CampusGO.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.CampusGO.app.databinding.ActivityMainBinding
import com.CampusGO.app.fragment.ChatListFragment
import com.CampusGO.app.fragment.FeedFragment
import com.CampusGO.app.fragment.MapFragment
import com.CampusGO.app.fragment.ProfileFragment
import com.CampusGO.app.fragment.TasksFragment
import com.CampusGO.app.model.CampusNotification
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.Query
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val dbRef = FirebaseDatabase.getInstance().reference
    private var notificationsQuery: Query? = null
    private var notificationsListener: ChildEventListener? = null
    private var presenceConnectedListener: ValueEventListener? = null

    companion object {
        private const val REQUEST_NOTIFICATION_PERMISSION = 1001
        private const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (savedInstanceState == null) {
            loadFragment(FeedFragment())
        }

        binding.bottomNav.setOnItemSelectedListener { item ->
            val fragment: Fragment = when (item.itemId) {
                R.id.nav_feed -> FeedFragment()
                R.id.nav_tasks -> TasksFragment()
                R.id.nav_map -> MapFragment()
                R.id.nav_chat -> ChatListFragment()
                R.id.nav_profile -> ProfileFragment()
                else -> FeedFragment()
            }
            loadFragment(fragment)
            true
        }

        checkNotificationPermission()
        startNotificationListener()
        startPresenceMonitoring()
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    REQUEST_NOTIFICATION_PERMISSION
                )
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_NOTIFICATION_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Notification permission granted")
            } else {
                Log.d(TAG, "Notification permission denied")
            }
        }
    }

    private fun startNotificationListener() {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        Log.d(TAG, "Starting notifications listener for user: $currentUserId")

        val query = dbRef.child("notifications")
            .orderByChild("receiverUserId")
            .equalTo(currentUserId)

        notificationsQuery = query

        notificationsListener = query.addChildEventListener(object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val notification = snapshot.getValue(CampusNotification::class.java) ?: return
                
                Log.d(TAG, "Received real-time notification: ${notification.title}")
                
                NotificationHelper.showPopupNotification(
                    context = this@MainActivity,
                    title = notification.title,
                    body = notification.body,
                    type = notification.type,
                    taskId = notification.taskId,
                    chatId = notification.chatId
                )

                // Delete immediately so it does not trigger again
                snapshot.ref.removeValue()
                    .addOnSuccessListener {
                        Log.d(TAG, "Deleted processed notification: ${notification.id}")
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Failed to delete notification ${notification.id}", e)
                    }
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onChildRemoved(snapshot: DataSnapshot) {}
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Notifications listener cancelled: ${error.message}")
            }
        })
    }

    private fun stopNotificationListener() {
        notificationsListener?.let { listener ->
            notificationsQuery?.removeEventListener(listener)
            Log.d(TAG, "Stopped notifications listener")
        }
        notificationsListener = null
        notificationsQuery = null
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val tabId = intent.getIntExtra("openTab", -1)
        if (tabId != -1) {
            binding.bottomNav.selectedItemId = tabId
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commitAllowingStateLoss()
    }

    private fun startPresenceMonitoring() {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val database = FirebaseDatabase.getInstance()
        val onlineRef = database.getReference("presence/$currentUserId/online")
        val lastSeenRef = database.getReference("presence/$currentUserId/lastSeen")
        val connectedRef = database.getReference(".info/connected")

        presenceConnectedListener = connectedRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val connected = snapshot.getValue(Boolean::class.java) ?: false
                if (connected) {
                    onlineRef.setValue(true)
                    onlineRef.onDisconnect().setValue(false)
                    lastSeenRef.onDisconnect().setValue(ServerValue.TIMESTAMP)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Presence listener cancelled: ${error.message}")
            }
        })
    }

    private fun stopPresenceMonitoring() {
        presenceConnectedListener?.let {
            FirebaseDatabase.getInstance().getReference(".info/connected").removeEventListener(it)
        }
        presenceConnectedListener = null

        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseDatabase.getInstance().getReference("presence/$currentUserId/online").setValue(false)
        FirebaseDatabase.getInstance().getReference("presence/$currentUserId/lastSeen").setValue(ServerValue.TIMESTAMP)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopNotificationListener()
        stopPresenceMonitoring()
    }
}
