package com.digibuddy.backend.helper

import com.digibuddy.backend.auth.AuthService
import com.digibuddy.backend.auth.AuthenticatedPrincipal
import com.digibuddy.backend.auth.AuthenticationException
import com.digibuddy.backend.auth.DevelopmentOtpProvider
import com.digibuddy.backend.auth.IdentifierFingerprinter
import com.digibuddy.backend.auth.InMemoryAuthRepository
import com.digibuddy.backend.auth.PasswordHasher
import com.digibuddy.backend.auth.SecretHasher
import com.digibuddy.backend.auth.UserIdentity
import com.digibuddy.backend.catalog.HelperCatalogService
import com.digibuddy.backend.catalog.HelperSearchQuery
import com.digibuddy.backend.catalog.InMemoryHelperCatalogRepository
import com.digibuddy.shared.contracts.HelperAccountStatus
import com.digibuddy.shared.contracts.HelperApplicationStepRequest
import com.digibuddy.shared.contracts.HelperFieldVisibility
import com.digibuddy.shared.contracts.HelperOnboardingStep
import com.digibuddy.shared.contracts.ProfilePhotoUploadRequest
import com.digibuddy.shared.contracts.UpdateHelperProfileRequest
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HelperApplicationServiceTest {
    @Test
    fun `onboarding saves each step and restores progress`() {
        val fixture = Fixture()

        val saved = fixture.service.saveStep(
            fixture.principal,
            HelperOnboardingStep.LEGAL_NAME,
            values("legalFirstName" to "Jamie", "legalLastName" to "Taylor"),
        )
        val restored = fixture.service.application(fixture.principal)

        assertEquals(listOf(HelperOnboardingStep.LEGAL_NAME), saved.completedSteps)
        assertEquals(saved, restored)
        assertTrue(restored.progressPercent > 0)
        assertEquals("Jamie", restored.steps.single().values["legalFirstName"])
    }

    @Test
    fun `legacy helper pricing payload is accepted but its amount is discarded`() {
        val fixture = Fixture()

        val saved = fixture.service.saveStep(
            fixture.principal,
            HelperOnboardingStep.PRICING,
            values("pricingSummary" to "Help starts at $45", "startingPriceCents" to "4500"),
        ).steps.single()

        assertTrue(saved.values.isEmpty())
        assertEquals(true, saved.booleanValues["platformPricingAcknowledged"])
    }

    @Test
    fun `unapproved account cannot activate services or receive paid requests`() {
        val fixture = Fixture()

        val activation = assertFailsWith<AuthenticationException> {
            fixture.service.requireCanActivateServices(fixture.principal)
        }
        val requests = assertFailsWith<AuthenticationException> {
            fixture.service.requireCanReceivePaidRequest(fixture.principal)
        }

        assertEquals("HELPER_NOT_APPROVED", activation.errorCode)
        assertEquals(403, requests.httpStatus)
    }

    @Test
    fun `approval grants shared helper role and enables work`() {
        val fixture = Fixture()
        fixture.completeRequiredSteps()
        fixture.service.submit(fixture.principal)

        val approved = fixture.service.review(
            fixture.userId,
            HelperAccountStatus.APPROVED,
            staffUserId = UUID.randomUUID(),
            reason = "Development review approved",
        )

        assertEquals(HelperAccountStatus.APPROVED, approved.status)
        assertTrue(approved.canReceivePaidWork)
        assertTrue("HELPER" in fixture.auth.currentUser(fixture.principal).roles)
        fixture.service.requireCanActivateServices(fixture.principal)
        fixture.service.requireCanReceivePaidRequest(fixture.principal)
    }

    @Test
    fun `local approval projects the submitted helper into customer search`() {
        val catalogRepository = InMemoryHelperCatalogRepository()
        val fixture = Fixture(
            onApproved = catalogRepository::upsertApprovedHelper,
            onStatusChanged = catalogRepository::updateHelperStatus,
        )
        fixture.completeRequiredSteps()
        fixture.service.submit(fixture.principal)

        fixture.service.approveForLocalDevelopment(fixture.principal)
        val results = HelperCatalogService(catalogRepository).search(
            HelperSearchQuery("60601", category = "other", remoteService = true),
        )

        val projected = results.items.single { it.displayName == "Jamie" }
        assertTrue(projected.helperId != fixture.userId.toString())

        fixture.service.setPaused(fixture.principal, true)
        assertTrue(
            HelperCatalogService(catalogRepository).search(
                HelperSearchQuery("60601", category = "other", remoteService = true),
            ).items.none { it.displayName == "Jamie" },
        )
        fixture.service.setPaused(fixture.principal, false)
        assertTrue(
            HelperCatalogService(catalogRepository).search(
                HelperSearchQuery("60601", category = "other", remoteService = true),
            ).items.any { it.displayName == "Jamie" },
        )
    }

    @Test
    fun `approved helper can update ZIP and every editable profile group`() {
        val catalogRepository = InMemoryHelperCatalogRepository()
        val fixture = Fixture(onApproved = catalogRepository::upsertApprovedHelper)
        fixture.completeRequiredSteps()
        fixture.service.submit(fixture.principal)
        fixture.service.approveForLocalDevelopment(fixture.principal)

        val updated = fixture.service.updateProfile(
            fixture.principal,
            UpdateHelperProfileRequest(
                legalFirstName = "Jamie",
                legalLastName = "Taylor",
                displayName = "Jamie T.",
                headline = "Friendly help for phones and Wi-Fi",
                biography = "I provide patient technology help using simple language and clear steps.",
                homeZip = "32539",
                serviceMode = "BOTH",
                serviceAreaSummary = "Crestview and nearby communities",
                skillIds = listOf("wifi", "phones"),
                serviceCategoryIds = listOf("wifi-internet", "phone-tablet-help"),
                yearsExperience = 8,
                languages = listOf("en"),
                availabilitySummary = "Weekdays and Saturday mornings",
            ),
        )

        val search = HelperCatalogService(catalogRepository).search(
            HelperSearchQuery("32539", category = "wifi-internet", inPersonService = true),
        )
        assertEquals(HelperAccountStatus.APPROVED, updated.status)
        assertEquals("Jamie T.", search.items.single().displayName)
    }

    @Test
    fun `approved helper profile photo validates uploaded file bytes`() {
        val fixture = Fixture()
        fixture.completeRequiredSteps()
        fixture.service.submit(fixture.principal)
        fixture.service.approveForLocalDevelopment(fixture.principal)
        val png = byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10)
        val grant = fixture.service.createProfilePhotoUpload(
            fixture.principal,
            ProfilePhotoUploadRequest("profile.png", "image/png", png.size.toLong()),
        )

        fixture.service.acceptLocalProfilePhoto(UUID.fromString(grant.uploadId), "image/png", png)
        val completed = fixture.service.completeProfilePhoto(fixture.principal, grant.uploadId)

        val photo = completed.steps.single { it.step == HelperOnboardingStep.PROFILE_MEDIA }
            .values["profilePictureUrl"]
        assertTrue(photo.orEmpty().contains(grant.uploadId))
    }

    @Test
    fun `public snapshot excludes private identity and location values`() {
        val fixture = Fixture()
        fixture.service.saveStep(
            fixture.principal,
            HelperOnboardingStep.LEGAL_NAME,
            values("legalFirstName" to "Private", "legalLastName" to "Person"),
        )
        fixture.service.saveStep(
            fixture.principal,
            HelperOnboardingStep.HOME_AND_SERVICE_MODE,
            values("homeZip" to "60601", "serviceMode" to "BOTH"),
        )
        fixture.service.saveStep(
            fixture.principal,
            HelperOnboardingStep.PUBLIC_PROFILE,
            values(
                "displayName" to "Helpful Jamie",
                "headline" to "Patient help with everyday technology",
                "biography" to "I explain technology slowly and clearly so each customer feels comfortable.",
            ),
        )

        val public = fixture.service.publicSnapshot(fixture.principal)
        val serializedShape = public.toString()

        assertEquals("Helpful Jamie", public.displayName)
        assertEquals("BOTH", public.serviceMode)
        assertFalse("Private" in serializedShape)
        assertFalse("60601" in serializedShape)
        assertFalse("phone" in serializedShape.lowercase())
    }

    @Test
    fun `requirements clearly classify public private required and optional fields`() {
        val fixture = Fixture()
        val requirements = fixture.service.application(fixture.principal).requirements

        assertTrue(
            requirements.any {
                it.label == "Legal name" &&
                    it.visibility == HelperFieldVisibility.PRIVATE &&
                    it.required
            },
        )
        assertTrue(
            requirements.any {
                it.label == "Profile picture and banner" &&
                    it.visibility == HelperFieldVisibility.PUBLIC &&
                    !it.required
            },
        )
    }

    @Test
    fun `changes requested preserve details and allow corrected resubmission`() {
        val fixture = Fixture()
        fixture.completeRequiredSteps()
        fixture.service.submit(fixture.principal)
        val changes = fixture.service.review(
            fixture.userId,
            HelperAccountStatus.CHANGES_REQUESTED,
            staffUserId = UUID.randomUUID(),
            requestedChanges = listOf(HelperOnboardingStep.PUBLIC_PROFILE to "Make the headline more specific."),
        )

        fixture.service.saveStep(
            fixture.principal,
            HelperOnboardingStep.PUBLIC_PROFILE,
            values(
                "displayName" to "Jamie",
                "headline" to "Friendly Wi-Fi and device setup help",
                "biography" to "I provide calm, patient help and explain every step in simple language.",
            ),
        )
        val resubmitted = fixture.service.submit(fixture.principal)

        assertEquals(HelperAccountStatus.CHANGES_REQUESTED, changes.status)
        assertEquals("Make the headline more specific.", changes.requestedChanges.single().message)
        assertEquals(HelperAccountStatus.UNDER_REVIEW, resubmitted.status)
        assertTrue(resubmitted.requestedChanges.isEmpty())
    }

    @Test
    fun `invalid status transitions are rejected`() {
        val fixture = Fixture()
        fixture.service.application(fixture.principal)

        val error = assertFailsWith<AuthenticationException> {
            fixture.service.review(fixture.userId, HelperAccountStatus.APPROVED, UUID.randomUUID())
        }

        assertEquals("INVALID_HELPER_STATUS", error.errorCode)
        assertNull(fixture.repository.findByUser(fixture.userId)?.submittedAt)
    }

    private class Fixture(
        onApproved: (UUID, HelperCatalogApplicationSnapshot) -> Unit = { _, _ -> },
        onStatusChanged: (UUID, HelperAccountStatus) -> Unit = { _, _ -> },
    ) {
        val userId: UUID = UUID.randomUUID()
        val principal = AuthenticatedPrincipal(userId, UUID.randomUUID())
        val authRepository = InMemoryAuthRepository().apply {
            createUser(
                UserIdentity(
                    id = userId,
                    phoneE164 = "+13125550199",
                    phoneFingerprint = "fingerprint-$userId",
                    createdAt = Instant.parse("2026-07-18T12:00:00Z"),
                ),
            )
        }
        private val secretHasher = SecretHasher("test-only-pepper")
        val auth = AuthService(
            repository = authRepository,
            otpProvider = DevelopmentOtpProvider(secretHasher),
            tokenHasher = secretHasher,
            fingerprinter = IdentifierFingerprinter("test-only-identifier-key"),
            passwordHasher = PasswordHasher(),
        )
        val repository = InMemoryHelperApplicationRepository()
        val service = HelperApplicationService(
            repository,
            auth,
            onApproved = onApproved,
            onStatusChanged = onStatusChanged,
        )

        fun completeRequiredSteps() {
            val steps = mapOf(
                HelperOnboardingStep.LEGAL_NAME to values("legalFirstName" to "Jamie", "legalLastName" to "Taylor"),
                HelperOnboardingStep.PUBLIC_PROFILE to values(
                    "displayName" to "Jamie",
                    "headline" to "Patient technology help for everyday devices",
                    "biography" to "I help people understand their devices with calm explanations and simple steps.",
                ),
                HelperOnboardingStep.HOME_AND_SERVICE_MODE to values("homeZip" to "60601", "serviceMode" to "BOTH"),
                HelperOnboardingStep.SERVICE_AREA to
                    values("serviceAreaSummary" to "Within 12 miles of central Chicago"),
                HelperOnboardingStep.SKILLS to lists("skillIds", "wifi", "device-setup"),
                HelperOnboardingStep.SERVICES to lists("serviceCategoryIds", "wifi-and-internet"),
                HelperOnboardingStep.EXPERIENCE to values("yearsExperience" to "7"),
                HelperOnboardingStep.LANGUAGES to lists("languages", "en", "es"),
                HelperOnboardingStep.PRICING to HelperApplicationStepRequest(
                    booleanValues = mapOf("platformPricingAcknowledged" to true),
                ),
                HelperOnboardingStep.AVAILABILITY to
                    values("availabilitySummary" to "Weekday afternoons and Saturday mornings"),
                HelperOnboardingStep.TERMS_AND_POLICIES to
                    HelperApplicationStepRequest(booleanValues = mapOf("accepted" to true)),
                HelperOnboardingStep.PAYOUT_ONBOARDING to HelperApplicationStepRequest(
                    booleanValues = mapOf("placeholderAcknowledged" to true),
                ),
            )
            steps.forEach { (step, request) -> service.saveStep(principal, step, request) }
        }
    }
}

private fun values(vararg pairs: Pair<String, String>) = HelperApplicationStepRequest(values = mapOf(*pairs))

private fun lists(key: String, vararg values: String) = HelperApplicationStepRequest(
    listValues = mapOf(
        key to values.toList(),
    ),
)
