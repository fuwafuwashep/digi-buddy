package com.digibuddy.backend.payment

import com.digibuddy.backend.auth.AuthenticatedPrincipal
import com.digibuddy.backend.auth.AuthenticationException
import com.digibuddy.backend.booking.BookingService
import com.digibuddy.shared.contracts.LedgerEntryResponse
import com.digibuddy.shared.contracts.PaymentIntentResponse
import com.digibuddy.shared.contracts.PaymentStatus
import com.digibuddy.shared.contracts.ReceiptResponse
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

data class ProviderPayment(val providerId: String, val clientSecret: String?, val status: PaymentStatus)

interface PaymentProvider {
    val developmentAdapter: Boolean
    fun create(amountCents: Int, currency: String, idempotencyKey: String): ProviderPayment
    fun authorize(providerId: String): PaymentStatus
    fun capture(providerId: String): PaymentStatus
    fun cancel(providerId: String): PaymentStatus
    fun refund(providerId: String, amountCents: Int): PaymentStatus
    fun verifyWebhook(payload: String, signature: String?): Boolean
}

class LocalDevelopmentPaymentProvider : PaymentProvider {
    override val developmentAdapter = true
    override fun create(amountCents: Int, currency: String, idempotencyKey: String) = ProviderPayment(
        "dev_${UUID.nameUUIDFromBytes(idempotencyKey.toByteArray())}",
        null,
        PaymentStatus.REQUIRES_AUTHORIZATION,
    )
    override fun authorize(providerId: String) = PaymentStatus.AUTHORIZED
    override fun capture(providerId: String) = PaymentStatus.CAPTURED
    override fun cancel(providerId: String) = PaymentStatus.CANCELED
    override fun refund(providerId: String, amountCents: Int) = PaymentStatus.REFUNDED
    override fun verifyWebhook(payload: String, signature: String?) = false
}

