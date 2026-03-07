/**
 * Timer extension functions for [MeterRegistry][io.micrometer.core.instrument.MeterRegistry].
 *
 * These functions wrap the [Timer.start][io.micrometer.core.instrument.Timer.start] /
 * [Timer.Sample.stop][io.micrometer.core.instrument.Timer.Sample.stop] pattern into single
 * higher-order function calls. Each function automatically adds an [TagNames.EXCEPTION] tag:
 * `"None"` on success, or the simple class name on failure. This tag is always present so that
 * Prometheus queries don't need to handle its absence.
 *
 * The [TagNames.EXCEPTION] tag uses the simple class name (e.g. `"IllegalStateException"`) rather than the
 * fully qualified name or the message string. This keeps cardinality bounded to the finite set of
 * exception types your code can throw, instead of growing with every unique message.
 *
 * All timing functions accept an optional `slos` parameter for histogram bucket boundaries.
 * The default is [SloPresets.API_RESPONSE]; pass [SloPresets.BACKGROUND_TASK] or
 * [SloPresets.LONG_RUNNING] for non-API workloads, or provide your own array.
 */
package group.phorus.metrics.commons

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import java.time.Duration

/**
 * Times a synchronous [block] and records it as a [Timer] metric.
 *
 * On success the timer is tagged with [TagNames.EXCEPTION]=None; on failure it is tagged with the exception's
 * simple class name. The original exception is always re-thrown.
 *
 * ```
 * val user = registry.timed("db.query", TagNames.TABLE to "users") {
 *     userRepository.findById(id)
 * }
 * ```
 *
 * @param R the return type of [block].
 * @param name the metric name.
 * @param tags key-value pairs attached to the metric.
 * @param slos histogram bucket boundaries. Defaults to [SloPresets.API_RESPONSE].
 * @param block the operation to time.
 * @return the result of [block].
 * @throws Exception any exception thrown by [block], after recording it.
 */
@Throws(Exception::class)
inline fun <R> MeterRegistry.timed(
    name: String,
    vararg tags: Pair<String, String>,
    slos: Array<Duration> = SloPresets.API_RESPONSE,
    block: () -> R,
): R {
    val sample = Timer.start(this)
    val tagArray = tags.toTagArray()

    return try {
        block().also {
            sample.stop(
                Timer.builder(name)
                    .serviceLevelObjectives(*slos)
                    .tags(*tagArray)
                    .tag(TagNames.EXCEPTION, "None")
                    .register(this)
            )
        }
    } catch (e: Exception) {
        sample.stop(
            Timer.builder(name)
                .serviceLevelObjectives(*slos)
                .tags(*tagArray)
                .tag(TagNames.EXCEPTION, e.javaClass.simpleName)
                .register(this)
        )
        throw e
    }
}

/**
 * Suspend variant of [timed]. Times a suspending [block] and records it as a [Timer] metric.
 *
 * Behaves identically to [timed] but accepts a `suspend` lambda, making it suitable for use
 * inside coroutines.
 *
 * ```
 * val user = registry.timedSuspend("db.query", TagNames.TABLE to "users") {
 *     userRepository.findByIdSuspend(id)
 * }
 * ```
 *
 * @param R the return type of [block].
 * @param name the metric name.
 * @param tags key-value pairs attached to the metric.
 * @param slos histogram bucket boundaries. Defaults to [SloPresets.API_RESPONSE].
 * @param block the suspending operation to time.
 * @return the result of [block].
 * @throws Exception any exception thrown by [block], after recording it.
 */
@Throws(Exception::class)
suspend inline fun <R> MeterRegistry.timedSuspend(
    name: String,
    vararg tags: Pair<String, String>,
    slos: Array<Duration> = SloPresets.API_RESPONSE,
    crossinline block: suspend () -> R,
): R {
    val sample = Timer.start(this)
    val tagArray = tags.toTagArray()

    return try {
        block().also {
            sample.stop(
                Timer.builder(name)
                    .serviceLevelObjectives(*slos)
                    .tags(*tagArray)
                    .tag(TagNames.EXCEPTION, "None")
                    .register(this)
            )
        }
    } catch (e: Exception) {
        sample.stop(
            Timer.builder(name)
                .serviceLevelObjectives(*slos)
                .tags(*tagArray)
                .tag(TagNames.EXCEPTION, e.javaClass.simpleName)
                .register(this)
        )
        throw e
    }
}

