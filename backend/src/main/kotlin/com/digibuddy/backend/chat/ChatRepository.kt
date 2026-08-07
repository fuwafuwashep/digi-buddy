package com.digibuddy.backend.chat

import com.digibuddy.shared.contracts.MessageDeliveryStatus
import com.digibuddy.shared.contracts.ReportMessageRequest
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class ConversationRecord(
    val id: UUID,
    val customerId: UUID,
    val helperId: UUID?,
    val customerName: String,
    val helperName: String,
    val bookingId: UUID?,
    val messages: List<MessageRecord>,
    val canReply: Boolean,
    val blocked: Boolean = false,
)

data class MessageRecord(
    val id: UUID,
    val clientId: String,
    val senderId: UUID?,
    val senderName: String,
    val body: String,
    val type: String,
    val attachments: List<String>,
    val sequence: Long,
    val createdAt: Instant,
    val status: MessageDeliveryStatus,
    val developmentSeed: Boolean = false,
)

interface ChatRepository {
    fun findConversation(id: UUID): ConversationRecord?

    fun listForUser(userId: UUID): List<ConversationRecord>

    fun saveConversation(record: ConversationRecord)

    fun findMessageByClientId(
        senderId: UUID,
        clientMessageId: String,
    ): Pair<UUID, MessageRecord>?

    fun appendMessage(
        conversationId: UUID,
        message: MessageRecord,
    ): MessageRecord

    fun markRead(
        conversationId: UUID,
        readerId: UUID,
    )

    fun blockConversation(conversationId: UUID)

    fun messageExists(
        conversationId: UUID,
        messageId: UUID,
    ): Boolean

    fun saveReport(
        conversationId: UUID,
        messageId: UUID,
        reporterId: UUID,
        request: ReportMessageRequest,
        createdAt: Instant,
    )
}

class InMemoryChatRepository : ChatRepository {
    private val conversations =
        ConcurrentHashMap<UUID, ConversationRecord>()

    private val reports =
        ConcurrentHashMap<Pair<UUID, UUID>, ReportMessageRequest>()

    override fun findConversation(
        id: UUID,
    ): ConversationRecord? =
        conversations[id]

    override fun listForUser(
        userId: UUID,
    ): List<ConversationRecord> =
        conversations.values
            .filter {
                it.customerId == userId ||
                    it.helperId == userId
            }

    override fun saveConversation(
        record: ConversationRecord,
    ) {
        conversations.putIfAbsent(
            record.id,
            record,
        )
    }

    override fun findMessageByClientId(
        senderId: UUID,
        clientMessageId: String,
    ): Pair<UUID, MessageRecord>? {
        conversations.values.forEach { conversation ->
            val message =
                conversation.messages.firstOrNull {
                    it.senderId == senderId &&
                        it.clientId == clientMessageId
                }

            if (message != null) {
                return conversation.id to message
            }
        }

        return null
    }

    @Synchronized
    override fun appendMessage(
        conversationId: UUID,
        message: MessageRecord,
    ): MessageRecord {
        val conversation =
            conversations[conversationId]
                ?: error("Conversation not found")

        conversation.messages
            .firstOrNull { it.id == message.id }
            ?.let { return it }

        if (message.senderId != null) {
            findMessageByClientId(
                message.senderId,
                message.clientId,
            )?.let {
                return it.second
            }
        }

        val nextSequence =
            (conversation.messages
                .maxOfOrNull { it.sequence } ?: 0L) + 1L

        val stored =
            message.copy(sequence = nextSequence)

        conversations[conversationId] =
            conversation.copy(
                messages =
                    conversation.messages + stored,
            )

        return stored
    }

    @Synchronized
    override fun markRead(
        conversationId: UUID,
        readerId: UUID,
    ) {
        val conversation =
            conversations[conversationId]
                ?: return

        conversations[conversationId] =
            conversation.copy(
                messages =
                    conversation.messages.map { message ->
                        if (message.senderId != readerId) {
                            message.copy(
                                status =
                                    MessageDeliveryStatus.READ,
                            )
                        } else {
                            message
                        }
                    },
            )
    }

    @Synchronized
    override fun blockConversation(
        conversationId: UUID,
    ) {
        val conversation =
            conversations[conversationId]
                ?: return

        conversations[conversationId] =
            conversation.copy(blocked = true)
    }

    override fun messageExists(
        conversationId: UUID,
        messageId: UUID,
    ): Boolean =
        conversations[conversationId]
            ?.messages
            ?.any { it.id == messageId }
            ?: false

    override fun saveReport(
        conversationId: UUID,
        messageId: UUID,
        reporterId: UUID,
        request: ReportMessageRequest,
        createdAt: Instant,
    ) {
        reports[messageId to reporterId] = request
    }
}
