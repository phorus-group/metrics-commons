package group.phorus.metrics.commons

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.micrometer.tracing.test.simple.SimpleTracer
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TracingTest {

    private lateinit var tracer: SimpleTracer
    private lateinit var registry: SimpleMeterRegistry

    @BeforeTest
    fun setUp() {
        tracer = SimpleTracer()
        registry = SimpleMeterRegistry()
    }

    // ========== TRACED ==========

    @Test
    fun `traced creates span with name`() {
        val result = tracer.traced("app.operation") { 42 }

        assertEquals(42, result)
        val spans = tracer.spans
        assertEquals(1, spans.size)
        assertEquals("app.operation", spans.first().name)
    }

    @Test
    fun `traced applies tags`() {
        tracer.traced("app.tagged", "env" to "prod", "region" to "eu") { }

        val span = tracer.spans.first()
        assertEquals("prod", span.tags["env"])
        assertEquals("eu", span.tags["region"])
    }

    @Test
    fun `traced records exception on failure`() {
        assertFailsWith<IllegalStateException> {
            tracer.traced("app.failing") {
                throw IllegalStateException("boom")
            }
        }

        val span = tracer.spans.first()
        assertEquals("app.failing", span.name)
        assertNotNull(span.error)
        assertTrue(span.error is IllegalStateException)
    }

    @Test
    fun `traced re-throws original exception`() {
        val exception = assertFailsWith<RuntimeException> {
            tracer.traced("app.rethrow") {
                throw RuntimeException("original")
            }
        }

        assertEquals("original", exception.message)
    }

    @Test
    fun `traced with no tags`() {
        tracer.traced("app.no-tags") { "result" }

        val span = tracer.spans.first()
        assertEquals("app.no-tags", span.name)
    }

    @Test
    fun `traced passes span to block for mid-execution events`() {
        tracer.traced("app.events") { span ->
            span.event("step1")
            span.tag("dynamic", "value")
            span.event("step2")
        }

        val span = tracer.spans.first()
        assertEquals("app.events", span.name)
        assertEquals("value", span.tags["dynamic"])
    }

    @Test
    fun `traced creates parent-child relationship`() {
        tracer.traced("parent") {
            tracer.traced("child") { }
        }

        val spans = tracer.spans
        assertEquals(2, spans.size)
    }

    // ========== TRACED SUSPEND ==========

    @Test
    fun `tracedSuspend creates span with name`() = runTest {
        val result = tracer.tracedSuspend("app.async") { 99 }

        assertEquals(99, result)
        val spans = tracer.spans
        assertEquals(1, spans.size)
        assertEquals("app.async", spans.first().name)
    }

    @Test
    fun `tracedSuspend applies tags`() = runTest {
        tracer.tracedSuspend("app.async.tagged", "key" to "value") { }

        val span = tracer.spans.first()
        assertEquals("value", span.tags["key"])
    }

    @Test
    fun `tracedSuspend records exception on failure`() = runTest {
        assertFailsWith<IllegalArgumentException> {
            tracer.tracedSuspend("app.async.fail") {
                throw IllegalArgumentException("bad input")
            }
        }

        val span = tracer.spans.first()
        assertNotNull(span.error)
        assertTrue(span.error is IllegalArgumentException)
    }

    @Test
    fun `tracedSuspend works with actual suspend calls`() = runTest {
        val result = tracer.tracedSuspend("app.async.delay") {
            delay(10)
            "after-delay"
        }

        assertEquals("after-delay", result)
        assertEquals(1, tracer.spans.size)
        assertEquals("app.async.delay", tracer.spans.first().name)
    }

    // ========== TRACED REQUEST ==========

    @Test
    fun `tracedRequest creates span for internal request`() {
        tracer.tracedRequest(
            source = "order-service",
            target = "user-service",
            type = RequestType.INTERNAL,
        ) { }

        val span = tracer.spans.first()
        assertEquals("service.request.internal", span.name)
        assertEquals("order-service", span.tags["source"])
        assertEquals("user-service", span.tags["target"])
    }

    @Test
    fun `tracedRequest creates span for external request`() {
        tracer.tracedRequest(
            source = "order-service",
            target = "stripe-api",
            type = RequestType.EXTERNAL,
        ) { }

        val span = tracer.spans.first()
        assertEquals("service.request.external", span.name)
        assertEquals("order-service", span.tags["source"])
        assertEquals("stripe-api", span.tags["target"])
    }

    @Test
    fun `tracedRequest includes uri when provided`() {
        tracer.tracedRequest(
            source = "gateway",
            target = "user-service",
            type = RequestType.INTERNAL,
            uri = "/api/users",
        ) { }

        val span = tracer.spans.first()
        assertEquals("/api/users", span.tags["uri"])
    }

    @Test
    fun `tracedRequest omits uri when null`() {
        tracer.tracedRequest(
            source = "gateway",
            target = "user-service",
            type = RequestType.INTERNAL,
        ) { }

        val span = tracer.spans.first()
        assertNull(span.tags["uri"])
    }

    @Test
    fun `tracedRequest records exception on failure`() {
        assertFailsWith<RuntimeException> {
            tracer.tracedRequest(
                source = "order-service",
                target = "payment-api",
                type = RequestType.EXTERNAL,
            ) {
                throw RuntimeException("connection refused")
            }
        }

        val span = tracer.spans.first()
        assertNotNull(span.error)
        assertEquals("service.request.external", span.name)
    }

    // ========== TRACED REQUEST SUSPEND ==========

    @Test
    fun `tracedRequestSuspend creates span`() = runTest {
        val result = tracer.tracedRequestSuspend(
            source = "order-service",
            target = "inventory-service",
            type = RequestType.INTERNAL,
        ) { "ok" }

        assertEquals("ok", result)
        val span = tracer.spans.first()
        assertEquals("service.request.internal", span.name)
        assertEquals("order-service", span.tags["source"])
        assertEquals("inventory-service", span.tags["target"])
    }

    @Test
    fun `tracedRequestSuspend records exception`() = runTest {
        assertFailsWith<IllegalStateException> {
            tracer.tracedRequestSuspend(
                source = "order-service",
                target = "payment-api",
                type = RequestType.EXTERNAL,
            ) {
                throw IllegalStateException("timeout")
            }
        }

        val span = tracer.spans.first()
        assertNotNull(span.error)
    }

    @Test
    fun `tracedRequestSuspend works with suspend calls`() = runTest {
        tracer.tracedRequestSuspend(
            source = "order-service",
            target = "email-service",
            type = RequestType.INTERNAL,
            uri = "/api/send",
        ) {
            delay(10)
        }

        val span = tracer.spans.first()
        assertEquals("service.request.internal", span.name)
        assertEquals("/api/send", span.tags["uri"])
    }

    // ========== TAG SAFE / TAG BOUNDED ==========

    @Test
    fun `tagSafe sanitizes null to None`() {
        tracer.traced("app.safe") { span ->
            span.tagSafe("user", null)
        }

        val span = tracer.spans.first()
        assertEquals("None", span.tags["user"])
    }

    @Test
    fun `tagSafe sanitizes blank to None`() {
        tracer.traced("app.safe") { span ->
            span.tagSafe("user", "   ")
        }

        val span = tracer.spans.first()
        assertEquals("None", span.tags["user"])
    }

    @Test
    fun `tagSafe passes through valid values`() {
        tracer.traced("app.safe") { span ->
            span.tagSafe("user", "alice")
        }

        val span = tracer.spans.first()
        assertEquals("alice", span.tags["user"])
    }

    @Test
    fun `tagBounded returns allowed value`() {
        tracer.traced("app.bounded") { span ->
            span.tagBounded("method", "GET", setOf("GET", "POST", "PUT", "DELETE"))
        }

        val span = tracer.spans.first()
        assertEquals("GET", span.tags["method"])
    }

    @Test
    fun `tagBounded returns fallback for unknown value`() {
        tracer.traced("app.bounded") { span ->
            span.tagBounded("method", "PATCH", setOf("GET", "POST", "PUT", "DELETE"))
        }

        val span = tracer.spans.first()
        assertEquals("other", span.tags["method"])
    }

    @Test
    fun `tagBounded returns custom fallback`() {
        tracer.traced("app.bounded") { span ->
            span.tagBounded("method", "TRACE", setOf("GET", "POST"), fallback = "unsupported")
        }

        val span = tracer.spans.first()
        assertEquals("unsupported", span.tags["method"])
    }

    @Test
    fun `tagBounded returns fallback for null`() {
        tracer.traced("app.bounded") { span ->
            span.tagBounded("method", null, setOf("GET", "POST"))
        }

        val span = tracer.spans.first()
        assertEquals("other", span.tags["method"])
    }

    // ========== TRACED WITH METRICS ==========

    @Test
    fun `traced with registry records timer by default`() {
        val result = tracer.traced("db.query", "table" to "users", registry = registry) { 42 }

        assertEquals(42, result)

        val timer = registry.find("db.query")
            .tag("table", "users")
            .tag("exception", "None")
            .timer()
        assertNotNull(timer)
        assertEquals(1, timer.count())

        assertEquals(1, tracer.spans.size)
        assertEquals("db.query", tracer.spans.first().name)
    }

    @Test
    fun `traced with registry records exception tag on timer failure`() {
        assertFailsWith<IllegalStateException> {
            tracer.traced("db.query", "table" to "users", registry = registry) {
                throw IllegalStateException("boom")
            }
        }

        val timer = registry.find("db.query")
            .tag("table", "users")
            .tag("exception", "IllegalStateException")
            .timer()
        assertNotNull(timer)
        assertEquals(1, timer.count())
    }

    @Test
    fun `traced with registry and counted records counter`() {
        tracer.traced("db.query", "table" to "users",
            registry = registry,
            metrics = { timed().counted() },
        ) { "ok" }

        val timer = registry.find("db.query").tag("exception", "None").timer()
        assertNotNull(timer)

        val counter = registry.find("db.query.count").tag("table", "users").counter()
        assertNotNull(counter)
        assertEquals(1.0, counter.count())
    }

    @Test
    fun `traced with registry and only counted skips timer`() {
        tracer.traced("db.query",
            registry = registry,
            metrics = { counted() },
        ) { "ok" }

        val timer = registry.find("db.query").timer()
        assertNull(timer)

        val counter = registry.find("db.query.count").counter()
        assertNotNull(counter)
        assertEquals(1.0, counter.count())
    }

    @Test
    fun `traced without registry records no metrics`() {
        tracer.traced("db.query", "table" to "users") { "ok" }

        val timer = registry.find("db.query").timer()
        assertNull(timer)
    }

    @Test
    fun `traced with empty metrics config records nothing`() {
        tracer.traced("db.query",
            registry = registry,
            metrics = {},
        ) { "ok" }

        val timer = registry.find("db.query").timer()
        assertNull(timer)

        assertEquals(1, tracer.spans.size)
    }

    @Test
    fun `traced counter records on exception too`() {
        assertFailsWith<RuntimeException> {
            tracer.traced("db.query",
                registry = registry,
                metrics = { timed().counted() },
            ) {
                throw RuntimeException("fail")
            }
        }

        val counter = registry.find("db.query.count").counter()
        assertNotNull(counter)
        assertEquals(1.0, counter.count())
    }

    // ========== TRACED SUSPEND WITH METRICS ==========

    @Test
    fun `tracedSuspend with registry records timer`() = runTest {
        val result = tracer.tracedSuspend("db.query", "table" to "users", registry = registry) { 99 }

        assertEquals(99, result)

        val timer = registry.find("db.query")
            .tag("table", "users")
            .tag("exception", "None")
            .timer()
        assertNotNull(timer)
        assertEquals(1, timer.count())
    }

    @Test
    fun `tracedSuspend with registry records exception tag on failure`() = runTest {
        assertFailsWith<IllegalArgumentException> {
            tracer.tracedSuspend("db.query", registry = registry) {
                throw IllegalArgumentException("bad")
            }
        }

        val timer = registry.find("db.query")
            .tag("exception", "IllegalArgumentException")
            .timer()
        assertNotNull(timer)
    }

    // ========== TRACED REQUEST WITH METRICS ==========

    @Test
    fun `tracedRequest with registry records timer and counter by default`() {
        val result = tracer.tracedRequest(
            source = "order-service",
            target = "payment-api",
            type = RequestType.EXTERNAL,
            registry = registry,
        ) { "charged" }

        assertEquals("charged", result)

        val timer = registry.find("service.request.external")
            .tag("source", "order-service")
            .tag("target", "payment-api")
            .tag("exception", "None")
            .timer()
        assertNotNull(timer)
        assertEquals(1, timer.count())

        val counter = registry.find("service.request")
            .tag("source", "order-service")
            .tag("target", "payment-api")
            .tag("type", "external")
            .counter()
        assertNotNull(counter)
        assertEquals(1.0, counter.count())

        assertEquals(1, tracer.spans.size)
        assertEquals("service.request.external", tracer.spans.first().name)
    }

    @Test
    fun `tracedRequest with registry records exception tag on timer failure`() {
        assertFailsWith<RuntimeException> {
            tracer.tracedRequest(
                source = "order-service",
                target = "payment-api",
                type = RequestType.EXTERNAL,
                registry = registry,
            ) {
                throw RuntimeException("timeout")
            }
        }

        val timer = registry.find("service.request.external")
            .tag("exception", "RuntimeException")
            .timer()
        assertNotNull(timer)

        val counter = registry.find("service.request")
            .tag("type", "external")
            .counter()
        assertNotNull(counter)
        assertEquals(1.0, counter.count())
    }

    @Test
    fun `tracedRequest with registry and only timed skips counter`() {
        tracer.tracedRequest(
            source = "order-service",
            target = "payment-api",
            type = RequestType.EXTERNAL,
            registry = registry,
            metrics = { timed() },
        ) { "ok" }

        val timer = registry.find("service.request.external")
            .tag("exception", "None")
            .timer()
        assertNotNull(timer)

        val counter = registry.find("service.request").counter()
        assertNull(counter)
    }

    @Test
    fun `tracedRequest with registry includes uri in timer and counter`() {
        tracer.tracedRequest(
            source = "gateway",
            target = "user-service",
            type = RequestType.INTERNAL,
            uri = "/api/users",
            registry = registry,
        ) { "ok" }

        val timer = registry.find("service.request.internal")
            .tag("uri", "/api/users")
            .tag("exception", "None")
            .timer()
        assertNotNull(timer)

        val counter = registry.find("service.request")
            .tag("uri", "/api/users")
            .counter()
        assertNotNull(counter)
    }

    @Test
    fun `tracedRequest without registry records no metrics`() {
        tracer.tracedRequest(
            source = "order-service",
            target = "payment-api",
            type = RequestType.EXTERNAL,
        ) { "ok" }

        val timer = registry.find("service.request.external").timer()
        assertNull(timer)

        assertEquals(1, tracer.spans.size)
    }

    @Test
    fun `tracedRequest with custom slos`() {
        tracer.tracedRequest(
            source = "order-service",
            target = "payment-api",
            type = RequestType.EXTERNAL,
            registry = registry,
            metrics = { timed(slos = SloPresets.BACKGROUND_TASK) },
        ) { "ok" }

        val timer = registry.find("service.request.external")
            .tag("exception", "None")
            .timer()
        assertNotNull(timer)
    }

    // ========== TRACED REQUEST SUSPEND WITH METRICS ==========

    @Test
    fun `tracedRequestSuspend with registry records timer and counter`() = runTest {
        val result = tracer.tracedRequestSuspend(
            source = "order-service",
            target = "inventory-service",
            type = RequestType.INTERNAL,
            registry = registry,
        ) { "ok" }

        assertEquals("ok", result)

        val timer = registry.find("service.request.internal")
            .tag("source", "order-service")
            .tag("target", "inventory-service")
            .tag("exception", "None")
            .timer()
        assertNotNull(timer)

        val counter = registry.find("service.request")
            .tag("source", "order-service")
            .tag("target", "inventory-service")
            .tag("type", "internal")
            .counter()
        assertNotNull(counter)
    }

    @Test
    fun `tracedRequestSuspend with registry records exception`() = runTest {
        assertFailsWith<IllegalStateException> {
            tracer.tracedRequestSuspend(
                source = "order-service",
                target = "payment-api",
                type = RequestType.EXTERNAL,
                registry = registry,
            ) {
                throw IllegalStateException("timeout")
            }
        }

        val timer = registry.find("service.request.external")
            .tag("exception", "IllegalStateException")
            .timer()
        assertNotNull(timer)

        val counter = registry.find("service.request").counter()
        assertNotNull(counter)
    }
}
