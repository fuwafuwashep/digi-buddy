package com.digibuddy.customer.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.digibuddy.customer.data.repository.AuthRepository
import com.digibuddy.customer.data.repository.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val otpSent: Boolean = false,
    val userId: String = "",
    val devCode: String? = null,
    val isLoggedIn: Boolean = false
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun sendOtp(phone: String) = viewModelScope.launch {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        when (val result = authRepository.sendOtp(phone)) {
            is Result.Success -> _uiState.value = _uiState.value.copy(
                isLoading = false, otpSent = true, userId = result.data.userId, devCode = result.data.devCode
            )
            is Result.Error -> _uiState.value = _uiState.value.copy(
                isLoading = false, error = result.message
            )
        }
    }

    fun loginWithEmail(email: String, password: String) = viewModelScope.launch {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        when (val result = authRepository.loginWithEmail(email, password)) {
            is Result.Success -> _uiState.value = _uiState.value.copy(
                isLoading = false, isLoggedIn = true
            )
            is Result.Error -> _uiState.value = _uiState.value.copy(
                isLoading = false, error = result.message
            )
        }
    }

    fun register(email: String, password: String, name: String) = viewModelScope.launch {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        when (val result = authRepository.register(email, password, name)) {
            is Result.Success -> _uiState.value = _uiState.value.copy(
                isLoading = false, isLoggedIn = true
            )
            is Result.Error -> _uiState.value = _uiState.value.copy(
                isLoading = false, error = result.message
            )
        }
    }

    fun clearError() { _uiState.value = _uiState.value.copy(error = null) }
}
