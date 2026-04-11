package com.digibuddy.customer.ui.screens.helpers

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.digibuddy.core.model.*
import com.digibuddy.customer.data.repository.BookingRepository
import com.digibuddy.customer.data.repository.HelperRepository
import com.digibuddy.customer.data.repository.Result
import com.digibuddy.customer.ui.components.StarRatingDisplay
import dagger.hilt.android.lifecycle.HiltViewModel
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HelperDetailUiState(
    val isLoading: Boolean = false,
    val helper: HelperProfile? = null,
    val ratings: List<Rating> = emptyList(),
    val error: String? = null,
    val bookingCreated: Booking? = null,
    val showBookingDialog: Boolean = false
)

@HiltViewModel
class HelperDetailViewModel @Inject constructor(
    private val helperRepository: HelperRepository,
    private val bookingRepository: BookingRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(HelperDetailUiState())
    val uiState: StateFlow<HelperDetailUiState> = _uiState.asStateFlow()

    fun loadHelper(helperId: String) = viewModelScope.launch {
        _uiState.value = _uiState.value.copy(isLoading = true)
        val helperResult = helperRepository.getHelperById(helperId)
        val ratingsResult = helperRepository.getHelperRatings(helperId)
        _uiState.value = _uiState.value.copy(
            isLoading = false,
            helper = (helperResult as? Result.Success)?.data,
            ratings = (ratingsResult as? Result.Success)?.data?.ratings ?: emptyList(),
            error = (helperResult as? Result.Error)?.message
        )
    }

    fun createBooking(helperId: String, issue: String) = viewModelScope.launch {
        when (val result = bookingRepository.createBooking(helperId, issue)) {
            is Result.Success -> _uiState.value = _uiState.value.copy(bookingCreated = result.data, showBookingDialog = false)
            is Result.Error   -> _uiState.value = _uiState.value.copy(error = result.message)
        }
    }

    fun toggleBookingDialog(show: Boolean) { _uiState.value = _uiState.value.copy(showBookingDialog = show) }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HelperDetailScreen(
    helperId: String,
    onBack: () -> Unit,
    onChatClick: (roomId: String, helperName: String) -> Unit,
    onMapClick: (lat: Double, lng: Double) -> Unit,
    viewModel: HelperDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var issueText by remember { mutableStateOf("") }

    LaunchedEffect(helperId) { viewModel.loadHelper(helperId) }

    // Booking success → open chat
    LaunchedEffect(uiState.bookingCreated) {
        uiState.bookingCreated?.let { booking ->
            booking.chatRoom?.id?.let { roomId ->
                onChatClick(roomId, uiState.helper?.name ?: uiState.helper?.user?.name ?: "Helper")
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.helper?.name ?: uiState.helper?.user?.name ?: "Helper Profile") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null) } }
            )
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        val helper = uiState.helper ?: return@Scaffold

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header card
            item {
                Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier.size(80.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                if (helper.avatar != null) {
                                    AsyncImage(model = helper.avatar, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                } else {
                                    Icon(Icons.Filled.Person, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
                                }
                            }
                            Spacer(Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(helper.name ?: helper.user?.name ?: "Helper", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                StarRatingDisplay(helper.avgRating, helper.ratingCount)
                                Text("${helper.totalSessions} sessions completed", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            // Availability badge
                            AssistChip(
                                onClick = {},
                                label = { Text(if (helper.isAvailable) "Available" else "Busy") },
                                leadingIcon = {
                                    Box(Modifier.size(8.dp).clip(CircleShape).background(
                                        if (helper.isAvailable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                    ))
                                }
                            )
                        }

                        if (!helper.bio.isNullOrBlank()) {
                            Spacer(Modifier.height(12.dp))
                            Text(helper.bio!!, style = MaterialTheme.typography.bodyMedium)
                        }

                        if (helper.skills.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                helper.skills.forEach { skill ->
                                    AssistChip(onClick = {}, label = { Text(skill) })
                                }
                            }
                        }
                    }
                }
            }

            // Location card
            if (helper.workAddress != null) {
                item {
                    Card(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.LocationOn, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Work Location", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(helper.workAddress!!, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                val distance = helper.distance
                                if (distance != null) {
                                    Text("${if (distance < 1.0) "${"%.0f".format(distance * 1000)}m" else "${"%.1f".format(distance)}km"} from you",
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            val workLat = helper.workLatitude
                            val workLng = helper.workLongitude
                            if (workLat != null && workLng != null) {
                                TextButton(onClick = { onMapClick(workLat, workLng) }) {
                                    Icon(Icons.Filled.Map, null, Modifier.size(16.dp))
                                    Text("Map")
                                }
                            }
                        }
                    }
                }
            }

            // Pricing
            if (helper.hourlyRate != null) {
                item {
                    Card(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.AttachMoney, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Text("$${"%.2f".format(helper.hourlyRate)} per hour", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            // Action buttons
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = { viewModel.toggleBookingDialog(true) },
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                        enabled = helper.isAvailable
                    ) {
                        Icon(Icons.Filled.BookOnline, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Request Help")
                    }
                }
            }

            // Ratings section
            if (uiState.ratings.isNotEmpty()) {
                item {
                    Text("Reviews (${uiState.ratings.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                items(uiState.ratings.take(5)) { rating ->
                    Card(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Person, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.width(8.dp))
                                Text(rating.customer?.name ?: "Customer", style = MaterialTheme.typography.titleSmall)
                                Spacer(Modifier.weight(1f))
                                StarRatingDisplay(rating.stars.toDouble(), starSize = 14.dp)
                            }
                            if (!rating.comment.isNullOrBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(rating.comment!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }

        // Booking dialog
        if (uiState.showBookingDialog) {
            AlertDialog(
                onDismissRequest = { viewModel.toggleBookingDialog(false) },
                icon = { Icon(Icons.Filled.Support, null) },
                title = { Text("Request Tech Help") },
                text = {
                    Column {
                        Text("Describe your tech issue:", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = issueText,
                            onValueChange = { issueText = it },
                            label = { Text("What do you need help with?") },
                            minLines = 3,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { viewModel.createBooking(helperId, issueText) },
                        enabled = issueText.isNotBlank()
                    ) { Text("Send Request") }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.toggleBookingDialog(false) }) { Text("Cancel") }
                }
            )
        }
    }
}
