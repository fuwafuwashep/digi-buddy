package com.digibuddy.customer.ui.screens.search

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
import com.digibuddy.core.model.HelperProfile
import com.digibuddy.core.utils.Constants
import com.digibuddy.customer.data.repository.HelperRepository
import com.digibuddy.customer.data.repository.Result
import com.digibuddy.customer.ui.components.HelperCard
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchUiState(
    val isLoading: Boolean = false,
    val helpers: List<HelperProfile> = emptyList(),
    val error: String? = null
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val helperRepository: HelperRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    fun search(lat: Double, lng: Double, radius: Double = 15.0, skills: String? = null) =
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            when (val result = helperRepository.searchHelpers(lat, lng, radius, skills)) {
                is Result.Success -> _uiState.value = _uiState.value.copy(
                    isLoading = false, helpers = result.data.helpers
                )
                is Result.Error -> _uiState.value = _uiState.value.copy(
                    isLoading = false, error = result.message
                )
            }
        }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onHelperClick: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var radiusKm by remember { mutableStateOf(15f) }
    var lat by remember { mutableStateOf(Constants.DEFAULT_LAT.toString()) }
    var lng by remember { mutableStateOf(Constants.DEFAULT_LNG.toString()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Search Helpers") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null) } }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Search filters
            Column(modifier = Modifier.padding(16.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = lat,
                        onValueChange = { lat = it },
                        label = { Text("Latitude") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = lng,
                        onValueChange = { lng = it },
                        label = { Text("Longitude") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                }

                Spacer(Modifier.height(8.dp))

                Text("Search radius: ${radiusKm.toInt()} km", style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = radiusKm,
                    onValueChange = { radiusKm = it },
                    valueRange = 1f..50f,
                    steps = 48
                )

                Button(
                    onClick = {
                        val latD = lat.toDoubleOrNull() ?: Constants.DEFAULT_LAT
                        val lngD = lng.toDoubleOrNull() ?: Constants.DEFAULT_LNG
                        viewModel.search(latD, lngD, radiusKm.toDouble(), searchQuery.takeIf { it.isNotBlank() })
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.Search, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Find Helpers in Area")
                }
            }

            HorizontalDivider()

            if (uiState.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (uiState.helpers.isNotEmpty()) {
                        item {
                            Text(
                                "${uiState.helpers.size} helper(s) found",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    items(uiState.helpers, key = { it.id }) { helper ->
                        HelperCard(helper = helper, onClick = { onHelperClick(helper.id) })
                    }
                }
            }
        }
    }
}
