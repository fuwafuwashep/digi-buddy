package com.digibuddy.helper.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.digibuddy.core.model.ChatMessage
import com.digibuddy.core.network.ApiService
import com.digibuddy.core.socket.SocketManager
import com.digibuddy.helper.data.local.HelperPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONObject
import javax.inject.Inject

data class HelperChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isLoading: Boolean = false,
    val isTyping: Boolean = false,
    val currentUserId: String = ""
)

@HiltViewModel
class HelperChatViewModel @Inject constructor(
    private val apiService: ApiService,
    private val helperPreferences: HelperPreferences
) : ViewModel() {
    private val _uiState = MutableStateFlow(HelperChatUiState())
    val uiState: StateFlow<HelperChatUiState> = _uiState.asStateFlow()
    private val socket = SocketManager.getInstance()
    private var currentRoomId = ""

    fun loadMessages(roomId: String) = viewModelScope.launch {
        currentRoomId = roomId
        _uiState.value = _uiState.value.copy(isLoading = true)
        val userId = helperPreferences.userId.first() ?: ""
        _uiState.value = _uiState.value.copy(currentUserId = userId)

        val token = helperPreferences.getAccessToken()
        if (token != null && !socket.isConnected()) socket.connect(token)
        socket.joinRoom(roomId)

        try {
            val res = apiService.getRoomMessages(roomId)
            if (res.isSuccessful) _uiState.value = _uiState.value.copy(messages = res.body() ?: emptyList(), isLoading = false)
            else _uiState.value = _uiState.value.copy(isLoading = false)
        } catch (_: Exception) { _uiState.value = _uiState.value.copy(isLoading = false) }

        socket.onNewMessage { json ->
            val msg = parseMessage(json)
            if (msg != null) _uiState.value = _uiState.value.copy(messages = _uiState.value.messages + msg)
        }
        socket.onTyping { _, isTyping -> _uiState.value = _uiState.value.copy(isTyping = isTyping) }
    }

    fun sendMessage(content: String) { if (content.isNotBlank()) { socket.sendMessage(currentRoomId, content); socket.sendTypingStop(currentRoomId) } }
    fun onTypingStart() = socket.sendTypingStart(currentRoomId)
    fun onTypingStop() = socket.sendTypingStop(currentRoomId)

    private fun parseMessage(json: JSONObject): ChatMessage? = try {
        ChatMessage(id = json.optString("id"), roomId = json.optString("roomId"), senderId = json.optString("senderId"), content = json.optString("content"), createdAt = json.optString("createdAt"))
    } catch (_: Exception) { null }

    override fun onCleared() { socket.off("message:new"); socket.off("typing:user"); super.onCleared() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    roomId: String, otherName: String, onBack: () -> Unit,
    viewModel: HelperChatViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var messageText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(roomId) { viewModel.loadMessages(roomId) }
    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) scope.launch { listState.animateScrollToItem(uiState.messages.size - 1) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Column {
                    Text(otherName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    if (uiState.isTyping) Text("typing...", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }},
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            )
        },
        bottomBar = {
            Surface(shadowElevation = 8.dp) {
                Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.Bottom) {
                    OutlinedTextField(
                        value = messageText,
                        onValueChange = { messageText = it; if (it.isNotBlank()) viewModel.onTypingStart() else viewModel.onTypingStop() },
                        placeholder = { Text("Message $otherName...") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(24.dp),
                        maxLines = 4
                    )
                    Spacer(Modifier.width(8.dp))
                    FloatingActionButton({ if (messageText.isNotBlank()) { viewModel.sendMessage(messageText); messageText = "" } }, containerColor = MaterialTheme.colorScheme.primary, modifier = Modifier.size(52.dp)) {
                        Icon(Icons.Filled.Send, "Send", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp), contentPadding = PaddingValues(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(uiState.messages, key = { it.id }) { message ->
                val isMine = message.senderId == uiState.currentUserId
                Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = if (isMine) Alignment.End else Alignment.Start) {
                    Box(modifier = Modifier.widthIn(max = 280.dp).background(if (isMine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = if (isMine) 16.dp else 4.dp, bottomEnd = if (isMine) 4.dp else 16.dp)).padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Text(message.content, color = if (isMine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
