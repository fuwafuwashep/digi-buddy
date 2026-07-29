package com.digibuddy.backend.payment

import com.digibuddy.backend.auth.AuthenticatedPrincipal
import com.digibuddy.backend.auth.AuthenticationException
import com.digibuddy.backend.booking.BookingActor
import com.digibuddy.backend.booking.BookingService
import com.digibuddy.backend.booking.TransitionContext
import com.digibuddy.shared.contracts.BookingStatus
import com.digibuddy.shared.contracts.CreateBookingRequest
import com.digibuddy.shared.contracts.PaymentStatus
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID
import kotlin.test.assertEquals

class PaymentServiceTest {
    @Test
    fun `development authorization confirms booking and balances ledger`() {
        val bookings = BookingService()
        val principal = AuthenticatedPrincipal(UUID.randomUUID(), UUID.randomUUID())
        val booking = bookings.create(principal, "payment-booking-key", request())
        val id = UUID.fromString(booking.summary.bookingId)
        bookings.transition(principal, id, BookingStatus.QUOTED, TransitionContext(BookingActor.HELPER))
        bookings.transition(
            principal,
            id,
            BookingStatus.AWAITING_CUSTOMER_APPROVAL,
            TransitionContext(BookingActor.SYSTEM),
        )
        bookings.acceptQuote(principal, id)
        val service = PaymentService(bookings, LocalDevelopmentPaymentProvider())
        val payment = service.create(principal, id, "payment-intent-key")
        val authorized = service.authorizeDevelopment(principal, UUID.fromString(payment.paymentId))
        assertEquals(PaymentStatus.AUTHORIZED, authorized.status)
        assertEquals(BookingStatus.CONFIRMED, bookings.detail(principal, id).summary.status)
        assertEquals(0, service.receipt(principal, id).entries.sumOf { it.amountCents })
    }

    @Test
    fun `local adapter rejects webhook trust`() {
        val service = PaymentService(BookingService(), LocalDevelopmentPaymentProvider())
        assertThrows<AuthenticationException> { service.webhook("{}", "invalid", "evt_1") }
    }

    private fun request() = CreateBookingRequest(
        "10000000-0000-0000-0000-000000000001", "Maya Rowan", "technology-lessons", "Lesson", "REMOTE",
        "Please help me understand my phone settings.", scheduledStart = "2026-08-01T15:00:00Z",
        scheduledEnd = "2026-08-01T16:00:00Z", pricingType = "HOURLY", expectedLaborCents = 4_000,
        cancellationTermsAccepted = true,
    )
}
