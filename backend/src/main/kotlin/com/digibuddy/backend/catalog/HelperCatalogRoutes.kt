package com.digibuddy.backend.catalog

import com.digibuddy.backend.auth.AuthenticationException
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import java.util.UUID

fun Route.helperCatalogRoutes(service: HelperCatalogService) {
    authenticate("access") {
        route("/api/v1/customer") {
            get("/helpers/search") { call.respond(service.search(call.searchQuery())) }
            get("/helper-filters") { call.respond(service.filterOptions()) }
            get("/service-categories") { call.respond(service.categories()) }
            get("/helpers/{helperId}") { call.respond(service.helper(call.helperId())) }
            get("/helpers/{helperId}/profile") { call.respond(service.profile(call.helperId())) }
            get("/helpers/{helperId}/reviews") {
                call.respond(
                    service.reviews(
                        call.helperId(),
                        call.request.queryParameters["page"]?.toIntOrNull() ?: 1,
                        call.request.queryParameters["pageSize"]?.toIntOrNull() ?: 20,
                    ),
                )
            }
            get("/helpers/{helperId}/availability") { call.respond(service.availability(call.helperId())) }
        }
    }
}

@Suppress("CyclomaticComplexMethod")
private fun io.ktor.server.application.ApplicationCall.searchQuery(): HelperSearchQuery {
    fun boolean(name: String) = request.queryParameters[name]?.let {
        it.toBooleanStrictOrNull() ?: invalid("$name must be true or false.")
    }
    fun integer(name: String) = request.queryParameters[name]?.toIntOrNull()
        ?: request.queryParameters[name]?.let { invalid("$name must be a whole number.") }
    fun decimal(name: String) = request.queryParameters[name]?.toDoubleOrNull()
        ?: request.queryParameters[name]?.let { invalid("$name must be a number.") }
    val sort = request.queryParameters["sort"]?.let { value ->
        runCatching { HelperSort.valueOf(value.uppercase().replace('-', '_')) }
            .getOrElse { invalid("Choose a supported sort option.") }
    } ?: HelperSort.RECOMMENDED
    return HelperSearchQuery(
        zipCode = request.queryParameters["zipCode"].orEmpty(),
        category = request.queryParameters["category"],
        skill = request.queryParameters["skill"],
        availableWithinDays = integer("availableWithinDays"),
        minimumRating = decimal("minimumRating"),
        maximumPriceCents = integer("maximumPriceCents"),
        language = request.queryParameters["language"],
        remoteService = boolean("remoteService"),
        inPersonService = boolean("inPersonService"),
        verifiedOnly = boolean("verifiedOnly") ?: false,
        sort = sort,
        page = integer("page") ?: 1,
        pageSize = integer("pageSize") ?: 20,
    )
}

private fun io.ktor.server.application.ApplicationCall.helperId(): UUID =
    runCatching { UUID.fromString(parameters["helperId"]) }
        .getOrElse { invalid("Enter a valid helper ID.") }

private fun invalid(message: String): Nothing = throw AuthenticationException("INVALID_SEARCH", message, 400)
