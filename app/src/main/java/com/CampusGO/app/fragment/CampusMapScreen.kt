package com.CampusGO.app.fragment

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.CampusGO.app.ChatActivity
import com.CampusGO.app.TaskTrackingActivity
import com.CampusGO.app.model.Task
import com.CampusGO.app.model.TaskCategory
import com.CampusGO.app.model.TaskStatus
import com.CampusGO.app.util.ChatIdHelper
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.maps.android.compose.*
import com.google.maps.android.compose.clustering.Clustering
import kotlinx.coroutines.launch
import com.google.maps.android.heatmaps.HeatmapTileProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

// Wrapper for custom cluster items
class MapTaskItem(val task: Task) : com.google.maps.android.clustering.ClusterItem {
    override fun getPosition(): LatLng = LatLng(task.pickupLatitude, task.pickupLongitude)
    override fun getTitle(): String = task.title
    override fun getSnippet(): String = task.description
    override fun getZIndex(): Float? = null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampusMapScreen(
    db: DatabaseReference,
    auth: FirebaseAuth,
    fusedLocationClient: FusedLocationProviderClient
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // 1. Reactive Location State
    var userLocation by remember { mutableStateOf<LatLng?>(null) }
    var locationPermissionGranted by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        if (fineGranted || coarseGranted) {
            locationPermissionGranted = true
            try {
                fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                    loc?.let { userLocation = LatLng(it.latitude, it.longitude) }
                }
            } catch (e: SecurityException) {
                e.printStackTrace()
            }
        } else {
            Toast.makeText(context, "Location permission required to center map.", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        val fineCheck = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarseCheck = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (fineCheck == PackageManager.PERMISSION_GRANTED || coarseCheck == PackageManager.PERMISSION_GRANTED) {
            locationPermissionGranted = true
            try {
                fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                    loc?.let { userLocation = LatLng(it.latitude, it.longitude) }
                }
            } catch (e: SecurityException) {
                e.printStackTrace()
            }
        } else {
            permissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
    }

    // Camera view centering state reactive trigger
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(2.9276, 101.6418), 16f)
    }

    LaunchedEffect(userLocation) {
        userLocation?.let {
            cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(it, 16.5f))
        }
    }

    // 2. Load Tasks reactive pipeline
    val taskListState = remember { MutableStateFlow<List<Task>>(emptyList()) }
    val tasksFlow = remember { taskListState.asStateFlow() }
    val tasks by tasksFlow.collectAsState(initial = emptyList())

    DisposableEffect(Unit) {
        val listener = db.child("tasks").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<Task>()
                for (snap in snapshot.children) {
                    if (snap.key == "_placeholder") continue
                    val t = snap.getValue(Task::class.java)
                    if (t != null) {
                        if (t.id.isBlank()) t.id = snap.key ?: ""
                        list.add(t)
                    }
                }
                taskListState.value = list
            }
            override fun onCancelled(error: DatabaseError) {}
        })
        onDispose {
            db.child("tasks").removeEventListener(listener)
        }
    }

    // 3. UI states
    var selectedFilter by remember { mutableStateOf("All") }
    var isHeatmapEnabled by remember { mutableStateOf(false) }
    var selectedTask by remember { mutableStateOf<Task?>(null) }
    var showBottomSheet by remember { mutableStateOf(false) }

    // Filter logic
    val filteredTasks = remember(tasks, selectedFilter, userLocation) {
        val base = tasks.filter { it.status == TaskStatus.OPEN }
        when (selectedFilter) {
            "Emergency" -> base.filter { it.isEmergency }
            "High Reward" -> base.filter { it.price >= 15.0 }
            "Newest" -> base.sortedByDescending { it.createdAt }
            "Nearest" -> {
                val loc = userLocation
                if (loc != null) {
                    base.sortedBy { task ->
                        val results = FloatArray(1)
                        Location.distanceBetween(loc.latitude, loc.longitude, task.pickupLatitude, task.pickupLongitude, results)
                        results[0]
                    }
                } else {
                    base
                }
            }
            else -> base
        }
    }

    // Map cluster items mapping
    val clusterItems = remember(filteredTasks) {
        filteredTasks.map { MapTaskItem(it) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // declarative map wrapper
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(isMyLocationEnabled = locationPermissionGranted),
            uiSettings = MapUiSettings(myLocationButtonEnabled = locationPermissionGranted)
        ) {
            if (isHeatmapEnabled && filteredTasks.isNotEmpty()) {
                val locations = filteredTasks.map { LatLng(it.pickupLatitude, it.pickupLongitude) }
                val provider = remember(locations) {
                    HeatmapTileProvider.Builder()
                        .data(locations)
                        .radius(30)
                        .opacity(0.8)
                        .build()
                }
                TileOverlay(tileProvider = provider)
            } else {
                // native maps clustering wrapper
                Clustering(
                    items = clusterItems,
                    onClusterClick = { cluster ->
                        val builder = LatLngBounds.Builder()
                        cluster.items.forEach { builder.include(it.position) }
                        coroutineScope.launch {
                            cameraPositionState.animate(CameraUpdateFactory.newLatLngBounds(builder.build(), 100))
                        }
                        true
                    },
                    onClusterItemClick = { item ->
                        selectedTask = item.task
                        showBottomSheet = true
                        true
                    },
                    clusterItemContent = { item ->
                        val color = if (item.task.isEmergency) {
                            Color(0xFFEF4444) // Vibrant Red for Emergency
                        } else {
                            when (item.task.category) {
                                TaskCategory.FOOD -> Color(0xFFF97316) // Coral Orange
                                TaskCategory.PRINT -> Color(0xFF0EA5E9) // Doc Blue
                                TaskCategory.PARCEL -> Color(0xFF8B5CF6) // Purple
                                TaskCategory.ERRAND -> Color(0xFF10B981) // Fresh Green
                                else -> Color(0xFF64748B) // Slate Gray
                            }
                        }

                        val emoji = if (item.task.isEmergency) {
                            "🚨"
                        } else {
                            when (item.task.category) {
                                TaskCategory.FOOD -> "🍔"
                                TaskCategory.PRINT -> "🖨️"
                                TaskCategory.PARCEL -> "📦"
                                TaskCategory.ERRAND -> "🏃"
                                else -> "✨"
                            }
                        }

                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .shadow(elevation = 4.dp, shape = CircleShape)
                                .background(color, shape = CircleShape)
                                .border(2.dp, Color.White, shape = CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = emoji,
                                fontSize = 18.sp
                            )
                        }
                    }
                )
            }
        }

        // Overlay Filters panel
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .align(Alignment.TopCenter)
        ) {
            // Heatmap / Markers Switch Card
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Map View Options", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (isHeatmapEnabled) "Heatmap" else "Markers", fontSize = 12.sp, modifier = Modifier.padding(end = 8.dp))
                        Switch(
                            checked = isHeatmapEnabled,
                            onCheckedChange = { isHeatmapEnabled = it }
                        )
                    }
                }
            }

            // Selecable row of filters
            val filterChips = listOf("All", "Emergency", "High Reward", "Newest", "Nearest")
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(filterChips) { chip ->
                    FilterChip(
                        selected = selectedFilter == chip,
                        onClick = { selectedFilter = chip },
                        label = { Text(chip) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }
        }

        // ModalBottomSheet Container logic
        if (showBottomSheet && selectedTask != null) {
            val task = selectedTask!!
            val userLoc = userLocation
            val distanceStr = remember(task, userLoc) {
                if (userLoc != null) {
                    val results = FloatArray(1)
                    Location.distanceBetween(
                        userLoc.latitude, userLoc.longitude,
                        task.pickupLatitude, task.pickupLongitude,
                        results
                    )
                    val km = results[0] / 1000.0
                    "${String.format("%.2f", km)} km"
                } else {
                    "Location unavailable"
                }
            }

            ModalBottomSheet(
                onDismissRequest = { showBottomSheet = false },
                containerColor = MaterialTheme.colorScheme.background,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                ) {
                    Text(
                        text = task.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Reward: RM ${String.format("%.2f", task.price)}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Distance to Pickup: $distanceStr",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = task.description,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        val uid = auth.currentUser?.uid ?: ""
                        val isPoster = task.posterId == uid

                        OutlinedButton(
                            onClick = {
                                showBottomSheet = false
                                if (uid.isNotEmpty()) {
                                    val chatId = ChatIdHelper.buildChatId(task.id, task.posterId, uid)
                                    val intent = Intent(context, ChatActivity::class.java).apply {
                                        putExtra("chatId", chatId)
                                        putExtra("taskId", task.id)
                                        putExtra("otherUserId", if (isPoster) task.runnerId else task.posterId)
                                        putExtra("otherUserName", if (isPoster) task.runnerName else task.posterName)
                                    }
                                    context.startActivity(intent)
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(24.dp)
                        ) {
                            Text("Chat")
                        }

                        Button(
                            onClick = {
                                showBottomSheet = false
                                if (isPoster) {
                                    Toast.makeText(context, "You cannot accept your own task.", Toast.LENGTH_SHORT).show()
                                } else {
                                    acceptTask(db, task, uid, context)
                                }
                            },
                            modifier = Modifier.weight(1.2f),
                            shape = RoundedCornerShape(24.dp),
                            enabled = task.status == TaskStatus.OPEN
                        ) {
                            Text("Accept")
                        }
                    }
                }
            }
        }
    }
}

