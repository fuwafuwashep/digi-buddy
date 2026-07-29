package com.digibuddy.shared.contracts

import kotlinx.serialization.Serializable

@Serializable
data class RegisterDeviceTokenRequest(
    val token: String,
    val platform: String,
    val deviceId: String,
    val appEnvironment: String,
)

@Serializable
data class NotificationPreferencesResponse(
    val security: Boolean = true,
    val bookings: Boolean = true,
    val messages: Boolean = true,
    val payments: Boolean = true,
    val reminders: Boolean = true,
)

@Serializable
data class UpdateNotificationPreferencesRequest(
    val security: Boolean,
    val bookings: Boolean,
    val messages: Boolean,
    val payments: Boolean,
    val reminders: Boolean,
)

@Serializable
data class NotificationRegistrationResponse(val registered: Boolean, val preferences: NotificationPreferencesResponse)

@Serializable
data class DeepLinkPayload(
    val destination: String,
    val resourceId: String? = null,
    val requiresAuthentication: Boolean = true,
    val expiresAt: String? = null,
)
