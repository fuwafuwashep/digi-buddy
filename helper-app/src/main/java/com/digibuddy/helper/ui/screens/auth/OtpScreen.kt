package com.digibuddy.helper.ui.screens.auth

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.digibuddy.core.model.*
import com.digibuddy.core.network.ApiService
import com.digibuddy.helper.data.local.HelperPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HelperOtpUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val isVerified: Boolean = false,
    val isNewUser: Boolean = false,
    val resendLoading: Boolean = false,
    val resendSuccess: Boolean = false
)

@HiltViewModel
class HelperOtpViewModel @Inject constructor(
    private val apiService: ApiService,
    private val helperPreferences: HelperPreferences
) : ViewModel() {
    private val _uiState = MutableStateFlow(HelperOtpUiState())
    val uiState: StateFlow<HelperOtpUiState> = _uiState.asStateFlow()

    fun verifyOtp(userId: String, code: String) = viewModelScope.launch {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        try {
            val res = apiService.verifyOtp(VerifyOtpRequest(userId, code))
            if (res.isSuccessful && res.body() != null) {
                val auth = res.body()!!
                helperPreferences.saveAuthData(auth.accessToken, auth.refreshToken, auth.user.id, auth.user.name, auth.user.role.name)
                val isNew = auth.user.name == "New User"
                _uiState.value = _uiState.value.copy(isLoading = false, isVerified = true, isNewUser = isNew)
            } else {
                _uiState.value = _uiState.value.copy(isLoading = false, error = "Invalid OTP. Please try again.")
            }
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
        }
    }

    fun resendOtp(phone: String) = viewModelScope.launch {
        _uiState.value = _uiState.value.copy(resendLoading = true, error = null, resendSuccess = false)
        try {
            val res = apiService.sendOtp(SendOtpRequest(phone))
            if (res.isSuccessful) {
                _uiState.value = _uiState.value.copy(resendLoading = false, resendSuccess = true)
            } else {
                _uiState.value = _uiState.value.copy(resendLoading = false, error = "Failed to resend code.")
            }
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(resendLoading = false, error = e.message)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OtpScreen(
    userId: String,
    phone: String,
    devCode: String? = null,
    onBack: () -> Unit,
    onNavigateToCompleteProfile: (String) -> Unit,
    onNavigateToDashboard: () -> Unit,
    viewModel: HelperOtpViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var otpValue by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    // Resend countdown (60 seconds)
    var resendCountdown by remember { mutableIntStateOf(60) }
    var canResend by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (resendCountdown > 0) {
            delay(1000)
            resendCountdown--
        }
        canResend = true
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    LaunchedEffect(uiState.isVerified) {
        if (uiState.isVerified) {
            if (uiState.isNewUser) onNavigateToCompleteProfile(userId) else onNavigateToDashboard()
        }
    }

    // Auto-verify when 6 digits entered
    LaunchedEffect(otpValue) {
        if (otpValue.length == 6) viewModel.verifyOtp(userId, otpValue)
    }

    // Reset countdown when resend succeeds
    LaunchedEffect(uiState.resendSuccess) {
        if (uiState.resendSuccess) {
            resendCountdown = 60
            canResend = false
            while (resendCountdown > 0) {
                delay(1000)
                resendCountdown--
            }
            canResend = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Verify Phone") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Enter Verification Code", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                "We sent a 6-digit code to\n$phone",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            // Dev mode banner — shows the OTP code on screen so you don't need real SMS
            if (devCode != null) {
                Spacer(Modifier.height(16.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                    modifier = Modifier.fillMaxWidth().clickable { if (devCode.length == 6) otpValue = devCode }
                ) {
                    Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("\uD83D\uDEE0 Dev Mode — Your OTP", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                        Spacer(Modifier.height(4.dp))
                        Text(devCode, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onTertiaryContainer)
                        Spacer(Modifier.height(4.dp))
                        Text("Tap to auto-fill", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f))
                    }
                }
            }

            Spacer(Modifier.height(40.dp))

            // OTP input boxes
            Box {
                OutlinedTextField(
                    otpValue,
                    { if (it.length <= 6 && it.all { c -> c.isDigit() }) otpValue = it },
                    modifier = Modifier.width(1.dp).height(1.dp).focusRequester(focusRequester),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(6) { index ->
                        Box(
                            modifier = Modifier.size(48.dp).border(
                                2.dp,
                                if (otpValue.length == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                RoundedCornerShape(8.dp)
                            ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                otpValue.getOrNull(index)?.toString() ?: "",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(32.dp))

            Button(
                onClick = { viewModel.verifyOtp(userId, otpValue) },
                enabled = otpValue.length == 6 && !uiState.isLoading,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                if (uiState.isLoading) CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                else Text("Verify Code", style = MaterialTheme.typography.titleMedium)
            }

            Spacer(Modifier.height(16.dp))

            // Resend code button with countdown
            if (canResend) {
                TextButton(
                    onClick = { viewModel.resendOtp(phone) },
                    enabled = !uiState.resendLoading
                ) {
                    if (uiState.resendLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("Resend Code")
                }
            } else {
                Text(
                    "Resend code in ${resendCountdown}s",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (uiState.resendSuccess) {
                Spacer(Modifier.height(8.dp))
                Text("A new code has been sent!", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
            }

            uiState.error?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
            }
        }
    }
}
