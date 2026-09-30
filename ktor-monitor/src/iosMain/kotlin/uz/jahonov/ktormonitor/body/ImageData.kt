package uz.jahonov.ktormonitor.body

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.create

/** The image bytes for `UIImage(data:)`. In Swift: `preview.data()`. */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
public fun BodyPreview.Image.data(): NSData {
    val bytes = bytes.toByteArray()
    if (bytes.isEmpty()) return NSData()
    return bytes.usePinned { NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong()) }
}
