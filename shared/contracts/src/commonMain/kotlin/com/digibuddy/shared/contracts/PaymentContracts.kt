package com.digibuddy.shared.contracts

import kotlinx.serialization.Serializable

@Serializable
enum class PaymentStatus {
    REQUIRES_PAYMENT_METHOD,
    REQUIRES_AUTHORIZATION,
    PROCESSING,
    AUTHORIZED,
    CAPTURED,
    FAILED,
    CANCELED,
    PARTIALLY_REFUNDED,
    REFUNDED,
}

@Serializable
data class CreatePaymentIntentRequest(val bookingId: String, val paymentMethodType: String = "DEVELOPMENT")

@Serializable
data class PaymentIntentResponse(
    val paymentId: String,
    val bookingId: String,
    val status: PaymentStatus,
    val amountCents: Int,
    val currency: String,
    val clientSecret: String? = null,
    val developmentAdapter: Boolean,
)

@Serializable
data class AuthorizeDevelopmentPaymentRequest(val paymentId: String)

@Serializable
data class LedgerEntryResponse(
    val entryId: String,
    val type: String,
    val amountCents: Int,
    val currency: String,
    val createdAt: String,
)

@Serializable
data class ReceiptResponse(
    val receiptId: String,
    val bookingId: String,
    val paymentStatus: PaymentStatus,
    val entries: List<LedgerEntryResponse>,
    val totalCents: Int,
    val currency: String,
    val issuedAt: String,
)
