package com.digibuddy.backend.chat

import com.digibuddy.backend.auth.AuthenticatedPrincipal
import com.digibuddy.backend.auth.AuthenticationException
import com.digibuddy.shared.contracts.SendMessageRequest
import com.digibuddy.shared.contracts.StartHelperConversationRequest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ChatServiceTest {
    @Test
    fun `messages are idempotent authorized and blocked when requested`() {
        val service = ChatService()
        val principal = AuthenticatedPrincipal(UUID.randomUUID(), UUID.randomUUID())
        val conversationId = service.openBookingConversation(
            principal.userId,
            UUID.randomUUID(),
            "Customer",
            "Helper",
            UUID.randomUUID(),
        )
        val request = SendMessageRequest("message-client-123", "Hello, I have a question.")
        val first = service.send(principal, conversationId, request)
        val replay = service.send(principal, conversationId, request)
        assertEquals(first.messageId, replay.messageId)
        assertThrows<AuthenticationException> {
            service.messages(
                AuthenticatedPrincipal(UUID.randomUUID(), UUID.randomUUID()),
                conversationId,
                null,
            )
        }
        service.block(principal, conversationId)
        assertThrows<AuthenticationException> {
            service.send(
                principal,
                conversationId,
                SendMessageRequest("message-client-456", "Retry"),
            )
        }
    }

    @Test
    fun `welcome conversation is informational and cannot receive replies`() {
        val service = ChatService()
        val principal = AuthenticatedPrincipal(UUID.randomUUID(), UUID.randomUUID())
        val conversation = service.list(principal).items.single()

        assertFalse(conversation.canReply)
        assertEquals("Welcome to Digibuddy. For help, call +1 (312) 555-0100.", conversation.lastMessagePreview)
        val error = assertThrows<AuthenticationException> {
            service.send(
                principal,
                UUID.fromString(conversation.conversationId),
                SendMessageRequest("welcome-reply-123", "Hello"),
            )
        }
        assertEquals("CONVERSATION_READ_ONLY", error.errorCode)
    }

    @Test
    fun `customer and helper share one direct conversation`() {
        val helperCatalogId = UUID.randomUUID()
        val helperUserId = UUID.randomUUID()
        val customer = AuthenticatedPrincipal(UUID.randomUUID(), UUID.randomUUID())
        val helper = AuthenticatedPrincipal(helperUserId, UUID.randomUUID())
        val service = ChatService(
            helperAccountResolver = { id ->
                if (id == helperCatalogId) helperUserId to "Jamie Helper" else null
            },
            customerDisplayName = { "Alex C." },
        )

        val conversation = service.startHelperConversation(
            customer,
            StartHelperConversationRequest(helperCatalogId.toString()),
        )
        service.send(
            customer,
            UUID.fromString(conversation.conversationId),
            SendMessageRequest("customer-msg-123", "Hello"),
        )
        val helperConversation = service.list(helper, includeWelcome = false).items.single()
        service.send(
            helper,
            UUID.fromString(helperConversation.conversationId),
            SendMessageRequest("helper-msg-12345", "I can help."),
        )

        assertEquals("Alex C.", helperConversation.otherParticipantDisplayName)
        assertEquals(2, service.messages(customer, UUID.fromString(conversation.conversationId), null).items.size)
    }
}
