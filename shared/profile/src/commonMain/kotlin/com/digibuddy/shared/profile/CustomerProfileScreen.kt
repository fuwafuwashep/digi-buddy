package com.digibuddy.shared.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.digibuddy.shared.contracts.SaveAddressRequest
import com.digibuddy.shared.contracts.UpdateAccessibilitySettingsRequest

@Composable
fun CustomerProfileExperience(
    coordinator: CustomerProfileCoordinator,
    onSignOut: () -> Unit,
    accountSettings: @Composable () -> Unit = {},
    profileContent: (@Composable (ProfileState.Profile) -> Unit)? = null,
) {
    val state by coordinator.state.collectAsState()
    LaunchedEffect(coordinator) { coordinator.load() }
    when (val current = state) {
        ProfileState.Loading -> Text("Loading your profile…")
        is ProfileState.Failure -> Button(onClick = coordinator::load) { Text(current.message) }
        is ProfileState.Onboarding -> OnboardingScreen(current, coordinator)
        is ProfileState.Profile -> profileContent?.invoke(current)
            ?: ProfileScreen(current, coordinator, onSignOut, accountSettings)
    }
}

@Composable
private fun OnboardingScreen(state: ProfileState.Onboarding, coordinator: CustomerProfileCoordinator) {
    var draft by remember(state.draft) { mutableStateOf(state.draft) }
    Column(Modifier.fillMaxSize().padding(28.dp)) {
        when (state.step) {
            OnboardingStep.WELCOME -> SimpleStep(
                "Welcome to Digibuddy",
                "We need just a few details. You can skip optional steps.",
            ) {
                coordinator.next(OnboardingStep.FIRST_NAME, draft)
            }
            OnboardingStep.FIRST_NAME -> EntryStep("What is your first name?", draft.firstName, {
                draft =
                    draft.copy(firstName = it)
            }, draft.firstName.trim().isNotEmpty()) {
                coordinator.next(OnboardingStep.LAST_NAME, draft)
            }
            OnboardingStep.LAST_NAME -> EntryStep("What is your last name?", draft.lastName, {
                draft =
                    draft.copy(lastName = it)
            }, draft.lastName.trim().isNotEmpty()) {
                coordinator.next(OnboardingStep.PHOTO, draft)
            }
            OnboardingStep.PHOTO -> OptionalStep("Add a profile picture", "You can choose or take a photo later.") {
                coordinator.next(OnboardingStep.ZIP, draft)
            }.also {
                Button(coordinator::choosePhoto) { Text("Choose Photo") }
                Button(coordinator::takePhoto) { Text("Take Photo") }
            }
            OnboardingStep.ZIP -> EntryStep("What is your ZIP code?", draft.zipCode, {
                draft =
                    draft.copy(zipCode = it.filter(Char::isDigit).take(5))
            }, draft.zipCode.length == 5) {
                coordinator.next(OnboardingStep.LOCATION, draft)
            }
            OnboardingStep.LOCATION -> OptionalStep(
                "Allow location?",
                "Location can help find nearby services. You can skip this.",
            ) {
                coordinator.next(OnboardingStep.NOTIFICATIONS, draft)
            }
            OnboardingStep.NOTIFICATIONS -> OptionalStep(
                "Allow notifications?",
                "Notifications can share useful updates. You can skip this.",
            ) {
                coordinator.next(OnboardingStep.INTERESTS, draft)
            }
            OnboardingStep.INTERESTS -> {
                Text("What technology would you like help with?")
                Text("Choose any, or skip this step.")
                listOf(
                    "SMARTPHONES" to "Smartphones",
                    "TABLETS" to "Tablets",
                    "COMPUTERS" to "Computers",
                    "WI_FI" to "Wi-Fi and internet",
                    "SMART_HOME" to "Smart home",
                    "ONLINE_SAFETY" to "Online safety",
                ).forEach { (value, label) ->
                    val checked = value in draft.technologyPreferences
                    TextButton(onClick = {
                        val choices = if (checked) {
                            draft.technologyPreferences - value
                        } else {
                            draft.technologyPreferences +
                                value
                        }
                        draft = draft.copy(technologyPreferences = choices)
                    }) {
                        Checkbox(checked, null)
                        Text(label)
                    }
                }
                Button(onClick = { coordinator.finish(draft) }) { Text("Continue") }
                TextButton(onClick = { coordinator.finish(draft.copy(technologyPreferences = emptySet())) }) {
                    Text("Skip for now")
                }
            }
            OnboardingStep.COMPLETE -> SimpleStep(
                "You are all set",
                "Your Digibuddy profile is ready.",
                coordinator::showProfile,
            )
        }
        state.error?.let { Text(it) }
    }
}

