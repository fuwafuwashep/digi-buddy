@file:Suppress("TooManyFunctions", "LongParameterList")

package com.digibuddy.backend.booking

import com.digibuddy.backend.auth.AuthenticatedPrincipal
import com.digibuddy.backend.auth.AuthenticationException
import com.digibuddy.shared.contracts.BookingDetailResponse
import com.digibuddy.shared.contracts.BookingListResponse
import com.digibuddy.shared.contracts.BookingPriceBreakdownResponse
import com.digibuddy.shared.contracts.BookingStatus
import com.digibuddy.shared.contracts.BookingStatusHistoryResponse
import com.digibuddy.shared.contracts.BookingSummaryResponse
import com.digibuddy.shared.contracts.CancelBookingRequest
import com.digibuddy.shared.contracts.CreateBookingRequest
import com.digibuddy.shared.contracts.RescheduleBookingRequest
import com.digibuddy.shared.core.DigibuddyPricing
import java.time.Clock
import java.time.Instant
import java.util.UUID

enum class BookingActor {
    CUSTOMER,
    HELPER,
    SYSTEM,
    SUPPORT,
}

data class TransitionContext(
    val actor: BookingActor,
    val paymentAuthorized: Boolean = false,
    val refundRecorded: Boolean = false,
)

object BookingStateMachine {
    private val transitions:
        Map<BookingStatus, Map<BookingStatus, Set<BookingActor>>> =
        mapOf(
            BookingStatus.DRAFT to mapOf(
                BookingStatus.REQUESTED to
                    setOf(BookingActor.CUSTOMER),
            ),
            BookingStatus.REQUESTED to mapOf(
                BookingStatus.AWAITING_HELPER_RESPONSE to
                    setOf(BookingActor.SYSTEM),
            ),
            BookingStatus.AWAITING_HELPER_RESPONSE to mapOf(
                BookingStatus.QUOTED to
                    setOf(BookingActor.HELPER),
                BookingStatus.CONFIRMED to
                    setOf(
                        BookingActor.HELPER,
                        BookingActor.SYSTEM,
                    ),
                BookingStatus.CANCELED_BY_CUSTOMER to
                    setOf(BookingActor.CUSTOMER),
                BookingStatus.CANCELED_BY_HELPER to
                    setOf(BookingActor.HELPER),
                BookingStatus.EXPIRED to
                    setOf(BookingActor.SYSTEM),
            ),
            BookingStatus.QUOTED to mapOf(
                BookingStatus.AWAITING_CUSTOMER_APPROVAL to
                    setOf(BookingActor.SYSTEM),
            ),
            BookingStatus.AWAITING_CUSTOMER_APPROVAL to mapOf(
                BookingStatus.AWAITING_PAYMENT_AUTHORIZATION to
                    setOf(BookingActor.CUSTOMER),
                BookingStatus.CANCELED_BY_CUSTOMER to
                    setOf(BookingActor.CUSTOMER),
                BookingStatus.EXPIRED to
                    setOf(BookingActor.SYSTEM),
            ),
            BookingStatus.AWAITING_PAYMENT_AUTHORIZATION to
                mapOf(
                    BookingStatus.CONFIRMED to
                        setOf(BookingActor.SYSTEM),
                    BookingStatus.CANCELED_BY_CUSTOMER to
                        setOf(BookingActor.CUSTOMER),
                    BookingStatus.EXPIRED to
                        setOf(BookingActor.SYSTEM),
                ),
            BookingStatus.CONFIRMED to mapOf(
                BookingStatus.HELPER_EN_ROUTE to
                    setOf(BookingActor.HELPER),
                BookingStatus.HELPER_ARRIVED to
                    setOf(BookingActor.HELPER),
                BookingStatus.IN_PROGRESS to
                    setOf(BookingActor.HELPER),
                BookingStatus.CANCELED_BY_CUSTOMER to
                    setOf(
                        BookingActor.CUSTOMER,
                        BookingActor.SUPPORT,
                    ),
                BookingStatus.CANCELED_BY_HELPER to
                    setOf(
                        BookingActor.HELPER,
                        BookingActor.SUPPORT,
                    ),
            ),
            BookingStatus.HELPER_EN_ROUTE to mapOf(
                BookingStatus.HELPER_ARRIVED to
                    setOf(BookingActor.HELPER),
                BookingStatus.CANCELED_BY_HELPER to
                    setOf(
                        BookingActor.HELPER,
                        BookingActor.SUPPORT,
                    ),
            ),
            BookingStatus.HELPER_ARRIVED to mapOf(
                BookingStatus.IN_PROGRESS to
                    setOf(BookingActor.HELPER),
                BookingStatus.CANCELED_BY_HELPER to
                    setOf(
                        BookingActor.HELPER,
                        BookingActor.SUPPORT,
                    ),
            ),
            BookingStatus.IN_PROGRESS to mapOf(
                BookingStatus.PAUSED to
                    setOf(BookingActor.HELPER),
                BookingStatus.CHANGE_ORDER_PENDING to
                    setOf(BookingActor.HELPER),
                BookingStatus.WORK_COMPLETED to
                    setOf(BookingActor.HELPER),
            ),
            BookingStatus.PAUSED to mapOf(
                BookingStatus.IN_PROGRESS to
                    setOf(BookingActor.HELPER),
                BookingStatus.CHANGE_ORDER_PENDING to
                    setOf(BookingActor.HELPER),
            ),
            BookingStatus.CHANGE_ORDER_PENDING to mapOf(
                BookingStatus.IN_PROGRESS to
                    setOf(BookingActor.CUSTOMER),
                BookingStatus.CANCELED_BY_CUSTOMER to
                    setOf(
                        BookingActor.CUSTOMER,
                        BookingActor.SUPPORT,
                    ),
            ),
            BookingStatus.WORK_COMPLETED to mapOf(
                BookingStatus.AWAITING_CUSTOMER_CONFIRMATION to
                    setOf(BookingActor.SYSTEM),
            ),
            BookingStatus.AWAITING_CUSTOMER_CONFIRMATION to
                mapOf(
                    BookingStatus.COMPLETED to
                        setOf(
                            BookingActor.CUSTOMER,
                            BookingActor.SYSTEM,
                        ),
                    BookingStatus.DISPUTED to
                        setOf(
                            BookingActor.CUSTOMER,
                            BookingActor.SUPPORT,
                        ),
                ),
            BookingStatus.COMPLETED to mapOf(
                BookingStatus.DISPUTED to
                    setOf(
                        BookingActor.CUSTOMER,
                        BookingActor.SUPPORT,
                    ),
                BookingStatus.REFUNDED to
                    setOf(
                        BookingActor.SYSTEM,
                        BookingActor.SUPPORT,
                    ),
                BookingStatus.PARTIALLY_REFUNDED to
                    setOf(
                        BookingActor.SYSTEM,
                        BookingActor.SUPPORT,
                    ),
            ),
            BookingStatus.DISPUTED to mapOf(
                BookingStatus.REFUNDED to
                    setOf(
                        BookingActor.SYSTEM,
                        BookingActor.SUPPORT,
                    ),
                BookingStatus.PARTIALLY_REFUNDED to
                    setOf(
                        BookingActor.SYSTEM,
                        BookingActor.SUPPORT,
                    ),
                BookingStatus.COMPLETED to
                    setOf(BookingActor.SUPPORT),
            ),
        )

