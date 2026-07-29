package com.digibuddy.backend.booking

import com.digibuddy.backend.auth.AuthenticatedPrincipal
import com.digibuddy.backend.auth.AuthenticationException
import com.digibuddy.shared.contracts.BookingStatus
import com.digibuddy.shared.contracts.CreateBookingRequest
import com.digibuddy.shared.core.DigibuddyPricing
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BookingServiceTest {
    private val principal = AuthenticatedPrincipal(UUID.randomUUID(), UUID.randomUUID())
    private val service = BookingService(Clock.fixed(Instant.parse("2026-07-16T12:00:00Z"), ZoneOffset.UTC))

    @Test
    fun `creation is idempotent and owned`() {
        val first = service.create(principal, "booking-key-123", request())
        val replay = service.create(principal, "booking-key-123", request())
        assertEquals(first.summary.bookingId, replay.summary.bookingId)
        assertEquals(DigibuddyPricing.QUICK_REMOTE_CENTS, first.summary.price.laborCents)
        assertEquals(0, first.summary.price.platformFeeCents)
        assertThrows<AuthenticationException> {
            service.detail(
                AuthenticatedPrincipal(UUID.randomUUID(), UUID.randomUUID()),
                UUID.fromString(first.summary.bookingId),
            )
        }
    }

    @Test
    fun `confirmed time cannot overlap`() {
        val first = service.create(principal, "booking-key-aaa", request())
        val second = service.create(principal, "booking-key-bbb", request())
        service.transition(
            principal,
            UUID.fromString(first.summary.bookingId),
            BookingStatus.CONFIRMED,
            TransitionContext(BookingActor.HELPER),
        )
        assertThrows<AuthenticationException> {
            service.transition(
                principal,
                UUID.fromString(second.summary.bookingId),
                BookingStatus.CONFIRMED,
                TransitionContext(BookingActor.HELPER),
            )
        }
    }

    @Test
    fun `representative allowed transitions require the responsible actor and conditions`() {
        assertDoesNotThrow {
            BookingStateMachine.requireAllowed(
                BookingStatus.DRAFT,
                BookingStatus.REQUESTED,
                TransitionContext(BookingActor.CUSTOMER),
            )
            BookingStateMachine.requireAllowed(
                BookingStatus.AWAITING_PAYMENT_AUTHORIZATION,
                BookingStatus.CONFIRMED,
                TransitionContext(BookingActor.SYSTEM, paymentAuthorized = true),
            )
            BookingStateMachine.requireAllowed(
                BookingStatus.COMPLETED,
                BookingStatus.REFUNDED,
                TransitionContext(BookingActor.SUPPORT, refundRecorded = true),
            )
        }
        assertThrows<AuthenticationException> {
            BookingStateMachine.requireAllowed(
                BookingStatus.CONFIRMED,
                BookingStatus.COMPLETED,
                TransitionContext(BookingActor.CUSTOMER),
            )
        }
        assertThrows<AuthenticationException> {
            BookingStateMachine.requireAllowed(
                BookingStatus.AWAITING_PAYMENT_AUTHORIZATION,
                BookingStatus.CONFIRMED,
                TransitionContext(BookingActor.SYSTEM),
            )
        }
    }

    @Test
    fun `real helper account receives and accepts its customer request`() {
        val helperId = UUID.fromString("10000000-0000-0000-0000-000000000001")
        val helperUserId = UUID.randomUUID()
        val helper = AuthenticatedPrincipal(helperUserId, UUID.randomUUID())
        var created: BookingRecord? = null
        val connected = BookingService(
            clock = Clock.fixed(Instant.parse("2026-07-16T12:00:00Z"), ZoneOffset.UTC),
            helperAccountResolver = { if (it == helperId) helperUserId to "Jamie Helper" else null },
            customerDisplayName = { "Alex C." },
            onBookingCreated = { created = it },
        )

        val booking = connected.create(principal, "connected-booking-key", request())
        val requestForHelper = connected.helperRequests(helper).items.single()
        val accepted = connected.helperAccept(helper, UUID.fromString(booking.summary.bookingId))

        assertEquals("Alex C.", requestForHelper.customerDisplayName)
        assertEquals(BookingStatus.AWAITING_CUSTOMER_APPROVAL, accepted.summary.status)
        assertEquals(BookingStatus.AWAITING_CUSTOMER_APPROVAL, connected.detail(principal, created!!.id).summary.status)
        assertTrue(connected.helperRequests(helper).items.isEmpty())
    }

    private fun request() = CreateBookingRequest(
        "10000000-0000-0000-0000-000000000001",
        "Maya Rowan",
        "technology-lessons",
        "Technology lessons",
        "REMOTE",
        "Please help me understand the settings on my phone.",
        scheduledStart = "2026-08-01T15:00:00Z",
        scheduledEnd = "2026-08-01T16:00:00Z",
        pricingType = "HOURLY",
        expectedLaborCents = 4_000,
        cancellationTermsAccepted = true,
    )
}