@Composable private fun SimpleStep(title: String, detail: String, action: () -> Unit) {
    Text(title)
    Text(detail)
    Button(action, Modifier.fillMaxWidth()) { Text("Continue") }
}

@Composable
private fun EntryStep(title: String, value: String, change: (String) -> Unit, enabled: Boolean, action: () -> Unit) {
    Text(title)
    OutlinedTextField(value, change, modifier = Modifier.fillMaxWidth())
    Button(action, Modifier.fillMaxWidth(), enabled = enabled) { Text("Continue") }
}

@Composable private fun OptionalStep(title: String, detail: String, skip: () -> Unit) {
    Text(title)
    Text(detail)
    TextButton(skip) { Text("Skip for now") }
}

@Composable
private fun ProfileScreen(
    state: ProfileState.Profile,
    coordinator: CustomerProfileCoordinator,
    onSignOut: () -> Unit,
    accountSettings: @Composable () -> Unit,
) {
    val profile = state.value
    val security by coordinator.security.collectAsState()
    var firstName by remember(profile.customerId) { mutableStateOf(profile.firstName) }
    var lastName by remember(profile.customerId) { mutableStateOf(profile.lastName) }
    var zipCode by remember(profile.customerId) { mutableStateOf(profile.zipCode) }
    LaunchedEffect(profile.customerId) { coordinator.loadSecurity() }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        Text(profile.publicDisplayName)
        Text("✓ Verified phone")
        Text(if (profile.verifiedEmail == null) "Email not added" else "✓ Verified email")
        Text("ZIP code: ${profile.zipCode}")
        Section(
            "Profile picture",
            listOf(
                "Choose Photo",
                "Take Photo",
                "Remove Photo",
                "Crop and compress automatically",
                "Retry failed upload",
            ),
        )
        Button(coordinator::choosePhoto) { Text("Choose Photo") }
        Button(coordinator::takePhoto) { Text("Take Photo") }
        TextButton(coordinator::removePhoto) { Text("Remove Photo") }
        TextButton(coordinator::retryPhotoUpload) { Text("Retry failed upload") }
        Section(
            "Account",
            listOf(
                "Edit name",
                "Change ZIP code",
                "Add verified email",
                "Create password",
                "Change password",
                "Change phone number with verification",
                "Manage saved addresses",
            ),
        )
        OutlinedTextField(firstName, { firstName = it }, label = { Text("First name") })
        OutlinedTextField(lastName, { lastName = it }, label = { Text("Last name") })
        Button(onClick = { coordinator.updateName(firstName, lastName) }) { Text("Save name") }
        OutlinedTextField(
            zipCode,
            { zipCode = it.filter(Char::isDigit).take(5) },
            label = { Text("ZIP code") },
        )
        Button(onClick = { coordinator.updateZip(zipCode) }, enabled = zipCode.length == 5) { Text("Save ZIP code") }
        AddressEditor(coordinator)
        accountSettings()
        Section("Security", listOf("Trusted devices", "Recent sign-ins", "Biometric unlock", "Sign out all devices"))
        security?.trustedDevices?.forEach {
            Text("${it.displayName}${if (it.currentDevice) " (this device)" else ""} — last used ${it.lastSeenAt}")
        }
        security?.recentSignIns?.take(5)?.forEach {
            Text("Sign-in ${it.signedInAt}: ${if (it.active) "active" else "signed out"}")
        }
        Section("Privacy", listOf("Permission status", "Blocked users (coming later)", "Privacy information"))
        profile.settings.permissionStatus.forEach { (permission, value) -> Text("$permission: $value") }
        Button(coordinator::requestExport) { Text("Download my data") }
        Section(
            "Accessibility",
            listOf(
                "Follow system text size",
                "Extra-large text",
                "High contrast",
                "Reduced motion",
                "Simplified instructions",
            ),
        )
        AccessibilityEditor(profile, coordinator)
        val notificationPermission = profile.settings.permissionStatus["notifications"] ?: "NOT_REQUESTED"
        TextButton(onClick = {
            coordinator.updateNotifications(!profile.settings.notificationsEnabled, notificationPermission)
        }) {
            Checkbox(profile.settings.notificationsEnabled, null)
            Text("Notifications enabled")
        }
        Section(
            "More",
            listOf(
                "Payment methods (coming later)",
                "Notification settings",
                "Help center (coming later)",
                "Terms",
                "Privacy policy",
                "App version 0.1.0",
            ),
        )
        TextButton(onSignOut) { Text("Sign out") }
        Button(coordinator::requestDeletion) { Text("Delete account") }
        state.message?.let { Text(it) }
    }
}

