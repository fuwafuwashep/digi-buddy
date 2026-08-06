package com.digibuddy.backend.booking

import com.digibuddy.shared.contracts.BookingStatus
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

interface BookingRepository {
    fun create(record: BookingRecord, idempotencyKey: String)

    fun findById(id: UUID): BookingRecord?

    fun findByIdempotency(
        customerId: UUID,
        idempotencyKey: String,
    ): BookingRecord?

    fun listForCustomer(customerId: UUID): List<BookingRecord>

    fun listForHelper(helperUserId: UUID): List<BookingRecord>

    fun updateSchedule(record: BookingRecord)

    fun saveTransition(
        record: BookingRecord,
        fromStatus: BookingStatus,
        actor: BookingActor,
        actorUserId: UUID?,
        occurredAt: Instant,
    )

    fun hasConfirmedOverlap(
        helperProfileId: UUID,
        start: Instant,
        end: Instant,
        excludingBookingId: UUID,
    ): Boolean
}

class InMemoryBookingRepository : BookingRepository {
    private val bookings = ConcurrentHashMap<UUID, BookingRecord>()
    private val idempotency = ConcurrentHashMap<String, UUID>()

    override fun create(
        record: BookingRecord,
        idempotencyKey: String,
    ) {
        bookings[record.id] = record
        idempotency[idempotencyIndex(record.customerId, idempotencyKey)] = record.id
    }

    override fun findById(id: UUID): BookingRecord? = bookings[id]

    override fun findByIdempotency(
        customerId: UUID,
        idempotencyKey: String,
    ): BookingRecord? {
        val bookingId = idempotency[idempotencyIndex(customerId, idempotencyKey)]
            ?: return null

        return bookings[bookingId]
    }

    override fun listForCustomer(customerId: UUID): List<BookingRecord> =
        bookings.values
            .filter { it.customerId == customerId }
            .sortedByDescending { it.createdAt }

    override fun listForHelper(helperUserId: UUID): List<BookingRecord> =
        bookings.values
            .filter { it.helperUserId == helperUserId }
            .sortedByDescending { it.createdAt }

    override fun updateSchedule(record: BookingRecord) {
        bookings[record.id] = record
    }

    override fun saveTransition(
        record: BookingRecord,
        fromStatus: BookingStatus,
        actor: BookingActor,
        actorUserId: UUID?,
        occurredAt: Instant,
    ) {
        bookings[record.id] = record
    }

    override fun hasConfirmedOverlap(
        helperProfileId: UUID,
        start: Instant,
        end: Instant,
        excludingBookingId: UUID,
    ): Boolean =
        bookings.values.any { existing ->
            existing.id != excludingBookingId &&
                existing.request.helperId == helperProfileId.toString() &&
                existing.status == BookingStatus.CONFIRMED &&
                start.isBefore(existing.scheduledEnd) &&
                end.isAfter(existing.scheduledStart)
        }

    private fun idempotencyIndex(
        customerId: UUID,
        idempotencyKey: String,
    ): String = "$customerId:$idempotencyKey"
}
