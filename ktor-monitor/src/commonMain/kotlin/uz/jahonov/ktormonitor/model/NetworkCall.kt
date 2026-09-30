package uz.jahonov.ktormonitor.model

import kotlinx.io.bytestring.ByteString

/**
 * One network attempt as the monitor recorded it. A request retried after a token refresh gives
 * several records with the same [groupId] and a growing [attempt].
 *
 * Times are epoch milliseconds. A record with neither [responseCode] nor [error] is still in flight.
 */
public data class NetworkCall(
    val id: String,
    val groupId: String,
    val attempt: Int,
    val method: String,
    val url: String,
    val requestTime: Long,
    val requestHeaders: Map<String, List<String>>,
    val requestContentType: String?,
    val requestBody: CapturedBody?,
    val protocol: String? = null,
    val responseCode: Int? = null,
    val responseTime: Long? = null,
    val responseHeaders: Map<String, List<String>> = emptyMap(),
    val responseContentType: String? = null,
    val responseBody: CapturedBody? = null,
    val error: String? = null,
) {
    val summary: CallSummary
        get() = CallSummary(
            id = id,
            groupId = groupId,
            attempt = attempt,
            method = method,
            url = url,
            requestTime = requestTime,
            requestContentType = requestContentType,
            requestSize = requestBody?.size,
            protocol = protocol,
            responseCode = responseCode,
            responseTime = responseTime,
            responseContentType = responseContentType,
            responseSize = responseBody?.size,
            error = error,
        )

    val isInProgress: Boolean get() = summary.isInProgress
    val durationMillis: Long? get() = summary.durationMillis
}

/**
 * A body as far as it was kept: at most the configured number of bytes, with the full [size] it had.
 * [isTruncated] is exact even when the size was not known up front.
 */
public data class CapturedBody(
    val bytes: ByteString,
    val size: Long,
) {
    val isTruncated: Boolean get() = size > bytes.size

    /** The kept bytes as text in the charset of [contentType], or null when the body is binary. */
    public fun text(contentType: String?): String? = decodeText(bytes, contentType)
}
