package dev.tripswitch;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.*;
import org.junit.jupiter.api.*;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.*;

class TripSwitchTest {

    private TripSwitch client;

    @AfterEach
    void tearDown() {
        if (client != null) {
            client.close(Duration.ofMillis(500));
            client = null;
        }
    }

    // ---- Reflection helpers ----

    private static String baseUrl(MockWebServer server) {
        return server.url("/").toString().replaceAll("/$", "");
    }

    private static void setBreakerState(TripSwitch c, String name, String state, double allowRate) throws Exception {
        Field stateLockField = TripSwitch.class.getDeclaredField("stateLock");
        stateLockField.setAccessible(true);
        ReentrantReadWriteLock lock = (ReentrantReadWriteLock) stateLockField.get(c);

        Field statesField = TripSwitch.class.getDeclaredField("breakerStates");
        statesField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> states = (Map<String, Object>) statesField.get(c);

        Class<?> innerClass = Arrays.stream(TripSwitch.class.getDeclaredClasses())
                .filter(cl -> cl.getSimpleName().equals("InternalBreakerState"))
                .findFirst().orElseThrow();
        Constructor<?> ctor = innerClass.getDeclaredConstructor(String.class, double.class);
        ctor.setAccessible(true);

        lock.writeLock().lock();
        try {
            states.put(name, ctor.newInstance(state, allowRate));
        } finally {
            lock.writeLock().unlock();
        }
    }

    @SuppressWarnings("unchecked")
    private static LinkedBlockingQueue<TripSwitch.Sample> getSampleQueue(TripSwitch c) throws Exception {
        Field field = TripSwitch.class.getDeclaredField("sampleQueue");
        field.setAccessible(true);
        return (LinkedBlockingQueue<TripSwitch.Sample>) field.get(c);
    }

    private static TripSwitch.Sample pollSample(TripSwitch c) throws Exception {
        return getSampleQueue(c).poll(200, TimeUnit.MILLISECONDS);
    }

    private static List<TripSwitch.Sample> drainSamples(TripSwitch c) throws Exception {
        List<TripSwitch.Sample> result = new ArrayList<>();
        LinkedBlockingQueue<TripSwitch.Sample> queue = getSampleQueue(c);
        TripSwitch.Sample s;
        while ((s = queue.poll(100, TimeUnit.MILLISECONDS)) != null) {
            result.add(s);
        }
        return result;
    }

    private static void setBreakersMetadata(TripSwitch c, List<BreakerMeta> meta) throws Exception {
        Field field = TripSwitch.class.getDeclaredField("breakersMeta");
        field.setAccessible(true);
        field.set(c, meta);
    }

    private static void setRoutersMetadata(TripSwitch c, List<RouterMeta> meta) throws Exception {
        Field field = TripSwitch.class.getDeclaredField("routersMeta");
        field.setAccessible(true);
        field.set(c, meta);
    }

    private TripSwitch createTestClient() {
        return TripSwitch.builder("proj_test")
                .metadataSyncInterval(Duration.ZERO)
                .build(Duration.ofMillis(100));
    }

    // ---- Client Lifecycle ----

    @Test
    void testBuildWithDefaults() {
        client = createTestClient();
        assertNotNull(client);
        SDKStats stats = client.stats();
        assertEquals(0, stats.droppedSamples());
        assertEquals(0, stats.bufferSize());
    }

    @Test
    void testCloseIdempotent() {
        client = createTestClient();
        assertDoesNotThrow(() -> client.close(Duration.ofMillis(100)));
        assertDoesNotThrow(() -> client.close(Duration.ofMillis(100)));
    }

    @Test
    void testStatsReflectState() throws Exception {
        client = createTestClient();
        setBreakerState(client, "b1", "closed", 1.0);
        setBreakerState(client, "b2", "open", 0.0);

        SDKStats stats = client.stats();
        assertEquals(2, stats.cachedBreakers());
    }

    // ---- Execute: Breaker Gating ----

