package com.digibuddy.backend.auth

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AuthServiceTest {
    @Test
    fun `successful signup creates an account and tokens`() = runTest {
        val fixture = AuthFixture()
        val result = fixture.login("+1 312 555 0199")

        assertNotNull(fixture.service.authenticateAccessToken(result.accessToken))
        assertTrue(result.refreshToken.startsWith("dbr_"))
        assertEquals(1, fixture.repository.auditEvents().count { it.eventType == "ACCOUNT_CREATED" })
    }

    @Test
    fun `successful existing-user phone login keeps the account identity`() = runTest {
        val fixture = AuthFixture()
        val first = fixture.login("+1 312 555 0199", "device-one")
        val second = fixture.login("+1 312 555 0199", "device-two")

        assertEquals(first.userId, second.userId)
        assertNotEquals(first.sessionId, second.sessionId)
    }

    @Test
    fun `invalid OTP is rejected without consuming the valid code`() = runTest {
        val fixture = AuthFixture()
        val challenge = fixture.start()
        val invalidCode = if (challenge.developmentCode == "000000") "000001" else "000000"

        val error = assertFailsWith<AuthenticationException> {
            fixture.verify(challenge.attemptId, invalidCode)
        }
        assertEquals("INVALID_CODE", error.errorCode)
        assertNotNull(fixture.verify(challenge.attemptId, challenge.developmentCode!!))
    }

    @Test
    fun `expired OTP is rejected`() = runTest {
        val fixture = AuthFixture()
        val challenge = fixture.start()
        fixture.clock.advance(Duration.ofMinutes(6))

        val error = assertFailsWith<AuthenticationException> {
            fixture.verify(challenge.attemptId, challenge.developmentCode!!)
        }
        assertEquals("CODE_EXPIRED", error.errorCode)
    }

    @Test
    fun `per-phone verification rate limit is enforced`() = runTest {
        val fixture = AuthFixture()
        repeat(5) {
            fixture.start(sourceIp = "192.0.2.$it")
            fixture.clock.advance(Duration.ofSeconds(61))
        }

        val error = assertFailsWith<AuthenticationException> {
            fixture.start(sourceIp = "192.0.2.99")
        }
        assertEquals("RATE_LIMITED", error.errorCode)
        assertEquals(429, error.httpStatus)
    }

    @Test
    fun `per-IP verification rate limit is enforced across phone numbers`() = runTest {
        val fixture = AuthFixture()
        repeat(20) { index ->
            fixture.start(phone = "+13125550${(100 + index)}", sourceIp = "192.0.2.50")
        }

        val error = assertFailsWith<AuthenticationException> {
            fixture.start(phone = "+13125550120", sourceIp = "192.0.2.50")
        }
        assertEquals("RATE_LIMITED", error.errorCode)
    }

    @Test
    fun `resend timer rejects an immediate replacement code`() = runTest {
        val fixture = AuthFixture()
        val challenge = fixture.start()

        val error = assertFailsWith<AuthenticationException> {
            fixture.service.resend(java.util.UUID.fromString(challenge.attemptId), "192.0.2.1")
        }
        assertEquals("RESEND_NOT_READY", error.errorCode)
        assertTrue(error.retryAfterSeconds ?: 0 > 0)
    }

    @Test
    fun `five invalid codes temporarily lock the attempt`() = runTest {
        val fixture = AuthFixture()
        val challenge = fixture.start()
        val invalidCode = if (challenge.developmentCode == "000000") "000001" else "000000"
        var lastError: AuthenticationException? = null

        repeat(5) {
            lastError = assertFailsWith { fixture.verify(challenge.attemptId, invalidCode) }
        }
        assertEquals("ACCOUNT_LOCKED", lastError?.errorCode)
        assertTrue(lastError?.retryAfterSeconds ?: 0 > 0)
    }

    @Test
    fun `refresh rotates both access and refresh tokens`() = runTest {
        val fixture = AuthFixture()
        val initial = fixture.login()

        val rotated = fixture.service.refresh(initial.refreshToken)

        assertNotEquals(initial.accessToken, rotated.accessToken)
        assertNotEquals(initial.refreshToken, rotated.refreshToken)
        assertNull(fixture.service.authenticateAccessToken(initial.accessToken))
        assertNotNull(fixture.service.authenticateAccessToken(rotated.accessToken))
    }

    @Test
    fun `refresh-token reuse revokes the token family`() = runTest {
        val fixture = AuthFixture()
        val initial = fixture.login()
        val rotated = fixture.service.refresh(initial.refreshToken)

        val error = assertFailsWith<AuthenticationException> {
            fixture.service.refresh(initial.refreshToken)
        }
        assertEquals("REFRESH_TOKEN_REUSED", error.errorCode)
        assertNull(fixture.service.authenticateAccessToken(rotated.accessToken))
    }

    @Test
    fun `logout revokes the current device session`() = runTest {
        val fixture = AuthFixture()
        val tokens = fixture.login()
        val principal = fixture.service.authenticateAccessToken(tokens.accessToken)!!

        fixture.service.logout(principal)

        assertNull(fixture.service.authenticateAccessToken(tokens.accessToken))
    }

    @Test
    fun `sign out all devices revokes every user session`() = runTest {
        val fixture = AuthFixture()
        val first = fixture.login(deviceId = "first")
        val second = fixture.login(deviceId = "second")
        val principal = fixture.service.authenticateAccessToken(first.accessToken)!!

        fixture.service.logoutAll(principal)

        assertNull(fixture.service.authenticateAccessToken(first.accessToken))
        assertNull(fixture.service.authenticateAccessToken(second.accessToken))
    }

    @Test
    fun `email and password first step sends a phone challenge`() = runTest {
        val fixture = AuthFixture()
        val tokens = fixture.login()
        val principal = fixture.service.authenticateAccessToken(tokens.accessToken)!!
        fixture.service.addEmailCredential(principal, "friend@example.com", "a secure passphrase")

        val challenge = fixture.service.startEmailPasswordLogin(
            "friend@example.com",
            "a secure passphrase",
            "192.0.2.10",
        )

        assertTrue(challenge.maskedDestination.endsWith("0199"))
        assertNotNull(challenge.developmentCode)
    }

    @Test
    fun `email login completes only after the SMS second factor`() = runTest {
        val fixture = AuthFixture()
        val phoneTokens = fixture.login()
        val principal = fixture.service.authenticateAccessToken(phoneTokens.accessToken)!!
        fixture.service.addEmailCredential(principal, "friend@example.com", "a secure passphrase")
        val challenge = fixture.service.startEmailPasswordLogin(
            "friend@example.com",
            "a secure passphrase",
            "192.0.2.10",
        )

        val result = fixture.verify(challenge.attemptId, challenge.developmentCode!!, "email-device")

        assertEquals(phoneTokens.userId, result.userId)
        assertNotNull(fixture.service.authenticateAccessToken(result.accessToken))
    }

    @Test
    fun `wrong email password returns a generic login response`() = runTest {
        val fixture = AuthFixture()
        val error = assertFailsWith<AuthenticationException> {
            fixture.service.startEmailPasswordLogin("unknown@example.com", "wrong password", "192.0.2.10")
        }

        assertEquals("LOGIN_FAILED", error.errorCode)
        assertFalse(error.message.contains("account", ignoreCase = true))
    }
}

