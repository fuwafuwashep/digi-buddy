@file:Suppress("TooManyFunctions")

package com.digibuddy.backend.customer

import com.digibuddy.backend.auth.AuthService
import com.digibuddy.backend.auth.AuthenticatedPrincipal
import com.digibuddy.backend.auth.AuthenticationException
import com.digibuddy.backend.auth.SystemTimeSource
import com.digibuddy.backend.auth.TimeSource
import com.digibuddy.shared.contracts.AccountDeletionResponse
import com.digibuddy.shared.contracts.CompleteCustomerOnboardingRequest
import com.digibuddy.shared.contracts.CustomerProfileResponse
import com.digibuddy.shared.contracts.CustomerSettingsResponse
import com.digibuddy.shared.contracts.DataExportRequestResponse
import com.digibuddy.shared.contracts.ProfilePhotoUploadGrantResponse
import com.digibuddy.shared.contracts.ProfilePhotoUploadRequest
import com.digibuddy.shared.contracts.RecentSignInResponse
import com.digibuddy.shared.contracts.SaveAddressRequest
import com.digibuddy.shared.contracts.SavedAddressResponse
import com.digibuddy.shared.contracts.SecurityOverviewResponse
import com.digibuddy.shared.contracts.TrustedDeviceResponse
import com.digibuddy.shared.contracts.UpdateAccessibilitySettingsRequest
import java.time.Duration
import java.time.Instant
import java.util.UUID

data class CustomerProfileRecord(
    val userId: UUID,
    val firstName: String,
    val lastName: String,
    val displayName: String,
    val zipCode: String,
    val photoUrl: String? = null,
    val technologyPreferences: Set<String> = emptySet(),
    val locationPermission: String = "NOT_REQUESTED",
    val notificationPermission: String = "NOT_REQUESTED",
    val notificationsEnabled: Boolean = false,
    val followSystemTextSize: Boolean = true,
    val extraLargeText: Boolean = false,
    val highContrast: Boolean = false,
    val reducedMotion: Boolean = false,
    val simplifiedInstructions: Boolean = true,
    val biometricUnlockEnabled: Boolean = false,
    val completedAt: Instant,
)

data class ProfileUploadRecord(
    val id: UUID,
    val userId: UUID,
    val contentType: String,
    val sizeBytes: Long,
    val objectKey: String,
    val expiresAt: Instant,
    val status: String = "PENDING",
)

interface CustomerProfileRepository {
    fun findProfile(userId: UUID): CustomerProfileRecord?
    fun saveProfile(profile: CustomerProfileRecord)
    fun saveAddress(userId: UUID, address: SavedAddressResponse)
    fun addresses(userId: UUID): List<SavedAddressResponse>
    fun saveUpload(upload: ProfileUploadRecord)
    fun findUpload(id: UUID): ProfileUploadRecord?
    fun updateUpload(upload: ProfileUploadRecord)
    fun createExportRequest(userId: UUID, at: Instant): UUID
    fun createDeletionRequest(userId: UUID, at: Instant): UUID
}

class InMemoryCustomerProfileRepository : CustomerProfileRepository {
    private val profiles = mutableMapOf<UUID, CustomerProfileRecord>()
    private val addresses = mutableMapOf<UUID, MutableList<SavedAddressResponse>>()
    private val uploads = mutableMapOf<UUID, ProfileUploadRecord>()

    override fun findProfile(userId: UUID) = profiles[userId]
    override fun saveProfile(profile: CustomerProfileRecord) {
        profiles[profile.userId] = profile
    }
    override fun saveAddress(userId: UUID, address: SavedAddressResponse) {
        addresses.getOrPut(userId) { mutableListOf() } += address
    }
    override fun addresses(userId: UUID) = addresses[userId]?.toList().orEmpty()
    override fun saveUpload(upload: ProfileUploadRecord) {
        uploads[upload.id] = upload
    }
    override fun findUpload(id: UUID) = uploads[id]
    override fun updateUpload(upload: ProfileUploadRecord) {
        uploads[upload.id] = upload
    }
    override fun createExportRequest(userId: UUID, at: Instant) = UUID.randomUUID()
    override fun createDeletionRequest(userId: UUID, at: Instant) = UUID.randomUUID()
}

interface ActiveBookingDeletionGuard {
    fun hasActiveBookings(customerId: UUID): Boolean
}

