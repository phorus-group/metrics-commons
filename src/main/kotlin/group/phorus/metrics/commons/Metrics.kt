/**
 * Core types, tag safety utilities, and SLO bucket presets for metrics-commons.
 *
 * This file contains the foundational building blocks used across the library. The tag utility functions
 * are designed to prevent high-cardinality issues in metric backends like Prometheus, where unbounded
 * tag values (e.g. raw user IDs, freeform strings, or full exception stack traces) can cause memory
 * exhaustion and slow queries.
 *
 * All tag utilities produce deterministic, bounded output suitable for use as metric tag values.
 * The same utilities are also available for span tags via [tagSafe] and [tagBounded] in `Tracing.kt`.
 */
package group.phorus.metrics.commons

import io.micrometer.core.instrument.MeterRegistry
import java.time.Duration

/**
 * Classifies a service request as internal (between owned services) or external (to third-party APIs).
 *
 * Used by [countRequest][group.phorus.metrics.commons.countRequest],
 * [timedRequest][group.phorus.metrics.commons.timedRequest], and related functions to produce
 * a consistent `type` tag across all request metrics.
 *
 * @param value the tag value written to the metric.
 */
enum class RequestType(val value: String) {
    /** Request between services you own. */
    INTERNAL("internal"),

    /** Request to a third-party or external API. */
    EXTERNAL("external"),
}

/**
 * Predefined service-level objective (SLO) bucket arrays for [Timer][io.micrometer.core.instrument.Timer]
 * histogram boundaries.
 *
 * These presets cover the most common latency profiles. Using a shared set of buckets across services
 * ensures consistent histogram granularity, which matters when aggregating across services in Grafana
 * or similar tools. If none of these fit, pass a custom array to the `slos` parameter.
 */
object SloPresets {
    /**
     * Buckets for synchronous API responses: 5ms to 10s.
     *
     * Suitable for HTTP handlers, gRPC calls, and database queries.
     */
    val API_RESPONSE: Array<Duration> = arrayOf(
        Duration.ofMillis(5), Duration.ofMillis(10), Duration.ofMillis(25),
        Duration.ofMillis(50), Duration.ofMillis(100), Duration.ofMillis(250),
        Duration.ofMillis(500), Duration.ofMillis(1000), Duration.ofMillis(2500),
        Duration.ofMillis(5000), Duration.ofMillis(10000),
    )

    /**
     * Buckets for background tasks: 1s to 30min.
     *
     * Suitable for scheduled jobs, queue consumers, and batch processing steps.
     */
    val BACKGROUND_TASK: Array<Duration> = arrayOf(
        Duration.ofSeconds(1), Duration.ofSeconds(5), Duration.ofSeconds(10),
        Duration.ofSeconds(30), Duration.ofMinutes(1), Duration.ofMinutes(5),
        Duration.ofMinutes(15), Duration.ofMinutes(30),
    )

    /**
     * Buckets for long-running operations: 1min to 24h.
     *
     * Suitable for data migrations, full reindexing, or report generation.
     */
    val LONG_RUNNING: Array<Duration> = arrayOf(
        Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(15),
        Duration.ofMinutes(30), Duration.ofHours(1), Duration.ofHours(2),
        Duration.ofHours(6), Duration.ofHours(12), Duration.ofHours(24),
    )
}

/**
 * Configuration DSL for recording metrics alongside a traced operation.
 *
 * Instances are created internally by the tracing functions when a [MeterRegistry] is provided.
 * Use [timed] to also record a [Timer][io.micrometer.core.instrument.Timer] and [counted] to
 * also increment a [Counter][io.micrometer.core.instrument.Counter].
 *
 * ```
 * tracer.tracedRequest(
 *     source = "order-service",
 *     target = "payment-api",
 *     type = RequestType.EXTERNAL,
 *     registry = registry,
 *     metrics = { timed(); counted() },
 * ) { span ->
 *     paymentClient.charge(order)
 * }
 * ```
 *
 * @see traced
 * @see tracedRequest
 */
