package uz.jahonov.ktormonitor.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.io.bytestring.ByteString
import uz.jahonov.ktormonitor.capture.CallStore
import uz.jahonov.ktormonitor.capture.KtorMonitorConfig
import uz.jahonov.ktormonitor.model.CallSummary
import uz.jahonov.ktormonitor.model.CapturedBody
import uz.jahonov.ktormonitor.model.NetworkCall

/**
 * The recorded history. Old calls go as new ones arrive: past the retention period, and beyond
 * `maxCalls`, the oldest first.
 */
internal class KtorMonitorRepository(
    private val dao: CallDao,
    private val config: KtorMonitorConfig,
    private val now: () -> Long,
) : CallStore {

    override suspend fun insert(call: NetworkCall) {
        dao.upsert(call.toEntity())
        trim()
    }

    override suspend fun update(call: NetworkCall) {
        dao.update(call.toEntity())
    }

    /** Newest first, matching [query] in the URL, method, status code or either body. */
    fun calls(query: String = "", limit: Int = Int.MAX_VALUE): Flow<List<CallSummary>> =
        dao.observeSummaries(query.escapedForLike(), limit).map { rows -> rows.map { it.toSummary() } }

    fun call(id: String): Flow<NetworkCall?> = dao.observe(id).map { it?.toCall() }

    suspend fun calls(ids: List<String>): List<NetworkCall> = dao.get(ids).map { it.toCall() }

    suspend fun delete(ids: List<String>) = dao.delete(ids)

    suspend fun clear() = dao.deleteAll()

    private suspend fun trim() {
        val period = config.retention.period
        if (period.isFinite()) dao.deleteOlderThan(now() - period.inWholeMilliseconds)
        dao.keepNewest(config.maxCalls)
    }
}

private fun String.escapedForLike() = replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

private fun NetworkCall.toEntity() = CallEntity(
    id = id,
    groupId = groupId,
    attempt = attempt,
    method = method,
    url = url,
    requestTime = requestTime,
    requestHeaders = requestHeaders,
    requestContentType = requestContentType,
    requestBody = requestBody?.bytes?.toByteArray(),
    requestBodySize = requestBody?.size,
    protocol = protocol,
    responseCode = responseCode,
    responseTime = responseTime,
    responseHeaders = responseHeaders,
    responseContentType = responseContentType,
    responseBody = responseBody?.bytes?.toByteArray(),
    responseBodySize = responseBody?.size,
    error = error,
)

private fun CallEntity.toCall() = NetworkCall(
    id = id,
    groupId = groupId,
    attempt = attempt,
    method = method,
    url = url,
    requestTime = requestTime,
    requestHeaders = requestHeaders,
    requestContentType = requestContentType,
    requestBody = body(requestBody, requestBodySize),
    protocol = protocol,
    responseCode = responseCode,
    responseTime = responseTime,
    responseHeaders = responseHeaders,
    responseContentType = responseContentType,
    responseBody = body(responseBody, responseBodySize),
    error = error,
)

private fun body(bytes: ByteArray?, size: Long?) =
    bytes?.let { CapturedBody(ByteString(it), size ?: it.size.toLong()) }

private fun CallSummaryRow.toSummary() = CallSummary(
    id = id,
    groupId = groupId,
    attempt = attempt,
    method = method,
    url = url,
    requestTime = requestTime,
    requestContentType = requestContentType,
    requestSize = requestBodySize,
    protocol = protocol,
    responseCode = responseCode,
    responseTime = responseTime,
    responseContentType = responseContentType,
    responseSize = responseBodySize,
    error = error,
)
