package uz.jahonov.ktormonitor.ui.detail

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.io.bytestring.ByteString
import uz.jahonov.ktormonitor.body.BodyPreview
import uz.jahonov.ktormonitor.body.ImageFormat
import uz.jahonov.ktormonitor.ui.ui.MonitorTheme
import uz.jahonov.ktormonitor.ui.ui.Spinner

private sealed interface Decoded {
    data object Loading : Decoded
    data object Failed : Decoded
    class Ready(val bitmap: ImageBitmap, val width: Int, val height: Int) : Decoded
}

/**
 * The image a body holds, decoded off the main thread. Android has no SVG decoder, so an SVG body is
 * read as code instead; a GIF shows its first frame.
 */
@Composable
internal fun ImagePreview(image: BodyPreview.Image, modifier: Modifier = Modifier) {
    if (image.format == ImageFormat.SVG) {
        Notice("SVG preview is not available; see Code", modifier)
        return
    }
    val decoded by produceState<Decoded>(Decoded.Loading, image.bytes) {
        value = withContext(Dispatchers.Default) { decode(image.bytes) }
    }
    when (val result = decoded) {
        Decoded.Loading -> Spinner(modifier.padding(vertical = 16.dp))
        Decoded.Failed -> Notice("The image could not be decoded", modifier)
        is Decoded.Ready -> Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Image(
                bitmap = result.bitmap,
                contentDescription = "Body image",
                contentScale = ContentScale.Fit,
                alignment = Alignment.TopStart,
                modifier = Modifier
                    .heightIn(max = MAX_HEIGHT)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MonitorTheme.colors.surface),
            )
            BasicText(
                "${result.width} × ${result.height} px, ${image.format.name}",
                style = MonitorTheme.typography.caption.copy(color = MonitorTheme.colors.textSecondary),
            )
        }
    }
}

@Composable
internal fun Notice(text: String, modifier: Modifier = Modifier) {
    BasicText(
        text,
        style = MonitorTheme.typography.body.copy(color = MonitorTheme.colors.textSecondary),
        modifier = modifier.fillMaxWidth().padding(vertical = 8.dp),
    )
}

/** Large images are sampled down: a 250 KB JPEG can still be 6000 px wide. */
private fun decode(bytes: ByteString): Decoded {
    val array = bytes.toByteArray()
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(array, 0, array.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return Decoded.Failed
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_SIDE_PX) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    val bitmap = BitmapFactory.decodeByteArray(array, 0, array.size, options) ?: return Decoded.Failed
    return Decoded.Ready(bitmap.asImageBitmap(), bounds.outWidth, bounds.outHeight)
}

private const val MAX_SIDE_PX = 2_048
private val MAX_HEIGHT = 480.dp
