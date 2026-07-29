package com.digibuddy.backend.chat

import com.digibuddy.backend.auth.AuthenticationException
import com.digibuddy.backend.auth.authPrincipal
import com.digibuddy.shared.contracts.BookingMessageResponse
import com.digibuddy.shared.contracts.ReportMessageRequest
import com.digibuddy.shared.contracts.SendMessageRequest
import com.digibuddy.shared.contracts.StartHelperConversationRequest
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.send
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

@Suppress("LongMethod")
fun Route.chatRoutes(service: ChatService) {
    authenticate("access") {
        route("/api/v1/customer/conversations") {
            get { call.respond(service.list(call.authPrincipal())) }
            post("/helper") {
                call.respond(
                    service.startHelperConversation(
                        call.authPrincipal(),
                        call.receive<StartHelperConversationRequest>(),
                    ),
                )
            }
            get("/{conversationId}/messages") {
                call.respond(
                    service.messages(
                        call.authPrincipal(),
                        call.conversationId(),
                        call.request.queryParameters["before"]?.toLongOrNull(),
                    ),
                )
            }
            post("/{conversationId}/messages") {
                call.respond(
                    service.send(call.authPrincipal(), call.conversationId(), call.receive<SendMessageRequest>()),
                )
            }
            post("/{conversationId}/read") {
                service.markRead(call.authPrincipal(), call.conversationId())
                call.respond(BookingMessageResponse("Messages marked as read."))
            }
            post("/{conversationId}/block") {
                service.block(call.authPrincipal(), call.conversationId())
                call.respond(BookingMessageResponse("This user is blocked."))
            }
            post("/{conversationId}/messages/{messageId}/report") {
                service.report(
                    call.authPrincipal(),
                    call.conversationId(),
                    call.messageId(),
                    call.receive<ReportMessageRequest>(),
                )
                call.respond(BookingMessageResponse("Your report was received."))
            }
        }
        route("/api/v1/helper/conversations") {
            get { call.respond(service.list(call.authPrincipal(), includeWelcome = false)) }
            get("/{conversationId}/messages") {
                call.respond(
                    service.messages(
                        call.authPrincipal(),
                        call.conversationId(),
                        call.request.queryParameters["before"]?.toLongOrNull(),
                    ),
                )
            }
            post("/{conversationId}/messages") {
                call.respond(
                    service.send(call.authPrincipal(), call.conversationId(), call.receive<SendMessageRequest>()),
                )
            }
            post("/{conversationId}/read") {
                service.markRead(call.authPrincipal(), call.conversationId())
                call.respond(BookingMessageResponse("Messages marked as read."))
            }
        }
        webSocket("/api/v1/realtime") {
            val principal = call.authPrincipal()
            val events = Channel<com.digibuddy.shared.contracts.ChatEventResponse>(Channel.BUFFERED)
            val unsubscribe = service.subscribe(principal.userId) { events.trySend(it) }
            try {
                send(Frame.Text("{\"eventType\":\"CONNECTED\",\"sequenceId\":0,\"conversationId\":\"system\"}"))
                for (event in events) send(Frame.Text(Json.encodeToString(event)))
            } finally {
                unsubscribe()
                events.close()
            }
        }
    }
}

private fun io.ktor.server.application.ApplicationCall.conversationId() = uuid("conversationId")
private fun io.ktor.server.application.ApplicationCall.messageId() = uuid("messageId")
private fun io.ktor.server.application.ApplicationCall.uuid(name: String): UUID =
    runCatching { UUID.fromString(parameters[name]) }
        .getOrElse { throw AuthenticationException("INVALID_ID", "Conversation not found.", 404) }
