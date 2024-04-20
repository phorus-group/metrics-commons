package group.phorus.metrics.commons

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class MetricsKtTest {

    private val meterRegistry = SimpleMeterRegistry()

    @Test
    fun `record internal request`() {
        var called = false

        meterRegistry.recordRequest(
            source = "testSource",
            target = "testTarget",
            type = RequestType.INTERNAL,
            uri = "testURI"
        ) {
            called = true
        }

        assertTrue(called)
    }

    @Test
    fun `record external request`() {
        var called = false

        meterRegistry.recordRequest(
            source = "testSource",
            target = "testTarget",
            type = RequestType.EXTERNAL,
            uri = "testURI"
        ) {
            called = true
        }

        assertTrue(called)
    }
}