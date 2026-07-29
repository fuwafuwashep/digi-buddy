package com.digibuddy.backend.notification

import com.digibuddy.backend.auth.AuthenticatedPrincipal
import com.digibuddy.backend.auth.AuthenticationException
import com.digibuddy.shared.contracts.DeepLinkPayload
import com.digibuddy.shared.contracts.NotificationPreferencesResponse
import com.digibuddy.shared.contracts.NotificationRegistrationResponse
import com.digibuddy.shared.contracts.RegisterDeviceTokenRequest
import com.digibuddy.shared.contracts.UpdateNotificationPreferencesRequest
import java.time.Clock
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class PushMessage(val title: String, val body: String, val deepLink: DeepLinkPayload, val eventType: String)
data class PushDeliveryResult(val providerId: String, val accepted: Boolean, val invalidToken: Boolean = false)

interface PushNotificationProvider {
    val developmentAdapter: Boolean
    fun send(deviceToken: String, message: PushMessage): PushDeliveryResult
}

class LocalDevelopmentNotificationProvider : PushNotificationProvider {
    override val developmentAdapter = true
    val deliveries = mutableListOf<Pair<String, PushMessage>>()
    override fun send(deviceToken: String, message: PushMessage): PushDeliveryResult {
        deliveries += deviceToken to message
        return PushDeliveryResult("development-${deliveries.size}", true)
    }
}

class ApnsNotificationProvider(
    private val teamId: String,
    private val keyId: String,
    private val bundleId: String,
    private val privateKeyPath: String,
) : PushNotificationProvider {
    init {
        require(teamId.isNotBlank() && keyId.isNotBlank() && bundleId.isNotBlank() && privateKeyPath.isNotBlank())
    }
    override val developmentAdapter = false
    override fun send(deviceToken: String, message: PushMessage): PushDeliveryResult = throw AuthenticationException(
        "APNS_TRANSPORT_REQUIRED",
        "APNs HTTP/2 transport is not configured on this host.",
        503,
    )
}

private data class DeviceTokenRecord(
    val userId: UUID,
    val deviceId: String,
    val token: String,
    val platform: String,
    val environment: String,
    var active: Boolean,
    var updatedAt: Instant,
)

data class NotificationDeliveryRecord(
    val id: UUID,
    val userId: UUID,
    val eventType: String,
    val providerId: String,
    val accepted: Boolean,
    val createdAt: Instant,
)

class NotificationService(
    private val provider: PushNotificationProvider,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val tokens = ConcurrentHashMap<String, DeviceTokenRecord>()
    private val preferences = ConcurrentHashMap<UUID, NotificationPreferencesResponse>()
    private val deliveries = mutableListOf<NotificationDeliveryRecord>()

    fun register(
        principal: AuthenticatedPrincipal,
        request: RegisterDeviceTokenRequest,
    ): NotificationRegistrationResponse {
        if (request.token.length !in 16..512 || request.deviceId.length !in 8..200) invalid()
        if (request.platform !in setOf("IOS", "ANDROID")) invalid()
        tokens.entries.removeIf { it.value.userId == principal.userId && it.value.deviceId == request.deviceId }
        tokens[request.token] = DeviceTokenRecord(
            principal.userId,
            request.deviceId,
            request.token,
            request.platform,
            request.appEnvironment,
            true,
            clock.instant(),
        )
        return NotificationRegistrationResponse(true, preferences(principal))
    }

    fun preferences(principal: AuthenticatedPrincipal): NotificationPreferencesResponse =
        preferences.computeIfAbsent(principal.userId) { NotificationPreferencesResponse() }

    fun update(
        principal: AuthenticatedPrincipal,
        request: UpdateNotificationPreferencesRequest,
    ): NotificationPreferencesResponse = NotificationPreferencesResponse(
        request.security,
        request.bookings,
        request.messages,
        request.payments,
        request.reminders,
    ).also { preferences[principal.userId] = it }

    fun send(userId: UUID, message: PushMessage) {
        tokens.values.filter { it.userId == userId && it.active }.forEach { token ->
            val result = provider.send(token.token, message)
            if (result.invalidToken) token.active = false
            deliveries += NotificationDeliveryRecord(
                UUID.randomUUID(),
                userId,
                message.eventType,
                result.providerId,
                result.accepted,
                clock.instant(),
            )
        }
    }

    private fun invalid(): Nothing =
        throw AuthenticationException("INVALID_DEVICE_TOKEN", "Device registration failed.", 400)
}
