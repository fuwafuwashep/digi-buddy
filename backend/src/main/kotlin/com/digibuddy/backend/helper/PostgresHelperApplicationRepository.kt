@file:Suppress("NestedBlockDepth", "TooGenericExceptionCaught", "TooManyFunctions")

package com.digibuddy.backend.helper

import com.digibuddy.shared.contracts.HelperAccountStatus
import com.digibuddy.shared.contracts.HelperApplicationStepRequest
import com.digibuddy.shared.contracts.HelperOnboardingStep
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Timestamp
import java.util.UUID

class PostgresHelperApplicationRepository(jdbcUrl: String, username: String, password: String) :
    HelperApplicationRepository,
    AutoCloseable {
    private val dataSource = HikariDataSource(
        HikariConfig().apply {
            this.jdbcUrl = jdbcUrl
            this.username = username
            this.password = password
            maximumPoolSize = 6
            minimumIdle = 1
            poolName = "digibuddy-helper-applications"
        },
    )
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    override fun findByUser(userId: UUID): HelperApplicationRecord? = dataSource.connection.use { connection ->
        connection.prepareStatement("SELECT * FROM helper_application WHERE user_id = ?").use { statement ->
            statement.setObject(1, userId)
            statement.executeQuery().use { results ->
                if (!results.next()) return@use null
                results.toApplication(readSteps(connection, results.getObject("id", UUID::class.java)))
            }
        }
    }

    override fun listByStatus(
        status: HelperAccountStatus,
    ): List<HelperApplicationRecord> {
        val userIds =
            dataSource.connection.use { connection ->
                connection.prepareStatement(
                    """
                SELECT user_id
                FROM helper_application
                WHERE status = ?
                ORDER BY submitted_at NULLS LAST, updated_at
                """.trimIndent(),
                ).use { statement ->
                    statement.setString(1, status.name)

                    statement.executeQuery().use { results ->
                        buildList {
                            while (results.next()) {
                                add(
                                    results.getObject(
                                        "user_id",
                                        UUID::class.java,
                                    ),
                                )
                            }
                        }
                    }
                }
            }

        return userIds.mapNotNull(::findByUser)
    }

    override fun create(application: HelperApplicationRecord): HelperApplicationRecord =
        dataSource.connection.use { connection ->
            connection.autoCommit = false
            try {
                connection.prepareStatement(
                    """
                    INSERT INTO helper_application
                        (id, user_id, status, current_step, created_at, updated_at, submitted_at, version)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (user_id) DO NOTHING
                    """.trimIndent(),
                ).use { statement ->
                    statement.bindApplication(application)
                    statement.executeUpdate()
                }
                connection.prepareStatement(
                    """
                    INSERT INTO helper_profile_status (application_id, user_id, status, updated_at)
                    VALUES (?, ?, ?, ?) ON CONFLICT (application_id) DO NOTHING
                    """.trimIndent(),
                ).use { statement ->
                    statement.setObject(1, application.id)
                    statement.setObject(2, application.userId)
                    statement.setString(3, application.status.name)
                    statement.setTimestamp(4, Timestamp.from(application.updatedAt))
                    statement.executeUpdate()
                }
                connection.commit()
                findByUser(application.userId) ?: application
            } catch (cause: Exception) {
                connection.rollback()
                throw cause
            } finally {
                connection.autoCommit = true
            }
        }

    override fun saveStep(application: HelperApplicationRecord, step: HelperStepRecord): HelperApplicationRecord =
        dataSource.connection.use { connection ->
            connection.autoCommit = false
            try {
                connection.prepareStatement(
                    """
                INSERT INTO helper_application_step (application_id, step, payload_json, completed, saved_at)
                VALUES (?, ?, CAST(? AS JSONB), ?, ?)
                ON CONFLICT (application_id, step) DO UPDATE SET
                    payload_json = EXCLUDED.payload_json,
                    completed = EXCLUDED.completed,
                    saved_at = EXCLUDED.saved_at
                    """.trimIndent(),
                ).use { statement ->
                    statement.setObject(1, application.id)
                    statement.setString(2, step.step.name)
                    statement.setString(3, json.encodeToString(step.payload))
                    statement.setBoolean(4, step.completed)
                    statement.setTimestamp(5, Timestamp.from(step.savedAt))
                    statement.executeUpdate()
                }
                updateApplication(connection, application)
                updateProfileStatus(connection, application)
                connection.commit()
                application
            } catch (cause: Exception) {
                connection.rollback()
                throw cause
            } finally {
                connection.autoCommit = true
            }
        }

    override fun updateStatus(
        application: HelperApplicationRecord,
        event: HelperApprovalEventRecord,
    ): HelperApplicationRecord = statusTransaction(application, event, grantRole = false)

    override fun approveAndGrantRole(
        application: HelperApplicationRecord,
        event: HelperApprovalEventRecord,
    ): HelperApplicationRecord = statusTransaction(application, event, grantRole = true)

    override fun requiredChanges(applicationId: UUID): List<HelperRequiredChangeRecord> =
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                SELECT * FROM helper_required_change
                WHERE application_id = ? AND resolved_at IS NULL
                ORDER BY created_at
                """.trimIndent(),
            ).use { statement ->
                statement.setObject(1, applicationId)
                statement.executeQuery().use { results ->
                    buildList {
                        while (results.next()) {
                            add(
                                HelperRequiredChangeRecord(
                                    results.getObject("id", UUID::class.java),
                                    results.getObject("application_id", UUID::class.java),
                                    HelperOnboardingStep.valueOf(results.getString("step")),
                                    results.getString("message"),
                                    results.getTimestamp("created_at").toInstant(),
                                    results.getTimestamp("resolved_at")?.toInstant(),
                                ),
                            )
                        }
                    }
                }
            }
        }

    override fun replaceRequiredChanges(applicationId: UUID, changes: List<HelperRequiredChangeRecord>) {
        dataSource.connection.use { connection ->
            connection.autoCommit = false
            try {
                connection.prepareStatement(
                    """
                    UPDATE helper_required_change SET resolved_at = now()
                    WHERE application_id = ? AND resolved_at IS NULL
                    """.trimIndent(),
                ).use { statement ->
                    statement.setObject(1, applicationId)
                    statement.executeUpdate()
                }
                connection.prepareStatement(
                    """
                    INSERT INTO helper_required_change (id, application_id, step, message, created_at)
                    VALUES (?, ?, ?, ?, ?)
                    """.trimIndent(),
                ).use { statement ->
                    changes.forEach { change ->
                        statement.setObject(1, change.id)
                        statement.setObject(2, change.applicationId)
                        statement.setString(3, change.step.name)
                        statement.setString(4, change.message)
                        statement.setTimestamp(5, Timestamp.from(change.createdAt))
                        statement.addBatch()
                    }
                    statement.executeBatch()
                }
                connection.commit()
            } catch (cause: Exception) {
                connection.rollback()
                throw cause
            } finally {
                connection.autoCommit = true
            }
        }
    }

    override fun approvalEvents(applicationId: UUID): List<HelperApprovalEventRecord> =
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                "SELECT * FROM helper_approval_event WHERE application_id = ? ORDER BY occurred_at",
            ).use { statement ->
                statement.setObject(1, applicationId)
                statement.executeQuery().use { results ->
                    buildList {
                        while (results.next()) add(results.toApprovalEvent())
                    }
                }
            }
        }

    override fun close() = dataSource.close()

    private fun statusTransaction(
        application: HelperApplicationRecord,
        event: HelperApprovalEventRecord,
        grantRole: Boolean,
    ): HelperApplicationRecord = dataSource.connection.use { connection ->
        connection.autoCommit = false
        try {
            updateApplication(connection, application)
            updateProfileStatus(connection, application)
            insertEvent(connection, event)
            if (grantRole) {
                connection.prepareStatement(
                    """
                    INSERT INTO user_role (user_id, role, granted_at) VALUES (?, 'HELPER', ?)
                    ON CONFLICT (user_id, role) DO NOTHING
                    """.trimIndent(),
                ).use { statement ->
                    statement.setObject(1, application.userId)
                    statement.setTimestamp(2, Timestamp.from(event.occurredAt))
                    statement.executeUpdate()
                }
            }
            connection.commit()
            application
        } catch (cause: Exception) {
            connection.rollback()
            throw cause
        } finally {
            connection.autoCommit = true
        }
    }

    private fun readSteps(connection: Connection, applicationId: UUID): Map<HelperOnboardingStep, HelperStepRecord> =
        connection.prepareStatement("SELECT * FROM helper_application_step WHERE application_id = ?").use { statement ->
            statement.setObject(1, applicationId)
            statement.executeQuery().use { results ->
                buildMap {
                    while (results.next()) {
                        val step = HelperOnboardingStep.valueOf(results.getString("step"))
                        put(
                            step,
                            HelperStepRecord(
                                step,
                                json.decodeFromString<HelperApplicationStepRequest>(results.getString("payload_json")),
                                results.getBoolean("completed"),
                                results.getTimestamp("saved_at").toInstant(),
                            ),
                        )
                    }
                }
            }
        }

    private fun updateApplication(connection: Connection, application: HelperApplicationRecord) {
        connection.prepareStatement(
            """
            UPDATE helper_application SET status = ?, current_step = ?, updated_at = ?, submitted_at = ?, version = ?
            WHERE id = ?
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, application.status.name)
            statement.setString(2, application.currentStep.name)
            statement.setTimestamp(3, Timestamp.from(application.updatedAt))
            statement.setTimestamp(4, application.submittedAt?.let(Timestamp::from))
            statement.setInt(5, application.version)
            statement.setObject(6, application.id)
            check(statement.executeUpdate() == 1) { "Helper application update failed" }
        }
    }

    private fun updateProfileStatus(connection: Connection, application: HelperApplicationRecord) {
        connection.prepareStatement(
            "UPDATE helper_profile_status SET status = ?, updated_at = ? WHERE application_id = ?",
        ).use { statement ->
            statement.setString(1, application.status.name)
            statement.setTimestamp(2, Timestamp.from(application.updatedAt))
            statement.setObject(3, application.id)
            statement.executeUpdate()
        }
    }

    private fun insertEvent(connection: Connection, event: HelperApprovalEventRecord) {
        connection.prepareStatement(
            """
            INSERT INTO helper_approval_event
                (id, application_id, from_status, to_status, actor_user_id, reason, occurred_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, event.id)
            statement.setObject(2, event.applicationId)
            statement.setString(3, event.fromStatus.name)
            statement.setString(4, event.toStatus.name)
            statement.setObject(5, event.actorUserId)
            statement.setString(6, event.reason)
            statement.setTimestamp(7, Timestamp.from(event.occurredAt))
            statement.executeUpdate()
        }
    }

    private fun java.sql.PreparedStatement.bindApplication(application: HelperApplicationRecord) {
        setObject(1, application.id)
        setObject(2, application.userId)
        setString(3, application.status.name)
        setString(4, application.currentStep.name)
        setTimestamp(5, Timestamp.from(application.createdAt))
        setTimestamp(6, Timestamp.from(application.updatedAt))
        setTimestamp(7, application.submittedAt?.let(Timestamp::from))
        setInt(8, application.version)
    }

    private fun ResultSet.toApplication(steps: Map<HelperOnboardingStep, HelperStepRecord>) = HelperApplicationRecord(
        id = getObject("id", UUID::class.java),
        userId = getObject("user_id", UUID::class.java),
        status = HelperAccountStatus.valueOf(getString("status")),
        currentStep = HelperOnboardingStep.valueOf(getString("current_step")),
        steps = steps,
        createdAt = getTimestamp("created_at").toInstant(),
        updatedAt = getTimestamp("updated_at").toInstant(),
        submittedAt = getTimestamp("submitted_at")?.toInstant(),
        version = getInt("version"),
    )

    private fun ResultSet.toApprovalEvent() = HelperApprovalEventRecord(
        id = getObject("id", UUID::class.java),
        applicationId = getObject("application_id", UUID::class.java),
        fromStatus = HelperAccountStatus.valueOf(getString("from_status")),
        toStatus = HelperAccountStatus.valueOf(getString("to_status")),
        actorUserId = getObject("actor_user_id", UUID::class.java),
        reason = getString("reason"),
        occurredAt = getTimestamp("occurred_at").toInstant(),
    )
}
