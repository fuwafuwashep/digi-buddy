package com.digibuddy.backend.migrations

import org.flywaydb.core.Flyway

data class DatabaseMigrationSettings(val jdbcUrl: String, val username: String, val password: String)

class DatabaseMigrator(private val settings: DatabaseMigrationSettings) {
    fun migrate(): Int = Flyway
        .configure()
        .dataSource(settings.jdbcUrl, settings.username, settings.password)
        .locations("classpath:db/migration")
        .load()
        .migrate()
        .migrationsExecuted
}
