package com.digibuddy.shared.helper.dashboard

import com.digibuddy.shared.contracts.BookingSummaryResponse
import com.digibuddy.shared.contracts.ChatMessageResponse
import com.digibuddy.shared.contracts.ConversationSummaryResponse
import com.digibuddy.shared.contracts.HelperApplicationResponse
import com.digibuddy.shared.contracts.SendMessageRequest
import com.digibuddy.shared.contracts.UpdateHelperProfileRequest
import com.digibuddy.shared.networking.BookingApiClient
import com.digibuddy.shared.networking.ChatApiClient
import com.digibuddy.shared.networking.HelperAccountApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

data class HelperDashboardState(
    val loadingRequests: Boolean = false,
    val loadingJobs: Boolean = false,
    val loadingChats: Boolean = false,
    val requests: List<BookingSummaryResponse> = emptyList(),
    val jobs: List<BookingSummaryResponse> = emptyList(),
    val conversations: List<ConversationSummaryResponse> = emptyList(),
    val selectedConversation: ConversationSummaryResponse? = null,
    val messages: List<ChatMessageResponse> = emptyList(),
    val application: HelperApplicationResponse? = null,
    val savingProfile: Boolean = false,
    val uploadingPhoto: Boolean = false,
    val message: String? = null,
    val error: String? = null,
)

data class HelperSelectedPhoto(val fileName: String, val contentType: String, val bytes: ByteArray)

class HelperDashboardCoordinator(
    private val bookings: BookingApiClient,
    private val chats: ChatApiClient,
    private val account: HelperAccountApiClient,
    private val accessToken: String,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val mutableState = MutableStateFlow(HelperDashboardState())
    val state = mutableState.asStateFlow()

    fun load() {
        refreshRequests()
        refreshJobs()
        refreshChats()
        refreshProfile()
    }

    fun refreshRequests() = scope.launch {
        mutableState.value = mutableState.value.copy(loadingRequests = true, error = null)
        runCatching { bookings.helperRequests(accessToken).items }
            .onSuccess { mutableState.value = mutableState.value.copy(loadingRequests = false, requests = it) }
            .onFailure { fail("Requests could not be refreshed.") { copy(loadingRequests = false) } }
    }

    fun refreshJobs() = scope.launch {
        mutableState.value = mutableState.value.copy(loadingJobs = true, error = null)
        runCatching { bookings.helperJobs(accessToken).items }
            .onSuccess { mutableState.value = mutableState.value.copy(loadingJobs = false, jobs = it) }
            .onFailure { fail("Jobs could not be refreshed.") { copy(loadingJobs = false) } }
    }

    fun refreshChats() = scope.launch {
        mutableState.value = mutableState.value.copy(loadingChats = true, error = null)
        runCatching { chats.conversations(accessToken).items }
            .onSuccess { mutableState.value = mutableState.value.copy(loadingChats = false, conversations = it) }
            .onFailure { fail("Messages could not be refreshed.") { copy(loadingChats = false) } }
    }

    fun refreshProfile() = scope.launch {
        runCatching { account.application(accessToken) }
            .onSuccess { mutableState.value = mutableState.value.copy(application = it, error = null) }
            .onFailure { fail("Your profile could not be loaded.") }
    }

    fun accept(bookingId: String) = bookingCommand("Request accepted.") {
        bookings.helperAccept(accessToken, bookingId)
    }

    fun decline(bookingId: String) = bookingCommand("Request declined.") {
        bookings.helperDecline(accessToken, bookingId)
    }

    fun openBookingChat(bookingId: String) {
        val conversation = mutableState.value.conversations.firstOrNull { it.bookingId == bookingId }
        if (conversation == null) {
            refreshChats()
            mutableState.value =
                mutableState.value.copy(message = "Refresh Messages, then open the booking conversation.")
        } else {
            openConversation(conversation)
        }
    }

    fun openConversation(conversation: ConversationSummaryResponse) = scope.launch {
        mutableState.value = mutableState.value.copy(selectedConversation = conversation, messages = emptyList())
        runCatching { chats.messages(accessToken, conversation.conversationId).items }
            .onSuccess { mutableState.value = mutableState.value.copy(messages = it, error = null) }
            .onFailure { fail("This conversation could not be opened.") }
        runCatching { chats.markRead(accessToken, conversation.conversationId) }
    }

    fun closeConversation() {
        mutableState.value = mutableState.value.copy(selectedConversation = null, messages = emptyList())
        refreshChats()
    }

    fun sendMessage(body: String) = scope.launch {
        val conversation = mutableState.value.selectedConversation ?: return@launch
        val trimmed = body.trim()
        if (trimmed.isEmpty()) return@launch
        val request = SendMessageRequest(
            "helper-${Random.nextLong().toString().replace('-', '0')}",
            trimmed,
        )
        runCatching { chats.send(accessToken, conversation.conversationId, request) }
            .onSuccess { sent ->
                mutableState.value = mutableState.value.copy(
                    messages = (mutableState.value.messages + sent).distinctBy { it.messageId },
                    error = null,
                )
            }.onFailure { fail("Your message was not sent. Please try again.") }
    }

    fun updateProfile(request: UpdateHelperProfileRequest) = scope.launch {
        mutableState.value = mutableState.value.copy(savingProfile = true, error = null, message = null)
        runCatching { account.updateProfile(accessToken, request) }
            .onSuccess {
                mutableState.value = mutableState.value.copy(
                    savingProfile = false,
                    application = it,
                    message = "Profile updated.",
                )
            }.onFailure { fail(it.message ?: "Your profile could not be updated.") { copy(savingProfile = false) } }
    }

    fun uploadPhoto(photo: HelperSelectedPhoto) = scope.launch {
        mutableState.value = mutableState.value.copy(uploadingPhoto = true, error = null, message = null)
        runCatching {
            val grant = account.createProfilePhotoUpload(
                accessToken,
                photo.fileName,
                photo.contentType,
                photo.bytes,
            )
            account.uploadProfilePhoto(grant, photo.contentType, photo.bytes)
            account.completeProfilePhoto(accessToken, grant.uploadId)
        }.onSuccess {
            mutableState.value = mutableState.value.copy(
                uploadingPhoto = false,
                application = it,
                message = "Profile photo updated.",
            )
        }.onFailure { fail(it.message ?: "The photo could not be uploaded.") { copy(uploadingPhoto = false) } }
    }

    fun removePhoto() = scope.launch {
        runCatching { account.removeProfilePhoto(accessToken) }
            .onSuccess {
                mutableState.value = mutableState.value.copy(application = it, message = "Profile photo removed.")
            }.onFailure { fail(it.message ?: "The photo could not be removed.") }
    }

    fun reportError(message: String) {
        mutableState.value = mutableState.value.copy(error = message)
    }

    private fun bookingCommand(success: String, command: suspend () -> Unit) = scope.launch {
        runCatching { command() }
            .onSuccess {
                mutableState.value = mutableState.value.copy(message = success, error = null)
                refreshRequests()
                refreshJobs()
                refreshChats()
            }.onFailure { fail(it.message ?: "That request could not be updated.") }
    }

    private fun fail(message: String, update: HelperDashboardState.() -> HelperDashboardState = { this }) {
        mutableState.value = mutableState.value.update().copy(error = message)
    }
}
