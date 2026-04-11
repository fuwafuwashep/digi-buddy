package com.digibuddy.customer.navigation

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
    object Home : Screen("home")
    object Search : Screen("search")
    object HelperDetail : Screen("helper/{helperId}") {
        fun createRoute(helperId: String) = "helper/$helperId"
    }
    object MapView : Screen("map?lat={lat}&lng={lng}") {
        fun createRoute(lat: Double? = null, lng: Double? = null): String =
            if (lat != null && lng != null) "map?lat=$lat&lng=$lng" else "map"
    }
    object Chat : Screen("chat/{roomId}/{otherName}") {
        fun createRoute(roomId: String, otherName: String) = "chat/$roomId/$otherName"
    }
    object Profile : Screen("profile")
    object ChatList : Screen("chat_list")
    object Bookings : Screen("bookings")
    object BookingDetail : Screen("booking/{bookingId}") {
        fun createRoute(bookingId: String) = "booking/$bookingId"
    }
}