    @Test
    void testExecute_NoBreakers_PassThrough() {
        client = createTestClient();
        String result = client.execute(() -> "success",
                TripSwitch.withRouter("router-1"),
                TripSwitch.withMetrics(Map.of("count", 1)));
        assertEquals("success", result);
    }

    @Test
    void testExecute_ClosedBreaker_Allows() throws Exception {
        client = createTestClient();
        setBreakerState(client, "test-breaker", "closed", 1.0);

        String result = client.execute(() -> "success",
                TripSwitch.withBreakers("test-breaker"));
        assertEquals("success", result);
    }

    @Test
    void testExecute_OpenBreaker_Throws() throws Exception {
        client = createTestClient();
        setBreakerState(client, "test-breaker", "open", 0.0);

        boolean[] taskRan = {false};
        assertThrows(BreakerOpenException.class, () ->
                client.execute(() -> { taskRan[0] = true; return "fail"; },
                        TripSwitch.withBreakers("test-breaker")));
        assertFalse(taskRan[0], "task should not run when breaker is open");
    }

    @Test
    void testExecute_HalfOpen_ZeroRate_AlwaysBlocked() throws Exception {
        client = createTestClient();
        setBreakerState(client, "throttled", "half_open", 0.0);

        assertThrows(BreakerOpenException.class, () ->
                client.execute(() -> "fail", TripSwitch.withBreakers("throttled")));
    }

    @Test
    void testExecute_HalfOpen_FullRate_AlwaysAllowed() throws Exception {
        client = createTestClient();
        setBreakerState(client, "allowed", "half_open", 1.0);

        String result = client.execute(() -> "success",
                TripSwitch.withBreakers("allowed"));
        assertEquals("success", result);
    }

    @Test
    void testExecute_MultipleBreakers_AnyOpenBlocks() throws Exception {
        client = createTestClient();
        setBreakerState(client, "breaker-a", "closed", 1.0);
        setBreakerState(client, "breaker-b", "open", 0.0);

        boolean[] taskRan = {false};
        assertThrows(BreakerOpenException.class, () ->
                client.execute(() -> { taskRan[0] = true; return "fail"; },
                        TripSwitch.withBreakers("breaker-a", "breaker-b")));
        assertFalse(taskRan[0]);
    }

    @Test
    void testExecute_MultipleBreakers_AllClosedAllows() throws Exception {
        client = createTestClient();
        setBreakerState(client, "breaker-a", "closed", 1.0);
        setBreakerState(client, "breaker-b", "closed", 1.0);

        String result = client.execute(() -> "success",
                TripSwitch.withBreakers("breaker-a", "breaker-b"));
        assertEquals("success", result);
    }

    @Test
    void testExecute_UnknownBreaker_FailOpen() {
        client = createTestClient();
        // Unknown breaker not in cache should fail-open
        String result = client.execute(() -> "success",
                TripSwitch.withBreakers("unknown-breaker"));
        assertEquals("success", result);
    }

    // ---- Execute: Error Handling ----

    @Test
    void testExecute_TaskException_Rethrown() {
        client = createTestClient();
        RuntimeException expected = new RuntimeException("task failed");
        RuntimeException thrown = assertThrows(RuntimeException.class, () ->
                client.execute(() -> { throw expected; }));
        assertSame(expected, thrown);
    }

    @Test
    void testExecute_CheckedException_Wrapped() {
        client = createTestClient();
        assertThrows(TripSwitchException.class, () ->
                client.execute(() -> { throw new Exception("checked"); }));
    }

    @Test
    void testExecute_WithIgnoreErrors() throws Exception {
        client = createTestClient();
        // Execute with ignored error type - sample should report ok=true
        assertThrows(IllegalArgumentException.class, () ->
                client.execute(() -> { throw new IllegalArgumentException("ignored"); },
                        TripSwitch.withRouter("router-1"),
                        TripSwitch.withMetrics(Map.of("count", 1)),
                        TripSwitch.withIgnoreErrors(IllegalArgumentException.class)));

        TripSwitch.Sample sample = pollSample(client);
        assertNotNull(sample);
        assertTrue(sample.ok(), "ignored error should report ok=true");
    }

