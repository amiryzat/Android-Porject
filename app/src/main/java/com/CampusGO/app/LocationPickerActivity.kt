package com.CampusGO.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.CampusGO.app.service.MapService
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import kotlinx.coroutines.launch
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions

class LocationPickerActivity : AppCompatActivity() {

    private val mapService = MapService()
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    companion object {
        private const val DEFAULT_LAT = 2.9276   // Cyberjaya Central Campus
        private const val DEFAULT_LON = 101.6418
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        val composeView = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                MaterialTheme {
                    LocationPickerScreen()
                }
            }
        }
        setContentView(composeView)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun LocationPickerScreen() {
        val context = LocalContext.current
        val coroutineScope = rememberCoroutineScope()
        val keyboardController = LocalSoftwareKeyboardController.current
        val focusManager = LocalFocusManager.current

        // 1. Permission and location states
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

        // Camera positioning state
        val cameraPositionState = rememberCameraPositionState {
            position = CameraPosition.fromLatLngZoom(LatLng(DEFAULT_LAT, DEFAULT_LON), 16f)
        }

        LaunchedEffect(userLocation) {
            userLocation?.let {
                cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(it, 17f))
            }
        }

        // Selection states
        var searchQuery by remember { mutableStateOf("") }
        var selectedLatitude by remember { mutableStateOf(DEFAULT_LAT) }
        var selectedLongitude by remember { mutableStateOf(DEFAULT_LON) }
        var selectedAddress by remember { mutableStateOf("Panning map...") }

        // Google Places API key and autocomplete states
        val apiKey = remember { getApiKey(context) ?: "" }
        var predictions by remember { mutableStateOf<List<com.CampusGO.app.service.PlacePrediction>>(emptyList()) }
        var showPredictions by remember { mutableStateOf(false) }

        // Fetch predictions dynamically as user types, biased around current map center
        LaunchedEffect(searchQuery) {
            if (apiKey.isNotBlank() && searchQuery.length >= 3) {
                if (searchQuery != selectedAddress) {
                    val mapCenter = cameraPositionState.position.target
                    mapService.getAutocompleteSuggestions(
                        searchQuery,
                        mapCenter.latitude,
                        mapCenter.longitude,
                        apiKey
                    ) { results ->
                        if (results != null) {
                            predictions = results
                            showPredictions = true
                        } else {
                            predictions = emptyList()
                            showPredictions = false
                        }
                    }
                } else {
                    predictions = emptyList()
                    showPredictions = false
                }
            } else {
                predictions = emptyList()
                showPredictions = false
            }
        }

        // Reverse geocode when map stops moving
        val center = cameraPositionState.position.target
        LaunchedEffect(center) {
            selectedLatitude = center.latitude
            selectedLongitude = center.longitude
            selectedAddress = "Fetching address..."
            mapService.reverseGeocode(context, center.latitude, center.longitude) { address ->
                selectedAddress = address ?: "Lat: ${String.format("%.5f", center.latitude)}, Lon: ${String.format("%.5f", center.longitude)}"
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            // declarative map wrapper
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                properties = MapProperties(isMyLocationEnabled = locationPermissionGranted),
                uiSettings = MapUiSettings(myLocationButtonEnabled = false) // Handled by custom float action button
            )

            // Center Pin Indicator
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Place,
                    contentDescription = "Center Pin",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(48.dp)
                        .offset(y = (-24).dp)
                )
            }

            // Top Search Bar Panel & Autocomplete suggestions
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .align(Alignment.TopCenter)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { finish() },
                        modifier = Modifier
                            .size(48.dp)
                            .background(MaterialTheme.colorScheme.surface, shape = CircleShape)
                    ) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.weight(1f)
                    ) {
                        TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search campus places...", fontSize = 14.sp) },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear")
                                    }
                                }
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                imeAction = androidx.compose.ui.text.input.ImeAction.Search
                            ),
                            keyboardActions = KeyboardActions(
                                onSearch = {
                                    if (searchQuery.isNotBlank()) {
                                        showPredictions = false
                                        keyboardController?.hide()
                                        focusManager.clearFocus()
                                        val mapCenter = cameraPositionState.position.target
                                        mapService.searchLocation(context, searchQuery, mapCenter.latitude, mapCenter.longitude) { results ->
                                            if (!results.isNullOrEmpty()) {
                                                val loc = results.first()
                                                coroutineScope.launch {
                                                    cameraPositionState.animate(
                                                        CameraUpdateFactory.newLatLngZoom(
                                                            LatLng(loc.latitude, loc.longitude), 17.5f
                                                        )
                                                    )
                                                }
                                            } else {
                                                Toast.makeText(context, "Place not found.", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                }
                            )
                        )
                    }
                }

                // Autocomplete Suggestions overlay list
                if (showPredictions && predictions.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                    ) {
                        LazyColumn(
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            items(predictions) { prediction ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            searchQuery = prediction.description
                                            showPredictions = false
                                            keyboardController?.hide()
                                            focusManager.clearFocus()
                                            val mapCenter = cameraPositionState.position.target
                                            if (apiKey.isNotBlank()) {
                                                mapService.getPlaceDetails(prediction.placeId, apiKey) { latLng ->
                                                    if (latLng != null) {
                                                        coroutineScope.launch {
                                                            cameraPositionState.animate(
                                                                CameraUpdateFactory.newLatLngZoom(latLng, 17.5f)
                                                            )
                                                        }
                                                    } else {
                                                        // Fallback OSM search by address, biased to current map center
                                                        mapService.searchLocation(context, prediction.description, mapCenter.latitude, mapCenter.longitude) { results ->
                                                            if (!results.isNullOrEmpty()) {
                                                                val loc = results.first()
                                                                coroutineScope.launch {
                                                                    cameraPositionState.animate(
                                                                        CameraUpdateFactory.newLatLngZoom(
                                                                            LatLng(loc.latitude, loc.longitude), 17.5f
                                                                        )
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            } else {
                                                // Fallback OSM search by address, biased to current map center
                                                mapService.searchLocation(context, prediction.description, mapCenter.latitude, mapCenter.longitude) { results ->
                                                    if (!results.isNullOrEmpty()) {
                                                        val loc = results.first()
                                                        coroutineScope.launch {
                                                            cameraPositionState.animate(
                                                                CameraUpdateFactory.newLatLngZoom(
                                                                    LatLng(loc.latitude, loc.longitude), 17.5f
                                                                )
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        .padding(vertical = 12.dp, horizontal = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Place,
                                        contentDescription = "Place Pin",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = prediction.description,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            }
                        }
                    }
                }
            }

            // My Location Custom FAB
            FloatingActionButton(
                onClick = {
                    val loc = userLocation
                    if (loc != null) {
                        coroutineScope.launch {
                            cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(loc, 17f))
                        }
                    } else {
                        Toast.makeText(context, "Acquiring GPS location...", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier
                    .padding(bottom = 16.dp, end = 16.dp)
                    .align(Alignment.BottomEnd)
                    .offset(y = (-180).dp), // Offset to float above bottom card
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    imageVector = Icons.Default.Place,
                    contentDescription = "My Location"
                )
            }

            // Confirmation Bottom Card
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                ) {
                    Text(
                        text = "SELECTED PLACE",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = selectedAddress,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        maxLines = 3,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = {
                            val data = Intent().apply {
                                putExtra("latitude", selectedLatitude)
                                putExtra("longitude", selectedLongitude)
                                putExtra("address", selectedAddress)
                            }
                            setResult(RESULT_OK, data)
                            finish()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Confirm Location", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }
        }
    }

    private fun getApiKey(context: android.content.Context): String? {
        return try {
            val appInfo = context.packageManager.getApplicationInfo(
                context.packageName,
                android.content.pm.PackageManager.GET_META_DATA
            )
            appInfo.metaData?.getString("com.google.android.geo.API_KEY")
        } catch (e: Exception) {
            null
        }
    }
}
