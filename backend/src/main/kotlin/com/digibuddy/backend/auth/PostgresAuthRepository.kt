@file:Suppress("NestedBlockDepth", "TooGenericExceptionCaught", "TooManyFunctions")

package com.digibuddy.backend.auth

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

class PostgresAuthRepository(jdbcUrl: String, username: String, password: String) :
    AuthRepository,
    AutoCloseable {
    private val dataSource = HikariDataSource(
        HikariConfig().apply {
            this.jdbcUrl = jdbcUrl
            this.username = username
            this.password = password
            maximumPoolSize = 10
            minimumIdle = 1
            poolName = "digibuddy-auth"
        },
    )

    override fun saveAttempt(attempt: VerificationAttempt) = execute(
        """
        INSERT INTO phone_verification_attempt
            (id, phone_e164, phone_fingerprint, ip_fingerprint, purpose, user_id, provider_reference,
             created_at, expires_at, resend_available_at, failed_attempts, locked_until, verified_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent(),
    ) { bindAttempt(attempt) }

    override fun findAttempt(id: UUID): VerificationAttempt? = queryOne(
        "SELECT * FROM phone_verification_attempt WHERE id = ?",
        { setObject(1, id) },
        { toAttempt() },
    )

    override fun latestAttempt(phoneFingerprint: String, purpose: VerificationPurpose): VerificationAttempt? = queryOne(
        """
        SELECT * FROM phone_verification_attempt
        WHERE phone_fingerprint = ? AND purpose = ?
        ORDER BY created_at DESC LIMIT 1
        """.trimIndent(),
        {
            setString(1, phoneFingerprint)
            setString(2, purpose.name)
        },
        { toAttempt() },
    )

    override fun countPhoneAttempts(phoneFingerprint: String, since: Instant): Int = count(
        "SELECT COUNT(*) FROM phone_verification_attempt WHERE phone_fingerprint = ? AND created_at >= ?",
        phoneFingerprint,
        since,
    )

    override fun countIpAttempts(ipFingerprint: String, since: Instant): Int = count(
        "SELECT COUNT(*) FROM phone_verification_attempt WHERE ip_fingerprint = ? AND created_at >= ?",
        ipFingerprint,
        since,
    )

    override fun updateAttempt(attempt: VerificationAttempt) = execute(
        """
        UPDATE phone_verification_attempt SET failed_attempts = ?, locked_until = ?, verified_at = ? WHERE id = ?
        """.trimIndent(),
    ) {
        setInt(1, attempt.failedAttempts)
        setInstant(2, attempt.lockedUntil)
        setInstant(3, attempt.verifiedAt)
        setObject(4, attempt.id)
    }

    override fun findUserByPhone(phoneE164: String): UserIdentity? = findUserWhere("phone_e164 = ?", phoneE164)

    override fun findUser(id: UUID): UserIdentity? = findUserWhere("id = ?", id)

    override fun createUser(user: UserIdentity) {
        dataSource.connection.use { connection ->
            connection.autoCommit = false
            try {
                val created = connection.prepareStatement(
                    """
                    INSERT INTO user_identity (id, phone_e164, phone_fingerprint, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?)
                    ON CONFLICT (phone_e164) DO NOTHING
                    """.trimIndent(),
                ).use {
                    it.setObject(1, user.id)
                    it.setString(2, user.phoneE164)
                    it.setString(3, user.phoneFingerprint)
                    it.setInstant(4, user.createdAt)
                    it.setInstant(5, user.createdAt)
                    it.executeUpdate() == 1
                }
                if (created) {
                    connection.prepareStatement(
                        "INSERT INTO user_role (user_id, role, granted_at) VALUES (?, ?, ?)",
                    ).use { statement ->
                        user.roles.forEach { role ->
                            statement.setObject(1, user.id)
                            statement.setString(2, role)
                            statement.setInstant(3, user.createdAt)
                            statement.addBatch()
                        }
                        statement.executeBatch()
                    }
                }
                connection.commit()
            } catch (cause: Exception) {
                connection.rollback()
                throw cause
            } finally {
                connection.autoCommit = true
            }
        }
    }

    override fun saveEmailCredential(credential: EmailCredential) = execute(
        """
        INSERT INTO email_credential (user_id, email_normalized, password_hash, created_at, updated_at)
        VALUES (?, ?, ?, now(), now())
        ON CONFLICT (user_id) DO UPDATE SET
            email_normalized = EXCLUDED.email_normalized,
            password_hash = EXCLUDED.password_hash,
            updated_at = now()
        """.trimIndent(),
    ) {
        setObject(1, credential.userId)
        setString(2, credential.emailNormalized)
        setString(3, credential.passwordHash)
    }

    override fun findEmailCredential(emailNormalized: String): EmailCredential? = queryOne(
        "SELECT user_id, email_normalized, password_hash FROM email_credential WHERE email_normalized = ?",
        { setString(1, emailNormalized) },
    ) {
        EmailCredential(
            getObject("user_id", UUID::class.java),
            getString("email_normalized"),
            getString("password_hash"),
        )
    }

    override fun findEmailCredentialByUser(userId: UUID): EmailCredential? = queryOne(
        "SELECT user_id, email_normalized, password_hash FROM email_credential WHERE user_id = ?",
        { setObject(1, userId) },
    ) {
        EmailCredential(
            getObject("user_id", UUID::class.java),
            getString("email_normalized"),
            getString("password_hash"),
        )
    }

    override fun hasEmailCredential(userId: UUID): Boolean = queryOne(
        "SELECT EXISTS (SELECT 1 FROM email_credential WHERE user_id = ?)",
        { setObject(1, userId) },
    ) { getBoolean(1) } ?: false

    override fun upsertDevice(userId: UUID, deviceId: String, displayName: String, at: Instant) = execute(
        """
        INSERT INTO trusted_device (id, user_id, display_name, created_at, last_seen_at)
        VALUES (?, ?, ?, ?, ?)
        ON CONFLICT (id, user_id) DO UPDATE SET display_name = EXCLUDED.display_name, last_seen_at = EXCLUDED.last_seen_at
        """.trimIndent(),
    ) {
        setString(1, deviceId)
        setObject(2, userId)
        setString(3, displayName)
        setInstant(4, at)
        setInstant(5, at)
    }

    override fun devicesForUser(userId: UUID): List<TrustedDeviceRecord> = queryList(
        "SELECT * FROM trusted_device WHERE user_id = ? ORDER BY last_seen_at DESC",
        { setObject(1, userId) },
    ) {
        TrustedDeviceRecord(
            userId = getObject("user_id", UUID::class.java),
            deviceId = getString("id"),
            displayName = getString("display_name"),
            createdAt = getTimestamp("created_at").toInstant(),
            lastSeenAt = getTimestamp("last_seen_at").toInstant(),
        )
    }

    override fun saveSession(session: RefreshSession) = execute(
        """
        INSERT INTO refresh_session
            (id, user_id, device_id, access_token_hash, access_expires_at, refresh_token_hash,
             refresh_expires_at, created_at, last_rotated_at, revoked_at, revocation_reason)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent(),
    ) { bindSession(session) }

    override fun findSession(id: UUID): RefreshSession? {
        val session = queryOne(
            "SELECT * FROM refresh_session WHERE id = ?",
            { setObject(1, id) },
        ) { toSession(emptySet()) } ?: return null
        val history = queryList(
            "SELECT token_hash FROM refresh_token_history WHERE session_id = ?",
            { setObject(1, id) },
        ) { getString(1) }.toSet()
        return session.copy(previousRefreshTokenHashes = history)
    }

    override fun sessionsForUser(userId: UUID): List<RefreshSession> = queryList(
        "SELECT id FROM refresh_session WHERE user_id = ? ORDER BY created_at DESC",
        { setObject(1, userId) },
    ) { getObject("id", UUID::class.java) }.mapNotNull(::findSession)

    override fun updateSession(session: RefreshSession, expectedRefreshTokenHash: String?): Boolean {
        dataSource.connection.use { connection ->
            connection.autoCommit = false
            try {
                val expectedClause = if (expectedRefreshTokenHash == null) {
                    ""
                } else {
                    " AND refresh_token_hash = ? AND revoked_at IS NULL"
                }
                val updated = connection.prepareStatement(
                    """ 
                    UPDATE refresh_session SET access_token_hash = ?, access_expires_at = ?, refresh_token_hash = ?,
                        refresh_expires_at = ?, last_rotated_at = ?, revoked_at = ?, revocation_reason = ?
                    WHERE id = ?$expectedClause
                    """.trimIndent(),
                ).use {
                    it.setString(1, session.accessTokenHash)
                    it.setInstant(2, session.accessExpiresAt)
                    it.setString(3, session.refreshTokenHash)
                    it.setInstant(4, session.refreshExpiresAt)
                    it.setInstant(5, session.lastRotatedAt)
                    it.setInstant(6, session.revokedAt)
                    it.setString(7, session.revocationReason)
                    it.setObject(8, session.id)
                    if (expectedRefreshTokenHash != null) it.setString(9, expectedRefreshTokenHash)
                    it.executeUpdate() == 1
                }
                if (updated) {
                    connection.prepareStatement(
                        """
                        INSERT INTO refresh_token_history (session_id, token_hash, rotated_at)
                        VALUES (?, ?, ?) ON CONFLICT DO NOTHING
                        """.trimIndent(),
                    ).use { statement ->
                        session.previousRefreshTokenHashes.forEach { hash ->
                            statement.setObject(1, session.id)
                            statement.setString(2, hash)
                            statement.setInstant(3, session.lastRotatedAt)
                            statement.addBatch()
                        }
                        statement.executeBatch()
                    }
                }
                connection.commit()
                return updated
            } catch (cause: Exception) {
                connection.rollback()
                throw cause
            } finally {
                connection.autoCommit = true
            }
        }
    }

    override fun revokeAllSessions(userId: UUID, at: Instant, reason: String) = execute(
        """
        UPDATE refresh_session SET revoked_at = ?, revocation_reason = ?
        WHERE user_id = ? AND revoked_at IS NULL
        """.trimIndent(),
    ) {
        setInstant(1, at)
        setString(2, reason)
        setObject(3, userId)
    }

    override fun updateAccountStatus(userId: UUID, status: String) = execute(
        "UPDATE user_identity SET account_status = ?, updated_at = now() WHERE id = ?",
    ) {
        setString(1, status)
        setObject(2, userId)
    }

    override fun grantRole(userId: UUID, role: String, at: Instant) = execute(
        """
        INSERT INTO user_role (user_id, role, granted_at) VALUES (?, ?, ?)
        ON CONFLICT (user_id, role) DO NOTHING
        """.trimIndent(),
    ) {
        setObject(1, userId)
        setString(2, role)
        setInstant(3, at)
    }

    override fun addAuditEvent(event: AuditEvent) = execute(
        """
        INSERT INTO audit_event
            (id, user_id, session_id, event_type, subject_fingerprint, ip_fingerprint, outcome, occurred_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent(),
    ) {
        setObject(1, event.id)
        setObject(2, event.userId)
        setObject(3, event.sessionId)
        setString(4, event.eventType)
        setString(5, event.subjectFingerprint)
        setString(6, event.ipFingerprint)
        setString(7, event.outcome)
        setInstant(8, event.occurredAt)
    }

    override fun auditEvents(): List<AuditEvent> = queryList(
        "SELECT * FROM audit_event ORDER BY occurred_at",
        {},
    ) {
        AuditEvent(
            id = getObject("id", UUID::class.java),
            userId = getObject("user_id", UUID::class.java),
            sessionId = getObject("session_id", UUID::class.java),
            eventType = getString("event_type"),
            subjectFingerprint = getString("subject_fingerprint"),
            ipFingerprint = getString("ip_fingerprint"),
            outcome = getString("outcome"),
            occurredAt = getTimestamp("occurred_at").toInstant(),
        )
    }

    override fun close() = dataSource.close()

    private fun findUserWhere(clause: String, value: Any): UserIdentity? {
        val user = queryOne(
            "SELECT id, phone_e164, phone_fingerprint, created_at, account_status FROM user_identity WHERE $clause",
            { setObject(1, value) },
        ) {
            UserIdentity(
                id = getObject("id", UUID::class.java),
                phoneE164 = getString("phone_e164"),
                phoneFingerprint = getString("phone_fingerprint"),
                roles = emptySet(),
                createdAt = getTimestamp("created_at").toInstant(),
                accountStatus = getString("account_status"),
            )
        } ?: return null
        val roles = queryList(
            "SELECT role FROM user_role WHERE user_id = ?",
            { setObject(1, user.id) },
        ) { getString(1) }.toSet()
        return user.copy(roles = roles)
    }

    private fun count(sql: String, fingerprint: String, since: Instant): Int = queryOne(
        sql,
        {
            setString(1, fingerprint)
            setInstant(2, since)
        },
    ) { getInt(1) } ?: 0

    private fun execute(sql: String, bind: PreparedStatement.() -> Unit) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(sql).use { statement ->
                statement.bind()
                statement.executeUpdate()
            }
        }
    }

    private fun <T> queryOne(sql: String, bind: PreparedStatement.() -> Unit, map: ResultSet.() -> T): T? =
        queryList(sql, bind, map).firstOrNull()

    private fun <T> queryList(sql: String, bind: PreparedStatement.() -> Unit, map: ResultSet.() -> T): List<T> =
        dataSource.connection.use { connection ->
            connection.prepareStatement(sql).use { statement ->
                statement.bind()
                statement.executeQuery().use { results ->
                    buildList {
                        while (results.next()) add(results.map())
                    }
                }
            }
        }

    private fun PreparedStatement.bindAttempt(attempt: VerificationAttempt) {
        setObject(1, attempt.id)
        setString(2, attempt.phoneE164)
        setString(3, attempt.phoneFingerprint)
        setString(4, attempt.ipFingerprint)
        setString(5, attempt.purpose.name)
        setObject(6, attempt.userId)
        setString(7, attempt.providerReference)
        setInstant(8, attempt.createdAt)
        setInstant(9, attempt.expiresAt)
        setInstant(10, attempt.resendAvailableAt)
        setInt(11, attempt.failedAttempts)
        setInstant(12, attempt.lockedUntil)
        setInstant(13, attempt.verifiedAt)
    }

    private fun PreparedStatement.bindSession(session: RefreshSession) {
        setObject(1, session.id)
        setObject(2, session.userId)
        setString(3, session.deviceId)
        setString(4, session.accessTokenHash)
        setInstant(5, session.accessExpiresAt)
        setString(6, session.refreshTokenHash)
        setInstant(7, session.refreshExpiresAt)
        setInstant(8, session.createdAt)
        setInstant(9, session.lastRotatedAt)
        setInstant(10, session.revokedAt)
        setString(11, session.revocationReason)
    }

    private fun ResultSet.toAttempt() = VerificationAttempt(
        id = getObject("id", UUID::class.java),
        phoneE164 = getString("phone_e164"),
        phoneFingerprint = getString("phone_fingerprint"),
        ipFingerprint = getString("ip_fingerprint"),
        purpose = VerificationPurpose.valueOf(getString("purpose")),
        userId = getObject("user_id", UUID::class.java),
        providerReference = getString("provider_reference"),
        createdAt = getTimestamp("created_at").toInstant(),
        expiresAt = getTimestamp("expires_at").toInstant(),
        resendAvailableAt = getTimestamp("resend_available_at").toInstant(),
        failedAttempts = getInt("failed_attempts"),
        lockedUntil = getTimestamp("locked_until")?.toInstant(),
        verifiedAt = getTimestamp("verified_at")?.toInstant(),
    )

    private fun ResultSet.toSession(history: Set<String>) = RefreshSession(
        id = getObject("id", UUID::class.java),
        userId = getObject("user_id", UUID::class.java),
        deviceId = getString("device_id"),
        accessTokenHash = getString("access_token_hash"),
        accessExpiresAt = getTimestamp("access_expires_at").toInstant(),
        refreshTokenHash = getString("refresh_token_hash"),
        refreshExpiresAt = getTimestamp("refresh_expires_at").toInstant(),
        previousRefreshTokenHashes = history,
        createdAt = getTimestamp("created_at").toInstant(),
        lastRotatedAt = getTimestamp("last_rotated_at").toInstant(),
        revokedAt = getTimestamp("revoked_at")?.toInstant(),
        revocationReason = getString("revocation_reason"),
    )

    private fun PreparedStatement.setInstant(index: Int, instant: Instant?) {
        setTimestamp(index, instant?.let(Timestamp::from))
    }
}
