package com.digibuddy.shared.networking

import com.digibuddy.shared.contracts.AuthenticationErrorResponse
import com.digibuddy.shared.contracts.CompleteProfilePhotoUploadRequest
import com.digibuddy.shared.contracts.HelperApplicationResponse
import com.digibuddy.shared.contracts.HelperApplicationStepRequest
import com.digibuddy.shared.contracts.HelperOnboardingStep
import com.digibuddy.shared.contracts.HelperStartupResponse
import com.digibuddy.shared.contracts.ProfilePhotoUploadGrantResponse
import com.digibuddy.shared.contracts.ProfilePhotoUploadRequest
import com.digibuddy.shared.contracts.UpdateHelperProfileRequest
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

class HelperAccountApiClient(private val network: DigibuddyNetworkClient, baseUrl: String) {
    private val base = baseUrl.trimEnd('/')
    private val startupUrl = "$base/api/v1/helper/startup"
    private val applicationUrl = "$base/api/v1/helper/application"

    suspend fun startup(accessToken: String): HelperStartupResponse = execute {
        network.httpClient.get(startupUrl) { bearerAuth(accessToken) }.body()
    }

    suspend fun application(accessToken: String): HelperApplicationResponse = execute {
        network.httpClient.get(applicationUrl) { bearerAuth(accessToken) }.body()
    }

    suspend fun saveStep(
        accessToken: String,
        step: HelperOnboardingStep,
        request: HelperApplicationStepRequest,
    ): HelperApplicationResponse = execute {
        network.httpClient.put("$applicationUrl/steps/${step.name}") {
            bearerAuth(accessToken)
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    suspend fun submit(accessToken: String): HelperApplicationResponse = execute {
        network.httpClient.post("$applicationUrl/submit") { bearerAuth(accessToken) }.body()
    }

    suspend fun pause(accessToken: String): HelperApplicationResponse = execute {
        network.httpClient.post("$applicationUrl/pause") { bearerAuth(accessToken) }.body()
    }

    suspend fun resume(accessToken: String): HelperApplicationResponse = execute {
        network.httpClient.post("$applicationUrl/resume") { bearerAuth(accessToken) }.body()
    }

    suspend fun updateProfile(accessToken: String, request: UpdateHelperProfileRequest): HelperApplicationResponse =
        execute {
            network.httpClient.put("$applicationUrl/profile") {
                bearerAuth(accessToken)
                contentType(ContentType.Application.Json)
                setBody(request)
            }.body()
        }

    suspend fun createProfilePhotoUpload(
        accessToken: String,
        fileName: String,
        contentType: String,
        bytes: ByteArray,
    ): ProfilePhotoUploadGrantResponse = execute {
        network.httpClient.post("$applicationUrl/profile/photo/uploads") {
            bearerAuth(accessToken)
            contentType(ContentType.Application.Json)
            setBody(ProfilePhotoUploadRequest(fileName, contentType, bytes.size.toLong()))
        }.body()
    }

    suspend fun uploadProfilePhoto(
        grant: ProfilePhotoUploadGrantResponse,
        contentType: String,
        bytes: ByteArray,
    ): Unit = execute {
        val url = if (grant.uploadUrl.startsWith('/')) "$base${grant.uploadUrl}" else grant.uploadUrl
        network.httpClient.put(url) {
            contentType(ContentType.parse(contentType))
            headers { grant.headers.forEach { (name, value) -> append(name, value) } }
            setBody(bytes)
        }
    }

    suspend fun completeProfilePhoto(accessToken: String, uploadId: String): HelperApplicationResponse = execute {
        network.httpClient.post("$applicationUrl/profile/photo/complete") {
            bearerAuth(accessToken)
            contentType(ContentType.Application.Json)
            setBody(CompleteProfilePhotoUploadRequest(uploadId))
        }.body()
    }

    suspend fun removeProfilePhoto(accessToken: String): HelperApplicationResponse = execute {
        network.httpClient.delete("$applicationUrl/profile/photo") { bearerAuth(accessToken) }.body()
    }

    suspend fun approveForLocalDevelopment(accessToken: String): HelperApplicationResponse = execute {
        network.httpClient.post("$applicationUrl/development/approve") { bearerAuth(accessToken) }.body()
    }

    private suspend fun <T> execute(block: suspend () -> T): T = try {
        block()
    } catch (cause: io.ktor.client.plugins.ResponseException) {
        val error = runCatching { cause.response.body<AuthenticationErrorResponse>() }.getOrNull()
        throw DigibuddyApiException(
            code = error?.code ?: "NETWORK_ERROR",
            message = error?.message ?: "The request could not be completed. Please try again.",
            cause = cause,
        )
    }

    companion object {
        fun forLocalDevelopment(network: DigibuddyNetworkClient = createDigibuddyNetworkClient()) =
            HelperAccountApiClient(network, localDevelopmentApiBaseUrl())
    }
}

class DigibuddyApiException(val code: String, override val message: String, cause: Throwable? = null) :
    Exception(message, cause)
