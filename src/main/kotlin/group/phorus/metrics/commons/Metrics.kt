package group.phorus.metrics.commons

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import java.time.Duration
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalUnit

// TODO: Add traceability (OpenTelemetry & Grafana Tempo)

sealed class DurationType(val unit: TemporalUnit)
class Millis() : DurationType(ChronoUnit.MILLIS)
class Seconds() : DurationType(ChronoUnit.SECONDS)
class Minutes() : DurationType(ChronoUnit.MINUTES)
class Hours() : DurationType(ChronoUnit.HOURS)

private inline fun <reified T: DurationType> toDurationsOf(vararg values: Long) = values.map {
    Duration.of(it, T::class.java.getConstructor().newInstance().unit)
}.toTypedArray()

enum class RequestType(val value: String) {
    INTERNAL("internal"),
    EXTERNAL("internal"),
}

private var defaultDurationSLOs = arrayOf(
    *toDurationsOf<Minutes>(1, 2, 3, 4, 5, 10, 15, 30, 45),
    *toDurationsOf<Hours>(1, 2, 3, 5, 8, 12, 18, 24, 48),
)

@PublishedApi
internal var defaultSLOs = arrayOf(
    *toDurationsOf<Millis>(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 15, 30, 45, 60),
    *toDurationsOf<Seconds>(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 15, 30, 45, 60),
)

fun MeterRegistry.logRequest(
    source: String,
    target: String,
    uri: String?,
    increment: Int = 1,
) = Counter.builder("service.request")
    .tag("source", source)
    .tag("target", target)
    .apply {
        if (uri != null) tag("URI", uri)
    }
    .register(this)
    .increment(increment.toDouble())

fun MeterRegistry.logRequestRetry(
    source: String,
    target: String,
    uri: String?,
    attempt: Int = 1,
) = Counter.builder("service.request.retry")
    .tag("source", source)
    .tag("target", target)
    .apply {
        if (uri != null) tag("URI", uri)
    }
    .tag("attempt", attempt.toString())
    .register(this)
    .increment()

fun MeterRegistry.logResponseStatus(
    source: String,
    target: String,
    uri: String?,
    status: Int,
    increment: Int = 1,
) = Counter.builder("service.response.status")
    .tag("source", source)
    .tag("target", target)
    .apply {
        if (uri != null) tag("URI", uri)
    }
    .tag("statusCode", status.toString())
    .register(this)
    .increment(increment.toDouble())

fun MeterRegistry.logDuration(
    source: String,
    target: String,
    uri: String?,
    status: Int,
    duration: Duration,
) = Timer.builder("service.duration")
    .serviceLevelObjectives(*defaultDurationSLOs)
    .tag("source", source)
    .tag("target", target)
    .apply {
        if (uri != null) tag("URI", uri)
    }
    .tag("statusCode", status.toString())
    .register(this)
    .record(duration)

@JvmOverloads
fun tagValue(value: String? = null) = value.orEmpty().trim().ifBlank { "None" }

@Throws(Exception::class)
inline fun <R> MeterRegistry.recordWithExceptionTag(name: String, callback: (Timer.Builder) -> R): R {
    val sample = Timer.start(this)
    val timerBuilder = Timer.builder(name).serviceLevelObjectives(*defaultSLOs)

    return runCatching {
        callback(timerBuilder).also {
            sample.stop(timerBuilder.tag("exception", tagValue()).register(this))
        }
    }.getOrElse {
        sample.stop(timerBuilder.tag("exception", it.javaClass.name).register(this))
        throw it
    }
}

inline fun <R> MeterRegistry.recordRequest(
    source: String,
    target: String,
    type: RequestType,
    uri: String?,
    callback: (Timer.Builder) -> R,
) = this.recordWithExceptionTag("service.request.${type.value}") { builder ->
    builder.tag("target", target)
    builder.tag("source", source)
    .apply {
        if (uri != null) tag("URI", uri)
    }
    callback(builder)
}
