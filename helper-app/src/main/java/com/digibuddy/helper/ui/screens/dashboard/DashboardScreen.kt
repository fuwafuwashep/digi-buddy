package com.digibuddy.helper.ui.screens.dashboard

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.digibuddy.core.model.Booking
import com.digibuddy.core.model.BookingStatus
import com.digibuddy.core.network.ApiService
import com.digibuddy.core.socket.SocketManager
import com.digibuddy.helper.data.local.HelperPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

data class DashboardUiState(
    val isAvailable: Boolean = false,
    val pendingBookings: List<Booking> = emptyList(),
    val activeBookings: List<Booking> = emptyList(),
    val isLoading: Boolean = false,
    val helperName: String = "",
    val error: String? = null
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val apiService: ApiService,
    private val helperPreferences: HelperPreferences
) : ViewModel() {
    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private val socket = SocketManager.getInstance()

    init {
        viewModelScope.launch {
            helperPreferences.isAvailable.collect { available ->
                _uiState.value = _uiState.value.copy(isAvailable = available)
            }
        }
        viewModelScope.launch {
            helperPreferences.userName.collect { name ->
                _uiState.value = _uiState.value.copy(helperName = name ?: "")
            }
        }
        loadBookings()
        connectSocket()
    }

    private fun connectSocket() = viewModelScope.launch {
        val token = helperPreferences.getAccessToken() ?: return@launch
        if (!socket.isConnected()) socket.connect(token)

        socket.onBookingNew { json ->
            _uiState.value = _uiState.value.copy(
                pendingBookings = _uiState.value.pendingBookings + Booking(
                    id = json.optString("bookingId"),
                    issue = json.optString("issue"),
                    status = BookingStatus.PENDING
                )
            )
        }
    }

    fun toggleAvailability(available: Boolean) = viewModelScope.launch {
        helperPreferences.setAvailability(available)
        _uiState.value = _uiState.value.copy(isAvailable = available)
        socket.updateAvailability(available)
        try { apiService.updateAvailability(com.digibuddy.core.model.UpdateAvailabilityRequest(available)) } catch (_: Exception) {}
    }

    fun acceptBooking(bookingId: String) = viewModelScope.launch {
        try {
            apiService.updateBookingStatus(bookingId, com.digibuddy.core.model.UpdateBookingStatusRequest("ACCEPTED"))
            loadBookings()
        } catch (_: Exception) {}
    }

    fun declineBooking(bookingId: String) = viewModelScope.launch {
        try {
            apiService.updateBookingStatus(bookingId, com.digibuddy.core.model.UpdateBookingStatusRequest("DECLINED"))
            loadBookings()
        } catch (_: Exception) {}
    }

    fun loadBookings() = viewModelScope.launch {
        _uiState.value = _uiState.value.copy(isLoading = true)
        try {
            val pending = apiService.getMyBookings("PENDING", "helper")
            val active  = apiService.getMyBookings("IN_PROGRESS", "helper")
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                pendingBookings = if (pending.isSuccessful) pending.body() ?: emptyList() else emptyList(),
                activeBookings  = if (active.isSuccessful) active.body() ?: emptyList() else emptyList()
            )
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
        }
    }

    override fun onCleared() { socket.disconnect(); super.onCleared() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onChatClick: (roomId: String, customerName: String) -> Unit,
    onSetWorkLocationClick: () -> Unit,
    onProfileClick: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("DigiBuddy Helper", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(uiState.helperName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = {
                    IconButton(onClick = onSetWorkLocationClick) { Icon(Icons.Filled.LocationOn, "Set work location") }
                    IconButton(onClick = onProfileClick) { Icon(Icons.Filled.Person, "Profile") }
                }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {

            // Availability toggle
            item {
                Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (uiState.isAvailable) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(if (uiState.isAvailable) "You're Available" else "You're Offline", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(if (uiState.isAvailable) "Customers can find and book you" else "Toggle on to start accepting requests", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = uiState.isAvailable,
                            onCheckedChange = viewModel::toggleAvailability,
                            thumbContent = {
                                Icon(if (uiState.isAvailable) Icons.Filled.Check else Icons.Filled.Close, null, modifier = Modifier.size(16.dp))
                            }
                        )
                    }
                }
            }

            // Quick stats
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard("Pending", uiState.pendingBookings.size.toString(), Icons.Filled.Pending, modifier = Modifier.weight(1f))
                    StatCard("Active", uiState.activeBookings.size.toString(), Icons.Filled.PlayArrow, modifier = Modifier.weight(1f))
                }
            }

            // Pending requests
            if (uiState.pendingBookings.isNotEmpty()) {
                item {
                    Text("New Requests (${uiState.pendingBookings.size})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
                items(uiState.pendingBookings, key = { it.id }) { booking ->
                    PendingBookingCard(
                        booking = booking,
                        onAccept = { viewModel.acceptBooking(booking.id) },
                        onDecline = { viewModel.declineBooking(booking.id) }
                    )
                }
            }

            // Active bookings
            if (uiState.activeBookings.isNotEmpty()) {
                item {
                    Text("Active Sessions (${uiState.activeBookings.size})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
                items(uiState.activeBookings, key = { "active_${it.id}" }) { booking ->
                    ActiveBookingCard(
                        booking = booking,
                        onChatClick = { booking.chatRoom?.id?.let { roomId -> onChatClick(roomId, booking.customer?.name ?: "Customer") } }
                    )
                }
            }

            // Empty state
            if (!uiState.isLoading && uiState.pendingBookings.isEmpty() && uiState.activeBookings.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Filled.EventAvailable, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(8.dp))
                            Text("No active requests", style = MaterialTheme.typography.titleMedium)
                            Text(if (uiState.isAvailable) "Waiting for customers…" else "Toggle availability to start", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier) {
    Card(shape = RoundedCornerShape(12.dp), modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PendingBookingCard(booking: Booking, onAccept: () -> Unit, onDecline: () -> Unit) {
    val customerName = booking.customer?.name ?: "Customer"
    Card(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Person, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(customerName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(8.dp))
            Text(booking.issue.take(120), style = MaterialTheme.typography.bodySmall)
            if (!booking.meetAddress.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Row { Icon(Icons.Filled.LocationOn, null, Modifier.size(14.dp)); Text(booking.meetAddress!!, style = MaterialTheme.typography.labelSmall) }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onDecline, modifier = Modifier.weight(1f), colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Decline") }
                Button(onClick = onAccept, modifier = Modifier.weight(1f)) { Text("Accept") }
            }
        }
    }
}

@Composable
private fun ActiveBookingCard(booking: Booking, onChatClick: () -> Unit) {
    val customerName = booking.customer?.name ?: "Customer"
    Card(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.SupportAgent, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(customerName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                AssistChip(onClick = {}, label = { Text("IN PROGRESS", style = MaterialTheme.typography.labelSmall) })
            }
            Spacer(Modifier.height(4.dp))
            Text(booking.issue.take(80), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onChatClick, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Chat, null, Modifier.size(16.dp)); Spacer(Modifier.width(8.dp)); Text("Open Chat")
            }
        }
    }
}
