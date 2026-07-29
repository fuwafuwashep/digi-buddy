package com.digibuddy.backend.payment

import com.digibuddy.backend.auth.AuthenticationException
import com.digibuddy.backend.auth.authPrincipal
import com.digibuddy.shared.contracts.AuthorizeDevelopmentPaymentRequest
import com.digibuddy.shared.contracts.CreatePaymentIntentRequest
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import java.util.UUID

fun Route.paymentRoutes(service: PaymentService) {
    route("/api/v1") {
        post("/webhooks/stripe") {
            val eventId = call.request.headers["Stripe-Event-Id"] ?: "missing"
            service.webhook(call.receiveText(), call.request.headers["Stripe-Signature"], eventId)
            call.respond(mapOf("received" to true))
        }
        authenticate("access") {
            route("/customer/payments") {
                post("/intents") {
                    val request = call.receive<CreatePaymentIntentRequest>()
                    val key = call.request.headers["Idempotency-Key"]
                        ?: throw AuthenticationException("IDEMPOTENCY_REQUIRED", "Please retry payment.", 400)
                    call.respond(service.create(call.authPrincipal(), UUID.fromString(request.bookingId), key))
                }
                post("/development/authorize") {
                    val request = call.receive<AuthorizeDevelopmentPaymentRequest>()
                    call.respond(service.authorizeDevelopment(call.authPrincipal(), UUID.fromString(request.paymentId)))
                }
                get("/receipts/{bookingId}") {
                    call.respond(service.receipt(call.authPrincipal(), UUID.fromString(call.parameters["bookingId"])))
                }
            }
        }
    }
}