    fun requireAllowed(
        from: BookingStatus,
        to: BookingStatus,
        context: TransitionContext,
    ) {
        val error = when {
            context.actor !in
                transitions[from]
                    .orEmpty()[to]
                    .orEmpty() ->
                Triple(
                    "INVALID_BOOKING_TRANSITION",
                    "That booking action is not available.",
                    409,
                )

            from ==
                BookingStatus.AWAITING_PAYMENT_AUTHORIZATION &&
                to == BookingStatus.CONFIRMED &&
                !context.paymentAuthorized ->
                Triple(
                    "PAYMENT_REQUIRED",
                    "Payment authorization is required.",
                    409,
                )

            to in setOf(
                BookingStatus.REFUNDED,
                BookingStatus.PARTIALLY_REFUNDED,
            ) &&
                !context.refundRecorded ->
                Triple(
                    "REFUND_REQUIRED",
                    "A verified refund record is required.",
                    409,
                )

            else -> null
        }

        error?.let {
            throw AuthenticationException(
                it.first,
                it.second,
                it.third,
            )
        }
    }

    fun allowedActions(
        status: BookingStatus,
    ): List<String> =
        when (status) {
            BookingStatus.AWAITING_CUSTOMER_APPROVAL ->
                listOf(
                    "ACCEPT_QUOTE",
                    "REJECT_QUOTE",
                    "CANCEL",
                )

            BookingStatus.AWAITING_CUSTOMER_CONFIRMATION ->
                listOf(
                    "CONFIRM_COMPLETION",
                    "DISPUTE",
                )

            BookingStatus.REQUESTED,
            BookingStatus.AWAITING_HELPER_RESPONSE,
                ->
                listOf(
                    "RESCHEDULE",
                    "CANCEL",
                )

            BookingStatus.CONFIRMED ->
                listOf(
                    "RESCHEDULE_REQUEST",
                    "CANCEL",
                    "MESSAGE",
                    "SUPPORT",
                )

            BookingStatus.COMPLETED ->
                listOf(
                    "REVIEW",
                    "RECEIPT",
                    "SUPPORT",
                    "DISPUTE",
                )

            else -> emptyList()
        }
}

