package com.digibuddy.backend.booking

import com.digibuddy.backend.auth.AuthenticationException
import com.digibuddy.backend.auth.authPrincipal
import com.digibuddy.shared.contracts.BookingMessageResponse
import com.digibuddy.shared.contracts.CancelBookingRequest
import com.digibuddy.shared.contracts.CreateBookingRequest
import com.digibuddy.shared.contracts.RescheduleBookingRequest
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import java.util.UUID

fun Route.bookingRoutes(service: BookingService) {
    authenticate("access") {
        route("/api/v1/customer/bookings") {
            get { call.respond(service.list(call.authPrincipal())) }
            post {
                val key = call.request.headers["Idempotency-Key"]
                    ?: throw AuthenticationException("IDEMPOTENCY_REQUIRED", "Please retry your request.", 400)
                call.respond(service.create(call.authPrincipal(), key, call.receive<CreateBookingRequest>()))
            }
            get("/{bookingId}") { call.respond(service.detail(call.authPrincipal(), call.bookingId())) }
            post("/{bookingId}/cancel") {
                call.respond(
                    service.cancel(call.authPrincipal(), call.bookingId(), call.receive<CancelBookingRequest>()),
                )
            }
            post("/{bookingId}/accept-quote") {
                call.respond(service.acceptQuote(call.authPrincipal(), call.bookingId()))
            }
            post("/{bookingId}/confirm-completion") {
                call.respond(service.confirmCompletion(call.authPrincipal(), call.bookingId()))
            }
            post("/{bookingId}/reschedule") {
                call.respond(
                    service.reschedule(
                        call.authPrincipal(),
                        call.bookingId(),
                        call.receive<RescheduleBookingRequest>(),
                    ),
                )
            }
            post("/{bookingId}/support") { call.respond(BookingMessageResponse("Your support request was opened.")) }
        }
        route("/api/v1/helper/bookings") {
            get("/requests") { call.respond(service.helperRequests(call.authPrincipal())) }
            get("/jobs") { call.respond(service.helperJobs(call.authPrincipal())) }
            get("/{bookingId}") { call.respond(service.helperDetail(call.authPrincipal(), call.bookingId())) }
            post("/{bookingId}/accept") {
                call.respond(service.helperAccept(call.authPrincipal(), call.bookingId()))
            }
            post("/{bookingId}/decline") {
                call.respond(service.helperDecline(call.authPrincipal(), call.bookingId()))
            }
        }
    }
}

private fun io.ktor.server.application.ApplicationCall.bookingId(): UUID =
    runCatching { UUID.fromString(parameters["bookingId"]) }
        .getOrElse { throw AuthenticationException("INVALID_BOOKING_ID", "Booking not found.", 404) }
