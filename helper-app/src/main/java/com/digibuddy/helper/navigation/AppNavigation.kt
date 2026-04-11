package com.digibuddy.helper.navigation

import androidx.compose.runtime.*
import androidx.navigation.*
import androidx.navigation.compose.*
import com.digibuddy.helper.ui.screens.auth.LoginScreen
import com.digibuddy.helper.ui.screens.auth.OtpScreen
import com.digibuddy.helper.ui.screens.auth.CompleteHelperProfileScreen
import com.digibuddy.helper.ui.screens.chat.ChatScreen
import com.digibuddy.helper.ui.screens.dashboard.DashboardScreen
import com.digibuddy.helper.ui.screens.location.SetWorkLocationScreen
import com.digibuddy.helper.ui.screens.profile.ProfileScreen
import com.digibuddy.helper.ui.screens.splash.SplashScreen

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Screen.Splash.route) {
        composable(Screen.Splash.route) {
            SplashScreen(
                onNavigateToLogin = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                },
                onNavigateToDashboard = {
                    navController.navigate(Screen.Dashboard.route) {
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
                onNavigateToDashboard = {
                    navController.navigate(Screen.Dashboard.route) {
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
                onNavigateToDashboard = {
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }

        composable(
            Screen.CompleteProfile.route,
            arguments = listOf(navArgument("userId") { type = NavType.StringType })
        ) { backStack ->
            CompleteHelperProfileScreen(
                userId = backStack.arguments?.getString("userId") ?: "",
                onNavigateToDashboard = {
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.CompleteProfile.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Dashboard.route) {
            DashboardScreen(
                onChatClick = { roomId, customerName ->
                    navController.navigate(Screen.Chat.createRoute(roomId, customerName))
                },
                onSetWorkLocationClick = {
                    navController.navigate(Screen.SetWorkLocation.route)
                },
                onProfileClick = {
                    navController.navigate(Screen.Profile.route)
                }
            )
        }

        composable(Screen.SetWorkLocation.route) {
            SetWorkLocationScreen(onBack = { navController.popBackStack() })
        }

        composable(
            Screen.Chat.route,
            arguments = listOf(
                navArgument("roomId") { type = NavType.StringType },
                navArgument("customerName") { type = NavType.StringType }
            )
        ) { backStack ->
            ChatScreen(
                roomId = backStack.arguments?.getString("roomId") ?: "",
                otherName = backStack.arguments?.getString("customerName") ?: "Customer",
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
    }
}
