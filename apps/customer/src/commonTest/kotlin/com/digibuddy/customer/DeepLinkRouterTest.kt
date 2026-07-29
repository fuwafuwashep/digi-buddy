package com.digibuddy.customer

import com.digibuddy.shared.contracts.DeepLinkPayload
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertNull

class DeepLinkRouterTest {
    @Test
    fun `authenticated links route and logged out links resume after login`() {
        val router = DeepLinkRouter()
        assertNull(router.route(DeepLinkPayload("booking", "booking-1"), authenticated = false))
        assertIs<CustomerDestination.Booking>(router.resumeAfterLogin())
        assertIs<CustomerDestination.Helper>(router.route(DeepLinkPayload("helper", "helper-1"), authenticated = true))
    }
}
