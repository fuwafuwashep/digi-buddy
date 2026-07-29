package com.digibuddy.customer.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.digibuddy.customer.DigibuddyApp
import com.digibuddy.shared.authentication.AndroidKeystoreRefreshTokenStore
import com.digibuddy.shared.authentication.AuthenticationCoordinator
import com.digibuddy.shared.networking.AuthenticationApiClient
import com.digibuddy.shared.networking.createDigibuddyNetworkClient
import java.util.UUID

class MainActivity : ComponentActivity() {
    private val authenticationCoordinator by lazy {
        val preferences = getSharedPreferences("digibuddy_device", MODE_PRIVATE)
        val deviceId = preferences.getString("device_id", null) ?: UUID.randomUUID().toString().also {
            preferences.edit().putString("device_id", it).apply()
        }
        AuthenticationCoordinator(
            api = AuthenticationApiClient.forLocalDevelopment(createDigibuddyNetworkClient()),
            refreshTokenStore = AndroidKeystoreRefreshTokenStore(this),
            deviceId = deviceId,
            deviceName = "Android customer app",
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DigibuddyApp(authenticationCoordinator)
        }
    }

    override fun onDestroy() {
        authenticationCoordinator.close()
        super.onDestroy()
    }
}
