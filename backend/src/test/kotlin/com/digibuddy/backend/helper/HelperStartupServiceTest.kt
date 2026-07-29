package com.digibuddy.backend.helper

import com.digibuddy.backend.catalog.HelperLifecycleSnapshot
import com.digibuddy.shared.contracts.HelperOnboardingStatus
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class HelperStartupServiceTest {
    @Test
    fun `account without helper role routes to onboarding`() {
        val profile = HelperLifecycleSnapshot("ACTIVE", "APPROVED", "Sample Helper")

        assertEquals(HelperOnboardingStatus.ONBOARDING, resolveHelperStatus(false, "ACTIVE", profile))
    }

    @Test
    fun `helper approval states map to startup destinations`() {
        val expected = mapOf(
            "PENDING" to HelperOnboardingStatus.UNDER_REVIEW,
            "REJECTED" to HelperOnboardingStatus.CHANGES_REQUESTED,
            "APPROVED" to HelperOnboardingStatus.APPROVED,
            "SUSPENDED" to HelperOnboardingStatus.SUSPENDED,
        )

        expected.forEach { (approval, status) ->
            val profile = HelperLifecycleSnapshot("ACTIVE", approval, "Sample Helper")
            assertEquals(status, resolveHelperStatus(true, "ACTIVE", profile))
        }
    }

    @Test
    fun `suspended identity cannot enter approved helper app`() {
        val profile = HelperLifecycleSnapshot("ACTIVE", "APPROVED", "Sample Helper")

        assertEquals(HelperOnboardingStatus.SUSPENDED, resolveHelperStatus(true, "SUSPENDED", profile))
    }
}
