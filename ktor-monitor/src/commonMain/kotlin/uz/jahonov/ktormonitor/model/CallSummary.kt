package uz.jahonov.ktormonitor.model

import uz.jahonov.ktormonitor.InternalKtorMonitorApi
import io.ktor.http.Url
import io.ktor.http.isSecure

/** A call as the list shows it: no headers, no bodies. */
@InternalKtorMonitorApi
public data class CallSummary(
    val id: String,
    val groupId: String,
    val attempt: Int,
    val method: String,
    val url: String,
    val requestTime: Long,
    val requestContentType: String?,
    val requestSize: Long?,
    val protocol: String?,
    val responseCode: Int?,
    val responseTime: Long?,
    val responseContentType: String?,
    val responseSize: Long?,
    val error: String?,
) {
    private val parsed by lazy { runCatching { Url(url) }.getOrNull() }

    val host: String get() = parsed?.host.orEmpty()

    /** Path and query, as the row shows them. */
    val path: String get() = parsed?.let { it.encodedPathAndQuery.ifEmpty { "/" } } ?: url

    /** https or wss. */
    val isSecure: Boolean get() = parsed?.protocol?.isSecure() == true

    val isInProgress: Boolean get() = responseCode == null && error == null
    /** 3xx but 304: Not Modified answers from the cache, it sends nowhere. */
    val isRedirect: Boolean get() = responseCode in 300..399 && responseCode != 304

    /** An exception, or a status outside 1xx–3xx. */
    val isError: Boolean get() = error != null || (responseCode != null && responseCode !in 100..399)

    val durationMillis: Long? get() = responseTime?.let { it - requestTime }

    val kind: ContentKind get() = ContentKind.of(responseContentType, url)
}
