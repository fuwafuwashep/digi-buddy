package com.digibuddy.shared.authentication

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun AuthenticationScreen(
    state: AuthenticationState,
    coordinator: AuthenticationCoordinator,
    modifier: Modifier = Modifier,
    brand: @Composable () -> Unit = {
        Text("Digibuddy", style = MaterialTheme.typography.headlineLarge)
    },
) {
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 40.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        brand()
        Spacer(Modifier.height(24.dp))
        when (state) {
            AuthenticationState.EnterPhone -> EnterPhoneScreen(coordinator)
            is AuthenticationState.ConfirmPhone -> ConfirmPhoneScreen(state, coordinator)
            is AuthenticationState.EnterCode -> VerificationCodeScreen(state, coordinator)
            is AuthenticationState.EmailPassword -> EmailPasswordScreen(state, coordinator)
            is AuthenticationState.ExpiredCode -> MessageScreen(
                title = "Code expired",
                message = "Request a new code to continue.",
                action = "Send a new code",
                onAction = coordinator::resendCode,
            )
            is AuthenticationState.RateLimited -> MessageScreen(
                title = "Please wait",
                message = retryMessage(state.retryAfterSeconds),
                action = "Back",
                onAction = coordinator::backToPhone,
            )
            is AuthenticationState.TemporarilyLocked -> MessageScreen(
                title = "Temporarily locked",
                message = retryMessage(state.retryAfterSeconds),
                action = "Back",
                onAction = coordinator::backToPhone,
            )
            is AuthenticationState.NetworkFailure -> MessageScreen(
                title = "Connection problem",
                message = buildString {
                    append("Check your connection. Your information is still here.")

                    state.details?.let { details ->
                        append("\n\nDebug information:\n")
                        append(details)
                    }
                },
                action = "Try again",
                onAction = coordinator::retryAfterNetworkFailure,
            )
            is AuthenticationState.Authenticated -> AuthenticationAccountSettingsScreen(coordinator)
        }
    }
}

@Composable
private fun EnterPhoneScreen(coordinator: AuthenticationCoordinator) {
    var phone by remember { mutableStateOf("") }
    Text("Enter your phone number", style = MaterialTheme.typography.headlineSmall)
    Text("We will text you a one-time code.")
    Spacer(Modifier.height(16.dp))
    OutlinedTextField(
        value = phone,
        onValueChange = { phone = it },
        label = { Text("Phone number") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        modifier = Modifier.fillMaxWidth(),
    )
    Button(
        onClick = { coordinator.normalizePhone(phone) },
        enabled = phone.isNotBlank(),
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Continue") }
    TextButton(onClick = coordinator::showEmailPassword) { Text("Sign in with email and password") }
}

@Composable
private fun ConfirmPhoneScreen(state: AuthenticationState.ConfirmPhone, coordinator: AuthenticationCoordinator) {
    Text("Is this number right?", style = MaterialTheme.typography.headlineSmall)
    Text(state.phone.display, style = MaterialTheme.typography.titleLarge)
    Text("Digibuddy will send a verification code to this number.")
    Spacer(Modifier.height(16.dp))
    Button(onClick = { coordinator.confirmPhone(state.phone) }, modifier = Modifier.fillMaxWidth()) {
        Text("Send code")
    }
    TextButton(onClick = coordinator::backToPhone) { Text("Change number") }
}

@Composable
private fun VerificationCodeScreen(state: AuthenticationState.EnterCode, coordinator: AuthenticationCoordinator) {
    var code by remember(state.challenge.attemptId) { mutableStateOf("") }
    var resendSeconds by remember(state.challenge.attemptId) {
        mutableIntStateOf(state.challenge.resendAfterSeconds.toInt())
    }
    LaunchedEffect(state.challenge.attemptId) {
        while (resendSeconds > 0) {
            delay(1_000)
            resendSeconds--
        }
    }
    Text(
        if (state.secondFactor) "Two-step verification" else "Enter verification code",
        style = MaterialTheme.typography.headlineSmall,
    )
    Text("Code sent to ${state.challenge.maskedDestination}")
    state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    state.challenge.developmentCode?.let {
        Text("Local development code: $it", color = MaterialTheme.colorScheme.primary)
    }
    Spacer(Modifier.height(16.dp))
    OutlinedTextField(
        value = code,
        onValueChange = { value -> code = value.filter(Char::isDigit).take(6) },
        label = { Text("6-digit code") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier.fillMaxWidth().semantics { contentType = ContentType.SmsOtpCode },
    )
    Button(
        onClick = { coordinator.submitCode(code) },
        enabled = code.length == 6,
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Verify") }
    TextButton(onClick = coordinator::resendCode, enabled = resendSeconds == 0) {
        Text(if (resendSeconds > 0) "Resend in ${resendSeconds}s" else "Resend code")
    }
}

@Composable
private fun EmailPasswordScreen(state: AuthenticationState.EmailPassword, coordinator: AuthenticationCoordinator) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    Text("Sign in with email", style = MaterialTheme.typography.headlineSmall)
    Text("A phone code is always required as the second step.")
    state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    OutlinedTextField(
        value = email,
        onValueChange = { email = it },
        label = { Text("Email") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = password,
        onValueChange = { password = it },
        label = { Text("Password") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
    )
    Button(
        onClick = { coordinator.startEmailPasswordLogin(email, password) },
        enabled = email.isNotBlank() && password.isNotBlank(),
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Continue") }
    TextButton(onClick = coordinator::backToPhone) { Text("Use phone instead") }
}

@Composable
fun AuthenticationAccountSettingsScreen(coordinator: AuthenticationCoordinator) {
    var addingEmail by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    Text("You are signed in", style = MaterialTheme.typography.headlineSmall)
    if (addingEmail) {
        Text("Add email sign-in", style = MaterialTheme.typography.titleLarge)
        Text("Phone verification will still be required for every email sign-in.")
        OutlinedTextField(email, { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            password,
            { password = it },
            label = { Text("Password (12+ characters)") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = { coordinator.addEmailCredential(email, password) { message = it } },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Save email sign-in") }
        message?.let { Text(it) }
    } else {
        Button(onClick = { addingEmail = true }, modifier = Modifier.fillMaxWidth()) { Text("Add email sign-in") }
    }
    TextButton(onClick = { coordinator.logout(false) }) { Text("Sign out this device") }
    TextButton(onClick = { coordinator.logout(true) }) { Text("Sign out all devices") }
}

@Composable
private fun MessageScreen(title: String, message: String, action: String, onAction: () -> Unit) {
    Text(title, style = MaterialTheme.typography.headlineSmall)
    Text(message)
    Button(onClick = onAction, modifier = Modifier.fillMaxWidth()) { Text(action) }
}

private fun retryMessage(seconds: Long?): String =
    if (seconds == null) "Try again later." else "Try again in about ${seconds.coerceAtLeast(1)} seconds."
