package com.digibuddy.shared.helper.onboarding

import com.digibuddy.shared.contracts.HelperApplicationStepRequest
import com.digibuddy.shared.contracts.HelperOnboardingStep
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HelperStepValidationTest {
    @Test
    fun `public profile explains short fields before the server request`() {
        val messages = HelperStepValidation.messages(
            HelperOnboardingStep.PUBLIC_PROFILE,
            HelperApplicationStepRequest(
                values = mapOf(
                    "displayName" to "CharlesHan",
                    "headline" to "ItWorker",
                    "biography" to "Good for fixing problems",
                ),
            ),
        )

        assertEquals(2, messages.size)
        assertTrue(messages.any { "Headline" in it && "currently 8" in it })
        assertTrue(messages.any { "Biography" in it && "at least 40" in it })
    }

    @Test
    fun `realistic 32539 helper profile and service inputs are valid`() {
        val locationMessages = HelperStepValidation.messages(
            HelperOnboardingStep.HOME_AND_SERVICE_MODE,
            HelperApplicationStepRequest(values = mapOf("homeZip" to "32539", "serviceMode" to "BOTH")),
        )
        val profileMessages = HelperStepValidation.messages(
            HelperOnboardingStep.PUBLIC_PROFILE,
            HelperApplicationStepRequest(
                values = mapOf(
                    "displayName" to "Charles Han",
                    "headline" to "Patient help with computers and Wi-Fi",
                    "biography" to
                        "I help neighbors solve everyday technology problems using calm, easy-to-follow steps.",
                ),
            ),
        )

        assertTrue(locationMessages.isEmpty())
        assertTrue(profileMessages.isEmpty())
    }
}