    @Test
    void testExecute_WithErrorEvaluator_FalseReturnsOk() throws Exception {
        client = createTestClient();
        // Evaluator returns false = not a failure
        assertThrows(RuntimeException.class, () ->
                client.execute(() -> { throw new RuntimeException("ok-error"); },
                        TripSwitch.withRouter("router-1"),
                        TripSwitch.withMetrics(Map.of("count", 1)),
                        TripSwitch.withErrorEvaluator(e -> false)));

        TripSwitch.Sample sample = pollSample(client);
        assertNotNull(sample);
        assertTrue(sample.ok());
    }

    @Test
    void testExecute_WithErrorEvaluator_TrueReturnsFailure() throws Exception {
        client = createTestClient();
        assertThrows(RuntimeException.class, () ->
                client.execute(() -> { throw new RuntimeException("bad-error"); },
                        TripSwitch.withRouter("router-1"),
                        TripSwitch.withMetrics(Map.of("count", 1)),
                        TripSwitch.withErrorEvaluator(e -> true)));

        TripSwitch.Sample sample = pollSample(client);
        assertNotNull(sample);
        assertFalse(sample.ok());
    }

    @Test
    void testExecute_EvaluatorOverridesIgnoreList() throws Exception {
        client = createTestClient();
        // Even if error type is in ignore list, evaluator takes precedence
        assertThrows(IllegalArgumentException.class, () ->
                client.execute(() -> { throw new IllegalArgumentException("test"); },
                        TripSwitch.withRouter("router-1"),
                        TripSwitch.withMetrics(Map.of("count", 1)),
                        TripSwitch.withIgnoreErrors(IllegalArgumentException.class),
                        TripSwitch.withErrorEvaluator(e -> true)));

        TripSwitch.Sample sample = pollSample(client);
        assertNotNull(sample);
        assertFalse(sample.ok(), "evaluator should override ignore list");
    }

    // ---- Execute: Metrics & Samples ----

    @Test
    void testExecute_LatencyMetric() throws Exception {
        client = createTestClient();
        client.execute(() -> "ok",
                TripSwitch.withRouter("router-1"),
                TripSwitch.withMetrics(Map.of("latency", TripSwitch.LATENCY)));

        TripSwitch.Sample sample = pollSample(client);
        assertNotNull(sample);
        assertEquals("latency", sample.metric());
        assertTrue(sample.value() >= 0, "latency should be >= 0");
        assertEquals("router-1", sample.routerId());
    }

    @Test
    void testExecute_MultipleMetrics() throws Exception {
        client = createTestClient();
        Supplier<Double> supplier = () -> 42.0;
        client.execute(() -> "ok",
                TripSwitch.withRouter("router-1"),
                TripSwitch.withMetrics(Map.of(
                        "latency", TripSwitch.LATENCY,
                        "count", 1,
                        "queue_depth", supplier)));

        List<TripSwitch.Sample> samples = drainSamples(client);
        assertEquals(3, samples.size());

        Map<String, Double> byMetric = new HashMap<>();
        for (TripSwitch.Sample s : samples) {
            byMetric.put(s.metric(), s.value());
        }
        assertTrue(byMetric.containsKey("latency"));
        assertEquals(1.0, byMetric.get("count"));
        assertEquals(42.0, byMetric.get("queue_depth"));
    }

    @Test
    void testExecute_SupplierThrows_MetricSkipped() throws Exception {
        client = createTestClient();
        Supplier<Double> bad = () -> { throw new RuntimeException("boom"); };
        client.execute(() -> "ok",
                TripSwitch.withRouter("router-1"),
                TripSwitch.withMetrics(Map.of(
                        "bad_metric", bad,
                        "good_metric", 99.0)));

        List<TripSwitch.Sample> samples = drainSamples(client);
        assertEquals(1, samples.size(), "bad supplier metric should be skipped");
        assertEquals("good_metric", samples.get(0).metric());
        assertEquals(99.0, samples.get(0).value());
    }

