package com.digibuddy.shared.helper.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Assignment
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.digibuddy.shared.contracts.BookingSummaryResponse
import com.digibuddy.shared.contracts.HelperApplicationResponse
import com.digibuddy.shared.contracts.HelperOnboardingStep
import com.digibuddy.shared.contracts.UpdateHelperProfileRequest
import com.digibuddy.shared.designsystem.DigibuddyCard
import com.digibuddy.shared.designsystem.DigibuddyColors
import com.digibuddy.shared.designsystem.DigibuddySectionHeader
import com.digibuddy.shared.designsystem.FriendlyEmptyState
import com.digibuddy.shared.designsystem.InitialAvatar
import com.digibuddy.shared.designsystem.StatusPill

enum class HelperTab(val label: String, val icon: ImageVector) {
    REQUESTS("Requests", Icons.Rounded.Inbox),
    JOBS("Jobs", Icons.AutoMirrored.Rounded.Assignment),
    CHATS("Chats", Icons.Rounded.ChatBubbleOutline),
    PROFILE("Profile", Icons.Rounded.Person),
}

@Composable
fun HelperDashboardShell(
    displayName: String?,
    coordinator: HelperDashboardCoordinator,
    onChoosePhoto: () -> Unit,
    onSignOut: () -> Unit,
) {
    var selected by remember { mutableStateOf(HelperTab.REQUESTS) }
    val state by coordinator.state.collectAsState()
    LaunchedEffect(coordinator) { coordinator.load() }
    Scaffold(
        bottomBar = {
            NavigationBar {
                HelperTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selected == tab,
                        onClick = { selected = tab },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            HelperWorkspaceHeader(displayName)
            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(12.dp))
            }
            state.message?.let {
                Text(it, color = DigibuddyColors.Success, modifier = Modifier.padding(horizontal = 12.dp))
            }
            Box(Modifier.fillMaxSize()) {
                when (selected) {
                    HelperTab.REQUESTS -> RequestsPage(state, coordinator)
                    HelperTab.JOBS -> JobsPage(state, coordinator)
                    HelperTab.CHATS -> ChatsPage(state, coordinator)
                    HelperTab.PROFILE -> ProfilePage(state, coordinator, onChoosePhoto, onSignOut)
                }
            }
        }
    }
}

@Composable
private fun HelperWorkspaceHeader(displayName: String?) {
    Surface(color = DigibuddyColors.Navy, contentColor = Color.White) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("DIGIBUDDY HELPERS", color = DigibuddyColors.Gold, style = MaterialTheme.typography.labelMedium)
                Text(
                    displayName?.let { "Welcome, $it" } ?: "Helper workspace",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
            StatusPill("Approved", DigibuddyColors.Gold)
        }
    }
}

@Composable
private fun RequestsPage(state: HelperDashboardState, coordinator: HelperDashboardCoordinator) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    DigibuddySectionHeader("New requests")
                    Text(
                        "Only real requests sent to your approved profile appear here.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(coordinator::refreshRequests, enabled = !state.loadingRequests) {
                    if (state.loadingRequests) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Rounded.Refresh, "Refresh requests")
                    }
                }
            }
        }
        if (state.requests.isEmpty() && !state.loadingRequests) {
            item {
                FriendlyEmptyState(
                    "No new requests",
                    "When a customer books your profile, the request will appear here. Use Refresh after testing the customer app.",
                    "R",
                )
            }
        } else {
            items(state.requests, key = { it.bookingId }) { request ->
                BookingCard(request) {
                    Button({ coordinator.accept(request.bookingId) }, Modifier.weight(1f)) { Text("Accept") }
                    OutlinedButton({ coordinator.decline(request.bookingId) }, Modifier.weight(1f)) { Text("Decline") }
                    TextButton({ coordinator.openBookingChat(request.bookingId) }, Modifier.fillMaxWidth()) {
                        Text("Message customer")
                    }
                }
            }
        }
    }
}

@Composable
private fun JobsPage(state: HelperDashboardState, coordinator: HelperDashboardCoordinator) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                DigibuddySectionHeader("Your jobs")
                IconButton(coordinator::refreshJobs) { Icon(Icons.Rounded.Refresh, "Refresh jobs") }
            }
        }
        if (state.jobs.isEmpty() && !state.loadingJobs) {
            item { FriendlyEmptyState("No jobs yet", "Accepted requests and completed jobs will appear here.", "J") }
        } else {
            items(state.jobs, key = { it.bookingId }) { job ->
                BookingCard(job) {
                    OutlinedButton(
                        { coordinator.openBookingChat(job.bookingId) },
                        Modifier.fillMaxWidth(),
                    ) { Text("Message customer") }
                }
            }
        }
    }
}

