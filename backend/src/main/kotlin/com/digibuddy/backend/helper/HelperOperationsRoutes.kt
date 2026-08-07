package com.digibuddy.backend.helper

import com.digibuddy.shared.contracts.HelperAccountStatus
import com.digibuddy.shared.contracts.HelperOnboardingStep
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.request.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import java.security.MessageDigest
import java.util.UUID

@Serializable
private data class HelperReviewQueueItem(
    val userId: String,
    val applicationId: String,
    val displayName: String?,
    val status: String,
    val submittedAt: String?,
)

@Serializable
private data class HelperReviewQueueResponse(
    val items: List<HelperReviewQueueItem>,
)

fun Route.helperOperationsRoutes(
    service: HelperApplicationService,
    approvalToken: String?,
) {
    if (approvalToken.isNullOrBlank()) {
        return
    }

    route("/api/v1/internal/helper-applications") {
        get("/under-review") {
            if (!call.validOperationsToken(approvalToken)) {
                call.respond(HttpStatusCode.NotFound)
                return@get
            }

            val items =
                service.applicationsUnderReview()
                    .map { application ->
                        HelperReviewQueueItem(
                            userId = application.userId.toString(),
                            applicationId = application.id.toString(),
                            displayName =
                                application.steps[
                                    HelperOnboardingStep.PUBLIC_PROFILE
                                ]?.payload?.values?.get(
                                    "displayName",
                                ),
                            status = application.status.name,
                            submittedAt =
                                application.submittedAt?.toString(),
                        )
                    }

            call.respond(
                HelperReviewQueueResponse(items),
            )
        }

        post("/{userId}/approve") {
            if (!call.validOperationsToken(approvalToken)) {
                call.respond(HttpStatusCode.NotFound)
                return@post
            }

            val userId =
                runCatching {
                    UUID.fromString(
                        call.parameters["userId"],
                    )
                }.getOrNull()

            if (userId == null) {
                call.respond(HttpStatusCode.BadRequest)
                return@post
            }

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

private fun ApplicationCall.validOperationsToken(
    expected: String,
): Boolean {
    val supplied =
        request.header(
            "X-Digibuddy-Operations-Token",
        ) ?: return false

    return MessageDigest.isEqual(
        expected.toByteArray(Charsets.UTF_8),
        supplied.toByteArray(Charsets.UTF_8),
    )
}
