package com.digibuddy.shared.networking

import com.digibuddy.shared.contracts.AuthenticationErrorResponse
import com.digibuddy.shared.contracts.HelperAccountStatus
import com.digibuddy.shared.contracts.HelperApplicationResponse
import com.digibuddy.shared.contracts.HelperApplicationStepRequest
import com.digibuddy.shared.contracts.HelperOnboardingStep
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class HelperAccountApiClientTest {
    @Test
    fun serializesApplicationStepAsJson() = runTest {
        val responseBody =
            Json.encodeToString(
                HelperApplicationResponse(
                    applicationId = "application-1",
                    status = HelperAccountStatus.PROFILE_INCOMPLETE,
                    currentStep = HelperOnboardingStep.PUBLIC_PROFILE,
                    completedSteps = listOf(HelperOnboardingStep.LEGAL_NAME),
                    steps = emptyList(),
                    requirements = emptyList(),
                    progressPercent = 7,
                    canSubmit = false,
                    canReceivePaidWork = false,
                    message = "Legal name saved",
                ),
            )
        val engine =
            MockEngine { request ->
                assertEquals("/api/v1/helper/application/steps/LEGAL_NAME", request.url.encodedPath)
                assertEquals(ContentType.Application.Json, request.body.contentType)
                respond(
                    content = responseBody,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
            }
        val client =
            HttpClient(engine) {
                install(ContentNegotiation) {
                    json(Json { ignoreUnknownKeys = true })
                }
            }

        try {
            val response =
                HelperAccountApiClient(
                    network = DigibuddyNetworkClient(client),
                    baseUrl = "http://localhost:8080",
                ).saveStep(
                    accessToken = "test-access-token",
                    step = HelperOnboardingStep.LEGAL_NAME,
                    request = HelperApplicationStepRequest(values = mapOf("legalFirstName" to "Jamie")),
                )

            assertEquals("application-1", response.applicationId)
            assertEquals(listOf(HelperOnboardingStep.LEGAL_NAME), response.completedSteps)
        } finally {
            client.close()
        }
    }

    @Test
    fun returnsFriendlyBackendValidationMessageInsteadOfRawHttpException() = runTest {
        val engine = MockEngine {
            respond(
                content = Json.encodeToString(
                    AuthenticationErrorResponse(
                        code = "INVALID_HELPER_FIELD",
                        message = "Complete the required information before continuing.",
                    ),
                ),
                status = HttpStatusCode.BadRequest,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val httpClient = HttpClient(engine) {
            expectSuccess = true
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

        try {
            val error = assertFailsWith<DigibuddyApiException> {
                HelperAccountApiClient(
                    DigibuddyNetworkClient(httpClient),
                    "http://localhost:8080",
                ).saveStep(
                    "test-token",
                    HelperOnboardingStep.PUBLIC_PROFILE,
                    HelperApplicationStepRequest(),
                )
            }

            assertEquals("INVALID_HELPER_FIELD", error.code)
            assertEquals("Complete the required information before continuing.", error.message)
        } finally {
            httpClient.close()
        }
    }
}