    @Test
    void testExecute_NoRouter_NoSamples() throws Exception {
        client = createTestClient();
        // Metrics without router = no samples emitted
        client.execute(() -> "ok",
                TripSwitch.withMetrics(Map.of("count", 1)));

        TripSwitch.Sample sample = pollSample(client);
        assertNull(sample, "no samples when no router specified");
    }

    @Test
    void testExecute_NoMetrics_NoRouter_NoSamples() throws Exception {
        client = createTestClient();
        client.execute(() -> "ok");

        TripSwitch.Sample sample = pollSample(client);
        assertNull(sample);
    }

    // ---- Execute: Tags ----

    @Test
    void testExecute_GlobalTags() throws Exception {
        client = TripSwitch.builder("proj_test")
                .globalTags(Map.of("env", "test", "service", "api"))
                .metadataSyncInterval(Duration.ZERO)
                .build(Duration.ofMillis(100));

        client.execute(() -> "ok",
                TripSwitch.withRouter("router-1"),
                TripSwitch.withMetrics(Map.of("count", 1)));

        TripSwitch.Sample sample = pollSample(client);
        assertNotNull(sample);
        assertNotNull(sample.tags());
        assertEquals("test", sample.tags().get("env"));
        assertEquals("api", sample.tags().get("service"));
    }

    @Test
    void testExecute_CallSiteTagsOverrideGlobal() throws Exception {
        client = TripSwitch.builder("proj_test")
                .globalTags(Map.of("env", "prod", "service", "api"))
                .metadataSyncInterval(Duration.ZERO)
                .build(Duration.ofMillis(100));

        client.execute(() -> "ok",
                TripSwitch.withRouter("router-1"),
                TripSwitch.withMetrics(Map.of("count", 1)),
                TripSwitch.withTags(Map.of("env", "staging", "endpoint", "/users")));

        TripSwitch.Sample sample = pollSample(client);
        assertNotNull(sample);
        assertEquals("staging", sample.tags().get("env"), "call-site should override global");
        assertEquals("api", sample.tags().get("service"), "global should remain");
        assertEquals("/users", sample.tags().get("endpoint"), "call-site tag added");
    }

    @Test
    void testExecute_WithTag() throws Exception {
        client = createTestClient();
        client.execute(() -> "ok",
                TripSwitch.withRouter("router-1"),
                TripSwitch.withMetrics(Map.of("count", 1)),
                TripSwitch.withTag("endpoint", "/checkout"),
                TripSwitch.withTag("method", "POST"));

        TripSwitch.Sample sample = pollSample(client);
        assertNotNull(sample);
        assertEquals("/checkout", sample.tags().get("endpoint"));
        assertEquals("POST", sample.tags().get("method"));
    }

    @Test
    void testExecute_WithTags() throws Exception {
        client = createTestClient();
        client.execute(() -> "ok",
                TripSwitch.withRouter("router-1"),
                TripSwitch.withMetrics(Map.of("count", 1)),
                TripSwitch.withTags(Map.of("a", "1", "b", "2")));

        TripSwitch.Sample sample = pollSample(client);
        assertNotNull(sample);
        assertEquals("1", sample.tags().get("a"));
        assertEquals("2", sample.tags().get("b"));
    }

    // ---- Execute: Trace ID ----

    @Test
    void testExecute_ExplicitTraceId() throws Exception {
        client = createTestClient();
        client.execute(() -> "ok",
                TripSwitch.withRouter("router-1"),
                TripSwitch.withMetrics(Map.of("count", 1)),
                TripSwitch.withTraceId("explicit-trace-123"));

        TripSwitch.Sample sample = pollSample(client);
        assertNotNull(sample);
        assertEquals("explicit-trace-123", sample.traceId());
    }

