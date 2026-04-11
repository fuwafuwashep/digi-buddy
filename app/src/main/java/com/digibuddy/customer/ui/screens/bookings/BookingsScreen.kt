package com.digibuddy.customer.ui.screens.bookings

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
import com.digibuddy.customer.data.repository.BookingRepository
import com.digibuddy.customer.data.repository.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BookingsUiState(
    val bookings: List<Booking> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class BookingsViewModel @Inject constructor(
    private val bookingRepository: BookingRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(BookingsUiState())
    val uiState: StateFlow<BookingsUiState> = _uiState.asStateFlow()

    init { loadBookings() }

    fun loadBookings(status: String? = null) = viewModelScope.launch {
        _uiState.value = _uiState.value.copy(isLoading = true)
        when (val result = bookingRepository.getMyBookings(status)) {
            is Result.Success -> _uiState.value = _uiState.value.copy(isLoading = false, bookings = result.data)
            is Result.Error   -> _uiState.value = _uiState.value.copy(isLoading = false, error = result.message)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookingsScreen(
    onBack: () -> Unit,
    onBookingClick: (String) -> Unit,
    viewModel: BookingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("All", "Pending", "Active", "Completed")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Bookings") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null) } }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            ScrollableTabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = {
                            selectedTab = index
                            val status = when (index) {
                                1 -> "PENDING"; 2 -> "IN_PROGRESS"; 3 -> "COMPLETED"; else -> null
                            }
                            viewModel.loadBookings(status)
                        },
                        text = { Text(title) }
                    )
                }
            }

            if (uiState.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else if (uiState.bookings.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.BookOnline, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        Text("No bookings yet", style = MaterialTheme.typography.titleMedium)
                    }
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(uiState.bookings, key = { it.id }) { booking ->
                        BookingCard(booking = booking, onClick = { onBookingClick(booking.id) })
                    }
                }
            }
        }
    }
}

@Composable
fun BookingCard(booking: Booking, onClick: () -> Unit) {
    val helperName = booking.helper?.name ?: booking.helper?.user?.name ?: "Helper"
    val statusColor = when (booking.status) {
        BookingStatus.PENDING    -> MaterialTheme.colorScheme.tertiary
        BookingStatus.ACCEPTED,
        BookingStatus.IN_PROGRESS -> MaterialTheme.colorScheme.primary
        BookingStatus.COMPLETED  -> MaterialTheme.colorScheme.secondary
        BookingStatus.CANCELLED,
        BookingStatus.DECLINED   -> MaterialTheme.colorScheme.error
    }

    Card(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.SupportAgent, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(helperName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                AssistChip(
                    onClick = {},
                    label = { Text(booking.status.name, style = MaterialTheme.typography.labelSmall) },
                    colors = AssistChipDefaults.assistChipColors(containerColor = statusColor.copy(alpha = 0.15f), labelColor = statusColor)
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(booking.issue, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            if (!booking.meetAddress.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Row {
                    Icon(Icons.Filled.LocationOn, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(booking.meetAddress!!, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookingDetailScreen(
    bookingId: String,
    onBack: () -> Unit,
    onChatClick: (roomId: String, helperName: String) -> Unit,
    viewModel: BookingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val booking = uiState.bookings.find { it.id == bookingId }

    LaunchedEffect(bookingId) {
        if (booking == null) viewModel.loadBookings()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Booking Details") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null) } }
            )
        }
    ) { padding ->
        if (booking == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
                BookingCard(booking = booking, onClick = {})
                Spacer(Modifier.height(16.dp))
                if (booking.chatRoom != null) {
                    Button(
                        onClick = { onChatClick(booking.chatRoom!!.id, booking.helper?.name ?: "Helper") },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Chat, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Open Chat")
                    }
                }
            }
        }
    }
}
