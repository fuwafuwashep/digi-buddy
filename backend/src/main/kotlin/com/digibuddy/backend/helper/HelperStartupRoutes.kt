package com.digibuddy.backend.helper

import com.digibuddy.backend.auth.authPrincipal
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route

fun Route.helperStartupRoutes(service: HelperStartupService) {
    authenticate("access") {
        route("/api/v1/helper") {
            get("/startup") {
                call.respond(service.startup(call.authPrincipal()))
            }
        }
    }
}
