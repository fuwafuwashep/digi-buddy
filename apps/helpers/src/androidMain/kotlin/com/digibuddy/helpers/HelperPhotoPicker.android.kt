package com.digibuddy.helpers

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.digibuddy.shared.helper.dashboard.HelperSelectedPhoto

@Composable
actual fun rememberHelperPhotoPicker(): HelperPhotoPicker {
    val context = LocalContext.current
    val callbacks = remember {
        mutableStateOf<Pair<(HelperSelectedPhoto) -> Unit, (String) -> Unit>?>(null)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val callback = callbacks.value ?: return@rememberLauncherForActivityResult
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching { context.readPhoto(uri) }
            .onSuccess(callback.first)
            .onFailure { callback.second("The selected photo could not be read.") }
    }
    return remember(launcher) {
        object : HelperPhotoPicker {
            override fun choose(onSelected: (HelperSelectedPhoto) -> Unit, onError: (String) -> Unit) {
                callbacks.value = onSelected to onError
                launcher.launch("image/*")
            }
        }
    }
}

private fun Context.readPhoto(uri: Uri): HelperSelectedPhoto {
    val type = contentResolver.getType(uri) ?: "application/octet-stream"
    val name = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    } ?: "profile-photo"
    val bytes = requireNotNull(contentResolver.openInputStream(uri)).use { it.readBytes() }
    return HelperSelectedPhoto(name, type, bytes)
}