    @Test
    void testExecute_TraceIdExtractor() throws Exception {
        client = TripSwitch.builder("proj_test")
                .traceIdExtractor(() -> "extracted-trace-456")
                .metadataSyncInterval(Duration.ZERO)
                .build(Duration.ofMillis(100));

        client.execute(() -> "ok",
                TripSwitch.withRouter("router-1"),
                TripSwitch.withMetrics(Map.of("count", 1)));

        TripSwitch.Sample sample = pollSample(client);
        assertNotNull(sample);
        assertEquals("extracted-trace-456", sample.traceId());
    }

    @Test
    void testExecute_ExplicitTraceIdOverridesExtractor() throws Exception {
        client = TripSwitch.builder("proj_test")
                .traceIdExtractor(() -> "extractor-trace")
                .metadataSyncInterval(Duration.ZERO)
                .build(Duration.ofMillis(100));

        client.execute(() -> "ok",
                TripSwitch.withRouter("router-1"),
                TripSwitch.withMetrics(Map.of("count", 1)),
                TripSwitch.withTraceId("option-trace"));

        TripSwitch.Sample sample = pollSample(client);
        assertNotNull(sample);
        assertEquals("option-trace", sample.traceId());
    }

    // ---- Execute: Deferred Metrics ----

    @Test
    void testExecute_DeferredMetrics() throws Exception {
        client = createTestClient();
        String result = client.execute(() -> "hello-world",
                TripSwitch.withRouter("router-1"),
                TripSwitch.withMetrics(Map.of("latency", TripSwitch.LATENCY)),
                TripSwitch.<String>withDeferredMetrics((res, err) ->
                        Map.of("result_length", (double) res.length())));

        assertEquals("hello-world", result);

        List<TripSwitch.Sample> samples = drainSamples(client);
        assertEquals(2, samples.size());

        Map<String, Double> byMetric = new HashMap<>();
        for (TripSwitch.Sample s : samples) {
            byMetric.put(s.metric(), s.value());
        }
        assertTrue(byMetric.containsKey("latency"));
        assertEquals(11.0, byMetric.get("result_length"));
    }

    @Test
    void testExecute_DeferredMetrics_Throws_OtherMetricsStillEmitted() throws Exception {
        client = createTestClient();
        client.execute(() -> "ok",
                TripSwitch.withRouter("router-1"),
                TripSwitch.withMetrics(Map.of("count", 1)),
                TripSwitch.<String>withDeferredMetrics((res, err) -> {
                    throw new RuntimeException("boom");
                }));

        // The eager metric should still be emitted
        TripSwitch.Sample sample = pollSample(client);
        assertNotNull(sample, "eager metric should still be emitted after deferred throws");
        assertEquals("count", sample.metric());
    }

    // ---- Execute: Dynamic Selection ----

    @Test
    void testExecute_SelectedBreakers() throws Exception {
        client = createTestClient();
        setBreakersMetadata(client, List.of(
                new BreakerMeta("b1", "breaker-east", Map.of("region", "us-east-1")),
                new BreakerMeta("b2", "breaker-west", Map.of("region", "us-west-2"))));
        setBreakerState(client, "breaker-east", "closed", 1.0);
        setBreakerState(client, "breaker-west", "open", 0.0);

        // Select only us-east-1 breakers (closed) → should succeed
        String result = client.execute(() -> "success",
                TripSwitch.withSelectedBreakers(breakers -> {
                    List<String> names = new ArrayList<>();
                    for (BreakerMeta b : breakers) {
                        if ("us-east-1".equals(b.metadata().get("region")))
                            names.add(b.name());
                    }
                    return names;
                }));
        assertEquals("success", result);

        // Select us-west-2 breakers (open) → should throw
        assertThrows(BreakerOpenException.class, () ->
                client.execute(() -> "fail",
                        TripSwitch.withSelectedBreakers(breakers -> {
                            List<String> names = new ArrayList<>();
                            for (BreakerMeta b : breakers) {
                                if ("us-west-2".equals(b.metadata().get("region")))
                                    names.add(b.name());
                            }
                            return names;
                        })));
    }

