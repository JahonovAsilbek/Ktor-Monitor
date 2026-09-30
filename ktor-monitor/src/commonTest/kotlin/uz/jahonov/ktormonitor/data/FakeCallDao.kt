package uz.jahonov.ktormonitor.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.io.bytestring.encodeToByteString
import uz.jahonov.ktormonitor.capture.KtorMonitorConfig
import uz.jahonov.ktormonitor.model.CapturedBody
import uz.jahonov.ktormonitor.model.NetworkCall

/** Room's DAO in memory, for tests that run on the JVM too. The real queries are tested on iOS. */
internal class FakeCallDao : CallDao {
    private val rows = MutableStateFlow<List<CallEntity>>(emptyList())

    override suspend fun upsert(call: CallEntity) = rows.update { list -> list.filterNot { it.id == call.id } + call }

    override fun observeSummaries(query: String, limit: Int): Flow<List<CallSummaryRow>> = rows.map { list ->
        val plain = query.replace("\\%", "%").replace("\\_", "_").replace("\\\\", "\\")
        list.filter { plain.isEmpty() || it.searchable().any { field -> field.contains(plain, ignoreCase = true) } }
            .sortedByDescending { it.requestTime }
            .take(limit)
            .map { it.toRow() }
    }

    override fun observe(id: String): Flow<CallEntity?> = rows.map { list -> list.firstOrNull { it.id == id } }

    override suspend fun get(ids: List<String>) = rows.value.filter { it.id in ids }.sortedByDescending { it.requestTime }

    override suspend fun delete(ids: List<String>) = rows.update { list -> list.filterNot { it.id in ids } }

    override suspend fun deleteAll() = rows.update { emptyList() }

    override suspend fun deleteOlderThan(time: Long) = rows.update { list -> list.filterNot { it.requestTime < time } }

    override suspend fun keepNewest(count: Int) = rows.update { list -> list.sortedByDescending { it.requestTime }.take(count) }

    private fun CallEntity.searchable() = listOfNotNull(
        url, method, responseCode?.toString(), requestBody?.decodeToString(), responseBody?.decodeToString(),
    )

    private fun CallEntity.toRow() = CallSummaryRow(
        id, groupId, attempt, method, url, requestTime, requestContentType, requestBodySize, protocol,
        responseCode, responseTime, responseContentType, responseBodySize, error,
    )
}

internal fun fakeRepository(configure: KtorMonitorConfig.() -> Unit = {}, now: () -> Long = { 0 }) =
    KtorMonitorRepository(FakeCallDao(), KtorMonitorConfig().apply(configure), now)

/** A finished GET unless told otherwise. */
internal fun testCall(
    id: String,
    url: String = "https://api.test/$id",
    method: String = "GET",
    requestTime: Long = 0,
    responseCode: Int? = 200,
    responseTime: Long? = responseCode?.let { requestTime + 100 },
    responseContentType: String? = "application/json",
    responseBody: String? = "{}",
    error: String? = null,
) = NetworkCall(
    id = id,
    groupId = "group-$id",
    attempt = 1,
    method = method,
    url = url,
    requestTime = requestTime,
    requestHeaders = mapOf("Accept" to listOf("application/json")),
    requestContentType = null,
    requestBody = null,
    protocol = responseCode?.let { "HTTP/1.1" },
    responseCode = responseCode,
    responseTime = responseTime,
    responseHeaders = responseContentType?.let { mapOf("Content-Type" to listOf(it)) }.orEmpty(),
    responseContentType = responseContentType,
    responseBody = responseBody?.let { CapturedBody(it.encodeToByteString(), it.length.toLong()) },
    error = error,
)
