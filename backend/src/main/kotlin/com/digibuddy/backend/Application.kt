package com.digibuddy.backend

import com.digibuddy.backend.auth.AuthService
import com.digibuddy.backend.auth.authenticationRoutes
import com.digibuddy.backend.auth.createDefaultAuthService
import com.digibuddy.backend.auth.installAuthentication
import com.digibuddy.backend.booking.BookingService
import com.digibuddy.backend.booking.InMemoryBookingRepository
import com.digibuddy.backend.booking.PostgresBookingRepository
import com.digibuddy.backend.booking.bookingRoutes
import com.digibuddy.backend.catalog.HelperCatalogService
import com.digibuddy.backend.catalog.InMemoryHelperCatalogRepository
import com.digibuddy.backend.catalog.PostgresHelperCatalogRepository
import com.digibuddy.backend.catalog.helperCatalogRoutes
import com.digibuddy.backend.chat.ChatService
import com.digibuddy.backend.chat.InMemoryChatRepository
import com.digibuddy.backend.chat.PostgresChatRepository
import com.digibuddy.backend.chat.chatRoutes
import com.digibuddy.backend.customer.CustomerProfileService
import com.digibuddy.backend.customer.InMemoryCustomerProfileRepository
import com.digibuddy.backend.customer.LocalDevelopmentProfileObjectStorage
import com.digibuddy.backend.customer.PostgresCustomerProfileRepository
import com.digibuddy.backend.customer.customerProfileRoutes
import com.digibuddy.backend.helper.HelperApplicationService
import com.digibuddy.backend.helper.helperOperationsRoutes
import com.digibuddy.backend.helper.staffHelperOperationsRoutes
import com.digibuddy.backend.helper.HelperStartupService
import com.digibuddy.backend.helper.InMemoryHelperApplicationRepository
import com.digibuddy.backend.helper.PostgresHelperApplicationRepository
import com.digibuddy.backend.helper.helperApplicationRoutes
import com.digibuddy.backend.helper.helperStartupRoutes
import com.digibuddy.backend.notification.LocalDevelopmentNotificationProvider
import com.digibuddy.backend.notification.NotificationService
import com.digibuddy.backend.notification.notificationRoutes
import com.digibuddy.backend.payment.LocalDevelopmentPaymentProvider
import com.digibuddy.backend.payment.PaymentService
import com.digibuddy.backend.payment.paymentRoutes
import com.digibuddy.shared.contracts.HealthResponse
import io.ktor.http.ContentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.netty.EngineMain
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import kotlinx.serialization.json.Json

fun main(args: Array<String>) {
    EngineMain.main(args)
}

@Suppress("LongMethod")
fun Application.configuredModule() {
    val authService = createDefaultAuthService(environment.config)
    val helperApprovalToken =
        environment.config
            .propertyOrNull(
                "digibuddy.helperApprovalToken",
            )
            ?.getString()

    val allowDevelopmentHelperApproval =
        environment.config
            .propertyOrNull(
                "digibuddy.allowDevelopmentHelperApproval",
            )
            ?.getString()
            ?.equals(
                "true",
                ignoreCase = true,
            ) == true
    val repositoryName =
        environment.config.propertyOrNull("digibuddy.authentication.repository")?.getString() ?: "memory"
    val database = environment.config.config("digibuddy.database")
    val catalogRepository = if (repositoryName == "postgresql") {
        PostgresHelperCatalogRepository(
            database.property("jdbcUrl").getString(),
            database.property("username").getString(),
            database.property("password").getString(),
        )
    } else {
        InMemoryHelperCatalogRepository()
    }
    val helperApplicationRepository = if (repositoryName == "postgresql") {
        PostgresHelperApplicationRepository(
            database.property("jdbcUrl").getString(),
            database.property("username").getString(),
            database.property("password").getString(),
        )
    } else {
        InMemoryHelperApplicationRepository()
    }
    val helperApplicationService = HelperApplicationService(
        helperApplicationRepository,
        authService,
        onApproved = { userId, profile ->
            (catalogRepository as? InMemoryHelperCatalogRepository)?.upsertApprovedHelper(userId, profile)
        },
        onStatusChanged = { userId, status ->
            (catalogRepository as? InMemoryHelperCatalogRepository)?.updateHelperStatus(userId, status)
        },
    )
    val customerProfileRepository =
        if (repositoryName == "postgresql") {
            PostgresCustomerProfileRepository(
                database.property("jdbcUrl").getString(),
                database.property("username").getString(),
                database.property("password").getString(),
            )
        } else {
            InMemoryCustomerProfileRepository()
        }

    val profiles = CustomerProfileService(
        customerProfileRepository,
        authService,
        LocalDevelopmentProfileObjectStorage(),
    )
    val catalog = HelperCatalogService(catalogRepository)

    val bookingRepository =
        if (repositoryName == "postgresql") {
            PostgresBookingRepository(
                database.property("jdbcUrl").getString(),
                database.property("username").getString(),
                database.property("password").getString(),
            )
        } else {
            InMemoryBookingRepository()
        }

    val chatRepository =
        if (repositoryName == "postgresql") {
            PostgresChatRepository(
                database.property("jdbcUrl").getString(),
                database.property("username").getString(),
                database.property("password").getString(),
            )
        } else {
            InMemoryChatRepository()
        }

    val chat = ChatService(
        repository = chatRepository,
        helperAccountResolver = { helperId ->
            runCatching { catalog.accountReference(helperId) }.getOrNull()?.let { it.userId to it.displayName }
        },
        customerDisplayName = profiles::publicDisplayName,
    )
    val bookings = BookingService(
        repository = bookingRepository,
        helperAccountResolver = { helperId ->
            runCatching { catalog.accountReference(helperId) }.getOrNull()?.let { it.userId to it.displayName }
        },
        customerDisplayName = profiles::publicDisplayName,
        requireHelperEligibility = helperApplicationService::requireCanReceivePaidRequest,
        onBookingCreated = { booking ->
            chat.openBookingConversation(
                booking.customerId,
                booking.helperUserId,
                booking.customerDisplayName,
                booking.request.helperDisplayName,
                booking.id,
            )
        },
    )
    module(
        RuntimeServices(
            auth = authService,
            profiles = profiles,
            catalog = catalog,
            helperApplications = helperApplicationService,
            allowDevelopmentHelperApproval =
                allowDevelopmentHelperApproval,
            helperApprovalToken =
                helperApprovalToken,
            bookings = bookings,
            chat = chat,
        ),
    )
}

