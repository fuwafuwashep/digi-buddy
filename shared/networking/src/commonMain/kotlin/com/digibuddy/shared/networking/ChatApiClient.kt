package com.digibuddy.shared.networking

import com.digibuddy.shared.contracts.ChatEventResponse
import com.digibuddy.shared.contracts.ChatMessageResponse
import com.digibuddy.shared.contracts.ConversationListResponse
import com.digibuddy.shared.contracts.ConversationSummaryResponse
import com.digibuddy.shared.contracts.MessagePageResponse
import com.digibuddy.shared.contracts.SendMessageRequest
import com.digibuddy.shared.contracts.StartHelperConversationRequest
import io.ktor.client.call.body
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.serialization.json.Json

class ChatApiClient(private val network: DigibuddyNetworkClient, baseUrl: String, helperMode: Boolean = false) {
    private val base = baseUrl.trimEnd('/')
    private val conversations = "$base/api/v1/${if (helperMode) "helper" else "customer"}/conversations"
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun conversations(token: String): ConversationListResponse =
        network.httpClient.get(conversations) { bearerAuth(token) }.body()

    suspend fun messages(token: String, id: String): MessagePageResponse =
        network.httpClient.get("$conversations/$id/messages") { bearerAuth(token) }.body()

    suspend fun send(token: String, id: String, request: SendMessageRequest): ChatMessageResponse =
        network.httpClient.post("$conversations/$id/messages") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    suspend fun startHelperConversation(token: String, helperId: String): ConversationSummaryResponse =
        network.httpClient.post("$conversations/helper") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(StartHelperConversationRequest(helperId))
        }.body()

    suspend fun markRead(token: String, id: String) {
        network.httpClient.post("$conversations/$id/read") { bearerAuth(token) }
    }

    suspend fun listen(token: String, onEvent: (ChatEventResponse) -> Unit) {
        val socketUrl = base.replaceFirst("http://", "ws://").replaceFirst("https://", "wss://")
        network.httpClient.webSocket(
            urlString = "$socketUrl/api/v1/realtime",
            request = { bearerAuth(token) },
        ) {
            for (frame in incoming) {
                if (frame is Frame.Text) {
                    runCatching {
                        json.decodeFromString<ChatEventResponse>(frame.readText())
                    }.onSuccess(onEvent)
                }
            }
        }
    }

    companion object {
        fun forLocalDevelopment(network: DigibuddyNetworkClient = createDigibuddyNetworkClient()) =
            ChatApiClient(network, localDevelopmentApiBaseUrl())

        fun forHelperLocalDevelopment(network: DigibuddyNetworkClient = createDigibuddyNetworkClient()) =
            ChatApiClient(network, localDevelopmentApiBaseUrl(), helperMode = true)
    }
}
