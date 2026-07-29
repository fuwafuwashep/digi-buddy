package com.digibuddy.helpers

import androidx.compose.runtime.Composable
import com.digibuddy.shared.helper.dashboard.HelperSelectedPhoto

interface HelperPhotoPicker {
    fun choose(onSelected: (HelperSelectedPhoto) -> Unit, onError: (String) -> Unit)
}

@Composable
expect fun rememberHelperPhotoPicker(): HelperPhotoPicker
