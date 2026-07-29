package com.digibuddy.backend.auth

import java.time.Clock
import java.time.Instant
import java.util.UUID

enum class VerificationPurpose {
    PHONE_PRIMARY,
    EMAIL_SECOND_FACTOR,
}

data class UserIdentity(
    val id: UUID,
    val phoneE164: String,
    val phoneFingerprint: String,
    val roles: Set<String> = setOf("CUSTOMER"),
    val createdAt: Instant,
    val accountStatus: String = "ACTIVE",
)

data class TrustedDeviceRecord(
    val userId: UUID,
    val deviceId: String,
    val displayName: String,
    val createdAt: Instant,
    val lastSeenAt: Instant,
)

data class AccountProfileIdentity(
    val userId: UUID,
    val phoneE164: String,
    val verifiedEmail: String?,
    val createdAt: Instant,
    val accountStatus: String,
)

data class VerificationAttempt(
    val id: UUID,
    val phoneE164: String,
    val phoneFingerprint: String,
    val ipFingerprint: String,
    val purpose: VerificationPurpose,
    val userId: UUID?,
    val providerReference: String,
    val createdAt: Instant,
    val expiresAt: Instant,
    val resendAvailableAt: Instant,
    val failedAttempts: Int = 0,
    val lockedUntil: Instant? = null,
    val verifiedAt: Instant? = null,
)

data class EmailCredential(val userId: UUID, val emailNormalized: String, val passwordHash: String)

data class RefreshSession(
    val id: UUID,
    val userId: UUID,
    val deviceId: String,
    val accessTokenHash: String,
    val accessExpiresAt: Instant,
    val refreshTokenHash: String,
    val refreshExpiresAt: Instant,
    val previousRefreshTokenHashes: Set<String>,
    val createdAt: Instant,
    val lastRotatedAt: Instant,
    val revokedAt: Instant? = null,
    val revocationReason: String? = null,
)

data class AuditEvent(
    val id: UUID = UUID.randomUUID(),
    val userId: UUID? = null,
    val sessionId: UUID? = null,
    val eventType: String,
    val subjectFingerprint: String? = null,
    val ipFingerprint: String? = null,
    val outcome: String,
    val occurredAt: Instant,
)

data class AuthenticatedPrincipal(val userId: UUID, val sessionId: UUID)

interface TimeSource {
    fun now(): Instant
}

class SystemTimeSource(private val clock: Clock = Clock.systemUTC()) : TimeSource {
    override fun now(): Instant = clock.instant()
}

class AuthenticationException(
    val errorCode: String,
    override val message: String,
    val httpStatus: Int,
    val retryAfterSeconds: Long? = null,
) : RuntimeException(message)
