package com.digibuddy.shared.helper.dashboard

import kotlin.test.Test
import kotlin.test.assertEquals

class HelperTabTest {
    @Test
    fun `helper shell exposes exactly the required four tabs`() {
        assertEquals(listOf("Requests", "Jobs", "Chats", "Profile"), HelperTab.entries.map { it.label })
    }
}
