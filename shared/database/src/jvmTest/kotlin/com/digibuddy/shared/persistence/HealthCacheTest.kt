package com.digibuddy.shared.persistence

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HealthCacheTest {
    @Test
    fun writesReadsAndClearsSnapshot() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        DigibuddyDatabase.Schema.create(driver)
        val cache = HealthCache(DigibuddyDatabase(driver))
        val expected = HealthSnapshot(status = "ok", checkedAtEpochSeconds = 1_234L)

        cache.write(expected)
        assertEquals(expected, cache.read())

        cache.clear()
        assertNull(cache.read())
        driver.close()
    }
}
