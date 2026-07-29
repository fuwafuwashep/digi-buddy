package com.digibuddy.shared.networking

import com.digibuddy.shared.contracts.AuthorizeDevelopmentPaymentRequest
import com.digibuddy.shared.contracts.CreatePaymentIntentRequest
import com.digibuddy.shared.contracts.PaymentIntentResponse
import com.digibuddy.shared.contracts.ReceiptResponse
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

class PaymentApiClient(private val network: DigibuddyNetworkClient, baseUrl: String) {
    private val payments = "${baseUrl.trimEnd('/')}/api/v1/customer/payments"

    suspend fun create(token: String, bookingId: String, idempotencyKey: String): PaymentIntentResponse =
        network.httpClient.post("$payments/intents") {
            bearerAuth(token)
            header("Idempotency-Key", idempotencyKey)
            contentType(ContentType.Application.Json)
            setBody(CreatePaymentIntentRequest(bookingId))
        }.body()

    suspend fun authorizeDevelopment(token: String, paymentId: String): PaymentIntentResponse =
        network.httpClient.post("$payments/development/authorize") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(AuthorizeDevelopmentPaymentRequest(paymentId))
        }.body()

    suspend fun receipt(token: String, bookingId: String): ReceiptResponse =
        network.httpClient.get("$payments/receipts/$bookingId") { bearerAuth(token) }.body()

    companion object {
        fun forLocalDevelopment(network: DigibuddyNetworkClient = createDigibuddyNetworkClient()) =
            PaymentApiClient(network, localDevelopmentApiBaseUrl())
    }
}
