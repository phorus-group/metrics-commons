package group.phorus.metrics.commons

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class DistributionsTest {

    private lateinit var registry: SimpleMeterRegistry

    @BeforeEach
    fun setUp() {
        registry = SimpleMeterRegistry()
    }

    // ========== recordValue ==========

    @Test
    fun `recordValue records a single value`() {
        registry.recordValue("app.loan_amount", 5000.0, "region" to "eu")

        val summary = registry.find("app.loan_amount").tag("region", "eu").summary()
        assertNotNull(summary)
        assertEquals(1, summary.count())
        assertEquals(5000.0, summary.totalAmount())
    }

    @Test
    fun `recordValue accumulates multiple values`() {
        registry.recordValue("app.loan_amount", 5000.0, "region" to "eu")
        registry.recordValue("app.loan_amount", 3000.0, "region" to "eu")
        registry.recordValue("app.loan_amount", 7000.0, "region" to "eu")

        val summary = registry.find("app.loan_amount").tag("region", "eu").summary()
        assertNotNull(summary)
        assertEquals(3, summary.count())
        assertEquals(15000.0, summary.totalAmount())
    }

    @Test
    fun `recordValue with multiple tags`() {
        registry.recordValue("app.order_total", 99.99, "currency" to "EUR", "type" to "subscription")

        val summary = registry.find("app.order_total")
            .tag("currency", "EUR")
            .tag("type", "subscription")
            .summary()
        assertNotNull(summary)
        assertEquals(1, summary.count())
        assertEquals(99.99, summary.totalAmount(), 0.01)
    }

    @Test
    fun `recordValue without tags`() {
        registry.recordValue("app.score", 42.0)

        val summary = registry.find("app.score").summary()
        assertNotNull(summary)
        assertEquals(1, summary.count())
        assertEquals(42.0, summary.totalAmount())
    }

    @Test
    fun `recordValue separates different tag values`() {
        registry.recordValue("app.amount", 100.0, "type" to "credit")
        registry.recordValue("app.amount", 200.0, "type" to "debit")

        val credit = registry.find("app.amount").tag("type", "credit").summary()
        val debit = registry.find("app.amount").tag("type", "debit").summary()
        assertNotNull(credit)
        assertNotNull(debit)
        assertEquals(100.0, credit.totalAmount())
        assertEquals(200.0, debit.totalAmount())
    }

    @Test
    fun `recordValue tracks max`() {
        registry.recordValue("app.latency", 50.0)
        registry.recordValue("app.latency", 150.0)
        registry.recordValue("app.latency", 100.0)

        val summary = registry.find("app.latency").summary()
        assertNotNull(summary)
        assertEquals(150.0, summary.max())
    }

    // ========== recordValueWithPercentiles ==========

    @Test
    fun `recordValueWithPercentiles records value`() {
        registry.recordValueWithPercentiles("app.response_size", 1024.0, "endpoint" to "/api")

        val summary = registry.find("app.response_size").tag("endpoint", "/api").summary()
        assertNotNull(summary)
        assertEquals(1, summary.count())
        assertEquals(1024.0, summary.totalAmount())
    }

    @Test
    fun `recordValueWithPercentiles accumulates`() {
        repeat(100) { i ->
            registry.recordValueWithPercentiles("app.response_size", i.toDouble())
        }

        val summary = registry.find("app.response_size").summary()
        assertNotNull(summary)
        assertEquals(100, summary.count())
    }

    @Test
    fun `recordValueWithPercentiles with custom percentiles`() {
        repeat(100) { i ->
            registry.recordValueWithPercentiles(
                "app.latency",
                i.toDouble(),
                percentiles = doubleArrayOf(0.5, 0.99),
            )
        }

        val summary = registry.find("app.latency").summary()
        assertNotNull(summary)
        assertEquals(100, summary.count())
    }

    // ========== recordValueWithBuckets ==========

    @Test
    fun `recordValueWithBuckets records value`() {
        registry.recordValueWithBuckets(
            "app.file_size",
            512.0,
            "type" to "image",
            buckets = doubleArrayOf(100.0, 500.0, 1000.0, 5000.0),
        )

        val summary = registry.find("app.file_size").tag("type", "image").summary()
        assertNotNull(summary)
        assertEquals(1, summary.count())
        assertEquals(512.0, summary.totalAmount())
    }

    @Test
    fun `recordValueWithBuckets accumulates across recordings`() {
        val buckets = doubleArrayOf(10.0, 50.0, 100.0, 500.0)

        registry.recordValueWithBuckets("app.queue_size", 5.0, buckets = buckets)
        registry.recordValueWithBuckets("app.queue_size", 75.0, buckets = buckets)
        registry.recordValueWithBuckets("app.queue_size", 200.0, buckets = buckets)

        val summary = registry.find("app.queue_size").summary()
        assertNotNull(summary)
        assertEquals(3, summary.count())
        assertEquals(280.0, summary.totalAmount())
    }
}
