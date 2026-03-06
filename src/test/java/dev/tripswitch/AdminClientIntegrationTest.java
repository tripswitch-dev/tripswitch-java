package dev.tripswitch;

import dev.tripswitch.admin.*;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the admin client.
 * Requires TRIPSWITCH_API_KEY (admin key) and TRIPSWITCH_PROJECT_ID.
 */
@Tag("integration")
class AdminClientIntegrationTest {

    private static final String API_KEY = System.getenv().getOrDefault("TRIPSWITCH_ADMIN_KEY", System.getenv("TRIPSWITCH_API_KEY"));
    private static final String PROJECT_ID = System.getenv("TRIPSWITCH_PROJECT_ID");
    private static final String BASE_URL = System.getenv().getOrDefault("TRIPSWITCH_BASE_URL", "https://api.tripswitch.dev");

    private void skipIfNoEnv() {
        Assumptions.assumeTrue(API_KEY != null && !API_KEY.isEmpty(), "TRIPSWITCH_API_KEY not set");
        Assumptions.assumeTrue(PROJECT_ID != null && !PROJECT_ID.isEmpty(), "TRIPSWITCH_PROJECT_ID not set");
    }

    private AdminClient newClient() {
        return AdminClient.builder()
                .apiKey(API_KEY)
                .baseUrl(BASE_URL)
                .build();
    }

    @Test
    void testGetProject() {
        skipIfNoEnv();

        try (AdminClient client = newClient()) {
            Project project = client.getProject(PROJECT_ID);
            assertNotNull(project);
            assertEquals(PROJECT_ID, project.id());
            System.out.printf("Project: %s (%s)%n", project.name(), project.id());
        }
    }

    @Test
    void testProjectCrud() {
        skipIfNoEnv();

        try (AdminClient client = newClient()) {
            String projectName = "integration-test-project-" + System.nanoTime();

            // Create
            Project project = client.createProject(new CreateProjectInput(projectName));
            assertNotNull(project);
            assertEquals(projectName, project.name());

            try {
                // List
                ListProjectsResponse result = client.listProjects();
                assertTrue(result.projects().stream().anyMatch(p -> p.id().equals(project.id())));

                // Delete
                client.deleteProject(project.id(), DeleteProjectOptions.builder(projectName).build());

                // Verify deletion
                assertThrows(NotFoundException.class, () -> client.getProject(project.id()));
            } catch (Exception e) {
                // Cleanup on failure
                try {
                    client.deleteProject(project.id(), DeleteProjectOptions.builder(projectName).build());
                } catch (Exception ignored) {
                }
                throw e;
            }
        }
    }

    @Test
    void testBreakerCrud() {
        skipIfNoEnv();

        try (AdminClient client = newClient()) {
            String breakerName = "integration-test-breaker-" + System.nanoTime();

            // Create
            Breaker breaker = client.createBreaker(PROJECT_ID, new CreateBreakerInput(
                    breakerName, "test_metric", BreakerKind.ERROR_RATE, BreakerOp.GT, 0.5
            ));
            assertNotNull(breaker);

            try {
                // Read
                Breaker fetched = client.getBreaker(PROJECT_ID, breaker.id());
                assertEquals(breakerName, fetched.name());

                // Update
                Breaker updated = client.updateBreaker(PROJECT_ID, breaker.id(),
                        UpdateBreakerInput.builder().threshold(0.75).build());
                assertEquals(0.75, updated.threshold(), 0.001);

                // Delete
                client.deleteBreaker(PROJECT_ID, breaker.id());

                // Verify deletion
                assertThrows(NotFoundException.class, () -> client.getBreaker(PROJECT_ID, breaker.id()));
            } catch (Exception e) {
                try { client.deleteBreaker(PROJECT_ID, breaker.id()); } catch (Exception ignored) {}
                throw e;
            }
        }
    }

    @Test
    void testListBreakers() {
        skipIfNoEnv();

        try (AdminClient client = newClient()) {
            ListBreakersResponse result = client.listBreakers(PROJECT_ID, new ListParams(10));
            assertNotNull(result);
            System.out.printf("Found %d breakers%n", result.breakers().size());
        }
    }

    @Test
    void testListRouters() {
        skipIfNoEnv();

        try (AdminClient client = newClient()) {
            ListRoutersResponse result = client.listRouters(PROJECT_ID, new ListParams(10));
            assertNotNull(result);
            System.out.printf("Found %d routers%n", result.routers().size());
        }
    }

    @Test
    void testListNotificationChannels() {
        skipIfNoEnv();

        try (AdminClient client = newClient()) {
            ListNotificationChannelsResponse result = client.listNotificationChannels(PROJECT_ID, new ListParams(10));
            assertNotNull(result);
            System.out.printf("Found %d notification channels%n", result.channels() != null ? result.channels().size() : 0);
        }
    }

    @Test
    void testListEvents() {
        skipIfNoEnv();

        try (AdminClient client = newClient()) {
            ListEventsResponse result = client.listEvents(PROJECT_ID, new ListEventsParams(10));
            assertNotNull(result);
            System.out.printf("Found %d events%n", result.events().size());
        }
    }
}
