package com.digibuddy.core.utils

object Constants {
    // Change this to your server's IP/domain when testing on a real device
    const val BASE_URL = "http://10.0.2.2:3000/api/"   // Android emulator → localhost
    const val SOCKET_URL = "http://10.0.2.2:3000"

    // DataStore
    const val PREFS_NAME = "digibuddy_prefs"
    const val KEY_ACCESS_TOKEN = "access_token"
    const val KEY_REFRESH_TOKEN = "refresh_token"
    const val KEY_USER_ID = "user_id"
    const val KEY_USER_NAME = "user_name"
    const val KEY_USER_ROLE = "user_role"
    const val KEY_USER_EMAIL = "user_email"
    const val KEY_USER_PHONE = "user_phone"
    const val KEY_USER_AVATAR = "user_avatar"
    const val KEY_ONBOARDING_DONE = "onboarding_done"

    // Default search radius (km)
    const val DEFAULT_SEARCH_RADIUS = 10.0

    // Map defaults
    const val DEFAULT_ZOOM = 13f
    const val DEFAULT_LAT = 37.7749   // San Francisco (fallback)
    const val DEFAULT_LNG = -122.4194
}
