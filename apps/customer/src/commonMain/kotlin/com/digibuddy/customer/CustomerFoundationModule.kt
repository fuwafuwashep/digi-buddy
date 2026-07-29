package com.digibuddy.customer

import com.digibuddy.shared.networking.AuthenticationApiClient
import com.digibuddy.shared.networking.HealthApiClient
import com.digibuddy.shared.networking.createDigibuddyNetworkClient
import org.koin.dsl.module

val customerFoundationModule =
    module {
        single { createDigibuddyNetworkClient() }
        single { HealthApiClient.forLocalDevelopment(networkClient = get()) }
        single { AuthenticationApiClient.forLocalDevelopment(networkClient = get()) }
    }
