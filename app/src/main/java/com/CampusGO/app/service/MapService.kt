package com.CampusGO.app.service

import android.os.Handler
import android.os.Looper
import android.util.Log
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import com.google.android.gms.maps.model.LatLng
import java.io.IOException
import java.net.URLEncoder
import kotlinx.coroutines.*

data class RouteResult(
    val points: List<LatLng>,
    val distanceMeters: Double,
    val durationSeconds: Double
)

data class GeocodingResult(
    val displayName: String,
    val latitude: Double,
    val longitude: Double
)

data class PlacePrediction(
    val description: String,
    val placeId: String
)

class MapService {

    private val client = OkHttpClient()
    private val mainHandler = Handler(Looper.getMainLooper())

    companion object {
        private const val TAG = "MapService"
        private const val USER_AGENT = "CampusGO/1.0 (Android; AmrAi)"
    }

    /**
     * Request a routing path between two coordinates from OSRM.
     */
    fun getRoute(
        startLat: Double, startLon: Double,
        endLat: Double, endLon: Double,
        callback: (RouteResult?) -> Unit
    ) {
        val url = "https://router.project-osrm.org/route/v1/driving/$startLon,$startLat;$endLon,$endLat?overview=full&geometries=polyline"

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "OSRM routing failed: ${e.message}", e)
                mainHandler.post { callback(null) }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!response.isSuccessful) {
                        Log.e(TAG, "OSRM routing unsuccessful: code ${response.code}")
                        mainHandler.post { callback(null) }
                        return
                    }

                    val responseData = response.body?.string()
                    if (responseData.isNullOrEmpty()) {
                        mainHandler.post { callback(null) }
                        return
                    }

                    try {
                        val json = JSONObject(responseData)
                        val routes = json.getJSONArray("routes")
                        if (routes.length() > 0) {
                            val route = routes.getJSONObject(0)
                            val encodedGeometry = route.getString("geometry")
                            val distance = route.getDouble("distance")
                            val duration = route.getDouble("duration")

                            val points = decodePolyline(encodedGeometry)
                            mainHandler.post {
                                callback(RouteResult(points, distance, duration))
                            }
                        } else {
                            mainHandler.post { callback(null) }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to parse OSRM response", e)
                        mainHandler.post { callback(null) }
                    }
                }
            }
        })
    }

    /**
     * Search for a location coordinates by text input query using Google Geocoder or Nominatim fallback.
     */
    fun searchLocation(
        context: android.content.Context,
        query: String,
        latitude: Double? = null,
        longitude: Double? = null,
        callback: (List<GeocodingResult>?) -> Unit
    ) {
        if (android.location.Geocoder.isPresent()) {
            val geocoder = android.location.Geocoder(context)
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val addresses = if (latitude != null && longitude != null) {
                        val latRange = 0.05 // ~5km bounding box
                        val lonRange = 0.05
                        geocoder.getFromLocationName(
                            query, 5,
                            latitude - latRange, longitude - lonRange,
                            latitude + latRange, longitude + lonRange
                        )
                    } else {
                        geocoder.getFromLocationName(query, 5)
                    }

                    if (!addresses.isNullOrEmpty()) {
                        val results = addresses.map { addr ->
                            val nameLine = addr.featureName ?: addr.getAddressLine(0) ?: query
                            val fullAddress = addr.getAddressLine(0) ?: nameLine
                            GeocodingResult(
                                displayName = if (nameLine != fullAddress) "$nameLine, $fullAddress" else fullAddress,
                                latitude = addr.latitude,
                                longitude = addr.longitude
                            )
                        }
                        withContext(Dispatchers.Main) {
                            callback(results)
                        }
                        return@launch
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Native Geocoder search failed, using fallback: ${e.message}")
                }

                // Fallback to OSM Nominatim
                performOsmGeocoding(query, latitude, longitude, callback)
            }
            return
        }

        performOsmGeocoding(query, latitude, longitude, callback)
    }

    private fun performOsmGeocoding(
        query: String,
        latitude: Double?,
        longitude: Double?,
        callback: (List<GeocodingResult>?) -> Unit
    ) {
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            var url = "https://nominatim.openstreetmap.org/search?q=$encodedQuery&format=json&limit=5"
            if (latitude != null && longitude != null) {
                url += "&lat=$latitude&lon=$longitude"
            }

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.e(TAG, "OSM Nominatim search failed: ${e.message}", e)
                    mainHandler.post { callback(null) }
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        if (!response.isSuccessful) {
                            mainHandler.post { callback(null) }
                            return
                        }
                        val responseData = response.body?.string()
                        if (responseData.isNullOrEmpty()) {
                            mainHandler.post { callback(null) }
                            return
                        }
                        try {
                            val results = ArrayList<GeocodingResult>()
                            val jsonArray = JSONArray(responseData)
                            for (i in 0 until jsonArray.length()) {
                                val obj = jsonArray.getJSONObject(i)
                                val displayName = obj.getString("display_name")
                                val lat = obj.getString("lat").toDouble()
                                val lon = obj.getString("lon").toDouble()
                                results.add(GeocodingResult(displayName, lat, lon))
                            }
                            mainHandler.post { callback(results) }
                        } catch (e: Exception) {
                            mainHandler.post { callback(null) }
                        }
                    }
                }
            })
        } catch (e: Exception) {
            callback(null)
        }
    }

    /**
     * Reverse geocode coordinates to a display address using Google Geocoder or Nominatim fallback.
     */
    fun reverseGeocode(
        context: android.content.Context,
        lat: Double,
        lon: Double,
        callback: (String?) -> Unit
    ) {
        if (android.location.Geocoder.isPresent()) {
            val geocoder = android.location.Geocoder(context)
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val addresses = geocoder.getFromLocation(lat, lon, 1)
                    val address = addresses?.firstOrNull()?.getAddressLine(0)
                    if (!address.isNullOrEmpty()) {
                        withContext(Dispatchers.Main) {
                            callback(address)
                        }
                        return@launch
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Native reverse geocode failed, using fallback: ${e.message}")
                }

                // Fallback to OSM Nominatim
                performOsmReverseGeocoding(lat, lon, callback)
            }
            return
        }

        performOsmReverseGeocoding(lat, lon, callback)
    }

    private fun performOsmReverseGeocoding(
        lat: Double,
        lon: Double,
        callback: (String?) -> Unit
    ) {
        val url = "https://nominatim.openstreetmap.org/reverse?lat=$lat&lon=$lon&format=json"

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "OSM Nominatim reverse geocode failed: ${e.message}", e)
                mainHandler.post { callback(null) }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!response.isSuccessful) {
                        mainHandler.post { callback(null) }
                        return
                    }
                    val responseData = response.body?.string()
                    if (responseData.isNullOrEmpty()) {
                        mainHandler.post { callback(null) }
                        return
                    }
                    try {
                        val json = JSONObject(responseData)
                        val displayName = json.optString("display_name", "")
                        mainHandler.post { callback(displayName.ifEmpty { null }) }
                    } catch (e: Exception) {
                        mainHandler.post { callback(null) }
                    }
                }
            }
        })
    }

    /**
     * Decode Google polyline string format (precision 5) to a list of LatLng.
     */
    fun decodePolyline(encoded: String): List<LatLng> {
        val poly = ArrayList<LatLng>()
        var index = 0
        val len = encoded.length
        var lat = 0
        var lng = 0

        while (index < len) {
            var b: Int
            var shift = 0
            var result = 0
            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlat = if (result and 1 != 0) (result shl 1).inv() else result shr 1
            lat += dlat

            shift = 0
            result = 0
            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlng = if (result and 1 != 0) (result shl 1).inv() else result shr 1
            lng += dlng

            val pLat = lat.toDouble() / 1E5
            val pLng = lng.toDouble() / 1E5
            poly.add(LatLng(pLat, pLng))
        }
        return poly
    }

    /**
     * Fetch autocomplete suggestions from Google Places API.
     */
    fun getAutocompleteSuggestions(
        input: String,
        latitude: Double,
        longitude: Double,
        apiKey: String,
        callback: (List<PlacePrediction>?) -> Unit
    ) {
        val encodedInput = java.net.URLEncoder.encode(input, "UTF-8")
        val url = "https://maps.googleapis.com/maps/api/place/autocomplete/json" +
                "?input=$encodedInput" +
                "&location=$latitude,$longitude" +
                "&radius=5000" +
                "&components=country:my" +
                "&key=$apiKey"

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Google Places Autocomplete failed: ${e.message}", e)
                mainHandler.post { callback(null) }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!response.isSuccessful) {
                        Log.e(TAG, "Google Places Autocomplete unsuccessful: code ${response.code}")
                        mainHandler.post { callback(null) }
                        return
                    }

                    val responseData = response.body?.string()
                    if (responseData.isNullOrEmpty()) {
                        mainHandler.post { callback(null) }
                        return
                    }

                    try {
                        val json = JSONObject(responseData)
                        val status = json.optString("status", "")
                        if (status == "OK") {
                            val predictionsJson = json.getJSONArray("predictions")
                            val list = ArrayList<PlacePrediction>()
                            for (i in 0 until predictionsJson.length()) {
                                val item = predictionsJson.getJSONObject(i)
                                val description = item.getString("description")
                                val placeId = item.getString("place_id")
                                list.add(PlacePrediction(description, placeId))
                            }
                            mainHandler.post { callback(list) }
                        } else {
                            Log.e(TAG, "Google Places Autocomplete status: $status")
                            mainHandler.post { callback(null) }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to parse Google Places Autocomplete response", e)
                        mainHandler.post { callback(null) }
                    }
                }
            }
        })
    }

    /**
     * Fetch latitude & longitude coordinates for a Place ID from Google Place Details.
     */
    fun getPlaceDetails(
        placeId: String,
        apiKey: String,
        callback: (LatLng?) -> Unit
    ) {
        val url = "https://maps.googleapis.com/maps/api/place/details/json" +
                "?place_id=$placeId" +
                "&fields=geometry" +
                "&key=$apiKey"

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Google Place Details failed: ${e.message}", e)
                mainHandler.post { callback(null) }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!response.isSuccessful) {
                        Log.e(TAG, "Google Place Details unsuccessful: code ${response.code}")
                        mainHandler.post { callback(null) }
                        return
                    }

                    val responseData = response.body?.string()
                    if (responseData.isNullOrEmpty()) {
                        mainHandler.post { callback(null) }
                        return
                    }

                    try {
                        val json = JSONObject(responseData)
                        val status = json.optString("status", "")
                        if (status == "OK") {
                            val result = json.getJSONObject("result")
                            val geometry = result.getJSONObject("geometry")
                            val location = geometry.getJSONObject("location")
                            val lat = location.getDouble("lat")
                            val lng = location.getDouble("lng")
                            mainHandler.post { callback(LatLng(lat, lng)) }
                        } else {
                            Log.e(TAG, "Google Place Details status: $status")
                            mainHandler.post { callback(null) }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to parse Google Place Details response", e)
                        mainHandler.post { callback(null) }
                    }
                }
            }
        })
    }
}
