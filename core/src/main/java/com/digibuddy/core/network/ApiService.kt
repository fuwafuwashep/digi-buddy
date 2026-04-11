package com.digibuddy.core.network

import com.digibuddy.core.model.*
import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    // ── Auth ──────────────────────────────────────────────────────────────────
    @POST("auth/send-otp")
    suspend fun sendOtp(@Body request: SendOtpRequest): Response<SendOtpResponse>

    @POST("auth/verify-otp")
    suspend fun verifyOtp(@Body request: VerifyOtpRequest): Response<AuthResponse>

    @POST("auth/register")
    suspend fun register(@Body request: RegisterRequest): Response<AuthResponse>

    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): Response<AuthResponse>

    @POST("auth/refresh")
    suspend fun refreshToken(@Body request: RefreshTokenRequest): Response<RefreshTokenResponse>

    @POST("auth/complete-profile")
    suspend fun completeProfile(@Body request: CompleteProfileRequest): Response<AuthResponse>

    // ── Users ─────────────────────────────────────────────────────────────────
    @GET("users/me")
    suspend fun getMe(): Response<User>

    @PUT("users/me")
    suspend fun updateMe(@Body body: Map<String, String>): Response<User>

    // ── Helpers ───────────────────────────────────────────────────────────────
    @GET("helpers/nearby")
    suspend fun getNearbyHelpers(
        @Query("lat") lat: Double,
        @Query("lng") lng: Double,
        @Query("radius") radius: Double = 10.0,
        @Query("skills") skills: String? = null,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20
    ): Response<HelpersResponse>

    @GET("helpers/search")
    suspend fun searchHelpers(
        @Query("lat") lat: Double,
        @Query("lng") lng: Double,
        @Query("radius") radius: Double = 15.0,
        @Query("skills") skills: String? = null
    ): Response<HelpersResponse>

    @GET("helpers/{id}")
    suspend fun getHelperById(@Path("id") id: String): Response<HelperProfile>

    @PUT("helpers/profile")
    suspend fun updateHelperProfile(@Body body: Map<String, Any>): Response<HelperProfile>

    @PUT("helpers/availability")
    suspend fun updateAvailability(@Body request: UpdateAvailabilityRequest): Response<Map<String, Boolean>>

    @PUT("helpers/work-location")
    suspend fun updateWorkLocation(@Body request: UpdateWorkLocationRequest): Response<HelperProfile>

    // ── Bookings ──────────────────────────────────────────────────────────────
    @POST("bookings")
    suspend fun createBooking(@Body request: CreateBookingRequest): Response<Booking>

    @GET("bookings")
    suspend fun getMyBookings(
        @Query("status") status: String? = null,
        @Query("role") role: String? = null
    ): Response<List<Booking>>

    @GET("bookings/{id}")
    suspend fun getBookingById(@Path("id") id: String): Response<Booking>

    @PUT("bookings/{id}/status")
    suspend fun updateBookingStatus(
        @Path("id") id: String,
        @Body request: UpdateBookingStatusRequest
    ): Response<Booking>

    // ── Ratings ───────────────────────────────────────────────────────────────
    @GET("ratings/helper/{helperId}")
    suspend fun getHelperRatings(
        @Path("helperId") helperId: String,
        @Query("page") page: Int = 1
    ): Response<RatingsResponse>

    @POST("ratings")
    suspend fun createRating(@Body request: CreateRatingRequest): Response<Rating>

    // ── Chat ──────────────────────────────────────────────────────────────────
    @GET("chat/rooms")
    suspend fun getMyChatRooms(): Response<List<ChatRoom>>

    @GET("chat/rooms/{roomId}/messages")
    suspend fun getRoomMessages(
        @Path("roomId") roomId: String,
        @Query("page") page: Int = 1
    ): Response<List<ChatMessage>>
}
