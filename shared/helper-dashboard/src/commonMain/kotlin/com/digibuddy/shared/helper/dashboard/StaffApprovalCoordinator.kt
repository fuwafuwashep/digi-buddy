package com.digibuddy.shared.helper.dashboard

import com.digibuddy.shared.contracts.StaffHelperApplicationSummaryResponse
import com.digibuddy.shared.networking.StaffApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class StaffApprovalState(
    val loading: Boolean = false,
    val applications:
    List<
        StaffHelperApplicationSummaryResponse
        > = emptyList(),
    val approvingUserId:
    String? = null,
    val message: String? = null,
    val error: String? = null,
)

class StaffApprovalCoordinator(
    private val api: StaffApiClient,
    private val accessToken: String,
    private val scope: CoroutineScope =
        CoroutineScope(
            SupervisorJob() +
                Dispatchers.Default,
        ),
) {
    private val mutableState =
        MutableStateFlow(
            StaffApprovalState(),
        )

    val state =
        mutableState.asStateFlow()

    fun load() {
        refresh()
    }

    fun refresh() =
        scope.launch {
            mutableState.value =
                mutableState.value.copy(
                    loading = true,
                    error = null,
                )

            runCatching {
                api.pendingApplications(
                    accessToken,
                ).items
            }
                .onSuccess { applications ->
                    mutableState.value =
                        mutableState.value.copy(
                            loading = false,
                            applications =
                                applications,
                            error = null,
                        )
                }
                .onFailure { error ->
                    mutableState.value =
                        mutableState.value.copy(
                            loading = false,
                            error =
                                error.message
                                    ?: "Applications could not be loaded.",
                        )
                }
        }

    fun approve(
        userId: String,
    ) =
        scope.launch {
            mutableState.value =
                mutableState.value.copy(
                    approvingUserId =
                        userId,
                    error = null,
                    message = null,
                )

            runCatching {
                api.approve(
                    accessToken,
                    userId,
                )
            }
                .onSuccess {
                    mutableState.value =
                        mutableState.value.copy(
                            approvingUserId =
                                null,
                            applications =
                                mutableState
                                    .value
                                    .applications
                                    .filterNot {
                                            application ->
                                        application.userId ==
                                            userId
                                    },
                            message =
                                "Helper approved.",
                            error = null,
                        )
                }
                .onFailure { error ->
                    mutableState.value =
                        mutableState.value.copy(
                            approvingUserId =
                                null,
                            error =
                                error.message
                                    ?: "The helper could not be approved.",
                        )
                }
        }

    fun close() {
        scope.cancel()
    }
}
