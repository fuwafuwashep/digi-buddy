@file:Suppress("LongParameterList", "ReturnCount", "ThrowsCount", "TooManyFunctions")

package com.digibuddy.backend.auth

import com.digibuddy.shared.contracts.AuthenticatedUserResponse
import com.digibuddy.shared.contracts.AuthenticationTokensResponse
import com.digibuddy.shared.contracts.NormalizedPhoneResponse
import com.digibuddy.shared.contracts.VerificationChallengeResponse
import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil
import java.security.SecureRandom
import java.time.Duration
import java.util.Base64
import java.util.Locale
import java.util.UUID

class AuthService(
    private val repository: AuthRepository,
    private val otpProvider: OtpProvider,
    private val tokenHasher: SecretHasher,
    private val fingerprinter: IdentifierFingerprinter,
    private val passwordHasher: PasswordHasher,
    private val timeSource: TimeSource = SystemTimeSource(),
    private val phoneUtil: PhoneNumberUtil = PhoneNumberUtil.getInstance(),
    private val secureRandom: SecureRandom = SecureRandom(),
) {
    fun normalizePhone(phoneNumber: String, defaultRegion: String): NormalizedPhoneResponse {
        val parsed = try {
            phoneUtil.parse(phoneNumber.trim(), defaultRegion.uppercase(Locale.US))
        } catch (_: NumberParseException) {
            throw badRequest("INVALID_PHONE", "Enter a valid phone number.")
        }
        if (!phoneUtil.isValidNumber(parsed)) {
            throw badRequest("INVALID_PHONE", "Enter a valid phone number.")
        }
        return NormalizedPhoneResponse(
            e164 = phoneUtil.format(parsed, PhoneNumberUtil.PhoneNumberFormat.E164),
            display = phoneUtil.format(parsed, PhoneNumberUtil.PhoneNumberFormat.INTERNATIONAL),
        )
    }

    suspend fun startPhoneVerification(
        phoneNumber: String,
        defaultRegion: String,
        sourceIp: String,
    ): VerificationChallengeResponse {
        val normalized = normalizePhone(phoneNumber, defaultRegion)
        return createAttempt(
            phoneE164 = normalized.e164,
            purpose = VerificationPurpose.PHONE_PRIMARY,
            userId = null,
            sourceIp = sourceIp,
        )
    }

    suspend fun resend(attemptId: UUID, sourceIp: String): VerificationChallengeResponse {
        val previous = repository.findAttempt(attemptId)
            ?: throw badRequest("INVALID_ATTEMPT", "Start verification again.")
        val now = timeSource.now()
        if (now.isBefore(previous.resendAvailableAt)) {
            val wait = Duration.between(now, previous.resendAvailableAt).seconds.coerceAtLeast(1)
            throw AuthenticationException("RESEND_NOT_READY", "Please wait before requesting another code.", 429, wait)
        }
        return createAttempt(previous.phoneE164, previous.purpose, previous.userId, sourceIp)
    }

    suspend fun verifyPhoneCode(
        attemptId: UUID,
        code: String,
        deviceId: String,
        deviceName: String,
        sourceIp: String,
    ): AuthenticationTokensResponse {
        val attempt = repository.findAttempt(attemptId)
            ?: throw badRequest("INVALID_CODE", "The code is invalid or no longer available.")
        val now = timeSource.now()
        val ipFingerprint = fingerprinter.fingerprint(sourceIp)
        if (attempt.verifiedAt != null || !now.isBefore(attempt.expiresAt)) {
            audit(attempt.userId, null, "OTP_VERIFY", attempt.phoneFingerprint, ipFingerprint, "EXPIRED")
            throw badRequest("CODE_EXPIRED", "This code has expired. Request a new code.")
        }
        if (attempt.lockedUntil?.let(now::isBefore) == true) {
            val wait = Duration.between(now, attempt.lockedUntil).seconds.coerceAtLeast(1)
            throw AuthenticationException("ACCOUNT_LOCKED", "Too many attempts. Try again later.", 423, wait)
        }
        val valid = code.matches(Regex("^[0-9]{6}$")) &&
            otpProvider.verifyCode(
                phoneE164 = attempt.phoneE164,
                attemptId = attempt.id,
                providerReference = attempt.providerReference,
                code = code,
            )
        if (!valid) {
            val failures = attempt.failedAttempts + 1
            val lockedUntil = if (failures >= MAX_CODE_ATTEMPTS) now.plus(LOCKOUT_DURATION) else null
            repository.updateAttempt(attempt.copy(failedAttempts = failures, lockedUntil = lockedUntil))
            audit(attempt.userId, null, "OTP_VERIFY", attempt.phoneFingerprint, ipFingerprint, "DENIED")
            if (lockedUntil != null) {
                throw AuthenticationException(
                    "ACCOUNT_LOCKED",
                    "Too many attempts. Try again later.",
                    423,
                    LOCKOUT_DURATION.seconds,
                )
            }
            throw badRequest("INVALID_CODE", "The code is invalid or no longer available.")
        }

        val user = when (attempt.purpose) {
            VerificationPurpose.PHONE_PRIMARY -> findOrCreateUser(attempt)
            VerificationPurpose.EMAIL_SECOND_FACTOR -> attempt.userId?.let(repository::findUser)
                ?: throw AuthenticationException("LOGIN_FAILED", GENERIC_LOGIN_FAILURE, 401)
        }
        repository.updateAttempt(attempt.copy(verifiedAt = now))
        val tokens = issueSession(user, sanitizeDeviceId(deviceId), sanitizeDeviceName(deviceName))
        audit(user.id, UUID.fromString(tokens.sessionId), "LOGIN", attempt.phoneFingerprint, ipFingerprint, "SUCCEEDED")
        return tokens
    }

    fun authenticateAccessToken(accessToken: String): AuthenticatedPrincipal? {
        val parsed = parseToken(accessToken, ACCESS_PREFIX) ?: return null
        val session = repository.findSession(parsed.first) ?: return null
        val now = timeSource.now()
        if (session.revokedAt != null || !now.isBefore(session.accessExpiresAt)) return null
        if (!tokenHasher.matches(accessToken, session.accessTokenHash)) return null
        return AuthenticatedPrincipal(session.userId, session.id)
    }

    fun refresh(refreshToken: String): AuthenticationTokensResponse {
        val parsed = parseToken(refreshToken, REFRESH_PREFIX)
            ?: throw AuthenticationException("INVALID_REFRESH_TOKEN", "Sign in again.", 401)
        val session = repository.findSession(parsed.first)
            ?: throw AuthenticationException("INVALID_REFRESH_TOKEN", "Sign in again.", 401)
        val now = timeSource.now()
        if (session.previousRefreshTokenHashes.any { tokenHasher.matches(refreshToken, it) }) {
            repository.revokeAllSessions(session.userId, now, "REFRESH_TOKEN_REUSE")
            audit(session.userId, session.id, "REFRESH_TOKEN_REUSE", null, null, "REVOKED")
            throw AuthenticationException(
                "REFRESH_TOKEN_REUSED",
                "This session is no longer valid. Sign in again.",
                401,
            )
        }
        if (session.revokedAt != null ||
            !now.isBefore(session.refreshExpiresAt) ||
            !tokenHasher.matches(refreshToken, session.refreshTokenHash)
        ) {
            throw AuthenticationException("INVALID_REFRESH_TOKEN", "Sign in again.", 401)
        }
        val accessToken = newToken(ACCESS_PREFIX, session.id)
        val newRefreshToken = newToken(REFRESH_PREFIX, session.id)
        val rotated = repository.updateSession(
            session.copy(
                accessTokenHash = tokenHasher.hash(accessToken),
                accessExpiresAt = now.plus(ACCESS_LIFETIME),
                refreshTokenHash = tokenHasher.hash(newRefreshToken),
                previousRefreshTokenHashes = session.previousRefreshTokenHashes + session.refreshTokenHash,
                lastRotatedAt = now,
            ),
            expectedRefreshTokenHash = session.refreshTokenHash,
        )
        if (!rotated) {
            repository.revokeAllSessions(session.userId, now, "CONCURRENT_REFRESH_TOKEN_REUSE")
            audit(session.userId, session.id, "REFRESH_TOKEN_REUSE", null, null, "REVOKED")
            throw AuthenticationException(
                "REFRESH_TOKEN_REUSED",
                "This session is no longer valid. Sign in again.",
                401,
            )
        }
        audit(session.userId, session.id, "REFRESH_TOKEN_ROTATED", null, null, "SUCCEEDED")
        return tokenResponse(session, accessToken, newRefreshToken)
    }

    fun logout(principal: AuthenticatedPrincipal) {
        val session = repository.findSession(principal.sessionId) ?: return
        if (session.userId != principal.userId || session.revokedAt != null) return
        val now = timeSource.now()
        repository.updateSession(session.copy(revokedAt = now, revocationReason = "USER_LOGOUT"))
        audit(principal.userId, principal.sessionId, "LOGOUT_CURRENT_DEVICE", null, null, "SUCCEEDED")
    }

    fun logoutAll(principal: AuthenticatedPrincipal) {
        val now = timeSource.now()
        repository.revokeAllSessions(principal.userId, now, "USER_LOGOUT_ALL")
        audit(principal.userId, principal.sessionId, "LOGOUT_ALL_DEVICES", null, null, "SUCCEEDED")
    }

    fun currentUser(principal: AuthenticatedPrincipal): AuthenticatedUserResponse {
        val user = repository.findUser(principal.userId)
            ?: throw AuthenticationException("UNAUTHORIZED", "Authentication is required.", 401)
        return AuthenticatedUserResponse(
            userId = user.id.toString(),
            roles = user.roles.sorted(),
            hasEmailCredential = repository.hasEmailCredential(user.id),
        )
    }

    fun accountProfileIdentity(principal: AuthenticatedPrincipal): AccountProfileIdentity {
        val user = repository.findUser(principal.userId)
            ?: throw AuthenticationException("UNAUTHORIZED", "Authentication is required.", 401)
        return AccountProfileIdentity(
            userId = user.id,
            phoneE164 = user.phoneE164,
            verifiedEmail = repository.findEmailCredentialByUser(user.id)?.emailNormalized,
            createdAt = user.createdAt,
            accountStatus = user.accountStatus,
        )
    }

    fun trustedDevices(principal: AuthenticatedPrincipal): List<TrustedDeviceRecord> =
        repository.devicesForUser(principal.userId)

    fun sessions(principal: AuthenticatedPrincipal): List<RefreshSession> = repository.sessionsForUser(principal.userId)

    fun requireFreshAuthentication(principal: AuthenticatedPrincipal) {
        val session = repository.findSession(principal.sessionId)
            ?: throw AuthenticationException("FRESH_AUTH_REQUIRED", "Verify your phone again to continue.", 401)
        if (session.revokedAt != null || Duration.between(session.createdAt, timeSource.now()) > FRESH_AUTH_LIFETIME) {
            throw AuthenticationException("FRESH_AUTH_REQUIRED", "Verify your phone again to continue.", 401)
        }
    }

    fun markDeletionRequested(principal: AuthenticatedPrincipal) {
        repository.updateAccountStatus(principal.userId, "DELETION_REQUESTED")
        logoutAll(principal)
    }

    fun grantHelperRole(userId: UUID) {
        repository.grantRole(userId, "HELPER", timeSource.now())
        audit(userId, null, "HELPER_ROLE_GRANTED", null, null, "SUCCEEDED")
    }

    fun bootstrapStaffAccount(
        email: String?,
        password: String?,
    ) {
        if (email.isNullOrBlank() && password.isNullOrBlank()) {
            return
        }

        val configuredEmail =
            requireNotNull(email?.takeIf { it.isNotBlank() }) {
                "DIGIBUDDY_ADMIN_EMAIL must be supplied when DIGIBUDDY_ADMIN_PASSWORD is configured."
            }

        val configuredPassword =
            requireNotNull(password?.takeIf { it.isNotBlank() }) {
                "DIGIBUDDY_ADMIN_PASSWORD must be supplied when DIGIBUDDY_ADMIN_EMAIL is configured."
            }

        require(configuredPassword.length >= MIN_PASSWORD_LENGTH) {
            "DIGIBUDDY_ADMIN_PASSWORD must contain at least $MIN_PASSWORD_LENGTH characters."
        }

        val normalizedEmail = normalizeEmail(configuredEmail)
        val now = timeSource.now()

        val deterministicId =
            UUID.nameUUIDFromBytes(
                "digibuddy-staff:$normalizedEmail".toByteArray(),
            )

        val syntheticPhone =
            "staff-" +
                deterministicId
                    .toString()
                    .replace("-", "")
                    .take(12)

        val existingCredential =
            repository.findEmailCredential(normalizedEmail)

        val user =
            existingCredential
                ?.let { repository.findUser(it.userId) }
                ?: repository.findUser(deterministicId)
                ?: repository.findUserByPhone(syntheticPhone)
                ?: UserIdentity(
                    id = deterministicId,
                    phoneE164 = syntheticPhone,
                    phoneFingerprint =
                        fingerprinter.fingerprint(syntheticPhone),
                    roles = setOf("STAFF"),
                    createdAt = now,
                ).also(repository::createUser)

        repository.grantRole(
            user.id,
            "STAFF",
            now,
        )

        val chars =
            configuredPassword.toCharArray()

        try {
            repository.saveEmailCredential(
                EmailCredential(
                    userId = user.id,
                    emailNormalized = normalizedEmail,
                    passwordHash =
                        passwordHasher.hash(chars),
                ),
            )
        } finally {
            chars.fill('\u0000')
        }

        audit(
            user.id,
            null,
            "STAFF_BOOTSTRAP",
            fingerprinter.fingerprint(normalizedEmail),
            null,
            "SUCCEEDED",
        )
    }

    fun staffPasswordLogin(
        email: String,
        password: String,
        deviceId: String,
        deviceName: String,
        sourceIp: String,
    ): AuthenticationTokensResponse {
        val normalizedEmail =
            normalizeEmail(email)

        val credential =
            repository.findEmailCredential(
                normalizedEmail,
            )

        val chars =
            password.toCharArray()

        val passwordValid =
            try {
                credential != null &&
                    passwordHasher.verify(
                        chars,
                        credential.passwordHash,
                    )
            } finally {
                chars.fill('\u0000')
            }

        val user =
            credential
                ?.takeIf { passwordValid }
                ?.let {
                    repository.findUser(it.userId)
                }

        if (
            user == null ||
            "STAFF" !in user.roles
        ) {
            audit(
                null,
                null,
                "STAFF_EMAIL_PASSWORD_LOGIN",
                fingerprinter.fingerprint(
                    normalizedEmail,
                ),
                fingerprinter.fingerprint(sourceIp),
                "DENIED",
            )

            throw AuthenticationException(
                "LOGIN_FAILED",
                GENERIC_LOGIN_FAILURE,
                401,
            )
        }

        val tokens =
            issueSession(
                user,
                sanitizeDeviceId(deviceId),
                sanitizeDeviceName(deviceName),
            )

        audit(
            user.id,
            UUID.fromString(tokens.sessionId),
            "STAFF_EMAIL_PASSWORD_LOGIN",
            user.phoneFingerprint,
            fingerprinter.fingerprint(sourceIp),
            "SUCCEEDED",
        )

        return tokens
    }

    fun addEmailCredential(principal: AuthenticatedPrincipal, email: String, password: String) {
        val normalizedEmail = normalizeEmail(email)
        if (password.length < MIN_PASSWORD_LENGTH) {
            throw badRequest("WEAK_PASSWORD", "Use at least $MIN_PASSWORD_LENGTH characters.")
        }
        val existing = repository.findEmailCredential(normalizedEmail)
        if (existing != null && existing.userId != principal.userId) {
            throw badRequest("EMAIL_UNAVAILABLE", "This email cannot be used.")
        }
        val chars = password.toCharArray()
        try {
            repository.saveEmailCredential(
                EmailCredential(principal.userId, normalizedEmail, passwordHasher.hash(chars)),
            )
        } finally {
            chars.fill('\u0000')
        }
        audit(principal.userId, principal.sessionId, "EMAIL_CREDENTIAL_SET", null, null, "SUCCEEDED")
    }

    suspend fun startEmailPasswordLogin(
        email: String,
        password: String,
        sourceIp: String,
    ): VerificationChallengeResponse {
        val credential = repository.findEmailCredential(normalizeEmail(email))
        val chars = password.toCharArray()
        val passwordValid = try {
            credential != null && passwordHasher.verify(chars, credential.passwordHash)
        } finally {
            chars.fill('\u0000')
        }
        val user = credential?.takeIf { passwordValid }?.let { repository.findUser(it.userId) }
        if (user == null) {
            audit(null, null, "EMAIL_PASSWORD_LOGIN", fingerprinter.fingerprint(email.lowercase()), null, "DENIED")
            throw AuthenticationException("LOGIN_FAILED", GENERIC_LOGIN_FAILURE, 401)
        }
        audit(user.id, null, "EMAIL_PASSWORD_LOGIN", user.phoneFingerprint, null, "FIRST_FACTOR_SUCCEEDED")
        return createAttempt(
            phoneE164 = user.phoneE164,
            purpose = VerificationPurpose.EMAIL_SECOND_FACTOR,
            userId = user.id,
            sourceIp = sourceIp,
        )
    }

    private suspend fun createAttempt(
        phoneE164: String,
        purpose: VerificationPurpose,
        userId: UUID?,
        sourceIp: String,
    ): VerificationChallengeResponse {
        val now = timeSource.now()
        val phoneFingerprint = fingerprinter.fingerprint(phoneE164)
        val ipFingerprint = fingerprinter.fingerprint(sourceIp)
        repository.latestAttempt(phoneFingerprint, purpose)?.let { latest ->
            if (latest.lockedUntil?.let(now::isBefore) == true) {
                val wait = Duration.between(now, latest.lockedUntil).seconds.coerceAtLeast(1)
                throw AuthenticationException("ACCOUNT_LOCKED", "Too many attempts. Try again later.", 423, wait)
            }
            if (latest.verifiedAt == null && now.isBefore(latest.resendAvailableAt)) {
                val wait = Duration.between(now, latest.resendAvailableAt).seconds.coerceAtLeast(1)
                throw AuthenticationException(
                    "RESEND_NOT_READY",
                    "Please wait before requesting another code.",
                    429,
                    wait,
                )
            }
        }
        val since = now.minus(RATE_LIMIT_WINDOW)
        if (repository.countPhoneAttempts(phoneFingerprint, since) >= MAX_PHONE_STARTS ||
            repository.countIpAttempts(ipFingerprint, since) >= MAX_IP_STARTS
        ) {
            audit(userId, null, "OTP_SEND", phoneFingerprint, ipFingerprint, "RATE_LIMITED")
            throw AuthenticationException(
                "RATE_LIMITED",
                "Too many requests. Try again later.",
                429,
                RATE_LIMIT_WINDOW.seconds,
            )
        }
        val id = UUID.randomUUID()
        val expiresAt = now.plus(OTP_LIFETIME)
        val delivery = otpProvider.sendCode(phoneE164, id, expiresAt)
        repository.saveAttempt(
            VerificationAttempt(
                id = id,
                phoneE164 = phoneE164,
                phoneFingerprint = phoneFingerprint,
                ipFingerprint = ipFingerprint,
                purpose = purpose,
                userId = userId,
                providerReference = delivery.providerReference,
                createdAt = now,
                expiresAt = expiresAt,
                resendAvailableAt = now.plus(RESEND_DELAY),
            ),
        )
        audit(userId, null, "OTP_SEND", phoneFingerprint, ipFingerprint, "ACCEPTED")
        return VerificationChallengeResponse(
            attemptId = id.toString(),
            maskedDestination = maskPhone(phoneE164),
            expiresInSeconds = OTP_LIFETIME.seconds,
            resendAfterSeconds = RESEND_DELAY.seconds,
            developmentCode = delivery.developmentCode,
        )
    }

    private fun findOrCreateUser(attempt: VerificationAttempt): UserIdentity {
        repository.findUserByPhone(attempt.phoneE164)?.let { return it }
        val user = UserIdentity(
            id = UUID.randomUUID(),
            phoneE164 = attempt.phoneE164,
            phoneFingerprint = attempt.phoneFingerprint,
            createdAt = timeSource.now(),
        )
        repository.createUser(user)
        val persistedUser = repository.findUserByPhone(attempt.phoneE164) ?: user
        if (persistedUser.id == user.id) {
            audit(user.id, null, "ACCOUNT_CREATED", user.phoneFingerprint, attempt.ipFingerprint, "SUCCEEDED")
        }
        return persistedUser
    }

    private fun issueSession(user: UserIdentity, deviceId: String, deviceName: String): AuthenticationTokensResponse {
        val now = timeSource.now()
        val sessionId = UUID.randomUUID()
        val accessToken = newToken(ACCESS_PREFIX, sessionId)
        val refreshToken = newToken(REFRESH_PREFIX, sessionId)
        repository.upsertDevice(user.id, deviceId, deviceName, now)
        val session = RefreshSession(
            id = sessionId,
            userId = user.id,
            deviceId = deviceId,
            accessTokenHash = tokenHasher.hash(accessToken),
            accessExpiresAt = now.plus(ACCESS_LIFETIME),
            refreshTokenHash = tokenHasher.hash(refreshToken),
            refreshExpiresAt = now.plus(REFRESH_LIFETIME),
            previousRefreshTokenHashes = emptySet(),
            createdAt = now,
            lastRotatedAt = now,
        )
        repository.saveSession(session)
        return tokenResponse(session, accessToken, refreshToken)
    }

    private fun tokenResponse(session: RefreshSession, accessToken: String, refreshToken: String) =
        AuthenticationTokensResponse(
            userId = session.userId.toString(),
            sessionId = session.id.toString(),
            accessToken = accessToken,
            accessExpiresInSeconds = ACCESS_LIFETIME.seconds,
            refreshToken = refreshToken,
            refreshExpiresInSeconds = Duration.between(timeSource.now(), session.refreshExpiresAt).seconds,
        )

    private fun newToken(prefix: String, sessionId: UUID): String {
        val secret = ByteArray(TOKEN_BYTES).also(secureRandom::nextBytes)
        return "$prefix$sessionId.${Base64.getUrlEncoder().withoutPadding().encodeToString(secret)}"
    }

    private fun parseToken(token: String, prefix: String): Pair<UUID, String>? {
        if (!token.startsWith(prefix)) return null
        val separator = token.indexOf('.', prefix.length)
        if (separator <= prefix.length || separator == token.lastIndex) return null
        return runCatching {
            UUID.fromString(token.substring(prefix.length, separator)) to
                token.substring(separator + 1)
        }
            .getOrNull()
    }

    private fun normalizeEmail(email: String): String {
        val normalized = email.trim().lowercase(Locale.US)
        if (!normalized.matches(Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) || normalized.length > 320) {
            throw badRequest("INVALID_EMAIL", "Enter a valid email address.")
        }
        return normalized
    }

    private fun sanitizeDeviceId(value: String): String = value.trim().take(MAX_DEVICE_FIELD).ifBlank {
        throw badRequest("INVALID_DEVICE", "Device information is required.")
    }

    private fun sanitizeDeviceName(value: String): String = value.trim().take(MAX_DEVICE_FIELD).ifBlank {
        "Customer device"
    }

    private fun maskPhone(phone: String): String = "••••${phone.takeLast(4)}"

    private fun audit(
        userId: UUID?,
        sessionId: UUID?,
        type: String,
        subjectFingerprint: String?,
        ipFingerprint: String?,
        outcome: String,
    ) {
        repository.addAuditEvent(
            AuditEvent(
                userId = userId,
                sessionId = sessionId,
                eventType = type,
                subjectFingerprint = subjectFingerprint,
                ipFingerprint = ipFingerprint,
                outcome = outcome,
                occurredAt = timeSource.now(),
            ),
        )
    }

    private fun badRequest(code: String, message: String) = AuthenticationException(code, message, 400)

    companion object {
        private val OTP_LIFETIME = Duration.ofMinutes(5)
        private val RESEND_DELAY = Duration.ofSeconds(60)
        private val LOCKOUT_DURATION = Duration.ofMinutes(15)
        private val RATE_LIMIT_WINDOW = Duration.ofMinutes(15)
        private val ACCESS_LIFETIME = Duration.ofMinutes(10)
        private val REFRESH_LIFETIME = Duration.ofDays(30)
        private val FRESH_AUTH_LIFETIME = Duration.ofMinutes(10)
        private const val MAX_CODE_ATTEMPTS = 5
        private const val MAX_PHONE_STARTS = 5
        private const val MAX_IP_STARTS = 20
        private const val MIN_PASSWORD_LENGTH = 12
        private const val MAX_DEVICE_FIELD = 160
        private const val TOKEN_BYTES = 32
        private const val ACCESS_PREFIX = "dba_"
        private const val REFRESH_PREFIX = "dbr_"
        private const val GENERIC_LOGIN_FAILURE = "The email, password, or verification step was not accepted."
    }
}
