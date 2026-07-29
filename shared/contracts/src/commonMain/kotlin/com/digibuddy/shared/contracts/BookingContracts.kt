package com.digibuddy.shared.contracts

import kotlinx.serialization.Serializable

@Serializable
enum class BookingStatus {
    DRAFT,
    REQUESTED,
    AWAITING_HELPER_RESPONSE,
    QUOTED,
    AWAITING_CUSTOMER_APPROVAL,
    AWAITING_PAYMENT_AUTHORIZATION,
    CONFIRMED,
    HELPER_EN_ROUTE,
    HELPER_ARRIVED,
    IN_PROGRESS,
    PAUSED,
    CHANGE_ORDER_PENDING,
    WORK_COMPLETED,
    AWAITING_CUSTOMER_CONFIRMATION,
    COMPLETED,
    CANCELED_BY_CUSTOMER,
    CANCELED_BY_HELPER,
    EXPIRED,
    DISPUTED,
    REFUNDED,
    PARTIALLY_REFUNDED,
}

@Serializable
data class BookingAddressRequest(
    val label: String,
    val line1: String,
    val line2: String? = null,
    val city: String,
    val region: String,
    val zipCode: String,
)

@Serializable
data class BookingPriceBreakdownResponse(
    val laborCents: Int,
    val materialsEstimateCents: Int = 0,
    val travelCents: Int = 0,
    val taxCents: Int = 0,
    val platformFeeCents: Int = 0,
    val discountCents: Int = 0,
    val tipCents: Int = 0,
    val totalCents: Int,
    val currency: String = "USD",
)

@Serializable
data class CreateBookingRequest(
    val helperId: String,
    val helperDisplayName: String,
    val serviceCategory: String,
    val serviceName: String,
    val serviceMode: String,
    val problemDescription: String,
    val diagnosticAnswers: Map<String, String> = emptyMap(),
    val attachmentUploadIds: List<String> = emptyList(),
    val address: BookingAddressRequest? = null,
    val scheduledStart: String,
    val scheduledEnd: String,
    val pricingType: String,
    val expectedLaborCents: Int,
    val cancellationTermsAccepted: Boolean,
    val paymentMethodPlaceholder: String? = null,
)

@Serializable
data class BookingStatusHistoryResponse(val status: BookingStatus, val occurredAt: String, val explanation: String)

@Serializable
data class BookingSummaryResponse(
    val bookingId: String,
    val helperId: String,
    val helperDisplayName: String,
    val serviceName: String,
    val serviceMode: String,
    val scheduledStart: String,
    val status: BookingStatus,
    val statusExplanation: String,
    val generalLocation: String?,
    val price: BookingPriceBreakdownResponse,
    val quoteStatus: String? = null,
    val unreadMessages: Int = 0,
    val nextAction: String? = null,
    val customerDisplayName: String? = null,
)

@Serializable
data class BookingDetailResponse(
    val summary: BookingSummaryResponse,
    val problemDescription: String,
    val diagnosticAnswers: Map<String, String>,
    val attachmentIds: List<String>,
    val address: BookingAddressRequest?,
    val scheduledEnd: String,
    val paymentStatus: String,
    val history: List<BookingStatusHistoryResponse>,
    val allowedActions: List<String>,
)

@Serializable
data class BookingListResponse(val items: List<BookingSummaryResponse>)

@Serializable
data class RescheduleBookingRequest(val scheduledStart: String, val scheduledEnd: String)

@Serializable
data class CancelBookingRequest(val reason: String)

@Serializable
data class BookingMessageResponse(val message: String)
