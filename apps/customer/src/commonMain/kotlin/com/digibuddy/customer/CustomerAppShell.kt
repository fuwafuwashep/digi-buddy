@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.digibuddy.customer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.digibuddy.shared.contracts.BookingDetailResponse
import com.digibuddy.shared.contracts.BookingStatus
import com.digibuddy.shared.contracts.BookingSummaryResponse
import com.digibuddy.shared.contracts.CustomerProfileResponse
import com.digibuddy.shared.contracts.HelperProfileResponse
import com.digibuddy.shared.contracts.HelperReviewResponse
import com.digibuddy.shared.contracts.HelperSummaryResponse
import com.digibuddy.shared.contracts.PaymentStatus
import com.digibuddy.shared.core.DigibuddyPricing
import com.digibuddy.shared.core.HelpPrice
import com.digibuddy.shared.core.MembershipPrice
import com.digibuddy.shared.designsystem.DigibuddyCard
import com.digibuddy.shared.designsystem.DigibuddyColors
import com.digibuddy.shared.designsystem.DigibuddySectionHeader
import com.digibuddy.shared.designsystem.FriendlyEmptyState
import com.digibuddy.shared.designsystem.InitialAvatar
import com.digibuddy.shared.designsystem.StatusPill
import com.digibuddy.shared.profile.CustomerProfileCoordinator

private enum class CustomerTab(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Rounded.Home),
    FIND("Find", Icons.Rounded.Search),
    BOOKINGS("Bookings", Icons.Rounded.CalendarMonth),
    CHATS("Chats", Icons.Rounded.ChatBubbleOutline),
    PROFILE("Profile", Icons.Rounded.Person),
}

