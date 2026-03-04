/**
 * Distribution summary extension functions for [MeterRegistry][io.micrometer.core.instrument.MeterRegistry].
 *
 * Distribution summaries track the distribution of values that are not time-based (for time-based
 * distributions, use the timer functions in `Timers.kt`). Typical use cases include request payload
 * sizes, monetary amounts, queue depths, or any numeric value where you care about the statistical
 * distribution rather than just a running total.
 *
 * Like counters, micrometer reuses the same summary instance when name and tags match, so calling
 * these functions repeatedly is safe.
 */
package group.phorus.metrics.commons

import io.micrometer.core.instrument.DistributionSummary
import io.micrometer.core.instrument.MeterRegistry

/**
 * Records a single [value] into a [DistributionSummary].
 *
 * This is the simplest distribution recording: no percentile histograms or SLO buckets are
 * configured. The registry will still track count, total, and max. Use [recordValueWithPercentiles]
 * or [recordValueWithBuckets] when you need richer histogram data.
 *
 * ```
 * registry.recordValue("app.loan_amount", 15000.0, "region" to "eu")
 * ```
 *
 * @param name the metric name.
 * @param value the observed value.
 * @param tags key-value pairs attached to the metric.
 */
fun MeterRegistry.recordValue(
    name: String,
    value: Double,
    vararg tags: Pair<String, String>,
) {
    DistributionSummary.builder(name)
        .tags(*tags.toTagArray())
        .register(this)
        .record(value)
}

/**
 * Records a [value] into a [DistributionSummary] that publishes client-side percentile approximations.
 *
 * The [percentiles] are computed locally in the application and exported as gauge metrics
 * (e.g. `app.latency.percentile{phi=0.95}`). This is useful for quick local insight but cannot be
 * aggregated across instances. For aggregatable histograms, use [recordValueWithBuckets] instead.
 *
 * The default percentiles are p50, p75, p90, p95, and p99.
 *
 * @param name the metric name.
 * @param value the observed value.
 * @param tags key-value pairs attached to the metric.
 * @param percentiles the percentile values to publish, each in the range `[0, 1]`.
 */
fun MeterRegistry.recordValueWithPercentiles(
    name: String,
    value: Double,
    vararg tags: Pair<String, String>,
    percentiles: DoubleArray = doubleArrayOf(0.5, 0.75, 0.9, 0.95, 0.99),
) {
    DistributionSummary.builder(name)
        .tags(*tags.toTagArray())
        .publishPercentiles(*percentiles)
        .register(this)
        .record(value)
}

/**
 * Records a [value] into a [DistributionSummary] with explicit SLO histogram [buckets].
 *
 * The [buckets] define the upper bounds of the histogram bins exported to the backend (e.g.
 * Prometheus `_bucket` series). Unlike client-side percentiles, these histogram buckets **can** be
 * aggregated across instances, making them the right choice for production dashboards.
 *
 * ```
 * registry.recordValueWithBuckets(
 *     "app.file_size",
 *     payload.length.toDouble(),
 *     "type" to "upload",
 *     buckets = doubleArrayOf(1024.0, 10_240.0, 102_400.0, 1_048_576.0),
 * )
 * ```
 *
 * @param name the metric name.
 * @param value the observed value.
 * @param tags key-value pairs attached to the metric.
 * @param buckets the histogram bucket upper bounds.
 */
fun MeterRegistry.recordValueWithBuckets(
    name: String,
    value: Double,
    vararg tags: Pair<String, String>,
    buckets: DoubleArray,
) {
    DistributionSummary.builder(name)
        .tags(*tags.toTagArray())
        .serviceLevelObjectives(*buckets)
        .register(this)
        .record(value)
}
