package com.digibuddy.shared.contracts

import kotlinx.serialization.Serializable

@Serializable
data class CompleteCustomerOnboardingRequest(
    val firstName: String,
    val lastName: String,
    val zipCode: String,
    val profilePhotoUploadId: String? = null,
    val locationPermission: String = "NOT_REQUESTED",
    val notificationPermission: String = "NOT_REQUESTED",
    val technologyPreferences: List<String> = emptyList(),
)

@Serializable
data class SavedAddressResponse(
    val id: String,
    val label: String,
    val line1: String,
    val line2: String? = null,
    val city: String,
    val region: String,
    val zipCode: String,
)

@Serializable
data class CustomerSettingsResponse(
    val notificationsEnabled: Boolean = false,
    val permissionStatus: Map<String, String> = emptyMap(),
    val followSystemTextSize: Boolean = true,
    val extraLargeText: Boolean = false,
    val highContrast: Boolean = false,
    val reducedMotion: Boolean = false,
    val simplifiedInstructions: Boolean = true,
    val biometricUnlockEnabled: Boolean = false,
)

@Serializable
data class CustomerProfileResponse(
    val customerId: String,
    val firstName: String,
    val lastName: String,
    val publicDisplayName: String,
    val verifiedPhoneNumber: String,
    val verifiedEmail: String? = null,
    val profilePictureUrl: String? = null,
    val zipCode: String,
    val savedAddresses: List<SavedAddressResponse> = emptyList(),
    val technologyPreferences: List<String> = emptyList(),
    val accountCreationDate: String,
    val accountStatus: String,
    val onboardingComplete: Boolean,
    val settings: CustomerSettingsResponse = CustomerSettingsResponse(),
)

@Serializable
data class UpdateCustomerNameRequest(val firstName: String, val lastName: String)

@Serializable
data class UpdateZipCodeRequest(val zipCode: String)

@Serializable
data class UpdateTechnologyPreferencesRequest(val technologyPreferences: List<String>)

@Serializable
data class UpdateNotificationSettingsRequest(val enabled: Boolean, val permissionStatus: String)

@Serializable
data class UpdateAccessibilitySettingsRequest(
    val followSystemTextSize: Boolean,
    val extraLargeText: Boolean,
    val highContrast: Boolean,
    val reducedMotion: Boolean,
    val simplifiedInstructions: Boolean,
)

@Serializable
data class UpdatePrivacySettingsRequest(val locationPermissionStatus: String)

@Serializable
data class SaveAddressRequest(
    val label: String,
    val line1: String,
    val line2: String? = null,
    val city: String,
    val region: String,
    val zipCode: String,
)

@Serializable
data class ProfilePhotoUploadRequest(val fileName: String, val contentType: String, val sizeBytes: Long)

@Serializable
data class ProfilePhotoUploadGrantResponse(
    val uploadId: String,
    val uploadUrl: String,
    val method: String = "PUT",
    val headers: Map<String, String> = emptyMap(),
    val maxSizeBytes: Long,
    val expiresInSeconds: Long,
)

@Serializable
data class CompleteProfilePhotoUploadRequest(val uploadId: String)

@Serializable
data class TrustedDeviceResponse(
    val deviceId: String,
    val displayName: String,
    val lastSeenAt: String,
    val currentDevice: Boolean,
)

@Serializable
data class RecentSignInResponse(
    val sessionId: String,
    val deviceId: String,
    val signedInAt: String,
    val active: Boolean,
)

@Serializable
data class SecurityOverviewResponse(
    val trustedDevices: List<TrustedDeviceResponse>,
    val recentSignIns: List<RecentSignInResponse>,
)

@Serializable
data class DataExportRequestResponse(val requestId: String, val status: String, val requestedAt: String)

@Serializable
data class DeleteAccountRequest(val confirmation: String)

@Serializable
data class AccountDeletionResponse(val requestId: String, val status: String, val requestedAt: String)
