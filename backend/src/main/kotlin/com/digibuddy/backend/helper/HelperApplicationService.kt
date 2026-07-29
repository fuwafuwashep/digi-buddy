@file:Suppress("ComplexCondition", "CyclomaticComplexMethod", "LongMethod", "TooManyFunctions")

package com.digibuddy.backend.helper

import com.digibuddy.backend.auth.AuthService
import com.digibuddy.backend.auth.AuthenticatedPrincipal
import com.digibuddy.backend.auth.AuthenticationException
import com.digibuddy.backend.auth.SystemTimeSource
import com.digibuddy.backend.auth.TimeSource
import com.digibuddy.backend.customer.LocalDevelopmentProfileObjectStorage
import com.digibuddy.backend.customer.ProfileObjectStorage
import com.digibuddy.shared.contracts.HelperAccountStatus
import com.digibuddy.shared.contracts.HelperApplicationRequirementResponse
import com.digibuddy.shared.contracts.HelperApplicationResponse
import com.digibuddy.shared.contracts.HelperApplicationStepRequest
import com.digibuddy.shared.contracts.HelperApplicationStepResponse
import com.digibuddy.shared.contracts.HelperFieldVisibility
import com.digibuddy.shared.contracts.HelperOnboardingStep
import com.digibuddy.shared.contracts.HelperRequiredChangeResponse
import com.digibuddy.shared.contracts.HelperRequirementState
import com.digibuddy.shared.contracts.ProfilePhotoUploadGrantResponse
import com.digibuddy.shared.contracts.ProfilePhotoUploadRequest
import com.digibuddy.shared.contracts.UpdateHelperProfileRequest
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

private data class HelperProfileUpload(
    val id: UUID,
    val userId: UUID,
    val contentType: String,
    val sizeBytes: Long,
    val objectKey: String,
    val expiresAt: Instant,
    var uploaded: Boolean = false,
)

