package com.digibuddy.shared.networking

import com.digibuddy.shared.contracts.AddEmailCredentialRequest
import com.digibuddy.shared.contracts.AuthenticatedUserResponse
import com.digibuddy.shared.contracts.AuthenticationErrorResponse
import com.digibuddy.shared.contracts.AuthenticationMessageResponse
import com.digibuddy.shared.contracts.AuthenticationTokensResponse
import com.digibuddy.shared.contracts.EmailPasswordLoginRequest
import com.digibuddy.shared.contracts.NormalizePhoneRequest
import com.digibuddy.shared.contracts.NormalizedPhoneResponse
import com.digibuddy.shared.contracts.RefreshTokenRequest
import com.digibuddy.shared.contracts.StartPhoneVerificationRequest
import com.digibuddy.shared.contracts.VerificationChallengeResponse
import com.digibuddy.shared.contracts.VerifyPhoneCodeRequest
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

class AuthenticationApiClient(private val networkClient: DigibuddyNetworkClient, baseUrl: String) {
    private val authUrl = "${baseUrl.trimEnd('/')}/api/v1/auth"

    suspend fun normalizePhone(phoneNumber: String, defaultRegion: String = "US"): NormalizedPhoneResponse = execute {
        networkClient.httpClient.post("$authUrl/phone/normalize") {
            jsonBody(NormalizePhoneRequest(phoneNumber, defaultRegion))
        }.body()
    }

    suspend fun startPhoneVerification(
        phoneNumber: String,
        defaultRegion: String = "US",
    ): VerificationChallengeResponse = execute {
        networkClient.httpClient.post("$authUrl/phone/verifications") {
            jsonBody(StartPhoneVerificationRequest(phoneNumber, defaultRegion))
        }.body()
    }

    suspend fun resend(attemptId: String): VerificationChallengeResponse = execute {
        networkClient.httpClient.post("$authUrl/phone/verifications/$attemptId/resend").body()
    }

    suspend fun verifyPhoneCode(request: VerifyPhoneCodeRequest): AuthenticationTokensResponse = execute {
        networkClient.httpClient.post("$authUrl/phone/verify") { jsonBody(request) }.body()
    }

    suspend fun startEmailPasswordLogin(email: String, password: String): VerificationChallengeResponse = execute {
        networkClient.httpClient.post("$authUrl/email/login") {
            jsonBody(EmailPasswordLoginRequest(email, password))
        }.body()
    }

    suspend fun verifyEmailSecondFactor(request: VerifyPhoneCodeRequest): AuthenticationTokensResponse = execute {
        networkClient.httpClient.post("$authUrl/email/verify") { jsonBody(request) }.body()
    }

    suspend fun refresh(refreshToken: String): AuthenticationTokensResponse = execute {
        networkClient.httpClient.post("$authUrl/refresh") { jsonBody(RefreshTokenRequest(refreshToken)) }.body()
    }

    suspend fun me(accessToken: String): AuthenticatedUserResponse = execute {
        networkClient.httpClient.get("$authUrl/me") { bearerAuth(accessToken) }.body()
    }

    suspend fun addEmailCredential(
        accessToken: String,
        email: String,
        password: String,
    ): AuthenticationMessageResponse = execute {
        networkClient.httpClient.put("$authUrl/email-credential") {
            bearerAuth(accessToken)
            jsonBody(AddEmailCredentialRequest(email, password))
        }.body()
    }

    suspend fun logout(accessToken: String): AuthenticationMessageResponse = execute {
        networkClient.httpClient.post("$authUrl/logout") { bearerAuth(accessToken) }.body()
    }

    suspend fun logoutAll(accessToken: String): AuthenticationMessageResponse = execute {
        networkClient.httpClient.post("$authUrl/logout-all") { bearerAuth(accessToken) }.body()
    }

    private suspend fun <T> execute(block: suspend () -> T): T = try {
        block()
    } catch (cause: io.ktor.client.plugins.ResponseException) {
        val error = runCatching { cause.response.body<AuthenticationErrorResponse>() }.getOrNull()
        throw AuthenticationApiException(
            code = error?.code ?: "NETWORK_ERROR",
            message = error?.message ?: "The request could not be completed.",
            retryAfterSeconds = error?.retryAfterSeconds,
            cause = cause,
        )
    }

    private inline fun <reified T : Any> io.ktor.client.request.HttpRequestBuilder.jsonBody(body: T) {
        contentType(ContentType.Application.Json)
        setBody(body)
    }

    companion object {
        fun forLocalDevelopment(networkClient: DigibuddyNetworkClient = createDigibuddyNetworkClient()) =
            AuthenticationApiClient(networkClient, localDevelopmentApiBaseUrl())
    }
}

class AuthenticationApiException(
    val code: String,
    override val message: String,
    val retryAfterSeconds: Long? = null,
    cause: Throwable? = null,
) : Exception(message, cause)
