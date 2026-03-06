package dev.tripswitch;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the TripSwitch runtime client.
 * Requires environment variables to be set.
 */
@Tag("integration")
class TripSwitchIntegrationTest {

    private static final String API_KEY = System.getenv("TRIPSWITCH_API_KEY");
    private static final String INGEST_SECRET = System.getenv("TRIPSWITCH_INGEST_SECRET");
    private static final String PROJECT_ID = System.getenv("TRIPSWITCH_PROJECT_ID");
    private static final String BREAKER_NAME = System.getenv("TRIPSWITCH_BREAKER_NAME");
    private static final String ROUTER_ID = System.getenv("TRIPSWITCH_BREAKER_ROUTER_ID");
    private static final String METRIC_NAME = System.getenv("TRIPSWITCH_BREAKER_METRIC");
    private static final String BASE_URL = System.getenv().getOrDefault("TRIPSWITCH_BASE_URL", "https://api.tripswitch.dev");

    private void skipIfNoEnv() {
        Assumptions.assumeTrue(API_KEY != null && !API_KEY.isEmpty(), "TRIPSWITCH_API_KEY not set");
        Assumptions.assumeTrue(PROJECT_ID != null && !PROJECT_ID.isEmpty(), "TRIPSWITCH_PROJECT_ID not set");
        Assumptions.assumeTrue(BREAKER_NAME != null && !BREAKER_NAME.isEmpty(), "TRIPSWITCH_BREAKER_NAME not set");
        Assumptions.assumeTrue(ROUTER_ID != null && !ROUTER_ID.isEmpty(), "TRIPSWITCH_BREAKER_ROUTER_ID not set");
        Assumptions.assumeTrue(METRIC_NAME != null && !METRIC_NAME.isEmpty(), "TRIPSWITCH_BREAKER_METRIC not set");
    }

    private void skipIfNoBasicEnv() {
        Assumptions.assumeTrue(API_KEY != null && !API_KEY.isEmpty(), "TRIPSWITCH_API_KEY not set");
        Assumptions.assumeTrue(PROJECT_ID != null && !PROJECT_ID.isEmpty(), "TRIPSWITCH_PROJECT_ID not set");
    }

    @Test
    void testNewClient() {
        skipIfNoEnv();

        try (var client = TripSwitch.builder(PROJECT_ID)
                .apiKey(API_KEY)
                .ingestSecret(INGEST_SECRET)
                .baseUrl(BASE_URL)
                .build(Duration.ofSeconds(10))) {
            assertNotNull(client);
        }
    }

    @Test
    void testExecute() {
        skipIfNoEnv();

        try (var client = TripSwitch.builder(PROJECT_ID)
                .apiKey(API_KEY)
                .ingestSecret(INGEST_SECRET)
                .baseUrl(BASE_URL)
                .build(Duration.ofSeconds(10))) {
            String result = client.execute(() -> "success",
                    TripSwitch.withBreakers(BREAKER_NAME),
                    TripSwitch.withRouter(ROUTER_ID),
                    TripSwitch.withMetrics(Map.of(METRIC_NAME, TripSwitch.LATENCY))
            );
            assertEquals("success", result);
        } catch (BreakerOpenException e) {
            // Expected if breaker is tripped
        }
    }

    @Test
    void testStats() {
        skipIfNoEnv();

        try (var client = TripSwitch.builder(PROJECT_ID)
                .apiKey(API_KEY)
                .ingestSecret(INGEST_SECRET)
                .baseUrl(BASE_URL)
                .build(Duration.ofSeconds(10))) {
            SDKStats stats = client.stats();
            assertTrue(stats.sseConnected(), "SSE should be connected after init");
        }
    }

    @Test
    void testGracefulShutdown() {
        skipIfNoEnv();

        try (var client = TripSwitch.builder(PROJECT_ID)
                .apiKey(API_KEY)
                .ingestSecret(INGEST_SECRET)
                .baseUrl(BASE_URL)
                .build(Duration.ofSeconds(10))) {
            // Execute a few tasks
            for (int i = 0; i < 5; i++) {
                final int iteration = i;
                try {
                    client.execute(() -> iteration,
                            TripSwitch.withBreakers(BREAKER_NAME),
                            TripSwitch.withRouter(ROUTER_ID),
                            TripSwitch.withMetrics(Map.of(METRIC_NAME, TripSwitch.LATENCY))
                    );
                } catch (BreakerOpenException ignored) {
                }
            }
        }
    }

    @Test
    void testGetStatus() {
        skipIfNoEnv();

        try (var client = TripSwitch.builder(PROJECT_ID)
                .apiKey(API_KEY)
                .baseUrl(BASE_URL)
                .build(Duration.ofSeconds(10))) {
            Status status = client.getStatus();
            assertNotNull(status);
            assertTrue(status.openCount() >= 0);
            assertTrue(status.closedCount() >= 0);
        }
    }

    @Test
    void testMetadataSync() throws Exception {
        skipIfNoBasicEnv();

        try (var client = TripSwitch.builder(PROJECT_ID)
                .apiKey(API_KEY)
                .baseUrl(BASE_URL)
                .metadataSyncInterval(Duration.ofSeconds(5))
                .build(Duration.ofSeconds(10))) {
            // Give metadata sync time
            Thread.sleep(500);

            var breakers = client.getBreakersMetadata();
            var routers = client.getRoutersMetadata();

            // At minimum, metadata should have been attempted
            System.out.printf("Cached %d breakers, %d routers%n",
                    breakers != null ? breakers.size() : 0,
                    routers != null ? routers.size() : 0);
        }
    }
}
