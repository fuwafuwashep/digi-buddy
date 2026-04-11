package com.digibuddy.customer.data.repository

import com.digibuddy.core.model.*
import com.digibuddy.core.network.ApiService
import com.digibuddy.customer.data.local.UserPreferences
import javax.inject.Inject
import javax.inject.Singleton

sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val message: String) : Result<Nothing>()
}

@Singleton
class AuthRepository @Inject constructor(
    private val apiService: ApiService,
    private val userPreferences: UserPreferences
) {
    suspend fun sendOtp(phone: String): Result<SendOtpResponse> = try {
        val response = apiService.sendOtp(SendOtpRequest(phone))
        if (response.isSuccessful && response.body() != null)
            Result.Success(response.body()!!)
        else
            Result.Error(response.errorBody()?.string() ?: "Failed to send OTP")
    } catch (e: Exception) {
        Result.Error(e.message ?: "Network error")
    }

    suspend fun verifyOtp(userId: String, code: String): Result<AuthResponse> = try {
        val response = apiService.verifyOtp(VerifyOtpRequest(userId, code))
        if (response.isSuccessful && response.body() != null) {
            val auth = response.body()!!
            userPreferences.saveAuthData(
                auth.accessToken, auth.refreshToken,
                auth.user.id, auth.user.name, auth.user.role.name,
                email = auth.user.email, phone = auth.user.phone, avatar = auth.user.avatar
            )
            Result.Success(auth)
        } else {
            Result.Error("Invalid or expired OTP")
        }
    } catch (e: Exception) {
        Result.Error(e.message ?: "Network error")
    }

    suspend fun loginWithEmail(email: String, password: String): Result<AuthResponse> = try {
        val response = apiService.login(LoginRequest(email, password))
        if (response.isSuccessful && response.body() != null) {
            val auth = response.body()!!
            userPreferences.saveAuthData(
                auth.accessToken, auth.refreshToken,
                auth.user.id, auth.user.name, auth.user.role.name, email = auth.user.email
            )
            Result.Success(auth)
        } else {
            Result.Error("Invalid email or password")
        }
    } catch (e: Exception) {
        Result.Error(e.message ?: "Network error")
    }

    suspend fun register(email: String, password: String, name: String): Result<AuthResponse> = try {
        val response = apiService.register(RegisterRequest(email, password, name))
        if (response.isSuccessful && response.body() != null) {
            val auth = response.body()!!
            userPreferences.saveAuthData(
                auth.accessToken, auth.refreshToken,
                auth.user.id, auth.user.name, auth.user.role.name, email = auth.user.email
            )
            Result.Success(auth)
        } else {
            Result.Error("Registration failed")
        }
    } catch (e: Exception) {
        Result.Error(e.message ?: "Network error")
    }

    suspend fun completeProfile(userId: String, name: String): Result<AuthResponse> = try {
        val response = apiService.completeProfile(CompleteProfileRequest(userId, name, "CUSTOMER"))
        if (response.isSuccessful && response.body() != null) {
            val auth = response.body()!!
            userPreferences.saveAuthData(
                auth.accessToken, auth.refreshToken,
                auth.user.id, auth.user.name, auth.user.role.name
            )
            Result.Success(auth)
        } else {
            Result.Error("Failed to complete profile")
        }
    } catch (e: Exception) {
        Result.Error(e.message ?: "Network error")
    }

    suspend fun logout() = userPreferences.clearAll()

    suspend fun isLoggedIn() = userPreferences.isLoggedIn()
}
