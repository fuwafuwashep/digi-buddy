package com.digibuddy.customer.data.repository

import com.digibuddy.core.model.*
import com.digibuddy.core.network.ApiService
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepository @Inject constructor(
    private val apiService: ApiService
) {
    suspend fun getMyChatRooms(): Result<List<ChatRoom>> = try {
        val response = apiService.getMyChatRooms()
        if (response.isSuccessful && response.body() != null)
            Result.Success(response.body()!!)
        else
            Result.Error("Failed to fetch chat rooms")
    } catch (e: Exception) {
        Result.Error(e.message ?: "Network error")
    }

    suspend fun getRoomMessages(roomId: String, page: Int = 1): Result<List<ChatMessage>> = try {
        val response = apiService.getRoomMessages(roomId, page)
        if (response.isSuccessful && response.body() != null)
            Result.Success(response.body()!!)
        else
            Result.Error("Failed to fetch messages")
    } catch (e: Exception) {
        Result.Error(e.message ?: "Network error")
    }
}
