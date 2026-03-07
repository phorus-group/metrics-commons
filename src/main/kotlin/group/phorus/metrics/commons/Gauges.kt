/**
 * Gauge extension functions for [MeterRegistry][io.micrometer.core.instrument.MeterRegistry].
 *
 * Gauges represent a point-in-time value that can go up or down (e.g. active connections, cache
 * size, queue depth). Unlike counters and timers, gauges are typically registered once during
 * initialization and then sampled periodically by the registry.
 *
 * All functions in this file return the tracked object so they can be used inline during
 * field initialization:
 *
 * ```
 * private val sessions = registry.trackMapSize("app.sessions", ConcurrentHashMap())
 * ```
 *
 * **Important:** Micrometer holds a [WeakReference][java.lang.ref.WeakReference] to the state
 * object. The caller must keep a strong reference for the gauge to remain active. In practice
 * this means assigning the return value to a field, not a local variable.
 */
package group.phorus.metrics.commons

import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry

/**
 * Registers a [Gauge] that reads from a [Number] state object.
 *
 * The gauge captures the [stateObject] reference and calls [Number.toDouble] on each scrape.
 * This works well with [AtomicInteger][java.util.concurrent.atomic.AtomicInteger],
 * [AtomicLong][java.util.concurrent.atomic.AtomicLong], or any mutable [Number] subtype.
 *
 * ```
 * val activeWorkers = AtomicInteger(0)
 * registry.trackGauge("app.workers.active", activeWorkers)
 * ```
 *
 * @param T a [Number] subtype.
 * @param name the metric name.
 * @param stateObject the object whose [Number.toDouble] value is read on each scrape.
 * @param tags key-value pairs attached to the metric.
 * @return [stateObject], for fluent field initialization.
 */
fun <T : Number> MeterRegistry.trackGauge(
    name: String,
    stateObject: T,
    vararg tags: Pair<String, String>,
): T {
    Gauge.builder(name) { stateObject.toDouble() }
        .tags(*tags.toTagArray())
        .register(this)
    return stateObject
}

/**
 * Registers a [Gauge] backed by an arbitrary state object and a custom [valueFunction].
 *
 * Use this when the value to expose isn't directly a [Number], for example tracking the
 * hit ratio of a cache or the number of entries in a custom data structure.
 *
 * ```
 * val cache = CacheBuilder.newBuilder().build<String, User>()
 * registry.trackGauge("app.cache.size", cache) { it.size().toDouble() }
 * ```
 *
 * @param T the state object type.
 * @param name the metric name.
 * @param stateObject the object passed to [valueFunction] on each scrape.
 * @param tags key-value pairs attached to the metric.
 * @param valueFunction extracts a [Double] from [stateObject].
 * @return [stateObject], for fluent field initialization.
 */
fun <T> MeterRegistry.trackGauge(
    name: String,
    stateObject: T,
    vararg tags: Pair<String, String>,
    valueFunction: (T) -> Double,
): T {
    Gauge.builder(name, stateObject) { valueFunction(it) }
        .tags(*tags.toTagArray())
        .register(this)
    return stateObject
}

/**
 * Registers a [Gauge] that tracks the [size][Collection.size] of a [Collection].
 *
 * Shortcut for `trackGauge(name, collection) { it.size.toDouble() }`. Use a thread-safe
 * collection (e.g. [CopyOnWriteArrayList][java.util.concurrent.CopyOnWriteArrayList]) if the
 * collection is modified concurrently.
 *
 * ```
 * private val pendingTasks = registry.trackCollectionSize(
 *     "app.tasks.pending",
 *     CopyOnWriteArrayList(),
 *     TagNames.QUEUE to "default",
 * )
 * ```
 *
 * @param T a [Collection] subtype.
 * @param name the metric name.
 * @param collection the collection to track.
 * @param tags key-value pairs attached to the metric.
 * @return [collection], for fluent field initialization.
 */
fun <T : Collection<*>> MeterRegistry.trackCollectionSize(
    name: String,
    collection: T,
    vararg tags: Pair<String, String>,
): T {
    Gauge.builder(name, collection) { it.size.toDouble() }
        .tags(*tags.toTagArray())
        .register(this)
    return collection
}

/**
 * Registers a [Gauge] that tracks the [size][Map.size] of a [Map].
 *
 * Shortcut for `trackGauge(name, map) { it.size.toDouble() }`. Use a thread-safe map
 * (e.g. [ConcurrentHashMap][java.util.concurrent.ConcurrentHashMap]) if the map is modified
 * concurrently.
 *
 * ```
 * private val activeSessions = registry.trackMapSize(
 *     "app.sessions.active",
 *     ConcurrentHashMap<String, Session>(),
 * )
 * ```
 *
 * @param T a [Map] subtype.
 * @param name the metric name.
 * @param map the map to track.
 * @param tags key-value pairs attached to the metric.
 * @return [map], for fluent field initialization.
 */
fun <T : Map<*, *>> MeterRegistry.trackMapSize(
    name: String,
    map: T,
    vararg tags: Pair<String, String>,
): T {
    Gauge.builder(name, map) { it.size.toDouble() }
        .tags(*tags.toTagArray())
        .register(this)
    return map
}
