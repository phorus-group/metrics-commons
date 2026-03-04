package group.phorus.metrics.commons

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class GaugesTest {

    private lateinit var registry: SimpleMeterRegistry

    @BeforeEach
    fun setUp() {
        registry = SimpleMeterRegistry()
    }

    // ========== trackGauge (Number) ==========

    @Test
    fun `trackGauge with AtomicInteger reflects current value`() {
        val activeConnections = AtomicInteger(0)
        registry.trackGauge("app.connections.active", activeConnections) { it.toDouble() }

        activeConnections.set(5)

        val gauge = registry.find("app.connections.active").gauge()
        assertNotNull(gauge)
        assertEquals(5.0, gauge.value())
    }

    @Test
    fun `trackGauge with AtomicInteger updates dynamically`() {
        val queueSize = AtomicInteger(0)
        registry.trackGauge("app.queue.size", queueSize) { it.toDouble() }

        queueSize.set(10)
        assertEquals(10.0, registry.find("app.queue.size").gauge()!!.value())

        queueSize.set(3)
        assertEquals(3.0, registry.find("app.queue.size").gauge()!!.value())
    }

    @Test
    fun `trackGauge with tags`() {
        val counter = AtomicInteger(42)
        registry.trackGauge("app.items", counter, "region" to "eu") { it.toDouble() }

        val gauge = registry.find("app.items").tag("region", "eu").gauge()
        assertNotNull(gauge)
        assertEquals(42.0, gauge.value())
    }

    // ========== trackGauge (valueFunction) ==========

    @Test
    fun `trackGauge with custom value function`() {
        val cache = mutableMapOf("a" to 1, "b" to 2, "c" to 3)
        registry.trackGauge("app.cache.entries", cache) { it.size.toDouble() }

        val gauge = registry.find("app.cache.entries").gauge()
        assertNotNull(gauge)
        assertEquals(3.0, gauge.value())

        cache["d"] = 4
        assertEquals(4.0, gauge.value())
    }

    @Test
    fun `trackGauge with value function and tags`() {
        val pool = AtomicInteger(8)
        registry.trackGauge("app.pool.available", pool, "pool" to "http") { it.toDouble() }

        val gauge = registry.find("app.pool.available").tag("pool", "http").gauge()
        assertNotNull(gauge)
        assertEquals(8.0, gauge.value())
    }

    // ========== trackCollectionSize ==========

    @Test
    fun `trackCollectionSize tracks list size`() {
        val items = CopyOnWriteArrayList<String>()
        registry.trackCollectionSize("app.items.count", items)

        val gauge = registry.find("app.items.count").gauge()
        assertNotNull(gauge)
        assertEquals(0.0, gauge.value())

        items.add("a")
        items.add("b")
        assertEquals(2.0, gauge.value())
    }

    @Test
    fun `trackCollectionSize with tags`() {
        val pending = CopyOnWriteArrayList(listOf("task1", "task2"))
        registry.trackCollectionSize("app.tasks.pending", pending, "priority" to "high")

        val gauge = registry.find("app.tasks.pending").tag("priority", "high").gauge()
        assertNotNull(gauge)
        assertEquals(2.0, gauge.value())
    }

    @Test
    fun `trackCollectionSize reflects removals`() {
        val items = CopyOnWriteArrayList(listOf("a", "b", "c"))
        registry.trackCollectionSize("app.buffer.size", items)

        assertEquals(3.0, registry.find("app.buffer.size").gauge()!!.value())

        items.removeAt(0)
        assertEquals(2.0, registry.find("app.buffer.size").gauge()!!.value())
    }

    // ========== trackMapSize ==========

    @Test
    fun `trackMapSize tracks map size`() {
        val sessions = ConcurrentHashMap<String, String>()
        registry.trackMapSize("app.sessions.active", sessions)

        val gauge = registry.find("app.sessions.active").gauge()
        assertNotNull(gauge)
        assertEquals(0.0, gauge.value())

        sessions["user1"] = "session1"
        sessions["user2"] = "session2"
        assertEquals(2.0, gauge.value())
    }

    @Test
    fun `trackMapSize with tags`() {
        val cache = ConcurrentHashMap(mapOf("k1" to "v1", "k2" to "v2"))
        registry.trackMapSize("app.cache.size", cache, "cache" to "users")

        val gauge = registry.find("app.cache.size").tag("cache", "users").gauge()
        assertNotNull(gauge)
        assertEquals(2.0, gauge.value())
    }

    @Test
    fun `trackMapSize reflects removals`() {
        val map = ConcurrentHashMap(mapOf("a" to 1, "b" to 2, "c" to 3))
        registry.trackMapSize("app.map.size", map)

        assertEquals(3.0, registry.find("app.map.size").gauge()!!.value())

        map.remove("a")
        assertEquals(2.0, registry.find("app.map.size").gauge()!!.value())
    }

    @Test
    fun `trackCollectionSize returns the collection for fluent usage`() {
        val list = registry.trackCollectionSize("app.list", CopyOnWriteArrayList<String>())
        list.add("item")
        assertEquals(1.0, registry.find("app.list").gauge()!!.value())
    }

    @Test
    fun `trackMapSize returns the map for fluent usage`() {
        val map = registry.trackMapSize("app.map", ConcurrentHashMap<String, Int>())
        map["key"] = 42
        assertEquals(1.0, registry.find("app.map").gauge()!!.value())
    }
}
