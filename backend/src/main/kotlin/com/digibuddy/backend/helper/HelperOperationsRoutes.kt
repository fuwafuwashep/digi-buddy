package com.digibuddy.backend.helper

import com.digibuddy.backend.auth.AuthService
import com.digibuddy.backend.auth.AuthenticatedPrincipal
import com.digibuddy.backend.auth.AuthenticationException
import com.digibuddy.backend.auth.authPrincipal
import com.digibuddy.shared.contracts.HelperAccountStatus
import com.digibuddy.shared.contracts.HelperOnboardingStep
import com.digibuddy.shared.contracts.StaffHelperApplicationListResponse
import com.digibuddy.shared.contracts.StaffHelperApplicationSummaryResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.request.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import java.security.MessageDigest
import java.util.UUID

fun Route.helperOperationsRoutes(
    service: HelperApplicationService,
    approvalToken: String?,
) {
    if (approvalToken.isNullOrBlank()) {
        return
    }

    route("/api/v1/internal/helper-applications") {
        get("/under-review") {
            if (
                !call.validOperationsToken(
                    approvalToken,
                )
            ) {
                call.respond(
                    HttpStatusCode.NotFound,
                )
                return@get
            }

            call.respond(
                service.reviewQueue(),
            )
        }

        post("/{userId}/approve") {
            if (
                !call.validOperationsToken(
                    approvalToken,
                )
            ) {
                call.respond(
                    HttpStatusCode.NotFound,
                )
                return@post
            }

            val userId =
                call.helperUserId()
                    ?: return@post call.respond(
                        HttpStatusCode.BadRequest,
                    )

            call.respond(
                service.review(
                    applicationUserId = userId,
                    target =
                        HelperAccountStatus.APPROVED,
                    staffUserId = null,
                    reason =
                        "Manual private beta approval",
                ),
            )
        }
    }
}

fun Route.staffHelperOperationsRoutes(
    service: HelperApplicationService,
    authentication: AuthService,
) {
    authenticate("access") {
        route(
            "/api/v1/staff/helper-applications",
        ) {
            get("/under-review") {
                val principal =
                    call.authPrincipal()

                authentication.requireStaff(
                    principal,
                )

                call.respond(
                    service.reviewQueue(),
                )
            }

            post("/{userId}/approve") {
                val principal =
                    call.authPrincipal()

                authentication.requireStaff(
                    principal,
                )

                val userId =
                    call.helperUserId()
                        ?: return@post call.respond(
                            HttpStatusCode.BadRequest,
                        )

                call.respond(
                    service.review(
                        applicationUserId =
                            userId,
                        target =
                            HelperAccountStatus.APPROVED,
                        staffUserId =
                            principal.userId,
                        reason =
                            "Approved from DigiBuddy Admin",
                    ),
                )
            }
        }
    }
}

private fun HelperApplicationService.reviewQueue():
    StaffHelperApplicationListResponse =
    StaffHelperApplicationListResponse(
        items =
            applicationsUnderReview()
                .map { application ->
                    StaffHelperApplicationSummaryResponse(
                        userId =
                            application.userId.toString(),
                        applicationId =
                            application.id.toString(),
                        displayName =
                            application.steps[
                                HelperOnboardingStep.PUBLIC_PROFILE
                            ]
                                ?.payload
                                ?.values
                                ?.get("displayName"),
                        status =
                            application.status.name,
                        submittedAt =
                            application.submittedAt
                                ?.toString(),
                    )
                },
    )

private fun AuthService.requireStaff(
    principal: AuthenticatedPrincipal,
) {
    val account =
        currentUser(principal)

    if ("STAFF" !in account.roles) {
        throw AuthenticationException(
            "STAFF_REQUIRED",
            "Staff access is required.",
            403,
        )
    }
}

private fun ApplicationCall.helperUserId():
    UUID? =
    runCatching {
        UUID.fromString(
            parameters["userId"],
        )
    }.getOrNull()

private fun ApplicationCall.validOperationsToken(
    expected: String,
): Boolean {
    val supplied =
        request.header(
            "X-Digibuddy-Operations-Token",
        ) ?: return false

    return MessageDigest.isEqual(
        expected.toByteArray(
            Charsets.UTF_8,
        ),
        supplied.toByteArray(
            Charsets.UTF_8,
        ),
    )
}
