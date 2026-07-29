package com.digibuddy.shared.networking

import com.digibuddy.shared.contracts.BookingDetailResponse
import com.digibuddy.shared.contracts.BookingListResponse
import com.digibuddy.shared.contracts.CancelBookingRequest
import com.digibuddy.shared.contracts.CreateBookingRequest
import com.digibuddy.shared.contracts.RescheduleBookingRequest
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

class BookingApiClient(private val network: DigibuddyNetworkClient, baseUrl: String) {
    private val bookings = "${baseUrl.trimEnd('/')}/api/v1/customer/bookings"
    private val helperBookings = "${baseUrl.trimEnd('/')}/api/v1/helper/bookings"

    suspend fun list(token: String): BookingListResponse = network.httpClient.get(bookings) { bearerAuth(token) }.body()

    suspend fun detail(token: String, id: String): BookingDetailResponse =
        network.httpClient.get("$bookings/$id") { bearerAuth(token) }.body()

    suspend fun create(token: String, idempotencyKey: String, request: CreateBookingRequest): BookingDetailResponse =
        network.httpClient.post(bookings) {
            bearerAuth(token)
            header("Idempotency-Key", idempotencyKey)
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    suspend fun cancel(token: String, id: String, reason: String): BookingDetailResponse =
        command(token, id, "cancel", CancelBookingRequest(reason))

    suspend fun acceptQuote(token: String, id: String): BookingDetailResponse = command<Unit>(token, id, "accept-quote")

    suspend fun confirmCompletion(token: String, id: String): BookingDetailResponse =
        command<Unit>(token, id, "confirm-completion")

    suspend fun reschedule(token: String, id: String, request: RescheduleBookingRequest): BookingDetailResponse =
        command(token, id, "reschedule", request)

    suspend fun helperRequests(token: String): BookingListResponse =
        network.httpClient.get("$helperBookings/requests") { bearerAuth(token) }.body()

    suspend fun helperJobs(token: String): BookingListResponse =
        network.httpClient.get("$helperBookings/jobs") { bearerAuth(token) }.body()

    suspend fun helperDetail(token: String, id: String): BookingDetailResponse =
        network.httpClient.get("$helperBookings/$id") { bearerAuth(token) }.body()

    suspend fun helperAccept(token: String, id: String): BookingDetailResponse =
        network.httpClient.post("$helperBookings/$id/accept") { bearerAuth(token) }.body()

    suspend fun helperDecline(token: String, id: String): BookingDetailResponse =
        network.httpClient.post("$helperBookings/$id/decline") { bearerAuth(token) }.body()

    private suspend inline fun <reified T : Any> command(
        token: String,
        id: String,
        action: String,
        body: T? = null,
    ): BookingDetailResponse = network.httpClient.post("$bookings/$id/$action") {
        bearerAuth(token)
        body?.let {
            contentType(ContentType.Application.Json)
            setBody(it)
        }
    }.body()

    companion object {
        fun forLocalDevelopment(network: DigibuddyNetworkClient = createDigibuddyNetworkClient()) =
            BookingApiClient(network, localDevelopmentApiBaseUrl())
    }
}
