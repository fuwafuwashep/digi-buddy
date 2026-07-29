package com.digibuddy.shared.helper.onboarding

import com.digibuddy.shared.contracts.HelperAccountStatus
import com.digibuddy.shared.contracts.HelperOnboardingStatus
import com.digibuddy.shared.contracts.HelperStartupResponse
import kotlin.test.Test
import kotlin.test.assertEquals

class HelperStartupRoutingTest {
    @Test
    fun `each server lifecycle status routes to the matching helper destination`() {
        val expected = mapOf(
            HelperAccountStatus.PROFILE_INCOMPLETE to HelperDestination.ONBOARDING,
            HelperAccountStatus.IDENTITY_INFORMATION_REQUIRED to HelperDestination.ONBOARDING,
            HelperAccountStatus.PAYMENT_ONBOARDING_REQUIRED to HelperDestination.ONBOARDING,
            HelperAccountStatus.UNDER_REVIEW to HelperDestination.UNDER_REVIEW,
            HelperAccountStatus.CHANGES_REQUESTED to HelperDestination.CHANGES_REQUESTED,
            HelperAccountStatus.APPROVED to HelperDestination.APPROVED_APP,
            HelperAccountStatus.PAUSED_BY_HELPER to HelperDestination.PAUSED,
            HelperAccountStatus.SUSPENDED to HelperDestination.SUSPENDED,
            HelperAccountStatus.REJECTED to HelperDestination.REJECTED,
        )

        expected.forEach { (status, destination) ->
            val response = HelperStartupResponse(
                "user",
                true,
                HelperOnboardingStatus.ONBOARDING,
                message = "Status",
                helperStatus = status,
            )
            assertEquals(destination, response.destination())
        }
    }

    @Test
    fun `legacy startup responses still route through the compatibility status`() {
        val response = HelperStartupResponse(
            "user",
            true,
            HelperOnboardingStatus.APPROVED,
            message = "Status",
        )

        assertEquals(HelperDestination.APPROVED_APP, response.destination())
    }
}
