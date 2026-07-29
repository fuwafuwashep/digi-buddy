package com.digibuddy.shared.contracts

import kotlinx.serialization.Serializable

@Serializable
enum class HelperOnboardingStatus {
    ONBOARDING,
    UNDER_REVIEW,
    CHANGES_REQUESTED,
    APPROVED,
    SUSPENDED,
}

@Serializable
enum class HelperAccountStatus {
    PROFILE_INCOMPLETE,
    IDENTITY_INFORMATION_REQUIRED,
    PAYMENT_ONBOARDING_REQUIRED,
    UNDER_REVIEW,
    CHANGES_REQUESTED,
    APPROVED,
    PAUSED_BY_HELPER,
    SUSPENDED,
    REJECTED,
}

@Serializable
enum class HelperOnboardingStep {
    LEGAL_NAME,
    PUBLIC_PROFILE,
    PROFILE_MEDIA,
    HOME_AND_SERVICE_MODE,
    SERVICE_AREA,
    SKILLS,
    SERVICES,
    EXPERIENCE,
    LANGUAGES,
    PRICING,
    AVAILABILITY,
    CERTIFICATIONS,
    PORTFOLIO,
    TERMS_AND_POLICIES,
    PAYOUT_ONBOARDING,
}

@Serializable
enum class HelperFieldVisibility { PUBLIC, PRIVATE }

@Serializable
enum class HelperRequirementState { NOT_STARTED, COMPLETE, NEEDS_ATTENTION }

@Serializable
data class HelperApplicationStepRequest(
    val values: Map<String, String> = emptyMap(),
    val listValues: Map<String, List<String>> = emptyMap(),
    val booleanValues: Map<String, Boolean> = emptyMap(),
)

@Serializable
data class HelperApplicationStepResponse(
    val step: HelperOnboardingStep,
    val values: Map<String, String> = emptyMap(),
    val listValues: Map<String, List<String>> = emptyMap(),
    val booleanValues: Map<String, Boolean> = emptyMap(),
    val completed: Boolean,
    val savedAt: String,
)

@Serializable
data class HelperApplicationRequirementResponse(
    val code: String,
    val label: String,
    val visibility: HelperFieldVisibility,
    val required: Boolean,
    val state: HelperRequirementState,
)

@Serializable
data class HelperRequiredChangeResponse(val id: String, val step: HelperOnboardingStep, val message: String)

@Serializable
data class HelperApplicationResponse(
    val applicationId: String,
    val status: HelperAccountStatus,
    val currentStep: HelperOnboardingStep,
    val completedSteps: List<HelperOnboardingStep>,
    val progressPercent: Int,
    val steps: List<HelperApplicationStepResponse>,
    val requirements: List<HelperApplicationRequirementResponse>,
    val requestedChanges: List<HelperRequiredChangeResponse> = emptyList(),
    val canSubmit: Boolean,
    val canReceivePaidWork: Boolean,
    val message: String,
)

@Serializable
data class HelperStartupResponse(
    val userId: String,
    val hasHelperRole: Boolean,
    val onboardingStatus: HelperOnboardingStatus,
    val displayName: String? = null,
    val message: String,
    val requestedChanges: List<String> = emptyList(),
    val helperStatus: HelperAccountStatus? = null,
    val progressPercent: Int = 0,
    val canReceivePaidWork: Boolean = false,
)

@Serializable
data class UpdateHelperProfileRequest(
    val legalFirstName: String,
    val legalLastName: String,
    val displayName: String,
    val headline: String,
    val biography: String,
    val homeZip: String,
    val serviceMode: String,
    val serviceAreaSummary: String,
    val skillIds: List<String>,
    val serviceCategoryIds: List<String>,
    val yearsExperience: Int,
    val languages: List<String>,
    val availabilitySummary: String,
    val certifications: List<String> = emptyList(),
    val portfolioLinks: List<String> = emptyList(),
)
