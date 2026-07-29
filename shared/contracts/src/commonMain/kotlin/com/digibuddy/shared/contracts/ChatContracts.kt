package com.digibuddy.shared.contracts

import kotlinx.serialization.Serializable

@Serializable
enum class MessageDeliveryStatus { PENDING, SENT, DELIVERED, READ, FAILED }

@Serializable
data class ConversationSummaryResponse(
    val conversationId: String,
    val bookingId: String? = null,
    val otherParticipantDisplayName: String,
    val lastMessagePreview: String,
    val lastMessageAt: String,
    val unreadCount: Int,
    val canReply: Boolean = true,
)

@Serializable
data class ChatMessageResponse(
    val messageId: String,
    val clientMessageId: String,
    val conversationId: String,
    val senderDisplayName: String,
    val senderIsCurrentUser: Boolean,
    val body: String,
    val messageType: String,
    val attachmentIds: List<String> = emptyList(),
    val sequenceId: Long,
    val createdAt: String,
    val deliveryStatus: MessageDeliveryStatus,
    val developmentSeed: Boolean = false,
)

@Serializable
data class ConversationListResponse(val items: List<ConversationSummaryResponse>)

@Serializable
data class StartHelperConversationRequest(val helperId: String)

@Serializable
data class MessagePageResponse(val items: List<ChatMessageResponse>, val nextBeforeSequence: Long? = null)

@Serializable
data class SendMessageRequest(
    val clientMessageId: String,
    val body: String,
    val attachmentIds: List<String> = emptyList(),
)

@Serializable
data class ReportMessageRequest(val reason: String)

@Serializable
data class ChatEventResponse(
    val eventType: String,
    val sequenceId: Long,
    val conversationId: String,
    val message: ChatMessageResponse? = null,
    val bookingId: String? = null,
    val bookingStatus: BookingStatus? = null,
)
