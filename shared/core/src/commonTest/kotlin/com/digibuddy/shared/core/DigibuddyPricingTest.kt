package com.digibuddy.shared.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DigibuddyPricingTest {
    @Test
    fun `customer prices match the platform schedule`() {
        assertEquals(listOf(2_900, 4_900, 7_900), DigibuddyPricing.oneTimeHelp.map { it.priceCents })
        assertEquals(listOf(999, 1_999, 9_999), DigibuddyPricing.memberships.map { it.monthlyPriceCents })
        assertEquals(listOf(10, 30), DigibuddyPricing.memberships.mapNotNull { it.includedIssues })
        assertNull(DigibuddyPricing.memberships.last().includedIssues)
    }

    @Test
    fun `booking prices are based on mode rather than a helper supplied amount`() {
        assertEquals(2_900, DigibuddyPricing.bookingLaborCents("REMOTE"))
        assertEquals(7_900, DigibuddyPricing.bookingLaborCents("IN_PERSON"))
    }
}
