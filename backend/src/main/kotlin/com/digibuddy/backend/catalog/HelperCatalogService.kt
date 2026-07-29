@file:Suppress("TooManyFunctions", "LongParameterList")

package com.digibuddy.backend.catalog

import com.digibuddy.backend.auth.AuthenticationException
import com.digibuddy.shared.contracts.HelperAvailabilitySummaryResponse
import com.digibuddy.shared.contracts.HelperFilterOptionsResponse
import com.digibuddy.shared.contracts.HelperPortfolioItemResponse
import com.digibuddy.shared.contracts.HelperProfileResponse
import com.digibuddy.shared.contracts.HelperReviewResponse
import com.digibuddy.shared.contracts.HelperReviewsResponse
import com.digibuddy.shared.contracts.HelperSearchResponse
import com.digibuddy.shared.contracts.HelperServiceResponse
import com.digibuddy.shared.contracts.HelperSummaryResponse
import com.digibuddy.shared.contracts.ServiceCategoryResponse
import java.time.Instant
import java.util.UUID
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt

data class GeoPoint(val latitude: Double, val longitude: Double)

data class CatalogService(
    val categorySlug: String,
    val startingPriceCents: Int,
    val remote: Boolean,
    val inPerson: Boolean,
)

data class CatalogServiceArea(val origin: GeoPoint, val radiusMiles: Double?, val zipCodes: Set<String>)

data class CatalogAvailability(
    val status: String,
    val nextAvailableAt: Instant?,
    val availableWithinDays: Int?,
    val acceptingNewCustomers: Boolean,
)

data class CatalogHelper(
    val id: UUID,
    val displayName: String,
    val headline: String,
    val biography: String,
    val profilePictureUrl: String? = null,
    val accountStatus: String = "ACTIVE",
    val approvalStatus: String = "APPROVED",
    val verificationStatus: String = "VERIFIED",
    val catalogVisible: Boolean = true,
    val skills: Map<String, Int>,
    val services: List<CatalogService>,
    val serviceAreas: List<CatalogServiceArea>,
    val languages: Set<String>,
    val availability: CatalogAvailability,
    val rating: Double,
    val reviewCount: Int,
    val completedJobs: Int,
    val responseMinutes: Int,
    val serviceAreaDescription: String? = null,
    val certifications: List<String> = emptyList(),
    val portfolioLinks: List<String> = emptyList(),
)

data class HelperLifecycleSnapshot(val accountStatus: String, val approvalStatus: String, val displayName: String?)

data class HelperAccountReference(val userId: UUID, val displayName: String)

data class HelperSearchQuery(
    val zipCode: String,
    val category: String? = null,
    val skill: String? = null,
    val availableWithinDays: Int? = null,
    val minimumRating: Double? = null,
    val maximumPriceCents: Int? = null,
    val language: String? = null,
    val remoteService: Boolean? = null,
    val inPersonService: Boolean? = null,
    val verifiedOnly: Boolean = false,
    val sort: HelperSort = HelperSort.RECOMMENDED,
    val page: Int = 1,
    val pageSize: Int = 20,
)

enum class HelperSort {
    RECOMMENDED,
    HIGHEST_RATED,
    CLOSEST,
    SOONEST_AVAILABLE,
    LOWEST_STARTING_PRICE,
    MOST_EXPERIENCED,
}

interface HelperCatalogRepository {
    fun zipLocation(zipCode: String): GeoPoint?
    fun helpers(zipCode: String? = null): List<CatalogHelper>
    fun categories(): List<ServiceCategoryResponse>
    fun lifecycle(userId: UUID): HelperLifecycleSnapshot? = null
    fun accountReference(helperId: UUID): HelperAccountReference? = null
}

class HelperCatalogService(private val repository: HelperCatalogRepository) {
    fun lifecycle(userId: UUID): HelperLifecycleSnapshot? = repository.lifecycle(userId)

    fun accountReference(helperId: UUID): HelperAccountReference {
        val helper = repository.helpers().find { it.id == helperId && eligible(it) }
            ?: throw AuthenticationException("HELPER_NOT_FOUND", "Helper not found.", 404)
        return repository.accountReference(helperId)
            ?: throw AuthenticationException(
                "HELPER_UNAVAILABLE",
                "This helper is not available for requests yet.",
                409,
            )
    }

    fun search(query: HelperSearchQuery): HelperSearchResponse {
        validate(query)
        val customer = repository.zipLocation(query.zipCode) ?: invalidZip()
        val matches = repository.helpers(query.zipCode).asSequence()
            .filter(::eligible)
            .mapNotNull { helper -> match(helper, customer, query) }
            .sortedWith(comparator(query.sort))
            .toList()
        val from = ((query.page - 1) * query.pageSize).coerceAtMost(matches.size)
        val items = matches.drop(from).take(query.pageSize).map(MatchedHelper::response)
        return HelperSearchResponse(
            items,
            query.page,
            query.pageSize,
            matches.size,
            if (matches.isEmpty()) 0 else (matches.size + query.pageSize - 1) / query.pageSize,
            query.sort.name,
        )
    }

