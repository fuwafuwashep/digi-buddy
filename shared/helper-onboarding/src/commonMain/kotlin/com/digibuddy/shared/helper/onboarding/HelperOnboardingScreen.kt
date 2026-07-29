@file:Suppress("LongMethod", "CyclomaticComplexMethod")

package com.digibuddy.shared.helper.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.digibuddy.shared.contracts.HelperAccountStatus
import com.digibuddy.shared.contracts.HelperApplicationStepRequest
import com.digibuddy.shared.contracts.HelperOnboardingStep
import com.digibuddy.shared.designsystem.DigibuddyCard
import com.digibuddy.shared.designsystem.DigibuddyColors
import com.digibuddy.shared.designsystem.StatusPill

@Composable
fun HelperOnboardingScreen(
    state: HelperApplicationUiState,
    onSaveStep: (HelperOnboardingStep, HelperApplicationStepRequest) -> Unit,
    onSubmit: () -> Unit,
    onRetry: () -> Unit,
    onSignOut: () -> Unit,
    onChooseProfilePhoto: () -> Unit,
) {
    val application = state.application
    if (state.loading && application == null) {
        Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator()
            Spacer(Modifier.height(12.dp))
            Text("Loading your saved progress…")
        }
        return
    }
    if (application == null) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
            Text("We could not load your application", style = MaterialTheme.typography.headlineSmall)
            Text(state.errorMessage ?: "Please try again.")
            Button(onClick = onRetry) { Text("Try again") }
            TextButton(onClick = onSignOut) { Text("Sign out") }
        }
        return
    }

    var selectedStep by remember(application.applicationId, application.currentStep) {
        mutableStateOf(application.currentStep)
    }
    val step = if (application.status == HelperAccountStatus.CHANGES_REQUESTED) {
        application.requestedChanges.firstOrNull()?.step ?: selectedStep
    } else {
        selectedStep
    }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("DIGIBUDDY HELPERS", color = DigibuddyColors.Gold, style = MaterialTheme.typography.labelLarge)
        Text("Your helper application", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            "We save each step as you go. You can sign out and finish later.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LinearProgressIndicator(
            progress = { application.progressPercent / 100f },
            modifier = Modifier.fillMaxWidth().height(10.dp),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            StatusPill("${application.progressPercent}% complete", DigibuddyColors.Navy)
            Text("${application.completedSteps.size} saved", style = MaterialTheme.typography.labelLarge)
        }
        application.requestedChanges.forEach {
            DigibuddyCard {
                StatusPill("Needs attention", MaterialTheme.colorScheme.error)
                Text(it.message, fontWeight = FontWeight.SemiBold)
                OutlinedButton(onClick = { selectedStep = it.step }) { Text("Open this step") }
            }
        }
        DigibuddyCard {
            Text(step.title(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(step.explanation(), color = MaterialTheme.colorScheme.onSurfaceVariant)
            FieldBadges(step.visibility(), step.isRequired())
            StepFields(
                step = step,
                existing = application.steps.firstOrNull { it.step == step }?.let {
                    HelperApplicationStepRequest(it.values, it.listValues, it.booleanValues)
                },
                saving = state.saving,
                onSave = { payload -> onSaveStep(step, payload) },
                onChooseProfilePhoto = onChooseProfilePhoto,
            )
            state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
        if (application.canSubmit) {
            Button(onClick = onSubmit, enabled = !state.saving, modifier = Modifier.fillMaxWidth()) {
                Text("Submit for review")
            }
            Text(
                "Submitting does not approve your account. Digibuddy must review it before you can receive paid work.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onSignOut, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Sign out") }
    }
}

@Composable
private fun StepFields(
    step: HelperOnboardingStep,
    existing: HelperApplicationStepRequest?,
    saving: Boolean,
    onSave: (HelperApplicationStepRequest) -> Unit,
    onChooseProfilePhoto: () -> Unit,
) {
    var first by remember(step, existing) { mutableStateOf(existing?.values?.get(step.firstKey()).orEmpty()) }
    var second by remember(step, existing) { mutableStateOf(existing?.values?.get(step.secondKey()).orEmpty()) }
    var third by remember(step, existing) { mutableStateOf(existing?.values?.get(step.thirdKey()).orEmpty()) }
    var list by remember(step, existing) {
        mutableStateOf(existing?.listValues?.get(step.listKey()).orEmpty().joinToString(", "))
    }

    when (step) {
        HelperOnboardingStep.LEGAL_NAME -> {
            Field("Legal first name", first, { first = it })
            Field("Legal last name", second, { second = it })
        }
        HelperOnboardingStep.PUBLIC_PROFILE -> {
            Field("Public display name", first, { first = it })
            Field("Public headline", second, { second = it })
            Field("Public biography", third, { third = it }, minLines = 4)
            Text(
                "Use at least 10 characters for the headline and 40 for the biography.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HelperOnboardingStep.PROFILE_MEDIA -> {
            Text("Choose an optional profile photo from the Files or photo picker on your device.")
            Button(onChooseProfilePhoto, enabled = !saving, modifier = Modifier.fillMaxWidth()) {
                Text(if (saving) "Uploading..." else "Choose profile photo from files")
            }
            Text(
                if (first.isBlank()) "No photo selected" else "Profile photo uploaded",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("JPEG, PNG, or WebP; maximum 5 MB.", style = MaterialTheme.typography.bodySmall)
        }
        HelperOnboardingStep.HOME_AND_SERVICE_MODE -> {
            Field("Private home ZIP code", first, { first = it }, keyboardType = KeyboardType.Number)
            Field("Service type: IN_PERSON, REMOTE, or BOTH", second, { second = it })
        }
        HelperOnboardingStep.SERVICE_AREA -> Field("Public approximate service area", first, { first = it })
        HelperOnboardingStep.SKILLS -> {
            Field("Skills, separated by commas", list, { list = it }, minLines = 2)
            Text("Examples: windows, iphone-ipad, home-networking, printers")
        }
        HelperOnboardingStep.SERVICES -> {
            Field("Services, separated by commas", list, { list = it }, minLines = 2)
            Text("Examples: computer-help, phone-tablet-help, wifi-internet, printer-setup")
        }
        HelperOnboardingStep.EXPERIENCE -> Field("Years of experience", first, {
            first = it
        }, keyboardType = KeyboardType.Number)
        HelperOnboardingStep.LANGUAGES -> Field("Languages, separated by commas", list, { list = it })
        HelperOnboardingStep.PRICING -> {
            Text("Digibuddy sets customer prices. Helpers cannot choose or change customer rates.")
            Text(
                "Your earnings and payout terms will be shown separately before you accept work. " +
                    "Continuing acknowledges the platform pricing policy.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HelperOnboardingStep.AVAILABILITY -> Field("Public availability summary", first, { first = it })
        HelperOnboardingStep.CERTIFICATIONS -> Field("Certifications, separated by commas (optional)", list, {
            list = it
        })
        HelperOnboardingStep.PORTFOLIO -> Field("Portfolio links, separated by commas (optional)", list, { list = it })
        HelperOnboardingStep.TERMS_AND_POLICIES ->
            Text("By continuing, you confirm you reviewed the helper terms, privacy notice, and safety rules.")
        HelperOnboardingStep.PAYOUT_ONBOARDING ->
            Text("Development placeholder only. No bank or tax information is collected in the app.")
    }

    val payload = when (step) {
        HelperOnboardingStep.LEGAL_NAME -> values("legalFirstName" to first, "legalLastName" to second)
        HelperOnboardingStep.PUBLIC_PROFILE -> values(
            "displayName" to first,
            "headline" to second,
            "biography" to third,
        )
        HelperOnboardingStep.PROFILE_MEDIA -> values("profilePictureUrl" to first, "bannerImageUrl" to second)
        HelperOnboardingStep.HOME_AND_SERVICE_MODE -> values("homeZip" to first, "serviceMode" to second.uppercase())
        HelperOnboardingStep.SERVICE_AREA -> values("serviceAreaSummary" to first)
        HelperOnboardingStep.SKILLS -> lists("skillIds", list)
        HelperOnboardingStep.SERVICES -> lists("serviceCategoryIds", list)
        HelperOnboardingStep.EXPERIENCE -> values("yearsExperience" to first)
        HelperOnboardingStep.LANGUAGES -> lists("languages", list)
        HelperOnboardingStep.PRICING -> HelperApplicationStepRequest(
            booleanValues = mapOf("platformPricingAcknowledged" to true),
        )
        HelperOnboardingStep.AVAILABILITY -> values("availabilitySummary" to first)
        HelperOnboardingStep.CERTIFICATIONS -> lists("certifications", list)
        HelperOnboardingStep.PORTFOLIO -> lists("portfolio", list)
        HelperOnboardingStep.TERMS_AND_POLICIES -> HelperApplicationStepRequest(
            booleanValues = mapOf("accepted" to true),
        )
        HelperOnboardingStep.PAYOUT_ONBOARDING -> HelperApplicationStepRequest(
            booleanValues = mapOf("placeholderAcknowledged" to true),
        )
    }
    val validationMessages = HelperStepValidation.messages(step, payload)
    validationMessages.forEach { message ->
        Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
    Button(
        onClick = { onSave(payload) },
        enabled = !saving && validationMessages.isEmpty(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (saving) {
            CircularProgressIndicator(
                Modifier.height(18.dp),
            )
        } else {
            Text(if (step.isRequired()) "Save and continue" else "Save or skip")
        }
    }
}

@Composable
private fun FieldBadges(visibility: String, required: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatusPill(visibility, if (visibility == "Private") DigibuddyColors.Navy else DigibuddyColors.Teal)
        StatusPill(if (required) "Required" else "Optional", DigibuddyColors.Slate)
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    minLines: Int = 1,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        minLines = minLines,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
    )
}

private fun values(vararg pairs: Pair<String, String>) = HelperApplicationStepRequest(values = mapOf(*pairs))

private fun lists(key: String, value: String) = HelperApplicationStepRequest(
    listValues = mapOf(key to value.split(',').map(String::trim).filter(String::isNotBlank)),
)

private fun HelperOnboardingStep.title(): String = name.lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase)

private fun HelperOnboardingStep.explanation(): String = when (this) {
    HelperOnboardingStep.LEGAL_NAME -> "Used only for identity, payment, tax, safety, and support needs. It is private."
    HelperOnboardingStep.PUBLIC_PROFILE -> "Customers will see these details when your profile is approved."
    HelperOnboardingStep.PROFILE_MEDIA -> "Add friendly images, or skip this optional step for now."
    HelperOnboardingStep.HOME_AND_SERVICE_MODE ->
        "Your exact home information stays private. Customers see only an approximate service area."
    HelperOnboardingStep.SERVICE_AREA -> "Describe the broad area you serve without entering a street address."
    HelperOnboardingStep.SKILLS -> "Choose only staff-managed skills you can confidently provide."
    HelperOnboardingStep.SERVICES -> "Choose the technology services you want to offer."
    HelperOnboardingStep.EXPERIENCE -> "Tell customers how long you have helped with technology."
    HelperOnboardingStep.LANGUAGES -> "List languages you can comfortably use while helping customers."
    HelperOnboardingStep.PRICING -> "Review the Digibuddy pricing policy. Customer prices are set by Digibuddy."
    HelperOnboardingStep.AVAILABILITY -> "Provide a short preview. A full calendar editor arrives later."
    HelperOnboardingStep.CERTIFICATIONS -> "Optional. Never upload private identity documents here."
    HelperOnboardingStep.PORTFOLIO -> "Optional examples of relevant, fictional or authorized work only."
    HelperOnboardingStep.TERMS_AND_POLICIES -> "Review the rules that keep customers and helpers safe."
    HelperOnboardingStep.PAYOUT_ONBOARDING ->
        "This placeholder prepares the workflow without collecting financial details."
}

private fun HelperOnboardingStep.visibility(): String = when (this) {
    HelperOnboardingStep.LEGAL_NAME,
    HelperOnboardingStep.HOME_AND_SERVICE_MODE,
    HelperOnboardingStep.PRICING,
    HelperOnboardingStep.TERMS_AND_POLICIES,
    HelperOnboardingStep.PAYOUT_ONBOARDING,
    -> "Private"
    else -> "Public"
}

private fun HelperOnboardingStep.isRequired(): Boolean = this !in setOf(
    HelperOnboardingStep.PROFILE_MEDIA,
    HelperOnboardingStep.CERTIFICATIONS,
    HelperOnboardingStep.PORTFOLIO,
)

private fun HelperOnboardingStep.firstKey(): String = when (this) {
    HelperOnboardingStep.LEGAL_NAME -> "legalFirstName"
    HelperOnboardingStep.PUBLIC_PROFILE -> "displayName"
    HelperOnboardingStep.PROFILE_MEDIA -> "profilePictureUrl"
    HelperOnboardingStep.HOME_AND_SERVICE_MODE -> "homeZip"
    HelperOnboardingStep.SERVICE_AREA -> "serviceAreaSummary"
    HelperOnboardingStep.EXPERIENCE -> "yearsExperience"
    HelperOnboardingStep.AVAILABILITY -> "availabilitySummary"
    else -> "value"
}

private fun HelperOnboardingStep.secondKey(): String = when (this) {
    HelperOnboardingStep.LEGAL_NAME -> "legalLastName"
    HelperOnboardingStep.PUBLIC_PROFILE -> "headline"
    HelperOnboardingStep.PROFILE_MEDIA -> "bannerImageUrl"
    HelperOnboardingStep.HOME_AND_SERVICE_MODE -> "serviceMode"
    else -> "second"
}

private fun HelperOnboardingStep.thirdKey(): String = if (this ==
    HelperOnboardingStep.PUBLIC_PROFILE
) {
    "biography"
} else {
    "third"
}

private fun HelperOnboardingStep.listKey(): String = when (this) {
    HelperOnboardingStep.SKILLS -> "skillIds"
    HelperOnboardingStep.SERVICES -> "serviceCategoryIds"
    HelperOnboardingStep.LANGUAGES -> "languages"
    HelperOnboardingStep.CERTIFICATIONS -> "certifications"
    HelperOnboardingStep.PORTFOLIO -> "portfolio"
    else -> "items"
}
