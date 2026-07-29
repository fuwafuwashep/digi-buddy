package com.digibuddy.shared.helper.onboarding

import com.digibuddy.shared.contracts.HelperApplicationStepRequest
import com.digibuddy.shared.contracts.HelperOnboardingStep

object HelperStepValidation {
    fun messages(step: HelperOnboardingStep, request: HelperApplicationStepRequest): List<String> {
        val values = request.values.mapValues { it.value.trim() }
        val lists = request.listValues.mapValues { entry -> entry.value.filter(String::isNotBlank) }
        fun lengthMessage(key: String, label: String, minimum: Int, maximum: Int): String? {
            val length = values[key].orEmpty().length
            return when {
                length < minimum -> "$label needs at least $minimum characters (currently $length)."
                length > maximum -> "$label must be $maximum characters or fewer."
                else -> null
            }
        }
        fun selectionMessage(key: String, label: String) =
            if (lists[key].isNullOrEmpty()) "Choose at least one $label." else null

        return when (step) {
            HelperOnboardingStep.LEGAL_NAME -> listOfNotNull(
                lengthMessage("legalFirstName", "First name", 1, 80),
                lengthMessage("legalLastName", "Last name", 1, 80),
            )
            HelperOnboardingStep.PUBLIC_PROFILE -> listOfNotNull(
                lengthMessage("displayName", "Display name", 2, 100),
                lengthMessage("headline", "Headline", 10, 160),
                lengthMessage("biography", "Biography", 40, 1_200),
            )
            HelperOnboardingStep.HOME_AND_SERVICE_MODE -> listOfNotNull(
                if (values["homeZip"]?.matches(Regex("^[0-9]{5}$")) == true) {
                    null
                } else {
                    "Enter a five-digit ZIP code."
                },
                if (values["serviceMode"] in setOf("IN_PERSON", "REMOTE", "BOTH")) {
                    null
                } else {
                    "Choose in-person, remote, or both."
                },
            )
            HelperOnboardingStep.SERVICE_AREA -> listOfNotNull(
                lengthMessage("serviceAreaSummary", "Service area", 2, 240),
            )
            HelperOnboardingStep.SKILLS -> listOfNotNull(selectionMessage("skillIds", "skill"))
            HelperOnboardingStep.SERVICES -> listOfNotNull(selectionMessage("serviceCategoryIds", "service"))
            HelperOnboardingStep.EXPERIENCE -> listOfNotNull(
                if (values["yearsExperience"]?.toIntOrNull() in 0..80) {
                    null
                } else {
                    "Enter years of experience from 0 to 80."
                },
            )
            HelperOnboardingStep.LANGUAGES -> listOfNotNull(selectionMessage("languages", "language"))
            HelperOnboardingStep.AVAILABILITY -> listOfNotNull(
                lengthMessage("availabilitySummary", "Availability", 2, 240),
            )
            HelperOnboardingStep.PRICING -> acknowledged(
                request,
                "Review and acknowledge the Digibuddy pricing policy.",
                "platformPricingAcknowledged",
            )
            HelperOnboardingStep.TERMS_AND_POLICIES -> acknowledged(
                request,
                "Review and accept the helper terms and safety rules.",
                "accepted",
            )
            HelperOnboardingStep.PAYOUT_ONBOARDING -> acknowledged(
                request,
                "Acknowledge the development payout placeholder.",
                "placeholderAcknowledged",
            )
            HelperOnboardingStep.PROFILE_MEDIA,
            HelperOnboardingStep.CERTIFICATIONS,
            HelperOnboardingStep.PORTFOLIO,
            -> emptyList()
        }
    }

    private fun acknowledged(request: HelperApplicationStepRequest, message: String, key: String) =
        if (request.booleanValues[key] == true) emptyList() else listOf(message)
}
