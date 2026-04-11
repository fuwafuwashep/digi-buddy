package com.digibuddy.customer.ui.screens.home

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
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
import java.util.Locale
import javax.inject.Inject

enum class SortOption(val label: String) {
    NEAREST("Nearest"),
    HIGHEST_RATED("Highest Rated"),
    A_TO_Z("A to Z"),
    MOST_REVIEWS("Most Reviews")
}

data class HomeUiState(
    val isLoading: Boolean = false,
    val helpers: List<HelperProfile> = emptyList(),
    val error: String? = null,
    val userLat: Double = Constants.DEFAULT_LAT,
    val userLng: Double = Constants.DEFAULT_LNG,
    val locationReady: Boolean = false,
    val currentAddress: String = "Detecting location...",
    val sortBy: SortOption = SortOption.NEAREST
) {
    val sortedHelpers: List<HelperProfile>
        get() = when (sortBy) {
            SortOption.NEAREST       -> helpers.sortedBy { it.distance ?: Double.MAX_VALUE }
            SortOption.HIGHEST_RATED -> helpers.sortedByDescending { it.avgRating }
            SortOption.A_TO_Z        -> helpers.sortedBy { it.name ?: it.user?.name ?: "" }
            SortOption.MOST_REVIEWS  -> helpers.sortedByDescending { it.ratingCount }
        }
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val helperRepository: HelperRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init { fetchLocationAndHelpers() }

    @SuppressLint("MissingPermission")
    fun fetchLocationAndHelpers() = viewModelScope.launch {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        try {
            val fusedLocation = LocationServices.getFusedLocationProviderClient(context)
            // Try the last known location first — it's instant and works offline
            var location = fusedLocation.lastLocation.await()
            // If no last location, request a fresh GPS fix
            if (location == null) {
                location = fusedLocation.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).await()
            }
            val lat = location?.latitude ?: Constants.DEFAULT_LAT
            val lng = location?.longitude ?: Constants.DEFAULT_LNG
            _uiState.value = _uiState.value.copy(userLat = lat, userLng = lng, locationReady = true)
            geocodeAddress(lat, lng)
            loadNearbyHelpers(lat, lng)
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(currentAddress = "Location unavailable")
            loadNearbyHelpers(Constants.DEFAULT_LAT, Constants.DEFAULT_LNG)
        }
    }

    private fun geocodeAddress(lat: Double, lng: Double) {
        try {
            @Suppress("DEPRECATION")
            val geocoder = Geocoder(context, Locale.getDefault())
            val addresses = geocoder.getFromLocation(lat, lng, 1)
            val addr = addresses?.firstOrNull()
            val formatted = buildString {
                addr?.let {
                    if (!it.thoroughfare.isNullOrBlank()) append(it.thoroughfare)
                    if (!it.locality.isNullOrBlank()) {
                        if (isNotBlank()) append(", ")
                        append(it.locality)
                    }
                    if (!it.adminArea.isNullOrBlank()) {
                        if (isNotBlank()) append(", ")
                        append(it.adminArea)
                    }
                }
            }.ifBlank { "${String.format("%.4f", lat)}, ${String.format("%.4f", lng)}" }
            _uiState.value = _uiState.value.copy(currentAddress = formatted)
        } catch (_: Exception) {
            _uiState.value = _uiState.value.copy(currentAddress = "Current Location")
        }
    }

    private suspend fun loadNearbyHelpers(lat: Double, lng: Double) {
        when (val result = helperRepository.getNearbyHelpers(lat, lng)) {
            is Result.Success -> _uiState.value = _uiState.value.copy(
                isLoading = false, helpers = result.data.helpers
            )
            is Result.Error -> _uiState.value = _uiState.value.copy(
                isLoading = false, error = result.message
            )
        }
    }

    fun refresh() { fetchLocationAndHelpers() }

    fun setSortBy(sort: SortOption) {
        _uiState.value = _uiState.value.copy(sortBy = sort)
    }
}
