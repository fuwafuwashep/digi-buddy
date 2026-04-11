package com.digibuddy.customer.data.repository

import com.digibuddy.core.model.*
import com.digibuddy.core.network.ApiService
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BookingRepository @Inject constructor(
    private val apiService: ApiService
) {
    suspend fun createBooking(
        helperId: String, issue: String,
        meetAddress: String? = null,
        meetLat: Double? = null, meetLng: Double? = null,
        meetTime: String? = null
    ): Result<Booking> = try {
        val response = apiService.createBooking(
            CreateBookingRequest(helperId, issue, meetAddress, meetLat, meetLng, meetTime)
        )
        if (response.isSuccessful && response.body() != null)
            Result.Success(response.body()!!)
        else
            Result.Error("Failed to create booking")
    } catch (e: Exception) {
        Result.Error(e.message ?: "Network error")
    }

    suspend fun getMyBookings(status: String? = null): Result<List<Booking>> = try {
        val response = apiService.getMyBookings(status)
        if (response.isSuccessful && response.body() != null)
            Result.Success(response.body()!!)
        else
            Result.Error("Failed to fetch bookings")
    } catch (e: Exception) {
        Result.Error(e.message ?: "Network error")
    }

    suspend fun getBookingById(id: String): Result<Booking> = try {
        val response = apiService.getBookingById(id)
        if (response.isSuccessful && response.body() != null)
            Result.Success(response.body()!!)
        else
            Result.Error("Booking not found")
    } catch (e: Exception) {
        Result.Error(e.message ?: "Network error")
    }
}
