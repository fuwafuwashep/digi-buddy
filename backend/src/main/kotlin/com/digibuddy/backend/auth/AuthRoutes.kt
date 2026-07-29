package com.digibuddy.backend.auth

import com.digibuddy.shared.contracts.AddEmailCredentialRequest
import com.digibuddy.shared.contracts.AuthenticationErrorResponse
import com.digibuddy.shared.contracts.AuthenticationMessageResponse
import com.digibuddy.shared.contracts.EmailPasswordLoginRequest
import com.digibuddy.shared.contracts.NormalizePhoneRequest
import com.digibuddy.shared.contracts.RefreshTokenRequest
import com.digibuddy.shared.contracts.StartPhoneVerificationRequest
import com.digibuddy.shared.contracts.VerifyPhoneCodeRequest
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.UserIdPrincipal
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.bearer
import io.ktor.server.auth.principal
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.plugins.statuspages.exception
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import java.util.UUID

fun Application.installAuthentication(authService: AuthService) {
    install(StatusPages) {
        exception<AuthenticationException> { call, cause ->
            cause.retryAfterSeconds?.let { call.response.headers.append("Retry-After", it.toString()) }
            call.respond(
                HttpStatusCode.fromValue(cause.httpStatus),
                AuthenticationErrorResponse(cause.errorCode, cause.message, cause.retryAfterSeconds),
            )
        }
    }
    install(Authentication) {
        bearer("access") {
            realm = "digibuddy-customer"
            authenticate { credential ->
                authService.authenticateAccessToken(credential.token)?.let { principal ->
                    UserIdPrincipal("${principal.userId}:${principal.sessionId}")
                }
            }
        }
    }
}

@Suppress("LongMethod")
fun Route.authenticationRoutes(authService: AuthService) {
    route("/api/v1/auth") {
        post("/phone/normalize") {
            val request = call.receive<NormalizePhoneRequest>()
            call.respond(authService.normalizePhone(request.phoneNumber, request.defaultRegion))
        }
        post("/phone/verifications") {
            val request = call.receive<StartPhoneVerificationRequest>()
            call.respond(
                authService.startPhoneVerification(request.phoneNumber, request.defaultRegion, call.sourceIp()),
            )
        }
        post("/phone/verifications/{attemptId}/resend") {
            call.respond(authService.resend(call.attemptId(), call.sourceIp()))
        }
        post("/phone/verify") {
            val request = call.receive<VerifyPhoneCodeRequest>()
            call.respond(
                authService.verifyPhoneCode(
                    attemptId = parseAttemptId(request.attemptId),
                    code = request.code,
                    deviceId = request.deviceId,
                    deviceName = request.deviceName,
                    sourceIp = call.sourceIp(),
                ),
            )
        }
        post("/email/login") {
            val request = call.receive<EmailPasswordLoginRequest>()
            call.respond(authService.startEmailPasswordLogin(request.email, request.password, call.sourceIp()))
        }
        post("/email/verify") {
            val request = call.receive<VerifyPhoneCodeRequest>()
            call.respond(
                authService.verifyPhoneCode(
                    attemptId = parseAttemptId(request.attemptId),
                    code = request.code,
                    deviceId = request.deviceId,
                    deviceName = request.deviceName,
                    sourceIp = call.sourceIp(),
                ),
            )
        }
        post("/refresh") {
            call.respond(authService.refresh(call.receive<RefreshTokenRequest>().refreshToken))
        }
        authenticate("access") {
            get("/me") {
                call.respond(authService.currentUser(call.authPrincipal()))
            }
            put("/email-credential") {
                val request = call.receive<AddEmailCredentialRequest>()
                authService.addEmailCredential(call.authPrincipal(), request.email, request.password)
                call.respond(AuthenticationMessageResponse("Email sign-in was added."))
            }
            post("/logout") {
                authService.logout(call.authPrincipal())
                call.respond(AuthenticationMessageResponse("Signed out from this device."))
            }
            post("/logout-all") {
                authService.logoutAll(call.authPrincipal())
                call.respond(AuthenticationMessageResponse("Signed out from all devices."))
            }
        }
    }
}

private fun io.ktor.server.application.ApplicationCall.sourceIp(): String = request.local.remoteHost

private fun io.ktor.server.application.ApplicationCall.attemptId(): UUID = parseAttemptId(parameters["attemptId"])

private fun parseAttemptId(value: String?): UUID = runCatching { UUID.fromString(value) }
    .getOrElse { throw AuthenticationException("INVALID_ATTEMPT", "Start verification again.", 400) }

internal fun io.ktor.server.application.ApplicationCall.authPrincipal(): AuthenticatedPrincipal {
    val parts = principal<UserIdPrincipal>()?.name?.split(':')
    if (parts?.size != 2) throw AuthenticationException("UNAUTHORIZED", "Authentication is required.", 401)
    return runCatching { AuthenticatedPrincipal(UUID.fromString(parts[0]), UUID.fromString(parts[1])) }
        .getOrElse { throw AuthenticationException("UNAUTHORIZED", "Authentication is required.", 401) }
}
