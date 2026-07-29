package com.digibuddy.shared.networking

import com.digibuddy.shared.contracts.HealthResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.request.get
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

expect fun localDevelopmentApiBaseUrl(): String

class DigibuddyNetworkClient internal constructor(internal val httpClient: HttpClient) {
    fun close() {
        httpClient.close()
    }
}

fun createDigibuddyNetworkClient(): DigibuddyNetworkClient = DigibuddyNetworkClient(
    HttpClient {
        expectSuccess = true
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    explicitNulls = false
                },
            )
        }
        install(WebSockets)
    },
)

class HealthApiClient(private val networkClient: DigibuddyNetworkClient, baseUrl: String) {
    private val normalizedBaseUrl = baseUrl.trimEnd('/')

    suspend fun getHealth(): HealthResponse = networkClient.httpClient.get("$normalizedBaseUrl/health").body()

    companion object {
        fun forLocalDevelopment(
            networkClient: DigibuddyNetworkClient = createDigibuddyNetworkClient(),
        ): HealthApiClient = HealthApiClient(
            networkClient = networkClient,
            baseUrl = localDevelopmentApiBaseUrl(),
        )
    }
}
