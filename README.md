# tripswitch-java

[![Maven Central](https://img.shields.io/maven-central/v/dev.tripswitch/tripswitch-java)](https://central.sonatype.com/artifact/dev.tripswitch/tripswitch-java)
[![Java](https://img.shields.io/badge/java-17%2B-blue)](https://openjdk.org/projects/jdk/17/)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue)](LICENSE)

Official Java SDK for [Tripswitch](https://tripswitch.dev) — a circuit breaker management service.

This SDK conforms to the [Tripswitch SDK Contract v0.2](https://tripswitch.dev/docs/sdk-contract).

## Features

- **Real-time state sync** via Server-Sent Events (SSE)
- **Automatic sample reporting** with buffered, batched uploads
- **Fail-open by default** — your app stays available even if Tripswitch is unreachable
- **Thread-safe** — one client per project, safe for concurrent use
- **Graceful shutdown** with timeout-aware close and sample flushing

## Installation

### Gradle (Kotlin DSL)

```kotlin
implementation("dev.tripswitch:tripswitch-java:0.1.0")
```

### Gradle (Groovy)

```groovy
implementation 'dev.tripswitch:tripswitch-java:0.1.0'
```

### Maven

```xml
<dependency>
    <groupId>dev.tripswitch</groupId>
    <artifactId>tripswitch-java</artifactId>
    <version>0.1.0</version>
</dependency>
```

**Requires Java 17+** (uses records, sealed classes, and pattern matching)

## Authentication

Tripswitch uses a two-tier authentication model:

### Runtime Credentials (SDK)

For SDK initialization, you need two credentials from **Project Settings > SDK Keys**:

| Credential | Prefix | Purpose |
|------------|--------|---------|
| **Project Key** | `eb_pk_` | SSE connection and state reads |
| **Ingest Secret** | `ik_` | HMAC-signed sample ingestion |

```java
TripSwitch ts = TripSwitch.builder("proj_abc123")
    .apiKey("eb_pk_...")
    .ingestSecret("ik_...")
    .build();
```

### Admin Credentials (Management API)

For management and automation tasks, use an **Admin Key** from **Organization Settings > Admin Keys**:

| Credential | Prefix | Purpose |
|------------|--------|---------|
| **Admin Key** | `eb_admin_` | Organization-scoped management operations |

Admin keys are used with the [Admin Client](#admin-client) for creating projects, managing breakers, and other administrative tasks — not for runtime SDK usage.

## Quick Start

```java
import dev.tripswitch.*;
import java.time.Duration;
import java.util.Map;

// Create client (blocks until SSE state sync completes)
TripSwitch ts = TripSwitch.builder("proj_abc123")
    .apiKey("eb_pk_...")
    .ingestSecret("ik_...")
    .build(Duration.ofSeconds(10));

try {
    // Wrap operations with circuit breaker
    String response = ts.execute(() -> {
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString()).body();
    },
        TripSwitch.withBreakers("external-api"),
        TripSwitch.withRouter("my-router-id"),
        TripSwitch.withMetrics(Map.of("latency", TripSwitch.LATENCY))
    );
    // Process response...
} catch (BreakerOpenException e) {
    // Circuit is open — return cached/fallback response
    System.out.println("circuit open, using fallback");
} finally {
    ts.close(Duration.ofSeconds(5));
}
```

## Configuration Options

### Client Options

| Option | Description | Default |
|--------|-------------|---------|
| `apiKey(key)` | Project key (`eb_pk_`) for SSE authentication | `null` |
| `ingestSecret(secret)` | Ingest secret (`ik_`) for HMAC-signed sample reporting | `null` |
| `failOpen(bool)` | Allow traffic when Tripswitch is unreachable | `true` |
| `baseUrl(url)` | Override API endpoint | `https://api.tripswitch.dev` |
| `onStateChange(fn)` | Callback `(name, from, to)` on breaker state transitions | `null` |
| `traceIdExtractor(fn)` | `Supplier<String>` returning a trace ID for each sample | `null` |
| `globalTags(tags)` | Tags applied to all samples | `null` |
| `metadataSyncInterval(d)` | Interval for refreshing breaker/router metadata. Set to `Duration.ZERO` to disable. | `30s` |
| `httpClient(client)` | Custom OkHttpClient instance | default |

### Execute Options

| Option | Description |
|--------|-------------|
| `withBreakers(names...)` | Breaker names to check before executing (any open throws `BreakerOpenException`). If omitted, no gating is performed. |
| `withSelectedBreakers(fn)` | Dynamically select breakers based on cached metadata. Mutually exclusive with `withBreakers`. |
| `withRouter(routerID)` | Router ID for sample routing. If omitted, no samples are emitted. |
| `withSelectedRouter(fn)` | Dynamically select a router based on cached metadata. Mutually exclusive with `withRouter`. |
| `withMetrics(map)` | Metrics to report (`LATENCY` sentinel, `Supplier<Double>`, or `Number` values) |
| `withDeferredMetrics(fn)` | Extract metrics from the task's return value (e.g., token counts from API responses) |
| `withTag(key, value)` | Add a single diagnostic tag |
| `withTags(tags)` | Diagnostic tags for this specific call (merged with global tags; call-site wins) |
| `withIgnoreErrors(types...)` | Exception classes that should not count as failures |
| `withErrorEvaluator(fn)` | Custom `Predicate<Exception>` to determine if an error is a failure (takes precedence over `withIgnoreErrors`) |
| `withTraceId(id)` | Explicit trace ID (takes precedence over `traceIdExtractor`) |

### Error Classification

Every sample includes an `ok` field indicating whether the task succeeded or failed. This is determined by the following evaluation order:

1. **`withErrorEvaluator(fn)`** — if set, takes precedence. Return `true` if the error **is a failure**; return `false` if it should be treated as success.

   ```java
   // Only count 5xx as failures; 4xx are "expected" errors
   TripSwitch.withErrorEvaluator(e -> {
       if (e instanceof HttpResponseException hre) {
           return hre.getStatusCode() >= 500;
       }
       return true; // non-HTTP errors are failures
   })
   ```

2. **`withIgnoreErrors(types...)`** — if the task exception is an instance of any listed class, it is **not** counted as a failure.

   ```java
   // NoSuchElementException is expected, don't count it
   TripSwitch.withIgnoreErrors(NoSuchElementException.class)
   ```

3. **Default** — any exception is a failure; no exception is success.

### Trace IDs

Trace IDs associate samples with distributed traces. Two ways to set them:

- **`withTraceId(id)`** — explicit per-call trace ID. Takes precedence over the extractor.

- **`traceIdExtractor(fn)`** (client option) — automatically extracts a trace ID for every `execute` call. Useful for OpenTelemetry integration:

  ```java
  TripSwitch.builder("proj_abc123")
      .traceIdExtractor(() -> {
          Span span = Span.current();
          return span.getSpanContext().isValid()
              ? span.getSpanContext().getTraceId()
              : "";
      })
      .build();
  ```

If both are set, `withTraceId` wins.

## API Reference

### TripSwitch.builder

```java
public static Builder builder(String projectId)
```

Creates a builder for the Tripswitch client. Call `.build()` or `.build(Duration timeout)` to create and start the client. Starts daemon threads for SSE state sync and sample flushing, and blocks until the initial SSE sync completes (when an API key is configured). The timeout controls how long to wait for initialization.

### execute

```java
public <T> T execute(Callable<T> task, ExecuteOption... options) throws BreakerOpenException
```

Runs a task end-to-end: checks breaker state, executes the task, and reports samples — all in one call.

- Use `withBreakers()` to gate execution on breaker state (omit for pass-through)
- Use `withRouter()` to specify where samples go (omit for no sample emission)
- Use `withMetrics()` to specify what values to report

Throws `BreakerOpenException` if any specified breaker is open.

### LATENCY

```java
public static final Object LATENCY
```

Sentinel value for `withMetrics` that instructs the SDK to automatically compute and report task duration in milliseconds.

### close

```java
public void close(Duration timeout)
public void close() // default 5-second timeout
```

Gracefully shuts down the client. The timeout controls how long to wait for buffered samples to flush. Implements `AutoCloseable`.

### stats

```java
public SDKStats stats()
```

Returns a snapshot of SDK health metrics:

```java
public record SDKStats(
    long droppedSamples,        // Samples dropped due to buffer overflow
    int bufferSize,             // Current buffer occupancy
    boolean sseConnected,       // SSE connection status
    long sseReconnects,         // Count of SSE reconnections
    Instant lastSuccessfulFlush,
    Instant lastSseEvent,
    long flushFailures,         // Batches dropped after retry exhaustion
    int cachedBreakers          // Number of breakers in local state cache
) {}
```

### Breaker State Inspection

These methods expose the SDK's local breaker cache for debugging, logging, and health checks. For gating traffic on breaker state, use `execute` with `withBreakers` — it handles state checks, throttling, and sample reporting together.

```java
public BreakerStatus getState(String name)    // null if not found
public Map<String, BreakerStatus> getAllStates()
```

```java
// Debug: why is checkout rejecting requests?
BreakerStatus status = ts.getState("checkout");
if (status != null) {
    log.info("checkout breaker: state={} allow_rate={}", status.state(), status.allowRate());
}

// Health endpoint: expose all breaker states to monitoring
ts.getAllStates().forEach((name, s) ->
    log.info("breaker {}: {}", name, s.state()));
```

### Error Handling

```java
public class BreakerOpenException extends TripSwitchException { ... }
public class ConflictingOptionsException extends TripSwitchException { ... }
public class MetadataUnavailableException extends TripSwitchException { ... }
```

| Exception | Cause |
|-----------|-------|
| `BreakerOpenException` | A specified breaker is open or request was throttled in half-open state |
| `ConflictingOptionsException` | Mutually exclusive options used (e.g. `withBreakers` + `withSelectedBreakers`) |
| `MetadataUnavailableException` | Selector used but metadata cache hasn't been populated yet |

```java
try {
    String result = ts.execute(() -> doWork(),
        TripSwitch.withBreakers("my-breaker"),
        TripSwitch.withRouter("my-router"),
        TripSwitch.withMetrics(Map.of("latency", TripSwitch.LATENCY))
    );
} catch (BreakerOpenException e) {
    // Breaker is open or request was throttled
    return fallbackValue;
}
```

## Custom Metric Values

`LATENCY` is a convenience sentinel that auto-computes task duration in milliseconds. You can report **any metric with any value**:

```java
TripSwitch.withMetrics(Map.of(
    // Auto-computed latency (convenience)
    "latency", TripSwitch.LATENCY,

    // Static numeric values
    "response_bytes", 4096,
    "queue_depth", 42.5,

    // Dynamic values via Supplier (called after task completes)
    "memory_mb", (Supplier<Double>) () -> {
        Runtime rt = Runtime.getRuntime();
        return (double) (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
    }
))
```

### Deferred Metrics

Use `withDeferredMetrics` to extract metrics from the task's return value — useful when the interesting values are in the response (e.g., token counts from LLM APIs):

```java
AnthropicResponse result = ts.execute(() -> {
    return anthropic.messages().create(request);
},
    TripSwitch.withBreakers("anthropic-spend"),
    TripSwitch.withRouter("llm-router"),
    TripSwitch.withMetrics(Map.of("latency", TripSwitch.LATENCY)),
    TripSwitch.<AnthropicResponse>withDeferredMetrics((res, err) -> {
        if (res == null) return null;
        return Map.of(
            "prompt_tokens", (double) res.usage().inputTokens(),
            "completion_tokens", (double) res.usage().outputTokens(),
            "total_tokens", (double) res.usage().totalTokens()
        );
    })
);
```

Deferred metrics are resolved after the task completes and merged with eager metrics into the same sample batch. If the function throws, it is caught and a warning is logged — eager metrics are still emitted.

### Dynamic Selection

Use `withSelectedBreakers` and `withSelectedRouter` to choose breakers or routers at runtime based on cached metadata. The SDK periodically syncs metadata from the API (default 30s), and your selector receives the current snapshot.

```java
// Gate on breakers matching a metadata property
String result = ts.execute(() -> doWork(),
    TripSwitch.withSelectedBreakers(breakers ->
        breakers.stream()
            .filter(b -> "us-east-1".equals(b.metadata().get("region")))
            .map(BreakerMeta::name)
            .toList()
    )
);

// Route samples to a router matching a metadata property
String result = ts.execute(() -> doWork(),
    TripSwitch.withSelectedRouter(routers ->
        routers.stream()
            .filter(r -> "production".equals(r.metadata().get("env")))
            .map(RouterMeta::id)
            .findFirst()
            .orElse("")
    ),
    TripSwitch.withMetrics(Map.of("latency", TripSwitch.LATENCY))
);
```

**Constraints:**
- `withBreakers` and `withSelectedBreakers` are mutually exclusive — using both throws `ConflictingOptionsException`
- `withRouter` and `withSelectedRouter` are mutually exclusive — using both throws `ConflictingOptionsException`
- If the metadata cache hasn't been populated yet, throws `MetadataUnavailableException`
- If the selector returns an empty list/string, no gating or sample emission occurs

You can also access the metadata cache directly:

```java
List<BreakerMeta> breakers = ts.getBreakersMetadata(); // null if not yet loaded
List<RouterMeta> routers = ts.getRoutersMetadata();
```

### report

```java
public void report(ReportInput input)
```

Send a sample independently of `execute`. Use this for async workflows, result-derived metrics, or fire-and-forget reporting:

```java
// Report token usage from an LLM API response
ts.report(ReportInput.builder("llm-router", "total_tokens")
    .value(1500)
    .ok(true)
    .build());

// Background process metrics
ts.report(ReportInput.builder("worker-metrics", "queue_depth")
    .value(queueLen)
    .ok(true)
    .tags(Map.of("worker", "processor-1"))
    .build());
```

Samples are buffered and batched the same way as `execute` samples. Global tags are merged automatically.

### getStatus

```java
public Status getStatus() throws IOException
```

Fetches project health status from the Tripswitch API:

```java
Status status = ts.getStatus();
System.out.printf("open=%d closed=%d%n", status.openCount(), status.closedCount());
```

## Circuit Breaker States

| State | Behavior |
|-------|----------|
| `closed` | All requests allowed, results reported |
| `open` | All requests rejected with `BreakerOpenException` |
| `half_open` | Requests throttled based on `allow_rate` (e.g., 20% allowed) |

## How It Works

1. **State Sync**: The client maintains a local cache of breaker states, updated in real-time via SSE
2. **Execute Check**: Each `execute` call checks the local cache (no network call)
3. **Sample Reporting**: Results are buffered and batched (500 samples or 15s, whichever comes first)
4. **Graceful Degradation**: If Tripswitch is unreachable, the client fails open by default

## Admin Client

The `admin` package provides a client for management and automation tasks. This is separate from the runtime SDK and uses organization-scoped admin keys.

```java
import dev.tripswitch.admin.*;

try (AdminClient client = AdminClient.builder()
        .apiKey("eb_admin_...")  // From Organization Settings > Admin Keys
        .build()) {

    // List all projects
    ListProjectsResponse projects = client.listProjects();

    // Create a project
    Project project = client.createProject(new CreateProjectInput("prod-payments"));

    // Get project details
    Project project = client.getProject("proj_abc123");

    // Delete a project (requires name confirmation as a safety guard)
    client.deleteProject("proj_abc123",
        DeleteProjectOptions.builder("prod-payments").build());

    // List breakers
    ListBreakersResponse breakers = client.listBreakers("proj_abc123", new ListParams(100));

    // Create a breaker
    Breaker breaker = client.createBreaker("proj_abc123",
        new CreateBreakerInput("api-latency", "latency_ms",
            BreakerKind.P95, BreakerOp.GT, 500));
}
```

### Admin Error Handling

The admin client maps HTTP error responses to specific exception types:

| Status | Exception | Description |
|--------|-----------|-------------|
| 400/422 | `ValidationException` | Invalid input |
| 401 | `UnauthorizedException` | Invalid or missing API key |
| 403 | `ForbiddenException` | Insufficient permissions |
| 404 | `NotFoundException` | Resource not found |
| 409 | `ConflictException` | Resource already exists |
| 429 | `RateLimitedException` | Rate limit exceeded (has `retryAfter`) |
| 5xx | `ServerFaultException` | Server error |

Network errors are wrapped in `TransportException`.

```java
try {
    client.getBreaker("proj_123", "nonexistent");
} catch (NotFoundException e) {
    System.out.println("Breaker not found: " + e.getMessage());
} catch (RateLimitedException e) {
    System.out.println("Retry after: " + e.getRetryAfter());
} catch (TransportException e) {
    System.out.println("Network error: " + e.getMessage());
}
```

**Note:** Admin keys (`eb_admin_`) are for management operations only. For runtime SDK usage, use project keys (`eb_pk_`) as shown in [Quick Start](#quick-start).

## Contributing

Contributions are welcome! Please open an issue or submit a pull request.

## License

[Apache License 2.0](LICENSE)
