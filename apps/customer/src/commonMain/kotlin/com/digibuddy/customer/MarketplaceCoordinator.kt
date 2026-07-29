package com.digibuddy.customer

import com.digibuddy.shared.contracts.HelperProfileResponse
import com.digibuddy.shared.contracts.HelperReviewResponse
import com.digibuddy.shared.contracts.HelperSummaryResponse
import com.digibuddy.shared.contracts.ServiceCategoryResponse
import com.digibuddy.shared.networking.HelperCatalogApiClient
import com.digibuddy.shared.networking.HelperSearchParameters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MarketplaceFilters(
    val minimumRating: Double? = null,
    val maximumPriceCents: Int? = null,
    val language: String? = null,
    val remote: Boolean? = null,
    val inPerson: Boolean? = null,
    val verifiedOnly: Boolean = false,
)

sealed interface MarketplaceState {
    data object Loading : MarketplaceState
    data class Results(
        val helpers: List<HelperSummaryResponse>,
        val categories: List<ServiceCategoryResponse>,
        val zipCode: String,
        val search: String,
        val category: String?,
        val filters: MarketplaceFilters,
        val sort: String,
        val cached: Boolean = false,
    ) : MarketplaceState
    data class Failure(val message: String, val cached: List<HelperSummaryResponse> = emptyList()) : MarketplaceState
}

sealed interface HelperDetailState {
    data object Closed : HelperDetailState
    data object Loading : HelperDetailState
    data class Ready(val profile: HelperProfileResponse, val reviews: List<HelperReviewResponse>) : HelperDetailState
    data class Failure(val message: String) : HelperDetailState
}

class MarketplaceCoordinator(
    private val api: HelperCatalogApiClient,
    private val accessToken: String,
    initialZipCode: String,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val mutableState = MutableStateFlow<MarketplaceState>(MarketplaceState.Loading)
    val state = mutableState.asStateFlow()
    private val mutableDetail = MutableStateFlow<HelperDetailState>(HelperDetailState.Closed)
    val detail = mutableDetail.asStateFlow()
    private var zipCode = initialZipCode.ifBlank { "60601" }
    private var search = ""
    private var category: String? = null
    private var filters = MarketplaceFilters()
    private var sort = "RECOMMENDED"
    private var cachedHelpers: List<HelperSummaryResponse> = emptyList()
    private var cachedCategories: List<ServiceCategoryResponse> = emptyList()
    private val recentlyViewed = linkedMapOf<String, HelperProfileResponse>()

    fun load() = refresh()

    fun refresh() = scope.launch {
        mutableState.value = MarketplaceState.Loading
        runCatching {
            val categories = if (cachedCategories.isEmpty()) api.categories(accessToken) else cachedCategories
            val response = api.search(
                accessToken,
                HelperSearchParameters(
                    zipCode = zipCode,
                    query = search,
                    category = category,
                    minimumRating = filters.minimumRating,
                    maximumPriceCents = filters.maximumPriceCents,
                    language = filters.language,
                    remote = filters.remote,
                    inPerson = filters.inPerson,
                    verifiedOnly = filters.verifiedOnly,
                    sort = sort,
                ),
            )
            cachedCategories = categories
            cachedHelpers = response.items
            MarketplaceState.Results(
                response.items,
                categories,
                zipCode,
                search,
                category,
                filters,
                sort,
            )
        }.onSuccess { mutableState.value = it }
            .onFailure {
                mutableState.value = if (cachedHelpers.isNotEmpty()) {
                    MarketplaceState.Results(
                        cachedHelpers,
                        cachedCategories,
                        zipCode,
                        search,
                        category,
                        filters,
                        sort,
                        cached = true,
                    )
                } else {
                    MarketplaceState.Failure("We could not load helpers. Check your connection and try again.")
                }
            }
    }

    fun updateSearch(value: String) {
        search = value
        refresh()
    }

    fun changeZip(value: String) {
        zipCode = value.filter(Char::isDigit).take(5)
        if (zipCode.length == 5) refresh()
    }

    fun selectCategory(value: String?) {
        category = value
        refresh()
    }

    fun applyFilters(value: MarketplaceFilters) {
        filters = value
        refresh()
    }

    fun changeSort(value: String) {
        sort = value
        refresh()
    }

    fun openHelper(helperId: String) = scope.launch {
        recentlyViewed[helperId]?.let {
            mutableDetail.value = HelperDetailState.Ready(it, emptyList())
        } ?: run { mutableDetail.value = HelperDetailState.Loading }
        runCatching { api.profile(accessToken, helperId) to api.reviews(accessToken, helperId).items }
            .onSuccess { (profile, reviews) ->
                recentlyViewed[helperId] = profile
                while (recentlyViewed.size > 10) recentlyViewed.remove(recentlyViewed.keys.first())
                mutableDetail.value = HelperDetailState.Ready(profile, reviews)
            }
            .onFailure { mutableDetail.value = HelperDetailState.Failure("We could not load this helper.") }
    }

    fun closeHelper() {
        mutableDetail.value = HelperDetailState.Closed
    }
}
