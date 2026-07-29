package com.digibuddy.shared.networking

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class HealthApiClientTest {
    @Test
    fun decodesHealthResponse() = runTest {
        val engine =
            MockEngine { request ->
                assertEquals("/health", request.url.encodedPath)
                respond(
                    content = """{"status":"ok","service":"digibuddy-backend"}""",
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
            }
        val client =
            HttpClient(engine) {
                install(ContentNegotiation) {
                    json(Json)
                }
            }

        try {
            val response =
                HealthApiClient(
                    networkClient = DigibuddyNetworkClient(client),
                    baseUrl = "http://localhost:8080/",
                ).getHealth()
            assertEquals("ok", response.status)
            assertEquals("digibuddy-backend", response.service)
        } finally {
            client.close()
        }
    }
}
