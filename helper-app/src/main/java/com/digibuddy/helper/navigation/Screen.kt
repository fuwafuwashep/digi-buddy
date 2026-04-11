package com.digibuddy.helper.navigation

sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Permissions : Screen("permissions")
    object Login : Screen("login")
    object Otp : Screen("otp/{userId}/{phone}?devCode={devCode}") {
        fun createRoute(userId: String, phone: String, devCode: String? = null) =
            if (devCode != null) "otp/$userId/$phone?devCode=$devCode" else "otp/$userId/$phone"
    }
    object CompleteProfile : Screen("complete_profile/{userId}") {
        fun createRoute(userId: String) = "complete_profile/$userId"
    }
    object Dashboard : Screen("dashboard")
    object SetWorkLocation : Screen("work_location")
    object Chat : Screen("chat/{roomId}/{customerName}") {
        fun createRoute(roomId: String, customerName: String) = "chat/$roomId/$customerName"
    }
    object Profile : Screen("profile")
    object Earnings : Screen("earnings")
}