data class BookingRecord(
    val id: UUID,
    val customerId: UUID,
    val helperUserId: UUID,
    val customerDisplayName: String,
    val request: CreateBookingRequest,
    var status: BookingStatus,
    val price: BookingPriceBreakdownResponse,
    val createdAt: Instant,
    var scheduledStart: Instant,
    var scheduledEnd: Instant,
    val history: MutableList<BookingStatusHistoryResponse>,
)

class BookingService(
    private val clock: Clock = Clock.systemUTC(),
    private val repository: BookingRepository =
        InMemoryBookingRepository(),
    private val helperAccountResolver:
        (UUID) -> Pair<UUID, String>? =
        { it to "Helper" },
    private val customerDisplayName:
        (UUID) -> String =
        { "Customer" },
    private val requireHelperEligibility:
        (AuthenticatedPrincipal) -> Unit =
        {},
    private val onBookingCreated:
        (BookingRecord) -> Unit =
        {},
) {

    @Synchronized
    fun create(
        principal: AuthenticatedPrincipal,
        key: String,
        request: CreateBookingRequest,
    ): BookingDetailResponse {
        if (key.length !in 8..128) {
            invalid("Provide a valid idempotency key.")
        }

        repository.findByIdempotency(
            customerId = principal.userId,
            idempotencyKey = key,
        )?.let {
            return it.response()
        }

        validate(request)

        val helperCatalogId =
            runCatching {
                UUID.fromString(request.helperId)
            }.getOrElse {
                invalid("Choose an available helper.")
            }

        val helperAccount =
            helperAccountResolver(helperCatalogId)
                ?: throw AuthenticationException(
                    "HELPER_UNAVAILABLE",
                    "This helper is not available for requests.",
                    409,
                )

        val start = parseTime(request.scheduledStart)
        val end = parseTime(request.scheduledEnd)

        if (!end.isAfter(start)) {
            invalid(
                "The appointment end must be after its start.",
            )
        }

        val laborCents =
            DigibuddyPricing.bookingLaborCents(
                request.serviceMode,
            )

        val price = BookingPriceBreakdownResponse(
            laborCents = laborCents,
            platformFeeCents = 0,
            totalCents = laborCents,
        )

        val id = UUID.randomUUID()
        val now = clock.instant()

        val record = BookingRecord(
            id = id,
            customerId = principal.userId,
            helperUserId = helperAccount.first,
            customerDisplayName =
                customerDisplayName(principal.userId),
            request = request.copy(
                helperDisplayName = helperAccount.second,
            ),
            status =
                BookingStatus.AWAITING_HELPER_RESPONSE,
            price = price,
            createdAt = now,
            scheduledStart = start,
            scheduledEnd = end,
            history = mutableListOf(
                history(BookingStatus.REQUESTED, now),
                history(
                    BookingStatus.AWAITING_HELPER_RESPONSE,
                    now,
                ),
            ),
        )

        repository.create(
            record = record,
            idempotencyKey = key,
        )

        onBookingCreated(record)
        return record.response()
    }

    fun list(
        principal: AuthenticatedPrincipal,
    ): BookingListResponse =
        BookingListResponse(
            repository
                .listForCustomer(principal.userId)
                .map { it.summary() },
        )

    fun detail(
        principal: AuthenticatedPrincipal,
        bookingId: UUID,
    ): BookingDetailResponse =
        owned(principal, bookingId).response()

    fun helperRequests(
        principal: AuthenticatedPrincipal,
    ): BookingListResponse =
        helperList(
            principal = principal,
            statuses = setOf(
                BookingStatus.AWAITING_HELPER_RESPONSE,
            ),
        )

    fun helperJobs(
        principal: AuthenticatedPrincipal,
    ): BookingListResponse =
        helperList(
            principal = principal,
            statuses =
                BookingStatus.entries.toSet() -
                    setOf(
                        BookingStatus.DRAFT,
                        BookingStatus.REQUESTED,
                        BookingStatus
                            .AWAITING_HELPER_RESPONSE,
                    ),
        )

    fun helperDetail(
        principal: AuthenticatedPrincipal,
        bookingId: UUID,
    ): BookingDetailResponse {
        requireHelperEligibility(principal)

        return helperOwned(
            principal,
            bookingId,
        ).response(helperView = true)
    }

    @Synchronized
    fun helperAccept(
        principal: AuthenticatedPrincipal,
        bookingId: UUID,
    ): BookingDetailResponse {
        requireHelperEligibility(principal)

        val record = helperOwned(
            principal,
            bookingId,
        )

        transitionRecord(
            record = record,
            target = BookingStatus.QUOTED,
            context =
                TransitionContext(BookingActor.HELPER),
            actorUserId = principal.userId,
        )

        transitionRecord(
            record = record,
            target =
                BookingStatus.AWAITING_CUSTOMER_APPROVAL,
            context =
                TransitionContext(BookingActor.SYSTEM),
            actorUserId = null,
        )

        return record.response(helperView = true)
    }

    @Synchronized
    fun helperDecline(
        principal: AuthenticatedPrincipal,
        bookingId: UUID,
    ): BookingDetailResponse {
        requireHelperEligibility(principal)

        val record = helperOwned(
            principal,
            bookingId,
        )

        transitionRecord(
            record = record,
            target =
                BookingStatus.CANCELED_BY_HELPER,
            context =
                TransitionContext(BookingActor.HELPER),
            actorUserId = principal.userId,
        )

        return record.response(helperView = true)
    }

    fun authorizePayment(
        principal: AuthenticatedPrincipal,
        bookingId: UUID,
    ): BookingDetailResponse =
        transition(
            principal = principal,
            bookingId = bookingId,
            target = BookingStatus.CONFIRMED,
            context = TransitionContext(
                actor = BookingActor.SYSTEM,
                paymentAuthorized = true,
            ),
        )

    @Synchronized
    fun cancel(
        principal: AuthenticatedPrincipal,
        bookingId: UUID,
        request: CancelBookingRequest,
    ): BookingDetailResponse {
        if (request.reason.trim().length !in 3..500) {
            invalid(
                "Tell us briefly why you need to cancel.",
            )
        }

        return transition(
            principal = principal,
            bookingId = bookingId,
            target =
                BookingStatus.CANCELED_BY_CUSTOMER,
            context =
                TransitionContext(BookingActor.CUSTOMER),
        )
    }

    @Synchronized
    fun acceptQuote(
        principal: AuthenticatedPrincipal,
        bookingId: UUID,
    ): BookingDetailResponse =
        transition(
            principal = principal,
            bookingId = bookingId,
            target =
                BookingStatus
                    .AWAITING_PAYMENT_AUTHORIZATION,
            context =
                TransitionContext(BookingActor.CUSTOMER),
        )

    @Synchronized
    fun confirmCompletion(
        principal: AuthenticatedPrincipal,
        bookingId: UUID,
    ): BookingDetailResponse =
        transition(
            principal = principal,
            bookingId = bookingId,
            target = BookingStatus.COMPLETED,
            context =
                TransitionContext(BookingActor.CUSTOMER),
        )

    @Synchronized
    fun reschedule(
        principal: AuthenticatedPrincipal,
        bookingId: UUID,
        request: RescheduleBookingRequest,
    ): BookingDetailResponse {
        val record = owned(principal, bookingId)

        if (
            record.status !in
            setOf(
                BookingStatus.REQUESTED,
                BookingStatus.AWAITING_HELPER_RESPONSE,
                BookingStatus.CONFIRMED,
            )
        ) {
            throw AuthenticationException(
                "RESCHEDULE_NOT_ALLOWED",
                "This booking cannot be rescheduled now.",
                409,
            )
        }

        val start = parseTime(request.scheduledStart)
        val end = parseTime(request.scheduledEnd)

        if (!end.isAfter(start)) {
            invalid(
                "The appointment end must be after its start.",
            )
        }

        preventOverlap(
            helperId = record.request.helperId,
            start = start,
            end = end,
            excluding = record.id,
        )

        record.scheduledStart = start
        record.scheduledEnd = end

        repository.updateSchedule(record)
        return record.response()
    }

    @Synchronized
    internal fun transition(
        principal: AuthenticatedPrincipal,
        bookingId: UUID,
        target: BookingStatus,
        context: TransitionContext,
    ): BookingDetailResponse {
        val record = owned(principal, bookingId)

        val actorUserId =
            when (context.actor) {
                BookingActor.CUSTOMER,
                BookingActor.HELPER,
                BookingActor.SUPPORT,
                    -> principal.userId

                BookingActor.SYSTEM -> null
            }

        transitionRecord(
            record = record,
            target = target,
            context = context,
            actorUserId = actorUserId,
        )

        return record.response()
    }

    private fun transitionRecord(
        record: BookingRecord,
        target: BookingStatus,
        context: TransitionContext,
        actorUserId: UUID?,
    ) {
        val fromStatus = record.status

        BookingStateMachine.requireAllowed(
            from = fromStatus,
            to = target,
            context = context,
        )

        if (target == BookingStatus.CONFIRMED) {
            preventOverlap(
                helperId = record.request.helperId,
                start = record.scheduledStart,
                end = record.scheduledEnd,
                excluding = record.id,
            )
        }

        val occurredAt = clock.instant()

        record.status = target
        record.history += history(target, occurredAt)

        repository.saveTransition(
            record = record,
            fromStatus = fromStatus,
            actor = context.actor,
            actorUserId = actorUserId,
            occurredAt = occurredAt,
        )
    }

    private fun preventOverlap(
        helperId: String,
        start: Instant,
        end: Instant,
        excluding: UUID,
    ) {
        val helperProfileId =
            runCatching {
                UUID.fromString(helperId)
            }.getOrElse {
                invalid("Choose an available helper.")
            }

        val overlaps =
            repository.hasConfirmedOverlap(
                helperProfileId = helperProfileId,
                start = start,
                end = end,
                excludingBookingId = excluding,
            )

        if (overlaps) {
            throw AuthenticationException(
                "TIME_UNAVAILABLE",
                "That time is no longer available.",
                409,
            )
        }
    }

    private fun owned(
        principal: AuthenticatedPrincipal,
        id: UUID,
    ): BookingRecord =
        repository.findById(id)
            ?.takeIf {
                it.customerId == principal.userId
            }
            ?: throw AuthenticationException(
                "BOOKING_NOT_FOUND",
                "Booking not found.",
                404,
            )

    private fun helperOwned(
        principal: AuthenticatedPrincipal,
        id: UUID,
    ): BookingRecord =
        repository.findById(id)
            ?.takeIf {
                it.helperUserId == principal.userId
            }
            ?: throw AuthenticationException(
                "BOOKING_NOT_FOUND",
                "Booking not found.",
                404,
            )

    private fun helperList(
        principal: AuthenticatedPrincipal,
        statuses: Set<BookingStatus>,
    ): BookingListResponse {
        requireHelperEligibility(principal)

        return BookingListResponse(
            repository
                .listForHelper(principal.userId)
                .filter { it.status in statuses }
                .map {
                    it.summary(helperView = true)
                },
        )
    }

    private fun validate(
        request: CreateBookingRequest,
    ) {
        if (
            request.helperId.isBlank() ||
            request.serviceName.isBlank()
        ) {
            invalid(
                "Choose a helper and service.",
            )
        }

        if (
            request.serviceMode !in
            setOf("REMOTE", "IN_PERSON")
        ) {
            invalid(
                "Choose remote or in-person service.",
            )
        }

        if (
            request.problemDescription
                .trim()
                .length !in 10..2_000
        ) {
            invalid(
                "Describe the problem in at least 10 characters.",
            )
        }

        if (
            request.serviceMode == "IN_PERSON" &&
            request.address == null
        ) {
            invalid(
                "An address is required for in-person help.",
            )
        }

        if (!request.cancellationTermsAccepted) {
            invalid(
                "Review and accept the cancellation terms.",
            )
        }

        if (
            request.expectedLaborCents !in
            0..1_000_000
        ) {
            invalid(
                "Enter a valid expected price.",
            )
        }

        if (request.attachmentUploadIds.size > 5) {
            invalid(
                "Attach no more than five files.",
            )
        }
    }

    private fun BookingRecord.response(
        helperView: Boolean = false,
    ): BookingDetailResponse =
        BookingDetailResponse(
            summary = summary(helperView),
            problemDescription =
                request.problemDescription,
            diagnosticAnswers =
                request.diagnosticAnswers,
            attachmentIds =
                request.attachmentUploadIds,
            address = request.address,
            scheduledEnd = scheduledEnd.toString(),
            paymentStatus =
                if (
                    status ==
                    BookingStatus
                        .AWAITING_PAYMENT_AUTHORIZATION
                ) {
                    "ACTION_NEEDED"
                } else {
                    "NOT_REQUIRED_YET"
                },
            history = history.toList(),
            allowedActions =
                if (
                    helperView &&
                    status ==
                    BookingStatus
                        .AWAITING_HELPER_RESPONSE
                ) {
                    listOf(
                        "ACCEPT",
                        "DECLINE",
                        "MESSAGE",
                    )
                } else {
                    BookingStateMachine
                        .allowedActions(status)
                },
        )

    private fun BookingRecord.summary(
        helperView: Boolean = false,
    ): BookingSummaryResponse =
        BookingSummaryResponse(
            bookingId = id.toString(),
            helperId = request.helperId,
            helperDisplayName =
                request.helperDisplayName,
            serviceName = request.serviceName,
            serviceMode = request.serviceMode,
            scheduledStart =
                scheduledStart.toString(),
            status = status,
            statusExplanation =
                statusExplanation(status),
            generalLocation =
                request.address?.let {
                    "${it.city}, ${it.region}"
                },
            price = price,
            quoteStatus =
                if (
                    status in
                    setOf(
                        BookingStatus.QUOTED,
                        BookingStatus
                            .AWAITING_CUSTOMER_APPROVAL,
                    )
                ) {
                    "READY"
                } else {
                    null
                },
            nextAction =
                BookingStateMachine
                    .allowedActions(status)
                    .firstOrNull()
                    ?.let(::plainAction),
            customerDisplayName =
                customerDisplayName
                    .takeIf { helperView },
        )

    private fun history(
        status: BookingStatus,
        time: Instant,
    ): BookingStatusHistoryResponse =
        BookingStatusHistoryResponse(
            status = status,
            occurredAt = time.toString(),
            explanation =
                statusExplanation(status),
        )

    private fun parseTime(
        value: String,
    ): Instant =
        runCatching {
            Instant.parse(value)
        }.getOrElse {
            invalid(
                "Choose a valid appointment time.",
            )
        }

    private fun invalid(
        message: String,
    ): Nothing =
        throw AuthenticationException(
            "INVALID_BOOKING",
            message,
            400,
        )
}

