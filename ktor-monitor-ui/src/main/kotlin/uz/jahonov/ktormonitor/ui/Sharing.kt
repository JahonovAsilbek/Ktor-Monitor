package uz.jahonov.ktormonitor.ui

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PersistableBundle
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.IOException
import uz.jahonov.ktormonitor.presentation.SharedFile

/** A class of its own, so it never clashes with a `FileProvider` the app declares. */
internal class KtorMonitorFileProvider : FileProvider()

/** Writes [file] to the cache and opens the share sheet for it. A full disk only shows a message. */
internal fun Context.share(file: SharedFile) {
    // An export holds tokens and bodies: the last one goes as the next is written.
    val directory = cacheDir.resolve(SHARE_DIRECTORY).apply { deleteRecursively(); mkdirs() }
    val written = directory.resolve(file.name)
    try {
        written.writeText(file.content)
    } catch (_: IOException) {
        Toast.makeText(this, "Could not write the file to share", Toast.LENGTH_SHORT).show()
        return
    }
    val uri = FileProvider.getUriForFile(this, "$packageName.ktormonitor.files", written)
    val send = Intent(Intent.ACTION_SEND)
        .setType(file.mimeType)
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    startActivity(Intent.createChooser(send, file.name))
}

/** Marked sensitive: Android 13 and later then hide the text in the clipboard preview. */
@SuppressLint("InlinedApi") // A plain string extra: older versions ignore it.
internal fun Context.copyToClipboard(text: String) {
    val clip = ClipData.newPlainText("Network monitor", text).apply {
        description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
    }
    getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
    // Android 13 and later confirm a copy themselves.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show()
}

private const val SHARE_DIRECTORY = "ktormonitor"
