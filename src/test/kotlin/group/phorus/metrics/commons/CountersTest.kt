package group.phorus.metrics.commons

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class CountersTest {

    private lateinit var registry: SimpleMeterRegistry

    @BeforeEach
    fun setUp() {
        registry = SimpleMeterRegistry()
    }

    @Test
    fun `count increments counter by 1`() {
        registry.count("app.events", "type" to "login")

        val counter = registry.find("app.events").tag("type", "login").counter()
        assertNotNull(counter)
        assertEquals(1.0, counter.count())
    }

    @Test
    fun `count accumulates multiple calls`() {
        registry.count("app.events", "type" to "login")
        registry.count("app.events", "type" to "login")
        registry.count("app.events", "type" to "login")

        val counter = registry.find("app.events").tag("type", "login").counter()
        assertNotNull(counter)
        assertEquals(3.0, counter.count())
    }

    @Test
    fun `count with multiple tags`() {
        registry.count("app.events", "type" to "login", "region" to "eu")

        val counter = registry.find("app.events")
            .tag("type", "login")
            .tag("region", "eu")
            .counter()
        assertNotNull(counter)
        assertEquals(1.0, counter.count())
    }

    @Test
    fun `count without tags`() {
        registry.count("app.events")

        val counter = registry.find("app.events").counter()
        assertNotNull(counter)
        assertEquals(1.0, counter.count())
    }

    @Test
    fun `count separates different tag values`() {
        registry.count("app.events", "type" to "login")
        registry.count("app.events", "type" to "logout")

        val loginCounter = registry.find("app.events").tag("type", "login").counter()
        val logoutCounter = registry.find("app.events").tag("type", "logout").counter()
        assertNotNull(loginCounter)
        assertNotNull(logoutCounter)
        assertEquals(1.0, loginCounter.count())
        assertEquals(1.0, logoutCounter.count())
    }

    @Test
    fun `countBy increments by custom amount`() {
        registry.countBy("app.bytes", 1024.0, "direction" to "inbound")

        val counter = registry.find("app.bytes").tag("direction", "inbound").counter()
        assertNotNull(counter)
        assertEquals(1024.0, counter.count())
    }

    @Test
    fun `countBy accumulates`() {
        registry.countBy("app.bytes", 512.0, "direction" to "inbound")
        registry.countBy("app.bytes", 256.0, "direction" to "inbound")

        val counter = registry.find("app.bytes").tag("direction", "inbound").counter()
        assertNotNull(counter)
        assertEquals(768.0, counter.count())
    }

    @Test
    fun `countRequest registers with correct tags for internal`() {
        registry.countRequest(
            source = "user-service",
            target = "auth-service",
            type = RequestType.INTERNAL,
        )

        val counter = registry.find("service.request")
            .tag("source", "user-service")
            .tag("target", "auth-service")
            .tag("type", "internal")
            .counter()
        assertNotNull(counter)
        assertEquals(1.0, counter.count())
    }

    @Test
    fun `countRequest registers with correct tags for external`() {
        registry.countRequest(
            source = "user-service",
            target = "stripe-api",
            type = RequestType.EXTERNAL,
        )

        val counter = registry.find("service.request")
            .tag("source", "user-service")
            .tag("target", "stripe-api")
            .tag("type", "external")
            .counter()
        assertNotNull(counter)
        assertEquals(1.0, counter.count())
    }

    @Test
    fun `countRequest with uri`() {
        registry.countRequest(
            source = "gateway",
            target = "user-service",
            type = RequestType.INTERNAL,
            uri = "/api/users",
        )

        val counter = registry.find("service.request")
            .tag("source", "gateway")
            .tag("target", "user-service")
            .tag("type", "internal")
            .tag("uri", "/api/users")
            .counter()
        assertNotNull(counter)
        assertEquals(1.0, counter.count())
    }

    @Test
    fun `countRetry registers with attempt tag`() {
        registry.countRetry(
            source = "user-service",
            target = "email-service",
            type = RequestType.INTERNAL,
            attempt = 2,
        )

        val counter = registry.find("service.request.retry")
            .tag("source", "user-service")
            .tag("target", "email-service")
            .tag("type", "internal")
            .tag("attempt", "2")
            .counter()
        assertNotNull(counter)
        assertEquals(1.0, counter.count())
    }

    @Test
    fun `countRetry with uri`() {
        registry.countRetry(
            source = "gateway",
            target = "user-service",
            type = RequestType.INTERNAL,
            attempt = 3,
            uri = "/api/users",
        )

        val counter = registry.find("service.request.retry")
            .tag("uri", "/api/users")
            .tag("attempt", "3")
            .counter()
        assertNotNull(counter)
        assertEquals(1.0, counter.count())
    }

    @Test
    fun `countStatus registers with status family and code`() {
        registry.countStatus("http.response", 200)

        val counter = registry.find("http.response")
            .tag("status_family", "2xx")
            .tag("status_code", "200")
            .counter()
        assertNotNull(counter)
        assertEquals(1.0, counter.count())
    }

    @Test
    fun `countStatus with extra tags`() {
        registry.countStatus("http.response", 404, "endpoint" to "/api/users")

        val counter = registry.find("http.response")
            .tag("status_family", "4xx")
            .tag("status_code", "404")
            .tag("endpoint", "/api/users")
            .counter()
        assertNotNull(counter)
        assertEquals(1.0, counter.count())
    }

    @Test
    fun `countStatus groups different codes in same family as separate counters`() {
        registry.countStatus("http.response", 200)
        registry.countStatus("http.response", 201)

        val counter200 = registry.find("http.response").tag("status_code", "200").counter()
        val counter201 = registry.find("http.response").tag("status_code", "201").counter()
        assertNotNull(counter200)
        assertNotNull(counter201)
        assertEquals(1.0, counter200.count())
        assertEquals(1.0, counter201.count())
    }
}
