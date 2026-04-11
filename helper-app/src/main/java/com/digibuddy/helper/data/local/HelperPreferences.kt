package com.digibuddy.helper.data.local

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

private val Context.helperDataStore: DataStore<Preferences> by preferencesDataStore(name = "digibuddy_helper_prefs")

@Singleton
class HelperPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val ACCESS_TOKEN   = stringPreferencesKey(Constants.KEY_ACCESS_TOKEN)
    private val REFRESH_TOKEN  = stringPreferencesKey(Constants.KEY_REFRESH_TOKEN)
    private val USER_ID        = stringPreferencesKey(Constants.KEY_USER_ID)
    private val USER_NAME      = stringPreferencesKey(Constants.KEY_USER_NAME)
    private val USER_ROLE      = stringPreferencesKey(Constants.KEY_USER_ROLE)
    private val HELPER_ID      = stringPreferencesKey("helper_profile_id")
    private val IS_AVAILABLE   = booleanPreferencesKey("is_available")

    val accessToken: Flow<String?> = context.helperDataStore.data.map { it[ACCESS_TOKEN] }
    val userId: Flow<String?> = context.helperDataStore.data.map { it[USER_ID] }
    val userName: Flow<String?> = context.helperDataStore.data.map { it[USER_NAME] }
    val helperId: Flow<String?> = context.helperDataStore.data.map { it[HELPER_ID] }
    val isAvailable: Flow<Boolean> = context.helperDataStore.data.map { it[IS_AVAILABLE] ?: false }

    suspend fun getAccessToken(): String? = accessToken.first()

    suspend fun saveAuthData(
        accessToken: String, refreshToken: String,
        userId: String, name: String, role: String,
        helperId: String? = null
    ) {
        context.helperDataStore.edit { prefs ->
            prefs[ACCESS_TOKEN]  = accessToken
            prefs[REFRESH_TOKEN] = refreshToken
            prefs[USER_ID]       = userId
            prefs[USER_NAME]     = name
            prefs[USER_ROLE]     = role
            helperId?.let { prefs[HELPER_ID] = it }
        }
    }

    suspend fun setAvailability(available: Boolean) {
        context.helperDataStore.edit { it[IS_AVAILABLE] = available }
    }

    suspend fun clearAll() { context.helperDataStore.edit { it.clear() } }

    suspend fun isLoggedIn(): Boolean = getAccessToken() != null
}
