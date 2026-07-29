package com.digibuddy.customer

import com.digibuddy.shared.contracts.DeepLinkPayload

sealed interface CustomerDestination {
    data class Helper(val id: String) : CustomerDestination
    data class Booking(val id: String) : CustomerDestination
    data class Quote(val id: String) : CustomerDestination
    data class Conversation(val id: String) : CustomerDestination
    data class PaymentResult(val id: String) : CustomerDestination
    data object SecuritySettings : CustomerDestination
    data object NotificationSettings : CustomerDestination
}

data class PendingDeepLink(val payload: DeepLinkPayload, val resumeAfterLogin: Boolean)

class DeepLinkRouter {
    private var pending: PendingDeepLink? = null

    fun route(payload: DeepLinkPayload, authenticated: Boolean): CustomerDestination? {
        if (payload.requiresAuthentication && !authenticated) {
            pending = PendingDeepLink(payload, true)
            return null
        }
        return destination(payload)
    }

    fun resumeAfterLogin(): CustomerDestination? = pending?.payload?.let(::destination).also { pending = null }

    private fun destination(payload: DeepLinkPayload): CustomerDestination? = when (payload.destination) {
        "helper" -> payload.resourceId?.let(CustomerDestination::Helper)
        "booking", "quote" -> payload.resourceId?.let(CustomerDestination::Booking)
        "chat" -> payload.resourceId?.let(CustomerDestination::Conversation)
        "payment" -> payload.resourceId?.let(CustomerDestination::PaymentResult)
        "security" -> CustomerDestination.SecuritySettings
        "notifications" -> CustomerDestination.NotificationSettings
        else -> null
    }
}