@Composable
private fun AddressEditor(coordinator: CustomerProfileCoordinator) {
    var label by remember { mutableStateOf("") }
    var line1 by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }
    var region by remember { mutableStateOf("") }
    var zip by remember { mutableStateOf("") }
    Text("Add saved address")
    OutlinedTextField(label, { label = it }, label = { Text("Label, such as Home") })
    OutlinedTextField(line1, { line1 = it }, label = { Text("Street address") })
    OutlinedTextField(city, { city = it }, label = { Text("City") })
    OutlinedTextField(region, { region = it.uppercase().take(2) }, label = { Text("State") })
    OutlinedTextField(zip, { zip = it.filter(Char::isDigit).take(5) }, label = { Text("ZIP code") })
    val complete =
        label.isNotBlank() &&
            line1.isNotBlank() &&
            city.isNotBlank() &&
            region.length == 2 &&
            zip.length == 5
    Button(
        onClick = {
            coordinator.saveAddress(SaveAddressRequest(label, line1, city = city, region = region, zipCode = zip))
        },
        enabled = complete,
    ) {
        Text("Save address")
    }
}

@Composable
private fun AccessibilityEditor(
    profile: com.digibuddy.shared.contracts.CustomerProfileResponse,
    coordinator: CustomerProfileCoordinator,
) {
    var settings by remember(profile.customerId, profile.settings) { mutableStateOf(profile.settings) }
    fun update(request: UpdateAccessibilitySettingsRequest) = coordinator.updateAccessibility(request)
    listOf(
        "Follow system text size" to settings.followSystemTextSize,
        "Extra-large text" to settings.extraLargeText,
        "High contrast" to settings.highContrast,
        "Reduced motion" to settings.reducedMotion,
        "Simplified instructions" to settings.simplifiedInstructions,
    ).forEachIndexed { index, (label, selected) ->
        TextButton(onClick = {
            settings = when (index) {
                0 -> settings.copy(followSystemTextSize = !selected)
                1 -> settings.copy(extraLargeText = !selected)
                2 -> settings.copy(highContrast = !selected)
                3 -> settings.copy(reducedMotion = !selected)
                else -> settings.copy(simplifiedInstructions = !selected)
            }
        }) {
            Checkbox(selected, null)
            Text(label)
        }
    }
    Button(
        onClick = {
            update(
                UpdateAccessibilitySettingsRequest(
                    settings.followSystemTextSize,
                    settings.extraLargeText,
                    settings.highContrast,
                    settings.reducedMotion,
                    settings.simplifiedInstructions,
                ),
            )
        },
    ) {
        Text("Save accessibility settings")
    }
}

@Composable private fun Section(title: String, rows: List<String>) {
    HorizontalDivider()
    Text(title)
    rows.forEach { Text(it, Modifier.padding(vertical = 6.dp)) }
}
