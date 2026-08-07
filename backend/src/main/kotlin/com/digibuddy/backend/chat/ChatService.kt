@file:Suppress("TooManyFunctions")

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
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet

class ChatService(
    private val clock: Clock = Clock.systemUTC(),
    private val repository: ChatRepository =
        InMemoryChatRepository(),
    private val helperAccountResolver:
        (UUID) -> Pair<UUID, String>? =
        { null },
    private val customerDisplayName:
        (UUID) -> String =
        { "Customer" },
) {
    private val subscribers =
        ConcurrentHashMap<
            UUID,
            CopyOnWriteArraySet<
                    (ChatEventResponse) -> Unit
                >,
            >()

    fun list(
        principal: AuthenticatedPrincipal,
        includeWelcome: Boolean = true,
    ): ConversationListResponse {
        if (includeWelcome) {
            ensureWelcomeConversation(
                principal.userId,
            )
        }

        return ConversationListResponse(
            repository
                .listForUser(principal.userId)
                .map { conversation ->
                    conversation.summary(
                        principal.userId,
                    )
                }
                .sortedByDescending {
                    it.lastMessageAt
                },
        )
    }

    fun messages(
        principal: AuthenticatedPrincipal,
        id: UUID,
        before: Long?,
    ): MessagePageResponse {
        val conversation =
            owned(principal, id)

        val values =
            conversation.messages
                .filter {
                    before == null ||
                        it.sequence < before
                }
                .sortedByDescending {
                    it.sequence
                }
                .take(50)
                .sortedBy {
                    it.sequence
                }

        return MessagePageResponse(
            items =
                values.map {
                    it.response(
                        principal.userId,
                        conversation.id,
                    )
                },
            nextBeforeSequence =
                values.firstOrNull()
                    ?.sequence
                    ?.takeIf {
                        values.size == 50
                    },
        )
    }

    @Synchronized
    fun send(
        principal: AuthenticatedPrincipal,
        id: UUID,
        request: SendMessageRequest,
    ): ChatMessageResponse {
        val conversation =
            owned(principal, id)

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

        if (
            request.clientMessageId.length !in
            8..128
        ) {
            invalid(
                "Invalid message identifier.",
            )
        }

        if (
            request.body.trim().length !in
            1..2_000
        ) {
            invalid(
                "Messages must be between 1 and 2,000 characters.",
            )
        }

        if (request.attachmentIds.size > 4) {
            invalid(
                "Attach no more than four images.",
            )
        }

        repository.findMessageByClientId(
            senderId = principal.userId,
            clientMessageId =
                request.clientMessageId,
        )?.let { existing ->
            return existing.second.response(
                currentUser = principal.userId,
                conversationId = existing.first,
            )
        }

        val senderName =
            if (
                principal.userId ==
                conversation.customerId
            ) {
                conversation.customerName
            } else {
                conversation.helperName
            }

        val message =
            repository.appendMessage(
                conversationId = conversation.id,
                message =
                    MessageRecord(
                        id = UUID.randomUUID(),
                        clientId =
                            request.clientMessageId,
                        senderId =
                            principal.userId,
                        senderName =
                            senderName,
                        body =
                            request.body.trim(),
                        type = "TEXT",
                        attachments =
                            request.attachmentIds,
                        sequence = 0,
                        createdAt =
                            clock.instant(),
                        status =
                            MessageDeliveryStatus
                                .DELIVERED,
                    ),
            )

        val response =
            message.response(
                currentUser = principal.userId,
                conversationId =
                    conversation.id,
            )

        conversation
            .participantIds()
            .forEach { userId ->
                publish(
                    userId,
                    ChatEventResponse(
                        eventType = "MESSAGE",
                        sequenceId =
                            message.sequence,
                        conversationId =
                            conversation.id
                                .toString(),
                        message = response,
                    ),
                )
            }

        return response
    }

    fun markRead(
        principal: AuthenticatedPrincipal,
        id: UUID,
    ) {
        owned(principal, id)

        repository.markRead(
            conversationId = id,
            readerId = principal.userId,
        )
    }

    fun block(
        principal: AuthenticatedPrincipal,
        id: UUID,
    ) {
        owned(principal, id)

        repository.blockConversation(id)
    }

    fun report(
        principal: AuthenticatedPrincipal,
        id: UUID,
        messageId: UUID,
        request: ReportMessageRequest,
    ) {
        owned(principal, id)

        if (
            !repository.messageExists(
                conversationId = id,
                messageId = messageId,
            )
        ) {
            notFound()
        }

        if (
            request.reason.trim().length !in
            3..500
        ) {
            invalid(
                "Choose a reason for the report.",
            )
        }

        repository.saveReport(
            conversationId = id,
            messageId = messageId,
            reporterId = principal.userId,
            request =
                request.copy(
                    reason =
                        request.reason.trim(),
                ),
            createdAt = clock.instant(),
        )
    }

    fun subscribe(
        userId: UUID,
        listener:
            (ChatEventResponse) -> Unit,
    ): () -> Unit {
        subscribers
            .computeIfAbsent(userId) {
                CopyOnWriteArraySet()
            }
            .add(listener)

        return {
            subscribers[userId]
                ?.remove(listener)
        }
    }

    private fun publish(
        userId: UUID,
        event: ChatEventResponse,
    ) {
        subscribers[userId]
            .orEmpty()
            .forEach {
                it(event)
            }
    }

    fun startHelperConversation(
        principal: AuthenticatedPrincipal,
        request: StartHelperConversationRequest,
    ): ConversationSummaryResponse {
        val helperCatalogId =
            runCatching {
                UUID.fromString(
                    request.helperId,
                )
            }.getOrElse {
                notFound()
            }

        val helper =
            helperAccountResolver(
                helperCatalogId,
            ) ?: notFound()

        if (
            helper.first ==
            principal.userId
        ) {
            invalid(
                "You cannot message your own helper profile.",
            )
        }

        val id =
            UUID.nameUUIDFromBytes(
                "direct-chat-${principal.userId}-${helper.first}"
                    .toByteArray(),
            )

        val existing =
            repository.findConversation(id)

        if (existing != null) {
            return existing.summary(
                principal.userId,
            )
        }

        val conversation =
            ConversationRecord(
                id = id,
                customerId =
                    principal.userId,
                helperId =
                    helper.first,
                customerName =
                    customerDisplayName(
                        principal.userId,
                    ),
                helperName =
                    helper.second,
                bookingId = null,
                messages = emptyList(),
                canReply = true,
            )

        repository.saveConversation(
            conversation,
        )

        return (
            repository.findConversation(id)
                ?: conversation
            ).summary(principal.userId)
    }

    internal fun openBookingConversation(
        customerId: UUID,
        helperId: UUID,
        customerName: String,
        helperName: String,
        bookingId: UUID,
    ): UUID {
        val id =
            UUID.nameUUIDFromBytes(
                "booking-chat-$bookingId"
                    .toByteArray(),
            )

        if (
            repository.findConversation(id) ==
            null
        ) {
            repository.saveConversation(
                ConversationRecord(
                    id = id,
                    customerId = customerId,
                    helperId = helperId,
                    customerName =
                        customerName,
                    helperName =
                        helperName,
                    bookingId = bookingId,
                    messages = emptyList(),
                    canReply = true,
                ),
            )
        }

        return id
    }

    private fun ensureWelcomeConversation(
        userId: UUID,
    ) {
        val conversationId =
            UUID.nameUUIDFromBytes(
                "welcome-$userId"
                    .toByteArray(),
            )

        val messageId =
            UUID.nameUUIDFromBytes(
                "welcome-message-$userId"
                    .toByteArray(),
            )

        var conversation =
            repository.findConversation(
                conversationId,
            )

        if (conversation == null) {
            val newConversation =
                ConversationRecord(
                    id = conversationId,
                    customerId = userId,
                    helperId = null,
                    customerName = "Customer",
                    helperName = "Digibuddy",
                    bookingId = null,
                    messages = emptyList(),
                    canReply = false,
                )

            repository.saveConversation(
                newConversation,
            )

            conversation =
                repository.findConversation(
                    conversationId,
                ) ?: newConversation
        }

        if (
            repository.messageExists(
                conversationId,
                messageId,
            )
        ) {
            return
        }

        repository.appendMessage(
            conversationId =
                conversation.id,
            message =
                MessageRecord(
                    id = messageId,
                    clientId =
                        "development-welcome",
                    senderId = null,
                    senderName = "Digibuddy",
                    body =
                        "Welcome to Digibuddy. For help, call +1 (312) 555-0100.",
                    type = "SYSTEM",
                    attachments =
                        emptyList(),
                    sequence = 0,
                    createdAt =
                        clock.instant(),
                    status =
                        MessageDeliveryStatus
                            .DELIVERED,
                    developmentSeed = true,
                ),
        )
    }

    private fun owned(
        principal: AuthenticatedPrincipal,
        id: UUID,
    ): ConversationRecord =
        repository
            .findConversation(id)
            ?.takeIf {
                it.hasParticipant(
                    principal.userId,
                )
            }
            ?: notFound()

    private fun MessageRecord.response(
        currentUser: UUID,
        conversationId: UUID,
    ): ChatMessageResponse =
        ChatMessageResponse(
            messageId =
                id.toString(),
            clientMessageId =
                clientId,
            conversationId =
                conversationId.toString(),
            senderDisplayName =
                senderName,
            senderIsCurrentUser =
                senderId == currentUser,
            body = body,
            messageType = type,
            attachmentIds =
                attachments,
            sequenceId =
                sequence,
            createdAt =
                createdAt.toString(),
            deliveryStatus =
                status,
            developmentSeed =
                developmentSeed,
        )

    private fun notFound(): Nothing =
        throw AuthenticationException(
            "CONVERSATION_NOT_FOUND",
            "Conversation not found.",
            404,
        )

    private fun invalid(
        message: String,
    ): Nothing =
        throw AuthenticationException(
            "INVALID_MESSAGE",
            message,
            400,
        )

    private fun ConversationRecord
        .hasParticipant(
        userId: UUID,
    ): Boolean =
        customerId == userId ||
            helperId == userId

    private fun ConversationRecord
        .participantIds():
        List<UUID> =
        listOfNotNull(
            customerId,
            helperId,
        ).distinct()

    private fun ConversationRecord
        .otherParticipantName(
        userId: UUID,
    ): String =
        if (userId == customerId) {
            helperName
        } else {
            customerName
        }

    private fun ConversationRecord.summary(
        userId: UUID,
    ): ConversationSummaryResponse {
        val last =
            messages.maxByOrNull {
                it.sequence
            }

        return ConversationSummaryResponse(
            conversationId =
                id.toString(),
            bookingId =
                bookingId?.toString(),
            otherParticipantDisplayName =
                otherParticipantName(userId),
            lastMessagePreview =
                last?.body
                    ?: "Start a conversation",
            lastMessageAt =
                last?.createdAt
                    ?.toString()
                    ?: clock.instant()
                        .toString(),
            unreadCount =
                messages.count {
                    it.senderId != userId &&
                        it.status !=
                        MessageDeliveryStatus.READ
                },
            canReply =
                canReply,
        )
    }
}
