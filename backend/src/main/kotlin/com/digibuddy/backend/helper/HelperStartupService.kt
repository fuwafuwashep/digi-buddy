package com.digibuddy.backend.helper

import com.digibuddy.backend.auth.AuthService
import com.digibuddy.backend.auth.AuthenticatedPrincipal
import com.digibuddy.backend.catalog.HelperCatalogService
import com.digibuddy.backend.catalog.HelperLifecycleSnapshot
import com.digibuddy.shared.contracts.HelperAccountStatus
import com.digibuddy.shared.contracts.HelperOnboardingStatus
import com.digibuddy.shared.contracts.HelperStartupResponse

class HelperStartupService(
    private val authentication: AuthService,
    private val catalog: HelperCatalogService,
    private val applications: HelperApplicationService? = null,
) {
    fun startup(principal: AuthenticatedPrincipal): HelperStartupResponse {
        val account = authentication.currentUser(principal)
        val identity = authentication.accountProfileIdentity(principal)
        val hasHelperRole = account.roles.any { it.equals(HELPER_ROLE, ignoreCase = true) }
        val lifecycle = catalog.lifecycle(principal.userId)
        val application = applications?.application(principal)
        val helperStatus = when {
            identity.accountStatus == SUSPENDED || lifecycle?.accountStatus == SUSPENDED ->
                HelperAccountStatus.SUSPENDED
            application != null -> application.status
            else -> resolveHelperStatus(hasHelperRole, identity.accountStatus, lifecycle).accountStatus()
        }
        val status = helperStatus.compatibilityStatus()
        return HelperStartupResponse(
            userId = account.userId,
            hasHelperRole = hasHelperRole,
            onboardingStatus = status,
            displayName = lifecycle?.displayName,
            message = application?.message ?: status.message(),
            requestedChanges = application?.requestedChanges?.map { it.message }.orEmpty(),
            helperStatus = helperStatus,
            progressPercent = application?.progressPercent ?: 0,
            canReceivePaidWork = hasHelperRole && helperStatus == HelperAccountStatus.APPROVED,
        )
    }

    private fun HelperOnboardingStatus.message(): String = when (this) {
        HelperOnboardingStatus.ONBOARDING -> "Tell us about your experience to begin your helper application."
        HelperOnboardingStatus.UNDER_REVIEW -> "Your helper application is being reviewed."
        HelperOnboardingStatus.CHANGES_REQUESTED -> "A few application details need your attention."
        HelperOnboardingStatus.APPROVED -> "Your helper workspace is ready."
        HelperOnboardingStatus.SUSPENDED -> "Your helper account is currently unavailable."
    }

    private companion object {
        const val HELPER_ROLE = "HELPER"
    }
}

private fun HelperOnboardingStatus.accountStatus(): HelperAccountStatus = when (this) {
    HelperOnboardingStatus.ONBOARDING -> HelperAccountStatus.PROFILE_INCOMPLETE
    HelperOnboardingStatus.UNDER_REVIEW -> HelperAccountStatus.UNDER_REVIEW
    HelperOnboardingStatus.CHANGES_REQUESTED -> HelperAccountStatus.CHANGES_REQUESTED
    HelperOnboardingStatus.APPROVED -> HelperAccountStatus.APPROVED
    HelperOnboardingStatus.SUSPENDED -> HelperAccountStatus.SUSPENDED
}

private fun HelperAccountStatus.compatibilityStatus(): HelperOnboardingStatus = when (this) {
    HelperAccountStatus.PROFILE_INCOMPLETE,
    HelperAccountStatus.IDENTITY_INFORMATION_REQUIRED,
    HelperAccountStatus.PAYMENT_ONBOARDING_REQUIRED,
    -> HelperOnboardingStatus.ONBOARDING
    HelperAccountStatus.UNDER_REVIEW, HelperAccountStatus.PAUSED_BY_HELPER -> HelperOnboardingStatus.UNDER_REVIEW
    HelperAccountStatus.CHANGES_REQUESTED, HelperAccountStatus.REJECTED -> HelperOnboardingStatus.CHANGES_REQUESTED
    HelperAccountStatus.APPROVED -> HelperOnboardingStatus.APPROVED
    HelperAccountStatus.SUSPENDED -> HelperOnboardingStatus.SUSPENDED
}

internal fun resolveHelperStatus(
    hasHelperRole: Boolean,
    identityStatus: String,
    lifecycle: HelperLifecycleSnapshot?,
): HelperOnboardingStatus = when {
    identityStatus == SUSPENDED || lifecycle?.accountStatus == SUSPENDED -> HelperOnboardingStatus.SUSPENDED
    !hasHelperRole || lifecycle == null -> HelperOnboardingStatus.ONBOARDING
    lifecycle.approvalStatus == "APPROVED" -> HelperOnboardingStatus.APPROVED
    lifecycle.approvalStatus == "PENDING" -> HelperOnboardingStatus.UNDER_REVIEW
    lifecycle.approvalStatus == "REJECTED" -> HelperOnboardingStatus.CHANGES_REQUESTED
    lifecycle.approvalStatus == SUSPENDED -> HelperOnboardingStatus.SUSPENDED
    else -> HelperOnboardingStatus.ONBOARDING
}

private const val SUSPENDED = "SUSPENDED"
