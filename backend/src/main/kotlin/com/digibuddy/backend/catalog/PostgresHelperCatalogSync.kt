@file:Suppress(
    "LongMethod",
    "TooManyFunctions",
)

package com.digibuddy.backend.catalog

import com.digibuddy.backend.helper.HelperCatalogApplicationSnapshot
import com.digibuddy.shared.contracts.HelperAccountStatus
import com.digibuddy.shared.core.DigibuddyPricing
import java.sql.Connection
import java.sql.DriverManager
import java.util.UUID

class PostgresHelperCatalogSync(
    private val jdbcUrl: String,
    private val username: String,
    private val password: String,
) {
    fun upsertApprovedHelper(
        userId: UUID,
        snapshot: HelperCatalogApplicationSnapshot,
    ) {
        DriverManager.getConnection(
            jdbcUrl,
            username,
            password,
        ).use { connection ->
            connection.autoCommit = false

            try {
                syncApprovedHelper(
                    connection,
                    userId,
                    snapshot,
                )

                connection.commit()
            } catch (cause: Exception) {
                connection.rollback()
                throw cause
            } finally {
                connection.autoCommit = true
            }
        }
    }

    fun updateHelperStatus(
        userId: UUID,
        status: HelperAccountStatus,
    ) {
        DriverManager.getConnection(
            jdbcUrl,
            username,
            password,
        ).use { connection ->
            connection.autoCommit = false

            try {
                val catalogStatus =
                    catalogStatus(status)

                connection.prepareStatement(
                    """
                    UPDATE helper_profile
                    SET
                        account_status = ?,
                        approval_status = ?,
                        catalog_visible = ?,
                        updated_at = NOW()
                    WHERE user_id = ?
                    """.trimIndent(),
                ).use { statement ->
                    statement.setString(
                        1,
                        catalogStatus.accountStatus,
                    )

                    statement.setString(
                        2,
                        catalogStatus.approvalStatus,
                    )

                    statement.setBoolean(
                        3,
                        catalogStatus.visible,
                    )

                    statement.setObject(
                        4,
                        userId,
                    )

                    statement.executeUpdate()
                }

                connection.prepareStatement(
                    """
                    UPDATE helper_availability_summary
                    SET
                        accepting_new_customers = ?,
                        updated_at = NOW()
                    WHERE helper_id = (
                        SELECT id
                        FROM helper_profile
                        WHERE user_id = ?
                    )
                    """.trimIndent(),
                ).use { statement ->
                    statement.setBoolean(
                        1,
                        catalogStatus.acceptingRequests,
                    )

                    statement.setObject(
                        2,
                        userId,
                    )

                    statement.executeUpdate()
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

    private fun syncApprovedHelper(
        connection: Connection,
        userId: UUID,
        snapshot: HelperCatalogApplicationSnapshot,
    ) {
        val profile =
            snapshot.publicProfile

        val helperId =
            findExistingHelperId(
                connection,
                userId,
            ) ?: UUID.nameUUIDFromBytes(
                "digibuddy-helper-catalog:$userId"
                    .toByteArray(),
            )

        val mode =
            profile.serviceMode
                ?.uppercase()
                ?: "REMOTE"

        val remote =
            mode != "IN_PERSON"

        val inPerson =
            mode != "REMOTE"

        val displayName =
            profile.displayName
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: "New Digibuddy Helper"

        val headline =
            profile.headline
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: "Patient technology help"

        val biography =
            profile.biography
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: "An approved Digibuddy helper."

        connection.prepareStatement(
            """
            INSERT INTO helper_profile (
                id,
                user_id,
                display_name,
                headline,
                biography,
                profile_photo_url,
                account_status,
                approval_status,
                verification_status,
                catalog_visible,
                seed_data,
                created_at,
                updated_at
            )
            VALUES (
                ?, ?, ?, ?, ?, ?,
                'ACTIVE',
                'APPROVED',
                'VERIFIED',
                TRUE,
                FALSE,
                NOW(),
                NOW()
            )
            ON CONFLICT (id)
            DO UPDATE SET
                user_id = EXCLUDED.user_id,
                display_name = EXCLUDED.display_name,
                headline = EXCLUDED.headline,
                biography = EXCLUDED.biography,
                profile_photo_url =
                    EXCLUDED.profile_photo_url,
                account_status = 'ACTIVE',
                approval_status = 'APPROVED',
                verification_status = 'VERIFIED',
                catalog_visible = TRUE,
                seed_data = FALSE,
                updated_at = NOW()
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(
                1,
                helperId,
            )

            statement.setObject(
                2,
                userId,
            )

            statement.setString(
                3,
                displayName,
            )

            statement.setString(
                4,
                headline,
            )

            statement.setString(
                5,
                biography,
            )

            statement.setString(
                6,
                profile.profilePictureUrl,
            )

            statement.executeUpdate()
        }

        replaceServices(
            connection = connection,
            helperId = helperId,
            requestedServices =
                profile.services,
            remote = remote,
            inPerson = inPerson,
        )

        replaceSkills(
            connection = connection,
            helperId = helperId,
            requestedSkills =
                profile.skills,
            yearsExperience =
                profile.yearsExperience
                    ?.coerceIn(0, 80)
                    ?: 0,
        )

        replaceLanguages(
            connection = connection,
            helperId = helperId,
            requestedLanguages =
                profile.languages,
        )

        replaceServiceArea(
            connection = connection,
            helperId = helperId,
            zipCode = snapshot.homeZip,
            inPerson = inPerson,
        )

        upsertAvailability(
            connection,
            helperId,
        )

        ensurePerformanceSummary(
            connection,
            helperId,
        )
    }

    private fun replaceServices(
        connection: Connection,
        helperId: UUID,
        requestedServices: List<String>,
        remote: Boolean,
        inPerson: Boolean,
    ) {
        connection.prepareStatement(
            """
            DELETE FROM helper_service
            WHERE helper_id = ?
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(
                1,
                helperId,
            )
            statement.executeUpdate()
        }

        val categories =
            requestedServices
                .map(String::trim)
                .filter(String::isNotBlank)
                .distinct()
                .mapNotNull { slug ->
                    findCategory(
                        connection,
                        slug,
                    )
                }
                .ifEmpty {
                    listOfNotNull(
                        findCategory(
                            connection,
                            "other",
                        ),
                    )
                }

        val price =
            DigibuddyPricing
                .startingPriceCents(
                    remote,
                    inPerson,
                )

        categories.forEach {
                category ->

            val serviceId =
                UUID.nameUUIDFromBytes(
                    "helper-service:$helperId:${category.first}"
                        .toByteArray(),
                )

            connection.prepareStatement(
                """
                INSERT INTO helper_service (
                    id,
                    helper_id,
                    category_id,
                    starting_price_cents,
                    currency,
                    remote_service,
                    in_person_service,
                    active
                )
                VALUES (
                    ?, ?, ?, ?, 'USD', ?, ?, TRUE
                )
                """.trimIndent(),
            ).use { statement ->
                statement.setObject(
                    1,
                    serviceId,
                )

                statement.setObject(
                    2,
                    helperId,
                )

                statement.setObject(
                    3,
                    category.first,
                )

                statement.setInt(
                    4,
                    price,
                )

                statement.setBoolean(
                    5,
                    remote,
                )

                statement.setBoolean(
                    6,
                    inPerson,
                )

                statement.executeUpdate()
            }
        }
    }

    private fun replaceSkills(
        connection: Connection,
        helperId: UUID,
        requestedSkills: List<String>,
        yearsExperience: Int,
    ) {
        connection.prepareStatement(
            """
            DELETE FROM helper_skill
            WHERE helper_id = ?
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(
                1,
                helperId,
            )
            statement.executeUpdate()
        }

        requestedSkills
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()
            .mapNotNull { slug ->
                findSkill(
                    connection,
                    slug,
                )
            }
            .forEach { skillId ->
                connection.prepareStatement(
                    """
                    INSERT INTO helper_skill (
                        helper_id,
                        skill_id,
                        years_experience
                    )
                    VALUES (?, ?, ?)
                    """.trimIndent(),
                ).use { statement ->
                    statement.setObject(
                        1,
                        helperId,
                    )

                    statement.setObject(
                        2,
                        skillId,
                    )

                    statement.setInt(
                        3,
                        yearsExperience,
                    )

                    statement.executeUpdate()
                }
            }
    }

    private fun replaceLanguages(
        connection: Connection,
        helperId: UUID,
        requestedLanguages: List<String>,
    ) {
        connection.prepareStatement(
            """
            DELETE FROM helper_language
            WHERE helper_id = ?
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(
                1,
                helperId,
            )
            statement.executeUpdate()
        }

        val languages =
            requestedLanguages
                .map(String::trim)
                .filter(String::isNotBlank)
                .distinct()
                .filter {
                    languageExists(
                        connection,
                        it,
                    )
                }
                .ifEmpty {
                    if (
                        languageExists(
                            connection,
                            "en",
                        )
                    ) {
                        listOf("en")
                    } else {
                        emptyList()
                    }
                }

        languages.forEach {
                languageCode ->

            connection.prepareStatement(
                """
                INSERT INTO helper_language (
                    helper_id,
                    language_code
                )
                VALUES (?, ?)
                """.trimIndent(),
            ).use { statement ->
                statement.setObject(
                    1,
                    helperId,
                )

                statement.setString(
                    2,
                    languageCode,
                )

                statement.executeUpdate()
            }
        }
    }

    private fun replaceServiceArea(
        connection: Connection,
        helperId: UUID,
        zipCode: String,
        inPerson: Boolean,
    ) {
        connection.prepareStatement(
            """
            DELETE FROM helper_service_area
            WHERE helper_id = ?
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(
                1,
                helperId,
            )
            statement.executeUpdate()
        }

        if (
            !zipExists(
                connection,
                zipCode,
            )
        ) {
            return
        }

        val areaId =
            UUID.nameUUIDFromBytes(
                "helper-service-area:$helperId"
                    .toByteArray(),
            )

        val inserted =
            connection.prepareStatement(
                """
                INSERT INTO helper_service_area (
                    id,
                    helper_id,
                    name,
                    origin,
                    service_radius_miles,
                    active
                )
                SELECT
                    ?,
                    ?,
                    ?,
                    centroid,
                    ?,
                    TRUE
                FROM zip_code_location
                WHERE zip_code = ?
                """.trimIndent(),
            ).use { statement ->
                statement.setObject(
                    1,
                    areaId,
                )

                statement.setObject(
                    2,
                    helperId,
                )

                statement.setString(
                    3,
                    "Service area near $zipCode",
                )

                if (inPerson) {
                    statement.setDouble(
                        4,
                        25.0,
                    )
                } else {
                    statement.setNull(
                        4,
                        java.sql.Types.NUMERIC,
                    )
                }

                statement.setString(
                    5,
                    zipCode,
                )

                statement.executeUpdate()
            }

        if (inserted == 0) {
            return
        }

        connection.prepareStatement(
            """
            INSERT INTO helper_service_zip_code (
                service_area_id,
                zip_code
            )
            VALUES (?, ?)
            ON CONFLICT DO NOTHING
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(
                1,
                areaId,
            )

            statement.setString(
                2,
                zipCode,
            )

            statement.executeUpdate()
        }
    }

    private fun upsertAvailability(
        connection: Connection,
        helperId: UUID,
    ) {
        connection.prepareStatement(
            """
            INSERT INTO helper_availability_summary (
                helper_id,
                status,
                next_available_at,
                available_within_days,
                accepting_new_customers,
                updated_at
            )
            VALUES (
                ?,
                'AVAILABLE_THIS_WEEK',
                NULL,
                7,
                TRUE,
                NOW()
            )
            ON CONFLICT (helper_id)
            DO UPDATE SET
                status =
                    EXCLUDED.status,
                available_within_days =
                    EXCLUDED.available_within_days,
                accepting_new_customers = TRUE,
                updated_at = NOW()
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(
                1,
                helperId,
            )

            statement.executeUpdate()
        }
    }

    private fun ensurePerformanceSummary(
        connection: Connection,
        helperId: UUID,
    ) {
        connection.prepareStatement(
            """
            INSERT INTO helper_performance_summary (
                helper_id,
                average_rating,
                review_count,
                completed_job_count,
                median_response_time_minutes,
                updated_at
            )
            VALUES (
                ?,
                0,
                0,
                0,
                0,
                NOW()
            )
            ON CONFLICT (helper_id)
            DO NOTHING
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(
                1,
                helperId,
            )

            statement.executeUpdate()
        }
    }

    private fun findExistingHelperId(
        connection: Connection,
        userId: UUID,
    ): UUID? =
        connection.prepareStatement(
            """
            SELECT id
            FROM helper_profile
            WHERE user_id = ?
            LIMIT 1
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(
                1,
                userId,
            )

            statement.executeQuery().use {
                    results ->
                if (results.next()) {
                    results.getObject(
                        "id",
                        UUID::class.java,
                    )
                } else {
                    null
                }
            }
        }

    private fun findCategory(
        connection: Connection,
        slug: String,
    ): Pair<UUID, String>? =
        connection.prepareStatement(
            """
            SELECT id, slug
            FROM service_category
            WHERE slug = ?
              AND active = TRUE
            """.trimIndent(),
        ).use { statement ->
            statement.setString(
                1,
                slug,
            )

            statement.executeQuery().use {
                    results ->
                if (results.next()) {
                    results.getObject(
                        "id",
                        UUID::class.java,
                    ) to
                        results.getString(
                            "slug",
                        )
                } else {
                    null
                }
            }
        }

    private fun findSkill(
        connection: Connection,
        slug: String,
    ): UUID? =
        connection.prepareStatement(
            """
            SELECT id
            FROM skill
            WHERE slug = ?
              AND active = TRUE
            """.trimIndent(),
        ).use { statement ->
            statement.setString(
                1,
                slug,
            )

            statement.executeQuery().use {
                    results ->
                if (results.next()) {
                    results.getObject(
                        "id",
                        UUID::class.java,
                    )
                } else {
                    null
                }
            }
        }

    private fun languageExists(
        connection: Connection,
        code: String,
    ): Boolean =
        connection.prepareStatement(
            """
            SELECT EXISTS (
                SELECT 1
                FROM language
                WHERE code = ?
                  AND active = TRUE
            )
            """.trimIndent(),
        ).use { statement ->
            statement.setString(
                1,
                code,
            )

            statement.executeQuery().use {
                    results ->
                results.next()
                results.getBoolean(1)
            }
        }

    private fun zipExists(
        connection: Connection,
        zipCode: String,
    ): Boolean =
        connection.prepareStatement(
            """
            SELECT EXISTS (
                SELECT 1
                FROM zip_code_location
                WHERE zip_code = ?
            )
            """.trimIndent(),
        ).use { statement ->
            statement.setString(
                1,
                zipCode,
            )

            statement.executeQuery().use {
                    results ->
                results.next()
                results.getBoolean(1)
            }
        }

    private fun catalogStatus(
        status: HelperAccountStatus,
    ): CatalogStatus =
        when (status) {
            HelperAccountStatus.APPROVED ->
                CatalogStatus(
                    accountStatus = "ACTIVE",
                    approvalStatus = "APPROVED",
                    visible = true,
                    acceptingRequests = true,
                )

            HelperAccountStatus.PAUSED_BY_HELPER ->
                CatalogStatus(
                    accountStatus = "ACTIVE",
                    approvalStatus = "APPROVED",
                    visible = false,
                    acceptingRequests = false,
                )

            HelperAccountStatus.SUSPENDED ->
                CatalogStatus(
                    accountStatus = "SUSPENDED",
                    approvalStatus = "SUSPENDED",
                    visible = false,
                    acceptingRequests = false,
                )

            HelperAccountStatus.REJECTED ->
                CatalogStatus(
                    accountStatus = "ACTIVE",
                    approvalStatus = "REJECTED",
                    visible = false,
                    acceptingRequests = false,
                )

            else ->
                CatalogStatus(
                    accountStatus = "ACTIVE",
                    approvalStatus = "PENDING",
                    visible = false,
                    acceptingRequests = false,
                )
        }

    private data class CatalogStatus(
        val accountStatus: String,
        val approvalStatus: String,
        val visible: Boolean,
        val acceptingRequests: Boolean,
    )
}
