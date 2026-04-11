package com.digibuddy.customer.ui.screens.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.digibuddy.customer.data.local.UserPreferences
import com.digibuddy.customer.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileUiState(
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val role: String = "",
    val avatar: String? = null,
    val notificationsEnabled: Boolean = true,
    val bookingAlertsEnabled: Boolean = true,
    val chatAlertsEnabled: Boolean = true
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val userPreferences: UserPreferences,
    private val authRepository: AuthRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                userPreferences.userName,
                userPreferences.userRole,
                userPreferences.userEmail,
                userPreferences.userPhone
            ) { name, role, email, phone ->
                ProfileUiState(
                    name = name ?: "",
                    role = role ?: "",
                    email = email ?: "",
                    phone = phone ?: ""
                )
            }.collect { _uiState.value = it }
        }
    }

    fun logout(onDone: () -> Unit) = viewModelScope.launch {
        authRepository.logout()
        onDone()
    }

    fun toggleNotifications(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(notificationsEnabled = enabled)
        // TODO: Save notification preference to DataStore
    }

    fun toggleBookingAlerts(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(bookingAlertsEnabled = enabled)
    }

    fun toggleChatAlerts(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(chatAlertsEnabled = enabled)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    onLogout: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showNotificationDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showSubscriptionDialog by remember { mutableStateOf(false) }

    // Edit dialogs
    var showEditName by remember { mutableStateOf(false) }
    var showEditEmail by remember { mutableStateOf(false) }
    var showEditPhone by remember { mutableStateOf(false) }
    var showEditPassword by remember { mutableStateOf(false) }

    // Image picker for profile photo
    var avatarUri by remember { mutableStateOf<Uri?>(null) }
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            avatarUri = uri
            // TODO: Upload avatar image to backend: PUT /api/users/avatar with multipart form
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Profile") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null) } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header with profile photo
            Box(
                modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primaryContainer).padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // Profile picture with pencil edit overlay
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .clickable { imagePickerLauncher.launch("image/*") }
                    ) {
                        Box(
                            modifier = Modifier.size(96.dp).clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = uiState.name.firstOrNull()?.uppercase() ?: "?",
                                style = MaterialTheme.typography.displaySmall,
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        // Pencil icon overlay
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.secondary)
                                .align(Alignment.BottomEnd)
                                .border(2.dp, MaterialTheme.colorScheme.primaryContainer, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.Edit,
                                contentDescription = "Change photo",
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSecondary
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(uiState.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    if (uiState.role.isNotBlank()) {
                        AssistChip(onClick = {}, label = { Text(uiState.role) })
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Account Information (editable fields)
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Account Information", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    // Name (editable)
                    EditableProfileRow(
                        icon = Icons.Filled.Person,
                        label = "Name",
                        value = uiState.name.ifBlank { "Not set" },
                        onEditClick = { showEditName = true }
                    )
                    // Email (editable with verification)
                    EditableProfileRow(
                        icon = Icons.Filled.Email,
                        label = "Email",
                        value = uiState.email.ifBlank { "Not set" },
                        onEditClick = { showEditEmail = true }
                    )
                    // Phone (editable with verification)
                    EditableProfileRow(
                        icon = Icons.Filled.Phone,
                        label = "Phone",
                        value = uiState.phone.ifBlank { "Not set" },
                        onEditClick = { showEditPhone = true }
                    )
                    // Password (editable with verification)
                    EditableProfileRow(
                        icon = Icons.Filled.Lock,
                        label = "Password",
                        value = "••••••••",
                        onEditClick = { showEditPassword = true }
                    )
                    // Role (read-only)
                    ProfileInfoRow(Icons.Filled.Badge, "Role", uiState.role.ifBlank { "Customer" })
                }
            }

            Spacer(Modifier.height(16.dp))

            // Settings actions
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column {
                    ListItem(
                        headlineContent = { Text("Notification Settings") },
                        leadingContent = { Icon(Icons.Filled.Notifications, null, tint = MaterialTheme.colorScheme.primary) },
                        trailingContent = { Icon(Icons.Filled.ChevronRight, null) },
                        modifier = Modifier.clickable { showNotificationDialog = true }
                    )
                    HorizontalDivider()
                    ListItem(
                        headlineContent = { Text("Privacy & Security") },
                        leadingContent = { Icon(Icons.Filled.Security, null, tint = MaterialTheme.colorScheme.primary) },
                        trailingContent = { Icon(Icons.Filled.ChevronRight, null) },
                        modifier = Modifier.clickable { showPrivacyDialog = true }
                    )
                    HorizontalDivider()
                    ListItem(
                        headlineContent = { Text("DigiBuddy Pro Subscription") },
                        supportingContent = { Text("Unlock premium helpers", style = MaterialTheme.typography.bodySmall) },
                        leadingContent = { Icon(Icons.Filled.Star, null, tint = MaterialTheme.colorScheme.tertiary) },
                        trailingContent = { Icon(Icons.Filled.ChevronRight, null) },
                        modifier = Modifier.clickable { showSubscriptionDialog = true }
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = { showLogoutDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(52.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Filled.Logout, null)
                Spacer(Modifier.width(8.dp))
                Text("Sign Out", fontWeight = FontWeight.SemiBold)
            }

            Spacer(Modifier.height(32.dp))
        }
    }

    // Logout dialog
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Sign Out") },
            text = { Text("Are you sure you want to sign out?") },
            confirmButton = {
                Button(onClick = { viewModel.logout(onLogout) }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Sign Out") }
            },
            dismissButton = { TextButton(onClick = { showLogoutDialog = false }) { Text("Cancel") } }
        )
    }

    // Notification Settings dialog
    if (showNotificationDialog) {
        AlertDialog(
            onDismissRequest = { showNotificationDialog = false },
            title = { Text("Notification Settings") },
            text = {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("All Notifications", modifier = Modifier.weight(1f))
                        Switch(checked = uiState.notificationsEnabled, onCheckedChange = { viewModel.toggleNotifications(it) })
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("Booking Alerts", modifier = Modifier.weight(1f))
                        Switch(checked = uiState.bookingAlertsEnabled, onCheckedChange = { viewModel.toggleBookingAlerts(it) })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("Chat Alerts", modifier = Modifier.weight(1f))
                        Switch(checked = uiState.chatAlertsEnabled, onCheckedChange = { viewModel.toggleChatAlerts(it) })
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showNotificationDialog = false }) { Text("Done") } }
        )
    }

    // Privacy & Security dialog
    if (showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            title = { Text("Privacy & Security") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Your data is encrypted and never sold to third parties.", style = MaterialTheme.typography.bodyMedium)
                    HorizontalDivider()
                    Text("Account Security", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text("Use a strong, unique password. We'll send a verification code before any sensitive changes.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    HorizontalDivider()
                    Text("Data & Privacy", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text("You can request data deletion by contacting support. Location data is only used while the app is active.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = { TextButton(onClick = { showPrivacyDialog = false }) { Text("Close") } }
        )
    }

    // Subscription dialog
    if (showSubscriptionDialog) {
        AlertDialog(
            onDismissRequest = { showSubscriptionDialog = false },
            title = { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.Star, null, tint = MaterialTheme.colorScheme.tertiary); Spacer(Modifier.width(8.dp)); Text("DigiBuddy Pro") } },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Upgrade to Pro and get:", style = MaterialTheme.typography.bodyMedium)
                    listOf(
                        "Priority access to top-rated helpers",
                        "Unlimited bookings per month",
                        "24/7 dedicated support",
                        "Exclusive discounts on sessions"
                    ).forEach { perk ->
                        Row {
                            Icon(Icons.Filled.Check, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text(perk, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("\$9.99 / month", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text("Cancel anytime", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                Button(onClick = {
                    // TODO: Initiate in-app purchase flow
                    showSubscriptionDialog = false
                }) { Text("Subscribe Now") }
            },
            dismissButton = { TextButton(onClick = { showSubscriptionDialog = false }) { Text("Maybe Later") } }
        )
    }

    // Edit Name dialog
    if (showEditName) {
        var nameInput by remember { mutableStateOf(uiState.name) }
        AlertDialog(
            onDismissRequest = { showEditName = false },
            title = { Text("Change Name") },
            text = {
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = { Text("Full Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    // TODO: Call PUT /api/users/profile with { name: nameInput }
                    showEditName = false
                }, enabled = nameInput.isNotBlank()) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showEditName = false }) { Text("Cancel") } }
        )
    }

    // Edit Email dialog (requires verification)
    if (showEditEmail) {
        var emailInput by remember { mutableStateOf("") }
        var passwordConfirm by remember { mutableStateOf("") }
        var passwordVisible by remember { mutableStateOf(false) }
        var verificationSent by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showEditEmail = false },
            title = { Text("Change Email") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter your password to confirm this change.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(value = emailInput, onValueChange = { emailInput = it }, label = { Text("New Email") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(
                        value = passwordConfirm, onValueChange = { passwordConfirm = it },
                        label = { Text("Current Password") }, singleLine = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = { IconButton({ passwordVisible = !passwordVisible }) { Icon(if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, null) } },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (verificationSent) {
                        Text("A verification code has been sent to $emailInput.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (!verificationSent) {
                        verificationSent = true
                        // TODO: Call API to send verification code to new email
                    } else {
                        // TODO: Call API to confirm email change with verification code
                        showEditEmail = false
                    }
                }, enabled = emailInput.isNotBlank() && passwordConfirm.isNotBlank()) {
                    Text(if (verificationSent) "Confirm Change" else "Send Verification Code")
                }
            },
            dismissButton = { TextButton(onClick = { showEditEmail = false }) { Text("Cancel") } }
        )
    }

    // Edit Phone dialog (requires verification)
    if (showEditPhone) {
        var phoneInput by remember { mutableStateOf("") }
        var verificationSent by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showEditPhone = false },
            title = { Text("Change Phone Number") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("A verification code will be sent to your new number.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(value = phoneInput, onValueChange = { phoneInput = it }, label = { Text("New Phone Number") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth())
                    if (verificationSent) {
                        Text("A verification code has been sent to $phoneInput.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (!verificationSent) {
                        verificationSent = true
                        // TODO: Call API to send OTP to new phone number
                    } else {
                        // TODO: Call API to confirm phone change with OTP
                        showEditPhone = false
                    }
                }, enabled = phoneInput.length >= 10) {
                    Text(if (verificationSent) "Confirm Change" else "Send Code")
                }
            },
            dismissButton = { TextButton(onClick = { showEditPhone = false }) { Text("Cancel") } }
        )
    }

    // Edit Password dialog (requires email or phone verification)
    if (showEditPassword) {
        var currentPass by remember { mutableStateOf("") }
        var newPass by remember { mutableStateOf("") }
        var confirmPass by remember { mutableStateOf("") }
        var currentVisible by remember { mutableStateOf(false) }
        var newVisible by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showEditPassword = false },
            title = { Text("Change Password") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = currentPass, onValueChange = { currentPass = it },
                        label = { Text("Current Password") }, singleLine = true,
                        visualTransformation = if (currentVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = { IconButton({ currentVisible = !currentVisible }) { Icon(if (currentVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, null) } },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newPass, onValueChange = { newPass = it },
                        label = { Text("New Password") }, singleLine = true,
                        visualTransformation = if (newVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = { IconButton({ newVisible = !newVisible }) { Icon(if (newVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, null) } },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = confirmPass, onValueChange = { confirmPass = it },
                        label = { Text("Confirm New Password") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        isError = confirmPass.isNotEmpty() && confirmPass != newPass,
                        supportingText = { if (confirmPass.isNotEmpty() && confirmPass != newPass) Text("Passwords do not match") }
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    // TODO: Call PUT /api/users/password with { currentPassword, newPassword }
                    showEditPassword = false
                }, enabled = currentPass.isNotBlank() && newPass.length >= 8 && newPass == confirmPass) { Text("Update Password") }
            },
            dismissButton = { TextButton(onClick = { showEditPassword = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun EditableProfileRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    onEditClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyMedium)
        }
        IconButton(onClick = onEditClick, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Filled.Edit, contentDescription = "Edit $label", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun ProfileInfoRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
