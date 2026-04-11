package com.digibuddy.helper.ui.screens.location

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.digibuddy.core.model.UpdateWorkLocationRequest
import com.digibuddy.core.network.ApiService
import com.digibuddy.helper.data.local.HelperPreferences
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

data class WorkLocationUiState(
    val isLoading: Boolean = false,
    val isSaved: Boolean = false,
    val error: String? = null,
    val currentLat: Double = 37.7749,
    val currentLng: Double = -122.4194
)

@HiltViewModel
class WorkLocationViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val apiService: ApiService,
    private val helperPreferences: HelperPreferences
) : ViewModel() {
    private val _uiState = MutableStateFlow(WorkLocationUiState())
    val uiState: StateFlow<WorkLocationUiState> = _uiState.asStateFlow()

    init { fetchCurrentLocation() }

    @SuppressLint("MissingPermission")
    private fun fetchCurrentLocation() = viewModelScope.launch {
        try {
            val loc = LocationServices.getFusedLocationProviderClient(context)
                .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).await()
            if (loc != null) _uiState.value = _uiState.value.copy(currentLat = loc.latitude, currentLng = loc.longitude)
        } catch (_: Exception) {}
    }

    fun saveWorkLocation(address: String, lat: Double, lng: Double, radiusKm: Double) = viewModelScope.launch {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        try {
            val res = apiService.updateWorkLocation(UpdateWorkLocationRequest(address, lat, lng, radiusKm))
            if (res.isSuccessful) _uiState.value = _uiState.value.copy(isLoading = false, isSaved = true)
            else _uiState.value = _uiState.value.copy(isLoading = false, error = "Failed to save location")
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetWorkLocationScreen(
    onBack: () -> Unit,
    viewModel: WorkLocationViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var address by remember { mutableStateOf("") }
    var lat by remember { mutableStateOf("") }
    var lng by remember { mutableStateOf("") }
    var radiusKm by remember { mutableStateOf(10f) }
    var useMapPicker by remember { mutableStateOf(false) }
    var markerPosition by remember { mutableStateOf<LatLng?>(null) }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(uiState.currentLat, uiState.currentLng), 13f)
    }

    LaunchedEffect(uiState.isSaved) { if (uiState.isSaved) onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Set Work Location") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null) } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
        ) {
            // Important note
            Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Info, null, tint = MaterialTheme.colorScheme.tertiary)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Use a public place (library, coffee shop, etc.) — not your home address. This is where you'll meet customers.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                // Mode toggle
                Row(modifier = Modifier.fillMaxWidth()) {
                    FilterChip(!useMapPicker, { useMapPicker = false }, { Text("Enter Address") }, modifier = Modifier.weight(1f).padding(end = 4.dp))
                    FilterChip(useMapPicker, { useMapPicker = true }, { Text("Pick on Map") }, modifier = Modifier.weight(1f).padding(start = 4.dp))
                }

                Spacer(Modifier.height(16.dp))

                if (useMapPicker) {
                    // Map picker
                    Box(modifier = Modifier.fillMaxWidth().height(300.dp)) {
                        GoogleMap(
                            modifier = Modifier.fillMaxSize(),
                            cameraPositionState = cameraPositionState,
                            onMapClick = { latLng ->
                                markerPosition = latLng
                                lat = latLng.latitude.toString()
                                lng = latLng.longitude.toString()
                            },
                            properties = MapProperties(isMyLocationEnabled = true)
                        ) {
                            markerPosition?.let { pos ->
                                Marker(state = MarkerState(pos), title = "Work Location")
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Tap on the map to set your work location", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Work Address") },
                    placeholder = { Text("e.g. City Library, 123 Main St") },
                    leadingIcon = { Icon(Icons.Filled.LocationOn, null) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                if (!useMapPicker) {
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            lat, { lat = it }, label = { Text("Latitude") },
                            modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                        )
                        OutlinedTextField(
                            lng, { lng = it }, label = { Text("Longitude") },
                            modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                Text("Service radius: ${radiusKm.toInt()} km", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                Text("Customers within this radius can see you", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Slider(radiusKm, { radiusKm = it }, valueRange = 1f..50f, steps = 48)

                Spacer(Modifier.height(24.dp))

                Button(
                    onClick = {
                        val latD = lat.toDoubleOrNull() ?: uiState.currentLat
                        val lngD = lng.toDoubleOrNull() ?: uiState.currentLng
                        viewModel.saveWorkLocation(address, latD, lngD, radiusKm.toDouble())
                    },
                    enabled = address.isNotBlank() && !uiState.isLoading,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    if (uiState.isLoading) CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                    else { Icon(Icons.Filled.Save, null); Spacer(Modifier.width(8.dp)); Text("Save Work Location", style = MaterialTheme.typography.titleMedium) }
                }

                if (uiState.error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(uiState.error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                Spacer(Modifier.height(32.dp))
            }
        }
    }
}
