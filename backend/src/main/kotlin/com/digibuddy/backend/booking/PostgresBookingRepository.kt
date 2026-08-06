@file:Suppress(
    "TooGenericExceptionCaught",
    "TooManyFunctions",
    "LongParameterList",
)

package com.digibuddy.backend.booking

import com.digibuddy.backend.auth.AuthenticationException
import com.digibuddy.shared.contracts.BookingPriceBreakdownResponse
import com.digibuddy.shared.contracts.BookingStatus
import com.digibuddy.shared.contracts.BookingStatusHistoryResponse
import com.digibuddy.shared.contracts.CreateBookingRequest
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

class PostgresBookingRepository(
    jdbcUrl: String,
    username: String,
    password: String,
) : BookingRepository, AutoCloseable {

    private val dataSource = HikariDataSource(
        HikariConfig().apply {
            this.jdbcUrl = jdbcUrl
            this.username = username
            this.password = password
            maximumPoolSize = 6
            minimumIdle = 1
            poolName = "digibuddy-bookings"
        },
    )

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    override fun create(
        record: BookingRecord,
        idempotencyKey: String,
    ) {
        dataSource.connection.use { connection ->
            connection.autoCommit = false

            try {
                val serviceCategoryId = resolveServiceCategoryId(
                    connection = connection,
                    categorySlug = record.request.serviceCategory,
                    serviceName = record.request.serviceName,
                )

                connection.prepareStatement(
                    """
                    INSERT INTO bookings (
                        id,
                        customer_user_id,
                        helper_profile_id,
                        helper_user_id,
                        service_category_id,
                        service_mode,
                        pricing_type,
                        problem_description,
                        status,
                        scheduled_start,
                        scheduled_end,
                        version,
                        idempotency_key,
                        created_at,
                        updated_at,
                        customer_display_name,
                        request_json,
                        price_json
                    )
                    VALUES (
                        ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                        CAST(? AS JSONB),
                        CAST(? AS JSONB)
                    )
                    """.trimIndent(),
                ).use { statement ->
                    statement.setObject(1, record.id)
                    statement.setObject(2, record.customerId)
                    statement.setObject(
                        3,
                        UUID.fromString(record.request.helperId),
                    )
                    statement.setObject(4, record.helperUserId)
                    statement.setObject(5, serviceCategoryId)
                    statement.setString(6, record.request.serviceMode)
                    statement.setString(7, record.request.pricingType)
                    statement.setString(
                        8,
                        record.request.problemDescription,
                    )
                    statement.setString(9, record.status.name)
                    statement.setTimestamp(
                        10,
                        Timestamp.from(record.scheduledStart),
                    )
                    statement.setTimestamp(
                        11,
                        Timestamp.from(record.scheduledEnd),
                    )
                    statement.setLong(12, 1)
                    statement.setString(13, idempotencyKey)
                    statement.setTimestamp(
                        14,
                        Timestamp.from(record.createdAt),
                    )
                    statement.setTimestamp(
                        15,
                        Timestamp.from(record.createdAt),
                    )
                    statement.setString(
                        16,
                        record.customerDisplayName,
                    )
                    statement.setString(
                        17,
                        json.encodeToString(record.request),
                    )
                    statement.setString(
                        18,
                        json.encodeToString(record.price),
                    )
                    statement.executeUpdate()
                }

                insertInitialHistory(connection, record)
                connection.commit()
            } catch (cause: Exception) {
                connection.rollback()
                throw cause
            } finally {
                connection.autoCommit = true
            }
        }
    }

    override fun findById(id: UUID): BookingRecord? =
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                SELECT *
                FROM bookings
                WHERE id = ?
                """.trimIndent(),
            ).use { statement ->
                statement.setObject(1, id)

                statement.executeQuery().use { results ->
                    if (results.next()) {
                        results.toBookingRecord(connection)
                    } else {
                        null
                    }
                }
            }
        }

    override fun findByIdempotency(
        customerId: UUID,
        idempotencyKey: String,
    ): BookingRecord? =
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                SELECT *
                FROM bookings
                WHERE customer_user_id = ?
                  AND idempotency_key = ?
                """.trimIndent(),
            ).use { statement ->
                statement.setObject(1, customerId)
                statement.setString(2, idempotencyKey)

                statement.executeQuery().use { results ->
                    if (results.next()) {
                        results.toBookingRecord(connection)
                    } else {
                        null
                    }
                }
            }
        }

    override fun listForCustomer(customerId: UUID): List<BookingRecord> =
        queryBookings(
            """
            SELECT *
            FROM bookings
            WHERE customer_user_id = ?
            ORDER BY created_at DESC
            """.trimIndent(),
        ) { statement ->
            statement.setObject(1, customerId)
        }

    override fun listForHelper(helperUserId: UUID): List<BookingRecord> =
        queryBookings(
            """
            SELECT *
            FROM bookings
            WHERE helper_user_id = ?
            ORDER BY created_at DESC
            """.trimIndent(),
        ) { statement ->
            statement.setObject(1, helperUserId)
        }

    override fun updateSchedule(record: BookingRecord) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                UPDATE bookings
                SET scheduled_start = ?,
                    scheduled_end = ?,
                    updated_at = ?,
                    version = version + 1
                WHERE id = ?
                """.trimIndent(),
            ).use { statement ->
                statement.setTimestamp(
                    1,
                    Timestamp.from(record.scheduledStart),
                )
                statement.setTimestamp(
                    2,
                    Timestamp.from(record.scheduledEnd),
                )
                statement.setTimestamp(
                    3,
                    Timestamp.from(Instant.now()),
                )
                statement.setObject(4, record.id)

                check(statement.executeUpdate() == 1) {
                    "Booking schedule update failed"
                }
            }
        }
    }

    override fun saveTransition(
        record: BookingRecord,
        fromStatus: BookingStatus,
        actor: BookingActor,
        actorUserId: UUID?,
        occurredAt: Instant,
    ) {
        dataSource.connection.use { connection ->
            connection.autoCommit = false

            try {
                connection.prepareStatement(
                    """
                    UPDATE bookings
                    SET status = ?,
                        updated_at = ?,
                        version = version + 1
                    WHERE id = ?
                    """.trimIndent(),
                ).use { statement ->
                    statement.setString(1, record.status.name)
                    statement.setTimestamp(
                        2,
                        Timestamp.from(occurredAt),
                    )
                    statement.setObject(3, record.id)

                    check(statement.executeUpdate() == 1) {
                        "Booking status update failed"
                    }
                }

                insertHistory(
                    connection = connection,
                    bookingId = record.id,
                    fromStatus = fromStatus,
                    toStatus = record.status,
                    actor = actor,
                    actorUserId = actorUserId,
                    occurredAt = occurredAt,
                )

                connection.commit()
            } catch (cause: Exception) {
                connection.rollback()
                throw cause
            } finally {
                connection.autoCommit = true
            }
        }
    }

    override fun hasConfirmedOverlap(
        helperProfileId: UUID,
        start: Instant,
        end: Instant,
        excludingBookingId: UUID,
    ): Boolean =
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                SELECT EXISTS (
                    SELECT 1
                    FROM bookings
                    WHERE helper_profile_id = ?
                      AND id <> ?
                      AND status = 'CONFIRMED'
                      AND ? < scheduled_end
                      AND ? > scheduled_start
                )
                """.trimIndent(),
            ).use { statement ->
                statement.setObject(1, helperProfileId)
                statement.setObject(2, excludingBookingId)
                statement.setTimestamp(3, Timestamp.from(start))
                statement.setTimestamp(4, Timestamp.from(end))

                statement.executeQuery().use { results ->
                    results.next()
                    results.getBoolean(1)
                }
            }
        }

    override fun close() {
        dataSource.close()
    }

    private fun queryBookings(
        sql: String,
        bind: (java.sql.PreparedStatement) -> Unit,
    ): List<BookingRecord> =
        dataSource.connection.use { connection ->
            connection.prepareStatement(sql).use { statement ->
                bind(statement)

                statement.executeQuery().use { results ->
                    buildList {
                        while (results.next()) {
                            add(results.toBookingRecord(connection))
                        }
                    }
                }
            }
        }

    private fun ResultSet.toBookingRecord(
        connection: Connection,
    ): BookingRecord {
        val bookingId = getObject("id", UUID::class.java)

        val requestJson = getString("request_json")
            ?: throw IllegalStateException(
                "Booking $bookingId has no saved request data",
            )

        val priceJson = getString("price_json")
            ?: throw IllegalStateException(
                "Booking $bookingId has no saved price data",
            )

        val helperUserId = getObject(
            "helper_user_id",
            UUID::class.java,
        ) ?: throw IllegalStateException(
            "Booking $bookingId has no helper user",
        )

        return BookingRecord(
            id = bookingId,
            customerId = getObject(
                "customer_user_id",
                UUID::class.java,
            ),
            helperUserId = helperUserId,
            customerDisplayName =
                getString("customer_display_name") ?: "Customer",
            request = json.decodeFromString<CreateBookingRequest>(
                requestJson,
            ),
            status = BookingStatus.valueOf(
                getString("status"),
            ),
            price =
                json.decodeFromString<BookingPriceBreakdownResponse>(
                    priceJson,
                ),
            createdAt = getTimestamp("created_at").toInstant(),
            scheduledStart =
                getTimestamp("scheduled_start").toInstant(),
            scheduledEnd =
                getTimestamp("scheduled_end").toInstant(),
            history = readHistory(
                connection = connection,
                bookingId = bookingId,
            ).toMutableList(),
        )
    }

    private fun readHistory(
        connection: Connection,
        bookingId: UUID,
    ): List<BookingStatusHistoryResponse> =
        connection.prepareStatement(
            """
            SELECT to_status, occurred_at
            FROM booking_status_history
            WHERE booking_id = ?
            ORDER BY occurred_at, id
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, bookingId)

            statement.executeQuery().use { results ->
                buildList {
                    while (results.next()) {
                        val status = BookingStatus.valueOf(
                            results.getString("to_status"),
                        )

                        add(
                            BookingStatusHistoryResponse(
                                status = status,
                                occurredAt = results
                                    .getTimestamp("occurred_at")
                                    .toInstant()
                                    .toString(),
                                explanation =
                                    statusExplanation(status),
                            ),
                        )
                    }
                }
            }
        }

    private fun insertInitialHistory(
        connection: Connection,
        record: BookingRecord,
    ) {
        record.history.forEachIndexed { index, entry ->
            val fromStatus = record.history
                .getOrNull(index - 1)
                ?.status

            val actor =
                if (index == 0) {
                    BookingActor.CUSTOMER
                } else {
                    BookingActor.SYSTEM
                }

            insertHistory(
                connection = connection,
                bookingId = record.id,
                fromStatus = fromStatus,
                toStatus = entry.status,
                actor = actor,
                actorUserId =
                    if (actor == BookingActor.CUSTOMER) {
                        record.customerId
                    } else {
                        null
                    },
                occurredAt = Instant.parse(entry.occurredAt),
            )
        }
    }

    private fun insertHistory(
        connection: Connection,
        bookingId: UUID,
        fromStatus: BookingStatus?,
        toStatus: BookingStatus,
        actor: BookingActor,
        actorUserId: UUID?,
        occurredAt: Instant,
    ) {
        connection.prepareStatement(
            """
            INSERT INTO booking_status_history (
                id,
                booking_id,
                from_status,
                to_status,
                actor_user_id,
                actor_type,
                occurred_at
            )
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, UUID.randomUUID())
            statement.setObject(2, bookingId)
            statement.setString(3, fromStatus?.name)
            statement.setString(4, toStatus.name)
            statement.setObject(5, actorUserId)
            statement.setString(6, actor.name)
            statement.setTimestamp(
                7,
                Timestamp.from(occurredAt),
            )
            statement.executeUpdate()
        }
    }

    private fun resolveServiceCategoryId(
        connection: Connection,
        categorySlug: String,
        serviceName: String,
    ): UUID =
        connection.prepareStatement(
            """
            SELECT id
            FROM service_category
            WHERE slug = ?
               OR name = ?
            ORDER BY
                CASE WHEN slug = ? THEN 0 ELSE 1 END
            LIMIT 1
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, categorySlug)
            statement.setString(2, serviceName)
            statement.setString(3, categorySlug)

            statement.executeQuery().use { results ->
                if (results.next()) {
                    results.getObject(
                        "id",
                        UUID::class.java,
                    )
                } else {
                    throw AuthenticationException(
                        "SERVICE_UNAVAILABLE",
                        "That service is no longer available.",
                        409,
                    )
                }
            }
        }
}
