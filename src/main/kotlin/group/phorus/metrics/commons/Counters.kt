/**
 * Counter extension functions for [MeterRegistry][io.micrometer.core.instrument.MeterRegistry].
 *
 * These functions reduce the boilerplate of creating, tagging, registering, and incrementing a
 * [Counter][io.micrometer.core.instrument.Counter]. Tags are passed as [Pair]s (`"key" to "value"`)
 * for readability.
 *
 * Micrometer reuses the same counter instance when name and tags match, so calling these functions
 * repeatedly with the same arguments is safe and efficient.
 */
package group.phorus.metrics.commons

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry

/**
 * Increments a counter by 1.
 *
 * This is the simplest way to record that something happened. For domain-specific request counting
 * with pre-defined tag structure, see [countRequest] and [countRetry].
 *
 * ```
 * registry.count("app.events", TagNames.TYPE to "login", TagNames.REGION to "eu")
 * ```
 *
 * @param name the metric name.
 * @param tags key-value pairs attached to the metric.
 */
fun MeterRegistry.count(
    name: String,
    vararg tags: Pair<String, String>,
) {
    Counter.builder(name)
        .tags(*tags.toTagArray())
        .register(this)
        .increment()
}

/**
 * Increments a counter by a custom [amount].
 *
 * Useful when a single event represents more than one logical unit (e.g. bytes transferred,
 * items processed in a batch).
 *
 * ```
 * registry.countBy("app.bytes.transferred", 4096.0, TagNames.DIRECTION to "inbound")
 * ```
 *
 * @param name the metric name.
 * @param amount the value to add. Must be non-negative.
 * @param tags key-value pairs attached to the metric.
 */
fun MeterRegistry.countBy(
    name: String,
    amount: Double,
    vararg tags: Pair<String, String>,
) {
    Counter.builder(name)
        .tags(*tags.toTagArray())
        .register(this)
        .increment(amount)
}

/**
 * Records a service-to-service request under the `service.request` metric.
 *
 * Produces the tags: [TagNames.SOURCE], [TagNames.TARGET], [TagNames.TYPE] ([RequestType.value]), and optionally [TagNames.URI].
 * This gives a consistent tag schema across all services for request counting.
 *
 * For the timed equivalent that also records latency and exception tags, see
 * [timedRequest][group.phorus.metrics.commons.timedRequest].
 *
 * @param source the calling service name.
 * @param target the destination service or API name.
 * @param type whether the request is [internal][RequestType.INTERNAL] or [external][RequestType.EXTERNAL].
 * @param uri optional request path. Only use bounded values here (e.g. route templates, not raw URLs)
 *   to avoid cardinality issues.
 */
fun MeterRegistry.countRequest(
    source: String,
    target: String,
    type: RequestType,
    uri: String? = null,
) {
    Counter.builder("service.request")
        .tag(TagNames.SOURCE, source)
        .tag(TagNames.TARGET, target)
        .tag(TagNames.TYPE, type.value)
        .apply { if (uri != null) tag(TagNames.URI, uri) }
        .register(this)
        .increment()
}

/**
 * Records a retry attempt under the `service.request.retry` metric.
 *
 * The [attempt] number is included as a tag so you can distinguish first retries from subsequent ones
 * in dashboards. Pair this with [countRequest] to get both total requests and retry rates.
 *
 * @param source the calling service name.
 * @param target the destination service or API name.
 * @param type whether the request is [internal][RequestType.INTERNAL] or [external][RequestType.EXTERNAL].
 * @param attempt the 1-based retry attempt number.
 * @param uri optional request path.
 */
fun MeterRegistry.countRetry(
    source: String,
    target: String,
    type: RequestType,
    attempt: Int,
    uri: String? = null,
) {
    Counter.builder("service.request.retry")
        .tag(TagNames.SOURCE, source)
        .tag(TagNames.TARGET, target)
        .tag(TagNames.TYPE, type.value)
        .tag(TagNames.ATTEMPT, attempt.toString())
        .apply { if (uri != null) tag(TagNames.URI, uri) }
        .register(this)
        .increment()
}

/**
 * Records an HTTP status code occurrence with both family and code tags.
 *
 * Tags produced: [TagNames.STATUS_FAMILY] (e.g. `"2xx"`) and [TagNames.STATUS_CODE] (e.g. `"200"`). The family tag
 * keeps aggregation queries fast and bounded, while the code tag allows drill-down when needed.
 * Additional tags can be appended via [tags].
 *
 * The family is computed by [statusFamily].
 *
 * ```
 * registry.countStatus("http.server.responses", 404, TagNames.METHOD to "GET")
 * ```
 *
 * @param name the metric name.
 * @param statusCode the HTTP status code.
 * @param tags additional key-value pairs.
 */
fun MeterRegistry.countStatus(
    name: String,
    statusCode: Int,
    vararg tags: Pair<String, String>,
) {
    Counter.builder(name)
        .tag(TagNames.STATUS_FAMILY, statusFamily(statusCode))
        .tag(TagNames.STATUS_CODE, statusCode.toString())
        .tags(*tags.toTagArray())
        .register(this)
        .increment()
}
