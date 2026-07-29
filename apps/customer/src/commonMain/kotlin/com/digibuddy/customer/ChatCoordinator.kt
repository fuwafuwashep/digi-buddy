package com.digibuddy.customer

import com.digibuddy.shared.contracts.ChatMessageResponse
import com.digibuddy.shared.contracts.ConversationSummaryResponse
import com.digibuddy.shared.contracts.MessageDeliveryStatus
import com.digibuddy.shared.contracts.SendMessageRequest
import com.digibuddy.shared.networking.ChatApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

data class ChatUiState(
    val loading: Boolean = false,
    val conversations: List<ConversationSummaryResponse> = emptyList(),
    val selected: ConversationSummaryResponse? = null,
    val messages: List<ChatMessageResponse> = emptyList(),
    val connecting: Boolean = false,
    val offline: Boolean = false,
    val queued: List<SendMessageRequest> = emptyList(),
    val error: String? = null,
)

class ChatCoordinator(
    private val api: ChatApiClient,
    private val accessToken: String,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val mutableState = MutableStateFlow(ChatUiState())
    val state = mutableState.asStateFlow()
    private var realtimeStarted = false

    fun load() = scope.launch {
        mutableState.value = mutableState.value.copy(loading = true)
        runCatching { api.conversations(accessToken).items }
            .onSuccess {
                mutableState.value =
                    mutableState.value.copy(loading = false, conversations = it, offline = false)
            }
            .onFailure { mutableState.value = mutableState.value.copy(loading = false, offline = true) }
        startRealtime()
    }

    fun open(conversation: ConversationSummaryResponse) = scope.launch {
        mutableState.value = mutableState.value.copy(selected = conversation)
        runCatching { api.messages(accessToken, conversation.conversationId).items }
            .onSuccess { mutableState.value = mutableState.value.copy(messages = it, offline = false) }
            .onFailure { mutableState.value = mutableState.value.copy(offline = true) }
        runCatching { api.markRead(accessToken, conversation.conversationId) }
    }

    fun close() {
        mutableState.value = mutableState.value.copy(selected = null, messages = emptyList())
        load()
    }

    fun startHelperConversation(helperId: String) = scope.launch {
        mutableState.value = mutableState.value.copy(loading = true, error = null)
        runCatching { api.startHelperConversation(accessToken, helperId) }
            .onSuccess { conversation ->
                val conversations = (mutableState.value.conversations + conversation)
                    .distinctBy { it.conversationId }
                mutableState.value = mutableState.value.copy(
                    loading = false,
                    conversations = conversations,
                    offline = false,
                )
                open(conversation)
            }
            .onFailure {
                mutableState.value = mutableState.value.copy(
                    loading = false,
                    error = "We could not start this conversation. Please try again.",
                )
            }
    }

    fun send(body: String) {
        val conversation = mutableState.value.selected ?: return
        if (!conversation.canReply) return
        val request = SendMessageRequest("customer-${Random.nextLong().toString().replace('-', '0')}", body.trim())
        if (request.body.isBlank()) return
        val pending = ChatMessageResponse(
            request.clientMessageId,
            request.clientMessageId,
            conversation.conversationId,
            "You",
            true,
            request.body,
            "TEXT",
            sequenceId = Long.MAX_VALUE - mutableState.value.queued.size,
            createdAt = "Sending now",
            deliveryStatus = MessageDeliveryStatus.PENDING,
        )
        mutableState.value =
            mutableState.value.copy(
                messages = mutableState.value.messages + pending,
                queued =
                mutableState.value.queued + request,
            )
        flushQueue()
    }

    fun retryQueued() = flushQueue()

    private fun flushQueue() = scope.launch {
        val conversation = mutableState.value.selected ?: return@launch
        mutableState.value.queued.toList().forEach { request ->
            runCatching { api.send(accessToken, conversation.conversationId, request) }
                .onSuccess { sent ->
                    mutableState.value = mutableState.value.copy(
                        messages = mergeChatMessage(mutableState.value.messages, sent),
                        queued = mutableState.value.queued - request,
                        offline = false,
                    )
                }.onFailure {
                    mutableState.value = mutableState.value.copy(
                        messages = mutableState.value.messages.map {
                            if (it.clientMessageId ==
                                request.clientMessageId
                            ) {
                                it.copy(deliveryStatus = MessageDeliveryStatus.FAILED)
                            } else {
                                it
                            }
                        },
                        offline = true,
                    )
                }
        }
    }

    private fun startRealtime() {
        if (realtimeStarted) return
        realtimeStarted = true
        scope.launch {
            while (isActive) {
                mutableState.value = mutableState.value.copy(connecting = true)
                runCatching {
                    api.listen(accessToken) { event ->
                        mutableState.value = mutableState.value.copy(connecting = false, offline = false)
                        event.message?.let { message ->
                            if (mutableState.value.selected?.conversationId == message.conversationId &&
                                mutableState.value.messages.none { it.messageId == message.messageId }
                            ) {
                                mutableState.value =
                                    mutableState.value.copy(
                                        messages = mergeChatMessage(mutableState.value.messages, message),
                                    )
                            }
                        }
                    }
                }
                mutableState.value = mutableState.value.copy(connecting = true, offline = true)
                delay(2_000)
            }
        }
    }
}

internal fun mergeChatMessage(
    current: List<ChatMessageResponse>,
    incoming: ChatMessageResponse,
): List<ChatMessageResponse> {
    val matchingIndex = current.indexOfFirst {
        it.messageId == incoming.messageId || it.clientMessageId == incoming.clientMessageId
    }
    return if (matchingIndex < 0) {
        current + incoming
    } else {
        current.toMutableList().apply { this[matchingIndex] = incoming }
    }
}
