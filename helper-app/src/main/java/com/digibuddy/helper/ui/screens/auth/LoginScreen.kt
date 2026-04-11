package com.digibuddy.helper.ui.screens.auth

import androidx.compose.animation.AnimatedContent
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
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.digibuddy.core.model.*
import com.digibuddy.core.network.ApiService
import com.digibuddy.helper.data.local.HelperPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HelperLoginUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val otpSent: Boolean = false,
    val userId: String = "",
    val devCode: String? = null,
    val isLoggedIn: Boolean = false
)

@HiltViewModel
class HelperLoginViewModel @Inject constructor(
    private val apiService: ApiService,
    private val helperPreferences: HelperPreferences
) : ViewModel() {
    private val _uiState = MutableStateFlow(HelperLoginUiState())
    val uiState: StateFlow<HelperLoginUiState> = _uiState.asStateFlow()

    fun sendOtp(phone: String) = viewModelScope.launch {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        try {
            val res = apiService.sendOtp(SendOtpRequest(phone))
            if (res.isSuccessful && res.body() != null) {
                val body = res.body()!!
                _uiState.value = _uiState.value.copy(isLoading = false, otpSent = true, userId = body.userId, devCode = body.devCode)
            } else _uiState.value = _uiState.value.copy(isLoading = false, error = "Failed to send OTP")
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
        }
    }

    fun loginWithEmail(email: String, password: String) = viewModelScope.launch {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        try {
            val res = apiService.login(LoginRequest(email, password))
            if (res.isSuccessful && res.body() != null) {
                val auth = res.body()!!
                helperPreferences.saveAuthData(auth.accessToken, auth.refreshToken, auth.user.id, auth.user.name, auth.user.role.name)
                _uiState.value = _uiState.value.copy(isLoading = false, isLoggedIn = true)
            } else {
                _uiState.value = _uiState.value.copy(isLoading = false, error = "Invalid credentials")
            }
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun registerHelper(
        name: String,
        @Suppress("UNUSED_PARAMETER") username: String,  // TODO: Add username field to backend RegisterRequest
        @Suppress("UNUSED_PARAMETER") phone: String,      // TODO: Save phone after registration via OTP flow
        email: String,
        @Suppress("UNUSED_PARAMETER") address: String,    // TODO: Save work address in CompleteHelperProfileScreen
        password: String
    ) = viewModelScope.launch {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        try {
            val res = apiService.register(RegisterRequest(email, password, name, role = "HELPER"))
            if (res.isSuccessful && res.body() != null) {
                val auth = res.body()!!
                helperPreferences.saveAuthData(auth.accessToken, auth.refreshToken, auth.user.id, auth.user.name, auth.user.role.name)
                _uiState.value = _uiState.value.copy(isLoading = false, isLoggedIn = true)
            } else {
                _uiState.value = _uiState.value.copy(isLoading = false, error = "Registration failed. Email may already be in use.")
            }
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
        }
    }
}

@Composable
fun LoginScreen(
    onNavigateToOtp: (userId: String, phone: String, devCode: String?) -> Unit,
    onNavigateToDashboard: () -> Unit,
    viewModel: HelperLoginViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // Outer tab: Sign In vs Sign Up
    var isSignUp by remember { mutableStateOf(false) }

    // Sign In state
    var usePhone by remember { mutableStateOf(true) }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    // Sign Up state
    var signUpName by remember { mutableStateOf("") }
    var signUpUsername by remember { mutableStateOf("") }
    var signUpPhone by remember { mutableStateOf("") }
    var signUpEmail by remember { mutableStateOf("") }
    var signUpAddress by remember { mutableStateOf("") }
    var signUpPassword by remember { mutableStateOf("") }
    var signUpConfirmPassword by remember { mutableStateOf("") }
    var signUpPasswordVisible by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.isLoggedIn) { if (uiState.isLoggedIn) onNavigateToDashboard() }
    LaunchedEffect(uiState.otpSent) { if (uiState.otpSent) onNavigateToOtp(uiState.userId, phone, uiState.devCode) }

    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Filled.SupportAgent, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(72.dp))
            Text(
                "DigiBuddy Helper",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                "Start helping people with tech",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(24.dp))

            // Sign In / Sign Up tabs
            TabRow(
                selectedTabIndex = if (isSignUp) 1 else 0,
                modifier = Modifier.fillMaxWidth()
            ) {
                Tab(
                    selected = !isSignUp,
                    onClick = { isSignUp = false; viewModel.clearError() },
                    text = { Text("Sign In", fontWeight = if (!isSignUp) FontWeight.Bold else FontWeight.Normal) }
                )
                Tab(
                    selected = isSignUp,
                    onClick = { isSignUp = true; viewModel.clearError() },
                    text = { Text("Create Account", fontWeight = if (isSignUp) FontWeight.Bold else FontWeight.Normal) }
                )
            }

            Spacer(Modifier.height(20.dp))

            AnimatedContent(isSignUp, label = "auth_mode") { signUp ->
                if (signUp) {
                    // ── SIGN UP FORM ──────────────────────────────────────────────
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            "Create your helper account",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )

                        OutlinedTextField(
                            value = signUpName,
                            onValueChange = { signUpName = it },
                            label = { Text("Full Name") },
                            leadingIcon = { Icon(Icons.Filled.Person, null) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = signUpUsername,
                            onValueChange = { signUpUsername = it.lowercase().replace(" ", "_") },
                            label = { Text("Username") },
                            leadingIcon = { Icon(Icons.Filled.AlternateEmail, null) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            supportingText = { Text("This will be your public handle", style = MaterialTheme.typography.labelSmall) }
                        )

                        OutlinedTextField(
                            value = signUpPhone,
                            onValueChange = { signUpPhone = it },
                            label = { Text("Phone Number") },
                            leadingIcon = { Icon(Icons.Filled.Phone, null) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                        )

                        OutlinedTextField(
                            value = signUpEmail,
                            onValueChange = { signUpEmail = it },
                            label = { Text("Email Address") },
                            leadingIcon = { Icon(Icons.Filled.Email, null) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                        )

                        OutlinedTextField(
                            value = signUpAddress,
                            onValueChange = { signUpAddress = it },
                            label = { Text("Work Area Address") },
                            leadingIcon = { Icon(Icons.Filled.LocationOn, null) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            supportingText = { Text("Area where you want to help people (not your home address)", style = MaterialTheme.typography.labelSmall) }
                        )

                        OutlinedTextField(
                            value = signUpPassword,
                            onValueChange = { signUpPassword = it },
                            label = { Text("Password") },
                            leadingIcon = { Icon(Icons.Filled.Lock, null) },
                            trailingIcon = {
                                IconButton({ signUpPasswordVisible = !signUpPasswordVisible }) {
                                    Icon(if (signUpPasswordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, null)
                                }
                            },
                            visualTransformation = if (signUpPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = signUpConfirmPassword,
                            onValueChange = { signUpConfirmPassword = it },
                            label = { Text("Confirm Password") },
                            leadingIcon = { Icon(Icons.Filled.Lock, null) },
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            isError = signUpConfirmPassword.isNotEmpty() && signUpConfirmPassword != signUpPassword,
                            supportingText = {
                                if (signUpConfirmPassword.isNotEmpty() && signUpConfirmPassword != signUpPassword)
                                    Text("Passwords do not match")
                            }
                        )

                        val signUpValid = signUpName.isNotBlank() && signUpUsername.isNotBlank() &&
                            signUpPhone.length >= 10 && signUpEmail.isNotBlank() &&
                            signUpAddress.isNotBlank() && signUpPassword.length >= 8 &&
                            signUpPassword == signUpConfirmPassword

                        Button(
                            onClick = {
                                viewModel.registerHelper(
                                    name = signUpName,
                                    username = signUpUsername,
                                    phone = signUpPhone,
                                    email = signUpEmail,
                                    address = signUpAddress,
                                    password = signUpPassword
                                )
                            },
                            enabled = signUpValid && !uiState.isLoading,
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            if (uiState.isLoading) CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                            else Text("Create Account", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                } else {
                    // ── SIGN IN FORM ──────────────────────────────────────────────
                    Column {
                        // Phone / Email inner tabs
                        Row(modifier = Modifier.fillMaxWidth()) {
                            FilterChip(
                                selected = usePhone,
                                onClick = { usePhone = true },
                                label = { Text("Phone") },
                                modifier = Modifier.weight(1f).padding(end = 4.dp)
                            )
                            FilterChip(
                                selected = !usePhone,
                                onClick = { usePhone = false },
                                label = { Text("Email") },
                                modifier = Modifier.weight(1f).padding(start = 4.dp)
                            )
                        }
                        Spacer(Modifier.height(16.dp))

                        AnimatedContent(usePhone, label = "login_form") { phoneMode ->
                            Column {
                                if (phoneMode) {
                                    OutlinedTextField(
                                        phone, { phone = it },
                                        label = { Text("Phone Number") },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                                    )
                                    Spacer(Modifier.height(20.dp))
                                    Button(
                                        onClick = { viewModel.sendOtp(phone) },
                                        enabled = phone.length >= 10 && !uiState.isLoading,
                                        modifier = Modifier.fillMaxWidth().height(56.dp),
                                        shape = RoundedCornerShape(16.dp)
                                    ) {
                                        if (uiState.isLoading) CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                                        else Text("Send Verification Code")
                                    }
                                } else {
                                    OutlinedTextField(
                                        email, { email = it },
                                        label = { Text("Email") },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                                    )
                                    Spacer(Modifier.height(12.dp))
                                    OutlinedTextField(
                                        password, { password = it },
                                        label = { Text("Password") },
                                        trailingIcon = {
                                            IconButton({ passwordVisible = !passwordVisible }) {
                                                Icon(if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, null)
                                            }
                                        },
                                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                        singleLine = true
                                    )
                                    Spacer(Modifier.height(20.dp))
                                    Button(
                                        onClick = { viewModel.loginWithEmail(email, password) },
                                        enabled = email.isNotBlank() && password.isNotBlank() && !uiState.isLoading,
                                        modifier = Modifier.fillMaxWidth().height(56.dp),
                                        shape = RoundedCornerShape(16.dp)
                                    ) {
                                        if (uiState.isLoading) CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                                        else Text("Sign In")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            uiState.error?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