    fun categories() = repository.categories()

    fun filterOptions(): HelperFilterOptionsResponse {
        val eligible = repository.helpers().filter(::eligible)
        val prices = eligible.flatMap(CatalogHelper::services).map { it.startingPriceCents }
        return HelperFilterOptionsResponse(
            categories = categories(),
            skills = eligible.flatMap { it.skills.keys }.distinct().sorted(),
            languages = eligible.flatMap { it.languages }.distinct().sorted(),
            sortChoices = HelperSort.entries.map { it.name },
            minimumPriceCents = prices.minOrNull(),
            maximumPriceCents = prices.maxOrNull(),
        )
    }

    fun helper(helperId: UUID): HelperSummaryResponse {
        val helper = repository.helpers().find { it.id == helperId && eligible(it) }
            ?: throw AuthenticationException("HELPER_NOT_FOUND", "Helper not found.", 404)
        return match(helper, null, HelperSearchQuery("00000", remoteService = true))?.response()
            ?: helper.response(null)
    }

    fun availability(helperId: UUID): HelperAvailabilitySummaryResponse {
        val helper = repository.helpers().find { it.id == helperId && eligible(it) }
            ?: throw AuthenticationException("HELPER_NOT_FOUND", "Helper not found.", 404)
        return helper.availability.response()
    }

    fun profile(helperId: UUID): HelperProfileResponse {
        val helper = eligibleHelper(helperId)
        val names = categories().associate { it.slug to it.name }
        return HelperProfileResponse(
            summary = helper.response(null),
            experienceYears = helper.skills.values.maxOrNull()?.coerceAtMost(20) ?: 1,
            services = helper.services.map { service ->
                val name = names[service.categorySlug] ?: service.categorySlug.replace('-', ' ')
                HelperServiceResponse(
                    categorySlug = service.categorySlug,
                    name = name,
                    description = "Patient, step-by-step $name with time for questions.",
                    startingPriceCents = service.startingPriceCents,
                    pricingType = if (service.categorySlug in
                        setOf("device-setup", "printer-setup")
                    ) {
                        "FIXED"
                    } else {
                        "HOURLY"
                    },
                    remote = service.remote,
                    inPerson = service.inPerson,
                )
            },
            serviceAreaDescription = helper.serviceAreaDescription ?: (
                "Approximate service area near " +
                    (helper.serviceAreas.firstOrNull()?.zipCodes?.firstOrNull() ?: "your ZIP code")
                ),
            certifications = helper.certifications,
            portfolio = helper.portfolioLinks.mapIndexed { index, link ->
                HelperPortfolioItemResponse("${helper.id}-portfolio-$index", "Portfolio link", link)
            },
            ratingBreakdown = ratingBreakdown(helper.reviewCount, helper.rating),
        )
    }

    fun reviews(helperId: UUID, page: Int, pageSize: Int): HelperReviewsResponse {
        val helper = eligibleHelper(helperId)
        if (page < 1 || pageSize !in 1..50) {
            throw AuthenticationException("INVALID_PAGINATION", "Use a positive page and page size up to 50.", 400)
        }
        val all = emptyList<HelperReviewResponse>()
        val from = ((page - 1) * pageSize).coerceAtMost(all.size)
        return HelperReviewsResponse(all.drop(from).take(pageSize), page, pageSize, all.size)
    }

    private fun eligibleHelper(helperId: UUID): CatalogHelper =
        repository.helpers().find { it.id == helperId && eligible(it) }
            ?: throw AuthenticationException("HELPER_NOT_FOUND", "Helper not found.", 404)

    @Suppress("CyclomaticComplexMethod", "ReturnCount")
    private fun match(helper: CatalogHelper, customer: GeoPoint?, query: HelperSearchQuery): MatchedHelper? {
        val services = helper.services.filter { service ->
            (query.category == null || service.categorySlug == query.category) &&
                (query.maximumPriceCents == null || service.startingPriceCents <= query.maximumPriceCents) &&
                (query.remoteService != true || service.remote) &&
                (query.inPersonService != true || service.inPerson)
        }
        if (services.isEmpty() || query.skill?.let { it !in helper.skills } == true) return null
        if (query.language?.let { it !in helper.languages } == true) return null
        if (query.minimumRating?.let { helper.rating < it } == true) return null
        if (query.verifiedOnly && helper.verificationStatus != "VERIFIED") return null
        if (query.availableWithinDays?.let { days ->
                helper.availability.availableWithinDays?.let { it > days } ?: true
            } == true
        ) {
            return null
        }

        val distance = customer?.let { point -> helper.serviceAreas.minOfOrNull { miles(point, it.origin) } }
        val exactZip = helper.serviceAreas.any { query.zipCode in it.zipCodes }
        val radiusMatch = customer != null &&
            helper.serviceAreas.any { area ->
                area.radiusMiles?.let { miles(customer, area.origin) <= it } == true
            }
        val remoteMatch = services.any(CatalogService::remote)
        val inPersonMatch = services.any(CatalogService::inPerson) && (exactZip || radiusMatch)
        val modeMatches = when {
            query.inPersonService == true && query.remoteService != true -> inPersonMatch
            query.remoteService == true && query.inPersonService != true -> remoteMatch
            query.inPersonService == true && query.remoteService == true -> inPersonMatch || remoteMatch
            else -> inPersonMatch || remoteMatch
        }
        if (!modeMatches) return null
        return MatchedHelper(helper, services.minOf { it.startingPriceCents }, distance, remoteMatch, inPersonMatch)
    }

