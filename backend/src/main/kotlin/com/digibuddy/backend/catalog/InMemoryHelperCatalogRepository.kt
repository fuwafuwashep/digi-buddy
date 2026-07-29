@file:Suppress("LongParameterList")

package com.digibuddy.backend.catalog

import com.digibuddy.backend.helper.HelperCatalogApplicationSnapshot
import com.digibuddy.shared.contracts.HelperAccountStatus
import com.digibuddy.shared.contracts.ServiceCategoryResponse
import com.digibuddy.shared.core.DigibuddyPricing
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

class InMemoryHelperCatalogRepository(includeFictionalSeeds: Boolean = false) : HelperCatalogRepository {
    private val zipPoints = mutableMapOf(
        "60601" to GeoPoint(41.8864, -87.6229),
        "60614" to GeoPoint(41.9227, -87.6505),
        "60618" to GeoPoint(41.9464, -87.7037),
        "60640" to GeoPoint(41.9722, -87.6622),
        "60657" to GeoPoint(41.9400, -87.6533),
        "60201" to GeoPoint(42.0562, -87.6947),
        "60302" to GeoPoint(41.8930, -87.7895),
        "60540" to GeoPoint(41.7662, -88.1473),
        "32539" to GeoPoint(30.7621, -86.5705),
    )

    private val categoryData = CATEGORY_NAMES.mapIndexed { index, (slug, name) ->
        ServiceCategoryResponse(
            UUID.nameUUIDFromBytes(slug.toByteArray()).toString(),
            slug,
            name,
            "Help with ${name.lowercase()}.",
        )
    }

    private val helperOwners = mutableMapOf<UUID, UUID>()
    private val helperData = CopyOnWriteArrayList(
        if (includeFictionalSeeds) {
            listOf(
                helper(
                    1, "Maya Rowan", "Patient help for everyday devices", "60657", 6.0, 4.92, 84, 126, 18, 1,
                    mapOf("iphone-ipad" to 9, "android" to 8), setOf("en", "es"),
                    service("phone-tablet-help", 4500, true, true), service("technology-lessons", 4000, true, true),
                ),
                helper(
                    2, "Elias Park", "Home internet and computer setup", "60302", 10.0, 4.78, 51, 93, 32, 0,
                    mapOf("windows" to 12, "home-networking" to 10, "printers" to 9), setOf("en", "pl"),
                    service("wifi-internet", 6500, false, true), service("printer-setup", 5500, false, true),
                ),
                helper(
                    3, "Nora Bell", "Friendly phone, tablet, and account lessons", "60201", 5.0, 4.98, 37, 61, 12, 1,
                    mapOf("iphone-ipad" to 7), setOf("en", "zh-Hans"),
                    service("email-accounts", 3500, true, true), service("technology-lessons", 3000, true, true),
                ),
                helper(
                    4, "Theo Linden", "Streaming and smart-home guidance", "60640", 8.0, 4.65, 22, 48, 44, 4,
                    mapOf("streaming" to 8, "smart-home" to 6), setOf("en"),
                    service("smart-tv-streaming", 5000, true, true), service("smart-home", 6000, false, true),
                    verification = "PENDING",
                ),
                helper(
                    5, "Samira Vale", "Online safety and backup specialist", "60601", 12.0, 4.88, 68, 109, 20, 6,
                    mapOf("online-safety" to 11, "backup" to 10), setOf("en", "fr"),
                    service("password-security", 7000, true, true), service("data-transfer-backup", 7500, true, true),
                ),
                helper(
                    6, "Jonah Reed", "Flexible remote technology help", "60540", 4.0, 4.55, 19, 77, 8, 0,
                    mapOf("windows" to 15, "macos" to 13), setOf("en"),
                    service("computer-help", 2500, true, false), service("software-installation", 3000, true, false),
                ),
                helper(
                    7, "Lina Marsh", "New helper awaiting approval", "60614", 20.0, 5.0, 2, 3, 15, 0,
                    mapOf(
                        "windows" to 3,
                    ),
                    setOf("en"),
                    service("computer-help", 1000, true, true),
                    approval = "PENDING",
                ),
                helper(
                    8, "Owen Lake", "Unavailable catalog fixture", "60614", 20.0, 4.7, 30, 55, 25, 0,
                    mapOf(
                        "windows" to 6,
                    ),
                    setOf("en"),
                    service("computer-help", 1000, true, true),
                    account = "SUSPENDED",
                ),
            )
        } else {
            emptyList()
        },
    )

    override fun zipLocation(zipCode: String) = zipPoints[zipCode]
    override fun helpers(zipCode: String?) = helperData
    override fun categories() = categoryData
    override fun accountReference(helperId: UUID): HelperAccountReference? = helperOwners[helperId]?.let { userId ->
        helperData.firstOrNull { it.id == helperId }?.let { helper ->
            HelperAccountReference(userId, helper.displayName)
        }
    }

