package com.digibuddy.backend.helper

import com.digibuddy.backend.auth.authPrincipal
import com.digibuddy.shared.contracts.CompleteProfilePhotoUploadRequest
import com.digibuddy.shared.contracts.HelperApplicationStepRequest
import com.digibuddy.shared.contracts.HelperOnboardingStep
import com.digibuddy.shared.contracts.ProfilePhotoUploadRequest
import com.digibuddy.shared.contracts.UpdateHelperProfileRequest
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.request.contentType
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route

fun Route.helperApplicationRoutes(service: HelperApplicationService, allowDevelopmentApproval: Boolean = false) {
    put("/api/v1/helper/uploads/local/{uploadId}") {
        val id = runCatching { java.util.UUID.fromString(call.parameters["uploadId"]) }.getOrNull()
            ?: return@put call.respond(HttpStatusCode.BadRequest)
        service.acceptLocalProfilePhoto(id, call.request.contentType().toString(), call.receive<ByteArray>())
        call.respond(HttpStatusCode.NoContent)
    }
    authenticate("access") {
        route("/api/v1/helper/application") {
            get { call.respond(service.application(call.authPrincipal())) }
            put("/steps/{step}") {
                val step = runCatching { HelperOnboardingStep.valueOf(call.parameters["step"].orEmpty()) }
                    .getOrElse {
                        throw com.digibuddy.backend.auth.AuthenticationException(
                            "INVALID_HELPER_STEP",
                            "That onboarding step is not available.",
                            400,
                        )
                    }
                call.respond(service.saveStep(call.authPrincipal(), step, call.receive<HelperApplicationStepRequest>()))
            }
            post("/submit") { call.respond(service.submit(call.authPrincipal())) }
            post("/pause") { call.respond(service.setPaused(call.authPrincipal(), paused = true)) }
            post("/resume") { call.respond(service.setPaused(call.authPrincipal(), paused = false)) }
            put("/profile") {
                call.respond(service.updateProfile(call.authPrincipal(), call.receive<UpdateHelperProfileRequest>()))
            }
            post("/profile/photo/uploads") {
                call.respond(
                    service.createProfilePhotoUpload(call.authPrincipal(), call.receive<ProfilePhotoUploadRequest>()),
                )
            }
            post("/profile/photo/complete") {
                call.respond(
                    service.completeProfilePhoto(
                        call.authPrincipal(),
                        call.receive<CompleteProfilePhotoUploadRequest>().uploadId,
                    ),
                )
            }
            delete("/profile/photo") { call.respond(service.removeProfilePhoto(call.authPrincipal())) }
            if (allowDevelopmentApproval) {
                post("/development/approve") {
                    call.respond(service.approveForLocalDevelopment(call.authPrincipal()))
                }
            }
        }
    }
}
