package com.digibuddy.helper.ui.screens.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.digibuddy.core.model.CompleteProfileRequest
import com.digibuddy.core.network.ApiService
import com.digibuddy.helper.data.local.HelperPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CompleteHelperProfileViewModel @Inject constructor(
    private val apiService: ApiService,
    private val helperPreferences: HelperPreferences
) : ViewModel() {
    private val _isDone = MutableStateFlow(false)
    val isDone: StateFlow<Boolean> = _isDone.asStateFlow()
    var isLoading by mutableStateOf(false)

    fun completeProfile(userId: String, name: String, bio: String) = viewModelScope.launch {
        isLoading = true
        try {
            val res = apiService.completeProfile(CompleteProfileRequest(userId, name, "HELPER"))
            if (res.isSuccessful && res.body() != null) {
                val auth = res.body()!!
                helperPreferences.saveAuthData(auth.accessToken, auth.refreshToken, auth.user.id, auth.user.name, auth.user.role.name)
                _isDone.value = true
            }
        } catch (e: Exception) { /* ignore */ }
        isLoading = false
    }
}

@Composable
fun CompleteHelperProfileScreen(
    userId: String,
    onNavigateToDashboard: () -> Unit,
    viewModel: CompleteHelperProfileViewModel = hiltViewModel()
) {
    val isDone by viewModel.isDone.collectAsState()
    var name by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf("") }

    LaunchedEffect(isDone) { if (isDone) onNavigateToDashboard() }

    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Filled.SupportAgent, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(80.dp))
            Spacer(Modifier.height(16.dp))
            Text("Set up your helper profile", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text("Customers will see this info when browsing helpers.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Spacer(Modifier.height(32.dp))

            OutlinedTextField(name, { name = it }, label = { Text("Your Name") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), singleLine = true)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(bio, { bio = it }, label = { Text("Short Bio (optional)") }, placeholder = { Text("e.g. iPhone & Android expert, 5 years experience") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), minLines = 2, maxLines = 4)
            Spacer(Modifier.height(24.dp))

            Button(
                onClick = { viewModel.completeProfile(userId, name, bio) },
                enabled = name.isNotBlank() && !viewModel.isLoading,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                if (viewModel.isLoading) CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                else Text("Start Helping!", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