class HelperApplicationService(
    private val repository: HelperApplicationRepository,
    private val authentication: AuthService,
    private val timeSource: TimeSource = SystemTimeSource(),
    private val onApproved: (UUID, HelperCatalogApplicationSnapshot) -> Unit = { _, _ -> },
    private val onStatusChanged: (UUID, HelperAccountStatus) -> Unit = { _, _ -> },
    private val objectStorage: ProfileObjectStorage = LocalDevelopmentProfileObjectStorage(),
) {
    private val profileUploads = ConcurrentHashMap<UUID, HelperProfileUpload>()
    fun application(principal: AuthenticatedPrincipal): HelperApplicationResponse =
        response(findOrCreate(principal.userId))

    fun saveStep(
        principal: AuthenticatedPrincipal,
        step: HelperOnboardingStep,
        request: HelperApplicationStepRequest,
    ): HelperApplicationResponse {
        val current = findOrCreate(principal.userId)
        if (current.status !in EDITABLE_STATUSES && current.status !in ACTIVE_WORK_STATUSES) {
            denied("APPLICATION_NOT_EDITABLE", "This helper application cannot be edited right now.", 409)
        }
        val normalized = validateAndNormalize(step, request)
        val now = timeSource.now()
        val updatedSteps = current.steps + (step to HelperStepRecord(step, normalized, true, now))
        val next = nextIncomplete(updatedSteps)
        val updated = current.copy(
            status = if (current.status in ACTIVE_WORK_STATUSES) {
                current.status
            } else if (current.status == HelperAccountStatus.CHANGES_REQUESTED) {
                HelperAccountStatus.CHANGES_REQUESTED
            } else {
                draftStatus(updatedSteps)
            },
            currentStep = next,
            steps = updatedSteps,
            updatedAt = now,
            version = current.version + 1,
        )
        val saved = repository.saveStep(updated, updated.steps.getValue(step))
        if (saved.status in ACTIVE_WORK_STATUSES) onApproved(principal.userId, catalogSnapshot(principal.userId))
        return response(saved)
    }

    fun updateProfile(
        principal: AuthenticatedPrincipal,
        request: UpdateHelperProfileRequest,
    ): HelperApplicationResponse {
        val current = repository.findByUser(principal.userId)
            ?: denied("HELPER_APPLICATION_NOT_FOUND", "Helper profile not found.", 404)
        if (current.status !in ACTIVE_WORK_STATUSES) {
            denied("HELPER_PROFILE_NOT_EDITABLE", "Your helper profile cannot be edited right now.", 409)
        }
        val updates = linkedMapOf(
            HelperOnboardingStep.LEGAL_NAME to HelperApplicationStepRequest(
                values = mapOf("legalFirstName" to request.legalFirstName, "legalLastName" to request.legalLastName),
            ),
            HelperOnboardingStep.PUBLIC_PROFILE to HelperApplicationStepRequest(
                values = mapOf(
                    "displayName" to request.displayName,
                    "headline" to request.headline,
                    "biography" to request.biography,
                ),
            ),
            HelperOnboardingStep.HOME_AND_SERVICE_MODE to HelperApplicationStepRequest(
                values = mapOf("homeZip" to request.homeZip, "serviceMode" to request.serviceMode),
            ),
            HelperOnboardingStep.SERVICE_AREA to HelperApplicationStepRequest(
                values = mapOf("serviceAreaSummary" to request.serviceAreaSummary),
            ),
            HelperOnboardingStep.SKILLS to HelperApplicationStepRequest(
                listValues = mapOf("skillIds" to request.skillIds),
            ),
            HelperOnboardingStep.SERVICES to HelperApplicationStepRequest(
                listValues = mapOf("serviceCategoryIds" to request.serviceCategoryIds),
            ),
            HelperOnboardingStep.EXPERIENCE to HelperApplicationStepRequest(
                values = mapOf("yearsExperience" to request.yearsExperience.toString()),
            ),
            HelperOnboardingStep.LANGUAGES to HelperApplicationStepRequest(
                listValues = mapOf("languages" to request.languages),
            ),
            HelperOnboardingStep.AVAILABILITY to HelperApplicationStepRequest(
                values = mapOf("availabilitySummary" to request.availabilitySummary),
            ),
            HelperOnboardingStep.CERTIFICATIONS to HelperApplicationStepRequest(
                listValues = mapOf("certifications" to request.certifications),
            ),
            HelperOnboardingStep.PORTFOLIO to HelperApplicationStepRequest(
                listValues = mapOf("portfolio" to request.portfolioLinks),
            ),
        ).mapValues { (step, payload) -> validateAndNormalize(step, payload) }
        val now = timeSource.now()
        var working = current
        updates.forEach { (step, payload) ->
            val stepRecord = HelperStepRecord(step, payload, true, now)
            working = working.copy(
                steps = working.steps + (step to stepRecord),
                updatedAt = now,
                version = working.version + 1,
            )
            working = repository.saveStep(working, stepRecord)
        }
        onApproved(principal.userId, catalogSnapshot(principal.userId))
        return response(working)
    }

    fun createProfilePhotoUpload(
        principal: AuthenticatedPrincipal,
        request: ProfilePhotoUploadRequest,
    ): ProfilePhotoUploadGrantResponse {
        requireProfileEditable(principal)
        validatePhoto(request.contentType, request.sizeBytes)
        val id = UUID.randomUUID()
        val extension = PHOTO_CONTENT_TYPES.getValue(request.contentType)
        val expiresAt = timeSource.now().plus(Duration.ofMinutes(10))
        val objectKey = "helpers/${principal.userId}/profile/$id.$extension"
        profileUploads[id] = HelperProfileUpload(
            id,
            principal.userId,
            request.contentType,
            request.sizeBytes,
            objectKey,
            expiresAt,
        )
        return objectStorage.createUpload(id, objectKey, request.contentType, request.sizeBytes, expiresAt).copy(
            uploadUrl = "/api/v1/helper/uploads/local/$id",
        )
    }

    fun acceptLocalProfilePhoto(uploadId: UUID, contentType: String, bytes: ByteArray) {
        val upload = profileUploads[uploadId]
            ?: denied("INVALID_UPLOAD", "Choose the photo again.", 400)
        if (timeSource.now().isAfter(upload.expiresAt)) denied("UPLOAD_EXPIRED", "Choose the photo again.", 400)
        validatePhoto(contentType, bytes.size.toLong())
        if (upload.contentType != contentType ||
            upload.sizeBytes != bytes.size.toLong() ||
            !validImage(contentType, bytes)
        ) {
            denied("INVALID_IMAGE", "Choose a JPEG, PNG, or WebP image under 5 MB.", 400)
        }
        objectStorage.acceptLocalUpload(uploadId, upload.objectKey, contentType, bytes)
        upload.uploaded = true
    }

    fun completeProfilePhoto(principal: AuthenticatedPrincipal, uploadId: String): HelperApplicationResponse {
        requireProfileEditable(principal)
        val id = runCatching { UUID.fromString(uploadId) }
            .getOrElse { denied("INVALID_UPLOAD", "Choose the photo again.", 400) }
        val upload = profileUploads[id]?.takeIf { it.userId == principal.userId && it.uploaded }
            ?: denied("INVALID_UPLOAD", "Choose the photo again.", 400)
        val currentMedia = repository.findByUser(principal.userId)?.steps?.get(HelperOnboardingStep.PROFILE_MEDIA)
        val result = saveStep(
            principal,
            HelperOnboardingStep.PROFILE_MEDIA,
            HelperApplicationStepRequest(
                values = currentMedia?.payload?.values.orEmpty() +
                    ("profilePictureUrl" to objectStorage.publicUrl(id, upload.objectKey)),
            ),
        )
        profileUploads.remove(id)
        return result
    }

    fun removeProfilePhoto(principal: AuthenticatedPrincipal): HelperApplicationResponse {
        requireProfileEditable(principal)
        val current = repository.findByUser(principal.userId)?.steps?.get(HelperOnboardingStep.PROFILE_MEDIA)
        return saveStep(
            principal,
            HelperOnboardingStep.PROFILE_MEDIA,
            HelperApplicationStepRequest(values = current?.payload?.values.orEmpty() - "profilePictureUrl"),
        )
    }

    fun submit(principal: AuthenticatedPrincipal): HelperApplicationResponse {
        val current = findOrCreate(principal.userId)
        if (current.status !in EDITABLE_STATUSES) {
            denied("APPLICATION_NOT_SUBMITTABLE", "This helper application cannot be submitted right now.", 409)
        }
        val missing = REQUIRED_HELPER_STEPS - current.steps.filterValues { it.completed }.keys
        if (missing.isNotEmpty()) {
            denied("APPLICATION_INCOMPLETE", "Complete every required step before submitting.", 409)
        }
        val now = timeSource.now()
        val updated = current.copy(
            status = HelperAccountStatus.UNDER_REVIEW,
            updatedAt = now,
            submittedAt = now,
            version = current.version + 1,
        )
        val event = event(current, updated, principal.userId, "Helper submitted application", now)
        repository.replaceRequiredChanges(current.id, emptyList())
        return response(repository.updateStatus(updated, event))
    }

    fun setPaused(principal: AuthenticatedPrincipal, paused: Boolean): HelperApplicationResponse {
        val current = repository.findByUser(principal.userId)
            ?: denied("HELPER_APPLICATION_NOT_FOUND", "Start helper onboarding first.", 404)
        val target = if (paused) HelperAccountStatus.PAUSED_BY_HELPER else HelperAccountStatus.APPROVED
        if ((paused && current.status != HelperAccountStatus.APPROVED) ||
            (!paused && current.status != HelperAccountStatus.PAUSED_BY_HELPER)
        ) {
            denied("INVALID_HELPER_STATUS", "That helper status change is not available.", 409)
        }
        val now = timeSource.now()
        val updated = current.copy(status = target, updatedAt = now, version = current.version + 1)
        val saved = repository.updateStatus(updated, event(current, updated, principal.userId, null, now))
        onStatusChanged(principal.userId, target)
        return response(saved)
    }

    fun review(
        applicationUserId: UUID,
        target: HelperAccountStatus,
        staffUserId: UUID?,
        reason: String? = null,
        requestedChanges: List<Pair<HelperOnboardingStep, String>> = emptyList(),
    ): HelperApplicationResponse {
        val current = repository.findByUser(applicationUserId)
            ?: denied("HELPER_APPLICATION_NOT_FOUND", "Helper application not found.", 404)
        requireAllowedTransition(current.status, target)
        if (target == HelperAccountStatus.CHANGES_REQUESTED && requestedChanges.isEmpty()) {
            denied("CHANGES_REQUIRED", "Describe at least one required change.", 400)
        }
        val now = timeSource.now()
        val updated = current.copy(status = target, updatedAt = now, version = current.version + 1)
        val event = event(current, updated, staffUserId, reason, now)
        val saved = if (target == HelperAccountStatus.APPROVED) {
            repository.approveAndGrantRole(updated, event).also {
                authentication.grantHelperRole(applicationUserId)
                onApproved(applicationUserId, catalogSnapshot(applicationUserId))
            }
        } else {
            repository.updateStatus(updated, event)
        }
        repository.replaceRequiredChanges(
            current.id,
            requestedChanges.map { (step, message) ->
                HelperRequiredChangeRecord(UUID.randomUUID(), current.id, step, message.trim(), now)
            },
        )
        onStatusChanged(applicationUserId, target)
        return response(saved)
    }

    fun requireCanActivateServices(principal: AuthenticatedPrincipal) = requirePaidWorkEligibility(principal)

    fun requireCanReceivePaidRequest(principal: AuthenticatedPrincipal) = requirePaidWorkEligibility(principal)

    fun publicSnapshot(principal: AuthenticatedPrincipal): HelperPublicApplicationSnapshot =
        publicSnapshot(principal.userId)

    private fun publicSnapshot(userId: UUID): HelperPublicApplicationSnapshot {
        val steps = repository.findByUser(userId)?.steps.orEmpty()
        fun value(step: HelperOnboardingStep, key: String) = steps[step]?.payload?.values?.get(key)
        fun list(step: HelperOnboardingStep, key: String) = steps[step]?.payload?.listValues?.get(key).orEmpty()
        return HelperPublicApplicationSnapshot(
            displayName = value(HelperOnboardingStep.PUBLIC_PROFILE, "displayName"),
            profilePictureUrl = value(HelperOnboardingStep.PROFILE_MEDIA, "profilePictureUrl"),
            bannerImageUrl = value(HelperOnboardingStep.PROFILE_MEDIA, "bannerImageUrl"),
            headline = value(HelperOnboardingStep.PUBLIC_PROFILE, "headline"),
            biography = value(HelperOnboardingStep.PUBLIC_PROFILE, "biography"),
            serviceMode = value(HelperOnboardingStep.HOME_AND_SERVICE_MODE, "serviceMode"),
            skills = list(HelperOnboardingStep.SKILLS, "skillIds"),
            services = list(HelperOnboardingStep.SERVICES, "serviceCategoryIds"),
            yearsExperience = value(HelperOnboardingStep.EXPERIENCE, "yearsExperience")?.toIntOrNull(),
            languages = list(HelperOnboardingStep.LANGUAGES, "languages"),
            availabilitySummary = value(HelperOnboardingStep.AVAILABILITY, "availabilitySummary"),
            serviceAreaSummary = value(HelperOnboardingStep.SERVICE_AREA, "serviceAreaSummary"),
            certifications = list(HelperOnboardingStep.CERTIFICATIONS, "certifications"),
            portfolioLinks = list(HelperOnboardingStep.PORTFOLIO, "portfolio"),
        )
    }

    private fun catalogSnapshot(userId: UUID): HelperCatalogApplicationSnapshot {
        val homeZip = repository.findByUser(userId)?.steps
            ?.get(HelperOnboardingStep.HOME_AND_SERVICE_MODE)?.payload?.values?.get("homeZip")
            ?: denied("HELPER_ZIP_REQUIRED", "A valid home ZIP code is required.", 409)
        return HelperCatalogApplicationSnapshot(publicSnapshot(userId), homeZip)
    }

    fun approveForLocalDevelopment(principal: AuthenticatedPrincipal): HelperApplicationResponse = review(
        applicationUserId = principal.userId,
        target = HelperAccountStatus.APPROVED,
        staffUserId = null,
        reason = "Local development approval",
    )

    private fun requirePaidWorkEligibility(principal: AuthenticatedPrincipal) {
        val account = authentication.currentUser(principal)
        val application = repository.findByUser(principal.userId)
        if ("HELPER" !in account.roles || application?.status !in ACTIVE_WORK_STATUSES) {
            denied("HELPER_NOT_APPROVED", "Your helper account must be approved before receiving paid work.", 403)
        }
    }

    private fun requireProfileEditable(principal: AuthenticatedPrincipal) {
        val current = repository.findByUser(principal.userId)
        if (current?.status !in EDITABLE_STATUSES && current?.status !in ACTIVE_WORK_STATUSES) {
            denied("HELPER_PROFILE_NOT_EDITABLE", "Your helper profile cannot be edited right now.", 409)
        }
    }

    private fun validatePhoto(contentType: String, sizeBytes: Long) {
        if (contentType !in PHOTO_CONTENT_TYPES || sizeBytes !in 1..MAX_PROFILE_PHOTO_BYTES) {
            denied("INVALID_IMAGE", "Choose a JPEG, PNG, or WebP image under 5 MB.", 400)
        }
    }

    private fun validImage(contentType: String, bytes: ByteArray): Boolean = when (contentType) {
        "image/jpeg" -> bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()
        "image/png" -> bytes.size >= 8 && bytes.sliceArray(0..7).contentEquals(PNG_SIGNATURE)
        "image/webp" ->
            bytes.size >= 12 &&
                bytes.copyOfRange(0, 4).decodeToString() == "RIFF" &&
                bytes.copyOfRange(8, 12).decodeToString() == "WEBP"
        else -> false
    }

    private fun findOrCreate(userId: UUID): HelperApplicationRecord = repository.findByUser(userId) ?: run {
        val now = timeSource.now()
        repository.create(
            HelperApplicationRecord(
                id = UUID.randomUUID(),
                userId = userId,
                status = HelperAccountStatus.PROFILE_INCOMPLETE,
                currentStep = HelperOnboardingStep.LEGAL_NAME,
                steps = emptyMap(),
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    private fun response(record: HelperApplicationRecord): HelperApplicationResponse {
        val completed = record.steps.filterValues { it.completed }.keys
        val requiredCompleted = completed.intersect(REQUIRED_HELPER_STEPS).size
        val changes = repository.requiredChanges(record.id)
        return HelperApplicationResponse(
            applicationId = record.id.toString(),
            status = record.status,
            currentStep = record.currentStep,
            completedSteps = completed.sortedBy(HelperOnboardingStep::ordinal),
            progressPercent = requiredCompleted * 100 / REQUIRED_HELPER_STEPS.size,
            steps = record.steps.values.sortedBy { it.step.ordinal }.map {
                HelperApplicationStepResponse(
                    it.step,
                    it.payload.values,
                    it.payload.listValues,
                    it.payload.booleanValues,
                    it.completed,
                    it.savedAt.toString(),
                )
            },
            requirements = requirementResponses(completed, changes),
            requestedChanges = changes.map {
                HelperRequiredChangeResponse(it.id.toString(), it.step, it.message)
            },
            canSubmit = record.status in EDITABLE_STATUSES && REQUIRED_HELPER_STEPS.all(completed::contains),
            canReceivePaidWork = record.status == HelperAccountStatus.APPROVED,
            message = record.status.message(),
        )
    }

    private fun requirementResponses(
        completed: Set<HelperOnboardingStep>,
        changes: List<HelperRequiredChangeRecord>,
    ): List<HelperApplicationRequirementResponse> = FIELD_REQUIREMENTS.map { requirement ->
        val state = when {
            changes.any { it.step == requirement.step } -> HelperRequirementState.NEEDS_ATTENTION
            requirement.step in completed -> HelperRequirementState.COMPLETE
            else -> HelperRequirementState.NOT_STARTED
        }
        HelperApplicationRequirementResponse(
            requirement.code,
            requirement.label,
            requirement.visibility,
            requirement.required,
            state,
        )
    }

    private fun validateAndNormalize(
        step: HelperOnboardingStep,
        request: HelperApplicationStepRequest,
    ): HelperApplicationStepRequest {
        val values = request.values.mapValues { it.value.trim() }
        val lists = request.listValues.mapValues { (_, items) ->
            items.map(String::trim).filter(String::isNotBlank).distinct()
        }
        fun required(key: String, min: Int = 1, max: Int = 500): String = values[key]
            ?.takeIf { it.length in min..max }
            ?: denied("INVALID_HELPER_FIELD", "Complete the required information before continuing.", 400)
        fun requiredList(key: String): List<String> = lists[key]
            ?.takeIf(List<String>::isNotEmpty)
            ?: denied("INVALID_HELPER_FIELD", "Choose at least one option before continuing.", 400)
        when (step) {
            HelperOnboardingStep.LEGAL_NAME -> {
                required("legalFirstName", max = 80)
                required("legalLastName", max = 80)
            }
            HelperOnboardingStep.PUBLIC_PROFILE -> {
                required("displayName", 2, 100)
                required("headline", 10, 160)
                required("biography", 40, 1200)
            }
            HelperOnboardingStep.PROFILE_MEDIA -> Unit
            HelperOnboardingStep.HOME_AND_SERVICE_MODE -> {
                if (!required("homeZip", 5, 5).matches(Regex("^[0-9]{5}$"))) invalidField()
                if (required("serviceMode") !in setOf("IN_PERSON", "REMOTE", "BOTH")) invalidField()
            }
            HelperOnboardingStep.SERVICE_AREA -> required("serviceAreaSummary", 2, 240)
            HelperOnboardingStep.SKILLS -> requiredList("skillIds")
            HelperOnboardingStep.SERVICES -> requiredList("serviceCategoryIds")
            HelperOnboardingStep.EXPERIENCE ->
                if (required("yearsExperience").toIntOrNull() !in 0..80) invalidField()
            HelperOnboardingStep.LANGUAGES -> requiredList("languages")
            HelperOnboardingStep.PRICING -> {
                val legacyClientAcknowledgment = values["pricingSummary"].orEmpty().isNotBlank() &&
                    values["startingPriceCents"]?.toIntOrNull()?.let { it in 1..1_000_000 } == true
                if (request.booleanValues["platformPricingAcknowledged"] != true && !legacyClientAcknowledgment) {
                    invalidField()
                }
            }
            HelperOnboardingStep.AVAILABILITY -> required("availabilitySummary", 2, 240)
            HelperOnboardingStep.CERTIFICATIONS, HelperOnboardingStep.PORTFOLIO -> Unit
            HelperOnboardingStep.TERMS_AND_POLICIES ->
                if (request.booleanValues["accepted"] != true) invalidField()
            HelperOnboardingStep.PAYOUT_ONBOARDING ->
                if (request.booleanValues["placeholderAcknowledged"] != true) invalidField()
        }
        return if (step == HelperOnboardingStep.PRICING) {
            HelperApplicationStepRequest(booleanValues = mapOf("platformPricingAcknowledged" to true))
        } else {
            request.copy(values = values, listValues = lists)
        }
    }

    private fun invalidField(): Nothing = denied(
        "INVALID_HELPER_FIELD",
        "Check this information and try again.",
        400,
    )

    private fun nextIncomplete(steps: Map<HelperOnboardingStep, HelperStepRecord>): HelperOnboardingStep =
        ONBOARDING_ORDER.firstOrNull { it !in steps || steps[it]?.completed != true }
            ?: HelperOnboardingStep.PAYOUT_ONBOARDING

    private fun draftStatus(steps: Map<HelperOnboardingStep, HelperStepRecord>): HelperAccountStatus {
        val completed = steps.filterValues { it.completed }.keys
        return when {
            HelperOnboardingStep.LEGAL_NAME !in completed && completed.size >= REQUIRED_HELPER_STEPS.size - 1 ->
                HelperAccountStatus.IDENTITY_INFORMATION_REQUIRED
            HelperOnboardingStep.PAYOUT_ONBOARDING !in completed &&
                (REQUIRED_HELPER_STEPS - HelperOnboardingStep.PAYOUT_ONBOARDING).all(completed::contains) ->
                HelperAccountStatus.PAYMENT_ONBOARDING_REQUIRED
            else -> HelperAccountStatus.PROFILE_INCOMPLETE
        }
    }

    private fun requireAllowedTransition(from: HelperAccountStatus, to: HelperAccountStatus) {
        val allowed = REVIEW_TRANSITIONS[from].orEmpty()
        if (to !in allowed) denied("INVALID_HELPER_STATUS", "That helper status change is not allowed.", 409)
    }

    private fun event(
        before: HelperApplicationRecord,
        after: HelperApplicationRecord,
        actor: UUID?,
        reason: String?,
        now: Instant,
    ) = HelperApprovalEventRecord(UUID.randomUUID(), before.id, before.status, after.status, actor, reason, now)

    private fun HelperAccountStatus.message(): String = when (this) {
        HelperAccountStatus.PROFILE_INCOMPLETE -> "Complete your helper profile one simple step at a time."
        HelperAccountStatus.IDENTITY_INFORMATION_REQUIRED -> "Your private identity information is still needed."
        HelperAccountStatus.PAYMENT_ONBOARDING_REQUIRED -> "Complete the payout setup placeholder before submitting."
        HelperAccountStatus.UNDER_REVIEW ->
            "Your application is under review. You can still sign in and check its status."
        HelperAccountStatus.CHANGES_REQUESTED -> "Please update the requested items and submit again."
        HelperAccountStatus.APPROVED -> "Your helper account is approved and ready."
        HelperAccountStatus.PAUSED_BY_HELPER -> "You paused new work. Your profile remains saved."
        HelperAccountStatus.SUSPENDED -> "Your helper account is suspended. Contact Digibuddy support."
        HelperAccountStatus.REJECTED -> "We could not approve this application. Contact support if you have questions."
    }

    private fun denied(code: String, message: String, status: Int): Nothing =
        throw AuthenticationException(code, message, status)

    private data class Requirement(
        val code: String,
        val label: String,
        val step: HelperOnboardingStep,
        val visibility: HelperFieldVisibility,
        val required: Boolean,
    )

    private companion object {
        val EDITABLE_STATUSES = setOf(
            HelperAccountStatus.PROFILE_INCOMPLETE,
            HelperAccountStatus.IDENTITY_INFORMATION_REQUIRED,
            HelperAccountStatus.PAYMENT_ONBOARDING_REQUIRED,
            HelperAccountStatus.CHANGES_REQUESTED,
        )
        val ACTIVE_WORK_STATUSES = setOf(HelperAccountStatus.APPROVED, HelperAccountStatus.PAUSED_BY_HELPER)
        const val MAX_PROFILE_PHOTO_BYTES = 5L * 1024 * 1024
        val PHOTO_CONTENT_TYPES = mapOf("image/jpeg" to "jpg", "image/png" to "png", "image/webp" to "webp")
        val PNG_SIGNATURE = byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10)
        val ONBOARDING_ORDER = HelperOnboardingStep.entries
        val REVIEW_TRANSITIONS = mapOf(
            HelperAccountStatus.UNDER_REVIEW to setOf(
                HelperAccountStatus.CHANGES_REQUESTED,
                HelperAccountStatus.APPROVED,
                HelperAccountStatus.SUSPENDED,
                HelperAccountStatus.REJECTED,
            ),
            HelperAccountStatus.CHANGES_REQUESTED to
                setOf(HelperAccountStatus.UNDER_REVIEW, HelperAccountStatus.REJECTED),
            HelperAccountStatus.APPROVED to setOf(HelperAccountStatus.PAUSED_BY_HELPER, HelperAccountStatus.SUSPENDED),
            HelperAccountStatus.PAUSED_BY_HELPER to setOf(HelperAccountStatus.APPROVED, HelperAccountStatus.SUSPENDED),
            HelperAccountStatus.SUSPENDED to setOf(HelperAccountStatus.APPROVED, HelperAccountStatus.REJECTED),
        )
        val FIELD_REQUIREMENTS = listOf(
            Requirement(
                "legal-name",
                "Legal name",
                HelperOnboardingStep.LEGAL_NAME,
                HelperFieldVisibility.PRIVATE,
                true,
            ),
            Requirement(
                "public-profile",
                "Display name, headline, and biography",
                HelperOnboardingStep.PUBLIC_PROFILE,
                HelperFieldVisibility.PUBLIC,
                true,
            ),
            Requirement(
                "profile-media",
                "Profile picture and banner",
                HelperOnboardingStep.PROFILE_MEDIA,
                HelperFieldVisibility.PUBLIC,
                false,
            ),
            Requirement(
                "home-zip",
                "Home ZIP code and service type",
                HelperOnboardingStep.HOME_AND_SERVICE_MODE,
                HelperFieldVisibility.PRIVATE,
                true,
            ),
            Requirement(
                "service-area",
                "Service area summary",
                HelperOnboardingStep.SERVICE_AREA,
                HelperFieldVisibility.PUBLIC,
                true,
            ),
            Requirement("skills", "Skills", HelperOnboardingStep.SKILLS, HelperFieldVisibility.PUBLIC, true),
            Requirement("services", "Services", HelperOnboardingStep.SERVICES, HelperFieldVisibility.PUBLIC, true),
            Requirement(
                "experience",
                "Years of experience",
                HelperOnboardingStep.EXPERIENCE,
                HelperFieldVisibility.PUBLIC,
                true,
            ),
            Requirement("languages", "Languages", HelperOnboardingStep.LANGUAGES, HelperFieldVisibility.PUBLIC, true),
            Requirement(
                "pricing",
                "Digibuddy pricing policy",
                HelperOnboardingStep.PRICING,
                HelperFieldVisibility.PRIVATE,
                true,
            ),
            Requirement(
                "availability",
                "Availability summary",
                HelperOnboardingStep.AVAILABILITY,
                HelperFieldVisibility.PUBLIC,
                true,
            ),
            Requirement(
                "certifications",
                "Certifications",
                HelperOnboardingStep.CERTIFICATIONS,
                HelperFieldVisibility.PUBLIC,
                false,
            ),
            Requirement("portfolio", "Portfolio", HelperOnboardingStep.PORTFOLIO, HelperFieldVisibility.PUBLIC, false),
            Requirement(
                "terms",
                "Terms and policies",
                HelperOnboardingStep.TERMS_AND_POLICIES,
                HelperFieldVisibility.PRIVATE,
                true,
            ),
            Requirement(
                "payout",
                "Payout onboarding placeholder",
                HelperOnboardingStep.PAYOUT_ONBOARDING,
                HelperFieldVisibility.PRIVATE,
                true,
            ),
        )
    }
}
