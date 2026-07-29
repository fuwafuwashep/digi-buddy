package com.digibuddy.helpers

import com.digibuddy.shared.networking.AuthenticationApiClient
import com.digibuddy.shared.networking.HelperAccountApiClient
import com.digibuddy.shared.networking.createDigibuddyNetworkClient
import org.koin.dsl.module

val helperFoundationModule = module {
    single { createDigibuddyNetworkClient() }
    single { AuthenticationApiClient.forLocalDevelopment(networkClient = get()) }
    single { HelperAccountApiClient.forLocalDevelopment(network = get()) }
}
