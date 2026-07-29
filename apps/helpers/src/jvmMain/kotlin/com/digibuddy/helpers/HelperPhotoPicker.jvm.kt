package com.digibuddy.helpers

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.digibuddy.shared.helper.dashboard.HelperSelectedPhoto
import java.nio.file.Files
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

@Composable
actual fun rememberHelperPhotoPicker(): HelperPhotoPicker = remember {
    object : HelperPhotoPicker {
        override fun choose(onSelected: (HelperSelectedPhoto) -> Unit, onError: (String) -> Unit) {
            val chooser = JFileChooser().apply {
                dialogTitle = "Choose a Digibuddy Helpers profile photo"
                fileFilter = FileNameExtensionFilter("Image files (JPEG, PNG, WebP)", "jpg", "jpeg", "png", "webp")
                isAcceptAllFileFilterUsed = false
            }
            if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) return
            runCatching {
                val file = chooser.selectedFile
                HelperSelectedPhoto(file.name, contentType(file.extension), Files.readAllBytes(file.toPath()))
            }.onSuccess(onSelected).onFailure { onError("The selected photo could not be read.") }
        }
    }
}

private fun contentType(extension: String) = when (extension.lowercase()) {
    "jpg", "jpeg" -> "image/jpeg"
    "png" -> "image/png"
    "webp" -> "image/webp"
    else -> "application/octet-stream"
}
