@file:Suppress("TooManyFunctions")

package com.digibuddy.backend.auth

import java.time.Instant
import java.util.UUID

interface AuthRepository {
    fun saveAttempt(attempt: VerificationAttempt)

    fun findAttempt(id: UUID): VerificationAttempt?

    fun latestAttempt(phoneFingerprint: String, purpose: VerificationPurpose): VerificationAttempt?

    fun countPhoneAttempts(phoneFingerprint: String, since: Instant): Int

    fun countIpAttempts(ipFingerprint: String, since: Instant): Int

    fun updateAttempt(attempt: VerificationAttempt)

    fun findUserByPhone(phoneE164: String): UserIdentity?

    fun findUser(id: UUID): UserIdentity?

    fun createUser(user: UserIdentity)

    fun saveEmailCredential(credential: EmailCredential)

    fun findEmailCredential(emailNormalized: String): EmailCredential?

    fun findEmailCredentialByUser(userId: UUID): EmailCredential?

    fun hasEmailCredential(userId: UUID): Boolean

    fun upsertDevice(userId: UUID, deviceId: String, displayName: String, at: Instant)

    fun devicesForUser(userId: UUID): List<TrustedDeviceRecord>

    fun saveSession(session: RefreshSession)

    fun findSession(id: UUID): RefreshSession?

    fun sessionsForUser(userId: UUID): List<RefreshSession>

    fun updateSession(session: RefreshSession, expectedRefreshTokenHash: String? = null): Boolean

    fun revokeAllSessions(userId: UUID, at: Instant, reason: String)

    fun updateAccountStatus(userId: UUID, status: String)

    fun grantRole(userId: UUID, role: String, at: Instant)

    fun addAuditEvent(event: AuditEvent)

    fun auditEvents(): List<AuditEvent>
}

class InMemoryAuthRepository : AuthRepository {
    private val attempts = linkedMapOf<UUID, VerificationAttempt>()
    private val users = linkedMapOf<UUID, UserIdentity>()
    private val credentials = linkedMapOf<String, EmailCredential>()
    private val sessions = linkedMapOf<UUID, RefreshSession>()
    private val devices = linkedMapOf<Pair<UUID, String>, TrustedDeviceRecord>()
    private val events = mutableListOf<AuditEvent>()

    @Synchronized
    override fun saveAttempt(attempt: VerificationAttempt) {
        attempts[attempt.id] = attempt
    }

    @Synchronized
    override fun findAttempt(id: UUID): VerificationAttempt? = attempts[id]

    @Synchronized
    override fun latestAttempt(phoneFingerprint: String, purpose: VerificationPurpose): VerificationAttempt? =
        attempts.values
            .filter { it.phoneFingerprint == phoneFingerprint && it.purpose == purpose }
            .maxByOrNull { it.createdAt }

    @Synchronized
    override fun countPhoneAttempts(phoneFingerprint: String, since: Instant): Int =
        attempts.values.count { it.phoneFingerprint == phoneFingerprint && !it.createdAt.isBefore(since) }

    @Synchronized
    override fun countIpAttempts(ipFingerprint: String, since: Instant): Int =
        attempts.values.count { it.ipFingerprint == ipFingerprint && !it.createdAt.isBefore(since) }

    @Synchronized
    override fun updateAttempt(attempt: VerificationAttempt) {
        require(attempts.containsKey(attempt.id))
        attempts[attempt.id] = attempt
    }

    @Synchronized
    override fun findUserByPhone(phoneE164: String): UserIdentity? = users.values.find { it.phoneE164 == phoneE164 }

    @Synchronized
    override fun findUser(id: UUID): UserIdentity? = users[id]

    @Synchronized
    override fun createUser(user: UserIdentity) {
        check(users.values.none { it.phoneE164 == user.phoneE164 })
        users[user.id] = user
    }

    @Synchronized
    override fun saveEmailCredential(credential: EmailCredential) {
        credentials.entries.removeIf { it.value.userId == credential.userId }
        credentials[credential.emailNormalized] = credential
    }

    @Synchronized
    override fun findEmailCredential(emailNormalized: String): EmailCredential? = credentials[emailNormalized]

    @Synchronized
    override fun findEmailCredentialByUser(userId: UUID): EmailCredential? = credentials.values.find {
        it.userId ==
            userId
    }

    @Synchronized
    override fun hasEmailCredential(userId: UUID): Boolean = credentials.values.any { it.userId == userId }

    @Synchronized
    override fun upsertDevice(userId: UUID, deviceId: String, displayName: String, at: Instant) {
        val existing = devices[userId to deviceId]
        devices[userId to deviceId] = TrustedDeviceRecord(
            userId = userId,
            deviceId = deviceId,
            displayName = displayName,
            createdAt = existing?.createdAt ?: at,
            lastSeenAt = at,
        )
    }

    @Synchronized
    override fun devicesForUser(userId: UUID): List<TrustedDeviceRecord> = devices.values.filter { it.userId == userId }

    @Synchronized
    override fun saveSession(session: RefreshSession) {
        sessions[session.id] = session
    }

    @Synchronized
    override fun findSession(id: UUID): RefreshSession? = sessions[id]

    @Synchronized
    override fun sessionsForUser(userId: UUID): List<RefreshSession> = sessions.values.filter { it.userId == userId }

    @Synchronized
    override fun updateSession(session: RefreshSession, expectedRefreshTokenHash: String?): Boolean {
        require(sessions.containsKey(session.id))
        if (expectedRefreshTokenHash != null) {
            val current = sessions.getValue(session.id)
            if (current.refreshTokenHash != expectedRefreshTokenHash || current.revokedAt != null) return false
        }
        sessions[session.id] = session
        return true
    }

    @Synchronized
    override fun revokeAllSessions(userId: UUID, at: Instant, reason: String) {
        sessions.replaceAll { _, session ->
            if (session.userId == userId && session.revokedAt == null) {
                session.copy(revokedAt = at, revocationReason = reason)
            } else {
                session
            }
        }
    }

    @Synchronized
    override fun updateAccountStatus(userId: UUID, status: String) {
        val user = users[userId] ?: return
        users[userId] = user.copy(accountStatus = status)
    }

    @Synchronized
    override fun grantRole(userId: UUID, role: String, at: Instant) {
        val user = users[userId] ?: return
        users[userId] = user.copy(roles = user.roles + role)
    }

    @Synchronized
    override fun addAuditEvent(event: AuditEvent) {
        events += event
    }

    @Synchronized
    override fun auditEvents(): List<AuditEvent> = events.toList()
}
