package com.digibuddy.backend.customer

import com.digibuddy.shared.contracts.ProfilePhotoUploadGrantResponse
import java.time.Instant
import java.util.UUID

interface ProfileObjectStorage {
    fun createUpload(
        uploadId: UUID,
        objectKey: String,
        contentType: String,
        sizeBytes: Long,
        expiresAt: Instant,
    ): ProfilePhotoUploadGrantResponse

    fun acceptLocalUpload(uploadId: UUID, objectKey: String, contentType: String, bytes: ByteArray)
    fun publicUrl(uploadId: UUID, objectKey: String): String
}

class LocalDevelopmentProfileObjectStorage : ProfileObjectStorage {
    private val objects = mutableMapOf<UUID, ByteArray>()

    override fun createUpload(
        uploadId: UUID,
        objectKey: String,
        contentType: String,
        sizeBytes: Long,
        expiresAt: Instant,
    ) = ProfilePhotoUploadGrantResponse(
        uploadId = uploadId.toString(),
        uploadUrl = "/api/v1/uploads/local/$uploadId",
        headers = mapOf("Content-Type" to contentType),
        maxSizeBytes = CustomerProfileService.MAX_PHOTO_BYTES,
        expiresInSeconds = 600,
    )

    override fun acceptLocalUpload(uploadId: UUID, objectKey: String, contentType: String, bytes: ByteArray) {
        objects[uploadId] = bytes.copyOf()
    }

    override fun publicUrl(uploadId: UUID, objectKey: String) = "/api/v1/uploads/local/$uploadId/content"

    fun bytes(uploadId: UUID) = objects[uploadId]?.copyOf()
}

interface PresignedUploadSigner {
    fun signPut(objectKey: String, contentType: String, sizeBytes: Long, expiresAt: Instant): PresignedPut
}

data class PresignedPut(val url: String, val headers: Map<String, String>, val publicUrl: String)

class PresignedProfileObjectStorage(private val signer: PresignedUploadSigner) : ProfileObjectStorage {
    private val publicUrls = mutableMapOf<UUID, String>()

    override fun createUpload(
        uploadId: UUID,
        objectKey: String,
        contentType: String,
        sizeBytes: Long,
        expiresAt: Instant,
    ): ProfilePhotoUploadGrantResponse {
        val signed = signer.signPut(objectKey, contentType, sizeBytes, expiresAt)
        publicUrls[uploadId] = signed.publicUrl
        return ProfilePhotoUploadGrantResponse(
            uploadId.toString(),
            signed.url,
            headers = signed.headers,
            maxSizeBytes = CustomerProfileService.MAX_PHOTO_BYTES,
            expiresInSeconds = 600,
        )
    }

    override fun acceptLocalUpload(uploadId: UUID, objectKey: String, contentType: String, bytes: ByteArray) {
        error("Hosted uploads go directly to object storage using the presigned URL.")
    }

    override fun publicUrl(uploadId: UUID, objectKey: String) = publicUrls[uploadId]
        ?: error("Unknown completed upload")
}
