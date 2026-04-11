package com.digibuddy.customer.navigation

import androidx.compose.runtime.*
import androidx.navigation.*
import androidx.navigation.compose.*
import com.digibuddy.customer.ui.screens.auth.LoginScreen
import com.digibuddy.customer.ui.screens.auth.OtpScreen
import com.digibuddy.customer.ui.screens.auth.CompleteProfileScreen
import com.digibuddy.customer.ui.screens.chat.ChatListScreen
import com.digibuddy.customer.ui.screens.chat.ChatScreen
import com.digibuddy.customer.ui.screens.helpers.HelperDetailScreen
import com.digibuddy.customer.ui.screens.home.HomeScreen
import com.digibuddy.customer.ui.screens.map.MapScreen
import com.digibuddy.customer.ui.screens.profile.ProfileScreen
import com.digibuddy.customer.ui.screens.splash.SplashScreen

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Screen.Splash.route
    ) {
        composable(Screen.Splash.route) {
            SplashScreen(
                onNavigateToLogin = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                },
                onNavigateToHome = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Login.route) {
            LoginScreen(
                onNavigateToOtp = { userId, phone, devCode ->
                    navController.navigate(Screen.Otp.createRoute(userId, phone, devCode))
                },
                onNavigateToHome = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }

        composable(
            Screen.Otp.route,
            arguments = listOf(
                navArgument("userId") { type = NavType.StringType },
                navArgument("phone") { type = NavType.StringType },
                navArgument("devCode") { type = NavType.StringType; nullable = true; defaultValue = null }
            )
        ) { backStack ->
            OtpScreen(
                userId = backStack.arguments?.getString("userId") ?: "",
                phone = backStack.arguments?.getString("phone") ?: "",
                devCode = backStack.arguments?.getString("devCode"),
                onBack = { navController.popBackStack() },
                onNavigateToCompleteProfile = { userId ->
                    navController.navigate(Screen.CompleteProfile.createRoute(userId)) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                },
                onNavigateToHome = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }

        composable(
            Screen.CompleteProfile.route,
            arguments = listOf(navArgument("userId") { type = NavType.StringType })
        ) { backStack ->
            CompleteProfileScreen(
                userId = backStack.arguments?.getString("userId") ?: "",
                onNavigateToHome = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.CompleteProfile.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Home.route) {
            HomeScreen(
                onHelperClick = { helperId ->
                    navController.navigate(Screen.HelperDetail.createRoute(helperId))
                },
                onMapClick = {
                    navController.navigate(Screen.MapView.createRoute()) { launchSingleTop = true }
                },
                onChatListClick = {
                    navController.navigate(Screen.ChatList.route) { launchSingleTop = true }
                },
                onProfileClick = {
                    navController.navigate(Screen.Profile.route)
                },
                onBookingsClick = {
                    navController.navigate(Screen.Bookings.route) { launchSingleTop = true }
                }
            )
        }

        composable(Screen.ChatList.route) {
            ChatListScreen(
                onHomeClick = {
                    navController.navigate(Screen.Home.route) { launchSingleTop = true }
                },
                onMapClick = {
                    navController.navigate(Screen.MapView.createRoute()) { launchSingleTop = true }
                },
                onBookingsClick = {
                    navController.navigate(Screen.Bookings.route) { launchSingleTop = true }
                },
                onProfileClick = {
                    navController.navigate(Screen.Profile.route)
                },
                onChatRoomClick = { roomId, helperName ->
                    navController.navigate(Screen.Chat.createRoute(roomId, helperName))
                }
            )
        }

        composable(
            Screen.HelperDetail.route,
            arguments = listOf(navArgument("helperId") { type = NavType.StringType })
        ) { backStack ->
            HelperDetailScreen(
                helperId = backStack.arguments?.getString("helperId") ?: "",
                onBack = { navController.popBackStack() },
                onChatClick = { roomId, helperName ->
                    navController.navigate(Screen.Chat.createRoute(roomId, helperName))
                },
                onMapClick = { lat, lng ->
                    navController.navigate(Screen.MapView.createRoute(lat, lng))
                }
            )
        }

        composable(
            Screen.MapView.route,
            arguments = listOf(
                navArgument("lat") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("lng") { type = NavType.StringType; nullable = true; defaultValue = null }
            )
        ) { backStack ->
            MapScreen(
                focusLat = backStack.arguments?.getString("lat")?.toDoubleOrNull(),
                focusLng = backStack.arguments?.getString("lng")?.toDoubleOrNull(),
                onBack = { navController.popBackStack() },
                onHelperClick = { helperId ->
                    navController.navigate(Screen.HelperDetail.createRoute(helperId))
                },
                onHomeClick = {
                    navController.navigate(Screen.Home.route) { launchSingleTop = true }
                },
                onChatListClick = {
                    navController.navigate(Screen.ChatList.route) { launchSingleTop = true }
                },
                onBookingsClick = {
                    navController.navigate(Screen.Bookings.route) { launchSingleTop = true }
                },
                onProfileClick = {
                    navController.navigate(Screen.Profile.route)
                }
            )
        }

        composable(
            Screen.Chat.route,
            arguments = listOf(
                navArgument("roomId") { type = NavType.StringType },
                navArgument("otherName") { type = NavType.StringType }
            )
        ) { backStack ->
            ChatScreen(
                roomId = backStack.arguments?.getString("roomId") ?: "",
                otherName = backStack.arguments?.getString("otherName") ?: "Helper",
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Profile.route) {
            ProfileScreen(
                onBack = { navController.popBackStack() },
                onLogout = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Bookings.route) {
            com.digibuddy.customer.ui.screens.bookings.BookingsScreen(
                onBack = { navController.popBackStack() },
                onBookingClick = { bookingId ->
                    navController.navigate(Screen.BookingDetail.createRoute(bookingId))
                }
            )
        }

        composable(
            Screen.BookingDetail.route,
            arguments = listOf(navArgument("bookingId") { type = NavType.StringType })
        ) { backStack ->
            com.digibuddy.customer.ui.screens.bookings.BookingDetailScreen(
                bookingId = backStack.arguments?.getString("bookingId") ?: "",
                onBack = { navController.popBackStack() },
                onChatClick = { roomId, helperName ->
                    navController.navigate(Screen.Chat.createRoute(roomId, helperName))
                }
            )
        }
    }
}
