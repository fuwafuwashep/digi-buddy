package com.digibuddy.shared.authentication

import com.digibuddy.shared.contracts.AuthenticationTokensResponse
import com.digibuddy.shared.contracts.NormalizedPhoneResponse
import com.digibuddy.shared.contracts.VerificationChallengeResponse
import com.digibuddy.shared.contracts.VerifyPhoneCodeRequest
import com.digibuddy.shared.networking.AuthenticationApiClient
import com.digibuddy.shared.networking.AuthenticationApiException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface AuthenticationState {
    data object EnterPhone : AuthenticationState

    data class ConfirmPhone(val phone: NormalizedPhoneResponse) : AuthenticationState

    data class EnterCode(
        val challenge: VerificationChallengeResponse,
        val secondFactor: Boolean,
        val errorMessage: String? = null,
    ) : AuthenticationState

    data class EmailPassword(val errorMessage: String? = null) : AuthenticationState

    data class ExpiredCode(val secondFactor: Boolean) : AuthenticationState

    data class RateLimited(val retryAfterSeconds: Long?) : AuthenticationState

    data class TemporarilyLocked(val retryAfterSeconds: Long?) : AuthenticationState

    data class NetworkFailure(
        val retry: RetryAction,
        val details: String? = null,
    ) : AuthenticationState

    data class Authenticated(val userId: String, val accessToken: String) : AuthenticationState
}

enum class RetryAction {
    PHONE_ENTRY,
    PHONE_CONFIRMATION,
    CODE,
    EMAIL_PASSWORD,
}

class AuthenticationCoordinator(
    private val api: AuthenticationApiClient,
    private val refreshTokenStore: RefreshTokenStore,
    private val deviceId: String,
    private val deviceName: String,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val mutableState = MutableStateFlow<AuthenticationState>(AuthenticationState.EnterPhone)
    val state: StateFlow<AuthenticationState> = mutableState.asStateFlow()
    private var lastChallenge: AuthenticationState.EnterCode? = null

    fun restoreSession() {
        val token = refreshTokenStore.read() ?: return
        scope.launch {
            runCatching { api.refresh(token) }
                .onSuccess(::acceptTokens)
                .onFailure {
                    refreshTokenStore.clear()
                    mutableState.value = AuthenticationState.EnterPhone
                }
        }
    }

    fun normalizePhone(phoneNumber: String, defaultRegion: String = "US") {
        scope.launch {
            runApi(RetryAction.PHONE_ENTRY) {
                mutableState.value = AuthenticationState.ConfirmPhone(api.normalizePhone(phoneNumber, defaultRegion))
            }
        }
    }

    fun confirmPhone(phone: NormalizedPhoneResponse) {
        scope.launch {
            runApi(RetryAction.PHONE_CONFIRMATION) {
                showChallenge(api.startPhoneVerification(phone.e164), secondFactor = false)
            }
        }
    }

    fun submitCode(code: String) {
        val current = mutableState.value as? AuthenticationState.EnterCode ?: return
        scope.launch {
            runApi(RetryAction.CODE) {
                val request = VerifyPhoneCodeRequest(current.challenge.attemptId, code, deviceId, deviceName)
                val tokens = if (current.secondFactor) {
                    api.verifyEmailSecondFactor(request)
                } else {
                    api.verifyPhoneCode(request)
                }
                acceptTokens(tokens)
            }
        }
    }

    fun resendCode() {
        val current = (mutableState.value as? AuthenticationState.EnterCode) ?: lastChallenge ?: return
        scope.launch {
            runApi(RetryAction.CODE) {
                showChallenge(api.resend(current.challenge.attemptId), current.secondFactor)
            }
        }
    }

    fun showEmailPassword() {
        mutableState.value = AuthenticationState.EmailPassword()
    }

    fun startEmailPasswordLogin(email: String, password: String) {
        scope.launch {
            runApi(RetryAction.EMAIL_PASSWORD) {
                showChallenge(api.startEmailPasswordLogin(email, password), secondFactor = true)
            }
        }
    }

    fun addEmailCredential(email: String, password: String, onComplete: (String) -> Unit) {
        val authenticated = mutableState.value as? AuthenticationState.Authenticated ?: return
        scope.launch {
            runCatching { api.addEmailCredential(authenticated.accessToken, email, password) }
                .onSuccess { onComplete(it.message) }
                .onFailure { onComplete(it.message ?: "Email sign-in could not be added.") }
        }
    }

    fun logout(allDevices: Boolean = false) {
        val authenticated = mutableState.value as? AuthenticationState.Authenticated
        refreshTokenStore.clear()
        mutableState.value = AuthenticationState.EnterPhone
        if (authenticated != null) {
            scope.launch {
                runCatching {
                    if (allDevices) api.logoutAll(authenticated.accessToken) else api.logout(authenticated.accessToken)
                }
            }
        }
    }

    fun backToPhone() {
        mutableState.value = AuthenticationState.EnterPhone
    }

    fun retryAfterNetworkFailure() {
        mutableState.value = when ((mutableState.value as? AuthenticationState.NetworkFailure)?.retry) {
            RetryAction.CODE -> lastChallenge ?: AuthenticationState.EnterPhone
            RetryAction.EMAIL_PASSWORD -> AuthenticationState.EmailPassword()
            else -> AuthenticationState.EnterPhone
        }
    }

    fun close() {
        scope.cancel()
    }

    private fun showChallenge(challenge: VerificationChallengeResponse, secondFactor: Boolean) {
        val state = AuthenticationState.EnterCode(challenge, secondFactor)
        lastChallenge = state
        mutableState.value = state
    }

    private fun acceptTokens(tokens: AuthenticationTokensResponse) {
        refreshTokenStore.save(tokens.refreshToken)
        mutableState.value = AuthenticationState.Authenticated(tokens.userId, tokens.accessToken)
    }

    private suspend fun runApi(
        retry: RetryAction,
        block: suspend () -> Unit,
    ) {
        try {
            block()
        } catch (error: AuthenticationApiException) {
            mutableState.value = when (error.code) {
                "CODE_EXPIRED" ->
                    AuthenticationState.ExpiredCode(lastChallenge?.secondFactor == true)

                "INVALID_CODE" ->
                    (lastChallenge ?: AuthenticationState.EnterPhone).let { prior ->
                        if (prior is AuthenticationState.EnterCode) {
                            prior.copy(errorMessage = error.message)
                        } else {
                            prior
                        }
                    }

                "LOGIN_FAILED" ->
                    AuthenticationState.EmailPassword(error.message)

                "RATE_LIMITED", "RESEND_NOT_READY" ->
                    AuthenticationState.RateLimited(error.retryAfterSeconds)

                "ACCOUNT_LOCKED" ->
                    AuthenticationState.TemporarilyLocked(error.retryAfterSeconds)

                else ->
                    AuthenticationState.NetworkFailure(
                        retry = retry,
                        details = "Code: ${error.code}\nMessage: ${error.message}",
                    )
            }
        } catch (error: Exception) {
            mutableState.value = AuthenticationState.NetworkFailure(
                retry = retry,
                details = "${error::class.simpleName}\nMessage: ${error.message}",
            )
        }
    }
}
