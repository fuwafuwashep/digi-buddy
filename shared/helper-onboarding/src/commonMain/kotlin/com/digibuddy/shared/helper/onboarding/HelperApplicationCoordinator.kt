package com.digibuddy.shared.helper.onboarding

import com.digibuddy.shared.contracts.HelperApplicationResponse
import com.digibuddy.shared.contracts.HelperApplicationStepRequest
import com.digibuddy.shared.contracts.HelperOnboardingStep
import com.digibuddy.shared.networking.HelperAccountApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HelperApplicationUiState(
    val loading: Boolean = true,
    val saving: Boolean = false,
    val application: HelperApplicationResponse? = null,
    val errorMessage: String? = null,
)

class HelperApplicationCoordinator(
    private val api: HelperAccountApiClient,
    private val accessToken: String,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val mutableState = MutableStateFlow(HelperApplicationUiState())
    val state: StateFlow<HelperApplicationUiState> = mutableState.asStateFlow()

    fun load() = request { api.application(accessToken) }

    fun saveStep(step: HelperOnboardingStep, payload: HelperApplicationStepRequest) =
        request(saving = true) { api.saveStep(accessToken, step, payload) }

    fun submit() = request(saving = true) { api.submit(accessToken) }

    fun uploadPhoto(fileName: String, contentType: String, bytes: ByteArray) = request(saving = true) {
        val grant = api.createProfilePhotoUpload(accessToken, fileName, contentType, bytes)
        api.uploadProfilePhoto(grant, contentType, bytes)
        api.completeProfilePhoto(accessToken, grant.uploadId)
    }

    fun reportError(message: String) {
        mutableState.value = mutableState.value.copy(errorMessage = message, saving = false)
    }

    fun close() = scope.cancel()

    private fun request(saving: Boolean = false, block: suspend () -> HelperApplicationResponse) {
        mutableState.value = mutableState.value.copy(
            loading = mutableState.value.application == null,
            saving = saving,
            errorMessage = null,
        )
        scope.launch {
            runCatching { block() }
                .onSuccess { mutableState.value = HelperApplicationUiState(application = it, loading = false) }
                .onFailure {
                    mutableState.value = mutableState.value.copy(
                        loading = false,
                        saving = false,
                        errorMessage = it.message
                            ?: "We could not save that step. Check your connection and try again.",
                    )
                }
        }
    }
}