/**
 * Times a synchronous service-to-service request and records it under
 * `service.request.internal` or `service.request.external` depending on [type].
 *
 * Produces the tags: [TagNames.SOURCE], [TagNames.TARGET], optionally [TagNames.URI], and [TagNames.EXCEPTION].
 * This is the timed counterpart of [countRequest]. Use it when you need both the request count
 * and the latency distribution in a single call.
 *
 * ```
 * val response = registry.timedRequest(
 *     source = "order-service",
 *     target = "payment-api",
 *     type = RequestType.EXTERNAL,
 * ) {
 *     paymentClient.charge(order)
 * }
 * ```
 *
 * @param R the return type of [block].
 * @param source the calling service name.
 * @param target the destination service or API name.
 * @param type whether the request is [internal][RequestType.INTERNAL] or [external][RequestType.EXTERNAL].
 * @param uri optional request path. Use bounded values (route templates) to avoid cardinality issues.
 * @param slos histogram bucket boundaries. Defaults to [SloPresets.API_RESPONSE].
 * @param block the operation to time.
 * @return the result of [block].
 * @throws Exception any exception thrown by [block], after recording it.
 */
@Throws(Exception::class)
inline fun <R> MeterRegistry.timedRequest(
    source: String,
    target: String,
    type: RequestType,
    uri: String? = null,
    slos: Array<Duration> = SloPresets.API_RESPONSE,
    block: () -> R,
): R {
    val sample = Timer.start(this)

    return try {
        block().also {
            sample.stop(
                requestTimerBuilder("service.request.${type.value}", source, target, uri, slos)
                    .tag(TagNames.EXCEPTION, "None")
                    .register(this)
            )
        }
    } catch (e: Exception) {
        sample.stop(
            requestTimerBuilder("service.request.${type.value}", source, target, uri, slos)
                .tag(TagNames.EXCEPTION, e.javaClass.simpleName)
                .register(this)
        )
        throw e
    }
}

/**
 * Suspend variant of [timedRequest]. Times a suspending service-to-service request.
 *
 * @see timedRequest for full documentation.
 *
 * @param R the return type of [block].
 * @param source the calling service name.
 * @param target the destination service or API name.
 * @param type whether the request is [internal][RequestType.INTERNAL] or [external][RequestType.EXTERNAL].
 * @param uri optional request path.
 * @param slos histogram bucket boundaries. Defaults to [SloPresets.API_RESPONSE].
 * @param block the suspending operation to time.
 * @return the result of [block].
 * @throws Exception any exception thrown by [block], after recording it.
 */
@Throws(Exception::class)
suspend inline fun <R> MeterRegistry.timedRequestSuspend(
    source: String,
    target: String,
    type: RequestType,
    uri: String? = null,
    slos: Array<Duration> = SloPresets.API_RESPONSE,
    crossinline block: suspend () -> R,
): R {
    val sample = Timer.start(this)

    return try {
        block().also {
            sample.stop(
                requestTimerBuilder("service.request.${type.value}", source, target, uri, slos)
                    .tag(TagNames.EXCEPTION, "None")
                    .register(this)
            )
        }
    } catch (e: Exception) {
        sample.stop(
            requestTimerBuilder("service.request.${type.value}", source, target, uri, slos)
                .tag(TagNames.EXCEPTION, e.javaClass.simpleName)
                .register(this)
        )
        throw e
    }
}

/**
 * Records a pre-measured [Duration] into a [Timer] metric.
 *
 * Use this when the duration has already been measured externally (e.g. from a response header
 * or a third-party SDK) and you just need to feed it into micrometer. For timing a block of code
 * directly, prefer [timed] or [timedSuspend].
 *
 * ```
 * val elapsed = Duration.between(start, Instant.now())
 * registry.recordDuration("http.client.duration", elapsed, TagNames.TARGET to "stripe")
 * ```
 *
 * @param name the metric name.
 * @param duration the measured duration.
 * @param tags key-value pairs attached to the metric.
 * @param slos histogram bucket boundaries. Defaults to [SloPresets.API_RESPONSE].
 */
fun MeterRegistry.recordDuration(
    name: String,
    duration: Duration,
    vararg tags: Pair<String, String>,
    slos: Array<Duration> = SloPresets.API_RESPONSE,
) {
    Timer.builder(name)
        .serviceLevelObjectives(*slos)
        .tags(*tags.toTagArray())
        .register(this)
        .record(duration)
}

/**
 * Builds a [Timer.Builder] with the standard request tags ([TagNames.SOURCE], [TagNames.TARGET], optionally [TagNames.URI]).
 *
 * Shared by [timedRequest] and [timedRequestSuspend] to avoid duplicating the builder setup.
 */
@PublishedApi
internal fun requestTimerBuilder(
    name: String,
    source: String,
    target: String,
    uri: String?,
    slos: Array<Duration>,
): Timer.Builder = Timer.builder(name)
    .serviceLevelObjectives(*slos)
    .tag(TagNames.SOURCE, source)
    .tag(TagNames.TARGET, target)
    .apply { if (uri != null) tag(TagNames.URI, uri) }