    @Test
    void testExecute_SelectedRouter() throws Exception {
        client = createTestClient();
        setRoutersMetadata(client, List.of(
                new RouterMeta("r1", "router-prod", Map.of("env", "production")),
                new RouterMeta("r2", "router-staging", Map.of("env", "staging"))));

        client.execute(() -> "ok",
                TripSwitch.withSelectedRouter(routers -> {
                    for (RouterMeta r : routers) {
                        if ("production".equals(r.metadata().get("env")))
                            return r.id();
                    }
                    return "";
                }),
                TripSwitch.withMetrics(Map.of("count", 1)));

        TripSwitch.Sample sample = pollSample(client);
        assertNotNull(sample);
        assertEquals("r1", sample.routerId());
    }

    @Test
    void testExecute_BreakersConflict() throws Exception {
        client = createTestClient();
        setBreakersMetadata(client, List.of(new BreakerMeta("b1", "breaker1", Map.of())));

        assertThrows(ConflictingOptionsException.class, () ->
                client.execute(() -> "fail",
                        TripSwitch.withBreakers("explicit"),
                        TripSwitch.withSelectedBreakers(b -> List.of("selected"))));
    }

    @Test
    void testExecute_RouterConflict() throws Exception {
        client = createTestClient();
        setRoutersMetadata(client, List.of(new RouterMeta("r1", "router1", Map.of())));

        assertThrows(ConflictingOptionsException.class, () ->
                client.execute(() -> "fail",
                        TripSwitch.withRouter("explicit"),
                        TripSwitch.withSelectedRouter(r -> "selected")));
    }

    @Test
    void testExecute_SelectedBreakers_EmptyCache() {
        client = createTestClient();
        // No metadata cached → MetadataUnavailableException
        assertThrows(MetadataUnavailableException.class, () ->
                client.execute(() -> "fail",
                        TripSwitch.withSelectedBreakers(b -> List.of("breaker"))));
    }

    @Test
    void testExecute_SelectedRouter_EmptyCache() {
        client = createTestClient();
        assertThrows(MetadataUnavailableException.class, () ->
                client.execute(() -> "fail",
                        TripSwitch.withSelectedRouter(r -> "router")));
    }

    // ---- SSE State Updates ----

    @Test
    void testSse_UpdatesBreakerState() throws Exception {
        MockWebServer server = new MockWebServer();
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody("data: {\"breaker\":\"test-breaker\",\"state\":\"closed\",\"allow_rate\":1.0}\n\n")
                .setSocketPolicy(SocketPolicy.KEEP_OPEN));
        server.start();