class StripePaymentProvider(
    private val secretKey: String,
    private val webhookSecret: String,
    private val clock: Clock = Clock.systemUTC(),
) : PaymentProvider {
    init {
        require(secretKey.startsWith("sk_")) { "Stripe secret key must remain server-side." }
        require(webhookSecret.startsWith("whsec_")) { "Stripe webhook secret is required." }
    }

    override val developmentAdapter = false

    override fun create(amountCents: Int, currency: String, idempotencyKey: String): ProviderPayment =
        throw AuthenticationException(
            "STRIPE_TRANSPORT_REQUIRED",
            "Stripe transport is not configured on this host.",
            503,
        )

    override fun authorize(providerId: String) = PaymentStatus.PROCESSING
    override fun capture(providerId: String) = PaymentStatus.PROCESSING
    override fun cancel(providerId: String) = PaymentStatus.PROCESSING
    override fun refund(providerId: String, amountCents: Int) = PaymentStatus.PROCESSING

    override fun verifyWebhook(payload: String, signature: String?): Boolean {
        val values = signature?.split(',')?.mapNotNull { part ->
            part.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] }
        }?.groupBy({ it.first }, { it.second }).orEmpty()
        val timestamp = values["t"]?.firstOrNull()?.toLongOrNull()
        return timestamp != null &&
            kotlin.math.abs(clock.instant().epochSecond - timestamp) <= 300 &&
            values["v1"].orEmpty().any { candidate ->
                val expected = hmacSha256(webhookSecret, "$timestamp.$payload")
                MessageDigest.isEqual(
                    expected.toByteArray(StandardCharsets.UTF_8),
                    candidate.toByteArray(StandardCharsets.UTF_8),
                )
            }
    }

    private fun hmacSha256(secret: String, value: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(value.toByteArray(StandardCharsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}

private data class PaymentRecord(
    val id: UUID,
    val customerId: UUID,
    val bookingId: UUID,
    val providerId: String,
    val amountCents: Int,
    val currency: String,
    var status: PaymentStatus,
    val development: Boolean,
    val createdAt: Instant,
)

class PaymentService(
    private val bookings: BookingService,
    private val provider: PaymentProvider,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val payments = ConcurrentHashMap<UUID, PaymentRecord>()
    private val bookingPayments = ConcurrentHashMap<UUID, UUID>()
    private val ledger = ConcurrentHashMap<UUID, MutableList<LedgerEntryResponse>>()
    private val webhookEvents = ConcurrentHashMap.newKeySet<String>()

    @Synchronized
    fun create(principal: AuthenticatedPrincipal, bookingId: UUID, idempotencyKey: String): PaymentIntentResponse {
        val booking = bookings.detail(principal, bookingId)
        bookingPayments[bookingId]?.let { return response(owned(principal, it)) }
        val total = booking.summary.price.totalCents
        val providerPayment = provider.create(total, "USD", idempotencyKey)
        val record = PaymentRecord(
            UUID.randomUUID(), principal.userId, bookingId, providerPayment.providerId, total, "USD",
            providerPayment.status, provider.developmentAdapter, clock.instant(),
        )
        payments[record.id] = record
        bookingPayments[bookingId] = record.id
        return response(record, providerPayment.clientSecret)
    }

    @Synchronized
    fun authorizeDevelopment(principal: AuthenticatedPrincipal, paymentId: UUID): PaymentIntentResponse {
        val record = owned(principal, paymentId)
        if (!record.development) {
            throw AuthenticationException(
                "DEVELOPMENT_ONLY",
                "Use the Stripe payment component.",
                400,
            )
        }
        record.status = provider.authorize(record.providerId)
        if (record.status == PaymentStatus.AUTHORIZED) {
            bookings.authorizePayment(principal, record.bookingId)
            appendLedger(record, "CUSTOMER_CHARGE", record.amountCents)
            appendLedger(record, "PLATFORM_FEE", -(record.amountCents * 12 / 112))
            appendLedger(record, "HELPER_TRANSFER_PLACEHOLDER", -(record.amountCents - record.amountCents * 12 / 112))
        }
        return response(record)
    }

    fun receipt(principal: AuthenticatedPrincipal, bookingId: UUID): ReceiptResponse {
        bookings.detail(principal, bookingId)
        val payment = bookingPayments[bookingId]?.let(payments::get)
            ?: throw AuthenticationException("PAYMENT_NOT_FOUND", "Receipt not available yet.", 404)
        return ReceiptResponse(
            UUID.nameUUIDFromBytes("receipt-$bookingId".toByteArray()).toString(),
            bookingId.toString(),
            payment.status,
            ledger[payment.id].orEmpty().toList(),
            payment.amountCents,
            payment.currency,
            payment.createdAt.toString(),
        )
    }

    fun webhook(payload: String, signature: String?, eventId: String) {
        if (!provider.verifyWebhook(payload, signature)) {
            throw AuthenticationException("INVALID_WEBHOOK_SIGNATURE", "Invalid webhook signature.", 400)
        }
        if (!webhookEvents.add(eventId)) return
    }

    private fun appendLedger(payment: PaymentRecord, type: String, amount: Int) {
        ledger.computeIfAbsent(payment.id) { mutableListOf() }.add(
            LedgerEntryResponse(
                UUID.randomUUID().toString(),
                type,
                amount,
                payment.currency,
                clock.instant().toString(),
            ),
        )
    }

    private fun owned(principal: AuthenticatedPrincipal, id: UUID): PaymentRecord =
        payments[id]?.takeIf { it.customerId == principal.userId }
            ?: throw AuthenticationException("PAYMENT_NOT_FOUND", "Payment not found.", 404)

    private fun response(record: PaymentRecord, clientSecret: String? = null) = PaymentIntentResponse(
        record.id.toString(),
        record.bookingId.toString(),
        record.status,
        record.amountCents,
        record.currency,
        clientSecret,
        record.development,
    )
}
