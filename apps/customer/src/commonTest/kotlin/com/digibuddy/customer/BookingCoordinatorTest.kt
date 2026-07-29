package com.digibuddy.customer

import com.digibuddy.shared.contracts.HelperAvailabilitySummaryResponse
import com.digibuddy.shared.contracts.HelperProfileResponse
import com.digibuddy.shared.contracts.HelperServiceResponse
import com.digibuddy.shared.contracts.HelperSummaryResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BookingCoordinatorTest {
    @Test
    fun `in-person draft starts with profile ZIP but no unrelated city or state`() {
        val draft = initialBookingDraft(helper(), "32539")

        assertEquals("32539", draft.zipCode)
        assertTrue(draft.city.isEmpty())
        assertTrue(draft.region.isEmpty())
    }

    private fun helper() = HelperProfileResponse(
        summary = HelperSummaryResponse(
            helperId = "10000000-0000-0000-0000-000000000001",
            displayName = "Charles Han",
            headline = "Patient help with computers and Wi-Fi",
            biography = "I help neighbors solve everyday technology problems using calm, easy-to-follow steps.",
            verified = true,
            rating = 0.0,
            reviewCount = 0,
            completedJobCount = 0,
            responseTimeMinutes = 30,
            startingPriceCents = 2900,
            currency = "USD",
            remoteService = true,
            inPersonService = true,
            languages = listOf("en"),
            skills = listOf("windows"),
            serviceCategories = listOf("computer-help"),
            availability = HelperAvailabilitySummaryResponse("AVAILABLE"),
        ),
        experienceYears = 6,
        services = listOf(
            HelperServiceResponse(
                categorySlug = "computer-help",
                name = "Computer help",
                description = "Friendly computer help.",
                startingPriceCents = 2900,
                pricingType = "FIXED",
                remote = true,
                inPerson = true,
            ),
        ),
        serviceAreaDescription = "Crestview and nearby communities",
        certifications = emptyList(),
        portfolio = emptyList(),
        ratingBreakdown = emptyMap(),
    )
}