private class AuthFixture {
    val clock = MutableTimeSource(Instant.parse("2026-07-16T12:00:00Z"))
    val repository = InMemoryAuthRepository()
    private val tokenHasher = SecretHasher("unit-test-token-pepper")
    private val provider = DevelopmentOtpProvider(tokenHasher)
    val service = AuthService(
        repository = repository,
        otpProvider = provider,
        tokenHasher = tokenHasher,
        fingerprinter = IdentifierFingerprinter("unit-test-identifier-key"),
        passwordHasher = PasswordHasher(),
        timeSource = clock,
    )

    suspend fun start(phone: String = "+1 312 555 0199", sourceIp: String = "192.0.2.1") =
        service.startPhoneVerification(phone, "US", sourceIp)

    suspend fun verify(attemptId: String, code: String, deviceId: String = "test-device") = service.verifyPhoneCode(
        attemptId = java.util.UUID.fromString(attemptId),
        code = code,
        deviceId = deviceId,
        deviceName = "Test device",
        sourceIp = "192.0.2.1",
    )

    suspend fun login(phone: String = "+1 312 555 0199", deviceId: String = "test-device") =
        start(phone).let { verify(it.attemptId, it.developmentCode!!, deviceId) }
}

private class MutableTimeSource(private var current: Instant) : TimeSource {
    override fun now(): Instant = current

    fun advance(duration: Duration) {
        current = current.plus(duration)
    }
}
