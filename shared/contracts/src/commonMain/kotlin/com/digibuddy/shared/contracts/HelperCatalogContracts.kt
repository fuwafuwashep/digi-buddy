package com.digibuddy.shared.contracts

import kotlinx.serialization.Serializable

@Serializable
data class ServiceCategoryResponse(val id: String, val slug: String, val name: String, val description: String)

@Serializable
data class HelperAvailabilitySummaryResponse(
    val status: String,
    val nextAvailableAt: String? = null,
    val availableWithinDays: Int? = null,
)

@Serializable
data class HelperSummaryResponse(
    val helperId: String,
    val displayName: String,
    val headline: String,
    val biography: String,
    val profilePictureUrl: String? = null,
    val verified: Boolean,
    val rating: Double,
    val reviewCount: Int,
    val completedJobCount: Int,
    val responseTimeMinutes: Int,
    val startingPriceCents: Int,
    val currency: String,
    val distanceMiles: Double? = null,
    val remoteService: Boolean,
    val inPersonService: Boolean,
    val languages: List<String>,
    val skills: List<String>,
    val serviceCategories: List<String>,
    val availability: HelperAvailabilitySummaryResponse,
)

@Serializable
data class HelperSearchResponse(
    val items: List<HelperSummaryResponse>,
    val page: Int,
    val pageSize: Int,
    val total: Int,
    val totalPages: Int,
    val sort: String,
)

@Serializable
data class HelperFilterOptionsResponse(
    val categories: List<ServiceCategoryResponse>,
    val skills: List<String>,
    val languages: List<String>,
    val sortChoices: List<String>,
    val minimumPriceCents: Int?,
    val maximumPriceCents: Int?,
)

@Serializable
data class HelperServiceResponse(
    val categorySlug: String,
    val name: String,
    val description: String,
    val startingPriceCents: Int,
    val pricingType: String,
    val remote: Boolean,
    val inPerson: Boolean,
)

@Serializable
data class HelperPortfolioItemResponse(
    val id: String,
    val title: String,
    val description: String,
    val imageUrl: String? = null,
)

@Serializable
data class HelperProfileResponse(
    val summary: HelperSummaryResponse,
    val experienceYears: Int,
    val services: List<HelperServiceResponse>,
    val serviceAreaDescription: String,
    val certifications: List<String>,
    val portfolio: List<HelperPortfolioItemResponse>,
    val ratingBreakdown: Map<Int, Int>,
)

@Serializable
data class HelperReviewResponse(
    val reviewId: String,
    val reviewerDisplayName: String,
    val rating: Int,
    val comment: String,
    val createdAt: String,
    val serviceName: String,
    val developmentSeed: Boolean,
)

@Serializable
data class HelperReviewsResponse(
    val items: List<HelperReviewResponse>,
    val page: Int,
    val pageSize: Int,
    val total: Int,
)
