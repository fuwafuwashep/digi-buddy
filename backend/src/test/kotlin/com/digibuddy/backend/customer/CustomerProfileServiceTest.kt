package com.digibuddy.backend.customer

import com.digibuddy.backend.auth.AuthService
import com.digibuddy.backend.auth.AuthenticationException
import com.digibuddy.backend.auth.DevelopmentOtpProvider
import com.digibuddy.backend.auth.IdentifierFingerprinter
import com.digibuddy.backend.auth.InMemoryAuthRepository
import com.digibuddy.backend.auth.PasswordHasher
import com.digibuddy.backend.auth.SecretHasher
import com.digibuddy.backend.auth.TimeSource
import com.digibuddy.backend.module
import com.digibuddy.shared.contracts.CompleteCustomerOnboardingRequest
import com.digibuddy.shared.contracts.ProfilePhotoUploadRequest
import com.digibuddy.shared.contracts.UpdateAccessibilitySettingsRequest
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CustomerProfileServiceTest {
    @Test
    fun `onboarding accepts required fields while optional choices are skipped`() = runTest {
        val fixture = ProfileFixture()
        val principal = fixture.login().let { fixture.auth.authenticateAccessToken(it.accessToken)!! }

        val profile = fixture.profiles.completeOnboarding(
            principal,
            CompleteCustomerOnboardingRequest("Ada", "Lovelace", "60601"),
        )

        assertTrue(profile.onboardingComplete)
        assertEquals("Ada L.", profile.publicDisplayName)
        assertEquals("+13125550199", profile.verifiedPhoneNumber)
        assertTrue(profile.technologyPreferences.isEmpty())
        assertFalse(profile.settings.permissionStatus.values.any { it == "GRANTED" })
    }

    @Test
    fun `onboarding accepts ZIP 32539 and Wi-Fi as a listed interest`() = runTest {
        val fixture = ProfileFixture()
        val principal = fixture.login().let { fixture.auth.authenticateAccessToken(it.accessToken)!! }

        val profile = fixture.profiles.completeOnboarding(
            principal,
            CompleteCustomerOnboardingRequest(
                "Maria",
                "Rivera",
                "32539",
                technologyPreferences = listOf("WI_FI", "COMPUTERS"),
            ),
        )

        assertEquals("32539", profile.zipCode)
        assertEquals(setOf("COMPUTERS", "WI_FI"), profile.technologyPreferences.toSet())
    }

    @Test
    fun `onboarding validates names ZIP codes and technology choices`() = runTest {
        val fixture = ProfileFixture()
        val principal = fixture.login().let { fixture.auth.authenticateAccessToken(it.accessToken)!! }

        val error = assertFailsWith<AuthenticationException> {
            fixture.profiles.completeOnboarding(
                principal,
                CompleteCustomerOnboardingRequest("Ada3", "Lovelace", "ABC", technologyPreferences = listOf("unknown")),
            )
        }

        assertEquals("INVALID_NAME", error.errorCode)
        assertFalse(fixture.profiles.profile(principal).onboardingComplete)
    }

    @Test
    fun `profile updates keep a private customer identity and accessibility preferences`() = runTest {
        val fixture = ProfileFixture()
        val principal = fixture.onboard()

        fixture.profiles.updateName(principal, "Grace", "Hopper")
        fixture.profiles.updateZip(principal, "94105")
        val updated = fixture.profiles.updateAccessibility(
            principal,
            UpdateAccessibilitySettingsRequest(
                followSystemTextSize = false,
                extraLargeText = true,
                highContrast = true,
                reducedMotion = true,
                simplifiedInstructions = true,
            ),
        )

        assertEquals("Grace H.", updated.publicDisplayName)
        assertEquals("94105", updated.zipCode)
        assertTrue(updated.settings.extraLargeText)
        assertTrue(updated.settings.reducedMotion)
    }

    @Test
    fun `photo upload rejects size type and content mismatches`() = runTest {
        val fixture = ProfileFixture()
        val principal = fixture.onboard()

        assertEquals(
            "INVALID_IMAGE",
            assertFailsWith<AuthenticationException> {
                fixture.profiles.createPhotoUpload(principal, ProfilePhotoUploadRequest("photo.gif", "image/gif", 20))
            }.errorCode,
        )
        val grant = fixture.profiles.createPhotoUpload(
            principal,
            ProfilePhotoUploadRequest("photo.png", "image/png", 8),
        )
        assertEquals(
            "INVALID_IMAGE",
            assertFailsWith<AuthenticationException> {
                fixture.profiles.acceptLocalUpload(UUID.fromString(grant.uploadId), "image/png", ByteArray(8))
            }.errorCode,
        )
    }

    @Test
    fun `valid image can be uploaded completed and removed`() = runTest {
        val fixture = ProfileFixture()
        val principal = fixture.onboard()
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        val grant = fixture.profiles.createPhotoUpload(
            principal,
            ProfilePhotoUploadRequest("photo.png", "image/png", png.size.toLong()),
        )

        fixture.profiles.acceptLocalUpload(UUID.fromString(grant.uploadId), "image/png", png)
        assertTrue(
            fixture.profiles.completePhoto(principal, grant.uploadId).profilePictureUrl?.contains(grant.uploadId) ==
                true,
        )
        assertNull(fixture.profiles.removePhoto(principal).profilePictureUrl)
    }

    @Test
    fun `export and deletion requests are authorized and revoke all sessions`() = runTest {
        val fixture = ProfileFixture()
        val principal = fixture.onboard()
        val second = fixture.login("second-device")

        assertEquals("REQUESTED", fixture.profiles.requestExport(principal).status)
        assertEquals("DELETION_REQUESTED", fixture.profiles.requestDeletion(principal, "DELETE").status)
        assertNull(fixture.auth.authenticateAccessToken(fixture.primaryAccessToken))
        assertNull(fixture.auth.authenticateAccessToken(second.accessToken))
    }

    @Test
    fun `deletion requires fresh authentication and respects the booking guard`() = runTest {
        val fixture = ProfileFixture(hasActiveBookings = true)
        val principal = fixture.onboard()
        assertEquals(
            "ACTIVE_BOOKINGS",
            assertFailsWith<AuthenticationException> {
                fixture.profiles.requestDeletion(principal, "DELETE")
            }.errorCode,
        )

        fixture.clock.advance(Duration.ofMinutes(11))
        assertEquals(
            "FRESH_AUTH_REQUIRED",
            assertFailsWith<AuthenticationException> {
                fixture.profiles.requestDeletion(principal, "DELETE")
            }.errorCode,
        )
    }

    @Test
    fun `profile endpoint rejects a request without an access token`() = testApplication {
        val fixture = ProfileFixture()
        application { module(fixture.auth, fixture.profiles) }

        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/v1/customer/profile").status)
    }
}

