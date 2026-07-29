package com.digibuddy.shared.profile

import com.digibuddy.shared.contracts.CompleteCustomerOnboardingRequest
import com.digibuddy.shared.contracts.CustomerProfileResponse
import com.digibuddy.shared.contracts.SaveAddressRequest
import com.digibuddy.shared.contracts.SecurityOverviewResponse
import com.digibuddy.shared.contracts.UpdateAccessibilitySettingsRequest
import com.digibuddy.shared.networking.CustomerProfileApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class OnboardingStep { WELCOME, FIRST_NAME, LAST_NAME, PHOTO, ZIP, LOCATION, NOTIFICATIONS, INTERESTS, COMPLETE }

data class OnboardingDraft(
    val firstName: String = "",
    val lastName: String = "",
    val zipCode: String = "",
    val locationPermission: String = "NOT_REQUESTED",
    val notificationPermission: String = "NOT_REQUESTED",
    val technologyPreferences: Set<String> = emptySet(),
)

sealed interface ProfileState {
    data object Loading : ProfileState
    data class Onboarding(val step: OnboardingStep, val draft: OnboardingDraft, val error: String? = null) :
        ProfileState
    data class Profile(val value: CustomerProfileResponse, val message: String? = null) : ProfileState
    data class Failure(val message: String) : ProfileState
}

interface CustomerPhotoActions {
    suspend fun choosePhoto(): SelectedPhoto?
    suspend fun takePhoto(): SelectedPhoto?
    suspend fun cropAndCompress(photo: SelectedPhoto): SelectedPhoto
}

data class SelectedPhoto(val fileName: String, val contentType: String, val bytes: ByteArray)

object UnavailableCustomerPhotoActions : CustomerPhotoActions {
    override suspend fun choosePhoto() = null
    override suspend fun takePhoto() = null
    override suspend fun cropAndCompress(photo: SelectedPhoto) = photo
}

class CustomerProfileCoordinator(
    private val api: CustomerProfileApiClient,
    private val accessToken: String,
    private val photos: CustomerPhotoActions = UnavailableCustomerPhotoActions,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val mutableState = MutableStateFlow<ProfileState>(ProfileState.Loading)
    val state = mutableState.asStateFlow()
    private val mutableSecurity = MutableStateFlow<SecurityOverviewResponse?>(null)
    val security = mutableSecurity.asStateFlow()
    private var lastPhoto: SelectedPhoto? = null
    private var uploadProgress = 0f

    fun load() = scope.launch {
        runCatching { api.profile(accessToken) }.onSuccess {
            mutableState.value = if (it.onboardingComplete) {
                ProfileState.Profile(it)
            } else {
                ProfileState.Onboarding(OnboardingStep.WELCOME, OnboardingDraft())
            }
        }.onFailure { mutableState.value = ProfileState.Failure("We could not load your profile. Try again.") }
    }

    fun next(step: OnboardingStep, draft: OnboardingDraft) {
        mutableState.value = ProfileState.Onboarding(step, draft)
    }

    fun finish(draft: OnboardingDraft) = scope.launch {
        runCatching {
            api.completeOnboarding(
                accessToken,
                CompleteCustomerOnboardingRequest(
                    draft.firstName,
                    draft.lastName,
                    draft.zipCode,
                    locationPermission = draft.locationPermission,
                    notificationPermission = draft.notificationPermission,
                    technologyPreferences = draft.technologyPreferences.toList(),
                ),
            )
        }.mapCatching { profile ->
            lastPhoto?.let { uploadNow(it) } ?: profile
        }.onSuccess { mutableState.value = ProfileState.Onboarding(OnboardingStep.COMPLETE, draft) }
            .onFailure { mutableState.value = ProfileState.Onboarding(OnboardingStep.ZIP, draft, it.message) }
    }

    fun showProfile() = load()

    fun updateName(first: String, last: String) = update { api.updateName(accessToken, first, last) }
    fun updateZip(zip: String) = update { api.updateZip(accessToken, zip) }
    fun updatePreferences(values: List<String>) = update { api.updatePreferences(accessToken, values) }
    fun updateAccessibility(request: UpdateAccessibilitySettingsRequest) = update {
        api.updateAccessibility(accessToken, request)
    }
    fun updateNotifications(enabled: Boolean, permission: String) = update {
        api.updateNotifications(accessToken, enabled, permission)
    }
    fun updatePrivacy(permission: String) = update { api.updatePrivacy(accessToken, permission) }
    fun saveAddress(request: SaveAddressRequest) = update { api.saveAddress(accessToken, request) }
    fun loadSecurity() = scope.launch {
        runCatching { api.security(accessToken) }.onSuccess { mutableSecurity.value = it }
    }
    fun requestExport() = scope.launch {
        runCatching { api.requestExport(accessToken) }.onSuccess {
            val current = mutableState.value as? ProfileState.Profile ?: return@onSuccess
            mutableState.value = current.copy(message = "Your data download request was received.")
        }
    }
    fun requestDeletion() = scope.launch {
        runCatching { api.requestDeletion(accessToken) }.onFailure {
            val current = mutableState.value as? ProfileState.Profile ?: return@onFailure
            mutableState.value = current.copy(message = "Verify your phone again, then retry account deletion.")
        }
    }
    fun removePhoto() = update { api.removePhoto(accessToken) }
    fun choosePhoto() = selectPhoto { photos.choosePhoto() }
    fun takePhoto() = selectPhoto { photos.takePhoto() }
    fun retryPhotoUpload() {
        lastPhoto?.let(::upload)
    }

    private fun selectPhoto(select: suspend () -> SelectedPhoto?) = scope.launch {
        select()?.let { selected ->
            val prepared = photos.cropAndCompress(selected)
            if (prepared.contentType !in setOf("image/jpeg", "image/png", "image/webp") ||
                prepared.bytes.size !in 1..MAX_PHOTO_BYTES
            ) {
                val current = mutableState.value as? ProfileState.Profile ?: return@let
                mutableState.value = current.copy(message = "Choose a JPEG, PNG, or WebP image under 5 MB.")
            } else if (mutableState.value is ProfileState.Onboarding) {
                lastPhoto = prepared
                val current = mutableState.value as ProfileState.Onboarding
                mutableState.value = ProfileState.Onboarding(OnboardingStep.ZIP, current.draft)
            } else {
                upload(prepared)
            }
        }
    }

    private fun upload(photo: SelectedPhoto) {
        lastPhoto = photo
        scope.launch {
            runCatching { uploadNow(photo) }
                .onSuccess { mutableState.value = ProfileState.Profile(it, "Photo uploaded.") }
                .onFailure {
                    val current = mutableState.value as? ProfileState.Profile ?: return@onFailure
                    mutableState.value =
                        current.copy(
                            message = "Photo upload failed at ${(uploadProgress * 100).toInt()}%. Retry when ready.",
                        )
                }
        }
    }

    private suspend fun uploadNow(photo: SelectedPhoto): CustomerProfileResponse {
        lastPhoto = photo
        val grant = api.createPhotoUpload(accessToken, photo.fileName, photo.contentType, photo.bytes)
        api.uploadPhoto(grant, photo.contentType, photo.bytes) { uploadProgress = it }
        return api.completePhoto(accessToken, grant.uploadId)
    }

    private fun update(block: suspend () -> CustomerProfileResponse) = scope.launch {
        runCatching { block() }.onSuccess { mutableState.value = ProfileState.Profile(it) }
    }

    private companion object {
        const val MAX_PHOTO_BYTES = 5 * 1024 * 1024
    }
}