private fun acceptTask(
    db: DatabaseReference,
    task: Task,
    uid: String,
    context: Context
) {
    if (uid.isEmpty()) {
        Toast.makeText(context, "You must login first", Toast.LENGTH_SHORT).show()
        return
    }

    val taskRef = db.child("tasks").child(task.id)
    taskRef.runTransaction(object : Transaction.Handler {
        override fun doTransaction(currentData: MutableData): Transaction.Result {
            val t = currentData.getValue(Task::class.java) ?: return Transaction.success(currentData)
            if (t.status != TaskStatus.OPEN) {
                return Transaction.abort()
            }
            t.status = TaskStatus.ACCEPTED
            t.runnerId = uid
            t.runnerName = FirebaseAuth.getInstance().currentUser?.displayName ?: "Runner"
            t.agreedPrice = t.price
            currentData.value = t
            return Transaction.success(currentData)
        }

        override fun onComplete(error: DatabaseError?, committed: Boolean, currentData: DataSnapshot?) {
            if (error != null) {
                Toast.makeText(context, "Error accepting task: ${error.message}", Toast.LENGTH_SHORT).show()
                return
            }
            if (!committed) {
                Toast.makeText(context, "Task is no longer available.", Toast.LENGTH_SHORT).show()
                return
            }
            Toast.makeText(context, "Task accepted successfully!", Toast.LENGTH_SHORT).show()
            val intent = Intent(context, TaskTrackingActivity::class.java).apply {
                putExtra("taskId", task.id)
            }
            context.startActivity(intent)
        }
    })
}
