/**
 * Tracing extension functions for [Tracer][io.micrometer.tracing.Tracer] and
 * [Span][io.micrometer.tracing.Span].
 *
 * These functions wrap the verbose `nextSpan()` -> `start()` -> `try/finally` -> `end()` pattern
 * into single higher-order function calls. On failure, the span is tagged with the exception via
 * [Span.error][io.micrometer.tracing.Span.error] before re-throwing, ensuring the trace backend
 * captures the error context.
 *
 * ### Bridge dependency
 *
 * This library depends only on the [Micrometer Tracing](https://micrometer.io/docs/tracing) facade
 * (`io.micrometer:micrometer-tracing`), not on any specific tracing bridge. The consuming project
 * must add the bridge dependency that matches its backend, such as:
 *
 * - **OpenTelemetry** (Grafana Tempo, Jaeger): `io.micrometer:micrometer-tracing-bridge-otel`
 *   plus an exporter such as `io.opentelemetry:opentelemetry-exporter-otlp`.
 * - **Brave** (Zipkin): `io.micrometer:micrometer-tracing-bridge-brave`
 *   plus a reporter such as `io.zipkin.reporter2:zipkin-reporter-brave`.
 *
 * If no bridge is on the classpath, the [Tracer] returns no-op spans and all helper functions
 * still execute their blocks normally.
 *
 * ### Synchronous vs. suspend variants
 *
 * **[traced] / [tracedRequest]** are for synchronous blocks. They put the span into a [ThreadLocal]
 * so that any nested `tracer.nextSpan()` call automatically uses it as the parent. This is safe
 * because the block runs on a single thread from start to finish.
 *
 * **[tracedSuspend] / [tracedRequestSuspend]** are for suspend blocks. They skip the ThreadLocal
 * because coroutines can switch threads at suspension points, which would silently lose it.
 * Instead, the span is passed explicitly to the block. See [tracedSuspend] for details.
 *
 * ### Span tag cardinality
 *
 * The same cardinality rules that apply to metric tags apply to span tags. Use [tagSafe] and
 * [tagBounded] to sanitize dynamic values before attaching them to spans. Span names should also
 * be low-cardinality (e.g. `"db.query"`, not `"db.query.SELECT * FROM users WHERE id=42"`).
 */
package group.phorus.metrics.commons

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import io.micrometer.tracing.Span
import io.micrometer.tracing.Tracer
import java.time.Duration

/**
 * Wraps a synchronous [block] in a new [Span].
 *
 * The span is started, put into [ThreadLocal scope][Tracer.withSpan] (so child spans created
 * within the block are automatically parented), and ended in a `finally` block. On exception,
 * [Span.error] is called before re-throwing.
 *
 * **This function is for synchronous blocks only.** The [block] parameter is `(Span) -> R`, not
 * `suspend`, so it runs entirely on the calling thread. The ThreadLocal scope is valid for the
 * entire duration. For suspend functions, use [tracedSuspend] instead.
 *
 * ```
 * val user = tracer.traced("db.query", TagNames.TABLE to "users") { span ->
 *     val result = userRepository.findById(id)
 *     span.event("query complete")
 *     result
 * }
 * ```
 *
 * Optionally, pass a [MeterRegistry] and a [metrics] configuration block to also record
 * metrics for the same operation without nesting:
 *
 * ```
 * val user = tracer.traced("db.query", TagNames.TABLE to "users",
 *     registry = registry,
 *     metrics = { timed() },
 * ) { span ->
 *     userRepository.findById(id)
 * }
 * ```
 *
 * @param R the return type of [block].
 * @param name the span name. Should be low-cardinality.
 * @param tags key-value pairs attached to the span.
 * @param registry optional [MeterRegistry] for recording metrics alongside the span. When null,
 *   the [metrics] block is ignored.
 * @param metrics configures which metrics to record. Defaults to [MetricsConfig.timed]. Only
 *   applied when [registry] is non-null.
 * @param block the operation to trace. Receives the active [Span].
 * @return the result of [block].
 * @throws Exception any exception thrown by [block], after recording it on the span.
 */
