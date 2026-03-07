package group.phorus.metrics.commons

/**
 * Standard tag names for consistent metric tagging across all services.
 *
 * Example:
 * ```kotlin
 * registry.count(
 *     MetricNames.HTTP_SERVER_REQUESTS,
 *     TagNames.METHOD to "GET",
 *     TagNames.STATUS_CODE to "200",
 * )
 * ```
 *
 * @see MetricNames for standardized metric names
 */
object TagNames {
    /** HTTP method (e.g., "GET", "POST", "PUT", "DELETE"). */
    const val METHOD = "method"

    /** HTTP status code as a string (e.g., "200", "404", "500"). */
    const val STATUS_CODE = "status_code"

    /** HTTP status family (e.g., "2xx", "3xx", "4xx", "5xx"). Computed via [statusFamily]. */
    const val STATUS_FAMILY = "status_family"

    /** Request URI path (e.g., "/api/users", "/health"). Keep cardinality bounded by using path templates. */
    const val URI = "uri"

    /** API endpoint name (e.g., "getUser", "createOrder"). Alternative to URI for lower cardinality. */
    const val ENDPOINT = "endpoint"

    /** Request protocol (e.g., "HTTP/1.1", "HTTP/2", "gRPC"). */
    const val PROTOCOL = "protocol"

    /** Source service name (e.g., "user-service", "gateway"). */
    const val SOURCE = "source"

    /** Target service or API name (e.g., "auth-service", "stripe-api"). */
    const val TARGET = "target"

    /** Request type: "internal" (between owned services) or "external" (to third-party APIs). */
    const val TYPE = "type"

    /** Environment (e.g., "prod", "staging", "dev"). */
    const val ENV = "env"

    /** Service region or availability zone (e.g., "us-east-1", "eu-central-1"). */
    const val REGION = "region"

    /** Exception simple class name (e.g., "NullPointerException", "NotFound"). Extracted via [exceptionTag]. */
    const val EXCEPTION = "exception"

    /** Error code or category (e.g., "VALIDATION_ERROR", "TIMEOUT", "UNAUTHORIZED"). */
    const val ERROR_CODE = "error_code"

    /** Error severity (e.g., "warning", "error", "critical"). */
    const val SEVERITY = "severity"

    /** Database table name (e.g., "users", "orders"). */
    const val TABLE = "table"

    /** Database operation (e.g., "SELECT", "INSERT", "UPDATE", "DELETE"). */
    const val OPERATION = "operation"

    /** Database query result (e.g., "success", "timeout", "error"). */
    const val RESULT = "result"

    /** Connection pool name or identifier. */
    const val POOL = "pool"

    /** Cache operation (e.g., "hit", "miss", "put", "evict"). */
    const val CACHE_OPERATION = "cache_operation"

    /** Cache name or identifier (e.g., "user_cache", "session_cache"). */
    const val CACHE_NAME = "cache_name"

    /** Queue name (e.g., "user_events", "email_queue"). */
    const val QUEUE = "queue"

    /** Topic name for pub/sub messaging (e.g., "order_created", "payment_processed"). */
    const val TOPIC = "topic"

    /** Message consumer group identifier. */
    const val CONSUMER_GROUP = "consumer_group"

    /** Retry attempt number (e.g., "1", "2", "3"). */
    const val ATTEMPT = "attempt"

    /** Circuit breaker state (e.g., "closed", "open", "half_open"). */
    const val CIRCUIT_STATE = "circuit_state"

    /** User ID (use only when cardinality is bounded, or use [boundedTag]). */
    const val USER_ID = "user_id"

    /** Tenant or organization ID. */
    const val TENANT_ID = "tenant_id"

    /** Currency code (e.g., "USD", "EUR", "GBP"). */
    const val CURRENCY = "currency"

    /** Payment method (e.g., "credit_card", "bank_transfer"). */
    const val PAYMENT_METHOD = "payment_method"

    /** Outcome of an operation (e.g., "success", "failure", "timeout"). */
    const val OUTCOME = "outcome"

    /** Direction of data flow (e.g., "inbound", "outbound"). */
    const val DIRECTION = "direction"

    /** Job or task name for background processing. */
    const val JOB = "job"

    /** Version identifier (e.g., "v1", "v2"). */
    const val VERSION = "version"
}
