package com.digibuddy.customer.desktop

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.digibuddy.customer.DigibuddyApp
import com.digibuddy.shared.authentication.AuthenticationCoordinator
import com.digibuddy.shared.authentication.InMemoryRefreshTokenStore
import com.digibuddy.shared.networking.AuthenticationApiClient
import com.digibuddy.shared.networking.createDigibuddyNetworkClient
import java.util.UUID

fun main() = application {
    val authenticationCoordinator = remember {
        AuthenticationCoordinator(
            api = AuthenticationApiClient.forLocalDevelopment(createDigibuddyNetworkClient()),
            refreshTokenStore = InMemoryRefreshTokenStore(),
            deviceId = UUID.randomUUID().toString(),
            deviceName = "Windows development preview",
        )
    }
    DisposableEffect(authenticationCoordinator) {
        onDispose(authenticationCoordinator::close)
    }

    Window(
        onCloseRequest = ::exitApplication,
        title = "Digibuddy — Development Preview",
        state = rememberWindowState(width = 430.dp, height = 820.dp),
    ) {
        DigibuddyApp(authenticationCoordinator)
    }
}
