package com.CampusGO.app.fragment

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.CampusGO.app.ChatActivity
import com.CampusGO.app.R
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
import com.google.android.gms.maps.model.MapStyleOptions
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
    "featureType": "landscape",
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
    var searchQuery by remember { mutableStateOf("") }

    // Read saved theme to set dark styled maps JSON automatically
    val prefs = remember { context.getSharedPreferences("CampusGO_Prefs", Context.MODE_PRIVATE) }
    val savedTheme = prefs.getString("theme", "light") ?: "light"
    val isDarkTheme = when (savedTheme) {
        "dark" -> true
        "light" -> false
        else -> androidx.compose.foundation.isSystemInDarkTheme()
    }
    
    val mapStyleOptions = remember(isDarkTheme) {
        if (isDarkTheme) MapStyleOptions(DARK_MAP_STYLE_JSON) else null
    }

    // Filter and search logic combined
    val filteredTasks = remember(tasks, selectedFilter, searchQuery, userLocation) {
        var base = tasks.filter { it.status == TaskStatus.OPEN }
        
        // Apply search query
        if (searchQuery.isNotBlank()) {
            base = base.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                it.description.contains(searchQuery, ignoreCase = true) ||
                it.pickup.contains(searchQuery, ignoreCase = true) ||
                it.dropoff.contains(searchQuery, ignoreCase = true) ||
                it.category.contains(searchQuery, ignoreCase = true)
            }
        }

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
        // Declarative map wrapper
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(
                isMyLocationEnabled = locationPermissionGranted,
                mapStyleOptions = mapStyleOptions
            ),
            uiSettings = MapUiSettings(
                myLocationButtonEnabled = false // Custom action FAB button handles this for a premium look
            )
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

        // Overlay Panel: Search Bar, Options, and Filters
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(16.dp)
                .align(Alignment.TopCenter)
        ) {
            // Google Maps-style Floating Search Bar (Lecturer Impressing UI)
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isDarkTheme) Color(0xFF1e293b) else Color.White
                ),
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .shadow(elevation = 8.dp, shape = RoundedCornerShape(28.dp)),
                border = BorderStroke(1.dp, if (isDarkTheme) Color(0xFF334155) else Color(0xFFE2E8F0))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search icon",
                        tint = if (isDarkTheme) Color(0xFF94A3B8) else Color(0xFF64748B),
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    TextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { 
                            Text(
                                text = "Search tasks, food, errands...", 
                                fontSize = 14.sp,
                                color = if (isDarkTheme) Color(0xFF94A3B8) else Color(0xFF64748B)
                            ) 
                        },
                        modifier = Modifier.weight(1f),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                            focusedTextColor = if (isDarkTheme) Color.White else Color(0xFF0F172A),
                            unfocusedTextColor = if (isDarkTheme) Color.White else Color(0xFF0F172A)
                        ),
                        singleLine = true
                    )
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { searchQuery = "" },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear search",
                                tint = if (isDarkTheme) Color(0xFF94A3B8) else Color(0xFF64748B)
                            )
                        }
                    }
                }
            }

            // Heatmap / Markers Switch Card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isDarkTheme) Color(0xFF1e293b).copy(alpha = 0.95f) else Color.White.copy(alpha = 0.95f)
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .shadow(elevation = 2.dp, shape = RoundedCornerShape(16.dp))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Map View Options", 
                        fontWeight = FontWeight.Bold, 
                        fontSize = 13.sp,
                        color = if (isDarkTheme) Color.White else Color(0xFF0F172A)
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (isHeatmapEnabled) "Heatmap" else "Markers", 
                            fontSize = 11.sp, 
                            color = if (isDarkTheme) Color(0xFF94A3B8) else Color(0xFF64748B),
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Switch(
                            checked = isHeatmapEnabled,
                            onCheckedChange = { isHeatmapEnabled = it }
                        )
                    }
                }
            }

            // Selectable row of filters
            val filterChips = listOf("All", "Emergency", "High Reward", "Newest", "Nearest")
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(filterChips) { chip ->
                    FilterChip(
                        selected = selectedFilter == chip,
                        onClick = { selectedFilter = chip },
                        label = { Text(chip, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = Color.White,
                            containerColor = if (isDarkTheme) Color(0xFF1e293b) else Color.White,
                            labelColor = if (isDarkTheme) Color(0xFF94A3B8) else Color(0xFF64748B)
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selectedFilter == chip,
                            borderColor = if (isDarkTheme) Color(0xFF334155) else Color(0xFFE2E8F0)
                        )
                    )
                }
            }
        }

        // Custom Geolocation Recenter FAB (Requested Feature)
        FloatingActionButton(
            onClick = {
                coroutineScope.launch {
                    userLocation?.let {
                        cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(it, 16.5f))
                    } ?: run {
                        Toast.makeText(context, "Locating current location...", Toast.LENGTH_SHORT).show()
                        try {
                            fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                                loc?.let {
                                    userLocation = LatLng(it.latitude, it.longitude)
                                    coroutineScope.launch {
                                        cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(userLocation!!, 16.5f))
                                    }
                                } ?: run {
                                    Toast.makeText(context, "Location unavailable. Enable GPS.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        } catch (e: SecurityException) {
                            e.printStackTrace()
                        }
                    }
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 16.dp, end = 16.dp)
                .size(54.dp)
                .shadow(elevation = 6.dp, shape = CircleShape),
            shape = CircleShape,
            containerColor = if (isDarkTheme) Color(0xFF1e293b) else Color.White,
            contentColor = MaterialTheme.colorScheme.primary
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_my_location),
                contentDescription = "Recenter GPS Location",
                modifier = Modifier.size(24.dp)
            )
        }

        // ModalBottomSheet Container logic (High-quality Logistics styling)
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
                containerColor = if (isDarkTheme) Color(0xFF0F172A) else MaterialTheme.colorScheme.background,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                dragHandle = { BottomSheetDefaults.DragHandle(color = if (isDarkTheme) Color(0xFF334155) else Color(0xFFE2E8F0)) }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, end = 24.dp, bottom = 32.dp)
                ) {
                    // Header title & category badges
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = task.title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = if (isDarkTheme) Color.White else Color(0xFF0F172A),
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (task.isEmergency) {
                                Box(
                                    modifier = Modifier
                                        .background(Color(0xFFFEE2E2), shape = RoundedCornerShape(12.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text("🚨 EMERGENCY", color = Color(0xFFEF4444), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(12.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(task.category.uppercase(), color = MaterialTheme.colorScheme.primary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))

                    // Route details styled like a real logistics app (Impressive UX)
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isDarkTheme) Color(0xFF1e293b) else Color(0xFFF8FAFC)
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, if (isDarkTheme) Color(0xFF334155) else Color(0xFFE2E8F0))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .background(Color(0xFF22C55E), shape = CircleShape)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text("PICKUP", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (isDarkTheme) Color(0xFF64748B) else Color(0xFF94A3B8))
                                    Text(task.pickup, fontSize = 14.sp, color = if (isDarkTheme) Color.White else Color(0xFF0F172A), fontWeight = FontWeight.SemiBold)
                                }
                            }
                            
                            // Vertical Dotted connector line
                            Box(
                                modifier = Modifier
                                    .padding(start = 4.dp, top = 4.dp, bottom = 4.dp)
                                    .width(2.dp)
                                    .height(20.dp)
                                    .background(if (isDarkTheme) Color(0xFF334155) else Color(0xFFE2E8F0))
                            )

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .background(Color(0xFFEF4444), shape = CircleShape)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text("DROPOFF", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (isDarkTheme) Color(0xFF64748B) else Color(0xFF94A3B8))
                                    Text(task.dropoff, fontSize = 14.sp, color = if (isDarkTheme) Color.White else Color(0xFF0F172A), fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Rewards & Distance metrics
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("PAYMENT", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (isDarkTheme) Color(0xFF64748B) else Color(0xFF94A3B8))
                            Text(
                                text = "RM ${String.format("%.2f", task.price)}",
                                fontWeight = FontWeight.Black,
                                fontSize = 24.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text("DISTANCE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (isDarkTheme) Color(0xFF64748B) else Color(0xFF94A3B8))
                            Text(
                                text = distanceStr,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = if (isDarkTheme) Color.White else Color(0xFF0F172A)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Task Description
                    Text("DESCRIPTION", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (isDarkTheme) Color(0xFF64748B) else Color(0xFF94A3B8))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = task.description.ifEmpty { "No description provided." },
                        fontSize = 14.sp,
                        color = if (isDarkTheme) Color(0xFFCBD5E1) else Color(0xFF334155),
                        lineHeight = 20.sp
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Action buttons
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
                            shape = RoundedCornerShape(24.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(painter = painterResource(id = R.drawable.ic_chat), contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
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
                            Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Accept Task")
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
