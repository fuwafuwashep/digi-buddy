package com.digibuddy.shared.networking

import com.digibuddy.shared.contracts.HelperFilterOptionsResponse
import com.digibuddy.shared.contracts.HelperProfileResponse
import com.digibuddy.shared.contracts.HelperReviewsResponse
import com.digibuddy.shared.contracts.HelperSearchResponse
import com.digibuddy.shared.contracts.ServiceCategoryResponse
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.parameter

data class HelperSearchParameters(
    val zipCode: String,
    val query: String = "",
    val category: String? = null,
    val minimumRating: Double? = null,
    val maximumPriceCents: Int? = null,
    val language: String? = null,
    val remote: Boolean? = null,
    val inPerson: Boolean? = null,
    val verifiedOnly: Boolean = false,
    val sort: String = "RECOMMENDED",
    val page: Int = 1,
    val pageSize: Int = 20,
)

class HelperCatalogApiClient(private val network: DigibuddyNetworkClient, baseUrl: String) {
    private val customer = "${baseUrl.trimEnd('/')}/api/v1/customer"

    suspend fun search(token: String, parameters: HelperSearchParameters): HelperSearchResponse {
        val response: HelperSearchResponse = network.httpClient.get("$customer/helpers/search") {
            bearerAuth(token)
            parameter("zipCode", parameters.zipCode)
            parameter("category", parameters.category)
            parameter("minimumRating", parameters.minimumRating)
            parameter("maximumPriceCents", parameters.maximumPriceCents)
            parameter("language", parameters.language)
            parameter("remoteService", parameters.remote)
            parameter("inPersonService", parameters.inPerson)
            parameter("verifiedOnly", parameters.verifiedOnly.takeIf { it })
            parameter("sort", parameters.sort)
            parameter("page", parameters.page)
            parameter("pageSize", parameters.pageSize)
        }.body()
        if (parameters.query.isBlank()) return response
        val needle = parameters.query.trim().lowercase()
        return response.copy(
            items = response.items.filter { helper ->
                helper.displayName.lowercase().contains(needle) ||
                    helper.headline.lowercase().contains(needle) ||
                    helper.skills.any { it.lowercase().contains(needle) }
            },
        )
    }

    suspend fun categories(token: String): List<ServiceCategoryResponse> =
        network.httpClient.get("$customer/service-categories") { bearerAuth(token) }.body()

    suspend fun filters(token: String): HelperFilterOptionsResponse =
        network.httpClient.get("$customer/helper-filters") { bearerAuth(token) }.body()

    suspend fun profile(token: String, helperId: String): HelperProfileResponse =
        network.httpClient.get("$customer/helpers/$helperId/profile") { bearerAuth(token) }.body()

    suspend fun reviews(token: String, helperId: String, page: Int = 1): HelperReviewsResponse =
        network.httpClient.get("$customer/helpers/$helperId/reviews") {
            bearerAuth(token)
            parameter("page", page)
        }.body()

    companion object {
        fun forLocalDevelopment(network: DigibuddyNetworkClient = createDigibuddyNetworkClient()) =
            HelperCatalogApiClient(network, localDevelopmentApiBaseUrl())
    }
}
