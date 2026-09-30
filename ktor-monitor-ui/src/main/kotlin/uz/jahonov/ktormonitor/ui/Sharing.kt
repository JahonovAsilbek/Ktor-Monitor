package uz.jahonov.ktormonitor.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.core.content.FileProvider
import uz.jahonov.ktormonitor.presentation.SharedFile

/** A class of its own, so it never clashes with a `FileProvider` the app declares. */
internal class KtorMonitorFileProvider : FileProvider()

/** Writes [file] to the cache and opens the share sheet for it. */
internal fun Context.share(file: SharedFile) {
    val directory = cacheDir.resolve(SHARE_DIRECTORY).apply { mkdirs() }
    val written = directory.resolve(file.name).apply { writeText(file.content) }
    val uri = FileProvider.getUriForFile(this, "$packageName.ktormonitor.files", written)
    val send = Intent(Intent.ACTION_SEND)
        .setType(file.mimeType)
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    startActivity(Intent.createChooser(send, file.name))
}

internal fun Context.copyToClipboard(text: String) {
    getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Network monitor", text))
    // Android 13 and later confirm a copy themselves.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show()
}

private const val SHARE_DIRECTORY = "ktormonitor"
