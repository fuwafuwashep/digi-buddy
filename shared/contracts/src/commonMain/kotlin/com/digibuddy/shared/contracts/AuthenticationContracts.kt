package com.digibuddy.shared.contracts

import kotlinx.serialization.Serializable

@Serializable
data class NormalizePhoneRequest(val phoneNumber: String, val defaultRegion: String = "US")

@Serializable
data class NormalizedPhoneResponse(val e164: String, val display: String)

@Serializable
data class StartPhoneVerificationRequest(val phoneNumber: String, val defaultRegion: String = "US")

@Serializable
data class VerificationChallengeResponse(
    val attemptId: String,
    val maskedDestination: String,
    val expiresInSeconds: Long,
    val resendAfterSeconds: Long,
    val developmentCode: String? = null,
)

@Serializable
data class VerifyPhoneCodeRequest(
    val attemptId: String,
    val code: String,
    val deviceId: String,
    val deviceName: String,
)

@Serializable
data class RefreshTokenRequest(val refreshToken: String)

@Serializable
data class AuthenticationTokensResponse(
    val userId: String,
    val sessionId: String,
    val accessToken: String,
    val accessExpiresInSeconds: Long,
    val refreshToken: String,
    val refreshExpiresInSeconds: Long,
)

@Serializable
data class AddEmailCredentialRequest(val email: String, val password: String)

@Serializable
data class EmailPasswordLoginRequest(val email: String, val password: String)

@Serializable
data class AuthenticationMessageResponse(val message: String)

@Serializable
data class AuthenticationErrorResponse(val code: String, val message: String, val retryAfterSeconds: Long? = null)

@Serializable
data class AuthenticatedUserResponse(val userId: String, val roles: List<String>, val hasEmailCredential: Boolean)