    @Synchronized
    fun upsertApprovedHelper(userId: UUID, snapshot: HelperCatalogApplicationSnapshot) {
        val profile = snapshot.publicProfile
        val zip = snapshot.homeZip.takeIf { it.matches(Regex("^[0-9]{5}$")) } ?: return
        val catalogId = UUID.nameUUIDFromBytes("digibuddy-helper-catalog:$userId".toByteArray())
        val origin = zipPoints.getOrPut(zip) { GeoPoint(0.0, 0.0) }
        val mode = profile.serviceMode ?: "REMOTE"
        val remote = mode != "IN_PERSON"
        val inPerson = mode != "REMOTE"
        val allowedCategories = categoryData.mapTo(mutableSetOf()) { it.slug }
        val services = profile.services.filter { it in allowedCategories }.ifEmpty { listOf("other") }
        val years = profile.yearsExperience?.coerceIn(0, 80) ?: 0
        val helper = CatalogHelper(
            id = catalogId,
            displayName = profile.displayName ?: "New Digibuddy Helper",
            headline = profile.headline ?: "Patient technology help",
            biography = profile.biography ?: "An approved Digibuddy helper.",
            profilePictureUrl = profile.profilePictureUrl,
            skills = profile.skills.associateWith { years },
            services = services.map {
                CatalogService(it, DigibuddyPricing.startingPriceCents(remote, inPerson), remote, inPerson)
            },
            serviceAreas = listOf(CatalogServiceArea(origin, if (inPerson) 25.0 else null, setOf(zip))),
            languages = profile.languages.ifEmpty { listOf("en") }.toSet(),
            availability = CatalogAvailability("AVAILABLE_THIS_WEEK", null, 7, true),
            rating = 0.0,
            reviewCount = 0,
            completedJobs = 0,
            responseMinutes = 0,
            serviceAreaDescription = profile.serviceAreaSummary,
            certifications = profile.certifications,
            portfolioLinks = profile.portfolioLinks,
        )
        helperData.removeIf { it.id == catalogId }
        helperData += helper
        helperOwners[catalogId] = userId
    }

    @Synchronized
    fun updateHelperStatus(userId: UUID, status: HelperAccountStatus) {
        val catalogId = UUID.nameUUIDFromBytes("digibuddy-helper-catalog:$userId".toByteArray())
        val existing = helperData.firstOrNull { it.id == catalogId } ?: return
        helperData.remove(existing)
        helperData += existing.copy(
            approvalStatus = status.name,
            catalogVisible = status == HelperAccountStatus.APPROVED,
        )
    }

    private fun helper(
        number: Int,
        name: String,
        headline: String,
        zip: String,
        radius: Double,
        rating: Double,
        reviews: Int,
        jobs: Int,
        response: Int,
        availableDays: Int,
        skills: Map<String, Int>,
        languages: Set<String>,
        vararg services: CatalogService,
        approval: String = "APPROVED",
        account: String = "ACTIVE",
        verification: String = "VERIFIED",
    ) = CatalogHelper(
        UUID.fromString("10000000-0000-0000-0000-${number.toString().padStart(12, '0')}"),
        name,
        headline,
        "$name is a fictional Digibuddy seed helper who uses simple, patient instructions.",
        accountStatus = account,
        approvalStatus = approval,
        verificationStatus = verification,
        skills = skills,
        services = services.toList(),
        serviceAreas = listOf(CatalogServiceArea(zipPoints.getValue(zip), radius, setOf(zip))),
        languages = languages,
        availability = CatalogAvailability(
            if (availableDays == 0) "AVAILABLE_TODAY" else "AVAILABLE_THIS_WEEK",
            Instant.parse("2026-07-${(17 + availableDays).coerceAtMost(28).toString().padStart(2, '0')}T15:00:00Z"),
            availableDays,
            true,
        ),
        rating = rating,
        reviewCount = reviews,
        completedJobs = jobs,
        responseMinutes = response,
    )

    @Suppress("UNUSED_PARAMETER")
    private fun service(category: String, price: Int, remote: Boolean, inPerson: Boolean) =
        CatalogService(category, DigibuddyPricing.startingPriceCents(remote, inPerson), remote, inPerson)

    private companion object {
        val CATEGORY_NAMES = listOf(
            "computer-help" to "Computer help",
            "phone-tablet-help" to "Phone and tablet help",
            "wifi-internet" to "Wi-Fi and internet",
            "printer-setup" to "Printer setup",
            "smart-tv-streaming" to "Smart television and streaming",
            "smart-home" to "Smart-home devices",
            "email-accounts" to "Email and account help",
            "password-security" to "Password and security help",
            "software-installation" to "Software installation",
            "data-transfer-backup" to "Data transfer and backup",
            "device-setup" to "Device setup",
            "technology-lessons" to "Technology lessons",
            "other" to "Other technology problem",
        )
    }
}
