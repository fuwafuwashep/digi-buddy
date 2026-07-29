package com.digibuddy.shared.helper.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.digibuddy.shared.contracts.HelperAccountStatus
import com.digibuddy.shared.designsystem.DigibuddyCard
import com.digibuddy.shared.designsystem.DigibuddyColors
import com.digibuddy.shared.designsystem.StatusPill

@Composable
fun HelperLifecycleScreen(
    state: HelperStartupUiState,
    onRetry: () -> Unit,
    onSignOut: () -> Unit,
    onApproveForLocalTesting: (() -> Unit)? = null,
    onResume: (() -> Unit)? = null,
) {
    if (state.loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(Modifier.height(16.dp))
                Text("Opening your helper workspace…")
            }
        }
        return
    }
    val response = state.response
    if (response == null) {
        MessageLayout(
            eyebrow = "DIGIBUDDY HELPERS",
            title = "We could not open your workspace",
            message = state.errorMessage ?: "Please try again.",
            status = "Connection problem",
            primaryAction = "Try again",
            onPrimaryAction = onRetry,
            onSignOut = onSignOut,
        )
        return
    }

    when (response.resolvedHelperStatus()) {
        HelperAccountStatus.PROFILE_INCOMPLETE,
        HelperAccountStatus.IDENTITY_INFORMATION_REQUIRED,
        HelperAccountStatus.PAYMENT_ONBOARDING_REQUIRED,
        -> MessageLayout(
            eyebrow = "BECOME A DIGIBUDDY HELPER",
            title = "Share your skills with your community",
            message = response.message,
            status = if (response.hasHelperRole) "Application started" else "Ready to get started",
            primaryAction = "Application setup coming next",
            onPrimaryAction = {},
            primaryEnabled = false,
            onSignOut = onSignOut,
            details = listOf(
                "Tell us which technology you can help with",
                "Choose where and when you want to work",
                "Complete identity and safety review",
            ),
            onApproveForLocalTesting = null,
        )
        HelperAccountStatus.UNDER_REVIEW -> MessageLayout(
            eyebrow = "APPLICATION STATUS",
            title = "Your application is under review",
            message = state.errorMessage ?: response.message,
            status = "Under review",
            primaryAction = "Refresh status",
            onPrimaryAction = onRetry,
            onSignOut = onSignOut,
            details = listOf(
                "We will keep your progress safe",
                "You will receive an update when review is complete",
                "No action is needed right now",
            ),
            onApproveForLocalTesting = onApproveForLocalTesting,
        )
        HelperAccountStatus.CHANGES_REQUESTED -> MessageLayout(
            eyebrow = "APPLICATION UPDATE",
            title = "A few details need your attention",
            message = response.message,
            status = "Changes requested",
            primaryAction = "Review requested changes",
            onPrimaryAction = {},
            primaryEnabled = false,
            onSignOut = onSignOut,
            details = response.requestedChanges.ifEmpty { listOf("Review your application details") },
            onApproveForLocalTesting = null,
        )
        HelperAccountStatus.PAUSED_BY_HELPER -> MessageLayout(
            eyebrow = "WORK STATUS",
            title = "New work is paused",
            message = response.message,
            status = "Paused",
            primaryAction = "Resume helper account",
            onPrimaryAction = onResume ?: {},
            primaryEnabled = onResume != null,
            onSignOut = onSignOut,
            details = listOf(
                "Your saved profile is still here",
                "You will not receive new paid requests while paused",
            ),
        )
        HelperAccountStatus.SUSPENDED -> MessageLayout(
            eyebrow = "ACCOUNT STATUS",
            title = "Your helper account is unavailable",
            message = "Contact Digibuddy support if you think this is a mistake.",
            status = "Suspended",
            primaryAction = "Contact support",
            onPrimaryAction = {},
            primaryEnabled = false,
            onSignOut = onSignOut,
        )
        HelperAccountStatus.REJECTED -> MessageLayout(
            eyebrow = "APPLICATION STATUS",
            title = "We could not approve your application",
            message = response.message,
            status = "Not approved",
            primaryAction = "Contact support",
            onPrimaryAction = {},
            primaryEnabled = false,
            onSignOut = onSignOut,
            details = listOf(
                "Your private information remains protected",
                "Digibuddy support can explain available next steps",
                "Digibuddy is not an emergency service",
            ),
        )
        HelperAccountStatus.APPROVED -> Unit
    }
}

@Composable
private fun MessageLayout(
    eyebrow: String,
    title: String,
    message: String,
    status: String,
    primaryAction: String,
    onPrimaryAction: () -> Unit,
    onSignOut: () -> Unit,
    primaryEnabled: Boolean = true,
    details: List<String> = emptyList(),
    onApproveForLocalTesting: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(eyebrow, color = DigibuddyColors.Gold, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(8.dp))
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Text(message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(20.dp))
        DigibuddyCard {
            StatusPill(status, if (status == "Suspended") MaterialTheme.colorScheme.error else DigibuddyColors.Navy)
            details.forEach { detail ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(
                        Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(detail, modifier = Modifier.weight(1f))
                }
            }
            Button(
                onClick = onPrimaryAction,
                modifier = Modifier.fillMaxWidth(),
                enabled = primaryEnabled,
            ) {
                Text(primaryAction)
            }
            onApproveForLocalTesting?.let { approve ->
                OutlinedButton(onClick = approve, modifier = Modifier.fillMaxWidth()) {
                    Text("Approve for local testing")
                }
                Text(
                    "Development only. This grants local approval and makes this helper searchable " +
                        "until the backend is restarted.",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        TextButton(onClick = onSignOut, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Sign out")
        }
    }
}
