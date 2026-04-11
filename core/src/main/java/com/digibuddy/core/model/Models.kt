package com.digibuddy.core.model

import com.google.gson.annotations.SerializedName

// ─── User & Auth ──────────────────────────────────────────────────────────────

data class User(
    val id: String = "",
    val phone: String? = null,
    val email: String? = null,
    val name: String = "",
    val avatar: String? = null,
    val role: UserRole = UserRole.CUSTOMER,
    val isVerified: Boolean = false,
    val helperProfile: HelperProfile? = null
)

enum class UserRole { CUSTOMER, HELPER, ADMIN }

data class AuthResponse(
    val user: User,
    val accessToken: String,
    val refreshToken: String
)

data class SendOtpRequest(val phone: String)
data class SendOtpResponse(val message: String, val userId: String, val devCode: String? = null)
data class VerifyOtpRequest(val userId: String, val code: String)
data class LoginRequest(val email: String, val password: String)
data class RegisterRequest(val email: String, val password: String, val name: String, val role: String = "CUSTOMER")
data class CompleteProfileRequest(val userId: String, val name: String, val role: String)
data class RefreshTokenRequest(val refreshToken: String)
data class RefreshTokenResponse(val accessToken: String)

// ─── Helper ───────────────────────────────────────────────────────────────────

data class HelperProfile(
    val id: String = "",
    val userId: String = "",
    val bio: String? = null,
    val skills: List<String> = emptyList(),
    val hourlyRate: Double? = null,
    val isAvailable: Boolean = false,
    val workAddress: String? = null,
    val workLatitude: Double? = null,
    val workLongitude: Double? = null,
    val workRadius: Double = 5.0,
    val avgRating: Double = 0.0,
    val ratingCount: Int = 0,
    val totalSessions: Int = 0,
    val name: String? = null,
    val avatar: String? = null,
    val user: User? = null,
    val distance: Double? = null
)

data class UpdateWorkLocationRequest(
    val workAddress: String,
    val workLatitude: Double,
    val workLongitude: Double,
    val workRadius: Double = 10.0
)

data class UpdateAvailabilityRequest(val isAvailable: Boolean)

data class HelpersResponse(
    val helpers: List<HelperProfile>,
    val total: Int
)

// ─── Booking ──────────────────────────────────────────────────────────────────

data class Booking(
    val id: String = "",
    val customerId: String = "",
    val helperId: String = "",
    val status: BookingStatus = BookingStatus.PENDING,
    val issue: String = "",
    val meetAddress: String? = null,
    val meetLatitude: Double? = null,
    val meetLongitude: Double? = null,
    val meetTime: String? = null,
    val startedAt: String? = null,
    val completedAt: String? = null,
    val duration: Int? = null,
    val totalCost: Double? = null,
    val notes: String? = null,
    val createdAt: String = "",
    val customer: User? = null,
    val helper: HelperProfile? = null,
    val chatRoom: ChatRoomRef? = null,
    val rating: Rating? = null
)

data class ChatRoomRef(val id: String)

enum class BookingStatus {
    PENDING, ACCEPTED, IN_PROGRESS, COMPLETED, CANCELLED, DECLINED
}

data class CreateBookingRequest(
    val helperId: String,
    val issue: String,
    val meetAddress: String? = null,
    val meetLatitude: Double? = null,
    val meetLongitude: Double? = null,
    val meetTime: String? = null
)

data class UpdateBookingStatusRequest(val status: String)

// ─── Chat ─────────────────────────────────────────────────────────────────────

data class ChatRoom(
    val id: String = "",
    val bookingId: String = "",
    val booking: Booking? = null,
    val messages: List<ChatMessage> = emptyList(),
    val updatedAt: String = ""
)

data class ChatMessage(
    val id: String = "",
    val roomId: String = "",
    val senderId: String = "",
    val content: String = "",
    val type: MessageType = MessageType.TEXT,
    val isRead: Boolean = false,
    val sender: User? = null,
    val createdAt: String = ""
)

enum class MessageType { TEXT, IMAGE, LOCATION, SYSTEM }

// ─── Rating ───────────────────────────────────────────────────────────────────

data class Rating(
    val id: String = "",
    val bookingId: String = "",
    val customerId: String = "",
    val helperId: String = "",
    val stars: Int = 0,
    val comment: String? = null,
    val customer: User? = null,
    val createdAt: String = ""
)

data class CreateRatingRequest(val bookingId: String, val stars: Int, val comment: String? = null)
data class RatingsResponse(val ratings: List<Rating>, val total: Int)
