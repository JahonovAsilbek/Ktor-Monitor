package uz.jahonov.ktormonitor.capture

import io.ktor.client.request.HttpRequestBuilder
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

/** Set once, when the monitor is created. Defaults record everything and redact nothing. */
public class KtorMonitorConfig internal constructor() {
    /** `false` records nothing and shows no notification. */
    public var isActive: Boolean = true

    /** Bytes kept per body, at least 0; the rest is counted, not stored. */
    public var maxContentLength: Int = DEFAULT_MAX_CONTENT_LENGTH
        set(value) {
            require(value >= 0) { "maxContentLength must be at least 0, was $value" }
            field = value
        }

    /** How long a call is kept. */
    public var retention: Retention = Retention.OneHour

    /** Most calls kept, at least 1; the oldest go first. */
    public var maxCalls: Int = DEFAULT_MAX_CALLS
        set(value) {
            require(value > 0) { "maxCalls must be at least 1, was $value" }
            field = value
        }

    /** A notification with the latest calls. `false` also removes one already shown. */
    public var showNotification: Boolean = true

    /** The app named in exports (HAR `creator`, JSON `app`). `null` reads it from the platform. */
    public var appName: String? = null

    /** The version named in exports. `null` reads it from the platform. */
    public var appVersion: String? = null

    /** Any failure of the monitor itself, such as a database error. The app's call is never affected. */
    public var onInternalError: (Throwable) -> Unit = {}

    internal val filters = mutableListOf<(HttpRequestBuilder) -> Boolean>()
    internal val headerRules = mutableListOf<HeaderRule>()
    internal val redactedFields = mutableSetOf<String>()
    internal val redactedQueryParameters = mutableSetOf<String>()

    /** Records only the calls some predicate accepts. With no filter, every call is recorded. */
    public fun filter(predicate: (HttpRequestBuilder) -> Boolean) {
        filters += predicate
    }

    /** Replaces the values of every request and response header [predicate] accepts. */
    public fun sanitizeHeader(placeholder: String = PLACEHOLDER, predicate: (name: String) -> Boolean) {
        headerRules += HeaderRule(placeholder, predicate)
    }

    /** [sanitizeHeader] by name, in any letter case. */
    public fun sanitizeHeaders(vararg names: String, placeholder: String = PLACEHOLDER): Unit =
        sanitizeHeader(placeholder) { name -> names.any { it.equals(name, ignoreCase = true) } }

    /**
     * Replaces the values of these fields, in any letter case, in both bodies: JSON keys at any depth
     * (in a JSON body, or a text one that is JSON), and form fields (`application/x-www-form-urlencoded`).
     * A JSON body that cannot be redacted (cut short, malformed) is not recorded at all. Other bodies,
     * multipart included, are recorded as they are.
     */
    public fun redactBodyFields(vararg names: String) {
        redactedFields += names.map { it.lowercase() }
    }

    /** Replaces the values of these URL query parameters, in any letter case, such as `access_token`. */
    public fun redactQueryParameters(vararg names: String) {
        redactedQueryParameters += names.map { it.lowercase() }
    }

    internal class HeaderRule(val placeholder: String, val matches: (String) -> Boolean)

    public companion object {
        public const val DEFAULT_MAX_CONTENT_LENGTH: Int = 250_000
        public const val DEFAULT_MAX_CALLS: Int = 1_000
        public const val PLACEHOLDER: String = "***"
    }
}

public enum class Retention(public val period: Duration) {
    OneHour(1.hours),
    OneDay(1.days),
    OneWeek(7.days),
    Forever(Duration.INFINITE),
}
