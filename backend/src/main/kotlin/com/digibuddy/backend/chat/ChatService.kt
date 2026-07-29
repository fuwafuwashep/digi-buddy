package com.digibuddy.backend.chat

import com.digibuddy.backend.auth.AuthenticatedPrincipal
import com.digibuddy.backend.auth.AuthenticationException
import com.digibuddy.shared.contracts.ChatEventResponse
import com.digibuddy.shared.contracts.ChatMessageResponse
import com.digibuddy.shared.contracts.ConversationListResponse
import com.digibuddy.shared.contracts.ConversationSummaryResponse
import com.digibuddy.shared.contracts.MessageDeliveryStatus
import com.digibuddy.shared.contracts.MessagePageResponse
import com.digibuddy.shared.contracts.ReportMessageRequest
import com.digibuddy.shared.contracts.SendMessageRequest
import com.digibuddy.shared.contracts.StartHelperConversationRequest
import java.time.Clock
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicLong

private data class ConversationRecord(
    val id: UUID,
    val customerId: UUID,
    val helperId: UUID?,
    val customerName: String,
    val helperName: String,
    val bookingId: UUID?,
    val messages: MutableList<MessageRecord>,
    val canReply: Boolean,
    var blocked: Boolean = false,
)

private data class MessageRecord(
    val id: UUID,
    val clientId: String,
    val senderId: UUID?,
    val senderName: String,
    val body: String,
    val type: String,
    val attachments: List<String>,
    val sequence: Long,
    val createdAt: Instant,
    var status: MessageDeliveryStatus,
    val developmentSeed: Boolean = false,
)

