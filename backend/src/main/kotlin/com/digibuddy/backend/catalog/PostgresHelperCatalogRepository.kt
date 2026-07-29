@file:Suppress("LongMethod", "NestedBlockDepth", "TooManyFunctions")

package com.digibuddy.backend.catalog

import com.digibuddy.shared.contracts.ServiceCategoryResponse
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.sql.ResultSet
import java.util.UUID

class PostgresHelperCatalogRepository(jdbcUrl: String, username: String, password: String) : HelperCatalogRepository {
    private val dataSource = HikariDataSource(
        HikariConfig().apply {
            this.jdbcUrl = jdbcUrl
            this.username = username
            this.password = password
            maximumPoolSize = 6
            isAutoCommit = true
        },
    )

    override fun zipLocation(zipCode: String): GeoPoint? = dataSource.connection.use { connection ->
        connection.prepareStatement(
            "SELECT ST_Y(centroid::geometry), ST_X(centroid::geometry) FROM zip_code_location WHERE zip_code = ?",
        ).use { statement ->
            statement.setString(1, zipCode)
            statement.executeQuery().use { results ->
                if (results.next()) GeoPoint(results.getDouble(1), results.getDouble(2)) else null
            }
        }
    }

    override fun categories(): List<ServiceCategoryResponse> = dataSource.connection.use { connection ->
        connection.prepareStatement(
            "SELECT id, slug, name, description FROM service_category WHERE active ORDER BY display_order, id",
        ).use { statement ->
            statement.executeQuery().use { results ->
                buildList {
                    while (results.next()) {
                        add(
                            ServiceCategoryResponse(
                                results.getObject("id", UUID::class.java).toString(),
                                results.getString("slug"),
                                results.getString("name"),
                                results.getString("description"),
                            ),
                        )
                    }
                }
            }
        }
    }

    override fun helpers(zipCode: String?): List<CatalogHelper> = dataSource.connection.use { connection ->
        connection.prepareStatement(HELPERS_SQL).use { statement ->
            statement.setString(1, zipCode)
            statement.setString(2, zipCode)
            statement.executeQuery().use(::readHelpers)
        }
    }

    override fun lifecycle(userId: UUID): HelperLifecycleSnapshot? = dataSource.connection.use { connection ->
        connection.prepareStatement(
            "SELECT account_status, approval_status, display_name FROM helper_profile WHERE user_id = ?",
        ).use { statement ->
            statement.setObject(1, userId)
            statement.executeQuery().use { results ->
                if (results.next()) {
                    HelperLifecycleSnapshot(
                        accountStatus = results.getString("account_status"),
                        approvalStatus = results.getString("approval_status"),
                        displayName = results.getString("display_name"),
                    )
                } else {
                    null
                }
            }
        }
    }

    override fun accountReference(helperId: UUID): HelperAccountReference? = dataSource.connection.use { connection ->
        connection.prepareStatement(
            "SELECT user_id, display_name FROM helper_profile WHERE id = ? AND seed_data = FALSE",
        ).use { statement ->
            statement.setObject(1, helperId)
            statement.executeQuery().use { results ->
                if (results.next()) {
                    HelperAccountReference(
                        results.getObject("user_id", UUID::class.java),
                        results.getString("display_name"),
                    )
                } else {
                    null
                }
            }
        }
    }

    private fun readHelpers(results: ResultSet): List<CatalogHelper> {
        val helpers = linkedMapOf<UUID, HelperAccumulator>()
        while (results.next()) {
            val id = results.getObject("helper_id", UUID::class.java)
            val helper = helpers.getOrPut(id) { results.newAccumulator(id) }
            results.getString("category_slug")?.let { slug ->
                helper.services[slug] = CatalogService(
                    slug,
                    results.getInt("starting_price_cents"),
                    results.getBoolean("remote_service"),
                    results.getBoolean("in_person_service"),
                )
            }
            results.getString("skill_slug")?.let { helper.skills[it] = results.getInt("years_experience") }
            results.getString("language_code")?.let(helper.languages::add)
            results.getObject("area_id", UUID::class.java)?.let { areaId ->
                val area = helper.areas.getOrPut(areaId) {
                    AreaAccumulator(
                        GeoPoint(results.getDouble("area_latitude"), results.getDouble("area_longitude")),
                        results.getDouble("service_radius_miles").takeUnless { results.wasNull() },
                    )
                }
                results.getString("service_zip")?.let(area.zips::add)
            }
        }
        return helpers.values.map(HelperAccumulator::build)
    }