@Throws(Exception::class)
inline fun <R> Tracer.traced(
    name: String,
    vararg tags: Pair<String, String>,
    registry: MeterRegistry? = null,
    noinline metrics: (MetricsConfig.() -> Unit) = { timed() },
    block: (Span) -> R,
): R {
    val config = registry?.let { MetricsConfig(it).apply(metrics) }
    val span = nextSpan().name(name)
    tags.forEach { (key, value) -> span.tag(key, value) }
    span.start()
    val scope = withSpan(span)
    val tagArray = if (config != null) tags.toTagArray() else emptyArray()
    val sample = if (config?.timed == true) Timer.start(config.registry) else null

    return try {
        val result = block(span)
        sample?.stop(
            Timer.builder(name)
                .serviceLevelObjectives(*config!!.slos)
                .tags(*tagArray)
                .tag(TagNames.EXCEPTION, "None")
                .register(config.registry)
        )
        result
    } catch (e: Exception) {
        span.error(e)
        sample?.stop(
            Timer.builder(name)
                .serviceLevelObjectives(*config!!.slos)
                .tags(*tagArray)
                .tag(TagNames.EXCEPTION, e.javaClass.simpleName)
                .register(config.registry)
        )
        throw e
    } finally {
        if (config?.counted == true) {
            Counter.builder("$name.count")
                .tags(*tagArray)
                .register(config.registry)
                .increment()
        }
        scope.close()
        span.end()
    }
}

/**
 * Suspend variant of [traced]. Wraps a suspending [block] in a new [Span].
 *
 * Usage is the same as [traced]: the function creates a span, runs the block, and ends the span.
 * The [Span] is passed to the block so you can add tags or events mid-execution:
 *
 * ```
 * val user = tracer.tracedSuspend("db.query", TagNames.TABLE to "users") { span ->
 *     val result = userRepository.findByIdSuspend(id)
 *     span.event("query complete")
 *     result
 * }
 * ```
 *
 * ### Why this function does not use ThreadLocal scope
 *
 * [traced] puts the span into a [ThreadLocal] so that nested `tracer.nextSpan()` calls
 * automatically pick it up as the parent. That works because [traced] is synchronous and
 * runs on a single thread.
 *
 * In a suspend context (WebFlux + Netty, Kotlin coroutines), the block can switch threads at
 * every suspension point. A ThreadLocal set before the first suspension would be silently lost
 * on the new thread. To avoid that, this function skips the ThreadLocal entirely and passes
 * the span explicitly instead.
 *
 * If you need to create child spans inside the block, pass the parent explicitly:
 *
 * ```
 * tracer.tracedSuspend("parent.op") { parentSpan ->
 *     val child = tracer.nextSpan(parentSpan).name("child.op").start()
 *     try { doWork() } finally { child.end() }
 * }
 * ```
 *
 * Note: this only matters when you create **new spans inside the block**. For most use cases
 * you just run your code, and the received [Span] is all you need.
 *
 * Optionally, pass a [MeterRegistry] and a [metrics] configuration block to also record
 * metrics. See [traced] for a full example.
 *
 * @param R the return type of [block].
 * @param name the span name. Should be low-cardinality.
 * @param tags key-value pairs attached to the span.
 * @param registry optional [MeterRegistry] for recording metrics alongside the span. When null,
 *   the [metrics] block is ignored.
 * @param metrics configures which metrics to record. Defaults to [MetricsConfig.timed]. Only
 *   applied when [registry] is non-null.
 * @param block the suspending operation to trace. Receives the active [Span].
 * @return the result of [block].
 * @throws Exception any exception thrown by [block], after recording it on the span.
 */
@Throws(Exception::class)
suspend inline fun <R> Tracer.tracedSuspend(
    name: String,
    vararg tags: Pair<String, String>,
    registry: MeterRegistry? = null,
    noinline metrics: (MetricsConfig.() -> Unit) = { timed() },
    crossinline block: suspend (Span) -> R,
): R {
    val config = registry?.let { MetricsConfig(it).apply(metrics) }
    val span = nextSpan().name(name)
    tags.forEach { (key, value) -> span.tag(key, value) }
    span.start()
    val tagArray = if (config != null) tags.toTagArray() else emptyArray()
    val sample = if (config?.timed == true) Timer.start(config.registry) else null

    return try {
        val result = block(span)
        sample?.stop(
            Timer.builder(name)
                .serviceLevelObjectives(*config!!.slos)
                .tags(*tagArray)
                .tag(TagNames.EXCEPTION, "None")
                .register(config.registry)
        )
        result
    } catch (e: Exception) {
        span.error(e)
        sample?.stop(
            Timer.builder(name)
                .serviceLevelObjectives(*config!!.slos)
                .tags(*tagArray)
                .tag(TagNames.EXCEPTION, e.javaClass.simpleName)
                .register(config.registry)
        )
        throw e
    } finally {
        if (config?.counted == true) {
            Counter.builder("$name.count")
                .tags(*tagArray)
                .register(config.registry)
                .increment()
        }
        span.end()
    }
}

