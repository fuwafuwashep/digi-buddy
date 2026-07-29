package com.digibuddy.customer

import com.digibuddy.shared.contracts.BookingAddressRequest
import com.digibuddy.shared.contracts.BookingDetailResponse
import com.digibuddy.shared.contracts.BookingSummaryResponse
import com.digibuddy.shared.contracts.CreateBookingRequest
import com.digibuddy.shared.contracts.HelperProfileResponse
import com.digibuddy.shared.contracts.PaymentIntentResponse
import com.digibuddy.shared.contracts.ReceiptResponse
import com.digibuddy.shared.core.DigibuddyPricing
import com.digibuddy.shared.networking.BookingApiClient
import com.digibuddy.shared.networking.PaymentApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

data class BookingDraft(
    val helper: HelperProfileResponse,
    val step: Int = 0,
    val serviceIndex: Int = 0,
    val mode: String = "IN_PERSON",
    val description: String = "",
    val addressLine: String = "",
    val city: String = "",
    val region: String = "",
    val zipCode: String = "",
    val scheduledStart: String = "2026-08-01T15:00:00Z",
    val scheduledEnd: String = "2026-08-01T16:00:00Z",
    val termsAccepted: Boolean = false,
    val submitting: Boolean = false,
    val error: String? = null,
)

data class BookingUiState(
    val loading: Boolean = false,
    val bookings: List<BookingSummaryResponse> = emptyList(),
    val selected: BookingDetailResponse? = null,
    val draft: BookingDraft? = null,
    val error: String? = null,
    val payment: PaymentIntentResponse? = null,
    val receipt: ReceiptResponse? = null,
)

internal fun initialBookingDraft(helper: HelperProfileResponse, customerZipCode: String) =
    BookingDraft(helper = helper, zipCode = customerZipCode)

class BookingCoordinator(
    private val api: BookingApiClient,
    private val payments: PaymentApiClient,
    private val accessToken: String,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val mutableState = MutableStateFlow(BookingUiState())
    val state = mutableState.asStateFlow()

    fun load() = scope.launch {
        mutableState.value = mutableState.value.copy(loading = true)
        runCatching { api.list(accessToken).items }
            .onSuccess { mutableState.value = mutableState.value.copy(loading = false, bookings = it, error = null) }
            .onFailure {
                mutableState.value =
                    mutableState.value.copy(loading = false, error = "Bookings could not be refreshed.")
            }
    }

    fun start(helper: HelperProfileResponse, customerZipCode: String) {
        mutableState.value =
            mutableState.value.copy(
                draft = initialBookingDraft(helper, customerZipCode),
                selected = null,
            )
    }

    fun updateDraft(change: (BookingDraft) -> BookingDraft) {
        mutableState.value = mutableState.value.copy(draft = mutableState.value.draft?.let(change))
    }

    fun cancelDraft() {
        mutableState.value = mutableState.value.copy(draft = null)
    }

    fun submit() {
        val draft = mutableState.value.draft ?: return
        val service = draft.helper.services.getOrNull(draft.serviceIndex) ?: return
        updateDraft { it.copy(submitting = true, error = null) }
        scope.launch {
            val address = if (draft.mode == "IN_PERSON") {
                BookingAddressRequest(
                    "Service address",
                    draft.addressLine,
                    city = draft.city,
                    region = draft.region,
                    zipCode = draft.zipCode,
                )
            } else {
                null
            }
            val request = CreateBookingRequest(
                helperId = draft.helper.summary.helperId,
                helperDisplayName = draft.helper.summary.displayName,
                serviceCategory = service.categorySlug,
                serviceName = service.name,
                serviceMode = draft.mode,
                problemDescription = draft.description,
                address = address,
                scheduledStart = draft.scheduledStart,
                scheduledEnd = draft.scheduledEnd,
                pricingType = service.pricingType,
                expectedLaborCents = DigibuddyPricing.bookingLaborCents(draft.mode),
                cancellationTermsAccepted = draft.termsAccepted,
                paymentMethodPlaceholder = "Development payment method",
            )
            runCatching {
                api.create(accessToken, "customer-${Random.nextLong().toString().replace('-', '0')}", request)
            }.onSuccess {
                mutableState.value = mutableState.value.copy(draft = null, selected = it)
                load()
            }.onFailure {
                updateDraft { current ->
                    current.copy(
                        submitting = false,
                        error = "We could not send your request. Your answers are still here.",
                    )
                }
            }
        }
    }

    fun open(id: String) = scope.launch {
        runCatching { api.detail(accessToken, id) }.onSuccess {
            mutableState.value =
                mutableState.value.copy(selected = it)
        }
    }

    fun closeDetail() {
        mutableState.value = mutableState.value.copy(selected = null)
    }

    fun cancel(id: String) = command { api.cancel(accessToken, id, "Plans changed") }
    fun acceptQuote(id: String) = command { api.acceptQuote(accessToken, id) }
    fun confirmCompletion(id: String) = command { api.confirmCompletion(accessToken, id) }

    fun startPayment(bookingId: String) = scope.launch {
        runCatching {
            payments.create(
                accessToken,
                bookingId,
                "payment-${Random.nextLong().toString().replace('-', '0')}",
            )
        }.onSuccess { mutableState.value = mutableState.value.copy(payment = it) }
            .onFailure { mutableState.value = mutableState.value.copy(error = "Payment could not be started.") }
    }

    fun authorizeDevelopmentPayment() = scope.launch {
        val payment = mutableState.value.payment ?: return@launch
        runCatching { payments.authorizeDevelopment(accessToken, payment.paymentId) }
            .onSuccess {
                mutableState.value = mutableState.value.copy(payment = it)
                open(it.bookingId)
            }.onFailure { mutableState.value = mutableState.value.copy(error = "Payment was not authorized.") }
    }

    fun loadReceipt(bookingId: String) = scope.launch {
        runCatching { payments.receipt(accessToken, bookingId) }
            .onSuccess { mutableState.value = mutableState.value.copy(receipt = it) }
    }

    fun closePayment() {
        mutableState.value = mutableState.value.copy(payment = null, receipt = null)
    }

    private fun command(block: suspend () -> BookingDetailResponse) = scope.launch {
        runCatching { block() }.onSuccess {
            mutableState.value = mutableState.value.copy(selected = it)
            load()
        }.onFailure { mutableState.value = mutableState.value.copy(error = "That action is not available right now.") }
    }
}