    private fun eligible(helper: CatalogHelper) = helper.accountStatus == "ACTIVE" &&
        helper.approvalStatus == "APPROVED" &&
        helper.catalogVisible &&
        helper.availability.acceptingNewCustomers &&
        helper.services.isNotEmpty()

    private fun comparator(sort: HelperSort): Comparator<MatchedHelper> {
        val primary = when (sort) {
            HelperSort.RECOMMENDED -> compareByDescending<MatchedHelper> { it.recommendedScore() }
            HelperSort.HIGHEST_RATED -> compareByDescending<MatchedHelper> { it.helper.rating }
                .thenByDescending { it.helper.reviewCount }
            HelperSort.CLOSEST -> compareBy<MatchedHelper> { it.distanceMiles ?: Double.MAX_VALUE }
            HelperSort.SOONEST_AVAILABLE -> compareBy<MatchedHelper> {
                it.helper.availability.nextAvailableAt ?: Instant.MAX
            }
            HelperSort.LOWEST_STARTING_PRICE -> compareBy<MatchedHelper> { it.startingPrice }
            HelperSort.MOST_EXPERIENCED -> compareByDescending<MatchedHelper> { it.helper.completedJobs }
        }
        return primary.thenBy { it.helper.id }
    }

    private fun validate(query: HelperSearchQuery) {
        if (!query.zipCode.matches(Regex("^[0-9]{5}$"))) invalidZip()
        if (query.page < 1 || query.pageSize !in 1..50) {
            throw AuthenticationException("INVALID_PAGINATION", "Use a positive page and page size up to 50.", 400)
        }
        if (query.minimumRating != null && query.minimumRating !in 0.0..5.0) {
            throw AuthenticationException("INVALID_FILTER", "Rating must be between 0 and 5.", 400)
        }
    }

    private fun invalidZip(): Nothing =
        throw AuthenticationException("INVALID_ZIP", "Enter a supported five-digit ZIP code.", 400)

    private data class MatchedHelper(
        val helper: CatalogHelper,
        val startingPrice: Int,
        val distanceMiles: Double?,
        val remoteMatch: Boolean,
        val inPersonMatch: Boolean,
    ) {
        fun recommendedScore() = helper.rating * 20 + helper.reviewCount.coerceAtMost(100) * .08 +
            helper.completedJobs.coerceAtMost(200) * .03 - (distanceMiles ?: 20.0) * .15 -
            (helper.availability.availableWithinDays ?: 30) * .5

        fun response() = helper.response(distanceMiles, startingPrice, remoteMatch, inPersonMatch)
    }

    companion object {
        private const val EARTH_RADIUS_MILES = 3958.7613

        private fun miles(a: GeoPoint, b: GeoPoint): Double {
            val lat = Math.toRadians(b.latitude - a.latitude)
            val lon = Math.toRadians(b.longitude - a.longitude)
            val value = sin(lat / 2) * sin(lat / 2) +
                cos(Math.toRadians(a.latitude)) * cos(Math.toRadians(b.latitude)) * sin(lon / 2) * sin(lon / 2)
            return EARTH_RADIUS_MILES * 2 * asin(sqrt(value))
        }

        private fun ratingBreakdown(total: Int, average: Double): Map<Int, Int> {
            val five = (total * (average / 5.0) * .86).toInt().coerceAtMost(total)
            val four = ((total - five) * .72).toInt()
            val three = (total - five - four).coerceAtLeast(0)
            return mapOf(5 to five, 4 to four, 3 to three, 2 to 0, 1 to 0)
        }
    }
}

private fun CatalogHelper.response(
    distance: Double?,
    price: Int = services.minOf { it.startingPriceCents },
    remote: Boolean = services.any { it.remote },
    inPerson: Boolean = services.any { it.inPerson },
) = HelperSummaryResponse(
    id.toString(), displayName, headline, biography, profilePictureUrl,
    verificationStatus == "VERIFIED", rating, reviewCount, completedJobs, responseMinutes, price, "USD",
    distance?.let { round(it * 10) / 10 }, remote, inPerson, languages.sorted(), skills.keys.sorted(),
    services.map { it.categorySlug }.distinct().sorted(), availability.response(),
)

private fun CatalogAvailability.response() = HelperAvailabilitySummaryResponse(
    status,
    nextAvailableAt?.toString(),
    availableWithinDays,
)