    private fun ResultSet.newAccumulator(id: UUID) = HelperAccumulator(
        id = id,
        displayName = getString("display_name"),
        headline = getString("headline"),
        biography = getString("biography"),
        profilePictureUrl = getString("profile_photo_url"),
        accountStatus = getString("account_status"),
        approvalStatus = getString("approval_status"),
        verificationStatus = getString("verification_status"),
        catalogVisible = getBoolean("catalog_visible"),
        availability = CatalogAvailability(
            getString("availability_status"),
            getTimestamp("next_available_at")?.toInstant(),
            getInt("available_within_days").takeUnless { wasNull() },
            getBoolean("accepting_new_customers"),
        ),
        rating = getDouble("average_rating"),
        reviewCount = getInt("review_count"),
        completedJobs = getInt("completed_job_count"),
        responseMinutes = getInt("median_response_time_minutes"),
    )

    private data class AreaAccumulator(
        val origin: GeoPoint,
        val radius: Double?,
        val zips: MutableSet<String> = linkedSetOf(),
    )

    private data class HelperAccumulator(
        val id: UUID,
        val displayName: String,
        val headline: String,
        val biography: String,
        val profilePictureUrl: String?,
        val accountStatus: String,
        val approvalStatus: String,
        val verificationStatus: String,
        val catalogVisible: Boolean,
        val availability: CatalogAvailability,
        val rating: Double,
        val reviewCount: Int,
        val completedJobs: Int,
        val responseMinutes: Int,
        val skills: MutableMap<String, Int> = linkedMapOf(),
        val services: MutableMap<String, CatalogService> = linkedMapOf(),
        val areas: MutableMap<UUID, AreaAccumulator> = linkedMapOf(),
        val languages: MutableSet<String> = linkedSetOf(),
    ) {
        fun build() = CatalogHelper(
            id, displayName, headline, biography, profilePictureUrl, accountStatus, approvalStatus,
            verificationStatus, catalogVisible, skills, services.values.toList(),
            areas.values.map { CatalogServiceArea(it.origin, it.radius, it.zips) },
            languages, availability, rating, reviewCount, completedJobs, responseMinutes,
        )
    }

    private companion object {
        val HELPERS_SQL = """
            SELECT h.id AS helper_id, h.display_name, h.headline, h.biography, h.profile_photo_url,
                   h.account_status, h.approval_status, h.verification_status, h.catalog_visible,
                   c.slug AS category_slug, hs.starting_price_cents, hs.remote_service, hs.in_person_service,
                   sk.slug AS skill_slug, hsk.years_experience, hl.language_code,
                   area.id AS area_id, ST_Y(area.origin::geometry) AS area_latitude,
                   ST_X(area.origin::geometry) AS area_longitude, area.service_radius_miles,
                   area_zip.zip_code AS service_zip, availability.status AS availability_status,
                   availability.next_available_at, availability.available_within_days,
                   availability.accepting_new_customers, performance.average_rating,
                   performance.review_count, performance.completed_job_count,
                   performance.median_response_time_minutes
            FROM helper_profile h
            JOIN helper_availability_summary availability ON availability.helper_id = h.id
            JOIN helper_performance_summary performance ON performance.helper_id = h.id
            LEFT JOIN helper_service hs ON hs.helper_id = h.id AND hs.active
            LEFT JOIN service_category c ON c.id = hs.category_id AND c.active
            LEFT JOIN helper_skill hsk ON hsk.helper_id = h.id
            LEFT JOIN skill sk ON sk.id = hsk.skill_id AND sk.active
            LEFT JOIN helper_language hl ON hl.helper_id = h.id
            LEFT JOIN helper_service_area area ON area.helper_id = h.id AND area.active
            LEFT JOIN helper_service_zip_code area_zip ON area_zip.service_area_id = area.id
            WHERE h.seed_data = FALSE AND (CAST(? AS VARCHAR) IS NULL OR EXISTS (
                SELECT 1 FROM helper_service remote_service
                WHERE remote_service.helper_id = h.id AND remote_service.active AND remote_service.remote_service
            ) OR EXISTS (
                SELECT 1 FROM helper_service_area matched_area
                JOIN zip_code_location target_zip ON target_zip.zip_code = ?
                WHERE matched_area.helper_id = h.id AND matched_area.active AND (
                    EXISTS (SELECT 1 FROM helper_service_zip_code exact_zip
                            WHERE exact_zip.service_area_id = matched_area.id
                              AND exact_zip.zip_code = target_zip.zip_code)
                    OR (matched_area.service_radius_miles IS NOT NULL AND
                        ST_DWithin(matched_area.origin, target_zip.centroid,
                                   matched_area.service_radius_miles * 1609.344))
                )
            ))
            ORDER BY h.id, c.slug, sk.slug, hl.language_code, area.id, area_zip.zip_code
        """.trimIndent()
    }
}