class MetricsConfig @PublishedApi internal constructor(
    @PublishedApi internal val registry: MeterRegistry,
) {
    /** Whether to record a [Timer][io.micrometer.core.instrument.Timer] for the traced block. */
    @PublishedApi internal var timed: Boolean = false

    /** Whether to increment a [Counter][io.micrometer.core.instrument.Counter] for the traced block. */
    @PublishedApi internal var counted: Boolean = false

    /** Histogram bucket boundaries for the timer. Defaults to [SloPresets.API_RESPONSE]. */
    @PublishedApi internal var slos: Array<Duration> = SloPresets.API_RESPONSE

    /**
     * Also record a [Timer][io.micrometer.core.instrument.Timer] for the traced operation.
     *
     * For `tracedRequest` / `tracedRequestSuspend`, the timer uses the same
     * `service.request.{type}` name and `source`/`target`/`uri`/`exception` tags as
     * [timedRequest][group.phorus.metrics.commons.timedRequest].
     *
     * For `traced` / `tracedSuspend`, the timer reuses the span name and tags, adding an
     * `exception` tag (`"None"` on success, simple class name on failure).
     *
     * @param slos histogram bucket boundaries. Defaults to [SloPresets.API_RESPONSE].
     */
    fun timed(slos: Array<Duration> = SloPresets.API_RESPONSE): MetricsConfig {
        this.timed = true
        this.slos = slos
        return this
    }

    /**
     * Also increment a [Counter][io.micrometer.core.instrument.Counter] for the traced operation.
     *
     * For `tracedRequest` / `tracedRequestSuspend`, the counter uses the same `service.request`
     * name and `source`/`target`/`type`/`uri` tags as
     * [countRequest][group.phorus.metrics.commons.countRequest].
     *
     * For `traced` / `tracedSuspend`, the counter name is `"{spanName}.count"` with the same
     * tags as the span.
     */
    fun counted(): MetricsConfig {
        this.counted = true
        return this
    }
}

/**
 * Sanitizes a [String] for use as a metric tag value.
 *
 * Trims whitespace and replaces null or blank values with `"None"`. This prevents empty or whitespace-only
 * strings from becoming invisible tag values that are hard to query in dashboards.
 *
 * @param value the raw value, or null.
 * @return the trimmed value, or `"None"` if the input was null or blank.
 */
@JvmOverloads
fun tagValue(value: String? = null): String = value.orEmpty().trim().ifBlank { "None" }

/**
 * Groups an HTTP status code into its family: `"1xx"`, `"2xx"`, `"3xx"`, `"4xx"`, or `"5xx"`.
 *
 * Using the family instead of the raw status code as a tag value keeps cardinality bounded. Individual
 * status codes can still be tracked separately via [countStatus] if needed, which records both
 * `status_family` and `status_code` tags: the family for aggregation, the code for drill-down.
 *
 * @param statusCode the HTTP status code (e.g. 200, 404, 503).
 * @return the status family string.
 */
fun statusFamily(statusCode: Int): String = "${statusCode / 100}xx"

/**
 * Extracts a bounded tag value from a [Throwable].
 *
 * Returns the simple class name (e.g. `"IllegalArgumentException"`), or `"None"` if null. Using the
 * simple class name rather than the full qualified name or the message keeps cardinality manageable
 * while still providing enough information to identify the exception type.
 *
 * @param throwable the exception, or null.
 * @return the simple class name, or `"None"`.
 */
fun exceptionTag(throwable: Throwable?): String = throwable?.javaClass?.simpleName ?: "None"

/**
 * Restricts a tag value to an explicit set of allowed values, falling back to a default otherwise.
 *
 * This is the primary tool for preventing cardinality explosion from user-controlled or dynamic input.
 * Any value not in [allowed] is collapsed into [fallback], guaranteeing a fixed upper bound on the
 * number of distinct tag values.
 *
 * Example:
 * ```
 * val method = boundedTag(request.method, setOf("GET", "POST", "PUT", "DELETE"))
 * // "GET" -> "GET", "PATCH" -> "other", null -> "other"
 * ```
 *
 * @param value the raw input.
 * @param allowed the set of permitted values.
 * @param fallback the value to use when [value] is not in [allowed]. Defaults to `"other"`.
 * @return the sanitized tag value.
 */
fun boundedTag(value: String?, allowed: Set<String>, fallback: String = "other"): String {
    val sanitized = tagValue(value)
    return if (sanitized in allowed) sanitized else fallback
}

/**
 * Converts an array of [Pair]s to the flat `[key, value, key, value, ...]` format expected by
 * micrometer's `.tags()` method.
 */
@PublishedApi
internal fun Array<out Pair<String, String>>.toTagArray(): Array<String> =
    flatMap { listOf(it.first, it.second) }.toTypedArray()
