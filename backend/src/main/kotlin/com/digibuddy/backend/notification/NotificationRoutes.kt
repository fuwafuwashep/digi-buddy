package com.digibuddy.backend.notification

import com.digibuddy.backend.auth.authPrincipal
import com.digibuddy.shared.contracts.RegisterDeviceTokenRequest
import com.digibuddy.shared.contracts.UpdateNotificationPreferencesRequest
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route

fun Route.notificationRoutes(service: NotificationService) {
    authenticate("access") {
        route("/api/v1/customer/notifications") {
            post("/devices") {
                call.respond(service.register(call.authPrincipal(), call.receive<RegisterDeviceTokenRequest>()))
            }
            get("/preferences") { call.respond(service.preferences(call.authPrincipal())) }
            put("/preferences") {
                call.respond(service.update(call.authPrincipal(), call.receive<UpdateNotificationPreferencesRequest>()))
            }
        }
    }
}
