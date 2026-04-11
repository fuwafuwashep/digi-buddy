package com.digibuddy.helper.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.digibuddy.core.network.ApiService
import com.digibuddy.helper.data.local.HelperPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HelperProfileUiState(
    val name: String = "",
    val role: String = "HELPER",
    val avgRating: Double = 0.0,
    val totalSessions: Int = 0,
    val skills: List<String> = emptyList(),
    val bio: String = "",
    val isAvailable: Boolean = false
)

@HiltViewModel
class HelperProfileViewModel @Inject constructor(
    private val helperPreferences: HelperPreferences,
    private val apiService: ApiService
) : ViewModel() {
    private val _uiState = MutableStateFlow(HelperProfileUiState())
    val uiState: StateFlow<HelperProfileUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(helperPreferences.userName, helperPreferences.isAvailable) { name, available ->
                _uiState.value = _uiState.value.copy(name = name ?: "", isAvailable = available)
            }.collect()
        }
        loadProfile()
    }

    private fun loadProfile() = viewModelScope.launch {
        try {
            val res = apiService.getMe()
            if (res.isSuccessful) {
                val user = res.body() ?: return@launch
                val hp = user.helperProfile
                _uiState.value = _uiState.value.copy(
                    name = user.name,
                    avgRating = hp?.avgRating ?: 0.0,
                    totalSessions = hp?.totalSessions ?: 0,
                    skills = hp?.skills ?: emptyList(),
                    bio = hp?.bio ?: ""
                )
            }
        } catch (_: Exception) {}
    }

    fun logout(onDone: () -> Unit) = viewModelScope.launch {
        helperPreferences.clearAll(); onDone()
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    onLogout: () -> Unit,
    viewModel: HelperProfileViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showLogoutDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("My Profile") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null) } })
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primaryContainer).padding(32.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(modifier = Modifier.size(96.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                        Text(uiState.name.firstOrNull()?.uppercase() ?: "H", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(uiState.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(onClick = {}, label = { Text("Digital Helper") })
                        if (uiState.isAvailable) AssistChip(onClick = {}, label = { Text("Available") })
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${uiState.totalSessions}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("Sessions", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(String.format("%.1f", uiState.avgRating), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("Rating", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            if (uiState.skills.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = RoundedCornerShape(16.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Skills", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(8.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            uiState.skills.forEach { skill -> AssistChip(onClick = {}, label = { Text(skill) }) }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { showLogoutDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(52.dp),
                shape = RoundedCornerShape(12.dp)
            ) { Icon(Icons.Filled.Logout, null); Spacer(Modifier.width(8.dp)); Text("Sign Out", fontWeight = FontWeight.SemiBold) }
            Spacer(Modifier.height(32.dp))
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Sign Out") },
            text = { Text("Are you sure you want to sign out?") },
            confirmButton = { Button({ viewModel.logout(onLogout) }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Sign Out") } },
            dismissButton = { TextButton({ showLogoutDialog = false }) { Text("Cancel") } }
        )
    }
}
