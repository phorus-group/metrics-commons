package group.phorus.metrics.commons

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import group.phorus.metrics.commons.TagNames as Tags

class CountersTest {

    private lateinit var registry: SimpleMeterRegistry

    @BeforeEach
    fun setUp() {
        registry = SimpleMeterRegistry()
    }

    @Test
    fun `count increments counter by 1`() {
        registry.count("app.events", Tags.TYPE to "login")

        val counter = registry.find("app.events").tag(Tags.TYPE, "login").counter()
        assertNotNull(counter)
        assertEquals(1.0, counter.count())
    }

    @Test
    fun `count accumulates multiple calls`() {
        registry.count("app.events", Tags.TYPE to "login")
        registry.count("app.events", Tags.TYPE to "login")
        registry.count("app.events", Tags.TYPE to "login")

        val counter = registry.find("app.events").tag(Tags.TYPE, "login").counter()
        assertNotNull(counter)
        assertEquals(3.0, counter.count())
    }

    @Test
    fun `count with multiple tags`() {
        registry.count("app.events", Tags.TYPE to "login", Tags.REGION to "eu")

        val counter = registry.find("app.events")
            .tag(Tags.TYPE, "login")
            .tag(Tags.REGION, "eu")
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
        registry.count("app.events", Tags.TYPE to "login")
        registry.count("app.events", Tags.TYPE to "logout")

        val loginCounter = registry.find("app.events").tag(Tags.TYPE, "login").counter()
        val logoutCounter = registry.find("app.events").tag(Tags.TYPE, "logout").counter()
        assertNotNull(loginCounter)
        assertNotNull(logoutCounter)
        assertEquals(1.0, loginCounter.count())
        assertEquals(1.0, logoutCounter.count())
    }

    @Test
    fun `countBy increments by custom amount`() {
        registry.countBy("app.bytes", 1024.0, Tags.DIRECTION to "inbound")

        val counter = registry.find("app.bytes").tag(Tags.DIRECTION, "inbound").counter()
        assertNotNull(counter)
        assertEquals(1024.0, counter.count())
    }

    @Test
    fun `countBy accumulates`() {
        registry.countBy("app.bytes", 512.0, Tags.DIRECTION to "inbound")
        registry.countBy("app.bytes", 256.0, Tags.DIRECTION to "inbound")

        val counter = registry.find("app.bytes").tag(Tags.DIRECTION, "inbound").counter()
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
            .tag(Tags.SOURCE, "user-service")
            .tag(Tags.TARGET, "auth-service")
            .tag(Tags.TYPE, "internal")
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
            .tag(Tags.SOURCE, "user-service")
            .tag(Tags.TARGET, "stripe-api")
            .tag(Tags.TYPE, "external")
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
            .tag(Tags.SOURCE, "gateway")
            .tag(Tags.TARGET, "user-service")
            .tag(Tags.TYPE, "internal")
            .tag(Tags.URI, "/api/users")
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
            .tag(Tags.SOURCE, "user-service")
            .tag(Tags.TARGET, "email-service")
            .tag(Tags.TYPE, "internal")
            .tag(Tags.ATTEMPT, "2")
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
            .tag(Tags.URI, "/api/users")
            .tag(Tags.ATTEMPT, "3")
            .counter()
        assertNotNull(counter)
        assertEquals(1.0, counter.count())
    }

    @Test
    fun `countStatus registers with status family and code`() {
        registry.countStatus(MetricNames.HTTP_SERVER_REQUESTS, 200)

        val counter = registry.find(MetricNames.HTTP_SERVER_REQUESTS)
            .tag(Tags.STATUS_FAMILY, "2xx")
            .tag(Tags.STATUS_CODE, "200")
            .counter()
        assertNotNull(counter)
        assertEquals(1.0, counter.count())
    }

    @Test
    fun `countStatus with extra tags`() {
        registry.countStatus(MetricNames.HTTP_SERVER_EXCEPTIONS, 404, Tags.TYPE to "NotFound")

        val counter = registry.find(MetricNames.HTTP_SERVER_EXCEPTIONS)
            .tag(Tags.STATUS_FAMILY, "4xx")
            .tag(Tags.STATUS_CODE, "404")
            .tag(Tags.TYPE, "NotFound")
            .counter()
        assertNotNull(counter)
        assertEquals(1.0, counter.count())
    }

    @Test
    fun `countStatus groups different codes in same family as separate counters`() {
        registry.countStatus(MetricNames.HTTP_SERVER_REQUESTS, 200)
        registry.countStatus(MetricNames.HTTP_SERVER_REQUESTS, 201)

        val counter200 = registry.find(MetricNames.HTTP_SERVER_REQUESTS).tag(Tags.STATUS_CODE, "200").counter()
        val counter201 = registry.find(MetricNames.HTTP_SERVER_REQUESTS).tag(Tags.STATUS_CODE, "201").counter()
        assertNotNull(counter200)
        assertNotNull(counter201)
        assertEquals(1.0, counter200.count())
        assertEquals(1.0, counter201.count())
    }
}
