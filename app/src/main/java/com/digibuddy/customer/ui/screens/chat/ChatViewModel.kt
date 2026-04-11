package com.digibuddy.customer.ui.screens.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.digibuddy.core.model.ChatMessage
import com.digibuddy.core.socket.SocketManager
import com.digibuddy.customer.data.local.UserPreferences
import com.digibuddy.customer.data.repository.ChatRepository
import com.digibuddy.customer.data.repository.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import javax.inject.Inject

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isLoading: Boolean = false,
    val isTyping: Boolean = false,
    val currentUserId: String = "",
    val error: String? = null
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val userPreferences: UserPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val socket = SocketManager.getInstance()
    private var currentRoomId = ""

    init {
        val userId = runBlocking { userPreferences.userId.first() } ?: ""
        _uiState.value = _uiState.value.copy(currentUserId = userId)
    }

    fun loadMessages(roomId: String) {
        currentRoomId = roomId
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val token = userPreferences.getAccessToken()
            if (token != null && !socket.isConnected()) socket.connect(token)

            socket.joinRoom(roomId)

            when (val result = chatRepository.getRoomMessages(roomId)) {
                is Result.Success -> _uiState.value = _uiState.value.copy(
                    messages = result.data, isLoading = false
                )
                is Result.Error -> _uiState.value = _uiState.value.copy(
                    isLoading = false, error = result.message
                )
            }

            // Listen for new messages
            socket.onNewMessage { json ->
                val msg = parseMessage(json)
                if (msg != null) {
                    _uiState.value = _uiState.value.copy(
                        messages = _uiState.value.messages + msg
                    )
                }
            }

            // Typing indicator
            socket.onTyping { _, isTyping ->
                _uiState.value = _uiState.value.copy(isTyping = isTyping)
            }
        }
    }

    fun sendMessage(content: String) {
        if (content.isBlank()) return
        socket.sendMessage(currentRoomId, content)
        socket.sendTypingStop(currentRoomId)
    }

    fun onTypingStart() = socket.sendTypingStart(currentRoomId)
    fun onTypingStop() = socket.sendTypingStop(currentRoomId)

    private fun parseMessage(json: JSONObject): ChatMessage? = try {
        val sender = json.optJSONObject("sender")
        ChatMessage(
            id = json.optString("id"),
            roomId = json.optString("roomId"),
            senderId = json.optString("senderId").ifBlank { sender?.optString("id") ?: "" },
            content = json.optString("content"),
            createdAt = json.optString("createdAt")
        )
    } catch (e: Exception) { null }

    override fun onCleared() {
        socket.off("message:new")
        socket.off("typing:user")
        super.onCleared()
    }
}