@Composable
fun CustomerAppShell(
    profile: CustomerProfileResponse,
    profileCoordinator: CustomerProfileCoordinator,
    marketplace: MarketplaceCoordinator,
    bookings: BookingCoordinator,
    chats: ChatCoordinator,
    onSignOut: () -> Unit,
    accountSettings: @Composable () -> Unit,
) {
    var tab by remember { mutableStateOf(CustomerTab.HOME) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Digibuddy", fontWeight = FontWeight.Bold)
                        Text(
                            tab.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                        Text(
                            profile.firstName.take(1).uppercase(),
                            Modifier.padding(horizontal = 13.dp, vertical = 8.dp),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(Modifier.size(12.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                CustomerTab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tab = item },
                        icon = { Icon(item.icon, contentDescription = null) },
                        label = { Text(item.label) },
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                CustomerTab.HOME -> HomeTab(profile, onFind = { tab = CustomerTab.FIND })
                CustomerTab.FIND -> FindHelpersTab(
                    marketplace,
                    onBook = { helper ->
                        bookings.start(helper, profile.zipCode)
                        tab = CustomerTab.BOOKINGS
                    },
                    onMessage = { helper ->
                        tab = CustomerTab.CHATS
                        chats.startHelperConversation(helper.summary.helperId)
                    },
                )
                CustomerTab.BOOKINGS -> BookingsTab(bookings) { helperId ->
                    tab = CustomerTab.CHATS
                    chats.startHelperConversation(helperId)
                }
                CustomerTab.CHATS -> ChatsTab(chats)
                CustomerTab.PROFILE -> ProfileTab(
                    profile,
                    profileCoordinator,
                    onSignOut,
                    accountSettings,
                )
            }
        }
    }
}

@Composable
private fun HomeTab(profile: CustomerProfileResponse, onFind: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "Hi, ${profile.firstName}",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "What can we make easier today?",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = MaterialTheme.shapes.large,
            ) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(
                        "Friendly technology help",
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "Find a verified helper for patient, step-by-step support—in person or remotely.",
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = .9f),
                    )
                    Button(
                        onClick = onFind,
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.primary,
                        ),
                    ) {
                        Text("Find a helper")
                        Spacer(Modifier.size(8.dp))
                        Icon(Icons.Rounded.Search, contentDescription = null)
                    }
                }
            }
        }
        item { DigibuddySectionHeader("Popular help") }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(
                    listOf("Computer help", "Phone & tablet", "Wi-Fi", "Printer setup", "Technology lessons"),
                ) { label ->
                    AssistChip(onClick = onFind, label = { Text(label) })
                }
            }
        }
        item {
            DigibuddyCard {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Surface(shape = CircleShape, color = DigibuddyColors.Mist) {
                        Icon(
                            Icons.Rounded.Verified,
                            null,
                            Modifier.padding(12.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text("Your safety comes first", fontWeight = FontWeight.Bold)
                        Text(
                            "Helpers never see your private phone number or exact home location while you browse.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        item { DigibuddySectionHeader("Simple pricing") }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DigibuddyPricing.oneTimeHelp.forEach { option -> HelpPriceCard(option, onFind) }
            }
        }
        item { DigibuddySectionHeader("Monthly memberships") }
        item {
            DigibuddyCard {
                DigibuddyPricing.memberships.forEach { plan -> MembershipPriceRow(plan) }
                Text(
                    "Membership enrollment is coming soon. No subscription will be started or charged from this preview.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            DigibuddySectionHeader("Your bookings")
            FriendlyEmptyState(
                "Nothing scheduled yet",
                "When you request help, your appointments will appear here.",
                "◇",
            )
        }
    }
}

@Composable
private fun HelpPriceCard(option: HelpPrice, onFind: () -> Unit) {
    DigibuddyCard(Modifier.clickable(onClick = onFind)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text(option.name, fontWeight = FontWeight.Bold)
                Text(option.description, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(money(option.priceCents), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun MembershipPriceRow(plan: MembershipPrice) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(plan.name, fontWeight = FontWeight.Bold)
            Text(
                plan.includedIssues?.let { "$it help issues each month" } ?: "Unlimited help each month",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text("${money(plan.monthlyPriceCents)}/month", fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun FindHelpersTab(
    coordinator: MarketplaceCoordinator,
    onBook: (HelperProfileResponse) -> Unit,
    onMessage: (HelperProfileResponse) -> Unit,
) {
    val state by coordinator.state.collectAsState()
    val detail by coordinator.detail.collectAsState()
    LaunchedEffect(coordinator) { coordinator.load() }
    when (val selected = detail) {
        HelperDetailState.Closed -> FindHelpersResults(state, coordinator)
        HelperDetailState.Loading -> CenteredProgress("Loading helper profile…")
        is HelperDetailState.Failure -> FriendlyEmptyState("Profile unavailable", selected.message)
        is HelperDetailState.Ready -> HelperProfileScreen(
            selected.profile,
            selected.reviews,
            coordinator::closeHelper,
            onBook,
            onMessage,
        )
    }
}

@Composable
private fun FindHelpersResults(state: MarketplaceState, coordinator: MarketplaceCoordinator) {
    var search by remember { mutableStateOf("") }
    var showMap by remember { mutableStateOf(false) }
    var showZip by remember { mutableStateOf(false) }
    var showFilters by remember { mutableStateOf(false) }
    var showSort by remember { mutableStateOf(false) }
    when (state) {
        MarketplaceState.Loading -> CenteredProgress("Finding helpers near you…")
        is MarketplaceState.Failure -> Column(Modifier.fillMaxSize()) {
            FriendlyEmptyState("We could not load helpers", state.message)
            Button(coordinator::refresh, Modifier.align(Alignment.CenterHorizontally)) { Text("Try again") }
        }
        is MarketplaceState.Results -> PullToRefreshBox(
            isRefreshing = false,
            onRefresh = coordinator::refresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Find your helper",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            Text("Near ${state.zipCode}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = { showZip = true }) { Text("Change ZIP") }
                    }
                }
                if (state.cached) item { StatusPill("Offline results", DigibuddyColors.Gold) }
                item {
                    OutlinedTextField(
                        value = search,
                        onValueChange = { search = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("What do you need help with?") },
                        leadingIcon = { Icon(Icons.Rounded.Search, null) },
                        trailingIcon = {
                            IconButton(onClick = {
                                coordinator.updateSearch(search)
                            }) { Icon(Icons.Rounded.ChevronRight, "Search") }
                        },
                        singleLine = true,
                    )
                }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item { AssistChip(onClick = { coordinator.selectCategory(null) }, label = { Text("All") }) }
                        items(state.categories) { category ->
                            AssistChip(onClick = {
                                coordinator.selectCategory(category.slug)
                            }, label = { Text(category.name) })
                        }
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = { showFilters = true }) {
                            Icon(Icons.Rounded.FilterList, null)
                            Text(" Filters")
                        }
                        FilledTonalButton(onClick = { showSort = true }) {
                            Icon(Icons.AutoMirrored.Rounded.Sort, null)
                            Text(" Sort")
                        }
                        FilledTonalButton(onClick = { showMap = !showMap }) {
                            Icon(Icons.Rounded.Map, null)
                            Text(if (showMap) " List" else " Map")
                        }
                        IconButton(coordinator::refresh) { Icon(Icons.Rounded.Refresh, "Refresh") }
                    }
                }
                if (showMap) {
                    item { ApproximateMap(state.helpers) }
                } else if (state.helpers.isEmpty()) {
                    item { FriendlyEmptyState("No helpers found", "Try a different ZIP code or remove a filter.", "⌕") }
                } else {
                    items(state.helpers, key = {
                        it.helperId
                    }) { helper -> HelperCard(helper) { coordinator.openHelper(helper.helperId) } }
                }
            }
        }
    }
    if (showZip) {
        ZipDialog({ showZip = false }, {
            coordinator.changeZip(it)
            showZip = false
        })
    }
    if (showFilters &&
        state is MarketplaceState.Results
    ) {
        FilterDialog(state.filters, { showFilters = false }) {
            coordinator.applyFilters(it)
            showFilters =
                false
        }
    }
    if (showSort &&
        state is MarketplaceState.Results
    ) {
        SortDialog(state.sort, { showSort = false }) {
            coordinator.changeSort(it)
            showSort =
                false
        }
    }
}

@Composable
private fun HelperCard(helper: HelperSummaryResponse, onClick: () -> Unit) {
    DigibuddyCard(Modifier.clickable(onClick = onClick)) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
            InitialAvatar(helper.displayName)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        helper.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    if (helper.verified) {
                        Icon(
                            Icons.Rounded.Verified,
                            "Verified helper",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                Text(
                    helper.headline,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "★ ${helper.rating}  (${helper.reviewCount})",
                    color = DigibuddyColors.Navy,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            helper.skills.take(3).forEach { StatusPill(it.replace('-', ' ')) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Digibuddy prices from ${money(helper.startingPriceCents)}", fontWeight = FontWeight.Bold)
            Text(
                helper.distanceMiles?.let {
                    "$it mi away"
                } ?: "Remote",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            if (helper.inPersonService) StatusPill("In person", DigibuddyColors.Success)
            if (helper.remoteService) StatusPill("Remote")
            StatusPill("${helper.completedJobCount} jobs", DigibuddyColors.Slate)
        }
        Text(
            "Next available: ${availabilityText(helper)} • responds in about ${helper.responseTimeMinutes} min",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ApproximateMap(helpers: List<HelperSummaryResponse>) {
    Box(
        Modifier.fillMaxWidth().height(360.dp).clip(MaterialTheme.shapes.large)
            .background(Brush.linearGradient(listOf(ColorToken.MapLight, ColorToken.MapDark))),
    ) {
        Text(
            "Approximate service areas",
            Modifier.align(Alignment.TopStart).padding(18.dp),
            fontWeight = FontWeight.Bold,
        )
        helpers.take(5).forEachIndexed { index, helper ->
            Column(
                Modifier.align(
                    listOf(
                        Alignment.Center,
                        Alignment.TopEnd,
                        Alignment.BottomStart,
                        Alignment.CenterEnd,
                        Alignment.BottomCenter,
                    )[index],
                ).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary) {
                    Icon(
                        Icons.Rounded.LocationOn,
                        null,
                        Modifier.padding(9.dp),
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                }
                Text(
                    helper.displayName.substringBefore(' '),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Text(
            "Locations are intentionally approximate",
            Modifier.align(Alignment.BottomEnd).padding(12.dp),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

private object ColorToken {
    val MapLight = androidx.compose.ui.graphics.Color(0xFFE5F4EE)
    val MapDark = androidx.compose.ui.graphics.Color(0xFFB8D8D2)
}

@Composable
private fun HelperProfileScreen(
    profile: HelperProfileResponse,
    reviews: List<HelperReviewResponse>,
    onBack: () -> Unit,
    onBook: (HelperProfileResponse) -> Unit,
    onMessage: (HelperProfileResponse) -> Unit,
) {
    val helper = profile.summary
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Box(
                Modifier.fillMaxWidth().height(
                    170.dp,
                ).background(Brush.linearGradient(listOf(DigibuddyColors.Teal, DigibuddyColors.BrightTeal))),
            ) {
                IconButton(
                    onBack,
                    Modifier.padding(8.dp).background(MaterialTheme.colorScheme.surface.copy(alpha = .9f), CircleShape),
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
                }
                InitialAvatar(
                    helper.displayName,
                    88.dp,
                    MaterialTheme.colorScheme.surface,
                    modifier = Modifier.align(Alignment.BottomStart).padding(start = 22.dp, bottom = 14.dp),
                )
            }
        }
        item {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            helper.displayName,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(helper.headline, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (helper.verified) StatusPill("Verified", DigibuddyColors.Success)
                }
                Text(
                    "★ ${helper.rating} (${helper.reviewCount} reviews)  •  ${helper.completedJobCount} completed jobs",
                )
                Text(
                    "Usually responds in ${helper.responseTimeMinutes} minutes",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button({ onBook(profile) }, Modifier.weight(1f)) { Text("Book this helper") }
                    OutlinedButton({ onMessage(profile) }, Modifier.weight(1f)) { Text("Message") }
                }
                DetailSection("About", helper.biography)
                DigibuddySectionHeader("Services")
                profile.services.forEach { service ->
                    DigibuddyCard {
                        Text(service.name, fontWeight = FontWeight.Bold)
                        Text(service.description, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "Pricing is set by Digibuddy",
                            fontWeight = FontWeight.SemiBold,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (service.inPerson) StatusPill("In person", DigibuddyColors.Success)
                            if (service.remote) StatusPill("Remote")
                        }
                    }
                }
                DetailSection(
                    "Experience",
                    "${profile.experienceYears} years helping people with ${helper.skills.joinToString {
                        it.replace('-', ' ')
                    }}.",
                )
                DetailSection("Languages", helper.languages.joinToString())
                DetailSection("Service area", profile.serviceAreaDescription)
                DetailSection("Availability", availabilityText(helper))
                DetailSection("Certifications", profile.certifications.joinToString("\n") { "✓ $it" })
                DigibuddySectionHeader("Portfolio")
                profile.portfolio.forEach { item ->
                    DigibuddyCard {
                        Text(item.title, fontWeight = FontWeight.Bold)
                        Text(item.description)
                    }
                }
                DigibuddySectionHeader("Reviews", "${helper.reviewCount} total")
                if (reviews.isEmpty()) Text("No reviews yet.") else reviews.forEach { ReviewCard(it) }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun ReviewCard(review: HelperReviewResponse) {
    DigibuddyCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(review.reviewerDisplayName, fontWeight = FontWeight.Bold)
            Text("★".repeat(review.rating), color = DigibuddyColors.Gold)
        }
        Text(review.comment)
        Text(
            review.serviceName + if (review.developmentSeed) " • Development review" else "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable private fun DetailSection(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun BookingsTab(coordinator: BookingCoordinator, onMessage: (String) -> Unit) {
    val state by coordinator.state.collectAsState()
    LaunchedEffect(coordinator) { coordinator.load() }
    when {
        state.draft != null -> BookingWizard(state.draft!!, coordinator)
        state.payment != null -> PaymentScreen(state, coordinator)
        state.selected != null -> BookingDetail(state.selected!!, coordinator, onMessage)
        else -> BookingList(state.bookings, state.loading, coordinator::open)
    }
}

@Composable
private fun BookingList(bookings: List<BookingSummaryResponse>, loading: Boolean, open: (String) -> Unit) {
    if (loading && bookings.isEmpty()) return CenteredProgress("Loading your bookings…")
    LazyColumn(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { Text("Bookings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }
        if (bookings.isEmpty()) {
            item {
                FriendlyEmptyState(
                    "No bookings yet",
                    "Choose a helper and request a time. We’ll keep every update in one place.",
                    "◇",
                )
            }
        } else {
            val groups = listOf(
                "Action needed" to bookings.filter { it.nextAction != null },
                "Upcoming" to
                    bookings.filter {
                        it.status in
                            setOf(
                                BookingStatus.CONFIRMED,
                                BookingStatus.AWAITING_HELPER_RESPONSE,
                                BookingStatus.REQUESTED,
                            )
                    },
                "In progress" to
                    bookings.filter {
                        it.status in
                            setOf(
                                BookingStatus.HELPER_EN_ROUTE,
                                BookingStatus.HELPER_ARRIVED,
                                BookingStatus.IN_PROGRESS,
                                BookingStatus.PAUSED,
                            )
                    },
                "Past" to
                    bookings.filter {
                        it.status in
                            setOf(BookingStatus.COMPLETED, BookingStatus.REFUNDED, BookingStatus.PARTIALLY_REFUNDED)
                    },
                "Canceled" to
                    bookings.filter {
                        it.status in
                            setOf(
                                BookingStatus.CANCELED_BY_CUSTOMER,
                                BookingStatus.CANCELED_BY_HELPER,
                                BookingStatus.EXPIRED,
                            )
                    },
            )
            groups.filter { it.second.isNotEmpty() }.forEach { (title, values) ->
                item { DigibuddySectionHeader(title) }
                items(
                    values.distinctBy {
                        it.bookingId
                    },
                    key = { "$title-${it.bookingId}" },
                ) { booking -> BookingCard(booking) { open(booking.bookingId) } }
            }
        }
    }
}

@Composable
private fun BookingCard(booking: BookingSummaryResponse, open: () -> Unit) {
    DigibuddyCard(Modifier.clickable(onClick = open)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            InitialAvatar(booking.helperDisplayName)
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(booking.serviceName, fontWeight = FontWeight.Bold)
                Text("with ${booking.helperDisplayName}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Rounded.ChevronRight, null)
        }
        StatusPill(booking.statusExplanation, bookingStatusColor(booking.status))
        Text("${pretty(booking.serviceMode)} • ${booking.generalLocation ?: "Online"}")
        Text("Total ${money(booking.price.totalCents)}", fontWeight = FontWeight.SemiBold)
        booking.nextAction?.let {
            Text("Next: $it", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun BookingWizard(draft: BookingDraft, coordinator: BookingCoordinator) {
    val service = draft.helper.services.getOrNull(draft.serviceIndex) ?: return
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                if (draft.step ==
                    0
                ) {
                    coordinator::cancelDraft
                } else {
                    { coordinator.updateDraft { it.copy(step = it.step - 1) } }
                },
            ) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
            Column(Modifier.weight(1f)) {
                Text("Request help", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Step ${draft.step + 1} of 6", style = MaterialTheme.typography.bodySmall)
            }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (draft.step) {
                0 -> {
                    Text(
                        "Choose a service",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    draft.helper.services.forEachIndexed { index, item ->
                        DigibuddyCard(
                            Modifier.clickable {
                                coordinator.updateDraft { it.copy(serviceIndex = index) }
                            },
                        ) {
                            Text(
                                (
                                    if (index ==
                                        draft.serviceIndex
                                    ) {
                                        "✓ "
                                    } else {
                                        ""
                                    }
                                    ) + item.name,
                                fontWeight = FontWeight.Bold,
                            )
                            Text("Final price depends on the help type you choose next.")
                        }
                    }
                }
                1 -> {
                    Text(
                        "How would you like help?",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    listOf(
                        "IN_PERSON" to "In-home visit · $79",
                        "REMOTE" to "Quick remote help · $29",
                    ).forEach { (mode, label) ->
                        DigibuddyCard(Modifier.clickable { coordinator.updateDraft { it.copy(mode = mode) } }) {
                            Text(
                                (if (draft.mode == mode) "✓ " else "") + label,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
                2 -> {
                    Text(
                        "Tell us what is happening",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text("Simple details help your helper arrive prepared.")
                    OutlinedTextField(draft.description, { value ->
                        coordinator.updateDraft { it.copy(description = value.take(2_000)) }
                    }, modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp), label = {
                        Text("Describe the problem")
                    })
                    Text(
                        "Photos are optional and can be attached from the mobile app.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                3 -> {
                    Text(
                        if (draft.mode ==
                            "IN_PERSON"
                        ) {
                            "Where do you need help?"
                        } else {
                            "Choose a time"
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    if (draft.mode == "IN_PERSON") {
                        OutlinedTextField(draft.addressLine, { value ->
                            coordinator.updateDraft { it.copy(addressLine = value) }
                        }, label = { Text("Street address") }, modifier = Modifier.fillMaxWidth())
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(draft.city, { value ->
                                coordinator.updateDraft { it.copy(city = value) }
                            }, label = { Text("City") }, modifier = Modifier.weight(1f))
                            OutlinedTextField(draft.region, { value ->
                                coordinator.updateDraft { it.copy(region = value.uppercase().take(2)) }
                            }, label = { Text("State") }, modifier = Modifier.weight(.55f))
                        }
                        OutlinedTextField(draft.zipCode, { value ->
                            coordinator.updateDraft { it.copy(zipCode = value.filter(Char::isDigit).take(5)) }
                        }, label = { Text("ZIP code") })
                    }
                    Text("Development appointment: August 1 at 10:00 AM", fontWeight = FontWeight.Bold)
                    Text("Live availability selection will use the helper’s schedule.")
                }
                4 -> {
                    Text("Review price", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    val laborCents = DigibuddyPricing.bookingLaborCents(draft.mode)
                    PriceRow(
                        if (draft.mode == "IN_PERSON") "In-home visit" else "Quick remote help",
                        laborCents,
                    )
                    HorizontalDivider()
                    PriceRow("Total", laborCents, true)
                    Text(
                        "Materials, travel, and tax are $0 in this development estimate. Any change requires your approval.",
                    )
                    Text("Payment method will be requested only after a quote or confirmation.")
                }
                else -> {
                    Text("Ready to send?", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    DigibuddyCard {
                        Text(service.name, fontWeight = FontWeight.Bold)
                        Text("${pretty(draft.mode)} with ${draft.helper.summary.displayName}")
                        Text(draft.description)
                        Text(
                            "Total ${money(DigibuddyPricing.bookingLaborCents(draft.mode))}",
                        )
                    }
                    FilterCheck("I reviewed the cancellation terms", draft.termsAccepted) { selected ->
                        coordinator.updateDraft { it.copy(termsAccepted = selected) }
                    }
                    Text(
                        "You can cancel before confirmation. Fees for later cancellation will always be shown before you agree.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    draft.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            }
        }
        val canContinue = when (draft.step) {
            2 -> draft.description.trim().length >= 10
            3 ->
                draft.mode == "REMOTE" ||
                    (
                        draft.addressLine.isNotBlank() &&
                            draft.city.isNotBlank() &&
                            draft.region.length == 2 &&
                            draft.zipCode.length == 5
                        )
            5 -> draft.termsAccepted && !draft.submitting
            else -> true
        }
        Button(
            onClick = {
                if (draft.step ==
                    5
                ) {
                    coordinator.submit()
                } else {
                    coordinator.updateDraft { it.copy(step = it.step + 1) }
                }
            },
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            enabled = canContinue,
        ) {
            Text(
                if (draft.submitting) {
                    "Sending…"
                } else if (draft.step == 5) {
                    "Confirm request"
                } else {
                    "Continue"
                },
            )
        }
    }
}

@Composable private fun PriceRow(label: String, cents: Int, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
        Text(money(cents), fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun BookingDetail(
    detail: BookingDetailResponse,
    coordinator: BookingCoordinator,
    onMessage: (String) -> Unit,
) {
    val booking = detail.summary
    LazyColumn(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(coordinator::closeDetail) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                Text("Booking details", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
        }
        item {
            DigibuddyCard {
                Text(
                    booking.statusExplanation,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text("${booking.serviceName} with ${booking.helperDisplayName}")
                StatusPill(pretty(booking.serviceMode))
            }
        }
        item { DigibuddySectionHeader("Status timeline") }
        items(detail.history) { event ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(12.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                Column {
                    Text(event.explanation, fontWeight = FontWeight.Medium)
                    Text(event.occurredAt, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item { DetailSection("Problem description", detail.problemDescription) }
        item { DetailSection("Appointment", "${pretty(booking.serviceMode)} • ${booking.generalLocation ?: "Online"}") }
        item {
            DigibuddySectionHeader("Price breakdown")
            PriceRow("Labor or estimate", booking.price.laborCents)
            PriceRow("Platform fee", booking.price.platformFeeCents)
            PriceRow("Total", booking.price.totalCents, true)
            Text("Payment: ${pretty(detail.paymentStatus)}")
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if ("ACCEPT_QUOTE" in
                    detail.allowedActions
                ) {
                    Button({
                        coordinator.acceptQuote(booking.bookingId)
                    }, Modifier.fillMaxWidth()) { Text("Accept quote") }
                }
                if (booking.status ==
                    BookingStatus.AWAITING_PAYMENT_AUTHORIZATION
                ) {
                    Button({
                        coordinator.startPayment(booking.bookingId)
                    }, Modifier.fillMaxWidth()) { Text("Choose payment method") }
                }
                if ("CONFIRM_COMPLETION" in
                    detail.allowedActions
                ) {
                    Button({
                        coordinator.confirmCompletion(booking.bookingId)
                    }, Modifier.fillMaxWidth()) { Text("Confirm completed work") }
                }
                if ("CANCEL" in
                    detail.allowedActions
                ) {
                    OutlinedButton({
                        coordinator.cancel(booking.bookingId)
                    }, Modifier.fillMaxWidth()) { Text("Cancel booking") }
                }
                OutlinedButton(
                    { onMessage(booking.helperId) },
                    Modifier.fillMaxWidth(),
                ) { Text("Message helper") }
                TextButton({}, Modifier.fillMaxWidth()) { Text("Contact support") }
            }
        }
    }
}

@Composable
private fun PaymentScreen(state: BookingUiState, coordinator: BookingCoordinator) {
    val payment = state.payment ?: return
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        IconButton(coordinator::closePayment) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
        Text("Payment", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        DigibuddyCard {
            Text("Review total", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            PriceRow("Booking total", payment.amountCents, true)
            Text("Your payment details are handled by the payment provider. Digibuddy never stores raw card numbers.")
        }
        when (payment.status) {
            PaymentStatus.REQUIRES_AUTHORIZATION -> {
                DigibuddySectionHeader("Payment method")
                DigibuddyCard {
                    Text("Development payment method", fontWeight = FontWeight.Bold)
                    Text(
                        "Simulates Stripe authorization locally. No money will move and no card details are collected.",
                    )
                }
                OutlinedButton({
                }, Modifier.fillMaxWidth()) { Text("Apple Pay (available on supported iPhone devices)") }
                Button(coordinator::authorizeDevelopmentPayment, Modifier.fillMaxWidth()) {
                    Text("Authorize development payment")
                }
            }
            PaymentStatus.PROCESSING -> CenteredProgress("Processing payment…")
            PaymentStatus.AUTHORIZED, PaymentStatus.CAPTURED -> {
                FriendlyEmptyState(
                    "Payment authorized",
                    "Your booking is confirmed. A receipt will be available after capture.",
                    "✓",
                )
                Button({ coordinator.loadReceipt(payment.bookingId) }, Modifier.fillMaxWidth()) { Text("View receipt") }
            }
            PaymentStatus.FAILED -> FriendlyEmptyState(
                "Payment failed",
                "No charge was completed. Choose another method and try again.",
                "!",
            )
            PaymentStatus.REFUNDED, PaymentStatus.PARTIALLY_REFUNDED -> FriendlyEmptyState(
                "Refund update",
                "Your refund status is confirmed by the server.",
                "↺",
            )
            else -> Text("Payment status: ${pretty(payment.status.name)}")
        }
        state.receipt?.let { receipt ->
            DigibuddySectionHeader("Receipt")
            DigibuddyCard {
                receipt.entries.forEach { entry -> PriceRow(pretty(entry.type), entry.amountCents) }
                HorizontalDivider()
                PriceRow("Total", receipt.totalCents, true)
                Text("Receipt ${receipt.receiptId.take(8)}")
            }
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun bookingStatusColor(status: BookingStatus) = when (status) {
    BookingStatus.CONFIRMED, BookingStatus.COMPLETED -> DigibuddyColors.Success
    BookingStatus.CANCELED_BY_CUSTOMER,
    BookingStatus.CANCELED_BY_HELPER,
    BookingStatus.EXPIRED,
    -> MaterialTheme.colorScheme.error
    else -> DigibuddyColors.Teal
}

@Composable
private fun ChatsTab(coordinator: ChatCoordinator) {
    val state by coordinator.state.collectAsState()
    LaunchedEffect(coordinator) { coordinator.load() }
    state.selected?.let {
        ConversationScreen(state, coordinator)
        return
    }
    LazyColumn(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { Text("Messages", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }
        state.error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
        if (state.offline) item { StatusPill("Offline — showing saved messages", DigibuddyColors.Gold) }
        item {
            DigibuddyCard {
                Text("A quick safety reminder", fontWeight = FontWeight.Bold)
                Text(
                    "Never share passwords, banking details, or verification codes. Digibuddy helpers and staff should not ask for them.",
                )
            }
        }
        if (state.loading && state.conversations.isEmpty()) {
            item { CenteredProgress("Loading messages…") }
        } else if (state.conversations.isEmpty()) {
            item { FriendlyEmptyState("No messages yet", "Your conversations with helpers will appear here.", "✉") }
        } else {
            items(state.conversations, key = { it.conversationId }) { conversation ->
                DigibuddyCard(Modifier.clickable { coordinator.open(conversation) }) {
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
                            conversation.bookingId?.let {
                                Text("Booking conversation", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        if (conversation.unreadCount >
                            0
                        ) {
                            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary) {
                                Text(
                                    conversation.unreadCount.toString(),
                                    Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConversationScreen(state: ChatUiState, coordinator: ChatCoordinator) {
    var message by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(coordinator::close) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
            InitialAvatar(state.selected?.otherParticipantDisplayName.orEmpty(), 42.dp)
            Spacer(Modifier.size(10.dp))
            Column(Modifier.weight(1f)) {
                Text(state.selected?.otherParticipantDisplayName.orEmpty(), fontWeight = FontWeight.Bold)
                Text(
                    if (state.selected?.canReply == false) {
                        "Welcome message — replies are turned off"
                    } else if (state.connecting) {
                        "Reconnecting…"
                    } else if (state.offline) {
                        "Offline"
                    } else {
                        "Connected"
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        HorizontalDivider()
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    "Never share passwords, banking details, or verification codes. Digibuddy helpers and staff should not ask for them.",
                    modifier = Modifier.fillMaxWidth().clip(
                        MaterialTheme.shapes.medium,
                    ).background(DigibuddyColors.Mist).padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            items(state.messages, key = { it.messageId }) { chat ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = if (chat.senderIsCurrentUser) Arrangement.End else Arrangement.Start,
                ) {
                    Column(
                        Modifier.fillMaxWidth(.78f).clip(MaterialTheme.shapes.medium)
                            .background(
                                if (chat.senderIsCurrentUser) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                },
                            )
                            .padding(12.dp),
                    ) {
                        Text(
                            chat.body,
                            color =
                            if (chat.senderIsCurrentUser) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                        Text(
                            pretty(chat.deliveryStatus.name),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (chat.senderIsCurrentUser) {
                                MaterialTheme.colorScheme.onPrimary.copy(
                                    alpha = .75f,
                                )
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }
            if (state.queued.isNotEmpty()) {
                item {
                    TextButton(coordinator::retryQueued) { Text("Retry failed messages") }
                }
            }
        }
        if (state.selected?.canReply == false) {
            Text(
                "This is an information message. Replies are turned off.",
                modifier = Modifier.fillMaxWidth().background(DigibuddyColors.Mist).padding(16.dp),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
        } else {
            Row(
                Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(message, {
                    message = it.take(2_000)
                }, modifier = Modifier.weight(1f), placeholder = { Text("Message") }, maxLines = 4)
                Button({
                    coordinator.send(message)
                    message = ""
                }, enabled = message.isNotBlank()) { Text("Send") }
            }
        }
    }
}

@Composable
private fun ProfileTab(
    profile: CustomerProfileResponse,
    coordinator: CustomerProfileCoordinator,
    onSignOut: () -> Unit,
    accountSettings: @Composable () -> Unit,
) {
    var editProfile by remember { mutableStateOf(false) }
    var showSecurity by remember { mutableStateOf(false) }
    LazyColumn(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            DigibuddyCard {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    InitialAvatar(profile.publicDisplayName, 72.dp)
                    Column(Modifier.weight(1f)) {
                        Text(
                            profile.publicDisplayName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text("ZIP ${profile.zipCode}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            StatusPill("Phone verified", DigibuddyColors.Success)
                            if (profile.verifiedEmail != null) StatusPill("Email verified")
                        }
                    }
                }
                OutlinedButton({ editProfile = true }, Modifier.fillMaxWidth()) { Text("Edit profile") }
            }
        }
        item { SettingsGroup("Account", listOf("Saved addresses", "Payment methods", "Notification settings")) }
        item {
            DigibuddyCard(Modifier.clickable { showSecurity = !showSecurity }) {
                SettingsRow("Security", "Trusted devices, password, and sign-ins")
                if (showSecurity) accountSettings()
            }
        }
        item { SettingsGroup("Preferences", listOf("Privacy", "Accessibility", "Permissions")) }
        item {
            SettingsGroup(
                "Help & information",
                listOf("Help center", "Terms of service", "Privacy policy", "App version 0.1.0"),
            )
        }
        item {
            OutlinedButton(onSignOut, Modifier.fillMaxWidth()) { Text("Sign out") }
            TextButton(coordinator::requestExport, Modifier.fillMaxWidth()) { Text("Download my data") }
            TextButton(coordinator::requestDeletion, Modifier.fillMaxWidth()) {
                Text("Delete account", color = MaterialTheme.colorScheme.error)
            }
        }
    }
    if (editProfile) EditProfileDialog(profile, coordinator) { editProfile = false }
}

@Composable private fun SettingsGroup(title: String, rows: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        DigibuddyCard {
            rows.forEachIndexed { index, row ->
                SettingsRow(row)
                if (index < rows.lastIndex) HorizontalDivider()
            }
        }
    }
}

@Composable private fun SettingsRow(title: String, subtitle: String? = null) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EditProfileDialog(
    profile: CustomerProfileResponse,
    coordinator: CustomerProfileCoordinator,
    dismiss: () -> Unit,
) {
    var first by remember { mutableStateOf(profile.firstName) }
    var last by remember { mutableStateOf(profile.lastName) }
    var zip by remember { mutableStateOf(profile.zipCode) }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("Edit profile") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(first, { first = it }, label = { Text("First name") })
                OutlinedTextField(last, { last = it }, label = { Text("Last name") })
                OutlinedTextField(zip, { zip = it.filter(Char::isDigit).take(5) }, label = { Text("ZIP code") })
            }
        },
        confirmButton = {
            Button(
                {
                    coordinator.updateName(first, last)
                    coordinator.updateZip(zip)
                    dismiss()
                },
                enabled =
                first.isNotBlank() && last.isNotBlank() && zip.length == 5,
            ) { Text("Save") }
        },
        dismissButton = { TextButton(dismiss) { Text("Cancel") } },
    )
}

@Composable private fun ZipDialog(dismiss: () -> Unit, save: (String) -> Unit) {
    var zip by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("Change ZIP code") },
        text = { OutlinedTextField(zip, { zip = it.filter(Char::isDigit).take(5) }, label = { Text("ZIP code") }) },
        confirmButton = { Button({ save(zip) }, enabled = zip.length == 5) { Text("Show helpers") } },
        dismissButton = { TextButton(dismiss) { Text("Cancel") } },
    )
}

@Composable
private fun FilterDialog(current: MarketplaceFilters, dismiss: () -> Unit, save: (MarketplaceFilters) -> Unit) {
    var value by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("Filters") },
        text = {
            Column {
                FilterCheck("Verified helpers", value.verifiedOnly) { value = value.copy(verifiedOnly = it) }
                FilterCheck("Remote service", value.remote == true) {
                    value =
                        value.copy(remote = it.takeIf { selected -> selected })
                }
                FilterCheck("In-person service", value.inPerson == true) {
                    value =
                        value.copy(inPerson = it.takeIf { selected -> selected })
                }
                FilterCheck("Rated 4.5 or higher", value.minimumRating == 4.5) {
                    value =
                        value.copy(minimumRating = 4.5.takeIf { _ -> it })
                }
                FilterCheck("Under $60", value.maximumPriceCents == 6000) {
                    value =
                        value.copy(maximumPriceCents = 6000.takeIf { _ -> it })
                }
            }
        },
        confirmButton = { Button({ save(value) }) { Text("Show results") } },
        dismissButton = { TextButton(dismiss) { Text("Cancel") } },
    )
}

@Composable private fun FilterCheck(label: String, selected: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { change(!selected) }, verticalAlignment = Alignment.CenterVertically) {
        Checkbox(selected, change)
        Text(label)
    }
}

@Composable private fun SortDialog(current: String, dismiss: () -> Unit, save: (String) -> Unit) {
    val options =
        listOf(
            "RECOMMENDED",
            "HIGHEST_RATED",
            "CLOSEST",
            "SOONEST_AVAILABLE",
            "LOWEST_STARTING_PRICE",
            "MOST_EXPERIENCED",
        )
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("Sort helpers") },
        text = {
            Column {
                options.forEach { option ->
                    TextButton({ save(option) }, Modifier.fillMaxWidth()) {
                        Text(
                            if (option ==
                                current
                            ) {
                                "✓ ${pretty(option)}"
                            } else {
                                pretty(option)
                            },
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(dismiss) { Text("Cancel") } },
    )
}

@Composable private fun CenteredProgress(label: String) {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(14.dp))
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun money(cents: Int) =
    "$${cents / 100}${if (cents % 100 == 0) "" else ".${(cents % 100).toString().padStart(2, '0')}"}"
private fun pretty(value: String) = value.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
private fun availabilityText(helper: HelperSummaryResponse) = when (helper.availability.availableWithinDays) {
    0 -> "Today"
    1 -> "Tomorrow"
    null -> "Ask for availability"
    else -> "In ${helper.availability.availableWithinDays} days"
}
