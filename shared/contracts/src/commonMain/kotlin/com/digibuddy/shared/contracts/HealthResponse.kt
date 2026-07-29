package com.digibuddy.shared.contracts

import kotlinx.serialization.Serializable

@Serializable
data class HealthResponse(val status: String, val service: String)