private class ProfileFixture(hasActiveBookings: Boolean = false) {
    val clock = ProfileTimeSource(Instant.parse("2026-07-16T12:00:00Z"))
    private val repository = InMemoryAuthRepository()
    private val tokenHasher = SecretHasher("profile-test-token-pepper")
    private val otp = DevelopmentOtpProvider(tokenHasher)
    val auth = AuthService(
        repository,
        otp,
        tokenHasher,
        IdentifierFingerprinter("profile-test-identifier-key"),
        PasswordHasher(),
        clock,
    )
    val profiles = CustomerProfileService(
        InMemoryCustomerProfileRepository(),
        auth,
        LocalDevelopmentProfileObjectStorage(),
        object : ActiveBookingDeletionGuard {
            override fun hasActiveBookings(customerId: UUID) = hasActiveBookings
        },
        clock,
    )
    var primaryAccessToken = ""

    suspend fun login(
        deviceId: String = "primary-device",
    ): com.digibuddy.shared.contracts.AuthenticationTokensResponse {
        val challenge = auth.startPhoneVerification("+1 312 555 0199", "US", "192.0.2.30")
        return auth.verifyPhoneCode(
            UUID.fromString(challenge.attemptId),
            challenge.developmentCode!!,
            deviceId,
            "Test phone",
            "192.0.2.30",
        ).also { if (deviceId == "primary-device") primaryAccessToken = it.accessToken }
    }

    suspend fun onboard(): com.digibuddy.backend.auth.AuthenticatedPrincipal {
        val token = login().accessToken
        val principal = auth.authenticateAccessToken(token)!!
        profiles.completeOnboarding(principal, CompleteCustomerOnboardingRequest("Ada", "Lovelace", "60601"))
        return principal
    }
}

private class ProfileTimeSource(private var current: Instant) : TimeSource {
    override fun now(): Instant = current
    fun advance(duration: Duration) {
        current = current.plus(duration)
    }
}
