package com.digibuddy.customer

import com.digibuddy.shared.contracts.ChatMessageResponse
import com.digibuddy.shared.contracts.MessageDeliveryStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class ChatCoordinatorTest {
    @Test
    fun websocketDeliveryReplacesMatchingOptimisticMessage() {
        val pending = message(
            messageId = "customer-pending",
            clientMessageId = "customer-12345678",
            status = MessageDeliveryStatus.PENDING,
        )
        val delivered = message(
            messageId = "server-message",
            clientMessageId = "customer-12345678",
            status = MessageDeliveryStatus.DELIVERED,
        )

        val merged = mergeChatMessage(listOf(pending), delivered)

        assertEquals(listOf(delivered), merged)
    }

    private fun message(messageId: String, clientMessageId: String, status: MessageDeliveryStatus) =
        ChatMessageResponse(
            messageId = messageId,
            clientMessageId = clientMessageId,
            conversationId = "conversation-1",
            senderDisplayName = "You",
            senderIsCurrentUser = true,
            body = "Hello",
            messageType = "TEXT",
            sequenceId = 1,
            createdAt = "2026-07-20T00:00:00Z",
            deliveryStatus = status,
        )
}
