@file:Suppress(
    "TooGenericExceptionCaught",
    "TooManyFunctions",
)

package com.digibuddy.backend.chat

import com.digibuddy.shared.contracts.MessageDeliveryStatus
import com.digibuddy.shared.contracts.ReportMessageRequest
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

class PostgresChatRepository(
    jdbcUrl: String,
    username: String,
    password: String,
) : ChatRepository, AutoCloseable {

    private val dataSource =
        HikariDataSource(
            HikariConfig().apply {
                this.jdbcUrl = jdbcUrl
                this.username = username
                this.password = password
                maximumPoolSize = 6
                minimumIdle = 1
                poolName = "digibuddy-chat"
            },
        )

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    override fun findConversation(
        id: UUID,
    ): ConversationRecord? =
        dataSource.connection.use { connection ->
            findConversation(connection, id)
        }

    override fun listForUser(
        userId: UUID,
    ): List<ConversationRecord> {
        val ids =
            dataSource.connection.use { connection ->
                connection.prepareStatement(
                    """
                    SELECT id
                    FROM conversations
                    WHERE customer_user_id = ?
                       OR helper_user_id = ?
                    ORDER BY created_at DESC
                    """.trimIndent(),
                ).use { statement ->
                    statement.setObject(1, userId)
                    statement.setObject(2, userId)

                    statement.executeQuery().use { results ->
                        buildList {
                            while (results.next()) {
                                add(
                                    results.getObject(
                                        "id",
                                        UUID::class.java,
                                    ),
                                )
                            }
                        }
                    }
                }
            }

        return ids.mapNotNull(::findConversation)
    }

    override fun saveConversation(
        record: ConversationRecord,
    ) {
        dataSource.connection.use { connection ->
            connection.autoCommit = false

            try {
                connection.prepareStatement(
                    """
                    INSERT INTO conversations (
                        id,
                        booking_id,
                        customer_user_id,
                        helper_user_id,
                        customer_display_name,
                        helper_display_name,
                        can_reply,
                        blocked,
                        last_sequence_id
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0)
                    ON CONFLICT (id) DO NOTHING
                    """.trimIndent(),
                ).use { statement ->
                    statement.setObject(1, record.id)
                    statement.setObject(2, record.bookingId)
                    statement.setObject(3, record.customerId)
                    statement.setObject(4, record.helperId)
                    statement.setString(
                        5,
                        record.customerName,
                    )
                    statement.setString(
                        6,
                        record.helperName,
                    )
                    statement.setBoolean(
                        7,
                        record.canReply,
                    )
                    statement.setBoolean(
                        8,
                        record.blocked,
                    )
                    statement.executeUpdate()
                }

                saveParticipant(
                    connection,
                    record.id,
                    record.customerId,
                )

                record.helperId?.let { helperId ->
                    saveParticipant(
                        connection,
                        record.id,
                        helperId,
                    )
                }

                connection.commit()
            } catch (cause: Exception) {
                connection.rollback()
                throw cause
            } finally {
                connection.autoCommit = true
            }
        }
    }

    override fun findMessageByClientId(
        senderId: UUID,
        clientMessageId: String,
    ): Pair<UUID, MessageRecord>? =
        dataSource.connection.use { connection ->
            findMessageByClientId(
                connection,
                senderId,
                clientMessageId,
            )
        }

    override fun appendMessage(
        conversationId: UUID,
        message: MessageRecord,
    ): MessageRecord =
        dataSource.connection.use { connection ->
            connection.autoCommit = false

            try {
                findMessageById(
                    connection,
                    message.id,
                )?.let { existing ->
                    connection.commit()
                    return@use existing
                }

                message.senderId?.let { senderId ->
                    findMessageByClientId(
                        connection,
                        senderId,
                        message.clientId,
                    )?.let { existing ->
                        connection.commit()
                        return@use existing.second
                    }
                }

                val nextSequence =
                    connection.prepareStatement(
                        """
                        UPDATE conversations
                        SET last_sequence_id =
                            last_sequence_id + 1
                        WHERE id = ?
                        RETURNING last_sequence_id
                        """.trimIndent(),
                    ).use { statement ->
                        statement.setObject(
                            1,
                            conversationId,
                        )

                        statement.executeQuery().use { results ->
                            check(results.next()) {
                                "Conversation not found"
                            }

                            results.getLong(
                                "last_sequence_id",
                            )
                        }
                    }

                val stored =
                    message.copy(
                        sequence = nextSequence,
                    )

                connection.prepareStatement(
                    """
                    INSERT INTO chat_messages (
                        id,
                        conversation_id,
                        sender_user_id,
                        client_message_id,
                        sequence_id,
                        message_type,
                        body,
                        created_at,
                        sender_display_name,
                        attachment_ids,
                        delivery_status,
                        development_seed
                    )
                    VALUES (
                        ?, ?, ?, ?, ?, ?, ?, ?, ?,
                        CAST(? AS JSONB),
                        ?, ?
                    )
                    """.trimIndent(),
                ).use { statement ->
                    statement.setObject(1, stored.id)
                    statement.setObject(
                        2,
                        conversationId,
                    )
                    statement.setObject(
                        3,
                        stored.senderId,
                    )
                    statement.setString(
                        4,
                        stored.clientId,
                    )
                    statement.setLong(
                        5,
                        stored.sequence,
                    )
                    statement.setString(
                        6,
                        stored.type,
                    )
                    statement.setString(
                        7,
                        stored.body,
                    )
                    statement.setTimestamp(
                        8,
                        Timestamp.from(
                            stored.createdAt,
                        ),
                    )
                    statement.setString(
                        9,
                        stored.senderName,
                    )
                    statement.setString(
                        10,
                        json.encodeToString(
                            stored.attachments,
                        ),
                    )
                    statement.setString(
                        11,
                        stored.status.name,
                    )
                    statement.setBoolean(
                        12,
                        stored.developmentSeed,
                    )
                    statement.executeUpdate()
                }

                saveDeliveryReceipts(
                    connection = connection,
                    conversationId = conversationId,
                    messageId = stored.id,
                    senderId = stored.senderId,
                    at = stored.createdAt,
                )

                connection.commit()
                stored
            } catch (cause: Exception) {
                connection.rollback()
                throw cause
            } finally {
                connection.autoCommit = true
            }
        }

    override fun markRead(
        conversationId: UUID,
        readerId: UUID,
    ) {
        dataSource.connection.use { connection ->
            connection.autoCommit = false

            try {
                connection.prepareStatement(
                    """
                    UPDATE chat_messages
                    SET delivery_status = 'READ'
                    WHERE conversation_id = ?
                      AND sender_user_id IS DISTINCT FROM ?
                    """.trimIndent(),
                ).use { statement ->
                    statement.setObject(
                        1,
                        conversationId,
                    )
                    statement.setObject(
                        2,
                        readerId,
                    )
                    statement.executeUpdate()
                }

                connection.prepareStatement(
                    """
                    UPDATE conversation_participants
                        AS participant
                    SET last_read_sequence_id =
                        conversation.last_sequence_id
                    FROM conversations AS conversation
                    WHERE participant.conversation_id =
                        conversation.id
                      AND participant.conversation_id = ?
                      AND participant.user_id = ?
                    """.trimIndent(),
                ).use { statement ->
                    statement.setObject(
                        1,
                        conversationId,
                    )
                    statement.setObject(
                        2,
                        readerId,
                    )
                    statement.executeUpdate()
                }

                connection.prepareStatement(
                    """
                    UPDATE message_receipts
                    SET read_at = ?
                    WHERE user_id = ?
                      AND message_id IN (
                          SELECT id
                          FROM chat_messages
                          WHERE conversation_id = ?
                            AND sender_user_id
                                IS DISTINCT FROM ?
                      )
                    """.trimIndent(),
                ).use { statement ->
                    statement.setTimestamp(
                        1,
                        Timestamp.from(
                            Instant.now(),
                        ),
                    )
                    statement.setObject(
                        2,
                        readerId,
                    )
                    statement.setObject(
                        3,
                        conversationId,
                    )
                    statement.setObject(
                        4,
                        readerId,
                    )
                    statement.executeUpdate()
                }

                connection.commit()
            } catch (cause: Exception) {
                connection.rollback()
                throw cause
            } finally {
                connection.autoCommit = true
            }
        }
    }

    override fun blockConversation(
        conversationId: UUID,
    ) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                UPDATE conversations
                SET blocked = TRUE
                WHERE id = ?
                """.trimIndent(),
            ).use { statement ->
                statement.setObject(
                    1,
                    conversationId,
                )
                statement.executeUpdate()
            }
        }
    }

    override fun messageExists(
        conversationId: UUID,
        messageId: UUID,
    ): Boolean =
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                SELECT EXISTS (
                    SELECT 1
                    FROM chat_messages
                    WHERE conversation_id = ?
                      AND id = ?
                )
                """.trimIndent(),
            ).use { statement ->
                statement.setObject(
                    1,
                    conversationId,
                )
                statement.setObject(
                    2,
                    messageId,
                )

                statement.executeQuery().use { results ->
                    results.next()
                    results.getBoolean(1)
                }
            }
        }

    override fun saveReport(
        conversationId: UUID,
        messageId: UUID,
        reporterId: UUID,
        request: ReportMessageRequest,
        createdAt: Instant,
    ) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                INSERT INTO message_reports (
                    id,
                    message_id,
                    reporter_user_id,
                    reason,
                    created_at
                )
                VALUES (?, ?, ?, ?, ?)
                """.trimIndent(),
            ).use { statement ->
                statement.setObject(
                    1,
                    UUID.randomUUID(),
                )
                statement.setObject(
                    2,
                    messageId,
                )
                statement.setObject(
                    3,
                    reporterId,
                )
                statement.setString(
                    4,
                    request.reason,
                )
                statement.setTimestamp(
                    5,
                    Timestamp.from(createdAt),
                )
                statement.executeUpdate()
            }
        }
    }

    override fun close() {
        dataSource.close()
    }

    private fun findConversation(
        connection: Connection,
        id: UUID,
    ): ConversationRecord? {
        val conversation =
            connection.prepareStatement(
                """
                SELECT
                    id,
                    booking_id,
                    customer_user_id,
                    helper_user_id,
                    customer_display_name,
                    helper_display_name,
                    can_reply,
                    blocked
                FROM conversations
                WHERE id = ?
                """.trimIndent(),
            ).use { statement ->
                statement.setObject(1, id)

                statement.executeQuery().use { results ->
                    if (!results.next()) {
                        null
                    } else {
                        val customerId =
                            results.getObject(
                                "customer_user_id",
                                UUID::class.java,
                            )
                                ?: error(
                                    "Conversation $id has no customer",
                                )

                        ConversationRecord(
                            id =
                                results.getObject(
                                    "id",
                                    UUID::class.java,
                                ),
                            customerId = customerId,
                            helperId =
                                results.getObject(
                                    "helper_user_id",
                                    UUID::class.java,
                                ),
                            customerName =
                                results.getString(
                                    "customer_display_name",
                                ) ?: "Customer",
                            helperName =
                                results.getString(
                                    "helper_display_name",
                                ) ?: "Helper",
                            bookingId =
                                results.getObject(
                                    "booking_id",
                                    UUID::class.java,
                                ),
                            messages = emptyList(),
                            canReply =
                                results.getBoolean(
                                    "can_reply",
                                ),
                            blocked =
                                results.getBoolean(
                                    "blocked",
                                ),
                        )
                    }
                }
            }

        return conversation?.copy(
            messages =
                readMessages(
                    connection,
                    conversation.id,
                ),
        )
    }

    private fun readMessages(
        connection: Connection,
        conversationId: UUID,
    ): List<MessageRecord> =
        connection.prepareStatement(
            """
            SELECT
                id,
                client_message_id,
                sender_user_id,
                sender_display_name,
                body,
                message_type,
                attachment_ids,
                sequence_id,
                created_at,
                delivery_status,
                development_seed
            FROM chat_messages
            WHERE conversation_id = ?
            ORDER BY sequence_id
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(
                1,
                conversationId,
            )

            statement.executeQuery().use { results ->
                buildList {
                    while (results.next()) {
                        add(
                            results.toMessageRecord(),
                        )
                    }
                }
            }
        }

    private fun findMessageByClientId(
        connection: Connection,
        senderId: UUID,
        clientMessageId: String,
    ): Pair<UUID, MessageRecord>? =
        connection.prepareStatement(
            """
            SELECT
                conversation_id,
                id,
                client_message_id,
                sender_user_id,
                sender_display_name,
                body,
                message_type,
                attachment_ids,
                sequence_id,
                created_at,
                delivery_status,
                development_seed
            FROM chat_messages
            WHERE sender_user_id = ?
              AND client_message_id = ?
            ORDER BY created_at DESC
            LIMIT 1
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(
                1,
                senderId,
            )
            statement.setString(
                2,
                clientMessageId,
            )

            statement.executeQuery().use { results ->
                if (results.next()) {
                    results.getObject(
                        "conversation_id",
                        UUID::class.java,
                    ) to results.toMessageRecord()
                } else {
                    null
                }
            }
        }

    private fun findMessageById(
        connection: Connection,
        messageId: UUID,
    ): MessageRecord? =
        connection.prepareStatement(
            """
            SELECT
                id,
                client_message_id,
                sender_user_id,
                sender_display_name,
                body,
                message_type,
                attachment_ids,
                sequence_id,
                created_at,
                delivery_status,
                development_seed
            FROM chat_messages
            WHERE id = ?
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(
                1,
                messageId,
            )

            statement.executeQuery().use { results ->
                if (results.next()) {
                    results.toMessageRecord()
                } else {
                    null
                }
            }
        }

    private fun ResultSet.toMessageRecord():
        MessageRecord {
        val attachmentJson =
            getString("attachment_ids")
                ?: "[]"

        return MessageRecord(
            id =
                getObject(
                    "id",
                    UUID::class.java,
                ),
            clientId =
                getString(
                    "client_message_id",
                ),
            senderId =
                getObject(
                    "sender_user_id",
                    UUID::class.java,
                ),
            senderName =
                getString(
                    "sender_display_name",
                ) ?: "Digibuddy",
            body =
                getString("body"),
            type =
                getString("message_type"),
            attachments =
                json.decodeFromString<List<String>>(
                    attachmentJson,
                ),
            sequence =
                getLong("sequence_id"),
            createdAt =
                getTimestamp(
                    "created_at",
                ).toInstant(),
            status =
                MessageDeliveryStatus.valueOf(
                    getString(
                        "delivery_status",
                    ),
                ),
            developmentSeed =
                getBoolean(
                    "development_seed",
                ),
        )
    }

    private fun saveParticipant(
        connection: Connection,
        conversationId: UUID,
        userId: UUID,
    ) {
        connection.prepareStatement(
            """
            INSERT INTO conversation_participants (
                conversation_id,
                user_id
            )
            VALUES (?, ?)
            ON CONFLICT (
                conversation_id,
                user_id
            ) DO NOTHING
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(
                1,
                conversationId,
            )
            statement.setObject(
                2,
                userId,
            )
            statement.executeUpdate()
        }
    }

    private fun saveDeliveryReceipts(
        connection: Connection,
        conversationId: UUID,
        messageId: UUID,
        senderId: UUID?,
        at: Instant,
    ) {
        if (senderId == null) {
            connection.prepareStatement(
                """
                INSERT INTO message_receipts (
                    message_id,
                    user_id,
                    delivered_at
                )
                SELECT ?, user_id, ?
                FROM conversation_participants
                WHERE conversation_id = ?
                ON CONFLICT (
                    message_id,
                    user_id
                ) DO NOTHING
                """.trimIndent(),
            ).use { statement ->
                statement.setObject(
                    1,
                    messageId,
                )
                statement.setTimestamp(
                    2,
                    Timestamp.from(at),
                )
                statement.setObject(
                    3,
                    conversationId,
                )
                statement.executeUpdate()
            }

            return
        }

        connection.prepareStatement(
            """
            INSERT INTO message_receipts (
                message_id,
                user_id,
                delivered_at
            )
            SELECT ?, user_id, ?
            FROM conversation_participants
            WHERE conversation_id = ?
              AND user_id <> ?
            ON CONFLICT (
                message_id,
                user_id
            ) DO NOTHING
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(
                1,
                messageId,
            )
            statement.setTimestamp(
                2,
                Timestamp.from(at),
            )
            statement.setObject(
                3,
                conversationId,
            )
            statement.setObject(
                4,
                senderId,
            )
            statement.executeUpdate()
        }
    }
}
