package com.digibuddy.customer.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector

data class BottomNavItem(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val route: String
)

val bottomNavItems = listOf(
    BottomNavItem("Home",     Icons.Filled.Home,           Icons.Outlined.Home,          "home"),
    BottomNavItem("Map",      Icons.Filled.Map,            Icons.Outlined.Map,           "map"),
    BottomNavItem("Chat",     Icons.Filled.Chat,           Icons.Outlined.Chat,          "chat_list"),
    BottomNavItem("Bookings", Icons.Filled.CalendarMonth,  Icons.Outlined.CalendarMonth, "bookings"),
    BottomNavItem("Profile",  Icons.Filled.Person,         Icons.Outlined.Person,        "profile"),
)

@Composable
fun DigiBuddyBottomNav(
    currentRoute: String,
    onNavigate: (String) -> Unit
) {
    NavigationBar {
        bottomNavItems.forEach { item ->
            val selected = currentRoute == item.route
            NavigationBarItem(
                selected = selected,
                onClick = { onNavigate(item.route) },
                icon = {
                    Icon(
                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                        contentDescription = item.label
                    )
                },
                label = { Text(item.label) }
            )
        }
    }
}
