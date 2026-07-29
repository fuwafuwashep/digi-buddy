package com.digibuddy.shared.contracts

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class HealthResponseTest {
    @Test
    fun healthResponseRoundTripsThroughJson() {
        val expected = HealthResponse(status = "ok", service = "digibuddy-backend")
        val encoded = Json.encodeToString(expected)

        assertEquals(expected, Json.decodeFromString<HealthResponse>(encoded))
    }
}
