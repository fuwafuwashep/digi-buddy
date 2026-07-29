package com.digibuddy.shared.persistence

data class HealthSnapshot(val status: String, val checkedAtEpochSeconds: Long)

class HealthCache(private val database: DigibuddyDatabase) {
    fun write(snapshot: HealthSnapshot) {
        database.healthStatusQueries.upsertHealthStatus(
            status = snapshot.status,
            checkedAtEpochSeconds = snapshot.checkedAtEpochSeconds,
        )
    }

    fun read(): HealthSnapshot? = database.healthStatusQueries
        .selectHealthStatus { status, checkedAtEpochSeconds ->
            HealthSnapshot(
                status = status,
                checkedAtEpochSeconds = checkedAtEpochSeconds,
            )
        }.executeAsOneOrNull()

    fun clear() {
        database.healthStatusQueries.clearHealthStatus()
    }
}