@Suppress("CyclomaticComplexMethod")
internal fun statusExplanation(
    status: BookingStatus,
): String =
    when (status) {
        BookingStatus.DRAFT ->
            "Your request is saved as a draft."

        BookingStatus.REQUESTED ->
            "Your request was sent."

        BookingStatus.AWAITING_HELPER_RESPONSE ->
            "Waiting for the helper to respond."

        BookingStatus.QUOTED,
        BookingStatus.AWAITING_CUSTOMER_APPROVAL,
            ->
            "A quote is ready for your review."

        BookingStatus.AWAITING_PAYMENT_AUTHORIZATION ->
            "Choose a payment method to confirm."

        BookingStatus.CONFIRMED ->
            "Your appointment is confirmed."

        BookingStatus.HELPER_EN_ROUTE ->
            "Your helper is on the way."

        BookingStatus.HELPER_ARRIVED ->
            "Your helper has arrived."

        BookingStatus.IN_PROGRESS ->
            "Help is in progress."

        BookingStatus.PAUSED ->
            "Work is temporarily paused."

        BookingStatus.CHANGE_ORDER_PENDING ->
            "A change needs your approval."

        BookingStatus.WORK_COMPLETED,
        BookingStatus.AWAITING_CUSTOMER_CONFIRMATION,
            ->
            "The helper marked the work complete. Please confirm."

        BookingStatus.COMPLETED ->
            "This booking is complete."

        BookingStatus.CANCELED_BY_CUSTOMER ->
            "You canceled this booking."

        BookingStatus.CANCELED_BY_HELPER ->
            "The helper canceled this booking."

        BookingStatus.EXPIRED ->
            "This request expired before confirmation."

        BookingStatus.DISPUTED ->
            "Support is reviewing this booking."

        BookingStatus.REFUNDED ->
            "This booking was refunded."

        BookingStatus.PARTIALLY_REFUNDED ->
            "A partial refund was issued."
    }

private fun plainAction(
    value: String,
): String =
    value
        .lowercase()
        .replace('_', ' ')
        .replaceFirstChar(Char::uppercase)
