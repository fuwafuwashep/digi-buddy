@file:Suppress("TooGenericExceptionCaught", "TooManyFunctions")

package com.digibuddy.backend.customer

import com.digibuddy.shared.contracts.SavedAddressResponse
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

class PostgresCustomerProfileRepository(
    jdbcUrl: String,
    username: String,
    password: String,
) : CustomerProfileRepository, AutoCloseable {

    private val dataSource = HikariDataSource(
        HikariConfig().apply {
            this.jdbcUrl = jdbcUrl
            this.username = username
            this.password = password
            maximumPoolSize = 6
            minimumIdle = 1
            poolName = "digibuddy-customer-profiles"
        },
    )

    override fun findProfile(userId: UUID): CustomerProfileRecord? =
        dataSource.connection.use { connection ->
            val profile = connection.prepareStatement(
                """
                SELECT
                    user_id,
                    first_name,
                    last_name,
                    public_display_name,
                    profile_photo_object_key,
                    zip_code,
                    location_permission_status,
                    notification_permission_status,
                    notifications_enabled,
                    follow_system_text_size,
                    extra_large_text,
                    high_contrast,
                    reduced_motion,
                    simplified_instructions,
                    biometric_unlock_enabled,
                    onboarding_completed_at
                FROM customer_profile
                WHERE user_id = ?
                """.trimIndent(),
            ).use { statement ->
                statement.setObject(1, userId)

                statement.executeQuery().use { results ->
                    if (results.next()) {
                        results.toCustomerProfileRecord()
                    } else {
                        null
                    }
                }
            }

            profile?.copy(
                technologyPreferences = readPreferences(connection, userId),
            )
        }

    override fun saveProfile(profile: CustomerProfileRecord) {
        dataSource.connection.use { connection ->
            connection.autoCommit = false

            try {
                connection.prepareStatement(
                    """
                    INSERT INTO customer_profile (
                        user_id,
                        first_name,
                        last_name,
                        public_display_name,
                        profile_photo_object_key,
                        zip_code,
                        location_permission_status,
                        notification_permission_status,
                        notifications_enabled,
                        follow_system_text_size,
                        extra_large_text,
                        high_contrast,
                        reduced_motion,
                        simplified_instructions,
                        biometric_unlock_enabled,
                        onboarding_completed_at,
                        updated_at
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (user_id) DO UPDATE SET
                        first_name = EXCLUDED.first_name,
                        last_name = EXCLUDED.last_name,
                        public_display_name = EXCLUDED.public_display_name,
                        profile_photo_object_key = EXCLUDED.profile_photo_object_key,
                        zip_code = EXCLUDED.zip_code,
                        location_permission_status = EXCLUDED.location_permission_status,
                        notification_permission_status = EXCLUDED.notification_permission_status,
                        notifications_enabled = EXCLUDED.notifications_enabled,
                        follow_system_text_size = EXCLUDED.follow_system_text_size,
                        extra_large_text = EXCLUDED.extra_large_text,
                        high_contrast = EXCLUDED.high_contrast,
                        reduced_motion = EXCLUDED.reduced_motion,
                        simplified_instructions = EXCLUDED.simplified_instructions,
                        biometric_unlock_enabled = EXCLUDED.biometric_unlock_enabled,
                        onboarding_completed_at = EXCLUDED.onboarding_completed_at,
                        updated_at = EXCLUDED.updated_at
                    """.trimIndent(),
                ).use { statement ->
                    statement.setObject(1, profile.userId)
                    statement.setString(2, profile.firstName)
                    statement.setString(3, profile.lastName)
                    statement.setString(4, profile.displayName)
                    statement.setString(5, profile.photoUrl)
                    statement.setString(6, profile.zipCode)
                    statement.setString(7, profile.locationPermission)
                    statement.setString(8, profile.notificationPermission)
                    statement.setBoolean(9, profile.notificationsEnabled)
                    statement.setBoolean(10, profile.followSystemTextSize)
                    statement.setBoolean(11, profile.extraLargeText)
                    statement.setBoolean(12, profile.highContrast)
                    statement.setBoolean(13, profile.reducedMotion)
                    statement.setBoolean(14, profile.simplifiedInstructions)
                    statement.setBoolean(15, profile.biometricUnlockEnabled)
                    statement.setTimestamp(16, Timestamp.from(profile.completedAt))
                    statement.setTimestamp(17, Timestamp.from(Instant.now()))
                    statement.executeUpdate()
                }

                connection.prepareStatement(
                    """
                    DELETE FROM customer_technology_preference
                    WHERE user_id = ?
                    """.trimIndent(),
                ).use { statement ->
                    statement.setObject(1, profile.userId)
                    statement.executeUpdate()
                }

                if (profile.technologyPreferences.isNotEmpty()) {
                    connection.prepareStatement(
                        """
                        INSERT INTO customer_technology_preference (
                            user_id,
                            preference
                        )
                        VALUES (?, ?)
                        """.trimIndent(),
                    ).use { statement ->
                        profile.technologyPreferences.forEach { preference ->
                            statement.setObject(1, profile.userId)
                            statement.setString(2, preference)
                            statement.addBatch()
                        }

                        statement.executeBatch()
                    }
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

    override fun saveAddress(
        userId: UUID,
        address: SavedAddressResponse,
    ) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                INSERT INTO customer_saved_address (
                    id,
                    user_id,
                    label,
                    line1,
                    line2,
                    city,
                    region,
                    zip_code,
                    created_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
            ).use { statement ->
                statement.setObject(1, UUID.fromString(address.id))
                statement.setObject(2, userId)
                statement.setString(3, address.label)
                statement.setString(4, address.line1)
                statement.setString(5, address.line2)
                statement.setString(6, address.city)
                statement.setString(7, address.region)
                statement.setString(8, address.zipCode)
                statement.setTimestamp(9, Timestamp.from(Instant.now()))
                statement.executeUpdate()
            }
        }
    }

    override fun addresses(userId: UUID): List<SavedAddressResponse> =
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                SELECT
                    id,
                    label,
                    line1,
                    line2,
                    city,
                    region,
                    zip_code
                FROM customer_saved_address
                WHERE user_id = ?
                ORDER BY created_at, id
                """.trimIndent(),
            ).use { statement ->
                statement.setObject(1, userId)

                statement.executeQuery().use { results ->
                    buildList {
                        while (results.next()) {
                            add(
                                SavedAddressResponse(
                                    id = results.getObject(
                                        "id",
                                        UUID::class.java,
                                    ).toString(),
                                    label = results.getString("label"),
                                    line1 = results.getString("line1"),
                                    line2 = results.getString("line2"),
                                    city = results.getString("city"),
                                    region = results.getString("region"),
                                    zipCode = results.getString("zip_code"),
                                ),
                            )
                        }
                    }
                }
            }
        }

    override fun saveUpload(upload: ProfileUploadRecord) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                INSERT INTO profile_photo_upload (
                    id,
                    user_id,
                    object_key,
                    content_type,
                    size_bytes,
                    status,
                    created_at,
                    expires_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET
                    object_key = EXCLUDED.object_key,
                    content_type = EXCLUDED.content_type,
                    size_bytes = EXCLUDED.size_bytes,
                    status = EXCLUDED.status,
                    expires_at = EXCLUDED.expires_at
                """.trimIndent(),
            ).use { statement ->
                statement.setObject(1, upload.id)
                statement.setObject(2, upload.userId)
                statement.setString(3, upload.objectKey)
                statement.setString(4, upload.contentType)
                statement.setLong(5, upload.sizeBytes)
                statement.setString(6, upload.status)
                statement.setTimestamp(7, Timestamp.from(Instant.now()))
                statement.setTimestamp(8, Timestamp.from(upload.expiresAt))
                statement.executeUpdate()
            }
        }
    }

    override fun findUpload(id: UUID): ProfileUploadRecord? =
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                SELECT
                    id,
                    user_id,
                    content_type,
                    size_bytes,
                    object_key,
                    expires_at,
                    status
                FROM profile_photo_upload
                WHERE id = ?
                """.trimIndent(),
            ).use { statement ->
                statement.setObject(1, id)

                statement.executeQuery().use { results ->
                    if (results.next()) {
                        ProfileUploadRecord(
                            id = results.getObject(
                                "id",
                                UUID::class.java,
                            ),
                            userId = results.getObject(
                                "user_id",
                                UUID::class.java,
                            ),
                            contentType = results.getString("content_type"),
                            sizeBytes = results.getLong("size_bytes"),
                            objectKey = results.getString("object_key"),
                            expiresAt = results.getTimestamp(
                                "expires_at",
                            ).toInstant(),
                            status = results.getString("status"),
                        )
                    } else {
                        null
                    }
                }
            }
        }

    override fun updateUpload(upload: ProfileUploadRecord) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                UPDATE profile_photo_upload
                SET
                    object_key = ?,
                    content_type = ?,
                    size_bytes = ?,
                    status = ?,
                    expires_at = ?,
                    completed_at = ?
                WHERE id = ?
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, upload.objectKey)
                statement.setString(2, upload.contentType)
                statement.setLong(3, upload.sizeBytes)
                statement.setString(4, upload.status)
                statement.setTimestamp(5, Timestamp.from(upload.expiresAt))
                statement.setTimestamp(
                    6,
                    if (upload.status == "COMPLETED") {
                        Timestamp.from(Instant.now())
                    } else {
                        null
                    },
                )
                statement.setObject(7, upload.id)

                check(statement.executeUpdate() == 1) {
                    "Customer profile upload update failed"
                }
            }
        }
    }

    override fun createExportRequest(
        userId: UUID,
        at: Instant,
    ): UUID {
        val id = UUID.randomUUID()

        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                INSERT INTO customer_data_export_request (
                    id,
                    user_id,
                    status,
                    requested_at
                )
                VALUES (?, ?, 'REQUESTED', ?)
                """.trimIndent(),
            ).use { statement ->
                statement.setObject(1, id)
                statement.setObject(2, userId)
                statement.setTimestamp(3, Timestamp.from(at))
                statement.executeUpdate()
            }
        }

        return id
    }

    override fun createDeletionRequest(
        userId: UUID,
        at: Instant,
    ): UUID {
        val id = UUID.randomUUID()

        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                INSERT INTO customer_account_deletion_request (
                    id,
                    user_id,
                    status,
                    requested_at,
                    active_booking_blocker
                )
                VALUES (?, ?, 'DELETION_REQUESTED', ?, FALSE)
                """.trimIndent(),
            ).use { statement ->
                statement.setObject(1, id)
                statement.setObject(2, userId)
                statement.setTimestamp(3, Timestamp.from(at))
                statement.executeUpdate()
            }
        }

        return id
    }

    override fun close() {
        dataSource.close()
    }

    private fun readPreferences(
        connection: Connection,
        userId: UUID,
    ): Set<String> =
        connection.prepareStatement(
            """
            SELECT preference
            FROM customer_technology_preference
            WHERE user_id = ?
            ORDER BY preference
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, userId)

            statement.executeQuery().use { results ->
                buildSet {
                    while (results.next()) {
                        add(results.getString("preference"))
                    }
                }
            }
        }

    private fun ResultSet.toCustomerProfileRecord() =
        CustomerProfileRecord(
            userId = getObject("user_id", UUID::class.java),
            firstName = getString("first_name"),
            lastName = getString("last_name"),
            displayName = getString("public_display_name"),
            zipCode = getString("zip_code"),
            photoUrl = getString("profile_photo_object_key"),
            locationPermission = getString(
                "location_permission_status",
            ),
            notificationPermission = getString(
                "notification_permission_status",
            ),
            notificationsEnabled = getBoolean(
                "notifications_enabled",
            ),
            followSystemTextSize = getBoolean(
                "follow_system_text_size",
            ),
            extraLargeText = getBoolean("extra_large_text"),
            highContrast = getBoolean("high_contrast"),
            reducedMotion = getBoolean("reduced_motion"),
            simplifiedInstructions = getBoolean(
                "simplified_instructions",
            ),
            biometricUnlockEnabled = getBoolean(
                "biometric_unlock_enabled",
            ),
            completedAt = getTimestamp(
                "onboarding_completed_at",
            ).toInstant(),
        )
}
