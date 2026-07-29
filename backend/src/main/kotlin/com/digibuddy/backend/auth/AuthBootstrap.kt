package com.digibuddy.backend.auth

import com.digibuddy.backend.migrations.DatabaseMigrationSettings
import com.digibuddy.backend.migrations.DatabaseMigrator
import io.ktor.server.config.ApplicationConfig

@Suppress("LongMethod")
fun createDefaultAuthService(config: ApplicationConfig): AuthService {
    val environment = config.propertyOrNull("digibuddy.environment")?.getString() ?: "local-development"
    val authConfig = config.config("digibuddy.authentication")
    val repositoryName = authConfig.propertyOrNull("repository")?.getString() ?: "memory"
    val providerName = authConfig.propertyOrNull("otpProvider")?.getString() ?: "development"
    val tokenPepper = authConfig.propertyOrNull("tokenPepper")?.getString()
        ?: "local-development-token-pepper-change-me"
    val identifierKey = authConfig.propertyOrNull("identifierKey")?.getString()
        ?: "local-development-identifier-key-change-me"
    if (environment != "local-development") {
        require(repositoryName == "postgresql") {
            "Production-like environments must use AUTH_REPOSITORY=postgresql"
        }
        require(providerName == "twilio-verify") {
            "Production-like environments must use AUTH_OTP_PROVIDER=twilio-verify"
        }
        require(!tokenPepper.contains("local-development")) { "AUTH_TOKEN_PEPPER must be supplied securely" }
        require(!identifierKey.contains("local-development")) { "AUTH_IDENTIFIER_KEY must be supplied securely" }
    }
    val tokenHasher = SecretHasher(tokenPepper)
    val repository = when (repositoryName) {
        "memory" -> {
            require(environment == "local-development") {
                "The in-memory authentication repository is restricted to local-development"
            }
            InMemoryAuthRepository()
        }
        "postgresql" -> {
            val database = config.config("digibuddy.database")
            val settings = DatabaseMigrationSettings(
                jdbcUrl = database.property("jdbcUrl").getString(),
                username = database.property("username").getString(),
                password = database.property("password").getString(),
            )
            DatabaseMigrator(settings).migrate()
            PostgresAuthRepository(settings.jdbcUrl, settings.username, settings.password)
        }
        else -> error("Unsupported AUTH_REPOSITORY: $repositoryName")
    }
    val provider = when (providerName) {
        "development" -> {
            require(environment == "local-development") {
                "The development OTP adapter is restricted to local-development"
            }
            DevelopmentOtpProvider(tokenHasher)
        }
        "twilio-verify" -> TwilioVerifyOtpProvider(
            accountSid = config.property("digibuddy.developmentAdapters.twilioAccountSid").getString(),
            authToken = config.property("digibuddy.developmentAdapters.twilioAuthToken").getString(),
            verifyServiceSid = authConfig.property("twilioVerifyServiceSid").getString(),
        )
        else -> error("Unsupported AUTH_OTP_PROVIDER: $providerName")
    }
    return AuthService(
        repository = repository,
        otpProvider = provider,
        tokenHasher = tokenHasher,
        fingerprinter = IdentifierFingerprinter(identifierKey),
        passwordHasher = PasswordHasher(),
    )
}
