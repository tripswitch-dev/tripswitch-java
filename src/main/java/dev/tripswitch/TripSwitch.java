package dev.tripswitch;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import okhttp3.*;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSourceListener;
import okhttp3.sse.EventSources;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.*;
import java.util.zip.GZIPOutputStream;

/**
 * The main Tripswitch runtime client. Maintains real-time breaker state via SSE
 * and reports execution samples to the Tripswitch API.
 *
 * <pre>{@code
 * TripSwitch client = TripSwitch.builder("proj_123")
 *     .apiKey("eb_pk_...")
 *     .ingestSecret("64-char-hex")
 *     .build();
 *
 * String result = client.execute(() -> doWork(),
 *     TripSwitch.withBreakers("my-breaker"),
 *     TripSwitch.withRouter("my-router"),
 *     TripSwitch.withMetrics(Map.of("latency", TripSwitch.LATENCY))
 * );
 *
 * client.close();
 * }</pre>
 */
public class TripSwitch implements AutoCloseable {

    /** SDK Contract version. */
    public static final String CONTRACT_VERSION = "0.2";

    /** Sentinel value for automatic latency measurement in metrics. */
    public static final Object LATENCY = new Object();

    private static final Logger log = LoggerFactory.getLogger(TripSwitch.class);
    private static final int BUFFER_CAPACITY = 10_000;
    private static final int BATCH_SIZE = 500;
    private static final Duration FLUSH_INTERVAL = Duration.ofSeconds(15);
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration DEFAULT_META_SYNC_INTERVAL = Duration.ofSeconds(30);

    private final String projectId;
    private final String apiKey;
    private final String ingestSecret;
    private final boolean failOpen;
    private final String baseUrl;
    private final StateChangeListener onStateChange;
    private final Supplier<String> traceIdExtractor;
    private final Map<String, String> globalTags;
    private final Duration metaSyncInterval;

    // Thread-safe state
    private final ReentrantReadWriteLock stateLock = new ReentrantReadWriteLock();
    private final Map<String, InternalBreakerState> breakerStates = new HashMap<>();

    private final ReentrantReadWriteLock metaLock = new ReentrantReadWriteLock();
    private volatile List<BreakerMeta> breakersMeta;
    private volatile List<RouterMeta> routersMeta;
    private volatile String breakersETag;
    private volatile String routersETag;

    // Stats
    private final AtomicLong droppedSamples = new AtomicLong();
    private volatile boolean sseConnected;
    private final AtomicLong sseReconnects = new AtomicLong();
    private volatile Instant lastSuccessfulFlush;
    private volatile Instant lastSseEvent;
    private final AtomicLong flushFailures = new AtomicLong();

    // Sample buffer
    private final LinkedBlockingQueue<Sample> sampleQueue = new LinkedBlockingQueue<>(BUFFER_CAPACITY);

    // HTTP & JSON
    private final OkHttpClient httpClient;
    private final ObjectMapper mapper;

