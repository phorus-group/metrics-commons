package group.phorus.metrics.commons

/**
 * Standardized metric names following Spring Boot Actuator and Micrometer conventions.
 *
 * Using these constants ensures consistency across all services and libraries, making
 * cross-service dashboards, queries, and alerts easier to build.
 *
 * **Naming conventions:**
 * - Lowercase with dots separating namespaces (e.g., `http.server.requests`)
 * - Start with domain/area: `http`, `database`, `cache`, `message`, etc.
 * - Be descriptive but concise
 * - Follow Spring Boot Actuator patterns where applicable
 *
 * @see [Micrometer Naming Conventions](https://micrometer.io/docs/concepts#_naming_meters)
 */
object MetricNames {

    /**
     * HTTP server request metrics.
     *
     * Standard Spring Boot Actuator metric for server-side HTTP requests.
     * Use with tags: `method`, `uri`, `status_code`, `status_family`, `outcome`.
     */
    const val HTTP_SERVER_REQUESTS = "http.server.requests"

    /**
     * HTTP server exception metrics.
     *
     * Records exceptions caught by exception handlers before being returned to the client.
     * Use with tags: `type` (exception class name), `status_code`, `status_family`.
     */
    const val HTTP_SERVER_EXCEPTIONS = "http.server.exceptions"

    /**
     * HTTP client request metrics.
     *
     * Standard Spring Boot Actuator metric for client-side HTTP requests (e.g., WebClient, RestTemplate).
     * Use with tags: `method`, `uri`, `status_code`, `status_family`, `outcome`, `clientName`.
     */
    const val HTTP_CLIENT_REQUESTS = "http.client.requests"

    /**
     * HTTP client errors.
     *
     * Records HTTP client errors (connection timeouts, DNS failures, etc.) before a response is received.
     * Use with tags: `type` (error type), `clientName`, `uri`.
     */
    const val HTTP_CLIENT_ERRORS = "http.client.errors"

    /**
     * Database query execution metrics.
     *
     * Records database query timing and count.
     * Use with tags: `operation` (select/insert/update/delete), `table`, `status` (success/error).
     */
    const val DATABASE_QUERIES = "database.queries"

    /**
     * Database connection pool metrics.
     *
     * Records connection pool state (active, idle, waiting).
     * Use with tags: `pool` (pool name), `state` (active/idle/waiting).
     */
    const val DATABASE_CONNECTIONS = "database.connections"

    /**
     * Database transaction metrics.
     *
     * Records transaction timing and outcome.
     * Use with tags: `status` (commit/rollback), `isolation_level`.
     */
    const val DATABASE_TRANSACTIONS = "database.transactions"

    /**
     * Cache operation metrics.
     *
     * Records cache hits, misses, puts, and evictions.
     * Use with tags: `cache` (cache name), `operation` (get/put/evict), `result` (hit/miss).
     */
    const val CACHE_OPERATIONS = "cache.operations"

    /**
     * Cache size metrics (gauge).
     *
     * Records current cache size.
     * Use with tags: `cache` (cache name).
     */
    const val CACHE_SIZE = "cache.size"

    /**
     * Message queue publish metrics.
     *
     * Records messages published to queues/topics.
     * Use with tags: `queue` or `topic`, `exchange`, `status` (success/error).
     */
    const val MESSAGE_PUBLISHED = "message.published"

    /**
     * Message queue consumption metrics.
     *
     * Records messages consumed from queues.
     * Use with tags: `queue`, `status` (success/error/retry).
     */
    const val MESSAGE_CONSUMED = "message.consumed"

    /**
     * Message processing duration.
     *
     * Records time spent processing messages.
     * Use with tags: `queue`, `handler`, `status` (success/error).
     */
    const val MESSAGE_PROCESSING = "message.processing"

    /**
     * Authentication attempts.
     *
     * Records authentication attempts (login, token validation, etc.).
     * Use with tags: `method` (password/token/oauth), `status` (success/failure), `reason` (expired/invalid/etc.).
     */
    const val AUTH_ATTEMPTS = "auth.attempts"

    /**
     * Authorization checks.
     *
     * Records authorization/permission checks.
     * Use with tags: `resource`, `action`, `status` (allowed/denied).
     */
    const val AUTH_CHECKS = "auth.checks"

    /**
     * Business events.
     *
     * Records domain-specific business events (user.created, order.placed, etc.).
     * Use with tags: `event_type`, `status` (success/failure).
     */
    const val BUSINESS_EVENTS = "business.events"

    /**
     * Business operations.
     *
     * Records business operation timing and outcome.
     * Use with tags: `operation`, `status` (success/failure).
     */
    const val BUSINESS_OPERATIONS = "business.operations"

    /**
     * External service calls.
     *
     * Records calls to external services (third-party APIs, microservices, etc.).
     * Use with tags: `service`, `operation`, `status_code`, `status_family`.
     */
    const val EXTERNAL_SERVICE_CALLS = "external.service.calls"

    /**
     * External service errors.
     *
     * Records errors when calling external services.
     * Use with tags: `service`, `operation`, `error_type`.
     */
    const val EXTERNAL_SERVICE_ERRORS = "external.service.errors"

    /**
     * File operations.
     *
     * Records file upload/download/delete operations.
     * Use with tags: `operation` (upload/download/delete), `storage` (local/s3/etc.), `status`.
     */
    const val FILE_OPERATIONS = "file.operations"

    /**
     * Storage size (gauge).
     *
     * Records current storage usage.
     * Use with tags: `storage` (local/s3/etc.), `bucket`.
     */
    const val STORAGE_SIZE = "storage.size"
}
