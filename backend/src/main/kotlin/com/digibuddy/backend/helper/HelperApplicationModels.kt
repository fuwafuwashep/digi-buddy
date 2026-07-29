package com.digibuddy.backend.helper

import com.digibuddy.shared.contracts.HelperAccountStatus
import com.digibuddy.shared.contracts.HelperApplicationStepRequest
import com.digibuddy.shared.contracts.HelperOnboardingStep
import java.time.Instant
import java.util.UUID

data class HelperApplicationRecord(
    val id: UUID,
    val userId: UUID,
    val status: HelperAccountStatus,
    val currentStep: HelperOnboardingStep,
    val steps: Map<HelperOnboardingStep, HelperStepRecord>,
    val createdAt: Instant,
    val updatedAt: Instant,
    val submittedAt: Instant? = null,
    val version: Int = 1,
)

data class HelperStepRecord(
    val step: HelperOnboardingStep,
    val payload: HelperApplicationStepRequest,
    val completed: Boolean,
    val savedAt: Instant,
)

data class HelperRequiredChangeRecord(
    val id: UUID,
    val applicationId: UUID,
    val step: HelperOnboardingStep,
    val message: String,
    val createdAt: Instant,
    val resolvedAt: Instant? = null,
)

data class HelperApprovalEventRecord(
    val id: UUID,
    val applicationId: UUID,
    val fromStatus: HelperAccountStatus,
    val toStatus: HelperAccountStatus,
    val actorUserId: UUID?,
    val reason: String?,
    val occurredAt: Instant,
)

data class HelperPublicApplicationSnapshot(
    val displayName: String?,
    val profilePictureUrl: String?,
    val bannerImageUrl: String?,
    val headline: String?,
    val biography: String?,
    val serviceMode: String?,
    val skills: List<String>,
    val services: List<String>,
    val yearsExperience: Int?,
    val languages: List<String>,
    val availabilitySummary: String?,
    val serviceAreaSummary: String? = null,
    val certifications: List<String> = emptyList(),
    val portfolioLinks: List<String> = emptyList(),
)

data class HelperCatalogApplicationSnapshot(val publicProfile: HelperPublicApplicationSnapshot, val homeZip: String)
