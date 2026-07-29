@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.digibuddy.helpers

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.digibuddy.shared.helper.dashboard.HelperSelectedPhoto
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSURL
import platform.Foundation.dataWithContentsOfURL
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerMode
import platform.UIKit.UIDocumentPickerViewController
import platform.darwin.NSObject
import platform.posix.memcpy

@Composable
actual fun rememberHelperPhotoPicker(): HelperPhotoPicker = remember { IosHelperPhotoPicker() }

private class IosHelperPhotoPicker : HelperPhotoPicker {
    private var delegate: IosDocumentPickerDelegate? = null

    override fun choose(onSelected: (HelperSelectedPhoto) -> Unit, onError: (String) -> Unit) {
        val pickerDelegate = IosDocumentPickerDelegate(onSelected, onError)
        delegate = pickerDelegate
        val picker = UIDocumentPickerViewController(
            documentTypes = listOf("public.image"),
            inMode = UIDocumentPickerMode.UIDocumentPickerModeImport,
        )
        picker.delegate = pickerDelegate
        picker.allowsMultipleSelection = false
        var presenter = UIApplication.sharedApplication.keyWindow?.rootViewController
        while (presenter?.presentedViewController != null) presenter = presenter.presentedViewController
        if (presenter == null) {
            onError("The file picker is not available right now.")
        } else {
            presenter.presentViewController(picker, animated = true, completion = null)
        }
    }
}

private class IosDocumentPickerDelegate(
    private val selected: (HelperSelectedPhoto) -> Unit,
    private val error: (String) -> Unit,
) : NSObject(),
    UIDocumentPickerDelegateProtocol {
    override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentAtURL: NSURL) =
        read(didPickDocumentAtURL)

    private fun read(url: NSURL) {
        val access = url.startAccessingSecurityScopedResource()
        runCatching {
            val data = requireNotNull(NSData.dataWithContentsOfURL(url))
            val bytes = ByteArray(data.length.toInt())
            if (bytes.isNotEmpty()) bytes.usePinned { memcpy(it.addressOf(0), data.bytes(), data.length) }
            val name = url.lastPathComponent ?: "profile-photo"
            HelperSelectedPhoto(name, contentType(name.substringAfterLast('.', "")), bytes)
        }.onSuccess(selected)
            .onFailure { error("The selected photo could not be read.") }
        if (access) url.stopAccessingSecurityScopedResource()
    }
}

private fun contentType(extension: String) = when (extension.lowercase()) {
    "jpg", "jpeg" -> "image/jpeg"
    "png" -> "image/png"
    "webp" -> "image/webp"
    else -> "application/octet-stream"
}