/**
 * Wraps a synchronous service-to-service request in a new [Span] named
 * `service.request.internal` or `service.request.external` depending on [type].
 *
 * Produces the span tags: [TagNames.SOURCE], [TagNames.TARGET], and optionally [TagNames.URI]. This mirrors the tag schema
 * used by [countRequest] and [timedRequest], making it straightforward to correlate traces with
 * metrics in dashboards.
 *
 * The span is scoped via [ThreadLocal][Tracer.withSpan], so any child spans created inside
 * the [block] are automatically parented. This is safe because the [block] is synchronous.
 * For the suspend variant, see [tracedRequestSuspend].
 *
 * ```
 * val response = tracer.tracedRequest(
 *     source = "order-service",
 *     target = "payment-api",
 *     type = RequestType.EXTERNAL,
 * ) { span ->
 *     val result = paymentClient.charge(order)
 *     span.event("payment charged")
 *     result
 * }
 * ```
 *
 * ### Wrapping WebClient calls
 *
 * When wrapping a `WebClient` call (or `RestTemplate`), this function creates a **parent span**,
 * and the HTTP client's automatic instrumentation (provided by the tracing bridge) creates a
 * **child span** underneath it. This produces **nested spans**:
 *
 * ```
 * service.request.external (your tracedRequest span, with business tags)
 * └── HTTP POST payment-api (WebClient's automatic span, with HTTP details)
 * ```
 *
 * The parent span captures business-level context (source/target/uri tags, correlated metrics),
 * while the child span captures technical HTTP details (method, status, headers).
 *
 * Optionally, pass a [MeterRegistry] and a [metrics] configuration block to also record
 * metrics for the same request without nesting:
 *
 * ```
 * val response = tracer.tracedRequest(
 *     source = "order-service",
 *     target = "payment-api",
 *     type = RequestType.EXTERNAL,
 *     registry = registry,
 * ) { span ->
 *     paymentClient.charge(order)
 * }
 * ```
 *
 * The default [metrics] configuration records both a timer (equivalent to [timedRequest]) and
 * a counter (equivalent to [countRequest]). Pass a custom [metrics] block to change this.
 *
 * @param R the return type of [block].
 * @param source the calling service name.
 * @param target the destination service or API name.
 * @param type whether the request is [internal][RequestType.INTERNAL] or [external][RequestType.EXTERNAL].
 * @param uri optional request path. Use bounded values (route templates) to avoid cardinality issues.
 * @param registry optional [MeterRegistry] for recording metrics alongside the span. When null,
 *   the [metrics] block is ignored.
 * @param metrics configures which metrics to record. Defaults to [MetricsConfig.timed] +
 *   [MetricsConfig.counted]. Only applied when [registry] is non-null.
 * @param block the operation to trace. Receives the active [Span].
 * @return the result of [block].
 * @throws Exception any exception thrown by [block], after recording it on the span.
 */
@Throws(Exception::class)
inline fun <R> Tracer.tracedRequest(
    source: String,
    target: String,
    type: RequestType,
    uri: String? = null,
    registry: MeterRegistry? = null,
    noinline metrics: (MetricsConfig.() -> Unit) = { timed().counted() },
    block: (Span) -> R,
): R {
    val config = registry?.let { MetricsConfig(it).apply(metrics) }
    val span = requestSpan(source, target, type, uri)
    span.start()
    val scope = withSpan(span)
    val sample = if (config?.timed == true) Timer.start(config.registry) else null

    return try {
        val result = block(span)
        sample?.stop(
            requestTimerBuilder("service.request.${type.value}", source, target, uri, config!!.slos)
                .tag(TagNames.EXCEPTION, "None")
                .register(config.registry)
        )
        result
    } catch (e: Exception) {
        span.error(e)
        sample?.stop(
            requestTimerBuilder("service.request.${type.value}", source, target, uri, config!!.slos)
                .tag(TagNames.EXCEPTION, e.javaClass.simpleName)
                .register(config.registry)
        )
        throw e
    } finally {
        if (config?.counted == true) {
            config.registry.countRequest(source, target, type, uri)
        }
        scope.close()
        span.end()
    }
}

