package com.digibuddy.customer.ui.screens.splash

import androidx.lifecycle.ViewModel
import com.digibuddy.customer.data.local.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@HiltViewModel
class SplashViewModel @Inject constructor(
    val userPreferences: UserPreferences
) : ViewModel() {
    fun isLoggedIn(): Boolean = runBlocking { userPreferences.isLoggedIn() }
}