@Composable
private fun BookingCard(booking: BookingSummaryResponse, actions: @Composable RowScope.() -> Unit) {
    DigibuddyCard {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            StatusPill(pretty(booking.status.name), DigibuddyColors.Gold)
            Text(money(booking.price.totalCents), fontWeight = FontWeight.Bold)
        }
        Text(booking.serviceName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Customer: ${booking.customerDisplayName ?: "Customer"}")
        Text("${pretty(booking.serviceMode)} - ${booking.generalLocation ?: "Online"}")
        Text(booking.scheduledStart, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(booking.statusExplanation)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), content = actions)
    }
}

@Composable
private fun ChatsPage(state: HelperDashboardState, coordinator: HelperDashboardCoordinator) {
    val selected = state.selectedConversation
    if (selected != null) {
        var message by remember(selected.conversationId) { mutableStateOf("") }
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(coordinator::closeConversation) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                InitialAvatar(selected.otherParticipantDisplayName, 42.dp)
                Spacer(Modifier.size(10.dp))
                Text(selected.otherParticipantDisplayName, style = MaterialTheme.typography.titleMedium)
            }
            HorizontalDivider()
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.messages, key = { it.messageId }) { chat ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = if (chat.senderIsCurrentUser) Arrangement.End else Arrangement.Start,
                    ) {
                        Surface(
                            color = if (chat.senderIsCurrentUser) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            shape = MaterialTheme.shapes.medium,
                        ) {
                            Text(
                                chat.body,
                                Modifier.fillMaxWidth(.78f).padding(12.dp),
                                color = if (chat.senderIsCurrentUser) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                }
            }
            OutlinedTextField(
                message,
                { message = it.take(2_000) },
                Modifier.fillMaxWidth(),
                label = { Text("Message") },
            )
            Button(
                onClick = {
                    coordinator.sendMessage(message)
                    message = ""
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = message.isNotBlank(),
            ) { Text("Send") }
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                DigibuddySectionHeader("Messages")
                IconButton(coordinator::refreshChats) { Icon(Icons.Rounded.Refresh, "Refresh messages") }
            }
        }
        if (state.conversations.isEmpty() && !state.loadingChats) {
            item { FriendlyEmptyState("No messages yet", "Customer conversations will appear here.", "M") }
        } else {
            items(state.conversations, key = { it.conversationId }) { conversation ->
                DigibuddyCard(Modifier.clickable { coordinator.openConversation(conversation) }) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        InitialAvatar(conversation.otherParticipantDisplayName)
                        Column(Modifier.weight(1f)) {
                            Text(conversation.otherParticipantDisplayName, fontWeight = FontWeight.Bold)
                            Text(
                                conversation.lastMessagePreview,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfilePage(
    state: HelperDashboardState,
    coordinator: HelperDashboardCoordinator,
    onChoosePhoto: () -> Unit,
    onSignOut: () -> Unit,
) {
    val application = state.application
    if (application == null) {
        Column(Modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            TextButton(coordinator::refreshProfile) { Text("Retry") }
        }
        return
    }
    var legalFirst by remember(application) {
        mutableStateOf(application.value(HelperOnboardingStep.LEGAL_NAME, "legalFirstName"))
    }
    var legalLast by remember(application) {
        mutableStateOf(application.value(HelperOnboardingStep.LEGAL_NAME, "legalLastName"))
    }
    var displayName by remember(application) {
        mutableStateOf(application.value(HelperOnboardingStep.PUBLIC_PROFILE, "displayName"))
    }
    var headline by remember(application) {
        mutableStateOf(application.value(HelperOnboardingStep.PUBLIC_PROFILE, "headline"))
    }
    var biography by remember(application) {
        mutableStateOf(application.value(HelperOnboardingStep.PUBLIC_PROFILE, "biography"))
    }
    var zip by remember(application) {
        mutableStateOf(application.value(HelperOnboardingStep.HOME_AND_SERVICE_MODE, "homeZip"))
    }
    var mode by remember(application) {
        mutableStateOf(application.value(HelperOnboardingStep.HOME_AND_SERVICE_MODE, "serviceMode"))
    }
    var area by remember(application) {
        mutableStateOf(application.value(HelperOnboardingStep.SERVICE_AREA, "serviceAreaSummary"))
    }
    var skills by remember(application) {
        mutableStateOf(application.list(HelperOnboardingStep.SKILLS, "skillIds").joinToString(", "))
    }
    var services by remember(application) {
        mutableStateOf(application.list(HelperOnboardingStep.SERVICES, "serviceCategoryIds").joinToString(", "))
    }
    var years by remember(application) {
        mutableStateOf(application.value(HelperOnboardingStep.EXPERIENCE, "yearsExperience"))
    }
    var languages by remember(application) {
        mutableStateOf(application.list(HelperOnboardingStep.LANGUAGES, "languages").joinToString(", "))
    }
    var availability by remember(application) {
        mutableStateOf(application.value(HelperOnboardingStep.AVAILABILITY, "availabilitySummary"))
    }
    var certifications by remember(application) {
        mutableStateOf(application.list(HelperOnboardingStep.CERTIFICATIONS, "certifications").joinToString(", "))
    }
    var portfolio by remember(application) {
        mutableStateOf(application.list(HelperOnboardingStep.PORTFOLIO, "portfolio").joinToString(", "))
    }
    ScrollPage {
        DigibuddySectionHeader("Helper profile")
        Text("Update the information customers see and the private details Digibuddy uses for matching.")
        DigibuddyCard {
            Text("Profile photo", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                if (application.value(HelperOnboardingStep.PROFILE_MEDIA, "profilePictureUrl").isBlank()) {
                    "No photo selected"
                } else {
                    "Photo uploaded"
                },
            )
            Button(onChoosePhoto, Modifier.fillMaxWidth(), enabled = !state.uploadingPhoto) {
                Text(if (state.uploadingPhoto) "Uploading..." else "Choose photo from files")
            }
            TextButton(coordinator::removePhoto, Modifier.fillMaxWidth()) { Text("Remove photo") }
            Text("JPEG, PNG, or WebP; maximum 5 MB.", style = MaterialTheme.typography.bodySmall)
        }
        DigibuddySectionHeader("Private account information")
        ProfileField("Legal first name", legalFirst) { legalFirst = it }
        ProfileField("Legal last name", legalLast) { legalLast = it }
        ProfileField("Home ZIP code", zip) { zip = it.filter(Char::isDigit).take(5) }
        DigibuddySectionHeader("Public profile")
        ProfileField("Public display name", displayName) { displayName = it }
        ProfileField("Headline", headline) { headline = it }
        ProfileField("Biography", biography, singleLine = false) { biography = it }
        ProfileField("Service mode (REMOTE, IN_PERSON, or BOTH)", mode) { mode = it.uppercase() }
        ProfileField("Service area", area) { area = it }
        ProfileField("Skills, separated by commas", skills) { skills = it }
        ProfileField("Service category IDs, separated by commas", services) { services = it }
        ProfileField("Years of experience", years) { years = it.filter(Char::isDigit).take(2) }
        ProfileField("Languages, separated by commas", languages) { languages = it }
        ProfileField("Availability", availability) { availability = it }
        ProfileField("Certifications, separated by commas (optional)", certifications) { certifications = it }
        ProfileField("Portfolio links, separated by commas (optional)", portfolio) { portfolio = it }
        DigibuddyCard {
            Text("Pricing", fontWeight = FontWeight.Bold)
            Text("Customer prices and helper pay are controlled by Digibuddy and cannot be edited here.")
        }
        Button(
            onClick = {
                coordinator.updateProfile(
                    UpdateHelperProfileRequest(
                        legalFirst,
                        legalLast,
                        displayName,
                        headline,
                        biography,
                        zip,
                        mode,
                        area,
                        commaList(skills),
                        commaList(services),
                        years.toIntOrNull() ?: 0,
                        commaList(languages),
                        availability,
                        commaList(certifications),
                        commaList(portfolio),
                    ),
                )
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.savingProfile && zip.length == 5,
        ) { Text(if (state.savingProfile) "Saving..." else "Save all changes") }
        OutlinedButton(onSignOut, Modifier.fillMaxWidth()) { Text("Sign out") }
    }
}

@Composable
private fun ProfileField(label: String, value: String, singleLine: Boolean = true, change: (String) -> Unit) {
    OutlinedTextField(
        value,
        change,
        Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 4,
    )
}

@Composable
private fun ScrollPage(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content,
    )
}

private fun HelperApplicationResponse.value(step: HelperOnboardingStep, key: String) =
    steps.firstOrNull { it.step == step }?.values?.get(key).orEmpty()

private fun HelperApplicationResponse.list(step: HelperOnboardingStep, key: String) =
    steps.firstOrNull { it.step == step }?.listValues?.get(key).orEmpty()

private fun commaList(value: String) = value.split(',').map(String::trim).filter(String::isNotEmpty).distinct()
private fun pretty(value: String) = value.lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase)
private fun money(cents: Int) = "$${cents / 100}.${(cents % 100).toString().padStart(2, '0')}"