/**
 * Suspend variant of [tracedRequest]. Wraps a suspending service-to-service request in a new [Span].
 *
 * Does **not** set a ThreadLocal scope. The [Span] is passed explicitly to the [block].
 *
 * When wrapping a `WebClient` call, this creates a parent span with business-level tags, and
 * WebClient's automatic instrumentation creates a child span with HTTP details. See [tracedRequest]
 * for the full explanation of nested span behavior.
 *
 * @see tracedRequest for full documentation.
 * @see tracedSuspend for why ThreadLocal scope is not used and how to handle child spans.
 *
 * @param R the return type of [block].
 * @param source the calling service name.
 * @param target the destination service or API name.
 * @param type whether the request is [internal][RequestType.INTERNAL] or [external][RequestType.EXTERNAL].
 * @param uri optional request path.
 * @param registry optional [MeterRegistry] for recording metrics alongside the span. When null,
 *   the [metrics] block is ignored.
 * @param metrics configures which metrics to record. Defaults to [MetricsConfig.timed] +
 *   [MetricsConfig.counted]. Only applied when [registry] is non-null.
 * @param block the suspending operation to trace. Receives the active [Span].
 * @return the result of [block].
 * @throws Exception any exception thrown by [block], after recording it on the span.
 */
@Throws(Exception::class)
suspend inline fun <R> Tracer.tracedRequestSuspend(
    source: String,
    target: String,
    type: RequestType,
    uri: String? = null,
    registry: MeterRegistry? = null,
    noinline metrics: (MetricsConfig.() -> Unit) = { timed().counted() },
    crossinline block: suspend (Span) -> R,
): R {
    val config = registry?.let { MetricsConfig(it).apply(metrics) }
    val span = requestSpan(source, target, type, uri)
    span.start()
    val sample = if (config?.timed == true) Timer.start(config.registry) else null

    return try {
        val result = block(span)
        sample?.stop(
            requestTimerBuilder("service.request.${type.value}", source, target, uri, config!!.slos)
                .tag(TagNames.EXCEPTION, "None")
                .register(config.registry)
        )
        result
    } catch (e: Exception) {
        span.error(e)
        sample?.stop(
            requestTimerBuilder("service.request.${type.value}", source, target, uri, config!!.slos)
                .tag(TagNames.EXCEPTION, e.javaClass.simpleName)
                .register(config.registry)
        )
        throw e
    } finally {
        if (config?.counted == true) {
            config.registry.countRequest(source, target, type, uri)
        }
        span.end()
    }
}

/**
 * Tags a [Span] with a sanitized value, replacing null or blank input with `"None"`.
 *
 * Equivalent to `span.tag(key, tagValue(value))`. Prevents empty or invisible tag values
 * in the trace backend.
 *
 * @param key the tag key.
 * @param value the raw value, or null.
 * @return this [Span] for chaining.
 * @see tagValue
 */
fun Span.tagSafe(key: String, value: String?): Span = tag(key, tagValue(value))

/**
 * Tags a [Span] with a cardinality-bounded value.
 *
 * Any [value] not in the [allowed] set is collapsed into [fallback], guaranteeing a fixed upper
 * bound on the number of distinct tag values in the trace backend. This prevents the same
 * cardinality explosion that [boundedTag] prevents for metrics.
 *
 * ```
 * span.tagBounded("method", request.method, setOf("GET", "POST", "PUT", "DELETE"))
 * ```
 *
 * @param key the tag key.
 * @param value the raw input.
 * @param allowed the set of permitted values.
 * @param fallback the value to use when [value] is not in [allowed]. Defaults to `"other"`.
 * @return this [Span] for chaining.
 * @see boundedTag
 */
fun Span.tagBounded(
    key: String,
    value: String?,
    allowed: Set<String>,
    fallback: String = "other",
): Span = tag(key, boundedTag(value, allowed, fallback))

/**
 * Creates a [Span] with the standard request tags (`source`, `target`, optionally `uri`).
 *
 * Shared by [tracedRequest] and [tracedRequestSuspend] to avoid duplicating the span setup.
 */
@PublishedApi
internal fun Tracer.requestSpan(
    source: String,
    target: String,
    type: RequestType,
    uri: String?,
): Span = nextSpan()
    .name("service.request.${type.value}")
    .tag(TagNames.SOURCE, source)
    .tag(TagNames.TARGET, target)
    .apply { if (uri != null) tag(TagNames.URI, uri) }
