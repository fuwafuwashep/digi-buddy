package com.digibuddy.customer.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.digibuddy.core.model.HelperProfile
import com.digibuddy.customer.ui.components.DigiBuddyBottomNav
import com.digibuddy.customer.ui.components.DigiBuddyTopBar

private val savedAddresses = listOf(
    "\uD83C\uDFE0 Home  •  123 Main Street, Springfield",
    "\uD83D\uDCBC Work  •  456 Office Blvd, Downtown",
    "\u2B50 Favorite  •  789 Park Ave, Uptown"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onHelperClick: (String) -> Unit,
    onMapClick: () -> Unit,
    onChatListClick: () -> Unit,
    onProfileClick: () -> Unit,
    onBookingsClick: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showAddressPicker by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            Column {
                DigiBuddyTopBar(
                    onNotificationsClick = { /* TODO: navigate to notifications screen */ },
                    onSettingsClick = { showSettings = true }
                )
                // Current address row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showAddressPicker = true }
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.LocationOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = uiState.currentAddress,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f),
                        maxLines = 1
                    )
                    Icon(
                        Icons.Filled.KeyboardArrowDown,
                        contentDescription = "Change address",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        bottomBar = {
            DigiBuddyBottomNav(currentRoute = "home") { route ->
                when (route) {
                    "home"      -> { /* already here */ }
                    "map"       -> onMapClick()
                    "chat_list" -> onChatListClick()
                    "bookings"  -> onBookingsClick()
                    "profile"   -> onProfileClick()
                }
            }
        }
    ) { paddingValues ->
        PullToRefreshBox(
            isRefreshing = uiState.isLoading,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier.fillMaxSize().padding(paddingValues)
        ) {
            if (uiState.isLoading && uiState.helpers.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(8.dp))
                        Text("Finding helpers near you...", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            } else if (uiState.helpers.isEmpty() && !uiState.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                        Icon(Icons.Filled.SearchOff, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        Text("No helpers found nearby", style = MaterialTheme.typography.titleMedium)
                        Text("Try the map to search a different area", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(16.dp))
                        OutlinedButton(onClick = onMapClick) { Text("Open Map") }
                    }
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                    // Sort by row
                    item {
                        SortByRow(selected = uiState.sortBy, onSelect = { viewModel.setSortBy(it) })
                    }

                    // Top Rated section
                    val topRated = uiState.sortedHelpers.filter { it.avgRating >= 4.0 }.take(10)
                    if (topRated.isNotEmpty()) {
                        item {
                            Spacer(Modifier.height(16.dp))
                            HelperSlider(title = "\u2B50 Top Rated Near You", helpers = topRated, onHelperClick = onHelperClick)
                        }
                    }

                    // Available Now section
                    val available = uiState.sortedHelpers.filter { it.isAvailable }.take(10)
                    if (available.isNotEmpty()) {
                        item {
                            Spacer(Modifier.height(20.dp))
                            HelperSlider(title = "\uD83D\uDFE2 Available Now", helpers = available, onHelperClick = onHelperClick)
                        }
                    }

                    // All Helpers section
                    item {
                        Spacer(Modifier.height(20.dp))
                        HelperSlider(title = "\uD83D\uDC68\u200D\uD83D\uDCBB All Helpers Near You", helpers = uiState.sortedHelpers, onHelperClick = onHelperClick)
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }

    // Address Picker - full screen modal bottom sheet
    if (showAddressPicker) {
        ModalBottomSheet(
            onDismissRequest = { showAddressPicker = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            modifier = Modifier.fillMaxHeight()
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { showAddressPicker = false }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Text(
                        "Saved Addresses",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f).padding(start = 8.dp)
                    )
                }
                HorizontalDivider()
                // Current location option
                ListItem(
                    headlineContent = { Text("Use Current Location", fontWeight = FontWeight.Medium) },
                    leadingContent = { Icon(Icons.Filled.MyLocation, null, tint = MaterialTheme.colorScheme.primary) },
                    supportingContent = { Text(uiState.currentAddress, style = MaterialTheme.typography.bodySmall) },
                    modifier = Modifier.clickable { showAddressPicker = false }
                )
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                Text(
                    "SAVED ADDRESSES",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
                savedAddresses.forEach { address ->
                    val parts = address.split("  •  ")
                    ListItem(
                        headlineContent = { Text(parts.getOrElse(0) { address }) },
                        supportingContent = { parts.getOrNull(1)?.let { Text(it, style = MaterialTheme.typography.bodySmall) } },
                        leadingContent = { Icon(Icons.Filled.LocationOn, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                        modifier = Modifier.clickable { showAddressPicker = false }
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { showAddressPicker = false },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                ) {
                    Icon(Icons.Filled.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Add New Address")
                }
            }
        }
    }

    // Settings dialog
    if (showSettings) {
        AlertDialog(
            onDismissRequest = { showSettings = false },
            title = { Text("Settings") },
            text = {
                Column {
                    Text("Appearance", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text("Dark mode and other settings can be changed from your Profile page.", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(onClick = { showSettings = false }) { Text("Close") } }
        )
    }
}

@Composable
private fun SortByRow(selected: SortOption, onSelect: (SortOption) -> Unit) {
    Column(modifier = Modifier.padding(top = 12.dp)) {
        Text(
            "Sort By",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(Modifier.height(6.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(SortOption.entries.toList()) { option ->
                FilterChip(
                    selected = selected == option,
                    onClick = { onSelect(option) },
                    label = { Text(option.label) }
                )
            }
        }
    }
}

@Composable
private fun HelperSlider(
    title: String,
    helpers: List<HelperProfile>,
    onHelperClick: (String) -> Unit
) {
    Column {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(Modifier.height(10.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(helpers, key = { it.id }) { helper ->
                HelperCardCompact(helper = helper, onClick = { onHelperClick(helper.id) })
            }
        }
    }
}

@Composable
private fun HelperCardCompact(helper: HelperProfile, onClick: () -> Unit) {
    val name = helper.name ?: helper.user?.name ?: "Helper"
    Card(
        modifier = Modifier
            .width(180.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    name.firstOrNull()?.uppercase() ?: "?",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Star, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.tertiary)
                Spacer(Modifier.width(2.dp))
                Text("${String.format("%.1f", helper.avgRating)} (${helper.ratingCount})", style = MaterialTheme.typography.labelSmall)
            }
            if (helper.skills.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(helper.skills.take(2).joinToString(", "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            Spacer(Modifier.height(4.dp))
            SuggestionChip(
                onClick = {},
                label = { Text("Free with Pro", style = MaterialTheme.typography.labelSmall) },
                modifier = Modifier.height(22.dp)
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(8.dp).clip(RoundedCornerShape(4.dp))
                        .background(if (helper.isAvailable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    if (helper.isAvailable) "Available" else "Busy",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (helper.isAvailable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
