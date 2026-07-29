package com.digibuddy.shared.networking

import com.digibuddy.shared.contracts.AccountDeletionResponse
import com.digibuddy.shared.contracts.CompleteCustomerOnboardingRequest
import com.digibuddy.shared.contracts.CompleteProfilePhotoUploadRequest
import com.digibuddy.shared.contracts.CustomerProfileResponse
import com.digibuddy.shared.contracts.DataExportRequestResponse
import com.digibuddy.shared.contracts.DeleteAccountRequest
import com.digibuddy.shared.contracts.ProfilePhotoUploadGrantResponse
import com.digibuddy.shared.contracts.ProfilePhotoUploadRequest
import com.digibuddy.shared.contracts.SaveAddressRequest
import com.digibuddy.shared.contracts.SecurityOverviewResponse
import com.digibuddy.shared.contracts.UpdateAccessibilitySettingsRequest
import com.digibuddy.shared.contracts.UpdateCustomerNameRequest
import com.digibuddy.shared.contracts.UpdateNotificationSettingsRequest
import com.digibuddy.shared.contracts.UpdatePrivacySettingsRequest
import com.digibuddy.shared.contracts.UpdateTechnologyPreferencesRequest
import com.digibuddy.shared.contracts.UpdateZipCodeRequest
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

class CustomerProfileApiClient(private val network: DigibuddyNetworkClient, baseUrl: String) {
    private val base = baseUrl.trimEnd('/')
    private val customer = "$base/api/v1/customer"

    suspend fun profile(token: String): CustomerProfileResponse = network.httpClient.get("$customer/profile") {
        bearerAuth(token)
    }.body()
    suspend fun completeOnboarding(token: String, request: CompleteCustomerOnboardingRequest): CustomerProfileResponse =
        network.httpClient.post("$customer/onboarding") { authJson(token, request) }.body()
    suspend fun updateName(token: String, first: String, last: String): CustomerProfileResponse =
        network.httpClient.put("$customer/profile/name") {
            authJson(token, UpdateCustomerNameRequest(first, last))
        }.body()
    suspend fun updateZip(token: String, zip: String): CustomerProfileResponse =
        network.httpClient.put("$customer/profile/zip") { authJson(token, UpdateZipCodeRequest(zip)) }.body()
    suspend fun updatePreferences(token: String, values: List<String>): CustomerProfileResponse =
        network.httpClient.put("$customer/profile/technology-preferences") {
            authJson(token, UpdateTechnologyPreferencesRequest(values))
        }.body()
    suspend fun updateAccessibility(
        token: String,
        request: UpdateAccessibilitySettingsRequest,
    ): CustomerProfileResponse =
        network.httpClient.put("$customer/settings/accessibility") { authJson(token, request) }.body()
    suspend fun updateNotifications(token: String, enabled: Boolean, permission: String): CustomerProfileResponse =
        network.httpClient.put("$customer/settings/notifications") {
            authJson(token, UpdateNotificationSettingsRequest(enabled, permission))
        }.body()
    suspend fun updatePrivacy(token: String, permission: String): CustomerProfileResponse =
        network.httpClient.put("$customer/settings/privacy") {
            authJson(token, UpdatePrivacySettingsRequest(permission))
        }.body()
    suspend fun saveAddress(token: String, request: SaveAddressRequest): CustomerProfileResponse =
        network.httpClient.post("$customer/profile/addresses") { authJson(token, request) }.body()
    suspend fun security(token: String): SecurityOverviewResponse =
        network.httpClient.get("$customer/security") { bearerAuth(token) }.body()
    suspend fun requestExport(token: String): DataExportRequestResponse =
        network.httpClient.post("$customer/privacy/data-export") { bearerAuth(token) }.body()
    suspend fun requestDeletion(token: String): AccountDeletionResponse =
        network.httpClient.post("$customer/account/delete") { authJson(token, DeleteAccountRequest("DELETE")) }.body()
    suspend fun createPhotoUpload(
        token: String,
        name: String,
        type: String,
        bytes: ByteArray,
    ): ProfilePhotoUploadGrantResponse = network.httpClient.post("$customer/profile/photo/uploads") {
        authJson(token, ProfilePhotoUploadRequest(name, type, bytes.size.toLong()))
    }.body()
    suspend fun uploadPhoto(
        grant: ProfilePhotoUploadGrantResponse,
        type: String,
        bytes: ByteArray,
        progress: (Float) -> Unit,
    ) {
        progress(0f)
        val url = if (grant.uploadUrl.startsWith("/")) "$base${grant.uploadUrl}" else grant.uploadUrl
        network.httpClient.put(url) {
            contentType(ContentType.parse(type))
            headers { grant.headers.forEach { (name, value) -> append(name, value) } }
            setBody(bytes)
        }
        progress(1f)
    }
    suspend fun completePhoto(token: String, uploadId: String): CustomerProfileResponse =
        network.httpClient.post("$customer/profile/photo/complete") {
            authJson(token, CompleteProfilePhotoUploadRequest(uploadId))
        }.body()
    suspend fun removePhoto(token: String): CustomerProfileResponse =
        network.httpClient.delete("$customer/profile/photo") { bearerAuth(token) }.body()

    private inline fun <reified T : Any> io.ktor.client.request.HttpRequestBuilder.authJson(token: String, body: T) {
        bearerAuth(token)
        contentType(ContentType.Application.Json)
        setBody(body)
    }

    companion object {
        fun forLocalDevelopment(network: DigibuddyNetworkClient = createDigibuddyNetworkClient()) =
            CustomerProfileApiClient(network, localDevelopmentApiBaseUrl())
    }
}
