package uz.jahonov.ktormonitor.presentation.list

import io.ktor.http.ContentType
import uz.jahonov.ktormonitor.model.CallSummary

/** A status class, or FAILED: a call that ended with no status at all (a timeout, no connection). */
public enum class StatusClass(public val label: String, private val codes: IntRange?) {
    INFORMATIONAL("1xx", 100..199),
    SUCCESS("2xx", 200..299),
    REDIRECT("3xx", 300..399),
    CLIENT_ERROR("4xx", 400..499),
    SERVER_ERROR("5xx", 500..599),
    FAILED("Failed", null),
    ;

    internal fun matches(call: CallSummary): Boolean =
        codes?.let { call.responseCode in it } ?: (call.error != null && call.responseCode == null)
}

public enum class DurationRange(public val label: String, internal val millis: LongRange) {
    UNDER_200_MS("< 200 ms", 0L until 200),
    UNDER_1_S("200 ms – 1 s", 200L until 1_000),
    UNDER_5_S("1 – 5 s", 1_000L until 5_000),
    OVER_5_S("> 5 s", 5_000L..Long.MAX_VALUE),
}

public enum class CallSort(public val label: String) {
    NEWEST("Newest first"),
    SIZE_ASCENDING("Size, smallest first"),
    SIZE_DESCENDING("Size, largest first"),
    DURATION_ASCENDING("Duration, fastest first"),
    DURATION_DESCENDING("Duration, slowest first"),
}

/** Values chosen in each filter. A call passes when it matches one value of every non-empty filter. */
public data class CallFilters(
    val hosts: Set<String> = emptySet(),
    val methods: Set<String> = emptySet(),
    val contentTypes: Set<String> = emptySet(),
    val statuses: Set<StatusClass> = emptySet(),
    val durations: Set<DurationRange> = emptySet(),
) {
    val isEmpty: Boolean
        get() = hosts.isEmpty() && methods.isEmpty() && contentTypes.isEmpty() && statuses.isEmpty() && durations.isEmpty()

    /** How many values are chosen, across all filters. */
    val count: Int
        get() = hosts.size + methods.size + contentTypes.size + statuses.size + durations.size

    internal fun matches(call: CallSummary): Boolean =
        (hosts.isEmpty() || call.host in hosts) &&
            (methods.isEmpty() || call.method in methods) &&
            (contentTypes.isEmpty() || call.mediaType in contentTypes) &&
            (statuses.isEmpty() || statuses.any { it.matches(call) }) &&
            (durations.isEmpty() || durations.any { range -> call.durationMillis?.let { it in range.millis } == true })
}

public data class FilterOptions(
    val hosts: List<String> = emptyList(),
    val methods: List<String> = emptyList(),
    val contentTypes: List<String> = emptyList(),
    val statuses: List<StatusClass> = StatusClass.entries.toList(),
    val durations: List<DurationRange> = DurationRange.entries.toList(),
) {
    internal companion object {
        fun of(calls: List<CallSummary>) = FilterOptions(
            hosts = calls.map { it.host }.filter { it.isNotEmpty() }.distinct().sorted(),
            methods = calls.map { it.method }.distinct().sorted(),
            contentTypes = calls.mapNotNull { it.mediaType }.distinct().sorted(),
        )
    }
}

internal fun List<CallSummary>.shown(onlyErrors: Boolean, filters: CallFilters, sort: CallSort): List<CallSummary> {
    val shown = filter { (!onlyErrors || it.isError) && filters.matches(it) }
    // Calls without a size or duration yet go last in either direction.
    return when (sort) {
        CallSort.NEWEST -> shown
        CallSort.SIZE_ASCENDING -> shown.sortedWith(compareBy(nullsLast()) { it.responseSize })
        CallSort.SIZE_DESCENDING -> shown.sortedWith(compareByDescending<CallSummary, Long?>(nullsFirst()) { it.responseSize })
        CallSort.DURATION_ASCENDING -> shown.sortedWith(compareBy(nullsLast()) { it.durationMillis })
        CallSort.DURATION_DESCENDING -> shown.sortedWith(compareByDescending<CallSummary, Long?>(nullsFirst()) { it.durationMillis })
    }
}

/** The response content type without parameters, as the content-type filter lists it. */
private val CallSummary.mediaType: String?
    get() = responseContentType?.let { runCatching { ContentType.parse(it).withoutParameters().toString() }.getOrNull() }
