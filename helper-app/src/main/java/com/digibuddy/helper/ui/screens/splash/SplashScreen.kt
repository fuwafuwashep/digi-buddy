package com.digibuddy.helper.ui.screens.splash

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.digibuddy.helper.data.local.HelperPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@HiltViewModel
class SplashViewModel @Inject constructor(val helperPreferences: HelperPreferences) : ViewModel() {
    fun isLoggedIn(): Boolean = runBlocking { helperPreferences.isLoggedIn() }
}

@Composable
fun SplashScreen(
    onNavigateToLogin: () -> Unit,
    onNavigateToDashboard: () -> Unit,
    viewModel: SplashViewModel = hiltViewModel()
) {
    val scale by animateFloatAsState(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "scale")
    val alpha by animateFloatAsState(1f, tween(800), label = "alpha")

    LaunchedEffect(Unit) {
        delay(1800)
        if (viewModel.isLoggedIn()) onNavigateToDashboard() else onNavigateToLogin()
    }

    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.scale(scale).alpha(alpha)) {
            Icon(Icons.Filled.SupportAgent, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(96.dp))
            Spacer(Modifier.height(16.dp))
            Text("DigiBuddy", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
            Text("Helper Dashboard", fontSize = 16.sp, color = MaterialTheme.colorScheme.onPrimary.copy(0.8f))
        }
    }
}