        try {
            client = TripSwitch.builder("proj_test")
                    .apiKey("test-key")
                    .baseUrl(baseUrl(server))
                    .metadataSyncInterval(Duration.ZERO)
                    .build(Duration.ofSeconds(5));

            BreakerStatus status = client.getState("test-breaker");
            assertNotNull(status, "breaker state should be set after SSE event");
            assertEquals("closed", status.state());
            assertEquals(1.0, status.allowRate());
        } finally {
            server.shutdown();
        }
    }

    @Test
    void testSse_StateChangeCallback() throws Exception {
        var changes = new CopyOnWriteArrayList<String>();

        MockWebServer server = new MockWebServer();
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody("data: {\"breaker\":\"cb-test\",\"state\":\"closed\",\"allow_rate\":1.0}\n\n"
                        + "data: {\"breaker\":\"cb-test\",\"state\":\"open\",\"allow_rate\":0.0}\n\n")
                .setSocketPolicy(SocketPolicy.KEEP_OPEN));
        server.start();

        try {
            client = TripSwitch.builder("proj_test")
                    .apiKey("test-key")
                    .baseUrl(baseUrl(server))
                    .metadataSyncInterval(Duration.ZERO)
                    .onStateChange((name, from, to) -> changes.add(name + ":" + from + "->" + to))
                    .build(Duration.ofSeconds(5));

            // Wait a moment for the second event to be processed
            Thread.sleep(300);

            assertFalse(changes.isEmpty(), "state change callback should have fired");
            assertTrue(changes.stream().anyMatch(s -> s.contains("cb-test:closed->open")));
        } finally {
            server.shutdown();
        }
    }

    @Test
    void testGetState_AndGetAllStates() throws Exception {
        client = createTestClient();
        setBreakerState(client, "b1", "closed", 1.0);
        setBreakerState(client, "b2", "open", 0.0);
        setBreakerState(client, "b3", "half_open", 0.5);

        BreakerStatus s1 = client.getState("b1");
        assertNotNull(s1);
        assertEquals("closed", s1.state());
        assertEquals(1.0, s1.allowRate());

        assertNull(client.getState("nonexistent"));

        Map<String, BreakerStatus> all = client.getAllStates();
        assertEquals(3, all.size());
        assertEquals("open", all.get("b2").state());
        assertEquals("half_open", all.get("b3").state());
        assertEquals(0.5, all.get("b3").allowRate());
    }

    // ---- Batch Sending ----

    @Test
    void testBatchSending_CompressedPayload() throws Exception {
        MockWebServer server = new MockWebServer();
        server.enqueue(new MockResponse().setResponseCode(202));
        server.start();

        try {
            String ingestSecret = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
            client = TripSwitch.builder("proj_test")
                    .ingestSecret(ingestSecret)
                    .baseUrl(baseUrl(server))
                    .metadataSyncInterval(Duration.ZERO)
                    .build(Duration.ofMillis(100));

            // Call sendBatch directly via reflection
            var sample = new TripSwitch.Sample("router-123", "latency",
                    System.currentTimeMillis(), 42.0, true, Map.of("env", "test"), "trace-1");

            Method sendBatch = TripSwitch.class.getDeclaredMethod("sendBatch", List.class);
            sendBatch.setAccessible(true);
            sendBatch.invoke(client, List.of(sample));

            RecordedRequest request = server.takeRequest(5, TimeUnit.SECONDS);
            assertNotNull(request);
            assertEquals("/v1/projects/proj_test/ingest", request.getPath());
            assertEquals("gzip", request.getHeader("Content-Encoding"));
            assertNotNull(request.getHeader("X-EB-Timestamp"));
            assertTrue(request.getHeader("X-EB-Signature").startsWith("v1="));

            // Decompress and verify JSON
            byte[] compressed = request.getBody().readByteArray();
            byte[] decompressed;
            try (var gis = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
                decompressed = gis.readAllBytes();
            }
            ObjectMapper mapper = new ObjectMapper();
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = mapper.readValue(decompressed, Map.class);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> samples = (List<Map<String, Object>>) payload.get("samples");
            assertEquals(1, samples.size());
            assertEquals("router-123", samples.get(0).get("router_id"));
            assertEquals("latency", samples.get(0).get("metric"));
        } finally {
            server.shutdown();
        }
    }

    @Test
    void testBatchSending_RetryOnFailure() throws Exception {
        MockWebServer server = new MockWebServer();
        server.enqueue(new MockResponse().setResponseCode(500)); // first attempt fails
        server.enqueue(new MockResponse().setResponseCode(202)); // retry succeeds
        server.start();

        try {
            client = TripSwitch.builder("proj_test")
                    .baseUrl(baseUrl(server))
                    .metadataSyncInterval(Duration.ZERO)
                    .build(Duration.ofMillis(100));

            var sample = new TripSwitch.Sample("router-1", "count",
                    System.currentTimeMillis(), 1.0, true, null, null);

            Method sendBatch = TripSwitch.class.getDeclaredMethod("sendBatch", List.class);
            sendBatch.setAccessible(true);
            sendBatch.invoke(client, List.of(sample));

            // Should have made 2 requests (first failed, second succeeded)
            assertEquals(2, server.getRequestCount());
            assertEquals(0, client.stats().droppedSamples());
        } finally {
            server.shutdown();
        }
    }

    @Test
    void testBatchSending_AllRetriesFail() throws Exception {
        MockWebServer server = new MockWebServer();
        // 4 attempts total (1 initial + 3 retries)
        for (int i = 0; i < 4; i++) {
            server.enqueue(new MockResponse().setResponseCode(500));
        }
        server.start();

        try {
            client = TripSwitch.builder("proj_test")
                    .baseUrl(baseUrl(server))
                    .metadataSyncInterval(Duration.ZERO)
                    .build(Duration.ofMillis(100));

            var sample = new TripSwitch.Sample("router-1", "count",
                    System.currentTimeMillis(), 1.0, true, null, null);

            Method sendBatch = TripSwitch.class.getDeclaredMethod("sendBatch", List.class);
            sendBatch.setAccessible(true);
            sendBatch.invoke(client, List.of(sample));

            SDKStats stats = client.stats();
            assertEquals(1, stats.droppedSamples(), "batch should be dropped after retries exhausted");
            assertEquals(1, stats.flushFailures());
        } finally {
            server.shutdown();
        }
    }

    // ---- Report ----

    @Test
    void testReport_ValidInput() throws Exception {
        client = TripSwitch.builder("proj_test")
                .globalTags(Map.of("env", "prod"))
                .metadataSyncInterval(Duration.ZERO)
                .build(Duration.ofMillis(100));

        client.report(ReportInput.builder("llm-router", "total_tokens")
                .value(1500)
                .ok(true)
                .traceId("trace_abc")
                .tags(Map.of("model", "claude"))
                .build());

        TripSwitch.Sample sample = pollSample(client);
        assertNotNull(sample);
        assertEquals("llm-router", sample.routerId());
        assertEquals("total_tokens", sample.metric());
        assertEquals(1500.0, sample.value());
        assertTrue(sample.ok());
        assertEquals("trace_abc", sample.traceId());
        assertEquals("claude", sample.tags().get("model"));
        assertEquals("prod", sample.tags().get("env"), "global tags should be merged");
    }

    @Test
    void testReport_MissingRouterId_NoSample() throws Exception {
        client = createTestClient();
        client.report(ReportInput.builder(null, "count").value(1).build());

        TripSwitch.Sample sample = pollSample(client);
        assertNull(sample, "no sample when routerId is missing");
    }

    @Test
    void testReport_MissingMetric_NoSample() throws Exception {
        client = createTestClient();
        client.report(ReportInput.builder("router", null).value(1).build());

        TripSwitch.Sample sample = pollSample(client);
        assertNull(sample, "no sample when metric is missing");
    }

    // ---- GetStatus ----

    @Test
    void testGetStatus() throws Exception {
        MockWebServer server = new MockWebServer();
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                if (request.getPath().contains("state:stream")) {
                    return new MockResponse()
                            .setHeader("Content-Type", "text/event-stream")
                            .setBody("data: {\"breaker\":\"test\",\"state\":\"closed\",\"allow_rate\":1.0}\n\n")
                            .setSocketPolicy(SocketPolicy.KEEP_OPEN);
                } else if (request.getPath().contains("/status")) {
                    return new MockResponse()
                            .setHeader("Content-Type", "application/json")
                            .setBody("{\"open_count\":2,\"closed_count\":8,\"last_eval_ms\":1234567890}");
                }
                return new MockResponse().setResponseCode(404);
            }
        });
        server.start();

        try {
            client = TripSwitch.builder("proj_123")
                    .apiKey("test-key")
                    .baseUrl(baseUrl(server))
                    .metadataSyncInterval(Duration.ZERO)
                    .build(Duration.ofSeconds(5));

            Status status = client.getStatus();
            assertNotNull(status);
            assertEquals(2, status.openCount());
            assertEquals(8, status.closedCount());
            assertEquals(1234567890L, status.lastEvalMs());
        } finally {
            server.shutdown();
        }
    }
}