fun Application.module() = configuredModule()

fun Application.module(
    authService: AuthService,
    catalogService: HelperCatalogService = HelperCatalogService(InMemoryHelperCatalogRepository()),
    helperApplicationService: HelperApplicationService = HelperApplicationService(
        InMemoryHelperApplicationRepository(),
        authService,
    ),
) {
    module(
        authService,
        CustomerProfileService(
            InMemoryCustomerProfileRepository(),
            authService,
            LocalDevelopmentProfileObjectStorage(),
        ),
        catalogService,
        helperApplicationService,
    )
}

fun Application.module(
    authService: AuthService,
    customerProfileService: CustomerProfileService,
    catalogService: HelperCatalogService = HelperCatalogService(InMemoryHelperCatalogRepository()),
    helperApplicationService: HelperApplicationService = HelperApplicationService(
        InMemoryHelperApplicationRepository(),
        authService,
    ),
) {
    module(
        RuntimeServices(
            auth = authService,
            profiles = customerProfileService,
            catalog = catalogService,
            helperApplications = helperApplicationService,
        ),
    )
}

private data class RuntimeServices(
    val auth: AuthService,
    val profiles: CustomerProfileService,
    val catalog: HelperCatalogService,
    val helperApplications: HelperApplicationService,
    val allowDevelopmentHelperApproval: Boolean = false,
    val helperApprovalToken: String? = null,
    val bookings: BookingService = BookingService(),
    val chat: ChatService = ChatService(),
    val payments: PaymentService? = null,
    val notifications: NotificationService = NotificationService(LocalDevelopmentNotificationProvider()),
)

private fun Application.module(services: RuntimeServices) {
    install(ContentNegotiation) {
        json(
            Json {
                prettyPrint = false
                explicitNulls = false
            },
            contentType = ContentType.Application.Json,
        )
    }

    installAuthentication(services.auth)
    install(WebSockets) {
        pingPeriodMillis = 20_000
        timeoutMillis = 20_000
        maxFrameSize = 1_048_576
    }

    routing {
        val resolvedPaymentService =
            services.payments ?: PaymentService(services.bookings, LocalDevelopmentPaymentProvider())
        get("/health") {
            call.respond(
                HealthResponse(
                    status = "ok",
                    service = "digibuddy-backend",
                ),
            )
        }
        authenticationRoutes(services.auth)
        customerProfileRoutes(services.profiles)
        helperCatalogRoutes(services.catalog)
        helperStartupRoutes(HelperStartupService(services.auth, services.catalog, services.helperApplications))
        helperApplicationRoutes(services.helperApplications, services.allowDevelopmentHelperApproval)
        helperOperationsRoutes(services.helperApplications, services.helperApprovalToken)
        staffHelperOperationsRoutes(services.helperApplications, services.auth)
        bookingRoutes(services.bookings)
        chatRoutes(services.chat)
        paymentRoutes(resolvedPaymentService)
        notificationRoutes(services.notifications)
    }
}
