package com.digibuddy.helpers

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.digibuddy.helpers.generated.resources.Res
import com.digibuddy.helpers.generated.resources.digibuddy_helpers_logo
import com.digibuddy.shared.authentication.AuthenticationCoordinator
import com.digibuddy.shared.authentication.AuthenticationScreen
import com.digibuddy.shared.authentication.AuthenticationState
import com.digibuddy.shared.designsystem.DigibuddyColors
import com.digibuddy.shared.designsystem.DigibuddyTheme
import com.digibuddy.shared.helper.dashboard.HelperDashboardCoordinator
import com.digibuddy.shared.helper.dashboard.HelperDashboardShell
import com.digibuddy.shared.helper.onboarding.HelperApplicationCoordinator
import com.digibuddy.shared.helper.onboarding.HelperDestination
import com.digibuddy.shared.helper.onboarding.HelperLifecycleScreen
import com.digibuddy.shared.helper.onboarding.HelperOnboardingScreen
import com.digibuddy.shared.helper.onboarding.HelperStartupCoordinator
import com.digibuddy.shared.helper.onboarding.destination
import com.digibuddy.shared.networking.BookingApiClient
import com.digibuddy.shared.networking.ChatApiClient
import com.digibuddy.shared.networking.HelperAccountApiClient
import org.jetbrains.compose.resources.painterResource

@Composable
fun DigibuddyHelpersApp(
    authenticationCoordinator: AuthenticationCoordinator,
    allowDevelopmentWorkspacePreview: Boolean = false,
) {
    val authenticationState by authenticationCoordinator.state.collectAsState()
    LaunchedEffect(authenticationCoordinator) { authenticationCoordinator.restoreSession() }

    DigibuddyTheme {
        Surface(Modifier.fillMaxSize()) {
            val authenticated = authenticationState as? AuthenticationState.Authenticated
            if (authenticated == null) {
                AuthenticationScreen(
                    state = authenticationState,
                    coordinator = authenticationCoordinator,
                    brand = { HelperLoginBrand() },
                )
            } else {
                HelperAuthenticatedRouter(
                    accessToken = authenticated.accessToken,
                    onSignOut = { authenticationCoordinator.logout() },
                    allowDevelopmentWorkspacePreview = allowDevelopmentWorkspacePreview,
                )
            }
        }
    }
}

@Composable
private fun HelperAuthenticatedRouter(
    accessToken: String,
    onSignOut: () -> Unit,
    allowDevelopmentWorkspacePreview: Boolean,
) {
    val coordinator = remember(accessToken) {
        HelperStartupCoordinator(HelperAccountApiClient.forLocalDevelopment(), accessToken)
    }
    val startup by coordinator.state.collectAsState()
    LaunchedEffect(coordinator) { coordinator.load() }
    DisposableEffect(coordinator) { onDispose(coordinator::close) }

    val approved = startup.response?.destination() == HelperDestination.APPROVED_APP
    if (approved) {
        val dashboard = remember(accessToken) {
            HelperDashboardCoordinator(
                BookingApiClient.forLocalDevelopment(),
                ChatApiClient.forHelperLocalDevelopment(),
                HelperAccountApiClient.forLocalDevelopment(),
                accessToken,
            )
        }
        val photoPicker = rememberHelperPhotoPicker()
        HelperDashboardShell(
            startup.response?.displayName,
            dashboard,
            onChoosePhoto = {
                photoPicker.choose(dashboard::uploadPhoto, dashboard::reportError)
            },
            onSignOut = onSignOut,
        )
    } else if (startup.response?.destination() in setOf(
            HelperDestination.ONBOARDING,
            HelperDestination.CHANGES_REQUESTED,
        )
    ) {
        HelperApplicationRoute(
            accessToken = accessToken,
            onStatusChanged = coordinator::load,
            onSignOut = onSignOut,
        )
    } else {
        HelperLifecycleScreen(
            state = startup,
            onRetry = coordinator::load,
            onSignOut = onSignOut,
            onApproveForLocalTesting = if (
                allowDevelopmentWorkspacePreview &&
                startup.response?.destination() == HelperDestination.UNDER_REVIEW
            ) {
                coordinator::approveForLocalTesting
            } else {
                null
            },
            onResume = if (startup.response?.destination() == HelperDestination.PAUSED) coordinator::resume else null,
        )
    }
}

@Composable
private fun HelperApplicationRoute(accessToken: String, onStatusChanged: () -> Unit, onSignOut: () -> Unit) {
    val coordinator = remember(accessToken) {
        HelperApplicationCoordinator(HelperAccountApiClient.forLocalDevelopment(), accessToken)
    }
    val state by coordinator.state.collectAsState()
    val photoPicker = rememberHelperPhotoPicker()
    LaunchedEffect(coordinator) { coordinator.load() }
    LaunchedEffect(state.application?.status) {
        if (state.application?.status?.name == "UNDER_REVIEW") onStatusChanged()
    }
    DisposableEffect(coordinator) { onDispose(coordinator::close) }
    HelperOnboardingScreen(
        state = state,
        onSaveStep = coordinator::saveStep,
        onSubmit = coordinator::submit,
        onRetry = coordinator::load,
        onSignOut = onSignOut,
        onChooseProfilePhoto = {
            photoPicker.choose(
                onSelected = { coordinator.uploadPhoto(it.fileName, it.contentType, it.bytes) },
                onError = coordinator::reportError,
            )
        },
    )
}

@Composable
private fun HelperLoginBrand() {
    Column(
        modifier = Modifier.fillMaxWidth().background(
            Brush.verticalGradient(listOf(DigibuddyColors.Navy, DigibuddyColors.DeepTeal)),
            MaterialTheme.shapes.large,
        ).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(Res.drawable.digibuddy_helpers_logo),
            contentDescription = "Digibuddy Helpers logo",
            modifier = Modifier.fillMaxWidth().heightIn(max = 120.dp),
            contentScale = ContentScale.Fit,
        )
        Spacer(Modifier.height(8.dp))
        Text("HELPER WORKSPACE", color = DigibuddyColors.Gold, style = MaterialTheme.typography.labelLarge)
        Text(
            "Digibuddy Helpers",
            color = androidx.compose.ui.graphics.Color.White,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "Manage requests and help people feel confident with technology.",
            color = androidx.compose.ui.graphics.Color.White.copy(alpha = .84f),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
