package com.digibuddy.backend.notification

import com.digibuddy.backend.auth.AuthenticatedPrincipal
import com.digibuddy.shared.contracts.DeepLinkPayload
import com.digibuddy.shared.contracts.RegisterDeviceTokenRequest
import com.digibuddy.shared.contracts.UpdateNotificationPreferencesRequest
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NotificationServiceTest {
    @Test
    fun `token rotation preferences and development delivery work`() {
        val provider = LocalDevelopmentNotificationProvider()
        val service = NotificationService(provider)
        val principal = AuthenticatedPrincipal(UUID.randomUUID(), UUID.randomUUID())
        assertTrue(
            service.register(
                principal,
                RegisterDeviceTokenRequest("a".repeat(64), "IOS", "device-12345678", "development"),
            ).registered,
        )
        service.register(principal, RegisterDeviceTokenRequest("b".repeat(64), "IOS", "device-12345678", "development"))
        val updated = service.update(principal, UpdateNotificationPreferencesRequest(true, true, false, true, false))
        assertFalse(updated.messages)
        service.send(
            principal.userId,
            PushMessage(
                "Booking confirmed",
                "Your appointment is ready.",
                DeepLinkPayload("booking", UUID.randomUUID().toString()),
                "BOOKING_CONFIRMED",
            ),
        )
        assertTrue(provider.deliveries.single().first.startsWith("b"))
    }
}