object NoBookingsImplementedGuard : ActiveBookingDeletionGuard {
    override fun hasActiveBookings(customerId: UUID) = false
}

class CustomerProfileService(
    private val repository: CustomerProfileRepository,
    private val authService: AuthService,
    private val objectStorage: ProfileObjectStorage,
    private val bookingGuard: ActiveBookingDeletionGuard = NoBookingsImplementedGuard,
    private val timeSource: TimeSource = SystemTimeSource(),
) {
    fun publicDisplayName(userId: UUID): String = repository.findProfile(userId)?.displayName ?: "Customer"

    fun profile(principal: AuthenticatedPrincipal): CustomerProfileResponse {
        val identity = authService.accountProfileIdentity(principal)
        val profile = repository.findProfile(identity.userId)
        return CustomerProfileResponse(
            customerId = identity.userId.toString(),
            firstName = profile?.firstName.orEmpty(),
            lastName = profile?.lastName.orEmpty(),
            publicDisplayName = profile?.displayName.orEmpty(),
            verifiedPhoneNumber = identity.phoneE164,
            verifiedEmail = identity.verifiedEmail,
            profilePictureUrl = profile?.photoUrl,
            zipCode = profile?.zipCode.orEmpty(),
            savedAddresses = repository.addresses(identity.userId),
            technologyPreferences = profile?.technologyPreferences?.sorted().orEmpty(),
            accountCreationDate = identity.createdAt.toString(),
            accountStatus = identity.accountStatus,
            onboardingComplete = profile != null,
            settings = profile?.toSettings() ?: CustomerSettingsResponse(),
        )
    }

    fun completeOnboarding(
        principal: AuthenticatedPrincipal,
        request: CompleteCustomerOnboardingRequest,
    ): CustomerProfileResponse {
        val identity = authService.accountProfileIdentity(principal)
        val firstName = validateName(request.firstName, "first name")
        val lastName = validateName(request.lastName, "last name")
        val zip = validateZip(request.zipCode)
        val preferences = validatePreferences(request.technologyPreferences)
        repository.saveProfile(
            CustomerProfileRecord(
                userId = identity.userId,
                firstName = firstName,
                lastName = lastName,
                displayName = "$firstName ${lastName.first()}.",
                zipCode = zip,
                technologyPreferences = preferences,
                locationPermission = validatePermission(request.locationPermission),
                notificationPermission = validatePermission(request.notificationPermission),
                completedAt = timeSource.now(),
            ),
        )
        request.profilePhotoUploadId?.let { completePhoto(principal, it) }
        return profile(principal)
    }

    fun updateName(principal: AuthenticatedPrincipal, first: String, last: String): CustomerProfileResponse =
        updateProfile(principal) { current ->
            val firstName = validateName(first, "first name")
            val lastName = validateName(last, "last name")
            current.copy(firstName = firstName, lastName = lastName, displayName = "$firstName ${lastName.first()}.")
        }

    fun updateZip(principal: AuthenticatedPrincipal, zip: String) =
        updateProfile(principal) { it.copy(zipCode = validateZip(zip)) }

    fun updatePreferences(principal: AuthenticatedPrincipal, values: List<String>) =
        updateProfile(principal) { it.copy(technologyPreferences = validatePreferences(values)) }

    fun updateNotifications(principal: AuthenticatedPrincipal, enabled: Boolean, permission: String) =
        updateProfile(principal) {
            it.copy(notificationsEnabled = enabled, notificationPermission = validatePermission(permission))
        }

    fun updatePrivacy(principal: AuthenticatedPrincipal, locationPermission: String) =
        updateProfile(principal) { it.copy(locationPermission = validatePermission(locationPermission)) }

    fun updateAccessibility(principal: AuthenticatedPrincipal, request: UpdateAccessibilitySettingsRequest) =
        updateProfile(principal) {
            it.copy(
                followSystemTextSize = request.followSystemTextSize,
                extraLargeText = request.extraLargeText,
                highContrast = request.highContrast,
                reducedMotion = request.reducedMotion,
                simplifiedInstructions = request.simplifiedInstructions,
            )
        }

    fun saveAddress(principal: AuthenticatedPrincipal, request: SaveAddressRequest): CustomerProfileResponse {
        requireProfile(principal)
        repository.saveAddress(
            principal.userId,
            SavedAddressResponse(
                id = UUID.randomUUID().toString(),
                label = required(request.label, 80, "address label"),
                line1 = required(request.line1, 160, "address"),
                line2 = request.line2?.trim()?.takeIf(String::isNotEmpty),
                city = required(request.city, 100, "city"),
                region = request.region.trim().uppercase().takeIf { it.matches(Regex("^[A-Z]{2}$")) }
                    ?: invalid("INVALID_ADDRESS", "Enter a two-letter state code."),
                zipCode = validateZip(request.zipCode),
            ),
        )
        return profile(principal)
    }

    fun createPhotoUpload(
        principal: AuthenticatedPrincipal,
        request: ProfilePhotoUploadRequest,
    ): ProfilePhotoUploadGrantResponse {
        validatePhoto(request.contentType, request.sizeBytes)
        val now = timeSource.now()
        val id = UUID.randomUUID()
        val extension = CONTENT_TYPES.getValue(request.contentType)
        val objectKey = "customers/${principal.userId}/profile/$id.$extension"
        val grant = objectStorage.createUpload(
            id,
            objectKey,
            request.contentType,
            request.sizeBytes,
            now.plus(UPLOAD_LIFETIME),
        )
        repository.saveUpload(
            ProfileUploadRecord(
                id,
                principal.userId,
                request.contentType,
                request.sizeBytes,
                objectKey,
                now.plus(UPLOAD_LIFETIME),
            ),
        )
        return grant
    }

    fun acceptLocalUpload(uploadId: UUID, contentType: String, bytes: ByteArray) {
        val record = repository.findUpload(uploadId) ?: invalid("INVALID_UPLOAD", "This upload is no longer available.")
        if (timeSource.now().isAfter(record.expiresAt)) invalid("UPLOAD_EXPIRED", "This upload has expired.")
        validatePhoto(contentType, bytes.size.toLong())
        if (record.contentType != contentType ||
            record.sizeBytes != bytes.size.toLong() ||
            !validMagic(contentType, bytes)
        ) {
            invalid("INVALID_IMAGE", "Choose a JPEG, PNG, or WebP image under 5 MB.")
        }
        objectStorage.acceptLocalUpload(uploadId, record.objectKey, contentType, bytes)
        repository.updateUpload(record.copy(status = "UPLOADED"))
    }

    fun completePhoto(principal: AuthenticatedPrincipal, uploadId: String): CustomerProfileResponse {
        val id = runCatching {
            UUID.fromString(uploadId)
        }.getOrElse { invalid("INVALID_UPLOAD", "Upload the photo again.") }
        val upload = repository.findUpload(id)
            ?.takeIf { it.userId == principal.userId && it.status == "UPLOADED" }
            ?: invalid("INVALID_UPLOAD", "Upload the photo again.")
        val url = objectStorage.publicUrl(id, upload.objectKey)
        updateProfile(principal) { it.copy(photoUrl = url) }
        repository.updateUpload(upload.copy(status = "COMPLETED"))
        return profile(principal)
    }

    fun removePhoto(principal: AuthenticatedPrincipal) = updateProfile(principal) { it.copy(photoUrl = null) }

    fun security(principal: AuthenticatedPrincipal): SecurityOverviewResponse {
        val devices = authService.trustedDevices(principal)
        val sessions = authService.sessions(principal)
        return SecurityOverviewResponse(
            trustedDevices = devices.map {
                TrustedDeviceResponse(
                    it.deviceId,
                    it.displayName,
                    it.lastSeenAt.toString(),
                    it.deviceId == sessions.find { s -> s.id == principal.sessionId }?.deviceId,
                )
            },
            recentSignIns = sessions.map {
                RecentSignInResponse(it.id.toString(), it.deviceId, it.createdAt.toString(), it.revokedAt == null)
            },
        )
    }

    fun requestExport(principal: AuthenticatedPrincipal): DataExportRequestResponse {
        val now = timeSource.now()
        return DataExportRequestResponse(
            repository.createExportRequest(principal.userId, now).toString(),
            "REQUESTED",
            now.toString(),
        )
    }

    fun requestDeletion(principal: AuthenticatedPrincipal, confirmation: String): AccountDeletionResponse {
        if (confirmation != "DELETE") invalid("INVALID_CONFIRMATION", "Type DELETE to confirm.")
        authService.requireFreshAuthentication(principal)
        if (bookingGuard.hasActiveBookings(principal.userId)) {
            throw AuthenticationException(
                "ACTIVE_BOOKINGS",
                "Resolve active bookings before deleting your account.",
                409,
            )
        }
        val now = timeSource.now()
        val id = repository.createDeletionRequest(principal.userId, now)
        authService.markDeletionRequested(principal)
        return AccountDeletionResponse(id.toString(), "DELETION_REQUESTED", now.toString())
    }

    private fun updateProfile(
        principal: AuthenticatedPrincipal,
        update: (CustomerProfileRecord) -> CustomerProfileRecord,
    ): CustomerProfileResponse {
        repository.saveProfile(update(requireProfile(principal)))
        return profile(principal)
    }

    private fun requireProfile(principal: AuthenticatedPrincipal) = repository.findProfile(principal.userId)
        ?: invalid("ONBOARDING_REQUIRED", "Finish the short setup first.")

    private fun validateName(value: String, label: String): String {
        val result = required(value, 80, label)
        if (!result.all(::isNameCharacter)) {
            invalid("INVALID_NAME", "Enter your $label using letters.")
        }
        return result
    }

    private fun isNameCharacter(character: Char) = character.isLetter() || character in NAME_PUNCTUATION

    private fun validateZip(value: String): String = value.trim().takeIf { it.matches(Regex("^[0-9]{5}$")) }
        ?: invalid("INVALID_ZIP", "Enter a five-digit ZIP code.")

    private fun validatePreferences(values: List<String>): Set<String> {
        val normalized = values.map { it.trim().uppercase().replace(' ', '_') }.toSet()
        if (!ALLOWED_PREFERENCES.containsAll(
                normalized,
            )
        ) {
            invalid("INVALID_PREFERENCE", "Choose a listed technology interest.")
        }
        return normalized
    }

    private fun validatePermission(value: String): String = value.uppercase().takeIf { it in PERMISSION_VALUES }
        ?: invalid("INVALID_PERMISSION", "Choose a valid permission setting.")

    private fun validatePhoto(contentType: String, size: Long) {
        if (contentType !in CONTENT_TYPES || size !in 1..MAX_PHOTO_BYTES) {
            invalid("INVALID_IMAGE", "Choose a JPEG, PNG, or WebP image under 5 MB.")
        }
    }

    private fun validMagic(type: String, bytes: ByteArray): Boolean = when (type) {
        "image/jpeg" ->
            bytes.size >= 3 &&
                bytes[0] == 0xFF.toByte() &&
                bytes[1] == 0xD8.toByte() &&
                bytes[2] == 0xFF.toByte()
        "image/png" -> bytes.size >= 8 && bytes.sliceArray(0..7).contentEquals(PNG_SIGNATURE)
        "image/webp" ->
            bytes.size >= 12 &&
                bytes.decodeToString(0, 4) == "RIFF" &&
                bytes.decodeToString(8, 12) == "WEBP"
        else -> false
    }

    private fun required(value: String, max: Int, label: String) =
        value.trim().takeIf { it.isNotEmpty() && it.length <= max }
            ?: invalid("INVALID_VALUE", "Enter a valid $label.")

    private fun invalid(code: String, message: String): Nothing = throw AuthenticationException(code, message, 400)

    companion object {
        const val MAX_PHOTO_BYTES = 5L * 1024 * 1024
        private val UPLOAD_LIFETIME = Duration.ofMinutes(10)
        private val CONTENT_TYPES = mapOf("image/jpeg" to "jpg", "image/png" to "png", "image/webp" to "webp")
        private val PERMISSION_VALUES = setOf("NOT_REQUESTED", "GRANTED", "DENIED", "RESTRICTED")
        private val ALLOWED_PREFERENCES = setOf(
            "SMARTPHONES",
            "TABLETS",
            "COMPUTERS",
            "EMAIL",
            "VIDEO_CALLS",
            "SMART_HOME",
            "ONLINE_SAFETY",
            "WI_FI",
            "OTHER",
        )
        private val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        private val NAME_PUNCTUATION = setOf(' ', '-', '\'')
    }
}

private fun CustomerProfileRecord.toSettings() = CustomerSettingsResponse(
    notificationsEnabled = notificationsEnabled,
    permissionStatus = mapOf("location" to locationPermission, "notifications" to notificationPermission),
    followSystemTextSize = followSystemTextSize,
    extraLargeText = extraLargeText,
    highContrast = highContrast,
    reducedMotion = reducedMotion,
    simplifiedInstructions = simplifiedInstructions,
    biometricUnlockEnabled = biometricUnlockEnabled,
)
