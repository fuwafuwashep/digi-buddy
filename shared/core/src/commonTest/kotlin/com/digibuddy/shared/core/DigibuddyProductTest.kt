package com.digibuddy.shared.core

import kotlin.test.Test
import kotlin.test.assertEquals

class DigibuddyProductTest {
    @Test
    fun productNameIsStable() {
        assertEquals("Digibuddy", DigibuddyProduct.NAME)
    }
}