@Suppress("TooManyFunctions")
class ChatService(
    private val clock: Clock = Clock.systemUTC(),
    private val helperAccountResolver: (UUID) -> Pair<UUID, String>? = { null },
    private val customerDisplayName: (UUID) -> String = { "Customer" },
) {
    private val conversations = ConcurrentHashMap<UUID, ConversationRecord>()
    private val clientIds = ConcurrentHashMap<String, Pair<UUID, UUID>>()
    private val sequence = AtomicLong(0)
    private val subscribers = ConcurrentHashMap<UUID, CopyOnWriteArraySet<(ChatEventResponse) -> Unit>>()
    private val reports = ConcurrentHashMap<UUID, ReportMessageRequest>()

    fun list(principal: AuthenticatedPrincipal, includeWelcome: Boolean = true): ConversationListResponse {
        if (includeWelcome) ensureWelcomeConversation(principal.userId)
        return ConversationListResponse(
            conversations.values.filter { it.hasParticipant(principal.userId) }.map { conversation ->
                val last = conversation.messages.maxByOrNull { it.sequence }
                ConversationSummaryResponse(
                    conversation.id.toString(),
                    conversation.bookingId?.toString(),
                    conversation.otherParticipantName(principal.userId),
                    last?.body ?: "Start a conversation",
                    last?.createdAt?.toString() ?: clock.instant().toString(),
                    conversation.messages.count {
                        it.senderId != principal.userId && it.status != MessageDeliveryStatus.READ
                    },
                    conversation.canReply,
                )
            }.sortedByDescending { it.lastMessageAt },
        )
    }

    fun messages(principal: AuthenticatedPrincipal, id: UUID, before: Long?): MessagePageResponse {
        val conversation = owned(principal, id)
        val values = conversation.messages.filter { before == null || it.sequence < before }
            .sortedByDescending { it.sequence }.take(50).sortedBy { it.sequence }
        return MessagePageResponse(
            values.map { it.response(principal.userId, conversation.id) },
            values.firstOrNull()?.sequence?.takeIf { values.size == 50 },
        )
    }

    @Synchronized
    fun send(principal: AuthenticatedPrincipal, id: UUID, request: SendMessageRequest): ChatMessageResponse {
        val conversation = owned(principal, id)
        if (!conversation.canReply) {
            throw AuthenticationException(
                "CONVERSATION_READ_ONLY",
                "This welcome message does not accept replies. Call Digibuddy Support for help.",
                403,
            )
        }
        if (conversation.blocked) {
            throw AuthenticationException(
                "CONVERSATION_BLOCKED",
                "Messaging is unavailable.",
                403,
            )
        }
        if (request.clientMessageId.length !in 8..128) invalid("Invalid message identifier.")
        if (request.body.trim().length !in 1..2_000) invalid("Messages must be between 1 and 2,000 characters.")
        if (request.attachmentIds.size > 4) invalid("Attach no more than four images.")
        clientIds["${principal.userId}:${request.clientMessageId}"]?.let { (conversationId, messageId) ->
            val existing = conversations[conversationId]?.messages?.find { it.id == messageId }
            if (existing != null) return existing.response(principal.userId, conversationId)
        }
        val message = MessageRecord(
            UUID.randomUUID(),
            request.clientMessageId,
            principal.userId,
            if (principal.userId == conversation.customerId) conversation.customerName else conversation.helperName,
            request.body.trim(),
            "TEXT",
            request.attachmentIds,
            sequence.incrementAndGet(),
            clock.instant(),
            MessageDeliveryStatus.DELIVERED,
        )
        conversation.messages += message
        clientIds["${principal.userId}:${request.clientMessageId}"] = conversation.id to message.id
        val response = message.response(principal.userId, conversation.id)
        conversation.participantIds().forEach { userId ->
            publish(userId, ChatEventResponse("MESSAGE", message.sequence, conversation.id.toString(), response))
        }
        return response
    }

    fun markRead(principal: AuthenticatedPrincipal, id: UUID) {
        owned(principal, id).messages.filter { it.senderId != principal.userId }
            .forEach { it.status = MessageDeliveryStatus.READ }
    }

    fun block(principal: AuthenticatedPrincipal, id: UUID) {
        owned(principal, id).blocked = true
    }

    fun report(principal: AuthenticatedPrincipal, id: UUID, messageId: UUID, request: ReportMessageRequest) {
        val conversation = owned(principal, id)
        if (conversation.messages.none { it.id == messageId }) notFound()
        if (request.reason.trim().length !in 3..500) invalid("Choose a reason for the report.")
        reports[messageId] = request.copy(reason = request.reason.trim())
    }

    fun subscribe(userId: UUID, listener: (ChatEventResponse) -> Unit): () -> Unit {
        subscribers.computeIfAbsent(userId) { CopyOnWriteArraySet() }.add(listener)
        return { subscribers[userId]?.remove(listener) }
    }

    private fun publish(userId: UUID, event: ChatEventResponse) {
        subscribers[userId].orEmpty().forEach { it(event) }
    }

    fun startHelperConversation(
        principal: AuthenticatedPrincipal,
        request: StartHelperConversationRequest,
    ): ConversationSummaryResponse {
        val helperCatalogId = runCatching { UUID.fromString(request.helperId) }.getOrElse { notFound() }
        val helper = helperAccountResolver(helperCatalogId) ?: notFound()
        if (helper.first == principal.userId) invalid("You cannot message your own helper profile.")
        val id = UUID.nameUUIDFromBytes("direct-chat-${principal.userId}-${helper.first}".toByteArray())
        val conversation = conversations.computeIfAbsent(id) {
            ConversationRecord(
                id,
                principal.userId,
                helper.first,
                customerDisplayName(principal.userId),
                helper.second,
                null,
                mutableListOf(),
                canReply = true,
            )
        }
        return conversation.summary(principal.userId)
    }

    internal fun openBookingConversation(
        customerId: UUID,
        helperId: UUID,
        customerName: String,
        helperName: String,
        bookingId: UUID,
    ): UUID {
        val id = UUID.nameUUIDFromBytes("booking-chat-$bookingId".toByteArray())
        conversations.putIfAbsent(
            id,
            ConversationRecord(
                id,
                customerId,
                helperId,
                customerName,
                helperName,
                bookingId,
                mutableListOf(),
                canReply = true,
            ),
        )
        return id
    }

    private fun ensureWelcomeConversation(userId: UUID) {
        val id = UUID.nameUUIDFromBytes("welcome-$userId".toByteArray())
        if (conversations.containsKey(id)) return
        val message = MessageRecord(
            UUID.nameUUIDFromBytes("welcome-message-$userId".toByteArray()),
            "development-welcome",
            null,
            "Digibuddy",
            "Welcome to Digibuddy. For help, call +1 (312) 555-0100.",
            "SYSTEM",
            emptyList(),
            sequence.incrementAndGet(),
            clock.instant(),
            MessageDeliveryStatus.DELIVERED,
            developmentSeed = true,
        )
        conversations[id] = ConversationRecord(
            id,
            userId,
            null,
            "Customer",
            "Digibuddy",
            null,
            mutableListOf(message),
            canReply = false,
        )
    }

    private fun owned(principal: AuthenticatedPrincipal, id: UUID): ConversationRecord =
        conversations[id]?.takeIf { it.hasParticipant(principal.userId) } ?: notFound()

    private fun MessageRecord.response(currentUser: UUID, conversationId: UUID) = ChatMessageResponse(
        id.toString(), clientId, conversationId.toString(), senderName, senderId == currentUser, body, type,
        attachments, sequence, createdAt.toString(), status, developmentSeed,
    )

    private fun notFound(): Nothing =
        throw AuthenticationException("CONVERSATION_NOT_FOUND", "Conversation not found.", 404)
    private fun invalid(message: String): Nothing = throw AuthenticationException("INVALID_MESSAGE", message, 400)

    private fun ConversationRecord.hasParticipant(userId: UUID) = customerId == userId || helperId == userId
    private fun ConversationRecord.participantIds() = listOfNotNull(customerId, helperId).distinct()
    private fun ConversationRecord.otherParticipantName(userId: UUID) =
        if (userId == customerId) helperName else customerName

    private fun ConversationRecord.summary(userId: UUID): ConversationSummaryResponse {
        val last = messages.maxByOrNull { it.sequence }
        return ConversationSummaryResponse(
            id.toString(),
            bookingId?.toString(),
            otherParticipantName(userId),
            last?.body ?: "Start a conversation",
            last?.createdAt?.toString() ?: clock.instant().toString(),
            messages.count { it.senderId != userId && it.status != MessageDeliveryStatus.READ },
            canReply,
        )
    }
}
