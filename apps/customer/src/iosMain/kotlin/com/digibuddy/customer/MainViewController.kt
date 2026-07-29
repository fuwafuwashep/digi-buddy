package com.digibuddy.customer

import androidx.compose.ui.window.ComposeUIViewController
import com.digibuddy.shared.authentication.AuthenticationCoordinator
import com.digibuddy.shared.authentication.IosKeychainRefreshTokenStore
import com.digibuddy.shared.networking.AuthenticationApiClient
import com.digibuddy.shared.networking.createDigibuddyNetworkClient
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSUUID
import platform.UIKit.UIDevice

fun mainViewController(): platform.UIKit.UIViewController {
    val coordinator = AuthenticationCoordinator(
        api = AuthenticationApiClient.forLocalDevelopment(createDigibuddyNetworkClient()),
        refreshTokenStore = IosKeychainRefreshTokenStore(),
        deviceId = UIDevice.currentDevice.identifierForVendor?.UUIDString ?: NSUUID().UUIDString,
        deviceName = NSProcessInfo.processInfo.hostName,
    )
    return ComposeUIViewController { DigibuddyApp(coordinator) }
}
