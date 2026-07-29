package com.digibuddy.shared.helper.onboarding

import com.digibuddy.shared.contracts.HelperAccountStatus
import com.digibuddy.shared.contracts.HelperOnboardingStatus
import com.digibuddy.shared.contracts.HelperStartupResponse
import com.digibuddy.shared.networking.HelperAccountApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HelperStartupUiState(
    val loading: Boolean = true,
    val response: HelperStartupResponse? = null,
    val errorMessage: String? = null,
)

enum class HelperDestination {
    ONBOARDING,
    UNDER_REVIEW,
    CHANGES_REQUESTED,
    APPROVED_APP,
    SUSPENDED,
    PAUSED,
    REJECTED,
}

fun HelperStartupResponse.resolvedHelperStatus(): HelperAccountStatus = helperStatus ?: when (onboardingStatus) {
    HelperOnboardingStatus.ONBOARDING -> HelperAccountStatus.PROFILE_INCOMPLETE
    HelperOnboardingStatus.UNDER_REVIEW -> HelperAccountStatus.UNDER_REVIEW
    HelperOnboardingStatus.CHANGES_REQUESTED -> HelperAccountStatus.CHANGES_REQUESTED
    HelperOnboardingStatus.APPROVED -> HelperAccountStatus.APPROVED
    HelperOnboardingStatus.SUSPENDED -> HelperAccountStatus.SUSPENDED
}

fun HelperStartupResponse.destination(): HelperDestination = when (resolvedHelperStatus()) {
    HelperAccountStatus.PROFILE_INCOMPLETE,
    HelperAccountStatus.IDENTITY_INFORMATION_REQUIRED,
    HelperAccountStatus.PAYMENT_ONBOARDING_REQUIRED,
    -> HelperDestination.ONBOARDING
    HelperAccountStatus.UNDER_REVIEW -> HelperDestination.UNDER_REVIEW
    HelperAccountStatus.CHANGES_REQUESTED -> HelperDestination.CHANGES_REQUESTED
    HelperAccountStatus.APPROVED -> HelperDestination.APPROVED_APP
    HelperAccountStatus.PAUSED_BY_HELPER -> HelperDestination.PAUSED
    HelperAccountStatus.SUSPENDED -> HelperDestination.SUSPENDED
    HelperAccountStatus.REJECTED -> HelperDestination.REJECTED
}

class HelperStartupCoordinator(
    private val api: HelperAccountApiClient,
    private val accessToken: String,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val mutableState = MutableStateFlow(HelperStartupUiState())
    val state: StateFlow<HelperStartupUiState> = mutableState.asStateFlow()

    fun load() {
        if (mutableState.value.loading && mutableState.value.response != null) return
        mutableState.value = HelperStartupUiState(loading = true)
        scope.launch {
            runCatching { api.startup(accessToken) }
                .onSuccess { mutableState.value = HelperStartupUiState(loading = false, response = it) }
                .onFailure {
                    mutableState.value = HelperStartupUiState(
                        loading = false,
                        errorMessage = "We could not load your helper workspace. Check your connection and try again.",
                    )
                }
        }
    }

    fun resume() {
        mutableState.value = mutableState.value.copy(loading = true, errorMessage = null)
        scope.launch {
            runCatching { api.resume(accessToken) }
                .onSuccess {
                    mutableState.value = mutableState.value.copy(loading = false)
                    load()
                }
                .onFailure {
                    mutableState.value = mutableState.value.copy(
                        loading = false,
                        errorMessage = "We could not resume your helper account. Please try again.",
                    )
                }
        }
    }

    fun approveForLocalTesting() {
        mutableState.value = mutableState.value.copy(loading = true, errorMessage = null)
        scope.launch {
            runCatching { api.approveForLocalDevelopment(accessToken) }
                .onSuccess {
                    mutableState.value = mutableState.value.copy(loading = false)
                    load()
                }
                .onFailure {
                    mutableState.value = mutableState.value.copy(
                        loading = false,
                        errorMessage = "Local approval failed. Submit the complete application first, then try again.",
                    )
                }
        }
    }

    fun close() {
        scope.cancel()
    }
}
