package com.digibuddy.backend.auth

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.basicAuth
import io.ktor.client.request.forms.submitForm
import io.ktor.http.Parameters
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.SecureRandom
import java.time.Instant
import java.util.UUID

data class OtpDelivery(val providerReference: String, val developmentCode: String? = null)

interface OtpProvider {
    suspend fun sendCode(phoneE164: String, attemptId: UUID, expiresAt: Instant): OtpDelivery

    suspend fun verifyCode(phoneE164: String, attemptId: UUID, providerReference: String, code: String): Boolean
}

class DevelopmentOtpProvider(
    private val hasher: SecretHasher,
    private val secureRandom: SecureRandom = SecureRandom(),
) : OtpProvider {
    private val codeHashes = mutableMapOf<UUID, String>()

    override suspend fun sendCode(phoneE164: String, attemptId: UUID, expiresAt: Instant): OtpDelivery {
        val code = secureRandom.nextInt(1_000_000).toString().padStart(6, '0')
        synchronized(codeHashes) {
            codeHashes[attemptId] = hasher.hash(code)
        }
        return OtpDelivery(providerReference = "development:$attemptId", developmentCode = code)
    }

    override suspend fun verifyCode(
        phoneE164: String,
        attemptId: UUID,
        providerReference: String,
        code: String,
    ): Boolean {
        synchronized(codeHashes) {
            val expected = codeHashes[attemptId] ?: return false
            val matches = hasher.matches(code, expected)
            if (matches) {
                codeHashes.remove(attemptId)
            }
            return matches
        }
    }
}

class TwilioVerifyOtpProvider(
    private val accountSid: String,
    private val authToken: String,
    private val verifyServiceSid: String,
    private val client: HttpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    },
) : OtpProvider {
    init {
        require(accountSid.isNotBlank()) { "TWILIO_ACCOUNT_SID is required" }
        require(authToken.isNotBlank()) { "TWILIO_AUTH_TOKEN is required" }
        require(verifyServiceSid.isNotBlank()) { "TWILIO_VERIFY_SERVICE_SID is required" }
    }

    override suspend fun sendCode(phoneE164: String, attemptId: UUID, expiresAt: Instant): OtpDelivery {
        val response = client.submitForm(
            url = "$VERIFY_BASE/$verifyServiceSid/Verifications",
            formParameters = Parameters.build {
                append("To", phoneE164)
                append("Channel", "sms")
            },
        ) {
            basicAuth(accountSid, authToken)
        }.body<TwilioVerificationResponse>()
        return OtpDelivery(providerReference = response.sid)
    }

    override suspend fun verifyCode(
        phoneE164: String,
        attemptId: UUID,
        providerReference: String,
        code: String,
    ): Boolean {
        val response = client.submitForm(
            url = "$VERIFY_BASE/$verifyServiceSid/VerificationCheck",
            formParameters = Parameters.build {
                append("To", phoneE164)
                append("Code", code)
            },
        ) {
            basicAuth(accountSid, authToken)
        }.body<TwilioVerificationResponse>()
        return response.status == "approved"
    }

    companion object {
        private const val VERIFY_BASE = "https://verify.twilio.com/v2/Services"
    }
}

@Serializable
private data class TwilioVerificationResponse(val sid: String, @SerialName("status") val status: String = "pending")
