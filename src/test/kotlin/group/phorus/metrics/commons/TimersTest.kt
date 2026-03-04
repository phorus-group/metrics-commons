package group.phorus.metrics.commons

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TimersTest {

    private lateinit var registry: SimpleMeterRegistry

    @BeforeEach
    fun setUp() {
        registry = SimpleMeterRegistry()
    }

    // ========== timed ==========

    @Test
    fun `timed records successful execution`() {
        val result = registry.timed("app.operation", "action" to "process") {
            "done"
        }

        assertEquals("done", result)

        val timer = registry.find("app.operation")
            .tag("action", "process")
            .tag("exception", "None")
            .timer()
        assertNotNull(timer)
        assertEquals(1, timer.count())
    }

    @Test
    fun `timed records exception tag on failure`() {
        assertFailsWith<IllegalStateException> {
            registry.timed("app.operation", "action" to "process") {
                throw IllegalStateException("boom")
            }
        }

        val timer = registry.find("app.operation")
            .tag("action", "process")
            .tag("exception", "IllegalStateException")
            .timer()
        assertNotNull(timer)
        assertEquals(1, timer.count())
    }

    @Test
    fun `timed with no tags`() {
        registry.timed("app.simple") { 42 }

        val timer = registry.find("app.simple").tag("exception", "None").timer()
        assertNotNull(timer)
        assertEquals(1, timer.count())
    }

    @Test
    fun `timed with multiple tags`() {
        registry.timed("app.operation", "service" to "auth", "method" to "login") {
            "ok"
        }

        val timer = registry.find("app.operation")
            .tag("service", "auth")
            .tag("method", "login")
            .tag("exception", "None")
            .timer()
        assertNotNull(timer)
        assertEquals(1, timer.count())
    }

    @Test
    fun `timed with custom SLOs`() {
        registry.timed(
            "app.batch",
            "job" to "import",
            slos = SloPresets.BACKGROUND_TASK,
        ) {
            "processed"
        }

        val timer = registry.find("app.batch")
            .tag("job", "import")
            .tag("exception", "None")
            .timer()
        assertNotNull(timer)
        assertEquals(1, timer.count())
    }

    @Test
    fun `timed preserves original exception`() {
        val exception = assertFailsWith<RuntimeException> {
            registry.timed("app.fail") {
                throw RuntimeException("original message")
            }
        }
        assertEquals("original message", exception.message)
    }

    @Test
    fun `timed records duration greater than zero`() {
        registry.timed("app.sleep") {
            Thread.sleep(10)
        }

        val timer = registry.find("app.sleep").tag("exception", "None").timer()
        assertNotNull(timer)
        assertTrue(timer.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS) > 0)
    }

    // ========== timedSuspend ==========

    @Test
    fun `timedSuspend records successful execution`() = runTest {
        val result = registry.timedSuspend("app.async", "action" to "fetch") {
            "fetched"
        }

        assertEquals("fetched", result)

        val timer = registry.find("app.async")
            .tag("action", "fetch")
            .tag("exception", "None")
            .timer()
        assertNotNull(timer)
        assertEquals(1, timer.count())
    }

    @Test
    fun `timedSuspend works with actual suspend calls`() = runTest {
        val result = registry.timedSuspend("app.async.delay") {
            delay(10)
            "after-delay"
        }

        assertEquals("after-delay", result)

        val timer = registry.find("app.async.delay").tag("exception", "None").timer()
        assertNotNull(timer)
        assertEquals(1, timer.count())
    }

    @Test
    fun `timedSuspend records exception tag on failure`() = runTest {
        assertFailsWith<IllegalArgumentException> {
            registry.timedSuspend("app.async", "action" to "fetch") {
                throw IllegalArgumentException("bad input")
            }
        }

        val timer = registry.find("app.async")
            .tag("action", "fetch")
            .tag("exception", "IllegalArgumentException")
            .timer()
        assertNotNull(timer)
        assertEquals(1, timer.count())
    }

    // ========== timedRequest ==========

    @Test
    fun `timedRequest records internal request`() {
        val result = registry.timedRequest(
            source = "user-service",
            target = "auth-service",
            type = RequestType.INTERNAL,
        ) {
            "authenticated"
        }

        assertEquals("authenticated", result)

        val timer = registry.find("service.request.internal")
            .tag("source", "user-service")
            .tag("target", "auth-service")
            .tag("exception", "None")
            .timer()
        assertNotNull(timer)
        assertEquals(1, timer.count())
    }

    @Test
    fun `timedRequest records external request`() {
        registry.timedRequest(
            source = "payment-service",
            target = "stripe",
            type = RequestType.EXTERNAL,
        ) {
            "charged"
        }

        val timer = registry.find("service.request.external")
            .tag("source", "payment-service")
            .tag("target", "stripe")
            .tag("exception", "None")
            .timer()
        assertNotNull(timer)
        assertEquals(1, timer.count())
    }

    @Test
    fun `timedRequest with uri`() {
        registry.timedRequest(
            source = "gateway",
            target = "user-service",
            type = RequestType.INTERNAL,
            uri = "/api/users",
        ) {
            "ok"
        }

        val timer = registry.find("service.request.internal")
            .tag("source", "gateway")
            .tag("target", "user-service")
            .tag("uri", "/api/users")
            .tag("exception", "None")
            .timer()
        assertNotNull(timer)
        assertEquals(1, timer.count())
    }

    @Test
    fun `timedRequest records exception on failure`() {
        assertFailsWith<RuntimeException> {
            registry.timedRequest(
                source = "user-service",
                target = "db",
                type = RequestType.INTERNAL,
            ) {
                throw RuntimeException("connection refused")
            }
        }

        val timer = registry.find("service.request.internal")
            .tag("source", "user-service")
            .tag("target", "db")
            .tag("exception", "RuntimeException")
            .timer()
        assertNotNull(timer)
        assertEquals(1, timer.count())
    }

    // ========== timedRequestSuspend ==========

    @Test
    fun `timedRequestSuspend records request`() = runTest {
        val result = registry.timedRequestSuspend(
            source = "api-gateway",
            target = "order-service",
            type = RequestType.INTERNAL,
        ) {
            "order-123"
        }

        assertEquals("order-123", result)

        val timer = registry.find("service.request.internal")
            .tag("source", "api-gateway")
            .tag("target", "order-service")
            .tag("exception", "None")
            .timer()
        assertNotNull(timer)
        assertEquals(1, timer.count())
    }

    @Test
    fun `timedRequestSuspend records exception`() = runTest {
        assertFailsWith<IllegalStateException> {
            registry.timedRequestSuspend(
                source = "api-gateway",
                target = "order-service",
                type = RequestType.INTERNAL,
            ) {
                throw IllegalStateException("service unavailable")
            }
        }

        val timer = registry.find("service.request.internal")
            .tag("exception", "IllegalStateException")
            .timer()
        assertNotNull(timer)
        assertEquals(1, timer.count())
    }

    // ========== recordDuration ==========

    @Test
    fun `recordDuration records a pre-measured duration`() {
        registry.recordDuration(
            "http.request.duration",
            Duration.ofMillis(150),
            "method" to "GET",
            "path" to "/api/users",
        )

        val timer = registry.find("http.request.duration")
            .tag("method", "GET")
            .tag("path", "/api/users")
            .timer()
        assertNotNull(timer)
        assertEquals(1, timer.count())
        assertEquals(150.0, timer.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS), 1.0)
    }

    @Test
    fun `recordDuration with custom SLOs`() {
        registry.recordDuration(
            "batch.job.duration",
            Duration.ofSeconds(45),
            "job" to "import",
            slos = SloPresets.BACKGROUND_TASK,
        )

        val timer = registry.find("batch.job.duration")
            .tag("job", "import")
            .timer()
        assertNotNull(timer)
        assertEquals(1, timer.count())
    }

    @Test
    fun `recordDuration accumulates multiple recordings`() {
        registry.recordDuration("op.duration", Duration.ofMillis(100), "op" to "read")
        registry.recordDuration("op.duration", Duration.ofMillis(200), "op" to "read")

        val timer = registry.find("op.duration").tag("op", "read").timer()
        assertNotNull(timer)
        assertEquals(2, timer.count())
        assertEquals(300.0, timer.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS), 1.0)
    }
}
