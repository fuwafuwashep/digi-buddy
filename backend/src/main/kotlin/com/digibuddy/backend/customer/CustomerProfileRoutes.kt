@file:Suppress("LongMethod")

package com.digibuddy.backend.customer

import com.digibuddy.backend.auth.authPrincipal
import com.digibuddy.shared.contracts.CompleteCustomerOnboardingRequest
import com.digibuddy.shared.contracts.CompleteProfilePhotoUploadRequest
import com.digibuddy.shared.contracts.DeleteAccountRequest
import com.digibuddy.shared.contracts.ProfilePhotoUploadRequest
import com.digibuddy.shared.contracts.SaveAddressRequest
import com.digibuddy.shared.contracts.UpdateAccessibilitySettingsRequest
import com.digibuddy.shared.contracts.UpdateCustomerNameRequest
import com.digibuddy.shared.contracts.UpdateNotificationSettingsRequest
import com.digibuddy.shared.contracts.UpdatePrivacySettingsRequest
import com.digibuddy.shared.contracts.UpdateTechnologyPreferencesRequest
import com.digibuddy.shared.contracts.UpdateZipCodeRequest
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
import java.util.UUID

fun Route.customerProfileRoutes(service: CustomerProfileService) {
    route("/api/v1") {
        put("/uploads/local/{uploadId}") {
            val id = runCatching { UUID.fromString(call.parameters["uploadId"]) }.getOrNull()
                ?: return@put call.respond(HttpStatusCode.BadRequest)
            service.acceptLocalUpload(id, call.request.contentType().toString(), call.receive<ByteArray>())
            call.respond(HttpStatusCode.NoContent)
        }
        authenticate("access") {
            route("/customer") {
                get("/profile") { call.respond(service.profile(call.authPrincipal())) }
                post("/onboarding") {
                    call.respond(
                        service.completeOnboarding(
                            call.authPrincipal(),
                            call.receive<CompleteCustomerOnboardingRequest>(),
                        ),
                    )
                }
                put("/profile/name") {
                    val request = call.receive<UpdateCustomerNameRequest>()
                    call.respond(service.updateName(call.authPrincipal(), request.firstName, request.lastName))
                }
                put("/profile/zip") {
                    call.respond(service.updateZip(call.authPrincipal(), call.receive<UpdateZipCodeRequest>().zipCode))
                }
                put("/profile/technology-preferences") {
                    call.respond(
                        service.updatePreferences(
                            call.authPrincipal(),
                            call.receive<UpdateTechnologyPreferencesRequest>().technologyPreferences,
                        ),
                    )
                }
                post("/profile/addresses") {
                    call.respond(service.saveAddress(call.authPrincipal(), call.receive<SaveAddressRequest>()))
                }
                put("/settings/notifications") {
                    val request = call.receive<UpdateNotificationSettingsRequest>()
                    call.respond(
                        service.updateNotifications(call.authPrincipal(), request.enabled, request.permissionStatus),
                    )
                }
                put("/settings/privacy") {
                    call.respond(
                        service.updatePrivacy(
                            call.authPrincipal(),
                            call.receive<UpdatePrivacySettingsRequest>().locationPermissionStatus,
                        ),
                    )
                }
                put("/settings/accessibility") {
                    call.respond(
                        service.updateAccessibility(
                            call.authPrincipal(),
                            call.receive<UpdateAccessibilitySettingsRequest>(),
                        ),
                    )
                }
                post("/profile/photo/uploads") {
                    call.respond(
                        service.createPhotoUpload(call.authPrincipal(), call.receive<ProfilePhotoUploadRequest>()),
                    )
                }
                post("/profile/photo/complete") {
                    call.respond(
                        service.completePhoto(
                            call.authPrincipal(),
                            call.receive<CompleteProfilePhotoUploadRequest>().uploadId,
                        ),
                    )
                }
                delete("/profile/photo") { call.respond(service.removePhoto(call.authPrincipal())) }
                get("/security") { call.respond(service.security(call.authPrincipal())) }
                post("/privacy/data-export") { call.respond(service.requestExport(call.authPrincipal())) }
                post("/account/delete") {
                    call.respond(
                        service.requestDeletion(
                            call.authPrincipal(),
                            call.receive<DeleteAccountRequest>().confirmation,
                        ),
                    )
                }
            }
        }
    }
}
