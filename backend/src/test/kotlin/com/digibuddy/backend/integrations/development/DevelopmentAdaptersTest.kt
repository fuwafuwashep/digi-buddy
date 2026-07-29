package com.digibuddy.backend.integrations.development

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class DevelopmentAdaptersTest {
    @Test
    fun placeholdersAreExplicitlyNotProductionReady() {
        assertEquals(4, DevelopmentIntegrationRegistry.adapters.size)
        DevelopmentIntegrationRegistry.adapters.forEach { adapter ->
            assertFalse(adapter.isProductionReady, adapter.providerName)
        }
    }
}
