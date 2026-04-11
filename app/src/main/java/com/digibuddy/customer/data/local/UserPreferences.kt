package com.digibuddy.customer.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.digibuddy.core.utils.Constants
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = Constants.PREFS_NAME)

@Singleton
class UserPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val ACCESS_TOKEN   = stringPreferencesKey(Constants.KEY_ACCESS_TOKEN)
    private val REFRESH_TOKEN  = stringPreferencesKey(Constants.KEY_REFRESH_TOKEN)
    private val USER_ID        = stringPreferencesKey(Constants.KEY_USER_ID)
    private val USER_NAME      = stringPreferencesKey(Constants.KEY_USER_NAME)
    private val USER_ROLE      = stringPreferencesKey(Constants.KEY_USER_ROLE)
    private val USER_EMAIL     = stringPreferencesKey(Constants.KEY_USER_EMAIL)
    private val USER_PHONE     = stringPreferencesKey(Constants.KEY_USER_PHONE)
    private val USER_AVATAR    = stringPreferencesKey(Constants.KEY_USER_AVATAR)
    private val ONBOARDING     = booleanPreferencesKey(Constants.KEY_ONBOARDING_DONE)

    val accessToken: Flow<String?> = context.dataStore.data.map { it[ACCESS_TOKEN] }
    val refreshToken: Flow<String?> = context.dataStore.data.map { it[REFRESH_TOKEN] }
    val userId: Flow<String?> = context.dataStore.data.map { it[USER_ID] }
    val userName: Flow<String?> = context.dataStore.data.map { it[USER_NAME] }
    val userRole: Flow<String?> = context.dataStore.data.map { it[USER_ROLE] }
    val userEmail: Flow<String?> = context.dataStore.data.map { it[USER_EMAIL] }
    val userPhone: Flow<String?> = context.dataStore.data.map { it[USER_PHONE] }
    val userAvatar: Flow<String?> = context.dataStore.data.map { it[USER_AVATAR] }
    val onboardingDone: Flow<Boolean> = context.dataStore.data.map { it[ONBOARDING] ?: false }

    suspend fun getAccessToken(): String? = accessToken.first()

    suspend fun saveAuthData(
        accessToken: String, refreshToken: String,
        userId: String, name: String, role: String,
        email: String? = null, phone: String? = null, avatar: String? = null
    ) {
        context.dataStore.edit { prefs ->
            prefs[ACCESS_TOKEN]  = accessToken
            prefs[REFRESH_TOKEN] = refreshToken
            prefs[USER_ID]       = userId
            prefs[USER_NAME]     = name
            prefs[USER_ROLE]     = role
            email?.let { prefs[USER_EMAIL] = it }
            phone?.let { prefs[USER_PHONE] = it }
            avatar?.let { prefs[USER_AVATAR] = it }
        }
    }

    suspend fun setOnboardingDone() {
        context.dataStore.edit { it[ONBOARDING] = true }
    }

    suspend fun clearAll() {
        context.dataStore.edit { it.clear() }
    }

    suspend fun isLoggedIn(): Boolean = getAccessToken() != null
}
