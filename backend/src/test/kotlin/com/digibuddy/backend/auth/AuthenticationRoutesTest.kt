package com.digibuddy.backend.auth

import com.digibuddy.backend.module
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class AuthenticationRoutesTest {
    @Test
    fun `protected endpoint rejects requests without an access token`() = testApplication {
        val tokenHasher = SecretHasher("route-test-pepper")
        application {
            module(
                AuthService(
                    repository = InMemoryAuthRepository(),
                    otpProvider = DevelopmentOtpProvider(tokenHasher),
                    tokenHasher = tokenHasher,
                    fingerprinter = IdentifierFingerprinter("route-test-key"),
                    passwordHasher = PasswordHasher(),
                ),
            )
        }

        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/v1/auth/me").status)
    }
}
