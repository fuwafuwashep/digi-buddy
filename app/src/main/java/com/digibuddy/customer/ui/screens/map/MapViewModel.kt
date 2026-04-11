package com.digibuddy.customer.ui.screens.map

import android.annotation.SuppressLint
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.digibuddy.core.model.HelperProfile
import com.digibuddy.core.utils.Constants
import com.digibuddy.customer.data.repository.HelperRepository
import com.digibuddy.customer.data.repository.Result
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

enum class MapSortOption(val label: String) {
    NEAREST("Nearest"),
    HIGHEST_RATED("Highest Rated"),
    A_TO_Z("A to Z"),
    AVAILABLE("Available Only")
}

data class MapUiState(
    val helpers: List<HelperProfile> = emptyList(),
    val isLoading: Boolean = false,
    val userLat: Double = Constants.DEFAULT_LAT,
    val userLng: Double = Constants.DEFAULT_LNG,
    val focusLat: Double? = null,
    val focusLng: Double? = null,
    val error: String? = null,
    val searchQuery: String = "",
    val sortBy: MapSortOption = MapSortOption.NEAREST
) {
    val displayHelpers: List<HelperProfile>
        get() {
            val filtered = if (sortBy == MapSortOption.AVAILABLE)
                helpers.filter { it.isAvailable }
            else helpers
            return when (sortBy) {
                MapSortOption.NEAREST       -> filtered.sortedBy { it.distance ?: Double.MAX_VALUE }
                MapSortOption.HIGHEST_RATED -> filtered.sortedByDescending { it.avgRating }
                MapSortOption.A_TO_Z        -> filtered.sortedBy { it.name ?: it.user?.name ?: "" }
                MapSortOption.AVAILABLE     -> filtered.sortedByDescending { it.avgRating }
            }
        }
}

@HiltViewModel
class MapViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val helperRepository: HelperRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    fun init(focusLat: Double?, focusLng: Double?) = viewModelScope.launch {
        _uiState.value = _uiState.value.copy(focusLat = focusLat, focusLng = focusLng, isLoading = true)
        getLocationAndLoad()
    }

    @SuppressLint("MissingPermission")
    private suspend fun getLocationAndLoad() {
        try {
            val fusedLocation = LocationServices.getFusedLocationProviderClient(context)
            // Try last known location first — faster and works without GPS fix
            var loc = fusedLocation.lastLocation.await()
            if (loc == null) {
                loc = fusedLocation.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).await()
            }
            val lat = loc?.latitude ?: Constants.DEFAULT_LAT
            val lng = loc?.longitude ?: Constants.DEFAULT_LNG
            _uiState.value = _uiState.value.copy(userLat = lat, userLng = lng)
            loadHelpers(lat, lng)
        } catch (e: Exception) {
            loadHelpers(Constants.DEFAULT_LAT, Constants.DEFAULT_LNG)
        }
    }

    private suspend fun loadHelpers(lat: Double, lng: Double) {
        val apiHelpers = when (val result = helperRepository.getNearbyHelpers(lat, lng, 20.0)) {
            is Result.Success -> result.data.helpers
            is Result.Error   -> emptyList()
        }
        // TODO: Remove fake helpers below once real helper data is available from the backend
        val allHelpers = if (apiHelpers.isEmpty()) generateFakeHelpers(lat, lng) else apiHelpers
        _uiState.value = _uiState.value.copy(isLoading = false, helpers = allHelpers)
    }

    fun setSort(sort: MapSortOption) {
        _uiState.value = _uiState.value.copy(sortBy = sort)
    }

    fun setSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    // TODO: Remove this function and the fake helpers once real backend data is available
    private fun generateFakeHelpers(baseLat: Double, baseLng: Double): List<HelperProfile> {
        return listOf(
            HelperProfile(
                id = "demo_1", name = "Alex Johnson",
                avgRating = 4.9, ratingCount = 134, isAvailable = true,
                skills = listOf("iPhone", "MacBook", "WiFi Setup"), hourlyRate = 30.0,
                workAddress = "Tech Hub, Downtown",
                workLatitude = baseLat + 0.008, workLongitude = baseLng + 0.005,
                bio = "10+ years experience with Apple devices"
            ),
            HelperProfile(
                id = "demo_2", name = "Maria Garcia",
                avgRating = 4.7, ratingCount = 89, isAvailable = true,
                skills = listOf("Windows PC", "Android", "Printers"), hourlyRate = 25.0,
                workAddress = "Central Library Area",
                workLatitude = baseLat - 0.006, workLongitude = baseLng + 0.009,
                bio = "Friendly tech helper for all platforms"
            ),
            HelperProfile(
                id = "demo_3", name = "James Wright",
                avgRating = 4.5, ratingCount = 52, isAvailable = false,
                skills = listOf("Networking", "Smart Home", "Security Cams"), hourlyRate = 35.0,
                workAddress = "North Park",
                workLatitude = baseLat + 0.012, workLongitude = baseLng - 0.004,
                bio = "Smart home and network specialist"
            ),
            HelperProfile(
                id = "demo_4", name = "Sophia Chen",
                avgRating = 4.8, ratingCount = 201, isAvailable = true,
                skills = listOf("iPhone", "iPad", "MacBook"), hourlyRate = 28.0,
                workAddress = "East Side Coffee",
                workLatitude = baseLat - 0.003, workLongitude = baseLng - 0.011,
                bio = "Apple certified technician"
            ),
            HelperProfile(
                id = "demo_5", name = "Marcus Davis",
                avgRating = 4.3, ratingCount = 31, isAvailable = true,
                skills = listOf("Gaming PCs", "Streaming Setup", "RGB"), hourlyRate = 20.0,
                workAddress = "Riverside Mall",
                workLatitude = baseLat + 0.004, workLongitude = baseLng + 0.013,
                bio = "Gaming tech specialist"
            )
        )
    }
}
