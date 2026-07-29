package com.digibuddy.shared.core

object DigibuddyProduct {
    const val NAME: String = "Digibuddy"
}

data class HelpPrice(val name: String, val description: String, val priceCents: Int)

data class MembershipPrice(val name: String, val monthlyPriceCents: Int, val includedIssues: Int?)

/**
 * The platform-owned customer price schedule. Helpers never provide or change these amounts.
 * The backend remains authoritative when a booking or future membership is charged.
 */
object DigibuddyPricing {
    const val QUICK_REMOTE_CENTS = 2_900
    const val STANDARD_HELP_CENTS = 4_900
    const val IN_HOME_VISIT_CENTS = 7_900

    val oneTimeHelp = listOf(
        HelpPrice("Quick remote help", "A focused remote fix", QUICK_REMOTE_CENTS),
        HelpPrice("Standard Help", "30–60 minutes", STANDARD_HELP_CENTS),
        HelpPrice("In-home visit", "Technology help at your home", IN_HOME_VISIT_CENTS),
    )

    val memberships = listOf(
        MembershipPrice("10-issue plan", 999, 10),
        MembershipPrice("30-issue plan", 1_999, 30),
        MembershipPrice("Unlimited Help", 9_999, null),
    )

    fun bookingLaborCents(serviceMode: String): Int =
        if (serviceMode == "IN_PERSON") IN_HOME_VISIT_CENTS else QUICK_REMOTE_CENTS

    fun startingPriceCents(remote: Boolean, inPerson: Boolean): Int = when {
        remote -> QUICK_REMOTE_CENTS
        inPerson -> IN_HOME_VISIT_CENTS
        else -> STANDARD_HELP_CENTS
    }
}

enum class DigibuddyEnvironment {
    LOCAL_DEVELOPMENT,
}
