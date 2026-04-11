package com.digibuddy.customer.ui.screens.map

import android.location.Geocoder
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.digibuddy.core.model.HelperProfile
import com.digibuddy.customer.ui.components.DigiBuddyBottomNav
import com.digibuddy.customer.ui.components.DigiBuddyTopBar
import com.digibuddy.customer.ui.components.StarRatingDisplay
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

private data class PoiCategory(val emoji: String, val label: String)
private val poiCategories = listOf(
    PoiCategory("\uD83C\uDF7D\uFE0F", "Restaurants"),
    PoiCategory("\uD83C\uDF33", "Parks"),
    PoiCategory("\uD83D\uDCDA", "Libraries"),
    PoiCategory("\u2615", "Cafés"),
    PoiCategory("\uD83C\uDFAA", "Stores"),
    PoiCategory("\uD83C\uDFE5", "Medical")
)

private val savedAddressesList = listOf(
    "\uD83C\uDFE0 Home  •  123 Main Street, Springfield",
    "\uD83D\uDCBC Work  •  456 Office Blvd, Downtown",
    "\u2B50 Favorite  •  789 Park Ave, Uptown"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    focusLat: Double?,
    focusLng: Double?,
    onBack: () -> Unit,
    onHelperClick: (String) -> Unit,
    onHomeClick: () -> Unit = {},
    onChatListClick: () -> Unit = {},
    onBookingsClick: () -> Unit = {},
    onProfileClick: () -> Unit = {},
    viewModel: MapViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedHelper by remember { mutableStateOf<HelperProfile?>(null) }
    var searchText by remember { mutableStateOf("") }
    var isSearchFocused by remember { mutableStateOf(false) }
    var showAddressPicker by remember { mutableStateOf(false) }
    var currentAddress by remember { mutableStateOf("Current Location") }
    var selectedPoiCategory by remember { mutableStateOf<String?>(null) }
    var geocodeError by remember { mutableStateOf<String?>(null) }
    val focusRequester = remember { FocusRequester() }

    val isBottomNavMode = focusLat == null && focusLng == null

    LaunchedEffect(Unit) { viewModel.init(focusLat, focusLng) }

    val centerLat = focusLat ?: uiState.userLat
    val centerLng = focusLng ?: uiState.userLng
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(centerLat, centerLng), 14f)
    }

    LaunchedEffect(focusLat, focusLng) {
        if (focusLat != null && focusLng != null) {
            cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(LatLng(focusLat, focusLng), 15f))
        }
    }

    // Geocode a search string to a lat/lng and move the camera there
    fun geocodeAndNavigate(query: String) {
        scope.launch {
            geocodeError = null
            try {
                val results = withContext(Dispatchers.IO) {
                    @Suppress("DEPRECATION")
                    Geocoder(context, Locale.getDefault()).getFromLocationName(query, 1)
                }
                if (!results.isNullOrEmpty()) {
                    val loc = results.first()
                    cameraPositionState.animate(
                        CameraUpdateFactory.newLatLngZoom(LatLng(loc.latitude, loc.longitude), 15f)
                    )
                    currentAddress = loc.getAddressLine(0) ?: query
                } else {
                    geocodeError = "Address not found. Try a different search."
                }
            } catch (_: Exception) {
                geocodeError = "Could not search for that address."
            }
        }
    }

    Scaffold(
        topBar = {
            if (isBottomNavMode) {
                Column {
                    DigiBuddyTopBar(onNotificationsClick = {}, onSettingsClick = {})
                    // Address row (below DigiBuddy header)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showAddressPicker = true }
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.LocationOn, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(currentAddress, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f), maxLines = 1)
                        Icon(Icons.Filled.KeyboardArrowDown, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                TopAppBar(
                    title = { Text("Map") },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null) } }
                )
            }
        },
        bottomBar = {
            if (isBottomNavMode) {
                DigiBuddyBottomNav(currentRoute = "map") { route ->
                    when (route) {
                        "home"      -> onHomeClick()
                        "map"       -> { /* already here */ }
                        "chat_list" -> onChatListClick()
                        "bookings"  -> onBookingsClick()
                        "profile"   -> onProfileClick()
                    }
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Google Map fills the entire background — zoom gestures always enabled
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                properties = MapProperties(isMyLocationEnabled = true),
                uiSettings = MapUiSettings(
                    myLocationButtonEnabled = true,
                    zoomControlsEnabled = true,       // always show +/- zoom buttons
                    zoomGesturesEnabled = true,        // always allow pinch-to-zoom
                    scrollGesturesEnabled = true,
                    tiltGesturesEnabled = true,
                    rotationGesturesEnabled = true
                )
            ) {
                Marker(
                    state = MarkerState(LatLng(uiState.userLat, uiState.userLng)),
                    title = "You are here",
                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)
                )
                if (focusLat != null && focusLng != null) {
                    Marker(
                        state = MarkerState(LatLng(focusLat, focusLng)),
                        title = "Selected Location",
                        icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE)
                    )
                }
                // Helper markers
                uiState.helpers.forEach { helper ->
                    val workLat = helper.workLatitude
                    val workLng = helper.workLongitude
                    if (workLat != null && workLng != null) {
                        Marker(
                            state = MarkerState(LatLng(workLat, workLng)),
                            title = helper.name ?: helper.user?.name ?: "Helper",
                            snippet = "\u2B50 ${String.format("%.1f", helper.avgRating)} • ${if (helper.isAvailable) "Available" else "Busy"}",
                            icon = BitmapDescriptorFactory.defaultMarker(
                                if (helper.isAvailable) BitmapDescriptorFactory.HUE_CYAN
                                else BitmapDescriptorFactory.HUE_RED
                            ),
                            onClick = { selectedHelper = helper; false }
                        )
                    }
                }
            }

            if (isBottomNavMode) {
                // Top overlay: POI chips + floating search bar
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    // POI filter chips
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        items(poiCategories) { poi ->
                            FilterChip(
                                selected = selectedPoiCategory == poi.label,
                                onClick = {
                                    selectedPoiCategory = if (selectedPoiCategory == poi.label) null else poi.label
                                    // TODO: Integrate Google Places API to filter by: ${poi.label}
                                },
                                label = { Text("${poi.emoji} ${poi.label}", style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.shadow(2.dp, RoundedCornerShape(20.dp))
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))

                    // Floating search bar
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(28.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                    ) {
                        OutlinedTextField(
                            value = searchText,
                            onValueChange = { searchText = it; geocodeError = null; viewModel.setSearchQuery(it) },
                            placeholder = { Text("Search helpers or enter an address...") },
                            leadingIcon = { Icon(Icons.Filled.Search, null) },
                            trailingIcon = {
                                if (searchText.isNotEmpty()) {
                                    IconButton(onClick = { searchText = ""; isSearchFocused = false; geocodeError = null; viewModel.setSearchQuery("") }) {
                                        Icon(Icons.Filled.Close, null)
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester)
                                .onFocusChanged { isSearchFocused = it.isFocused },
                            shape = RoundedCornerShape(28.dp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(
                                onSearch = {
                                    if (searchText.isNotBlank()) {
                                        isSearchFocused = false
                                        geocodeAndNavigate(searchText)
                                    }
                                }
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent
                            )
                        )
                    }

                    // Geocode error
                    geocodeError?.let { err ->
                        Spacer(Modifier.height(4.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                        ) {
                            Text(err, modifier = Modifier.padding(8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }

                    // Helper name/skill search dropdown
                    AnimatedVisibility(
                        visible = isSearchFocused && searchText.isNotEmpty(),
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        val filtered = uiState.helpers.filter { helper ->
                            val name = helper.name ?: helper.user?.name ?: ""
                            name.contains(searchText, ignoreCase = true) ||
                            (helper.workAddress?.contains(searchText, ignoreCase = true) == true) ||
                            helper.skills.any { it.contains(searchText, ignoreCase = true) }
                        }
                        if (filtered.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            Card(
                                modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp),
                                shape = RoundedCornerShape(12.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                            ) {
                                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                                    filtered.take(5).forEach { helper ->
                                        ListItem(
                                            headlineContent = { Text(helper.name ?: helper.user?.name ?: "Helper") },
                                            supportingContent = { Text(helper.workAddress ?: "", style = MaterialTheme.typography.bodySmall) },
                                            leadingContent = { Icon(Icons.Filled.Person, null, tint = MaterialTheme.colorScheme.primary) },
                                            modifier = Modifier.clickable { onHelperClick(helper.id); isSearchFocused = false }
                                        )
                                        HorizontalDivider()
                                    }
                                    // Option to search as address
                                    ListItem(
                                        headlineContent = { Text("Go to address: \"$searchText\"") },
                                        leadingContent = { Icon(Icons.Filled.LocationSearching, null, tint = MaterialTheme.colorScheme.secondary) },
                                        modifier = Modifier.clickable { isSearchFocused = false; geocodeAndNavigate(searchText) }
                                    )
                                }
                            }
                        }
                    }
                }

                // Bottom overlay: sort chips + helper slider
                Card(
                    modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter),
                    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(top = 12.dp)) {
                        Box(
                            modifier = Modifier.width(40.dp).height(4.dp).clip(RoundedCornerShape(2.dp))
                                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                                .align(Alignment.CenterHorizontally)
                        )
                        Spacer(Modifier.height(8.dp))
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(MapSortOption.entries.toList()) { option ->
                                FilterChip(
                                    selected = uiState.sortBy == option,
                                    onClick = { viewModel.setSort(option) },
                                    label = { Text(option.label, style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        if (uiState.displayHelpers.isEmpty()) {
                            Box(modifier = Modifier.fillMaxWidth().height(90.dp), contentAlignment = Alignment.Center) {
                                Text("No helpers found in this area", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(uiState.displayHelpers, key = { it.id }) { helper ->
                                    MapHelperCard(helper = helper, onClick = { onHelperClick(helper.id) })
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            } else {
                // Focused mode (from helper detail) — legend only
                Card(modifier = Modifier.align(Alignment.TopEnd).padding(8.dp), shape = RoundedCornerShape(8.dp)) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(12.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                            Spacer(Modifier.width(4.dp))
                            Text("Available", style = MaterialTheme.typography.labelSmall)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(12.dp).clip(CircleShape).background(MaterialTheme.colorScheme.error))
                            Spacer(Modifier.width(4.dp))
                            Text("Busy", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.3f)), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }

        // Marker click bottom sheet
        selectedHelper?.let { helper ->
            ModalBottomSheet(onDismissRequest = { selectedHelper = null }) {
                Column(modifier = Modifier.padding(16.dp).padding(bottom = 24.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Person, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(helper.name ?: helper.user?.name ?: "Helper", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            StarRatingDisplay(helper.avgRating, helper.ratingCount)
                        }
                        AssistChip(onClick = {}, label = { Text(if (helper.isAvailable) "Available" else "Busy") })
                    }
                    helper.workAddress?.let { addr ->
                        Spacer(Modifier.height(8.dp))
                        Row { Icon(Icons.Filled.LocationOn, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant); Text(addr, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { selectedHelper = null; onHelperClick(helper.id) }, modifier = Modifier.fillMaxWidth()) { Text("View Profile") }
                }
            }
        }
    }

    // Address picker
    if (showAddressPicker) {
        ModalBottomSheet(
            onDismissRequest = { showAddressPicker = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            modifier = Modifier.fillMaxHeight()
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { showAddressPicker = false }) { Icon(Icons.Filled.ArrowBack, "Back") }
                    Text("Saved Addresses", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f).padding(start = 8.dp))
                }
                HorizontalDivider()
                ListItem(
                    headlineContent = { Text("Use Current Location", fontWeight = FontWeight.Medium) },
                    leadingContent = { Icon(Icons.Filled.MyLocation, null, tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.clickable { currentAddress = "Current Location"; showAddressPicker = false }
                )
                HorizontalDivider()
                savedAddressesList.forEach { address ->
                    val parts = address.split("  •  ")
                    ListItem(
                        headlineContent = { Text(parts.getOrElse(0) { address }) },
                        supportingContent = { parts.getOrNull(1)?.let { Text(it) } },
                        leadingContent = { Icon(Icons.Filled.LocationOn, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                        modifier = Modifier.clickable {
                            val addr = parts.getOrNull(1) ?: parts.getOrElse(0) { address }
                            currentAddress = addr
                            showAddressPicker = false
                            geocodeAndNavigate(addr)
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { showAddressPicker = false }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Icon(Icons.Filled.Add, null); Spacer(Modifier.width(8.dp)); Text("Add New Address")
                }
            }
        }
    }
}

@Composable
private fun MapHelperCard(helper: HelperProfile, onClick: () -> Unit) {
    val name = helper.name ?: helper.user?.name ?: "Helper"
    Card(
        modifier = Modifier.width(160.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                    Text(name.firstOrNull()?.uppercase() ?: "?", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(name, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Star, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.tertiary)
                        Text(" ${String.format("%.1f", helper.avgRating)}", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            if (helper.skills.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(helper.skills.take(2).joinToString(", "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            Spacer(Modifier.height(4.dp))
            // Helpers are free with subscription — no hourly rate shown
            SuggestionChip(
                onClick = {},
                label = { Text("Free with Pro", style = MaterialTheme.typography.labelSmall) },
                modifier = Modifier.height(22.dp)
            )
        }
    }
}
