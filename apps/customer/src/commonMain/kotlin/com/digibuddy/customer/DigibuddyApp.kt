package com.digibuddy.customer

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.digibuddy.customer.generated.resources.Res
import com.digibuddy.customer.generated.resources.digibuddy_logo
import com.digibuddy.shared.authentication.AuthenticationAccountSettingsScreen
import com.digibuddy.shared.authentication.AuthenticationCoordinator
import com.digibuddy.shared.authentication.AuthenticationScreen
import com.digibuddy.shared.authentication.AuthenticationState
import com.digibuddy.shared.designsystem.DigibuddyTheme
import com.digibuddy.shared.networking.BookingApiClient
import com.digibuddy.shared.networking.ChatApiClient
import com.digibuddy.shared.networking.CustomerProfileApiClient
import com.digibuddy.shared.networking.HelperCatalogApiClient
import com.digibuddy.shared.networking.PaymentApiClient
import com.digibuddy.shared.profile.CustomerProfileCoordinator
import com.digibuddy.shared.profile.CustomerProfileExperience
import org.jetbrains.compose.resources.painterResource

@Composable
fun DigibuddyApp(authenticationCoordinator: AuthenticationCoordinator) {
    val authenticationState by authenticationCoordinator.state.collectAsState()
    LaunchedEffect(authenticationCoordinator) {
        authenticationCoordinator.restoreSession()
    }
    DigibuddyTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
        ) {
            val authenticated = authenticationState as? AuthenticationState.Authenticated
            if (authenticated == null) {
                AuthenticationScreen(
                    state = authenticationState,
                    coordinator = authenticationCoordinator,
                    brand = {
                        Image(
                            painter = painterResource(Res.drawable.digibuddy_logo),
                            contentDescription = "Digibuddy logo",
                            modifier = Modifier.fillMaxWidth().heightIn(max = 160.dp),
                            contentScale = ContentScale.Fit,
                        )
                    },
                )
            } else {
                val profileCoordinator = remember(authenticated.accessToken) {
                    CustomerProfileCoordinator(
                        CustomerProfileApiClient.forLocalDevelopment(),
                        authenticated.accessToken,
                    )
                }
                CustomerProfileExperience(
                    profileCoordinator,
                    onSignOut = { authenticationCoordinator.logout() },
                    accountSettings = { AuthenticationAccountSettingsScreen(authenticationCoordinator) },
                    profileContent = { profileState ->
                        val marketplace = remember(authenticated.accessToken, profileState.value.zipCode) {
                            MarketplaceCoordinator(
                                HelperCatalogApiClient.forLocalDevelopment(),
                                authenticated.accessToken,
                                profileState.value.zipCode,
                            )
                        }
                        val bookings = remember(authenticated.accessToken) {
                            BookingCoordinator(
                                BookingApiClient.forLocalDevelopment(),
                                PaymentApiClient.forLocalDevelopment(),
                                authenticated.accessToken,
                            )
                        }
                        val chats = remember(authenticated.accessToken) {
                            ChatCoordinator(ChatApiClient.forLocalDevelopment(), authenticated.accessToken)
                        }
                        CustomerAppShell(
                            profileState.value,
                            profileCoordinator,
                            marketplace,
                            bookings,
                            chats,
                            onSignOut = { authenticationCoordinator.logout() },
                            accountSettings = { AuthenticationAccountSettingsScreen(authenticationCoordinator) },
                        )
                    },
                )
            }
        }
    }
}
