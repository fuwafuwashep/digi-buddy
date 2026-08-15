package com.digibuddy.shared.networking

import com.digibuddy.shared.contracts.AuthenticationErrorResponse
import com.digibuddy.shared.contracts.HelperApplicationResponse
import com.digibuddy.shared.contracts.StaffHelperApplicationListResponse
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post

class StaffApiClient(
    private val network:
    DigibuddyNetworkClient,
    baseUrl: String,
) {
    private val applicationsUrl =
        "${baseUrl.trimEnd('/')}" +
            "/api/v1/staff/helper-applications"

    suspend fun pendingApplications(
        accessToken: String,
    ): StaffHelperApplicationListResponse =
        execute {
            network.httpClient
                .get(
                    "$applicationsUrl/under-review",
                ) {
                    bearerAuth(accessToken)
                }
                .body()
        }

    suspend fun approve(
        accessToken: String,
        userId: String,
    ): HelperApplicationResponse =
        execute {
            network.httpClient
                .post(
                    "$applicationsUrl/$userId/approve",
                ) {
                    bearerAuth(accessToken)
                }
                .body()
        }

    private suspend fun <T> execute(
        block: suspend () -> T,
    ): T =
        try {
            block()
        } catch (
            cause:
            io.ktor.client.plugins
            .ResponseException
        ) {
            val error =
                runCatching {
                    cause.response
                        .body<
                            AuthenticationErrorResponse
                            >()
                }.getOrNull()

            throw DigibuddyApiException(
                code =
                    error?.code
                        ?: "NETWORK_ERROR",
                message =
                    error?.message
                        ?: "The request could not be completed.",
                cause = cause,
            )
        }

    companion object {
        fun forLocalDevelopment(
            network:
            DigibuddyNetworkClient =
                createDigibuddyNetworkClient(),
        ) =
            StaffApiClient(
                network,
                localDevelopmentApiBaseUrl(),
            )
    }
}