    // Background threads
    private final ScheduledExecutorService scheduler;
    private final ExecutorService flushExecutor;
    private volatile EventSource eventSource;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    private TripSwitch(Builder builder) {
        this.projectId = builder.projectId;
        this.apiKey = builder.apiKey;
        this.ingestSecret = builder.ingestSecret;
        this.failOpen = builder.failOpen;
        this.baseUrl = builder.baseUrl;
        this.onStateChange = builder.onStateChange;
        this.traceIdExtractor = builder.traceIdExtractor;
        this.globalTags = builder.globalTags != null ? Map.copyOf(builder.globalTags) : Map.of();
        this.metaSyncInterval = builder.metaSyncInterval;

        this.httpClient = builder.httpClient != null ? builder.httpClient :
                new OkHttpClient.Builder().callTimeout(DEFAULT_TIMEOUT).build();
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        this.scheduler = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            t.setName("tripswitch-scheduler");
            return t;
        });

        this.flushExecutor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            t.setName("tripswitch-flush-worker");
            return t;
        });
    }

    // ---- Static factory methods for ExecuteOption ----

    /** Specifies breaker names to check before executing. */
    public static ExecuteOption withBreakers(String... names) {
        return opts -> opts.breakers.addAll(Arrays.asList(names));
    }

    /** Dynamically selects breakers based on cached metadata. */
    public static ExecuteOption withSelectedBreakers(Function<List<BreakerMeta>, List<String>> selector) {
        return opts -> opts.breakerSelector = selector;
    }

    /** Specifies the router ID for sample routing. */
    public static ExecuteOption withRouter(String routerId) {
        return opts -> opts.routerId = routerId;
    }

    /** Dynamically selects a router based on cached metadata. */
    public static ExecuteOption withSelectedRouter(Function<List<RouterMeta>, String> selector) {
        return opts -> opts.routerSelector = selector;
    }

    /**
     * Sets metrics to report. Values can be:
     * <ul>
     *   <li>{@link #LATENCY} — auto-computed task duration in ms</li>
     *   <li>{@link Supplier}{@code <Double>} — called after task completes</li>
     *   <li>{@link Number} — static value</li>
     * </ul>
     */
    public static ExecuteOption withMetrics(Map<String, Object> metrics) {
        return opts -> {
            for (var entry : metrics.entrySet()) {
                if (entry.getKey() != null && !entry.getKey().isEmpty()) {
                    opts.metrics.put(entry.getKey(), entry.getValue());
                }
            }
        };
    }

    /** Registers a function to extract metrics from the task result after execution. */
    @SuppressWarnings("unchecked")
    public static <T> ExecuteOption withDeferredMetrics(BiFunction<T, Exception, Map<String, Double>> fn) {
        return opts -> opts.deferredMetrics = (BiFunction<Object, Exception, Map<String, Double>>) (BiFunction<?, ?, ?>) fn;
    }

    /** Adds a single tag. */
    public static ExecuteOption withTag(String key, String value) {
        return opts -> opts.tags.put(key, value);
    }

    /** Adds multiple tags. */
    public static ExecuteOption withTags(Map<String, String> tags) {
        return opts -> opts.tags.putAll(tags);
    }

    /** Specifies exception classes that should not count as failures. */
    @SafeVarargs
    public static ExecuteOption withIgnoreErrors(Class<? extends Exception>... types) {
        return opts -> opts.ignoreErrors.addAll(Arrays.asList(types));
    }

    /** Custom predicate to determine if an exception is a failure. Return true = failure. */
    public static ExecuteOption withErrorEvaluator(Predicate<Exception> evaluator) {
        return opts -> opts.errorEvaluator = evaluator;
    }

    /** Sets a specific trace ID. */
    public static ExecuteOption withTraceId(String traceId) {
        return opts -> opts.traceId = traceId;
    }

    // ---- Core execute ----

    /**
     * Wraps a task with circuit breaker logic.
     *
     * @param task    the task to execute
     * @param options per-call options
     * @param <T>     the return type
     * @return the task result
     * @throws BreakerOpenException if a breaker is open
     */
    public <T> T execute(Callable<T> task, ExecuteOption... options) {
        // 1. Apply options
        ExecuteOptions execOpts = new ExecuteOptions();
        for (ExecuteOption opt : options) {
            opt.apply(execOpts);
        }

        // 2. Resolve dynamic breaker selection
        if (execOpts.breakerSelector != null) {
            if (!execOpts.breakers.isEmpty()) {
                throw new ConflictingOptionsException("WithBreakers and WithSelectedBreakers");
            }
            List<BreakerMeta> meta = getBreakersMetadata();
            if (meta == null) {
                throw new MetadataUnavailableException();
            }
            try {
                List<String> selected = execOpts.breakerSelector.apply(meta);
                if (selected != null) execOpts.breakers.addAll(selected);
            } catch (Exception e) {
                log.warn("breaker selector threw exception", e);
            }
        }

        // 3. Resolve dynamic router selection
        if (execOpts.routerSelector != null) {
            if (execOpts.routerId != null && !execOpts.routerId.isEmpty()) {
                throw new ConflictingOptionsException("WithRouter and WithSelectedRouter");
            }
            List<RouterMeta> meta = getRoutersMetadata();
            if (meta == null) {
                throw new MetadataUnavailableException();
            }
            try {
                execOpts.routerId = execOpts.routerSelector.apply(meta);
            } catch (Exception e) {
                log.warn("router selector threw exception", e);
            }
        }

        // 4. Check breaker states
        double minAllowRate = 1.0;
        stateLock.readLock().lock();
        try {
            for (String name : execOpts.breakers) {
                InternalBreakerState state = breakerStates.get(name);
                if (state == null) continue;
                if ("open".equals(state.state)) {
                    throw new BreakerOpenException(name);
                }
                if ("half_open".equals(state.state) && state.allowRate < minAllowRate) {
                    minAllowRate = state.allowRate;
                }
            }
        } finally {
            stateLock.readLock().unlock();
        }

        if (minAllowRate < 1.0 && ThreadLocalRandom.current().nextDouble() >= minAllowRate) {
            throw new BreakerOpenException();
        }

        // 5. Run task
        long startNanos = System.nanoTime();
        T result;
        Exception taskError = null;
        try {
            result = task.call();
        } catch (Exception e) {
            taskError = e;
            result = null;
        }

        // 6. Compute duration
        long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);

        // 7. Determine OK
        boolean ok = !isFailure(taskError, execOpts);

        // 8. Resolve trace ID
        String traceId = execOpts.traceId;
        if ((traceId == null || traceId.isEmpty()) && traceIdExtractor != null) {
            try {
                traceId = traceIdExtractor.get();
            } catch (Exception e) {
                log.warn("traceIdExtractor threw exception", e);
            }
        }

        // 9. Resolve metrics
        boolean hasMetrics = !execOpts.metrics.isEmpty() || execOpts.deferredMetrics != null;
        if (hasMetrics && (execOpts.routerId == null || execOpts.routerId.isEmpty())) {
            log.warn("metrics specified but no router - samples will not be emitted");
        }

        // 10. Emit samples if router specified
        if (execOpts.routerId != null && !execOpts.routerId.isEmpty()) {
            List<Sample> samples = resolveMetrics(execOpts.metrics, durationMs);

            // Deferred metrics
            if (execOpts.deferredMetrics != null) {
                try {
                    Map<String, Double> deferred = execOpts.deferredMetrics.apply(result, taskError);
                    if (deferred != null) {
                        for (var entry : deferred.entrySet()) {
                            if (entry.getKey() != null && !entry.getKey().isEmpty()) {
                                samples.add(new Sample(null, entry.getKey(), 0, entry.getValue(), false, null, null));
                            }
                        }
                    }
                } catch (Exception e) {
                    log.warn("deferred metrics function threw exception", e);
                }
            }

            Map<String, String> mergedTags = mergeTags(execOpts.tags);
            long tsMs = System.currentTimeMillis();

            for (Sample s : samples) {
                enqueue(new Sample(execOpts.routerId, s.metric(), tsMs, s.value(), ok, mergedTags, traceId));
            }
        }

        // 11. Return or rethrow
        if (taskError != null) {
            if (taskError instanceof RuntimeException re) throw re;
            throw new TripSwitchException("task execution failed", taskError);
        }
        return result;
    }

    /** Fire-and-forget sample reporting. */
    public void report(ReportInput input) {
        if (input.getRouterId() == null || input.getRouterId().isEmpty() ||
            input.getMetric() == null || input.getMetric().isEmpty()) {
            log.warn("Report called with missing required fields: routerId={}, metric={}",
                    input.getRouterId(), input.getMetric());
            return;
        }

        Map<String, String> mergedTags = mergeTags(input.getTags());
        enqueue(new Sample(
                input.getRouterId(), input.getMetric(), System.currentTimeMillis(),
                input.getValue(), input.isOk(), mergedTags, input.getTraceId()
        ));
    }

    // ---- State inspection ----

    /** Returns the cached state for a single breaker, or null. */
    public BreakerStatus getState(String name) {
        stateLock.readLock().lock();
        try {
            InternalBreakerState s = breakerStates.get(name);
            return s != null ? new BreakerStatus(name, s.state, s.allowRate) : null;
        } finally {
            stateLock.readLock().unlock();
        }
    }

    /** Returns a copy of all cached breaker states. */
    public Map<String, BreakerStatus> getAllStates() {
        stateLock.readLock().lock();
        try {
            Map<String, BreakerStatus> result = new HashMap<>(breakerStates.size());
            breakerStates.forEach((name, s) -> result.put(name, new BreakerStatus(name, s.state, s.allowRate)));
            return result;
        } finally {
            stateLock.readLock().unlock();
        }
    }

    /** Returns SDK health metrics snapshot. */
    public SDKStats stats() {
        int cached;
        stateLock.readLock().lock();
        try { cached = breakerStates.size(); } finally { stateLock.readLock().unlock(); }

        return new SDKStats(
                droppedSamples.get(), sampleQueue.size(), sseConnected,
                sseReconnects.get(), lastSuccessfulFlush, lastSseEvent,
                flushFailures.get(), cached
        );
    }

    /** Fetches project health status from the API. */
    public Status getStatus() {
        Request req = new Request.Builder()
                .url(baseUrl + "/v1/projects/" + projectId + "/status")
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .build();

        try (Response resp = httpClient.newCall(req).execute()) {
            if (resp.code() != 200) {
                throw new IOException("tripswitch: unexpected status code: " + resp.code());
            }
            return mapper.readValue(resp.body().bytes(), Status.class);
        } catch (IOException e) {
            throw new TripSwitchException("failed to fetch project status", e);
        }
    }

    /** Returns a deep copy of cached breaker metadata, or null if not yet loaded. */
    public List<BreakerMeta> getBreakersMetadata() {
        metaLock.readLock().lock();
        try {
            if (breakersMeta == null) return null;
            List<BreakerMeta> copy = new ArrayList<>(breakersMeta.size());
            for (BreakerMeta b : breakersMeta) {
                copy.add(new BreakerMeta(b.id(), b.name(),
                        b.metadata() != null ? new HashMap<>(b.metadata()) : null));
            }
            return copy;
        } finally {
            metaLock.readLock().unlock();
        }
    }

    /** Returns a deep copy of cached router metadata, or null if not yet loaded. */
    public List<RouterMeta> getRoutersMetadata() {
        metaLock.readLock().lock();
        try {
            if (routersMeta == null) return null;
            List<RouterMeta> copy = new ArrayList<>(routersMeta.size());
            for (RouterMeta r : routersMeta) {
                copy.add(new RouterMeta(r.id(), r.name(),
                        r.metadata() != null ? new HashMap<>(r.metadata()) : null));
            }
            return copy;
        } finally {
            metaLock.readLock().unlock();
        }
    }

    /** Lists breaker metadata with ETag support. */
    public MetadataResponse<BreakerMeta> listBreakersMetadata(String etag) throws IOException {
        Request.Builder reqBuilder = new Request.Builder()
                .url(baseUrl + "/v1/projects/" + projectId + "/breakers/metadata")
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + apiKey);
        if (etag != null && !etag.isEmpty()) {
            reqBuilder.header("If-None-Match", etag);
        }

        try (Response resp = httpClient.newCall(reqBuilder.build()).execute()) {
            if (resp.code() == 304) {
                return new MetadataResponse<>(null, etag);
            }
            if (resp.code() == 401 || resp.code() == 403) {
                throw new IOException("tripswitch: unauthorized");
            }
            if (resp.code() != 200) {
                throw new IOException("tripswitch: unexpected status: " + resp.code());
            }
            BreakersMetaResponse parsed = mapper.readValue(resp.body().bytes(), BreakersMetaResponse.class);
            return new MetadataResponse<>(parsed.breakers(), resp.header("ETag"));
        }
    }

    /** Lists router metadata with ETag support. */
    public MetadataResponse<RouterMeta> listRoutersMetadata(String etag) throws IOException {
        Request.Builder reqBuilder = new Request.Builder()
                .url(baseUrl + "/v1/projects/" + projectId + "/routers/metadata")
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + apiKey);
        if (etag != null && !etag.isEmpty()) {
            reqBuilder.header("If-None-Match", etag);
        }

        try (Response resp = httpClient.newCall(reqBuilder.build()).execute()) {
            if (resp.code() == 304) {
                return new MetadataResponse<>(null, etag);
            }
            if (resp.code() == 401 || resp.code() == 403) {
                throw new IOException("tripswitch: unauthorized");
            }
            if (resp.code() != 200) {
                throw new IOException("tripswitch: unexpected status: " + resp.code());
            }
            RoutersMetaResponse parsed = mapper.readValue(resp.body().bytes(), RoutersMetaResponse.class);
            return new MetadataResponse<>(parsed.routers(), resp.header("ETag"));
        }
    }

    // ---- Lifecycle ----

    /** Starts background threads. Called by builder after construction. */
    void start(Duration timeout) {
        // Start SSE listener
        if (apiKey != null && !apiKey.isEmpty()) {
            startSseListener();
        }

        // Start flusher
        scheduler.scheduleAtFixedRate(this::flushBatch, FLUSH_INTERVAL.toMillis(),
                FLUSH_INTERVAL.toMillis(), TimeUnit.MILLISECONDS);

        // Start metadata sync
        if (metaSyncInterval != null && !metaSyncInterval.isNegative() && !metaSyncInterval.isZero()) {
            scheduler.scheduleAtFixedRate(this::refreshMetadata, 0,
                    metaSyncInterval.toMillis(), TimeUnit.MILLISECONDS);
        }

        // Block until SSE ready if API key is set
        if (apiKey != null && !apiKey.isEmpty() && timeout != null) {
            CountDownLatch latch = sseReadyLatch;
            if (latch != null) {
                try {
                    latch.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    private volatile CountDownLatch sseReadyLatch = new CountDownLatch(1);

    @Override
    public void close() {
        close(Duration.ofSeconds(5));
    }

    /** Gracefully shuts down, flushing buffered samples within the timeout. */
    public void close(Duration timeout) {
        if (!closed.compareAndSet(false, true)) return;

        // Close SSE
        if (eventSource != null) {
            eventSource.cancel();
        }

        // Final flush
        flushBatch();

        // Shutdown executors
        scheduler.shutdown();
        flushExecutor.shutdown();
        try {
            flushExecutor.awaitTermination(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ---- SSE ----

    private void startSseListener() {
        String sseUrl = baseUrl + "/v1/projects/" + projectId + "/breakers/state:stream";

        Request request = new Request.Builder()
                .url(sseUrl)
                .header("Authorization", "Bearer " + apiKey)
                .header("Accept", "text/event-stream")
                .build();

        EventSourceListener listener = new EventSourceListener() {
            @Override
            public void onOpen(EventSource es, Response response) {
                log.debug("SSE connection opened");
                sseConnected = true;
            }

            @Override
            public void onEvent(EventSource es, String id, String type, String data) {
                try {
                    SseBreakerEvent event = mapper.readValue(data, SseBreakerEvent.class);

                    double allowRate = 0.0;
                    if (event.allowRate() != null) {
                        allowRate = event.allowRate();
                    } else if ("half_open".equals(event.state())) {
                        log.warn("SSE event has null allow_rate for half_open breaker: {}", event.breaker());
                    }

                    updateBreakerState(event.breaker(), event.state(), allowRate);

                    sseConnected = true;
                    lastSseEvent = Instant.now();

                    CountDownLatch latch = sseReadyLatch;
                    if (latch != null && latch.getCount() > 0) {
                        latch.countDown();
                    }
                } catch (Exception e) {
                    log.error("Failed to parse SSE event: {}", data, e);
                }
            }

            @Override
            public void onClosed(EventSource es) {
                log.debug("SSE connection closed");
                sseConnected = false;
                if (!closed.get()) {
                    sseReconnects.incrementAndGet();
                    // Reconnect
                    scheduler.schedule(() -> startSseListener(), 1, TimeUnit.SECONDS);
                }
            }

            @Override
            public void onFailure(EventSource es, Throwable t, Response response) {
                if (closed.get()) return;
                sseConnected = false;
                sseReconnects.incrementAndGet();
                if (t != null) {
                    log.warn("SSE connection failed, reconnecting...", t);
                }
                // Reconnect with backoff
                scheduler.schedule(() -> startSseListener(), 3, TimeUnit.SECONDS);
            }
        };

        EventSource.Factory factory = EventSources.createFactory(httpClient);
        eventSource = factory.newEventSource(request, listener);
    }

    private void updateBreakerState(String name, String newState, double allowRate) {
        String oldState = null;
        stateLock.writeLock().lock();
        try {
            InternalBreakerState existing = breakerStates.get(name);
            if (existing != null) oldState = existing.state;
            breakerStates.put(name, new InternalBreakerState(newState, allowRate));
        } finally {
            stateLock.writeLock().unlock();
        }

        log.info("Breaker state updated: name={}, oldState={}, newState={}, allowRate={}",
                name, oldState, newState, allowRate);

        if (oldState != null && !oldState.equals(newState) && onStateChange != null) {
            try {
                onStateChange.accept(name, oldState, newState);
            } catch (Exception e) {
                log.warn("onStateChange callback threw exception", e);
            }
        }
    }

    // ---- Flusher ----

    private void flushBatch() {
        List<Sample> batch = new ArrayList<>(BATCH_SIZE);
        sampleQueue.drainTo(batch, BATCH_SIZE);
        if (batch.isEmpty()) return;
        flushExecutor.submit(() -> sendBatch(batch));
    }

    private void sendBatch(List<Sample> batch) {
        if (batch.isEmpty()) return;

        try {
            // Marshal
            BatchPayload payload = new BatchPayload(batch);
            byte[] jsonBytes = mapper.writeValueAsBytes(payload);

            // GZIP
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (GZIPOutputStream gz = new GZIPOutputStream(baos)) {
                gz.write(jsonBytes);
            }
            byte[] compressed = baos.toByteArray();

            // Sign
            long timestampMs = System.currentTimeMillis();
            String timestampStr = String.valueOf(timestampMs);
            String signature = null;
            if (ingestSecret != null && !ingestSecret.isEmpty()) {
                byte[] secretBytes = hexDecode(ingestSecret);
                byte[] tsBytes = timestampStr.getBytes(StandardCharsets.UTF_8);
                byte[] message = new byte[tsBytes.length + 1 + compressed.length];
                System.arraycopy(tsBytes, 0, message, 0, tsBytes.length);
                message[tsBytes.length] = (byte) '.';
                System.arraycopy(compressed, 0, message, tsBytes.length + 1, compressed.length);
                Mac mac = Mac.getInstance("HmacSHA256");
                mac.init(new SecretKeySpec(secretBytes, "HmacSHA256"));
                byte[] hmacBytes = mac.doFinal(message);
                signature = "v1=" + hexEncode(hmacBytes);
            }

            // Retry with backoff
            long[] backoffs = {100, 400, 1000};
            String url = baseUrl + "/v1/projects/" + projectId + "/ingest";

            for (int attempt = 0; attempt <= backoffs.length; attempt++) {
                if (closed.get()) {
                    droppedSamples.addAndGet(batch.size());
                    return;
                }

                if (attempt > 0) {
                    Thread.sleep(backoffs[attempt - 1]);
                }

                Request.Builder reqBuilder = new Request.Builder()
                        .url(url)
                        .post(RequestBody.create(compressed, MediaType.get("application/json")))
                        .header("Content-Encoding", "gzip")
                        .header("X-EB-Timestamp", timestampStr);

                if (signature != null) {
                    reqBuilder.header("X-EB-Signature", signature);
                }

                try (Response resp = httpClient.newCall(reqBuilder.build()).execute()) {
                    if (resp.isSuccessful()) {
                        lastSuccessfulFlush = Instant.now();
                        return;
                    }
                    log.warn("Batch send failed: status={}, attempt={}", resp.code(), attempt + 1);
                } catch (IOException e) {
                    if (!closed.get()) {
                        log.warn("Batch send failed: attempt={}", attempt + 1, e);
                    }
                }
            }

            // Exhausted
            log.error("Dropping batch after retries exhausted: count={}", batch.size());
            droppedSamples.addAndGet(batch.size());
            flushFailures.incrementAndGet();

        } catch (Exception e) {
            log.error("Failed to send batch", e);
            droppedSamples.addAndGet(batch.size());
        }
    }

    // ---- Metadata sync ----

    private void refreshMetadata() {
        try {
            // Breakers
            MetadataResponse<BreakerMeta> bResp = listBreakersMetadata(breakersETag);
            if (bResp.items() != null) {
                metaLock.writeLock().lock();
                try {
                    breakersMeta = bResp.items();
                    breakersETag = bResp.newEtag();
                } finally {
                    metaLock.writeLock().unlock();
                }
            }
        } catch (IOException e) {
            if (e.getMessage() != null && e.getMessage().contains("unauthorized")) {
                log.warn("Metadata sync stopping due to auth failure");
                return;
            }
            log.warn("Failed to refresh breakers metadata", e);
        }

        try {
            // Routers
            MetadataResponse<RouterMeta> rResp = listRoutersMetadata(routersETag);
            if (rResp.items() != null) {
                metaLock.writeLock().lock();
                try {
                    routersMeta = rResp.items();
                    routersETag = rResp.newEtag();
                } finally {
                    metaLock.writeLock().unlock();
                }
            }
        } catch (IOException e) {
            if (e.getMessage() != null && e.getMessage().contains("unauthorized")) {
                log.warn("Metadata sync stopping due to auth failure");
                return;
            }
            log.warn("Failed to refresh routers metadata", e);
        }
    }

    // ---- Internal helpers ----

    private void enqueue(Sample sample) {
        if (!sampleQueue.offer(sample)) {
            droppedSamples.incrementAndGet();
        }
    }

    private boolean isFailure(Exception err, ExecuteOptions opts) {
        if (err == null) return false;
        if (opts.errorEvaluator != null) return opts.errorEvaluator.test(err);
        for (Class<? extends Exception> cls : opts.ignoreErrors) {
            if (cls.isInstance(err)) return false;
        }
        return true;
    }

    private List<Sample> resolveMetrics(Map<String, Object> metrics, long durationMs) {
        List<Sample> samples = new ArrayList<>();
        for (var entry : metrics.entrySet()) {
            String key = entry.getKey();
            Object val = entry.getValue();
            double resolved;

            if (val == LATENCY) {
                resolved = durationMs;
            } else if (val instanceof Supplier<?> supplier) {
                try {
                    Object result = supplier.get();
                    resolved = ((Number) result).doubleValue();
                } catch (Exception e) {
                    log.warn("Metric supplier threw exception for key={}", key, e);
                    continue;
                }
            } else if (val instanceof Number num) {
                resolved = num.doubleValue();
            } else {
                log.warn("Unsupported metric value type for key={}: {}", key, val != null ? val.getClass().getName() : "null");
                continue;
            }

            samples.add(new Sample(null, key, 0, resolved, false, null, null));
        }
        return samples;
    }

    private Map<String, String> mergeTags(Map<String, String> dynamic) {
        if (dynamic == null || dynamic.isEmpty()) return globalTags.isEmpty() ? null : globalTags;
        if (globalTags.isEmpty()) return dynamic;
        Map<String, String> merged = new HashMap<>(globalTags);
        merged.putAll(dynamic);
        return merged;
    }

    private static byte[] hexDecode(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }

    private static String hexEncode(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    // ---- Internal types ----

    private static class InternalBreakerState {
        final String state;
        final double allowRate;
        InternalBreakerState(String state, double allowRate) {
            this.state = state;
            this.allowRate = allowRate;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SseBreakerEvent(
            @JsonProperty("breaker") String breaker,
            @JsonProperty("state") String state,
            @JsonProperty("allow_rate") Double allowRate
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Sample(
            @JsonProperty("router_id") String routerId,
            @JsonProperty("metric") String metric,
            @JsonProperty("ts_ms") long tsMs,
            @JsonProperty("value") double value,
            @JsonProperty("ok") boolean ok,
            @JsonProperty("tags") Map<String, String> tags,
            @JsonProperty("trace_id") String traceId
    ) {}

    record BatchPayload(@JsonProperty("samples") List<Sample> samples) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record BreakersMetaResponse(@JsonProperty("breakers") List<BreakerMeta> breakers) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RoutersMetaResponse(@JsonProperty("routers") List<RouterMeta> routers) {}

    /** Callback for breaker state changes. */
    @FunctionalInterface
    public interface StateChangeListener {
        void accept(String name, String from, String to);
    }

    // ---- Builder ----

    public static Builder builder(String projectId) {
        return new Builder(projectId);
    }

    public static class Builder {
        private final String projectId;
        private String apiKey;
        private String ingestSecret;
        private boolean failOpen = true;
        private String baseUrl = "https://api.tripswitch.dev";
        private StateChangeListener onStateChange;
        private Supplier<String> traceIdExtractor;
        private Duration metaSyncInterval = DEFAULT_META_SYNC_INTERVAL;
        private Map<String, String> globalTags;
        private OkHttpClient httpClient;

        Builder(String projectId) { this.projectId = projectId; }

        public Builder apiKey(String key) { this.apiKey = key; return this; }
        public Builder ingestSecret(String secret) { this.ingestSecret = secret; return this; }
        public Builder failOpen(boolean failOpen) { this.failOpen = failOpen; return this; }
        public Builder baseUrl(String url) { this.baseUrl = url; return this; }
        public Builder onStateChange(StateChangeListener callback) { this.onStateChange = callback; return this; }
        public Builder traceIdExtractor(Supplier<String> extractor) { this.traceIdExtractor = extractor; return this; }
        public Builder metadataSyncInterval(Duration interval) { this.metaSyncInterval = interval; return this; }
        public Builder globalTags(Map<String, String> tags) { this.globalTags = tags; return this; }
        public Builder httpClient(OkHttpClient client) { this.httpClient = client; return this; }

        /** Builds and starts the client. Blocks until SSE is ready or timeout expires. */
        public TripSwitch build(Duration timeout) {
            TripSwitch client = new TripSwitch(this);
            client.start(timeout);
            return client;
        }

        /** Builds and starts the client with default 10-second timeout. */
        public TripSwitch build() {
            return build(Duration.ofSeconds(10));
        }
    }
}
