package com.digibuddy.backend.catalog

import com.digibuddy.backend.auth.AuthService
import com.digibuddy.backend.auth.DevelopmentOtpProvider
import com.digibuddy.backend.auth.IdentifierFingerprinter
import com.digibuddy.backend.auth.InMemoryAuthRepository
import com.digibuddy.backend.auth.PasswordHasher
import com.digibuddy.backend.auth.SecretHasher
import com.digibuddy.backend.helper.HelperCatalogApplicationSnapshot
import com.digibuddy.backend.helper.HelperPublicApplicationSnapshot
import com.digibuddy.backend.module
import com.digibuddy.shared.core.DigibuddyPricing
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HelperCatalogServiceTest {
    private val service = HelperCatalogService(InMemoryHelperCatalogRepository(includeFictionalSeeds = true))

    @Test
    fun `exact ZIP coverage matches an in-person helper`() {
        val result = service.search(HelperSearchQuery("60657", inPersonService = true))

        assertTrue(result.items.any { it.displayName == "Maya Rowan" && it.inPersonService })
    }

    @Test
    fun `Crestview ZIP 32539 is accepted for customer discovery`() {
        val result = service.search(HelperSearchQuery("32539", remoteService = true))

        assertTrue(result.items.isNotEmpty())
        assertTrue(result.items.all { it.remoteService })
    }

    @Test
    fun `radius coverage matches a nearby ZIP`() {
        val result = service.search(
            HelperSearchQuery("60618", category = "phone-tablet-help", inPersonService = true),
        )

        assertEquals(listOf("Maya Rowan"), result.items.map { it.displayName })
        assertTrue(result.items.single().distanceMiles != null)
    }

    @Test
    fun `remote matching does not require geographic coverage`() {
        val result = service.search(
            HelperSearchQuery("60540", category = "technology-lessons", remoteService = true),
        )

        assertEquals(setOf("Maya Rowan", "Nora Bell"), result.items.map { it.displayName }.toSet())
        assertTrue(result.items.all { it.remoteService })
    }

    @Test
    fun `pending and suspended helpers never appear`() {
        val names = service.search(HelperSearchQuery("60614", remoteService = true)).items.map { it.displayName }

        assertFalse("Lina Marsh" in names)
        assertFalse("Owen Lake" in names)
    }

    @Test
    fun `locally approved application becomes searchable with platform pricing`() {
        val repository = InMemoryHelperCatalogRepository(includeFictionalSeeds = true)
        val helperId = java.util.UUID.randomUUID()
        repository.upsertApprovedHelper(
            helperId,
            HelperCatalogApplicationSnapshot(
                publicProfile = HelperPublicApplicationSnapshot(
                    displayName = "Test Helper",
                    profilePictureUrl = null,
                    bannerImageUrl = null,
                    headline = "Patient computer and phone help",
                    biography = "A fictional local-development helper profile used only by this automated test.",
                    serviceMode = "BOTH",
                    skills = listOf("windows"),
                    services = listOf("computer-help"),
                    yearsExperience = 4,
                    languages = listOf("en"),
                    availabilitySummary = "This week",
                ),
                homeZip = "32539",
            ),
        )

        val result = HelperCatalogService(repository).search(HelperSearchQuery("32539", inPersonService = true))

        assertEquals(listOf("Test Helper"), result.items.map { it.displayName })
        assertEquals(DigibuddyPricing.QUICK_REMOTE_CENTS, result.items.single().startingPriceCents)
    }

    @Test
    fun `category skill availability rating price language service mode and verification filters combine`() {
        val result = service.search(
            HelperSearchQuery(
                zipCode = "60601",
                category = "password-security",
                skill = "online-safety",
                availableWithinDays = 7,
                minimumRating = 4.8,
                maximumPriceCents = 7000,
                language = "fr",
                remoteService = true,
                verifiedOnly = true,
            ),
        )

        assertEquals(listOf("Samira Vale"), result.items.map { it.displayName })
    }

    @Test
    fun `verified filter excludes a pending verification`() {
        val unfiltered = service.search(
            HelperSearchQuery("60640", category = "smart-tv-streaming", inPersonService = true),
        )
        val verified = service.search(
            HelperSearchQuery("60640", category = "smart-tv-streaming", inPersonService = true, verifiedOnly = true),
        )

        assertEquals("Theo Linden", unfiltered.items.single().displayName)
        assertTrue(verified.items.isEmpty())
    }

    @Test
    fun `all sort choices are deterministic`() {
        HelperSort.entries.forEach { sort ->
            val first = service.search(HelperSearchQuery("60601", remoteService = true, sort = sort))
            val second = service.search(HelperSearchQuery("60601", remoteService = true, sort = sort))
            assertEquals(first.items.map { it.helperId }, second.items.map { it.helperId })
        }
        val lowPrice = service.search(
            HelperSearchQuery("60601", remoteService = true, sort = HelperSort.LOWEST_STARTING_PRICE),
        )
        assertEquals(
            lowPrice.items.map {
                it.startingPriceCents
            }.sorted(),
            lowPrice.items.map { it.startingPriceCents },
        )
    }

    @Test
    fun `pagination has stable non-overlapping pages`() {
        val first = service.search(HelperSearchQuery("60601", remoteService = true, page = 1, pageSize = 2))
        val second = service.search(HelperSearchQuery("60601", remoteService = true, page = 2, pageSize = 2))

        assertEquals(2, first.items.size)
        assertTrue(
            first.items.map {
                it.helperId
            }.toSet().intersect(second.items.map { it.helperId }.toSet()).isEmpty(),
        )
        assertEquals(first.total, second.total)
    }

    @Test
    fun `invalid or unknown ZIP is rejected`() {
        assertFails { service.search(HelperSearchQuery("ABC")) }
        assertFails { service.search(HelperSearchQuery("99999")) }
    }

    @Test
    fun `filter categories summary and availability reads are available`() {
        assertEquals(13, service.categories().size)
        assertTrue(service.filterOptions().skills.contains("windows"))
        val helper = service.search(HelperSearchQuery("60657", category = "phone-tablet-help")).items.single()
        assertEquals(helper.helperId, service.helper(java.util.UUID.fromString(helper.helperId)).helperId)
        assertTrue(service.availability(java.util.UUID.fromString(helper.helperId)).status.isNotBlank())
    }

    @Test
    fun `catalog endpoints require authentication`() = testApplication {
        application { module(testAuthService()) }

        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/v1/customer/helpers/search?zipCode=60601").status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/v1/customer/service-categories").status)
    }
}

private fun testAuthService(): AuthService {
    val hasher = SecretHasher("catalog-test-pepper")
    return AuthService(
        InMemoryAuthRepository(),
        DevelopmentOtpProvider(hasher),
        hasher,
        IdentifierFingerprinter("catalog-test-fingerprint"),
        PasswordHasher(),
    )
}
