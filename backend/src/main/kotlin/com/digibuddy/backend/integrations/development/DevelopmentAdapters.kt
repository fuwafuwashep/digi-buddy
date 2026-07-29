package com.digibuddy.backend.integrations.development

interface DevelopmentIntegrationAdapter {
    val providerName: String
    val purpose: String
    val isProductionReady: Boolean
        get() = false
}

data object DevelopmentTwilioAdapter : DevelopmentIntegrationAdapter {
    override val providerName = "Twilio"
    override val purpose = "Development placeholder for SMS and voice notifications; sends nothing."
}

data object DevelopmentStripeAdapter : DevelopmentIntegrationAdapter {
    override val providerName = "Stripe"
    override val purpose = "Development placeholder for payment-provider wiring; processes no money."
}

data object DevelopmentApnsAdapter : DevelopmentIntegrationAdapter {
    override val providerName = "APNs"
    override val purpose = "Development placeholder for Apple push notifications; sends no notifications."
}

data object DevelopmentS3Adapter : DevelopmentIntegrationAdapter {
    override val providerName = "S3-compatible object storage"
    override val purpose = "Development placeholder for object storage; persists no objects."
}

object DevelopmentIntegrationRegistry {
    val adapters: List<DevelopmentIntegrationAdapter> =
        listOf(
            DevelopmentTwilioAdapter,
            DevelopmentStripeAdapter,
            DevelopmentApnsAdapter,
            DevelopmentS3Adapter,
        )
}
