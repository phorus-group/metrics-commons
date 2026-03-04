package group.phorus.metrics.commons

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class TagsTest {

    @Test
    fun `tagValue returns value when non-blank`() {
        assertEquals("hello", tagValue("hello"))
    }

    @Test
    fun `tagValue trims whitespace`() {
        assertEquals("hello", tagValue("  hello  "))
    }

    @Test
    fun `tagValue returns None for null`() {
        assertEquals("None", tagValue(null))
    }

    @Test
    fun `tagValue returns None for empty string`() {
        assertEquals("None", tagValue(""))
    }

    @Test
    fun `tagValue returns None for blank string`() {
        assertEquals("None", tagValue("   "))
    }

    @Test
    fun `statusFamily groups 2xx`() {
        assertEquals("2xx", statusFamily(200))
        assertEquals("2xx", statusFamily(201))
        assertEquals("2xx", statusFamily(204))
    }

    @Test
    fun `statusFamily groups 4xx`() {
        assertEquals("4xx", statusFamily(400))
        assertEquals("4xx", statusFamily(404))
        assertEquals("4xx", statusFamily(422))
    }

    @Test
    fun `statusFamily groups 5xx`() {
        assertEquals("5xx", statusFamily(500))
        assertEquals("5xx", statusFamily(502))
        assertEquals("5xx", statusFamily(503))
    }

    @Test
    fun `statusFamily handles 1xx and 3xx`() {
        assertEquals("1xx", statusFamily(100))
        assertEquals("3xx", statusFamily(301))
    }

    @Test
    fun `exceptionTag returns class name for throwable`() {
        assertEquals("IllegalArgumentException", exceptionTag(IllegalArgumentException("test")))
    }

    @Test
    fun `exceptionTag returns None for null`() {
        assertEquals("None", exceptionTag(null))
    }

    @Test
    fun `exceptionTag returns simple name for nested exceptions`() {
        assertEquals("RuntimeException", exceptionTag(RuntimeException("wrapper", IllegalStateException())))
    }

    @Test
    fun `boundedTag returns value when in allowed set`() {
        val allowed = setOf("GET", "POST", "PUT", "DELETE")
        assertEquals("GET", boundedTag("GET", allowed))
        assertEquals("POST", boundedTag("POST", allowed))
    }

    @Test
    fun `boundedTag returns fallback when not in allowed set`() {
        val allowed = setOf("GET", "POST", "PUT", "DELETE")
        assertEquals("other", boundedTag("PATCH", allowed))
    }

    @Test
    fun `boundedTag returns custom fallback`() {
        val allowed = setOf("admin", "user")
        assertEquals("unknown", boundedTag("hacker", allowed, fallback = "unknown"))
    }

    @Test
    fun `boundedTag returns fallback for null`() {
        val allowed = setOf("GET", "POST")
        assertEquals("other", boundedTag(null, allowed))
    }

    @Test
    fun `boundedTag returns fallback for blank`() {
        val allowed = setOf("GET", "POST")
        assertEquals("other", boundedTag("  ", allowed))
    }

    @Test
    fun `boundedTag trims before matching`() {
        val allowed = setOf("GET", "POST")
        assertEquals("GET", boundedTag("  GET  ", allowed))
    }

    @Test
    fun `RequestType INTERNAL has correct value`() {
        assertEquals("internal", RequestType.INTERNAL.value)
    }

    @Test
    fun `RequestType EXTERNAL has correct value`() {
        assertEquals("external", RequestType.EXTERNAL.value)
    }
}
