package com.digibuddy.customer.data.repository

import com.digibuddy.core.model.*
import com.digibuddy.core.network.ApiService
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HelperRepository @Inject constructor(
    private val apiService: ApiService
) {
    suspend fun getNearbyHelpers(
        lat: Double, lng: Double,
        radius: Double = 10.0,
        skills: String? = null
    ): Result<HelpersResponse> = try {
        val response = apiService.getNearbyHelpers(lat, lng, radius, skills)
        if (response.isSuccessful && response.body() != null)
            Result.Success(response.body()!!)
        else
            Result.Error("Failed to fetch helpers")
    } catch (e: Exception) {
        Result.Error(e.message ?: "Network error")
    }

    suspend fun searchHelpers(
        lat: Double, lng: Double,
        radius: Double = 15.0,
        skills: String? = null
    ): Result<HelpersResponse> = try {
        val response = apiService.searchHelpers(lat, lng, radius, skills)
        if (response.isSuccessful && response.body() != null)
            Result.Success(response.body()!!)
        else
            Result.Error("Search failed")
    } catch (e: Exception) {
        Result.Error(e.message ?: "Network error")
    }

    suspend fun getHelperById(id: String): Result<HelperProfile> = try {
        val response = apiService.getHelperById(id)
        if (response.isSuccessful && response.body() != null)
            Result.Success(response.body()!!)
        else
            Result.Error("Helper not found")
    } catch (e: Exception) {
        Result.Error(e.message ?: "Network error")
    }

    suspend fun getHelperRatings(helperId: String): Result<RatingsResponse> = try {
        val response = apiService.getHelperRatings(helperId)
        if (response.isSuccessful && response.body() != null)
            Result.Success(response.body()!!)
        else
            Result.Error("Failed to fetch ratings")
    } catch (e: Exception) {
        Result.Error(e.message ?: "Network error")
    }

    suspend fun createRating(bookingId: String, stars: Int, comment: String?): Result<Rating> = try {
        val response = apiService.createRating(CreateRatingRequest(bookingId, stars, comment))
        if (response.isSuccessful && response.body() != null)
            Result.Success(response.body()!!)
        else
            Result.Error("Failed to submit rating")
    } catch (e: Exception) {
        Result.Error(e.message ?: "Network error")
    }
}
